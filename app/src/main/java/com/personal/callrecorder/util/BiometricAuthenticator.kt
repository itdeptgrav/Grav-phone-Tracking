package com.personal.callrecorder.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin helper around androidx.biometric. Falls back to device credential (PIN/
 * pattern/password) so enabling the lock can never permanently lock the user out
 * of their own recordings.
 */
object BiometricAuthenticator {

    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Combined biometric + device credential is only supported on API 30+.
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK
        }

    /** Whether any usable authenticator (biometric or credential) is available. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Call Recorder")
            .setAllowedAuthenticators(allowedAuthenticators())

        // A negative button is required only when device credential is NOT allowed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setNegativeButtonText("Cancel")
        }

        runCatching { prompt.authenticate(builder.build()) }
            .onFailure { onFailure(it.message ?: "Cannot show biometric prompt") }
    }
}
