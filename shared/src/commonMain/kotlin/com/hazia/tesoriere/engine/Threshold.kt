package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import kotlinx.datetime.LocalDate

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Threshold.kt at 2892110.
 *
 * The recurring threshold note (SPEC R68), as a fact to show rather than a verdict: which case
 * applies, and the two figures. How it reads is the design pass's (SPEC R16).
 *
 * Changed from Sci-Fin: the note carries a [ThresholdCode] naming its case; Sci-Fin's `incomeOn`
 * and `baseIsEmpty` are read from it.
 */

/** Which case the note is. */
enum class ThresholdCode {
    /** Income off: the scheduled total is above 1/12 of Savings. */
    SAVINGS_TWELFTH,
    /** Income off and Savings at zero or below: any scheduled total; no ratio is taken. */
    NO_SAVINGS_BASE,
    /** Income on: the scheduled total is above 1/3 of the income received this month. */
    INCOME_THIRD,
    /** Income on and nothing received this month: any scheduled total; no ratio is taken. */
    NO_INCOME_BASE,
}

/** The note's case and its two figures: the month's [scheduled] total and the [base] it is weighed against. */
data class ThresholdNote(val code: ThresholdCode, val scheduled: Money, val base: Money) {
    val incomeOn: Boolean get() = code == ThresholdCode.INCOME_THIRD || code == ThresholdCode.NO_INCOME_BASE
    /** At a zero or negative base the note names the figure and takes no ratio (SPEC R68). */
    val baseIsEmpty: Boolean get() = code == ThresholdCode.NO_SAVINGS_BASE || code == ThresholdCode.NO_INCOME_BASE
}

object Threshold {
    /**
     * Null when nothing is scheduled, when the total is within the threshold, or, with Income off,
     * while Savings is unset (SPEC R49: there is no figure to take a twelfth of). Income on: above
     * 1/3 of the income received this month. Income off: above 1/12 of Savings, which at Savings
     * of zero or below holds for any positive total (SPEC R68).
     */
    fun recurringNote(d: ProfileData, today: LocalDate): ThresholdNote? {
        val scheduled = Recurring.scheduledTotal(d, today)
        if (scheduled <= 0) return null
        if (d.settings.incomeEnabled) {
            val received = Income.receivedThisMonth(d, today)
            if (scheduled <= received / 3) return null
            return ThresholdNote(if (received <= 0) ThresholdCode.NO_INCOME_BASE else ThresholdCode.INCOME_THIRD, scheduled, received)
        }
        if (d.savings.amount == null) return null
        val savings = d.savingsAmount
        if (scheduled <= savings / 12) return null
        return ThresholdNote(if (savings <= 0) ThresholdCode.NO_SAVINGS_BASE else ThresholdCode.SAVINGS_TWELFTH, scheduled, savings)
    }
}
