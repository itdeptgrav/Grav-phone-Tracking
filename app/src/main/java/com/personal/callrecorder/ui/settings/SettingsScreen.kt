package com.personal.callrecorder.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.callrecorder.data.settings.RecordingMethod
import com.personal.callrecorder.recording.AudioQuality
import com.personal.callrecorder.recording.CallAudioSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storageText.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Folder picker for importing the phone's own (OEM) call recordings.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setImportFolder(uri.toString())
        }
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("Recording")
            SwitchRow("Automatically record calls", settings.autoRecord, viewModel::setAutoRecord)
            SwitchRow("Record incoming calls", settings.recordIncoming, viewModel::setRecordIncoming, enabled = settings.autoRecord)
            SwitchRow("Record outgoing calls", settings.recordOutgoing, viewModel::setRecordOutgoing, enabled = settings.autoRecord)

            ChoiceRow(
                title = "Recording method",
                options = RecordingMethod.entries.map { it to methodLabel(it) },
                selected = settings.recordingMethod,
                onSelect = viewModel::setMethod
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Audio")
            Text(
                "If a call records silent, try a different audio source. If it records " +
                    "but sounds distorted/too fast/too slow, change the sample rate " +
                    "(8000 Hz suits many phone calls). Best combo is device-specific.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            ChoiceRow(
                title = "Call audio source",
                options = CallAudioSource.entries.map { it to it.label },
                selected = settings.callAudioSource,
                onSelect = viewModel::setCallAudioSource
            )
            ChoiceRow(
                title = "Sample rate",
                options = listOf(8000 to "8 kHz", 16000 to "16 kHz", 44100 to "44.1 kHz"),
                selected = settings.sampleRateHz,
                onSelect = viewModel::setSampleRate
            )
            ChoiceRow(
                title = "Recording quality",
                options = AudioQuality.entries.map { it to it.label },
                selected = settings.audioQuality,
                onSelect = viewModel::setQuality
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Storage")
            InfoRow("Storage used", storage)
            InfoRow("Recording location", "App-private internal storage")
            ChoiceRow(
                title = "Delete recordings older than",
                options = listOf(0 to "Never", 30 to "30 days", 90 to "90 days", 365 to "1 year"),
                selected = settings.deleteOlderThanDays,
                onSelect = viewModel::setDeleteOlderThanDays
            )
            OutlinedButton(
                onClick = viewModel::purgeOldNow,
                modifier = Modifier.padding(vertical = 4.dp)
            ) { Text("Apply retention now") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Import from phone recorder")
            Text(
                "If your phone's own dialer records calls (e.g. OPPO), point this at " +
                    "its recordings folder. Those recordings are imported here and made " +
                    "playable, searchable, and ready for transcription + AI summary.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            InfoRow("Import folder", if (settings.importFolderUri != null) "Selected" else "Not set")
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Text(if (settings.importFolderUri == null) "Choose folder" else "Change folder")
                }
                if (settings.importFolderUri != null) {
                    Button(onClick = viewModel::importNow) { Text("Import now") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("AI")
            if (!viewModel.transcriptionAvailable) {
                Text(
                    "Transcription and summaries are not configured. See the README to connect Whisper and your backend (→ Ollama → Qwen).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            SwitchRow(
                "Transcription",
                settings.transcriptionEnabled,
                viewModel::setTranscription,
                enabled = viewModel.transcriptionAvailable
            )
            SwitchRow(
                "Automatic summaries",
                settings.autoSummaries,
                viewModel::setAutoSummaries,
                enabled = viewModel.aiAvailable
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Privacy")
            SwitchRow("Require biometric unlock", settings.biometricLock, viewModel::setBiometricLock)
            Text(
                "Recordings never leave this device unless you explicitly configure and enable a provider.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

private fun methodLabel(method: RecordingMethod): String = when (method) {
    RecordingMethod.AUTOMATIC -> "Automatic"
    RecordingMethod.MICROPHONE -> "Microphone"
}
