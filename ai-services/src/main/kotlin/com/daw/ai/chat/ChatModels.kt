package com.daw.ai.chat

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val reasoning: String = "",
    val toolsUsed: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
)

enum class Role { USER, ASSISTANT, SYSTEM, TOOL }
enum class MessageStatus { SENT, STREAMING }

data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val rawArgs: String = "",
)

data class ToolResultInfo(
    val id: String,
    val name: String,
    val result: String,
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class ToolCalls(val calls: List<ToolCallInfo>) : StreamEvent
    data class ToolResult(val result: ToolResultInfo) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data class Done(val finishReason: String = "stop") : StreamEvent
    data class Status(val label: String) : StreamEvent
}
