package com.uprunner.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.uprunner.app.MainActivity
import com.uprunner.app.UprunnerApplication
import com.uprunner.app.data.db.AppDatabase
import com.uprunner.app.data.db.LocationSampleEntity
import com.uprunner.app.data.db.RunEntity
import com.uprunner.core.model.LocationSample
import com.uprunner.core.model.RunStatus
import com.uprunner.core.pace.PaceCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground Service per spec §7: polls android.location.LocationManager (no Google Play
 * Services), runs GPS fixes through [PaceCalculator]'s 50m rolling window, and persists
 * every sample via Room. Talks to the UI through [RunTrackingRepository] — see that class
 * for why a started service + singleton was chosen over a bound service.
 */
class RunTrackingService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var locationManager: LocationManager
    private lateinit var database: AppDatabase
    private var paceCalculator: PaceCalculator? = null
    private var currentRunId: String? = null

    private val locationListener = LocationListener { location -> onLocationUpdate(location) }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        database = AppDatabase.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationManager.removeUpdates(locationListener)
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startTracking() {
        if (currentRunId != null) return // already tracking

        val runId = UUID.randomUUID().toString()
        currentRunId = runId
        paceCalculator = PaceCalculator()

        startForeground(NOTIFICATION_ID, buildNotification())
        RunTrackingRepository.publishStatus(RunStatus.ACTIVE)

        serviceScope.launch {
            database.runDao().insert(
                RunEntity(
                    id = runId,
                    startTimeMillis = System.currentTimeMillis(),
                    endTimeMillis = null,
                    totalDistanceMeters = 0.0,
                    status = RunStatus.ACTIVE.name,
                ),
            )
        }

        @Suppress("MissingPermission") // caller (Activity) must have already requested/granted this
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            MIN_UPDATE_INTERVAL_MILLIS,
            MIN_UPDATE_DISTANCE_METERS,
            locationListener,
        )
    }

    private fun pauseTracking() {
        locationManager.removeUpdates(locationListener)
        RunTrackingRepository.publishStatus(RunStatus.PAUSED)
    }

    private fun stopTracking() {
        locationManager.removeUpdates(locationListener)
        val runId = currentRunId
        val finalSnapshot = RunTrackingRepository.telemetry.value
        if (runId != null) {
            serviceScope.launch {
                database.runDao().getById(runId)?.let { existing ->
                    database.runDao().update(
                        existing.copy(
                            endTimeMillis = System.currentTimeMillis(),
                            totalDistanceMeters = finalSnapshot.totalDistanceMeters,
                            status = RunStatus.COMPLETED.name,
                        ),
                    )
                }
            }
        }
        currentRunId = null
        paceCalculator = null
        RunTrackingRepository.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onLocationUpdate(location: Location) {
        val calculator = paceCalculator ?: return
        val runId = currentRunId ?: return

        val sample = LocationSample(
            latitude = location.latitude,
            longitude = location.longitude,
            timestampMillis = location.time,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
        )

        val snapshot = calculator.addSample(sample)
        RunTrackingRepository.publishTelemetry(snapshot)

        serviceScope.launch {
            database.locationSampleDao().insert(
                LocationSampleEntity(
                    runId = runId,
                    timestampMillis = sample.timestampMillis,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    accuracyMeters = sample.accuracyMeters,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, UprunnerApplication.RUN_TRACKING_CHANNEL_ID)
            .setContentTitle("Run in progress")
            .setContentText("Tracking your pace, distance, and time")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.uprunner.app.action.START"
        const val ACTION_PAUSE = "com.uprunner.app.action.PAUSE"
        const val ACTION_STOP = "com.uprunner.app.action.STOP"

        private const val NOTIFICATION_ID = 1001
        private const val MIN_UPDATE_INTERVAL_MILLIS = 2_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 3.0f
    }
}
