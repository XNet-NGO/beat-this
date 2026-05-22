package com.beatthis.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Piano, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("MIDIjourney", style = MaterialTheme.typography.headlineMedium)
        }
        Text("AI composition assistant — outputs load into piano roll", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // Messages
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Try asking:", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            SuggestionRow(Icons.Default.QueueMusic, "Write a 4-bar chord progression in Am for lo-fi")
                            SuggestionRow(Icons.Default.Straighten, "Suggest a melody over Cmaj7 - Dm7 - G7")
                            SuggestionRow(Icons.Default.ViewTimeline, "Create a song structure for a 3-minute pop track")
                        }
                    }
                }
            }
            items(messages.indices.toList()) { i ->
                val (text, isUser) = messages[i]
                MessageBubble(text, isUser, onImport = {
                    val parsed = MidiJourneyParser.parse(text)
                    if (parsed.isNotEmpty()) vm.loadToPianoRoll(parsed)
                })
            }
        }

        Spacer(Modifier.height(8.dp))

        // Input
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Describe what you want composed...") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 160.dp),
            maxLines = 6,
            trailingIcon = {
                IconButton(onClick = { if (input.isNotBlank()) { vm.askComposition(input); input = "" } }) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        )
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean, onImport: () -> Unit) {
    val hasImportable = !isUser && MidiJourneyParser.hasImportableContent(text)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }

            if (hasImportable) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Piano, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import to Studio")
                }
            }
        }
    }
}
