package com.daw.ai.services

import com.daw.ai.client.*
import com.daw.ai.tools.DawTools
import kotlinx.serialization.json.Json

/**
 * Voice command pipeline: STT → LLM (function calling) → TTS feedback.
 * All cloud via Pollinations.
 */
class VoiceCommandService(private val client: PollinationsClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private val systemPrompt = """You are a DAW (Digital Audio Workstation) assistant. 
The user gives voice commands to control music production. 
Parse their intent and call the appropriate tool. 
Be concise. Only call tools — do not explain unless asked."""

    /** Full pipeline: audio bytes in → DAW command + spoken feedback out */
    suspend fun process(audioBytes: ByteArray): VoiceCommandResult {
        val transcript = client.transcribe(audioBytes)
        if (transcript.isBlank()) return VoiceCommandResult.Empty

        val response = client.chatCompletions(ChatRequest(
            model = "openai",
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = transcript)
            ),
            tools = DawTools.all
        ))

        val choice = response.choices.firstOrNull()?.message ?: return VoiceCommandResult.NoAction(transcript)
        val toolCall = choice.toolCalls?.firstOrNull() ?: return VoiceCommandResult.NoAction(transcript)
        val fn = toolCall.function ?: return VoiceCommandResult.NoAction(transcript)

        val feedbackText = buildFeedback(fn.name, fn.arguments)
        val feedbackAudio = client.tts(feedbackText)

        return VoiceCommandResult.Command(
            transcript = transcript,
            functionName = fn.name,
            arguments = fn.arguments,
            feedbackText = feedbackText,
            feedbackAudio = feedbackAudio
        )
    }

    /** Text-only pipeline (skip STT, for typed commands) */
    suspend fun processText(text: String): VoiceCommandResult {
        val response = client.chatCompletions(ChatRequest(
            model = "openai",
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = text)
            ),
            tools = DawTools.all
        ))

        val choice = response.choices.firstOrNull()?.message ?: return VoiceCommandResult.NoAction(text)
        val toolCall = choice.toolCalls?.firstOrNull() ?: return VoiceCommandResult.NoAction(text)
        val fn = toolCall.function ?: return VoiceCommandResult.NoAction(text)

        return VoiceCommandResult.Command(
            transcript = text,
            functionName = fn.name,
            arguments = fn.arguments,
            feedbackText = buildFeedback(fn.name, fn.arguments),
            feedbackAudio = null
        )
    }

    private fun buildFeedback(name: String, args: String): String {
        return when (name) {
            "set_tempo" -> "Tempo set"
            "add_track" -> "Track added"
            "remove_track" -> "Track removed"
            "mute_track" -> "Track muted"
            "solo_track" -> "Track soloed"
            "set_volume" -> "Volume set"
            "set_pan" -> "Pan set"
            "add_effect" -> "Effect added"
            "remove_effect" -> "Effect removed"
            "record" -> "Recording"
            "play" -> "Playing"
            "stop" -> "Stopped"
            "loop" -> "Loop set"
            "generate_music" -> "Generating music"
            "generate_vocals" -> "Generating vocals"
            "export_mixdown" -> "Exporting"
            "undo" -> "Undone"
            "redo" -> "Redone"
            else -> "Done"
        }
    }
}

sealed class VoiceCommandResult {
    object Empty : VoiceCommandResult()
    data class NoAction(val transcript: String) : VoiceCommandResult()
    data class Command(
        val transcript: String,
        val functionName: String,
        val arguments: String,
        val feedbackText: String,
        val feedbackAudio: ByteArray?
    ) : VoiceCommandResult()
}
