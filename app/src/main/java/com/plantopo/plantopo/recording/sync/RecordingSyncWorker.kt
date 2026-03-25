package com.plantopo.plantopo.recording.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.plantopo.plantopo.AuthManager
import com.plantopo.plantopo.TrpcClient
import com.plantopo.plantopo.recording.data.db.RecordingDatabase
import com.plantopo.plantopo.recording.data.repository.RecordingRepository
import timber.log.Timber

class RecordingSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("Starting recording sync work")

        val database = RecordingDatabase.getInstance(applicationContext)
        val repository = RecordingRepository(
            recordingDao = database.recordingDao(),
            trackPointDao = database.trackPointDao(),
            trpcClient = TrpcClient(AuthManager(applicationContext))
        )

        val unsyncedRecordings = repository.getUnsyncedRecordings()
        Timber.d("Found ${unsyncedRecordings.size} unsynced recordings")

        var successCount = 0
        var failureCount = 0

        for (recording in unsyncedRecordings) {
            val result = repository.syncRecording(recording.id)
            if (result.isSuccess) {
                successCount++
                Timber.d("Synced recording ${recording.id}")
            } else {
                failureCount++
                Timber.e("Failed to sync recording ${recording.id}: ${result.exceptionOrNull()?.message}")
            }
        }

        Timber.d("Sync complete: $successCount succeeded, $failureCount failed")

        return if (failureCount == 0) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "recording_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<RecordingSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Timber.d("Enqueued recording sync work")
        }
    }
}
