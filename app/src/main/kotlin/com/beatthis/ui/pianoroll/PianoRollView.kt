package com.beatthis.ui.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.engine.midi.Note
import com.beatthis.engine.midi.Pattern

@Composable
fun PianoRollView(
    pattern: Pattern,
    onNoteAdd: (Note) -> Unit = {},
    onNoteRemove: (Note) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val noteRange = 36..96 // C2 to C7 (5 octaves)
    val totalKeys = noteRange.last - noteRange.first
    val keyHeight = 20.dp
    val tickWidth = 0.5.dp // pixels per tick
    val beatWidth = tickWidth * Pattern.TICKS_PER_BEAT

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    var notes by remember { mutableStateOf(pattern.notes.toList()) }

    Row(modifier.fillMaxSize()) {
        // Piano keys column
        Column(
            Modifier
                .width(48.dp)
                .verticalScroll(vScroll)
        ) {
            for (pitch in noteRange.reversed()) {
                val isBlack = pitch % 12 in listOf(1, 3, 6, 8, 10)
                Box(
                    Modifier
                        .height(keyHeight)
                        .fillMaxWidth()
                        .background(if (isBlack) Color(0xFF333333) else Color(0xFF555555))
                ) {
                    if (pitch % 12 == 0) {
                        Text("C${pitch / 12 - 1}", fontSize = 9.sp, color = Color.White)
                    }
                }
            }
        }

        // Note grid
        Box(
            Modifier
                .weight(1f)
                .horizontalScroll(hScroll)
                .verticalScroll(vScroll)
        ) {
            Canvas(
                Modifier
                    .width((pattern.lengthTicks * tickWidth.value).dp)
                    .height((totalKeys * keyHeight.value).dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val tick = (offset.x / tickWidth.toPx()).toInt()
                            val pitch = noteRange.last - (offset.y / keyHeight.toPx()).toInt()
                            val quantizedTick = (tick / 120) * 120 // quantize to 16th
                            val note = Note(pitch, quantizedTick, 120, 100)
                            // Toggle: remove if exists, add if not
                            val existing = notes.find { it.pitch == pitch && it.startTick == quantizedTick }
                            if (existing != null) {
                                notes = notes - existing
                                onNoteRemove(existing)
                            } else {
                                notes = notes + note
                                onNoteAdd(note)
                            }
                        }
                    }
            ) {
                drawGrid(noteRange, pattern.lengthTicks, keyHeight.toPx(), tickWidth.toPx())
                drawNotes(notes, noteRange, keyHeight.toPx(), tickWidth.toPx())
            }
        }
    }
}

private fun DrawScope.drawGrid(noteRange: IntRange, lengthTicks: Int, keyH: Float, tickW: Float) {
    val totalKeys = noteRange.last - noteRange.first
    val gridColor = Color(0xFF2A2A2A)
    val beatColor = Color(0xFF3A3A3A)
    val barColor = Color(0xFF4A4A4A)

    // Horizontal lines (per key)
    for (i in 0..totalKeys) {
        val y = i * keyH
        val pitch = noteRange.last - i
        val isBlack = pitch % 12 in listOf(1, 3, 6, 8, 10)
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
        if (isBlack) {
            drawRect(Color(0x11FFFFFF), Offset(0f, y), Size(size.width, keyH))
        }
    }

    // Vertical lines (per beat and bar)
    val beatsTotal = lengthTicks / Pattern.TICKS_PER_BEAT
    for (beat in 0..beatsTotal) {
        val x = beat * Pattern.TICKS_PER_BEAT * tickW
        val color = if (beat % 4 == 0) barColor else beatColor
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (beat % 4 == 0) 2f else 1f)
    }
}

private fun DrawScope.drawNotes(notes: List<Note>, noteRange: IntRange, keyH: Float, tickW: Float) {
    val noteColor = Color(0xFFBB86FC)
    for (note in notes) {
        if (note.pitch !in noteRange) continue
        val x = note.startTick * tickW
        val y = (noteRange.last - note.pitch) * keyH
        val w = note.durationTicks * tickW
        drawRoundRect(noteColor, Offset(x, y + 1), Size(w, keyH - 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
    }
}
