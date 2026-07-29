package com.uprunner.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnCueTest {

    @Test
    fun `recognizes plain left and right turns`() {
        assertEquals("Turn left", TurnCue.shortText("Turn left onto Main Street."))
        assertEquals("Turn right", TurnCue.shortText("Turn right onto Oak Avenue."))
    }

    @Test
    fun `recognizes sharp and slight turns before falling back to plain left-right`() {
        assertEquals("Sharp left", TurnCue.shortText("Turn sharp left onto Elm Street."))
        assertEquals("Sharp right", TurnCue.shortText("Turn sharp right onto Elm Street."))
        assertEquals("Bear left", TurnCue.shortText("Turn slight left to stay on Elm Street."))
        assertEquals("Bear right", TurnCue.shortText("Turn slight right to stay on Elm Street."))
    }

    @Test
    fun `recognizes u-turns and arrival`() {
        assertEquals("Make a U-turn", TurnCue.shortText("Make a U-turn at Main Street."))
        assertEquals("Arrive at destination", TurnCue.shortText("You have arrived at your destination."))
    }

    @Test
    fun `falls back to continue straight for anything else`() {
        assertEquals("Continue straight", TurnCue.shortText("Continue on Main Street."))
        assertEquals("Continue straight", TurnCue.shortText("Walk south on Main Street."))
    }
}
