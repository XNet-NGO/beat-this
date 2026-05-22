package com.daw.ai.services

import com.daw.ai.client.PollinationsClient

/** Generate music via Pollinations ACE-Step model. */
class MusicGenService(private val client: PollinationsClient) {

    /**
     * Generate music from a text prompt.
     * @param prompt Description (e.g. "upbeat lo-fi hip hop beat, 90 bpm, chill vibes")
     * @param durationSec Desired duration (3-300). Model may not honor exactly.
     * @param lyrics Optional lyrics for vocal tracks.
     * @return Raw audio bytes (mp3)
     */
    suspend fun generate(
        prompt: String,
        durationSec: Int = 30,
        lyrics: String? = null
    ): ByteArray {
        val fullPrompt = buildString {
            append(prompt)
            if (lyrics != null) append("\n[Lyrics]\n$lyrics")
            append("\nDuration: ${durationSec}s")
        }
        return client.audio(fullPrompt, model = "acestep")
    }

    /** Generate with ElevenLabs Music (premium, higher quality). */
    suspend fun generatePremium(prompt: String, durationSec: Int = 30): ByteArray {
        val fullPrompt = "$prompt\nDuration: ${durationSec}s"
        return client.audio(fullPrompt, model = "elevenmusic")
    }
}
