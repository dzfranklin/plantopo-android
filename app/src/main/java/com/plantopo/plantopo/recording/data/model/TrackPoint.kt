package com.plantopo.plantopo.recording.data.model

import com.plantopo.plantopo.recording.data.db.TrackPointEntity
import kotlinx.serialization.Serializable

@Serializable
data class TrackPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val horizontalAccuracy: Float?,
    val verticalAccuracy: Float?,
    val speed: Float?,
    val speedAccuracy: Float?,
    val bearing: Float?,
    val bearingAccuracy: Float?,
    val provider: String
)

fun TrackPointEntity.toDomain() = TrackPoint(
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    elevation = elevation,
    horizontalAccuracy = horizontalAccuracy,
    verticalAccuracy = verticalAccuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    bearing = bearing,
    bearingAccuracy = bearingAccuracy,
    provider = provider
)

fun TrackPoint.toEntity(recordingId: Long) = TrackPointEntity(
    recordingId = recordingId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    elevation = elevation,
    horizontalAccuracy = horizontalAccuracy,
    verticalAccuracy = verticalAccuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    bearing = bearing,
    bearingAccuracy = bearingAccuracy,
    provider = provider
)
