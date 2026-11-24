package com.hazia.tesoriere

import com.hazia.tesoriere.engine.firstOfNextMonth
import com.hazia.tesoriere.engine.lastDayOfMonth
import com.hazia.tesoriere.engine.plusMonthsClamped
import com.hazia.tesoriere.engine.ymKey
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Calendar-day arithmetic: dates are days with no zone (SPEC R20). */
class DatesTest {

    // SPEC:R20
    @Test fun `R20 2026-01-31 plus one month is clamped to 2026-02-28`() {
        assertEquals(LocalDate(2026, 2, 28), LocalDate(2026, 1, 31).plusMonthsClamped(1))
        assertEquals(LocalDate(2028, 2, 29), LocalDate(2028, 1, 31).plusMonthsClamped(1))
    }

    // SPEC:R20
    @Test fun `R20 a clamped date keeps its anchor day for the next month`() {
        assertEquals(LocalDate(2026, 3, 31), LocalDate(2026, 2, 28).plusMonthsClamped(1, anchorDay = 31))
    }

    // SPEC:R20
    @Test fun `R20 the month key of 2026-03-31 is 2026-03`() {
        assertEquals("2026-03", LocalDate(2026, 3, 31).ymKey())
    }

    // SPEC:R20
    @Test fun `R20 month lengths and the first of the next month`() {
        assertEquals(28, lastDayOfMonth(2026, 2))
        assertEquals(31, lastDayOfMonth(2026, 12))
        assertEquals(LocalDate(2027, 1, 1), LocalDate(2026, 12, 15).firstOfNextMonth())
    }
}
