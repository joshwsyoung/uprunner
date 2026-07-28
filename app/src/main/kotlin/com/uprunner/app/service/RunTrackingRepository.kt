package com.uprunner.app.service

import com.uprunner.core.model.PaceSnapshot
import com.uprunner.core.model.RunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bridge between [RunTrackingService] and the UI layer. A started (not bound)
 * service and this singleton avoid `ServiceConnection`/binder lifecycle handling entirely,
 * since the service and its observers always share the same process. The same singleton is
 * reused by the future headphone-tap/voice-query handler (M3) to answer telemetry questions
 * instantly without its own connection to the service.
 */
object RunTrackingRepository {

    private val _telemetry = MutableStateFlow(
        PaceSnapshot(currentPaceSecPerKm = null, totalDistanceMeters = 0.0, elapsedTimeMillis = 0L),
    )
    val telemetry: StateFlow<PaceSnapshot> = _telemetry

    private val _runStatus = MutableStateFlow(RunStatus.COMPLETED)
    val runStatus: StateFlow<RunStatus> = _runStatus

    fun publishTelemetry(snapshot: PaceSnapshot) {
        _telemetry.value = snapshot
    }

    fun publishStatus(status: RunStatus) {
        _runStatus.value = status
    }

    fun reset() {
        _telemetry.value = PaceSnapshot(null, 0.0, 0L)
        _runStatus.value = RunStatus.COMPLETED
    }
}
