package com.beatthis.ui.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beatthis.audio.Stem
import com.beatthis.audio.StemType
import com.beatthis.ui.viewmodel.MainViewModel

private val STYLES = listOf("Pop", "Rock", "EDM", "Jazz", "Lo-fi", "Classical", "Hip-hop", "R&B", "Trap", "Metal", "Acoustic", "Electronic", "Ambient", "Funk", "Soul")

@Composable
fun GenerateScreen(vm: MainViewModel) {
    val isGenerating by vm.isGenerating.collectAsState()
    val status by vm.status.collectAsState()
    val stems by vm.stems.collectAsState()
    val playingId by vm.playingId.collectAsState()

    var customMode by remember { mutableStateOf(false) }
    var isInstrumental by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var bpm by remember { mutableStateOf("120") }
    var duration by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Status
        if (status.isNotBlank()) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isGenerating) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Mode toggle: Quick / Custom
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !customMode,
                    onClick = { customMode = false },
                    label = { Text("Quick Mode") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = customMode,
                    onClick = { customMode = true },
                    label = { Text("Custom Lyrics") },
                    leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
                )
            }
        }

        // Instrumental toggle
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Instrumental (no vocals)", Modifier.weight(1f))
                Switch(checked = isInstrumental, onCheckedChange = { isInstrumental = it })
            }
        }

        // Prompt or Lyrics input
        item {
            if (customMode && !isInstrumental) {
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Lyrics") },
                    placeholder = { Text("[verse]\nWalking down the street tonight\nNeon lights are shining bright\n[chorus]\nOh we're alive, we're alive") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6
                )
            } else {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe your song") },
                    placeholder = { Text("dark trap beat with heavy 808 bass and hi-hats") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        }

        // Style presets
        item {
            Text("Style", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(STYLES) { s ->
                    FilterChip(
                        selected = style == s,
                        onClick = { style = if (style == s) "" else s },
                        label = { Text(s, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }

        // BPM + Duration + Seed
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bpm,
                    onValueChange = { bpm = it },
                    label = { Text("BPM") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
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
                    placeholder = { Text("-1") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        // Generate button
        item {
            Button(
                onClick = {
                    val fullPrompt = buildPrompt(
                        customMode = customMode,
                        isInstrumental = isInstrumental,
                        prompt = prompt,
                        lyrics = lyrics,
                        style = style,
                        bpm = bpm
                    )
                    if (isInstrumental || !customMode) {
                        vm.generateInstrumental(fullPrompt, duration.toIntOrNull(), seed.toLongOrNull())
                    } else {
                        vm.generateVocals(fullPrompt, duration.toIntOrNull(), seed.toLongOrNull(), null)
                    }
                },
                enabled = (prompt.isNotBlank() || lyrics.isNotBlank()) && !isGenerating,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Icon(Icons.Default.MusicNote, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate")
                }
            }
        }

        // Library
        if (stems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Library (${stems.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(stems, key = { it.id }) { stem ->
                StemCard(stem, vm, isPlaying = playingId == stem.id)
            }
        }
    }
}

/** Build the prompt string for ACE-Step based on mode */
private fun buildPrompt(
    customMode: Boolean,
    isInstrumental: Boolean,
    prompt: String,
    lyrics: String,
    style: String,
    bpm: String
): String {
    val parts = mutableListOf<String>()

    if (isInstrumental) {
        // Instrumental: just describe the music, no lyrics tags
        parts.add(prompt)
        if (style.isNotBlank()) parts.add(style)
        if (bpm.isNotBlank()) parts.add("$bpm bpm")
        parts.add("instrumental")
        return parts.joinToString(", ")
    }

    if (customMode) {
        // Custom lyrics mode: pass lyrics with tags directly
        val prefix = buildString {
            if (style.isNotBlank()) append("$style, ")
            if (bpm.isNotBlank()) append("$bpm bpm, ")
            append("vocals")
        }
        return "$prefix\n$lyrics"
    }

    // Quick mode with vocals: describe and let AI figure it out
    parts.add(prompt)
    if (style.isNotBlank()) parts.add(style)
    if (bpm.isNotBlank()) parts.add("$bpm bpm")
    return parts.joinToString(", ")
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
                val badge = when (stem.type) { StemType.INSTRUMENTAL -> "🎵"; StemType.VOCALS -> "🎤"; StemType.SPEECH -> "🗣" }
                Text(badge)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stem.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("${stem.sizeBytes / 1024}KB")
                            stem.duration?.let { append(" • ${it}s") }
                            stem.seed?.let { if (it != 0L) append(" • seed:$it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Play/Stop
                IconButton(onClick = { if (isPlaying) vm.stopPlayback() else vm.playStem(stem) }) {
                    Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, "Play")
                }
                // Remix (re-generate, new seed)
                IconButton(onClick = { vm.regenerate(stem) }) {
                    Icon(Icons.Default.Refresh, "Remix")
                }
                // Delete
                IconButton(onClick = { vm.deleteStem(stem.id) }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (showRename) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.renameStem(stem.id, newName); showRename = false }) { Text("Save") }
                }
            }
        }
    }
}
