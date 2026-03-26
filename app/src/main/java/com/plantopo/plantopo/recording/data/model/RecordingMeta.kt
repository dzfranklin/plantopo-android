package com.plantopo.plantopo.recording.data.model

import com.plantopo.plantopo.recording.data.db.RecordingEntity
import com.plantopo.plantopo.recording.data.db.RecordingStatus
import kotlinx.serialization.Serializable

@Serializable
data class RecordingMeta(
    val id: String,
    val name: String?,
    val startTime: Long,
    val endTime: Long?,
    val status: RecordingStatus,
    val pointCount: Int = 0
)

fun RecordingEntity.toDomain() = RecordingMeta(
    id = id,
    name = name,
    startTime = startTime,
    endTime = endTime,
    status = status,
)
