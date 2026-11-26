package com.hazia.tesoriere.payments

import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.Ledger
import com.hazia.tesoriere.engine.Movement
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.engine.dateWithDay
import com.hazia.tesoriere.engine.ymKey
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels

/*
 * Tesoriere's own: a category's spent figure, worked out per month and never stored (SPEC R25).
 * It replaces Sci-Fin's stored `Category.spent`; Sci-Fin's direct edit of spent becomes an
 * adjustment, a `cat:` ledger row (SPEC R51). A recurring expense's `rec:` rows count under the
 * category each was filed under when written (SPEC R67).
 */
object Spent {

    /**
     * The category's spent for month [ym] (`YYYY-MM`): its parts of payments dated that month, a
     * refund's lowering it, and the ledger rows dated that month that count in it as written
     * (Ledger.spent): its adjustments, the recurring expense rows filed under it (SPEC R25, R67)
     * other than an Account payoff's, a card's or a Loan's payment being a transfer whose charges count already
     * through their parts (SPEC R93, owner answer A of 2026-11-25), and the payoff rows of a card
     * whose charges are not logged, filed under it (SPEC R109).
     */
    fun of(d: ProfileData, categoryId: String, ym: String): Money {
        val parts = d.payments.filter { it.date.ymKey() == ym }
            .sumOf { p -> p.parts.filter { it.categoryId == categoryId }.sumOf { it.amount } }
        val rows = d.transactions.filter { it.date.ymKey() == ym }
            .sumOf { t -> Ledger.spent(t)?.takeIf { it.categoryId == categoryId }?.amount ?: 0L }
        return parts + rows
    }

    /**
     * Sets the category's spent for [ym] to [newValue] by logging the difference as an adjustment
     * (SPEC R25), which moves Savings by its opposite (SPEC R51). The row is dated today when [ym]
     * is the current month, else the last day of [ym], so it counts in that month only. A month
     * still to come cannot be adjusted; an adjustment to the same figure logs nothing. A month still
     * to come, or a category that does not exist, gives [d] back unchanged.
     */
    fun adjust(d: ProfileData, categoryId: String, ym: String, newValue: Money, clock: Clock, id: String): ProfileData {
        val today = clock.today()
        if (ym > today.ymKey()) return d
        val c = d.categories.firstOrNull { it.id == categoryId } ?: return d
        val delta = newValue - of(d, categoryId, ym)
        if (delta == 0L) return d
        val date = if (ym == today.ymKey()) today else ym.split('-').let { (y, m) -> dateWithDay(y.toInt(), m.toInt(), 31) }
        return Ledger.logAndMove(d, Movement(delta, TransactionKey.CategoryKey(categoryId), c.name, StoredLabels.CORRECTION, date = date), clock, id).first
    }
}
