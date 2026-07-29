package com.uprunner.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStatsTest {

    @Test
    fun `empty or single-point route has zero distance and time`() {
        assertEquals(0.0, RouteStats.summarize(emptyList()).distanceMeters, 0.001)
        assertEquals(0L, RouteStats.summarize(emptyList()).estimatedTimeMillis)
        assertEquals(0.0, RouteStats.summarize(listOf(0.0 to 0.0)).distanceMeters, 0.001)
    }

    @Test
    fun `estimates time from distance at the given pace`() {
        // ~111.19m per 0.001 degree latitude at the equator.
        val summary = RouteStats.summarize(listOf(0.0 to 0.0, 0.001 to 0.0), paceSecPerKm = 300.0)

        assertEquals(111.19, summary.distanceMeters, 1.0)
        val expectedMillis = (summary.distanceMeters / 1000.0 * 300.0 * 1000.0).toLong()
        assertEquals(expectedMillis, summary.estimatedTimeMillis)
    }

    @Test
    fun `defaults to a 6-00-per-km pace when none is given`() {
        val summary = RouteStats.summarize(listOf(0.0 to 0.0, 0.01 to 0.0))
        val expectedMillis = (summary.distanceMeters / 1000.0 * 360.0 * 1000.0).toLong()
        assertEquals(expectedMillis, summary.estimatedTimeMillis)
    }

    @Test
    fun `elevation gain sums only positive climbs`() {
        assertEquals(30.0, RouteStats.elevationGainMeters(listOf(100.0, 110.0, 105.0, 125.0))!!, 0.001)
    }

    @Test
    fun `elevation gain skips pairs with an unknown side`() {
        assertEquals(15.0, RouteStats.elevationGainMeters(listOf(100.0, null, 115.0, 110.0))!!, 0.001)
    }

    @Test
    fun `elevation gain is null with fewer than two known elevations`() {
        assertEquals(null, RouteStats.elevationGainMeters(emptyList()))
        assertEquals(null, RouteStats.elevationGainMeters(listOf(100.0)))
        assertEquals(null, RouteStats.elevationGainMeters(listOf(null, null)))
    }
}
