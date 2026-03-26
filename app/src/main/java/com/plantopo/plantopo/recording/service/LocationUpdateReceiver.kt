package com.plantopo.plantopo.recording.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.LocationResult
import timber.log.Timber

class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LOCATION_UPDATE) {
            val locationResult = LocationResult.extractResult(intent)
            if (locationResult != null) {
                Timber.d("Received ${locationResult.locations.size} location update(s)")

                // Forward to RecordingService to handle
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_PROCESS_LOCATION
                    putExtra(EXTRA_LOCATION_RESULT, locationResult)
                }
                context.startService(serviceIntent)
            } else {
                Timber.w("Received location update intent but no LocationResult found")
            }
        }
    }

    companion object {
        const val ACTION_LOCATION_UPDATE = "com.plantopo.plantopo.LOCATION_UPDATE"
        const val EXTRA_LOCATION_RESULT = "location_result"
    }
}
