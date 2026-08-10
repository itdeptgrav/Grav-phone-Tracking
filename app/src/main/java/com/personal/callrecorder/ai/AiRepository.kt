package com.personal.callrecorder.ai

import com.personal.callrecorder.data.entity.ProcessingStatus
import com.personal.callrecorder.data.repository.CallRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates AI summarization for a stored call: takes its transcript, runs
 * the configured [AiSummaryProvider], and persists the structured summary JSON.
 * Manual/opt-in — nothing runs or uploads automatically.
 */
@Singleton
class AiRepository @Inject constructor(
    private val provider: AiSummaryProvider,
    private val callRepository: CallRepository
) {
    val isEnabled: Boolean get() = provider.isEnabled

    /** @return user-facing error message, or null on success. */
    suspend fun summarize(callId: Long): String? {
        if (!provider.isEnabled) return "AI summaries are not configured"
        val record = callRepository.getById(callId) ?: return "Call not found"
        val transcript = record.transcription
        if (transcript.isNullOrBlank()) return "Transcribe the call first"

        callRepository.updateSummary(callId, record.summary, ProcessingStatus.IN_PROGRESS)
        val result = provider.summarize(transcript)
        return result.fold(
            onSuccess = {
                callRepository.updateSummary(callId, it.toJson(), ProcessingStatus.COMPLETED)
                null
            },
            onFailure = {
                callRepository.updateSummary(callId, record.summary, ProcessingStatus.FAILED)
                it.message ?: "Summarization failed"
            }
        )
    }
}
