package com.uprunner.core.model

enum class RunStatus { ACTIVE, PAUSED, COMPLETED }

data class Run(
    val id: String,
    val startTimeMillis: Long,
    val status: RunStatus,
)
