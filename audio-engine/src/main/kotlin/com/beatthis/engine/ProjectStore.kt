package com.beatthis.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Project save/load in JSON format.
 */
object ProjectStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun save(file: File, project: Project) {
        file.writeText(json.encodeToString(project))
    }

    fun load(file: File): Project {
        return json.decodeFromString(file.readText())
    }
}

@Serializable
data class Project(
    val name: String = "Untitled",
    val tempo: Float = 120f,
    val timeSignatureNum: Int = 4,
    val timeSignatureDen: Int = 4,
    val sampleRate: Int = 44100,
    val tracks: List<TrackState> = emptyList()
)

@Serializable
data class TrackState(
    val id: Int,
    val name: String,
    val type: String, // "audio", "midi", "drum"
    val volume: Float = 1f,
    val pan: Float = 0f,
    val muted: Boolean = false,
    val audioFile: String? = null, // relative path to WAV
    val effects: List<EffectState> = emptyList()
)

@Serializable
data class EffectState(
    val type: String, // "reverb", "delay", "compressor", etc.
    val params: Map<String, Float> = emptyMap()
)
