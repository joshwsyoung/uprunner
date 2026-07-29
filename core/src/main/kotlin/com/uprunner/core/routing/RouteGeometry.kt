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
