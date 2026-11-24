package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.NOTIFY_HOUR
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.ReminderEntry
import com.hazia.tesoriere.engine.Reminders
import com.hazia.tesoriere.model.Cadence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/RemindersTest.kt at 2892110:
 * the dates only. The registrations, their composed bodies and the money text are not ported
 * (delivery is the platform's, wording the design pass's, SPEC R16).
 */
class RemindersTest {

    private fun rent(due: LocalDate, lead: Int = 3, enabled: Boolean = true, finished: LocalDate? = null) =
        recurring("rent", "950.00", Cadence.MONTHLY, LocalDate(2026, 1, 31), dueDate = due, name = "Rent",
            reminderEnabled = enabled, reminderLeadDays = lead, finishedAt = finished)

    // SPEC:R69 SPEC:R61
    @Test fun `R69 a rent on the 31st with a 3-day lead reminds on 25 February 2026 and then 28 March`() {
        val d = profile().copy(recurringExpenses = listOf(rent(LocalDate(2026, 1, 31))))
        val feb = Recurring.advanceDueDates(d, LocalDate(2026, 2, 1))
        assertEquals(listOf(ReminderEntry("rent", LocalDate(2026, 2, 28), LocalDate(2026, 2, 25))), Reminders.schedule(feb))
        val mar = Recurring.advanceDueDates(d, LocalDate(2026, 3, 1))
        assertEquals(LocalDate(2026, 3, 28), Reminders.schedule(mar).single().fireDate)
    }

    // SPEC:R69
    @Test fun `R69 a reminder turned off, or on a finished expense, has no date`() {
        assertNull(Reminders.fireDate(rent(LocalDate(2026, 9, 30), enabled = false)))
        assertNull(Reminders.fireDate(rent(LocalDate(2026, 9, 30), finished = LocalDate(2026, 9, 1))))
        assertEquals(false, recurring("x", "1.00", Cadence.MONTHLY, LocalDate(2026, 9, 1)).reminderEnabled)   // off until turned on
    }

    // SPEC:R69
    @Test fun `R69 the schedule is in fire-date order, a 14-day lead on 30 September firing on the 16th`() {
        val water = recurring("water", "40.00", Cadence.MONTHLY, LocalDate(2026, 9, 30), name = "Water", reminderEnabled = true, reminderLeadDays = 14)
        val phone = recurring("phone", "20.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), name = "Phone", reminderEnabled = true, reminderLeadDays = 1)
        val d = profile().copy(recurringExpenses = listOf(phone, water))
        assertEquals(listOf("water" to LocalDate(2026, 9, 16), "phone" to LocalDate(2026, 9, 19)), Reminders.schedule(d).map { it.expenseId to it.fireDate })
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate(y, m, d).atTime(h, min)

    // SPEC:R69
    @Test fun `R69 the New Month reminder read at 08_30 on 1 October 2026 is that day at 09_00, read at 09_00 it is 1 November`() {
        assertEquals(9, NOTIFY_HOUR)
        assertEquals(at(2026, 10, 1, 9), Reminders.nextNewMonthFire(at(2026, 10, 1, 8, 30)))
        assertEquals(at(2026, 11, 1, 9), Reminders.nextNewMonthFire(at(2026, 10, 1, 9, 0)))
        assertEquals(at(2026, 11, 1, 9), Reminders.nextNewMonthFire(at(2026, 10, 14, 12)))
        assertEquals(at(2027, 1, 1, 9), Reminders.nextNewMonthFire(at(2026, 12, 31, 23)))
    }

    // SPEC:R69
    @Test fun `R69 the import reminder on day 1 read on 14 September 2026 falls on 1 October, and is off until turned on`() {
        val off = profile()
        assertNull(Reminders.nextImportReminder(off, LocalDate(2026, 9, 14)))
        val on = off.copy(importState = off.importState.copy(importReminderEnabled = true, importReminderDay = 1))
        assertEquals(LocalDate(2026, 10, 1), Reminders.nextImportReminder(on, LocalDate(2026, 9, 14)))
        val day20 = on.copy(importState = on.importState.copy(importReminderDay = 20))
        assertEquals(LocalDate(2026, 9, 20), Reminders.nextImportReminder(day20, LocalDate(2026, 9, 14)))
        assertEquals(LocalDate(2026, 9, 14), Reminders.nextImportReminder(day20.copy(importState = day20.importState.copy(importReminderDay = 14)), LocalDate(2026, 9, 14)))
    }
}
