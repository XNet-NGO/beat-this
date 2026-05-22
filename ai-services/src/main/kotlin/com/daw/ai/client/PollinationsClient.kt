package com.daw.ai.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class PollinationsClient(private val apiKey: String) {

    companion object {
        const val BASE = "https://gen.pollinations.ai"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(this@PollinationsClient.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    /** POST /v1/chat/completions */
    suspend fun chatCompletions(request: ChatRequest): ChatResponse =
        http.post("$BASE/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }.body()

    /**
     * GET /audio/{text}?model=acestep — full ACE-Step music generation.
     * @param text prompt or lyrics (with [verse]/[chorus] tags for vocals)
     * @param duration seconds (default server-side ~15s)
     * @param seed 0=default, -1=random/no-cache, or specific seed for reproducibility
     * @param voice voice hint (optional)
     */
    suspend fun acestep(
        text: String,
        duration: Int? = null,
        seed: Long? = null,
        voice: String? = null
    ): ByteArray = http.get("$BASE/audio/${text.encodeURLPath()}") {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        parameter("model", "acestep")
        duration?.let { parameter("duration", it) }
        seed?.let { parameter("seed", it) }
        voice?.let { parameter("voice", it) }
    }.readBytes()

    /** GET /audio/{text} — TTS */
    suspend fun audio(text: String, model: String = "qwen-tts", voice: String? = null): ByteArray =
        http.get("$BASE/audio/${text.encodeURLPath()}") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            parameter("model", model)
            voice?.let { parameter("voice", it) }
        }.readBytes()

    /** Alias for services compat */
    suspend fun tts(input: String, model: String = "qwen-tts", voice: String = "nova"): ByteArray =
        audio(input, model, voice)

    /** POST /v1/audio/speech — OpenAI-compatible (acestep or tts) */
    suspend fun audioSpeech(input: String, model: String = "acestep", voice: String? = null, seed: Long? = null): ByteArray =
        http.post("$BASE/v1/audio/speech") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(AudioSpeechRequest(input = input, model = model, voice = voice ?: "alloy"))
        }.readBytes()

    /** POST /v1/audio/transcriptions — STT */
    suspend fun transcribe(audioBytes: ByteArray, model: String = "whisper-large-v3"): String =
        http.post("$BASE/v1/audio/transcriptions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(MultiPartFormDataContent(formData {
                append("file", audioBytes, Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                })
                append("model", model)
            }))
        }.body<TranscriptionResponse>().text

    fun close() = http.close()
}
