package com.personal.callrecorder.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Number + direction recovered from the system call log. */
data class CallLogEntry(
    val number: String?,
    val direction: CallDirection
)

/**
 * Best-effort recovery of a call's number/direction from CallLog.
 *
 * WHY THIS EXISTS: on Android 9+ the phone number is usually withheld from the
 * PHONE_STATE broadcast, so at call-end we reconcile against the system call
 * log. This is best-effort: the just-ended call may not be written to the log
 * for a short moment, so callers should treat a null result as "unknown", never
 * as an error.
 */
@Singleton
class CallLogResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /** Most recent call-log entry, or null if unavailable / not permitted. */
    fun mostRecent(): CallLogEntry? {
        if (!hasPermission()) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val number = if (numberIdx >= 0) c.getString(numberIdx) else null
                val direction = if (typeIdx >= 0) directionFor(c.getInt(typeIdx)) else CallDirection.UNKNOWN
                CallLogEntry(number, direction)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun directionFor(type: Int): CallDirection = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
        else -> CallDirection.UNKNOWN
    }
}
