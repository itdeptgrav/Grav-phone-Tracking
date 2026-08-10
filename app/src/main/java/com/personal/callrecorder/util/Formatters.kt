package com.personal.callrecorder.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Small, allocation-cheap formatting helpers used across the UI. */
object Formatters {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val fullDateFmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

    /** "6:43 PM" */
    fun clockTime(epochMillis: Long): String = timeFmt.format(Date(epochMillis))

    /** "10 August 2026" */
    fun fullDate(epochMillis: Long): String = fullDateFmt.format(Date(epochMillis))

    /** Filesystem-safe timestamp for recording filenames. */
    fun fileTimestamp(epochMillis: Long): String = fileFmt.format(Date(epochMillis))

    /** "14m 32s" / "8m 11s" / "42s". */
    fun duration(millis: Long): String {
        if (millis <= 0) return "0s"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (h > 0 || m > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }

    /** "00:00" / "14:32" position for the audio player. */
    fun clockPosition(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }

    /** Human day bucket relative to today: "Today", "Yesterday", or a date. */
    fun dayBucket(epochMillis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = dayOfYearDiff(now, then)
        return when {
            sameYear && dayDiff == 0 -> "Today"
            sameYear && dayDiff == 1 -> "Yesterday"
            else -> fullDate(epochMillis)
        }
    }

    private fun dayOfYearDiff(now: Calendar, then: Calendar): Int {
        if (now.get(Calendar.YEAR) != then.get(Calendar.YEAR)) return Int.MAX_VALUE
        return now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    }

    /** Rough human size: "1.2 MB". */
    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return if (i == 0) "$bytes B" else "%.1f %s".format(value, units[i])
    }
}
