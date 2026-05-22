package com.beatthis.ui.generate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GenerateScreen() {
    var musicPrompt by remember { mutableStateOf("") }
    var vocalText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Generate", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Music generation
        Text("Music", style = MaterialTheme.typography.titleMedium)
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
            onClick = { isGenerating = true },
            enabled = musicPrompt.isNotBlank() && !isGenerating
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Music")
        }

        Spacer(Modifier.height(24.dp))

        // Vocal generation
        Text("Vocals", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vocalText,
            onValueChange = { vocalText = it },
            label = { Text("Lyrics or text to vocalize...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { isGenerating = true },
            enabled = vocalText.isNotBlank() && !isGenerating
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Vocals")
        }
    }
}
