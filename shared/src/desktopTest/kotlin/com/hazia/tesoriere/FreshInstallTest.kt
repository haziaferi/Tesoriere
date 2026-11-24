package com.hazia.tesoriere

import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.ProfileData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FreshInstallTest {

    // SPEC:R18
    @Test fun `R18 a fresh install has Income, Accounts, Savings vs Owed, Science, Overtime and Profiles off`() {
        val s = ProfileData.fresh("p", "Main").settings
        assertFalse(s.incomeEnabled)
        assertFalse(s.accountsEnabled)
        assertFalse(s.savingsVsOwedEnabled)
        assertFalse(s.scienceEnabled)
        assertFalse(s.overtimeEnabled)
        assertFalse(AppState(activeProfileId = "p").profilesEnabled)
    }

    // SPEC:R49
    @Test fun `R49 a fresh Profile's Savings is unset, not zero`() {
        assertNull(ProfileData.fresh("p", "Main").savings.amount)
    }

    // SPEC:R20
    @Test fun `R20 cents reads 42_30 as 4230 with no floating point`() {
        assertEquals(4230L, cents("42.30"))
        assertEquals(100000L, cents("1,000.00"))
        assertEquals(-6000L, cents("-60.00"))
        assertFailsWith<IllegalArgumentException> { cents("42.3") }
    }
}
