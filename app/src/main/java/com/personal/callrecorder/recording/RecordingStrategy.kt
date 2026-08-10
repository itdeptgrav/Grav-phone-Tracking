package com.personal.callrecorder.recording

import com.personal.callrecorder.call.CallSession
import java.io.File

/** Immutable config handed to a strategy at start time. */
data class RecordingConfig(
    val outputFile: File,
    /** Resolved MediaRecorder.AudioSource int (from [CallAudioSource]). */
    val audioSource: Int,
    val sampleRateHz: Int,
    val bitRate: Int
)

/**
 * Abstraction over *how* call audio is captured. The rest of the app depends
 * only on this — it never knows or cares whether audio came from the microphone
 * (Mode B), a future OEM API (Mode C), or a privileged/root path (Mode D).
 *
 * Contract:
 *  - [isSupported] must be cheap and side-effect free.
 *  - [start] must throw nothing to the caller on ordinary failure; surface it
 *    from [stop] as a [RecordingResult.Failure] instead, so the call still logs.
 */
interface RecordingStrategy {

    /** Stable identifier stored on the call record, e.g. "MICROPHONE". */
    val method: String

    /** Whether this strategy can run on the current device/OS right now. */
    fun isSupported(): Boolean

    /** Begin capturing. Implementations should record their own failure state. */
    suspend fun start(session: CallSession, config: RecordingConfig)

    /** Stop capturing and report the outcome. Always safe to call. */
    suspend fun stop(): RecordingResult
}
