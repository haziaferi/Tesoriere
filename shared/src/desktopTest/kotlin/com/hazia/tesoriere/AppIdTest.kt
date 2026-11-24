package com.hazia.tesoriere

import kotlin.test.Test
import kotlin.test.assertEquals

class AppIdTest {
    // SPEC:R03
    @Test
    fun `R03 the app id is com_hazia_tesoriere`() {
        assertEquals("com.hazia.tesoriere", APP_ID)
    }
}
