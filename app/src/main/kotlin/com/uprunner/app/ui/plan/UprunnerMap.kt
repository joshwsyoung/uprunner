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
import com.uprunner.core.model.GpxTrack
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Free, no-API-key vector basemap (spec's F-Droid/no-proprietary-service constraint rules out
 * Mapbox/Google styles). See https://openfreemap.org — self-hostable, unlimited, no signup.
 */
const val OPENFREEMAP_LIBERTY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val LOADED_TRACK_SOURCE_ID = "uprunner-loaded-track-source"
private const val LOADED_TRACK_LINE_LAYER_ID = "uprunner-loaded-track-line"
private const val WAYPOINT_PREVIEW_SOURCE_ID = "uprunner-waypoint-preview-source"
private const val WAYPOINT_PREVIEW_LINE_LAYER_ID = "uprunner-waypoint-preview-line"
private const val PLANNED_ROUTE_SOURCE_ID = "uprunner-planned-route-source"
private const val PLANNED_ROUTE_LINE_LAYER_ID = "uprunner-planned-route-line"
private const val WAYPOINTS_SOURCE_ID = "uprunner-waypoints-source"
private const val WAYPOINTS_CIRCLE_LAYER_ID = "uprunner-waypoints-circle"

private const val DEFAULT_LOCAL_ZOOM = 14.0
private const val NO_LOCATION_FALLBACK_ZOOM = 2.0

/**
 * Full-screen MapLibre map for the Plan tab (spec §3 Tab 2). Not yet doing 3D terrain/DEM
 * hillshading — that's a deliberately deferred fast-follow, not attempted in this pass.
 *
 * Several layers can be visible at once while planning: [track] (a loaded/saved GPX, blue), a
 * dashed straight-line preview connecting [waypoints] in order (so there's instant feedback
 * before routing resolves), the solid snapped [plannedRoutePoints] (orange, drawn once the
 * routing call succeeds), and circle markers for each waypoint.
 */
@Composable
fun UprunnerMap(
    track: GpxTrack?,
    waypoints: List<Pair<Double, Double>> = emptyList(),
    plannedRoutePoints: List<Pair<Double, Double>> = emptyList(),
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapClick: (Pair<Double, Double>) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

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

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                map.addOnMapClickListener { latLng ->
                    onMapClick(latLng.latitude to latLng.longitude)
                    true
                }
                map.setStyle(Style.Builder().fromUri(OPENFREEMAP_LIBERTY_STYLE_URL)) { style ->
                    renderLoadedTrack(style, track)
                    renderWaypointPreviewLine(style, waypoints)
                    renderPlannedRoute(style, plannedRoutePoints)
                    renderWaypoints(style, waypoints)
                    if (track != null) {
                        fitCameraToPoints(map, track.points.map { it.latitude to it.longitude })
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
            val map = maplibreMap
            val style = map?.style
            if (map != null && style != null) {
                renderLoadedTrack(style, track)
                renderWaypointPreviewLine(style, waypoints)
                renderPlannedRoute(style, plannedRoutePoints)
                renderWaypoints(style, waypoints)
                track?.let { fitCameraToPoints(map, it.points.map { p -> p.latitude to p.longitude }) }
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

private fun renderWaypoints(style: Style, waypoints: List<Pair<Double, Double>>) {
    style.getLayer(WAYPOINTS_CIRCLE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(WAYPOINTS_SOURCE_ID)?.let { style.removeSource(it) }
    if (waypoints.isEmpty()) return

    val features = waypoints.map { (lat, lon) -> Feature.fromGeometry(Point.fromLngLat(lon, lat)) }
    style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        CircleLayer(WAYPOINTS_CIRCLE_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#FF6D00"),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
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
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val lastKnown = if (hasLocationPermission) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.allProviders.firstNotNullOfOrNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
    } else {
        null
    }

    map.cameraPosition = if (lastKnown != null) {
        CameraPosition.Builder().target(LatLng(lastKnown.latitude, lastKnown.longitude)).zoom(DEFAULT_LOCAL_ZOOM).build()
    } else {
        CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(NO_LOCATION_FALLBACK_ZOOM).build()
    }
}
