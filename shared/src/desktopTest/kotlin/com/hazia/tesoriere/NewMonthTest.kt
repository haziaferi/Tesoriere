package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.Ended
import com.hazia.tesoriere.engine.EndedKind
import com.hazia.tesoriere.engine.NewMonth
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/NewMonthTest.kt at 2892110.
 * Changed: a category holds no spent and no month (SPEC R25), so the reset assertions become the
 * check that a new month writes nothing for categories (SPEC R70); an ended expense gives a code,
 * not a sentence (SPEC R72); figures are cents.
 */
class NewMonthTest {

    private fun monthly(id: String, amount: String, day: Int, linked: String? = null, endDate: LocalDate? = null, swept: String? = "2026-08") =
        recurring(id, amount, Cadence.MONTHLY, LocalDate(2026, 1, day), dueDate = LocalDate(2026, 9, day),
            endDate = endDate, linkedAccountId = linked, lastSweptMonth = swept, categoryId = "housing")

    private fun world(vararg exps: RecurringExpense, savings: String = "5,000.00") =
        profile(savings = savings, categories = listOf(category("groc", "Groceries"), category("housing", "Housing")))
            .copy(recurringExpenses = exps.toList())

    private fun loan(balance: String, rateType: RateType?, autoUpdate: Boolean = false, rate: Double? = null) =
        Account("loan", Fixture.PROFILE, "Car loan", null, AccountType.LOAN, balance = cents(balance), linkedRecurringExpenseId = "pay",
            autoUpdateFromPayments = autoUpdate, interestRate = rate, rateType = rateType)

    // SPEC:R70 SPEC:R63
    @Test fun `R70 crossing into September 2026 sweeps 950_00 rent and 45_00 phone once, leaving 4,005_00, and a second open writes nothing`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        val (d, s) = NewMonth.runCatchUp(world(monthly("rent", "950.00", 1), monthly("phone", "45.00", 10)), clock) { ids.next() }
        assertNotNull(s)
        assertEquals(1, s.monthsReplayed)
        assertEquals(cents("995.00"), s.sweptTotal)
        assertEquals(cents("4,005.00"), d.savingsAmount)
        assertEquals(setOf("rec:rent", "rec:phone"), d.transactions.map { it.key }.toSet())
        assertEquals(LocalDate(2026, 9, 10), d.transactions.first { it.key == "rec:phone" }.date)   // its real due date
        assertFalse(d.settings.catchUpSummaryPending)                                               // one month: an ordinary crossing
        val (d2, s2) = NewMonth.runCatchUp(d, clock) { ids.next() }
        assertNull(s2); assertEquals(d.transactions.size, d2.transactions.size)
    }

    // SPEC:R70 SPEC:R25
    @Test fun `R70 a new month starts every category's spent at 0 with no write`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        val before = Payments.record(world(monthly("rent", "950.00", 1)),
            PaymentDraft(LocalDate(2026, 8, 20), cents("120.00"), PaymentSource.Savings, null, listOf(Part("groc", cents("120.00")))), clock, "pay-1")
        val (d, _) = NewMonth.runCatchUp(before, clock) { ids.next() }
        assertEquals(cents("120.00"), Spent.of(d, "groc", "2026-08"))
        assertEquals(0L, Spent.of(d, "groc", "2026-09"))
        assertEquals(before.categories, d.categories)
        assertEquals(listOf("rec:rent"), d.transactions.map { it.key })                             // the sweep, and nothing for categories
    }

    // SPEC:R70 SPEC:R74 SPEC:R61
    @Test fun `R70 opened on 14 November 2026 the rent writes rows dated 1 September, 1 October and 1 November and is next due 1 December`() {
        val clock = TestClock(LocalDate(2026, 11, 14)); val ids = Ids()
        val (d, s) = NewMonth.runCatchUp(world(monthly("rent", "950.00", 1)), clock) { ids.next() }
        assertEquals(3, s!!.monthsReplayed)
        assertEquals(listOf(LocalDate(2026, 9, 1), LocalDate(2026, 10, 1), LocalDate(2026, 11, 1)), d.transactions.map { it.date })
        assertTrue(d.settings.catchUpSummaryPending)
        assertEquals("2026-11", d.recurringExpenses[0].lastSweptMonth)
        assertEquals(LocalDate(2026, 12, 1), d.recurringExpenses[0].dueDate)
    }

    // SPEC:R70 SPEC:R61
    @Test fun `R70 an open on 20 September 2026 with September swept moves a phone bill due the 10th on to 10 October`() {
        val clock = TestClock(LocalDate(2026, 9, 20))
        val (d, s) = NewMonth.runCatchUp(world(monthly("phone", "45.00", 10, swept = "2026-09")), clock) { Ids().next() }
        assertNull(s)
        assertEquals(LocalDate(2026, 10, 10), d.recurringExpenses[0].dueDate)
    }

    // SPEC:R72
    @Test fun `R72 a 260_00 payment ending 15 August 2026 finishes on 1 September and pays off its fixed-rate Loan of 120_00`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        val w = world(monthly("pay", "260.00", 15, linked = "loan", endDate = LocalDate(2026, 8, 15))).copy(accounts = listOf(loan("120.00", RateType.FIXED)))
        val (d, s) = NewMonth.runCatchUp(w, clock) { ids.next() }
        val ended = s!!.endedNow.single()
        assertEquals("pay", ended.expenseId); assertEquals(EndedKind.LOAN_PAID_OFF, ended.kind)
        assertEquals(LocalDate(2026, 9, 1), d.recurringExpenses[0].finishedAt)
        assertEquals(0L, d.accounts[0].balance)
        val closing = d.transactions.single { it.key == "acct:loan" }
        assertEquals(StoredLabels.LOAN_FINISHED, closing.payee); assertEquals(cents("-120.00"), closing.amount)
        assertEquals(LocalDate(2026, 9, 1), closing.date)
    }

    // SPEC:R72 SPEC:R73
    @Test fun `R72 a variable-rate Loan of 100_00 cleared by its final 260_00 payment ends with loanPaidOff`() {
        val w = world(monthly("pay", "260.00", 1, linked = "loan", endDate = LocalDate(2026, 9, 1)))
            .copy(accounts = listOf(loan("100.00", RateType.VARIABLE, autoUpdate = true)))
        val (d, s) = NewMonth.runCatchUp(w, TestClock(LocalDate(2026, 9, 2))) { Ids().next() }
        assertEquals(0L, d.accounts[0].balance)
        assertEquals(EndedKind.LOAN_PAID_OFF, s!!.endedNow.single().kind)
    }

    // SPEC:R72
    @Test fun `R72 a Loan of 100_00 left at the end of its term gives variableTermEnded or interestOnlyEnded, and 0_00 updated from payments gives loanPaidOff`() {
        val base = world(monthly("pay", "260.00", 15, linked = "loan"))
        val e = base.recurringExpenses[0]
        assertEquals(EndedKind.VARIABLE_TERM_ENDED, NewMonth.endedKind(base.copy(accounts = listOf(loan("100.00", RateType.VARIABLE))), e))
        assertEquals(EndedKind.INTEREST_ONLY_ENDED, NewMonth.endedKind(base.copy(accounts = listOf(loan("100.00", RateType.INTEREST_ONLY))), e))
        // the update from payments took the balance to 0: paid off whatever the rate
        assertEquals(EndedKind.LOAN_PAID_OFF, NewMonth.endedKind(base.copy(accounts = listOf(loan("0.00", RateType.VARIABLE, autoUpdate = true))), e))
        assertEquals(EndedKind.ENDED, NewMonth.endedKind(base, e.copy(linkedAccountId = null)))
    }

    // SPEC:R73 SPEC:R51
    @Test fun `R73 a Loan of 6,000_00 at 6 percent falls to 5,770_00 with a -230_00 row dated 15 September, and Savings falls by 260_00 only`() {
        val clock = TestClock(LocalDate(2026, 9, 1)); val ids = Ids()
        val w = world(monthly("pay", "260.00", 15, linked = "loan")).copy(accounts = listOf(loan("6,000.00", RateType.FIXED, autoUpdate = true, rate = 0.06)))
        val (d, _) = NewMonth.runCatchUp(w, clock) { ids.next() }
        assertEquals(cents("5,770.00"), d.accounts[0].balance)                                    // interest 30.00, principal 230.00
        val row = d.transactions.single { it.key == "acct:loan" }
        assertEquals(cents("-230.00"), row.amount); assertEquals("pay", row.payee); assertEquals(LocalDate(2026, 9, 15), row.date)
        assertEquals(cents("4,740.00"), d.savingsAmount)
    }

    // SPEC:R73
    @Test fun `R73 a Loan of 200_00 at 0 percent reaches 0_00 and its payment finishes though it ends in 2030`() {
        val clock = TestClock(LocalDate(2026, 9, 1))
        val w = world(monthly("pay", "260.00", 15, linked = "loan", endDate = LocalDate(2030, 1, 1)))
            .copy(accounts = listOf(loan("200.00", null, autoUpdate = true, rate = 0.0)))
        val (d, _) = NewMonth.runCatchUp(w, clock) { Ids().next() }
        assertEquals(0L, d.accounts[0].balance)
        assertEquals(LocalDate(2026, 9, 1), d.recurringExpenses[0].finishedAt)
    }

    // SPEC:R73 SPEC:R72 SPEC:R74
    @Test fun `R73 the payment of a Loan of 200_00 at 0 percent paid to 0_00 gives a loanPaidOff notice with its name`() {
        val clock = TestClock(LocalDate(2026, 9, 1))
        val w = world(monthly("pay", "260.00", 15, linked = "loan", endDate = LocalDate(2030, 1, 1)))
            .copy(accounts = listOf(loan("200.00", null, autoUpdate = true, rate = 0.0)))
        val (d, s) = NewMonth.runCatchUp(w, clock) { Ids().next() }
        assertEquals(listOf(Ended("pay", EndedKind.LOAN_PAID_OFF, "pay")), s!!.endedNow)
        assertTrue(d.settings.catchUpSummaryPending)                                   // an expense finished: the summary is kept
        assertEquals(listOf(Ended("pay", EndedKind.LOAN_PAID_OFF, "pay")), NewMonth.persistedSummary(d.settings)!!.endedNow)
    }

    // SPEC:R70 SPEC:R62 SPEC:R61
    @Test fun `R70 a 1,000_00 yearly expense due 1 April 2026 at 333_34, swept for February and opened 10 May, sweeps 333_34 for March and April and 83_34 for May`() {
        val e = recurring("ins", "1,000.00", Cadence.YEARLY, LocalDate(2025, 4, 1), dueDate = LocalDate(2026, 4, 1),
            share = "333.34", lastSweptMonth = "2026-02", categoryId = "housing")
        val (d, s) = NewMonth.runCatchUp(world(e), TestClock(LocalDate(2026, 5, 10))) { Ids().next() }
        val rows = d.transactions.filter { it.key == "rec:ins" }
        assertEquals(listOf(LocalDate(2026, 3, 1), LocalDate(2026, 4, 1), LocalDate(2026, 5, 1)), rows.map { it.date })
        assertEquals(listOf(cents("333.34"), cents("333.34"), cents("83.34")), rows.map { it.amount })
        assertEquals(cents("750.02"), s!!.sweptTotal)
        val after = d.recurringExpenses.single()
        assertEquals(LocalDate(2027, 4, 1), after.dueDate)
        assertEquals(cents("83.34"), after.apportionedShare)
        assertEquals("2026-05", after.lastSweptMonth)
    }

    // SPEC:R71 SPEC:R70
    @Test fun `R71 opened on 2 October 2026 with a 1,450_00 biweekly income from 11 September, two deposits and the rent leave 6,950_00`() {
        val clock = TestClock(LocalDate(2026, 10, 2)); val ids = Ids()
        val w = world(monthly("rent", "950.00", 1, swept = "2026-09")).let {
            it.copy(settings = it.settings.copy(incomeEnabled = true, income = cents("1,450.00"), incomeCadence = IncomeCadence.BIWEEKLY,
                incomePayDate = LocalDate(2026, 9, 11), incomeAutoDeposit = true, incomeEnabledDate = LocalDate(2026, 9, 11)))
        }
        val (d, s) = NewMonth.runCatchUp(w, clock) { ids.next() }
        assertEquals(1, s!!.monthsReplayed)
        assertEquals(2, s.deposits.count); assertEquals(cents("2,900.00"), s.deposits.total)
        assertEquals(cents("6,950.00"), d.savingsAmount)
        assertEquals(listOf(LocalDate(2026, 9, 11), LocalDate(2026, 9, 25)),
            d.transactions.filter { it.payee == StoredLabels.AUTO_DEPOSIT }.map { it.date })
    }

    // SPEC:R63
    @Test fun `R63 on 18 September 2026 a swept 950_00 rent and 830_00 bill make a scheduled total of 1,780_00`() {
        val w = world(monthly("rent", "950.00", 1, swept = "2026-09"), monthly("late", "830.00", 5, swept = "2026-09"))
        assertEquals(cents("1,780.00"), Recurring.scheduledTotal(w, LocalDate(2026, 9, 18)))
    }
}
