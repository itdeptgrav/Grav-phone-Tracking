package com.personal.callrecorder.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Receives the PHONE_STATE broadcast — still an allowed implicit broadcast — and
 * forwards normalised transitions to [CallStateMonitor].
 *
 * We use a Hilt [EntryPoint] rather than @AndroidEntryPoint field injection:
 * BroadcastReceiver.onReceive is abstract, so the usual "call super.onReceive()
 * to trigger injection" pattern does not compile in Kotlin. Resolving the
 * singleton monitor from the application graph is equivalent and clean.
 *
 * Note on the number extra: EXTRA_INCOMING_NUMBER is populated for incoming
 * calls only when READ_CALL_LOG (or the phone role) permits it, and is not
 * provided at all for outgoing calls on modern Android. The monitor and service
 * reconcile against the call log afterwards to recover it where possible.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PhoneStateReceiverEntryPoint {
        fun callStateMonitor(): CallStateMonitor
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val monitor = EntryPointAccessors
            .fromApplication(context.applicationContext, PhoneStateReceiverEntryPoint::class.java)
            .callStateMonitor()

        val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        monitor.onCallStateChanged(CallState.fromExtra(stateExtra), number)
    }
}
