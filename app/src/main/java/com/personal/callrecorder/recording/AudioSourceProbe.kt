package com.personal.callrecorder.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.personal.callrecorder.util.StorageManager
import com.personal.callrecorder.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Result of probing a single audio source. */
data class ProbeResult(
    val source: CallAudioSource,
    /** Peak amplitude observed (0..32767). ~0 means the source delivered silence. */
    val peakAmplitude: Int,
    val path: String?,
    val error: String? = null,
    /** Amplitude reading per poll tick (~150ms), for drawing a waveform. */
    val samples: List<Int> = emptyList()
) {
    val capturedSomething: Boolean get() = error == null && peakAmplitude > 300
}

/**
 * Records a short sample from a given [CallAudioSource] and measures the peak
 * amplitude, so we can determine empirically — on THIS device — which public
 * audio sources actually capture sound during a call (and, on playback, whose
 * voice). Uses only public Android APIs; no bypasses.
 *
 * Note: run this while the app is in the FOREGROUND. Android silences background
 * mic capture, so a foreground probe reflects the source's true capability.
 */
@Singleton
class AudioSourceProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: StorageManager,
    private val time: TimeProvider
) {
    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun probe(
        source: CallAudioSource,
        sampleRateHz: Int = 16000,
        durationMs: Long = 6000
    ): ProbeResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext ProbeResult(source, 0, null, "Microphone permission not granted")
        }
        val file = storage.newProbeFile(source.name)
        file.parentFile?.mkdirs()

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()

        var peak = 0
        val samples = mutableListOf<Int>()
        try {
            rec.setAudioSource(source.source)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(sampleRateHz)
            rec.setAudioEncodingBitRate(64_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()

            val end = time.now() + durationMs
            rec.maxAmplitude // first read establishes a baseline
            while (time.now() < end) {
                delay(150)
                val amp = runCatching { rec.maxAmplitude }.getOrDefault(0)
                samples.add(amp)
                if (amp > peak) peak = amp
            }
            rec.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "Probe failed for $source", t)
            runCatching { rec.reset() }
            runCatching { rec.release() }
            file.delete()
            return@withContext ProbeResult(source, 0, null, friendly(t))
        }
        runCatching { rec.reset() }
        runCatching { rec.release() }

        if (!file.exists() || file.length() <= 0L) {
            return@withContext ProbeResult(source, peak, null, "No data written", samples)
        }
        ProbeResult(source, peak, file.absolutePath, samples = samples)
    }

    private fun friendly(t: Throwable): String = when (t) {
        is IllegalStateException -> "Source unavailable / mic busy"
        is SecurityException -> "Not permitted (privileged source)"
        else -> t.message ?: "Failed"
    }

    private companion object {
        const val TAG = "AudioSourceProbe"
    }
}
