package com.daw.ai.companion

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * REST client for the Beat This companion server.
 * Server runs on user's PC, hosts heavy AI models (Demucs, ACE-Step, Magenta).
 *
 * API Protocol:
 *   POST /stems       — separate audio into stems (Demucs)
 *   POST /generate    — generate music (ACE-Step 3.5B)
 *   POST /jam/start   — start live accompaniment (Magenta RealTime)
 *   POST /jam/stop    — stop live accompaniment
 *   GET  /health      — server health check
 */
class CompanionClient(private val server: CompanionServer) {

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    /** Health check. */
    suspend fun health(): Boolean = try {
        http.get("${server.baseUrl}/health").status == HttpStatusCode.OK
    } catch (_: Exception) { false }

    /** Separate audio into stems via Demucs. Returns map of stem name → audio bytes. */
    suspend fun separateStems(audioBytes: ByteArray): StemsResponse =
        http.post("${server.baseUrl}/stems") {
            setBody(MultiPartFormDataContent(formData {
                append("file", audioBytes, Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"input.wav\"")
                })
            }))
        }.body()

    /** Generate music via ACE-Step (full 3.5B model on GPU). */
    suspend fun generateMusic(request: MusicGenRequest): ByteArray =
        http.post("${server.baseUrl}/generate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.readBytes()

    /** Start live AI accompaniment. */
    suspend fun startJam(request: JamRequest): JamResponse =
        http.post("${server.baseUrl}/jam/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Stop live AI accompaniment. */
    suspend fun stopJam() {
        http.post("${server.baseUrl}/jam/stop")
    }

    fun close() = http.close()
}

@Serializable
data class StemsResponse(
    val vocals: String? = null,  // base64 or URL to download
    val drums: String? = null,
    val bass: String? = null,
    val other: String? = null
)

@Serializable
data class MusicGenRequest(
    val prompt: String,
    val duration_sec: Int = 30,
    val lyrics: String? = null,
    val style: String? = null
)

@Serializable
data class JamRequest(
    val tempo: Float = 120f,
    val key: String = "C",
    val style: String = "ambient"
)

@Serializable
data class JamResponse(
    val sessionId: String,
    val streamUrl: String // WebSocket URL for live audio stream
)
