package com.personal.callrecorder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.ui.components.CallListItem
import com.personal.callrecorder.ui.permissions.OverlayPermissionCard
import com.personal.callrecorder.ui.permissions.PermissionBanner
import com.personal.callrecorder.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCall: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call recordings") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.Info, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PermissionBanner(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OverlayPermissionCard(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
            if (calls.isEmpty()) {
                EmptyState(Modifier.fillMaxSize().weight(1f))
            } else {
                CallList(
                    calls = calls,
                    onOpenCall = onOpenCall,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CallList(
    calls: List<CallRecord>,
    onOpenCall: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Group consecutive records under a day header ("Today", "Yesterday", date).
    val grouped = remember(calls) { groupByDay(calls) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (day, records) ->
            item(key = "header_$day") {
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(records, key = { it.id }) { record ->
                CallListItem(
                    record = record,
                    onClick = { onOpenCall(record.id) },
                    onPlay = { onOpenCall(record.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun groupByDay(calls: List<CallRecord>): List<Pair<String, List<CallRecord>>> {
    return calls
        .groupBy { Formatters.dayBucket(it.startTime) }
        .toList()
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "No calls yet",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Recordings appear here. If your phone records calls itself (e.g. OPPO), " +
                    "open Settings → \"Import from phone recorder\" and pick its recordings folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
