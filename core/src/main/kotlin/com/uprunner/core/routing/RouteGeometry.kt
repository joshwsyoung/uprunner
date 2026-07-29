package com.uprunner.core.routing

import com.uprunner.core.pace.GeoUtils
import kotlin.math.cos

/**
 * Decides whether a tapped point should extend a route being planned, or be inserted partway
 * through it — the "tap on the line to insert a waypoint, tap elsewhere to extend" editing
 * behavior (matching how Komoot handles route editing).
 */
object RouteGeometry {

    /**
     * Returns the index at which to insert [tapPoint] into [waypoints] if it lands within
     * [snapThresholdMeters] of an existing segment, or null if it should be appended to the end.
     */
    fun nearestSegmentInsertionIndex(
        waypoints: List<Pair<Double, Double>>,
        tapPoint: Pair<Double, Double>,
        snapThresholdMeters: Double,
    ): Int? {
        if (waypoints.size < 2) return null

        var bestIndex: Int? = null
        var bestDistanceMeters = Double.MAX_VALUE
        for (i in 0 until waypoints.size - 1) {
            val closest = closestPointOnSegment(waypoints[i], waypoints[i + 1], tapPoint)
            val distanceMeters = GeoUtils.haversineDistanceMeters(
                tapPoint.first, tapPoint.second, closest.first, closest.second,
            )
            if (distanceMeters < bestDistanceMeters) {
                bestDistanceMeters = distanceMeters
                bestIndex = i + 1
            }
        }
        return if (bestDistanceMeters <= snapThresholdMeters) bestIndex else null
    }

    /** How far along [routePoints] (from its start) the closest point to [position] sits, plus
     *  how far off the route [position] itself is — the basis for "distance remaining" and
     *  "distance to next turn" while following a planned route. */
    data class RouteProjection(val distanceAlongRouteMeters: Double, val distanceFromRouteMeters: Double)

    fun projectOntoRoute(routePoints: List<Pair<Double, Double>>, position: Pair<Double, Double>): RouteProjection? {
        if (routePoints.size < 2) return null

        var bestDistanceAlongMeters = 0.0
        var bestDistanceFromMeters = Double.MAX_VALUE
        var cumulativeMeters = 0.0

        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            val closest = closestPointOnSegment(a, b, position)
            val distanceFromMeters = GeoUtils.haversineDistanceMeters(position.first, position.second, closest.first, closest.second)
            val segmentLengthMeters = GeoUtils.haversineDistanceMeters(a.first, a.second, b.first, b.second)

            if (distanceFromMeters < bestDistanceFromMeters) {
                bestDistanceFromMeters = distanceFromMeters
                bestDistanceAlongMeters = cumulativeMeters + GeoUtils.haversineDistanceMeters(a.first, a.second, closest.first, closest.second)
            }
            cumulativeMeters += segmentLengthMeters
        }

        return RouteProjection(bestDistanceAlongMeters, bestDistanceFromMeters)
    }

    /** Cumulative walking distance from the start of [routePoints] up to (and including) the
     *  point at [upToIndex] — used to translate a maneuver's point index into "how far into
     *  the route" it is. */
    fun cumulativeDistanceMeters(routePoints: List<Pair<Double, Double>>, upToIndex: Int): Double {
        if (routePoints.size < 2) return 0.0
        var total = 0.0
        for (i in 1..upToIndex.coerceIn(0, routePoints.size - 1)) {
            val a = routePoints[i - 1]
            val b = routePoints[i]
            total += GeoUtils.haversineDistanceMeters(a.first, a.second, b.first, b.second)
        }
        return total
    }

    /** Nearest point to [p] on segment [a]-[b], via a local equirectangular approximation
     *  (accurate enough at the scale of a planned run route) then clamped to the segment. */
    private fun closestPointOnSegment(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        p: Pair<Double, Double>,
    ): Pair<Double, Double> {
        val cosLat = cos(Math.toRadians((a.first + b.first) / 2.0))
        val ax = a.second * cosLat
        val ay = a.first
        val bx = b.second * cosLat
        val by = b.first
        val px = p.second * cosLat
        val py = p.first

        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) {
            0.0
        } else {
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        }

        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return Pair(closestY, closestX / cosLat)
    }
}
