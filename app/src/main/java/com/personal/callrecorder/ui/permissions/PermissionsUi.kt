package com.personal.callrecorder.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** A permission the app can ask for, with a plain-language reason. */
data class PermissionInfo(
    val permission: String,
    val title: String,
    val rationale: String
)

/** Required for the core recording flow to function at all. */
fun requiredPermissions(): List<PermissionInfo> = buildList {
    add(PermissionInfo(Manifest.permission.RECORD_AUDIO, "Microphone", "Needed to record call audio."))
    add(PermissionInfo(Manifest.permission.READ_PHONE_STATE, "Phone state", "Needed to detect when a call starts and ends."))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "Needed to show the mandatory recording notification."))
    }
}

/** Optional: improve labelling. The app works without them. */
fun optionalPermissions(): List<PermissionInfo> = listOf(
    PermissionInfo(Manifest.permission.READ_CONTACTS, "Contacts", "Show a caller's saved name instead of just the number."),
    PermissionInfo(Manifest.permission.READ_CALL_LOG, "Call log", "Recover the phone number when the system withholds it during the call.")
)

/**
 * A dismissible card that appears when core (or, secondarily, optional)
 * permissions are missing. Explains why before launching the system dialog.
 */
@Composable
fun PermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val missingRequired = remember(refresh) { requiredPermissions().filterNot { granted(it.permission) } }
    val missingOptional = remember(refresh) { optionalPermissions().filterNot { granted(it.permission) } }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    when {
        missingRequired.isNotEmpty() -> PermissionCard(
            title = "Permissions needed",
            body = missingRequired.joinToString("\n") { "• ${it.title}: ${it.rationale}" },
            buttonText = "Grant permissions",
            emphasize = true,
            onClick = { launcher.launch(missingRequired.map { it.permission }.toTypedArray()) },
            modifier = modifier
        )
        missingOptional.isNotEmpty() -> PermissionCard(
            title = "Optional permissions",
            body = missingOptional.joinToString("\n") { "• ${it.title}: ${it.rationale}" },
            buttonText = "Grant",
            emphasize = false,
            onClick = { launcher.launch(missingOptional.map { it.permission }.toTypedArray()) },
            modifier = modifier
        )
    }
}

/**
 * Card prompting the user to grant "Display over other apps". On Android 12+
 * this is what exempts the app from the background foreground-service-start
 * restriction, so recording can begin automatically when a call starts.
 */
@Composable
fun OverlayPermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val granted = remember(refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    if (granted) return

    PermissionCard(
        title = "Enable automatic recording",
        body = "Android blocks apps from starting call recording from the background. " +
            "Granting \"Display over other apps\" lets this app legitimately start " +
            "recording the moment a call begins. Without it, calls will show as Failed.",
        buttonText = "Open setting",
        emphasize = true,
        onClick = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            launcher.launch(intent)
        },
        modifier = modifier
    )
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    buttonText: String,
    emphasize: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasize) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (emphasize) {
                Button(onClick = onClick, modifier = Modifier.padding(top = 10.dp)) { Text(buttonText) }
            } else {
                TextButton(onClick = onClick, modifier = Modifier.padding(top = 6.dp)) { Text(buttonText) }
            }
        }
    }
}
