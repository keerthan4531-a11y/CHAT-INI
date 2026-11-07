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
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OneSearchProvider — 100% Standalone Begamob OneSearch 2API Provider.
 *
 * Reverse-engineered from OneSearch (com.chat.chatai.chatbot.aichatbot v1.5.5).
 * - Zero login, zero account pool, zero SMS, zero cookies.
 * - Self-signed client-side HS256 JWT authentication.
 * - Real upstream SSE streaming for frontier models (Claude 3.7 Sonnet, DeepSeek R1, GPT-5, o3, etc.).
 */
class OneSearchProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient,
    private val upstreamUrl: String = "https://chat-ai.begamob.com/api/v3/chat/stream",
    private val proxyPool: ProxyPool? = null
) : AiProvider {

    override val providerType: ProviderType = ProviderType.ONESEARCH

    companion object {
        private const val SECRET_KEY = "stteam-ikameglobal-chatapiopenai"
        private const val BUNDLE_ID = "com.chat.chatai.chatbot.aichatbot"
        private const val APP_VERSION = "1.5.5"

        private val b64UrlEncoder = Base64.getUrlEncoder().withoutPadding()

        fun generateToken(): String {
            val header = JSONObject().apply {
                put("alg", "HS256")
                put("typ", "JWT")
            }

            val nowMs = System.currentTimeMillis()
            val nowSec = nowMs / 1000
            val payload = JSONObject().apply {
                put("iat", nowMs)
                put("exp", nowSec + 86400)
                put("bundleId", BUNDLE_ID)
                put("os", "Android")
                put("versionApp", APP_VERSION)
                put("timezone", "Asia/Shanghai")
            }

            val headerB64 = b64UrlEncoder.encodeToString(header.toString().toByteArray(StandardCharsets.UTF_8))
            val payloadB64 = b64UrlEncoder.encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
            val unsignedToken = "$headerB64.$payloadB64"

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(SECRET_KEY.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signatureBytes = mac.doFinal(unsignedToken.toByteArray(StandardCharsets.UTF_8))
            val signatureB64 = b64UrlEncoder.encodeToString(signatureBytes)

            return "$unsignedToken.$signatureB64"
        }
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        if (request.enableWebSearch) {
            trySend(StreamEvent.WebSearchStarted("Searching with Google Grounding..."))
        }

        val token = generateToken()
        val deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "").take(16)

        val messagesArray = JSONArray()
        for (m in request.history) {
            val content = m.content.trim()
            if (content.isNotEmpty()) {
                messagesArray.put(JSONObject().apply {
                    put("role", m.role.name.lowercase())
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", content)
                        })
                    })
                })
            }
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", request.prompt.trim())
                })
            })
        })

        val toolsArray = JSONArray()
        if (request.enableWebSearch) {
            toolsArray.put("web_search")
        }

        val payload = JSONObject().apply {
            put("max_tokens", 4096)
            put("model", request.model.id)
            put("messages", messagesArray)
            put("response_length", "medium")
            put("response_tone", "default")
            put("topic_type", "general")
            put("image_and_analytic", false)
            put("tools", toolsArray)
            put("is_image_vip", false)
        }

        val httpRequest = Request.Builder()
            .url(upstreamUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("user-header", "bundleId:$BUNDLE_ID/versionApp:$APP_VERSION/OS:Android/osVersion:34/userId:/deviceId:$deviceId")
            .header("x-app-version", APP_VERSION)
            .header("x-app-bundle-id", BUNDLE_ID)
            .header("x-os", "android")
            .header("Content-Type", "application/json")
            .header("User-Agent", "okhttp/4.12.0")
            .build()

        val activeClient = proxyPool?.httpClientFor(request.proxy) ?: this@OneSearchProvider.client
        val call = activeClient.newCall(httpRequest)

        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful && response.code != 201) {
                    val rawError = response.body?.string().orEmpty()
                    val detail = if (rawError.isNotBlank()) rawError.take(200) else response.message
                    val err = Exception("OneSearch returned HTTP ${response.code}: $detail")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                val bodyStream = response.body?.source()?.inputStream()
                if (bodyStream == null) {
                    val err = Exception("Empty response body from OneSearch")
                    trySend(StreamEvent.Error(err))
                    close(err)
                    return@callbackFlow
                }

                SseStreamReader.readStream(
                    inputStream = bodyStream,
                    onDataPayload = { rawData ->
                        try {
                            val json = JSONObject(rawData)
                            if (json.has("error")) {
                                val errObj = json.optJSONObject("error")
                                val msg = errObj?.optString("message", "OneSearch server error") ?: "Error"
                                val err = Exception(msg)
                                trySend(StreamEvent.Error(err))
                                close(err)
                                return@readStream
                            }

                            val dataObj = json.optJSONObject("data")
                            if (dataObj != null) {
                                val finishReason = dataObj.optString("finishReason", "")
                                if (finishReason == "stop") {
                                    return@readStream
                                }

                                val chunk = dataObj.optString("contentText", "")
                                    .ifEmpty { dataObj.optString("content", "") }

                                if (chunk.isNotEmpty()) {
                                    parser.feedContentChunk(chunk)
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
