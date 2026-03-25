package com.plantopo.plantopo.recording.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordingId"), Index("timestamp")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val timestamp: Long, // Epoch milliseconds
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?, // Nullable - not always available
    val horizontalAccuracy: Float?, // Meters (Location.getAccuracy())
    val verticalAccuracy: Float?, // Meters (Location.getVerticalAccuracyMeters())
    val speed: Float?, // Meters/second
    val speedAccuracy: Float?, // Meters/second (Location.getSpeedAccuracyMetersPerSecond())
    val bearing: Float?, // Degrees
    val bearingAccuracy: Float?, // Degrees (Location.getBearingAccuracyDegrees())
    val provider: String // "gps", "network", "fused", etc.
)
