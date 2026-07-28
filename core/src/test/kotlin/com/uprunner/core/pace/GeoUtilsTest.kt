package com.uprunner.core.pace

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun `same point has zero distance`() {
        val d = GeoUtils.haversineDistanceMeters(51.5074, -0.1278, 51.5074, -0.1278)
        assertEquals(0.0, d, 0.001)
    }

    @Test
    fun `known short distance is within tolerance`() {
        // Roughly 0.001 degrees latitude apart at the equator is ~111 meters.
        val d = GeoUtils.haversineDistanceMeters(0.0, 0.0, 0.001, 0.0)
        assertEquals(111.19, d, 1.0)
    }
}
