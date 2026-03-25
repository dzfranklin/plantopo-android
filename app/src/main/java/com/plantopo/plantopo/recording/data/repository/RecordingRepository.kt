package com.plantopo.plantopo.recording.data.repository

import com.plantopo.plantopo.TrpcClient
import com.plantopo.plantopo.mutation
import com.plantopo.plantopo.recording.data.db.RecordingDao
import com.plantopo.plantopo.recording.data.db.RecordingEntity
import com.plantopo.plantopo.recording.data.db.RecordingStatus
import com.plantopo.plantopo.recording.data.db.TrackPointDao
import com.plantopo.plantopo.recording.data.db.TrackPointEntity
import com.plantopo.plantopo.recording.data.model.Recording
import com.plantopo.plantopo.recording.data.model.TrackPoint
import com.plantopo.plantopo.recording.data.model.toDomain
import com.plantopo.plantopo.recording.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import timber.log.Timber

class RecordingRepository(
    private val recordingDao: RecordingDao,
    private val trackPointDao: TrackPointDao,
    private val trpcClient: TrpcClient
) {
    // Observe all recordings with point counts
    fun observeAllRecordings(): Flow<List<Recording>> {
        return combine(
            recordingDao.observeAll(),
            recordingDao.observeAll()
        ) { recordings, _ ->
            recordings.map { entity ->
                val pointCount = trackPointDao.getPointCount(entity.id)
                entity.toDomain(pointCount)
            }
        }
    }

    // Observe a specific recording
    fun observeRecording(id: Long): Flow<Recording?> {
        return combine(
            recordingDao.observeById(id),
            trackPointDao.observePointCount(id)
        ) { entity, pointCount ->
            entity?.toDomain(pointCount)
        }
    }

    // Get the active recording
    suspend fun getActiveRecording(): Recording? {
        val entity = recordingDao.getActiveRecording() ?: return null
        val pointCount = trackPointDao.getPointCount(entity.id)
        return entity.toDomain(pointCount)
    }

    // Start a new recording
    suspend fun startRecording(name: String? = null): Long {
        val entity = RecordingEntity(
            name = name,
            startTime = System.currentTimeMillis(),
            status = RecordingStatus.RECORDING
        )
        return recordingDao.insert(entity)
    }

    // Stop a recording
    suspend fun stopRecording(id: Long) {
        val entity = recordingDao.getById(id) ?: return
        recordingDao.update(
            entity.copy(
                endTime = System.currentTimeMillis(),
                status = RecordingStatus.STOPPED
            )
        )
    }

    // Add a track point
    suspend fun addTrackPoint(recordingId: Long, point: TrackPoint) {
        trackPointDao.insert(point.toEntity(recordingId))
    }

    // Get all points for a recording
    suspend fun getTrackPoints(recordingId: Long): List<TrackPoint> {
        return trackPointDao.getPointsForRecording(recordingId).map { it.toDomain() }
    }

    // Observe points for a recording
    fun observeTrackPoints(recordingId: Long): Flow<List<TrackPoint>> {
        return combine(
            trackPointDao.observePointsForRecording(recordingId),
            trackPointDao.observePointsForRecording(recordingId)
        ) { points, _ ->
            points.map { it.toDomain() }
        }
    }

    // Sync a recording to the server
    suspend fun syncRecording(id: Long): Result<Unit> {
        return try {
            val recording = recordingDao.getById(id) ?: return Result.failure(
                IllegalArgumentException("Recording not found")
            )
            val points = trackPointDao.getPointsForRecording(id)

            val payload = UploadRecordingRequest(
                name = recording.name,
                startTime = recording.startTime,
                endTime = recording.endTime ?: System.currentTimeMillis(),
                points = points.map { it.toDomain() }
            )

            // Call the tRPC endpoint to upload the recording
            trpcClient.mutation<UploadRecordingRequest, UploadRecordingResponse>(
                "track.upload",
                payload
            )

            // Mark as synced
            recordingDao.update(
                recording.copy(
                    status = RecordingStatus.SYNCED,
                    syncedAt = System.currentTimeMillis(),
                    syncError = null
                )
            )

            Timber.d("Recording $id synced successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync recording $id")

            // Mark as sync failed
            val recording = recordingDao.getById(id)
            recording?.let {
                recordingDao.update(
                    it.copy(
                        status = RecordingStatus.SYNC_FAILED,
                        syncError = e.message
                    )
                )
            }

            Result.failure(e)
        }
    }

    // Get unsynced recordings
    suspend fun getUnsyncedRecordings(): List<Recording> {
        return recordingDao.getUnsyncedRecordings().map { entity ->
            val pointCount = trackPointDao.getPointCount(entity.id)
            entity.toDomain(pointCount)
        }
    }

    // Delete a recording
    suspend fun deleteRecording(id: Long) {
        recordingDao.deleteById(id)
    }
}

@Serializable
data class UploadRecordingRequest(
    val name: String?,
    val startTime: Long,
    val endTime: Long,
    val points: List<TrackPoint>
)

@Serializable
data class UploadRecordingResponse(
    val id: String
)
