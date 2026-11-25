package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.ceilDiv
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Recurring.kt at 2892110.
 *
 * Recurring expenses (SPEC R60-R67): the occurrence walk, the sweep amounts, the chain of due
 * dates, apportioned shares, the four creation cases, edit-and-reconcile, and the return on
 * deletion.
 *
 * Changed from Sci-Fin:
 * - Every recurring expense has a category (SPEC R67, owner answer 2 of 2026-11-24): `create` and
 *   `edit` require one that exists ([canSave]), and every sweep, share and accepted difference
 *   written here carries the expense's `categoryId` at that moment, so it counts in that
 *   category's spent for its month (SPEC R25). A return on deletion is filed under the category
 *   of the rows it reverses, one row per category, never the expense's category of the day (SPEC
 *   R66, owner answer of 2026-11-25).
 * - A row written while the expense is linked to a card is marked `cardPayoff`: it moves Savings
 *   and counts in no spent, since the charges it pays count through their parts (SPEC R67, R93,
 *   owner answer A of 2026-11-25). Sci-Fin counted a card's payment as spending. The mark is
 *   written, not read from the link, so a later link, release or deletion leaves past rows as
 *   they were; a return carries the mark of the rows it reverses.
 * - The rows' notes come from StoredLabels, not Sci-Fin's `stored()`.
 * - Sci-Fin's section references are replaced by SPEC rule numbers.
 */
object Recurring {

    /** Weekly 7 and biweekly 14 always; 30, 60 and 365 are interval mode's days; semimonthly has none (SPEC R60). */
    fun defaultIntervalDays(c: Cadence): Int? = when (c) {
        Cadence.WEEKLY -> 7; Cadence.BIWEEKLY -> 14; Cadence.MONTHLY -> 30; Cadence.BIMONTHLY -> 60; Cadence.YEARLY -> 365
        Cadence.SEMIMONTHLY -> null
    }

    /** The months in one full cycle: 2 for bimonthly, 12 for yearly, 1 otherwise. */
    fun cycleLength(c: Cadence): Int = when (c) { Cadence.BIMONTHLY -> 2; Cadence.YEARLY -> 12; else -> 1 }

    /** Bimonthly and yearly set aside a share each month instead of sweeping due dates (SPEC R62). */
    val Cadence.apportioned: Boolean get() = this == Cadence.BIMONTHLY || this == Cadence.YEARLY

    private fun stepDays(e: RecurringExpense): Int = e.intervalDays ?: defaultIntervalDays(e.cadence) ?: 30

    /**
     * Whether [draft] can be saved: it names a category that exists (SPEC R67). A caller keeps its
     * Save off until this holds; [create] and [edit] refuse a draft that fails it.
     */
    fun canSave(d: ProfileData, draft: RecurringExpense): Boolean =
        draft.categoryId.isNotBlank() && d.categories.any { it.id == draft.categoryId }

    /**
     * The due dates in one month, ascending, never before `firstDueDate` and never after
     * `endDate` (SPEC R61, R63). One walk for every cadence of a month or less; bimonthly and
     * yearly return null: they set aside shares, not due dates.
     */
    fun occurrenceDatesInMonth(e: RecurringExpense, year: Int, month: Int): List<LocalDate>? {
        if (e.cadence.apportioned) return null
        val monthStart = LocalDate(year, month, 1)
        val monthEnd = monthStart.firstOfNextMonth()
        val floor = e.firstDueDate
        val cap = e.endDate
        val out = ArrayList<LocalDate>()
        fun keep(d: LocalDate) { if (d >= monthStart && d < monthEnd && d >= floor && (cap == null || d <= cap)) out.add(d) }

        // Fixed calendar days: semimonthly's pair, and monthly in fixed-date mode. The anchor
        // day is read from dueDateAnchorDay, never from wherever the last due date landed.
        if (e.cadence == Cadence.SEMIMONTHLY || (e.cadence == Cadence.MONTHLY && e.dateMode != DateMode.INTERVAL)) {
            val days = if (e.cadence == Cadence.SEMIMONTHLY) listOfNotNull(floor.day, e.dueDate2?.day)
                       else listOf(e.dueDateAnchorDay ?: floor.day)
            days.forEach { keep(dateWithDay(year, month, it)) }
            return out.sorted()
        }

        // Interval chain from firstDueDate. A 30-day interval can put two due dates in one
        // calendar month; that is real, not a rounding error (SPEC R60).
        val step = stepDays(e)
        var cursor: LocalDate
        if (floor >= monthStart) {
            if (floor >= monthEnd) return emptyList()            // first due date in a later month
            cursor = floor
        } else {
            // Jump rather than step, so an expense years old costs the same as a new one.
            cursor = floor.plusDays((floor.daysTo(monthStart) / step) * step)
            if (cursor < monthStart) cursor = cursor.plusDays(step)
        }
        var guard = 0
        while (cursor < monthEnd && guard++ < 200) { keep(cursor); cursor = cursor.plusDays(step) }
        return out
    }

    /**
     * What one month sweeps for this expense (SPEC R63): the held share for bimonthly and yearly
     * (never the amount over a shrinking divisor), the amount times the due dates otherwise, 0 once
     * finished. A share stops after the last due date `endDate` allows: no cycle follows it.
     */
    fun sweepAmountForMonth(e: RecurringExpense, year: Int, month: Int): Money {
        if (e.finishedAt != null) return 0
        if (e.cadence.apportioned && e.endDate != null && LocalDate(year, month, 1) > e.dueDate &&
            nextOccurrenceAfter(e, e.dueDate) > e.endDate) return 0
        return when (e.cadence) {
            Cadence.BIMONTHLY -> e.apportionedShare ?: e.amount.ceilDiv(2)
            Cadence.YEARLY -> e.apportionedShare ?: e.amount.ceilDiv(12)
            else -> e.amount * (occurrenceDatesInMonth(e, year, month)?.size ?: 0)
        }
    }

    /**
     * The due dates from [from] to [to], both included, ascending (SPEC R61): never before the
     * first due date, never after the end date. Bimonthly and yearly expenses fall due too, though
     * they sweep shares; a statement row of the bill is read against these (SPEC R107).
     */
    fun dueDatesBetween(e: RecurringExpense, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to < from) return emptyList()
        if (!e.cadence.apportioned) {
            val out = ArrayList<LocalDate>()
            var m = from.firstOfMonth()
            while (m <= to) {
                occurrenceDatesInMonth(e, m.year, m.month.number)?.filterTo(out) { it >= from && it <= to }
                m = m.firstOfNextMonth()
            }
            return out
        }
        val out = ArrayList<LocalDate>()
        var cur = e.firstDueDate
        var guard = 0
        while (cur <= to && (e.endDate == null || cur <= e.endDate) && guard++ < 2000) {
            if (cur >= from) out.add(cur)
            cur = nextOccurrenceAfter(e, cur)
        }
        return out
    }

    /** The month's scheduled total: what is committed, not what has swept so far (SPEC R63, R56, R68). */
    fun scheduledTotal(d: ProfileData, today: LocalDate): Money =
        d.recurringExpenses.sumOf { sweepAmountForMonth(it, today.year, today.month.number) }

    // ---- the chain of due dates (SPEC R61) ---------------------------------------------------

    /** The due date strictly after [d] on this expense's chain. */
    fun nextOccurrenceAfter(e: RecurringExpense, d: LocalDate): LocalDate {
        if (e.cadence == Cadence.SEMIMONTHLY) {
            val days = listOfNotNull(e.firstDueDate.day, e.dueDate2?.day).sorted()
            if (days.isEmpty()) return d.plusDays(15)
            val next = d.firstOfNextMonth()
            val cands = days.map { dateWithDay(d.year, d.month.number, it) } + days.map { dateWithDay(next.year, next.month.number, it) }
            return cands.firstOrNull { it > d } ?: d.plusDays(15)
        }
        if (e.dateMode == DateMode.FIXED_DATE) {
            val months = cycleLength(e.cadence)
            // Month arithmetic from day 1, then the kept anchor day clamped: 31 January
            // + 1 month lands on 28 February and returns to the 31st in March.
            return d.plusMonthsClamped(months, e.dueDateAnchorDay ?: d.day)
        }
        return d.plusDays(stepDays(e))
    }

    /**
     * The semimonthly pair as an editor shows it: the month's two days in calendar order, each as
     * its next date on the chain (the live `dueDate` alternates between them).
     */
    fun semimonthlyFields(e: RecurringExpense): Pair<LocalDate, LocalDate> {
        val second = e.dueDate2 ?: return e.dueDate to e.dueDate
        val (early, late) = listOf(e.firstDueDate.day, second.day).sorted()
        fun nextOn(day: Int): LocalDate {
            val here = dateWithDay(e.dueDate.year, e.dueDate.month.number, day)
            if (here >= e.dueDate) return here
            val next = e.dueDate.firstOfNextMonth()
            return dateWithDay(next.year, next.month.number, day)
        }
        return nextOn(early) to nextOn(late)
    }

    /**
     * The two fields back onto the expense: the earlier date is the chain's next due date, the
     * later one the second day. The chain reads one day from `firstDueDate`, so when the earlier
     * date's day is not that one, `firstDueDate` moves to it too; otherwise editing one field
     * could leave both days the same.
     */
    fun withSemimonthlyFields(e: RecurringExpense, a: LocalDate, b: LocalDate): RecurringExpense {
        val (lo, hi) = if (a <= b) a to b else b to a
        return e.copy(firstDueDate = if (e.firstDueDate.day == lo.day) e.firstDueDate else lo, dueDate = lo, dueDate2 = hi)
    }

    /** One cycle back from the live due date, or null before the first due date. */
    fun previousOccurrence(e: RecurringExpense): LocalDate? {
        val d = e.dueDate
        val prev = if (e.dateMode == DateMode.FIXED_DATE) {
            d.plusMonthsClamped(-cycleLength(e.cadence), e.dueDateAnchorDay ?: d.day)
        } else d.minusDays(stepDays(e))
        return if (prev < e.firstDueDate) null else prev
    }

    /** A due date entered in the past moves forward until it lands today or later (SPEC R61). */
    fun normalisedNextDue(e: RecurringExpense, today: LocalDate): LocalDate {
        var d = e.dueDate
        val anchored = e.copy(dueDateAnchorDay = e.dueDateAnchorDay ?: d.day, intervalDays = e.intervalDays ?: defaultIntervalDays(e.cadence))
        var guard = 0
        while (d < today && guard++ < 600) d = nextOccurrenceAfter(anchored, d)
        return d
    }

    /**
     * The apportioned share (SPEC R62): the amount over the number of 1sts of a month strictly
     * after [from] and on or before the due date, at least 1, rounded up to the cent so the whole
     * amount is set aside when it falls due. [from] is when the cycle began: today at creation,
     * the previous due date when the chain moves on.
     */
    fun apportionedShareFor(e: RecurringExpense, from: LocalDate): Money? {
        if (!e.cadence.apportioned) return null
        var sweep = from.firstOfNextMonth()
        var count = 0
        var guard = 0
        while (sweep <= e.dueDate && guard++ < 400) { count++; sweep = sweep.firstOfNextMonth() }
        return e.amount.ceilDiv(maxOf(1, count).toLong())
    }

    /**
     * `dueDate` moves on once its day has passed, from the start of the next day, never when a
     * sweep runs (SPEC R61). The share is worked out again only when the due date moved, from the
     * previous due date (SPEC R62). The chain stops at the last due date on or before `endDate`;
     * finishing the expense is the month boundary's.
     */
    fun advanceDueDates(d: ProfileData, today: LocalDate): ProfileData = d.copy(recurringExpenses = d.recurringExpenses.map { e ->
        if (e.finishedAt != null) return@map e
        var cur = if (e.dueDate < e.firstDueDate) e.firstDueDate else e.dueDate
        var guard = 0
        while (cur < today && guard++ < 600) {
            val nxt = nextOccurrenceAfter(e, cur)
            if (e.endDate != null && nxt > e.endDate) break
            cur = nxt
        }
        val advanced = cur != e.dueDate
        var out = e.copy(dueDate = cur)
        if (advanced && e.cadence.apportioned) {
            out = out.copy(apportionedShare = apportionedShareFor(out, previousOccurrence(out) ?: today))
        }
        out
    })

    // ---- creation, edit, deletion --------------------------------------------------------------

    /** Whether an edit is a new plan for the share: it moved the amount, the schedule or the end date (SPEC R62). */
    fun planChanged(before: RecurringExpense, after: RecurringExpense): Boolean =
        scheduleChanged(before, after) || before.amount != after.amount || before.endDate != after.endDate

    /**
     * The fields derived from what the user entered, in one place: `dateMode` dropped on the
     * cadences that have none (SPEC R60), `firstDueDate` and the anchor day taken from the entered
     * date, the interval set, and `dueDate` moved forward (SPEC R61). Creation runs it, and so does
     * an edit that moved a date, the mode or the cadence (SPEC R65).
     */
    fun derivePlan(entered: RecurringExpense, today: LocalDate, firstDue: LocalDate = entered.dueDate): RecurringExpense {
        val dateMode = if (!entered.cadence.monthAnchored) null else entered.dateMode
        val e = entered.copy(
            dateMode = dateMode,
            firstDueDate = firstDue,
            dueDateAnchorDay = if (dateMode == DateMode.FIXED_DATE) entered.dueDate.day else null,
            intervalDays = if (dateMode == DateMode.INTERVAL) defaultIntervalDays(entered.cadence) else null,
        )
        return e.copy(dueDate = normalisedNextDue(e, today))
    }

    /** Whether an edit moved what [derivePlan] derives from. */
    private fun scheduleChanged(before: RecurringExpense, after: RecurringExpense): Boolean =
        before.cadence != after.cadence || before.dueDate != after.dueDate || before.dueDate2 != after.dueDate2 || before.dateMode != after.dateMode

    /**
     * Creation (SPEC R64): `firstDueDate` keeps the entered date, the anchor day and interval are
     * set once, the share is worked out against the moved-forward due date, and one of the four
     * cases decides whether anything sweeps at once. All four mark the month as swept. The draft
     * must name a category that exists (SPEC R67).
     */
    fun create(d: ProfileData, draft: RecurringExpense, clock: Clock, ids: () -> String): ProfileData {
        require(canSave(d, draft)) { "a recurring expense needs a category that exists: '${draft.categoryId}'" }
        val today = clock.today()
        var e = derivePlan(draft.copy(profileId = d.profileId, finishedAt = null, lastSweptMonth = null), today)
        e = e.copy(apportionedShare = apportionedShareFor(e, today))

        var next = d.copy(recurringExpenses = d.recurringExpenses + e)
        val ym = today.ymKey()
        val firstDue = e.firstDueDate
        when {
            e.cadence.apportioned -> Unit                                   // never sweeps on creation
            firstDue.ymKey() > ym -> Unit                                   // first due in a later month
            firstDue >= today -> next = sweepOccurrences(next, e.id, today, clock, ids).first   // still ahead this month
            else -> Unit                                                    // already passed: no back-sweep
        }
        return next.copy(recurringExpenses = next.recurringExpenses.map { if (it.id == e.id) it.copy(lastSweptMonth = ym) else it })
    }

    /** What an edit may leave to settle: the difference this month's sweep would have under the new settings. */
    data class Reconcile(val expenseId: String, val delta: Money, val occurrenceDate: LocalDate)

    /**
     * Edit-and-reconcile (SPEC R65): the new settings are stored; if this month has swept and its
     * scheduled amount moved, the difference is offered (never applied here) with the date of the
     * due date it adjusts. The share is worked out again only when the plan changed (SPEC R62). A
     * changed category applies to rows written from here on (SPEC R67).
     */
    fun edit(d: ProfileData, edited: RecurringExpense, today: LocalDate): Pair<ProfileData, Reconcile?> {
        require(canSave(d, edited)) { "a recurring expense needs a category that exists: '${edited.categoryId}'" }
        val before = d.recurringExpenses.first { it.id == edited.id }
        val sweptThisMonth = before.lastSweptMonth == today.ymKey()
        val beforeAmt = if (sweptThisMonth) sweepAmountForMonth(before, today.year, today.month.number) else 0
        var e = edited.copy(profileId = d.profileId)
        // the chain starts again from the date entered; only a semimonthly edit of the second date alone keeps the first
        if (scheduleChanged(before, e)) {
            val secondOnly = e.dueDate == before.dueDate && e.firstDueDate == before.firstDueDate && e.cadence == before.cadence && e.dateMode == before.dateMode
            e = derivePlan(e, today, firstDue = if (secondOnly) before.firstDueDate else e.dueDate)
        }
        e = if (e.cadence.apportioned) {
            if (planChanged(before, e)) e.copy(apportionedShare = apportionedShareFor(e, today)) else e.copy(apportionedShare = before.apportionedShare)
        } else e.copy(apportionedShare = null)
        val next = d.copy(recurringExpenses = d.recurringExpenses.map { if (it.id == e.id) e else it })
        if (!sweptThisMonth) return next to null
        val afterAmt = sweepAmountForMonth(e, today.year, today.month.number)
        val delta = afterAmt - beforeAmt
        if (delta == 0L) return next to null
        return next to Reconcile(e.id, delta, reconcileOccurrenceDate(e, today))
    }

    /** The month's earliest due date not yet passed, else its last; bimonthly and yearly stay on the 1st (SPEC R65). */
    fun reconcileOccurrenceDate(e: RecurringExpense, today: LocalDate): LocalDate {
        val dates = occurrenceDatesInMonth(e, today.year, today.month.number)
        if (dates.isNullOrEmpty()) return today.firstOfMonth()
        return dates.firstOrNull { it >= today } ?: dates.last()
    }

    /**
     * Applies an accepted difference: one `rec:` row for it, dated to the due date it adjusts and
     * filed under the expense's category. On an expense linked to a Loan the tracked debt follows
     * the Savings movement, with its own `acct:` row on the same date: a further 200.00 taken
     * lowers the debt by 200.00 (SPEC R65).
     */
    fun applyReconcile(d: ProfileData, r: Reconcile, clock: Clock, ids: () -> String): ProfileData {
        val e = d.recurringExpenses.first { it.id == r.expenseId }
        var next = Ledger.logAndMove(d, row(d, e, r.delta, StoredLabels.EDIT_RECONCILIATION, r.occurrenceDate), clock, ids()).first
        next.accounts.firstOrNull { it.id == e.linkedAccountId && it.type == AccountType.LOAN }?.let { loan ->
            next = Accounts.balanceEdit(next, loan.id, loan.balance - r.delta, StoredLabels.EDIT_RECONCILIATION, clock, ids(), r.occurrenceDate)
        }
        return next
    }

    /**
     * What a deletion offers back is what the ledger recorded, the sum of the expense's `rec:` rows
     * for due dates not yet passed, never the expense's `amount` (SPEC R66). Bimonthly and yearly:
     * the shares swept so far in the current cycle. A finished expense offers 0.
     */
    fun reversalAmount(d: ProfileData, e: RecurringExpense, today: LocalDate): Money =
        reversedRows(d, e, today).sumOf { it.amount }

    /** The expense's `rec:` rows a deletion offers back (SPEC R66); none for a finished expense. */
    private fun reversedRows(d: ProfileData, e: RecurringExpense, today: LocalDate): List<Transaction> {
        if (e.finishedAt != null) return emptyList()
        val rows = d.transactions.filter { it.key == TransactionKey.RecurringKey(e.id).stored }
        if (e.cadence.apportioned) {
            val prev = previousOccurrence(e)
            return rows.filter { prev == null || it.date > prev }
        }
        // A due date has passed from the start of the next day: due today is still ahead (SPEC R61).
        return rows.filter { it.date >= today }
    }

    /**
     * The return on deletion: `rec:` rows of the opposite sign, dated today, one per category the
     * reversed rows were filed under, in the order those rows were logged (SPEC R66, R67).
     * Nothing is written unless the whole return is above zero.
     */
    fun applyReversal(d: ProfileData, e: RecurringExpense, clock: Clock, ids: () -> String): ProfileData {
        val rows = reversedRows(d, e, clock.today())
        if (rows.sumOf { it.amount } <= 0) return d
        var next = d
        // a card payoff's rows are returned as a card payoff, counted in no spent either (SPEC R67)
        for ((filedAs, filed) in rows.groupBy { it.categoryId to it.cardPayoff }) {
            val back = filed.sumOf { it.amount }
            if (back == 0L) continue
            val (categoryId, cardPayoff) = filedAs
            val m = Movement(-back, TransactionKey.RecurringKey(e.id), e.name, StoredLabels.DELETION_REVERSAL, categoryId = categoryId, cardPayoff = cardPayoff)
            next = Ledger.logAndMove(next, m, clock, ids()).first
        }
        return next
    }

    /**
     * Delete, optionally returning the sweeps for due dates not yet passed; the earlier rows stay
     * in the ledger. A linked Account goes too, with its closing row (SPEC R66).
     */
    fun delete(d: ProfileData, expenseId: String, reverse: Boolean, clock: Clock, ids: () -> String): ProfileData {
        val e = d.recurringExpenses.firstOrNull { it.id == expenseId } ?: return d
        var next = if (reverse) applyReversal(d, e, clock, ids) else d
        e.linkedAccountId?.let { accId ->
            next.accounts.firstOrNull { it.id == accId }?.let { acct -> next = Accounts.close(next, acct, clock, ids) }
        }
        return next.copy(recurringExpenses = next.recurringExpenses.filter { it.id != expenseId })
    }

    /**
     * Sweeps one month (SPEC R63): each due date writes its own row, dated to that due date; a
     * share is one row dated to the 1st. Every row is filed under the expense's category (SPEC
     * R67). Returns the new state and the total swept.
     */
    fun sweepOccurrences(d: ProfileData, expenseId: String, monthDate: LocalDate, clock: Clock, ids: () -> String): Pair<ProfileData, Money> {
        val e = d.recurringExpenses.first { it.id == expenseId }
        val dates = occurrenceDatesInMonth(e, monthDate.year, monthDate.month.number)
        var next = d
        if (dates == null) {
            val share = sweepAmountForMonth(e, monthDate.year, monthDate.month.number)
            if (share > 0) next = Ledger.logAndMove(next, row(next, e, share, StoredLabels.RECURRING_SWEEP, monthDate.firstOfMonth()), clock, ids()).first
            return next to share
        }
        if (e.finishedAt != null) return next to 0
        var total = 0L
        for (day in dates) {
            next = Ledger.logAndMove(next, row(next, e, e.amount, StoredLabels.RECURRING_SWEEP, day), clock, ids()).first
            total += e.amount
        }
        return next to total
    }

    /**
     * A `rec:` movement for [e], filed under its category as it stands (SPEC R67); while [e] is
     * linked to a card it is a card payoff, counted in no spent (SPEC R67, R93).
     */
    private fun row(d: ProfileData, e: RecurringExpense, amount: Money, note: String, date: LocalDate?) =
        Movement(amount, TransactionKey.RecurringKey(e.id), e.name, note, date = date, categoryId = e.categoryId, cardPayoff = paysCard(d, e))

    /** Whether [e] is a card's payment (SPEC R93): linked to an Account of type credit. */
    fun paysCard(d: ProfileData, e: RecurringExpense): Boolean =
        e.linkedAccountId != null && d.accounts.any { it.id == e.linkedAccountId && it.type == AccountType.CREDIT }
}
