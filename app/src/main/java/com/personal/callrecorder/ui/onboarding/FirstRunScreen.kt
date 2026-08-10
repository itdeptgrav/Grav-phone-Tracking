package com.personal.callrecorder.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-run legal/consent notice. Deliberately jurisdiction-neutral — it states
 * that consent laws vary and the responsibility is the user's, without encoding
 * any location-specific assumptions.
 */
@Composable
fun FirstRunScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Call Recording",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Call-recording laws and consent requirements vary by location.\n\n" +
                "You are responsible for ensuring that you have permission to record " +
                "conversations where required.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        Button(onClick = onAccept) {
            Text("I Understand")
        }
    }
}
