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
 * (SPEC R37); a suggestion only names a payment.
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

object Pairing {

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
     * Which payment each row of [rows] names in the import review (SPEC R118): as many rows in
     * [eligible] (money out, not recorded yet) as possible each name a receipt payment that [match]
     * finds, each row and each payment once at most, and R118's order (the store's link, equal
     * totals, the closest day, the earlier row, the row listed first, the payment recorded first)
     * decides among such choices. Rows are taken in the order of their best option and each tries
     * its options in that order, displacing an earlier row's payment only along an augmenting path,
     * which leaves that row a payment too (Kuhn's matching). Indices into [rows] to payment ids.
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
        // each row's options in R118's order; rows in the order of their best option
        val byRow = LinkedHashMap<Int, MutableList<Int>>()
        for (o in options) byRow.getOrPut(o.row) { mutableListOf() } += o.payment
        val holder = HashMap<Int, Int>()          // payment index to the row naming it
        fun augment(row: Int, seen: MutableSet<Int>): Boolean {
            // a free payment first, in order; only then displace another row's, which must find another
            byRow.getValue(row).firstOrNull { it !in holder && it !in seen }?.let { seen += it; holder[it] = row; return true }
            for (p in byRow.getValue(row)) {
                if (!seen.add(p)) continue
                if (augment(holder.getValue(p), seen)) { holder[p] = row; return true }
            }
            return false
        }
        for (row in byRow.keys) augment(row, HashSet())
        return holder.entries.associate { (p, row) -> row to payments[p].id }
    }

    /** The difference part of a pair (SPEC R39): the bank amount less the receipt's total, negative when the bank row is below it. */
    fun differencePart(receiptTotal: Money, bankAmount: Money): Money = bankAmount - receiptTotal

    /** Whether a pair with [difference] can be recorded: nothing to file, or a category picked for it (SPEC R39). */
    fun canConfirmPair(difference: Money, differenceCategoryId: String?): Boolean = difference == 0L || differenceCategoryId != null

    // --- receipt first, then its bank row (SPEC R38 row 1) ---

    /** What the receipt's payment [paymentId] becomes when paired with bank row [row] (SPEC R119). */
    fun bankPairDraft(d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?): PaymentDraft {
        val p = d.payments.first { it.id == paymentId }
        val bank = -row.row.amount
        return PaymentDraft(p.date, bank, p.source, p.payee, withDifference(p.parts.filter { !it.difference }, differencePart(p.total, bank), differenceCategoryId))
    }

    /** Why receipt payment [paymentId] and bank row [row] cannot be paired, or null (SPEC R119). */
    fun bankPairProblem(d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?): PairProblem? {
        val p = d.payments.firstOrNull { it.id == paymentId }
        if (p == null || !receiptSide(p)) return PairProblem.PAYMENT_NOT_PAIRABLE
        if (row.row.amount >= 0) return PairProblem.ROW_NOT_PAIRABLE
        if (recorded(d, row)) return PairProblem.ROW_RECORDED
        if (!canConfirmPair(differencePart(p.total, -row.row.amount), differenceCategoryId)) return PairProblem.DIFFERENCE_UNFILED
        if (!Payments.canSave(d, bankPairDraft(d, paymentId, row, differenceCategoryId), editing = paymentId)) return PairProblem.CANNOT_SAVE
        return null
    }

    /** The warning the paired payment would carry, as for any payment edit (SPEC R54, R109). */
    fun bankPairNotice(d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?): PaymentNotice? =
        Payments.draftNotice(d, bankPairDraft(d, paymentId, row, differenceCategoryId), editing = paymentId)

    /**
     * Pairs receipt payment [paymentId] with bank row [row], suggested or by hand (SPEC R38, R40,
     * R119): Payments.edit gives it the bank amount as its total and the difference part, keeping
     * its day and source; it gains the row's keys, which the import keeps too (SPEC R26), and the
     * store's link the first time the store pairs (SPEC R120). [txId] is the ledger row a deleted
     * source's edit may write (SPEC R54).
     */
    fun pairBankWithPayment(d: ProfileData, paymentId: String, row: ReviewRow, differenceCategoryId: String?, clock: Clock, txId: String): ProfileData {
        val problem = bankPairProblem(d, paymentId, row, differenceCategoryId)
        require(problem == null) { "the pair cannot be recorded: $problem (SPEC R119)" }
        val store = d.payments.first { it.id == paymentId }.payee
        val edited = Payments.edit(d, paymentId, bankPairDraft(d, paymentId, row, differenceCategoryId), clock, txId)
        return linkStore(withKeys(edited, paymentId, row), store, row.row.description)
    }

    // --- bank row first, then its receipt (SPEC R38 row 2) ---

    /** What bank payment [paymentId] becomes when paired with the receipt under [review] (SPEC R119). */
    fun receiptPairDraft(d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?): PaymentDraft {
        val p = d.payments.first { it.id == paymentId }
        val f = review.file
        return PaymentDraft(f.date, p.total, review.source, f.store, withDifference(Receipts.parts(review), differencePart(f.totalPaid, p.total), differenceCategoryId))
    }

    /** Why bank payment [paymentId] and the receipt under [review] cannot be paired, or null (SPEC R119). */
    fun receiptPairProblem(d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?): PairProblem? {
        val p = d.payments.firstOrNull { it.id == paymentId }
        if (p == null || !bankSide(p)) return PairProblem.PAYMENT_NOT_PAIRABLE
        // the receipt's own checks, but its own total: the pair's total is the bank row's
        val rp = Receipts.problem(d, review)
        if (rp != null && rp != ReceiptProblem.CANNOT_SAVE) return PairProblem.RECEIPT_PROBLEM
        if (!canConfirmPair(differencePart(review.file.totalPaid, p.total), differenceCategoryId)) return PairProblem.DIFFERENCE_UNFILED
        val base = Receipts.withAdded(d, review)
        if (!Payments.canSave(base, receiptPairDraft(d, paymentId, review, differenceCategoryId), editing = paymentId)) return PairProblem.CANNOT_SAVE
        return null
    }

    /** The warning the paired payment would carry (SPEC R54, R109). */
    fun receiptPairNotice(d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?): PaymentNotice? =
        Payments.draftNotice(Receipts.withAdded(d, review), receiptPairDraft(d, paymentId, review, differenceCategoryId), editing = paymentId)

    /**
     * Pairs bank payment [paymentId] with the receipt under [review], suggested or by hand (SPEC
     * R38, R40, R119): Payments.edit gives it the receipt's parts, day, source and store, with the
     * difference part, keeping its total and keys; then the receipt is kept as its Confirm keeps
     * it (SPEC R115), and the store's link made the first time the store pairs (SPEC R120).
     */
    fun pairReceiptWithPayment(d: ProfileData, paymentId: String, review: ReceiptReview, differenceCategoryId: String?, clock: Clock, txId: String): ProfileData {
        val problem = receiptPairProblem(d, paymentId, review, differenceCategoryId)
        require(problem == null) { "the pair cannot be recorded: $problem (SPEC R119)" }
        val bankText = d.payments.first { it.id == paymentId }.payee
        val base = Receipts.withAdded(d, review)
        val edited = Payments.edit(base, paymentId, receiptPairDraft(d, paymentId, review, differenceCategoryId), clock, txId)
        return linkStore(Receipts.keep(d, edited, review, paymentId), review.file.store, bankText)
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

    private fun recorded(d: ProfileData, row: ReviewRow): Boolean {
        val keys = StatementImport.keyStrings(row.keys)
        val seen = d.importState.importedKeys.toHashSet()
        for (p in d.payments) p.bankKeys?.let { seen.addAll(StatementImport.keyStrings(it)) }
        return keys.any { it in seen }
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
