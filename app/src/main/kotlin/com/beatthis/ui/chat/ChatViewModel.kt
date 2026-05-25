package com.beatthis.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daw.ai.chat.*
import com.daw.ai.tools.DawTools
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Chat ViewModel — full streaming AI chat with DAW tool calling.
 * Ported from pulse-ai ChatViewModel, adapted for beat-this DAW control.
 */
class ChatViewModel : ViewModel() {

    private val apiKey = "sk_trNBfF4GS2SUBK8yTlAD2pS1SKxM6yGh"
    private val client = StreamingClient(apiKey)
    private val orchestrator = Orchestrator(client)
    private val model = "openai"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    /** Callback for executing DAW commands — set by the screen that hosts this VM */
    var dawExecutor: ((String, Map<String, Any?>) -> String)? = null

    fun send(text: String) {
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            content = text,
        )
        _messages.value = _messages.value + userMsg
        viewModelScope.launch { streamResponse() }
    }

    fun clear() { _messages.value = emptyList() }

    private suspend fun streamResponse() {
        _isStreaming.value = true
        val assistantId = UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(id = assistantId, role = Role.ASSISTANT, content = "", status = MessageStatus.STREAMING)

        val apiMessages = buildApiMessages()
        val tools = buildToolJson()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolsUsed = mutableListOf<String>()

        orchestrator.run(apiMessages, model, tools) { name, args ->
            _status.value = toolStatusLabel(name)
            if (name !in toolsUsed) toolsUsed.add(name)
            dawExecutor?.invoke(name, args) ?: "OK"
        }.collect { event ->
            when (event) {
                is StreamEvent.Delta -> { content.append(event.text); updateAssistant(assistantId, content.toString(), reasoning.toString(), toolsUsed) }
                is StreamEvent.Reasoning -> { reasoning.append(event.text); updateAssistant(assistantId, content.toString(), reasoning.toString(), toolsUsed) }
                is StreamEvent.ToolCalls -> _status.value = toolStatusLabel(event.calls.firstOrNull()?.name ?: "")
                is StreamEvent.ToolResult -> _status.value = null
                is StreamEvent.Status -> _status.value = toolStatusLabel(event.label)
                is StreamEvent.Error -> { content.append("\n⚠️ ${event.message}"); updateAssistant(assistantId, content.toString(), reasoning.toString(), toolsUsed) }
                is StreamEvent.Done -> _status.value = null
            }
        }

        _isStreaming.value = false
        _status.value = null
        _messages.value = _messages.value.map { if (it.id == assistantId) it.copy(status = MessageStatus.SENT) else it }
    }

    private fun updateAssistant(id: String, content: String, reasoning: String, tools: List<String>) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(content = content, reasoning = reasoning, toolsUsed = tools) else it
        }
    }

    private fun buildApiMessages(): List<JSONObject> {
        val system = JSONObject().put("role", "system").put("content", SYSTEM_PROMPT)
        val msgs = _messages.value.filter { it.role != Role.TOOL }.map { msg ->
            JSONObject().put("role", msg.role.name.lowercase()).put("content", msg.content)
        }
        return listOf(system) + msgs
    }

    private fun buildToolJson(): List<JSONObject> = DawTools.all.map { tool ->
        JSONObject().put("type", "function").put("function",
            JSONObject().put("name", tool.function.name)
                .put("description", tool.function.description)
                .put("parameters", JSONObject(tool.function.parameters.toString())))
    }

    private fun toolStatusLabel(name: String): String = when (name) {
        "set_tempo" -> "Setting tempo..."
        "add_track" -> "Adding track..."
        "remove_track" -> "Removing track..."
        "mute_track", "solo_track" -> "Updating track..."
        "set_volume", "set_pan" -> "Adjusting mix..."
        "add_effect", "remove_effect" -> "Updating effects..."
        "record" -> "Recording..."
        "play" -> "Playing..."
        "stop" -> "Stopping..."
        "generate_music" -> "Generating music..."
        "generate_vocals" -> "Generating vocals..."
        "export_mixdown" -> "Exporting..."
        else -> "Working..."
    }

    companion object {
        private const val SYSTEM_PROMPT = """You are the AI producer inside Beat This, an Android DAW. You help the user make music by controlling the DAW via tools.

You can: set tempo, add/remove tracks, mute/solo, adjust volume/pan, add effects, control playback, generate music and vocals, export, and undo/redo.

Guidelines:
- Be concise and musical. Short responses unless the user asks for detail.
- Use tools proactively — if the user says "add some reverb to track 1", just do it.
- Chain multiple tools when needed (e.g. "set up a beat" → add drum track + set tempo).
- After executing tools, briefly confirm what you did.
- For creative requests, suggest ideas then execute.
- Use markdown for any structured info."""
    }
}
