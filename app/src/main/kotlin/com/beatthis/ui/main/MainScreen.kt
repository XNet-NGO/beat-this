package com.beatthis.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Beat This", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Voice-Controlled AI DAW", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.weight(1f))

        // Transport bar
        TransportBar()

        Spacer(Modifier.height(24.dp))

        // Voice command button
        VoiceCommandButton()

        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun TransportBar() {
    var isPlaying by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { /* rewind */ }) {
            Icon(Icons.Default.SkipPrevious, "Rewind")
        }
        FilledIconButton(
            onClick = { isPlaying = !isPlaying },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, "Play/Stop")
        }
        FilledIconButton(
            onClick = { isRecording = !isRecording },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(Icons.Default.FiberManualRecord, "Record")
        }
    }
}

@Composable
fun VoiceCommandButton() {
    var isListening by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = { isListening = !isListening },
            containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(Icons.Default.Mic, "Voice Command", modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (isListening) "Listening..." else "Tap to speak",
            style = MaterialTheme.typography.bodySmall
        )
        if (lastResult.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(lastResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
