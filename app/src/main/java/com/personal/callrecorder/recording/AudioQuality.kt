package com.personal.callrecorder.recording

/**
 * Speech-oriented recording quality presets. Both use AAC in an MPEG-4 (.m4a)
 * container — efficient and well supported by Android encoders and by Whisper.
 */
enum class AudioQuality(
    val sampleRate: Int,
    val bitRate: Int,
    val label: String
) {
    /** Plenty for speech + transcription; small files. */
    STANDARD(sampleRate = 16_000, bitRate = 64_000, label = "Standard"),

    /** Higher fidelity if you want cleaner playback. */
    HIGH(sampleRate = 44_100, bitRate = 128_000, label = "High")
}
