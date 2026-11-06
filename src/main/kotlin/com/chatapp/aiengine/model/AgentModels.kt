package com.chatapp.aiengine.model

import java.util.UUID

/**
 * Domain models for the agent runtime, tool layer, MCP connections and the
 * per-IP / proxy-pool rate limiting system.
 *
 * Everything here is plain Kotlin (no Android types) so the engine keeps
 * working on the JVM and on Android.
 */

enum class AgentMode {
    /** Plain chat: one model call, optional web search / thinking. */
    CHAT,

    /** Agent: plan -> act with tools -> observe -> reflect -> answer. */
    AGENT
}

enum class StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED
}

enum class ToolRisk {
    /** Reads data. Safe to auto-run. */
    READ_ONLY,

    /** Creates or updates something (send mail, open PR, write file). */
    WRITE,

    /** Deletes or overwrites. Always needs explicit approval. */
    DESTRUCTIVE
}

/**
 * A typed tool contract. The schema is a JSON-Schema string so it can be sent
 * verbatim to any model / MCP server.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val serverId: String,
    val serverName: String,
    val emoji: String,
    val risk: ToolRisk = ToolRisk.READ_ONLY,
    val inputSchema: String = "{}",
    val requiresApproval: Boolean = risk != ToolRisk.READ_ONLY
) {
    val qualifiedName: String get() = "$serverId__$name"
}

data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val tool: ToolDefinition,
    val arguments: String = "{}",
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
    val error: String? = null,
    val durationMs: Long = 0,
    val simulated: Boolean = false,
    val approved: Boolean = true
) {
    val isComplete: Boolean get() = status == StepStatus.DONE || status == StepStatus.FAILED
}

data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val toolCalls: List<ToolCall> = emptyList(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null
) {
    val isComplete: Boolean get() = status == StepStatus.DONE || status == StepStatus.FAILED
}

data class AgentPlan(
    val goal: String,
    val steps: List<AgentStep>,
    val status: StepStatus = StepStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val maxSteps: Int = 8
) {
    val completedSteps: Int get() = steps.count { it.status == StepStatus.DONE }
    val progress: Float get() = if (steps.isEmpty()) 0f else completedSteps.toFloat() / steps.size

    fun withStep(step: AgentStep): AgentPlan =
        copy(steps = steps.map { if (it.id == step.id) step else it })

    fun nextPending(): AgentStep? = steps.firstOrNull { it.status == StepStatus.PENDING }
}

/** Hard safety caps. One runaway loop must never burn a whole day of quota. */
data class AgentBudget(
    val maxSteps: Int = 8,
    val maxToolCalls: Int = 12,
    val maxDurationMs: Long = 90_000L,
    val maxTokens: Int = 24_000
)

enum class McpTransport {
    STDIO,
    STREAMABLE_HTTP
}

/**
 * A configured MCP server. `env` is only kept in memory for the current
 * process — never persist tokens to disk.
 */
data class McpServerConfig(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val transport: McpTransport,
    val command: String = "",
    val args: List<String> = emptyList(),
    val url: String = "",
    val env: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = false,
    val toolAllowlist: List<String> = emptyList(),
    val simulated: Boolean = false
)

data class McpServerStatus(
    val serverId: String,
    val connected: Boolean,
    val toolCount: Int = 0,
    val latencyMs: Long = 0,
    val message: String = ""
)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: String
)

enum class ProxyType { HTTP, HTTPS, SOCKS5 }

data class ProxyEndpoint(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.HTTP,
    val username: String = "",
    val password: String = "",
    val region: String = "",
    val healthy: Boolean = true,
    val latencyMs: Int = 0,
    val usedToday: Int = 0,
    val simulated: Boolean = false
) {
    val address: String get() = "$host:$port"
}

enum class RotationStrategy {
    LEAST_USED,
    ROUND_ROBIN,
    RANDOM,
    FASTEST
}

/**
 * Product policy: no global daily cap. Each IP gets 50 messages/day, and every
 * healthy proxy lane adds another 50. Rotating the pool is the intended way to
 * scale a single user or team beyond one IP.
 */
data class RateLimitPolicy(
    val perIpDailyLimit: Int = 50,
    val perProxyDailyLimit: Int = 50,
    val windowHours: Int = 24,
    val maxLanes: Int = 20
)

data class LaneUsage(
    val id: String,
    val label: String,
    val emoji: String,
    val used: Int,
    val limit: Int,
    val healthy: Boolean,
    val latencyMs: Int,
    val region: String,
    val isDirect: Boolean = false
) {
    val remaining: Int get() = (limit - used).coerceAtLeast(0)
    val percent: Float get() = if (limit == 0) 0f else used.toFloat() / limit
}

data class UsageSnapshot(
    val ipKey: String,
    val ipUsed: Int,
    val ipLimit: Int,
    val proxyUsed: Int,
    val proxyLimit: Int,
    val healthyProxies: Int,
    val totalCapacity: Int,
    val resetAt: Long,
    val lanes: List<LaneUsage> = emptyList()
) {
    val totalUsed: Int get() = ipUsed + proxyUsed
    val remaining: Int get() = (ipLimit - ipUsed).coerceAtLeast(0) + (proxyLimit - proxyUsed).coerceAtLeast(0)
    val percent: Float get() = if (totalCapacity <= 0) 0f else totalUsed.toFloat() / totalCapacity
}

sealed interface RateLimitResult {
    data class Allowed(val lane: LaneUsage, val snapshot: UsageSnapshot) : RateLimitResult
    data class Rejected(val snapshot: UsageSnapshot, val retryAt: Long) : RateLimitResult
}

/** One memory tier of the agent. Kept small and typed on purpose. */
data class MemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val kind: String = "semantic",
    val createdAt: Long = System.currentTimeMillis()
)

data class AgentDecision(
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val finalAnswer: String? = null,
    val isPlanUpdate: Boolean = false
)
