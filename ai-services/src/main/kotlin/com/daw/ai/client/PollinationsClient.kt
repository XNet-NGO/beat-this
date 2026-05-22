package com.daw.ai.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
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
    }

    suspend fun chatCompletions(request: ChatRequest): ChatResponse =
        http.post("$BASE/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }.body()

    suspend fun audio(prompt: String, model: String = "qwen-tts", voice: String? = null): ByteArray =
        http.get("$BASE/audio/${prompt.encodeURLPath()}") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            parameter("model", model)
            voice?.let { parameter("voice", it) }
        }.readBytes()

    suspend fun tts(input: String, model: String = "qwen-tts", voice: String = "nova"): ByteArray =
        http.post("$BASE/v1/audio/speech") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(AudioSpeechRequest(input = input, model = model, voice = voice))
        }.readBytes()

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

    suspend fun balance(): Double =
        http.get("$BASE/account/balance") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }.body<BalanceResponse>().balance

    fun close() = http.close()
}
