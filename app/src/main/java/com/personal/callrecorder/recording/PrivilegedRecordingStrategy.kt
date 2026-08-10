package com.personal.callrecorder.recording

import com.personal.callrecorder.call.CallSession
import javax.inject.Inject

/**
 * Mode D — privileged / root / system-app placeholder.
 *
 * A device with root or the app installed as a system/priv-app could capture
 * the true call audio (e.g. via AudioSource.VOICE_CALL, or an AudioRecord on a
 * privileged capture path). This is intentionally NOT implemented in the
 * initial build. The interface is here so a privileged implementation can be
 * added later with zero changes elsewhere.
 */
class PrivilegedRecordingStrategy @Inject constructor() : RecordingStrategy {

    override val method: String = "PRIVILEGED"

    /** Detection intentionally conservative: never claim root we haven't verified. */
    override fun isSupported(): Boolean = false

    override suspend fun start(session: CallSession, config: RecordingConfig) {
        // No-op until a privileged build implements true call-audio capture.
    }

    override suspend fun stop(): RecordingResult =
        RecordingResult.Failure("Privileged recording not implemented")
}
