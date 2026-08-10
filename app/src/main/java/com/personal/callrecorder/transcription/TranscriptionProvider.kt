package com.personal.callrecorder.transcription

import java.io.File

/** Result of transcribing an audio file. */
data class TranscriptionResult(
    val text: String,
    val language: String? = null
)

/**
 * Vendor-neutral transcription contract. The app depends only on this; concrete
 * providers can be swapped via Hilt without touching UI or data code.
 *
 * Planned implementations (not built yet, by design — nothing uploads until you
 * explicitly wire one up):
 *   - WhisperLocalProvider      (on-device whisper.cpp / TFLite)
 *   - WhisperApiProvider        (OpenAI-compatible Whisper endpoint)
 *   - BackendTranscriptionProvider (your own CMS backend)
 */
interface TranscriptionProvider {

    /** Whether a real provider is configured and enabled. */
    val isEnabled: Boolean

    /** Transcribe [audioFile]. Returns a failed Result on any error; never throws. */
    suspend fun transcribe(audioFile: File): Result<TranscriptionResult>
}
