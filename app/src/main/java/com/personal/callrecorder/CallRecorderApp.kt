package com.personal.callrecorder

import android.app.Application
import com.personal.callrecorder.call.CallStateMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CallRecorderApp : Application() {

    @Inject lateinit var callStateMonitor: CallStateMonitor

    override fun onCreate() {
        super.onCreate()
        // Begin observing settings so the (process-wide) call-state machine has a
        // fresh snapshot when a PHONE_STATE broadcast arrives.
        callStateMonitor.start()
    }
}
