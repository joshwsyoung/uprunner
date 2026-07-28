package com.uprunner.core.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsFormatterTest {

    @Test
    fun `pace with colon and slash-km is spoken as per K`() {
        assertEquals("5 45 per K", TtsFormatter.format("5:45/km"))
    }

    @Test
    fun `decimal kilometers is spoken with point`() {
        assertEquals("4 point 3 kilometers", TtsFormatter.format("4.33 km"))
    }

    @Test
    fun `negative relative time is spoken as down`() {
        assertEquals("15 seconds down", TtsFormatter.format("-15s"))
    }

    @Test
    fun `positive relative time is spoken as up`() {
        assertEquals("10 seconds up", TtsFormatter.format("+10s"))
    }

    @Test
    fun `bare numeric dash is spaced out`() {
        assertEquals("5 - 55 per K.", TtsFormatter.format("5-55 per K."))
    }

    @Test
    fun `combined sentence applies rules in the correct order`() {
        val input = "Pace 5:45/km, that's -15s versus target, distance 4.33 km, keep going 5-55."
        val expected = "Pace 5 45 per K, that's 15 seconds down versus target, " +
            "distance 4 point 3 kilometers, keep going 5 - 55."
        assertEquals(expected, TtsFormatter.format(input))
    }
}
