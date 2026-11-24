package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import kotlinx.datetime.LocalDate

/*
 * Ported in part from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Income.kt at
 * 2892110: only the income received this month, which the goal calculator reads (SPEC R56, R57).
 * The rest of Income (cadences, auto-deposit, paydays) is ported with the Income piece.
 */
object Income {

    /**
     * Worked out when read, never stored: the sum of the income rows dated in [today]'s month and
     * on or after the day Income was switched on.
     */
    fun receivedThisMonth(d: ProfileData, today: LocalDate): Money {
        val ym = today.ymKey()
        val floor = d.settings.incomeEnabledDate
        return d.transactions
            .filter { it.isIncome && it.date.ymKey() == ym && (floor == null || it.date >= floor) }
            .sumOf { it.amount }
    }
}
