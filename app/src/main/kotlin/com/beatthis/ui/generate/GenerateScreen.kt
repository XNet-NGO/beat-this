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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beatthis.audio.Stem
import com.beatthis.audio.StemType
import com.beatthis.ui.viewmodel.MainViewModel

@Composable
fun GenerateScreen(vm: MainViewModel) {
    val isGenerating by vm.isGenerating.collectAsState()
    val status by vm.status.collectAsState()
    val stems by vm.stems.collectAsState()
    val playingId by vm.playingId.collectAsState()

    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Status bar
        if (status.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isGenerating) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }

        // Tabs
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }) { Text("Instrumental", Modifier.padding(12.dp)) }
            Tab(tab == 1, onClick = { tab = 1 }) { Text("Vocals", Modifier.padding(12.dp)) }
            Tab(tab == 2, onClick = { tab = 2 }) { Text("Library", Modifier.padding(12.dp)) }
        }

        when (tab) {
            0 -> InstrumentalTab(vm, isGenerating)
            1 -> VocalsTab(vm, isGenerating)
            2 -> LibraryTab(vm, stems, playingId)
        }
    }
}

@Composable
private fun InstrumentalTab(vm: MainViewModel, isGenerating: Boolean) {
    var prompt by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            placeholder = { Text("dark trap beat 140 bpm 808 bass") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duration (s)") },
                placeholder = { Text("15") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it },
                label = { Text("Seed") },
                placeholder = { Text("-1 = random") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.generateInstrumental(
                    prompt = prompt,
                    duration = duration.toIntOrNull(),
                    seed = seed.toLongOrNull()
                )
            },
            enabled = prompt.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Instrumental")
        }
    }
}

@Composable
private fun VocalsTab(vm: MainViewModel, isGenerating: Boolean) {
    var lyrics by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            label = { Text("Lyrics") },
            placeholder = { Text("[verse]\nI walk alone tonight\nunder city lights\n[chorus]\noh yeah oh yeah") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duration") },
                placeholder = { Text("30") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it },
                label = { Text("Seed") },
                placeholder = { Text("-1") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = voice,
                onValueChange = { voice = it },
                label = { Text("Voice") },
                placeholder = { Text("optional") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.generateVocals(
                    lyrics = lyrics,
                    duration = duration.toIntOrNull(),
                    seed = seed.toLongOrNull(),
                    voice = voice.ifBlank { null }
                )
            },
            enabled = lyrics.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Vocals")
        }
    }
}

@Composable
private fun LibraryTab(vm: MainViewModel, stems: List<Stem>, playingId: String?) {
    if (stems.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No stems yet. Generate some!", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(stems, key = { it.id }) { stem ->
            StemCard(stem, vm, isPlaying = playingId == stem.id)
        }
    }
}

@Composable
private fun StemCard(stem: Stem, vm: MainViewModel, isPlaying: Boolean) {
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(stem.name) }

    Card(
        Modifier.fillMaxWidth(),
        colors = if (isPlaying) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Type badge
                val badge = when (stem.type) {
                    StemType.INSTRUMENTAL -> "🎵"
                    StemType.VOCALS -> "🎤"
                    StemType.SPEECH -> "🗣"
                }
                Text(badge)
                Spacer(Modifier.width(8.dp))

                Column(Modifier.weight(1f)) {
                    Text(stem.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("${stem.sizeBytes / 1024}KB")
                            stem.duration?.let { append(" • ${it}s") }
                            stem.seed?.let { append(" • seed:$it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play/Stop
                IconButton(onClick = { if (isPlaying) vm.stopPlayback() else vm.playStem(stem) }) {
                    Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, "Play")
                }
                // Re-generate (new seed)
                IconButton(onClick = { vm.regenerate(stem) }) {
                    Icon(Icons.Default.Refresh, "Re-generate")
                }
                // Rename
                IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Default.Edit, "Rename")
                }
                // Delete
                IconButton(onClick = { vm.deleteStem(stem.id) }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Rename dialog inline
            if (showRename) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.renameStem(stem.id, newName); showRename = false }) { Text("Save") }
                }
            }
        }
    }
}
