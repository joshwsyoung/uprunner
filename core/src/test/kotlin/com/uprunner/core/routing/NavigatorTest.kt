package com.uprunner.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigatorTest {

    // Roughly west-east segments near the equator, ~111.19m per 0.001 degrees longitude.
    private val routePoints = listOf(0.0 to 0.0, 0.0 to 0.001, 0.0 to 0.002)
    private val maneuvers = listOf(
        ValhallaRouting.Maneuver("Turn left onto Second Street.", beginPointIndex = 1),
        ValhallaRouting.Maneuver("You have arrived at your destination.", beginPointIndex = 2),
    )

    @Test
    fun `at the start, the next maneuver is the first turn`() {
        val state = Navigator.computeState(routePoints, maneuvers, position = 0.0 to 0.0)

        assertEquals("Turn left", state?.nextManeuverCue)
        assertEquals(111.19, state?.distanceToNextManeuverMeters ?: -1.0, 1.0)
        assertEquals(222.38, state?.distanceRemainingMeters ?: -1.0, 1.0)
    }

    @Test
    fun `just past the first turn, the next maneuver is the arrival`() {
        val state = Navigator.computeState(routePoints, maneuvers, position = 0.0 to 0.0015)

        assertEquals("Arrive at destination", state?.nextManeuverCue)
        assertEquals(55.6, state?.distanceToNextManeuverMeters ?: -1.0, 2.0)
        assertEquals(55.6, state?.distanceRemainingMeters ?: -1.0, 2.0)
    }

    @Test
    fun `at the very end, there is no next maneuver left`() {
        val state = Navigator.computeState(routePoints, maneuvers, position = 0.0 to 0.002)

        assertNull(state?.nextManeuverCue)
        assertNull(state?.distanceToNextManeuverMeters)
        assertEquals(0.0, state?.distanceRemainingMeters ?: -1.0, 1.0)
    }

    @Test
    fun `off to the side of the route, distanceOffRouteMeters reflects it`() {
        // ~20m north of the midpoint of the first segment.
        val state = Navigator.computeState(routePoints, maneuvers, position = 0.00018 to 0.0005)

        assertEquals(20.0, state?.distanceOffRouteMeters ?: -1.0, 2.0)
    }

    @Test
    fun `fewer than two route points yields no navigation state`() {
        assertNull(Navigator.computeState(listOf(0.0 to 0.0), maneuvers, 0.0 to 0.0))
    }
}
