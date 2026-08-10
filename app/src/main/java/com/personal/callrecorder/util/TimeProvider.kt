package com.personal.callrecorder.util

import javax.inject.Inject
import javax.inject.Singleton

/** Indirection over the system clock so time-dependent code stays testable. */
interface TimeProvider {
    fun now(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
