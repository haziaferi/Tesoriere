package com.hazia.tesoriere.statements

import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.payments.BankKeys
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
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
 *   Sci-Fin did through Accounts.quickAdd, but with R89's default for the Savings pair, off for an
 *   Account linked to a recurring expense, and [ReviewRow.payoffPairSavings] to change it per row
 *   (SPEC R89, R107); Sci-Fin always paired.
 * - Money in goes to Savings, and on into a Goal when [ReviewRow.incomingGoalId] names one, as
 *   Sci-Fin's `goal:<id>` target did: a `savings` row, then a goal quick-add of the same amount on
 *   the same day; a Goal deleted before Confirm leaves the money in Savings (SPEC R107).
 * - Keys are a BankKeys (SPEC R26), and duplicates are also found against every payment's
 *   bankKeys, so a bank row recorded through a pair is not offered again.
 * - A payee rule is learned from a row filed under one category, from any source; a split row
 *   teaches nothing (SPEC R108). Sci-Fin learned from a `cat:` target only. A rule pre-fills a
 *   one-part draft from Savings.
 * - canConfirm() replaces `unassigned()`: it also checks that a draft's parts add up (SPEC R23),
 *   and an outflow sent into a Goal holds it back, as Sci-Fin's `goal:` target on an outflow did.
 *   confirmSummary() gives the count and the money out and in apart, the figures of Sci-Fin's
 *   `importSummary()` line, as figures with no sentence (SPEC R16, R107).
 * - No wording: Sci-Fin's review notes are dropped (the status is the code, SPEC R16), and its
 *   target lists with their labels (`inflowTargets`, `outflowTargets`) are the design pass's.
 * - Names: ImportRow is BankRow, ImportStatus is RowStatus (with PAIR, set by pairing, SPEC R37),
 *   annotate() is review(), parseCSV/parseOFX are parseCsv/parseOfx.
 */

/** One parsed statement row. [amount] is signed: negative is money out. */
data class BankRow(val date: LocalDate, val amount: Money, val description: String, val fitid: String?)

/** What the review thinks of a row (SPEC R107). PAIR is set by pairing with a receipt (SPEC R37), never here. */
enum class RowStatus { READY, INCOMING, DUPLICATE, RECURRING, PAIR }

/**
 * A row in the review: what the app thinks, and what the user starts with. A ticked outflow is
 * recorded through [draft] (a payment, SPEC R21) or [payoffAccountId] (a payment off an Account,
 * SPEC R89), exactly one of them; both are null until the user files it or a payee rule
 * pre-fills a draft (SPEC R108). Money in uses neither: it goes to Savings, and on into the Goal
 * [incomingGoalId] when the user picks one (SPEC R107). [pairPaymentId] is filled by pairing
 * (SPEC R37).
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
     * Whether a payoff of [payoffAccountId] also lowers Savings (SPEC R89, R107): null takes R89's
     * default, on unless the Account is linked to a recurring expense; the user can set it per row.
     */
    val payoffPairSavings: Boolean? = null,
)

/**
 * What the review's Confirm names (SPEC R107): how many ticked rows it records, and the money out
 * and the money in apart, each a positive figure. How it reads is the design pass's (SPEC R16).
 */
data class ConfirmSummary(val count: Int, val moneyOut: Money, val moneyIn: Money)

object StatementImport {

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

    /** Both keys where the format allows (SPEC R26): the FITID is identity, the synthetic key catches a CSV and an OFX of one period. */
    fun keys(r: BankRow): BankKeys =
        BankKeys(r.fitid, "syn:${r.date}|${r.amount.toMajor2()}|${r.description.take(40).lowercase()}")

    /** [k] as the strings kept in `ImportState.importedKeys`: `fit:<FITID>` when there is one, then the synthetic key. */
    fun keyStrings(k: BankKeys): List<String> = listOfNotNull(k.fitid?.let { "fit:$it" }, k.synKey)

    private fun Money.toMajor2(): String {
        val a = abs(this)
        return (if (this < 0) "-" else "") + "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }

    /** A row's payee (SPEC R108): lower case, anything but letters, digits and spaces read as a space, runs of spaces made one. */
    fun normalisePayee(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The payment an outflow [row] becomes: its day, its amount as the total, its description as the payee. */
    fun draft(row: BankRow, parts: List<Part>, source: PaymentSource = PaymentSource.Savings): PaymentDraft {
        require(row.amount < 0) { "only money out is a payment" }
        return PaymentDraft(row.date, -row.amount, source, row.description, parts)
    }

    /**
     * Annotates every row with what the Profile already knows (SPEC R107), newest first. Nothing
     * here writes. [today] is not read here; pairing (SPEC R37) uses it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun review(d: ProfileData, rows: List<BankRow>, today: LocalDate): List<ReviewRow> {
        val seen = d.importState.importedKeys.toHashSet()
        for (p in d.payments) p.bankKeys?.let { seen.addAll(keyStrings(it)) }
        val recurring = d.recurringExpenses.map { words(normalisePayee(it.name)) }.filter { it.isNotEmpty() }
        return rows.map { r ->
            val keys = keys(r)
            val payee = normalisePayee(r.description)
            val pw = words(payee)
            val status = when {
                keyStrings(keys).any { it in seen } -> RowStatus.DUPLICATE
                r.amount > 0 -> RowStatus.INCOMING
                // whole words either way round: "Rent" is in "Affitto casa rent", not in "Parent teacher assoc"
                pw.isNotEmpty() && recurring.any { nw -> pw.windowed(nw.size).any { it == nw } || nw.windowed(pw.size).any { it == pw } } ->
                    RowStatus.RECURRING
                else -> RowStatus.READY
            }
            // a category is never guessed: only a rule the user taught pre-fills one (SPEC R108)
            val rule = if (r.amount < 0) d.importState.payeeRules[payee]?.takeIf { id -> d.categories.any { it.id == id } } else null
            ReviewRow(
                row = r, keys = keys, payee = payee, status = status,
                include = status == RowStatus.READY || status == RowStatus.INCOMING,
                draft = rule?.let { draft(r, listOf(Part(it, -r.amount))) },
                payoffAccountId = null, pairPaymentId = null,
            )
        }.sortedByDescending { it.row.date }
    }

    /** The payment a filed outflow records: the draft with the bank row's own day and amount. */
    private fun recorded(rr: ReviewRow): PaymentDraft? = rr.draft?.copy(date = rr.row.date, total = -rr.row.amount)

    /**
     * Whether the review's Confirm is on (SPEC R107): every ticked outflow is either a payment
     * whose parts add up and whose source exists (SPEC R23, R54) or a payoff of an Account that
     * exists, and none is sent into a Goal. Rows paying from the same Goal are taken in turn, as
     * [confirm] writes them, so two that together pay more than it holds are caught here.
     */
    fun canConfirm(d: ProfileData, rows: List<ReviewRow>): Boolean {
        val holds = d.goals.associate { it.id to it.saved }.toMutableMap()
        for (rr in rows) {
            if (!rr.include || rr.row.amount >= 0) continue
            if (rr.incomingGoalId != null) return false
            val payoff = rr.payoffAccountId
            val draft = recorded(rr)
            when {
                payoff != null && draft == null -> if (d.accounts.none { it.id == payoff }) return false
                draft != null && payoff == null -> {
                    if (!Payments.canSave(d, draft)) return false
                    val s = draft.source
                    if (s is PaymentSource.Goal) {
                        val left = holds[s.id] ?: return false
                        if (draft.total > left) return false
                        holds[s.id] = left - draft.total
                    }
                }
                else -> return false
            }
        }
        return true
    }

    /** What the Confirm names (SPEC R107): the ticked rows' count, the money out and the money in, each positive. */
    fun confirmSummary(rows: List<ReviewRow>): ConfirmSummary {
        val ticked = rows.filter { it.include }
        return ConfirmSummary(
            count = ticked.size,
            moneyOut = ticked.filter { it.row.amount < 0 }.sumOf { -it.row.amount },
            moneyIn = ticked.filter { it.row.amount > 0 }.sumOf { it.row.amount },
        )
    }

    /**
     * Records every ticked row with the statement's day (SPEC R107): money in to Savings and on
     * into the Goal it was sent to, if that Goal still exists (SPEC R50), an outflow as its payment with the row's keys (SPEC R21, R26) or as a payoff with the Savings
     * pair by R89's default unless the row sets it (SPEC R89, R107). A row filed under one category teaches its payee that category (SPEC R108);
     * every recorded row's keys are kept.
     */
    fun confirm(d: ProfileData, rows: List<ReviewRow>, clock: Clock, ids: () -> String): ProfileData {
        require(canConfirm(d, rows)) { "every ticked outflow needs a payment that adds up or a payoff (SPEC R107)" }
        var next = d
        var rules = d.importState.payeeRules
        val keys = d.importState.importedKeys.toMutableList()
        for (rr in rows.filter { it.include }) {
            val r = rr.row
            val amt = abs(r.amount)
            val payoff = rr.payoffAccountId
            next = when {
                r.amount > 0 -> {
                    val saved = Writes.savingsQuickAdd(next, amt, r.description, false, clock, ids(), r.date)
                    // a Goal deleted while the review was open leaves the money in Savings
                    val goal = rr.incomingGoalId?.takeIf { id -> saved.goals.any { it.id == id } }
                    if (goal == null) saved else Writes.goalQuickAdd(saved, goal, amt, r.description, clock, ids(), r.date)
                }
                payoff != null -> {
                    // R89's default: a linked Account's sweep already took the money from Savings
                    val pair = rr.payoffPairSavings ?: Accounts.pairedOutflowDefault(next.accounts.first { it.id == payoff })
                    Accounts.quickAdd(next, payoff, amt, Accounts.Direction.PAYMENT, r.description, pair, clock, r.date, ids = ids)
                }
                else -> {
                    val draft = requireNotNull(recorded(rr))
                    val categories = draft.parts.mapNotNull { it.categoryId }.distinct()
                    if (categories.size == 1 && rr.payee.isNotEmpty()) rules = rules + (rr.payee to categories.single())
                    val id = ids()
                    val written = Payments.record(next, draft, clock, id)
                    written.copy(payments = written.payments.map { if (it.id == id) it.copy(bankKeys = rr.keys) else it })
                }
            }
            for (k in keyStrings(rr.keys)) if (k !in keys) keys.add(k)
        }
        return next.copy(importState = next.importState.copy(importedKeys = keys, payeeRules = rules, lastImportDate = clock.today()))
    }
}
