package com.personal.callrecorder.data.entity

/** Lifecycle of the audio file attached to a call record. */
enum class RecordingStatus {
    /** Recording is currently in progress. */
    RECORDING,

    /** Recording finished and a valid audio file exists. */
    COMPLETED,

    /** Recording was attempted but failed (mic busy, permission, write error…). */
    FAILED,

    /** No audio was captured (e.g. auto-record disabled, or call never answered). */
    NO_AUDIO
}
