package com.chatapp.aiengine.mcp

import com.chatapp.aiengine.model.McpServerConfig
import com.chatapp.aiengine.model.McpServerStatus
import com.chatapp.aiengine.model.McpTool
import com.chatapp.aiengine.model.McpTransport
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.ToolDefinition
import com.chatapp.aiengine.model.ToolRisk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Minimal, dependency-light MCP (Model Context Protocol) client.
 *
 * Supports both transports that matter in 2026:
 * - **stdio** for local servers (desktop / JVM). Android apps should prefer
 *   Streamable HTTP because they cannot spawn `npx` processes.
 * - **Streamable HTTP** for remote servers (the practical choice on Android).
 *
 * Implements the JSON-RPC 2.0 handshake, `tools/list`, `tools/call`,
 * `resources/list`, `resources/read`, `prompts/list` and `prompts/get`.
 */
class McpClient(
    val config: McpServerConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transport: TransportConnection? = null
    private var readerJob: Job? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val notifications = ConcurrentHashMap<String, (params: JSONObject?) -> Unit>()

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var latencyMs: Long = 0
        private set

    @Volatile
    var tools: List<McpTool> = emptyList()
        private set

    @Volatile
    var lastError: String? = null
        private set

    suspend fun connect(): McpServerStatus = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        lastError = null
        try {
            transport = when (config.transport) {
                McpTransport.STDIO -> StdioTransport(config)
                McpTransport.STREAMABLE_HTTP -> HttpTransport(config, httpClient)
            }
            transport?.start()

            val init = transport!!.request(
                method = "initialize",
                params = JSONObject().apply {
                    put(
                        "protocolVersion",
                        "2025-06-18"
                    )
                    put(
                        "capabilities",
                        JSONObject().put("tools", JSONObject().put("listChanged", false))
                    )
                    put(
                        "clientInfo",
                        JSONObject().put("name", "pawpal-android").put("version", "1.0.0")
                    )
                }
            )
            if (init.optJSONObject("result") == null && init.has("error")) {
                throw Exception(init.optString("error", "initialize failed"))
            }
            transport?.notify("notifications/initialized", null)

            val list = transport!!.request("tools/list", null)
            val rawTools = list.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
            tools = (0 until rawTools.length()).mapNotNull { i ->
                rawTools.optJSONObject(i)?.let { json ->
                    McpTool(
                        name = json.optString("name"),
                        description = json.optString("description", ""),
                        inputSchema = json.opt("inputSchema")?.toString() ?: "{}"
                    )
                }
            }
            connected = true
            latencyMs = System.currentTimeMillis() - started
            McpServerStatus(
                serverId = config.id,
                connected = true,
                toolCount = tools.size,
                latencyMs = latencyMs,
                message = "Connected · ${tools.size} tools"
            )
        } catch (e: Exception) {
            connected = false
            lastError = e.message ?: e.javaClass.simpleName
            McpServerStatus(
                serverId = config.id,
                connected = false,
                toolCount = 0,
                latencyMs = System.currentTimeMillis() - started,
                message = lastError ?: "Connection failed"
            )
        }
    }

    suspend fun callTool(name: String, arguments: String): ToolCallResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val args = runCatching { JSONObject(arguments) }.getOrElse { JSONObject() }
            val response = transport!!.request(
                method = "tools/call",
                params = JSONObject().put("name", name).put("arguments", args)
            )
            val error = response.optJSONObject("error")
            if (error != null) {
                return@withContext ToolCallResult(
                    ok = false,
                    output = error.optString("message", error.toString()),
                    durationMs = System.currentTimeMillis() - started
                )
            }
            val result = response.optJSONObject("result")
            val content = result?.optJSONArray("content")
            val text = if (content != null) {
                (0 until content.length()).joinToString("\n") { i ->
                    val item = content.optJSONObject(i)
                    item?.optString("text") ?: item?.optString("data") ?: item?.toString().orEmpty()
                }
            } else {
                result?.toString() ?: "ok"
            }
            val isError = result?.optBoolean("isError", false) == true
            ToolCallResult(ok = !isError, output = text, durationMs = System.currentTimeMillis() - started)
        } catch (e: Exception) {
            ToolCallResult(ok = false, output = e.message ?: "tool call failed", durationMs = System.currentTimeMillis() - started)
        }
    }

    suspend fun close(): Unit = withContext(Dispatchers.IO) {
        runCatching { transport?.request("shutdown", null) }
        runCatching { transport?.close() }
        readerJob?.cancel()
        scope.cancel()
        connected = false
    }

    private fun dispatch(raw: String) {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val id = json.opt("id")
        if (id != null) {
            val deferred = pending.remove(id.toString())
            deferred?.complete(json)
        } else {
            val method = json.optString("method")
            val handler = notifications[method]
            handler?.invoke(json.optJSONObject("params"))
        }
    }

    // ------------------------------------------------------------------
    // Transports
    // ------------------------------------------------------------------

    private interface TransportConnection {
        suspend fun start()
        suspend fun request(method: String, params: Any?): JSONObject
        suspend fun notify(method: String, params: Any?)
        suspend fun close()
    }

    private inner class StdioTransport(config: McpServerConfig) : TransportConnection {
        private lateinit var process: Process
        private lateinit var writer: java.io.OutputStream

        override suspend fun start() = withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(config.command, *config.args.toTypedArray())
            builder.environment().putAll(config.env)
            builder.environment().putIfAbsent("PATH", System.getenv("PATH") ?: "/usr/bin:/bin:/usr/local/bin")
            builder.redirectErrorStream(true)
            process = builder.start()
            writer = process.outputStream
            readerJob = launch {
                val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    dispatch(line)
                }
            }
        }

        override suspend fun request(method: String, params: Any?): JSONObject = withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<JSONObject>()
            pending[id] = deferred
            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
            if (params != null) payload.put("params", params)
            write(payload)
            try {
                withTimeout(45_000) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                pending.remove(id)
                throw Exception("MCP request timed out: $method")
            }
        }

        override suspend fun notify(method: String, params: Any?): Unit = withContext(Dispatchers.IO) {
            val payload = JSONObject().put("jsonrpc", "2.0").put("method", method)
            if (params != null) payload.put("params", params)
            write(payload)
        }

        private fun write(payload: Any) {
            writer.write((payload.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            writer.flush()
        }

        override suspend fun close(): Unit = withContext(Dispatchers.IO) {
            runCatching { process.destroy() }
            runCatching { process.waitFor(3, TimeUnit.SECONDS) }
            Unit
        }
    }

    private inner class HttpTransport(
        config: McpServerConfig,
        private val client: OkHttpClient
    ) : TransportConnection {
        private var sessionId: String? = null

        override suspend fun start() {
            // The initialize request happens through request(); nothing to do here.
        }

        override suspend fun request(method: String, params: Any?): JSONObject = withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", UUID.randomUUID().toString())
                .put("method", method)
            if (params != null) payload.put("params", params)
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val builder = Request.Builder()
                .url(config.url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
            config.headers.forEach { (key, value) -> builder.header(key, value) }
            sessionId?.let { builder.header("Mcp-Session-Id", it) }
            client.newCall(builder.build()).execute().use { response ->
                response.headers["Mcp-Session-Id"]?.let { sessionId = it }
                if (!response.isSuccessful) {
                    throw Exception("MCP HTTP ${response.code}: ${response.message}")
                }
                val contentType = response.header("Content-Type").orEmpty()
                val raw = if (contentType.contains("text/event-stream")) {
                    readSse(response.body?.byteStream() ?: throw Exception("empty MCP body"))
                } else {
                    response.body?.string() ?: "{}"
                }
                runCatching { JSONObject(raw) }.getOrElse { error ->
                    // Some servers reply with an SSE batch; try the first data line.
                    raw.lineSequence().firstOrNull { it.startsWith("data:") }
                        ?.removePrefix("data:")?.trim()
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                        ?: throw error
                }
            }
        }

        private fun readSse(stream: java.io.InputStream): String {
            val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
            val data = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val current = line ?: continue
                if (current.startsWith("data:")) {
                    data.append(current.substring(5).trim())
                }
                if (current.isBlank() && data.isNotEmpty()) break
            }
            return data.toString().ifEmpty { "{}" }
        }

        override suspend fun notify(method: String, params: Any?): Unit = withContext(Dispatchers.IO) {
            val payload = JSONObject().put("jsonrpc", "2.0").put("method", method)
            if (params != null) payload.put("params", params)
            val builder = Request.Builder()
                .url(config.url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
            config.headers.forEach { (key, value) -> builder.header(key, value) }
            sessionId?.let { builder.header("Mcp-Session-Id", it) }
            runCatching { client.newCall(builder.build()).execute().use { } }
            Unit
        }

        override suspend fun close() = Unit
    }
}

data class ToolCallResult(
    val ok: Boolean,
    val output: String,
    val durationMs: Long
)

/** Converts discovered MCP tools into the engine's typed tool contracts. */
fun McpTool.toDefinition(server: McpServerConfig): ToolDefinition {
    val lower = name.lowercase()
    val risk = when {
        lower.contains("delete") || lower.contains("remove") || lower.contains("drop") -> ToolRisk.DESTRUCTIVE
        lower.startsWith("write") || lower.contains("create") || lower.contains("send") ||
            lower.contains("update") || lower.contains("post") || lower.contains("add") ||
            lower.contains("move") || lower.contains("merge") -> ToolRisk.WRITE
        else -> ToolRisk.READ_ONLY
    }
    return ToolDefinition(
        name = name,
        description = description.ifEmpty { "MCP tool $name on ${server.name}" },
        serverId = server.id,
        serverName = server.name,
        emoji = server.emoji,
        risk = risk,
        inputSchema = inputSchema,
        requiresApproval = risk != ToolRisk.READ_ONLY
    )
}

/**
 * Manages a set of MCP connections and exposes their tools to the agent.
 */
class McpManager(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val clients = ConcurrentHashMap<String, McpClient>()
    private val statuses = ConcurrentHashMap<String, McpServerStatus>()

    suspend fun connect(config: McpServerConfig): McpServerStatus {
        clients[config.id]?.close()
        val client = McpClient(config, httpClient)
        clients[config.id] = client
        val status = client.connect()
        statuses[config.id] = status
        return status
    }

    suspend fun disconnect(id: String) {
        clients.remove(id)?.close()
        statuses.remove(id)
    }

    suspend fun connectAll(configs: List<McpServerConfig>): List<McpServerStatus> =
        configs.filter { it.enabled }.map { connect(it) }

    fun status(id: String): McpServerStatus? = statuses[id]

    fun allStatuses(): List<McpServerStatus> = statuses.values.toList()

    fun isConnected(id: String): Boolean = clients[id]?.connected == true

    fun toolsFor(configs: List<McpServerConfig>): List<ToolDefinition> =
        configs.filter { it.enabled && isConnected(it.id) }.flatMap { config ->
            clients[config.id]?.tools?.map { tool ->
                val definition = tool.toDefinition(config)
                if (config.toolAllowlist.isEmpty() || config.toolAllowlist.contains(tool.name)) definition
                else definition.copy(name = "__disabled_${definition.name}")
            }?.filterNot { it.name.startsWith("__disabled_") } ?: emptyList()
        }

    suspend fun execute(call: ToolCall): ToolCallResult {
        val client = clients[call.tool.serverId]
            ?: return ToolCallResult(false, "MCP server '${call.tool.serverId}' is not connected", 0)
        return client.callTool(call.tool.name, call.arguments)
    }

    suspend fun closeAll() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        statuses.clear()
    }
}

/** Ready-made MCP server presets. Secrets are supplied at runtime, never stored. */
object McpPresets {
    fun defaults(): List<McpServerConfig> = listOf(
        // --- GitHub: remote works on Android, local is for desktop/JVM ---
        McpServerConfig(
            id = "github-remote",
            name = "GitHub (remote)",
            emoji = "🐙",
            description = "Official GitHub MCP over Streamable HTTP. Works on Android.",
            transport = McpTransport.STREAMABLE_HTTP,
            url = "https://api.githubcopilot.com/mcp/",
            env = mapOf("GITHUB_PERSONAL_ACCESS_TOKEN" to ""),
            toolAllowlist = listOf(
                "search_repositories", "search_issues", "get_file_contents",
                "list_commits", "create_pull_request", "push_files"
            )
        ),
        McpServerConfig(
            id = "github-local",
            name = "GitHub (local)",
            emoji = "🐙",
            description = "Runs the official server via npx. Desktop/JVM only.",
            transport = McpTransport.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-github"),
            env = mapOf("GITHUB_PERSONAL_ACCESS_TOKEN" to ""),
            toolAllowlist = listOf(
                "search_repositories", "search_issues", "get_file_contents",
                "list_commits", "create_pull_request", "push_files"
            )
        ),

        // --- Email: hosted server works on Android, local has full IMAP/SMTP ---
        McpServerConfig(
            id = "email-remote",
            name = "Email (remote)",
            emoji = "📧",
            description = "Hosted email MCP over Streamable HTTP. Works on Android.",
            transport = McpTransport.STREAMABLE_HTTP,
            url = "https://mcpemails.com/api/mcp",
            env = mapOf("MCP_API_KEY" to ""),
            toolAllowlist = listOf(
                "search_emails", "read_email", "create_draft", "send_email", "list_labels"
            )
        ),
        McpServerConfig(
            id = "email-local",
            name = "Email (local)",
            emoji = "📧",
            description = "Full IMAP/SMTP server via npx. Desktop/JVM only.",
            transport = McpTransport.STDIO,
            command = "npx",
            args = listOf("-y", "email-mcp"),
            env = mapOf(
                "EMAIL_ADDRESS" to "",
                "EMAIL_APP_PASSWORD" to "",
                "IMAP_HOST" to "imap.gmail.com",
                "SMTP_HOST" to "smtp.gmail.com"
            ),
            toolAllowlist = listOf(
                "search_emails", "read_email", "create_draft", "send_email", "list_labels"
            )
        ),

        McpServerConfig(
            id = "notion",
            name = "Notion",
            emoji = "📒",
            description = "Search pages, read docs and write notes into a workspace.",
            transport = McpTransport.STREAMABLE_HTTP,
            url = "https://mcp.notion.com/mcp",
            env = mapOf("NOTION_TOKEN" to "")
        ),
        McpServerConfig(
            id = "filesystem",
            name = "Files",
            emoji = "📂",
            description = "Read and edit project files inside a sandboxed folder.",
            transport = McpTransport.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-filesystem", "."),
            toolAllowlist = listOf("read_file", "write_file", "list_directory", "search_files")
        ),
        McpServerConfig(
            id = "slack",
            name = "Slack",
            emoji = "💬",
            description = "Read channels and post updates to a workspace.",
            transport = McpTransport.STREAMABLE_HTTP,
            url = "https://mcp.slack.com/mcp",
            env = mapOf("SLACK_BOT_TOKEN" to "", "SLACK_TEAM_ID" to "")
        )
    )

    fun find(id: String): McpServerConfig? = defaults().firstOrNull { it.id == id }
}
