package com.uprunner.app.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uprunner.app.data.db.AppDatabase
import com.uprunner.app.data.db.RouteEntity
import com.uprunner.app.data.db.SplitEntity
import com.uprunner.core.gpx.GpxParser
import com.uprunner.core.gpx.GpxWriter
import com.uprunner.core.model.GpxPoint
import com.uprunner.core.model.GpxTrack
import com.uprunner.core.pace.GeoUtils
import com.uprunner.core.routing.RouteGeometry
import com.uprunner.core.routing.RouteStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import java.io.IOException
import java.util.UUID
import kotlin.math.ceil

enum class PlanMode { VIEW, PLAN }

private const val WAYPOINT_SNAP_THRESHOLD_METERS = 30.0

/** A long-pressed point awaiting the user's choice in the "New Waypoint" sheet (Komoot's
 *  flow) — [suggestedInsertionIndex] is pre-computed so the sheet can default the "insert here
 *  vs. append to end" radio choice to whatever's geometrically sensible. */
data class PendingWaypoint(
    val point: Pair<Double, Double>,
    val distanceToNearestWaypointMeters: Double?,
    val suggestedInsertionIndex: Int?,
)

data class PlanUiState(
    val mode: PlanMode = PlanMode.VIEW,
    val routeId: String? = null,
    val track: GpxTrack? = null,
    val totalDistanceKm: Int = 0,
    val splitTargetsText: Map<Int, String> = emptyMap(),
    val errorMessage: String? = null,
    val offlineDownloadStatus: String? = null,
    val waypoints: List<Pair<Double, Double>> = emptyList(),
    val plannedRoutePoints: List<Pair<Double, Double>> = emptyList(),
    val followWays: Boolean = true,
    val pendingWaypoint: PendingWaypoint? = null,
    val insertAsMiddle: Boolean = false,
    val isRouting: Boolean = false,
    val routingError: String? = null,
) {
    val routeStats: RouteStats.Summary?
        get() {
            val points = if (followWays) plannedRoutePoints else waypoints
            return if (points.size < 2) null else RouteStats.summarize(points)
        }

    /** Distance/time for a loaded (not currently-being-planned) route, shown in the route
     *  detail card once a saved route or a run's route is loaded onto the map. */
    val loadedRouteStats: RouteStats.Summary?
        get() = track?.points?.takeIf { it.size >= 2 }?.let { points ->
            RouteStats.summarize(points.map { it.latitude to it.longitude })
        }

    val loadedRouteElevationGainMeters: Double?
        get() = track?.points?.let { points -> RouteStats.elevationGainMeters(points.map { it.elevationMeters }) }
}

class PlanViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState

    val savedRoutes: StateFlow<List<RouteEntity>> = database.routeDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var routingJob: Job? = null

    fun enterPlanMode() {
        routingJob?.cancel()
        _uiState.update {
            it.copy(
                mode = PlanMode.PLAN,
                track = null,
                routeId = null,
                waypoints = emptyList(),
                plannedRoutePoints = emptyList(),
                pendingWaypoint = null,
                routingError = null,
                isRouting = false,
            )
        }
    }

    fun cancelPlanning() {
        routingJob?.cancel()
        _uiState.update {
            it.copy(
                mode = PlanMode.VIEW,
                waypoints = emptyList(),
                plannedRoutePoints = emptyList(),
                pendingWaypoint = null,
                routingError = null,
                isRouting = false,
            )
        }
    }

    /** Long-press stages a point for confirmation (Komoot's "New Waypoint" sheet) rather than
     *  committing it immediately — a tap (or a long-press outside [PlanMode.PLAN]) does nothing. */
    fun handleMapLongPress(point: Pair<Double, Double>) {
        if (_uiState.value.mode != PlanMode.PLAN) return

        val waypoints = _uiState.value.waypoints
        val suggestedIndex = if (waypoints.size >= 2) {
            RouteGeometry.nearestSegmentInsertionIndex(waypoints, point, WAYPOINT_SNAP_THRESHOLD_METERS)
        } else {
            null
        }
        val distanceToNearest = waypoints.minOfOrNull {
            GeoUtils.haversineDistanceMeters(it.first, it.second, point.first, point.second)
        }

        _uiState.update {
            it.copy(
                pendingWaypoint = PendingWaypoint(point, distanceToNearest, suggestedIndex),
                insertAsMiddle = suggestedIndex != null,
            )
        }
    }

    fun setInsertAsMiddle(value: Boolean) {
        _uiState.update { it.copy(insertAsMiddle = value) }
    }

    fun dismissPendingWaypoint() {
        _uiState.update { it.copy(pendingWaypoint = null) }
    }

    /** Commits the staged [PendingWaypoint] — the same action backs "Start Here", "Set as End
     *  Point", and "Add to Route", since which one is shown depends only on how many waypoints
     *  already exist (see PlanScreen). */
    fun confirmPendingWaypoint() {
        val pending = _uiState.value.pendingWaypoint ?: return
        val current = _uiState.value.waypoints
        val insertionIndex = pending.suggestedInsertionIndex

        val updated = if (current.size >= 2 && _uiState.value.insertAsMiddle && insertionIndex != null) {
            current.toMutableList().apply { add(insertionIndex, pending.point) }
        } else {
            current + pending.point
        }

        _uiState.update { it.copy(waypoints = updated, pendingWaypoint = null, routingError = null) }
        updateRouteForWaypoints(updated)
    }

    fun undoLastWaypoint() {
        val updated = _uiState.value.waypoints.dropLast(1)
        _uiState.update { it.copy(waypoints = updated) }
        updateRouteForWaypoints(updated)
    }

    fun setFollowWays(value: Boolean) {
        _uiState.update { it.copy(followWays = value) }
        updateRouteForWaypoints(_uiState.value.waypoints)
    }

    fun clearWaypoints() {
        routingJob?.cancel()
        _uiState.update {
            it.copy(waypoints = emptyList(), plannedRoutePoints = emptyList(), pendingWaypoint = null, routingError = null, isRouting = false)
        }
    }

    private fun updateRouteForWaypoints(waypoints: List<Pair<Double, Double>>) {
        if (waypoints.size < 2) {
            routingJob?.cancel()
            _uiState.update { it.copy(plannedRoutePoints = emptyList(), isRouting = false) }
            return
        }
        if (_uiState.value.followWays) requestRoute(waypoints) else useStraightLineRoute(waypoints)
    }

    private fun useStraightLineRoute(waypoints: List<Pair<Double, Double>>) {
        routingJob?.cancel()
        _uiState.update { it.copy(plannedRoutePoints = waypoints, isRouting = false, routingError = null) }
    }

    private fun requestRoute(waypoints: List<Pair<Double, Double>>) {
        routingJob?.cancel()
        routingJob = viewModelScope.launch {
            _uiState.update { it.copy(isRouting = true) }
            val result = RoutingClient.routePedestrian(waypoints)
            result.onSuccess { points ->
                _uiState.update { it.copy(plannedRoutePoints = points, isRouting = false, routingError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(isRouting = false, routingError = friendlyRoutingErrorMessage(error)) }
            }
        }
    }

    private fun friendlyRoutingErrorMessage(error: Throwable): String = when {
        error is IOException -> "Couldn't reach the routing service — check your connection and try again."
        error is RoutingClient.RoutingHttpException && error.statusCode == 400 ->
            "Couldn't find a route between those points. Try placing a waypoint closer to a road or trail."
        error is RoutingClient.RoutingHttpException && error.statusCode == 429 ->
            "Routing service is busy right now — wait a moment and try again."
        error is RoutingClient.RoutingHttpException ->
            "Routing service error (${error.statusCode}). Please try again in a moment."
        else -> "Couldn't find a route between those points. Try placing a waypoint closer to a road or trail."
    }

    /** Persists the currently planned route through the same Route/Split tables a loaded GPX
     *  file uses, via [GpxWriter] so it round-trips identically. */
    fun savePlannedRoute() {
        val state = _uiState.value
        val points = if (state.followWays) state.plannedRoutePoints else state.waypoints
        if (points.size < 2) return

        viewModelScope.launch(Dispatchers.IO) {
            val track = GpxTrack(
                name = "Planned route",
                points = points.map { (lat, lon) -> GpxPoint(lat, lon, elevationMeters = null, timeMillis = null) },
            )
            persistAndDisplayRoute(track)
        }
    }

    /** Loads a previously saved route back onto the map (the Plan tab's "load it later" library). */
    fun loadSavedRoute(route: RouteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = try {
                GpxParser.parse(route.gpxRaw)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't load saved route: ${e.message}") }
                return@launch
            }
            val totalDistanceKm = ceil(totalDistanceMeters(track) / 1000.0).toInt().coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    mode = PlanMode.VIEW,
                    routeId = route.id,
                    track = track,
                    totalDistanceKm = totalDistanceKm,
                    splitTargetsText = (1..totalDistanceKm).associateWith { "" },
                    waypoints = emptyList(),
                    plannedRoutePoints = emptyList(),
                    errorMessage = null,
                )
            }
        }
    }

    private suspend fun persistAndDisplayRoute(track: GpxTrack, existingGpxText: String? = null) {
        val gpxText = existingGpxText ?: GpxWriter.write(track)
        val routeId = UUID.randomUUID().toString()
        database.routeDao().insert(
            RouteEntity(id = routeId, name = track.name, gpxRaw = gpxText, createdAtMillis = System.currentTimeMillis()),
        )

        val totalDistanceKm = ceil(totalDistanceMeters(track) / 1000.0).toInt().coerceAtLeast(1)
        _uiState.update {
            it.copy(
                mode = PlanMode.VIEW,
                routeId = routeId,
                track = track,
                totalDistanceKm = totalDistanceKm,
                splitTargetsText = (1..totalDistanceKm).associateWith { "" },
                waypoints = emptyList(),
                plannedRoutePoints = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun updateSplitTarget(kmMarker: Int, text: String) {
        _uiState.update { it.copy(splitTargetsText = it.splitTargetsText + (kmMarker to text)) }
    }

    fun saveSplits() {
        val state = _uiState.value
        val routeId = state.routeId ?: return
        val splits = state.splitTargetsText.mapNotNull { (kmMarker, text) ->
            parseMinutesSecondsToMillis(text)?.let { millis -> SplitEntity(routeId = routeId, kmMarker = kmMarker, targetTimeMillis = millis) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            database.splitDao().deleteForRoute(routeId)
            if (splits.isNotEmpty()) database.splitDao().upsertAll(splits)
        }
    }

    fun downloadOfflineRegion(bounds: LatLngBounds, minZoom: Double, maxZoom: Double) {
        val name = _uiState.value.track?.name ?: "uprunner-region"
        OfflineRegionDownloader.download(
            context = getApplication(),
            bounds = bounds,
            minZoom = minZoom,
            maxZoom = maxZoom,
            regionName = name,
            onStarted = { _uiState.update { it.copy(offlineDownloadStatus = "Downloading offline map…") } },
            onError = { message -> _uiState.update { it.copy(offlineDownloadStatus = "Download failed: $message") } },
        )
    }

    private fun totalDistanceMeters(track: GpxTrack): Double {
        var total = 0.0
        for (i in 1 until track.points.size) {
            val a = track.points[i - 1]
            val b = track.points[i]
            total += GeoUtils.haversineDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    private fun parseMinutesSecondsToMillis(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toLongOrNull() ?: return null
        if (seconds !in 0..59) return null
        return (minutes * 60 + seconds) * 1000
    }
}
