package com.personal.callrecorder.data.database

import androidx.room.TypeConverter
import com.personal.callrecorder.call.CallDirection
import com.personal.callrecorder.data.entity.ProcessingStatus
import com.personal.callrecorder.data.entity.RecordingStatus

/** Room stores enums as their name string. Unknown values fall back safely. */
class Converters {

    @TypeConverter
    fun fromDirection(value: CallDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): CallDirection =
        runCatching { CallDirection.valueOf(value) }.getOrDefault(CallDirection.UNKNOWN)

    @TypeConverter
    fun fromRecordingStatus(value: RecordingStatus): String = value.name

    @TypeConverter
    fun toRecordingStatus(value: String): RecordingStatus =
        runCatching { RecordingStatus.valueOf(value) }.getOrDefault(RecordingStatus.NO_AUDIO)

    @TypeConverter
    fun fromProcessingStatus(value: ProcessingStatus): String = value.name

    @TypeConverter
    fun toProcessingStatus(value: String): ProcessingStatus =
        runCatching { ProcessingStatus.valueOf(value) }.getOrDefault(ProcessingStatus.NONE)
}
