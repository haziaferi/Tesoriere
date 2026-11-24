package com.hazia.tesoriere.payments

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData

/*
 * Tesoriere's own: recording, editing and removing a payment (SPEC R21-R25, R54). A payment is
 * its own record and writes no ledger row; its source moves Savings, a Goal or an Account at
 * once, and its parts count in their categories' spent when read (SPEC R25).
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
        val base = editing?.let { id -> d.payments.firstOrNull { it.id == id }?.let { undo(d, it) } } ?: d
        return when (val s = draft.source) {
            PaymentSource.Savings -> true
            is PaymentSource.Goal -> base.goals.firstOrNull { it.id == s.id }?.let { draft.total <= it.saved } ?: false
            is PaymentSource.Account -> base.accounts.any { it.id == s.id }
        }
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
     */
    fun edit(d: ProfileData, paymentId: String, draft: PaymentDraft): ProfileData {
        val old = d.payments.first { it.id == paymentId }
        require(canSave(d, draft, editing = paymentId)) { "the payment cannot be saved" }
        val new = old.copy(date = draft.date, total = draft.total, source = draft.source, payee = draft.payee?.takeIf { it.isNotBlank() }, parts = draft.parts)
        val next = apply(undo(d, old), new)
        return next.copy(payments = d.payments.map { if (it.id == paymentId) new else it })
    }

    /** Removes a payment and undoes its source's effect (SPEC R54). */
    fun remove(d: ProfileData, paymentId: String): ProfileData {
        val p = d.payments.firstOrNull { it.id == paymentId } ?: return d
        return undo(d, p).copy(payments = d.payments.filter { it.id != paymentId })
    }

    /** SPEC R54's table: Savings and a Goal go down by the total, an Account's balance goes up. */
    private fun move(d: ProfileData, source: PaymentSource, total: Money): ProfileData = when (source) {
        // touching Savings ends its unset state, counting from zero (SPEC R49)
        PaymentSource.Savings -> d.copy(savings = d.savings.copy(amount = (d.savings.amount ?: 0) - total))
        is PaymentSource.Goal -> d.copy(goals = d.goals.map { if (it.id == source.id) it.copy(saved = it.saved - total) else it })
        is PaymentSource.Account -> d.copy(accounts = d.accounts.map { if (it.id == source.id) it.copy(balance = it.balance + total) else it })
    }

    private fun apply(d: ProfileData, p: Payment) = move(d, p.source, p.total)
    private fun undo(d: ProfileData, p: Payment) = move(d, p.source, -p.total)
}
