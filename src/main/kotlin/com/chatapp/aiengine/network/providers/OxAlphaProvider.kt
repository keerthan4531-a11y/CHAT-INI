package com.chatapp.aiengine.network.providers

import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.core.ThinkingStreamParser
import com.chatapp.aiengine.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Ox Alpha Provider (GLM-5.3-Flash) with native thinking/reasoning token streaming.
 * Handles automatic CSRF handshake and session cookies.
 */
class OxAlphaProvider(
    private val baseUrl: String = "https://oxalpha.com"
) : AiProvider {

    override val providerType: ProviderType = ProviderType.OX_ALPHA

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.removeAll { oldCookie -> cookies.any { it.name == oldCookie.name } }
            cookieStore.addAll(cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore

        fun clear() {
            cookieStore.clear()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var currentCsrfToken: String? = null

    /**
     * Initializes or refreshes session and extracts CSRF token.
     */
    private suspend fun initFreshSession(): Boolean = withContext(Dispatchers.IO) {
        cookieJar.clear()
        currentCsrfToken = null

        val request = Request.Builder()
            .url("$baseUrl/chat")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val html = response.body?.string() ?: return@withContext false

                val regex = """<meta name="csrf-token" content="([^"]+)"""".toRegex()
                val match = regex.find(html)
                currentCsrfToken = match?.groups?.get(1)?.value
                return@withContext currentCsrfToken != null
            }
        } catch (_: Exception) {
            return@withContext false
        }
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        // Auto-initialize session if needed or if forced
        if (currentCsrfToken == null || request.forceNewSession) {
            val ok = initFreshSession()
            if (!ok || currentCsrfToken == null) {
                val err = Exception("Failed to initialize Ox Alpha session / CSRF token.")
                trySend(StreamEvent.Error(err))
                close(err)
                return@callbackFlow
            }
        }

        val messagesArray = JSONArray()
        // Include multi-turn conversation history
        for (msg in request.history) {
            messagesArray.put(JSONObject().apply {
                put("role", msg.role.name.lowercase())
                put("content", msg.content)
            })
        }
        // Current user prompt
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", request.prompt)
        })

        val payload = JSONObject().apply {
            put("model", request.model.id)
            put("messages", messagesArray)
        }

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/event-stream, application/json, text/plain, */*")
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/chat")
            .header("X-CSRF-TOKEN", currentCsrfToken ?: "")
            .build()

        val call = client.newCall(httpRequest)

        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val err = Exception("Ox Alpha returned HTTP ${response.code}: ${response.message}")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val source = response.body?.source() ?: run {
                    val err = Exception("Empty response body from Ox Alpha")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val reader = BufferedReader(InputStreamReader(source.inputStream(), StandardCharsets.UTF_8))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data:")) {
                        val rawData = currentLine.substring(5).trim()
                        if (rawData == "[DONE]") {
                            break
                        }

                        try {
                            val chunk = JSONObject(rawData)
                            val choices = chunk.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                if (delta != null) {
                                    val reasoning = delta.optString("reasoning", "")
                                    val content = delta.optString("content", "")

                                    if (reasoning.isNotEmpty()) {
                                        parser.feedReasoningToken(reasoning)
                                    }

                                    if (content.isNotEmpty()) {
                                        parser.finishReasoning()
                                        parser.feedContentChunk(content)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Non-JSON or keepalive packet
                        }
                    }
                }

                parser.flush()
                val finalMsg = ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = parser.getFullContent(),
                    reasoningContent = parser.getFullThinking().ifEmpty { null }
                )
                trySend(StreamEvent.Completed(finalMsg))
                close()
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

