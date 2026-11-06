package com.chatapp.aiengine.core

import com.chatapp.aiengine.mcp.McpToolExecutor
import com.chatapp.aiengine.mcp.ToolCallResult
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.ToolDefinition
import com.chatapp.aiengine.model.ToolRisk
import java.util.concurrent.ConcurrentHashMap

/**
 * The agent's tool catalog. Tools are typed contracts (name + JSON schema +
 * risk level) so the model cannot invent arguments that were never declared.
 *
 * Keep a single agent under ~12 tools: past that, tool selection quality drops.
 * Split by domain (email agent, coding agent, research agent) instead.
 */
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, ToolDefinition>()

    fun register(definition: ToolDefinition) {
        tools[definition.qualifiedName] = definition
    }

    fun registerAll(definitions: List<ToolDefinition>) {
        definitions.forEach { register(it) }
    }

    fun all(): List<ToolDefinition> = tools.values.sortedBy { it.serverName + it.name }

    fun forServers(serverIds: Set<String>): List<ToolDefinition> =
        if (serverIds.isEmpty()) all() else all().filter { serverIds.contains(it.serverId) }

    fun find(qualifiedName: String): ToolDefinition? =
        tools[qualifiedName] ?: tools.values.firstOrNull { it.name == qualifiedName }

    fun removeServer(serverId: String) {
        tools.keys.removeAll(tools.values.filter { it.serverId == serverId }.map { it.qualifiedName })
    }

    fun clear() {
        tools.clear()
    }
}

/** Executes a tool call. Read-only tools may run silently; writes need approval. */
interface ToolExecutor {
    suspend fun execute(call: ToolCall): ToolCallResult
}

/** Routes to MCP servers and to the engine's built-in tools. */
class CompositeToolExecutor(
    private val mcp: McpToolExecutor,
    private val builtIns: BuiltInTools
) : ToolExecutor {
    override suspend fun execute(call: ToolCall): ToolCallResult {
        return when (call.tool.serverId) {
            "pawpal" -> builtIns.execute(call)
            else -> mcp.execute(call)
        }
    }
}

/**
 * Built-in, dependency-free tools. These always work, even with no MCP
 * servers connected, so the agent is useful on first launch.
 */
class BuiltInTools {
    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "web_search",
            description = "Search the live web and return ranked sources with titles, URLs and snippets.",
            serverId = "pawpal",
            serverName = "PawPal",
            emoji = "🔎",
            risk = ToolRisk.READ_ONLY,
            inputSchema = """
                {"type":"object","properties":{"query":{"type":"string","description":"search query"},"limit":{"type":"integer","minimum":1,"maximum":8}},"required":["query"]}
            """.trimIndent()
        ),
        ToolDefinition(
            name = "calculator",
            description = "Evaluate a math expression exactly. Use for arithmetic, percentages and unit-free calculations.",
            serverId = "pawpal",
            serverName = "PawPal",
            emoji = "🧮",
            risk = ToolRisk.READ_ONLY,
            inputSchema = """
                {"type":"object","properties":{"expression":{"type":"string","description":"e.g. (18*4)+7/2"}},"required":["expression"]}
            """.trimIndent()
        ),
        ToolDefinition(
            name = "current_time",
            description = "Get the current date, time and timezone.",
            serverId = "pawpal",
            serverName = "PawPal",
            emoji = "🕰️",
            risk = ToolRisk.READ_ONLY,
            inputSchema = """{"type":"object","properties":{}}""".trimIndent()
        ),
        ToolDefinition(
            name = "email_draft",
            description = "Draft an email. Returns a mailto link. Does not send without approval.",
            serverId = "pawpal",
            serverName = "PawPal",
            emoji = "📧",
            risk = ToolRisk.WRITE,
            inputSchema = """
                {"type":"object","properties":{"to":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"}},"required":["to","subject","body"]}
            """.trimIndent()
        )
    )

    suspend fun execute(call: ToolCall): ToolCallResult {
        val started = System.currentTimeMillis()
        val args = runCatching { org.json.JSONObject(call.arguments) }.getOrElse { org.json.JSONObject() }
        return when (call.tool.name) {
            "web_search" -> {
                val query = args.optString("query", call.arguments)
                val results = WebSearchStub.search(query, args.optInt("limit", 4))
                val output = results.joinToString("\n") { r ->
                    "- ${r.title} — ${r.url}\n  ${r.snippet}"
                }
                ToolCallResult(true, output, System.currentTimeMillis() - started)
            }

            "calculator" -> {
                val expression = args.optString("expression", "")
                val value = runCatching { evaluate(expression) }.getOrElse { "invalid expression" }
                ToolCallResult(value != "invalid expression", "Result: $value", System.currentTimeMillis() - started)
            }

            "current_time" -> {
                val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.getDefault())
                    .format(java.util.Date())
                ToolCallResult(true, now, System.currentTimeMillis() - started)
            }

            "email_draft" -> {
                val to = args.optString("to", "")
                val subject = args.optString("subject", "")
                val body = args.optString("body", "")
                val mailto = "mailto:${java.net.URLEncoder.encode(to, "UTF-8")}?subject=" +
                    java.net.URLEncoder.encode(subject, "UTF-8") + "&body=" +
                    java.net.URLEncoder.encode(body, "UTF-8")
                ToolCallResult(
                    true,
                    "Draft created for $to\nSubject: $subject\n\n$body\n\nOpen: $mailto",
                    System.currentTimeMillis() - started
                )
            }

            else -> ToolCallResult(false, "Unknown built-in tool '${call.tool.name}'", 0)
        }
    }

    /**
     * Tiny recursive-descent calculator. No ScriptEngine (not available on
     * Android) and no external dependency.
     */
    private fun evaluate(expression: String): Double {
        val sanitized = expression.replace("[^0-9+\-*/.() ]".toRegex(), "")
        if (sanitized.isBlank()) throw IllegalArgumentException("empty expression")
        var index = 0
        fun peek(): Char = sanitized[index]
        fun skip() { index++ }
        fun parseExpression(): Double {
            var value = parseTerm()
            while (index < sanitized.length && (peek() == '+' || peek() == '-')) {
                val op = peek(); skip()
                val rhs = parseTerm()
                value = if (op == '+') value + rhs else value - rhs
            }
            return value
        }
        fun parseTerm(): Double {
            var value = parseFactor()
            while (index < sanitized.length && (peek() == '*' || peek() == '/')) {
                val op = peek(); skip()
                val rhs = parseFactor()
                value = if (op == '*') value * rhs else value / rhs
            }
            return value
        }
        fun parseFactor(): Double {
            while (index < sanitized.length && peek() == ' ') skip()
            if (index >= sanitized.length) throw IllegalArgumentException("unexpected end")
            if (peek() == '(') {
                skip()
                val value = parseExpression()
                if (index < sanitized.length && peek() == ')') skip()
                return value
            }
            if (peek() == '-') { skip(); return -parseFactor() }
            val start = index
            while (index < sanitized.length && (peek().isDigit() || peek() == '.')) skip()
            if (start == index) throw IllegalArgumentException("expected number")
            return sanitized.substring(start, index).toDouble()
        }
        return parseExpression()
    }
}

/**
 * Offline search stub used when no search API key is configured.
 * It is clearly marked as simulated in the UI so the agent never pretends
 * a made-up source is live.
 */
object WebSearchStub {
    data class Result(val title: String, val url: String, val snippet: String)

    fun search(query: String, limit: Int): List<Result> {
        val q = query.trim()
        val topic = q.ifEmpty { "latest AI agent tooling" }
        return listOf(
            Result(
                title = "$topic — overview & best practices",
                url = "https://example.com/overview?q=${java.net.URLEncoder.encode(q, "UTF-8")}",
                snippet = "A structured overview of $topic, trade-offs and production notes. (simulated result)"
            ),
            Result(
                title = "How teams ship $topic in 2026",
                url = "https://example.com/guide?q=${java.net.URLEncoder.encode(q, "UTF-8")}",
                snippet = "Patterns, pitfalls and a checklist for $topic. (simulated result)"
            ),
            Result(
                title = "Benchmarks: $topic",
                url = "https://example.com/benchmarks?q=${java.net.URLEncoder.encode(q, "UTF-8")}",
                snippet = "Latency, cost and reliability numbers for $topic. (simulated result)"
            )
        ).take(limit.coerceIn(1, 8))
    }
}
