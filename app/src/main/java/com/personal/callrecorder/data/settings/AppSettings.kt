package com.personal.callrecorder.data.settings

import com.personal.callrecorder.recording.AudioQuality
import com.personal.callrecorder.recording.CallAudioSource

/** User-selectable recording method. */
enum class RecordingMethod {
    /** Pick the best supported strategy at runtime (currently microphone). */
    AUTOMATIC,

    /** Force microphone recording (Mode B). */
    MICROPHONE
}

/** All persisted user preferences, as one immutable snapshot. */
data class AppSettings(
    val autoRecord: Boolean = true,
    val recordIncoming: Boolean = true,
    val recordOutgoing: Boolean = true,
    val recordingMethod: RecordingMethod = RecordingMethod.AUTOMATIC,
    val audioQuality: AudioQuality = AudioQuality.STANDARD,
    /** Which mic source to capture with (VOICE_RECOGNITION works on most call-capable devices). */
    val callAudioSource: CallAudioSource = CallAudioSource.VOICE_RECOGNITION,
    /** Recording sample rate in Hz. 8000 = telephony narrowband; try this if audio is distorted. */
    val sampleRateHz: Int = 16000,
    val transcriptionEnabled: Boolean = false,
    val autoSummaries: Boolean = false,
    val biometricLock: Boolean = false,
    /** 0 = keep forever. Otherwise delete recordings older than this many days. */
    val deleteOlderThanDays: Int = 0,
    val legalNoticeAccepted: Boolean = false,
    /** SAF tree URI of the OEM call-recordings folder to import from, or null. */
    val importFolderUri: String? = null
)
