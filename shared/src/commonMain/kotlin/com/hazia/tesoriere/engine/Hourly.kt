package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Hourly.kt at 2892110.
 *
 * Hourly income (SPEC R79-R84). Hours are logged as shifts that move nothing (SPEC R80); income
 * lands only on a payday, when what arrived is confirmed (SPEC R81). The estimate is the pay
 * before deductions until a payday is confirmed, then that figure scaled by what arrived against
 * what was logged over the last three paydays (SPEC R82): no tax rate is ever asked.
 *
 * Changed from Sci-Fin: the payday row's payee comes from StoredLabels; `§x.y` references are
 * SPEC rule numbers. Hours and the learned ratio are Double, as in Sci-Fin; every product with
 * money is rounded to whole cents at once, half up, as Sci-Fin's `roundToLong` does (SPEC R20).
 */

/** The payday being asked about: its date and the shifts it covers (SPEC R81). */
data class Payday(val date: LocalDate, val shifts: List<Shift>) {
    val hours: Double get() = shifts.sumOf { it.hours }
    val gross: Money get() = Hourly.gross(shifts)
}

object Hourly {
    /** Paydays averaged for the learned estimate (SPEC R82). */
    const val ESTIMATE_PAYDAYS = 3

    fun isHourly(s: Settings): Boolean = s.incomeEnabled && s.incomeType == IncomeType.HOURLY

    /** Income not recorded by itself: hourly, or salaried without automatic deposit (SPEC R69, R84). */
    fun manualPayday(s: Settings): Boolean = s.incomeEnabled && (s.incomeType == IncomeType.HOURLY || !s.incomeAutoDeposit)

    /** Pay before deductions: each shift's hours times its rate, rounded to the cent, summed (SPEC R80). */
    fun gross(shifts: List<Shift>): Money = shifts.sumOf { (it.hours * it.rate).roundToLong() }

    /** Shifts not yet paid, oldest first. */
    fun pending(d: ProfileData): List<Shift> = d.shifts.sortedWith(compareBy({ it.date }, { it.id }))

    /**
     * The oldest payday that has come and is not answered, or null (SPEC R81). Paydays are SPEC
     * R76's; the first counted is the first on or after the day Income was switched on, so
     * switching it on never brings up a payday behind it.
     */
    fun duePayday(d: ProfileData, today: LocalDate): Payday? {
        val s = d.settings
        if (!isHourly(s)) return null
        val from = s.lastPaydayHandled?.plusDays(1) ?: s.incomeEnabledDate ?: return null
        val day = Income.nextOccurrence(s, from) ?: return null
        if (day > today) return null
        return Payday(day, pending(d).filter { it.date <= day })
    }

    /** The next payday on or after [today]. */
    fun nextPayday(s: Settings, today: LocalDate): LocalDate? = Income.nextOccurrence(s, today)

    /**
     * What arrived over what was logged, averaged over the last [ESTIMATE_PAYDAYS] confirmed
     * hourly paydays; null before the first (SPEC R82). A ratio, not money.
     */
    fun learnedRatio(d: ProfileData): Double? {
        val paid = d.transactions
            .filter { it.isIncome && (it.hoursWorked ?: 0.0) > 0 && (it.rate ?: 0) > 0 && it.amount > 0 }
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.loggedAt })
            .take(ESTIMATE_PAYDAYS)
        if (paid.isEmpty()) return null
        return paid.map { it.amount / (it.hoursWorked!! * it.rate!!) }.average()
    }

    /** The estimate for [shifts], and whether it is still the before-deductions figure (SPEC R82). */
    fun estimate(d: ProfileData, shifts: List<Shift>): Pair<Money, Boolean> {
        val g = gross(shifts)
        val r = learnedRatio(d) ?: return g to true
        return (g * r).roundToLong() to false
    }

    fun logShift(d: ProfileData, shift: Shift): ProfileData = d.copy(shifts = d.shifts + shift.copy(profileId = d.profileId))

    fun editShift(d: ProfileData, shift: Shift): ProfileData =
        d.copy(shifts = d.shifts.map { if (it.id == shift.id) shift.copy(profileId = d.profileId) else it })

    fun removeShift(d: ProfileData, id: String): ProfileData = d.copy(shifts = d.shifts.filterNot { it.id == id })

    /**
     * The payday confirmed (SPEC R81): what arrived lands in Savings as income dated to the
     * payday, carrying the hours and the rate they were logged at, which the learned estimate
     * reads back, and the shifts it paid go.
     */
    fun confirmPayday(d: ProfileData, payday: Payday, arrived: Money, clock: Clock, txId: String): ProfileData {
        require(arrived > 0) { "a confirmed payday has an amount" }
        val hours = payday.hours
        val rate = if (hours > 0) (payday.gross / hours).roundToLong() else null
        val moved = Ledger.logAndMove(
            d,
            Movement(arrived, TransactionKey.SavingsKey, StoredLabels.SAVINGS, StoredLabels.PAYDAY, isIncome = true, date = payday.date,
                hoursWorked = hours.takeIf { it > 0 }, rate = rate),
            clock, txId,
        ).first
        return answered(moved, payday, paid = true)
    }

    /**
     * The payday answered with nothing this time (SPEC R81): with no hours logged that closes
     * it; logged hours wait for the next payday, which covers every shift dated on or before it.
     */
    fun nothingThisTime(d: ProfileData, payday: Payday): ProfileData = answered(d, payday, paid = false)

    private fun answered(d: ProfileData, payday: Payday, paid: Boolean): ProfileData {
        val ids = payday.shifts.map { it.id }.toSet()
        return d.copy(
            shifts = if (paid) d.shifts.filterNot { it.id in ids } else d.shifts,
            settings = d.settings.copy(lastPaydayHandled = payday.date),
        )
    }

    /**
     * Leaving hourly with shifts still logged (SPEC R83): what arrived for them is confirmed now,
     * dated today, or, with [arrived] null or not above zero, they are discarded.
     */
    fun settleOnSwitch(d: ProfileData, arrived: Money?, clock: Clock, txId: String): ProfileData {
        val shifts = pending(d)
        if (shifts.isEmpty()) return d
        if (arrived == null || arrived <= 0) return d.copy(shifts = emptyList())
        return confirmPayday(d, Payday(clock.today(), shifts), arrived, clock, txId)
    }

    /**
     * The type change (SPEC R79). Hourly has no automatic deposit and counts paydays after
     * [today] only, so neither one behind it nor today's is asked about; back to salaried keeps
     * SPEC R76's paydays. Shifts are settled first (SPEC R83).
     */
    fun setType(d: ProfileData, type: IncomeType, today: LocalDate): ProfileData {
        val s = d.settings
        if (s.incomeType == type) return d
        require(d.shifts.isEmpty()) { "shifts are settled before the type changes" }
        return d.copy(settings = s.copy(
            incomeType = type,
            incomeAutoDeposit = false,
            lastIncomeDepositDate = null,
            // today's payday, if it is one, belongs to the type being left
            lastPaydayHandled = if (type == IncomeType.HOURLY) today else null,
        ))
    }

    /**
     * Salaried without automatic deposit (SPEC R84): the latest payday passed since the last one
     * offered, on that payday or the day after, while no income is logged on or after it; null
     * otherwise. Older missed paydays are SPEC R78's count.
     */
    fun salariedPaydayToLog(d: ProfileData, today: LocalDate): LocalDate? {
        val s = d.settings
        if (!s.incomeEnabled || s.incomeType != IncomeType.SALARIED || s.incomeAutoDeposit) return null
        val from = s.lastPaydayHandled?.plusDays(1) ?: s.incomeEnabledDate ?: return null
        var day = Income.nextOccurrence(s, from) ?: return null
        if (day > today) return null
        // the latest passed payday: one offer, however many were missed
        var guard = 0
        while (guard++ < 400) {
            val next = Income.nextOccurrence(s, day.plusDays(1)) ?: break
            if (next > today) break
            day = next
        }
        if (day.daysTo(today) > 1) return null
        val logged = s.lastIncomeLoggedDate
        return if (logged != null && logged >= day) null else day
    }
}
