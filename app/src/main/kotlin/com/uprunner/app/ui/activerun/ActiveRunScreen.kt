package com.uprunner.app.ui.activerun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uprunner.app.service.SelectedRoute
import com.uprunner.app.ui.plan.UprunnerMap
import com.uprunner.core.model.PaceSnapshot
import com.uprunner.core.model.RunStatus
import com.uprunner.core.pace.RelativePace
import com.uprunner.core.routing.NavigationState
import com.uprunner.core.routing.Navigator
import com.uprunner.core.routing.RouteStats
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_MAP_FRACTION = 0.55f
private const val MIN_MAP_FRACTION = 0.2f
private const val MAX_MAP_FRACTION = 0.8f

/** Tab 1, Mode A (Free Run) per spec §3 — pace/distance/time only, no map, no clutter. When a
 *  route is selected via the Plan tab's "Run Route" button, [RouteRunSplitScreen] takes over
 *  instead, adding a live map so the runner can navigate as well as watch their stats. */
@Composable
fun ActiveRunScreen(viewModel: ActiveRunViewModel = viewModel()) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    if (!permissionsGranted) {
        PermissionGate(onGrantClick = { permissionLauncher.launch(requiredPermissions()) })
        return
    }

    val telemetry by viewModel.telemetry.collectAsState()
    val runStatus by viewModel.runStatus.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()

    val route = selectedRoute
    if (route == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MetricRow(label = "Pace", value = formatPace(telemetry.currentPaceSecPerKm))
            MetricRow(label = "Distance", value = formatDistance(telemetry.totalDistanceMeters))
            MetricRow(label = "Time", value = formatElapsed(telemetry.elapsedTimeMillis))

            RunControls(
                status = runStatus,
                onStart = viewModel::startRun,
                onPause = viewModel::pauseRun,
                onStop = viewModel::stopRun,
            )
        }
    } else {
        RouteRunSplitScreen(
            route = route,
            telemetry = telemetry,
            runStatus = runStatus,
            onStart = viewModel::startRun,
            onPause = viewModel::pauseRun,
            onStop = viewModel::stopRun,
        )
    }
}

/** A draggable top/bottom split between the live map and the run stats — the user decides how
 *  much of the screen goes to navigation versus glanceable numbers. */
@Composable
private fun RouteRunSplitScreen(
    route: SelectedRoute,
    telemetry: PaceSnapshot,
    runStatus: RunStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    var mapFraction by remember { mutableStateOf(DEFAULT_MAP_FRACTION) }
    var containerHeightPx by remember { mutableStateOf(0f) }
    val currentLocation = telemetry.latestLatitude?.let { lat ->
        telemetry.latestLongitude?.let { lon -> lat to lon }
    }
    val navigationState = currentLocation?.let { location ->
        Navigator.computeState(
            routePoints = route.track.points.map { it.latitude to it.longitude },
            maneuvers = route.maneuvers,
            position = location,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
    ) {
        Box(modifier = Modifier.weight(mapFraction).fillMaxWidth()) {
            UprunnerMap(
                track = route.track,
                currentLocation = currentLocation,
                modifier = Modifier.fillMaxSize(),
            )
            navigationState?.let { state ->
                NavigationCard(state, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (containerHeightPx > 0f) {
                            mapFraction = (mapFraction + dragAmount / containerHeightPx)
                                .coerceIn(MIN_MAP_FRACTION, MAX_MAP_FRACTION)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
            )
        }

        Box(modifier = Modifier.weight(1f - mapFraction).fillMaxWidth()) {
            CompactRunStats(
                telemetry = telemetry,
                runStatus = runStatus,
                onStart = onStart,
                onPause = onPause,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun CompactRunStats(
    telemetry: PaceSnapshot,
    runStatus: RunStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CompactMetric(label = "Pace", value = formatPace(telemetry.currentPaceSecPerKm))
            CompactMetric(label = "Distance", value = formatDistance(telemetry.totalDistanceMeters))
            CompactMetric(label = "Time", value = formatElapsed(telemetry.elapsedTimeMillis))
        }
        RelativePaceLabel(telemetry.currentPaceSecPerKm)
        CompactRunControls(status = runStatus, onStart = onStart, onPause = onPause, onStop = onStop)
    }
}

/** Compares live pace against [RouteStats.DEFAULT_PACE_SEC_PER_KM] — there's no per-route
 *  target-pace input in the UI yet (the Plan tab's per-km split editor from M2 has no screen
 *  wired up to it), so this is a flat default rather than a per-km-aware target for now. */
@Composable
private fun RelativePaceLabel(actualPaceSecPerKm: Double?) {
    if (actualPaceSecPerKm == null) return
    val diffSecPerKm = RelativePace.diffSecPerKm(actualPaceSecPerKm, RouteStats.DEFAULT_PACE_SEC_PER_KM)
    val (text, color) = when {
        diffSecPerKm > 2 -> "${diffSecPerKm}s/km behind target" to MaterialTheme.colorScheme.error
        diffSecPerKm < -2 -> "${-diffSecPerKm}s/km ahead of target" to AHEAD_OF_PACE_COLOR
        else -> "On target pace" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, fontSize = 14.sp, color = color)
}

@Composable
private fun NavigationCard(state: NavigationState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cue = state.nextManeuverCue
            if (cue == "Arrive at destination") {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).rotate(turnRotationDegrees(cue)),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                state.distanceToNextManeuverMeters?.let {
                    Text(formatCardDistance(it), style = MaterialTheme.typography.titleMedium)
                }
                Text(cue ?: "${formatCardDistance(state.distanceRemainingMeters)} to go", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Degrees to rotate the generic "up" navigation arrow so it visually points the right way —
 *  Valhalla's own instructions are already runner-relative (see [com.uprunner.core.routing.TurnCue]),
 *  so no compass/heading math is involved, just mapping a cue to an angle. */
private fun turnRotationDegrees(cue: String?): Float = when (cue) {
    "Turn left" -> -90f
    "Sharp left" -> -135f
    "Bear left" -> -45f
    "Turn right" -> 90f
    "Sharp right" -> 135f
    "Bear right" -> 45f
    "Make a U-turn" -> 180f
    else -> 0f
}

private fun formatCardDistance(meters: Double): String = if (meters >= 1000) {
    String.format(Locale.US, "%.1f km", meters / 1000.0)
} else {
    "${meters.roundToInt()} m"
}

private val AHEAD_OF_PACE_COLOR = Color(0xFF2E7D32)

@Composable
private fun CompactMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 13.sp)
        Text(text = value, fontSize = 28.sp)
    }
}

@Composable
private fun CompactRunControls(status: RunStatus, onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (status) {
            RunStatus.COMPLETED -> Button(onClick = onStart, modifier = Modifier.width(140.dp).height(52.dp)) {
                Text("Start", fontSize = 18.sp)
            }

            RunStatus.ACTIVE -> Button(onClick = onPause, modifier = Modifier.width(140.dp).height(52.dp)) {
                Text("Pause", fontSize = 18.sp)
            }

            RunStatus.PAUSED -> Button(onClick = onStart, modifier = Modifier.width(140.dp).height(52.dp)) {
                Text("Resume", fontSize = 18.sp)
            }
        }

        if (status != RunStatus.COMPLETED) {
            Button(onClick = onStop, modifier = Modifier.width(140.dp).height(52.dp)) {
                Text("Stop", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Column(
        modifier = Modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 20.sp)
        Text(text = value, fontSize = 64.sp)
    }
}

@Composable
private fun RunControls(status: RunStatus, onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier.padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            RunStatus.COMPLETED -> Button(
                onClick = onStart,
                modifier = Modifier.width(220.dp).height(80.dp),
            ) { Text("Start", fontSize = 28.sp) }

            RunStatus.ACTIVE -> Button(
                onClick = onPause,
                modifier = Modifier.width(220.dp).height(80.dp),
            ) { Text("Pause", fontSize = 28.sp) }

            RunStatus.PAUSED -> Button(
                onClick = onStart,
                modifier = Modifier.width(220.dp).height(80.dp),
            ) { Text("Resume", fontSize = 28.sp) }
        }

        if (status != RunStatus.COMPLETED) {
            Button(
                onClick = onStop,
                modifier = Modifier.padding(top = 16.dp).width(220.dp).height(80.dp),
            ) { Text("Stop", fontSize = 28.sp) }
        }
    }
}

@Composable
private fun PermissionGate(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Location access is required to track your run.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGrantClick, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant permissions")
        }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.RECORD_AUDIO) // voice trigger pipeline (spec §4)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun hasRequiredPermissions(context: android.content.Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun formatPace(paceSecPerKm: Double?): String {
    if (paceSecPerKm == null) return "--:--"
    val totalSeconds = paceSecPerKm.roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
}

private fun formatDistance(distanceMeters: Double): String =
    String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)

private fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
