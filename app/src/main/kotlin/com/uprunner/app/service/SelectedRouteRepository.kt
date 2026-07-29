package com.uprunner.app.service

import com.uprunner.core.model.GpxTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bridge between the Plan tab's "Run Route" button and the Active Run screen —
 * mirrors [RunTrackingRepository]'s started-service pattern for the same reason: both live in
 * the same process, so a plain singleton is simpler than passing the route through nav
 * arguments or re-fetching it by ID. Cleared when a run stops, so the next visit to Active Run
 * without picking a route again shows the plain free-run screen.
 */
object SelectedRouteRepository {

    private val _selectedRoute = MutableStateFlow<GpxTrack?>(null)
    val selectedRoute: StateFlow<GpxTrack?> = _selectedRoute

    fun select(track: GpxTrack) {
        _selectedRoute.value = track
    }

    fun clear() {
        _selectedRoute.value = null
    }
}
