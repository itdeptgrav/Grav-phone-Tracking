package com.personal.callrecorder.recording

import android.util.Log
import com.personal.callrecorder.call.CallSession
import com.personal.callrecorder.data.settings.RecordingMethod
import com.personal.callrecorder.util.StorageManager
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Selects a [RecordingStrategy] and drives its lifecycle. The service and the
 * rest of the app talk only to this class, never to a concrete strategy.
 */
@Singleton
class RecorderManager @Inject constructor(
    private val micProvider: Provider<MicrophoneRecordingStrategy>,
    private val oem: OemRecordingStrategy,
    private val privileged: PrivilegedRecordingStrategy,
    private val storage: StorageManager
) {
    @Volatile
    private var active: RecordingStrategy? = null

    val isRecording: Boolean get() = active != null

    /**
     * Resolve the strategy to use. AUTOMATIC prefers the most capable supported
     * strategy (privileged → OEM → microphone). Today only microphone is
     * supported, but the ordering is where future modes plug in.
     */
    fun resolveStrategy(method: RecordingMethod): RecordingStrategy? = when (method) {
        RecordingMethod.MICROPHONE -> micProvider.get().takeIf { it.isSupported() }
        RecordingMethod.AUTOMATIC -> sequenceOf(privileged, oem, micProvider.get())
            .firstOrNull { it.isSupported() }
    }

    /**
     * Start recording. Returns the method name that was started, or null if no
     * strategy is supported (e.g. microphone permission missing). Never throws.
     */
    suspend fun start(
        session: CallSession,
        audioSource: Int,
        sampleRateHz: Int,
        bitRate: Int,
        method: RecordingMethod
    ): String? {
        if (active != null) {
            Log.w(TAG, "start() called while already recording; ignoring")
            return active?.method
        }
        val strategy = resolveStrategy(method) ?: return null
        val file = storage.newRecordingFile(session.startTime)
        active = strategy
        strategy.start(
            session,
            RecordingConfig(
                outputFile = file,
                audioSource = audioSource,
                sampleRateHz = sampleRateHz,
                bitRate = bitRate
            )
        )
        return strategy.method
    }

    /** Stop the active recording and clear state. Safe to call when idle. */
    suspend fun stop(): RecordingResult {
        val strategy = active ?: return RecordingResult.Failure("No active recording")
        return try {
            strategy.stop()
        } finally {
            active = null
        }
    }

    fun currentMethod(): String? = active?.method

    private companion object {
        const val TAG = "RecorderManager"
    }
}
