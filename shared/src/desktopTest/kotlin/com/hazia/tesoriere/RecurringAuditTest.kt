package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.AccountEditing
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.EndedKind
import com.hazia.tesoriere.engine.NewMonth
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/RecurringAuditTest.kt at
 * 2892110. The end-of-term tests run the month cycle (SPEC R70, R72) and give codes where Sci-Fin
 * gave sentences. The three that edit, delete or re-type an Account from its own side came with
 * the Accounts piece (SPEC R87, R94).
 */
class RecurringAuditTest {

    private fun monthly(
        id: String, amount: String, day: Int, due: LocalDate, first: LocalDate = LocalDate(2026, 1, day),
        linked: String? = null, endDate: LocalDate? = null, swept: String? = null,
    ) = recurring(id, amount, Cadence.MONTHLY, first, dueDate = due, endDate = endDate, linkedAccountId = linked, lastSweptMonth = swept)

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

    // ---- the end of a term -------------------------------------------------------------------

    // SPEC:R72
    @Test fun `R72 a payment ending 15 October 2026 is not finished on 20 October and finishes on 1 November with its closing row`() {
        val ids = Ids()
        val e = monthly("pay", "260.00", 15, due = LocalDate(2026, 10, 15), linked = "loan", endDate = LocalDate(2026, 10, 15), swept = "2026-10")
        val w = world(e, accounts = listOf(loan("loan", "120.00", "pay", RateType.FIXED)))
        val (mid, none) = NewMonth.runCatchUp(w, TestClock(LocalDate(2026, 10, 20))) { ids.next() }
        assertNull(none); assertNull(mid.recurringExpenses.single().finishedAt)
        val (d, s) = NewMonth.runCatchUp(mid, TestClock(LocalDate(2026, 11, 2))) { ids.next() }
        assertEquals(1, s!!.endedNow.size)
        assertEquals(LocalDate(2026, 11, 1), d.recurringExpenses.single().finishedAt)
        assertEquals(1, d.transactions.count { it.payee == StoredLabels.LOAN_FINISHED })
    }

    // SPEC:R72 SPEC:R70
    @Test fun `R72 a 40_00 gym fee ending 15 October 2026, opened on 20 November, sweeps 15 September and 15 October and leaves 4,920_00`() {
        val ids = Ids()
        val e = monthly("gym", "40.00", 15, due = LocalDate(2026, 9, 15), endDate = LocalDate(2026, 10, 15), swept = "2026-08")
        val (d, s) = NewMonth.runCatchUp(world(e), TestClock(LocalDate(2026, 11, 20))) { ids.next() }
        assertEquals(listOf(LocalDate(2026, 9, 15), LocalDate(2026, 10, 15)), d.transactions.filter { it.key == "rec:gym" }.map { it.date })
        assertEquals(cents("4,920.00"), d.savingsAmount)
        assertEquals(EndedKind.ENDED, s!!.endedNow.single().kind)
        assertNotNull(d.recurringExpenses.single().finishedAt)
    }

    // SPEC:R72
    @Test fun `R72 an interest-only Loan of 5,000_00 keeps its balance at the end of its term, with interestOnlyEnded and no closing row`() {
        val ids = Ids()
        val e = monthly("pay", "260.00", 15, due = LocalDate(2026, 8, 15), linked = "loan", endDate = LocalDate(2026, 8, 15), swept = "2026-08")
        val w = world(e, accounts = listOf(loan("loan", "5,000.00", "pay", RateType.INTEREST_ONLY)))
        val (d, s) = NewMonth.runCatchUp(w, TestClock(LocalDate(2026, 9, 1))) { ids.next() }
        assertEquals(EndedKind.INTEREST_ONLY_ENDED, s!!.endedNow.single().kind)
        assertEquals(cents("5,000.00"), d.accounts.single().balance)
        assertEquals(0, d.transactions.count { it.payee == StoredLabels.LOAN_FINISHED })
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

    // ---- linked both ways, one way at a time ---------------------------------------------------

    // SPEC:R87
    @Test fun `R87 a Loan linked to a 200_00 payment and linked again to a 250_00 one releases the first, and deleting it keeps the Loan`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        val x = monthly("x", "200.00", 5, due = LocalDate(2026, 10, 5), linked = "loan")
        val y = monthly("y", "250.00", 20, due = LocalDate(2026, 9, 20))
        val a = loan("loan", "4,000.00", "x", RateType.FIXED)
        var d = AccountEditing.save(world(x, y, accounts = listOf(a)), a.copy(linkedRecurringExpenseId = "y"), clock) { ids.next() }
        assertNull(d.recurringExpenses.first { it.id == "x" }.linkedAccountId)
        assertEquals("loan", d.recurringExpenses.first { it.id == "y" }.linkedAccountId)
        d = Recurring.delete(d, "x", reverse = false, clock) { ids.next() }
        assertEquals(1, d.accounts.size)
    }

    // ---- the return on deletion runs in the same step from the Account's side ------------------

    // SPEC:R87 SPEC:R66
    @Test fun `R87 a Loan whose 260_00 payment swept on 10 September 2026, deleted with the return accepted, leaves Savings at 5,000_00`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        var w = world(accounts = listOf(loan("loan", "4,000.00", "pay", RateType.FIXED)))
        w = Recurring.create(w, monthly("pay", "260.00", 20, due = LocalDate(2026, 9, 20), first = LocalDate(2026, 9, 20), linked = "loan"), clock) { ids.next() }
        assertEquals(cents("4,740.00"), w.savingsAmount)
        val d = Accounts.deleteCascade(w, "loan", clock, reverse = true) { ids.next() }
        assertTrue(d.accounts.isEmpty()); assertTrue(d.recurringExpenses.isEmpty())
        assertEquals(cents("5,000.00"), d.savingsAmount)
    }

    // SPEC:R94 SPEC:R66
    @Test fun `R94 a Loan changed to a card takes its 260_00 payment with it, the card stays and Savings is 5,000_00`() {
        val clock = TestClock(LocalDate(2026, 9, 10)); val ids = Ids()
        val a = loan("loan", "4,000.00", "pay", RateType.FIXED)
        var w = world(accounts = listOf(a))
        w = Recurring.create(w, monthly("pay", "260.00", 20, due = LocalDate(2026, 9, 20), first = LocalDate(2026, 9, 20), linked = "loan"), clock) { ids.next() }
        val draft = Accounts.retype(a, AccountType.CREDIT)
        assertTrue(Accounts.retypeNeedsCascade(w, draft))
        val d = Accounts.retypeToCreditCascade(w, draft, reverse = true, clock) { ids.next() }
        assertTrue(d.recurringExpenses.isEmpty())
        val out = d.accounts.single()
        assertEquals(AccountType.CREDIT, out.type); assertNull(out.linkedRecurringExpenseId)
        assertEquals(cents("5,000.00"), d.savingsAmount)
    }
}
