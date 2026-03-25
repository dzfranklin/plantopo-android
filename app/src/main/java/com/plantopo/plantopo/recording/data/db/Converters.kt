package com.plantopo.plantopo.recording.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromRecordingStatus(status: RecordingStatus): String {
        return status.name
    }

    @TypeConverter
    fun toRecordingStatus(value: String): RecordingStatus {
        return RecordingStatus.valueOf(value)
    }
}
