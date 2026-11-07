package com.chatapp.aiengine.network.providers

import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.core.ThinkingStreamParser
import com.chatapp.aiengine.model.*
import com.chatapp.aiengine.network.HttpClientProvider
import com.chatapp.aiengine.network.ProxyPool
import com.chatapp.aiengine.network.SseStreamReader
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * ChatGPT Free Provider — communicates with https://chatgpt-proxy-chi-five.vercel.app
 * Provides real-time streaming for GPT-6 without API keys or login requirements.
 */
class ChatGptFreeProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient,
    private val baseUrl: String = "https://chatgpt-proxy-chi-five.vercel.app",
    private val proxyPool: ProxyPool? = null
) : AiProvider {

    override val providerType: ProviderType = ProviderType.CHATGPT_FREE

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        if (request.enableWebSearch) {
            trySend(StreamEvent.WebSearchStarted("Searching with GPT-6..."))
        }

        val messagesArray = JSONArray()
        for (m in request.history) {
            val content = m.content.trim()
            if (content.isNotEmpty()) {
                messagesArray.put(JSONObject().apply {
                    put("role", m.role.name.lowercase())
                    put("content", content)
                })
            }
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", request.prompt.trim())
        })

        val payload = JSONObject().apply {
            put("model", request.model.id)
            put("messages", messagesArray)
            put("stream", true)
        }

        val endpoint = if (baseUrl.endsWith("/v1/chat/completions")) {
            baseUrl
        } else {
            "${baseUrl.trimEnd('/')}/v1/chat/completions"
        }

        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val activeClient = proxyPool?.httpClientFor(request.proxy) ?: this@ChatGptFreeProvider.client
        val call = activeClient.newCall(httpRequest)

        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawError = response.body?.string().orEmpty()
                    val detail = if (rawError.isNotBlank()) rawError.take(200) else response.message
                    val err = Exception("ChatGPT Free returned HTTP ${response.code}: $detail")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val bodyStream = response.body?.source()?.inputStream()
                if (bodyStream == null) {
                    val err = Exception("Empty response body from ChatGPT Free")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                SseStreamReader.readStream(
                    inputStream = bodyStream,
                    onDataPayload = { rawData ->
                        try {
                            val chunk = JSONObject(rawData)
                            val choices = chunk.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                if (delta != null) {
                                    val reasoning = delta.optString("reasoning_content", "")
                                        .ifEmpty { delta.optString("reasoning", "") }

                                    if (reasoning.isNotEmpty()) {
                                        parser.feedReasoningToken(reasoning)
                                    }

                                    val content = delta.optString("content", "")
                                    if (content.isNotEmpty()) {
                                        parser.finishReasoning()
                                        parser.feedContentChunk(content)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    },
                    onComplete = {
                        parser.flush()
                        val finalMsg = ChatMessage(
                            role = ChatRole.ASSISTANT,
                            content = parser.getFullContent(),
                            reasoningContent = parser.getFullThinking().ifEmpty { null }
                        )
                        trySend(StreamEvent.Completed(finalMsg))
                        close()
                    }
                )
            }
        } catch (e: Exception) {
            parser.flush()
            trySend(
                StreamEvent.Error(
                    throwable = e,
                    partialContent = parser.getFullContent(),
                    partialThinking = parser.getFullThinking()
                )
            )
            close(e)
        }

        awaitClose {
            call.cancel()
        }
    }
}
