package com.beatthis.audio

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Full CRUD for generated audio stems.
 * Stores audio files + JSON metadata sidecar.
 */
class StemRepository(context: Context) {

    private val dir = File(context.filesDir, "stems").also { it.mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Create: save audio bytes + metadata. Returns the Stem. */
    fun create(
        audioBytes: ByteArray,
        prompt: String,
        type: StemType,
        duration: Int? = null,
        seed: Long? = null,
        voice: String? = null
    ): Stem {
        val id = System.currentTimeMillis().toString()
        val ext = if (audioBytes.size > 4 && audioBytes[0] == 'I'.code.toByte()) "mp3" else "wav"
        val audioFile = File(dir, "$id.$ext")
        audioFile.writeBytes(audioBytes)

        val stem = Stem(
            id = id,
            name = prompt.take(40),
            filename = audioFile.name,
            type = type,
            prompt = prompt,
            duration = duration,
            seed = seed,
            voice = voice,
            sizeBytes = audioBytes.size.toLong(),
            createdAt = System.currentTimeMillis()
        )
        saveMeta(stem)
        return stem
    }

    /** Read: list all stems sorted by newest first. */
    fun list(): List<Stem> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<Stem>(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /** Read: get a single stem by id. */
    fun get(id: String): Stem? {
        val meta = File(dir, "$id.json")
        if (!meta.exists()) return null
        return runCatching { json.decodeFromString<Stem>(meta.readText()) }.getOrNull()
    }

    /** Get the audio file for a stem. */
    fun audioFile(stem: Stem): File = File(dir, stem.filename)

    /** Update: rename a stem. */
    fun rename(id: String, newName: String): Stem? {
        val stem = get(id) ?: return null
        val updated = stem.copy(name = newName)
        saveMeta(updated)
        return updated
    }

    /** Delete: remove audio + metadata. */
    fun delete(id: String): Boolean {
        val stem = get(id) ?: return false
        File(dir, stem.filename).delete()
        File(dir, "${id}.json").delete()
        return true
    }

    private fun saveMeta(stem: Stem) {
        File(dir, "${stem.id}.json").writeText(json.encodeToString(stem))
    }
}

@Serializable
data class Stem(
    val id: String,
    val name: String,
    val filename: String,
    val type: StemType,
    val prompt: String,
    val duration: Int? = null,
    val seed: Long? = null,
    val voice: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long = 0
)

@Serializable
enum class StemType { INSTRUMENTAL, VOCALS, SPEECH }
