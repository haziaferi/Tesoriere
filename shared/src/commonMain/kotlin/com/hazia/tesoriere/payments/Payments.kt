package com.hazia.tesoriere.payments

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.Ledger
import com.hazia.tesoriere.engine.Movement
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels

/*
 * Tesoriere's own: recording, editing and removing a payment (SPEC R21-R25, R54). A payment is
 * its own record and writes no ledger row; its source moves Savings, a Goal or an Account at
 * once, and its parts count in their categories' spent when read (SPEC R25). The two rows: what
 * a payment from a Goal deleted since moves in Savings, when edited or removed, is logged as a
 * `savings` row, so the reports' replay of Savings (SPEC R95) still adds up to it; and a change to
 * what is charged to an Account deleted since is logged as an `acct:` row, so its rows and its
 * payments still add up to zero and Savings vs Owed's trend matches its figure (SPEC R88, R100).
 */
object Payments {

    /** What is left to file: the total minus the parts (SPEC R23). Zero when they add up. */
    fun remainder(draft: PaymentDraft): Money = draft.total - draft.parts.sumOf { it.amount }

    /**
     * Whether the editor's save is on: a positive total, parts in categories that exist, adding
     * up to the total (SPEC R23), and a source that exists; a Goal pays at most what it holds
     * (SPEC R54). When [editing] names the payment being edited, its own effect counts as undone.
     */
    fun canSave(d: ProfileData, draft: PaymentDraft, editing: String? = null): Boolean {
        if (draft.total <= 0 || draft.parts.isEmpty() || remainder(draft) != 0L) return false
        val categories = d.categories.mapTo(HashSet()) { it.id }
        if (draft.parts.any { it.amount == 0L || it.categoryId == null || it.categoryId !in categories }) return false
        val old = editing?.let { id -> d.payments.firstOrNull { it.id == id } }
        val base = old?.let { undo(d, it) } ?: d
        return when (val s = draft.source) {
            PaymentSource.Savings -> true
            // a Goal deleted since stays this payment's source; it moves Savings instead (SPEC R54)
            is PaymentSource.Goal -> base.goals.firstOrNull { it.id == s.id }?.let { draft.total <= it.saved } ?: (old?.source == s)
            // an Account deleted since stays this payment's source and moves no balance (SPEC R54, R88)
            is PaymentSource.Account -> base.accounts.any { it.id == s.id } || old?.source == s
        }
    }

    /** The warning code for a recorded payment whose source was deleted since, or null when there is none (SPEC R54). */
    fun notice(d: ProfileData, paymentId: String): PaymentNotice? =
        when (val source = d.payments.firstOrNull { it.id == paymentId }?.source) {
            is PaymentSource.Goal -> PaymentNotice.SOURCE_GOAL_DELETED.takeIf { d.goals.none { it.id == source.id } }
            is PaymentSource.Account -> PaymentNotice.SOURCE_ACCOUNT_DELETED.takeIf { d.accounts.none { it.id == source.id } }
            PaymentSource.Savings, null -> null
        }

    /** Records [draft] as a new payment and applies its source (SPEC R54). */
    fun record(d: ProfileData, draft: PaymentDraft, clock: Clock, id: String): ProfileData {
        require(canSave(d, draft)) { "the payment cannot be saved" }
        val p = Payment(
            id = id, profileId = d.profileId, date = draft.date, loggedAt = clock.now(), total = draft.total,
            source = draft.source, payee = draft.payee?.takeIf { it.isNotBlank() }, parts = draft.parts,
        )
        return apply(d, p).copy(payments = d.payments + p)
    }

    /**
     * Replaces a payment's date, total, source, payee and parts, undoing the old source's effect
     * and applying the new one (SPEC R54). Its id, logged instant, receipt and bank keys stay.
     * What a Goal deleted since moves in Savings is logged as one `savings` row [txId], dated
     * today, when it is not zero.
     */
    fun edit(d: ProfileData, paymentId: String, draft: PaymentDraft, clock: Clock, txId: String): ProfileData {
        val old = d.payments.first { it.id == paymentId }
        require(canSave(d, draft, editing = paymentId)) { "the payment cannot be saved" }
        val new = old.copy(date = draft.date, total = draft.total, source = draft.source, payee = draft.payee?.takeIf { it.isNotBlank() }, parts = draft.parts)
        var next = apply(undo(d, old), new)
        val shift = deletedGoalShift(d, old.source, old.total) - deletedGoalShift(d, new.source, new.total)
        next = logDeletedGoalShift(next, shift, clock, txId)
        // canSave admits a deleted Goal or Account only as the payment's own source, so at most one row is logged
        next = logDeletedAccountShift(d, next, old, new, clock, txId)
        return next.copy(payments = d.payments.map { if (it.id == paymentId) new else it })
    }

    /**
     * Removes a payment and undoes its source's effect (SPEC R54); for a Goal deleted since, the
     * money returned to Savings is logged as one `savings` row [txId], dated today, and for an
     * Account deleted since, the charge taken off it as one `acct:` row [txId].
     */
    fun remove(d: ProfileData, paymentId: String, clock: Clock, txId: String): ProfileData {
        val p = d.payments.firstOrNull { it.id == paymentId } ?: return d
        var next = logDeletedGoalShift(undo(d, p), deletedGoalShift(d, p.source, p.total), clock, txId)
        next = logDeletedAccountShift(d, next, p, null, clock, txId)
        return next.copy(payments = d.payments.filter { it.id != paymentId })
    }

    /**
     * What is charged to an Account deleted since must stay matched by its `acct:` rows, so that
     * they add up to zero together, as the closing row left them (SPEC R54, R88, R100): when [old]
     * was charged to such an Account and [new] (null on removal) charges it less, or nothing, the
     * difference is logged as one `acct:` row of the opposite sign, dated today. It moves no balance.
     */
    private fun logDeletedAccountShift(before: ProfileData, d: ProfileData, old: Payment, new: Payment?, clock: Clock, txId: String): ProfileData {
        val s = old.source as? PaymentSource.Account ?: return d
        if (before.accounts.any { it.id == s.id }) return d
        val change = (if (new?.source == s) new.total else 0L) - old.total
        if (change == 0L) return d
        val key = TransactionKey.AccountKey(s.id)
        val label = before.transactions.lastOrNull { it.key == key.stored }?.targetLabel ?: s.id
        return Ledger.logAndMove(d, Movement(-change, key, label, StoredLabels.DELETED_ACCOUNT_PAYMENT), clock, txId).first
    }

    /** What undoing [total] from [source] returns to Savings when the source is a Goal deleted since, else 0. */
    private fun deletedGoalShift(d: ProfileData, source: PaymentSource, total: Money): Money =
        if (source is PaymentSource.Goal && d.goals.none { it.id == source.id }) total else 0

    /** The `savings` row that moves Savings in a deleted Goal's place (SPEC R54, R95); nothing for 0. */
    private fun logDeletedGoalShift(d: ProfileData, amount: Money, clock: Clock, txId: String): ProfileData =
        if (amount == 0L) d
        else Ledger.logAndMove(d, Movement(amount, TransactionKey.SavingsKey, StoredLabels.SAVINGS, StoredLabels.DELETED_GOAL_PAYMENT), clock, txId).first

    /**
     * SPEC R54's table: Savings and a Goal go down by the total, an Account's balance goes up. A
     * Goal or an Account deleted since moves nothing here: the Goal's Savings movement is logged
     * as a ledger row by the caller ([logDeletedGoalShift]); the Account is gone.
     */
    private fun move(d: ProfileData, source: PaymentSource, total: Money): ProfileData = when (source) {
        // touching Savings ends its unset state, counting from zero (SPEC R49)
        PaymentSource.Savings -> d.copy(savings = d.savings.copy(amount = (d.savings.amount ?: 0) - total))
        is PaymentSource.Goal -> d.copy(goals = d.goals.map { if (it.id == source.id) it.copy(saved = it.saved - total) else it })
        is PaymentSource.Account -> d.copy(accounts = d.accounts.map { if (it.id == source.id) it.copy(balance = it.balance + total) else it })
    }

    private fun apply(d: ProfileData, p: Payment) = move(d, p.source, p.total)
    private fun undo(d: ProfileData, p: Payment) = move(d, p.source, -p.total)
}
