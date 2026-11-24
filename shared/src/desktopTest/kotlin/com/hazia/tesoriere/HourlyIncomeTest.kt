package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.Hourly
import com.hazia.tesoriere.engine.Income
import com.hazia.tesoriere.engine.Reminders
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.engine.plusDays
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/HourlyIncomeTest.kt at
 * 2892110. Changed: figures are cents, the arrived amounts written out rather than computed from a
 * Double share; the payday notification test checks the reminder's date only, not its body (SPEC
 * R16). Not ported: the backup round trip (the backup piece, Task 19) and the time calculator's
 * rate (the Overtime piece, Task 12).
 */
class HourlyIncomeTest {
    private val clock = TestClock(LocalDate(2026, 11, 2))
    private val ids = Ids("h")
    private val rate = cents("20.00")

    /** Hourly since 1 November 2026, paid every two weeks from Friday 13 November. */
    private fun hourly(): ProfileData = profile(savings = "1,000.00").let {
        it.copy(settings = it.settings.copy(
            incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 11, 1), incomeCadence = IncomeCadence.BIWEEKLY,
            incomePayDate = LocalDate(2026, 11, 13), incomeType = IncomeType.HOURLY, hourlyRate = rate, lastPaydayHandled = LocalDate(2026, 10, 31),
        ))
    }

    private fun ProfileData.shift(day: Int, hours: Double, r: String = "20.00") =
        Hourly.logShift(this, Shift(ids.next(), profileId, LocalDate(2026, 11, day), hours, cents(r)))

    // ---- logged hours ----

    // SPEC:R80
    @Test fun `R80 a 6-hour shift at 20_00 on 3 November 2026 moves nothing`() {
        val d = hourly()
        val after = d.shift(3, 6.0)
        assertEquals(d.savings, after.savings)
        assertEquals(d.transactions, after.transactions)
        assertEquals(0, Income.receivedThisMonth(after, LocalDate(2026, 11, 3)))
        assertEquals(1, after.shifts.size)
    }

    // SPEC:R80 SPEC:R20
    @Test fun `R80 a shift of 1_5 hours at 13_33 comes to 20_00, the half cent rounded up`() {
        val d = hourly().shift(3, 1.5, "13.33")
        assertEquals(cents("20.00"), Hourly.gross(d.shifts))
    }

    // SPEC:R80
    @Test fun `R80 a shift can be edited or removed until a payday pays it`() {
        val d = hourly().shift(3, 6.0)
        val s = d.shifts.single()
        val edited = Hourly.editShift(d, s.copy(hours = 7.0))
        assertEquals(7.0, edited.shifts.single().hours)
        assertEquals(cents("140.00"), Hourly.gross(edited.shifts))
        assertTrue(Hourly.removeShift(edited, s.id).shifts.isEmpty())
    }

    // ---- the payday ----

    // SPEC:R81 SPEC:R80
    @Test fun `R81 nothing is asked on 12 November 2026, and on the 13th the payday covers 14_5 hours, 290_00`() {
        val d = hourly().shift(3, 6.0).shift(10, 8.5).shift(14, 4.0)
        assertNull(Hourly.duePayday(d, LocalDate(2026, 11, 12)))
        val p = assertNotNull(Hourly.duePayday(d, LocalDate(2026, 11, 13)))
        assertEquals(LocalDate(2026, 11, 13), p.date)
        assertEquals(14.5, p.hours)                                  // the 14th waits for the next payday
        assertEquals(cents("290.00"), p.gross)
    }

    // SPEC:R81 SPEC:R77
    @Test fun `R81 confirmed with 232_00, Savings goes from 1,000_00 to 1,232_00 by one income row dated 13 November`() {
        val d = hourly().shift(3, 6.0).shift(10, 8.5).shift(14, 4.0)
        val p = Hourly.duePayday(d, LocalDate(2026, 11, 13))!!
        val after = Hourly.confirmPayday(d, p, cents("232.00"), clock, ids.next())
        val tx = after.transactions.single()
        assertTrue(tx.isIncome); assertEquals(LocalDate(2026, 11, 13), tx.date); assertEquals(cents("232.00"), tx.amount)
        assertEquals(14.5, tx.hoursWorked); assertEquals(rate, tx.rate); assertEquals(StoredLabels.PAYDAY, tx.payee)
        assertEquals(cents("1,232.00"), after.savingsAmount)
        assertEquals(cents("232.00"), Income.receivedThisMonth(after, LocalDate(2026, 11, 13)))
        assertEquals(listOf(4.0), after.shifts.map { it.hours })
        assertNull(Hourly.duePayday(after, LocalDate(2026, 11, 20)), "answered, and the next is the 27th")
        assertEquals(LocalDate(2026, 11, 27), Hourly.duePayday(after, LocalDate(2026, 11, 27))!!.date)
    }

    // SPEC:R81
    @Test fun `R81 a payday cannot be confirmed with 0_00`() {
        val d = hourly().shift(3, 6.0)
        val p = Hourly.duePayday(d, LocalDate(2026, 11, 13))!!
        assertFailsWith<IllegalArgumentException> { Hourly.confirmPayday(d, p, 0, clock, ids.next()) }
    }

    // SPEC:R81
    @Test fun `R81 nothing this time keeps a 6-hour shift for 27 November, and with no hours 13 November is still asked`() {
        val d = hourly().shift(3, 6.0)
        val p = Hourly.duePayday(d, LocalDate(2026, 11, 13))!!
        val after = Hourly.nothingThisTime(d, p)
        assertEquals(d.savings, after.savings)
        assertEquals(6.0, Hourly.duePayday(after, LocalDate(2026, 11, 27))!!.hours)
        val empty = Hourly.duePayday(hourly(), LocalDate(2026, 11, 13))
        assertNotNull(empty); assertTrue(empty.shifts.isEmpty())
    }

    // ---- the estimate ----

    // SPEC:R82
    @Test fun `R82 10 hours at 20_00 are 200_00 before deductions, then 153_33 after 160_00, 160_00, 100_00 and 200_00 arrived`() {
        var d = hourly().shift(3, 10.0)
        assertEquals(cents("200.00") to true, Hourly.estimate(d, d.shifts))
        assertNull(Hourly.learnedRatio(d))
        for ((i, arrived) in listOf("160.00", "160.00", "100.00", "200.00").withIndex()) {
            val day = LocalDate(2026, 11, 13).plusDays(14 * i)
            val p = Hourly.duePayday(d, day)!!
            assertEquals(cents("200.00"), p.gross)
            d = Hourly.confirmPayday(d, p, cents(arrived), clock, ids.next())
            d = Hourly.logShift(d, Shift(ids.next(), d.profileId, day.plusDays(1), 10.0, rate))
        }
        assertEquals(cents("153.33") to false, Hourly.estimate(d, d.shifts), "(0.8 + 0.5 + 1.0) / 3 of 200.00")
    }

    // ---- salaried or hourly ----

    // SPEC:R79 SPEC:R71 SPEC:R78
    @Test fun `R79 hourly income with 1,000_00 and automatic deposit set deposits nothing by 31 December 2026 and gives no count`() {
        val d = hourly().let { it.copy(settings = it.settings.copy(incomeAutoDeposit = true, income = cents("1,000.00"))) }
        assertTrue(Hourly.isHourly(d.settings))
        assertEquals(0, Income.runCatchUp(d, TestClock(LocalDate(2026, 12, 31)), ids::next).second.count)
        assertNull(Income.noIncomeLoggedDays(d, LocalDate(2026, 12, 31)))
    }

    // SPEC:R79
    @Test fun `R79 changed to hourly on 2 November 2026, automatic deposit is off and no payday up to that day is asked`() {
        val salaried = profile().let { it.copy(settings = it.settings.copy(incomeEnabled = true, incomeAutoDeposit = true, lastIncomeDepositDate = LocalDate(2026, 10, 30))) }
        val switched = Hourly.setType(salaried, IncomeType.HOURLY, clock.today)
        assertFalse(switched.settings.incomeAutoDeposit)
        assertNull(switched.settings.lastIncomeDepositDate)
        assertEquals(clock.today, switched.settings.lastPaydayHandled)
        assertEquals(switched, Hourly.setType(switched, IncomeType.HOURLY, clock.today))     // the type it has: unchanged
    }

    // SPEC:R79
    @Test fun `R79 changed to hourly on its payday of 13 November 2026, it asks about 27 November, not the 13th`() {
        val onPayday = Hourly.setType(hourly().let { it.copy(settings = it.settings.copy(incomeType = IncomeType.SALARIED)) }, IncomeType.HOURLY, LocalDate(2026, 11, 13))
        assertNull(Hourly.duePayday(onPayday, LocalDate(2026, 11, 13)), "a change on a payday does not ask about it")
        assertEquals(LocalDate(2026, 11, 27), Hourly.duePayday(onPayday, LocalDate(2026, 11, 27))!!.date)
    }

    // ---- leaving hourly ----

    // SPEC:R83
    @Test fun `R83 shifts of 6 and 4 hours settled on 2 November 2026 with 170_00 leave one income row with 10 hours`() {
        val d = hourly().shift(3, 6.0).shift(4, 4.0)
        val confirmed = Hourly.settleOnSwitch(d, cents("170.00"), clock, ids.next())
        assertTrue(confirmed.shifts.isEmpty())
        val tx = confirmed.transactions.single()
        assertEquals(clock.today, tx.date); assertEquals(cents("170.00"), tx.amount); assertEquals(10.0, tx.hoursWorked)
    }

    // SPEC:R83 SPEC:R79
    @Test fun `R83 discarded, the shifts leave no row and the income can then be made salaried, but not with a shift logged`() {
        val d = hourly().shift(3, 6.0).shift(4, 4.0)
        assertFailsWith<IllegalArgumentException> { Hourly.setType(d, IncomeType.SALARIED, clock.today) }
        val discarded = Hourly.settleOnSwitch(d, null, clock, ids.next())
        assertTrue(discarded.shifts.isEmpty() && discarded.transactions.isEmpty())
        val salaried = Hourly.setType(discarded, IncomeType.SALARIED, clock.today).settings
        assertEquals(IncomeType.SALARIED, salaried.incomeType); assertNull(salaried.lastPaydayHandled)
    }

    // SPEC:R75 SPEC:R83
    @Test fun `R75 an hourly income with two shifts switched off has no shift and is salaried at 0_00`() {
        val off = Income.setEnabled(hourly().shift(3, 6.0).shift(4, 4.0), false, clock.today)
        assertTrue(off.shifts.isEmpty()); assertEquals(IncomeType.SALARIED, off.settings.incomeType)
        assertEquals(0L, off.settings.hourlyRate); assertNull(off.settings.lastPaydayHandled)
    }

    // ---- salaried by hand ----

    private fun salariedByHand() = profile().let {
        it.copy(settings = it.settings.copy(
            incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 11, 1), income = cents("1,450.00"),
            incomeCadence = IncomeCadence.MONTHLY, incomePayDate = LocalDate(2026, 11, 10),
        ))
    }

    // SPEC:R84
    @Test fun `R84 a 1,450_00 monthly income on the 10th offers nothing on 9 November 2026, the 10th on the 10th, nothing on the 12th, 10 December on 11 December`() {
        val d = salariedByHand()
        assertTrue(Hourly.manualPayday(d.settings))
        assertNull(Hourly.salariedPaydayToLog(d, LocalDate(2026, 11, 9)))
        assertEquals(LocalDate(2026, 11, 10), Hourly.salariedPaydayToLog(d, LocalDate(2026, 11, 10)))
        assertEquals(LocalDate(2026, 12, 10), Hourly.salariedPaydayToLog(d, LocalDate(2026, 12, 11)), "one offer, for the latest")
        assertNull(Hourly.salariedPaydayToLog(d, LocalDate(2026, 11, 12)), "two days on, the count of R78 has it")
    }

    // SPEC:R84
    @Test fun `R84 with 1,450_00 logged on 10 November 2026, 10 November already offered, or automatic deposit on, nothing is offered on the 11th`() {
        val d = salariedByHand()
        val logged = Writes.savingsQuickAdd(d, cents("1,450.00"), null, true, TestClock(LocalDate(2026, 11, 10)), "i-1", LocalDate(2026, 11, 10))
        assertNull(Hourly.salariedPaydayToLog(logged, LocalDate(2026, 11, 11)))
        val handled = d.copy(settings = d.settings.copy(lastPaydayHandled = LocalDate(2026, 11, 10)))
        assertNull(Hourly.salariedPaydayToLog(handled, LocalDate(2026, 11, 11)))
        val auto = d.copy(settings = d.settings.copy(incomeAutoDeposit = true))
        assertNull(Hourly.salariedPaydayToLog(auto, LocalDate(2026, 11, 11)))
        assertFalse(Hourly.manualPayday(auto.settings))
    }

    // ---- the payday reminder (SPEC R69) ----

    // SPEC:R69 SPEC:R79
    @Test fun `R69 an hourly income paid from 13 November 2026 reminds on 13 November read on the 2nd, on 27 November read at 09_00 on the 13th`() {
        assertEquals(LocalDate(2026, 11, 13).atTime(9, 0), Reminders.nextPaydayFire(hourly(), LocalDate(2026, 11, 2).atTime(12, 0)))
        assertEquals(LocalDate(2026, 11, 27).atTime(9, 0), Reminders.nextPaydayFire(hourly(), LocalDate(2026, 11, 13).atTime(9, 0)))
        assertEquals(LocalDate(2026, 11, 13), Hourly.nextPayday(hourly().settings, LocalDate(2026, 11, 2)))
    }

    // SPEC:R69
    @Test fun `R69 salaried with automatic deposit on, the payday reminder off, or Income off, has no payday reminder`() {
        val now = LocalDate(2026, 11, 2).atTime(12, 0)
        val manual = hourly().let { it.copy(settings = it.settings.copy(incomeType = IncomeType.SALARIED, income = cents("1,450.00"))) }
        assertEquals(LocalDate(2026, 11, 13).atTime(9, 0), Reminders.nextPaydayFire(manual, now))
        assertNull(Reminders.nextPaydayFire(manual.copy(settings = manual.settings.copy(incomeAutoDeposit = true)), now))
        assertNull(Reminders.nextPaydayFire(hourly().let { it.copy(settings = it.settings.copy(paydayNotificationEnabled = false)) }, now))
        assertNull(Reminders.nextPaydayFire(profile(), now))
    }
}
