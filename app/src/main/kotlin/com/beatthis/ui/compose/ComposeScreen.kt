package com.beatthis.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
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

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("MIDIjourney", style = MaterialTheme.typography.headlineMedium)
        Text("AI Composition Assistant — ask for chords, melodies, structures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // Messages
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Try asking:", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("• Write a 4-bar chord progression in Am for lo-fi", style = MaterialTheme.typography.bodySmall)
                            Text("• Suggest a melody over Cmaj7 - Dm7 - G7 - Cmaj7", style = MaterialTheme.typography.bodySmall)
                            Text("• Create a song structure for a 3-minute pop track", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(messages) { (text, isUser) ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Input
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask for chords, melody, structure...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) { vm.askComposition(input); input = "" }
                }
            ) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}
