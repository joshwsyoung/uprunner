package com.uprunner.app.ui.plan

import android.app.Application
import android.net.Uri
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
    val isRouting: Boolean = false,
    val routingError: String? = null,
)

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
                routingError = null,
                isRouting = false,
            )
        }
    }

    fun cancelPlanning() {
        routingJob?.cancel()
        _uiState.update {
            it.copy(mode = PlanMode.VIEW, waypoints = emptyList(), plannedRoutePoints = emptyList(), routingError = null, isRouting = false)
        }
    }

    /** Only acts while in [PlanMode.PLAN]; a tap in [PlanMode.VIEW] is just browsing the map. */
    fun handleMapTap(point: Pair<Double, Double>) {
        if (_uiState.value.mode != PlanMode.PLAN) return

        val currentWaypoints = _uiState.value.waypoints
        val insertionIndex = RouteGeometry.nearestSegmentInsertionIndex(currentWaypoints, point, WAYPOINT_SNAP_THRESHOLD_METERS)
        val waypoints = if (insertionIndex != null) {
            currentWaypoints.toMutableList().apply { add(insertionIndex, point) }
        } else {
            currentWaypoints + point
        }

        _uiState.update { it.copy(waypoints = waypoints, routingError = null) }
        if (waypoints.size >= 2) requestRoute(waypoints)
    }

    fun clearWaypoints() {
        routingJob?.cancel()
        _uiState.update { it.copy(waypoints = emptyList(), plannedRoutePoints = emptyList(), routingError = null, isRouting = false) }
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

    private fun friendlyRoutingErrorMessage(error: Throwable): String = when (error) {
        is IOException -> "Couldn't reach the routing service — check your connection and try again."
        else -> "Couldn't find a route between those points. Try placing a waypoint closer to a road or trail."
    }

    /** Persists the currently planned (routed) waypoint path through the same Route/Split
     *  tables a loaded GPX file uses, via [GpxWriter] so it round-trips identically. */
    fun savePlannedRoute() {
        val points = _uiState.value.plannedRoutePoints
        if (points.size < 2) return

        viewModelScope.launch(Dispatchers.IO) {
            val track = GpxTrack(
                name = "Planned route",
                points = points.map { (lat, lon) -> GpxPoint(lat, lon, elevationMeters = null, timeMillis = null) },
            )
            persistAndDisplayRoute(track)
        }
    }

    fun loadGpxFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val gpxText = try {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            if (gpxText == null) {
                _uiState.update { it.copy(errorMessage = "Couldn't read the selected file") }
                return@launch
            }

            val track = try {
                GpxParser.parse(gpxText)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't parse GPX: ${e.message}") }
                return@launch
            }

            persistAndDisplayRoute(track, existingGpxText = gpxText)
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
