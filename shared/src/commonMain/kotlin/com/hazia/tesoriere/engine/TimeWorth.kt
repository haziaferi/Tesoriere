package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.Settings
import kotlin.math.roundToLong

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/TimeWorth.kt at 2892110.
 *
 * The overtime calculator (SPEC R102): what extra hours come to, the rate times the hours and
 * nothing more. It reads Settings and writes nothing: no ledger row, no Goal. Whether it is shown
 * at all is `Settings.overtimeEnabled`, off on a fresh Profile (SPEC R18), and the caller's.
 *
 * Changed from Sci-Fin:
 * - A month's pay for weekly and every-two-weeks pay is worked out in whole cents, the pay times
 *   52 (or 26) divided by 12 with a half cent rounded up, where Sci-Fin multiplied by a Double;
 *   the results are the same, with no floating point on money (SPEC R20).
 * - Where Double still touches money: [prefillRate] divides a month's pay by the hours, and
 *   [worth] multiplies the rate by the hours, since hours are a Double, as in Sci-Fin and as
 *   Hourly.gross does. Each product is rounded to whole cents at once, half up, by `roundToLong`.
 */
object TimeWorth {
    /** The hours expected in a month when the user gives none, used only for the salaried pre-fill (SPEC R102). */
    const val DEFAULT_EXPECTED_HOURS = 160.0

    /** One paycheck as a month's pay, by cadence; null with Income off, no pay, or hourly income (SPEC R102). */
    fun monthlyEquivalent(s: Settings): Money? {
        if (!s.incomeEnabled || s.income <= 0 || Hourly.isHourly(s)) return null
        return when (s.incomeCadence) {
            IncomeCadence.WEEKLY -> divideHalfUp(s.income * 52, 12)
            IncomeCadence.BIWEEKLY -> divideHalfUp(s.income * 26, 12)
            IncomeCadence.SEMIMONTHLY -> s.income * 2
            IncomeCadence.MONTHLY -> s.income
        }
    }

    /**
     * The pre-filled rate: hourly income's own rate, or a month's pay over the hours the user
     * expects to work in it, rounded to the cent. A starting point the user can type over, never
     * a gate; null when there is nothing to work it out from (SPEC R102).
     */
    fun prefillRate(s: Settings, expectedHours: Double): Money? {
        if (Hourly.isHourly(s)) return s.hourlyRate.takeIf { it > 0 }
        val monthly = monthlyEquivalent(s) ?: return null
        if (expectedHours <= 0) return null
        return (monthly / expectedHours).roundToLong()
    }

    /** What [hours] extra hours a month at [rate] come to: in a month, and in a year at the same rate and hours (SPEC R102). */
    fun worth(rate: Money, hours: Double): Pair<Money, Money> {
        val monthly = (rate * hours.coerceAtLeast(0.0)).roundToLong()
        return monthly to monthly * 12
    }

    /** [num] / [den] for a positive [num], to the nearest cent, a half rounded up as `roundToLong` does. */
    private fun divideHalfUp(num: Long, den: Long): Long = (2 * num + den) / (2 * den)
}
