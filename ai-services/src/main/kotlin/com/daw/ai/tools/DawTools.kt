package com.daw.ai.tools

import com.daw.ai.client.Tool
import com.daw.ai.client.FunctionDef
import kotlinx.serialization.json.*

/** All DAW commands exposed to the LLM via function calling. */
object DawTools {

    val all: List<Tool> = listOf(
        tool("set_tempo", "Set project tempo in BPM", params {
            prop("bpm", "number", "Tempo in beats per minute (20-300)")
            required("bpm")
        }),
        tool("add_track", "Add a new track to the project", params {
            prop("type", "string", "Track type: audio, midi, or drum")
            prop("name", "string", "Optional track name")
            required("type")
        }),
        tool("remove_track", "Remove a track by number", params {
            prop("track", "integer", "Track number (1-based)")
            required("track")
        }),
        tool("mute_track", "Mute or unmute a track", params {
            prop("track", "integer", "Track number")
            prop("mute", "boolean", "True to mute, false to unmute")
            required("track")
        }),
        tool("solo_track", "Solo or unsolo a track", params {
            prop("track", "integer", "Track number")
            prop("solo", "boolean", "True to solo, false to unsolo")
            required("track")
        }),
        tool("set_volume", "Set track volume", params {
            prop("track", "integer", "Track number")
            prop("db", "number", "Volume in dB (-inf to +6)")
            required("track", "db")
        }),
        tool("set_pan", "Set track panning", params {
            prop("track", "integer", "Track number")
            prop("pan", "number", "Pan position (-1.0 left to 1.0 right)")
            required("track", "pan")
        }),
        tool("add_effect", "Add an effect to a track", params {
            prop("track", "integer", "Track number")
            prop("effect", "string", "Effect name: reverb, delay, compressor, eq, chorus, phaser, distortion, limiter, gate, filter")
            required("track", "effect")
        }),
        tool("remove_effect", "Remove an effect from a track", params {
            prop("track", "integer", "Track number")
            prop("slot", "integer", "Effect slot number (1-based)")
            required("track", "slot")
        }),
        tool("record", "Start recording on a track", params {
            prop("track", "integer", "Track number")
            required("track")
        }),
        tool("play", "Start playback", params {}),
        tool("stop", "Stop playback or recording", params {}),
        tool("loop", "Set loop region", params {
            prop("start_bar", "integer", "Loop start bar")
            prop("end_bar", "integer", "Loop end bar")
            required("start_bar", "end_bar")
        }),
        tool("generate_music", "Generate music from a text prompt using AI", params {
            prop("prompt", "string", "Description of the music to generate")
            prop("duration_sec", "integer", "Duration in seconds (3-300)")
            required("prompt")
        }),
        tool("generate_vocals", "Generate vocal audio from text", params {
            prop("text", "string", "Lyrics or text to sing/speak")
            prop("voice", "string", "Voice name (e.g. nova, shimmer, adam)")
            required("text")
        }),
        tool("set_time_signature", "Set time signature", params {
            prop("numerator", "integer", "Beats per bar")
            prop("denominator", "integer", "Beat unit (4=quarter, 8=eighth)")
            required("numerator", "denominator")
        }),
        tool("export_mixdown", "Export the project as an audio file", params {
            prop("format", "string", "Export format: wav, mp3, flac")
            required("format")
        }),
        tool("undo", "Undo the last action", params {}),
        tool("redo", "Redo the last undone action", params {}),
        tool("add_note", "Add a MIDI note to a track's piano roll", params {
            prop("track", "integer", "Track number")
            prop("pitch", "integer", "MIDI pitch (0-127, 60=C4)")
            prop("start_beat", "number", "Start position in beats (0-based)")
            prop("duration_beats", "number", "Duration in beats (e.g. 0.5 = eighth note)")
            prop("velocity", "integer", "Velocity 1-127 (default 90)")
            required("track", "pitch", "start_beat", "duration_beats")
        }),
        tool("remove_notes", "Remove all notes from a track's piano roll", params {
            prop("track", "integer", "Track number")
            required("track")
        }),
        tool("set_pattern_length", "Set the piano roll pattern length in bars", params {
            prop("bars", "integer", "Number of bars (1-16)")
            required("bars")
        }),
    )

    private fun tool(name: String, description: String, parameters: JsonObject) =
        Tool(function = FunctionDef(name = name, description = description, parameters = parameters))

    private fun params(block: ParamBuilder.() -> Unit): JsonObject =
        ParamBuilder().apply(block).build()

    private class ParamBuilder {
        private val props = mutableMapOf<String, JsonObject>()
        private val req = mutableListOf<String>()

        fun prop(name: String, type: String, description: String) {
            props[name] = buildJsonObject {
                put("type", type)
                put("description", description)
            }
        }

        fun required(vararg names: String) { req.addAll(names) }

        fun build(): JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
            if (req.isNotEmpty()) put("required", JsonArray(req.map { JsonPrimitive(it) }))
        }
    }
}
