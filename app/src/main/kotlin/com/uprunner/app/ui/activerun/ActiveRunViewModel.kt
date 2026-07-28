package com.uprunner.app.ui.activerun

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uprunner.app.service.RunTrackingRepository
import com.uprunner.app.service.RunTrackingService
import com.uprunner.core.model.PaceSnapshot
import com.uprunner.core.model.RunStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ActiveRunViewModel(application: Application) : AndroidViewModel(application) {

    val telemetry: StateFlow<PaceSnapshot> = RunTrackingRepository.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunTrackingRepository.telemetry.value)

    val runStatus: StateFlow<RunStatus> = RunTrackingRepository.runStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunTrackingRepository.runStatus.value)

    fun startRun() = sendServiceAction(RunTrackingService.ACTION_START)

    fun pauseRun() = sendServiceAction(RunTrackingService.ACTION_PAUSE)

    fun stopRun() = sendServiceAction(RunTrackingService.ACTION_STOP)

    private fun sendServiceAction(action: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, RunTrackingService::class.java).setAction(action)
        context.startForegroundService(intent)
    }
}
