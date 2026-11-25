package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.TimeWorth
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/TimeWorthTest.kt at 2892110.
 * Changed: figures are cents; the settings are a fresh synthetic Profile's, not the prototype
 * seed's. Not ported: the Learn topics' order and the topic's link to its screen (screens and
 * copy, SPEC R16, R18's Science row).
 */
class TimeWorthTest {
    private val settings = profile().settings

    private fun on(c: IncomeCadence, pay: String): Settings = settings.copy(incomeEnabled = true, income = cents(pay), incomeCadence = c)

    // SPEC:R102 SPEC:R18
    @Test fun `R102 the overtime calculator starts off on a fresh Profile`() {
        assertFalse(settings.overtimeEnabled)
    }

    // SPEC:R102 SPEC:R20
    @Test fun `R102 with Income off nothing is pre-filled, and 22_00 for 10 hours is 220_00 a month and 2,640_00 a year`() {
        val off = settings.copy(incomeEnabled = false)
        assertNull(TimeWorth.monthlyEquivalent(off)); assertNull(TimeWorth.prefillRate(off, 160.0))
        assertEquals(cents("220.00") to cents("2,640.00"), TimeWorth.worth(cents("22.00"), 10.0))
        assertEquals(cents("11.00") to cents("132.00"), TimeWorth.worth(cents("22.00"), 0.5))
        assertEquals(0L to 0L, TimeWorth.worth(cents("22.00"), -3.0))
    }

    // SPEC:R102 SPEC:R20
    @Test fun `R102 a half cent rounds up, 0_05 for 0_5 hours is 0_03 a month and 0_36 a year`() {
        assertEquals(cents("0.03") to cents("0.36"), TimeWorth.worth(cents("0.05"), 0.5))
    }

    // SPEC:R102
    @Test fun `R102 a month's pay is 3,200_00 from 3,200_00 monthly or 1,600_00 twice a month, 2,600_00 from 1,200_00 every two weeks or 600_00 weekly`() {
        assertEquals(cents("3,200.00"), TimeWorth.monthlyEquivalent(on(IncomeCadence.MONTHLY, "3,200.00")))
        assertEquals(cents("3,200.00"), TimeWorth.monthlyEquivalent(on(IncomeCadence.SEMIMONTHLY, "1,600.00")))
        assertEquals(cents("2,600.00"), TimeWorth.monthlyEquivalent(on(IncomeCadence.BIWEEKLY, "1,200.00")))
        assertEquals(cents("2,600.00"), TimeWorth.monthlyEquivalent(on(IncomeCadence.WEEKLY, "600.00")))
    }

    // SPEC:R102 SPEC:R20
    @Test fun `R102 1,000_01 weekly is 4,333_38 a month, 1,000_03 every two weeks is 2,166_73, and 0_03 every two weeks is 0_07, a half cent rounded up`() {
        // in cents: 100001 x 52 / 12 = 433337.67; 100003 x 26 / 12 = 216673.17; 3 x 26 / 12 = 6.5
        assertEquals(cents("4,333.38"), TimeWorth.monthlyEquivalent(on(IncomeCadence.WEEKLY, "1,000.01")))
        assertEquals(cents("2,166.73"), TimeWorth.monthlyEquivalent(on(IncomeCadence.BIWEEKLY, "1,000.03")))
        assertEquals(cents("0.07"), TimeWorth.monthlyEquivalent(on(IncomeCadence.BIWEEKLY, "0.03")))
    }

    // SPEC:R102
    @Test fun `R102 salaried at 3,200_00 a month over 160 hours pre-fills 20_00, over 0 hours or with no pay nothing`() {
        assertEquals(cents("20.00"), TimeWorth.prefillRate(on(IncomeCadence.MONTHLY, "3,200.00"), TimeWorth.DEFAULT_EXPECTED_HOURS))
        assertEquals(160.0, TimeWorth.DEFAULT_EXPECTED_HOURS)
        assertNull(TimeWorth.prefillRate(on(IncomeCadence.MONTHLY, "3,200.00"), 0.0), "no hours, no rate: never a division by zero")
        assertNull(TimeWorth.monthlyEquivalent(on(IncomeCadence.MONTHLY, "0.00")), "Income on with no pay is no pre-fill")
        assertNull(TimeWorth.prefillRate(on(IncomeCadence.MONTHLY, "0.00"), 160.0))
    }
}
