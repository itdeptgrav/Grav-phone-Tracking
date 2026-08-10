package com.personal.callrecorder.recording

/** Outcome of a completed recording attempt. */
sealed interface RecordingResult {

    data class Success(
        val path: String,
        val durationMillis: Long,
        val sizeBytes: Long,
        val method: String
    ) : RecordingResult

    /** Recorder ran but produced nothing usable (0 bytes, too short). */
    data class NoAudio(val reason: String) : RecordingResult

    /** Recorder could not start or crashed mid-way. */
    data class Failure(val reason: String) : RecordingResult
}
