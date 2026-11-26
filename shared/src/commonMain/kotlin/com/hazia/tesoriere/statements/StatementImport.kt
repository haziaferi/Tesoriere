package com.hazia.tesoriere.statements

import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.engine.minusDays
import com.hazia.tesoriere.engine.plusDays
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.pairing.Pairing
import com.hazia.tesoriere.payments.BankKeys
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Import.kt at 2892110.
 *
 * Statement import (SPEC R26, R103-R108): a CSV or OFX export is read on the device, each row is
 * annotated with what the Profile already knows, and the result is a review, never a write. One
 * confirm records the ticked rows, each with the statement's own day.
 *
 * Changed from Sci-Fin:
 * - amount() reads cents with whole-number arithmetic (SPEC R20, R104); Sci-Fin's went through a
 *   Double and rounded. A third decimal rounds the cent half up, as Sci-Fin's rounding did. A
 *   text holding `/`, `:` or a letter (other than a trailing DR or CR) is no amount, where
 *   Sci-Fin read a day's digits as one; the Unicode minus, the en dash and DR read as minus.
 * - The amount keyword "value" never takes a header holding "date" (SPEC R105): Sci-Fin let a
 *   Value date column become the amount of a Debit/Credit statement. Italian bank headers (Data
 *   contabile, Data valuta, Causale, Descrizione operazione, Dare, Avere, Addebiti, Accrediti and
 *   others) are keywords, and the booking date wins over the value date; Sci-Fin took the first.
 * - An outflow becomes a payment (SPEC R21) with the row's keys in its bankKeys: Sci-Fin's string
 *   target becomes a PaymentDraft with a source and parts. `cat:` is a draft from Savings,
 *   `spend:` a draft from a Goal, `charge:` a draft from an Account (SPEC R22, R54), and any of
 *   them may be split (SPEC R24). `payoff:` becomes [ReviewRow.payoffAccountId], written as
 *   Sci-Fin did through Accounts.quickAdd, but with R89's default, Savings, or nowhere for an
 *   Account linked to a recurring expense, and [ReviewRow.payoffFrom] to pick Savings, a Goal or
 *   nowhere per row (SPEC R89, R107); Sci-Fin always took it from Savings.
 * - Money in goes to Savings, and on into a Goal when [ReviewRow.incomingGoalId] names one, as
 *   Sci-Fin's `goal:<id>` target did: a `savings` row, then a goal quick-add of the same amount on
 *   the same day; a Goal deleted before Confirm leaves the money in Savings (SPEC R107). Money in
 *   can also be filed as a refund, a payment with a negative total and its keys (SPEC R21, R107),
 *   which Sci-Fin did not have.
 * - Keys are a BankKeys (SPEC R26), and duplicates are also found against every payment's
 *   bankKeys, so a bank row recorded through a pair is not offered again. The synthetic key makes
 *   the description's spacing one space and trims it first; Sci-Fin took it as written.
 * - A payee rule is learned from a row filed under one category, from any source; a split row
 *   teaches nothing (SPEC R108). Sci-Fin learned from a `cat:` target only. A rule pre-fills a
 *   one-part draft from Savings. Rules are kept under [payeeRuleName], the payee less words with a
 *   digit, masked card numbers and card-payment words, where Sci-Fin kept the whole payee; a rule
 *   kept under the whole payee still matches.
 * - canConfirm() replaces `unassigned()`: it also checks that a draft's parts add up (SPEC R23),
 *   and an outflow sent into a Goal holds it back, as Sci-Fin's `goal:` target on an outflow did.
 *   A row paid from a Goal short of it takes the rest from Savings, with the warning code of
 *   rowNotices() (SPEC R54, owner answer H of 2026-11-25, in place of answer 6 of 2026-11-24,
 *   which kept Confirm off).
 *   confirmSummary() gives the count and the money out and in apart, the figures of Sci-Fin's
 *   `importSummary()` line, as figures with no sentence (SPEC R16, R107).
 * - An outflow is RECURRING also when its amount is a recurring expense's amount, or what its rows
 *   dated on that due date hold, within [RECURRING_DAYS] of one of its due dates (SPEC R107);
 *   Sci-Fin matched the expense's name only. A card's payment is left out of both matches, the
 *   name match included, which Sci-Fin applied to every expense. Neither match finds a row dated
 *   before the later of the expense's first due date and its creation day (SPEC R60, owner
 *   ruling of 2026-11-25): Sci-Fin's name match had no date.
 * - No wording: Sci-Fin's review notes are dropped (the status is the code, SPEC R16), and its
 *   target lists with their labels (`inflowTargets`, `outflowTargets`) are the design pass's.
 * - Names: ImportRow is BankRow, ImportStatus is RowStatus (with PAIR, SPEC R37, R118),
 *   annotate() is review(), parseCSV/parseOFX are parseCsv/parseOfx.
 * - A row suggested as a receipt's pair (SPEC R118) arrives PAIR, unticked, naming the payment in
 *   [ReviewRow.pairPaymentId]; Confirm records nothing for a row that names a pair, which only
 *   Pairing.pairBankWithPayment records (SPEC R37, R119). Sci-Fin had no receipts.
 */

/** One parsed statement row. [amount] is signed: negative is money out. */
data class BankRow(val date: LocalDate, val amount: Money, val description: String, val fitid: String?)

/** What the review thinks of a row (SPEC R107). PAIR: suggested as a receipt payment's pair (SPEC R37, R118). */
enum class RowStatus { READY, INCOMING, DUPLICATE, RECURRING, PAIR }

/**
 * A row in the review: what the app thinks, and what the user starts with. A ticked outflow is
 * recorded through [draft] (a payment, SPEC R21) or [payoffAccountId] (a payment off an Account,
 * SPEC R89), exactly one of them; both are null until the user files it or a payee rule
 * pre-fills a draft (SPEC R108). Money in goes to Savings, and on into the Goal [incomingGoalId]
 * when the user picks one, or, filed with a [draft] of negative total, is recorded as a refund
 * (SPEC R21, R107). [pairPaymentId] names the receipt payment the row is suggested as the pair of
 * (SPEC R118): while it is set, Confirm records nothing for the row, ticked or not; pairing records
 * it (Pairing.pairBankWithPayment), and the user turning the suggestion down sets it to null.
 */
data class ReviewRow(
    val row: BankRow,
    val keys: BankKeys,
    val payee: String,
    val status: RowStatus,
    val include: Boolean,
    val draft: PaymentDraft?,
    val payoffAccountId: String?,
    val pairPaymentId: String?,
    val incomingGoalId: String? = null,
    /**
     * Where a payoff of [payoffAccountId] takes its money from (SPEC R89, R107): Savings, a Goal or
     * nowhere; null takes R89's default, Savings unless the Account is linked to a recurring
     * expense; the user can set it per row.
     */
    val payoffFrom: Accounts.PayoffFrom? = null,
    /**
     * The category picked for a payoff of a Loan with nothing charged to it, which counts as
     * spending (SPEC R89, R107); null takes the Loan's linked payment's category.
     */
    val payoffCategoryId: String? = null,
    /**
     * The payments holding this row's keys (StatementImport.sameRow) when the review was built:
     * they stood for rows recorded before it, so none of them is a pair made since (SPEC R119).
     */
    val heldAtReview: Set<String> = emptySet(),
)

/**
 * What the review's Confirm names (SPEC R107): how many ticked rows it records, and the money out
 * and the money in apart, each a positive figure. How it reads is the design pass's (SPEC R16).
 */
data class ConfirmSummary(val count: Int, val moneyOut: Money, val moneyIn: Money)

object StatementImport {

    /** How many days either side of a due date a bank row of the expense's amount is its bill (SPEC R107). */
    const val RECURRING_DAYS = 3

    /**
     * A statement amount in cents (SPEC R104), with whole-number arithmetic only (SPEC R20). When
     * both separators appear the last is the decimal point; with one, three trailing digits mean a
     * thousands separator and any other count a decimal point. `(12,40)`, `12,40-`, `−12,40` and
     * `12,40 DR` are -12.40, `12,40 CR` is 12.40. A text holding `/`, `:` or any other letter is a
     * day, a time or a word, never an amount.
     */
    private val LEADING_CODE = Regex("""^[A-Za-z]{3}\s+(?=\S)""")
    private val TRAILING_CODE = Regex("""(?<=\S)\s+[A-Za-z]{3}$""")

    fun amount(raw: String?): Money? {
        if (raw == null) return null
        var s = raw.trim()
        // a trailing DR is money out, CR money in (SPEC R104)
        val suffix = s.takeLast(2).uppercase().takeIf { s.length > 2 && (it == "DR" || it == "CR") }
        if (suffix != null) s = s.dropLast(2).trim()
        // one three-letter currency code before or after the figure is ignored (SPEC R104)
        s = s.replaceFirst(LEADING_CODE, "").replaceFirst(TRAILING_CODE, "")
        if (s.any { it == '/' || it == ':' || it.isLetter() }) return null
        // the Unicode minus and the en dash stand for a minus
        var t = s.map { if (it == '−' || it == '–') '-' else it }.joinToString("").filter { it.isDigit() || it in ".,-+()" }
        if (t.isEmpty()) return null
        val neg = when (suffix) {
            "DR" -> true
            "CR" -> false
            else -> (t.startsWith("(") && t.endsWith(")")) || t.startsWith("-") || t.endsWith("-")
        }
        t = t.filter { it !in "()-+" }
        if (t.isEmpty()) return null
        val lastDot = t.lastIndexOf('.')
        val lastComma = t.lastIndexOf(',')
        val whole: String
        val fraction: String
        when {
            lastDot >= 0 && lastComma >= 0 -> {
                val dec = maxOf(lastDot, lastComma)
                val thousands = if (dec == lastComma) "." else ","
                whole = t.substring(0, dec).replace(thousands, "")
                fraction = t.substring(dec + 1)
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = maxOf(lastDot, lastComma)
                val tail = t.substring(sep + 1)
                if (tail.length == 3) { whole = t.replace(t[sep].toString(), ""); fraction = "" }
                else { whole = t.substring(0, sep); fraction = tail }
            }
            else -> { whole = t; fraction = "" }
        }
        if (whole.isEmpty() && fraction.isEmpty()) return null
        if (!whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return null
        val units = (whole.ifEmpty { "0" }).toLongOrNull() ?: return null
        if (units > Long.MAX_VALUE / 100 - 1) return null
        val hundredths = fraction.take(2).padEnd(2, '0').toLong()
        val roundUp = if (fraction.length > 2 && fraction[2] >= '5') 1 else 0
        val cents = units * 100 + hundredths + roundUp
        return if (neg) -cents else cents
    }

    /**
     * A statement day (SPEC R106): year first when it starts with four digits, else day first, so
     * 03/04/2026 is 3 April. A two-digit year is in the 2000s; a day that does not exist is null.
     */
    fun date(raw: String?): LocalDate? {
        val t = raw?.trim() ?: return null
        Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").find(t)?.let { m ->
            return runCatching { LocalDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})""").find(t)?.let { m ->
            val y = m.groupValues[3].let { if (it.length == 2) "20$it" else it }.toInt()
            return runCatching { LocalDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    /** OFX (SPEC R103): each `<STMTTRN>` block is a row and carries its FITID. */
    fun parseOfx(text: String): List<BankRow> {
        val blocks = Regex("<STMTTRN>[\\s\\S]*?</STMTTRN>", RegexOption.IGNORE_CASE).findAll(text).map { it.value }
        fun field(b: String, tag: String) =
            Regex("<$tag>([^<\r\n]*)", RegexOption.IGNORE_CASE).find(b)?.groupValues?.get(1)?.trim() ?: ""
        return blocks.mapNotNull { b ->
            val posted = field(b, "DTPOSTED").take(8)
            if (posted.length != 8) return@mapNotNull null
            val d = runCatching { LocalDate(posted.substring(0, 4).toInt(), posted.substring(4, 6).toInt(), posted.substring(6, 8).toInt()) }
                .getOrNull() ?: return@mapNotNull null
            val amt = amount(field(b, "TRNAMT"))?.takeIf { it != 0L } ?: return@mapNotNull null // a zero row moves nothing
            val name = field(b, "NAME").ifEmpty { field(b, "MEMO") }.ifEmpty { StoredLabels.STATEMENT_ENTRY }
            BankRow(d, amt, name, field(b, "FITID").ifEmpty { null })
        }.toList()
    }

    // booking dates first, value dates last (SPEC R105)
    private val VALUE_DATE_KEYS = listOf("value date", "data valuta")
    private val DATE_KEYS = listOf("data contabile", "data operazione", "booking date", "transaction date", "date", "data", "datum", "fecha") + VALUE_DATE_KEYS
    private val DESC_KEYS = listOf(
        "description", "descrizione operazione", "descrizione", "payee", "merchant", "details", "dettagli",
        "beschreibung", "verwendungszweck", "concepto", "narrative", "reference", "causale",
    )
    private val AMOUNT_KEYS = listOf("amount", "importo", "betrag", "value", "importe", "bedrag")
    private val DEBIT_KEYS = listOf("debit", "dare", "addebiti", "addebito", "uscite", "uscita", "soll", "withdrawal", "paid out")
    private val CREDIT_KEYS = listOf("credit", "avere", "accrediti", "accredito", "entrate", "entrata", "haben", "deposit", "paid in")

    /** The text's records: a newline ends one only outside quotes, so a quoted multi-line memo stays one field. */
    private fun csvRecords(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQ = false
        for (ch in text) {
            when {
                ch == '"' -> { inQ = !inQ; cur.append(ch) }
                ch == '\n' && !inQ -> { out.add(cur.toString().removeSuffix("\r")); cur.setLength(0) }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString().removeSuffix("\r"))
        return out.filter { it.isNotBlank() }
    }

    /** One record's fields; `""` inside quotes is one quote. */
    fun splitCsvLine(line: String, delim: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> if (inQ && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else inQ = !inQ
                ch == delim && !inQ -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out.map { it.trim() }
    }

    private fun words(s: String) = s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * The column for [keys], skipping columns another field already claimed (SPEC R105). Keys are
     * tried in their listed order, so "amount" beats "value"; a key matches a header that is the
     * key itself or the key plus one more word ("Booking date", "Amount EUR"), never a longer
     * phrase that merely holds it ("Credit card number"), and an exact header wins over that.
     */
    private fun find(header: List<String>, keys: List<String>, claimed: Set<Int>): Int {
        val free = header.indices.filter { it !in claimed }
        // the amount keyword "value" never takes "Value date", and a date keyword before the value
        // dates never takes a value-date column, so the booking date wins (SPEC R105)
        fun fits(k: String, i: Int) = when {
            k == "value" -> "date" !in words(header[i])
            k in DATE_KEYS && k !in VALUE_DATE_KEYS -> "value" !in header[i] && "valuta" !in header[i]
            else -> true
        }
        for (k in keys) free.firstOrNull { header[it].trim() == k }?.let { return it }
        for (k in keys) {
            val kw = words(k)
            free.firstOrNull { i ->
                val hw = words(header[i])
                fits(k, i) && hw.size <= kw.size + 1 && hw.windowed(kw.size).any { it == kw }
            }?.let { return it }
        }
        // a one-word compound still carries its key: German "Buchungsdatum", "Umsatzbetrag"
        for (k in keys) free.firstOrNull { i -> fits(k, i) && words(header[i]).let { it.size == 1 && ' ' !in k && it[0].contains(k) } }?.let { return it }
        return -1
    }

    /** CSV (SPEC R103, R105): no id, columns by header keyword, `;` where `,` is the decimal separator. */
    fun parseCsv(text: String): List<BankRow> {
        val lines = csvRecords(text)
        if (lines.isEmpty()) return emptyList()
        val delim = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val header = splitCsvLine(lines[0], delim).map { it.lowercase() }
        val iDate = find(header, DATE_KEYS, emptySet())
        val iDesc = find(header, DESC_KEYS, setOf(iDate))
        // split columns first, so "Debit Amount" and "Credit Amount" are never taken as the signed amount
        val iDeb = find(header, DEBIT_KEYS, setOf(iDate, iDesc))
        val iCred = find(header, CREDIT_KEYS, setOf(iDate, iDesc, iDeb))
        val iAmt = find(header, AMOUNT_KEYS, setOf(iDate, iDesc, iDeb, iCred))
        if (iDate < 0 || (iAmt < 0 && iDeb < 0 && iCred < 0)) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val c = splitCsvLine(line, delim)
            val d = date(c.getOrNull(iDate)) ?: return@mapNotNull null
            var amt = if (iAmt >= 0) amount(c.getOrNull(iAmt)) else null
            if (amt == null && iDeb >= 0) amount(c.getOrNull(iDeb))?.takeIf { it != 0L }?.let { amt = -abs(it) }
            if (amt == null && iCred >= 0) amount(c.getOrNull(iCred))?.takeIf { it != 0L }?.let { amt = abs(it) }
            val a = amt ?: return@mapNotNull null
            if (a == 0L) return@mapNotNull null
            val desc = (if (iDesc >= 0) c.getOrNull(iDesc) else null)?.replace(Regex("\\s*\r?\n\\s*"), " ")
            BankRow(d, a, desc?.ifEmpty { null } ?: StoredLabels.STATEMENT_ENTRY, null)
        }
    }

    /** Spaces, tabs, non-breaking and other Unicode spaces, in runs. */
    private val SPACING = Regex("[\\s\\u00A0\\u2007\\u202F]+")

    /**
     * Both keys where the format allows (SPEC R26): the FITID is identity, the synthetic key catches a
     * CSV and an OFX of one period. The description's spacing is made one space and trimmed before
     * its first 40 characters are taken, so two exports that space one payee differently key alike;
     * a description with single spaces keys as it always did.
     */
    fun keys(r: BankRow): BankKeys {
        val desc = r.description.replace(SPACING, " ").trim()
        return BankKeys(r.fitid, "syn:${r.date}|${r.amount.toMajor2()}|${desc.take(40).lowercase()}")
    }

    /** [k] as the strings kept in `ImportState.importedKeys`: `fit:<FITID>` when there is one, then the synthetic key. */
    fun keyStrings(k: BankKeys): List<String> = listOfNotNull(k.fitid?.let { "fit:$it" }, k.synKey)

    /**
     * Whether [a] and [b] key one bank row (SPEC R26, R119): by their FITIDs when both have one,
     * so two equal purchases of one day with FITIDs of their own are two rows; else by the synthetic key.
     */
    fun sameRow(a: BankKeys, b: BankKeys): Boolean = if (a.fitid != null && b.fitid != null) a.fitid == b.fitid else a.synKey == b.synKey

    /**
     * One recorded row as `ImportState.importedKeys` keeps it (SPEC R26): each recorded row appends
     * its FITID key, when it has one, then its synthetic key, so a synthetic key right after a
     * FITID key is that row's. [synKey] is null for a FITID key kept alone, as keys kept before
     * every row appended its own were when the synthetic key was there already.
     */
    internal data class KeptRow(val fitid: String?, val synKey: String?)

    /** The recorded rows [kept] holds, in the order they were recorded (SPEC R26). */
    internal fun keptRows(kept: List<String>): List<KeptRow> {
        val out = ArrayList<KeptRow>()
        var i = 0
        while (i < kept.size) {
            val k = kept[i]
            if (k.startsWith("fit:")) {
                val syn = kept.getOrNull(i + 1)?.takeIf { it.startsWith("syn:") }
                out += KeptRow(k.removePrefix("fit:"), syn)
                i += if (syn != null) 2 else 1
            } else {
                out += KeptRow(null, k)
                i++
            }
        }
        return out
    }

    /** [kept] with one recorded row of keys [k] appended (SPEC R26): every recorded row is kept once. */
    internal fun withRow(kept: List<String>, k: BankKeys): List<String> = kept + keyStrings(k)

    /**
     * [kept] less one recorded row of keys [k] (SPEC R26, R125, R128): the last FITID key of its
     * FITID with the synthetic key kept with it; with no FITID, the last synthetic key kept alone,
     * else the last one kept at all.
     */
    internal fun withoutRow(kept: List<String>, k: BankKeys): List<String> {
        val out = kept.toMutableList()
        if (k.fitid != null) {
            val at = out.lastIndexOf("fit:${k.fitid}")
            if (at < 0) return kept
            if (out.getOrNull(at + 1) == k.synKey) out.removeAt(at + 1)
            out.removeAt(at)
            return out
        }
        val alone = out.indices.lastOrNull { out[it] == k.synKey && !(it > 0 && out[it - 1].startsWith("fit:")) } ?: out.lastIndexOf(k.synKey)
        if (alone >= 0) out.removeAt(alone)
        return out
    }

    /** Whether a recorded row of FITID [fitid] is kept or held by a payment (SPEC R26). */
    private fun recordedByFitid(d: ProfileData, kept: List<KeptRow>, fitid: String): Boolean =
        kept.any { it.fitid == fitid } || d.payments.any { it.bankKeys?.fitid == fitid }

    /**
     * How many recorded rows carry synthetic key [syn] (SPEC R26): those the import keeps and those
     * payments hold, whichever are more, payments' keys being kept too; with [withoutFitid], only
     * rows recorded with no FITID, the ones a row with a FITID is compared with by that key.
     */
    private fun heldBySyn(d: ProfileData, kept: List<KeptRow>, syn: String, withoutFitid: Boolean): Int = maxOf(
        kept.count { it.synKey == syn && (!withoutFitid || it.fitid == null) },
        d.payments.count { p -> p.bankKeys?.let { it.synKey == syn && (!withoutFitid || it.fitid == null) } == true },
    )

    /**
     * Which rows of a statement are recorded already (SPEC R26, R107), indices into [rows]: a row
     * whose FITID was recorded; else, by synthetic key in the statement's order, as many rows as
     * recorded rows carry it, compared with the rows recorded with no FITID for a row with one,
     * the rows of the statement recorded by their FITID taking theirs.
     */
    internal fun recordedRows(d: ProfileData, rows: List<BankKeys>): Set<Int> {
        val kept = keptRows(d.importState.importedKeys)
        val done = rows.indices.filterTo(HashSet()) { i -> rows[i].fitid?.let { recordedByFitid(d, kept, it) } == true }
        for ((syn, group) in rows.indices.filter { it !in done }.groupBy { rows[it].synKey }) {
            val withFit = group.filter { rows[it].fitid != null }
            val noFit = group.filter { rows[it].fitid == null }
            val byFit = withFit.take(heldBySyn(d, kept, syn, withoutFitid = true))
            done += byFit
            val taken = done.count { rows[it].synKey == syn }
            done += noFit.take(maxOf(0, heldBySyn(d, kept, syn, withoutFitid = false) - taken))
        }
        return done
    }

    /**
     * Whether [row] is recorded already (SPEC R26, R119): its FITID recorded; or, by its synthetic
     * key, when the rows recorded with it, less those the rows of [review] that arrived `DUPLICATE`
     * with it stand for, outnumber the other rows of [review] that did not: two equal coffees of one
     * day with no FITID are two rows, and one payment taking their key stands for one of them only.
     * A row that arrived `DUPLICATE` is recorded while any recorded row carries its key.
     */
    fun rowRecorded(d: ProfileData, row: ReviewRow, review: List<ReviewRow>): Boolean {
        val k = row.keys
        val kept = keptRows(d.importState.importedKeys)
        if (k.fitid != null && recordedByFitid(d, kept, k.fitid)) return true
        val held = heldBySyn(d, kept, k.synKey, withoutFitid = k.fitid != null)
        if (row.status == RowStatus.DUPLICATE) return held > 0
        val group = review.filter { sameRow(it.keys, k) }
        val arrived = group.count { it.status == RowStatus.DUPLICATE }
        val others = group.count { it.status != RowStatus.DUPLICATE } - 1
        return held - arrived > maxOf(0, others)
    }

    private fun Money.toMajor2(): String {
        val a = abs(this)
        return (if (this < 0) "-" else "") + "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }

    /** A row's payee (SPEC R108): lower case, anything but letters, digits and spaces read as a space, runs of spaces made one. */
    fun normalisePayee(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The bank's words for a card payment, dropped from a rule name (SPEC R108). Kept short on purpose. */
    private val PAYEE_NOISE = setOf("pos", "pagamento", "carta", "ore", "card", "payment")

    /**
     * The name a payee rule is kept under (SPEC R108): the payee less every word holding a digit
     * (dates, times, terminal ids, card numbers), every word of three or more x's (a masked card
     * number) and the card-payment words above; the whole payee when nothing is left.
     */
    fun payeeRuleName(description: String): String {
        val payee = normalisePayee(description)
        val kept = payee.split(' ').filter { w ->
            w.isNotEmpty() && w.none { it.isDigit() } && w !in PAYEE_NOISE && !(w.length >= 3 && w.all { it == 'x' })
        }
        return kept.joinToString(" ").ifEmpty { payee }
    }

    /**
     * The payment a [row] becomes: its day, minus its amount as the total, its description as the
     * payee. Money out is a purchase; money in a refund, with a negative total (SPEC R21, R107).
     */
    fun draft(row: BankRow, parts: List<Part>, source: PaymentSource = PaymentSource.Savings): PaymentDraft {
        require(row.amount != 0L) { "a row of zero moves nothing" }
        return PaymentDraft(row.date, -row.amount, source, row.description, parts)
    }

    /**
     * Annotates every row with what the Profile already knows (SPEC R107), newest first. Nothing
     * here writes. [today] is not read here; pairing (SPEC R37) uses it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun review(d: ProfileData, rows: List<BankRow>, today: LocalDate): List<ReviewRow> {
        val rowKeys = rows.map(::keys)
        // twins told apart: FITIDs first, then as many rows of a synthetic key as were recorded (SPEC R26)
        val recorded = recordedRows(d, rowKeys)
        val holders = d.payments.filter { it.bankKeys != null }.groupBy { it.bankKeys!!.synKey }
        val byFitid = d.payments.filter { it.bankKeys?.fitid != null }.groupBy { it.bankKeys!!.fitid!! }
        /** The payments holding [k]'s row's keys now, before anything in this review is recorded. */
        fun heldBy(k: BankKeys): Set<String> =
            (holders[k.synKey].orEmpty() + k.fitid?.let { byFitid[it] }.orEmpty())
                .filterTo(HashSet()) { StatementImport.sameRow(it.bankKeys!!, k) }.mapTo(HashSet()) { it.id }
        // a card's payment is left out of both matches: its bank row is the payoff that lowers the card (SPEC R89, R93, R107)
        val byAmount = d.recurringExpenses.filter { !Recurring.paysCard(d, it) }
        val recurring = byAmount.map { it to words(normalisePayee(it.name)) }.filter { it.second.isNotEmpty() }
        /**
         * Whether an outflow [r] with payee words [pw] names a recurring expense (SPEC R107): whole
         * words either way round, "Rent" is in "Affitto casa rent", not in "Parent teacher assoc";
         * never for a row dated before its floor (Recurring.billFloor, SPEC R60, R61).
         */
        fun named(r: BankRow, pw: List<String>): Boolean = pw.isNotEmpty() && recurring.any { (e, nw) ->
            r.date >= Recurring.billFloor(d, e) &&
                (pw.windowed(nw.size).any { it == nw } || nw.windowed(pw.size).any { it == pw })
        }
        /**
         * Whether an outflow [r] is a recurring expense's bill by its amount and day (SPEC R107):
         * the days are its due dates on the chain (SPEC R61) and the days of its sweep rows, which
         * an edit of its schedule leaves where they were (SPEC R65); never for a row dated before
         * its floor (Recurring.billFloor).
         */
        fun billOf(r: BankRow): Boolean = byAmount.any { e ->
            if (r.date < Recurring.billFloor(d, e)) return@any false
            val from = r.date.minusDays(RECURRING_DAYS); val to = r.date.plusDays(RECURRING_DAYS)
            val rows = Recurring.dueDateRows(d, e)                                         // a share pays toward a bill still to come (SPEC R63)
            // what its rows dated a day hold: an edit with the difference declined leaves the old amount there
            fun held(day: LocalDate) = rows.filter { it.date == day }.sumOf { it.amount }.let { it > 0 && -r.amount == it }
            // on a due date the amount matches too; on a sweep row's day only what the rows hold,
            // so a share swept on the 1st does not make every bill of the full amount near a 1st its bill
            Recurring.dueDatesBetween(e, from, to).any { due -> -r.amount == e.amount || held(due) } ||
                rows.map { it.date }.filter { it >= from && it <= to }.distinct().any { held(it) }
        }
        // money out not recorded yet may be a receipt payment's pair (SPEC R118)
        val pairs = Pairing.suggest(d, rows, rows.indices.filterTo(HashSet()) { i -> rows[i].amount < 0 && i !in recorded })
        return rows.mapIndexed { i, r ->
            val keys = rowKeys[i]
            val payee = normalisePayee(r.description)
            val pw = words(payee)
            val status = when {
                i in recorded -> RowStatus.DUPLICATE
                r.amount > 0 -> RowStatus.INCOMING
                i in pairs -> RowStatus.PAIR
                named(r, pw) -> RowStatus.RECURRING
                // a bank seldom writes the expense's name: its amount within 3 days of a due date finds it too
                billOf(r) -> RowStatus.RECURRING
                else -> RowStatus.READY
            }
            // a category is never guessed: only a rule the user taught pre-fills one, kept under the
            // row's rule name, else under its whole payee as a rule taught before rule names was (SPEC R108)
            val rule = if (r.amount < 0) {
                listOf(payeeRuleName(r.description), payee).firstNotNullOfOrNull { name ->
                    d.importState.payeeRules[name]?.takeIf { id -> d.categories.any { it.id == id } }
                }
            } else null
            ReviewRow(
                row = r, keys = keys, payee = payee, status = status,
                include = status == RowStatus.READY || status == RowStatus.INCOMING,
                draft = rule?.let { draft(r, listOf(Part(it, -r.amount))) },
                payoffAccountId = null, pairPaymentId = pairs[i], heldAtReview = heldBy(keys),
            )
        }.sortedByDescending { it.row.date }
    }

    /** The payment a filed outflow records: the draft with the bank row's own day and amount. */
    private fun recorded(rr: ReviewRow): PaymentDraft? = rr.draft?.copy(date = rr.row.date, total = -rr.row.amount)

    /**
     * Whether the review's Confirm is on (SPEC R107): every ticked outflow is either a payment
     * whose parts add up and whose source exists (SPEC R23, R54) or a payoff of an Account that
     * exists, and none is sent into a Goal; a ticked row of money in filed as a refund is a
     * payment whose parts add up, sent into no Goal (SPEC R21). A Goal short of a row is no reason
     * to refuse: Savings pays the rest, after the warning of [rowNotices] (SPEC R54).
     */
    fun canConfirm(d: ProfileData, rows: List<ReviewRow>): Boolean = walk(d, rows).first

    /**
     * The warning code of each row, in the order given, or null: the payment editor's warning
     * (Payments.draftNotice) for a ticked row filed as a payment, worked out against what the
     * ticked rows recorded before it hold, in the order [confirm] records them: [PaymentNotice.GOAL_SHORT]
     * for a Goal that holds less than it (SPEC R54, R107), [PaymentNotice.CHARGES_NOT_LOGGED] for a
     * card whose charges are not logged (SPEC R109).
     */
    fun rowNotices(d: ProfileData, rows: List<ReviewRow>): List<PaymentNotice?> = walk(d, rows).second

    /** Checks the rows by recording them on a copy, as [confirm] does, with a fixed clock and throwaway ids; the copy is dropped. */
    private fun walk(d: ProfileData, rows: List<ReviewRow>): Pair<Boolean, List<PaymentNotice?>> {
        var n = 0
        val run = run(d, rows, SIMULATION_CLOCK) { "check-${++n}" }
        return run.ok to run.notices
    }

    /** The clock a check records with: what it writes is dropped, so its instant never reaches the data. */
    private object SIMULATION_CLOCK : Clock {
        override fun today(): LocalDate = LocalDate(1970, 1, 1)
        @OptIn(kotlin.time.ExperimentalTime::class)
        override fun now() = kotlin.time.Instant.fromEpochMilliseconds(0)
    }

    /** What a run of the ticked rows came to: whether every row could be recorded, each row's notice, and the state written. */
    private class Run(val ok: Boolean, val notices: List<PaymentNotice?>, val state: ProfileData)

    /**
     * The order Confirm records the rows in (SPEC R107): oldest first, rows of one day in the
     * order the review lists them, so a charge is recorded before a later payoff that depends on
     * it (SPEC R89) and a Goal is filled before a later row takes from it. Indices into [rows].
     */
    private fun recordOrder(rows: List<ReviewRow>): List<Int> = rows.indices.sortedBy { rows[it].row.date }

    /**
     * Records every ticked row in [recordOrder] against the state as it builds, checking each
     * against what the rows before it recorded: [confirm] keeps the state, [canConfirm] and
     * [rowNotices] only the verdict and the notices, so the three never disagree. A row that
     * cannot be recorded is skipped and the run marked not ok.
     */
    private fun run(d: ProfileData, rows: List<ReviewRow>, clock: Clock, ids: () -> String): Run {
        var next = d
        var ok = true
        val notices = MutableList<PaymentNotice?>(rows.size) { null }
        var rules = d.importState.payeeRules
        val keys = d.importState.importedKeys.toMutableList()
        val since = pairedSince(d, rows)
        for (i in recordOrder(rows)) {
            val rr = rows[i]
            // a row named as a receipt's pair is recorded by pairing, never as new spending, and a row
            // a payment took the keys of since the review was built, paired by hand, is recorded already (SPEC R107, R118, R119)
            if (!rr.include || rr.pairPaymentId != null || i in since) continue
            val r = rr.row
            val amt = abs(r.amount)
            val payoff = rr.payoffAccountId
            val draft = recorded(rr)
            /** Records [draft] as a payment with the row's keys. */
            fun pay(dr: PaymentDraft): ProfileData {
                val id = ids()
                val written = Payments.record(next, dr, clock, id)
                return written.copy(payments = written.payments.map { if (it.id == id) it.copy(bankKeys = rr.keys) else it })
            }
            val written: ProfileData? = when {
                // a refund: a payment of minus the row's amount, with its keys; it teaches no rule (SPEC R21, R107, R108)
                r.amount > 0 && draft != null ->
                    if (rr.incomingGoalId != null || payoff != null || !Payments.canSave(next, draft)) null else {
                        // the payment editor's warning, as for any payment (SPEC R109)
                        notices[i] = Payments.draftNotice(next, draft)
                        pay(draft)
                    }
                r.amount > 0 -> {
                    val saved = Writes.savingsQuickAdd(next, amt, r.description, false, clock, ids(), r.date)
                    // a Goal deleted while the review was open leaves the money in Savings
                    val goal = rr.incomingGoalId?.takeIf { id -> saved.goals.any { it.id == id } }
                    if (goal == null) saved else Writes.goalQuickAdd(saved, goal, amt, r.description, clock, ids(), r.date)
                }
                rr.incomingGoalId != null -> null
                payoff != null && draft == null -> {
                    val acct = next.accounts.firstOrNull { it.id == payoff }
                    // R89's default: a linked Account's sweep already took the money from Savings
                    val from = acct?.let { rr.payoffFrom ?: Accounts.payoffFromDefault(it) }
                    val goalHolds = (from as? Accounts.PayoffFrom.Goal)?.let { f -> next.goals.firstOrNull { it.id == f.id }?.saved }
                    when {
                        acct == null || from == null -> null
                        // a Loan's payoff that counts as spending needs a category (SPEC R89)
                        !Accounts.canPayOff(next, acct, from, rr.payoffCategoryId) -> null
                        // a payoff from a Goal takes at most what it holds after the rows before it (SPEC R89)
                        from is Accounts.PayoffFrom.Goal && (goalHolds == null || amt > goalHolds) -> null
                        else -> Accounts.quickAdd(next, payoff, amt, Accounts.Direction.PAYMENT, r.description, from, clock, r.date, rr.payoffCategoryId, ids = ids)
                    }
                }
                draft != null && payoff == null -> if (!Payments.canSave(next, draft)) null else {
                    // a Goal short of it warns, Savings paying the rest (SPEC R54)
                    notices[i] = Payments.draftNotice(next, draft)
                    val categories = draft.parts.mapNotNull { it.categoryId }.distinct()
                    val name = payeeRuleName(r.description)
                    if (categories.size == 1 && name.isNotEmpty()) rules = rules + (name to categories.single())
                    pay(draft)
                }
                else -> null
            }
            if (written == null) { ok = false; continue }
            next = written
            // every recorded row kept once, so twins are counted (SPEC R26)
            keys.addAll(keyStrings(rr.keys))
        }
        val state = next.copy(importState = next.importState.copy(importedKeys = keys, payeeRules = rules, lastImportDate = clock.today()))
        return Run(ok, notices, state)
    }

    /**
     * The rows of [rows] paired since the review was built (SPEC R119), indices: each payment holding
     * a row's keys now that held none of them then ([ReviewRow.heldAtReview]) took them since, and
     * stands for one row only, so a twin row with the same keys, as two equal coffees on one day
     * with no FITID, is still recorded. A payment's keys and a row's are of one
     * row by [sameRow]: their FITIDs when both have one. Each payment takes the row naming it as
     * its pair, else a row with the very keys it holds, else the first row of the same keys. A
     * row that arrived DUPLICATE is recorded when ticked (SPEC R107). It runs in every check of the
     * review, so the rows that did not arrive DUPLICATE are indexed once by FITID and by synthetic
     * key, and only payments whose keys meet the index are looked at.
     */
    internal fun pairedSince(d: ProfileData, rows: List<ReviewRow>): Set<Int> {
        val byFit = HashMap<String, MutableList<Int>>()
        val bySyn = HashMap<String, MutableList<Int>>()
        for (i in rows.indices) {
            val k = rows[i].keys
            if (rows[i].status == RowStatus.DUPLICATE) continue
            k.fitid?.let { byFit.getOrPut(it) { ArrayList() }.add(i) }
            bySyn.getOrPut(k.synKey) { ArrayList() }.add(i)
        }
        val before = rows.flatMapTo(HashSet()) { it.heldAtReview }
        val taken = HashSet<Int>()
        for (p in d.payments) {
            val held = p.bankKeys ?: continue
            // a payment that held the keys when the review was built stands for a row recorded before it
            if (p.id in before) continue
            val bySynHeld = bySyn[held.synKey].orEmpty()
            // [sameRow]: a FITID meets rows of that FITID and rows with none sharing the synthetic key
            val same = if (held.fitid == null) bySynHeld else (byFit[held.fitid].orEmpty() + bySynHeld.filter { rows[it].keys.fitid == null }).sorted()
            if (same.isEmpty()) continue
            val open = same.filter { it !in taken }
            val pick = open.firstOrNull { rows[it].pairPaymentId == p.id } ?: open.firstOrNull { rows[it].keys == held } ?: open.firstOrNull() ?: continue
            taken += pick
        }
        return taken
    }

    /**
     * What the Confirm names against [d] as it stands (SPEC R107): the ticked rows' count, the money out
     * and the money in, each positive; a row naming a pair (SPEC R118), or one paired by hand since the
     * review was built (SPEC R119), is not counted.
     */
    fun confirmSummary(d: ProfileData, rows: List<ReviewRow>): ConfirmSummary {
        val since = pairedSince(d, rows)
        val ticked = rows.filterIndexed { i, it -> it.include && it.pairPaymentId == null && i !in since }
        return ConfirmSummary(
            count = ticked.size,
            moneyOut = ticked.filter { it.row.amount < 0 }.sumOf { -it.row.amount },
            moneyIn = ticked.filter { it.row.amount > 0 }.sumOf { it.row.amount },
        )
    }

    /**
     * Records every ticked row with the statement's day (SPEC R107): money in to Savings and on
     * into the Goal it was sent to, if that Goal still exists (SPEC R50), or as a refund; an
     * outflow as its payment with the row's keys (SPEC R21, R26) or as a payoff taking its money
     * from where the row says, else R89's default (SPEC R89, R107). A row filed under one category
     * teaches its payee that category (SPEC R108); every recorded row's keys are kept. Rows are
     * recorded oldest first, one day's in the review's order (SPEC R107).
     */
    fun confirm(d: ProfileData, rows: List<ReviewRow>, clock: Clock, ids: () -> String): ProfileData {
        require(canConfirm(d, rows)) { "every ticked outflow needs a payment that adds up or a payoff (SPEC R107)" }
        val run = run(d, rows, clock, ids)
        check(run.ok) { "a row the check let through could not be recorded" }
        return run.state
    }
}
