package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.roundToLong

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/NewMonth.kt at 2892110.
 *
 * The month cycle (SPEC R70): on each open, process every month between the last month
 * processed (or an older month a recurring expense last swept) and the current one, in order and
 * with real dates, the due dates moved on to each month's 1st first; then move them on to today.
 * A month already processed is left alone. The
 * automatic deposits catch up on their own payday clock in the same open (SPEC R71); neither
 * depends on the other.
 *
 * Changed from Sci-Fin:
 * - No category reset: spent is worked out per month (SPEC R25), so a new month writes nothing
 *   for categories. Categories carry no month marker; the Profile's `Settings.lastMonthProcessed`
 *   stands for Sci-Fin's `Category.lastResetMonth` beside the expenses' `lastSweptMonth`, so a
 *   Profile with no recurring expense still processes the months of an absence. Each open sets
 *   it to the current month; a Profile with no mark yet (none given at creation) gets one.
 * - An ended expense gives a code ([EndedKind]) in place of Sci-Fin's sentence, with its id and
 *   name as typed, and the kept summary stores `expenseId<TAB>code<TAB>name` per line in place of
 *   `name<TAB>note`, so an expense deleted since can still be named; a line of `expenseId<TAB>code`
 *   alone, or with an empty name, reads with no name (SPEC R72, R74).
 * - Each past month is processed with the due dates moved on to its 1st, not to the day of the
 *   open (SPEC R70): Sci-Fin moved them on first, so a bimonthly or yearly cycle that ended while
 *   the app was closed swept its last months at the next cycle's share. This follows Sci-Fin's
 *   spec, section 3.11, which wants a late catch-up to give the share a live rollover would.
 * - An expense finished by a Loan its payments took to 0 gives the `loanPaidOff` notice (SPEC
 *   R72, R73); Sci-Fin finished it with none.
 * - `completionNoticeFor` is `endedKind`; the closing row's note comes from StoredLabels.
 */

/** Why an expense finished, as a code; how it reads is the design pass's (SPEC R72). */
enum class EndedKind(val token: String) {
    /** No Loan linked. */
    ENDED("ended"),
    /** The linked Loan is paid off. */
    LOAN_PAID_OFF("loanPaidOff"),
    /** An interest-only Loan reached the end of its term: the principal is still owed. */
    INTEREST_ONLY_ENDED("interestOnlyEnded"),
    /** A variable-rate Loan reached the end of its term: the balance is for the user to check. */
    VARIABLE_TERM_ENDED("variableTermEnded");

    companion object { fun of(token: String): EndedKind? = entries.firstOrNull { it.token == token } }
}

/** One expense that finished: its id, why, and its name as typed; null only on a line kept with no name (SPEC R72, R74). */
data class Ended(val expenseId: String, val kind: EndedKind, val name: String? = null)

/** What one month's processing did. */
data class MonthResult(val ym: String, val sweptTotal: Money, val endedNow: List<Ended>)

/** What a catch-up run did (SPEC R74). `monthsReplayed > 1` means a real absence. */
data class CatchUpSummary(val monthsReplayed: Int, val sweptTotal: Money, val endedNow: List<Ended>, val deposits: CatchUp)

object NewMonth {

    /** A Loan its scheduled payments retire: any but interest-only and variable-rate (SPEC R72). */
    fun paysOffAtTerm(a: Account): Boolean =
        a.type == AccountType.LOAN && a.rateType != RateType.INTEREST_ONLY && a.rateType != RateType.VARIABLE

    /**
     * The code for an expense that finished (SPEC R72's table). A balance the update from
     * payments took to 0 is paid off whatever the rate; otherwise interest-only and variable-rate
     * Loans are not stated paid off.
     */
    fun endedKind(d: ProfileData, e: RecurringExpense): EndedKind {
        val acct = e.linkedAccountId?.let { id -> d.accounts.firstOrNull { it.id == id } }
        if (acct == null || acct.type != AccountType.LOAN) return EndedKind.ENDED
        val paidOffForReal = acct.autoUpdateFromPayments && acct.balance == 0L
        if (!paidOffForReal) {
            if (acct.rateType == RateType.INTEREST_ONLY) return EndedKind.INTEREST_ONLY_ENDED
            if (acct.rateType == RateType.VARIABLE) return EndedKind.VARIABLE_TERM_ENDED
        }
        return EndedKind.LOAN_PAID_OFF
    }

    /**
     * One month, once per expense: an expense already swept for it is left alone. The end date
     * finishes an expense on the month's 1st after its last sweep, with a closing row on a linked
     * Loan its payments retire (SPEC R72); a Loan updated from payments falls by the payment less
     * a month's interest, only for a sweep made in this run (SPEC R73).
     */
    fun processMonth(d: ProfileData, monthDate: LocalDate, clock: Clock, ids: () -> String): Pair<ProfileData, MonthResult> {
        val ym = monthDate.ymKey()
        val first = monthDate.firstOfMonth()
        var next = d
        var sweptTotal = 0L
        val ending = ArrayList<RecurringExpense>()
        // finished by a Loan its payments took to 0 (SPEC R73): the notice of R72, loanPaidOff
        val paidOff = ArrayList<Ended>()
        val sweptIds = HashSet<String>()

        for (id in d.recurringExpenses.map { it.id }) {
            val e = next.recurringExpenses.first { it.id == id }
            if ((e.lastSweptMonth ?: "") >= ym) continue
            val (afterSweep, swept) = Recurring.sweepOccurrences(next, id, first, clock, ids)
            next = afterSweep
            sweptTotal += swept
            sweptIds.add(id)
            var updated = next.recurringExpenses.first { it.id == id }.copy(lastSweptMonth = ym)
            if (e.endDate != null && e.finishedAt == null && first >= e.endDate) {
                ending.add(e)
                updated = updated.copy(finishedAt = first)
                e.linkedAccountId?.let { accId ->
                    next.accounts.firstOrNull { it.id == accId }?.let { acct ->
                        // interest-only and variable-rate Loans keep what they owe (SPEC R72)
                        if (acct.balance != 0L && paysOffAtTerm(acct)) {
                            next = Ledger.logAccountEdit(next, acct.id, acct.name, -acct.balance, StoredLabels.LOAN_FINISHED, clock, ids(), first)
                            next = next.copy(accounts = next.accounts.map { if (it.id == acct.id) it.copy(balance = 0) else it })
                        }
                    }
                }
            }
            next = next.copy(recurringExpenses = next.recurringExpenses.map { if (it.id == id) updated else it })
        }

        // A Loan updated from payments: the split worked out from the balance each month (SPEC R73).
        for (acct in next.accounts.filter { it.type == AccountType.LOAN && it.autoUpdateFromPayments && it.linkedRecurringExpenseId != null }) {
            val e = next.recurringExpenses.firstOrNull { it.id == acct.linkedRecurringExpenseId } ?: continue
            if (e.id !in sweptIds) continue
            if (e.finishedAt != null && e.finishedAt != first) continue
            val payment = Recurring.sweepAmountForMonth(e.copy(finishedAt = null), first.year, first.month.number)
            // a rate, not money: the product is rounded to whole cents at once (SPEC R20)
            val interest: Money = acct.interestRate?.let { (acct.balance * it / 12.0).roundToLong() } ?: 0
            val principal = maxOf(0L, minOf(acct.balance, payment - interest))
            if (principal > 0) {
                // dated to the payment's first due date in the month (the 1st for a share), named after the payment
                val on = Recurring.occurrenceDatesInMonth(e, first.year, first.month.number)?.firstOrNull() ?: first
                next = Ledger.logAccountEdit(next, acct.id, acct.name, -principal, e.name, clock, ids(), on)
                next = next.copy(accounts = next.accounts.map { if (it.id == acct.id) it.copy(balance = it.balance - principal) else it })
            }
            val nowBalance = next.accounts.first { it.id == acct.id }.balance
            if (nowBalance == 0L && e.finishedAt == null) {
                next = next.copy(recurringExpenses = next.recurringExpenses.map { if (it.id == e.id) it.copy(finishedAt = first) else it })
                paidOff.add(Ended(e.id, EndedKind.LOAN_PAID_OFF, e.name))
            }
        }
        // read after this month's payment, so a variable-rate Loan that payment cleared is paid off
        val ended = ending.map { Ended(it.id, endedKind(next, it), it.name) } + paidOff
        return next to MonthResult(ym, sweptTotal, ended)
    }

    /**
     * The month cycle, run on every open (SPEC R70): process every month after the oldest month
     * swept up to the current one, each with the due dates moved on to its 1st, so a bimonthly or
     * yearly expense sweeps the share in force that month (SPEC R62); then move the due dates on
     * to today, then the automatic deposits (SPEC R71). Returns a null summary when no month was
     * processed. The summary is kept only after two or more months, or when an expense finished
     * (SPEC R74).
     */
    fun runCatchUp(d: ProfileData, clock: Clock, ids: () -> String): Pair<ProfileData, CatchUpSummary?> {
        val today = clock.today()
        var next = d
        val nowYm = today.ymKey()
        val marks = (listOfNotNull(next.settings.lastMonthProcessed) + next.recurringExpenses.mapNotNull { it.lastSweptMonth }).sorted()
        if (marks.isEmpty() || marks.first() >= nowYm) {
            // the payday clock still runs: a payday may have passed inside the month
            val (afterIncome, _) = Income.runCatchUp(markProcessed(Recurring.advanceDueDates(next, today), nowYm), clock, ids)
            return afterIncome to null
        }
        val months = ArrayList<LocalDate>()
        var cur = LocalDate(marks.first().substring(0, 4).toInt(), marks.first().substring(5, 7).toInt(), 1)
        var guard = 0
        while (cur.ymKey() < nowYm && guard++ < 120) { cur = cur.firstOfNextMonth(); months.add(cur) }
        var swept = 0L
        val ended = ArrayList<Ended>()
        for (m in months) {
            // a due date passes from the day after it: on its own 1st it still belongs to the old cycle (SPEC R61)
            val (after, r) = processMonth(Recurring.advanceDueDates(next, m), m, clock, ids)
            next = after; swept += r.sweptTotal; ended.addAll(r.endedNow)
        }
        next = Recurring.advanceDueDates(next, today)
        val (afterIncome, deposits) = Income.runCatchUp(markProcessed(next, nowYm), clock, ids)
        val summary = CatchUpSummary(months.size, swept, ended, deposits)
        // Kept until dismissed, so a restart cannot lose it; a summary not yet read grows, and
        // only the user's dismissal clears it (SPEC R74).
        next = if (months.size > 1 || ended.isNotEmpty()) afterIncome.copy(settings = record(afterIncome.settings, summary)) else afterIncome
        return next to summary
    }

    /** The Profile marked processed up to [ym]; a later mark already kept stays (SPEC R70). */
    private fun markProcessed(d: ProfileData, ym: String): ProfileData =
        if ((d.settings.lastMonthProcessed ?: "") >= ym) d else d.copy(settings = d.settings.copy(lastMonthProcessed = ym))

    private const val FIELD = '\t'

    private fun record(s: Settings, run: CatchUpSummary): Settings {
        val base = if (s.catchUpSummaryPending) s else dismissed(s)
        // a line break in a name would split its line: it is kept as a space
        val lines = run.endedNow.map { e ->
            listOfNotNull(e.expenseId, e.kind.token, e.name?.takeIf { it.isNotEmpty() }?.replace('\r', ' ')?.replace('\n', ' ')).joinToString(FIELD.toString())
        }
        return base.copy(
            catchUpSummaryPending = true,
            catchUpMonths = base.catchUpMonths + run.monthsReplayed,
            catchUpSwept = base.catchUpSwept + run.sweptTotal,
            catchUpDepositCount = base.catchUpDepositCount + run.deposits.count,
            catchUpDepositTotal = base.catchUpDepositTotal + run.deposits.total,
            catchUpEnded = (base.catchUpEnded.lines().filter { it.isNotEmpty() } + lines).joinToString("\n"),
        )
    }

    /** The summary not yet dismissed, read back from what [runCatchUp] kept; null once dismissed. */
    fun persistedSummary(s: Settings): CatchUpSummary? {
        if (!s.catchUpSummaryPending) return null
        val ended = s.catchUpEnded.lines().filter { it.isNotEmpty() }.mapNotNull { l ->
            // the name is the rest of the line, a tab in it kept; a two-field line has none
            val f = l.split(FIELD, limit = 3)
            // an empty name, as a line `id<TAB>code<TAB>` holds, is no name
            EndedKind.of(f.getOrElse(1) { "" })?.let { Ended(f[0], it, f.getOrNull(2)?.takeIf { n -> n.isNotEmpty() }) }
        }
        return CatchUpSummary(s.catchUpMonths, s.catchUpSwept, ended, CatchUp(s.catchUpDepositCount, s.catchUpDepositTotal))
    }

    /** Dismissal clears the flag and every figure behind it, so the next summary starts from nothing. */
    fun dismissed(s: Settings): Settings =
        s.copy(catchUpSummaryPending = false, catchUpMonths = 0, catchUpSwept = 0, catchUpDepositCount = 0, catchUpDepositTotal = 0, catchUpEnded = "")
}
