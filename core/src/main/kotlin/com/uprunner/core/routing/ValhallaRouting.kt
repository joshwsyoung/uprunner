package com.uprunner.core.routing

import com.uprunner.core.gpx.PolylineCodec

/**
 * Request/response helpers for the FOSSGIS-hosted public Valhalla routing API
 * (https://valhalla1.openstreetmap.de/route) — a free, no-API-key, pedestrian-capable
 * routing engine over OpenStreetMap data, fitting the same "no proprietary key" constraint
 * as the OpenFreeMap tile source. The actual HTTP call lives in :app (this stays pure/testable).
 *
 * Response parsing is a small purpose-built regex extraction of the `shape` fields, not a
 * general JSON parser: adding a JSON library to :core risks a duplicate-class conflict with
 * Android's platform-bundled org.json once :app pulls it in transitively, and Valhalla's
 * response shape here is small, fixed, and well documented.
 */
object ValhallaRouting {

    const val PEDESTRIAN_COSTING = "pedestrian"
    const val SHAPE_PRECISION = 6

    fun buildRouteRequestJson(waypoints: List<Pair<Double, Double>>): String {
        val locations = waypoints.joinToString(",") { (lat, lon) -> "{\"lat\":$lat,\"lon\":$lon}" }
        return "{\"locations\":[$locations],\"costing\":\"$PEDESTRIAN_COSTING\"}"
    }

    private val SHAPE_FIELD_REGEX = Regex("\"shape\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Extracts each leg's encoded shape, in the order legs appear in the response. */
    fun extractLegShapes(responseJson: String): List<String> =
        SHAPE_FIELD_REGEX.findAll(responseJson).map { it.groupValues[1] }.toList()

    /**
     * Decodes and stitches all legs into one ordered point list. Consecutive legs repeat
     * their shared boundary point, so every leg after the first has its leading point dropped.
     */
    fun decodeFullRoute(legShapes: List<String>): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        legShapes.forEachIndexed { index, shape ->
            val legPoints = PolylineCodec.decode(shape, SHAPE_PRECISION)
            points += if (index == 0) legPoints else legPoints.drop(1)
        }
        return points
    }
}
