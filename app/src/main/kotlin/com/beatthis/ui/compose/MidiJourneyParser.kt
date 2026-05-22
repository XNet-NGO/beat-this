package com.beatthis.ui.compose

import com.beatthis.engine.midi.Note
import com.beatthis.engine.midi.Pattern

/**
 * Parses MIDIjourney output into piano roll notes.
 * Handles:
 * 1. CSV notation: pitch,time,duration,velocity
 * 2. Chord progressions: | Dm9 | Cmaj7 | Bbmaj7 | A7 |
 * 3. Bass patterns: D – C – Bb – A
 */
object MidiJourneyParser {

    private val NOTE_MAP = mapOf(
        "C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11
    )

    /** Check if text contains importable musical content */
    fun hasImportableContent(text: String): Boolean {
        return hasChordProgression(text) || hasCsvNotation(text) || hasBassPattern(text)
    }

    private fun hasChordProgression(text: String) = Regex("""\|?\s*[A-G][#b]?\w*\s*\|""").containsMatchIn(text)
    private fun hasCsvNotation(text: String) = text.contains("pitch,time,duration,velocity")
    private fun hasBassPattern(text: String) = Regex("""[A-G][#b]?\s*[–—-]\s*[A-G][#b]?""").containsMatchIn(text)

    /** Parse all importable content from text into notes */
    fun parse(text: String): List<Note> {
        val notes = mutableListOf<Note>()

        // Try CSV notation first
        notes.addAll(parseCsvNotation(text))
        if (notes.isNotEmpty()) return notes

        // Parse chord progressions
        notes.addAll(parseChordProgressions(text))

        // Parse bass patterns
        notes.addAll(parseBassPatterns(text, startOctave = 2))

        return notes
    }

    /** Parse | Dm9 | Cmaj7 | Bbmaj7 | A7 | format */
    private fun parseChordProgressions(text: String): List<Note> {
        val notes = mutableListOf<Note>()
        val chordRegex = Regex("""([A-G][#b]?)(\w*)""")
        var barOffset = 0

        for (line in text.lines()) {
            // Strip markdown formatting
            val trimmed = line.trim().replace(Regex("""\*+"""), "").replace(Regex("""_+"""), "")
            if (!trimmed.contains("|") || !trimmed.contains(Regex("""[A-G]"""))) continue

            val segments = trimmed.split("|").filter { it.isNotBlank() }.map { it.trim() }

            for (segment in segments) {
                // Skip section markers like [Chorus], [Verse]
                if (segment.startsWith("[")) continue
                val match = chordRegex.find(segment) ?: continue
                val root = match.groupValues[1]
                val quality = match.groupValues[2]
                if (root.isEmpty()) continue

                val chordNotes = chordToNotes(root, quality, octave = 4)
                val tickStart = barOffset * Pattern.TICKS_PER_BAR

                for (pitch in chordNotes) {
                    notes.add(Note(pitch, tickStart, Pattern.TICKS_PER_BAR, 80))
                }
                barOffset++
            }
        }
        return notes
    }

    /** Parse bass patterns like "D – C – Bb – A" or "D → C → Bb → A" */
    private fun parseBassPatterns(text: String, startOctave: Int): List<Note> {
        val notes = mutableListOf<Note>()
        val bassRegex = Regex("""([A-G][#b]?)\s*[–—\-→]\s*(?:\(rest\)\s*[–—\-→]\s*)?([A-G][#b]?)\s*[–—\-→]\s*(?:\(rest\)\s*[–—\-→]\s*)?([A-G][#b]?)(?:\s*[–—\-→]\s*(?:\(rest\)\s*[–—\-→]\s*)?([A-G][#b]?))?""")

        var barOffset = 0
        for (line in text.lines()) {
            val trimmed = line.trim()
            // Look for lines with bass note sequences
            if (!trimmed.contains("–") && !trimmed.contains("→") && !trimmed.contains(" - ")) continue
            if (!Regex("""[A-G][#b]?\s*[–—\-→]""").containsMatchIn(trimmed)) continue
            // Skip chord progression lines (they have | delimiters)
            if (trimmed.startsWith("|")) continue

            // Extract individual notes from the pattern
            val noteNames = Regex("""[A-G][#b]?""").findAll(trimmed)
                .map { it.value }
                .filter { it.length <= 2 }
                .toList()

            if (noteNames.size < 2) continue

            val ticksPerNote = Pattern.TICKS_PER_BAR / noteNames.size.coerceAtLeast(1)
            for ((i, name) in noteNames.withIndex()) {
                val pitch = noteNameToMidi(name, startOctave)
                if (pitch != null) {
                    notes.add(Note(pitch, barOffset * Pattern.TICKS_PER_BAR + i * ticksPerNote, ticksPerNote, 90))
                }
            }
            barOffset++
        }
        return notes
    }

    /** Parse pitch,time,duration,velocity CSV */
    private fun parseCsvNotation(text: String): List<Note> {
        val notes = mutableListOf<Note>()
        var inNotation = false
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed == "pitch,time,duration,velocity") { inNotation = true; continue }
            if (!inNotation) continue
            if (trimmed.isBlank() || !trimmed[0].isDigit()) { if (trimmed.isNotBlank()) break; continue }
            val parts = trimmed.split(",").map { it.trim() }
            if (parts.size >= 4) {
                val pitch = parts[0].toIntOrNull() ?: continue
                val time = parts[1].toFloatOrNull() ?: continue
                val dur = parts[2].toFloatOrNull() ?: continue
                val vel = parts[3].toIntOrNull() ?: continue
                notes.add(Note(pitch.coerceIn(0, 127), (time * Pattern.TICKS_PER_BEAT).toInt(), (dur * Pattern.TICKS_PER_BEAT).toInt(), vel.coerceIn(0, 127)))
            }
        }
        return notes
    }

    /** Convert chord symbol to MIDI pitches */
    private fun chordToNotes(root: String, quality: String, octave: Int): List<Int> {
        val rootMidi = noteNameToMidi(root, octave) ?: return emptyList()
        val intervals = when {
            quality.contains("dim") -> listOf(0, 3, 6)
            quality.contains("aug") -> listOf(0, 4, 8)
            quality.contains("maj7") || quality.contains("maj9") -> listOf(0, 4, 7, 11)
            quality.contains("m7") || quality.contains("m9") || quality.contains("min7") -> listOf(0, 3, 7, 10)
            quality.contains("7") -> listOf(0, 4, 7, 10) // dominant 7
            quality.contains("9") && quality.startsWith("m") -> listOf(0, 3, 7, 10, 14)
            quality.contains("9") -> listOf(0, 4, 7, 10, 14)
            quality.contains("m") || quality.contains("min") -> listOf(0, 3, 7)
            quality.contains("sus4") -> listOf(0, 5, 7)
            quality.contains("sus2") -> listOf(0, 2, 7)
            else -> listOf(0, 4, 7) // major triad
        }
        return intervals.map { rootMidi + it }
    }

    /** Convert note name (C, D#, Bb, etc) to MIDI number at given octave */
    private fun noteNameToMidi(name: String, octave: Int): Int? {
        if (name.isEmpty()) return null
        val base = NOTE_MAP[name.substring(0, 1)] ?: return null
        val modifier = when {
            name.length > 1 && name[1] == '#' -> 1
            name.length > 1 && name[1] == 'b' -> -1
            else -> 0
        }
        return (octave + 1) * 12 + base + modifier
    }
}
