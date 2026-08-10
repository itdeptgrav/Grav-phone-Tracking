package com.personal.callrecorder.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns where recordings live. Everything is under app-private internal storage
 * (context.filesDir) so nothing is world-readable and nothing survives an
 * uninstall — appropriate for highly sensitive call audio.
 *
 * Layout:  files/recordings/2026/08/call_2026-08-10_184302.m4a
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeProvider
) {
    private val root: File
        get() = File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }

    /** Allocate (but do not create) a new recording file path for [startTime]. */
    fun newRecordingFile(startTime: Long, extension: String = "m4a"): File {
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }
        val year = cal.get(Calendar.YEAR).toString()
        val month = "%02d".format(cal.get(Calendar.MONTH) + 1)
        val dir = File(root, "$year/$month").apply { if (!exists()) mkdirs() }
        val name = "call_${Formatters.fileTimestamp(startTime)}.$extension"
        return File(dir, name)
    }

    /**
     * A fixed file per diagnostic label (overwritten each run) for the audio-source
     * probe. Kept out of the main recordings tree.
     */
    fun newProbeFile(label: String): File {
        val dir = File(context.filesDir, "probes").apply { if (!exists()) mkdirs() }
        return File(dir, "probe_$label.m4a")
    }

    /** Total bytes used by all stored recordings. */
    fun totalBytes(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return runCatching { File(path).delete() }.getOrDefault(false)
    }

    fun exists(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).let { it.exists() && it.length() > 0 }
}
