package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.number

/*
 * Ported in part from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Reminders.kt at
 * 2892110: the dates only (SPEC R69). The engine says when each reminder falls; delivering it is
 * the platform's, and how it reads is the design pass's (SPEC R16).
 *
 * Changed from Sci-Fin: `ReminderEntry` holds the expense's id and dates, not its name or amount.
 * Not ported: `registrations` and `ReminderRegistration` (the platform's), the notification
 * bodies, the payday reminder (with the Income piece), the registration keys and `localNow`.
 */

/** The hour, device time, at which every reminder fires (SPEC R69). */
const val NOTIFY_HOUR = 9

/** One recurring expense's reminder: which expense, the due date it is for, and the day it fires. */
data class ReminderEntry(val expenseId: String, val dueDate: LocalDate, val fireDate: LocalDate)

object Reminders {

    /** The lead counted back from `dueDate`, the live due date, so it follows the chain; off or finished: none (SPEC R69). */
    fun fireDate(e: RecurringExpense): LocalDate? {
        if (!e.reminderEnabled || e.finishedAt != null) return null
        return e.dueDate.minusDays(e.reminderLeadDays)
    }

    /** Every recurring expense's reminder, by fire date, then by the expense's name. */
    fun schedule(d: ProfileData): List<ReminderEntry> =
        d.recurringExpenses.mapNotNull { e -> fireDate(e)?.let { e.name to ReminderEntry(e.id, e.dueDate, it) } }
            .sortedWith(compareBy({ it.second.fireDate }, { it.first }))
            .map { it.second }

    /** The statement import reminder's day on or after [from]: its day of this month or the next, or null while off (SPEC R69). */
    fun nextImportReminder(d: ProfileData, from: LocalDate): LocalDate? {
        if (!d.importState.importReminderEnabled) return null
        val day = d.importState.importReminderDay.coerceIn(1, 28)
        val thisMonth = dateWithDay(from.year, from.month.number, day)
        return if (thisMonth >= from) thisMonth else from.firstOfNextMonth().let { dateWithDay(it.year, it.month.number, day) }
    }

    /** The New Month reminder: this month's 1st at 09:00 while it is ahead of [now], else next month's (SPEC R69). */
    fun nextNewMonthFire(now: LocalDateTime): LocalDateTime {
        val thisFirst = LocalDate(now.year, now.month, 1)
        val at = thisFirst.atTime(NOTIFY_HOUR, 0)
        return if (at > now) at else thisFirst.firstOfNextMonth().atTime(NOTIFY_HOUR, 0)
    }
}
