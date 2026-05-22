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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.audio.ToneGenerator
import com.beatthis.engine.midi.Note
import com.beatthis.engine.midi.Pattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class EditTool { DRAW, ERASE, SELECT }

@Composable
fun PianoRollView(
    importedNotes: List<Note> = emptyList(),
    lengthBars: Int = 4,
    modifier: Modifier = Modifier
) {
    val noteRange = 36..96
    val totalKeys = noteRange.last - noteRange.first
    val keyH = 18.dp
    val beatW = 40.dp
    val totalBeats = lengthBars * 4
    val quantize = 120 // 1/8th note in ticks

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val density = LocalDensity.current

    var notes by remember(importedNotes) { mutableStateOf(importedNotes) }
    var tool by remember { mutableStateOf(EditTool.DRAW) }
    var isPlaying by remember { mutableStateOf(false) }
    var playheadTick by remember { mutableIntStateOf(0) }
    var bpm by remember { mutableStateOf("120") }
    var selectedNote by remember { mutableStateOf<Note?>(null) }

    // Playback
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val tempo = bpm.toFloatOrNull() ?: 120f
        val tickMs = 60_000.0 / tempo / Pattern.TICKS_PER_BEAT
        val totalTicks = lengthBars * Pattern.TICKS_PER_BAR
        playheadTick = 0
        while (isActive && isPlaying) {
            // Play notes at current tick
            notes.filter { it.startTick == playheadTick }.forEach { ToneGenerator.playNote(it.pitch, 150) }
            delay((tickMs * 10).toLong()) // advance 10 ticks at a time
            playheadTick = (playheadTick + 10) % totalTicks
        }
    }

    Column(modifier.fillMaxSize()) {
        // Toolbar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Play/Stop
                IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(32.dp)) {
                    Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                }
                // BPM
                OutlinedTextField(value = bpm, onValueChange = { bpm = it }, modifier = Modifier.width(60.dp).height(36.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp))

                Spacer(Modifier.width(8.dp))

                // Tools
                FilterChip(selected = tool == EditTool.DRAW, onClick = { tool = EditTool.DRAW }, label = { Text("✏️", fontSize = 12.sp) }, modifier = Modifier.height(28.dp))
                FilterChip(selected = tool == EditTool.ERASE, onClick = { tool = EditTool.ERASE }, label = { Text("🗑", fontSize = 12.sp) }, modifier = Modifier.height(28.dp))
                FilterChip(selected = tool == EditTool.SELECT, onClick = { tool = EditTool.SELECT }, label = { Text("👆", fontSize = 12.sp) }, modifier = Modifier.height(28.dp))

                Spacer(Modifier.weight(1f))

                // Note count
                Text("${notes.size} notes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Clear
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
                    Text(
                        if (isBar) "${beat / 4 + 1}" else "·",
                        fontSize = 9.sp,
                        color = if (isBar) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
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
                        Modifier
                            .height(keyH)
                            .fillMaxWidth()
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

            // Note grid canvas
            Box(Modifier.weight(1f).horizontalScroll(hScroll).verticalScroll(vScroll)) {
                val canvasW = beatW * totalBeats
                val canvasH = keyH * totalKeys

                Canvas(
                    Modifier
                        .width(canvasW)
                        .height(canvasH)
                        .pointerInput(tool, notes) {
                            detectTapGestures { offset ->
                                val tickW = beatW.toPx() / Pattern.TICKS_PER_BEAT
                                val keyHPx = keyH.toPx()
                                val tick = (offset.x / tickW).toInt()
                                val pitch = noteRange.last - (offset.y / keyHPx).toInt()
                                if (pitch !in noteRange) return@detectTapGestures

                                val qTick = (tick / quantize) * quantize

                                when (tool) {
                                    EditTool.DRAW -> {
                                        val existing = notes.find { it.pitch == pitch && qTick >= it.startTick && qTick < it.startTick + it.durationTicks }
                                        if (existing == null) {
                                            val note = Note(pitch, qTick, quantize * 2, 90)
                                            notes = notes + note
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
                                }
                            }
                        }
                ) {
                    val tickW = beatW.toPx() / Pattern.TICKS_PER_BEAT
                    val keyHPx = keyH.toPx()

                    // Background grid
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
                        drawLine(
                            if (isBar) Color(0xFF444444) else Color(0xFF222222),
                            Offset(x, 0f), Offset(x, size.height),
                            strokeWidth = if (isBar) 1.5f else 0.5f
                        )
                    }

                    // Quantize grid (8th notes)
                    for (tick in 0 until totalBeats * Pattern.TICKS_PER_BEAT step quantize) {
                        val x = tick * tickW
                        drawLine(Color(0xFF1A1A1A), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.3f)
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
                        val color = if (isSelected) Color(0xFFFF9800) else Color(0xFFBB86FC)
                        drawRoundRect(color, Offset(x, y + 1), Size(w, keyHPx - 2), cornerRadius = CornerRadius(3f))
                        // Velocity indicator (brightness)
                        val velAlpha = note.velocity / 127f
                        drawRoundRect(Color.White.copy(alpha = velAlpha * 0.3f), Offset(x, y + 1), Size(w, keyHPx - 2), cornerRadius = CornerRadius(3f))
                    }
                }
            }
        }
    }
}
