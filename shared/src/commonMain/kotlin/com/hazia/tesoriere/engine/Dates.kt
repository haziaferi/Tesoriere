package com.hazia.tesoriere.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Dates.kt at 2892110.
 *
 * Calendar-day arithmetic. A LocalDate has no zone to re-interpret, so nothing here depends on
 * where the device is (SPEC R20).
 */

/** `YYYY-MM`, the key a month is known by. */
fun LocalDate.ymKey(): String = toString().substring(0, 7)

private val YM = Regex("""(\d{4})-(\d{2})""")

/** The 1st of the month [ym] names as `YYYY-MM`, or null for a string that is no month: a stored mark may be corrupted. */
fun monthStart(ym: String?): LocalDate? {
    val m = ym?.let { YM.matchEntire(it) } ?: return null
    val month = m.groupValues[2].toInt()
    if (month !in 1..12) return null
    return LocalDate(m.groupValues[1].toInt(), month, 1)
}

/** [ym] when it names a month as `YYYY-MM`, else null: a corrupted month mark counts as no mark. */
fun monthMark(ym: String?): String? = ym?.takeIf { monthStart(it) != null }

fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)

/** Whole days from this to [other]; negative when [other] is earlier. */
fun LocalDate.daysTo(other: LocalDate): Int = this.daysUntil(other)

fun lastDayOfMonth(year: Int, month: Int): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minusDays(1).day

/** A day of the month, clamped to the month's length. */
fun dateWithDay(year: Int, month: Int, day: Int): LocalDate =
    LocalDate(year, month, minOf(day, lastDayOfMonth(year, month)))

/**
 * Month arithmetic that clamps rather than overflows: 31 January plus one month is 28 or 29
 * February, not 2 or 3 March. [anchorDay] lets a clamped date return to its own day the month
 * after, so a short month never becomes the new anchor.
 */
fun LocalDate.plusMonthsClamped(n: Int, anchorDay: Int = day): LocalDate {
    val shifted = LocalDate(year, month, 1).plus(n, DateTimeUnit.MONTH)
    return dateWithDay(shifted.year, shifted.month.number, anchorDay)
}

fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)
fun LocalDate.firstOfNextMonth(): LocalDate = firstOfMonth().plus(1, DateTimeUnit.MONTH)
