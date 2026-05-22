package com.daw.ai.settings

/** AI service configuration. Persisted by the app layer (DataStore/SharedPrefs). */
data class AiSettings(
    val apiKey: String = "",
    val pollenBalance: Double = 0.0,
    val sttModel: String = "whisper-large-v3",
    val llmModel: String = "openai",
    val ttsModel: String = "qwen-tts",
    val ttsVoice: String = "nova",
    val musicModel: String = "acestep",
    val compositionModel: String = "midijourney",
    val showCostBeforeGeneration: Boolean = true,
    val onDeviceStt: Boolean = false,
    val onDeviceLlm: Boolean = false,
    val onDeviceTts: Boolean = false,
)
