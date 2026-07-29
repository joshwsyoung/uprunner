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

    /** Sum of positive elevation deltas between consecutive *known* points — unknown entries are
     *  dropped first rather than skipped pairwise, so a single missing sample doesn't erase the
     *  climb across it. GPX files without a barometer/DEM source, and every route this app plans
     *  itself (Valhalla's shape has no elevation), leave every entry null, so callers should
     *  treat a null result as "unknown" rather than "no climb". */
    fun elevationGainMeters(elevations: List<Double?>): Double? {
        val known = elevations.filterNotNull()
        if (known.size < 2) return null
        var gain = 0.0
        for (i in 1 until known.size) {
            val diff = known[i] - known[i - 1]
            if (diff > 0) gain += diff
        }
        return gain
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
