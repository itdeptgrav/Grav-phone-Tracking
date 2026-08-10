package com.personal.callrecorder.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Structured AI summary of a call. Stored as JSON in CallRecord.summary so the
 * schema can evolve and a future CMS can consume it directly.
 */
@Serializable
data class CallSummary(
    val summary: String = "",
    val importantPoints: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val followUpDate: String? = null,
    val peopleMentioned: List<String> = emptyList(),
    val amountsMentioned: List<String> = emptyList()
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJsonOrNull(raw: String?): CallSummary? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<CallSummary>(raw) }.getOrNull()
        }
    }
}
