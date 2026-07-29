package com.uprunner.app.service

import com.uprunner.core.model.GpxTrack
import com.uprunner.core.routing.RouteStats
import com.uprunner.core.routing.ValhallaRouting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [maneuvers] is empty for routes with no turn-by-turn data — either saved before M6, or
 *  planned with "Follow roads & trails" off (a straight-line path has no maneuvers). The
 *  Navigation Card just doesn't show a next-turn cue in that case. [targetPaceSecPerKm] is
 *  whatever the runner entered in the Plan tab's pace-input step before starting. */
data class SelectedRoute(
    val track: GpxTrack,
    val maneuvers: List<ValhallaRouting.Maneuver>,
    val targetPaceSecPerKm: Double = RouteStats.DEFAULT_PACE_SEC_PER_KM,
)

/**
 * Process-wide bridge between the Plan tab's "Run Route" button and the Active Run screen —
 * mirrors [RunTrackingRepository]'s started-service pattern for the same reason: both live in
 * the same process, so a plain singleton is simpler than passing the route through nav
 * arguments or re-fetching it by ID. Cleared when a run stops, so the next visit to Active Run
 * without picking a route again shows the plain free-run screen.
 */
object SelectedRouteRepository {

    private val _selectedRoute = MutableStateFlow<SelectedRoute?>(null)
    val selectedRoute: StateFlow<SelectedRoute?> = _selectedRoute

    fun select(
        track: GpxTrack,
        maneuvers: List<ValhallaRouting.Maneuver> = emptyList(),
        targetPaceSecPerKm: Double = RouteStats.DEFAULT_PACE_SEC_PER_KM,
    ) {
        _selectedRoute.value = SelectedRoute(track, maneuvers, targetPaceSecPerKm)
    }

    fun clear() {
        _selectedRoute.value = null
    }
}
