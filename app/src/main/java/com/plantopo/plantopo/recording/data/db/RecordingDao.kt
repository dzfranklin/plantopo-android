package com.plantopo.plantopo.recording.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity)

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: String): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings ORDER BY startTime DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE status = :status ORDER BY startTime DESC")
    fun observeByStatus(status: RecordingStatus): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE status = 'RECORDING' LIMIT 1")
    suspend fun getActiveRecording(): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status IN ('STOPPED', 'SYNC_FAILED') ORDER BY startTime ASC")
    suspend fun getUnsyncedRecordings(): List<RecordingEntity>

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: String)
}
