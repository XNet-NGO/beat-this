package com.daw.ai.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Orchestrator — runs the streaming tool-calling loop.
 * Ported from pulse-ai core-network. Streams LLM output, executes tool calls,
 * feeds results back, and loops until the model stops calling tools.
 */
class Orchestrator(private val client: StreamingClient) {

    companion object {
        private const val MAX_ROUNDS = 20
        private const val MAX_RESULT_LEN = 8000
    }

    fun run(
        messages: List<JSONObject>,
        model: String,
        tools: List<JSONObject>,
        executor: suspend (String, Map<String, Any?>) -> String,
    ): Flow<StreamEvent> = flow {
        val raw = messages.toMutableList()
        var lastToolKey = ""
        var sameCount = 0

        for (round in 0 until MAX_ROUNDS) {
            val events = mutableListOf<StreamEvent>()
            client.stream(raw, model, tools).collect { ev -> events.add(ev); emit(ev) }

            val tcEvent = events.filterIsInstance<StreamEvent.ToolCalls>().lastOrNull() ?: break

            // Loop detection
            val key = "${tcEvent.calls.firstOrNull()?.name}:${tcEvent.calls.firstOrNull()?.rawArgs}"
            if (key == lastToolKey) { sameCount++; if (sameCount >= 3) { emit(StreamEvent.Done("tool_loop")); return@flow } }
            else { lastToolKey = key; sameCount = 1 }

            // Append assistant message with tool_calls
            raw.add(JSONObject().apply {
                put("role", "assistant")
                put("content", JSONObject.NULL)
                put("tool_calls", JSONArray().apply {
                    tcEvent.calls.forEach { c ->
                        put(JSONObject().put("id", c.id).put("type", "function")
                            .put("function", JSONObject().put("name", c.name).put("arguments", c.rawArgs)))
                    }
                })
            })

            // Execute tools
            emit(StreamEvent.Status(tcEvent.calls.firstOrNull()?.name ?: ""))
            val results = executeCalls(tcEvent.calls, executor)
            for (r in results) {
                emit(StreamEvent.ToolResult(r))
                val content = if (r.result.length > MAX_RESULT_LEN) r.result.take(MAX_RESULT_LEN) + "...(truncated)" else r.result
                raw.add(JSONObject().put("role", "tool").put("tool_call_id", r.id).put("content", content))
            }
        }
    }

    private suspend fun executeCalls(
        calls: List<ToolCallInfo>,
        executor: suspend (String, Map<String, Any?>) -> String,
    ): List<ToolResultInfo> = coroutineScope {
        calls.map { c ->
            async(Dispatchers.IO) {
                val result = try { executor(c.name, c.arguments) } catch (e: Exception) { "Error: ${e.message}" }
                ToolResultInfo(id = c.id, name = c.name, result = result.ifBlank { "OK" })
            }
        }.map { it.await() }
    }
}
