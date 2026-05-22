package com.beatthis.ui.generate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.audio.Stem
import com.beatthis.audio.StemType
import com.beatthis.ui.viewmodel.MainViewModel

private val STYLES = listOf("Pop", "Rock", "EDM", "Jazz", "Lo-fi", "Classical", "Hip-hop", "R&B", "Trap", "Metal", "Acoustic", "Electronic", "Ambient", "Funk", "Phonk")

@Composable
fun GenerateScreen(vm: MainViewModel) {
    val isGenerating by vm.isGenerating.collectAsState()
    val status by vm.status.collectAsState()
    val stems by vm.stems.collectAsState()
    val playingId by vm.playingId.collectAsState()

    // State
    var customMode by remember { mutableStateOf(false) }
    var isInstrumental by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var bpm by remember { mutableIntStateOf(120) }
    var seed by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Mode toggle (Quick / Custom)
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    FilledTonalButton(
                        onClick = { customMode = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (!customMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Quick")
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = { customMode = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (customMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Custom")
                    }
                }
            }
        }

        // Voice & Tempo section
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Voice & Tempo", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Instrumental toggle
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Instrumental (no vocals)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = isInstrumental, onCheckedChange = { isInstrumental = it })
                    }

                    Spacer(Modifier.height(12.dp))

                    // BPM slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("$bpm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = bpm.toFloat(), onValueChange = { bpm = it.toInt() }, valueRange = 60f..200f)
                }
            }
        }

        // Prompt / Lyrics
        item {
            if (customMode && !isInstrumental) {
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Lyrics") },
                    placeholder = { Text("[Verse]\nYour lyrics here\n\n[Chorus]\nMore lyrics") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    minLines = 6
                )
            } else {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe your song") },
                    placeholder = { Text("An upbeat electro-pop song about summer vibes, catchy melody") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    minLines = 3
                )
            }
        }

        // Title
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Style
        item {
            OutlinedTextField(
                value = style,
                onValueChange = { style = it },
                label = { Text("Style") },
                placeholder = { Text("pop, upbeat, electronic") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(STYLES) { s ->
                    FilterChip(
                        selected = style.contains(s, ignoreCase = true),
                        onClick = {
                            style = if (style.contains(s, ignoreCase = true))
                                style.replace(Regex("$s,?\\s*", RegexOption.IGNORE_CASE), "").trim().trimEnd(',')
                            else if (style.isBlank()) s else "$style, $s"
                        },
                        label = { Text(s, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Seed
        item {
            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it },
                label = { Text("Seed (optional, -1 = random)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Generate button
        item {
            Button(
                onClick = {
                    if (customMode && !isInstrumental) {
                        vm.generateVocals(lyrics, null, seed.toLongOrNull(), null)
                    } else {
                        val fullPrompt = buildString {
                            append(prompt)
                            if (style.isNotBlank()) append(", $style")
                            append(", $bpm bpm")
                            if (isInstrumental) append(", instrumental")
                        }
                        vm.generateInstrumental(fullPrompt, null, seed.toLongOrNull())
                    }
                },
                enabled = (if (customMode && !isInstrumental) lyrics.isNotBlank() else prompt.isNotBlank()) && !isGenerating,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(status.ifBlank { "Generating..." })
                } else {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("GENERATE", letterSpacing = 2.sp)
                }
            }
        }

        // Status
        if (status.isNotBlank() && !isGenerating) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Library
        if (stems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Library (${stems.size})", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(stems, key = { it.id }) { stem ->
                StemCard(stem, vm, isPlaying = playingId == stem.id)
            }
        }
    }
}

@Composable
private fun StemCard(stem: Stem, vm: MainViewModel, isPlaying: Boolean) {
    val audioFile = remember(stem.id) { vm.repo.audioFile(stem) }
    val waveform = remember(stem.id) { com.beatthis.ui.waveform.extractWaveformSamples(audioFile, 200) }

    Card(
        Modifier.fillMaxWidth(),
        colors = if (isPlaying) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (stem.type) {
                        StemType.INSTRUMENTAL -> Icons.Default.MusicNote
                        StemType.VOCALS -> Icons.Default.RecordVoiceOver
                        StemType.SPEECH -> Icons.Default.VoiceChat
                    },
                    null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stem.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("${stem.sizeBytes / 1024}KB")
                            stem.duration?.let { append(" | ${it}s") }
                            stem.seed?.let { if (it != 0L) append(" | seed:$it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { if (isPlaying) vm.stopPlayback() else vm.playStem(stem) }) {
                    Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                }
                IconButton(onClick = { vm.regenerate(stem) }) {
                    Icon(Icons.Default.Refresh, null)
                }
                IconButton(onClick = { vm.deleteStem(stem.id) }) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }

            // Waveform
            if (waveform.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                com.beatthis.ui.waveform.WaveformView(
                    samples = waveform,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Load to arrangement
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = { vm.loadStemToArrangement(stem) },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add to Arrangement", fontSize = 11.sp)
            }
        }
    }
}
