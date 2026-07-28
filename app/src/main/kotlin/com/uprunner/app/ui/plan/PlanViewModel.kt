package com.uprunner.app.ui.plan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uprunner.app.data.db.AppDatabase
import com.uprunner.app.data.db.RouteEntity
import com.uprunner.app.data.db.SplitEntity
import com.uprunner.core.gpx.GpxParser
import com.uprunner.core.model.GpxTrack
import com.uprunner.core.pace.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import java.util.UUID
import kotlin.math.ceil

data class PlanUiState(
    val routeId: String? = null,
    val track: GpxTrack? = null,
    val totalDistanceKm: Int = 0,
    val splitTargetsText: Map<Int, String> = emptyMap(),
    val errorMessage: String? = null,
    val offlineDownloadStatus: String? = null,
)

class PlanViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState

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

            val routeId = UUID.randomUUID().toString()
            database.routeDao().insert(
                RouteEntity(
                    id = routeId,
                    name = track.name,
                    gpxRaw = gpxText,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )

            val totalDistanceKm = ceil(totalDistanceMeters(track) / 1000.0).toInt().coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    routeId = routeId,
                    track = track,
                    totalDistanceKm = totalDistanceKm,
                    splitTargetsText = (1..totalDistanceKm).associateWith { "" },
                    errorMessage = null,
                )
            }
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
