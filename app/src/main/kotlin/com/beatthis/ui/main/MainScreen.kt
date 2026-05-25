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
    ARRANGE("Arrange"), PIANO_ROLL("Piano Roll"), DRUMS("Drums"), MIXER("Mixer"), PLUGINS("FX")
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
            StudioView.DRUMS -> {
                var showPads by remember { mutableStateOf(true) }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = showPads,
                            onClick = { showPads = true },
                            label = { Text("Pads") }
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.FilterChip(
                            selected = !showPads,
                            onClick = { showPads = false },
                            label = { Text("Sequencer") }
                        )
                    }
                    if (showPads) {
                        com.beatthis.ui.drums.DrumPadView(Modifier.fillMaxSize())
                    } else {
                        StepSequencerView(remember {
                            DrumPattern(1, tracks = mutableListOf(
                                DrumTrackRow("Kick", 36), DrumTrackRow("Snare", 38), DrumTrackRow("HiHat", 42),
                                DrumTrackRow("Clap", 39), DrumTrackRow("Tom", 45), DrumTrackRow("Rim", 37),
                            ))
                        }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            StudioView.MIXER -> MixerView(engine, vm.pluginHost)
            StudioView.PLUGINS -> PluginsStudioView(vm.pluginHost)
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
                    Icon(Icons.Default.Timer, null, Modifier.size(14.dp),
                        tint = if (metronomeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
    var editingTrack by remember { mutableStateOf<DawTrack?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
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
                items(tracks.size) { index ->
                    val track = tracks[index]
                    TrackLaneWithCrud(
                        track = track,
                        index = index,
                        totalSteps = engine.totalSteps,
                        currentStep = currentStep,
                        isFirst = index == 0,
                        isLast = index == tracks.size - 1,
                        onRename = { editingTrack = track; renameText = track.name },
                        onDelete = { engine.removeTrack(track.id) },
                        onDuplicate = { engine.duplicateTrack(track.id) },
                        onMoveUp = { if (index > 0) engine.moveTrack(index, index - 1) },
                        onMoveDown = { if (index < tracks.size - 1) engine.moveTrack(index, index + 1) }
                    )
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

        // Rename dialog
        if (editingTrack != null) {
            AlertDialog(
                onDismissRequest = { editingTrack = null },
                title = { Text("Rename Track") },
                text = {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = {
                    Button(onClick = { engine.renameTrack(editingTrack!!.id, renameText); editingTrack = null }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editingTrack = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun TrackLaneWithCrud(
    track: DawTrack, index: Int, totalSteps: Int, currentStep: Int,
    isFirst: Boolean, isLast: Boolean,
    onRename: () -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit
) {
    val trackColor = when (track.type) {
        TrackType.SYNTH -> Color(0xFF8B5CF6)
        TrackType.SAMPLER -> Color(0xFF3B82F6)
        TrackType.AUDIO -> Color(0xFF10B981)
    }
    var showMenu by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 1.dp)) {
        // Track header with context menu
        Surface(
            color = trackColor.copy(alpha = 0.15f),
            modifier = Modifier.width(100.dp).fillMaxHeight().clickable { showMenu = true }
        ) {
            Box(Modifier.padding(4.dp)) {
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text(track.name, fontSize = 10.sp, maxLines = 1)
                    Text(track.type.name, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) })
                    if (!isFirst) DropdownMenuItem(text = { Text("Move Up") }, onClick = { showMenu = false; onMoveUp() }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp)) })
                    if (!isLast) DropdownMenuItem(text = { Text("Move Down") }, onClick = { showMenu = false; onMoveDown() }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp)) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) })
                }
            }
        }

        // Timeline lane
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A1A)).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width((totalSteps * 8).dp).fillMaxHeight()) {
                for (event in track.events) {
                    val x = event.step * 8f * density
                    val w = event.duration * 8f * density
                    drawRoundRect(trackColor, topLeft = androidx.compose.ui.geometry.Offset(x, 4f), size = androidx.compose.ui.geometry.Size(w, size.height - 8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                }
                val px = currentStep * 8f * density
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), strokeWidth = 2f)
            }
        }
    }
}

@Composable
private fun MixerView(engine: DawEngine, pluginHost: com.beatthis.plugins.host.PluginHost) {
    val tracks by engine.tracks.collectAsState()
    val pluginInstances by pluginHost.instances.collectAsState()
    var selectedPlugin by remember { mutableStateOf<com.beatthis.plugins.host.PluginInstance?>(null) }

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Add tracks to see mixer", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (track in tracks) {
                val trackPlugins = pluginInstances.filter { it.trackId == track.id }
                ChannelStrip(track, trackPlugins) { selectedPlugin = it }
            }
            MasterStrip()
        }

        // Plugin params panel
        if (selectedPlugin != null) {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                com.beatthis.ui.plugins.PluginParamsSheet(
                    instance = selectedPlugin!!,
                    pluginHost = pluginHost,
                    onDismiss = { selectedPlugin = null }
                )
            }
        }
    }
}

@Composable
private fun ChannelStrip(track: DawTrack, plugins: List<com.beatthis.plugins.host.PluginInstance>, onPluginClick: (com.beatthis.plugins.host.PluginInstance) -> Unit) {
    val trackColor = when (track.type) {
        TrackType.SYNTH -> Color(0xFF8B5CF6)
        TrackType.SAMPLER -> Color(0xFF3B82F6)
        TrackType.AUDIO -> Color(0xFF10B981)
    }
    var volume by remember { mutableFloatStateOf(track.volume) }
    var pan by remember { mutableFloatStateOf(track.pan) }

    Card(Modifier.width(76.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(track.name, fontSize = 8.sp, maxLines = 1, color = trackColor)
            Spacer(Modifier.height(2.dp))

            // Insert slots (up to 4)
            for (i in 0 until 4) {
                val plugin = plugins.find { it.slotIndex == i }
                Box(
                    Modifier.fillMaxWidth().height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (plugin != null) trackColor.copy(0.3f) else Color(0xFF2A2A2A))
                        .clickable { plugin?.let { onPluginClick(it) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(plugin?.plugin?.displayName?.take(8) ?: "", fontSize = 6.sp, color = Color.White, maxLines = 1)
                }
                Spacer(Modifier.height(1.dp))
            }

            Spacer(Modifier.height(4.dp))

            // Mute/Solo
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (track.muted) Color.Red.copy(0.8f) else Color(0xFF333333))
                        .clickable { track.muted = !track.muted },
                    contentAlignment = Alignment.Center
                ) { Text("M", fontSize = 7.sp, color = Color.White) }
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (track.solo) Color.Yellow.copy(0.8f) else Color(0xFF333333))
                        .clickable { track.solo = !track.solo },
                    contentAlignment = Alignment.Center
                ) { Text("S", fontSize = 7.sp, color = if (track.solo) Color.Black else Color.White) }
            }

            Spacer(Modifier.height(4.dp))
            Text("Pan", fontSize = 6.sp, color = Color.Gray)
            Slider(value = pan, onValueChange = { pan = it; track.pan = it }, valueRange = -1f..1f, modifier = Modifier.height(20.dp))

            Spacer(Modifier.weight(1f))

            Slider(
                value = volume,
                onValueChange = { volume = it; track.volume = it },
                valueRange = 0f..1.2f,
                modifier = Modifier.fillMaxHeight(0.5f).width(20.dp),
                colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor)
            )

            val db = if (volume > 0) (20 * Math.log10(volume.toDouble())).toInt() else -60
            Text("${db}dB", fontSize = 7.sp, color = Color.Gray)
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

@Composable
private fun PluginsStudioView(pluginHost: com.beatthis.plugins.host.PluginHost) {
    val instances by pluginHost.instances.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var expandedInstance by remember { mutableStateOf<com.beatthis.plugins.host.PluginInstance?>(null) }

    if (instances.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Extension, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No plugins loaded", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("Load plugins from the Plugins tab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            // Embedded native plugin UI
            if (expandedInstance != null) {
                val inst = expandedInstance!!
                Surface(Modifier.fillMaxWidth().weight(1f), tonalElevation = 2.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(inst.plugin.displayName, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { expandedInstance = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        }
                        // Render plugin's native UI via SurfaceControl (cross-process)
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                val client = org.androidaudioplugin.hosting.AudioPluginSurfaceControlClient(ctx)
                                val view = client.surfaceView
                                client.connectUIAsync(
                                    inst.plugin.packageName,
                                    inst.plugin.pluginId,
                                    inst.slotIndex,
                                    800, 1200
                                )
                                view ?: android.view.View(ctx)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Plugin list
            LazyColumn(
                Modifier.let { if (expandedInstance != null) it.weight(0.3f) else it.weight(1f) }.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(instances.size) { i ->
                    val instance = instances[i]
                    Card(Modifier.fillMaxWidth().clickable { expandedInstance = instance }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (instance.plugin.category == "Instrument") Icons.Default.Piano else Icons.Default.Tune,
                                    null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(instance.plugin.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("Track ${instance.trackId} | Slot ${instance.slotIndex}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalButton(onClick = { expandedInstance = instance }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    Icon(Icons.Default.Fullscreen, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("UI", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { pluginHost.unloadPlugin(instance.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            // Params (collapsed when UI is open)
                            if (expandedInstance == null && instance.plugin.parameters.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                for (param in instance.plugin.parameters.take(4)) {
                                    var value by remember { mutableFloatStateOf(instance.paramValues[param.id] ?: param.default.toFloat()) }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(param.name, Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Slider(
                                            value = value,
                                            onValueChange = { value = it; pluginHost.setParameter(instance.id, param.id, it) },
                                            valueRange = param.min.toFloat()..param.max.toFloat(),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
