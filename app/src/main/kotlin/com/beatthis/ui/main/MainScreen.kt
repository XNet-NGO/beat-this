package com.beatthis.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.beatthis.engine.midi.DrumPattern
import com.beatthis.engine.midi.DrumTrackRow
import com.beatthis.engine.Track
import com.beatthis.engine.TrackType
import com.beatthis.ui.pianoroll.PianoRollView
import com.beatthis.ui.sequencer.StepSequencerView
import com.beatthis.ui.timeline.TimelineView
import com.beatthis.ui.timeline.TimelineClip
import com.beatthis.ui.viewmodel.MainViewModel

@Composable
fun MainScreen(vm: MainViewModel) {
    var currentView by remember { mutableStateOf(StudioView.TRANSPORT) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = currentView.ordinal) {
            StudioView.entries.forEach { view ->
                Tab(selected = currentView == view, onClick = { currentView = view }, text = { Text(view.label) })
            }
        }

        when (currentView) {
            StudioView.TRANSPORT -> TransportView(vm)
            StudioView.TIMELINE -> {
                val demoTracks = remember { listOf(
                    Track(1, "Drums", TrackType.DRUM), Track(2, "Bass", TrackType.MIDI),
                    Track(3, "Keys", TrackType.MIDI), Track(4, "Vocals", TrackType.AUDIO),
                ) }
                val demoClips = remember { listOf(
                    TimelineClip(1, 0, 4), TimelineClip(1, 4, 4), TimelineClip(2, 0, 8),
                    TimelineClip(3, 4, 4), TimelineClip(4, 8, 4),
                ) }
                TimelineView(demoTracks, demoClips)
            }
            StudioView.PIANO_ROLL -> {
                val importedNotes by vm.pianoNotes.collectAsState()
                val lengthBars by vm.pianoLengthBars.collectAsState()
                PianoRollView(importedNotes = importedNotes, lengthBars = lengthBars, modifier = Modifier.fillMaxSize())
            }
            StudioView.SEQUENCER -> StepSequencerView(remember {
                DrumPattern(1, tracks = mutableListOf(
                    DrumTrackRow("Kick", 36), DrumTrackRow("Snare", 38), DrumTrackRow("HiHat", 42),
                    DrumTrackRow("Clap", 39), DrumTrackRow("Tom", 45), DrumTrackRow("Rim", 37),
                ))
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

enum class StudioView(val label: String) {
    TRANSPORT("Studio"), TIMELINE("Timeline"), PIANO_ROLL("Piano Roll"), SEQUENCER("Drums")
}

@Composable
private fun TransportView(vm: MainViewModel) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val isListening by vm.isListening.collectAsState()
    var textInput by remember { mutableStateOf("") }

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.voiceCommand()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Beat This", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text("Voice-Controlled AI DAW", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Status display
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.weight(1f))

        // Voice command button with permission check
        FloatingActionButton(
            onClick = {
                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) vm.voiceCommand()
                else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            if (isListening) CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 3.dp)
            else Icon(Icons.Default.Mic, "Voice Command", modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(if (isListening) "Listening..." else "Tap to speak", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))

        // Text command input (alternative to voice)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type a command...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (textInput.isNotBlank()) { vm.textCommand(textInput); textInput = "" }
            }) {
                Icon(Icons.Default.Send, "Send")
            }
        }

        Spacer(Modifier.weight(0.5f))
    }
}
