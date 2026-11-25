package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.AccountEditing
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.GoalMode
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/AccountsTest.kt at 2892110.
 * Changed: Sci-Fin's charge quick-add is replaced by a payment charged to the card (SPEC R22); the
 * months wording test is not ported (the engine returns figures, SPEC R16); the payoff tests
 * check the exact figures of the walk in whole cents (SPEC R92) where Sci-Fin checked a range.
 */
class AccountsTest {
    private val today = LocalDate(2026, 9, 14)

    private fun world(savings: String? = "1,000.00", accounts: List<Account> = emptyList()): ProfileData =
        profile(savings = savings, categories = listOf(category("c1", "Car"), category("groc", "Groceries"))).copy(accounts = accounts)

    private fun card(id: String = "cc", name: String = "Card", balance: String = "0.00", apr: Double? = null, linked: String? = null) =
        Account(id, Fixture.PROFILE, name, type = AccountType.CREDIT, balance = cents(balance), apr = apr, linkedRecurringExpenseId = linked)

    // ---- the loan calculator ---------------------------------------------------------------

    // SPEC:R90
    @Test fun `R90 12,000_00 at 6_5 percent over 48 months fixed is 284_58 a month, 13,659_84 repaid, 1,659_84 of interest`() {
        val m = Accounts.loanMath(cents("12,000.00"), 0.065, 48, RateType.FIXED)
        assertEquals(cents("284.58"), m.monthly)
        assertEquals(cents("13,659.84"), m.totalRepaid)
        assertEquals(cents("1,659.84"), m.totalInterest)
        assertEquals(0L, m.principalRemaining)
        assertEquals(48, m.months)
    }

    // SPEC:R90
    @Test fun `R90 12,000_00 interest-only at 6_5 percent is 65_00 a month, 3,120_00 repaid and 12,000_00 left`() {
        val m = Accounts.loanMath(cents("12,000.00"), 0.065, 48, RateType.INTEREST_ONLY)
        assertEquals(cents("65.00"), m.monthly)
        assertEquals(cents("3,120.00"), m.totalRepaid)              // the payments, not payments plus principal
        assertEquals(cents("3,120.00"), m.totalInterest)
        assertEquals(cents("12,000.00"), m.principalRemaining)
    }

    // SPEC:R90
    @Test fun `R90 12,000_00 at 0 percent over 48 months is 250_00 a month`() {
        assertEquals(cents("250.00"), Accounts.loanMath(cents("12,000.00"), 0.0, 48, RateType.FIXED).monthly)
    }

    // SPEC:R91 SPEC:R87 SPEC:R86
    @Test fun `R91 taking 9,800_00 at 5_9 percent over 48 months on 14 September 2026 creates a Loan and its 229_70 payment due 14 October`() {
        val clock = TestClock(today); val ids = Ids()
        val d = Accounts.getLoan(world(), cents("9,800.00"), 0.059, 48, RateType.FIXED, "Car loan", "Car loan payment", "c1",
            autoUpdate = true, clock = clock) { ids.next() }
        val a = d.accounts.single(); val e = d.recurringExpenses.single()
        assertEquals(e.id, a.linkedRecurringExpenseId); assertEquals(a.id, e.linkedAccountId)
        assertEquals(cents("229.70"), e.amount)
        assertEquals(Cadence.MONTHLY, e.cadence); assertEquals(DateMode.FIXED_DATE, e.dateMode); assertEquals(14, e.dueDateAnchorDay)
        assertEquals("c1", e.categoryId)
        assertEquals(LocalDate(2026, 10, 14), e.firstDueDate)                    // one month after the start
        assertEquals(LocalDate(2026, 10, 14), e.dueDate)
        assertEquals(LocalDate(2030, 9, 14), e.endDate)                          // the term's last payment
        assertEquals("2026-09", e.lastSweptMonth)                                // nothing sweeps this month
        assertEquals(cents("9,800.00"), a.balance); assertEquals(cents("9,800.00"), a.principal)
        assertEquals(0.059, a.interestRate); assertEquals(48, a.termMonths); assertEquals(RateType.FIXED, a.rateType)
        assertEquals(today, a.originationDate); assertTrue(a.autoUpdateFromPayments)
        val row = d.transactions.single()
        assertEquals(cents("9,800.00"), row.amount); assertEquals(StoredLabels.OPENING_BALANCE, row.payee)
        assertEquals(cents("1,000.00"), d.savingsAmount)                         // Savings does not move
        assertFalse(Accounts.finished(d, a))
        val ended = d.copy(recurringExpenses = d.recurringExpenses.map { it.copy(finishedAt = LocalDate(2030, 10, 1)) })
        assertTrue(Accounts.finished(ended, a))                                  // a Loan is finished when its payment is
    }

    // SPEC:R91 SPEC:R67
    @Test fun `R91 taking a loan with no category, or one that does not exist, creates nothing`() {
        val clock = TestClock(today)
        assertFailsWith<IllegalArgumentException> {
            Accounts.getLoan(world(), cents("9,800.00"), 0.059, 48, RateType.FIXED, "", "", "", false, clock) { Ids().next() }
        }
        assertFailsWith<IllegalArgumentException> {
            Accounts.getLoan(world(), cents("9,800.00"), 0.059, 48, RateType.FIXED, "", "", "nope", false, clock) { Ids().next() }
        }
    }

    // SPEC:R91
    @Test fun `R91 an unnamed loan is Loan and its payment Loan payment`() {
        val d = Accounts.getLoan(world(), cents("9,800.00"), 0.059, 48, RateType.FIXED, " ", "", "c1", false, TestClock(today)) { Ids().next() }
        assertEquals(StoredLabels.LOAN, d.accounts.single().name)
        assertEquals(StoredLabels.LOAN_PAYMENT, d.recurringExpenses.single().name)
    }

    // SPEC:R91 SPEC:R55
    @Test fun `R91 saving for 12,000_00 over 48 months on 14 September 2026 is a goal by 14 September 2030 named Savings goal`() {
        val m = Accounts.loanMath(cents("12,000.00"), 0.065, 48, RateType.FIXED)
        val g = Accounts.saveForItGoal(cents("12,000.00"), m, "", today, "g", Fixture.PROFILE)
        assertEquals(StoredLabels.SAVINGS_GOAL, g.name)
        assertEquals(cents("12,000.00"), g.target); assertEquals(0L, g.saved)
        assertEquals(LocalDate(2030, 9, 14), g.targetDate); assertEquals(GoalMode.DATE, g.mode)
        assertEquals(cents("250.00"), g.monthly)                                 // 12,000.00 over 48 calendar months
    }

    // ---- the card payoff outlook -----------------------------------------------------------

    // SPEC:R92
    @Test fun `R92 640_00 at 21_9 percent paid 50_00 a month clears in 15 months with 95_63 of interest, at 11_00 never`() {
        val p = Accounts.payoffAtFixedPayment(cents("640.00"), 0.219 / 12, cents("50.00"))
        assertEquals(15, p.months); assertEquals(cents("95.63"), p.interest)
        val never = Accounts.payoffAtFixedPayment(cents("640.00"), 0.219 / 12, cents("11.00"))
        assertNull(never.months); assertNull(never.interest)
        assertEquals(0, Accounts.payoffAtFixedPayment(0, 0.02, cents("50.00")).months)
        assertEquals(13, Accounts.payoffAtFixedPayment(cents("640.00"), 0.0, cents("50.00")).months)
        assertNull(Accounts.payoffAtFixedPayment(cents("640.00"), 0.0, 0).months)   // an empty payment never clears
    }

    // SPEC:R92
    @Test fun `R92 the minimum on 640_00 at 21_9 percent starts at 25_00 and clears in 35 months with 230_40 of interest`() {
        val m = Accounts.payoffAtMinimum(cents("640.00"), 0.219 / 12)
        assertEquals(cents("25.00"), m.first)                                   // 2 % of 640.00 is 12.80: the floor
        assertEquals(35, m.months); assertEquals(cents("230.40"), m.interest)
        assertEquals(cents("60.00"), Accounts.payoffAtMinimum(cents("3,000.00"), 0.0).first)
        assertNull(Accounts.payoffAtMinimum(cents("1,000.00"), 0.05).months)     // 25.00 below 5 % interest: never
        assertEquals(0, Accounts.payoffAtMinimum(0, 0.02).months)
    }

    // SPEC:R93 SPEC:R87
    @Test fun `R93 committing 50_00 for Credit Card on 14 September 2026 creates Credit Card payment due 14 October with no end date`() {
        val clock = TestClock(today)
        val a = card(name = "Credit Card", balance = "640.00", apr = 0.219).copy(autoUpdateFromPayments = true)
        val d = Accounts.commitFixedPayment(world(accounts = listOf(a)), "cc", cents("50.00"), "", "c1", clock, "e1")
        val e = d.recurringExpenses.single()
        assertEquals(StoredLabels.fill(StoredLabels.NAMED_PAYMENT, "Credit Card"), e.name)
        assertEquals(cents("50.00"), e.amount); assertEquals("c1", e.categoryId)
        assertEquals("cc", e.linkedAccountId); assertNull(e.endDate)
        assertEquals(LocalDate(2026, 10, 14), e.firstDueDate); assertEquals("2026-09", e.lastSweptMonth)
        assertEquals("e1", d.accounts.single().linkedRecurringExpenseId)
        assertFalse(d.accounts.single().autoUpdateFromPayments)                 // only a Loan follows its payment
        assertEquals(cents("1,000.00"), d.savingsAmount)
    }

    // SPEC:R93 SPEC:R87
    @Test fun `R93 a card that linked an earlier expense releases it when a fixed payment is committed`() {
        val old = recurring("old", "30.00", Cadence.MONTHLY, LocalDate(2026, 10, 1), linkedAccountId = "cc")
        val w = world(accounts = listOf(card(linked = "old"))).copy(recurringExpenses = listOf(old))
        val d = Accounts.commitFixedPayment(w, "cc", cents("50.00"), "Card plan", "c1", TestClock(today), "e1")
        assertNull(d.recurringExpenses.first { it.id == "old" }.linkedAccountId)
        assertEquals("cc", d.recurringExpenses.first { it.id == "e1" }.linkedAccountId)
        assertEquals("Card plan", d.recurringExpenses.first { it.id == "e1" }.name)
    }

    // ---- accounts --------------------------------------------------------------------------

    // SPEC:R85 SPEC:R18
    @Test fun `R85 a fresh Profile has Accounts off and no Account`() {
        val d = ProfileData.fresh("p", "Main")
        assertFalse(d.settings.accountsEnabled)
        assertTrue(d.accounts.isEmpty())
    }

    // SPEC:R22 SPEC:R85 SPEC:R54 SPEC:R25
    @Test fun `R22 a charge is a payment from the card and counts in its categories`() {
        val clock = TestClock(today)
        val d0 = world(accounts = listOf(card(balance = "100.00")))
        val draft = PaymentDraft(today, cents("40.00"), PaymentSource.Account("cc"), "Shoe shop", listOf(Part("groc", cents("40.00"))))
        val d = Payments.record(d0, draft, clock, "pay-1")
        assertEquals(cents("140.00"), d.accounts.single().balance)
        assertEquals(cents("40.00"), Spent.of(d, "groc", "2026-09"))
        assertEquals(cents("1,000.00"), d.savingsAmount)
        assertTrue(d.transactions.isEmpty())                                     // a payment writes no ledger row
    }

    // SPEC:R89
    @Test fun `R89 a card at 100_00 paid 60_00 with the pair on reads 40_00 and Savings 940_00, with the pair off 1,000_00`() {
        val clock = TestClock(today); val ids = Ids()
        val a = card(balance = "100.00")
        var d = Accounts.quickAdd(world(accounts = listOf(a)), "cc", cents("60.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        assertEquals(cents("40.00"), d.accounts.single().balance); assertEquals(cents("940.00"), d.savingsAmount)
        val (acct, sav) = d.transactions
        assertEquals("acct:cc", acct.key); assertEquals(cents("-60.00"), acct.amount); assertEquals(StoredLabels.PAID_OFF, acct.payee)
        assertEquals("savings", sav.key); assertEquals(cents("-60.00"), sav.amount)
        assertEquals(StoredLabels.fill(StoredLabels.PAYMENT_TO, "Card"), sav.payee)
        d = Accounts.quickAdd(d, "cc", cents("10.00"), Accounts.Direction.PAYMENT, "Bank transfer", from = Accounts.PayoffFrom.None, clock = clock) { ids.next() }
        assertEquals(cents("30.00"), d.accounts.single().balance); assertEquals(cents("940.00"), d.savingsAmount)
        assertEquals("Bank transfer", d.transactions.last().payee)
        assertTrue(Accounts.pairedOutflowDefault(a))
        assertFalse(Accounts.pairedOutflowDefault(a.copy(linkedRecurringExpenseId = "e")))
        assertEquals(Accounts.PayoffFrom.Savings, Accounts.payoffFromDefault(a))
        assertEquals(Accounts.PayoffFrom.None, Accounts.payoffFromDefault(a.copy(linkedRecurringExpenseId = "e")))
    }

    // SPEC:R89 SPEC:R51
    @Test fun `R89 a card at 100_00 paid 60_00 from a Goal holding 80_00 reads 40_00, the Goal 20_00 and Savings 1,000_00, and 90_00 is refused`() {
        val clock = TestClock(today); val ids = Ids()
        val d0 = world(accounts = listOf(card(balance = "100.00"))).copy(goals = listOf(Fixture.goal("trip", "Trip", saved = "80.00")))
        val d = Accounts.quickAdd(d0, "cc", cents("60.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Goal("trip"), clock = clock) { ids.next() }
        assertEquals(cents("40.00"), d.accounts.single().balance)
        assertEquals(cents("20.00"), d.goals.single().saved)
        assertEquals(cents("1,000.00"), d.savingsAmount)
        val (acct, spend) = d.transactions
        assertEquals("acct:cc" to cents("-60.00"), acct.key to acct.amount)
        assertEquals("spend:trip" to cents("-60.00"), spend.key to spend.amount)
        assertEquals(StoredLabels.fill(StoredLabels.PAYMENT_TO, "Card"), spend.payee)
        assertEquals(0L, com.hazia.tesoriere.engine.Ledger.savingsEffect(spend))
        assertFailsWith<IllegalArgumentException> {
            Accounts.quickAdd(d0, "cc", cents("90.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Goal("trip"), clock = clock) { ids.next() }
        }
    }

    // SPEC:R89 SPEC:R67 SPEC:R25
    @Test fun `R89 a Loan with nothing charged to it paid 200_00 by hand adds 200_00 to Car, after a 9,800_00 car is charged 100_00 from a Goal adds nothing, and removing the car changes neither`() {
        val clock = TestClock(today); val ids = Ids()
        val loan = Account("l", Fixture.PROFILE, "Car loan", type = AccountType.LOAN, balance = cents("4,000.00"), linkedRecurringExpenseId = "pay")
        val pay = recurring("pay", "260.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), categoryId = "c1", linkedAccountId = "l", lastSweptMonth = "2026-09")
        var d = world(accounts = listOf(loan)).copy(recurringExpenses = listOf(pay), goals = listOf(Fixture.goal("trip", "Trip", saved = "150.00")))
        d = Accounts.quickAdd(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        assertEquals(cents("200.00"), Spent.of(d, "c1", "2026-09"))
        assertEquals("c1", d.transactions.last().categoryId)
        val car = PaymentDraft(today, cents("9,800.00"), PaymentSource.Account("l"), "Car dealer", listOf(Part("c1", cents("9,800.00"))))
        d = Payments.record(d, car, clock, "car")
        d = Accounts.quickAdd(d, "l", cents("100.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Goal("trip"), clock = clock) { ids.next() }
        assertEquals(cents("10,000.00"), Spent.of(d, "c1", "2026-09"))                    // the car and the first payment
        assertNull(d.transactions.last().categoryId)
        d = Payments.remove(d, "car", clock, "t-remove")
        assertEquals(cents("200.00"), Spent.of(d, "c1", "2026-09"))                       // rows as written
        // a category picked for the payment wins over the linked payment's
        val picked = Accounts.quickAdd(world(accounts = listOf(loan)).copy(recurringExpenses = listOf(pay)), "l", cents("50.00"),
            Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock, categoryId = "groc") { ids.next() }
        assertEquals(cents("50.00"), Spent.of(picked, "groc", "2026-09"))
    }

    // SPEC:R89
    @Test fun `R89 a Loan linked to nothing paid 200_00 from Savings with no category picked is refused, from nowhere it is not`() {
        val clock = TestClock(today); val ids = Ids()
        val loan = Account("l", Fixture.PROFILE, "Loan", type = AccountType.LOAN, balance = cents("4,000.00"))
        val d = world(accounts = listOf(loan))
        assertFalse(Accounts.canPayOff(d, loan, Accounts.PayoffFrom.Savings))
        assertFailsWith<IllegalArgumentException> {
            Accounts.quickAdd(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        }
        val none = Accounts.quickAdd(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.None, clock = clock) { ids.next() }
        assertEquals(cents("3,800.00"), none.accounts.single().balance)
    }

    // SPEC:R89 SPEC:R85
    @Test fun `R89 a Loan with Car as its own category paid 200_00 from Savings counts in Car, Groceries picked wins, and its linked payment's sweeps stay under Bills`() {
        val clock = TestClock(today); val ids = Ids()
        val d0 = profile(categories = listOf(category("car", "Car"), category("groc", "Groceries"), category("bills", "Bills")))
        val loan = Account("l", Fixture.PROFILE, "Loan", type = AccountType.LOAN, balance = cents("4,000.00"), loanCategoryId = "car")
        val d = d0.copy(accounts = listOf(loan))
        assertTrue(Accounts.canPayOff(d, loan, Accounts.PayoffFrom.Savings))
        val paid = Accounts.quickAdd(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        assertEquals(cents("200.00"), Spent.of(paid, "car", "2026-09"))
        val picked = Accounts.quickAdd(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock, categoryId = "groc") { ids.next() }
        assertEquals(cents("200.00"), Spent.of(picked, "groc", "2026-09")); assertEquals(0L, Spent.of(picked, "car", "2026-09"))
        // linked to a payment under Bills: the Loan's own category wins for a payment by hand, the sweeps keep Bills
        val pay = recurring("pay", "260.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), categoryId = "bills", linkedAccountId = "l")
        var linked = d.copy(accounts = listOf(loan.copy(linkedRecurringExpenseId = "pay")), recurringExpenses = listOf(pay))
        linked = Accounts.quickAdd(linked, "l", cents("100.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        linked = com.hazia.tesoriere.engine.Recurring.sweepOccurrences(linked, "pay", LocalDate(2026, 9, 1), clock) { ids.next() }.first
        assertEquals(cents("100.00"), Spent.of(linked, "car", "2026-09"))
        assertEquals(cents("260.00"), Spent.of(linked, "bills", "2026-09"))
    }

    // SPEC:R89 SPEC:R110
    @Test fun `R89 a picked category that does not exist refuses a Loan payment by hand with a code, instead of counting in Car`() {
        val clock = TestClock(today); val ids = Ids()
        val loan = Account("l", Fixture.PROFILE, "Loan", type = AccountType.LOAN, balance = cents("4,000.00"), loanCategoryId = "c1")
        val d = world(accounts = listOf(loan))
        assertEquals(Accounts.QuickAddProblem.CATEGORY_UNKNOWN,
            Accounts.quickAddProblem(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, Accounts.PayoffFrom.Savings, categoryId = "nowhere"))
        assertFailsWith<IllegalArgumentException> {
            Accounts.quickAdd(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, null, from = Accounts.PayoffFrom.Savings, clock = clock, categoryId = "nowhere") { ids.next() }
        }
        assertNull(Accounts.quickAddProblem(d, "l", cents("200.00"), Accounts.Direction.PAYMENT, Accounts.PayoffFrom.Savings))
        // a card with Fees as its fees category refuses an increase picked under a category that does not exist
        val card = card().copy(feesCategoryId = "c1")
        assertEquals(Accounts.QuickAddProblem.CATEGORY_UNKNOWN,
            Accounts.quickAddProblem(world(accounts = listOf(card)), "cc", cents("12.50"), Accounts.Direction.INCREASE, Accounts.PayoffFrom.None, categoryId = "nowhere"))
        // a Loan with nothing to file under and no category picked has its own code
        assertEquals(Accounts.QuickAddProblem.CATEGORY_MISSING,
            Accounts.quickAddProblem(world(accounts = listOf(loan.copy(loanCategoryId = null))), "l", cents("200.00"), Accounts.Direction.PAYMENT, Accounts.PayoffFrom.Savings))
    }

    // SPEC:R86 SPEC:R94 SPEC:R91
    @Test fun `R86 a Loan saves with no category of its own but not with one that does not exist, changed to a card it has none, and taken from the calculator it has its payment's`() {
        val clock = TestClock(today)
        val loan = Account("l", Fixture.PROFILE, "Loan", type = AccountType.LOAN)
        assertTrue(AccountEditing.canSave(world(), loan))
        assertFalse(AccountEditing.canSave(world(), loan.copy(loanCategoryId = "nowhere")))
        assertTrue(AccountEditing.canSave(world(), loan.copy(loanCategoryId = "c1")))
        assertNull(Accounts.retype(loan.copy(loanCategoryId = "c1"), AccountType.CREDIT).loanCategoryId)
        val taken = Accounts.getLoan(world(), cents("9,800.00"), 0.059, 48, RateType.FIXED, "", "", "c1", false, clock) { Ids().next() }
        assertEquals("c1", taken.accounts.single().loanCategoryId)
    }

    // SPEC:R89 SPEC:R22
    @Test fun `R89 a Loan of 4,000_00 increased by 500_00 reads 4,500_00 and an increase on a card is refused`() {
        val clock = TestClock(today); val ids = Ids()
        val loan = Account("l", Fixture.PROFILE, "Loan", type = AccountType.LOAN, balance = cents("4,000.00"))
        val d = Accounts.quickAdd(world(accounts = listOf(loan, card())), "l", cents("500.00"), Accounts.Direction.INCREASE, null, from = Accounts.PayoffFrom.Savings, clock = clock) { ids.next() }
        assertEquals(cents("4,500.00"), d.accounts.first { it.id == "l" }.balance)
        assertEquals(cents("1,000.00"), d.savingsAmount)                          // an increase is never paired
        assertEquals(StoredLabels.INCREASED, d.transactions.single().payee)
        assertFailsWith<IllegalArgumentException> {
            Accounts.quickAdd(d, "cc", cents("40.00"), Accounts.Direction.INCREASE, "Shoes", from = Accounts.PayoffFrom.None, clock = clock) { ids.next() }
        }
    }

    // SPEC:R94 SPEC:R86
    @Test fun `R94 a Loan at 5 percent changed to a card clears its Loan fields, and a card at 20 percent changed to a Loan has no rate`() {
        val loan = Account("l", Fixture.PROFILE, "L", type = AccountType.LOAN, autoUpdateFromPayments = true, principal = cents("1.00"),
            interestRate = 0.05, termMonths = 12, rateType = RateType.FIXED, originationDate = today)
        val asCredit = Accounts.retype(loan, AccountType.CREDIT)
        assertEquals(AccountType.CREDIT, asCredit.type)
        assertFalse(asCredit.autoUpdateFromPayments)
        assertNull(asCredit.principal); assertNull(asCredit.interestRate); assertNull(asCredit.termMonths)
        assertNull(asCredit.rateType); assertNull(asCredit.originationDate)
        val asLoan = Accounts.retype(card(apr = 0.2), AccountType.LOAN)
        assertEquals(AccountType.LOAN, asLoan.type); assertNull(asLoan.apr)
        assertFalse(Accounts.loanInputsComplete(asLoan))
        assertTrue(Accounts.loanInputsComplete(loan))
    }

    // SPEC:R87
    @Test fun `R87 a Loan of 9,800_00 deleted takes its payment with it and logs a -9,800_00 Account closed row`() {
        val clock = TestClock(today)
        val d0 = Accounts.getLoan(world(), cents("9,800.00"), 0.059, 48, RateType.FIXED, "L", "P", "c1", false, clock) { Ids("a").next() }
        val d1 = Accounts.deleteCascade(d0, d0.accounts.single().id, clock) { Ids("b").next() }
        assertTrue(d1.accounts.isEmpty()); assertTrue(d1.recurringExpenses.isEmpty())
        assertEquals(cents("-9,800.00"), d1.transactions.last().amount)
        assertEquals(StoredLabels.ACCOUNT_CLOSED, d1.transactions.last().payee)
    }

    // SPEC:R88 SPEC:R25 SPEC:R54
    @Test fun `R88 a card at 100_00 with a 40_00 payment under Groceries, deleted, logs -140_00 and Groceries keeps 40_00`() {
        val clock = TestClock(today); val ids = Ids()
        var d = AccountEditing.save(world(), card(balance = "100.00"), clock) { ids.next() }
        val draft = PaymentDraft(today, cents("40.00"), PaymentSource.Account("cc"), "Corner shop", listOf(Part("groc", cents("40.00"))))
        d = Payments.record(d, draft, clock, "pay-1")
        d = Accounts.deleteCascade(d, "cc", clock) { ids.next() }
        assertTrue(d.accounts.isEmpty())
        val closing = d.transactions.last()
        assertEquals(cents("-140.00"), closing.amount); assertEquals(StoredLabels.ACCOUNT_CLOSED, closing.payee)
        assertEquals(PaymentSource.Account("cc"), d.payments.single().source)       // the payment stays
        assertEquals(cents("40.00"), Spent.of(d, "groc", "2026-09"))
        assertEquals(cents("1,000.00"), d.savingsAmount)
        // the Account's rows and the payments charged to it add up to zero
        assertEquals(0L, d.transactions.filter { it.key == "acct:cc" }.sumOf { it.amount } + d.payments.sumOf { it.total })
    }

    // SPEC:R86
    @Test fun `R86 a card created at 640_00 logs an Opening balance row of 640_00, and edited to 600_00 a -40_00 Correction`() {
        val clock = TestClock(today); val ids = Ids()
        var d = AccountEditing.save(world(), card(balance = "640.00"), clock) { ids.next() }
        assertEquals(cents("640.00"), d.accounts.single().balance)
        val opening = d.transactions.single()
        assertEquals(cents("640.00"), opening.amount); assertEquals(StoredLabels.OPENING_BALANCE, opening.payee)
        d = AccountEditing.save(d, d.accounts.single().copy(balance = cents("600.00")), clock) { ids.next() }
        assertEquals(cents("-40.00"), d.transactions.last().amount); assertEquals(StoredLabels.CORRECTION, d.transactions.last().payee)
        assertEquals(cents("1,000.00"), d.savingsAmount)
    }

    // SPEC:R86
    @Test fun `R86 a Loan saved to follow its payment with no rate does not follow and starts on 14 September 2026, a blank name is not saved`() {
        val clock = TestClock(today)
        val draft = Account("l", Fixture.PROFILE, " Car loan ", type = AccountType.LOAN, autoUpdateFromPayments = true)
        val d = AccountEditing.save(world(), draft, clock) { Ids().next() }
        val a = d.accounts.single()
        assertEquals("Car loan", a.name); assertFalse(a.autoUpdateFromPayments); assertEquals(today, a.originationDate)
        assertTrue(d.transactions.isEmpty())                                     // a balance of 0 logs no opening row
        val w = world()
        assertEquals(w, AccountEditing.save(w, card().copy(name = "  "), clock) { Ids().next() })
        val fresh = AccountEditing.fresh("n", Fixture.PROFILE)
        assertEquals(AccountType.CREDIT, fresh.type); assertEquals("", fresh.name)
    }
}
