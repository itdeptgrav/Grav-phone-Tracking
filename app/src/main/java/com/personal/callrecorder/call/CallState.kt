package com.personal.callrecorder.call

import android.telephony.TelephonyManager

/** Normalised telephony call state, independent of the API used to obtain it. */
enum class CallState {
    IDLE,
    RINGING,
    OFF_HOOK;

    companion object {
        /** Map the string from a PHONE_STATE broadcast's EXTRA_STATE. */
        fun fromExtra(value: String?): CallState = when (value) {
            TelephonyManager.EXTRA_STATE_RINGING -> RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> OFF_HOOK
            else -> IDLE
        }

        /** Map the int from TelephonyCallback / PhoneStateListener. */
        fun fromInt(state: Int): CallState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> OFF_HOOK
            else -> IDLE
        }
    }
}
