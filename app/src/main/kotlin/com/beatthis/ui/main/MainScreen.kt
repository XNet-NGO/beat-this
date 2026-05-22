package com.beatthis.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.beatthis.daw.*
import com.beatthis.ui.pianoroll.PianoRollView
import com.beatthis.ui.sequencer.StepSequencerView
import com.beatthis.ui.viewmodel.MainViewModel
import com.beatthis.engine.midi.DrumPattern
import com.beatthis.engine.midi.DrumTrackRow

enum class StudioView(val label: String) {
    ARRANGE("Arrange"), PIANO_ROLL("Piano Roll"), DRUMS("Drums"), MIXER("Mixer")
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val engine = remember { vm.dawEngine }
    var currentView by remember { mutableStateOf(StudioView.ARRANGE) }

    Column(Modifier.fillMaxSize()) {
        // Transport bar (always visible)
        TransportBar(engine, vm)

        // View tabs
        ScrollableTabRow(selectedTabIndex = currentView.ordinal, edgePadding = 0.dp) {
            StudioView.entries.forEach { view ->
                Tab(selected = currentView == view, onClick = { currentView = view }, text = { Text(view.label, fontSize = 11.sp) })
            }
        }

        // Content
        when (currentView) {
            StudioView.ARRANGE -> ArrangementView(engine)
            StudioView.PIANO_ROLL -> {
                val importedNotes by vm.pianoNotes.collectAsState()
                val lengthBars by vm.pianoLengthBars.collectAsState()
                PianoRollView(importedNotes = importedNotes, lengthBars = lengthBars, modifier = Modifier.fillMaxSize())
            }
            StudioView.DRUMS -> StepSequencerView(remember {
                DrumPattern(1, tracks = mutableListOf(
                    DrumTrackRow("Kick", 36), DrumTrackRow("Snare", 38), DrumTrackRow("HiHat", 42),
                    DrumTrackRow("Clap", 39), DrumTrackRow("Tom", 45), DrumTrackRow("Rim", 37),
                ))
            }, modifier = Modifier.fillMaxSize())
            StudioView.MIXER -> MixerView(engine)
        }
    }
}

@Composable
private fun TransportBar(engine: DawEngine, vm: MainViewModel) {
    val isPlaying by engine.isPlaying.collectAsState()
    val isRecording by engine.isRecording.collectAsState()
    val tempo by engine.tempo.collectAsState()
    val currentStep by engine.currentStep.collectAsState()
    val loopEnabled by engine.loopEnabled.collectAsState()
    val metronomeEnabled by engine.metronomeEnabled.collectAsState()
    val status by vm.status.collectAsState()
    val isListening by vm.isListening.collectAsState()
    val context = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.voiceCommand()
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Rewind
                IconButton(onClick = { engine.stop() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, Modifier.size(18.dp))
                }
                // Play/Pause
                IconButton(onClick = { if (isPlaying) engine.pause() else engine.play() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                        Modifier.size(22.dp),
                        tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                // Record
                IconButton(onClick = { if (isRecording) engine.stopRecord() else engine.record() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FiberManualRecord, null, Modifier.size(16.dp),
                        tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Loop
                IconButton(onClick = { engine.toggleLoop() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Loop, null, Modifier.size(14.dp),
                        tint = if (loopEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Metronome
                IconButton(onClick = { engine.toggleMetronome() }, modifier = Modifier.size(28.dp)) {
                    Text("🥁", fontSize = 12.sp)
                }

                // Tempo
                Text("${tempo.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("BPM", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.weight(1f))

                // Position
                val bar = currentStep / engine.stepsPerMeasure + 1
                val beat = (currentStep % engine.stepsPerMeasure) / 4 + 1
                Text("$bar.$beat", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)

                Spacer(Modifier.width(8.dp))

                // Voice command
                IconButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) vm.voiceCommand() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isListening) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Mic, null, Modifier.size(16.dp))
                }
            }

            // Status line
            if (status.isNotBlank()) {
                Text(status, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ArrangementView(engine: DawEngine) {
    val tracks by engine.tracks.collectAsState()
    val currentStep by engine.currentStep.collectAsState()
    var showAddTrack by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Track list + timeline
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No tracks yet", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddTrack = true }) { Text("+ Add Track") }
                    Spacer(Modifier.height(8.dp))
                    Text("Or say: \"Add a synth track\"", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tracks) { track ->
                    TrackLane(track, engine.totalSteps, currentStep)
                }
                item {
                    TextButton(onClick = { showAddTrack = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Track")
                    }
                }
            }
        }

        if (showAddTrack) {
            AddTrackDialog(onDismiss = { showAddTrack = false }, onAdd = { name, type ->
                engine.addTrack(name, type)
                showAddTrack = false
            })
        }
    }
}

@Composable
private fun TrackLane(track: DawTrack, totalSteps: Int, currentStep: Int) {
    val trackColor = when (track.type) {
        TrackType.SYNTH -> Color(0xFF8B5CF6)
        TrackType.SAMPLER -> Color(0xFF3B82F6)
        TrackType.AUDIO -> Color(0xFF10B981)
    }

    Row(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 1.dp)) {
        // Track header
        Surface(
            color = trackColor.copy(alpha = 0.15f),
            modifier = Modifier.width(80.dp).fillMaxHeight()
        ) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                Text(track.name, fontSize = 10.sp, maxLines = 1)
                Text(track.type.name, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Timeline lane
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A1A)).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width((totalSteps * 8).dp).fillMaxHeight()) {
                // Events as blocks
                for (event in track.events) {
                    val x = event.step * 8f * density
                    val w = event.duration * 8f * density
                    drawRoundRect(trackColor, topLeft = androidx.compose.ui.geometry.Offset(x, 4f), size = androidx.compose.ui.geometry.Size(w, size.height - 8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                }
                // Playhead
                val px = currentStep * 8f * density
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), strokeWidth = 2f)
            }
        }
    }
}

@Composable
private fun MixerView(engine: DawEngine) {
    val tracks by engine.tracks.collectAsState()

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Add tracks to see mixer", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (track in tracks) {
            ChannelStrip(track)
        }
        // Master
        MasterStrip()
    }
}

@Composable
private fun ChannelStrip(track: DawTrack) {
    val trackColor = when (track.type) {
        TrackType.SYNTH -> Color(0xFF8B5CF6)
        TrackType.SAMPLER -> Color(0xFF3B82F6)
        TrackType.AUDIO -> Color(0xFF10B981)
    }
    var volume by remember { mutableFloatStateOf(track.volume) }
    var pan by remember { mutableFloatStateOf(track.pan) }

    Card(Modifier.width(72.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Track name
            Text(track.name, fontSize = 8.sp, maxLines = 1, color = trackColor)
            Spacer(Modifier.height(4.dp))

            // Mute/Solo
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (track.muted) Color.Red.copy(0.8f) else Color(0xFF333333))
                        .clickable { track.muted = !track.muted },
                    contentAlignment = Alignment.Center
                ) { Text("M", fontSize = 8.sp, color = Color.White) }
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (track.solo) Color.Yellow.copy(0.8f) else Color(0xFF333333))
                        .clickable { track.solo = !track.solo },
                    contentAlignment = Alignment.Center
                ) { Text("S", fontSize = 8.sp, color = if (track.solo) Color.Black else Color.White) }
            }

            Spacer(Modifier.height(4.dp))

            // Pan knob
            Text("Pan", fontSize = 7.sp, color = Color.Gray)
            Slider(value = pan, onValueChange = { pan = it; track.pan = it }, valueRange = -1f..1f, modifier = Modifier.height(20.dp))

            Spacer(Modifier.weight(1f))

            // Volume fader
            Slider(
                value = volume,
                onValueChange = { volume = it; track.volume = it },
                valueRange = 0f..1.2f,
                modifier = Modifier.fillMaxHeight(0.5f).width(20.dp),
                colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor)
            )

            // dB label
            val db = if (volume > 0) (20 * Math.log10(volume.toDouble())).toInt() else -60
            Text("${db}dB", fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun MasterStrip() {
    Card(Modifier.width(72.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MASTER", fontSize = 8.sp, color = Color.White)
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(8.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF10B981)))
            Spacer(Modifier.height(4.dp))
            Text("0dB", fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun AddTrackDialog(onDismiss: () -> Unit, onAdd: (String, TrackType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TrackType.SYNTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Track") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrackType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name, fontSize = 10.sp) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onAdd(name.ifBlank { "${type.name} ${1}" }, type) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
