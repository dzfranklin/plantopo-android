package com.plantopo.plantopo.recording.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.plantopo.plantopo.MainActivity
import com.plantopo.plantopo.R
import com.plantopo.plantopo.recording.data.db.RecordingDatabase
import com.plantopo.plantopo.recording.data.model.TrackPoint
import com.plantopo.plantopo.recording.data.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class RecordingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: RecordingRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recordingId: Long? = null
    private var pointCount = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                handleLocationUpdate(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val database = RecordingDatabase.getInstance(this)
        repository = RecordingRepository(
            recordingDao = database.recordingDao(),
            trackPointDao = database.trackPointDao(),
            trpcClient = com.plantopo.plantopo.TrpcClient(
                com.plantopo.plantopo.AuthManager(this)
            )
        )

        createNotificationChannel()
        Timber.d("RecordingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val id = intent.getLongExtra(EXTRA_RECORDING_ID, -1)
                if (id != -1L) {
                    startRecording(id)
                }
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording(id: Long) {
        recordingId = id
        pointCount = 0

        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()

        Timber.d("Started recording: $id")
    }

    private fun stopRecording() {
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Timber.d("Stopped recording: $recordingId, total points: $pointCount")
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.e("Location permission not granted")
            stopRecording()
            return
        }

        // Maximum accuracy configuration
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 second interval for maximum detail
        ).apply {
            setMinUpdateIntervalMillis(500L) // Accept updates as fast as 0.5 seconds
            setMaxUpdateDelayMillis(15000L) // Batch delivery max 15 seconds
            setMinUpdateDistanceMeters(0f) // Capture all points, no distance filter
            setWaitForAccurateLocation(true) // Wait for accurate location
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        Timber.d("Location updates started with HIGH_ACCURACY priority")
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Timber.d("Location updates stopped")
    }

    private fun handleLocationUpdate(location: Location) {
        val id = recordingId ?: return

        val trackPoint = TrackPoint(
            timestamp = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            elevation = if (location.hasAltitude()) location.altitude else null,
            horizontalAccuracy = if (location.hasAccuracy()) location.accuracy else null,
            verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters
            } else null,
            speed = if (location.hasSpeed()) location.speed else null,
            speedAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                location.speedAccuracyMetersPerSecond
            } else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            bearingAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
                location.bearingAccuracyDegrees
            } else null,
            provider = location.provider ?: "unknown"
        )

        serviceScope.launch {
            repository.addTrackPoint(id, trackPoint)
            pointCount++

            if (pointCount % 10 == 0) {
                Timber.d("Recorded $pointCount points")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Track recording in progress"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Track")
            .setContentText("GPS tracking active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("RecordingService destroyed")
    }

    companion object {
        const val ACTION_START_RECORDING = "com.plantopo.plantopo.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.plantopo.plantopo.STOP_RECORDING"
        const val EXTRA_RECORDING_ID = "recording_id"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
    }
}
