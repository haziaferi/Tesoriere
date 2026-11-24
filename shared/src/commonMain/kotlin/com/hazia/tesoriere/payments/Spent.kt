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
 * adjustment, a `cat:` ledger row (SPEC R51).
 */
object Spent {

    /** The category's spent for month [ym] (`YYYY-MM`): its parts of payments dated that month plus its adjustments dated that month. */
    fun of(d: ProfileData, categoryId: String, ym: String): Money {
        val parts = d.payments.filter { it.date.ymKey() == ym }
            .sumOf { p -> p.parts.filter { it.categoryId == categoryId }.sumOf { it.amount } }
        val key = TransactionKey.CategoryKey(categoryId).stored
        val adjustments = d.transactions.filter { it.key == key && it.date.ymKey() == ym }.sumOf { it.amount }
        return parts + adjustments
    }

    /**
     * Sets the category's spent for [ym] to [newValue] by logging the difference as an adjustment
     * (SPEC R25), which moves Savings by its opposite (SPEC R51). The row is dated today when [ym]
     * is the current month, else the last day of [ym], so it counts in that month only. A month
     * still to come cannot be adjusted; an adjustment to the same figure logs nothing.
     */
    fun adjust(d: ProfileData, categoryId: String, ym: String, newValue: Money, clock: Clock, id: String): ProfileData {
        val today = clock.today()
        require(ym <= today.ymKey()) { "a month still to come cannot be adjusted: $ym" }
        val c = d.categories.first { it.id == categoryId }
        val delta = newValue - of(d, categoryId, ym)
        if (delta == 0L) return d
        val date = if (ym == today.ymKey()) today else ym.split('-').let { (y, m) -> dateWithDay(y.toInt(), m.toInt(), 31) }
        return Ledger.logAndMove(d, Movement(delta, TransactionKey.CategoryKey(categoryId), c.name, StoredLabels.CORRECTION, date = date), clock, id).first
    }
}
