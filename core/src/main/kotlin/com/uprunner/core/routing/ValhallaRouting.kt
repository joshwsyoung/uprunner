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

    /** One turn-by-turn maneuver, in Valhalla's own English (e.g. "Turn left onto Main
     *  Street."), plus where it starts along the *stitched* (multi-leg) route — see
     *  [decodeFullRouteWithManeuvers]. [TurnCue] turns the instruction into a short display
     *  cue; this class just carries Valhalla's data through unmodified. */
    data class Maneuver(val instruction: String, val beginPointIndex: Int)

    data class RoutedPath(val points: List<Pair<Double, Double>>, val maneuvers: List<Maneuver>)

    fun buildRouteRequestJson(waypoints: List<Pair<Double, Double>>): String {
        val locations = waypoints.joinToString(",") { (lat, lon) -> "{\"lat\":$lat,\"lon\":$lon}" }
        return "{\"locations\":[$locations],\"costing\":\"$PEDESTRIAN_COSTING\"}"
    }

    private val SHAPE_FIELD_REGEX = Regex("\"shape\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val MANEUVERS_KEY_REGEX = Regex("\"maneuvers\"\\s*:\\s*")
    private val INSTRUCTION_FIELD_REGEX = Regex("\"instruction\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val BEGIN_SHAPE_INDEX_FIELD_REGEX = Regex("\"begin_shape_index\"\\s*:\\s*(\\d+)")

    /** Extracts each leg's encoded shape, in the order legs appear in the response. */
    fun extractLegShapes(responseJson: String): List<String> =
        SHAPE_FIELD_REGEX.findAll(responseJson).map { it.groupValues[1] }.toList()

    /**
     * Decodes and stitches all legs into one ordered point list. Consecutive legs repeat
     * their shared boundary point, so every leg after the first has its leading point dropped.
     */
    fun decodeFullRoute(legShapes: List<String>): List<Pair<Double, Double>> = stitchLegs(legShapes).points

    /**
     * Same stitching as [decodeFullRoute], plus each leg's maneuvers with their
     * `begin_shape_index` translated from "index within that leg's own shape" to "index in the
     * final stitched point list" — the offset differs per leg because of the dropped shared
     * boundary point, so this can't just reuse [decodeFullRoute]'s output as-is.
     */
    fun decodeFullRouteWithManeuvers(responseJson: String): RoutedPath {
        val legShapes = extractLegShapes(responseJson)
        val stitched = stitchLegs(legShapes)
        val legManeuvers = extractLegManeuvers(responseJson)

        val maneuvers = mutableListOf<Maneuver>()
        legManeuvers.forEachIndexed { index, maneuversInLeg ->
            val offset = stitched.legOffsets.getOrNull(index) ?: return@forEachIndexed
            maneuversInLeg.forEach { (instruction, localIndex) ->
                maneuvers.add(Maneuver(instruction, offset + localIndex))
            }
        }
        return RoutedPath(stitched.points, maneuvers)
    }

    private data class StitchedLegs(val points: List<Pair<Double, Double>>, val legOffsets: List<Int>)

    private fun stitchLegs(legShapes: List<String>): StitchedLegs {
        val points = mutableListOf<Pair<Double, Double>>()
        val legOffsets = mutableListOf<Int>()
        var stitchedCount = 0
        legShapes.forEachIndexed { index, shape ->
            val legPoints = PolylineCodec.decode(shape, SHAPE_PRECISION)
            // Leg 0's local index 0 lands at global index 0; every later leg's local index 0
            // is the same point as the previous leg's last point, already at stitchedCount - 1.
            legOffsets.add(if (index == 0) 0 else stitchedCount - 1)
            points += if (index == 0) legPoints else legPoints.drop(1)
            stitchedCount += if (index == 0) legPoints.size else legPoints.size - 1
        }
        return StitchedLegs(points, legOffsets)
    }

    /** Per leg, the (instruction, begin_shape_index) of each maneuver in that leg's own local
     *  shape-index space — see [decodeFullRouteWithManeuvers] for the global-index translation.
     *  Extracted with a balanced-bracket scan (not a naive regex over the whole array) because
     *  a maneuver can itself contain a nested array (`street_names`) that would otherwise
     *  terminate a lazy match early. */
    private fun extractLegManeuvers(responseJson: String): List<List<Pair<String, Int>>> =
        MANEUVERS_KEY_REGEX.findAll(responseJson).map { match ->
            val arrayText = extractBalancedArray(responseJson, match.range.last + 1) ?: return@map emptyList()
            val instructions = INSTRUCTION_FIELD_REGEX.findAll(arrayText).map { it.groupValues[1] }.toList()
            val beginIndices = BEGIN_SHAPE_INDEX_FIELD_REGEX.findAll(arrayText).map { it.groupValues[1].toInt() }.toList()
            instructions.zip(beginIndices)
        }.toList()

    /** Returns the substring from [startIndex] (which must point at a '[') to its matching ']',
     *  tracking bracket depth and skipping over string-literal content so brackets inside a
     *  string value (which can't happen here, but field values elsewhere might) or a nested
     *  array don't miscount. */
    private fun extractBalancedArray(text: String, startIndex: Int): String? {
        if (startIndex >= text.length || text[startIndex] != '[') return null
        var depth = 0
        var inString = false
        var i = startIndex
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return text.substring(startIndex, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }
}
