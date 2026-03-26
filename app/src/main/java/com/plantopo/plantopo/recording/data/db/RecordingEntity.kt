package com.plantopo.plantopo.recording.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey
    val id: String,
    val name: String? = null,
    val startTime: Long, // Epoch milliseconds
    val endTime: Long? = null, // Null if still recording
    val status: RecordingStatus,
    val syncedAt: Long? = null, // Null if not synced yet
    val syncError: String? = null
)

@Serializable
enum class RecordingStatus {
    RECORDING,
    STOPPED,
    SYNCED,
    SYNC_FAILED
}
