package com.personal.callrecorder.call

/**
 * An in-progress call the app is (or considers) tracking. Immutable snapshot;
 * a new instance is produced as the call progresses.
 */
data class CallSession(
    val phoneNumber: String?,
    val direction: CallDirection,
    val startTime: Long,
    /** DB row id once the record has been created, else null. */
    val recordId: Long? = null
)
