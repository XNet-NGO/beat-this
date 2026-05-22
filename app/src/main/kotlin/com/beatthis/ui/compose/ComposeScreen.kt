package com.beatthis.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beatthis.engine.midi.Note
import com.beatthis.engine.midi.Pattern
import com.beatthis.ui.viewmodel.MainViewModel

@Composable
fun ComposeScreen(vm: MainViewModel) {
    var input by remember { mutableStateOf("") }
    val messages by vm.compositionMessages.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("MIDIjourney", style = MaterialTheme.typography.headlineMedium)
        Text("AI composition — outputs load directly into piano roll", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // Messages
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Try:", style = MaterialTheme.typography.titleSmall)
                            Text("• Write a 4-bar chord progression in Am for lo-fi", style = MaterialTheme.typography.bodySmall)
                            Text("• Suggest a melody over Cmaj7 - Dm7 - G7", style = MaterialTheme.typography.bodySmall)
                            Text("• Create a bass line for a funk groove in E", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(messages.indices.toList()) { i ->
                val (text, isUser) = messages[i]
                MessageBubble(text, isUser, onLoadToPianoRoll = {
                    val parsed = parseMidiJourneyNotation(text)
                    if (parsed.isNotEmpty()) vm.loadToPianoRoll(parsed)
                })
            }
        }

        Spacer(Modifier.height(8.dp))

        // Input — multiline, scrollable, word wrap, copy/paste all work natively with TextField
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Describe what you want composed...") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 160.dp),
            maxLines = 6,
            trailingIcon = {
                IconButton(
                    onClick = { if (input.isNotBlank()) { vm.askComposition(input); input = "" } }
                ) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean, onLoadToPianoRoll: () -> Unit) {
    val hasNotation = !isUser && text.contains("pitch,time,duration,velocity")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            // Selectable text for copy/paste
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }

            // Show "Load to Piano Roll" button if response contains notation
            if (hasNotation) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onLoadToPianoRoll, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Piano, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Load to Piano Roll")
                }
            }
        }
    }
}

/**
 * Parse MIDIjourney notation output:
 *   pitch,time,duration,velocity
 *   57,0,1.5,75
 *   60,0.3,1.2,85
 *
 * Converts time/duration from beats (float) to ticks (480 ticks/beat).
 */
fun parseMidiJourneyNotation(text: String): List<Note> {
    val notes = mutableListOf<Note>()
    val lines = text.lines()
    var inNotation = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "pitch,time,duration,velocity") {
            inNotation = true
            continue
        }
        if (!inNotation) continue
        if (trimmed.isBlank() || !trimmed[0].isDigit()) {
            if (inNotation && trimmed.isNotBlank()) break // end of notation block
            continue
        }

        val parts = trimmed.split(",").map { it.trim() }
        if (parts.size >= 4) {
            val pitch = parts[0].toIntOrNull() ?: continue
            val time = parts[1].toFloatOrNull() ?: continue
            val dur = parts[2].toFloatOrNull() ?: continue
            val vel = parts[3].toIntOrNull() ?: continue

            notes.add(Note(
                pitch = pitch.coerceIn(0, 127),
                startTick = (time * Pattern.TICKS_PER_BEAT).toInt(),
                durationTicks = (dur * Pattern.TICKS_PER_BEAT).toInt(),
                velocity = vel.coerceIn(0, 127)
            ))
        }
    }
    return notes
}
