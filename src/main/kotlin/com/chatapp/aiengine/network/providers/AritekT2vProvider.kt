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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * AritekT2vProvider — Multi-Engine AI Video Generator Provider.
 *
 * Supports:
 * 1. Sora AI Video (T2V) — General high-speed video (https://t2v.aritek.app, ~2.5s)
 * 2. Cartoon & Pet Video (T2P) — Cartoon animation & pet specialist (https://t2p.aritek.app, ~3.5s)
 * 3. Character Merge Video (Fusion) — Character transform & merge (https://aifusion.aritek.app, ~100s)
 *
 * Features:
 * - 100% On-device Kotlin execution with zero laptop/python required.
 * - Auto-bootstraps guest token with arbitrary random Device-Id.
 * - Auto-rotates Device-Id on quota exhaustion (10 free generations per device slot).
 * - Zero login, zero cookie, zero account required.
 */
class AritekT2vProvider(
    private val client: OkHttpClient = HttpClientProvider.defaultClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val proxyPool: ProxyPool? = null
) : AiProvider {

    override val providerType: ProviderType = ProviderType.ARITEK_T2V

    private data class EngineConfig(
        val baseUrl: String,
        val sign: String,
        val versionCode: String,
        val endpointPath: String,
        val engineName: String,
        val estimatedDuration: String
    )

    companion object {
        private const val CTRY_TARGET = "others"
        private const val USER_AGENT = "okhttp/4.12.0"

        private val ENGINE_SORA = EngineConfig(
            baseUrl = "https://t2v.aritek.app",
            sign = "68d6165b72a7f2d8d17b0dc6fe9691abdf77c583",
            versionCode = "93",
            endpointPath = "/api/v4/video/t2v",
            engineName = "Sora AI Video (T2V)",
            estimatedDuration = "2.5 - 5 seconds"
        )

        private val ENGINE_PET = EngineConfig(
            baseUrl = "https://t2p.aritek.app",
            sign = "7259cb222a66496242650023583742fe40ea0a80",
            versionCode = "23",
            endpointPath = "/api/v1/video/t2v",
            engineName = "Cartoon & Pet Video (T2P)",
            estimatedDuration = "3 - 5 seconds"
        )

        private val ENGINE_FUSION = EngineConfig(
            baseUrl = "https://aifusion.aritek.app",
            sign = "3325ecccfae4a0c4ee7eed0a4043daac58c51bec",
            versionCode = "22",
            endpointPath = "/api/v1/video/t2v",
            engineName = "Character Merge Video (Fusion)",
            estimatedDuration = "~90 - 110 seconds"
        )

        private fun generateDeviceId(): String {
            val bytes = ByteArray(8)
            Random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private data class EngineSession(
        var deviceId: String = generateDeviceId(),
        var token: String? = null,
        var tokenExpiry: Long = 0L
    )

    private val sessions = ConcurrentHashMap<String, EngineSession>()

    private fun getSession(engineKey: String): EngineSession {
        return sessions.computeIfAbsent(engineKey) { EngineSession() }
    }

    private fun resolveEngine(modelId: String): EngineConfig {
        return when {
            modelId.contains("pet", ignoreCase = true) || modelId.contains("cartoon", ignoreCase = true) -> ENGINE_PET
            modelId.contains("fusion", ignoreCase = true) || modelId.contains("merge", ignoreCase = true) -> ENGINE_FUSION
            else -> ENGINE_SORA
        }
    }

    private suspend fun bootstrap(engine: EngineConfig, session: EngineSession, activeClient: OkHttpClient): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${engine.baseUrl}/api/v1/user/info")
            .addHeader("Sign", engine.sign)
            .addHeader("versionCode", engine.versionCode)
            .addHeader("Ctry-Target", CTRY_TARGET)
            .addHeader("pt", "")
            .addHeader("Device-Id", session.deviceId)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        activeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("${engine.engineName} auth bootstrap failed: HTTP ${response.code}")
            }
            val bodyStr = response.body?.string().orEmpty()
            val json = JSONObject(bodyStr)
            val data = json.optJSONObject("data") ?: throw Exception("Invalid bootstrap response: $bodyStr")
            val jwt = data.getString("token")
            val ttl = data.optLong("ttl", 3600L)
            session.token = jwt
            session.tokenExpiry = System.currentTimeMillis() + (ttl - 120L) * 1000L
            jwt
        }
    }

    private suspend fun ensureToken(engine: EngineConfig, session: EngineSession, activeClient: OkHttpClient): String {
        val current = session.token
        return if (current != null && System.currentTimeMillis() < session.tokenExpiry) {
            current
        } else {
            bootstrap(engine, session, activeClient)
        }
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        trySend(StreamEvent.Started(request.model, request.conversationId))

        val engine = resolveEngine(request.model.id)
        val session = getSession(engine.engineName)
        val prompt = request.prompt.trim()

        trySend(
            StreamEvent.ContentDelta(
                "🎬 **Generating AI Video with ${engine.engineName}...**\n\n" +
                "Prompt: _\"$prompt\"_\n\n" +
                "⏳ Rendering MP4 frames (estimated time: ${engine.estimatedDuration}). Please hold on...\n\n"
            )
        )

        val activeClient = proxyPool?.httpClientFor(request.proxy) ?: client

        try {
            var jwt = ensureToken(engine, session, activeClient)

            val jsonBody = when (engine) {
                ENGINE_PET -> JSONObject().apply {
                    put("prompt", prompt)
                    put("versionCode", engine.versionCode.toIntOrNull() ?: 23)
                    put("deviceID", session.deviceId)
                    put("isPremium", 0)
                    put("ctry_target", CTRY_TARGET)
                    put("used", JSONArray())
                    put("aspect_ratio", "9:16")
                    put("ai_sound", 1)
                }
                ENGINE_FUSION -> JSONObject().apply {
                    put("prompt", prompt)
                    put("used", JSONArray())
                    put("ratio", "9:16")
                    put("resolution", "480p")
                    put("ai_sound", 1)
                }
                else -> JSONObject().apply {
                    put("prompt", prompt)
                    put("aspect_ratio", "9:16")
                    put("ai_sound", 0)
                }
            }

            var httpRequest = Request.Builder()
                .url("${engine.baseUrl}${engine.endpointPath}")
                .addHeader("Sign", engine.sign)
                .addHeader("versionCode", engine.versionCode)
                .addHeader("Ctry-Target", CTRY_TARGET)
                .addHeader("pt", "")
                .addHeader("Device-Id", session.deviceId)
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            var response = activeClient.newCall(httpRequest).execute()
            var bodyStr = response.body?.string().orEmpty()

            // Auto-rotate device slot on 429 quota exhaustion
            if (response.code == 429) {
                trySend(StreamEvent.ContentDelta("🔄 Guest device slot quota full. Rotating to fresh device slot...\n\n"))
                session.deviceId = generateDeviceId()
                session.token = null
                jwt = ensureToken(engine, session, activeClient)

                // update body for pet engine if needed
                if (engine == ENGINE_PET) {
                    jsonBody.put("deviceID", session.deviceId)
                }

                httpRequest = httpRequest.newBuilder()
                    .header("Device-Id", session.deviceId)
                    .header("Authorization", "Bearer $jwt")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                response = activeClient.newCall(httpRequest).execute()
                bodyStr = response.body?.string().orEmpty()
            }

            if (!response.isSuccessful) {
                val err = Exception("${engine.engineName} failed (HTTP ${response.code}): $bodyStr")
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
                append("*(Engine: ${engine.engineName})*")
            }

            trySend(StreamEvent.ContentDelta(resultMarkdown))

            val finalMsg = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "🎬 **Video Generated (${engine.engineName}):**\n\n$videoUrl\n\n✨ Real MP4 video created successfully from prompt: \"$prompt\""
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
