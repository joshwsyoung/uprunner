package com.uprunner.app.ui.plan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uprunner.app.data.db.RouteEntity
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ROUTE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

@Composable
fun PlanScreen(viewModel: PlanViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var showRoutesDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(viewModel::loadGpxFromUri) }

    Box(modifier = Modifier.fillMaxSize()) {
        UprunnerMap(
            track = uiState.track,
            waypoints = uiState.waypoints,
            plannedRoutePoints = uiState.plannedRoutePoints,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map = it },
            onMapClick = viewModel::handleMapTap,
        )

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            searchError?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.padding(top = 4.dp)) {
                    Text(it, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Appetize's cloud emulator (and any mouse-only input) can't pinch-to-zoom, so these
        // are here for testability as much as for on-device convenience.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingActionButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
            FloatingActionButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                Text("−", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (uiState.mode == PlanMode.VIEW) {
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
                    Text(
                        "Tap the map to add a waypoint, or tap a segment to insert one along the route",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = viewModel::cancelPlanning) { Text("Cancel") }
                        Button(onClick = viewModel::clearWaypoints, enabled = uiState.waypoints.isNotEmpty()) {
                            Text("Clear Waypoints")
                        }
                        Button(
                            onClick = viewModel::savePlannedRoute,
                            enabled = uiState.plannedRoutePoints.size >= 2 && !uiState.isRouting,
                        ) {
                            Text("Save Route")
                        }
                    }
                    if (uiState.isRouting) {
                        Text("Routing…", modifier = Modifier.padding(top = 4.dp))
                    }
                    uiState.routingError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                uiState.offlineDownloadStatus?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
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
