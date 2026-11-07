package com.chatapp.aiengine.network.providers

import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.core.ThinkingStreamParser
import com.chatapp.aiengine.model.*
import com.chatapp.aiengine.network.HttpClientProvider
import com.chatapp.aiengine.network.ProxyPool
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * ZyraProvider — High-Speed Unlimited Zero-Limit Failover Provider (Qwen 3.5 Heavy Context).
 *
 * Direct upstream: https://us-central1-llmchat-22d7a.cloudfunctions.net/generate
 * - Static Header Key: QtcY6-KEY: bOBHq59mhSJ7aPK5Ut4k
 * - 0 rate limits, instant cloud failover.
 */
class ZyraProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient,
    private val endpointUrl: String = "https://us-central1-llmchat-22d7a.cloudfunctions.net/generate",
    private val proxyPool: ProxyPool? = null
) : AiProvider {

    override val providerType: ProviderType = ProviderType.ZYRA

    companion object {
        private const val API_KEY = "bOBHq59mhSJ7aPK5Ut4k"
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        // Build combined prompt with history context
        val promptBuilder = StringBuilder()
        for (m in request.history.takeLast(6)) {
            val roleName = if (m.role == ChatRole.USER) "User" else "Assistant"
            promptBuilder.append("$roleName: ${m.content}\n\n")
        }
        promptBuilder.append("User: ${request.prompt.trim()}")

        val bodyJson = JSONObject().apply {
            put("input", promptBuilder.toString())
            put("app-name", "notebook")
            put("version", "1")
            put("platform", "mobile")
        }

        val httpRequest = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("QtcY6-KEY", API_KEY)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val activeClient = proxyPool?.httpClientFor(request.proxy) ?: this@ZyraProvider.client
        val call = activeClient.newCall(httpRequest)

        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawError = response.body?.string().orEmpty().take(200)
                    val err = Exception("Zyra returned HTTP ${response.code}: $rawError")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val resStr = response.body?.string().orEmpty()
                val json = JSONObject(resStr)

                if (!json.optBoolean("success", false)) {
                    val errMsg = json.optString("error", "Zyra returned failure")
                    val err = Exception(errMsg)
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val fullOutput = json.optString("output", "")

                // Stream tokens out smoothly word-by-word for ChatGPT feel
                val words = fullOutput.split(Regex("(?<=\\s)|(?=\\s)"))
                for (chunk in words) {
                    if (chunk.isNotEmpty()) {
                        parser.feedContentChunk(chunk)
                        delay(15) // Natural typing pace
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
