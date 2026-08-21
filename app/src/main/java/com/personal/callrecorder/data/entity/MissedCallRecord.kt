package com.personal.callrecorder.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.callrecorder.call.CallDirection

/**
 * A call that rang and ended without ever being answered — telephony state
 * went RINGING → IDLE, never OFF_HOOK.
 *
 * Kept in its own table rather than as a row in [CallRecord]: a call that
 * never connected has no recording, no duration, none of that entity's
 * audio-lifecycle fields (recordingPath/recordingStatus/recordingMethod/
 * transcription/summary) would ever be meaningful for it, and mixing
 * "never connected" rows into the recordings list would make every
 * recordings query filter around them (21 Aug 2026, explicit request — "the
 * calls events which are not received by the receiver then these logs need
 * to keep in another schema").
 */
@Entity(
    tableName = "missed_calls",
    indices = [Index("ringStartTime"), Index("phoneNumber")]
)
data class MissedCallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val phoneNumber: String? = null,
    val contactName: String? = null,

    // Always INCOMING in practice: an outgoing call reaches OFF_HOOK the
    // moment it is dialled (Android reports it as off-hook while ringing
    // out), so RINGING→IDLE without OFF_HOOK can only happen on the
    // receiving end. Kept as a real field rather than hardcoded, for
    // symmetry with CallRecord and in case a future OEM exposes a distinct
    // "outgoing, no answer" signal.
    val direction: CallDirection = CallDirection.INCOMING,

    /** Epoch millis when the phone started ringing. */
    val ringStartTime: Long,

    /** Epoch millis when it went back to idle (i.e. stopped ringing). */
    val endTime: Long,

    val createdAt: Long
) {
    val displayName: String
        get() = contactName?.takeIf { it.isNotBlank() }
            ?: phoneNumber?.takeIf { it.isNotBlank() }
            ?: "Unknown number"
}
