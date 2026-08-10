package com.personal.callrecorder.capability

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes [DeviceCapabilities] at runtime. Deliberately conservative: it never
 * claims a capability it cannot verify (esp. direct call-audio capture, which is
 * reported false on any normal non-rooted device).
 */
@Singleton
class CapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun detect(): DeviceCapabilities {
        val hasMic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        return DeviceCapabilities(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidRelease = "Android ${Build.VERSION.RELEASE}",
            apiLevel = Build.VERSION.SDK_INT,
            callDetectionSupported = hasTelephony && granted(Manifest.permission.READ_PHONE_STATE),
            microphoneRecordingSupported = hasMic && granted(Manifest.permission.RECORD_AUDIO),
            // Not possible for third-party apps through standard APIs. Honest false.
            directCallAudioSupported = false,
            speakerphoneRecordingAvailable = hasMic,
            oemIntegrationAvailable = false,
            rootDetected = isRootedBestEffort(),
            recordAudioGranted = granted(Manifest.permission.RECORD_AUDIO),
            phoneStateGranted = granted(Manifest.permission.READ_PHONE_STATE),
            overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)
        )
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Best-effort root check via common su binary locations. Never definitive. */
    private fun isRootedBestEffort(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/app/Superuser.apk", "/su/bin/su"
        )
        return runCatching { paths.any { File(it).exists() } }.getOrDefault(false)
    }
}
