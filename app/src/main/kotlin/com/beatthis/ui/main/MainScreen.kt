package com.beatthis.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beatthis.engine.midi.DrumPattern
import com.beatthis.engine.midi.DrumTrackRow
import com.beatthis.engine.midi.Pattern
import com.beatthis.engine.Track
import com.beatthis.engine.TrackType
import com.beatthis.ui.pianoroll.PianoRollView
import com.beatthis.ui.sequencer.StepSequencerView
import com.beatthis.ui.timeline.TimelineView
import com.beatthis.ui.timeline.TimelineClip

@Composable
fun MainScreen() {
    var currentView by remember { mutableStateOf(StudioView.TRANSPORT) }

    Column(Modifier.fillMaxSize()) {
        // View switcher
        TabRow(selectedTabIndex = currentView.ordinal) {
            StudioView.entries.forEach { view ->
                Tab(
                    selected = currentView == view,
                    onClick = { currentView = view },
                    text = { Text(view.label) }
                )
            }
        }

        when (currentView) {
            StudioView.TRANSPORT -> TransportView()
            StudioView.TIMELINE -> {
                val demoTracks = remember { listOf(
                    Track(1, "Drums", TrackType.DRUM),
                    Track(2, "Bass", TrackType.MIDI),
                    Track(3, "Keys", TrackType.MIDI),
                    Track(4, "Vocals", TrackType.AUDIO),
                ) }
                val demoClips = remember { listOf(
                    TimelineClip(1, 0, 4, "Beat A"),
                    TimelineClip(1, 4, 4, "Beat A"),
                    TimelineClip(2, 0, 8, "Bassline"),
                    TimelineClip(3, 4, 4, "Chords"),
                    TimelineClip(4, 8, 4, "Verse 1"),
                ) }
                TimelineView(demoTracks, demoClips)
            }
            StudioView.PIANO_ROLL -> {
                val pattern = remember { Pattern(id = 1, name = "Pattern 1") }
                PianoRollView(pattern, modifier = Modifier.fillMaxSize())
            }
            StudioView.SEQUENCER -> {
                val drumPattern = remember {
                    DrumPattern(id = 1, tracks = mutableListOf(
                        DrumTrackRow("Kick", 36),
                        DrumTrackRow("Snare", 38),
                        DrumTrackRow("HiHat", 42),
                        DrumTrackRow("Clap", 39),
                        DrumTrackRow("Tom", 45),
                        DrumTrackRow("Rim", 37),
                    ))
                }
                StepSequencerView(drumPattern, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

enum class StudioView(val label: String) {
    TRANSPORT("Studio"), TIMELINE("Timeline"), PIANO_ROLL("Piano Roll"), SEQUENCER("Drums")
}

@Composable
private fun TransportView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Beat This", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Voice-Controlled AI DAW", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        TransportBar()
        Spacer(Modifier.height(24.dp))
        VoiceCommandButton()
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun TransportBar() {
    var isPlaying by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var tempo by remember { mutableStateOf("120") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Tempo display
        OutlinedTextField(
            value = tempo,
            onValueChange = { tempo = it },
            label = { Text("BPM") },
            modifier = Modifier.width(100.dp),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

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
