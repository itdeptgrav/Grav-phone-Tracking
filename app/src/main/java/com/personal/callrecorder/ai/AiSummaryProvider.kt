package com.personal.callrecorder.ai

/**
 * Vendor-neutral AI-summary contract. Intended eventual wiring:
 *
 *   Android app → your CMS backend → Ollama → Qwen
 *
 * The app must never talk to Ollama directly over the public internet; a
 * BackendAiSummaryProvider that posts a transcript to your own authenticated
 * backend is the supported shape. Disabled by default — nothing is sent
 * anywhere until you wire a real provider and enable it in Settings.
 */
interface AiSummaryProvider {

    val isEnabled: Boolean

    /** Summarize a transcript. Returns a failed Result on any error; never throws. */
    suspend fun summarize(transcript: String): Result<CallSummary>
}
