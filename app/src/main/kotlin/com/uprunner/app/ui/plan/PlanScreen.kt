package com.uprunner.app.ui.plan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uprunner.app.data.db.RouteEntity
import com.uprunner.core.routing.RouteStats
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val ROUTE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

@Composable
fun PlanScreen(viewModel: PlanViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var showRoutesDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }

    // The New Waypoint sheet needs to keep showing its last contents while it slides/fades
    // out — by the time pendingWaypoint flips to null the AnimatedVisibility exit transition
    // is still running, so this remembers the last non-null value for it to render against.
    var lastPendingWaypoint by remember { mutableStateOf<PendingWaypoint?>(null) }
    uiState.pendingWaypoint?.let { lastPendingWaypoint = it }
    var lastPendingWaypointCount by remember { mutableStateOf(0) }
    if (uiState.pendingWaypoint != null) lastPendingWaypointCount = uiState.waypoints.size

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(viewModel::loadGpxFromUri) }

    LaunchedEffect(uiState.routingError) {
        uiState.routingError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(uiState.offlineDownloadStatus) {
        uiState.offlineDownloadStatus?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(searchError) {
        searchError?.let {
            snackbarHostState.showSnackbar(it)
            searchError = null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            UprunnerMap(
                track = uiState.track,
                waypoints = uiState.waypoints,
                plannedRoutePoints = uiState.plannedRoutePoints,
                pendingPoint = uiState.pendingWaypoint?.point,
                modifier = Modifier.fillMaxSize(),
                onMapReady = { map = it },
                onMapLongPress = viewModel::handleMapLongPress,
            )

            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search a place or area") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            searchError = null
                            coroutineScope.launch {
                                GeocodingClient.searchFirstResult(searchQuery)
                                    .onSuccess { result ->
                                        map?.animateCamera(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.Builder()
                                                    .target(LatLng(result.latitude, result.longitude))
                                                    .zoom(13.0)
                                                    .build(),
                                            ),
                                        )
                                    }
                                    .onFailure { searchError = "Couldn't find that place — try a different search." }
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                )
            }

            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
                SmallFloatingActionButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }
                AnimatedVisibility(visible = uiState.mode == PlanMode.PLAN && uiState.waypoints.isNotEmpty()) {
                    SmallFloatingActionButton(onClick = viewModel::undoLastWaypoint) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo last waypoint")
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            ) {
                Crossfade(targetState = uiState.mode, label = "plan-mode-panel") { mode ->
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (mode == PlanMode.VIEW) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(onClick = viewModel::enterPlanMode) { Text("Plan New Route") }
                                Button(onClick = { showRoutesDialog = true }) { Text("Routes") }
                            }
                            Button(
                                onClick = {
                                    map?.let { m ->
                                        viewModel.downloadOfflineRegion(
                                            bounds = m.projection.visibleRegion.latLngBounds,
                                            minZoom = (m.cameraPosition.zoom - 1).coerceAtLeast(0.0),
                                            maxZoom = (m.cameraPosition.zoom + 3).coerceAtMost(20.0),
                                        )
                                    }
                                },
                                enabled = map != null,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text("Download Offline Region")
                            }
                        } else {
                            uiState.routeStats?.let { RouteStatsRow(it) }
                            Text(
                                "Long-press the map to add a waypoint",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = viewModel::cancelPlanning) { Text("Cancel") }
                                Button(onClick = viewModel::clearWaypoints, enabled = uiState.waypoints.isNotEmpty()) {
                                    Text("Clear")
                                }
                                Button(
                                    onClick = viewModel::savePlannedRoute,
                                    enabled = uiState.plannedRoutePoints.size >= 2 && !uiState.isRouting,
                                ) {
                                    Text("Save Route")
                                }
                            }
                            if (uiState.isRouting) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Finding route…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.pendingWaypoint != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                lastPendingWaypoint?.let { pending ->
                    NewWaypointSheet(
                        pending = pending,
                        waypointCount = lastPendingWaypointCount,
                        insertAsMiddle = uiState.insertAsMiddle,
                        followWays = uiState.followWays,
                        onInsertAsMiddleChange = viewModel::setInsertAsMiddle,
                        onFollowWaysChange = viewModel::setFollowWays,
                        onConfirm = viewModel::confirmPendingWaypoint,
                        onDismiss = viewModel::dismissPendingWaypoint,
                    )
                }
            }
        }
    }

    if (showRoutesDialog) {
        RoutesDialog(
            savedRoutes = savedRoutes,
            onLoadGpxFile = {
                showRoutesDialog = false
                gpxPickerLauncher.launch("*/*")
            },
            onLoadSavedRoute = { route ->
                showRoutesDialog = false
                viewModel.loadSavedRoute(route)
            },
            onDismiss = { showRoutesDialog = false },
        )
    }
}

@Composable
private fun NewWaypointSheet(
    pending: PendingWaypoint,
    waypointCount: Int,
    insertAsMiddle: Boolean,
    followWays: Boolean,
    onInsertAsMiddleChange: (Boolean) -> Unit,
    onFollowWaysChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isFirstPoint = waypointCount == 0
    val canInsertMiddle = pending.suggestedInsertionIndex != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("New Waypoint", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            }

            if (!isFirstPoint) {
                pending.distanceToNearestWaypointMeters?.let { distance ->
                    Text(
                        "${formatDistance(distance)} from your route",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (canInsertMiddle) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = !insertAsMiddle, onClick = { onInsertAsMiddleChange(false) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !insertAsMiddle, onClick = { onInsertAsMiddleChange(false) })
                        Text("Add to end of route")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = insertAsMiddle, onClick = { onInsertAsMiddleChange(true) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = insertAsMiddle, onClick = { onInsertAsMiddleChange(true) })
                        Text("Insert as waypoint along the route")
                    }
                }
            }

            if (!isFirstPoint) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = followWays, onCheckedChange = onFollowWaysChange)
                    Text("Follow roads & trails")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onConfirm) {
                    Text(
                        when {
                            isFirstPoint -> "Start Here"
                            canInsertMiddle && insertAsMiddle -> "Insert Waypoint"
                            else -> "Set as End Point"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteStatsRow(summary: RouteStats.Summary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(icon = Icons.Filled.Straighten, label = formatDistance(summary.distanceMeters))
        StatItem(icon = Icons.Filled.Timer, label = formatDuration(summary.estimatedTimeMillis))
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) {
    "%.2f km".format(meters / 1000.0)
} else {
    "${meters.roundToInt()} m"
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
private fun RoutesDialog(
    savedRoutes: List<RouteEntity>,
    onLoadGpxFile: () -> Unit,
    onLoadSavedRoute: (RouteEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Routes") },
        text = {
            Column {
                Button(onClick = onLoadGpxFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Load GPX from file")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                if (savedRoutes.isEmpty()) {
                    Text("No saved routes yet — plan or load one to see it here.")
                } else {
                    Text("Saved routes", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(savedRoutes) { route ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(route.name ?: "Unnamed route")
                                    Text(
                                        ROUTE_DATE_FORMAT.format(Instant.ofEpochMilli(route.createdAtMillis)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { onLoadSavedRoute(route) }, modifier = Modifier.width(80.dp)) {
                                    Text("Load")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
