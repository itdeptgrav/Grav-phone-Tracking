package com.personal.callrecorder.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.callrecorder.call.CallDirection

/**
 * A single recorded (or attempted) call. This is the core persistent model.
 *
 * Nullability reflects Android reality:
 *  - [phoneNumber] can be null: on Android 9+ the number is frequently withheld
 *    from the PHONE_STATE broadcast, and may only be recoverable from the call
 *    log (requires READ_CALL_LOG) or not at all.
 *  - [contactName] is null unless READ_CONTACTS is granted and a match exists.
 *  - [recordingPath] is null when no audio was captured.
 */
@Entity(
    tableName = "call_records",
    indices = [
        Index("startTime"),
        Index("phoneNumber")
    ]
)
data class CallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val phoneNumber: String? = null,
    val contactName: String? = null,

    val direction: CallDirection = CallDirection.UNKNOWN,

    /** Epoch millis when the call became active (recording start reference). */
    val startTime: Long,

    /** Epoch millis when the call ended; null while in progress. */
    val endTime: Long? = null,

    /** Call/recording duration in milliseconds. */
    val durationMillis: Long = 0L,

    /** Absolute path to the audio file in app-private storage, or null. */
    val recordingPath: String? = null,

    val recordingStatus: RecordingStatus = RecordingStatus.NO_AUDIO,

    /** Which [com.personal.callrecorder.recording.RecordingStrategy] produced the audio. */
    val recordingMethod: String? = null,

    /**
     * Source filename when this record was imported from an OEM recorder folder
     * (e.g. OPPO's built-in call recording). Used to avoid re-importing. Null for
     * recordings this app captured itself.
     */
    val importSourceName: String? = null,

    /** Human-readable reason a recording failed / produced no audio. */
    val recordingError: String? = null,

    val transcription: String? = null,
    val transcriptionStatus: ProcessingStatus = ProcessingStatus.NONE,

    /** JSON-serialized [com.personal.callrecorder.ai.CallSummary], or null. */
    val summary: String? = null,
    val summaryStatus: ProcessingStatus = ProcessingStatus.NONE,

    val notes: String? = null,

    val createdAt: Long,
    val updatedAt: Long
) {
    /** Best display label: contact name if known, else formatted number, else "Unknown". */
    val displayName: String
        get() = contactName?.takeIf { it.isNotBlank() }
            ?: phoneNumber?.takeIf { it.isNotBlank() }
            ?: "Unknown number"

    val hasRecording: Boolean
        get() = recordingStatus == RecordingStatus.COMPLETED && !recordingPath.isNullOrBlank()
}
