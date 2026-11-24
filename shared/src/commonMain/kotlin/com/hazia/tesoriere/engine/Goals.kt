package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.MINOR_UNITS_PER_MAJOR
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.ceilDiv
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Goals.kt at 2892110.
 * Changed from Sci-Fin: the gentler amounts are rounded with integer arithmetic, not through a
 * Double (SPEC R20).
 *
 * The goal calculator (SPEC R55), where a goal fits (SPEC R56) and gentler monthly amounts (SPEC
 * R57). The months and the monthly amount are worked out whenever they are read, never left
 * standing after money moves under them. This month's recurring total is a parameter here: it is
 * the recurring piece's arithmetic, and taking it as an input keeps this file testable alone.
 */

/** SPEC R55's two figures. `months` is null when there is nothing to work out. */
data class GoalDerivation(val months: Int?, val monthly: Money?)

/** SPEC R56's comparison, present once a monthly amount exists. */
sealed class GoalFrame {
    abstract val monthly: Money
    abstract val fits: Boolean
    /** Income on: `available` = income received this month - recurring - other goals. */
    data class IncomeOn(override val monthly: Money, val available: Money, override val fits: Boolean) : GoalFrame()
    /** Income off: `committed` = recurring + other goals + this goal, against Savings. */
    data class IncomeOff(override val monthly: Money, val committed: Money, val savings: Money, override val fits: Boolean) : GoalFrame()
}

/** SPEC R57: the three gentler amounts, or null `suggestions` when the base is zero or less. */
data class Sustainability(val incomeOn: Boolean, val suggestions: List<Money>?)

object Goals {

    /** Calendar months from the current month to the target's month, at least 1 (SPEC R55). */
    fun calendarMonthsUntil(today: LocalDate, target: LocalDate): Int =
        maxOf(1, (target.year - today.year) * 12 + (target.month.number - today.month.number))

    /**
     * SPEC R55: the amount divided is target minus saved; by date, the monthly amount rounds up
     * to the cent; by amount, the months round up to a whole month.
     */
    fun derive(g: Goal, today: LocalDate): GoalDerivation {
        val target = g.target ?: return GoalDerivation(null, null)          // a running tally
        val remaining = maxOf(0L, target - g.saved)
        g.targetDate?.let { date ->
            val months = calendarMonthsUntil(today, date)
            return GoalDerivation(months, remaining.ceilDiv(months.toLong()))
        }
        val monthly = g.monthly
        if (monthly != null && monthly > 0) {
            return GoalDerivation(remaining.ceilDiv(monthly).toInt(), monthly)
        }
        return GoalDerivation(null, null)
    }

    /** [derive] applied to the stored monthly amount; the months are never stored. */
    fun rederive(g: Goal, today: LocalDate): Goal = g.copy(monthly = derive(g, today).monthly)

    /** A goal stops counting once its saved money reaches its target; the Goal itself stays (SPEC R56). */
    fun commitmentEnded(g: Goal): Boolean = g.target != null && g.saved >= g.target

    /** Every other goal's monthly amount; reached goals and goals with none count 0 (SPEC R56). */
    fun otherGoalsMonthlyTotal(d: ProfileData, excludeId: String?, today: LocalDate): Money =
        d.goals.filter { it.id != excludeId && it.target != null && !commitmentEnded(it) }
            .sumOf { derive(it, today).monthly ?: 0 }

    /** SPEC R56's comparison. With Income on it reads the income received, never a forecast. */
    fun frame(d: ProfileData, monthly: Money, excludeId: String?, scheduledRecurring: Money, today: LocalDate): GoalFrame {
        val others = otherGoalsMonthlyTotal(d, excludeId, today)
        return if (d.settings.incomeEnabled) {
            val available = Income.receivedThisMonth(d, today) - scheduledRecurring - others
            GoalFrame.IncomeOn(monthly, available, monthly <= available)
        } else {
            val committed = scheduledRecurring + others + monthly
            GoalFrame.IncomeOff(monthly, committed, d.savingsAmount, committed <= d.savingsAmount)
        }
    }

    /**
     * SPEC R57: the base is income received this month with Income on (threshold 1/6) and
     * Savings with it off (threshold 1/12). The amounts offered are 1/14, 1/12 and 1/6 of income
     * or 1/20, 1/16 and 1/12 of Savings, each rounded to the nearest whole unit, halves up.
     */
    fun sustainability(d: ProfileData, monthly: Money, today: LocalDate): Sustainability? {
        val incomeOn = d.settings.incomeEnabled
        val base = if (incomeOn) Income.receivedThisMonth(d, today) else d.savingsAmount
        val threshold = if (incomeOn) base / 6 else base / 12
        if (monthly <= threshold) return null
        if (base <= 0) return Sustainability(incomeOn, null)
        val fractions = if (incomeOn) listOf(14L, 12L, 6L) else listOf(20L, 16L, 12L)
        return Sustainability(incomeOn, fractions.map { roundToWholeUnit(base, it) })
    }

    /** [base] / [divisor] rounded to the nearest whole unit, halves up, in cents; [base] > 0. */
    private fun roundToWholeUnit(base: Money, divisor: Long): Money {
        val unit = MINOR_UNITS_PER_MAJOR * divisor
        return (base + unit / 2) / unit * MINOR_UNITS_PER_MAJOR
    }
}
