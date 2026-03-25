package com.plantopo.plantopo.recording.data.model

import com.plantopo.plantopo.recording.data.db.RecordingEntity
import com.plantopo.plantopo.recording.data.db.RecordingStatus
import kotlinx.serialization.Serializable

@Serializable
data class Recording(
    val id: Long,
    val name: String?,
    val startTime: Long,
    val endTime: Long?,
    val status: RecordingStatus,
    val pointCount: Int = 0
)

@Serializable
data class RecordingWithPoints(
    val recording: Recording,
    val points: List<TrackPoint>
)

fun RecordingEntity.toDomain() = Recording(
    id = id,
    name = name,
    startTime = startTime,
    endTime = endTime,
    status = status,
)
