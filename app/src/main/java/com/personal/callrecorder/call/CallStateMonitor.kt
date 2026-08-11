package com.personal.callrecorder.call

import android.content.Context
import android.util.Log
import com.personal.callrecorder.automation.AutomationController
import com.personal.callrecorder.contacts.ContactResolver
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.data.settings.AppSettings
import com.personal.callrecorder.data.settings.SettingsRepository
import com.personal.callrecorder.imports.RecordingImporter
import com.personal.callrecorder.recording.RecordingService
import com.personal.callrecorder.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide state machine that turns raw telephony transitions into call
 * sessions and drives automatic recording.
 *
 * Direction inference (no reliable API gives this directly on modern Android):
 *   IDLE → RINGING → OFF_HOOK   ⇒ INCOMING (answered)
 *   IDLE → OFF_HOOK             ⇒ OUTGOING
 *   IDLE → RINGING → IDLE       ⇒ missed incoming (never recorded)
 *
 * Instances of [PhoneStateReceiver] are short-lived, so all state lives here in
 * a singleton.
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repository: CallRepository,
    private val contactResolver: ContactResolver,
    private val importer: RecordingImporter,
    private val time: TimeProvider,
    private val automationController: AutomationController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var settings: AppSettings = AppSettings()

    private var lastState: CallState = CallState.IDLE
    private var sawRingingThisCall: Boolean = false
    private var pendingNumber: String? = null

    /** True while the service has (or should have) an active recording. */
    private var activeSession: CallSession? = null

    /** Begin observing settings. Call once from Application.onCreate. */
    fun start() {
        scope.launch {
            settingsRepository.settings.collect { settings = it }
        }
    }

    /** Entry point from the broadcast receiver. Synchronized: broadcasts are serial but cheap to guard. */
    @Synchronized
    fun onCallStateChanged(state: CallState, incomingNumber: String?) {
        Log.d(TAG, "state=$state (last=$lastState) number=${incomingNumber?.take(3)}…")
        when (state) {
            CallState.RINGING -> {
                sawRingingThisCall = true
                if (!incomingNumber.isNullOrBlank()) pendingNumber = incomingNumber
            }

            CallState.OFF_HOOK -> {
                // If the user has configured an OEM import folder, the phone's own
                // recorder handles the audio — we defer entirely and do NOT attempt
                // our own (failing) foreground recording. We just import afterwards.
                if (activeSession == null && settings.importFolderUri == null) {
                    val direction =
                        if (sawRingingThisCall) CallDirection.INCOMING else CallDirection.OUTGOING
                    val number = incomingNumber ?: pendingNumber
                    beginSession(number, direction)
                }
            }

            CallState.IDLE -> {
                val realCallEnded = lastState == CallState.OFF_HOOK
                if (activeSession != null) {
                    endSession()
                }
                // When deferring to an OEM recorder, auto-import the new file once
                // the call ends (the recorder needs a moment to finish writing).
                if (settings.importFolderUri != null) {
                    scheduleImport()
                }
                // Reset per-call transient state.
                sawRingingThisCall = false
                pendingNumber = null
                if (realCallEnded) {
                    scheduleShareAutomation()
                }
            }
        }
        lastState = state
    }

    private fun beginSession(number: String?, direction: CallDirection) {
        val snapshot = settings
        if (!snapshot.autoRecord || !directionAllowed(direction, snapshot)) {
            Log.d(TAG, "Auto-record disabled for $direction; not recording")
            return
        }
        val session = CallSession(number, direction, time.now())
        activeSession = session
        try {
            context.startForegroundService(RecordingService.startIntent(context, session))
        } catch (t: Throwable) {
            // Android 12+ can refuse a background foreground-service start from a
            // PHONE_STATE broadcast. Surface it as a logged, failed call rather
            // than crashing — the user sees why nothing was recorded.
            Log.e(TAG, "Could not start recording service", t)
            activeSession = null
            logCouldNotStart(session, t)
        }
    }

    /**
     * After a call ends, wait for the OEM recorder to flush its file, then import.
     * Retries a couple of times because write timing varies by device.
     */
    private fun scheduleImport() {
        scope.launch {
            repeat(3) { attempt ->
                delay(if (attempt == 0) 6_000L else 8_000L)
                val result = runCatching { importer.importFromFolder() }.getOrNull()
                if (result != null && result.imported > 0) return@launch
            }
        }
    }

    private fun endSession() {
        activeSession = null
        try {
            // The service is already running in the foreground, so delivering a
            // STOP command via startService is permitted even from background.
            context.startService(RecordingService.stopIntent(context))
        } catch (t: Throwable) {
            Log.e(TAG, "Could not deliver stop to recording service", t)
        }
    }

    private fun directionAllowed(direction: CallDirection, s: AppSettings): Boolean = when (direction) {
        CallDirection.INCOMING -> s.recordIncoming
        CallDirection.OUTGOING -> s.recordOutgoing
        CallDirection.UNKNOWN -> s.recordIncoming || s.recordOutgoing
    }

    private fun logCouldNotStart(session: CallSession, t: Throwable) {
        scope.launch {
            val id = repository.createInProgress(
                phoneNumber = session.phoneNumber,
                contactName = contactResolver.resolveName(session.phoneNumber),
                direction = session.direction,
                startTime = session.startTime,
                status = RecordingStatus.FAILED,
                method = null
            )
            repository.finalizeRecording(
                id = id,
                endTime = time.now(),
                durationMillis = 0,
                recordingPath = null,
                status = RecordingStatus.FAILED,
                error = "Android blocked background recording start. Grant " +
                    "\"Display over other apps\" on the home screen to enable auto-recording. " +
                    "(${t.javaClass.simpleName})"
            )
        }
    }
    private fun scheduleShareAutomation() {
        scope.launch {
            delay(6_000L)
            if (!automationController.running) {
                automationController.start()
            }
        }
    }
    private companion object {
        const val TAG = "CallStateMonitor"
    }
}
