package com.uprunner.app.ui.plan

import android.os.Bundle
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.uprunner.core.model.GpxTrack
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Free, no-API-key vector basemap (spec's F-Droid/no-proprietary-service constraint rules out
 * Mapbox/Google styles). See https://openfreemap.org — self-hostable, unlimited, no signup.
 */
const val OPENFREEMAP_LIBERTY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val ROUTE_SOURCE_ID = "uprunner-route-source"
private const val ROUTE_LINE_LAYER_ID = "uprunner-route-line"

/**
 * Full-screen MapLibre map for the Plan tab (spec §3 Tab 2). Not yet doing 3D terrain/DEM
 * hillshading — that's a deliberately deferred fast-follow, not attempted in this pass.
 */
@Composable
fun UprunnerMap(
    track: GpxTrack?,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
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
                map.setStyle(Style.Builder().fromUri(OPENFREEMAP_LIBERTY_STYLE_URL)) { style ->
                    drawTrack(style, track)
                    track?.let { fitCameraToTrack(map, it) }
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
                drawTrack(style, track)
                track?.let { fitCameraToTrack(map, it) }
            }
        },
    )
}

private fun drawTrack(style: Style, track: GpxTrack?) {
    style.getLayer(ROUTE_LINE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(ROUTE_SOURCE_ID)?.let { style.removeSource(it) }
    if (track == null || track.points.size < 2) return

    val points = track.points.map { Point.fromLngLat(it.longitude, it.latitude) }
    val feature = Feature.fromGeometry(LineString.fromLngLats(points))
    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, feature))
    style.addLayer(
        LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#2962FF"),
            PropertyFactory.lineWidth(4f),
        ),
    )
}

private fun fitCameraToTrack(map: MapLibreMap, track: GpxTrack) {
    if (track.points.size < 2) return
    val boundsBuilder = LatLngBounds.Builder()
    track.points.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
    map.easeCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 96))
}
