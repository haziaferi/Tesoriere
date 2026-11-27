@file:OptIn(ExperimentalTime::class)
@file:UseSerializers(InstantMillisSerializer::class)

package com.hazia.tesoriere.payments

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hazia.tesoriere.model.InstantMillisSerializer
import com.hazia.tesoriere.model.Money
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
        /** The source stored as [s], or null for a string that is no source: storage may be corrupted. */
        fun parse(s: String): PaymentSource? {
            if (s == "savings") return Savings
            val i = s.indexOf(':')
            if (i <= 0 || i >= s.length - 1) return null
            val id = s.substring(i + 1)
            return when (s.substring(0, i)) {
                "goal" -> Goal(id)
                "account" -> Account(id)
                else -> null
            }
        }
    }
}

/**
 * A source as its stored string. A string that is no source fails the decoding of what holds it,
 * as kotlinx.serialization reports any malformed input, with a SerializationException its caller
 * catches, as Trash.decode does.
 */
object PaymentSourceSerializer : KSerializer<PaymentSource> {
    override val descriptor = PrimitiveSerialDescriptor("com.hazia.tesoriere.PaymentSource", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PaymentSource) = encoder.encodeString(value.stored)
    override fun deserialize(decoder: Decoder): PaymentSource =
        decoder.decodeString().let { PaymentSource.parse(it) ?: throw SerializationException("not a payment source: $it") }
}

/**
 * One category's share of a payment (SPEC R21, R23). [categoryId] is null only for a part read
 * from a Sci-Fin backup (owner answer 3 of 2026-11-24), which counts in no category's spent.
 * [amount] has the sign of the payment's total: negative on a refund (SPEC R21, R23), except on
 * the one part marked [difference], a pair's difference between the bank row and the receipt
 * (SPEC R39), which may have either sign. Pairing (PR 4) sets the mark; nothing else does.
 */
@Serializable
data class Part(val categoryId: String?, val amount: Money, val difference: Boolean = false)

/** A bank row's two keys (SPEC R26): the OFX FITID when there is one, and `syn:date|amount|description`. */
@Serializable
data class BankKeys(val fitid: String?, val synKey: String)

/**
 * One line of a receipt, kept so each part can be traced to the lines it came from (SPEC R21).
 * The first six fields mirror a line of the receipt file (Chatelaine's spec, rule 123): [pack] is
 * absent when the line has none, [quantity] is a whole number of packs, [pricePaid] is in cents.
 * [categoryId] is the Tesoriere category the line was filed under. A line stored with no `pack`
 * key reads as one with no pack.
 */
@Serializable
data class ReceiptLine(
    val name: String,
    val pack: String? = null,
    val quantity: Int,
    val pricePaid: Money,
    val chatelaineCategoryId: String,
    val chatelaineCategoryName: String,
    val categoryId: String,
)

/**
 * A payment (SPEC R21): one date, one total, one source, and parts that add up to the total. A refund's total is negative.
 * Stored in the `payments` table, its source as text and its parts, receipt lines and bank keys as JSON columns
 * (data/Converters.kt): the engine reads a Profile whole, so no query needs them apart (SPEC R141).
 */
@Serializable
@Entity(tableName = "payments", indices = [Index("profileId")])
data class Payment(
    @PrimaryKey val id: String,
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
    /**
     * A payment from a Goal short of its total: the share Savings paid, the Goal paying the rest
     * (SPEC R54). Kept so undoing, editing or removing it restores both exactly; 0 otherwise.
     */
    val fromSavings: Money = 0,
)

/**
 * What the engine flags about a recorded payment, for a warning the user can dismiss (SPEC R54).
 * A code, not wording: how it reads is the design pass's (SPEC R16).
 */
enum class PaymentNotice {
    /** Its source Goal was deleted: undoing, editing or removing it moves Savings in the Goal's place. */
    SOURCE_GOAL_DELETED,

    /** Its source Account was deleted: editing, re-filing or removing it moves no balance (SPEC R88). */
    SOURCE_ACCOUNT_DELETED,

    /** A draft from a Goal that holds less than its total: the Goal pays all it holds, Savings the rest (SPEC R54). */
    GOAL_SHORT,

    /** A draft charged to a card whose charges are not logged: its payoff counts that spending already (SPEC R109). */
    CHARGES_NOT_LOGGED,
}

/** What the payment editor holds before it is saved. */
data class PaymentDraft(
    val date: LocalDate,
    val total: Money,
    val source: PaymentSource,
    val payee: String?,
    val parts: List<Part>,
)
