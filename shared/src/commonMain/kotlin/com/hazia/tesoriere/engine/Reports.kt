@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Reports.kt at 2892110.
 *
 * The four reports (SPEC R95-R101), each worked out from the ledger and the payments, nothing stored.
 *
 * Changed from Sci-Fin:
 * - Every expense is a payment, which writes no ledger row (SPEC R54). The movements of Savings
 *   are the ledger rows by their Savings effect plus the payments from Savings, each out by its
 *   total (SPEC R95, [savingsMovements]); a payment from a Goal or charged to an Account is none.
 * - Spending counts what was spent, composed by category from Spent.of (SPEC R25): the parts of
 *   payments from any source, so a split payment counts in each of its categories, the `cat:`
 *   adjustments, and the sweep rows filed under the category (SPEC R67). Sci-Fin's slice per
 *   recurring expense and its Goals slice are gone, and `savings` rows count in no slice: money
 *   into a Goal or paid off an Account is a transfer, counted when a payment spends it (owner,
 *   2026-11-25). Parts and sweep rows with no category (both only from a Sci-Fin backup) are
 *   Uncategorised (SPEC R99).
 * - A slice is a code and an id, not a label and an icon: names are the caller's to look up and
 *   how they read is the design pass's (SPEC R16).
 * - Savings vs Owed's trend adds the payments charged to an Account to what is owed and the
 *   payments from Savings to the Savings side (SPEC R100).
 * - Income vs Expenses' expenses are the month's Spending total: every payment's parts, from any
 *   source, the `cat:` adjustments and the sweep rows; transfers into Goals and off Accounts and
 *   Sci-Fin's `spend:` rows are not counted, since a Sci-Fin goal spend arrives as a payment
 *   (SPEC R19, R101). It returns its figures whatever the Income switch says: showing the report only while
 *   Income is on (SPEC R18) is the caller's, so the return type is no longer nullable.
 * - Coverage divides in whole cents with a half rounded to the even day, the result Sci-Fin's
 *   Double division and `round` give, with no floating point on money (SPEC R20, R98). No other
 *   figure here touches a Double.
 */

/** What a Spending slice holds (SPEC R99). */
enum class SliceKind { CATEGORY, UNCATEGORISED }

/** One slice of the month's Spending (SPEC R99); [categoryId] is set for a [SliceKind.CATEGORY] slice only. */
data class Slice(val kind: SliceKind, val categoryId: String?, val amount: Money)

/** The month's Spending, largest slice first. [plainList] when shares would describe nothing (SPEC R99). */
data class SpendingComposition(val slices: List<Slice>, val net: Money) {
    val anyNegative: Boolean get() = slices.any { it.amount < 0 }
    val plainList: Boolean get() = anyNegative || net <= 0
}

/** One month's point of a trend: days for Age of Money (SPEC R97), cents for Savings vs Owed (SPEC R100). */
data class MonthPoint(val month: LocalDate, val value: Long)

data class IncomeExpensePoint(val month: LocalDate, val income: Money, val expenses: Money)

/** One movement of Savings as the reports read it (SPEC R95): signed, positive in. */
data class SavingsMovement(val date: LocalDate, val loggedAt: Instant, val effect: Money)

object Reports {

    /**
     * Every movement of Savings, in replay order: date, then the instant it was logged (SPEC R95).
     * Ledger rows by their Savings effect (SPEC R51), zero effects left out; payments from Savings
     * as money out by their total (SPEC R54).
     */
    fun savingsMovements(d: ProfileData): List<SavingsMovement> {
        val rows = d.transactions.mapNotNull { t ->
            Ledger.savingsEffect(t).takeIf { it != 0L }?.let { SavingsMovement(t.date, t.loggedAt, it) }
        }
        val paid = d.payments.filter { it.source == PaymentSource.Savings }.map { SavingsMovement(it.date, it.loggedAt, -it.total) }
        return (rows + paid).sortedWith(compareBy({ it.date }, { it.loggedAt }))
    }

    // ---- Age of Money (SPEC R96, R97) --------------------------------------------------------

    /**
     * Oldest money first: every movement in opens a parcel dated to its day, every movement out
     * empties the oldest parcels first. Money out beyond every parcel is carried as a shortfall the
     * next movements in fill before they open a parcel, so the parcels add up to the replayed
     * Savings at every step; while a shortfall is left or no parcel holds money, the figure is 0.
     */
    fun ageOfMoney(d: ProfileData, asOf: LocalDate): Int {
        val lots = ArrayList<Pair<LocalDate, Money>>()
        var deficit = 0L
        for (m in savingsMovements(d)) {
            if (m.date > asOf) break
            val effect = m.effect
            if (effect > 0) {
                val absorbed = minOf(deficit, effect)
                deficit -= absorbed
                if (effect > absorbed) lots.add(m.date to (effect - absorbed))
            } else {
                var toConsume = -effect
                for (i in lots.indices) {
                    if (toConsume <= 0) break
                    val (day, rem) = lots[i]
                    val take = minOf(rem, toConsume)
                    lots[i] = day to (rem - take)
                    toConsume -= take
                }
                deficit += toConsume
            }
        }
        if (deficit > 0) return 0
        val oldest = lots.firstOrNull { it.second > 0 } ?: return 0
        return maxOf(0, oldest.first.daysTo(asOf))
    }

    /**
     * One point per month, each read on that month's last day, the current month's on [today]
     * (SPEC R97). A month that ended before the first movement in has no point; a 0 after it stays.
     */
    fun ageOfMoneyTrend(d: ProfileData, today: LocalDate, months: Int = 6): List<MonthPoint> {
        val opening = savingsMovements(d).firstOrNull { it.effect > 0 }?.date
        return (months - 1 downTo 0).mapNotNull { i ->
            val p = today.plusMonthsClamped(-i, 1)
            val asOf = if (i == 0) today else p.firstOfNextMonth().minusDays(1)
            if (i > 0 && (opening == null || asOf < opening)) null
            else MonthPoint(p, ageOfMoney(d, asOf).toLong())
        }
    }

    // ---- Coverage (SPEC R98) -----------------------------------------------------------------

    /** The last three complete calendar months, [start] inclusive, [end] exclusive (SPEC R98). */
    data class Window(val start: LocalDate, val end: LocalDate) { val days: Int get() = maxOf(1, start.daysTo(end)) }

    fun trailingSpendWindow(today: LocalDate): Window {
        val end = today.firstOfMonth()
        return Window(end.plusMonthsClamped(-3, 1), end)
    }

    /**
     * Days Savings would last at the window's daily outflow, or null when there is no answer:
     * Savings unset, a ledger and payments younger than the window, or nothing out in it. 0 with
     * Savings at or below zero (SPEC R98).
     */
    fun coverageDaysOrNull(d: ProfileData, today: LocalDate): Int? {
        if (d.savings.amount == null) return null
        val w = trailingSpendWindow(today)
        val earliest = (d.transactions.map { it.date } + d.payments.map { it.date }).minOrNull() ?: return null
        if (earliest > w.start) return null
        val outflow = savingsMovements(d).filter { it.date >= w.start && it.date < w.end && it.effect < 0 }.sumOf { -it.effect }
        if (outflow <= 0) return null
        if (d.savingsAmount <= 0) return 0
        return roundHalfEven(d.savingsAmount * w.days, outflow).toInt()
    }

    /** [num] / [den] for positive operands, to the nearest whole number, a half to the even one. */
    private fun roundHalfEven(num: Long, den: Long): Long {
        val q = num / den
        val twice = 2 * (num % den)
        return q + when {
            twice > den -> 1
            twice < den -> 0
            else -> q % 2
        }
    }

    // ---- Spending (SPEC R99) -----------------------------------------------------------------

    /**
     * The current month by slice: each category's spent figure (SPEC R25), and Uncategorised for
     * parts and sweeps with no category. `goal:`, `savings`, `acct:` and `spend:` rows are in no
     * slice: transfers, income and corrections, not spending. Zero slices are left out; the rest
     * go largest first, ties in the categories' order.
     */
    fun spendingComposition(d: ProfileData, today: LocalDate): SpendingComposition {
        val shown = spendingSlices(d, today.ymKey()).filter { it.amount != 0L }.sortedByDescending { it.amount }
        return SpendingComposition(shown, shown.sumOf { it.amount })
    }

    /** What was spent in [ym] (`YYYY-MM`), one slice per category seen and one for Uncategorised (SPEC R99). */
    private fun spendingSlices(d: ProfileData, ym: String): List<Slice> {
        val payments = d.payments.filter { it.date.ymKey() == ym }
        val rows = d.transactions.filter { it.date.ymKey() == ym }

        val categoryIds = LinkedHashSet<String>()
        d.categories.forEach { categoryIds += it.id }
        payments.forEach { p -> p.parts.forEach { part -> part.categoryId?.let { categoryIds += it } } }
        var uncategorised = payments.sumOf { p -> p.parts.filter { it.categoryId == null }.sumOf { it.amount } }
        for (t in rows) {
            when (val k = TransactionKey.parse(t.key)) {
                is TransactionKey.CategoryKey -> categoryIds += k.id
                is TransactionKey.RecurringKey -> if (t.categoryId != null) categoryIds += t.categoryId else uncategorised += t.amount
                is TransactionKey.GoalKey, TransactionKey.SavingsKey, is TransactionKey.AccountKey, is TransactionKey.GoalSpendKey -> Unit
            }
        }
        return categoryIds.map { Slice(SliceKind.CATEGORY, it, Spent.of(d, it, ym)) } + Slice(SliceKind.UNCATEGORISED, null, uncategorised)
    }

    // ---- Savings vs Owed (SPEC R100) ---------------------------------------------------------

    /** Savings less every Account's balance; money held in Goals is not counted. */
    fun savingsVsOwedNow(d: ProfileData): Money = d.savingsAmount - d.accounts.sumOf { it.balance }

    /**
     * One point per month as of its last day, replayed from zero: the movements of Savings, and
     * what is owed, the `acct:` rows and the payments charged to an Account.
     */
    fun savingsVsOwedTrend(d: ProfileData, today: LocalDate, months: Int = 6): List<MonthPoint> {
        val moves = savingsMovements(d)
        return (months - 1 downTo 0).map { i ->
            val p = today.plusMonthsClamped(-i, 1)
            val asOf = p.firstOfNextMonth().minusDays(1)
            val savings = moves.filter { it.date <= asOf }.sumOf { it.effect }
            val owedRows = d.transactions.filter { it.date <= asOf && TransactionKey.parse(it.key) is TransactionKey.AccountKey }.sumOf { it.amount }
            val charged = d.payments.filter { it.date <= asOf && it.source is PaymentSource.Account }.sumOf { it.total }
            MonthPoint(p, savings - owedRows - charged)
        }
    }

    // ---- Income vs Expenses (SPEC R101) ------------------------------------------------------

    /**
     * Per month: the income rows, and as expenses what was spent, the month's Spending total
     * (SPEC R99): every payment's parts from any source, the `cat:` adjustments and the sweep rows.
     * Money into a Goal or paid off an Account counts once, when a payment spends it. Shown only
     * while Income is on (SPEC R18): that is the caller's.
     */
    fun incomeVsExpenses(d: ProfileData, today: LocalDate, months: Int = 6): List<IncomeExpensePoint> =
        (months - 1 downTo 0).map { i ->
            val p = today.plusMonthsClamped(-i, 1)
            val ym = p.ymKey()
            val income = d.transactions.filter { it.date.ymKey() == ym && it.isIncome }.sumOf { it.amount }
            IncomeExpensePoint(p, income, spendingSlices(d, ym).sumOf { it.amount })
        }
}
