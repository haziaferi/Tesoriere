@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.data

import androidx.room.TypeConverter
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.GoalMode
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.payments.BankKeys
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.ReceiptLine
import com.hazia.tesoriere.receipts.PickupMode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/data/Converters.kt at 2892110.
 *
 * The stored form of the two kinds of time, the closed vocabularies and the JSON columns. A day is
 * ISO text (`2026-09-14`), sortable as text; an instant is epoch milliseconds; an enum stores its
 * token, never its ordinal, so reordering an enum can never re-label stored rows.
 *
 * Changed from Sci-Fin:
 * - A value that cannot be read never fails as a crash (SPEC R141). A field that may hold nothing
 *   reads as nothing; any other field throws [UnreadableStoredValue], which the reads in
 *   TesoriereDao catch to leave that one record out. Sci-Fin threw whatever the parser threw.
 * - Tesoriere's own columns: a payment's source as `PaymentSource.stored`, its parts, receipt lines
 *   and bank keys as JSON (SPEC R21, R39, R26), the receipt settings' pickup mode, lists and maps
 *   (SPEC R112). Sci-Fin's appearance enums are not ported (SPEC R16).
 * - Section references are replaced by SPEC rule numbers.
 */

/** A stored value that cannot be read as its type (SPEC R141): the record holding it is read as missing. */
class UnreadableStoredValue(what: String) : RuntimeException(what)

class Converters {
    private fun unreadable(what: String, s: String): Nothing = throw UnreadableStoredValue("$what: $s")

    private fun dayOrNull(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    @TypeConverter fun fromDay(d: LocalDate): String = d.toString()
    @TypeConverter fun toDay(s: String): LocalDate = dayOrNull(s) ?: unreadable("day", s)
    @TypeConverter fun fromDayOrNull(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun toDayOrNull(s: String?): LocalDate? = s?.let(::dayOrNull)

    @TypeConverter fun fromInstant(i: Instant): Long = i.toEpochMilliseconds()
    @TypeConverter fun toInstant(l: Long): Instant = Instant.fromEpochMilliseconds(l)

    @TypeConverter fun fromCadence(c: Cadence): String = c.token
    @TypeConverter fun toCadence(s: String): Cadence = Cadence.of(s) ?: unreadable("cadence", s)
    @TypeConverter fun fromIncomeCadence(c: IncomeCadence): String = c.token
    @TypeConverter fun toIncomeCadence(s: String): IncomeCadence = IncomeCadence.of(s) ?: unreadable("income cadence", s)
    @TypeConverter fun fromIncomeType(t: IncomeType): String = t.token
    @TypeConverter fun toIncomeType(s: String): IncomeType = IncomeType.of(s) ?: unreadable("income type", s)
    @TypeConverter fun fromAccountType(a: AccountType): String = a.token
    @TypeConverter fun toAccountType(s: String): AccountType = AccountType.of(s) ?: unreadable("account type", s)
    @TypeConverter fun fromPickupMode(p: PickupMode): String = p.token
    @TypeConverter fun toPickupMode(s: String): PickupMode = PickupMode.entries.firstOrNull { it.token == s } ?: unreadable("pickup mode", s)

    // fields that may hold nothing: a token that is not one reads as nothing
    @TypeConverter fun fromDateMode(d: DateMode?): String? = d?.token
    @TypeConverter fun toDateMode(s: String?): DateMode? = s?.let { DateMode.of(it) }
    @TypeConverter fun fromGoalMode(g: GoalMode?): String? = g?.token
    @TypeConverter fun toGoalMode(s: String?): GoalMode? = s?.let { GoalMode.of(it) }
    @TypeConverter fun fromRateType(r: RateType?): String? = r?.token
    @TypeConverter fun toRateType(s: String?): RateType? = s?.let { RateType.of(it) }

    @TypeConverter fun fromSource(s: PaymentSource): String = s.stored
    @TypeConverter fun toSource(s: String): PaymentSource = PaymentSource.parse(s) ?: unreadable("payment source", s)

    @TypeConverter fun fromStrings(l: List<String>): String = encode(STRINGS, l)
    @TypeConverter fun toStrings(s: String): List<String> = decode(STRINGS, s, "list")
    @TypeConverter fun fromStringMap(m: Map<String, String>): String = encode(STRING_MAP, m)
    @TypeConverter fun toStringMap(s: String): Map<String, String> = decode(STRING_MAP, s, "map")
    @TypeConverter fun fromParts(l: List<Part>): String = encode(PARTS, l)
    @TypeConverter fun toParts(s: String): List<Part> = decode(PARTS, s, "parts")
    @TypeConverter fun fromLines(l: List<ReceiptLine>): String = encode(LINES, l)
    @TypeConverter fun toLines(s: String): List<ReceiptLine> = decode(LINES, s, "receipt lines")

    // a payment's bank keys may be none: keys that cannot be read read as none, the import's own
    // kept keys still holding the row (SPEC R26)
    @TypeConverter fun fromBankKeys(k: BankKeys?): String? = k?.let { encode(BankKeys.serializer(), it) }
    @TypeConverter fun toBankKeys(s: String?): BankKeys? = s?.let { runCatching { json.decodeFromString(BankKeys.serializer(), it) }.getOrNull() }

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    private fun <T> decode(serializer: KSerializer<T>, s: String, what: String): T =
        try {
            json.decodeFromString(serializer, s)
        } catch (_: Exception) {
            unreadable(what, s)
        }
}

// Outside the class Room reads, with their types written out: Room's processor runs before the
// serialization plugin writes `serializer()`, so it could not work out an inferred type.
private val json: Json = Json { ignoreUnknownKeys = true }
private val STRINGS: KSerializer<List<String>> = ListSerializer(String.serializer())
private val STRING_MAP: KSerializer<Map<String, String>> = MapSerializer(String.serializer(), String.serializer())
private val PARTS: KSerializer<List<Part>> = ListSerializer(Part.serializer())
private val LINES: KSerializer<List<ReceiptLine>> = ListSerializer(ReceiptLine.serializer())
