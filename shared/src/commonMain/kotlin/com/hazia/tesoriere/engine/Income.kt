package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Income.kt at 2892110.
 *
 * Income (SPEC R75-R78): the switch, the paydays of each cadence, the income received this month
 * (read by SPEC R56, R57, R68), the automatic deposits' catch-up the month cycle runs (SPEC R71),
 * and the count of days with no income logged.
 *
 * Changed from Sci-Fin: the deposit's payee and target come from StoredLabels; `noIncomeLoggedDays`
 * returns the count only, its note's wording being the design pass's (SPEC R16); `§x.y`
 * references are SPEC rule numbers.
 */

/** What a catch-up run deposited: the count of paydays and their total (SPEC R74). */
data class CatchUp(val count: Int, val total: Money)

object Income {

    /**
     * Worked out when read, never stored: the sum of the income rows dated in [today]'s month and
     * on or after the day Income was switched on (SPEC R77).
     */
    fun receivedThisMonth(d: ProfileData, today: LocalDate): Money {
        val ym = today.ymKey()
        val floor = d.settings.incomeEnabledDate
        return d.transactions
            .filter { it.isIncome && it.date.ymKey() == ym && (floor == null || it.date >= floor) }
            .sumOf { it.amount }
    }

    /** The first payday on or after [from], or null with no payday chosen (SPEC R76). */
    fun nextOccurrence(s: Settings, from: LocalDate): LocalDate? {
        val anchor = s.incomePayDate ?: return null
        return when (s.incomeCadence) {
            IncomeCadence.SEMIMONTHLY -> {
                // both days or none: a second payday not chosen does not make it monthly
                val day2 = s.incomePayDate2?.day ?: return null
                val candidates = (-1..1).flatMap { off ->
                    val base = from.plusMonthsClamped(off, 1)
                    listOf(dateWithDay(base.year, base.month.number, anchor.day), dateWithDay(base.year, base.month.number, day2))
                }.sorted()
                candidates.firstOrNull { it >= from } ?: candidates.last()
            }
            IncomeCadence.MONTHLY -> monthlyNext(anchor.day, from)
            IncomeCadence.WEEKLY, IncomeCadence.BIWEEKLY -> {
                val step = if (s.incomeCadence == IncomeCadence.WEEKLY) 7 else 14
                var d = anchor
                var guard = 0
                while (d < from && guard++ < 4000) d = d.plusDays(step)
                d
            }
        }
    }

    private fun monthlyNext(anchorDay: Int, from: LocalDate): LocalDate {
        val d = dateWithDay(from.year, from.month.number, anchorDay)
        return if (d < from) from.firstOfNextMonth().let { dateWithDay(it.year, it.month.number, anchorDay) } else d
    }

    /** Semimonthly only: the latest of the two paydays on or before [asOf], for SPEC R78's count. */
    fun mostRecentlyPassedAnchor(s: Settings, asOf: LocalDate): LocalDate? {
        if (s.incomeCadence != IncomeCadence.SEMIMONTHLY) return null
        val anchor = s.incomePayDate ?: return null
        val day2 = s.incomePayDate2?.day ?: return null
        return (-1..0).flatMap { off ->
            val base = asOf.plusMonthsClamped(off, 1)
            listOf(dateWithDay(base.year, base.month.number, anchor.day), dateWithDay(base.year, base.month.number, day2))
        }.filter { it <= asOf }.maxOrNull()
    }

    /**
     * The automatic deposits' catch-up, on the payday clock (SPEC R71): every payday passed since
     * the last deposit, or from the chosen payday when there has been none, deposits one income
     * row dated to that payday. A gap is never one lump sum.
     */
    fun runCatchUp(d: ProfileData, clock: Clock, ids: () -> String): Pair<ProfileData, CatchUp> {
        val s = d.settings
        if (!s.incomeEnabled || !s.incomeAutoDeposit || s.income <= 0 || Hourly.isHourly(s)) return d to CatchUp(0, 0)
        var cur = d
        var count = 0
        var total = 0L
        var guard = 0
        while (guard++ < 400) {
            val st = cur.settings
            val from = st.lastIncomeDepositDate?.plusDays(1) ?: st.incomePayDate ?: clock.today()
            val scheduled = nextOccurrence(st, from) ?: break
            if (scheduled > clock.today()) break
            if (st.lastIncomeDepositDate != null && scheduled <= st.lastIncomeDepositDate) break
            cur = Ledger.logAndMove(
                cur,
                Movement(st.income, TransactionKey.SavingsKey, StoredLabels.SAVINGS, StoredLabels.AUTO_DEPOSIT, isIncome = true, date = scheduled),
                clock, ids(),
            ).first
            cur = cur.copy(settings = cur.settings.copy(lastIncomeDepositDate = scheduled))
            count++; total += st.income
        }
        // the month cycle records these on its summary, only when it keeps one (SPEC R74)
        return cur to CatchUp(count, total)
    }

    /**
     * The days with no income logged, for salaried income with automatic deposit off (SPEC R78):
     * today minus the reference day, or null when no payday has passed since it. Hourly has none:
     * its payday asks instead (SPEC R81).
     */
    fun noIncomeLoggedDays(d: ProfileData, today: LocalDate): Int? {
        val s = d.settings
        if (!s.incomeEnabled || s.incomeAutoDeposit || Hourly.isHourly(s)) return null
        val ref = s.lastIncomeLoggedDate ?: s.incomeEnabledDate ?: return null
        val elapsed = ref.daysTo(today)
        return when (s.incomeCadence) {
            IncomeCadence.SEMIMONTHLY -> {
                val passed = mostRecentlyPassedAnchor(s, today) ?: return null
                if (ref >= passed) null else elapsed.takeIf { it > 0 }
            }
            IncomeCadence.MONTHLY -> {
                val due = nextOccurrence(s, ref.plusDays(1)) ?: return null
                if (due > today) null else elapsed.takeIf { it > 0 }
            }
            else -> {
                val threshold = if (s.incomeCadence == IncomeCadence.WEEKLY) 7 else 14
                elapsed.takeIf { it > threshold }
            }
        }
    }

    /**
     * The Income switch (SPEC R75). Off clears every income setting and the shifts not yet paid,
     * so switching on again starts blank; on keeps the day and takes it as the payday until
     * another is chosen. Shifts are settled before switching off (SPEC R83).
     */
    fun setEnabled(d: ProfileData, enabled: Boolean, today: LocalDate): ProfileData {
        val s = d.settings
        return d.copy(shifts = if (!enabled) emptyList() else d.shifts, settings = if (!enabled) s.copy(
            incomeEnabled = false, income = 0, incomeAutoDeposit = false,
            incomePayDate = null, incomePayDate2 = null, incomeEnabledDate = null,
            lastIncomeLoggedDate = null, lastIncomeDepositDate = null,
            incomeType = IncomeType.SALARIED, hourlyRate = 0, lastPaydayHandled = null,
        ) else s.copy(
            incomeEnabled = true, incomeEnabledDate = today,
            incomePayDate = s.incomePayDate ?: today,
        ))
    }

    /**
     * The automatic deposit switch (SPEC R71). Switched back on after an earlier period on, it
     * counts from [today]: the old marker would otherwise deposit every payday passed while it
     * was off. A first switch-on, with no marker, deposits from the chosen payday.
     */
    fun setAutoDeposit(d: ProfileData, on: Boolean, today: LocalDate): ProfileData {
        val s = d.settings
        if (s.incomeAutoDeposit == on) return d
        return d.copy(settings = s.copy(
            incomeAutoDeposit = on,
            lastIncomeDepositDate = if (on && s.lastIncomeDepositDate != null) today.minusDays(1) else s.lastIncomeDepositDate,
        ))
    }

    /**
     * A cadence change (SPEC R76): the payday is asked again, the second payday is kept only for
     * semimonthly, and the deposit marker is cleared, so no payday is counted across the change.
     */
    fun setCadence(d: ProfileData, cadence: IncomeCadence): ProfileData {
        if (d.settings.incomeCadence == cadence) return d
        return d.copy(settings = d.settings.copy(
            incomeCadence = cadence,
            incomePayDate = null,
            incomePayDate2 = if (cadence == IncomeCadence.SEMIMONTHLY) d.settings.incomePayDate2 else null,
            lastIncomeDepositDate = null,
        ))
    }
}
