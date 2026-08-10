package com.personal.callrecorder.recording

import android.media.MediaRecorder

/**
 * Selectable microphone audio source for call recording.
 *
 * On modern Android, `MIC` typically yields SILENCE during a call (the telephony
 * stack owns the mic). The sources that actually capture call audio on capable
 * devices are VOICE_RECOGNITION and VOICE_COMMUNICATION — this mirrors what
 * dedicated call recorders (e.g. Cube ACR) use on Android 10–14. The best source
 * is device-dependent, so we expose it as a setting to tune out distortion.
 */
enum class CallAudioSource(val label: String, val source: Int) {
    /** Least processing; usually the cleanest for call capture on Unisoc/OPPO. */
    VOICE_RECOGNITION("Voice recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION),

    /** Adds echo/noise processing tuned for two-way speech; try if the other side echoes. */
    VOICE_COMMUNICATION("Voice communication", MediaRecorder.AudioSource.VOICE_COMMUNICATION),

    /** Plain mic — usually silent during calls, kept for comparison. */
    MICROPHONE("Microphone", MediaRecorder.AudioSource.MIC),

    /** True call audio — blocked for non-privileged apps; will fail unless privileged. */
    VOICE_CALL("Voice call (privileged)", MediaRecorder.AudioSource.VOICE_CALL);
}
