package com.personal.callrecorder.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default AI provider: no-op, sends nothing. Swap the Hilt binding in
 * [com.personal.callrecorder.di.AiModule] for a real backend-backed provider
 * (→ Ollama → Qwen) to enable summaries.
 */
@Singleton
class DisabledAiSummaryProvider @Inject constructor() : AiSummaryProvider {
    override val isEnabled: Boolean = false

    override suspend fun summarize(transcript: String): Result<CallSummary> =
        Result.failure(IllegalStateException("AI summaries are not configured"))
}
