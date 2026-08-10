package com.personal.callrecorder.ui.callDetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.callrecorder.ai.CallSummary
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.ui.player.AudioPlayerController
import com.personal.callrecorder.util.Formatters
import com.personal.callrecorder.util.WaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    onBack: () -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Call details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val sharePath = record?.takeIf { it.hasRecording }?.recordingPath
                    if (sharePath != null) {
                        IconButton(onClick = { shareRecording(context, sharePath) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export / Share")
                        }
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        val current = record
        if (current == null) {
            Column(
                Modifier.fillMaxWidth().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text("Call not found") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(current)
            HorizontalDivider()
            AudioSection(current)
            HorizontalDivider()
            SummarySection(current, viewModel, busy)
            HorizontalDivider()
            TranscriptSection(current, viewModel, busy)
            HorizontalDivider()
            NotesSection(current, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete call?") },
            text = { Text("This permanently deletes the recording and its data.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Header(record: CallRecord) {
    Column {
        Text(record.displayName, style = MaterialTheme.typography.titleLarge)
        if (record.contactName != null && !record.phoneNumber.isNullOrBlank()) {
            Text(
                record.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${Formatters.fullDate(record.startTime)} • ${Formatters.clockTime(record.startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${record.direction.label()} • Duration: ${Formatters.duration(record.durationMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AudioSection(record: CallRecord) {
    SectionTitle("AUDIO")
    if (!record.hasRecording) {
        val reason = when (record.recordingStatus) {
            RecordingStatus.FAILED -> record.recordingError ?: "Recording failed"
            RecordingStatus.NO_AUDIO -> record.recordingError ?: "No audio was captured"
            RecordingStatus.RECORDING -> "Recording in progress…"
            else -> "No recording available"
        }
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val context = LocalContext.current
    val controller = remember { AudioPlayerController(context) }
    val playerState by controller.state.collectAsStateWithLifecycle()

    DisposableEffect(record.recordingPath) {
        controller.setFile(record.recordingPath)
        onDispose { controller.release() }
    }

    // Decode the file into a static waveform (once), off the main thread.
    var waveform by remember(record.recordingPath) { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(record.recordingPath) {
        val path = record.recordingPath
        if (path != null) {
            waveform = withContext(Dispatchers.IO) { WaveformExtractor.extract(path) }
        }
    }

    // Keep the scrubber in sync while playing.
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            controller.refreshPosition()
            delay(500)
        }
    }

    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = playerState.durationMs.coerceAtLeast(0)
    val position = playerState.positionMs.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)

    if (playerState.error != null) {
        Text(
            playerState.error!!,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { controller.skip(-10_000) }) {
            Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(32.dp))
        }
        FilledIconButton(onClick = { controller.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(onClick = { controller.skip(10_000) }) {
            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", modifier = Modifier.size(32.dp))
        }
    }

    val wave = waveform
    if (wave != null && wave.isNotEmpty()) {
        val progress = if (duration > 0) (position.toFloat() / duration) else 0f
        RecordingWaveform(
            samples = wave,
            progress = progress,
            playedColor = MaterialTheme.colorScheme.primary,
            restColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp)
        )
    }

    Slider(
        value = scrub ?: position.toFloat(),
        onValueChange = { scrub = it },
        onValueChangeFinished = {
            scrub?.let { controller.seekTo(it.toLong()) }
            scrub = null
        },
        valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
        modifier = Modifier.fillMaxWidth()
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Formatters.clockPosition((scrub?.toLong() ?: position)), style = MaterialTheme.typography.labelMedium)
        Text(Formatters.clockPosition(duration), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SummarySection(
    record: CallRecord,
    viewModel: CallDetailViewModel,
    busy: Boolean
) {
    SectionTitle("AI SUMMARY")
    val parsed = CallSummary.fromJsonOrNull(record.summary)
    when {
        parsed != null -> SummaryContent(parsed)
        !record.summary.isNullOrBlank() -> Text(record.summary!!, style = MaterialTheme.typography.bodyMedium)
        else -> {
            Text(
                if (viewModel.aiEnabled) "No summary yet."
                else "AI summaries are not configured. See Settings and the README to connect your backend (→ Ollama → Qwen).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (viewModel.aiEnabled) {
                OutlinedButton(
                    onClick = { viewModel.summarize() },
                    enabled = !busy && !record.transcription.isNullOrBlank()
                ) { Text("Summarize") }
            }
        }
    }
}

@Composable
private fun SummaryContent(summary: CallSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (summary.summary.isNotBlank()) {
            Text(summary.summary, style = MaterialTheme.typography.bodyMedium)
        }
        BulletBlock("Important points", summary.importantPoints)
        BulletBlock("Action items", summary.actionItems)
        BulletBlock("People", summary.peopleMentioned)
        BulletBlock("Amounts", summary.amountsMentioned)
        summary.followUpDate?.let {
            Text("Follow-up: $it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BulletBlock(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun TranscriptSection(
    record: CallRecord,
    viewModel: CallDetailViewModel,
    busy: Boolean
) {
    SectionTitle("TRANSCRIPT")
    when {
        !record.transcription.isNullOrBlank() ->
            Text(record.transcription!!, style = MaterialTheme.typography.bodyMedium)
        else -> {
            Text(
                if (viewModel.transcriptionEnabled) "Not transcribed yet."
                else "Transcription is not configured. See the README to connect local Whisper, a Whisper API, or your backend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (viewModel.transcriptionEnabled) {
                OutlinedButton(
                    onClick = { viewModel.transcribe() },
                    enabled = !busy && record.hasRecording
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Transcribe")
                }
            }
        }
    }
}

@Composable
private fun NotesSection(
    record: CallRecord,
    viewModel: CallDetailViewModel
) {
    SectionTitle("NOTES")
    var text by remember(record.id) { mutableStateOf(record.notes ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Add a note…") },
        minLines = 2
    )
    Button(
        onClick = { viewModel.saveNotes(text) },
        modifier = Modifier.padding(top = 8.dp)
    ) { Text("Save note") }
}

/** Export a recording via the Android share sheet (Drive, email, WhatsApp, etc.). */
private fun shareRecording(context: Context, path: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path)
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share recording"))
    }
}

/** Static waveform of the recording; the played portion is highlighted. */
@Composable
private fun RecordingWaveform(
    samples: FloatArray,
    progress: Float,
    playedColor: Color,
    restColor: Color,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val n = samples.size
        val barW = size.width / n
        val midY = size.height / 2f
        val playedUntil = progress.coerceIn(0f, 1f) * n
        samples.forEachIndexed { i, s ->
            // Amplify a touch so quiet speech is still visible; silence stays flat.
            val h = (s * 1.6f).coerceIn(0f, 1f) * (size.height / 2f)
            val x = i * barW + barW / 2f
            val c = if (i <= playedUntil) playedColor else restColor
            drawLine(
                color = c,
                start = Offset(x, midY - h),
                end = Offset(x, midY + h),
                strokeWidth = (barW * 0.6f).coerceAtLeast(2f)
            )
        }
    }
}
