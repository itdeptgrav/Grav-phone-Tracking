package com.personal.callrecorder.transcription

import com.personal.callrecorder.data.entity.ProcessingStatus
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.util.StorageManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates transcription for a stored call: reads the audio file, runs the
 * configured [TranscriptionProvider], and writes the result + status back to the
 * database. Manual/opt-in — nothing runs automatically.
 */
@Singleton
class TranscriptionRepository @Inject constructor(
    private val provider: TranscriptionProvider,
    private val callRepository: CallRepository,
    private val storage: StorageManager
) {
    val isEnabled: Boolean get() = provider.isEnabled

    /** @return user-facing error message, or null on success. */
    suspend fun transcribe(callId: Long): String? {
        if (!provider.isEnabled) return "Transcription is not configured"
        val record = callRepository.getById(callId) ?: return "Call not found"
        val path = record.recordingPath
        if (path.isNullOrBlank() || !storage.exists(path)) {
            return "No audio available to transcribe"
        }

        callRepository.updateTranscription(callId, record.transcription, ProcessingStatus.IN_PROGRESS)
        val result = provider.transcribe(File(path))
        return result.fold(
            onSuccess = {
                callRepository.updateTranscription(callId, it.text, ProcessingStatus.COMPLETED)
                null
            },
            onFailure = {
                callRepository.updateTranscription(callId, record.transcription, ProcessingStatus.FAILED)
                it.message ?: "Transcription failed"
            }
        )
    }
}
