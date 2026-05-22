package com.beatthis.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var apiKey by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("—") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // API Key
        Text("Pollinations API Key", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("sk_...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* save + fetch balance */ }) { Text("Save & Check Balance") }

        Spacer(Modifier.height(16.dp))

        // Balance
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pollen Balance")
                Text(balance, style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Model selection
        Text("Models", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        ModelRow("Voice Commands (LLM)", "openai")
        ModelRow("Speech-to-Text", "whisper-large-v3")
        ModelRow("Text-to-Speech", "qwen-tts")
        ModelRow("Music Generation", "acestep")
        ModelRow("Composition", "midijourney")

        Spacer(Modifier.height(24.dp))

        // On-device AI toggles
        Text("On-Device AI (opt-in)", style = MaterialTheme.typography.titleSmall)
        Text("Download models for offline/low-latency use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OnDeviceToggle("STT (Whisper.cpp)", "~150 MB download", false)
        OnDeviceToggle("LLM (Gemma E4B)", "~4 GB download, 6GB+ RAM", false)
        OnDeviceToggle("TTS (Piper)", "~30 MB download", false)
    }
}

@Composable
private fun OnDeviceToggle(label: String, subtitle: String, initialValue: Boolean) {
    var enabled by remember { mutableStateOf(initialValue) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
}

@Composable
private fun ModelRow(label: String, model: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
