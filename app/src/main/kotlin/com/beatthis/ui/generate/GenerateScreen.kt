package com.beatthis.ui.generate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beatthis.ui.viewmodel.MainViewModel

@Composable
fun GenerateScreen(vm: MainViewModel) {
    var musicPrompt by remember { mutableStateOf("") }
    var vocalText by remember { mutableStateOf("") }
    val isGenerating by vm.isGenerating.collectAsState()
    val status by vm.status.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Generate", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        // Status card
        if (status.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (isGenerating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Music generation
        Text("Music (ACE-Step)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = musicPrompt,
            onValueChange = { musicPrompt = it },
            label = { Text("Describe the music...") },
            placeholder = { Text("chill lo-fi beat, 85 bpm, rainy mood") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.generateMusic(musicPrompt) },
            enabled = musicPrompt.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isGenerating) "Generating..." else "🎵 Generate Music")
        }

        Spacer(Modifier.height(24.dp))

        // Vocal generation
        Text("Vocals (Qwen TTS)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = vocalText,
            onValueChange = { vocalText = it },
            label = { Text("Text to speak/sing...") },
            placeholder = { Text("Hello world, this is Beat This") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.generateVocals(vocalText) },
            enabled = vocalText.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isGenerating) "Generating..." else "🎤 Generate Vocals")
        }
    }
}
