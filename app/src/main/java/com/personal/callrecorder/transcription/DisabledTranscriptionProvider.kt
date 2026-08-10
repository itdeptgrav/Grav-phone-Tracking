package com.personal.callrecorder.transcription

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default provider: does nothing and uploads nothing. This is the safe,
 * privacy-preserving default. Replace the Hilt binding in
 * [com.personal.callrecorder.di.AiModule] with a real provider to enable
 * transcription.
 */
@Singleton
class DisabledTranscriptionProvider @Inject constructor() : TranscriptionProvider {
    override val isEnabled: Boolean = false

    override suspend fun transcribe(audioFile: File): Result<TranscriptionResult> =
        Result.failure(IllegalStateException("Transcription is not configured"))
}
