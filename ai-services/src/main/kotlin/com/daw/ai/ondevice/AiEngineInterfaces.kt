package com.daw.ai.ondevice

/**
 * Common interface for AI services — allows swapping between cloud (Pollinations) and on-device.
 */
interface SttEngine {
    suspend fun transcribe(audioBytes: ByteArray): String
    fun isAvailable(): Boolean
}

interface LlmEngine {
    suspend fun complete(prompt: String, tools: String? = null): String
    fun isAvailable(): Boolean
}

interface TtsEngine {
    suspend fun synthesize(text: String): ByteArray
    fun isAvailable(): Boolean
}
