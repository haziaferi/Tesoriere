package com.hazia.tesoriere.payments

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.Ledger
import com.hazia.tesoriere.engine.Movement
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels

/*
 * Tesoriere's own: recording, editing and removing a payment (SPEC R21-R25, R54). A payment is
 * its own record and writes no ledger row; its source moves Savings, a Goal or an Account at
 * once, and its parts count in their categories' spent when read (SPEC R25). A refund is a
 * payment with a negative total and negative parts: the same arithmetic moves its source the
 * other way and lowers its categories' spent (SPEC R21, R23). A payment from a Goal short of its
 * total takes all the Goal holds and the rest from Savings, and keeps that share in
 * [Payment.fromSavings], so undoing it restores both exactly (SPEC R54). The two rows: what
 * a payment from a Goal deleted since moves in Savings, when edited or removed, is logged as a
 * `savings` row, so the reports' replay of Savings (SPEC R95) still adds up to it; and a change to
 * what is charged to an Account deleted since is logged as an `acct:` row, so its rows and its
 * payments still add up to zero and Savings vs Owed's trend matches its figure (SPEC R88, R100).
 * A payment put back from the Trash ([Payments.reinstate]) moves its source again as recorded
 * (SPEC R125).
 */
object Payments {

    /** What is left to file: the total minus the parts (SPEC R23). Zero when they add up. */
    fun remainder(draft: PaymentDraft): Money = draft.total - draft.parts.sumOf { it.amount }

    /**
     * Whether [total] and [parts] hold to SPEC R23 as stored: a total other than zero, one part or
     * more, none of zero, each of the total's sign but at most one difference part (SPEC R39), and
     * adding up to the total, summed with no overflow. Categories and who may write a difference
     * part are not looked at: a recorded payment's category may be deleted since (SPEC R124), and a
     * stored difference part was written by pairing. The editor's [canSave] and a backup's reading
     * (SPEC R131) both start from it.
     */
    fun partsAddUp(total: Money, parts: List<Part>): Boolean {
        if (total == 0L || parts.isEmpty() || parts.count { it.difference } > 1) return false
        val refund = total < 0
        var sum = 0L
        for (p in parts) {
            val a = p.amount
            if (a == 0L || (!p.difference && (a < 0) != refund)) return false
            if ((a > 0 && sum > Long.MAX_VALUE - a) || (a < 0 && sum < Long.MIN_VALUE - a)) return false
            sum += a
        }
        return sum == total
    }

    /**
     * Whether the editor's save is on: a total other than zero, negative for a refund (SPEC R21),
     * parts in categories that exist, each of the total's sign but a pair's one difference part
     * (SPEC R39), adding up to the total (SPEC R23),
     * and a source that exists. A Goal short of the total is no reason to refuse: Savings pays
     * the rest, after the warning of [draftNotice] (SPEC R54). When [editing] names the payment
     * being edited, its own effect counts as undone. A difference part is taken only when the
     * payment being edited holds one already: only pairing writes one (SPEC R23).
     */
    fun canSave(d: ProfileData, draft: PaymentDraft, editing: String? = null): Boolean = canSave(d, draft, editing, pairing = false)

    /** [canSave] for pairing (SPEC R39, R119), the one caller that may write a difference part. */
    internal fun canSave(d: ProfileData, draft: PaymentDraft, editing: String?, pairing: Boolean): Boolean {
        if (!partsAddUp(draft.total, draft.parts)) return false
        val categories = d.categories.mapTo(HashSet()) { it.id }
        val old = editing?.let { id -> d.payments.firstOrNull { it.id == id } }
        // a pair's difference part, at most one, may have the other sign (SPEC R23, R39); only pairing
        // writes one, and an edit keeps one the payment already holds
        if (draft.parts.any { it.difference } && !pairing && old?.parts?.any { it.difference } != true) return false
        if (draft.parts.any { it.categoryId == null || it.categoryId !in categories }) return false
        val base = old?.let { undo(d, it) } ?: d
        return when (val s = draft.source) {
            PaymentSource.Savings -> true
            // a Goal deleted since stays this payment's source; it moves Savings instead (SPEC R54)
            is PaymentSource.Goal -> base.goals.any { it.id == s.id } || old?.source == s
            // an Account deleted since stays this payment's source and moves no balance (SPEC R54, R88)
            is PaymentSource.Account -> base.accounts.any { it.id == s.id } || old?.source == s
        }
    }

    /**
     * The share of [draft]'s total that Savings would pay because its Goal holds less (SPEC R54):
     * the total less all the Goal holds, worked out against what it holds once the payment being
     * edited has returned its own share; 0 for any other source, a refund, or a Goal deleted since.
     */
    fun fromSavings(d: ProfileData, draft: PaymentDraft, editing: String? = null): Money {
        val s = draft.source as? PaymentSource.Goal ?: return 0
        if (draft.total <= 0) return 0
        val old = editing?.let { id -> d.payments.firstOrNull { it.id == id } }
        val base = old?.let { undo(d, it) } ?: d
        val goal = base.goals.firstOrNull { it.id == s.id }
            // deleted since: the share it already took is kept, never more than the new total
            ?: return if (old?.source == s) minOf(old.fromSavings, draft.total) else 0
        return maxOf(0L, draft.total - maxOf(0L, goal.saved))
    }

    /**
     * The warning code for a draft before it is saved, or null when there is none: a Goal short
     * of its total (SPEC R54), or a card whose charges are not logged (SPEC R109).
     */
    fun draftNotice(d: ProfileData, draft: PaymentDraft, editing: String? = null): PaymentNotice? = when (val s = draft.source) {
        is PaymentSource.Goal ->
            if (d.goals.none { it.id == s.id }) null else PaymentNotice.GOAL_SHORT.takeIf { fromSavings(d, draft, editing) > 0 }
        is PaymentSource.Account ->
            PaymentNotice.CHARGES_NOT_LOGGED.takeIf { d.accounts.any { it.id == s.id && it.type == AccountType.CREDIT && it.chargesNotLogged } }
        PaymentSource.Savings -> null
    }

    /** The warning code for a recorded payment whose source was deleted since, or null when there is none (SPEC R54). */
    fun notice(d: ProfileData, paymentId: String): PaymentNotice? =
        when (val source = d.payments.firstOrNull { it.id == paymentId }?.source) {
            is PaymentSource.Goal -> PaymentNotice.SOURCE_GOAL_DELETED.takeIf { d.goals.none { it.id == source.id } }
            is PaymentSource.Account -> PaymentNotice.SOURCE_ACCOUNT_DELETED.takeIf { d.accounts.none { it.id == source.id } }
            PaymentSource.Savings, null -> null
        }

    /**
     * Records [draft] as a new payment and applies its source (SPEC R54), with Savings paying what a
     * short Goal cannot. A draft [canSave] refuses changes nothing: [d] comes back unchanged.
     */
    fun record(d: ProfileData, draft: PaymentDraft, clock: Clock, id: String): ProfileData {
        if (!canSave(d, draft)) return d
        val p = Payment(
            id = id, profileId = d.profileId, date = draft.date, loggedAt = clock.now(), total = draft.total,
            source = draft.source, payee = draft.payee?.takeIf { it.isNotBlank() }, parts = draft.parts,
            fromSavings = fromSavings(d, draft),
        )
        return apply(d, p).copy(payments = d.payments + p)
    }

    /**
     * Replaces a payment's date, total, source, payee and parts, undoing the old source's effect
     * and applying the new one (SPEC R54), with the share Savings pays for a short Goal worked out
     * again once the old share is returned. Its id, logged instant, receipt and bank keys stay.
     * What a Goal deleted since moves in Savings is logged as one `savings` row [txId], dated
     * today, when it is not zero. An id no payment holds, one in the Trash say, or a draft [canSave]
     * refuses, gives [d] back unchanged.
     */
    fun edit(d: ProfileData, paymentId: String, draft: PaymentDraft, clock: Clock, txId: String): ProfileData =
        edit(d, paymentId, draft, clock, txId, pairing = false)

    /** [edit] for pairing (SPEC R119), which may write a difference part (SPEC R23, R39). */
    internal fun edit(d: ProfileData, paymentId: String, draft: PaymentDraft, clock: Clock, txId: String, pairing: Boolean): ProfileData {
        // an unknown id, such as a payment in the Trash, changes nothing, as remove does
        val old = d.payments.firstOrNull { it.id == paymentId } ?: return d
        // a draft that cannot be saved changes nothing either
        if (!canSave(d, draft, paymentId, pairing)) return d
        val new = old.copy(
            date = draft.date, total = draft.total, source = draft.source, payee = draft.payee?.takeIf { it.isNotBlank() }, parts = draft.parts,
            fromSavings = fromSavings(d, draft, editing = paymentId),
        )
        var next = apply(undo(d, old), new)
        val shift = deletedGoalShift(d, old) - deletedGoalShift(d, new)
        next = logDeletedGoalShift(next, shift, clock, txId)
        // canSave admits a deleted Goal or Account only as the payment's own source, so at most one row is logged
        next = logDeletedAccountShift(d, next, old, new, clock, txId)
        return next.copy(payments = d.payments.map { if (it.id == paymentId) new else it })
    }

    /**
     * Removes a payment and undoes its source's effect (SPEC R54); for a Goal deleted since, the
     * money returned to Savings in its place is logged as one `savings` row [txId], dated today,
     * and for an Account deleted since, the charge taken off it as one `acct:` row [txId].
     */
    fun remove(d: ProfileData, paymentId: String, clock: Clock, txId: String): ProfileData {
        val p = d.payments.firstOrNull { it.id == paymentId } ?: return d
        var next = logDeletedGoalShift(undo(d, p), deletedGoalShift(d, p), clock, txId)
        next = logDeletedAccountShift(d, next, p, null, clock, txId)
        return next.copy(payments = d.payments.filter { it.id != paymentId })
    }

    /**
     * Puts back payment [p], removed earlier, for a restore from the Trash (SPEC R122, R125): its
     * id, date, parts, receipt and keys as they were, at its place in logged order, and its source
     * moved again as recorded. A Goal that holds less than its share gives all it holds and Savings
     * the rest, the payment's [Payment.fromSavings] growing by it (SPEC R54). A Goal or an Account
     * deleted since its removal moves as for any payment of a deleted source: one `savings` or
     * `acct:` row [txId], dated today. When [settled], the source was deleted before the removal
     * and is still gone, and the caller undoes the removal's row instead, so no row is written here;
     * a source restored since is not settled and moves as recorded. A payment whose id is in the
     * Profile already is not put back a second time: [d] comes back unchanged.
     */
    fun reinstate(d: ProfileData, p: Payment, settled: Boolean, clock: Clock, txId: String): ProfileData {
        if (d.payments.any { it.id == p.id }) return d
        val back = reinstated(d, p, settled)
        val at = d.payments.indexOfFirst { it.loggedAt > back.loggedAt }.let { if (it < 0) d.payments.size else it }
        var next = apply(d, back).copy(payments = d.payments.subList(0, at) + back + d.payments.subList(at, d.payments.size))
        if (settled) return next
        when (val s = back.source) {
            is PaymentSource.Goal -> if (d.goals.none { it.id == s.id }) next = logDeletedGoalShift(next, -(back.total - back.fromSavings), clock, txId)
            is PaymentSource.Account -> next = logDeletedAccountCharge(d, next, s.id, back.total, clock, txId)
            PaymentSource.Savings -> Unit
        }
        return next
    }

    /**
     * The warning a restore of [p] carries, or null (SPEC R125): its source deleted since its
     * removal, or a Goal short of its share (SPEC R54). None when [settled]: the source was deleted
     * before the removal, and undoing the removal's row moves Savings back as it was.
     */
    fun reinstateNotice(d: ProfileData, p: Payment, settled: Boolean): PaymentNotice? = if (settled) null else when (val s = p.source) {
        is PaymentSource.Goal -> when {
            d.goals.none { it.id == s.id } -> PaymentNotice.SOURCE_GOAL_DELETED
            reinstated(d, p, settled = false).fromSavings > p.fromSavings -> PaymentNotice.GOAL_SHORT
            else -> null
        }
        is PaymentSource.Account -> PaymentNotice.SOURCE_ACCOUNT_DELETED.takeIf { d.accounts.none { it.id == s.id } }
        PaymentSource.Savings -> null
    }

    /** [p] with the share Savings pays grown to what its Goal cannot give, when the Goal holds less than its share (SPEC R54, R125). */
    private fun reinstated(d: ProfileData, p: Payment, settled: Boolean): Payment {
        val s = p.source as? PaymentSource.Goal ?: return p
        if (settled || p.total <= 0) return p
        val goal = d.goals.firstOrNull { it.id == s.id } ?: return p
        val held = maxOf(0L, goal.saved)
        return if (held >= p.total - p.fromSavings) p else p.copy(fromSavings = p.total - held)
    }

    /**
     * What is charged to an Account deleted since must stay matched by its `acct:` rows, so that
     * they add up to zero together, as the closing row left them (SPEC R54, R88, R100): when [old]
     * was charged to such an Account and [new] (null on removal) charges it less, or nothing, the
     * difference is logged as one `acct:` row of the opposite sign, dated today. It moves no balance.
     */
    private fun logDeletedAccountShift(before: ProfileData, d: ProfileData, old: Payment, new: Payment?, clock: Clock, txId: String): ProfileData {
        val s = old.source as? PaymentSource.Account ?: return d
        return logDeletedAccountCharge(before, d, s.id, (if (new?.source == s) new.total else 0L) - old.total, clock, txId)
    }

    /** A [change] to what is charged to Account [accountId], when it was deleted before [before], logged as one `acct:` row of the opposite sign (SPEC R54, R88). */
    private fun logDeletedAccountCharge(before: ProfileData, d: ProfileData, accountId: String, change: Money, clock: Clock, txId: String): ProfileData {
        if (change == 0L || before.accounts.any { it.id == accountId }) return d
        val key = TransactionKey.AccountKey(accountId)
        val label = before.transactions.lastOrNull { it.key == key.stored }?.targetLabel ?: accountId
        return Ledger.logAndMove(d, Movement(-change, key, label, StoredLabels.DELETED_ACCOUNT_PAYMENT), clock, txId).first
    }

    /**
     * What undoing [p] returns to Savings in its Goal's place when that Goal was deleted since:
     * the Goal's share, the total less what Savings paid; 0 for any other source.
     */
    private fun deletedGoalShift(d: ProfileData, p: Payment): Money {
        val s = p.source
        return if (s is PaymentSource.Goal && d.goals.none { it.id == s.id }) p.total - p.fromSavings else 0
    }

    /** The `savings` row that moves Savings in a deleted Goal's place (SPEC R54, R95); nothing for 0. */
    private fun logDeletedGoalShift(d: ProfileData, amount: Money, clock: Clock, txId: String): ProfileData =
        if (amount == 0L) d
        else Ledger.logAndMove(d, Movement(amount, TransactionKey.SavingsKey, StoredLabels.SAVINGS, StoredLabels.DELETED_GOAL_PAYMENT), clock, txId).first

    /**
     * SPEC R54's table: Savings goes down by the total, a Goal by its share of it with Savings
     * paying [fromSavings], an Account's balance goes up. A Goal or an Account deleted since moves
     * nothing here: the Goal's Savings movement is logged as a ledger row by the caller
     * ([logDeletedGoalShift]), though the share Savings paid moves Savings as it did; the Account
     * is gone. A negative [total] is a refund, or an undo.
     */
    private fun move(d: ProfileData, source: PaymentSource, total: Money, fromSavings: Money): ProfileData = when (source) {
        // touching Savings ends its unset state, counting from zero (SPEC R49)
        PaymentSource.Savings -> d.copy(savings = d.savings.copy(amount = (d.savings.amount ?: 0) - total))
        is PaymentSource.Goal -> {
            val g = d.copy(goals = d.goals.map { if (it.id == source.id) it.copy(saved = it.saved - (total - fromSavings)) else it })
            if (fromSavings == 0L) g else g.copy(savings = g.savings.copy(amount = (g.savings.amount ?: 0) - fromSavings))
        }
        is PaymentSource.Account -> d.copy(accounts = d.accounts.map { if (it.id == source.id) it.copy(balance = it.balance + total) else it })
    }

    private fun apply(d: ProfileData, p: Payment) = move(d, p.source, p.total, p.fromSavings)
    private fun undo(d: ProfileData, p: Payment) = move(d, p.source, -p.total, -p.fromSavings)
}
