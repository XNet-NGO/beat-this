package com.beatthis.ui.generate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beatthis.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun GenerateScreen(vm: MainViewModel) {
    var musicPrompt by remember { mutableStateOf("") }
    var lyricsText by remember { mutableStateOf("") }
    val isGenerating by vm.isGenerating.collectAsState()
    val status by vm.status.collectAsState()
    val stems by vm.stems.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Status
        if (status.isNotBlank()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isGenerating) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Playback controls
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.pausePlayback() }) { Text("⏸") }
                FilledTonalButton(onClick = { vm.resumePlayback() }) { Text("▶") }
                FilledTonalButton(onClick = { vm.stopPlayback() }) { Text("⏹") }
            }
        }

        // Music generation
        item {
            Text("🎵 Generate Instrumental", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = musicPrompt,
                onValueChange = { musicPrompt = it },
                placeholder = { Text("dark trap beat 140 bpm 808 bass") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.generateMusic(musicPrompt) },
                enabled = musicPrompt.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGenerating) "Generating (~20s)..." else "Generate Instrumental")
            }
        }

        // Vocal generation
        item {
            Spacer(Modifier.height(8.dp))
            Text("🎤 Generate Vocals (ACE-Step)", style = MaterialTheme.typography.titleMedium)
            Text("Write lyrics with [verse], [chorus] tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = lyricsText,
                onValueChange = { lyricsText = it },
                placeholder = { Text("[verse]\nI walk alone tonight\nunder city lights\n[chorus]\noh yeah oh yeah") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.generateVocals(lyricsText) },
                enabled = lyricsText.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGenerating) "Generating (~20s)..." else "Generate Vocals")
            }
        }

        // Stems library
        if (stems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("📁 Stems Library", style = MaterialTheme.typography.titleMedium)
            }
            items(stems) { file ->
                StemCard(file, vm)
            }
        }
    }
}

@Composable
private fun StemCard(file: File, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.playStem(file) }) {
                Icon(Icons.Default.PlayArrow, "Play")
            }
            IconButton(onClick = { vm.deleteStem(file) }) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
