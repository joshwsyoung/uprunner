package com.uprunner.core.model

/**
 * Current telemetry for the Active Run "Free Run" (Mode A) screen.
 * [currentPaceSecPerKm] is null until enough distance has accrued in the rolling window
 * to produce a stable reading (UI shows "--:--" during that gap).
 */
data class PaceSnapshot(
    val currentPaceSecPerKm: Double?,
    val totalDistanceMeters: Double,
    val elapsedTimeMillis: Long,
)
