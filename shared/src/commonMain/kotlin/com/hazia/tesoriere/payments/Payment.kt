@file:OptIn(ExperimentalTime::class)
@file:UseSerializers(InstantMillisSerializer::class)

package com.hazia.tesoriere.payments

import com.hazia.tesoriere.model.InstantMillisSerializer
import com.hazia.tesoriere.model.Money
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Tesoriere's own: the payment (SPEC R21), where every expense is recorded. Sci-Fin had no such
 * thing; its expenses were ledger rows under one key each.
 */

/** Where a payment's money came from (SPEC R22, R54). Stored as `savings`, `goal:<id>` or `account:<id>`. */
@Serializable(with = PaymentSourceSerializer::class)
sealed interface PaymentSource {
    val stored: String

    data object Savings : PaymentSource { override val stored = "savings" }
    data class Goal(val id: String) : PaymentSource { override val stored get() = "goal:$id" }
    data class Account(val id: String) : PaymentSource { override val stored get() = "account:$id" }

    companion object {
        fun parse(s: String): PaymentSource {
            if (s == "savings") return Savings
            val i = s.indexOf(':')
            require(i > 0 && i < s.length - 1) { "not a payment source: $s" }
            val id = s.substring(i + 1)
            return when (s.substring(0, i)) {
                "goal" -> Goal(id)
                "account" -> Account(id)
                else -> throw IllegalArgumentException("not a payment source: $s")
            }
        }
    }
}

object PaymentSourceSerializer : KSerializer<PaymentSource> {
    override val descriptor = PrimitiveSerialDescriptor("com.hazia.tesoriere.PaymentSource", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PaymentSource) = encoder.encodeString(value.stored)
    override fun deserialize(decoder: Decoder): PaymentSource = PaymentSource.parse(decoder.decodeString())
}

/**
 * One category's share of a payment (SPEC R21, R23). [categoryId] is null only for a part read
 * from a Sci-Fin backup (owner answer 3 of 2026-11-24), which counts in no category's spent.
 * [amount] may be negative only for a difference part (SPEC R39).
 */
@Serializable
data class Part(val categoryId: String?, val amount: Money)

/** A bank row's two keys (SPEC R26): the OFX FITID when there is one, and `syn:date|amount|description`. */
@Serializable
data class BankKeys(val fitid: String?, val synKey: String)

/**
 * One line of a receipt, kept so each part can be traced to the lines it came from (SPEC R21).
 * The type of [quantity] waits on the receipt file's format (Chatelaine's spec, rule 123).
 */
@Serializable
data class ReceiptLine(
    val name: String,
    val pack: String?,
    val quantity: Int,
    val pricePaid: Money,
    val chatelaineCategoryId: String,
    val chatelaineCategoryName: String,
    val categoryId: String,
)

/** A payment (SPEC R21): one date, one total, one source, and parts that add up to the total. */
@Serializable
data class Payment(
    val id: String,
    val profileId: String,
    val date: LocalDate,
    val loggedAt: Instant,
    val total: Money,
    val source: PaymentSource,
    val payee: String?,
    val parts: List<Part>,
    val receiptId: String? = null,
    val receiptLines: List<ReceiptLine> = emptyList(),
    val bankKeys: BankKeys? = null,
)

/**
 * What the engine flags about a recorded payment, for a warning the user can dismiss (SPEC R54).
 * A code, not wording: how it reads is the design pass's (SPEC R16).
 */
enum class PaymentNotice {
    /** Its source Goal was deleted: undoing, editing or removing it moves Savings in the Goal's place. */
    SOURCE_GOAL_DELETED,
}

/** What the payment editor holds before it is saved. */
data class PaymentDraft(
    val date: LocalDate,
    val total: Money,
    val source: PaymentSource,
    val payee: String?,
    val parts: List<Part>,
)
