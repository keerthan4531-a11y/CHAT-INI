package com.chatapp.aiengine.network.providers

import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.model.*
import com.chatapp.aiengine.network.HttpClientProvider
import com.chatapp.aiengine.network.ProxyPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * AritekT2vProvider — Standalone AI Video Generation Provider (Sora / Aritek T2V v1.9.3).
 *
 * Reverse-engineered from t2v-staging.aritek.app-2api:
 * - Generates actual MP4 video files directly from user prompts.
 * - Auto-bootstraps guest token with arbitrary random Device-Id.
 * - Auto-rotates Device-Id on quota exhaustion (10 free generations per device ID).
 * - Zero login, zero cookie, zero account required.
 */
class AritekT2vProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val proxyPool: ProxyPool? = null
) : AiProvider {

    override val providerType: ProviderType = ProviderType.ARITEK_T2V

    companion object {
        private const val BASE_URL = "https://t2v.aritek.app"
        private const val SIGN = "68d6165b72a7f2d8d17b0dc6fe9691abdf77c583"
        private const val VERSION_CODE = "93"
        private const val CTRY_TARGET = "others"
        private const val USER_AGENT = "okhttp/4.12.0"

        private fun generateDeviceId(): String {
            val bytes = ByteArray(16)
            Random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private var currentDeviceId: String = generateDeviceId()
    private var token: String? = null
    private var tokenExpiry: Long = 0L

    private suspend fun bootstrap(activeClient: OkHttpClient): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/user/info")
            .addHeader("sign", SIGN)
            .addHeader("versionCode", VERSION_CODE)
            .addHeader("Ctry-Target", CTRY_TARGET)
            .addHeader("pt", "")
            .addHeader("Device-Id", currentDeviceId)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        activeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Video auth bootstrap failed: HTTP ${response.code}")
            }
            val bodyStr = response.body?.string().orEmpty()
            val json = JSONObject(bodyStr)
            val data = json.optJSONObject("data") ?: throw Exception("Invalid bootstrap response: $bodyStr")
            val jwt = data.getString("token")
            val ttl = data.optLong("ttl", 3600L)
            token = jwt
            tokenExpiry = System.currentTimeMillis() + (ttl - 120L) * 1000L
            jwt
        }
    }

    private suspend fun ensureToken(activeClient: OkHttpClient): String {
        val current = token
        return if (current != null && System.currentTimeMillis() < tokenExpiry) {
            current
        } else {
            bootstrap(activeClient)
        }
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        val prompt = request.prompt.trim()
        trySend(StreamEvent.ContentDelta("🎬 **Generating AI Video...**\n\nPrompt: _\"$prompt\"_\n\nRendering MP4 frames via Sora T2V engine (usually takes 5-10 seconds)... ⏳\n\n"))

        val activeClient = proxyPool?.httpClientFor(request.proxy) ?: client

        try {
            var jwt = ensureToken(activeClient)

            val jsonBody = JSONObject().apply {
                put("prompt", prompt)
                put("aspect_ratio", "9:16")
                put("ai_sound", 0)
            }

            var httpRequest = Request.Builder()
                .url("$BASE_URL/api/v4/video/t2v")
                .addHeader("sign", SIGN)
                .addHeader("versionCode", VERSION_CODE)
                .addHeader("Ctry-Target", CTRY_TARGET)
                .addHeader("pt", "")
                .addHeader("Device-Id", currentDeviceId)
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            var response = activeClient.newCall(httpRequest).execute()
            var bodyStr = response.body?.string().orEmpty()

            // Auto-rotate device ID if quota reached (429)
            if (response.code == 429) {
                trySend(StreamEvent.ContentDelta("🔄 Quota full on guest slot. Rotating to fresh device slot...\n\n"))
                currentDeviceId = generateDeviceId()
                token = null
                jwt = ensureToken(activeClient)

                httpRequest = httpRequest.newBuilder()
                    .header("Device-Id", currentDeviceId)
                    .header("Authorization", "Bearer $jwt")
                    .build()

                response = activeClient.newCall(httpRequest).execute()
                bodyStr = response.body?.string().orEmpty()
            }

            if (!response.isSuccessful) {
                val err = Exception("Video generation failed (HTTP ${response.code}): $bodyStr")
                trySend(StreamEvent.Error(err))
                close(err)
                return@callbackFlow
            }

            val json = JSONObject(bodyStr)
            val data = json.optJSONObject("data")
            val videoUrl = data?.optString("url", "")

            if (videoUrl.isNullOrEmpty()) {
                val err = Exception("No video URL returned by server: $bodyStr")
                trySend(StreamEvent.Error(err))
                close(err)
                return@callbackFlow
            }

            val resultMarkdown = buildString {
                append("✨ **AI Video Generated Successfully!** 🎉\n\n")
                append("🎥 **Download & Watch Link:**\n")
                append("$videoUrl\n\n")
                append("*(Tap the link above to view or download the MP4 video)*")
            }

            trySend(StreamEvent.ContentDelta(resultMarkdown))

            val finalMsg = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "🎬 **Video Generated:**\n\n$videoUrl\n\n✨ Real MP4 video created successfully from prompt: \"$prompt\""
            )
            trySend(StreamEvent.Completed(finalMsg))
            close()
        } catch (e: Exception) {
            val err = Exception("Video generator error: ${e.message}")
            trySend(StreamEvent.Error(err))
            close(err)
        }

        awaitClose {}
    }
}
