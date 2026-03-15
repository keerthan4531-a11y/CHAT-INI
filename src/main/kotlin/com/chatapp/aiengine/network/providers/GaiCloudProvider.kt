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
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Direct Cloud Provider communicating with multi-model cloud services.
 * Features:
 * - Native AES-128-CBC encryption and SHA-1 request signatures.
 * - Live token-by-token streaming (immediate response rendering without waiting).
 * - Real-time thinking / reasoning token separation using [ThinkingStreamParser].
 * - Integrated Web Search parameter support.
 * - Supports GPT-4o, GPT-4.1-mini, Gemini 3 Flash, DeepSeek V3/V4, Grok 4, Claude Haiku.
 */
class GaiCloudProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient,
    private val baseUrl: String = "https://api.chatgai.fun",
    private val proxyPool: ProxyPool? = null
) : AiProvider {

    override val providerType: ProviderType = ProviderType.GAI_CLOUD

    companion object {
        private const val BUNDLE = "com.aichatmaster.chat.gp"
        private const val APP_VERSION = "2.6.8"
        private const val SALT = "t6KeG6aKR5pm65oWn5aqS6LWE57O757ufS2V2aW4uWWFuZw"

        private val AES_KEY = "Ka7Ya98107EdGXQa".toByteArray(StandardCharsets.UTF_8)
        private val AES_IV = "yc0q2icx1oq4lijm".toByteArray(StandardCharsets.UTF_8)
    }

    private val deviceId = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun getRandomResidentialIp(): String {
        return "${Random.nextInt(50, 201)}.${Random.nextInt(1, 251)}.${Random.nextInt(1, 251)}.${Random.nextInt(1, 251)}"
    }

    private fun sha1(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(text.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun aesEncrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        val timestamp = System.currentTimeMillis() / 1000
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val conversationId = request.conversationId

        trySend(StreamEvent.Started(request.model, conversationId))

        if (request.enableWebSearch) {
            trySend(StreamEvent.WebSearchStarted("Searching for latest live context..."))
        }

        // 1. Calculate signature
        val signStr = "aiVersion=${request.model.id}" +
                "&bundle=$BUNDLE" +
                "&conversationId=$conversationId" +
                "&nonce=$nonce" +
                "&question=${request.prompt}" +
                "&timestamp=$timestamp" +
                "&version=$APP_VERSION"

        val signature = sha1(signStr + SALT)

        // 2. Build secure JSON envelope
        val innerJson = JSONObject().apply {
            put("aiVersion", request.model.id)
            put("bundle", BUNDLE)
            put("conversationId", conversationId)
            put("language", "en")
            put("needSearch", request.enableWebSearch)
            put("nonce", nonce)
            put("question", request.prompt)
            put("signature", signature)
            put("timestamp", timestamp)
            put("type", "1")
            put("version", APP_VERSION)
        }

        val envelopeJson = JSONObject().apply {
            put("bundle", BUNDLE)
            put("bundleId", BUNDLE)
            put("security", aesEncrypt(innerJson.toString()))
        }

        val fakeIp = getRandomResidentialIp()

        val httpRequest = Request.Builder()
            .url("$baseUrl/common/sse/chat")
            .post(envelopeJson.toString().toRequestBody("application/json".toMediaType()))
            .header("UserType", "app_user")
            .header("x-bundle-id", BUNDLE)
            .header("x-app-version", APP_VERSION)
            .header("x-language", "en")
            .header("x-device-type", "android")
            .header("x-device-id", deviceId)
            .header("X-Forwarded-For", fakeIp)
            .header("X-Real-IP", fakeIp)
            .header("Client-IP", fakeIp)
            .header("content-type", "application/json")
            .build()

        // Rotate through the proxy pool when a lane was assigned by the repository.
        val activeClient = proxyPool?.httpClientFor(request.proxy) ?: this@GaiCloudProvider.client
        val call = activeClient.newCall(httpRequest)

        // Parser automatically splits <think>...</think> tokens from normal content tokens!
        val parser = ThinkingStreamParser { event ->
            trySend(event)
        }

        val citations = mutableListOf<WebCitation>()

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val error = Exception("GaiCloud returned HTTP ${response.code}: ${response.message}")
                    trySend(StreamEvent.Error(error))
                    close(error)
                    return@callbackFlow
                }

                val bodyStream = response.body?.source()?.inputStream()
                if (bodyStream == null) {
                    val error = Exception("Empty response body from GaiCloud")
                    trySend(StreamEvent.Error(error))
                    close(error)
                    return@callbackFlow
                }

                var hasError = false

                SseStreamReader.readStream(
                    inputStream = bodyStream,
                    onDataPayload = { payload ->
                        try {
                            val json = JSONObject(payload)
                            val code = json.optInt("code", 0)
                            val msg = json.optString("message", "")
                            if (msg.contains("free usage limit", ignoreCase = true) || msg.contains("upgrade to VIP", ignoreCase = true)) {
                                hasError = true
                                val ex = Exception("Model limit: $msg. Please switch to GPT-4o, DeepSeek V3, or Gemini 3 Flash.")
                                trySend(StreamEvent.Error(ex))
                                close(ex)
                                return@readStream
                            }
                            if (code != 0 && code != 200) {
                                hasError = true
                                val ex = Exception(msg.ifEmpty { "Error code $code" })
                                trySend(StreamEvent.Error(ex))
                                close(ex)
                                return@readStream
                            }

                            val data = json.optJSONObject("data")
                            if (data != null) {
                                // Extract answers
                                val answer = data.optString("answer", "")
                                if (answer.isNotEmpty()) {
                                    parser.feedContentChunk(answer)
                                }

                                // Extract web search results if returned
                                val searchArray = data.optJSONArray("search_results")
                                if (searchArray != null && searchArray.length() > 0) {
                                    for (i in 0 until searchArray.length()) {
                                        val item = searchArray.optJSONObject(i) ?: continue
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
                            }
                        } catch (_: Exception) {
                            // Non-json or heartbeat chunk, feed raw if valid
                            if (payload.isNotEmpty()) {
                                parser.feedContentChunk(payload)
                            }
                        }
                    },
                    onComplete = {
                        if (!hasError) {
                            parser.flush()
                            val fullContent = parser.getFullContent()
                            val fullThinking = parser.getFullThinking()
                            if (fullContent.isNotEmpty() || fullThinking.isNotEmpty()) {
                                val finalMsg = ChatMessage(
                                    role = ChatRole.ASSISTANT,
                                    content = fullContent,
                                    reasoningContent = fullThinking.ifEmpty { null },
                                    citations = citations
                                )
                                trySend(StreamEvent.Completed(finalMsg))
                            }
                        }
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

