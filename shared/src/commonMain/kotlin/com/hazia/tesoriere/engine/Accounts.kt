package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.GoalMode
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.ceilDiv
import kotlinx.datetime.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Accounts.kt at 2892110.
 *
 * Credit and Loan accounts (SPEC R85-R94): balance edits, quick-add, the paired Savings row, the
 * link to a recurring expense, deleting and re-typing with their cascades, the loan calculator and
 * its two outcomes, and the card payoff outlook. An Account's own rows never move Savings (SPEC R51).
 *
 * Changed from Sci-Fin:
 * - No charge direction: a charge to a card is a payment with an Account source (SPEC R22, R54,
 *   payments/Payments.kt), so `Direction` is PAYMENT and INCREASE only, and an increase is refused
 *   on a card (SPEC R89).
 * - quickAdd writes its own row note when the user gave none (`Paid off`, `Increased`, Sci-Fin's
 *   stored words), where Sci-Fin took it from its entry sentence's `rowLabel`.
 * - Every recurring expense has a category (SPEC R67): getLoan and commitFixedPayment take one and
 *   refuse a category that does not exist.
 * - Money stays in Long cents (SPEC R20). Rates are Double; each product of money and a rate is
 *   rounded to the cent at once: the annuity payment and the interest-only payment in loanMath,
 *   and each month's interest and minimum payment in the payoff walks, which therefore run on a
 *   whole-cent balance where Sci-Fin's ran on a Double one (SPEC R92). The months match Sci-Fin's;
 *   the interest can differ by a few cents. At a rate of 0 the payments are integer divisions.
 * - A fixed payment of 0 or less never clears a balance (SPEC R92); Sci-Fin divided by it.
 * - Stored text comes from StoredLabels; icon names (Sci-Fin's glyphs) are not set, as how an
 *   Account looks is the design pass's (SPEC R16).
 * - Not ported: `LoanDraft` (the calculator's starting values, the design pass's) and `monthsLabel`
 *   (wording). Sci-Fin's section references are replaced by SPEC rule numbers.
 */

/** The loan calculator's figures for one set of inputs (SPEC R90). Rates are fractions: 0.065 is 6.5 %. */
data class LoanMath(val monthly: Money, val totalRepaid: Money, val totalInterest: Money, val principalRemaining: Money, val months: Int)

/** Months to clear a card's balance at a fixed payment, and the interest on the way; both null when it is never cleared (SPEC R92). */
data class Payoff(val months: Int?, val interest: Money?)

/** The same at the minimum payment, with the first month's minimum (SPEC R92). */
data class MinimumPayoff(val months: Int?, val interest: Money?, val first: Money)

object Accounts {

    /** The minimum payment: 2 % of the balance, at least 25.00 (SPEC R92). */
    const val MIN_PAYMENT_PCT = 0.02
    const val MIN_PAYMENT_FLOOR: Money = 2500

    /** A walk stops after 1,200 months: a balance still owed then is never cleared (SPEC R92). */
    private const val WALK_LIMIT = 1200

    // ---- the loan calculator (SPEC R90, R91) ---------------------------------------------------

    /** Interest-only pays the interest alone for the term; fixed and variable amortise (SPEC R90). */
    fun loanMath(principal: Money, annualRate: Double, termMonths: Int, rateType: RateType): LoanMath {
        val p = maxOf(0L, principal)
        val n = maxOf(1, termMonths)
        val i = maxOf(0.0, annualRate) / 12.0
        if (rateType == RateType.INTEREST_ONLY) {
            val monthly = (p * i).roundToLong()
            return LoanMath(monthly, monthly * n, monthly * n, principal, n)
        }
        val monthly = if (i == 0.0) p.ceilDiv(n.toLong()) else (p * i / (1 - (1 + i).pow(-n))).roundToLong()
        return LoanMath(monthly, monthly * n, monthly * n - principal, 0, n)
    }

    /** Saving for it instead: a Goal planned by date for the principal over the term, nothing else (SPEC R91). */
    fun saveForItGoal(principal: Money, math: LoanMath, name: String, today: LocalDate, id: String, profileId: String): Goal =
        Goals.rederive(
            Goal(id, profileId, name.trim().ifEmpty { StoredLabels.SAVINGS_GOAL }, saved = 0, target = principal,
                targetDate = today.plusMonthsClamped(math.months), mode = GoalMode.DATE),
            today,
        )

    /**
     * Taking the loan (SPEC R91): a Loan and its payment in one step, linked both ways (SPEC R87),
     * the payment first due one month after today and ending on the term's last payment, so nothing
     * sweeps this month (SPEC R64); the Loan's opening row (SPEC R86). The payment's category must
     * exist (SPEC R67).
     */
    fun getLoan(
        d: ProfileData, principal: Money, annualRate: Double, termMonths: Int, rateType: RateType,
        accountName: String, expenseName: String, categoryId: String, autoUpdate: Boolean, clock: Clock, ids: () -> String,
    ): ProfileData {
        val m = loanMath(principal, annualRate, termMonths, rateType)
        val today = clock.today()
        val anchorDay = today.day
        val firstDue = today.plusMonthsClamped(1, anchorDay)
        val acctId = ids(); val expId = ids()
        val acct = Account(
            acctId, d.profileId, accountName.trim().ifEmpty { StoredLabels.LOAN }, type = AccountType.LOAN, balance = principal,
            linkedRecurringExpenseId = expId, autoUpdateFromPayments = autoUpdate, principal = principal,
            interestRate = annualRate, termMonths = m.months, rateType = rateType, originationDate = today,
        )
        val exp = RecurringExpense(
            expId, d.profileId, expenseName.trim().ifEmpty { StoredLabels.LOAN_PAYMENT }, amount = m.monthly, cadence = Cadence.MONTHLY,
            categoryId = categoryId, firstDueDate = firstDue, dueDate = firstDue, dueDateAnchorDay = anchorDay, dateMode = DateMode.FIXED_DATE,
            lastSweptMonth = today.ymKey(), endDate = today.plusMonthsClamped(m.months, anchorDay), linkedAccountId = acctId,
        )
        require(Recurring.canSave(d, exp)) { "a Loan's payment needs a category that exists: '$categoryId'" }
        val next = d.copy(accounts = d.accounts + acct, recurringExpenses = d.recurringExpenses + exp)
        return Ledger.logAccountEdit(next, acct.id, acct.name, principal, StoredLabels.OPENING_BALANCE, clock, ids())
    }

    // ---- the card payoff outlook (SPEC R92, R93) ------------------------------------------------

    /** Months to clear at a fixed payment, walked month by month in whole cents so the last payment is what is left (SPEC R92). */
    fun payoffAtFixedPayment(balance: Money, monthlyRate: Double, payment: Money): Payoff {
        if (balance <= 0) return Payoff(0, 0)
        if (payment <= 0) return Payoff(null, null)
        if (monthlyRate <= 0) return Payoff(balance.ceilDiv(payment).toInt(), 0)
        if (payment <= interestOn(balance, monthlyRate)) return Payoff(null, null)
        var bal = balance; var interest = 0L; var months = 0
        while (bal > 0 && months < WALK_LIMIT) {
            val int = interestOn(bal, monthlyRate)
            interest += int
            bal = bal + int - minOf(payment, bal + int)
            months++
        }
        if (bal > 0) return Payoff(null, null)
        return Payoff(months, interest)
    }

    /** The minimum is a share of a moving balance, so it is walked; there is no closed form (SPEC R92). */
    fun payoffAtMinimum(balance: Money, monthlyRate: Double): MinimumPayoff {
        if (balance <= 0) return MinimumPayoff(0, 0, 0)
        val first = minimumPayment(balance)
        var bal = balance; var interest = 0L; var months = 0
        while (bal > 0 && months < WALK_LIMIT) {
            val int = interestOn(bal, monthlyRate)
            val pay = minOf(bal + int, minimumPayment(bal))
            if (pay <= int) return MinimumPayoff(null, null, first)
            interest += int
            bal = bal + int - pay
            months++
        }
        if (bal > 0) return MinimumPayoff(null, null, first)
        return MinimumPayoff(months, interest, first)
    }

    /** One month's interest on [balance], rounded to the cent at once (SPEC R20). */
    private fun interestOn(balance: Money, monthlyRate: Double): Money = (balance * maxOf(0.0, monthlyRate)).roundToLong()

    private fun minimumPayment(balance: Money): Money = maxOf(MIN_PAYMENT_FLOOR, (balance * MIN_PAYMENT_PCT).roundToLong())

    /**
     * Committing a card's fixed payment (SPEC R93): a monthly recurring expense linked both ways,
     * first due one month after today, no end date; the card never follows it. The card's earlier
     * expense loses its back-link, or deleting it would take the card (SPEC R87).
     */
    fun commitFixedPayment(d: ProfileData, accountId: String, amount: Money, name: String, categoryId: String, clock: Clock, expId: String): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val today = clock.today()
        val firstDue = today.plusMonthsClamped(1, today.day)
        val e = RecurringExpense(
            expId, d.profileId, name.trim().ifEmpty { StoredLabels.fill(StoredLabels.NAMED_PAYMENT, a.name) }, amount = amount,
            cadence = Cadence.MONTHLY, categoryId = categoryId, firstDueDate = firstDue, dueDate = firstDue, dueDateAnchorDay = today.day,
            dateMode = DateMode.FIXED_DATE, lastSweptMonth = today.ymKey(), linkedAccountId = a.id,
        )
        require(Recurring.canSave(d, e)) { "a fixed payment needs a category that exists: '$categoryId'" }
        return d.copy(
            recurringExpenses = d.recurringExpenses.map { if (it.linkedAccountId == a.id) it.copy(linkedAccountId = null) else it } + e,
            accounts = d.accounts.map { if (it.id == a.id) it.copy(linkedRecurringExpenseId = e.id, autoUpdateFromPayments = false) else it },
        )
    }

    // ---- accounts (SPEC R85-R89, R94) --------------------------------------------------------------

    /** A direct balance edit: logs the change as an `acct:` row; Savings is untouched (SPEC R51, R86). */
    fun balanceEdit(d: ProfileData, accountId: String, newBalance: Money, payee: String?, clock: Clock, txId: String, date: LocalDate? = null): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val delta = newBalance - a.balance
        val next = d.copy(accounts = d.accounts.map { if (it.id == accountId) it.copy(balance = newBalance) else it })
        if (delta == 0L) return next
        return Ledger.logAccountEdit(next, a.id, a.name, delta, payee ?: StoredLabels.CORRECTION, clock, txId, date)
    }

    /** A quick-add's direction: PAYMENT lowers the balance, INCREASE (a Loan only) raises it. A charge is a payment (SPEC R22). */
    enum class Direction { PAYMENT, INCREASE }

    /** The paired Savings row is on by default unless a linked expense's sweep already moved Savings (SPEC R89). */
    fun pairedOutflowDefault(a: Account): Boolean = a.linkedRecurringExpenseId == null

    /**
     * Account quick-add (SPEC R89): one `acct:` row; on a payment with [pairSavings], the matching
     * `savings` row too, since the `acct:` row moved the balance and no Savings.
     */
    fun quickAdd(
        d: ProfileData, accountId: String, amount: Money, direction: Direction, payee: String?, pairSavings: Boolean,
        clock: Clock, date: LocalDate? = null, ids: () -> String,
    ): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        require(direction == Direction.PAYMENT || a.type == AccountType.LOAN) { "only a Loan takes an increase; a charge is a payment (SPEC R22)" }
        val signed = if (direction == Direction.PAYMENT) -amount else amount
        val note = payee ?: if (direction == Direction.PAYMENT) StoredLabels.PAID_OFF else StoredLabels.INCREASED
        var next = balanceEdit(d, accountId, a.balance + signed, note, clock, ids(), date)
        if (direction == Direction.PAYMENT && pairSavings) {
            next = Writes.savingsQuickAdd(next, -amount, payee ?: StoredLabels.fill(StoredLabels.PAYMENT_TO, a.name), false, clock, ids(), date)
        }
        return next
    }

    /** A Loan with no rate cannot follow its payment: the update has nothing to work out from (SPEC R86). */
    fun loanInputsComplete(a: Account): Boolean = a.type == AccountType.LOAN && a.interestRate != null

    /** Re-typing clears the state belonging to the type it leaves (SPEC R94). */
    fun retype(a: Account, next: AccountType): Account {
        if (a.type == next) return a
        return if (next == AccountType.CREDIT) a.copy(
            type = next, autoUpdateFromPayments = false,
            principal = null, interestRate = null, termMonths = null, rateType = null, originationDate = null,
        ) else a.copy(type = next, apr = null)
    }

    /** A Loan is finished when its linked expense is (SPEC R87, R72). */
    fun finished(d: ProfileData, a: Account): Boolean {
        if (a.type != AccountType.LOAN || a.linkedRecurringExpenseId == null) return false
        return d.recurringExpenses.firstOrNull { it.id == a.linkedRecurringExpenseId }?.finishedAt != null
    }

    /**
     * Deleting from the Account's side (SPEC R87): a closing row, the Account goes, and so does the
     * linked expense, with its return on deletion when the user took it in the same step
     * ([reverse], SPEC R66). Payments charged to the Account stay as they are (SPEC R88): the
     * closing row is minus a balance that includes them.
     */
    fun deleteCascade(d: ProfileData, accountId: String, clock: Clock, reverse: Boolean = false, ids: () -> String): ProfileData {
        val a = d.accounts.firstOrNull { it.id == accountId } ?: return d
        val linked = a.linkedRecurringExpenseId?.let { eid -> d.recurringExpenses.firstOrNull { it.id == eid } }
        var next = if (reverse && linked != null) Recurring.applyReversal(d, linked, clock, ids) else d
        next = close(next, a, clock, ids)
        a.linkedRecurringExpenseId?.let { eid -> next = next.copy(recurringExpenses = next.recurringExpenses.filter { it.id != eid }) }
        return next
    }

    /**
     * A Loan changed to a card takes its linked payment with it, with the return on deletion if
     * accepted; the Account stays, re-typed and linked to nothing (SPEC R94).
     */
    fun retypeToCreditCascade(d: ProfileData, draft: Account, reverse: Boolean, clock: Clock, ids: () -> String): ProfileData {
        val before = d.accounts.firstOrNull { it.id == draft.id } ?: return d
        val linked = before.linkedRecurringExpenseId?.let { eid -> d.recurringExpenses.firstOrNull { it.id == eid } }
        var next = if (reverse && linked != null) Recurring.applyReversal(d, linked, clock, ids) else d
        if (linked != null) next = next.copy(recurringExpenses = next.recurringExpenses.filter { it.id != linked.id })
        return AccountEditing.save(next, draft.copy(linkedRecurringExpenseId = null), clock, ids)
    }

    /** An Account leaves with a closing row for the balance it still showed (SPEC R87, R66). */
    internal fun close(d: ProfileData, a: Account, clock: Clock, ids: () -> String): ProfileData {
        val next = if (a.balance != 0L) Ledger.logAccountEdit(d, a.id, a.name, -a.balance, StoredLabels.ACCOUNT_CLOSED, clock, ids()) else d
        return next.copy(accounts = next.accounts.filter { it.id != a.id })
    }

    /** Whether saving [draft] over what is stored is a Loan changed to a card with a payment to take along (SPEC R94). */
    fun retypeNeedsCascade(d: ProfileData, draft: Account): Boolean {
        val before = d.accounts.firstOrNull { it.id == draft.id } ?: return false
        return before.type == AccountType.LOAN && draft.type == AccountType.CREDIT &&
            before.linkedRecurringExpenseId?.let { eid -> d.recurringExpenses.any { it.id == eid } } == true
    }
}
