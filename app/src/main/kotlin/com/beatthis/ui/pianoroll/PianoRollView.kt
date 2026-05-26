package com.beatthis.ui.pianoroll

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.audio.ToneGenerator
import com.beatthis.daw.DawEngine
import com.beatthis.engine.midi.Note
import com.beatthis.engine.midi.Pattern

enum class EditTool { DRAW, ERASE, SELECT, VELOCITY }
enum class SnapValue(val ticks: Int, val label: String) {
    BAR(Pattern.TICKS_PER_BAR, "1 Bar"),
    BEAT(Pattern.TICKS_PER_BEAT, "1/4"),
    EIGHTH(Pattern.TICKS_PER_BEAT / 2, "1/8"),
    SIXTEENTH(Pattern.TICKS_PER_BEAT / 4, "1/16"),
    THIRTYSECOND(Pattern.TICKS_PER_BEAT / 8, "1/32"),
    OFF(1, "Off"),
}

@Composable
fun PianoRollView(
    engine: DawEngine,
    importedNotes: List<Note> = emptyList(),
    lengthBars: Int = 4,
    onNotesChanged: (List<Note>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val noteRange = 36..96
    val totalKeys = noteRange.last - noteRange.first
    val keyH = 18.dp
    val beatW = 40.dp
    val totalBeats = lengthBars * 4

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    var notes by remember(importedNotes) { mutableStateOf(importedNotes) }
    var tool by remember { mutableStateOf(EditTool.DRAW) }
    var snap by remember { mutableStateOf(SnapValue.EIGHTH) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var showSnapMenu by remember { mutableStateOf(false) }

    val isPlaying by engine.isPlaying.collectAsState()
    val currentStep by engine.currentStep.collectAsState()
    val tempo by engine.tempo.collectAsState()

    // Sync notes to engine when changed
    LaunchedEffect(notes) { onNotesChanged(notes) }

    // Convert engine step to tick for playhead
    val playheadTick = currentStep * (Pattern.TICKS_PER_BEAT / 4) // steps are 16th notes

    fun quantize(tick: Int): Int = (tick / snap.ticks) * snap.ticks

    Column(modifier.fillMaxSize()) {
        // Toolbar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Transport
                IconButton(onClick = { if (isPlaying) engine.stop() else engine.play() }, modifier = Modifier.size(32.dp)) {
                    Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                }
                Text("${tempo.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.width(8.dp))

                // Tools
                IconButton(onClick = { tool = EditTool.DRAW }, modifier = Modifier.size(28.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = if (tool == EditTool.DRAW) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                }
                IconButton(onClick = { tool = EditTool.ERASE }, modifier = Modifier.size(28.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = if (tool == EditTool.ERASE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                }
                IconButton(onClick = { tool = EditTool.SELECT }, modifier = Modifier.size(28.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = if (tool == EditTool.SELECT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Icon(Icons.Default.TouchApp, null, Modifier.size(16.dp))
                }
                IconButton(onClick = { tool = EditTool.VELOCITY }, modifier = Modifier.size(28.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = if (tool == EditTool.VELOCITY) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Icon(Icons.Default.Speed, null, Modifier.size(16.dp))
                }

                Spacer(Modifier.width(4.dp))

                // Snap
                Box {
                    FilterChip(selected = true, onClick = { showSnapMenu = true }, label = { Text(snap.label, fontSize = 10.sp) }, modifier = Modifier.height(26.dp))
                    DropdownMenu(expanded = showSnapMenu, onDismissRequest = { showSnapMenu = false }) {
                        SnapValue.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.label) }, onClick = { snap = s; showSnapMenu = false })
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Quantize all
                IconButton(onClick = {
                    notes = notes.map { it.copy(startTick = quantize(it.startTick), durationTicks = snap.ticks.coerceAtLeast(it.durationTicks)) }
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.GridOn, "Quantize", Modifier.size(16.dp))
                }

                Text("${notes.size}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                IconButton(onClick = { notes = emptyList(); selectedNote = null }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                }
            }
        }

        // Beat ruler
        Row(Modifier.fillMaxWidth().horizontalScroll(hScroll).padding(start = 44.dp)) {
            for (beat in 0 until totalBeats) {
                Box(Modifier.width(beatW).height(16.dp)) {
                    val isBar = beat % 4 == 0
                    Text(if (isBar) "${beat / 4 + 1}" else "·", fontSize = 9.sp, color = if (isBar) MaterialTheme.colorScheme.onSurface else Color.Gray)
                }
            }
        }

        // Piano + Grid
        Row(Modifier.fillMaxSize()) {
            // Piano keys
            Column(Modifier.width(44.dp).verticalScroll(vScroll)) {
                for (pitch in noteRange.reversed()) {
                    val isBlack = pitch % 12 in listOf(1, 3, 6, 8, 10)
                    val isC = pitch % 12 == 0
                    Box(
                        Modifier.height(keyH).fillMaxWidth()
                            .background(if (isBlack) Color(0xFF1A1A1A) else if (isC) Color(0xFF555555) else Color(0xFF3A3A3A))
                            .border(0.5.dp, Color(0xFF111111))
                            .clickable { ToneGenerator.playNote(pitch, 300) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                        if (isC || pitch == noteRange.first || pitch == noteRange.last) {
                            Text(" ${names[pitch % 12]}${pitch / 12 - 1}", fontSize = 7.sp, color = Color.White)
                        }
                    }
                }
            }

            // Grid canvas
            Box(Modifier.weight(1f).horizontalScroll(hScroll).verticalScroll(vScroll)) {
                val canvasW = beatW * totalBeats
                val canvasH = keyH * totalKeys

                Canvas(
                    Modifier.width(canvasW).height(canvasH)
                        .pointerInput(tool, notes, snap) {
                            detectTapGestures { offset ->
                                val tickW = beatW.toPx() / Pattern.TICKS_PER_BEAT
                                val keyHPx = keyH.toPx()
                                val rawTick = (offset.x / tickW).toInt()
                                val pitch = noteRange.last - (offset.y / keyHPx).toInt()
                                if (pitch !in noteRange) return@detectTapGestures
                                val qTick = quantize(rawTick)

                                when (tool) {
                                    EditTool.DRAW -> {
                                        val existing = notes.find { it.pitch == pitch && qTick >= it.startTick && qTick < it.startTick + it.durationTicks }
                                        if (existing == null) {
                                            notes = notes + Note(pitch, qTick, snap.ticks * 2, 90)
                                            ToneGenerator.playNote(pitch, 150)
                                        }
                                    }
                                    EditTool.ERASE -> {
                                        val hit = notes.find { it.pitch == pitch && qTick >= it.startTick && qTick < it.startTick + it.durationTicks }
                                        if (hit != null) notes = notes - hit
                                    }
                                    EditTool.SELECT -> {
                                        selectedNote = notes.find { it.pitch == pitch && qTick >= it.startTick && qTick < it.startTick + it.durationTicks }
                                    }
                                    EditTool.VELOCITY -> {
                                        val hit = notes.find { it.pitch == pitch && qTick >= it.startTick && qTick < it.startTick + it.durationTicks }
                                        if (hit != null) {
                                            val newVel = ((offset.y % keyHPx) / keyHPx * 127).toInt().coerceIn(1, 127)
                                            notes = notes.map { if (it === hit) it.copy(velocity = newVel) else it }
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(tool, notes, snap) {
                            if (tool != EditTool.SELECT) return@pointerInput
                            detectDragGestures { change, dragAmount ->
                                val sel = selectedNote ?: return@detectDragGestures
                                val tickW = beatW.toPx() / Pattern.TICKS_PER_BEAT
                                val keyHPx = keyH.toPx()
                                val dTick = (dragAmount.x / tickW).toInt()
                                val dPitch = -(dragAmount.y / keyHPx).toInt()
                                if (dTick != 0 || dPitch != 0) {
                                    val newTick = (sel.startTick + dTick).coerceAtLeast(0)
                                    val newPitch = (sel.pitch + dPitch).coerceIn(noteRange)
                                    val moved = sel.copy(startTick = quantize(newTick), pitch = newPitch)
                                    notes = notes.map { if (it === sel) moved else it }
                                    selectedNote = moved
                                }
                            }
                        }
                ) {
                    val tickW = beatW.toPx() / Pattern.TICKS_PER_BEAT
                    val keyHPx = keyH.toPx()

                    // Background
                    for (i in 0..totalKeys) {
                        val y = i * keyHPx
                        val p = noteRange.last - i
                        val isBlack = p % 12 in listOf(1, 3, 6, 8, 10)
                        if (isBlack) drawRect(Color(0x0AFFFFFF), Offset(0f, y), Size(size.width, keyHPx))
                        drawLine(Color(0xFF1A1A1A), Offset(0f, y), Offset(size.width, y))
                    }

                    // Beat/bar lines
                    for (beat in 0..totalBeats) {
                        val x = beat * beatW.toPx()
                        val isBar = beat % 4 == 0
                        drawLine(if (isBar) Color(0xFF444444) else Color(0xFF222222), Offset(x, 0f), Offset(x, size.height), strokeWidth = if (isBar) 1.5f else 0.5f)
                    }

                    // Snap grid
                    val snapTicks = snap.ticks
                    if (snapTicks < Pattern.TICKS_PER_BEAT) {
                        for (tick in 0 until totalBeats * Pattern.TICKS_PER_BEAT step snapTicks) {
                            val x = tick * tickW
                            drawLine(Color(0xFF1A1A1A), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.3f)
                        }
                    }

                    // Playhead
                    if (isPlaying) {
                        val px = playheadTick * tickW
                        drawLine(Color(0xFFFF5252), Offset(px, 0f), Offset(px, size.height), strokeWidth = 2f)
                    }

                    // Notes
                    for (note in notes) {
                        if (note.pitch !in noteRange) continue
                        val x = note.startTick * tickW
                        val y = (noteRange.last - note.pitch) * keyHPx
                        val w = (note.durationTicks * tickW).coerceAtLeast(4f)
                        val isSelected = note == selectedNote
                        val velBrightness = 0.4f + (note.velocity / 127f) * 0.6f
                        val color = if (isSelected) Color(0xFFFF9800) else Color(0xFFBB86FC).copy(alpha = velBrightness)
                        drawRoundRect(color, Offset(x, y + 1), Size(w, keyHPx - 2), cornerRadius = CornerRadius(3f))
                    }
                }
            }
        }

        // Velocity lane for selected note
        selectedNote?.let { sel ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vel: ${sel.velocity}", fontSize = 11.sp, modifier = Modifier.width(50.dp))
                    Slider(
                        value = sel.velocity.toFloat(),
                        onValueChange = { v ->
                            val newVel = v.toInt()
                            notes = notes.map { if (it == sel) it.copy(velocity = newVel) else it }
                            selectedNote = sel.copy(velocity = newVel)
                        },
                        valueRange = 1f..127f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Dur: ${sel.durationTicks}", fontSize = 11.sp, modifier = Modifier.width(60.dp))
                    Slider(
                        value = sel.durationTicks.toFloat(),
                        onValueChange = { d ->
                            val newDur = (d.toInt() / snap.ticks) * snap.ticks
                            if (newDur > 0) {
                                notes = notes.map { if (it == sel) it.copy(durationTicks = newDur) else it }
                                selectedNote = sel.copy(durationTicks = newDur)
                            }
                        },
                        valueRange = snap.ticks.toFloat()..(Pattern.TICKS_PER_BAR * 2).toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
