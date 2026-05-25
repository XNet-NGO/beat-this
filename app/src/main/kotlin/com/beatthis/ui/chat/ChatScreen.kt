package com.beatthis.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daw.ai.chat.MessageStatus
import com.daw.ai.chat.Role
import com.daw.ai.chat.ChatMessage
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val status by viewModel.status.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("AI Producer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.clear() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyState() }
            }
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
        }

        // Status
        AnimatedVisibility(visible = status != null) {
            StatusPulse(status ?: "")
        }

        // Input bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tell me what to do...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = !isStreaming,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank()) { viewModel.send(text.trim()); text = ""; scope.launch { listState.animateScrollToItem(messages.size) } }
                }),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { if (text.isNotBlank()) { viewModel.send(text.trim()); text = ""; scope.launch { listState.animateScrollToItem(messages.size) } } },
                enabled = text.isNotBlank() && !isStreaming,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text("AI Producer", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Text("Control your DAW with natural language.\nTry: \"Set tempo to 140 and add a drum track\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(16.dp, 16.dp, if (isUser) 4.dp else 16.dp, if (isUser) 16.dp else 4.dp)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(shape = shape, color = bgColor, modifier = Modifier.widthIn(max = if (isUser) 300.dp else 10000.dp).fillMaxWidth(if (isUser) 0.8f else 0.95f)) {
            Column(Modifier.padding(12.dp)) {
                // Thinking section
                if (msg.reasoning.isNotBlank()) {
                    ThinkingSection(msg)
                }
                // Tool tags
                if (msg.toolsUsed.isNotEmpty()) {
                    Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        msg.toolsUsed.forEach { tool ->
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text(toolLabel(tool), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                // Content
                val content = msg.content.ifBlank { if (msg.status == MessageStatus.STREAMING && msg.reasoning.isBlank()) "..." else "" }
                if (content.isNotBlank()) {
                    if (isUser) Text(content, style = MaterialTheme.typography.bodyMedium)
                    else if (msg.status == MessageStatus.STREAMING) StreamingText(content)
                    else SelectionContainer { FormattedText(content) }
                }
            }
        }
    }
}

@Composable
private fun ThinkingSection(msg: ChatMessage) {
    val isActive = msg.status == MessageStatus.STREAMING && msg.content.isBlank()
    var expanded by remember { mutableStateOf(isActive) }
    LaunchedEffect(msg.status) { if (msg.status != MessageStatus.STREAMING) expanded = false }

    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), onClick = { expanded = !expanded }) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    val alpha by rememberInfiniteTransition(label = "tp").animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ta")
                    Text("Thinking", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                } else {
                    Text("Thought", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle", modifier = Modifier.size(16.dp))
            }
            AnimatedVisibility(visible = expanded || isActive) {
                val lines = msg.reasoning.lines()
                val display = if (isActive && lines.size > 6) lines.takeLast(6).joinToString("\n") else msg.reasoning
                Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp).heightIn(max = 120.dp).verticalScroll(rememberScrollState()), lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun StatusPulse(label: String) {
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.alpha(alpha))
    }
}

@Composable
private fun StreamingText(content: String) {
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < content.length) {
            when {
                content.startsWith("```", i) -> {
                    val end = content.indexOf("```", i + 3)
                    val block = if (end != -1) content.substring(i + 3, end) else content.substring(i + 3)
                    val code = block.substringAfter('\n', block)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, background = Color(0xFF1E1E1E))) { append(code) }
                    i = if (end != -1) end + 3 else content.length
                }
                content[i] == '`' -> {
                    val end = content.indexOf('`', i + 1)
                    if (end != -1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2D2D2D))) { append(content.substring(i + 1, end)) }; i = end + 1 }
                    else { append('`'); i++ }
                }
                content.startsWith("**", i) -> {
                    val end = content.indexOf("**", i + 2)
                    if (end != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content.substring(i + 2, end)) }; i = end + 2 }
                    else { append("**"); i += 2 }
                }
                content.startsWith("- ", i) -> { append("• "); i += 2 }
                else -> { append(content[i]); i++ }
            }
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FormattedText(content: String) {
    // Same as StreamingText but wrapped in SelectionContainer at call site
    StreamingText(content)
}

private fun toolLabel(name: String) = when (name) {
    "set_tempo" -> "Tempo"
    "add_track" -> "Add Track"
    "remove_track" -> "Remove Track"
    "mute_track" -> "Mute"
    "solo_track" -> "Solo"
    "set_volume" -> "Volume"
    "set_pan" -> "Pan"
    "add_effect" -> "Effect"
    "remove_effect" -> "Remove FX"
    "record" -> "Record"
    "play" -> "Play"
    "stop" -> "Stop"
    "loop" -> "Loop"
    "generate_music" -> "Generate"
    "generate_vocals" -> "Vocals"
    "set_time_signature" -> "Time Sig"
    "export_mixdown" -> "Export"
    "undo" -> "Undo"
    "redo" -> "Redo"
    else -> name
}
