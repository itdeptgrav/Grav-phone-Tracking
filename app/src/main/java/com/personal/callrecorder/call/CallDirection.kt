package com.personal.callrecorder.call

/** Direction of a call. UNKNOWN is used when the OS does not expose enough info. */
enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN;

    fun label(): String = when (this) {
        INCOMING -> "Incoming"
        OUTGOING -> "Outgoing"
        UNKNOWN -> "Call"
    }
}
