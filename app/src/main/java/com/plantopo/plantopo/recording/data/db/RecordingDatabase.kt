package com.plantopo.plantopo.recording.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RecordingEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RecordingDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun trackPointDao(): TrackPointDao

    companion object {
        @Volatile
        private var INSTANCE: RecordingDatabase? = null

        fun getInstance(context: Context): RecordingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecordingDatabase::class.java,
                    "plantopo_recordings.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
