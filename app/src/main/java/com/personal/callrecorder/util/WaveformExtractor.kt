package com.personal.callrecorder.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Decodes an audio file to PCM and reduces it to a small array of per-bucket peak
 * amplitudes (0..1) suitable for drawing a static waveform. Uses only the public
 * MediaExtractor/MediaCodec APIs. Returns an empty array on any failure — the UI
 * simply shows no waveform in that case.
 */
object WaveformExtractor {

    fun extract(path: String, buckets: Int = 160): FloatArray {
        if (path.isBlank() || !File(path).let { it.exists() && it.length() > 0 }) return FloatArray(0)

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return FloatArray(0)

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            val durationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val peaks = FloatArray(buckets)
            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var sawInputEos = false
            var sawOutputEos = false
            var iterations = 0
            val maxIterations = 100_000 // safety backstop

            while (!sawOutputEos && iterations++ < maxIterations) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shorts = ShortArray(info.size / 2)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

                            var maxAbs = 0
                            for (s in shorts) {
                                val a = abs(s.toInt())
                                if (a > maxAbs) maxAbs = a
                            }
                            val bucket = if (durationUs > 0) {
                                ((info.presentationTimeUs.toDouble() / durationUs) * buckets)
                                    .toInt().coerceIn(0, buckets - 1)
                            } else 0
                            val v = maxAbs / 32767f
                            if (v > peaks[bucket]) peaks[bucket] = v
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
            return peaks
        } catch (t: Throwable) {
            return FloatArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
