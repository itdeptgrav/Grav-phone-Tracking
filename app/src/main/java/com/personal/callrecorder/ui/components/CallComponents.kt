package com.personal.callrecorder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.callrecorder.call.CallDirection
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.entity.ProcessingStatus
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.util.Formatters

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DirectionAvatar(direction: CallDirection) {
    val icon: ImageVector = when (direction) {
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.UNKNOWN -> Icons.Filled.Call
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = direction.label(),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallListItem(
    record: CallRecord,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DirectionAvatar(record.direction)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(record.direction.label())
                        if (record.durationMillis > 0) {
                            append(" • ")
                            append(Formatters.duration(record.durationMillis))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusRow(record)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.clockTime(record.startTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.hasRecording && onPlay != null) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(record: CallRecord) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (record.recordingStatus) {
            RecordingStatus.COMPLETED ->
                StatusChip("Recorded", MaterialTheme.colorScheme.primary)
            RecordingStatus.FAILED ->
                StatusChip("Failed", MaterialTheme.colorScheme.error)
            RecordingStatus.NO_AUDIO ->
                StatusChip("No audio", MaterialTheme.colorScheme.onSurfaceVariant)
            RecordingStatus.RECORDING ->
                StatusChip("Recording…", MaterialTheme.colorScheme.error)
        }
        if (record.transcriptionStatus == ProcessingStatus.COMPLETED) {
            StatusChip("Transcript", MaterialTheme.colorScheme.tertiary)
        }
        if (record.summaryStatus == ProcessingStatus.COMPLETED) {
            StatusChip("Summary", MaterialTheme.colorScheme.secondary)
        }
    }
}
