package com.beatthis.engine.midi

import kotlinx.serialization.Serializable

/** A single MIDI note event. */
@Serializable
data class Note(
    val pitch: Int,        // 0-127
    val startTick: Int,    // position in ticks (480 ticks per beat)
    val durationTicks: Int,
    val velocity: Int = 100 // 0-127
)

/** A pattern of notes (one clip on the timeline). */
@Serializable
data class Pattern(
    val id: Int,
    var name: String = "Pattern",
    val notes: MutableList<Note> = mutableListOf(),
    var lengthBars: Int = 4
) {
    val lengthTicks: Int get() = lengthBars * TICKS_PER_BAR

    companion object {
        const val TICKS_PER_BEAT = 480
        const val TICKS_PER_BAR = TICKS_PER_BEAT * 4 // assumes 4/4
    }
}

/** A drum step sequencer pattern. */
@Serializable
data class DrumPattern(
    val id: Int,
    var name: String = "Beat",
    val steps: Int = 16,
    val tracks: MutableList<DrumTrackRow> = mutableListOf()
)

@Serializable
data class DrumTrackRow(
    val name: String,       // e.g. "Kick", "Snare", "HiHat"
    val pitch: Int,         // MIDI note for this drum
    val hits: BooleanArray = BooleanArray(16) { false }
) {
    override fun equals(other: Any?) = other is DrumTrackRow && name == other.name
    override fun hashCode() = name.hashCode()
}

/** Full sequence: ordered list of patterns on a timeline. */
@Serializable
data class Sequence(
    val trackId: Int,
    val clips: MutableList<Clip> = mutableListOf()
)

@Serializable
data class Clip(
    val patternId: Int,
    var startTick: Int,
    var lengthTicks: Int
)
