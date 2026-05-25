package com.beatthis.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daw.ai.chat.*
import com.daw.ai.tools.DawTools
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val client = StreamingClient("sk_trNBfF4GS2SUBK8yTlAD2pS1SKxM6yGh")
    private val orchestrator = Orchestrator(client)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    var dawExecutor: ((String, Map<String, Any?>) -> String)? = null

    fun send(text: String) {
        _messages.value = _messages.value + ChatMessage(id = UUID.randomUUID().toString(), role = Role.USER, content = text)
        viewModelScope.launch { streamResponse() }
    }

    fun clear() { _messages.value = emptyList() }

    private suspend fun streamResponse() {
        _isStreaming.value = true
        val id = UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(id = id, role = Role.ASSISTANT, content = "", status = MessageStatus.STREAMING)

        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolsUsed = mutableListOf<String>()

        orchestrator.run(buildApiMessages(), MODEL, buildToolJson()) { name, args ->
            _status.value = toolLabel(name)
            if (name !in toolsUsed) toolsUsed.add(name)
            dawExecutor?.invoke(name, args) ?: "OK"
        }.collect { ev ->
            when (ev) {
                is StreamEvent.Delta -> { content.append(ev.text); update(id, content, reasoning, toolsUsed) }
                is StreamEvent.Reasoning -> { reasoning.append(ev.text); update(id, content, reasoning, toolsUsed) }
                is StreamEvent.ToolCalls -> _status.value = toolLabel(ev.calls.firstOrNull()?.name ?: "")
                is StreamEvent.ToolResult -> _status.value = null
                is StreamEvent.Status -> _status.value = toolLabel(ev.label)
                is StreamEvent.Error -> { content.append("\n⚠️ ${ev.message}"); update(id, content, reasoning, toolsUsed) }
                is StreamEvent.Done -> _status.value = null
            }
        }

        _isStreaming.value = false
        _status.value = null
        _messages.value = _messages.value.map { if (it.id == id) it.copy(status = MessageStatus.SENT) else it }
    }

    private fun update(id: String, content: StringBuilder, reasoning: StringBuilder, tools: List<String>) {
        _messages.value = _messages.value.map { if (it.id == id) it.copy(content = content.toString(), reasoning = reasoning.toString(), toolsUsed = tools.toList()) else it }
    }

    private fun buildApiMessages(): List<JSONObject> {
        val sys = JSONObject().put("role", "system").put("content", SYSTEM_PROMPT)
        return listOf(sys) + _messages.value.filter { it.role != Role.TOOL }.map {
            JSONObject().put("role", it.role.name.lowercase()).put("content", it.content)
        }
    }

    private fun buildToolJson(): List<JSONObject> = DawTools.all.map { t ->
        JSONObject().put("type", "function").put("function",
            JSONObject().put("name", t.function.name).put("description", t.function.description)
                .put("parameters", JSONObject(t.function.parameters.toString())))
    }

    private fun toolLabel(name: String) = when (name) {
        "generate_music" -> "Generating music..."
        "generate_vocals" -> "Generating vocals..."
        "export_mixdown" -> "Exporting..."
        else -> "Working..."
    }

    companion object {
        private const val MODEL = "mistral-4"
        private const val SYSTEM_PROMPT = """You are the AI producer inside Beat This, a professional Android DAW. You run natively on the user's device with direct control over their music project.

Personality: Musically knowledgeable, efficient, creative. You are a co-producer — not just an assistant. You make suggestions, take initiative on creative decisions, and execute confidently.

Tone: Concise and professional. Short sentences. No filler. Match the user's energy.

Capabilities (via tools):
- Transport: play, stop, record, loop, set tempo, set time signature
- Tracks: add/remove tracks (audio, midi, drum), mute/solo, volume/pan
- Effects: add/remove effects (reverb, delay, compressor, eq, chorus, phaser, distortion, limiter, gate, filter)
- Generation: generate music from text prompts, generate vocals from lyrics
- Export: mixdown to wav/mp3/flac
- Undo/redo

Rules:
- Use tools proactively. If the user says "add reverb to track 1", just do it.
- Chain multiple tools for complex requests (e.g. "set up a beat" → set tempo + add drum track).
- After executing tools, briefly confirm what you did in one line.
- For creative requests, suggest ideas then execute without waiting for confirmation.
- Use markdown for structured info (tables, lists, code blocks).
- Never explain what tools are available unless asked — just use them.
- If a tool fails, explain what happened and suggest an alternative."""
    }
}
