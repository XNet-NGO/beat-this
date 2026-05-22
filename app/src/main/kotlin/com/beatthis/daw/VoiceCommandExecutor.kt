package com.beatthis.daw

import com.daw.ai.client.ChatRequest
import com.daw.ai.client.Message
import com.daw.ai.client.PollinationsClient
import kotlinx.serialization.json.*

/**
 * Voice command executor — parses LLM function calls and executes DAW actions.
 * Implements the tool schema from SPEC.md.
 */
class VoiceCommandExecutor(private val engine: DawEngine) {

    private val toolSchema = """
    [
      {"type":"function","function":{"name":"add_track","description":"Add a new track","parameters":{"type":"object","properties":{"name":{"type":"string"},"track_type":{"type":"string","enum":["synth","sampler","audio"]}}}}},
      {"type":"function","function":{"name":"set_tempo","description":"Set BPM","parameters":{"type":"object","properties":{"bpm":{"type":"number"}}}}},
      {"type":"function","function":{"name":"play","description":"Start playback","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"stop","description":"Stop playback","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"record","description":"Start recording","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"mute_track","description":"Mute a track","parameters":{"type":"object","properties":{"track":{"type":"integer"}}}}},
      {"type":"function","function":{"name":"solo_track","description":"Solo a track","parameters":{"type":"object","properties":{"track":{"type":"integer"}}}}},
      {"type":"function","function":{"name":"set_volume","description":"Set track volume","parameters":{"type":"object","properties":{"track":{"type":"integer"},"volume":{"type":"number"}}}}},
      {"type":"function","function":{"name":"add_effect","description":"Add effect to track","parameters":{"type":"object","properties":{"track":{"type":"integer"},"effect":{"type":"string","enum":["reverb","delay","filter","distortion","chorus","phaser","compressor","eq"]}}}}},
      {"type":"function","function":{"name":"toggle_metronome","description":"Toggle metronome","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"toggle_loop","description":"Toggle loop","parameters":{"type":"object","properties":{}}}}
    ]
    """.trimIndent()

    /** Execute a voice command via LLM function calling */
    suspend fun execute(text: String, client: PollinationsClient): String {
        val response = client.chatCompletions(ChatRequest(
            model = "openai",
            messages = listOf(
                Message("system", "You are a DAW assistant. Call the appropriate function for the user's request. If no function matches, respond with text."),
                Message("user", text)
            ),
            tools = parseTools()
        ))

        val msg = response.choices.firstOrNull()?.message ?: return "No response"
        val toolCall = msg.toolCalls?.firstOrNull()

        if (toolCall != null) {
            val fn = toolCall.function ?: return "No function"
            val args = Json.parseToJsonElement(fn.arguments).jsonObject
            return executeFunction(fn.name, args)
        }

        return msg.content ?: "Done"
    }

    private fun executeFunction(name: String, args: JsonObject): String {
        return when (name) {
            "add_track" -> {
                val trackName = args["name"]?.jsonPrimitive?.content ?: "Track ${engine.tracks.value.size + 1}"
                val type = when (args["track_type"]?.jsonPrimitive?.content) {
                    "sampler" -> TrackType.SAMPLER
                    "audio" -> TrackType.AUDIO
                    else -> TrackType.SYNTH
                }
                engine.addTrack(trackName, type)
                "Added $type track: $trackName"
            }
            "set_tempo" -> {
                val bpm = args["bpm"]?.jsonPrimitive?.float ?: 120f
                engine.setTempo(bpm)
                "Tempo set to ${bpm.toInt()} BPM"
            }
            "play" -> { engine.play(); "Playing" }
            "stop" -> { engine.stop(); "Stopped" }
            "record" -> { engine.record(); "Recording" }
            "mute_track" -> {
                val id = args["track"]?.jsonPrimitive?.int ?: 0
                engine.getTrack(id)?.let { it.muted = !it.muted; "Track ${it.name} ${if (it.muted) "muted" else "unmuted"}" } ?: "Track not found"
            }
            "solo_track" -> {
                val id = args["track"]?.jsonPrimitive?.int ?: 0
                engine.getTrack(id)?.let { it.solo = !it.solo; "Track ${it.name} ${if (it.solo) "soloed" else "unsoloed"}" } ?: "Track not found"
            }
            "set_volume" -> {
                val id = args["track"]?.jsonPrimitive?.int ?: 0
                val vol = args["volume"]?.jsonPrimitive?.float ?: 0.8f
                engine.getTrack(id)?.let { it.volume = vol; "Track ${it.name} volume: ${(vol * 100).toInt()}%" } ?: "Track not found"
            }
            "add_effect" -> {
                val id = args["track"]?.jsonPrimitive?.int ?: 0
                val effectName = args["effect"]?.jsonPrimitive?.content ?: "reverb"
                val effectType = EffectType.entries.find { it.name.equals(effectName, true) } ?: EffectType.REVERB
                engine.getTrack(id)?.let {
                    it.effects.add(DawEffect(effectType))
                    "Added $effectName to ${it.name}"
                } ?: "Track not found"
            }
            "toggle_metronome" -> { engine.toggleMetronome(); "Metronome ${if (engine.metronomeEnabled.value) "on" else "off"}" }
            "toggle_loop" -> { engine.toggleLoop(); "Loop ${if (engine.loopEnabled.value) "on" else "off"}" }
            else -> "Unknown command: $name"
        }
    }

    private fun parseTools(): List<com.daw.ai.client.Tool> {
        val json = Json { ignoreUnknownKeys = true }
        val arr = json.parseToJsonElement(toolSchema).jsonArray
        return arr.map { el ->
            val fn = el.jsonObject["function"]!!.jsonObject
            com.daw.ai.client.Tool(
                function = com.daw.ai.client.FunctionDef(
                    name = fn["name"]!!.jsonPrimitive.content,
                    description = fn["description"]!!.jsonPrimitive.content,
                    parameters = fn["parameters"]!!.jsonObject
                )
            )
        }
    }
}
