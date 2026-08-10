package com.personal.callrecorder.data.entity

/** Shared status enum for the transcription and AI-summary pipelines. */
enum class ProcessingStatus {
    /** Not requested. */
    NONE,

    /** Queued but not started. */
    PENDING,

    /** Actively processing. */
    IN_PROGRESS,

    /** Finished successfully. */
    COMPLETED,

    /** Failed; safe to retry. */
    FAILED
}
