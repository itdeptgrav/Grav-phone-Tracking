package com.personal.callrecorder.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.personal.callrecorder.call.CallSession
import com.personal.callrecorder.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Mode B — microphone recording.
 *
 * ANDROID REALITY (read this before "fixing" the audio source):
 * Third-party apps on modern Android CANNOT capture the downlink (the other
 * person's voice) of a cellular call. AudioSource.VOICE_CALL / VOICE_DOWNLINK
 * are blocked for non-system apps and throw or return silence. Only
 * AudioSource.MIC / VOICE_COMMUNICATION are available to us, and those capture
 * the local microphone only. The far party is captured ONLY if the call is on
 * speakerphone (their voice comes out of the speaker and back into the mic).
 *
 * We use VOICE_COMMUNICATION when available (echo/noise processing tuned for
 * two-way speech) and fall back to MIC. This is honestly a mic recorder, not
 * internal call-audio capture, and the UI labels it as such.
 */
class MicrophoneRecordingStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeProvider
) : RecordingStrategy {

    override val method: String = "MICROPHONE"

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L
    private var startError: String? = null

    override fun isSupported(): Boolean {
        val hasMic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        return hasMic && hasRecordAudioPermission()
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun start(session: CallSession, config: RecordingConfig) =
        withContext(Dispatchers.IO) {
            startError = null
            if (!hasRecordAudioPermission()) {
                startError = "Microphone permission not granted"
                return@withContext
            }
            val file = config.outputFile
            outputFile = file
            file.parentFile?.mkdirs()

            val rec = createRecorder()
            try {
                // Audio source is configurable: on modern Android, MIC is usually
                // silent during a call, while VOICE_RECOGNITION / VOICE_COMMUNICATION
                // capture call audio on capable devices. The exact source + sample
                // rate that sound clean are device-dependent, so both are settings.
                rec.setAudioSource(config.audioSource)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioChannels(1)
                rec.setAudioSamplingRate(config.sampleRateHz)
                rec.setAudioEncodingBitRate(config.bitRate)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()
                recorder = rec
                startedAt = time.now()
            } catch (t: Throwable) {
                // IllegalStateException (mic busy / another app owns input),
                // IOException (prepare failed / cannot write), etc.
                Log.e(TAG, "Failed to start microphone recording", t)
                startError = friendlyError(t)
                safeReleaseAfterFailure(rec)
                recorder = null
            }
        }

    override suspend fun stop(): RecordingResult = withContext(Dispatchers.IO) {
        val rec = recorder
        val file = outputFile
        val err = startError

        if (rec == null) {
            return@withContext RecordingResult.Failure(err ?: "Recorder was not running")
        }

        val duration = (time.now() - startedAt).coerceAtLeast(0)
        try {
            rec.stop()
        } catch (t: Throwable) {
            // stop() throws if stopped almost immediately (no frames written).
            Log.w(TAG, "MediaRecorder.stop() failed", t)
            safeReleaseAfterFailure(rec)
            recorder = null
            file?.delete()
            return@withContext RecordingResult.NoAudio("Call too short to record")
        } finally {
            runCatching { rec.reset() }
            runCatching { rec.release() }
            recorder = null
        }

        return@withContext when {
            file == null || !file.exists() ->
                RecordingResult.Failure("Output file missing")
            file.length() <= 0L -> {
                file.delete()
                RecordingResult.NoAudio("Recorded 0 bytes")
            }
            else -> RecordingResult.Success(
                path = file.absolutePath,
                durationMillis = duration,
                sizeBytes = file.length(),
                method = method
            )
        }
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()

    private fun safeReleaseAfterFailure(rec: MediaRecorder) {
        runCatching { rec.reset() }
        runCatching { rec.release() }
        outputFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
    }

    private fun friendlyError(t: Throwable): String = when (t) {
        is IllegalStateException -> "Microphone is in use by another app"
        is SecurityException -> "Microphone permission denied"
        else -> t.message ?: "Recorder initialization failed"
    }

    private companion object {
        const val TAG = "MicRecorder"
    }
}
