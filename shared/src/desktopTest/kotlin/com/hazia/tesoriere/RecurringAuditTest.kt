package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.RecurringExpense
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/RecurringAuditTest.kt at
 * 2892110. Not ported here: the tests that run the New Month catch-up (`NewMonth.runCatchUp`,
 * Task 8) and the ones that edit, delete or re-type an Account from its own side
 * (`AccountEditing.save`, `Accounts.deleteCascade`, `Accounts.retypeToCreditCascade`, Task 10);
 * each comes with its piece.
 */
class RecurringAuditTest {

    private fun monthly(id: String, amount: String, day: Int, due: LocalDate, first: LocalDate = LocalDate(2026, 1, day), linked: String? = null, swept: String? = null) =
        recurring(id, amount, Cadence.MONTHLY, first, dueDate = due, linkedAccountId = linked, lastSweptMonth = swept)

    private fun world(vararg e: RecurringExpense, accounts: List<Account> = emptyList()) =
        profile(savings = "5,000.00", categories = listOf(category("c1"))).copy(recurringExpenses = e.toList(), accounts = accounts)

    private fun loan(id: String, balance: String, linked: String, rateType: RateType) =
        Account(id, Fixture.PROFILE, "Car loan", null, AccountType.LOAN, balance = cents(balance), linkedRecurringExpenseId = linked, rateType = rateType, interestRate = 0.05)

    // ---- an edit is a new plan --------------------------------------------------------------

    private fun semi(due: LocalDate) = recurring("phone", "20.00", Cadence.SEMIMONTHLY, LocalDate(2026, 1, 15), dueDate = due, dueDate2 = LocalDate(2026, 1, 1))

    // SPEC:R60 SPEC:R61
    @Test fun `R60 a phone bill on the 1st and 15th shows 1 October and 15 September, then 1 and 15 October`() {
        // the live due date is the 15th; the 1st has passed, so its next date is in October
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 9, 15), Recurring.semimonthlyFields(semi(LocalDate(2026, 9, 15))))
        // once the 15th passes, the first field still shows the 1st
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 15), Recurring.semimonthlyFields(semi(LocalDate(2026, 10, 1))))
    }

    // SPEC:R60
    @Test fun `R60 the two semimonthly dates 15 and 1 October write back as due 1 October and second date 15 October`() {
        val e = semi(LocalDate(2026, 10, 1))
        val (a, b) = Recurring.semimonthlyFields(e)
        val back = Recurring.withSemimonthlyFields(e, b, a)                 // order on screen does not matter
        assertEquals(LocalDate(2026, 10, 1), back.dueDate)
        assertEquals(LocalDate(2026, 10, 15), back.dueDate2)
        assertEquals(setOf(1, 15), setOf(back.firstDueDate.day, back.dueDate2!!.day))
    }

    // SPEC:R65
    @Test fun `R65 moving the 15th of a bill on the 1st and 15th to the 20th falls due on 1 and 20 November`() {
        val today = LocalDate(2026, 9, 20)
        val e = semi(LocalDate(2026, 10, 1)).copy(lastSweptMonth = "2026-09")
        val (first, _) = Recurring.semimonthlyFields(e)
        val (d, _) = Recurring.edit(world(e), Recurring.withSemimonthlyFields(e, first, LocalDate(2026, 10, 20)), today)
        val out = d.recurringExpenses.single()
        assertEquals(listOf(LocalDate(2026, 11, 1), LocalDate(2026, 11, 20)), Recurring.occurrenceDatesInMonth(out, 2026, 11))
    }

    // SPEC:R65 SPEC:R61
    @Test fun `R65 a fixed-date rent on the 1st moved to the 15th on 10 September 2026 falls due on 15 November`() {
        val today = LocalDate(2026, 9, 10)
        val e = monthly("rent", "900.00", 1, due = LocalDate(2026, 10, 1), swept = "2026-09")
        val (d, _) = Recurring.edit(world(e), e.copy(dueDate = LocalDate(2026, 10, 15)), today)
        val out = d.recurringExpenses.single()
        assertEquals(15, out.dueDateAnchorDay)
        assertEquals(listOf(LocalDate(2026, 11, 15)), Recurring.occurrenceDatesInMonth(out, 2026, 11))
        assertEquals(LocalDate(2026, 11, 15), Recurring.nextOccurrenceAfter(out, out.dueDate))
    }

    // SPEC:R65 SPEC:R60
    @Test fun `R65 switching to interval mode sets 30 days, drops the anchor, and 1 October is followed by 31 October`() {
        val today = LocalDate(2026, 9, 10)
        val e = monthly("sub", "10.00", 1, due = LocalDate(2026, 10, 1))
        val (d, _) = Recurring.edit(world(e), e.copy(dateMode = DateMode.INTERVAL), today)
        val out = d.recurringExpenses.single()
        assertEquals(30, out.intervalDays); assertNull(out.dueDateAnchorDay)
        assertEquals(LocalDate(2026, 10, 31), Recurring.nextOccurrenceAfter(out, LocalDate(2026, 10, 1)))
    }

    // SPEC:R65
    @Test fun `R65 a semimonthly edit of only the second date to the 28th keeps the first due date of 15 January`() {
        val today = LocalDate(2026, 9, 10)
        val e = recurring("s", "300.00", Cadence.SEMIMONTHLY, LocalDate(2026, 1, 15), dueDate = LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30))
        val (d, _) = Recurring.edit(world(e), e.copy(dueDate2 = LocalDate(2026, 9, 28)), today)
        assertEquals(LocalDate(2026, 1, 15), d.recurringExpenses.single().firstDueDate)
        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 10, 28)), Recurring.occurrenceDatesInMonth(d.recurringExpenses.single(), 2026, 10))
    }

    // ---- a confirmed difference on a Loan moves the tracked debt too ---------------------------

    // SPEC:R65
    @Test fun `R65 a swept 260_00 Loan payment edited to 300_00 leaves the Loan at 3,960_00 with a -40_00 row on 20 September`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        val e = monthly("pay", "260.00", 20, due = LocalDate(2026, 9, 20), linked = "loan", swept = "2026-09")
        val w = world(e, accounts = listOf(loan("loan", "4,000.00", "pay", RateType.VARIABLE)))
        val (edited, r) = Recurring.edit(w, e.copy(amount = cents("300.00")), clock.today())
        val d = Recurring.applyReconcile(edited, r!!, clock) { ids.next() }
        assertEquals(cents("3,960.00"), d.accounts.single().balance)
        val row = d.transactions.single { it.key == "acct:loan" }
        assertEquals(cents("-40.00"), row.amount); assertEquals(LocalDate(2026, 9, 20), row.date)
        assertNull(row.categoryId)                                          // only the rec: row is filed under the category
    }
}
