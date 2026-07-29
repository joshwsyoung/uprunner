package com.uprunner.core.routing

import com.uprunner.core.pace.GeoUtils

/**
 * Live distance/time-estimate stats for a route while it's being planned (Komoot's summary
 * card shows Time/Distance/Pace as soon as 2+ points exist). There's no user pace preference
 * yet, so [DEFAULT_PACE_SEC_PER_KM] is a placeholder estimate, not a personalized one.
 */
object RouteStats {

    const val DEFAULT_PACE_SEC_PER_KM = 360.0 // 6:00/km

    data class Summary(val distanceMeters: Double, val estimatedTimeMillis: Long)

    fun summarize(points: List<Pair<Double, Double>>, paceSecPerKm: Double = DEFAULT_PACE_SEC_PER_KM): Summary {
        val distanceMeters = totalDistanceMeters(points)
        val estimatedTimeMillis = ((distanceMeters / 1000.0) * paceSecPerKm * 1000.0).toLong()
        return Summary(distanceMeters, estimatedTimeMillis)
    }

    private fun totalDistanceMeters(points: List<Pair<Double, Double>>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            val (lat1, lon1) = points[i - 1]
            val (lat2, lon2) = points[i]
            total += GeoUtils.haversineDistanceMeters(lat1, lon1, lat2, lon2)
        }
        return total
    }
}
