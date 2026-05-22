package com.beatthis.ui.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.audio.ToneGenerator
import com.beatthis.engine.midi.Note
import com.beatthis.engine.midi.Pattern

@Composable
fun PianoRollView(
    importedNotes: List<Note> = emptyList(),
    lengthBars: Int = 4,
    modifier: Modifier = Modifier
) {
    val noteRange = 36..96 // C2 to C7
    val totalKeys = noteRange.last - noteRange.first
    val keyHeight = 20.dp
    val tickWidth = 0.5.dp
    val totalTicks = lengthBars * Pattern.TICKS_PER_BAR

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Local mutable notes state, seeded from imported
    var notes by remember(importedNotes) { mutableStateOf(importedNotes) }

    Row(modifier.fillMaxSize()) {
        // Piano keys
        Column(Modifier.width(40.dp).verticalScroll(vScroll)) {
            for (pitch in noteRange.reversed()) {
                val isBlack = pitch % 12 in listOf(1, 3, 6, 8, 10)
                Box(
                    Modifier
                        .height(keyHeight)
                        .fillMaxWidth()
                        .background(if (isBlack) Color(0xFF222222) else Color(0xFF444444))
                        .clickable { ToneGenerator.playNote(pitch, 300) }
                ) {
                    val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
                    if (pitch % 12 == 0) {
                        Text("C${pitch / 12 - 1}", fontSize = 8.sp, color = Color.White)
                    }
                }
            }
        }

        // Grid + notes
        Box(Modifier.weight(1f).horizontalScroll(hScroll).verticalScroll(vScroll)) {
            Canvas(
                Modifier
                    .width((totalTicks * tickWidth.value).dp)
                    .height((totalKeys * keyHeight.value).dp)
                    .pointerInput(notes) {
                        detectTapGestures { offset ->
                            val tick = (offset.x / tickWidth.toPx()).toInt()
                            val pitch = noteRange.last - (offset.y / keyHeight.toPx()).toInt()
                            if (pitch !in noteRange) return@detectTapGestures
                            val q = (tick / 120) * 120
                            val existing = notes.find { it.pitch == pitch && it.startTick == q }
                            if (existing != null) {
                                notes = notes - existing
                            } else {
                                val note = Note(pitch, q, 120, 100)
                                notes = notes + note
                                ToneGenerator.playNote(pitch, 200)
                            }
                        }
                    }
            ) {
                val keyH = keyHeight.toPx()
                val tickW = tickWidth.toPx()

                // Grid
                for (i in 0..totalKeys) {
                    val y = i * keyH
                    val p = noteRange.last - i
                    drawLine(Color(0xFF1A1A1A), Offset(0f, y), Offset(size.width, y))
                    if (p % 12 in listOf(1, 3, 6, 8, 10)) drawRect(Color(0x08FFFFFF), Offset(0f, y), Size(size.width, keyH))
                }
                val beats = totalTicks / Pattern.TICKS_PER_BEAT
                for (b in 0..beats) {
                    val x = b * Pattern.TICKS_PER_BEAT * tickW
                    drawLine(if (b % 4 == 0) Color(0xFF3A3A3A) else Color(0xFF222222), Offset(x, 0f), Offset(x, size.height), strokeWidth = if (b % 4 == 0) 2f else 1f)
                }

                // Notes
                for (note in notes) {
                    if (note.pitch !in noteRange) continue
                    val x = note.startTick * tickW
                    val y = (noteRange.last - note.pitch) * keyH
                    val w = note.durationTicks * tickW
                    drawRoundRect(Color(0xFFBB86FC), Offset(x, y + 1), Size(w.coerceAtLeast(4f), keyH - 2), cornerRadius = CornerRadius(3f))
                }
            }
        }
    }
}
