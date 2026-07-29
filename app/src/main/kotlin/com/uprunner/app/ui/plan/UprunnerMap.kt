package com.uprunner.app.ui.plan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.JsonObject
import com.uprunner.core.model.GpxTrack
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Free, no-API-key vector basemaps (spec's F-Droid/no-proprietary-service constraint rules out
 * Mapbox/Google styles). See https://openfreemap.org — self-hostable, unlimited, no signup.
 * Two styles are offered behind the map's "layers" toggle; both come from the same free source.
 */
const val OPENFREEMAP_LIBERTY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
const val OPENFREEMAP_BRIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"

private const val LOADED_TRACK_SOURCE_ID = "uprunner-loaded-track-source"
private const val LOADED_TRACK_LINE_LAYER_ID = "uprunner-loaded-track-line"
private const val WAYPOINT_PREVIEW_SOURCE_ID = "uprunner-waypoint-preview-source"
private const val WAYPOINT_PREVIEW_LINE_LAYER_ID = "uprunner-waypoint-preview-line"
private const val PLANNED_ROUTE_SOURCE_ID = "uprunner-planned-route-source"
private const val PLANNED_ROUTE_LINE_LAYER_ID = "uprunner-planned-route-line"
private const val WAYPOINTS_SOURCE_ID = "uprunner-waypoints-source"
private const val WAYPOINTS_CIRCLE_LAYER_ID = "uprunner-waypoints-circle"
private const val WAYPOINTS_LABEL_LAYER_ID = "uprunner-waypoints-label"
private const val PENDING_POINT_SOURCE_ID = "uprunner-pending-point-source"
private const val PENDING_POINT_CIRCLE_LAYER_ID = "uprunner-pending-point-circle"
private const val CURRENT_LOCATION_SOURCE_ID = "uprunner-current-location-source"
private const val CURRENT_LOCATION_CIRCLE_LAYER_ID = "uprunner-current-location-circle"

private const val DEFAULT_LOCAL_ZOOM = 14.0
private const val NO_LOCATION_FALLBACK_ZOOM = 2.0

/**
 * Full-screen MapLibre map for the Plan tab (spec §3 Tab 2) and the Active Run split-screen
 * view. Not yet doing 3D terrain/DEM hillshading — that's a deliberately deferred fast-follow,
 * not attempted in this pass.
 *
 * Several layers can be visible at once: [track] (a loaded/saved GPX, blue), a dashed
 * straight-line preview connecting [waypoints] in order (instant feedback before routing
 * resolves), the solid snapped [plannedRoutePoints] (orange, once routing succeeds),
 * lettered/numbered circle markers per waypoint (A, B, 2, 3… — Komoot's convention),
 * [pendingPoint] — a distinct red marker for a just-tapped point still awaiting the user's
 * choice in the "New Waypoint" sheet, and [currentLocation] — a blue dot the camera follows,
 * used by the Active Run screen while running a planned route.
 *
 * Placing a point is a quick tap via [onMapTap], staging it rather than committing it
 * immediately.
 */
@Composable
fun UprunnerMap(
    track: GpxTrack?,
    waypoints: List<Pair<Double, Double>> = emptyList(),
    plannedRoutePoints: List<Pair<Double, Double>> = emptyList(),
    pendingPoint: Pair<Double, Double>? = null,
    currentLocation: Pair<Double, Double>? = null,
    styleUrl: String = OPENFREEMAP_LIBERTY_STYLE_URL,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapTap: (Pair<Double, Double>) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var appliedStyleUrl by remember { mutableStateOf<String?>(null) }
    var lastCenteredLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun renderAllLayers(style: Style) {
        renderLoadedTrack(style, track)
        renderWaypointPreviewLine(style, waypoints)
        renderPlannedRoute(style, plannedRoutePoints)
        renderWaypoints(style, waypoints)
        renderPendingPoint(style, pendingPoint)
        renderCurrentLocation(style, currentLocation)
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                map.addOnMapClickListener { latLng ->
                    onMapTap(latLng.latitude to latLng.longitude)
                    true
                }
                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    appliedStyleUrl = styleUrl
                    renderAllLayers(style)
                    if (track != null) {
                        fitCameraToPoints(map, track.points.map { it.latitude to it.longitude })
                    } else if (currentLocation != null) {
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(currentLocation.first, currentLocation.second))
                            .zoom(DEFAULT_LOCAL_ZOOM)
                            .build()
                        lastCenteredLocation = currentLocation
                    } else {
                        centerOnLastKnownLocationOrFallback(context, map)
                    }
                }
                maplibreMap = map
                onMapReady(map)
            }
            mapView
        },
        update = {
            val map = maplibreMap ?: return@AndroidView
            if (appliedStyleUrl != styleUrl) {
                appliedStyleUrl = styleUrl
                map.setStyle(Style.Builder().fromUri(styleUrl)) { style -> renderAllLayers(style) }
                return@AndroidView
            }
            val style = map.style ?: return@AndroidView
            renderAllLayers(style)
            track?.let { fitCameraToPoints(map, it.points.map { p -> p.latitude to p.longitude }) }
            if (currentLocation != null && currentLocation != lastCenteredLocation) {
                lastCenteredLocation = currentLocation
                map.easeCamera(CameraUpdateFactory.newLatLng(LatLng(currentLocation.first, currentLocation.second)))
            }
        },
    )
}

private fun renderLoadedTrack(style: Style, track: GpxTrack?) {
    renderLine(
        style,
        LOADED_TRACK_SOURCE_ID,
        LOADED_TRACK_LINE_LAYER_ID,
        "#2962FF",
        track?.points?.map { it.latitude to it.longitude },
        dashed = false,
    )
}

private fun renderPlannedRoute(style: Style, points: List<Pair<Double, Double>>) {
    renderLine(style, PLANNED_ROUTE_SOURCE_ID, PLANNED_ROUTE_LINE_LAYER_ID, "#FF6D00", points, dashed = false)
}

/** Immediate straight-line feedback between raw waypoint taps, shown while the snapped route
 *  is still being fetched (or if it never resolves) — a visible "building" state rather than
 *  nothing happening between a tap and the routing call finishing. */
private fun renderWaypointPreviewLine(style: Style, waypoints: List<Pair<Double, Double>>) {
    renderLine(style, WAYPOINT_PREVIEW_SOURCE_ID, WAYPOINT_PREVIEW_LINE_LAYER_ID, "#FF6D00", waypoints, dashed = true)
}

private fun renderLine(style: Style, sourceId: String, layerId: String, colorHex: String, points: List<Pair<Double, Double>>?, dashed: Boolean) {
    style.getLayer(layerId)?.let { style.removeLayer(it) }
    style.getSource(sourceId)?.let { style.removeSource(it) }
    if (points == null || points.size < 2) return

    val lineString = LineString.fromLngLats(points.map { (lat, lon) -> Point.fromLngLat(lon, lat) })
    style.addSource(GeoJsonSource(sourceId, Feature.fromGeometry(lineString)))
    val layer = LineLayer(layerId, sourceId)
    if (dashed) {
        layer.setProperties(
            PropertyFactory.lineColor(colorHex),
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
        )
    } else {
        layer.setProperties(
            PropertyFactory.lineColor(colorHex),
            PropertyFactory.lineWidth(4f),
        )
    }
    style.addLayer(layer)
}

/** Komoot's convention: first waypoint is "A", last is "B", anything in between is numbered
 *  starting at 2 (the 2nd waypoint overall). */
private fun waypointLabel(index: Int, total: Int): String = when {
    index == 0 -> "A"
    index == total - 1 -> "B"
    else -> (index + 1).toString()
}

private fun renderWaypoints(style: Style, waypoints: List<Pair<Double, Double>>) {
    style.getLayer(WAYPOINTS_LABEL_LAYER_ID)?.let { style.removeLayer(it) }
    style.getLayer(WAYPOINTS_CIRCLE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(WAYPOINTS_SOURCE_ID)?.let { style.removeSource(it) }
    if (waypoints.isEmpty()) return

    val features = waypoints.mapIndexed { index, (lat, lon) ->
        val properties = JsonObject().apply { addProperty("label", waypointLabel(index, waypoints.size)) }
        Feature.fromGeometry(Point.fromLngLat(lon, lat), properties)
    }
    style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        CircleLayer(WAYPOINTS_CIRCLE_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#FF6D00"),
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
    style.addLayer(
        SymbolLayer(WAYPOINTS_LABEL_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )
}

/** A distinct marker for a just-tapped point still awaiting the user's choice in the "New
 *  Waypoint" sheet — visible confirmation that the tap registered, before anything commits. */
private fun renderPendingPoint(style: Style, point: Pair<Double, Double>?) {
    style.getLayer(PENDING_POINT_CIRCLE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(PENDING_POINT_SOURCE_ID)?.let { style.removeSource(it) }
    if (point == null) return

    val feature = Feature.fromGeometry(Point.fromLngLat(point.second, point.first))
    style.addSource(GeoJsonSource(PENDING_POINT_SOURCE_ID, feature))
    style.addLayer(
        CircleLayer(PENDING_POINT_CIRCLE_LAYER_ID, PENDING_POINT_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#E53935"),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

/** The runner's live position while following a route — a blue dot, the map-standard color for
 *  "you are here", distinct from every other marker's orange/red. */
private fun renderCurrentLocation(style: Style, point: Pair<Double, Double>?) {
    style.getLayer(CURRENT_LOCATION_CIRCLE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(CURRENT_LOCATION_SOURCE_ID)?.let { style.removeSource(it) }
    if (point == null) return

    val feature = Feature.fromGeometry(Point.fromLngLat(point.second, point.first))
    style.addSource(GeoJsonSource(CURRENT_LOCATION_SOURCE_ID, feature))
    style.addLayer(
        CircleLayer(CURRENT_LOCATION_CIRCLE_LAYER_ID, CURRENT_LOCATION_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#2979FF"),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

private fun fitCameraToPoints(map: MapLibreMap, points: List<Pair<Double, Double>>) {
    if (points.size < 2) return
    val boundsBuilder = LatLngBounds.Builder()
    points.forEach { (lat, lon) -> boundsBuilder.include(LatLng(lat, lon)) }
    map.easeCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 96))
}

/** Fixes the map defaulting to a zoom-0 world view when there's nothing loaded yet. */
private fun centerOnLastKnownLocationOrFallback(context: Context, map: MapLibreMap) {
    val lastKnown = lastKnownLocation(context)
    map.cameraPosition = if (lastKnown != null) {
        CameraPosition.Builder().target(LatLng(lastKnown.first, lastKnown.second)).zoom(DEFAULT_LOCAL_ZOOM).build()
    } else {
        CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(NO_LOCATION_FALLBACK_ZOOM).build()
    }
}

/** Reusable outside this file too (the Plan tab's "locate me" toolbar button) — last-known
 *  location via the plain [LocationManager], matching this app's no-Play-Services constraint. */
fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasLocationPermission) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = locationManager.allProviders.firstNotNullOfOrNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    } ?: return null
    return location.latitude to location.longitude
}
