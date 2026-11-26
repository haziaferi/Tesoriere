@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.hazia.tesoriere.pairing

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.daysTo
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.receipts.ReceiptFile
import com.hazia.tesoriere.receipts.ReceiptProblem
import com.hazia.tesoriere.receipts.ReceiptReview
import com.hazia.tesoriere.receipts.Receipts
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.ReviewRow
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts and no pairs. A receipt and a bank row of
 * one purchase become one payment (SPEC R36-R40, R118-R120): whichever arrived first was recorded
 * as a payment, and pairing edits that payment, through Payments.edit, into the pair. Nothing is
 * paired without a call to [Pairing.pairBankWithPayment] or [Pairing.pairReceiptWithPayment]
 * (SPEC R37); a suggestion only names a payment. [Pairing.unpair] turns a pair back into the
 * receipt's payment alone, its bank row free to arrive again (SPEC R128).
 */

/** The most a pair's totals may differ and still be suggested, in cents (SPEC R37). */
const val PAIR_TOLERANCE: Money = 50

/** How many days after the receipt a bank row may be dated and still be suggested (SPEC R37). */
const val PAIR_DAYS_AFTER = 3

/** How a receipt and a bank row meet R37's table. */
enum class Match { EXACT, WITH_DIFFERENCE }

/** Why a pair cannot be recorded, as a code, first match in this order (SPEC R119). */
enum class PairProblem {
    /** The payment does not exist, is a refund, or holds already what this side would bring. */
    PAYMENT_NOT_PAIRABLE,
    /** The bank row is money in (SPEC R118). */
    ROW_NOT_PAIRABLE,
    /** The bank row is recorded already (SPEC R26). */
    ROW_RECORDED,
    /** The receipt's own review cannot be confirmed: see Receipts.problem (SPEC R115). */
    RECEIPT_PROBLEM,
    /** A difference other than zero with no category picked (SPEC R39). */
    DIFFERENCE_UNFILED,
    /** The paired payment could not be saved: a category that does not exist, a source that does not exist (SPEC R23, R54). */
    CANNOT_SAVE,
}

/** Why a payment cannot be unpaired, as a code (SPEC R128). */
enum class UnpairProblem {
    /** The payment does not exist, or does not hold both a receipt and bank keys. */
    NOT_A_PAIR,
    /** The receipt's payment alone could not be saved: a line's category deleted since (SPEC R23). */
    CANNOT_SAVE,
}

object Pairing {

    // --- unpairing (SPEC R128) ---

    /**
     * What pair [paymentId] becomes unpaired (SPEC R128): the receipt's payment alone, its total the
     * sum of its lines' price paid, one part per category its lines are filed under now adding up to
     * other than zero, its day, source and payee kept; null for a payment that is not a pair.
     */
    fun unpairDraft(d: ProfileData, paymentId: String): PaymentDraft? {
        val p = d.payments.firstOrNull { it.id == paymentId } ?: return null
        if (p.receiptId == null || p.bankKeys == null || p.receiptLines.isEmpty()) return null
        val sums = LinkedHashMap<String, Money>()
        for (l in p.receiptLines) sums[l.categoryId] = (sums[l.categoryId] ?: 0L) + l.pricePaid
        val parts = sums.filter { it.value != 0L }.map { (c, amount) -> Part(c, amount) }
        return PaymentDraft(p.date, p.receiptLines.sumOf { it.pricePaid }, p.source, p.payee, parts)
    }

    /** Why [paymentId] cannot be unpaired, or null (SPEC R128). */
    fun unpairProblem(d: ProfileData, paymentId: String): UnpairProblem? {
        val draft = unpairDraft(d, paymentId) ?: return UnpairProblem.NOT_A_PAIR
        return UnpairProblem.CANNOT_SAVE.takeIf { !Payments.canSave(d, draft, paymentId) }
    }

    /** The warning the receipt's payment alone would carry, as for any payment edit (SPEC R54, R109); null for none. */
    fun unpairNotice(d: ProfileData, paymentId: String): PaymentNotice? =
        unpairDraft(d, paymentId)?.let { Payments.draftNotice(d, it, editing = paymentId) }

    /**
     * Unpairs [paymentId] (SPEC R128): Payments.edit makes it the receipt's payment alone, and its
     * bank keys leave it and the import's keys, so its row arrives as new on the next import (SPEC
     * R26). The receipt id stays recorded and the store link stays (SPEC R36). [txId] is the ledger
     * row a deleted source's edit may write (SPEC R54). While [unpairProblem] finds one, [d] comes
     * back unchanged.
     */
    fun unpair(d: ProfileData, paymentId: String, clock: Clock, txId: String): ProfileData {
        if (unpairProblem(d, paymentId) != null) return d
        val keys = StatementImport.keyStrings(d.payments.first { it.id == paymentId }.bankKeys!!).toSet()
        val edited = Payments.edit(d, paymentId, unpairDraft(d, paymentId)!!, clock, txId)
        return edited.copy(
            payments = edited.payments.map { if (it.id == paymentId) it.copy(bankKeys = null) else it },
            importState = edited.importState.copy(importedKeys = edited.importState.importedKeys.filter { it !in keys }),
        )
    }

    /**
     * How a receipt of [receiptTotal] on [receiptDate] meets bank row [bank] (SPEC R37), or null:
     * money out of the same amount, or within [PAIR_TOLERANCE] of it, dated the receipt's day or up
     * to [PAIR_DAYS_AFTER] days after. A row of money in never matches (SPEC R118).
     */
    fun match(receiptDate: LocalDate, receiptTotal: Money, bank: BankRow): Match? {
        if (bank.amount >= 0) return null
        val days = receiptDate.daysTo(bank.date)
        if (days < 0 || days > PAIR_DAYS_AFTER) return null
        val gap = abs(-bank.amount - receiptTotal)
        return when {
            gap == 0L -> Match.EXACT
            gap <= PAIR_TOLERANCE -> Match.WITH_DIFFERENCE
            else -> null
        }
    }

    /**
     * The rows of [rows] a receipt of [store] would be suggested with (SPEC R37, R118), best first in
     * R118's order: a row matching the store's link, then equal totals, then the closest day, then
     * the statement's order. A link ranks and never hides (SPEC R36, R120).
     */
    fun candidates(d: ProfileData, store: String, receiptDate: LocalDate, receiptTotal: Money, rows: List<BankRow>): List<Pair<BankRow, Match>> =
        rows.mapNotNull { r -> match(receiptDate, receiptTotal, r)?.let { r to it } }
            .sortedWith(compareBy({ rank(d, store, it.first.description) }, { it.second.ordinal }, { abs(receiptDate.daysTo(it.first.date)) }))

    /**
     * The payments recorded from a bank row, holding no receipt, that receipt [file] would be
     * suggested with (SPEC R118), best first: each read as its bank row, its day and its total.
     */
    fun paymentCandidates(d: ProfileData, file: ReceiptFile): List<Pair<String, Match>> =
        d.payments.withIndex().filter { bankSide(it.value) }
            .mapNotNull { (i, p) -> match(file.date, file.totalPaid, BankRow(p.date, -p.total, p.payee ?: "", p.bankKeys?.fitid))?.let { Triple(i, p, it) } }
            .sortedWith(compareBy({ rank(d, file.store, it.second.payee ?: "") }, { it.third.ordinal }, { abs(file.date.daysTo(it.second.date)) }, { it.first }))
            .map { it.second.id to it.third }

    /**
     * The receipt payments, holding no bank keys, that bank row [row] would be suggested with (SPEC
     * R118, R127), best first in R118's order: the store's link, equal totals, the closest day, the
     * payment recorded first.
     */
    fun receiptCandidates(d: ProfileData, row: BankRow): List<Pair<String, Match>> =
        d.payments.withIndex().filter { receiptSide(it.value) }
            .mapNotNull { (i, p) -> match(p.date, p.total, row)?.let { Triple(i, p, it) } }
            .sortedWith(compareBy({ rank(d, it.second.payee ?: "", row.description) }, { it.third.ordinal }, { abs(it.second.date.daysTo(row.date)) }, { it.first }))
            .map { it.second.id to it.third }

    /**
     * Which payment each row of [rows] names in the import review (SPEC R118): every option, a row
     * in [eligible] (money out, not recorded yet) and a receipt payment that [match] finds, taken in
     * R118's order (the store's link, equal totals, the closest day, the earlier row, the row listed
     * first, the payment recorded first), is named when its row and payment are free and naming it
     * still lets as many rows as possible name a payment. The result names the most pairs any
     * choice can, and among those is the best in R118's order. Indices into [rows] to payment ids.
     */
    internal fun suggest(d: ProfileData, rows: List<BankRow>, eligible: Set<Int>): Map<Int, String> {
        class Option(val row: Int, val payment: Int, val match: Match)
        val payments = d.payments
        val options = eligible.flatMap { i ->
            payments.indices.filter { receiptSide(payments[it]) }.mapNotNull { j ->
                match(payments[j].date, payments[j].total, rows[i])?.let { Option(i, j, it) }
            }
        }.sortedWith(compareBy(
            { rank(d, payments[it.payment].payee ?: "", rows[it.row].description) }, { it.match.ordinal },
            { abs(payments[it.payment].date.daysTo(rows[it.row].date)) }, { rows[it.row].date }, { it.row }, { it.payment },
        ))
        // the most pairs any choice names, then each option in R118's order, kept when its row and
        // payment are free and the rest can still reach that count
        val best = maxPairs(options.map { it.row to it.payment })
        val kept = ArrayList<Option>()
        for (o in options) {
            if (kept.any { it.row == o.row || it.payment == o.payment }) continue
            val rest = options.filter { r -> (kept + o).none { it.row == r.row || it.payment == r.payment } }
            if (kept.size + 1 + maxPairs(rest.map { it.row to it.payment }) == best) kept += o
        }
        return kept.associate { it.row to payments[it.payment].id }
    }

    /** The most pairs [edges] (row, payment) can name, each row and each payment once at most (Kuhn's matching). */
    private fun maxPairs(edges: List<Pair<Int, Int>>): Int {
        val byRow = edges.groupBy({ it.first }, { it.second })
        val holder = HashMap<Int, Int>()
        fun augment(row: Int, seen: MutableSet<Int>): Boolean {
            for (p in byRow.getValue(row)) {
                if (!seen.add(p)) continue
                val other = holder[p]
                if (other == null || augment(other, seen)) { holder[p] = row; return true }
            }
            return false
        }
        return byRow.keys.count { augment(it, HashSet()) }
    }

    /** The difference part of a pair (SPEC R39): the bank amount less the receipt's total, negative when the bank row is below it. */
    fun differencePart(receiptTotal: Money, bankAmount: Money): Money = bankAmount - receiptTotal

    /** Whether a pair with [difference] can be recorded: nothing to file, or a category picked for it (SPEC R39). */
    fun canConfirmPair(difference: Money, differenceCategoryId: String?): Boolean = difference == 0L || differenceCategoryId != null

    // --- receipt first, then its bank row (SPEC R38 row 1) ---

    /** What the receipt's payment [paymentId] becomes when paired with bank row [row] (SPEC R119); null for an unknown id. */
    fun bankPairDraft(d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?): PaymentDraft? {
        val p = d.payments.firstOrNull { it.id == paymentId } ?: return null
        val bank = -row.row.amount
        return PaymentDraft(p.date, bank, p.source, p.payee, withDifference(p.parts.filter { !it.difference }, differencePart(p.total, bank), differenceCategoryId))
    }

    /**
     * Why receipt payment [paymentId] and bank row [row] cannot be paired, or null (SPEC R119).
     * [review] is the import review [row] is in, when it is: a payment holding the synthetic key
     * of [row] and of its twins there stands for one of them only (StatementImport.rowRecorded).
     */
    fun bankPairProblem(
        d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?, review: List<ReviewRow> = emptyList(),
    ): PairProblem? {
        val p = d.payments.firstOrNull { it.id == paymentId }
        if (p == null || !receiptSide(p)) return PairProblem.PAYMENT_NOT_PAIRABLE
        if (row.row.amount >= 0) return PairProblem.ROW_NOT_PAIRABLE
        if (StatementImport.rowRecorded(d, row, review)) return PairProblem.ROW_RECORDED
        if (!canConfirmPair(differencePart(p.total, -row.row.amount), differenceCategoryId)) return PairProblem.DIFFERENCE_UNFILED
        val draft = bankPairDraft(d, paymentId, row, differenceCategoryId) ?: return PairProblem.PAYMENT_NOT_PAIRABLE
        if (!Payments.canSave(d, draft, paymentId, pairing = true)) return PairProblem.CANNOT_SAVE
        return null
    }

    /** The warning the paired payment would carry, as for any payment edit (SPEC R54, R109); null for an unknown id. */
    fun bankPairNotice(d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?): PaymentNotice? =
        bankPairDraft(d, paymentId, row, differenceCategoryId)?.let { Payments.draftNotice(d, it, editing = paymentId) }

    /**
     * Pairs receipt payment [paymentId] with bank row [row], suggested or by hand (SPEC R38, R40,
     * R119): Payments.edit gives it the bank amount as its total and the difference part, keeping
     * its day and source; it gains the row's keys, which the import keeps too (SPEC R26), and the
     * store's link the first time the store pairs (SPEC R120). [txId] is the ledger row a deleted
     * source's edit may write (SPEC R54); [review] is as for [bankPairProblem].
     */
    fun pairBankWithPayment(
        d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?, clock: Clock, txId: String,
        review: List<ReviewRow> = emptyList(),
    ): ProfileData {
        val problem = bankPairProblem(d, paymentId, row, differenceCategoryId, review)
        require(problem == null) { "the pair cannot be recorded: $problem (SPEC R119)" }
        val store = d.payments.first { it.id == paymentId }.payee
        val edited = Payments.edit(d, paymentId, bankPairDraft(d, paymentId, row, differenceCategoryId)!!, clock, txId, pairing = true)
        return linkStore(withKeys(edited, paymentId, row), store, row.row.description)
    }

    // --- bank row first, then its receipt (SPEC R38 row 2) ---

    /**
     * What bank payment [paymentId] becomes when paired with the receipt under [review] (SPEC R119);
     * null for an unknown id. [receiptParts], when given, are the receipt side's own parts, kept by
     * a restore from the Trash in place of its lines' categories, and its total is theirs (SPEC R127).
     */
    fun receiptPairDraft(
        d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?, receiptParts: List<Part>? = null,
    ): PaymentDraft? {
        val p = d.payments.firstOrNull { it.id == paymentId } ?: return null
        val f = review.file
        val parts = receiptParts ?: Receipts.parts(review)
        return PaymentDraft(f.date, p.total, pairSource(d, p, review), f.store, withDifference(parts, differencePart(receiptTotal(review, receiptParts), p.total), differenceCategoryId))
    }

    /**
     * The source of a pair whose bank row came first (SPEC R35, R119): the review's, unless the
     * store has no source kept (or its Goal or Account is gone) and the review's source was not
     * picked, Savings only because R113 opened it so: then bank payment [p]'s own, the card it was
     * charged to say.
     */
    private fun pairSource(d: ProfileData, p: Payment, review: ReceiptReview): PaymentSource =
        if (!review.sourcePicked && Receipts.keptSource(d, review.file.store) == null) p.source else review.source

    /** The receipt side's total: its own parts' when given (SPEC R127), else the receipt's. */
    private fun receiptTotal(review: ReceiptReview, receiptParts: List<Part>?): Money = receiptParts?.sumOf { it.amount } ?: review.file.totalPaid

    /** Why bank payment [paymentId] and the receipt under [review] cannot be paired, or null (SPEC R119). */
    fun receiptPairProblem(
        d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?, receiptParts: List<Part>? = null,
    ): PairProblem? {
        val p = d.payments.firstOrNull { it.id == paymentId }
        if (p == null || !bankSide(p)) return PairProblem.PAYMENT_NOT_PAIRABLE
        // the receipt's own checks, but its own total: the pair's total is the bank row's
        val rp = Receipts.problem(d, review)
        if (rp != null && rp != ReceiptProblem.CANNOT_SAVE) return PairProblem.RECEIPT_PROBLEM
        if (!canConfirmPair(differencePart(receiptTotal(review, receiptParts), p.total), differenceCategoryId)) return PairProblem.DIFFERENCE_UNFILED
        val base = Receipts.withAdded(d, review)
        val draft = receiptPairDraft(d, paymentId, review, differenceCategoryId, receiptParts) ?: return PairProblem.PAYMENT_NOT_PAIRABLE
        if (!Payments.canSave(base, draft, paymentId, pairing = true)) return PairProblem.CANNOT_SAVE
        return null
    }

    /** The warning the paired payment would carry (SPEC R54, R109); null for an unknown id. */
    fun receiptPairNotice(
        d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?, receiptParts: List<Part>? = null,
    ): PaymentNotice? =
        receiptPairDraft(d, paymentId, review, differenceCategoryId, receiptParts)?.let { Payments.draftNotice(Receipts.withAdded(d, review), it, editing = paymentId) }

    /**
     * Pairs bank payment [paymentId] with the receipt under [review], suggested or by hand (SPEC
     * R38, R40, R119): Payments.edit gives it the receipt's parts, day, source and store, with the
     * difference part, keeping its total and keys, its source as [pairSource] has it; then the
     * receipt is kept as its Confirm keeps it (SPEC R115), the store keeping the pair's source, and
     * the store's link made the first time the store pairs (SPEC R120).
     */
    fun pairReceiptWithPayment(
        d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?, clock: Clock, txId: String,
        receiptParts: List<Part>? = null,
    ): ProfileData {
        val problem = receiptPairProblem(d, paymentId, review, differenceCategoryId, receiptParts)
        require(problem == null) { "the pair cannot be recorded: $problem (SPEC R119)" }
        val bankText = d.payments.first { it.id == paymentId }.payee
        val base = Receipts.withAdded(d, review)
        val draft = receiptPairDraft(d, paymentId, review, differenceCategoryId, receiptParts)!!
        val edited = Payments.edit(base, paymentId, draft, clock, txId, pairing = true)
        return linkStore(Receipts.keep(d, edited, review.copy(source = draft.source), paymentId), review.file.store, bankText)
    }

    // --- shared ---

    /** A payment a bank row can complete: from a receipt, with no bank keys, a purchase. */
    private fun receiptSide(p: Payment) = p.receiptId != null && p.bankKeys == null && p.total > 0

    /** A payment a receipt can complete: from a bank row, with no receipt, a purchase. */
    private fun bankSide(p: Payment) = p.bankKeys != null && p.receiptId == null && p.total > 0

    /** 0 for a bank text that matches [store]'s link, 1 otherwise: a link ranks, never hides (SPEC R36, R120). */
    private fun rank(d: ProfileData, store: String, description: String): Int {
        val link = d.receiptSettings?.storeLinks?.get(store) ?: return 1
        return if (StatementImport.payeeRuleName(description) == link) 0 else 1
    }

    private fun withDifference(parts: List<Part>, difference: Money, categoryId: String?): List<Part> =
        if (difference == 0L) parts else parts + Part(categoryId, difference, difference = true)

    /** The row's keys on the payment and among the import's keys, so a later import finds it a duplicate (SPEC R26, R119). */
    private fun withKeys(d: ProfileData, paymentId: String, row: ReviewRow): ProfileData {
        val keys = d.importState.importedKeys.toMutableList()
        for (k in StatementImport.keyStrings(row.keys)) if (k !in keys) keys.add(k)
        return d.copy(
            payments = d.payments.map { if (it.id == paymentId) it.copy(bankKeys = row.keys) else it },
            importState = d.importState.copy(importedKeys = keys),
        )
    }

    /** The store's link, made the first time it pairs: the bank text's rule name (SPEC R36, R120). */
    private fun linkStore(d: ProfileData, store: String?, bankText: String?): ProfileData {
        val s = d.receiptSettings ?: return d
        if (store == null || bankText == null || store in s.storeLinks) return d
        return d.copy(receiptSettings = s.copy(storeLinks = s.storeLinks + (store to StatementImport.payeeRuleName(bankText))))
    }
}
