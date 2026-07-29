package com.uprunner.core.routing

/** Everything the Active Run screen's Navigation Card needs, recomputed on each GPS update. */
data class NavigationState(
    val distanceRemainingMeters: Double,
    val distanceOffRouteMeters: Double,
    val nextManeuverCue: String?,
    val distanceToNextManeuverMeters: Double?,
)

object Navigator {

    /** [maneuvers] must already be in stitched-route point-index space — see
     *  [ValhallaRouting.decodeFullRouteWithManeuvers]. Returns null if [routePoints] can't be
     *  projected onto (fewer than 2 points). */
    fun computeState(
        routePoints: List<Pair<Double, Double>>,
        maneuvers: List<ValhallaRouting.Maneuver>,
        position: Pair<Double, Double>,
    ): NavigationState? {
        val projection = RouteGeometry.projectOntoRoute(routePoints, position) ?: return null
        val totalDistanceMeters = RouteGeometry.cumulativeDistanceMeters(routePoints, routePoints.size - 1)
        val distanceRemainingMeters = (totalDistanceMeters - projection.distanceAlongRouteMeters).coerceAtLeast(0.0)

        // The maneuver the runner is currently on top of shouldn't still read as "next" — the
        // small epsilon avoids that flicker right as each one is reached.
        val next = maneuvers
            .map { it to RouteGeometry.cumulativeDistanceMeters(routePoints, it.beginPointIndex) }
            .firstOrNull { (_, cumulativeMeters) -> cumulativeMeters > projection.distanceAlongRouteMeters + PASSED_MANEUVER_EPSILON_METERS }

        return NavigationState(
            distanceRemainingMeters = distanceRemainingMeters,
            distanceOffRouteMeters = projection.distanceFromRouteMeters,
            nextManeuverCue = next?.first?.let { TurnCue.shortText(it.instruction) },
            distanceToNextManeuverMeters = next?.let { (_, cumulativeMeters) -> cumulativeMeters - projection.distanceAlongRouteMeters },
        )
    }

    private const val PASSED_MANEUVER_EPSILON_METERS = 1.0
}
