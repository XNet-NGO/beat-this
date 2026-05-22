package com.beatthis.engine

/**
 * Manages tracks. Stub — will delegate to MWEngine instruments/channels.
 */
class TrackManager {

    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    fun getTracks(): List<Track> = tracks.toList()

    fun addTrack(name: String = "Track $nextId", type: TrackType = TrackType.AUDIO): Track {
        val track = Track(id = nextId++, name = name, type = type)
        tracks.add(track)
        return track
    }

    fun removeTrack(id: Int) { tracks.removeAll { it.id == id } }
    fun getTrack(id: Int): Track? = tracks.find { it.id == id }
}

data class Track(
    val id: Int,
    var name: String,
    val type: TrackType,
    var volume: Float = 1f,
    var pan: Float = 0f,
    var muted: Boolean = false,
    var soloed: Boolean = false
)

enum class TrackType { AUDIO, MIDI, DRUM }
