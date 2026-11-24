package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.EndedKind
import com.hazia.tesoriere.engine.Income
import com.hazia.tesoriere.engine.NewMonth
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.RateType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/CatchUpAuditTest.kt at
 * 2892110. Changed: the restart test runs on the engine (the stored summary read back with
 * `persistedSummary`), since the app store is not ported; the first switch-on sets the automatic
 * deposit directly. Not ported here: switching to a Profile last open months ago (the app store,
 * and a Profile with only categories has no month to process, SPEC R70), and turning automatic
 * deposit back on (`Income.setAutoDeposit`, ported with Income).
 */
class CatchUpAuditTest {
    private val ids = Ids("cu")

    private fun rent(swept: String) =
        recurring("rent", "950.00", Cadence.MONTHLY, LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 9, 1), lastSweptMonth = swept, name = "Rent")

    private fun world() = profile(savings = "5,000.00", categories = listOf(category("c1"))).copy(recurringExpenses = listOf(rent("2026-08")))

    // SPEC:R74
    @Test fun `R74 a 950_00 rent swept for August, opened on 14 November 2026, keeps 3 months and 2,850_00 until dismissed`() {
        val clock = TestClock(LocalDate(2026, 11, 14))
        val (first, _) = NewMonth.runCatchUp(world(), clock) { ids.next() }
        val (again, none) = NewMonth.runCatchUp(first, clock) { ids.next() }           // the same day: nothing left to process
        assertNull(none)
        val kept = NewMonth.persistedSummary(again.settings)!!
        assertEquals(3, kept.monthsReplayed); assertEquals(cents("2,850.00"), kept.sweptTotal)
        val dismissed = NewMonth.dismissed(again.settings)
        assertNull(NewMonth.persistedSummary(dismissed))
        assertFalse(dismissed.catchUpSummaryPending)
        assertEquals(0, dismissed.catchUpMonths); assertEquals(0L, dismissed.catchUpSwept); assertEquals("", dismissed.catchUpEnded)
    }

    // SPEC:R74
    @Test fun `R74 a summary of 2 months from 14 October 2026 stays 2 on 3 November and reads 4 on 3 January 2027`() {
        val (afterAbsence, _) = NewMonth.runCatchUp(world(), TestClock(LocalDate(2026, 10, 14))) { ids.next() }
        assertEquals(2, NewMonth.persistedSummary(afterAbsence.settings)!!.monthsReplayed)
        val (afterBoundary, _) = NewMonth.runCatchUp(afterAbsence, TestClock(LocalDate(2026, 11, 3))) { ids.next() }
        assertEquals(2, NewMonth.persistedSummary(afterBoundary.settings)!!.monthsReplayed)
        val (afterSecond, _) = NewMonth.runCatchUp(afterBoundary, TestClock(LocalDate(2027, 1, 3))) { ids.next() }
        assertEquals(4, NewMonth.persistedSummary(afterSecond.settings)!!.monthsReplayed)
    }

    // SPEC:R74
    @Test fun `R74 an open on 1 September 2026 that crosses one month and finishes nothing keeps no summary`() {
        val (d, s) = NewMonth.runCatchUp(world(), TestClock(LocalDate(2026, 9, 1))) { ids.next() }
        assertEquals(1, s!!.monthsReplayed)
        assertNull(NewMonth.persistedSummary(d.settings))
    }

    // SPEC:R74 SPEC:R72
    @Test fun `R74 a finished Loan payment is kept as its id and the code loanPaidOff, never a sentence`() {
        val e = recurring("pay", "260.00", Cadence.MONTHLY, LocalDate(2026, 1, 15), dueDate = LocalDate(2026, 9, 15),
            endDate = LocalDate(2026, 8, 15), linkedAccountId = "loan", lastSweptMonth = "2026-08", name = "Car loan payment")
        val loan = Account("loan", Fixture.PROFILE, "Car loan", null, AccountType.LOAN, balance = cents("120.00"), linkedRecurringExpenseId = "pay", rateType = RateType.FIXED)
        val w = world().copy(recurringExpenses = listOf(e), accounts = listOf(loan))
        val (d, _) = NewMonth.runCatchUp(w, TestClock(LocalDate(2026, 9, 1))) { ids.next() }
        assertEquals("pay\tloanPaidOff", d.settings.catchUpEnded)
        val kept = NewMonth.persistedSummary(d.settings)!!
        assertEquals("pay", kept.endedNow.single().expenseId); assertEquals(EndedKind.LOAN_PAID_OFF, kept.endedNow.single().kind)
    }

    // SPEC:R71
    @Test fun `R71 a first switch-on on 14 September 2026 of a 100_00 monthly income paid the 5th deposits 5 August and 5 September`() {
        val base = profile(savings = "1,000.00")
        val d = base.copy(settings = base.settings.copy(incomeEnabled = true, income = cents("100.00"), incomeCadence = IncomeCadence.MONTHLY,
            incomePayDate = LocalDate(2026, 8, 5), incomeAutoDeposit = true))
        val (after, c) = Income.runCatchUp(d, TestClock(LocalDate(2026, 9, 14))) { ids.next() }
        assertEquals(2, c.count); assertEquals(cents("1,200.00"), after.savingsAmount)
        assertEquals(listOf(LocalDate(2026, 8, 5), LocalDate(2026, 9, 5)), after.transactions.map { it.date })
    }

    // SPEC:R71
    @Test fun `R71 a semimonthly income with no second payday chosen has no next payday and deposits nothing`() {
        val base = profile(savings = "1,000.00")
        val s = base.settings.copy(incomeEnabled = true, income = cents("100.00"), incomeCadence = IncomeCadence.SEMIMONTHLY,
            incomePayDate = LocalDate(2026, 9, 1), incomePayDate2 = null, incomeAutoDeposit = true)
        assertNull(Income.nextOccurrence(s, LocalDate(2026, 9, 14)))
        val (after, c) = Income.runCatchUp(base.copy(settings = s), TestClock(LocalDate(2026, 9, 14))) { ids.next() }
        assertEquals(0, c.count); assertEquals(cents("1,000.00"), after.savingsAmount)
    }
}
