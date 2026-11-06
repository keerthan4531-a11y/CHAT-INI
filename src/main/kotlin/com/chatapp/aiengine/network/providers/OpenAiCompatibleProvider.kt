package com.chatapp.aiengine.network.providers

import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.core.ThinkingStreamParser
import com.chatapp.aiengine.model.*
import com.chatapp.aiengine.network.HttpClientProvider
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
 * Standard OpenAI-compatible Provider.
 * Powers direct connections to:
 * - DeepSeek Official API (deepseek-chat, deepseek-reasoner R1)
 * - OpenRouter (routes to 200+ models with web search plugins)
 * - Groq (ultra high-speed inference)
 * - Local Ollama / vLLM servers
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient,
    private val endpointUrl: String = "https://api.deepseek.com/chat/completions",
    private val apiKey: String = ""
) : AiProvider {

    override val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        if (request.enableWebSearch) {
            trySend(StreamEvent.WebSearchStarted("Searching live web data..."))
        }

        val messagesArray = JSONArray()
        for (m in request.history) {
            messagesArray.put(JSONObject().apply {
                put("role", m.role.name.lowercase())
                put("content", m.content)
            })
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", request.prompt)
        })

        val payload = JSONObject().apply {
            put("model", request.model.id)
            put("messages", messagesArray)
            put("stream", true)
            put("temperature", request.temperature)
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")

        if (apiKey.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val call = client.newCall(reqBuilder.build())

        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        val citations = mutableListOf<WebCitation>()

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val err = Exception("Provider returned HTTP ${response.code}: ${response.message}")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val bodyStream = response.body?.source()?.inputStream()
                if (bodyStream == null) {
                    val err = Exception("Empty response body from stream")
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
                                    // 1. DeepSeek R1 / OpenAI o1 reasoning tokens
                                    val reasoning = delta.optString("reasoning_content", "")
                                        .ifEmpty { delta.optString("reasoning", "") }

                                    if (reasoning.isNotEmpty()) {
                                        parser.feedReasoningToken(reasoning)
                                    }

                                    // 2. Normal response content
                                    val content = delta.optString("content", "")
                                    if (content.isNotEmpty()) {
                                        parser.finishReasoning()
                                        parser.feedContentChunk(content)
                                    }
                                }
                            }

                            // 3. Citations if provided by OpenRouter / search plugins
                            val citationsJson = chunk.optJSONArray("citations")
                            if (citationsJson != null && citationsJson.length() > 0) {
                                for (i in 0 until citationsJson.length()) {
                                    val item = citationsJson.optJSONObject(i) ?: continue
                                    citations.add(
                                        WebCitation(
                                            title = item.optString("title", ""),
                                            url = item.optString("url", ""),
                                            snippet = item.optString("snippet", "")
                                        )
                                    )
                                }
                                trySend(StreamEvent.WebSearchCompleted(citations))
                            }
                        } catch (_: Exception) {}
                    },
                    onComplete = {
                        parser.flush()
                        val finalMsg = ChatMessage(
                            role = ChatRole.ASSISTANT,
                            content = parser.getFullContent(),
                            reasoningContent = parser.getFullThinking().ifEmpty { null },
                            citations = citations
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

