package com.personal.callrecorder.call

import android.content.Context
import android.util.Log
import com.personal.callrecorder.automation.AutomationController
import com.personal.callrecorder.contacts.ContactResolver
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.data.repository.MissedCallRepository
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
 *   IDLE → RINGING → IDLE       ⇒ missed incoming — recorded into
 *                                  [MissedCallRepository]'s own table, not
 *                                  call_records (21 Aug 2026, was previously
 *                                  dropped on the floor entirely — explicit
 *                                  request: "the call which are made and the
 *                                  receiver haven't received the call... that
 *                                  log is not gonna stored").
 *
 * Instances of [PhoneStateReceiver] are short-lived, so all state lives here in
 * a singleton.
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repository: CallRepository,
    private val missedCallRepository: MissedCallRepository,
    private val contactResolver: ContactResolver,
    private val importer: RecordingImporter,
    private val time: TimeProvider,
    private val automationController: AutomationController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var settings: AppSettings = AppSettings()

    private var lastState: CallState = CallState.IDLE
    private val prefs = context.getSharedPreferences("call_state_monitor", Context.MODE_PRIVATE)
    private var sawRingingThisCall: Boolean = false
    private var pendingNumber: String? = null
    private var ringStartTime: Long? = null

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
                if (ringStartTime == null) ringStartTime = time.now()
                if (!incomingNumber.isNullOrBlank()) pendingNumber = incomingNumber
            }

            CallState.OFF_HOOK -> {
           prefs.edit().putBoolean("call_in_progress", true).apply()
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
                // Rang and went straight back to idle — never connected. This
                // check is deliberately on `lastState`, NOT `activeSession`:
                // in OEM-import mode (settings.importFolderUri != null, e.g.
                // OPPO's own recorder) activeSession is null for every call,
                // answered or not, so gating on it would have flagged every
                // OPPO call as missed.
                val wasMissedCall = lastState == CallState.RINGING
                if (activeSession != null) {
                    endSession()
                }
                // When deferring to an OEM recorder, auto-import the new file once
                // the call ends (the recorder needs a moment to finish writing).
                if (settings.importFolderUri != null) {
                    scheduleImport()
                }
                if (wasMissedCall) {
                    recordMissedCall(pendingNumber, ringStartTime ?: time.now())
                }
                // Reset per-call transient state.
                sawRingingThisCall = false
                pendingNumber = null
                ringStartTime = null
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
            Log.d(TAG, "Auto-record disabled for $direction")
            return
        }

        // Native Google Phone recording is started by GooglePhoneShareService.
        // Do NOT start our own microphone-based RecordingService.
        activeSession = CallSession(number, direction, time.now())
        Log.d(TAG, "Native recording mode: session started for $direction")
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
        Log.d(TAG, "Native recording mode: session ended")
    }

    /** RINGING → IDLE with no OFF_HOOK in between — logged to its own table, see MissedCallRecord. */
    private fun recordMissedCall(number: String?, ringStart: Long) {
        scope.launch {
            val name = runCatching { contactResolver.resolveName(number) }.getOrNull()
            missedCallRepository.record(
                phoneNumber = number,
                contactName = name,
                ringStartTime = ringStart
            )
            Log.d(TAG, "Missed call recorded: ${number?.take(3)}…")
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
            delay(1_000L)
            if (!automationController.running) {
                automationController.start()
            }
        }
    }
    private companion object {
        const val TAG = "CallStateMonitor"
    }
}
