package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.Spent

/*
 * Plan against spent (SPEC R59), after Sci-Fin's Budget categories (Sci-Fin's spec, section 4.3,
 * at 2892110), with spent worked out per month (SPEC R25) instead of stored.
 */

/** One category's month: its planned amount beside what was spent. How over-plan looks is the design pass's (SPEC R10, R16). */
data class CategoryMonth(val categoryId: String, val planned: Money, val spent: Money)

object Categories {

    /** Every category of the Profile, in its stored order, for month [ym] (`YYYY-MM`). */
    fun month(d: ProfileData, ym: String): List<CategoryMonth> =
        d.categories.map { CategoryMonth(it.id, it.planned, Spent.of(d, it.id, ym)) }
}
