package com.daw.ai.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatRequest(
    val model: String = "openai",
    val messages: List<Message>,
    val tools: List<Tool>? = null,
    val temperature: Double? = null
)

@Serializable
data class Message(val role: String, val content: String? = null, @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null)

@Serializable
data class Tool(val type: String = "function", val function: FunctionDef)

@Serializable
data class FunctionDef(val name: String, val description: String, val parameters: JsonObject)

@Serializable
data class ChatResponse(val choices: List<Choice>)

@Serializable
data class Choice(val message: Message? = null, @SerialName("finish_reason") val finishReason: String? = null)

@Serializable
data class ToolCall(val id: String? = null, val type: String? = null, val function: FunctionCallData? = null)

@Serializable
data class FunctionCallData(val name: String, val arguments: String)

@Serializable
data class AudioSpeechRequest(val input: String, val model: String, val voice: String)

@Serializable
data class TranscriptionResponse(val text: String)

@Serializable
data class BalanceResponse(val balance: Double)
