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

/**
 * Pollinations.ai REST client.
 * Base URL: https://gen.pollinations.ai
 */
class PollinationsClient(private val apiKey: String) {

    companion object {
        const val BASE = "https://gen.pollinations.ai"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(this@PollinationsClient.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000  // 90s for acestep
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000
        }
    }

    /** POST /v1/chat/completions */
    suspend fun chatCompletions(request: ChatRequest): ChatResponse =
        http.post("$BASE/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }.body()

    /** GET /audio/{text} — TTS or music. Returns audio bytes. */
    suspend fun audio(text: String, model: String = "qwen-tts", voice: String? = null): ByteArray =
        http.get("$BASE/audio/${text.encodeURLPath()}") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            parameter("model", model)
            voice?.let { parameter("voice", it) }
        }.readBytes()

    /** POST /v1/audio/speech — for acestep with lyrics or TTS */
    suspend fun audioSpeech(input: String, model: String = "acestep", voice: String? = null): ByteArray =
        http.post("$BASE/v1/audio/speech") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(AudioSpeechRequest(input = input, model = model, voice = voice ?: "alloy"))
        }.readBytes()

    /** Alias for backward compat with services */
    suspend fun tts(input: String, model: String = "qwen-tts", voice: String = "nova"): ByteArray =
        audio(input, model, voice)

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

    /** GET /account/balance */
    suspend fun balance(): Double =
        http.get("$BASE/account/balance") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }.body<BalanceResponse>().balance

    fun close() = http.close()
}
