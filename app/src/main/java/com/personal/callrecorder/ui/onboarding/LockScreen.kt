package com.personal.callrecorder.ui.onboarding

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.personal.callrecorder.util.BiometricAuthenticator

/**
 * Blocks access until the user authenticates. If the device has no biometric or
 * credential enrolled, it cannot lock the user out — it unlocks automatically.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()

    fun tryAuth() {
        if (activity == null || !BiometricAuthenticator.canAuthenticate(context)) {
            // Nothing to authenticate against — do not trap the user.
            onUnlocked()
            return
        }
        BiometricAuthenticator.authenticate(
            activity = activity,
            onSuccess = onUnlocked,
            onFailure = { /* stay locked; user can retry */ }
        )
    }

    LaunchedEffect(Unit) { tryAuth() }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Locked", style = MaterialTheme.typography.titleLarge)
        Text(
            "Authenticate to view your recordings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Button(onClick = { tryAuth() }) { Text("Unlock") }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
