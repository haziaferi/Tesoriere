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
import com.hazia.tesoriere.payments.PaymentSource
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
 *   payments/Payments.kt), so `Direction` is PAYMENT and INCREASE only. An increase on a card is
 *   its interest or fees, filed under a category, and refused with none (SPEC R89, R110, owner
 *   answer E of 2026-11-25).
 * - quickAdd writes its own row note when the user gave none (`Paid off`, `Increased`, Sci-Fin's
 *   stored words), where Sci-Fin took it from its entry sentence's `rowLabel`.
 * - A payment off an Account takes its money from Savings, a Goal or nowhere ([PayoffFrom]), where
 *   Sci-Fin had a Savings pair on or off; from a Goal it logs a `spend:` row, at most what the
 *   Goal holds (SPEC R51, R89, owner answer I of 2026-11-25).
 * - A card can be one whose charges are not logged (SPEC R109, owner answer D of 2026-11-25): the
 *   `savings` or `spend:` row of a payoff is then filed under its payoff category, written so
 *   with the row; changing a card to a Loan clears the switch (SPEC R94).
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

    /**
     * A direct balance edit: logs the change as an `acct:` row; Savings is untouched (SPEC R51,
     * R86). [categoryId] files the row as a card's interest or fees (SPEC R110); the callers that
     * mean a rise on a card pass it, every other edit leaves the row in no category.
     */
    fun balanceEdit(
        d: ProfileData, accountId: String, newBalance: Money, payee: String?, clock: Clock, txId: String,
        date: LocalDate? = null, categoryId: String? = null,
    ): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        val delta = newBalance - a.balance
        val next = d.copy(accounts = d.accounts.map { if (it.id == accountId) it.copy(balance = newBalance) else it })
        if (delta == 0L) return next
        return Ledger.logAccountEdit(next, a.id, a.name, delta, payee ?: StoredLabels.CORRECTION, clock, txId, date, categoryId)
    }

    /**
     * The category a rise of card [a]'s balance that is not a payment is filed under as interest
     * or fees (SPEC R110): [picked], else the card's fees category, when it exists; null when
     * none does. A card whose charges are not logged files its rises under none (SPEC R109).
     */
    fun riseCategory(d: ProfileData, a: Account, picked: String? = null): String? {
        if (a.type != AccountType.CREDIT || a.chargesNotLogged) return null
        return (picked ?: a.feesCategoryId)?.takeIf { id -> d.categories.any { it.id == id } }
    }

    /**
     * Whether [a]'s balance may rise other than by a payment charged to it (SPEC R89, R110): a
     * Loan always; a card as its interest or fees, filed under a category that exists, or under
     * none while its charges are not logged (SPEC R109).
     */
    fun canRise(d: ProfileData, a: Account, picked: String? = null): Boolean = when (a.type) {
        AccountType.LOAN -> true
        AccountType.CREDIT -> a.chargesNotLogged || riseCategory(d, a, picked) != null
    }

    /** A quick-add's direction: PAYMENT lowers the balance, INCREASE raises it: a Loan, or a card's interest or fees (SPEC R89, R110). */
    enum class Direction { PAYMENT, INCREASE }

    /** Where a payment off an Account takes its money from (SPEC R89). An increase takes it from nowhere. */
    sealed interface PayoffFrom {
        /** A `savings` row of minus the amount. */
        data object Savings : PayoffFrom
        /** A `spend:<goalId>` row of minus the amount: the Goal goes down, Savings does not move (SPEC R51). */
        data class Goal(val id: String) : PayoffFrom
        /** Nothing besides the `acct:` row, as when a linked expense's sweep already moved Savings. */
        data object None : PayoffFrom
    }

    /** The paired Savings row is on by default unless a linked expense's sweep already moved Savings (SPEC R89). */
    fun pairedOutflowDefault(a: Account): Boolean = a.linkedRecurringExpenseId == null

    /** Where a payment off [a] takes its money from unless the user picks another: Savings, or nowhere for a linked Account (SPEC R89). */
    fun payoffFromDefault(a: Account): PayoffFrom = if (pairedOutflowDefault(a)) PayoffFrom.Savings else PayoffFrom.None

    /**
     * Account quick-add (SPEC R89): one `acct:` row; on a payment, the row of where its money
     * came from [from] too, since the `acct:` row moved the balance and nothing else: a `savings`
     * row, or a `spend:` row that lowers a Goal, which must hold the amount. An increase on a
     * card is its interest or fees, filed under [categoryId] if picked, else its fees category
     * (SPEC R110); with neither, it is refused, unless the card's charges are not logged. A
     * payment off a Loan with nothing charged to it is filed under [categoryId] if picked, else
     * its linked payment's category, and refused with neither ([canPayOff], SPEC R89).
     */
    fun quickAdd(
        d: ProfileData, accountId: String, amount: Money, direction: Direction, payee: String?, from: PayoffFrom,
        clock: Clock, date: LocalDate? = null, categoryId: String? = null, ids: () -> String,
    ): ProfileData {
        val a = d.accounts.first { it.id == accountId }
        require(direction == Direction.PAYMENT || canRise(d, a, categoryId)) {
            "a card takes an increase only as interest or fees in a category that exists; a charge is a payment (SPEC R22, R110)"
        }
        require(direction == Direction.INCREASE || canPayOff(d, a, from, categoryId)) {
            "a payment off a Loan with nothing charged to it counts as spending and needs a category (SPEC R89)"
        }
        val goal = (from as? PayoffFrom.Goal)?.takeIf { direction == Direction.PAYMENT }?.let { f -> d.goals.firstOrNull { it.id == f.id } }
        if (direction == Direction.PAYMENT && from is PayoffFrom.Goal) {
            require(goal != null && amount <= goal.saved) { "a payoff from a Goal takes at most what it holds (SPEC R89)" }
        }
        val signed = if (direction == Direction.PAYMENT) -amount else amount
        val note = payee ?: if (direction == Direction.PAYMENT) StoredLabels.PAID_OFF else StoredLabels.INCREASED
        val rise = if (direction == Direction.INCREASE) riseCategory(d, a, categoryId) else null
        var next = balanceEdit(d, accountId, a.balance + signed, note, clock, ids(), date, rise)
        if (direction != Direction.PAYMENT) return next
        val fromNote = payee ?: StoredLabels.fill(StoredLabels.PAYMENT_TO, a.name)
        // filed as written: a card whose charges are not logged under its payoff category (SPEC R109),
        // a Loan with nothing charged to it under the picked or linked payment's category (SPEC R89)
        val filed = payoffFiling(d, a, categoryId)
        when (from) {
            PayoffFrom.Savings ->
                next = Ledger.logAndMove(next, Movement(-amount, TransactionKey.SavingsKey, StoredLabels.SAVINGS, fromNote, date = date, categoryId = filed), clock, ids()).first
            is PayoffFrom.Goal -> {
                val g = requireNotNull(goal)
                next = next.copy(goals = next.goals.map { if (it.id == g.id) it.copy(saved = it.saved - amount) else it })
                next = Ledger.logAndMove(next, Movement(-amount, TransactionKey.GoalSpendKey(g.id), g.name, fromNote, date = date, categoryId = filed), clock, ids()).first
            }
            PayoffFrom.None -> Unit
        }
        return next
    }

    /**
     * The category [a]'s payoffs count in while its charges are not logged (SPEC R109): its
     * payoff category, when it is a card with the switch on and that category exists; else null,
     * and a payoff is a transfer.
     */
    fun payoffCategory(d: ProfileData, a: Account): String? {
        if (a.type != AccountType.CREDIT || !a.chargesNotLogged) return null
        return a.payoffCategoryId?.takeIf { id -> d.categories.any { it.id == id } }
    }

    /** Whether a payment is charged to [a] (SPEC R54): a Loan's payments are then transfers (SPEC R67, R89). */
    fun hasCharges(d: ProfileData, a: Account): Boolean = d.payments.any { it.source == PaymentSource.Account(a.id) }

    /**
     * The category the `savings` or `spend:` row of a payment off [a] is filed under as it is
     * written, or null for a transfer: a card's payoff category while its charges are not logged
     * (SPEC R109); for a Loan with nothing charged to it, [picked], else its linked payment's
     * category, when it exists (SPEC R89).
     */
    fun payoffFiling(d: ProfileData, a: Account, picked: String? = null): String? = when (a.type) {
        AccountType.CREDIT -> payoffCategory(d, a)
        AccountType.LOAN -> if (hasCharges(d, a)) null else {
            val linked = a.linkedRecurringExpenseId?.let { id -> d.recurringExpenses.firstOrNull { it.id == id }?.categoryId }
            (picked ?: linked)?.takeIf { id -> d.categories.any { it.id == id } }
        }
    }

    /**
     * Whether a payment off [a] from [from] can be logged (SPEC R89): a payment off a Loan from
     * Savings or a Goal while nothing is charged to it counts as spending, so it needs a category,
     * [picked] or the linked payment's; every other payment can.
     */
    fun canPayOff(d: ProfileData, a: Account, from: PayoffFrom, picked: String? = null): Boolean =
        a.type != AccountType.LOAN || from == PayoffFrom.None || hasCharges(d, a) || payoffFiling(d, a, picked) != null

    /** A Loan with no rate cannot follow its payment: the update has nothing to work out from (SPEC R86). */
    fun loanInputsComplete(a: Account): Boolean = a.type == AccountType.LOAN && a.interestRate != null

    /** Re-typing clears the state belonging to the type it leaves (SPEC R94). */
    fun retype(a: Account, next: AccountType): Account {
        if (a.type == next) return a
        return if (next == AccountType.CREDIT) a.copy(
            type = next, autoUpdateFromPayments = false,
            principal = null, interestRate = null, termMonths = null, rateType = null, originationDate = null,
        ) else a.copy(type = next, apr = null, chargesNotLogged = false, payoffCategoryId = null, feesCategoryId = null)
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
        return AccountEditing.save(next, draft.copy(linkedRecurringExpenseId = null), clock, ids = ids)
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
