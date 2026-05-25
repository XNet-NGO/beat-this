package com.daw.ai.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SSE streaming Pollinations client — ported from pulse-ai core-network.
 * Handles retries, think-tag extraction, and tool call accumulation.
 */
class StreamingClient(private val apiKey: String) {

    companion object {
        private const val BASE_URL = "https://gen.pollinations.ai/v1"
        private val JSON_MT = "application/json; charset=utf-8".toMediaType()
        private val REASONING_TAGS = listOf("think", "thinking", "thought")
        private const val MAX_RETRIES = 3

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
                .build()
        }

        private fun isTransientError(msg: String): Boolean {
            val lower = msg.lowercase()
            return lower.contains("connection reset") || lower.contains("stream was reset") ||
                lower.contains("unexpected end of stream") || lower.contains("broken pipe") ||
                lower.contains("timeout") || lower.contains("failed to connect")
        }
    }

    fun stream(
        messages: List<JSONObject>,
        model: String,
        tools: List<JSONObject> = emptyList(),
    ): Flow<StreamEvent> = callbackFlow {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("reasoning_effort", "high")
            put("temperature", 0.3)
            put("top_p", 0.9)
            put("max_tokens", 16384)
            put("frequency_penalty", 0.1)
            put("presence_penalty", 0.1)
            put("messages", JSONArray(messages))
            if (tools.isNotEmpty()) put("tools", JSONArray(tools))
        }.toString()

        val contentSoFar = StringBuilder()
        var retries = 0
        var inThinkTag = false
        var thinkTagName = ""
        var sseError: String? = null
        var sseDone = false

        while (true) {
            sseError = null
            sseDone = false
            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Connection", "close")
                .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
                .post(body.toRequestBody(JSON_MT))
                .build()

            val toolAcc = mutableMapOf<Int, Triple<String, String, StringBuilder>>()
            val contentBeforeAttempt = contentSoFar.length
            val latch = CountDownLatch(1)

            val es = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        if (toolAcc.isNotEmpty()) trySend(StreamEvent.ToolCalls(buildToolCalls(toolAcc)))
                        else trySend(StreamEvent.Done())
                        sseDone = true; latch.countDown(); return
                    }
                    val chunk = try { JSONObject(data) } catch (_: Exception) { return }
                    chunk.optJSONObject("error")?.let { sseError = it.optString("message", "Unknown error"); latch.countDown(); return }
                    val choices = chunk.optJSONArray("choices") ?: return
                    if (choices.length() == 0) return
                    val choice = choices.getJSONObject(0)
                    val delta = choice.optJSONObject("delta") ?: return
                    val fr = choice.optString("finish_reason", "").takeIf { it.isNotBlank() && it != "null" }

                    delta.optJSONArray("tool_calls")?.let { tcArr ->
                        for (i in 0 until tcArr.length()) {
                            val tc = tcArr.getJSONObject(i)
                            val idx = tc.optInt("index", 0)
                            val acc = toolAcc.getOrPut(idx) { Triple("", "", StringBuilder()) }
                            val tcId = tc.optString("id", "")
                            val fn = tc.optJSONObject("function")
                            val name = fn?.optString("name", "") ?: ""
                            val args = fn?.optString("arguments", "") ?: ""
                            toolAcc[idx] = Triple(
                                if (tcId.isNotEmpty()) tcId else acc.first,
                                if (name.isNotEmpty()) name else acc.second,
                                acc.third.append(args),
                            )
                        }
                    }

                    if (fr == "tool_calls" || (fr == "stop" && toolAcc.isNotEmpty())) {
                        trySend(StreamEvent.ToolCalls(buildToolCalls(toolAcc))); sseDone = true; latch.countDown(); return
                    }
                    if (fr == "stop" || fr == "end_turn" || fr == "length") {
                        trySend(StreamEvent.Done(fr)); sseDone = true; latch.countDown(); return
                    }

                    var content = delta.optString("content", "").let { if (it == "null") "" else it }
                    var reasoning = delta.optString("reasoning_content", "").let { if (it == "null") "" else it }
                        .ifBlank { delta.optString("reasoning", "").let { if (it == "null") "" else it } }

                    if (!inThinkTag) {
                        for (tag in REASONING_TAGS) {
                            if (content.contains("<$tag>")) { inThinkTag = true; thinkTagName = tag; content = content.substringAfter("<$tag>"); break }
                        }
                    }
                    if (inThinkTag) {
                        val close = "</$thinkTagName>"
                        if (content.contains(close)) { reasoning = content.substringBefore(close); content = content.substringAfter(close); inThinkTag = false }
                        else { reasoning = content; content = "" }
                    }

                    if (content.isNotEmpty()) contentSoFar.append(content)
                    val shouldEmit = contentSoFar.length > contentBeforeAttempt || reasoning.isNotEmpty()
                    if (shouldEmit) {
                        if (content.isNotEmpty()) trySend(StreamEvent.Delta(content))
                        if (reasoning.isNotEmpty()) trySend(StreamEvent.Reasoning(reasoning))
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (!sseDone) sseError = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "Connection failed"
                    latch.countDown()
                }
                override fun onClosed(eventSource: EventSource) { latch.countDown() }
            })

            val latchOk = latch.await(180, TimeUnit.SECONDS)
            es.cancel()
            if (!latchOk && !sseDone) sseError = "Stream timeout (180s)"
            if (sseError == null || sseDone) break
            if (!isTransientError(sseError!!) || sseError!!.startsWith("HTTP 4")) break
            if (retries < MAX_RETRIES) { retries++; Thread.sleep(1000L * retries); continue }
            break
        }

        if (sseError != null && !sseDone) trySend(StreamEvent.Error(sseError!!))
        channel.close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private fun buildToolCalls(acc: Map<Int, Triple<String, String, StringBuilder>>): List<ToolCallInfo> =
        acc.values.map { (id, name, argsSb) ->
            val argsStr = argsSb.toString()
            val args: Map<String, Any?> = try {
                val j = JSONObject(argsStr); j.keys().asSequence().associateWith { k -> j.opt(k) }
            } catch (_: Exception) { emptyMap() }
            ToolCallInfo(id = id, name = name, arguments = args, rawArgs = argsStr)
        }
}
