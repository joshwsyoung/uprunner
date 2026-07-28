package com.uprunner.app.ui.plan

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Triggers a background download of vector map tiles for a bounding box (spec §3 Tab 2,
 * "Download Offline Elevation Region"). Terrain/DEM raster tiles are not part of this pass —
 * see UprunnerMap's doc comment — so today this caches the vector basemap only.
 *
 * Deliberately doesn't attach a live progress [OfflineRegion] observer yet: that callback
 * interface's exact shape isn't something I could verify without compiling against the real
 * Android SDK, so this sticks to the documented create-and-activate call, which is enough to
 * make the region actually download and be usable offline.
 */
object OfflineRegionDownloader {

    fun download(
        context: Context,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        regionName: String,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val definition = OfflineTilePyramidRegionDefinition(
            OPENFREEMAP_LIBERTY_STYLE_URL,
            bounds,
            minZoom,
            maxZoom,
            context.resources.displayMetrics.density,
        )
        val metadata = regionName.toByteArray()

        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    onStarted()
                }

                override fun onError(error: String) {
                    onError(error)
                }
            },
        )
    }
}
