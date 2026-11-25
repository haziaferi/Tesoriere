package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.Income
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/IncomeTest.kt at 2892110.
 * Changed: `moneyOf` figures are written as cents; `FakeClock` is `TestClock`. Not ported: the
 * month arithmetic test, which DatesTest holds (SPEC R20).
 */
class IncomeTest {
    private val day = LocalDate(2026, 9, 14)

    private fun incomeOn(
        cadence: IncomeCadence, payDate: LocalDate?, payDate2: LocalDate? = null, auto: Boolean = false,
        amount: String = "1,450.00", enabledOn: LocalDate = LocalDate(2026, 3, 1),
    ): ProfileData = profile(savings = "1,000.00").let {
        it.copy(settings = it.settings.copy(
            incomeEnabled = true, income = cents(amount), incomeCadence = cadence,
            incomePayDate = payDate, incomePayDate2 = payDate2, incomeAutoDeposit = auto, incomeEnabledDate = enabledOn,
        ))
    }

    // ---- the switch ----

    // SPEC:R75 SPEC:R18
    @Test fun `R75 a fresh Profile has Income off, salaried, with automatic deposit off`() {
        val s = profile().settings
        assertFalse(s.incomeEnabled); assertEquals(IncomeType.SALARIED, s.incomeType); assertFalse(s.incomeAutoDeposit)
        assertNull(s.incomePayDate); assertEquals(0L, s.income)
    }

    // SPEC:R75
    @Test fun `R75 switched off, a semimonthly 1,450_00 income on the 15th and 30th with automatic deposit clears every setting`() {
        val d = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30), auto = true)
            .let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = day, lastIncomeDepositDate = day)) }
        val off = Income.setEnabled(d, false, day)
        val s = off.settings
        assertFalse(s.incomeEnabled); assertEquals(0L, s.income); assertFalse(s.incomeAutoDeposit)
        assertNull(s.incomePayDate); assertNull(s.incomePayDate2); assertNull(s.incomeEnabledDate)
        assertNull(s.lastIncomeLoggedDate); assertNull(s.lastIncomeDepositDate)
        assertEquals(d.savings, off.savings)                                      // money already received stays
    }

    // SPEC:R75
    @Test fun `R75 switched on on 14 September 2026, Income has that day as its switch-on day and payday`() {
        val s = Income.setEnabled(profile(), true, day).settings
        assertTrue(s.incomeEnabled); assertEquals(day, s.incomeEnabledDate); assertEquals(day, s.incomePayDate)
    }

    // ---- paydays ----

    // SPEC:R76
    @Test fun `R76 a biweekly income first paid 11 September 2026 is next paid 11 September, 25 September, 9 October`() {
        val s = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 9, 11)).settings
        assertEquals(LocalDate(2026, 9, 11), Income.nextOccurrence(s, LocalDate(2026, 9, 11)))
        assertEquals(LocalDate(2026, 9, 25), Income.nextOccurrence(s, LocalDate(2026, 9, 12)))
        assertEquals(LocalDate(2026, 10, 9), Income.nextOccurrence(s, LocalDate(2026, 9, 26)))
    }

    // SPEC:R76
    @Test fun `R76 a weekly income first paid 11 September 2026 is next paid 18 September read on the 12th`() {
        val s = incomeOn(IncomeCadence.WEEKLY, LocalDate(2026, 9, 11)).settings
        assertEquals(LocalDate(2026, 9, 18), Income.nextOccurrence(s, LocalDate(2026, 9, 12)))
    }

    // SPEC:R76
    @Test fun `R76 a monthly income paid on 31 January 2026 is next paid 28 February, then 31 March`() {
        val s = incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 1, 31)).settings
        assertEquals(LocalDate(2026, 2, 28), Income.nextOccurrence(s, LocalDate(2026, 2, 1)))
        assertEquals(LocalDate(2026, 3, 31), Income.nextOccurrence(s, LocalDate(2026, 3, 1)))
        assertEquals(LocalDate(2026, 3, 31), Income.nextOccurrence(s, LocalDate(2026, 3, 31)))
    }

    // SPEC:R76
    @Test fun `R76 a semimonthly income on the 15th and 30th is paid 15 and 30 September, then 15 October, and on the 31st, 28 February`() {
        val s = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30)).settings
        assertEquals(LocalDate(2026, 9, 15), Income.nextOccurrence(s, LocalDate(2026, 9, 2)))
        assertEquals(LocalDate(2026, 9, 30), Income.nextOccurrence(s, LocalDate(2026, 9, 16)))
        assertEquals(LocalDate(2026, 10, 15), Income.nextOccurrence(s, LocalDate(2026, 10, 1)))
        val feb = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 1, 15), LocalDate(2026, 1, 31)).settings
        assertEquals(LocalDate(2026, 2, 28), Income.nextOccurrence(feb, LocalDate(2026, 2, 16)))
    }

    // SPEC:R76
    @Test fun `R76 with no payday chosen there is no payday`() {
        assertNull(Income.nextOccurrence(incomeOn(IncomeCadence.MONTHLY, null).settings, day))
    }

    // SPEC:R76
    @Test fun `R76 a semimonthly income changed to monthly has no payday, no second payday and no last deposit`() {
        val d = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30))
            .let { it.copy(settings = it.settings.copy(lastIncomeDepositDate = day)) }
        val s = Income.setCadence(d, IncomeCadence.MONTHLY).settings
        assertEquals(IncomeCadence.MONTHLY, s.incomeCadence)
        assertNull(s.incomePayDate); assertNull(s.incomePayDate2); assertNull(s.lastIncomeDepositDate)
        assertEquals(d, Income.setCadence(d, IncomeCadence.SEMIMONTHLY))              // the cadence it has: unchanged
    }

    // SPEC:R76
    @Test fun `R76 a monthly income changed to semimonthly keeps a second payday already chosen`() {
        val d = incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30))
        assertEquals(LocalDate(2026, 9, 30), Income.setCadence(d, IncomeCadence.SEMIMONTHLY).settings.incomePayDate2)
    }

    // SPEC:R76
    @Test fun `R76 the latest semimonthly payday on or before 16 September 2026 is 15 September`() {
        val s = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30)).settings
        assertEquals(LocalDate(2026, 9, 15), Income.mostRecentlyPassedAnchor(s, LocalDate(2026, 9, 16)))
        assertEquals(LocalDate(2026, 8, 30), Income.mostRecentlyPassedAnchor(s, LocalDate(2026, 9, 14)))
        assertNull(Income.mostRecentlyPassedAnchor(incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 9, 15)).settings, day))
    }

    // ---- automatic deposits (SPEC R71) ----

    // SPEC:R71
    @Test fun `R71 a biweekly income paid from 7 August 2026 deposits 7 and 21 August and 4 September by 14 September, once`() {
        val clock = TestClock(day)
        val ids = Ids()
        val d0 = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 8, 7), auto = true)
        val (d1, c) = Income.runCatchUp(d0, clock) { ids.next() }
        assertEquals(3, c.count)
        assertEquals(listOf(LocalDate(2026, 8, 7), LocalDate(2026, 8, 21), LocalDate(2026, 9, 4)), d1.transactions.map { it.date })
        assertEquals(cents("5,350.00"), d1.savingsAmount)
        assertEquals(LocalDate(2026, 9, 4), d1.settings.lastIncomeDepositDate)
        assertEquals(0, d1.settings.catchUpDepositCount)                     // the month cycle records it, only with a summary
        assertEquals(StoredLabels.AUTO_DEPOSIT, d1.transactions[0].payee)
        val (d2, c2) = Income.runCatchUp(d1, clock) { ids.next() }
        assertEquals(0, c2.count); assertEquals(d1.transactions.size, d2.transactions.size)
    }

    // SPEC:R71
    @Test fun `R71 with no deposit yet a monthly income paid from 10 June 2026 deposits from 10 June, 4 rows by 14 September`() {
        val (d1, c) = Income.runCatchUp(incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 6, 10), auto = true), TestClock(day)) { Ids().next() }
        assertEquals(4, c.count)
        assertEquals(LocalDate(2026, 6, 10), d1.transactions.first().date)
    }

    // SPEC:R71
    @Test fun `R71 a payday of 20 September 2026 deposits nothing on 14 September`() {
        val (d1, c) = Income.runCatchUp(incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 9, 20), auto = true), TestClock(day)) { Ids().next() }
        assertEquals(0, c.count); assertNull(d1.settings.lastIncomeDepositDate)
    }

    // SPEC:R71
    @Test fun `R71 automatic deposit off, or Income off, deposits nothing`() {
        val clock = TestClock(day)
        assertEquals(0, Income.runCatchUp(incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 6, 10), auto = false), clock) { Ids().next() }.second.count)
        assertEquals(0, Income.runCatchUp(profile(), clock) { Ids().next() }.second.count)
    }

    // ---- income received this month ----

    // SPEC:R77
    @Test fun `R77 switched on 5 September 2026, only the 1,450_00 of income dated 11 September counts on the 14th`() {
        val clock = TestClock(day)
        val ids = Ids()
        var d = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 9, 11), enabledOn = LocalDate(2026, 9, 5))
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "Salary", true, clock, ids.next(), date = LocalDate(2026, 9, 11))
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "Salary", true, clock, ids.next(), date = LocalDate(2026, 8, 28))
        d = Writes.savingsQuickAdd(d, cents("300.00"), "Earlier", true, clock, ids.next(), date = LocalDate(2026, 9, 2))
        d = Writes.savingsQuickAdd(d, cents("50.00"), "Gift", false, clock, ids.next(), date = LocalDate(2026, 9, 12))
        assertEquals(cents("1,450.00"), Income.receivedThisMonth(d, clock.today()))
    }

    // ---- no income logged ----

    // SPEC:R78
    @Test fun `R78 a biweekly income last logged 20 August 2026 gives 25 on 14 September, none on 3 September, 15 on 4 September`() {
        val d = incomeOn(IncomeCadence.BIWEEKLY, LocalDate(2026, 8, 7)).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 8, 20))) }
        assertEquals(25, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 3)))
        assertEquals(15, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 4)))
    }

    // SPEC:R78
    @Test fun `R78 a weekly income switched on 10 September 2026 with nothing logged gives none on the 14th and 8 on the 18th`() {
        val d = incomeOn(IncomeCadence.WEEKLY, LocalDate(2026, 9, 10), enabledOn = LocalDate(2026, 9, 10))
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
        assertEquals(8, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 18)))
    }

    // SPEC:R78
    @Test fun `R78 a monthly income on the 10th last logged 10 August 2026 gives none on 9 September and 31 on 10 September`() {
        val d = incomeOn(IncomeCadence.MONTHLY, LocalDate(2026, 8, 10)).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 8, 10))) }
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 9)))
        assertEquals(31, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 10)))
    }

    // SPEC:R78
    @Test fun `R78 a semimonthly income on the 15th and 30th last logged 1 September 2026 gives none on the 14th and 15 on the 16th`() {
        val d = incomeOn(IncomeCadence.SEMIMONTHLY, LocalDate(2026, 9, 15), LocalDate(2026, 9, 30)).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 9, 1))) }
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
        assertEquals(15, Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 16)))
    }

    // SPEC:R78
    @Test fun `R78 with automatic deposit on there is no count`() {
        val d = incomeOn(IncomeCadence.WEEKLY, LocalDate(2026, 8, 1), auto = true).let { it.copy(settings = it.settings.copy(lastIncomeLoggedDate = LocalDate(2026, 8, 1))) }
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 9, 14)))
    }
}
