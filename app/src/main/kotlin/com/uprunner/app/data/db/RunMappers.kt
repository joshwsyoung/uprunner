package com.uprunner.app.data.db

import com.uprunner.core.model.Run
import com.uprunner.core.model.RunStatus

fun Run.toEntity(endTimeMillis: Long?, totalDistanceMeters: Double) = RunEntity(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    totalDistanceMeters = totalDistanceMeters,
    status = status.name,
)

fun RunEntity.toDomain() = Run(
    id = id,
    startTimeMillis = startTimeMillis,
    status = RunStatus.valueOf(status),
)
