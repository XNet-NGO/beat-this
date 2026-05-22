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
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

        // Music generation
        Text("Music (ACE-Step)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = musicPrompt,
            onValueChange = { musicPrompt = it },
            label = { Text("Describe the music...") },
            placeholder = { Text("e.g. chill lo-fi beat, 85 bpm, rainy mood") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.generateMusic(musicPrompt) },
            enabled = musicPrompt.isNotBlank() && !isGenerating
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Music")
        }

        Spacer(Modifier.height(24.dp))

        // Vocal generation
        Text("Vocals (TTS)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vocalText,
            onValueChange = { vocalText = it },
            label = { Text("Lyrics or text to vocalize...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.generateVocals(vocalText) },
            enabled = vocalText.isNotBlank() && !isGenerating
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Vocals")
        }
    }
}
