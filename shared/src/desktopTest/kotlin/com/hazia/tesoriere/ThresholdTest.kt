package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.Threshold
import com.hazia.tesoriere.engine.ThresholdCode
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.Savings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/ThresholdTest.kt at 2892110. */
class ThresholdTest {
    private val today = LocalDate(2026, 9, 14)
    private val clock = TestClock(today)
    private fun rent(amount: String) = recurring("r", amount, Cadence.MONTHLY, LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 10, 5), name = "Rent")

    // SPEC:R68 SPEC:R49
    @Test fun `R68 with Income off and Savings 12,000_00, 900_00 shows no note, 1,100_00 does, -5_00 has no base, unset none`() {
        val d = profile(savings = "12,000.00").copy(recurringExpenses = listOf(rent("900.00")))
        assertNull(Threshold.recurringNote(d, today))                                                   // 900 at most 1,000
        val n = Threshold.recurringNote(d.copy(recurringExpenses = listOf(rent("1,100.00"))), today)!!
        assertEquals(ThresholdCode.SAVINGS_TWELFTH, n.code)
        assertEquals(cents("1,100.00"), n.scheduled); assertEquals(cents("12,000.00"), n.base); assertFalse(n.incomeOn)
        val broke = Threshold.recurringNote(d.copy(savings = Savings(Fixture.PROFILE, cents("-5.00"))), today)!!
        assertEquals(ThresholdCode.NO_SAVINGS_BASE, broke.code); assertTrue(broke.baseIsEmpty)
        assertNull(Threshold.recurringNote(d.copy(savings = Savings(Fixture.PROFILE, null)), today))    // no figure to take a twelfth of
        assertNull(Threshold.recurringNote(profile(savings = "0.00"), today))                           // nothing scheduled
    }

    // SPEC:R68
    @Test fun `R68 with Income on, 900_00 shows a note with no base, then base 1,450_00 after 1,450_00, none after 2,900_00`() {
        var d = profile(savings = "50,000.00").copy(recurringExpenses = listOf(rent("900.00")))
        d = d.copy(settings = d.settings.copy(incomeEnabled = true, incomeEnabledDate = LocalDate(2026, 9, 1)))
        val early = Threshold.recurringNote(d, today)!!                                                   // nothing landed yet
        assertEquals(ThresholdCode.NO_INCOME_BASE, early.code); assertTrue(early.incomeOn); assertTrue(early.baseIsEmpty)
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "pay", true, clock, "t1")
        val n = Threshold.recurringNote(d, today)!!                                                       // 900 above 483.33
        assertEquals(ThresholdCode.INCOME_THIRD, n.code)
        assertEquals(cents("1,450.00"), n.base)
        d = Writes.savingsQuickAdd(d, cents("1,450.00"), "pay", true, clock, "t2")
        assertNull(Threshold.recurringNote(d, today))                                                     // 900 at most 966.66: Savings is not read
    }
}
