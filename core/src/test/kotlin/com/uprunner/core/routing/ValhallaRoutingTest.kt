package com.uprunner.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class ValhallaRoutingTest {

    @Test
    fun `builds a pedestrian route request for the given waypoints`() {
        val json = ValhallaRouting.buildRouteRequestJson(listOf(1.0 to 2.0, 3.0 to 4.0))
        assertEquals(
            "{\"locations\":[{\"lat\":1.0,\"lon\":2.0},{\"lat\":3.0,\"lon\":4.0}],\"costing\":\"pedestrian\"}",
            json,
        )
    }

    @Test
    fun `extracts a single leg shape`() {
        val response = """{"trip":{"legs":[{"shape":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}]}}"""
        assertEquals(listOf("_p~iF~ps|U_ulLnnqC_mqNvxq`@"), ValhallaRouting.extractLegShapes(response))
    }

    @Test
    fun `extracts multiple leg shapes in order`() {
        val response = """{"trip":{"legs":[{"shape":"_c`|@_gayB_gayB_gayB"},{"shape":"_kbvD_ocsF_gayB_gayB"}]}}"""
        assertEquals(
            listOf("_c`|@_gayB_gayB_gayB", "_kbvD_ocsF_gayB_gayB"),
            ValhallaRouting.extractLegShapes(response),
        )
    }

    @Test
    fun `stitches legs together dropping the shared boundary point`() {
        // leg1: (1,2)->(3,4); leg2: (3,4)->(5,6) — cross-checked against an independent
        // Python implementation of the same precision-6 polyline algorithm.
        val points = ValhallaRouting.decodeFullRoute(listOf("_c`|@_gayB_gayB_gayB", "_kbvD_ocsF_gayB_gayB"))

        assertEquals(3, points.size)
        assertPointEquals(1.0 to 2.0, points[0])
        assertPointEquals(3.0 to 4.0, points[1])
        assertPointEquals(5.0 to 6.0, points[2])
    }

    @Test
    fun `no legs decodes to no points`() {
        assertEquals(emptyList<Pair<Double, Double>>(), ValhallaRouting.decodeFullRoute(emptyList()))
    }

    @Test
    fun `extracts a single leg's maneuvers, unaffected by a nested street_names array`() {
        val response = """
            {"trip":{"legs":[{"maneuvers":[
                {"type":1,"instruction":"Walk east on Main Street.","street_names":["Main Street"],"begin_shape_index":0},
                {"type":9,"instruction":"Turn right onto Oak Avenue.","begin_shape_index":1}
            ],"shape":"_c`|@_gayB_gayB_gayB"}]}}
        """.trimIndent()

        val routed = ValhallaRouting.decodeFullRouteWithManeuvers(response)

        assertEquals(2, routed.points.size)
        assertEquals(
            listOf(
                ValhallaRouting.Maneuver("Walk east on Main Street.", 0),
                ValhallaRouting.Maneuver("Turn right onto Oak Avenue.", 1),
            ),
            routed.maneuvers,
        )
    }

    @Test
    fun `translates begin_shape_index across legs into global stitched-point indices`() {
        val response = """
            {"trip":{"legs":[
                {"maneuvers":[
                    {"type":1,"instruction":"Walk east on Main Street.","begin_shape_index":0},
                    {"type":9,"instruction":"Turn right onto Oak Avenue.","begin_shape_index":1}
                ],"shape":"_c`|@_gayB_gayB_gayB"},
                {"maneuvers":[
                    {"type":4,"instruction":"You have arrived at your destination.","begin_shape_index":1}
                ],"shape":"_kbvD_ocsF_gayB_gayB"}
            ]}}
        """.trimIndent()

        val routed = ValhallaRouting.decodeFullRouteWithManeuvers(response)

        assertEquals(3, routed.points.size)
        assertEquals(
            listOf(
                ValhallaRouting.Maneuver("Walk east on Main Street.", 0),
                ValhallaRouting.Maneuver("Turn right onto Oak Avenue.", 1),
                ValhallaRouting.Maneuver("You have arrived at your destination.", 2),
            ),
            routed.maneuvers,
        )
    }

    private fun assertPointEquals(expected: Pair<Double, Double>, actual: Pair<Double, Double>) {
        assertEquals(expected.first, actual.first, 0.00001)
        assertEquals(expected.second, actual.second, 0.00001)
    }
}
