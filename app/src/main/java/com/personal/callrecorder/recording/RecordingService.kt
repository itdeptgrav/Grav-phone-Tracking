package com.personal.callrecorder.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.callrecorder.MainActivity
import com.personal.callrecorder.R
import com.personal.callrecorder.call.CallDirection
import com.personal.callrecorder.call.CallLogResolver
import com.personal.callrecorder.call.CallSession
import com.personal.callrecorder.contacts.ContactResolver
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.data.settings.SettingsRepository
import com.personal.callrecorder.util.TimeProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns a single recording for the duration of a call.
 * It shows the mandatory recording notification, drives [RecorderManager], and
 * persists the resulting [com.personal.callrecorder.data.entity.CallRecord].
 *
 * Started by [com.personal.callrecorder.call.CallStateMonitor] when a call goes
 * off-hook, and stopped when it returns to idle.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var recorderManager: RecorderManager
    @Inject lateinit var repository: CallRepository
    @Inject lateinit var contactResolver: ContactResolver
    @Inject lateinit var callLogResolver: CallLogResolver
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var time: TimeProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recordId: Long? = null
    private var sessionStart: Long = 0L
    private var phoneNumber: String? = null
    private var direction: CallDirection = CallDirection.UNKNOWN
    private var displayName: String = "Call"
    private var foregroundError: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> stopSelf()
        }
        // Do not auto-restart: recording only makes sense while a real call is up.
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (recorderManager.isRecording) {
            Log.w(TAG, "Already recording; ignoring duplicate START")
            return
        }
        phoneNumber = intent.getStringExtra(EXTRA_PHONE)
        direction = runCatching {
            CallDirection.valueOf(intent.getStringExtra(EXTRA_DIRECTION) ?: "")
        }.getOrDefault(CallDirection.UNKNOWN)
        sessionStart = intent.getLongExtra(EXTRA_START_TIME, time.now())
        displayName = phoneNumber ?: "Call"

        // Must move to foreground promptly after startForegroundService(). If the
        // OS refuses (background-start limits on Android 12+), log and bail out
        // cleanly rather than crashing or ANR-ing.
        if (!goForeground()) {
            scope.launch {
                logFailedToStart(
                    foregroundError ?: "Could not start recording foreground service"
                )
                stopSelfSafely()
            }
            return
        }

        scope.launch {
            val contactName = contactResolver.resolveName(phoneNumber)
            if (contactName != null) {
                displayName = contactName
                updateNotification()
            }

            val id = repository.createInProgress(
                phoneNumber = phoneNumber,
                contactName = contactName,
                direction = direction,
                startTime = sessionStart,
                status = RecordingStatus.RECORDING,
                method = null
            )
            recordId = id

            val snapshot = settings.settings.first()
            val session = CallSession(phoneNumber, direction, sessionStart, id)
            val method = recorderManager.start(
                session = session,
                audioSource = snapshot.callAudioSource.source,
                sampleRateHz = snapshot.sampleRateHz,
                bitRate = snapshot.audioQuality.bitRate,
                method = snapshot.recordingMethod
            )

            if (method == null) {
                // No supported strategy (typically microphone permission missing).
                repository.finalizeRecording(
                    id = id,
                    endTime = time.now(),
                    durationMillis = 0,
                    recordingPath = null,
                    status = RecordingStatus.NO_AUDIO,
                    error = "No supported recording method (check microphone permission)"
                )
                stopSelfSafely()
            } else {
                repository.update(
                    repository.getById(id)!!.copy(recordingMethod = method)
                )
            }
        }
    }

    private fun handleStop() {
        scope.launch {
            val id = recordId
            val result = recorderManager.stop()
            val end = time.now()

            if (id != null) {
                reconcileContactIfNeeded(id)
                when (result) {
                    is RecordingResult.Success -> repository.finalizeRecording(
                        id = id,
                        endTime = end,
                        durationMillis = result.durationMillis,
                        recordingPath = result.path,
                        status = RecordingStatus.COMPLETED
                    )
                    is RecordingResult.NoAudio -> repository.finalizeRecording(
                        id = id,
                        endTime = end,
                        durationMillis = (end - sessionStart).coerceAtLeast(0),
                        recordingPath = null,
                        status = RecordingStatus.NO_AUDIO,
                        error = result.reason
                    )
                    is RecordingResult.Failure -> repository.finalizeRecording(
                        id = id,
                        endTime = end,
                        durationMillis = (end - sessionStart).coerceAtLeast(0),
                        recordingPath = null,
                        status = RecordingStatus.FAILED,
                        error = result.reason
                    )
                }
            }
            stopSelfSafely()
        }
    }

    /**
     * If the number was withheld during the call, try to recover it from the
     * call log now that the call has ended. Best-effort and null-safe.
     */
    private suspend fun reconcileContactIfNeeded(id: Long) {
        if (!phoneNumber.isNullOrBlank()) return
        val entry = callLogResolver.mostRecent() ?: return
        val number = entry.number ?: return
        val name = contactResolver.resolveName(number)
        repository.updateContact(id, number, name)
    }

    private suspend fun logFailedToStart(reason: String) {
        val id = recordId ?: repository.createInProgress(
            phoneNumber = phoneNumber,
            contactName = contactResolver.resolveName(phoneNumber),
            direction = direction,
            startTime = sessionStart,
            status = RecordingStatus.FAILED,
            method = null
        )
        repository.finalizeRecording(
            id = id,
            endTime = time.now(),
            durationMillis = 0,
            recordingPath = null,
            status = RecordingStatus.FAILED,
            error = reason
        )
    }

    private fun goForeground(): Boolean = try {
        foregroundError = null
        ensureChannel()
        val notification = buildNotification()
        // Legacy (untyped) startForeground. Because the app targets SDK 33, the
        // Android 14+ typed-FGS "while-in-use eligibility" check does not apply,
        // so a background-triggered mic recording is permitted (with the overlay
        // exemption covering the background start). Do NOT switch this to the
        // 3-arg typed call unless targetSdk goes back to 34+.
        startForeground(NOTIFICATION_ID, notification)
        true
    } catch (t: Throwable) {
        Log.e(TAG, "startForeground failed", t)
        foregroundError = "startForeground: ${t.javaClass.simpleName}: ${t.message}"
        false
    }

    private fun updateNotification() {
        // Safe to call any time after goForeground(): we're already foregrounded.
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(displayName)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(sessionStart)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.recording_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopSelfSafely() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "call_recording"
        const val NOTIFICATION_ID = 4201

        const val ACTION_START = "com.personal.callrecorder.action.START"
        const val ACTION_STOP = "com.personal.callrecorder.action.STOP"

        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_DIRECTION = "extra_direction"
        const val EXTRA_START_TIME = "extra_start_time"

        fun startIntent(context: Context, session: CallSession): Intent =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PHONE, session.phoneNumber)
                putExtra(EXTRA_DIRECTION, session.direction.name)
                putExtra(EXTRA_START_TIME, session.startTime)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
    }
}
