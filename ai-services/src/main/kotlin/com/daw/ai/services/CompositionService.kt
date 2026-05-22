package com.daw.ai.services

import com.daw.ai.client.*

/** AI composition assistant via MIDIjourney model. */
class CompositionService(private val client: PollinationsClient) {

    private val systemPrompt = """You are MIDIjourney, an AI music composition assistant.
Help the user create chord progressions, melodies, arrangements, and song structures.
When suggesting musical content, output in a structured format:
- Chords: list with bar positions
- Melody: note names with durations
- Structure: sections (intro, verse, chorus, bridge, outro) with bar counts
Be creative but respect the user's genre/style preferences."""

    /** Ask the composition assistant for musical suggestions. */
    suspend fun suggest(prompt: String): String {
        val response = client.chatCompletions(ChatRequest(
            model = "midijourney",
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = prompt)
            )
        ))
        return response.choices.firstOrNull()?.message?.content ?: ""
    }

    /** Generate a chord progression for a given key/genre. */
    suspend fun chords(key: String, genre: String, bars: Int = 8): String =
        suggest("Write a $bars-bar chord progression in $key for $genre music")

    /** Suggest a melody over given chords. */
    suspend fun melody(chords: String, style: String = ""): String =
        suggest("Write a melody over these chords: $chords. Style: $style")

    /** Suggest a full song structure. */
    suspend fun structure(genre: String, durationMin: Int = 3): String =
        suggest("Suggest a song structure for a $durationMin-minute $genre track with section lengths")
}
