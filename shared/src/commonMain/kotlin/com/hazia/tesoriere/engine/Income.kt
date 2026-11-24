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
 * Ported in part from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Income.kt at
 * 2892110: the income received this month, which the goal calculator reads (SPEC R56, R57), and
 * the automatic deposits' catch-up with the payday walk it needs, which the month cycle runs
 * (SPEC R71). The rest of Income (the switches, the cadence change, the no-income check, hourly
 * paydays) is ported with the Income piece.
 *
 * Changed from Sci-Fin: `Hourly.isHourly` is read here as the income type, until Hourly is
 * ported; the deposit's payee and target come from StoredLabels.
 */

/** What a catch-up run deposited: the count of paydays and their total (SPEC R74). */
data class CatchUp(val count: Int, val total: Money)

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

    /** The first payday on or after [from], or null with no payday chosen. */
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

    /**
     * The automatic deposits' catch-up, on the payday clock (SPEC R71): every payday passed since
     * the last deposit, or from the chosen payday when there has been none, deposits one income
     * row dated to that payday. A gap is never one lump sum.
     */
    fun runCatchUp(d: ProfileData, clock: Clock, ids: () -> String): Pair<ProfileData, CatchUp> {
        val s = d.settings
        if (!s.incomeEnabled || !s.incomeAutoDeposit || s.income <= 0 || s.incomeType == IncomeType.HOURLY) return d to CatchUp(0, 0)
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
}
