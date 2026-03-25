package com.plantopo.plantopo.recording.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPointEntity): Long

    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    suspend fun getPointsForRecording(recordingId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    fun observePointsForRecording(recordingId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT COUNT(*) FROM track_points WHERE recordingId = :recordingId")
    suspend fun getPointCount(recordingId: Long): Int

    @Query("DELETE FROM track_points WHERE recordingId = :recordingId")
    suspend fun deletePointsForRecording(recordingId: Long)
}
