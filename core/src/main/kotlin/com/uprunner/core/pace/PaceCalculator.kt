package com.uprunner.core.pace

import com.uprunner.core.model.LocationSample
import com.uprunner.core.model.PaceSnapshot

/**
 * Computes a rolling-average pace over a 50m distance window (per spec §7), plus
 * unwindowed total distance/elapsed time. One instance per active run; not thread-safe —
 * callers must serialize access (the Foreground Service calls this from a single thread).
 */
class PaceCalculator {

    private data class WindowPoint(val cumulativeDistanceMeters: Double, val timestampMillis: Long)

    private var lastSample: LocationSample? = null
    private var startTimeMillis: Long? = null
    private var totalDistanceMeters = 0.0
    private val window = ArrayDeque<WindowPoint>()

    fun addSample(sample: LocationSample): PaceSnapshot {
        val previous = lastSample
        if (previous == null) {
            lastSample = sample
            startTimeMillis = sample.timestampMillis
            window.addLast(WindowPoint(0.0, sample.timestampMillis))
            return snapshot(sample.timestampMillis)
        }

        if (sample.accuracyMeters != null && sample.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return snapshot(previous.timestampMillis)
        }

        val dtMillis = sample.timestampMillis - previous.timestampMillis
        if (dtMillis <= 0) {
            return snapshot(previous.timestampMillis)
        }

        val deltaMeters = GeoUtils.haversineDistanceMeters(
            previous.latitude,
            previous.longitude,
            sample.latitude,
            sample.longitude,
        )
        val impliedSpeedMps = deltaMeters / (dtMillis / 1000.0)
        if (impliedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS) {
            return snapshot(previous.timestampMillis)
        }

        totalDistanceMeters += deltaMeters
        lastSample = sample
        window.addLast(WindowPoint(totalDistanceMeters, sample.timestampMillis))
        while (window.size >= 2 && (totalDistanceMeters - window[1].cumulativeDistanceMeters) >= WINDOW_METERS) {
            window.removeFirst()
        }

        return snapshot(sample.timestampMillis)
    }

    private fun snapshot(nowMillis: Long): PaceSnapshot {
        val start = startTimeMillis ?: nowMillis
        val elapsedMillis = nowMillis - start
        val anchor = window.firstOrNull()
        val paceSecPerKm = anchor?.let {
            val windowDistanceMeters = totalDistanceMeters - it.cumulativeDistanceMeters
            val windowTimeMillis = nowMillis - it.timestampMillis
            if (windowDistanceMeters >= MIN_WINDOW_METERS_FOR_PACE && windowTimeMillis > 0) {
                (windowTimeMillis / 1000.0) / (windowDistanceMeters / 1000.0)
            } else {
                null
            }
        }
        return PaceSnapshot(paceSecPerKm, totalDistanceMeters, elapsedMillis)
    }

    companion object {
        private const val WINDOW_METERS = 50.0
        private const val MIN_WINDOW_METERS_FOR_PACE = 5.0
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 20f
        private const val MAX_PLAUSIBLE_SPEED_MPS = 10.0
    }
}
