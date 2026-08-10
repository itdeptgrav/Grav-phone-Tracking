package com.personal.callrecorder.automation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brain of the zero-touch share automation. Holds the state machine + a rolling
 * log (surfaced in Settings → import diagnostics). The [GooglePhoneShareService]
 * reads this state and performs the actual UI clicks; this class never touches
 * the UI itself.
 *
 * Everything it does is user-visible UI automation of Google Phone's own Share
 * flow — no audio capture, no private storage, no permission changes.
 */
@Singleton
class AutomationController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Step { IDLE, LAUNCHING, IN_DIALER, IN_RESOLVER, DONE, FAILED }

    @Volatile var step: Step = Step.IDLE
        private set
    @Volatile var attempts: Int = 0
    @Volatile private var startedAt: Long = 0L
    @Volatile private var lastActionAt: Long = 0L

    val running: Boolean
        get() = step == Step.LAUNCHING || step == Step.IN_DIALER || step == Step.IN_RESOLVER

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    /** Kick off: launch Google Phone; the service takes over from window events. */
    fun start() {
        if (running) {
            log("Already running — ignoring new trigger")
            return
        }
        if (!isServiceEnabled(context)) {
            log("Accessibility service not enabled — cannot automate")
            return
        }
        step = Step.LAUNCHING
        startedAt = now()
        lastActionAt = 0L
        attempts = 0
        log("Started — launching Google Phone")

        val intent = context.packageManager.getLaunchIntentForPackage(DIALER_PKG)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (intent == null) {
            fail("Google Phone not installed")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { fail("Cannot launch dialer: ${it.message}") }
    }

    fun markInDialer() { if (running) step = Step.IN_DIALER }
    fun markInResolver() { if (running) { step = Step.IN_RESOLVER } }
    fun succeed() { step = Step.DONE; log("Selected Call Recorder — importing") }
    fun fail(reason: String) { step = Step.FAILED; log("Failed: $reason") }

    fun timedOut(): Boolean = running && now() - startedAt > TIMEOUT_MS

    /** Debounce: accessibility events fire in bursts; act at most every 700ms. */
    fun readyToAct(): Boolean = now() - lastActionAt > 700
    fun actionTaken() { lastActionAt = now() }

    fun log(msg: String) {
        _log.update { (it + "${stamp()} $msg").takeLast(60) }
        Log.d(TAG, msg)
    }

    private fun now() = System.currentTimeMillis()
    private fun stamp() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    companion object {
        const val TAG = "AutomationController"
        const val DIALER_PKG = "com.google.android.dialer"
        const val RESOLVER_PKG = "com.android.intentresolver"
        const val SERVICE_ID = "com.personal.callrecorder/com.personal.callrecorder.automation.GooglePhoneShareService"
        const val TIMEOUT_MS = 20_000L

        /** True if the user has enabled our AccessibilityService in system Settings. */
        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(SERVICE_ID, ignoreCase = true)) return true
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
