package com.uprunner.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteGeometryTest {

    // Roughly west-east segments near the equator, ~111m per 0.001 degrees longitude.
    private val threeWaypointRoute = listOf(0.0 to 0.0, 0.0 to 0.001, 0.0 to 0.002)

    @Test
    fun `tap on the first segment inserts after the first waypoint`() {
        val index = RouteGeometry.nearestSegmentInsertionIndex(
            threeWaypointRoute,
            tapPoint = 0.0 to 0.0005,
            snapThresholdMeters = 30.0,
        )
        assertEquals(1, index)
    }

    @Test
    fun `tap on the second segment inserts after the second waypoint`() {
        val index = RouteGeometry.nearestSegmentInsertionIndex(
            threeWaypointRoute,
            tapPoint = 0.0 to 0.0015,
            snapThresholdMeters = 30.0,
        )
        assertEquals(2, index)
    }

    @Test
    fun `tap far from every segment returns null (append instead)`() {
        val index = RouteGeometry.nearestSegmentInsertionIndex(
            threeWaypointRoute,
            tapPoint = 5.0 to 5.0,
            snapThresholdMeters = 30.0,
        )
        assertNull(index)
    }

    @Test
    fun `tap just within the perpendicular threshold snaps to the segment`() {
        // ~20m north of the segment's midpoint.
        val index = RouteGeometry.nearestSegmentInsertionIndex(
            listOf(0.0 to 0.0, 0.0 to 0.001),
            tapPoint = 0.00018 to 0.0005,
            snapThresholdMeters = 30.0,
        )
        assertEquals(1, index)
    }

    @Test
    fun `tap just beyond the perpendicular threshold does not snap`() {
        // ~100m north of the segment's midpoint.
        val index = RouteGeometry.nearestSegmentInsertionIndex(
            listOf(0.0 to 0.0, 0.0 to 0.001),
            tapPoint = 0.0009 to 0.0005,
            snapThresholdMeters = 30.0,
        )
        assertNull(index)
    }

    @Test
    fun `fewer than two waypoints always returns null`() {
        assertNull(RouteGeometry.nearestSegmentInsertionIndex(listOf(0.0 to 0.0), 0.0 to 0.0, 30.0))
        assertNull(RouteGeometry.nearestSegmentInsertionIndex(emptyList(), 0.0 to 0.0, 30.0))
    }
}
