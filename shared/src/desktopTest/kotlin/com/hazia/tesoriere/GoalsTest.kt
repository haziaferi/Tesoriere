package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.GoalFrame
import com.hazia.tesoriere.engine.Goals
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.GoalMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val GoalDerivationNone = com.hazia.tesoriere.engine.GoalDerivation(null, null)

/** Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/GoalsTest.kt at 2892110. */
class GoalsTest {
    private val today = LocalDate(2026, 9, 14)
    private val p = Fixture.PROFILE

    // ---- the calculator ------------------------------------------------------------------

    // SPEC:R55
    @Test fun `R55 a 1,200_00 goal with 400_00 saved and a date six months ahead needs 133_34 a month`() {
        val g = Goal("g", p, "Bike", saved = cents("400.00"), target = cents("1,200.00"), targetDate = LocalDate(2027, 3, 14), mode = GoalMode.DATE)
        val d = Goals.derive(g, today)
        assertEquals(6, d.months)
        assertEquals(cents("133.34"), d.monthly)
    }

    // SPEC:R55
    @Test fun `R55 the same goal at 150_00 a month takes 6 months`() {
        val g = Goal("g", p, "Bike", saved = cents("400.00"), target = cents("1,200.00"), monthly = cents("150.00"), mode = GoalMode.AMOUNT)
        assertEquals(6, Goals.derive(g, today).months)
        assertEquals(cents("150.00"), Goals.derive(g, today).monthly)
    }

    // SPEC:R55
    @Test fun `R55 months are calendar months from this month to the target's, at least 1`() {
        assertEquals(1, Goals.calendarMonthsUntil(today, LocalDate(2026, 9, 30)))
        assertEquals(1, Goals.calendarMonthsUntil(today, LocalDate(2026, 10, 1)))
        assertEquals(8, Goals.calendarMonthsUntil(today, LocalDate(2027, 5, 14)))
        assertEquals(1, Goals.calendarMonthsUntil(today, LocalDate(2026, 1, 1)))
    }

    // SPEC:R55
    @Test fun `R55 by date rounds the monthly amount up to the cent`() {
        val g = Goal("g", p, "x", target = cents("100.00"), targetDate = LocalDate(2026, 11, 1), mode = GoalMode.DATE)
        assertEquals(cents("50.00"), Goals.derive(g, today).monthly)
        assertEquals(cents("33.34"), Goals.derive(g.copy(targetDate = LocalDate(2026, 12, 1)), today).monthly)
    }

    // SPEC:R55
    @Test fun `R55 by amount rounds the months up to a whole month`() {
        val g = Goal("g", p, "x", target = cents("900.00"), monthly = cents("74.00"), mode = GoalMode.AMOUNT, saved = cents("400.00"))
        assertEquals(7, Goals.derive(g, today).months)        // 500.00 / 74.00 = 6.76
    }

    // SPEC:R55
    @Test fun `R55 no target means no monthly amount, and a reached goal needs nothing more`() {
        assertNull(Goals.derive(Goal("g", p, "Someday"), today).monthly)
        val reached = Goal("g", p, "x", saved = cents("500.00"), target = cents("500.00"), targetDate = LocalDate(2027, 1, 1))
        assertEquals(0L, Goals.derive(reached, today).monthly)
    }

    // SPEC:R55
    @Test fun `R55 money added under a date lowers the monthly amount when it is read again`() {
        val g = Goal("g", p, "x", target = cents("1,000.00"), targetDate = LocalDate(2027, 7, 14), mode = GoalMode.DATE)   // 10 months
        var d = profile(savings = "5,000.00", goals = listOf(Goals.rederive(g, today)))
        assertEquals(cents("100.00"), d.goals[0].monthly)
        d = Writes.goalQuickAdd(d, "g", cents("500.00"), null, TestClock(today), "t1")
        d = d.copy(goals = d.goals.map { Goals.rederive(it, today) })
        assertEquals(cents("50.00"), d.goals[0].monthly)
    }

    // ---- where a goal fits ---------------------------------------------------------------

    // SPEC:R56
    @Test fun `R56 another goal stops counting once its saved money reaches its target`() {
        assertTrue(Goals.commitmentEnded(Goal("g", p, "x", saved = cents("200.00"), target = cents("200.00"))))
        assertFalse(Goals.commitmentEnded(Goal("g", p, "x", saved = cents("199.00"), target = cents("200.00"))))
        assertFalse(Goals.commitmentEnded(Goal("g", p, "x", saved = cents("999.00"))))
    }

    // SPEC:R56
    @Test fun `R56 the other goals leave out reached goals, goals with no amount and the goal itself, and keep a passed date`() {
        val d = profile(goals = listOf(
            Goal("a", p, "A", target = cents("600.00"), monthly = cents("200.00"), mode = GoalMode.AMOUNT),
            Goal("b", p, "B", saved = cents("600.00"), target = cents("600.00"), monthly = cents("200.00"), mode = GoalMode.AMOUNT),
            Goal("c", p, "C"),
            Goal("d", p, "D", target = cents("500.00"), targetDate = LocalDate(2026, 1, 1), mode = GoalMode.DATE),
            Goal("e", p, "E", target = cents("300.00"), monthly = cents("100.00"), mode = GoalMode.AMOUNT),
        ))
        assertEquals(cents("700.00"), Goals.otherGoalsMonthlyTotal(d, "e", today))    // A 200.00 + D 500.00
    }

    // SPEC:R56
    @Test fun `R56 with Income on the room is income received minus recurring minus other goals`() {
        var d = profile(savings = "2,000.00", goals = listOf(Goal("a", p, "A", target = cents("600.00"), monthly = cents("200.00"), mode = GoalMode.AMOUNT)))
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 1, 1)))
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "Salary", true, TestClock(today), "t1", date = LocalDate(2026, 9, 5))
        val f = Goals.frame(d, cents("150.00"), excludeId = "new", scheduledRecurring = cents("1,000.00"), today = today) as GoalFrame.IncomeOn
        assertEquals(cents("250.00"), f.available)
        assertTrue(f.fits)
        assertFalse((Goals.frame(d, cents("300.00"), "new", cents("1,000.00"), today) as GoalFrame.IncomeOn).fits)
    }

    // SPEC:R56
    @Test fun `R56 with Income off a goal at 133_34 adds up to 633_34 against 1,000_00 and fits`() {
        val other = Goal("a", p, "A", target = cents("600.00"), monthly = cents("200.00"), mode = GoalMode.AMOUNT)
        val d = profile(savings = "1,000.00", goals = listOf(other))
        val f = Goals.frame(d, cents("133.34"), "new", cents("300.00"), today) as GoalFrame.IncomeOff
        assertEquals(cents("633.34"), f.committed); assertEquals(cents("1,000.00"), f.savings); assertTrue(f.fits)
        val reached = d.copy(goals = listOf(other.copy(saved = cents("600.00"))))
        assertEquals(cents("433.34"), (Goals.frame(reached, cents("133.34"), "new", cents("300.00"), today) as GoalFrame.IncomeOff).committed)
    }

    // ---- gentler amounts -----------------------------------------------------------------

    // SPEC:R57
    @Test fun `R57 with Income off and Savings 2,400_00 a goal at 250_00 is offered 120_00, 150_00 and 200_00`() {
        val d = profile(savings = "2,400.00")
        assertNull(Goals.sustainability(d, cents("200.00"), today))
        val s = Goals.sustainability(d, cents("250.00"), today)!!
        assertFalse(s.incomeOn)
        assertEquals(listOf(cents("120.00"), cents("150.00"), cents("200.00")), s.suggestions)
    }

    // SPEC:R57
    @Test fun `R57 with Income on the base is income received, one sixth the threshold, amounts rounded to a whole unit`() {
        var d = profile(savings = "100,000.00")
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 1, 1)))
        d = Writes.savingsQuickAdd(d, cents("1,200.00"), "Salary", true, TestClock(today), "t1", date = LocalDate(2026, 9, 5))
        assertNull(Goals.sustainability(d, cents("200.00"), today))
        val s = Goals.sustainability(d, cents("201.00"), today)!!
        assertTrue(s.incomeOn)
        assertEquals(listOf(cents("86.00"), cents("100.00"), cents("200.00")), s.suggestions)     // 85.71 rounds to 86.00
    }

    // SPEC:R49 SPEC:R56 SPEC:R57
    @Test fun `R49 with Income off and Savings unset the goal checks are skipped, not read as zero`() {
        val d = profile(savings = null)
        assertNull(Goals.frame(d, cents("250.00"), "new", cents("300.00"), today))
        assertNull(Goals.sustainability(d, cents("250.00"), today))
    }

    // SPEC:R49 SPEC:R56
    @Test fun `R49 with Income on the room is worked out even while Savings is unset`() {
        var d = profile(savings = null)
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 1, 1)))
        val f = Goals.frame(d, cents("100.00"), "new", cents("0.00"), today) as GoalFrame.IncomeOn
        assertEquals(0L, f.available)
    }

    // SPEC:R55
    @Test fun `R55 a target of zero has no monthly amount, even with a date`() {
        val g = Goal("g", p, "Jar", target = 0, targetDate = LocalDate(2027, 3, 14), mode = GoalMode.DATE)
        assertEquals(GoalDerivationNone, Goals.derive(g, today))
    }

    // SPEC:R57
    @Test fun `R57 a base of zero or less offers no amounts`() {
        assertNull(Goals.sustainability(profile(savings = "0.00"), cents("10.00"), today)!!.suggestions)
        assertNull(Goals.sustainability(profile(savings = "-50.00"), cents("10.00"), today)!!.suggestions)
    }
}
