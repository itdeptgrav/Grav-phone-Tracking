package com.personal.callrecorder.ui.diagnostics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.callrecorder.ui.player.AudioPlayerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTestScreen(
    onBack: () -> Unit,
    viewModel: AudioTestViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val player = remember { AudioPlayerController(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio source test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "How to use: start a call, put it on the settings you want, then run each " +
                        "source for 6s while BOTH people talk. The bar shows if the source captured " +
                        "any sound; Play lets you hear whose voice it got. Run this with the app in " +
                        "the foreground.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rows, key = { it.source.name }) { row ->
                SourceRow(
                    row = row,
                    busy = busy,
                    onTest = { viewModel.test(row.source) },
                    onPlay = { path -> player.setFile(path); player.play() }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    row: AudioTestViewModel.Row,
    busy: Boolean,
    onTest: () -> Unit,
    onPlay: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.source.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            val path = row.result?.path
            if (path != null) {
                IconButton(onClick = { onPlay(path) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
            }
            OutlinedButton(onClick = onTest, enabled = !busy) {
                if (row.running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Test 6s")
                }
            }
        }

        val result = row.result
        val peak = result?.peakAmplitude ?: 0
        val fraction = (peak / 32767f).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )

        // Waveform of the amplitude samples: flat line = silence, spikes = real audio.
        val samples = result?.samples.orEmpty()
        if (samples.isNotEmpty()) {
            val waveColor = if (result?.capturedSomething == true) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Waveform(
                samples = samples,
                color = waveColor,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
        }
        Text(
            text = when {
                result == null -> "Not tested yet"
                result.error != null -> "✕ ${result.error}"
                result.capturedSomething -> "✓ Captured audio — peak $peak (play to hear whose voice)"
                else -> "✕ Silent — peak $peak (no audio from this source)"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                result?.error != null -> MaterialTheme.colorScheme.error
                result?.capturedSomething == true -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun Waveform(samples: List<Int>, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val ref = 8000f // amplitude that fills full height (typical near-phone speech)
        val n = samples.size
        val barW = size.width / n
        val midY = size.height / 2f
        samples.forEachIndexed { i, s ->
            val h = (s / ref).coerceIn(0f, 1f) * (size.height / 2f)
            val x = i * barW + barW / 2f
            drawLine(
                color = color,
                start = Offset(x, midY - h),
                end = Offset(x, midY + h),
                strokeWidth = (barW * 0.6f).coerceAtLeast(2f)
            )
        }
    }
}
