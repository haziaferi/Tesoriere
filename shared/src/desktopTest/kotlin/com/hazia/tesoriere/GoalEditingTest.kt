package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.GoalDraft
import com.hazia.tesoriere.engine.GoalEditing
import com.hazia.tesoriere.engine.Goals
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.GoalMode
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/GoalEditingTest.kt at 2892110. */
class GoalEditingTest {
    private val today = LocalDate(2026, 9, 14)
    private val clock = TestClock(today)
    private val ids = Ids("g")

    // SPEC:R55 SPEC:R52
    @Test fun `R55 by date stores the date and works out the monthly amount, rounding up`() {
        val draft = GoalDraft(name = "Trip", target = cents("1,200.00"), saved = cents("400.00"), mode = GoalMode.DATE, months = 6).synced(GoalMode.DATE)
        assertEquals(cents("133.34"), draft.monthly)
        val d = GoalEditing.save(profile(savings = "1,000.00"), draft, clock, ids::next)
        val g = d.goals.single()
        assertEquals(LocalDate(2027, 3, 14), g.targetDate); assertEquals(GoalMode.DATE, g.mode)
        assertEquals(6, Goals.derive(g, today).months)
        // money already saved on a new Goal leaves Savings and is logged
        assertEquals(cents("400.00"), g.saved); assertEquals(cents("600.00"), d.savingsAmount)
        assertEquals(StoredLabels.STARTING_BALANCE, d.transactions.single().payee)
    }

    // SPEC:R55
    @Test fun `R55 a rename keeps the stored date, even a passed one`() {
        for (deadline in listOf(LocalDate(2026, 8, 1), LocalDate(2027, 1, 20))) {
            val g = Goal("g1", Fixture.PROFILE, "Trip", null, saved = cents("100.00"), target = cents("1,200.00"), targetDate = deadline, mode = GoalMode.DATE)
            val d0 = profile().copy(goals = listOf(g))
            val d = GoalEditing.save(d0, GoalDraft.of(g, today).copy(name = "Holiday"), clock, ids::next)
            assertEquals(deadline, d.goals.single().targetDate, "a save that did not touch the months moved $deadline")
            val moved = GoalEditing.save(d0, GoalDraft.of(g, today).copy(months = 3).synced(GoalMode.DATE), clock, ids::next)
            assertEquals(LocalDate(2026, 12, 14), moved.goals.single().targetDate)
        }
    }

    // SPEC:R55
    @Test fun `R55 by amount stores the monthly amount, not a date, and the months round up`() {
        val draft = GoalDraft(name = "Laptop", target = cents("1,000.00"), mode = GoalMode.AMOUNT, monthly = cents("300.00")).synced(GoalMode.AMOUNT)
        assertEquals(4, draft.months)
        val g = GoalEditing.save(profile(), draft, clock, ids::next).goals.single()
        assertNull(g.targetDate); assertEquals(cents("300.00"), g.monthly); assertEquals(4, Goals.derive(g, today).months)
    }

    // SPEC:R55 SPEC:R52
    @Test fun `R55 no target stores nothing to divide, and an edit of saved money logs a correction`() {
        var d = GoalEditing.save(profile(savings = "1,000.00"), GoalDraft(name = "Rainy day", noTarget = true), clock, ids::next)
        val g = d.goals.single()
        assertNull(g.target); assertNull(g.targetDate); assertNull(g.monthly); assertTrue(d.transactions.isEmpty())
        d = GoalEditing.save(d, GoalDraft.of(g, today).copy(saved = cents("50.00"), name = "Rainy day fund"), clock, ids::next)
        assertEquals("Rainy day fund", d.goals.single().name); assertEquals(cents("50.00"), d.goals.single().saved)
        assertEquals(cents("950.00"), d.savingsAmount); assertEquals(StoredLabels.CORRECTION, d.transactions.single().payee)
    }

    // SPEC:R55
    @Test fun `R55 a target of zero is a running tally`() {
        val d = GoalEditing.save(profile(), GoalDraft(name = "Jar", target = 0, mode = GoalMode.DATE, months = 6), clock, ids::next)
        assertNull(d.goals.single().target); assertNull(d.goals.single().monthly)
    }

    // SPEC:R58
    @Test fun `R58 deleting a Goal holding 300_00 returns it only when asked, and the history stays either way`() {
        val d0 = GoalEditing.save(profile(savings = "1,000.00"), GoalDraft(name = "Trip", target = cents("500.00"), saved = cents("300.00")).synced(GoalMode.DATE), clock, ids::next)
        val id = d0.goals.single().id
        val kept = GoalEditing.delete(d0, id, returnSaved = false, clock, ids::next)
        assertTrue(kept.goals.isEmpty()); assertEquals(cents("700.00"), kept.savingsAmount); assertEquals(1, kept.transactions.size)
        val back = GoalEditing.delete(d0, id, returnSaved = true, clock, ids::next)
        assertTrue(back.goals.isEmpty()); assertEquals(cents("1,000.00"), back.savingsAmount); assertEquals(2, back.transactions.size)
        val row = back.transactions.last()
        assertEquals("goal:$id", row.key); assertEquals(cents("-300.00"), row.amount)
        assertEquals(StoredLabels.GOAL_DELETED_RETURNED, row.payee)
    }

    // SPEC:R17
    @Test fun `R17 a blank name saves nothing, as in Sci-Fin`() {
        val d = profile()
        assertEquals(d, GoalEditing.save(d, GoalDraft(name = "  "), clock, ids::next))
    }
}
