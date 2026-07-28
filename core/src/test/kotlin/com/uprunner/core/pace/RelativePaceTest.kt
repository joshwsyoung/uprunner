package com.uprunner.core.pace

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativePaceTest {

    @Test
    fun `slower than target is positive (up)`() {
        assertEquals(14, RelativePace.diffSecPerKm(actualPaceSecPerKm = 314.0, targetPaceSecPerKm = 300.0))
    }

    @Test
    fun `faster than target is negative (down)`() {
        assertEquals(-15, RelativePace.diffSecPerKm(actualPaceSecPerKm = 285.0, targetPaceSecPerKm = 300.0))
    }

    @Test
    fun `exactly on target is zero`() {
        assertEquals(0, RelativePace.diffSecPerKm(actualPaceSecPerKm = 300.0, targetPaceSecPerKm = 300.0))
    }

    @Test
    fun `rounds to nearest second`() {
        assertEquals(4, RelativePace.diffSecPerKm(actualPaceSecPerKm = 303.6, targetPaceSecPerKm = 300.0))
    }
}
