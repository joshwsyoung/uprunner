package com.uprunner.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryIntentMatcherTest {

    @Test
    fun `matches pace query`() {
        assertEquals(TelemetryIntent.Pace, TelemetryIntentMatcher.match("what's my pace"))
    }

    @Test
    fun `matches distance query`() {
        assertEquals(TelemetryIntent.Distance, TelemetryIntentMatcher.match("how far have I gone"))
    }

    @Test
    fun `matches time query`() {
        assertEquals(TelemetryIntent.Time, TelemetryIntentMatcher.match("how long have I been running"))
    }

    @Test
    fun `conversational query does not match and falls through to the LLM tier`() {
        assertNull(TelemetryIntentMatcher.match("tell me a joke"))
    }

    @Test
    fun `empty transcript does not match`() {
        assertNull(TelemetryIntentMatcher.match(""))
    }
}
