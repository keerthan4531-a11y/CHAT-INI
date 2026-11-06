package com.chatapp.aiengine.network.providers

import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.core.ThinkingStreamParser
import com.chatapp.aiengine.model.*
import com.chatapp.aiengine.network.ProxyPool
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
    private val baseUrl: String = "https://oxalpha.com",
    private val proxyPool: ProxyPool? = null
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

    private fun clientFor(proxy: ProxyEndpoint?): OkHttpClient =
        if (proxy == null) client
        else proxyPool?.applyProxy(client.newBuilder(), proxy)?.build() ?: client

    /**
     * Initializes or refreshes session and extracts CSRF token.
     */
    private suspend fun initFreshSession(proxy: ProxyEndpoint? = null): Boolean = withContext(Dispatchers.IO) {
        cookieJar.clear()
        currentCsrfToken = null

        val request = Request.Builder()
            .url("$baseUrl/chat")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        try {
            clientFor(proxy).newCall(request).execute().use { response ->
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
            val ok = initFreshSession(request.proxy)
            if (!ok || currentCsrfToken == null) {
                val err = Exception("Failed to initialize Ox Alpha session / CSRF token.")
                trySend(StreamEvent.Error(err))
                close(err)
                return@callbackFlow
            }
        }

        val messagesArray = JSONArray()
        // Ox Alpha strictly requires alternating "user" and "assistant" roles.
        // It rejects "system" role with HTTP 422 {"error":"Invalid messages"}.
        // Any system messages are merged into the user prompt.
        val systemNotes = mutableListOf<String>()
        val sanitized = mutableListOf<Pair<String, StringBuilder>>()

        for (msg in request.history) {
            val content = msg.content.trim()
            if (content.isEmpty()) continue
            if (msg.role == ChatRole.SYSTEM) {
                systemNotes.add(content)
            } else {
                val role = if (msg.role == ChatRole.ASSISTANT) "assistant" else "user"
                if (sanitized.isNotEmpty() && sanitized.last().first == role) {
                    sanitized.last().second.append("\n\n").append(content)
                } else {
                    sanitized.add(role to StringBuilder(content))
                }
            }
        }

        // Now handle the current prompt
        val currentPrompt = request.prompt.trim()
        if (sanitized.isNotEmpty() && sanitized.last().first == "user") {
            sanitized.last().second.append("\n\n").append(currentPrompt)
        } else {
            sanitized.add("user" to StringBuilder(currentPrompt))
        }

        // If there were system prompts, prepend to the very first user message
        if (systemNotes.isNotEmpty()) {
            val firstUserIndex = sanitized.indexOfFirst { it.first == "user" }
            if (firstUserIndex != -1) {
                val combinedSystem = systemNotes.joinToString("\n\n")
                sanitized[firstUserIndex].second.insert(0, "$combinedSystem\n\n")
            }
        }

        // Must start with user
        while (sanitized.isNotEmpty() && sanitized.first().first != "user") {
            sanitized.removeAt(0)
        }

        for ((role, sb) in sanitized) {
            messagesArray.put(JSONObject().apply {
                put("role", role)
                put("content", sb.toString())
            })
        }

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

        val client = clientFor(request.proxy)
        val call = client.newCall(httpRequest)

        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawError = response.body?.string().orEmpty()
                    if (response.code == 419 || response.code == 401 || response.code == 428) {
                        currentCsrfToken = null
                        cookieJar.clear()
                    }
                    val detail = if (rawError.isNotBlank()) rawError.take(180) else response.message
                    val err = Exception("Ox Alpha returned HTTP ${response.code}: $detail")
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

