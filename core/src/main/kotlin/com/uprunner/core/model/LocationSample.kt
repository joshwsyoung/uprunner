package com.uprunner.core.model

/**
 * A single raw GPS fix, decoupled from android.location.Location so :core has no
 * Android framework dependency.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float?,
)
