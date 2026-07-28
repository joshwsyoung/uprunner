package com.uprunner.core.model

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val timeMillis: Long?,
)

data class GpxTrack(
    val name: String?,
    val points: List<GpxPoint>,
)
