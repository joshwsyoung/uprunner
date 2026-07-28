package com.uprunner.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class UprunnerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createRunTrackingNotificationChannel()
    }

    private fun createRunTrackingNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            RUN_TRACKING_CHANNEL_ID,
            "Run tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing notification shown while a run is being tracked"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val RUN_TRACKING_CHANNEL_ID = "run_tracking"
    }
}
