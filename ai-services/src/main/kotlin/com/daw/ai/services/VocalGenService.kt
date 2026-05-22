package com.daw.ai.services

import com.daw.ai.client.PollinationsClient

/** Generate vocal audio from text via Pollinations TTS models. */
class VocalGenService(private val client: PollinationsClient) {

    /** Fast multilingual TTS (cheapest). */
    suspend fun generate(text: String, voice: String = "nova"): ByteArray =
        client.tts(input = text, model = "qwen-tts", voice = voice)

    /** TTS with emotion/style control. */
    suspend fun generateExpressive(text: String, voice: String = "nova"): ByteArray =
        client.tts(input = text, model = "qwen-tts-instruct", voice = voice)

    /** High-quality ElevenLabs voice (premium). */
    suspend fun generatePremium(text: String, voice: String = "rachel"): ByteArray =
        client.tts(input = text, model = "elevenlabs", voice = voice)

    companion object {
        val VOICES = listOf(
            "alloy", "echo", "fable", "onyx", "nova", "shimmer",
            "ash", "ballad", "coral", "sage", "verse",
            "rachel", "bella", "charlotte", "sarah", "emily", "lily",
            "adam", "antoni", "josh", "sam", "daniel", "james", "liam"
        )
    }
}
