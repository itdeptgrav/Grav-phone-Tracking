package com.personal.callrecorder.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.callrecorder.capability.DeviceCapabilities

private enum class Cap { YES, NO, UNKNOWN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenAudioTest: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording capability") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            InfoLine("Device", caps.deviceModel)
            InfoLine("Android", "${caps.androidRelease} (API ${caps.apiLevel})")
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            CapLine("Call detection", caps.callDetectionSupported.toCap())
            CapLine("Microphone recording", caps.microphoneRecordingSupported.toCap())
            CapLine("Speakerphone recording", caps.speakerphoneRecordingAvailable.toCap())
            CapLine(
                "Direct internal call audio",
                Cap.NO,
                note = "Not available to third-party apps through standard Android APIs."
            )
            CapLine("OEM recording integration", if (caps.oemIntegrationAvailable) Cap.YES else Cap.UNKNOWN)
            CapLine("Root access", if (caps.rootDetected) Cap.YES else Cap.NO)

            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            CapLine("Microphone permission granted", caps.recordAudioGranted.toCap())
            CapLine("Phone state permission granted", caps.phoneStateGranted.toCap())
            CapLine(
                "Auto-record enabled (\"Display over other apps\")",
                caps.overlayGranted.toCap(),
                note = if (caps.overlayGranted) null
                else "Grant this from the home screen so recording can start automatically on Android 12+."
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Button(
                onClick = onOpenAudioTest,
                modifier = Modifier.padding(vertical = 4.dp)
            ) { Text("Test audio sources on this device") }
            Text(
                "Run the audio-source test during a call to find which source (MIC, Voice recognition, Voice communication…) captures your voice, the other person, or both on this exact phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CapLine(label: String, cap: Cap, note: String? = null) {
    val (icon, tint) = when (cap) {
        Cap.YES -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        Cap.NO -> Icons.Filled.Cancel to MaterialTheme.colorScheme.error
        Cap.UNKNOWN -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(icon, contentDescription = cap.name, tint = tint)
    }
}

private fun Boolean.toCap(): Cap = if (this) Cap.YES else Cap.NO
