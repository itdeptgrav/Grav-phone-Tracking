package com.personal.callrecorder.capability

/** A transparent, honest snapshot of what the app can and cannot do here. */
data class DeviceCapabilities(
    val deviceModel: String,
    val androidRelease: String,
    val apiLevel: Int,
    val callDetectionSupported: Boolean,
    val microphoneRecordingSupported: Boolean,
    /** Always false on a normal non-rooted device — documented, not hidden. */
    val directCallAudioSupported: Boolean,
    val speakerphoneRecordingAvailable: Boolean,
    val oemIntegrationAvailable: Boolean,
    val rootDetected: Boolean,
    val recordAudioGranted: Boolean,
    val phoneStateGranted: Boolean,
    /** "Display over other apps" — the exemption that lets recording auto-start. */
    val overlayGranted: Boolean
)
