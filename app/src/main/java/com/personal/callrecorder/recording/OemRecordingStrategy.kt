package com.personal.callrecorder.recording

import com.personal.callrecorder.call.CallSession
import javax.inject.Inject

/**
 * Mode C — OEM/system integration placeholder.
 *
 * Some manufacturers (e.g. certain Xiaomi/Realme/Google Pixel builds and
 * regions) ship a native call-recording capability. There is no stable public
 * Android API to invoke it from a third-party app, so this strategy reports
 * unsupported by default. It exists so that a device-specific integration can
 * be dropped in later without touching the rest of the app.
 */
class OemRecordingStrategy @Inject constructor() : RecordingStrategy {

    override val method: String = "OEM"

    override fun isSupported(): Boolean = false

    override suspend fun start(session: CallSession, config: RecordingConfig) {
        // No-op: never selected while isSupported() is false.
    }

    override suspend fun stop(): RecordingResult =
        RecordingResult.Failure("OEM recording integration not available")
}
