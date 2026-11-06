package com.chatapp.aiengine.repository

import com.chatapp.aiengine.core.ApprovalGate
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.McpServerConfig
import com.chatapp.aiengine.model.McpServerStatus
import com.chatapp.aiengine.model.MemoryFact
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.RotationStrategy
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.model.ToolDefinition
import com.chatapp.aiengine.model.UsageSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Clean domain repository for driving AI conversations and agent runs.
 * Exposes simple, reactive Kotlin flows directly consumed by Android ViewModels.
 */
interface AiChatRepository {

    /** Streams real-time tokens (thinking, answer content, web search events) for the given request. */
    fun streamChat(request: ChatRequest): Flow<StreamEvent>

    /** Runs the plan -> act -> observe agent loop and emits plan/tool/usage events. */
    fun streamAgent(request: ChatRequest): Flow<StreamEvent>

    /** Lists all models supported by the engine. */
    fun getAvailableModels(): List<AiModel>

    /** Gets currently selected active model. */
    fun getActiveModel(): AiModel

    /** Updates currently selected active model. */
    fun setActiveModel(model: AiModel)

    /** Checks if web search is enabled globally by default. */
    fun isWebSearchEnabled(): Boolean

    /** Toggles global web search flag. */
    fun setWebSearchEnabled(enabled: Boolean)

    /** Current quota snapshot for a lane key (usually the public IP). */
    fun usage(ipKey: String): UsageSnapshot

    /** Proxy pool state. */
    fun proxies(): List<ProxyEndpoint>

    fun setProxies(proxies: List<ProxyEndpoint>)

    fun addProxy(proxy: ProxyEndpoint)

    fun removeProxy(id: String)

    fun setRotationStrategy(strategy: RotationStrategy)

    fun rotationStrategy(): RotationStrategy

    suspend fun checkProxyHealth(): List<ProxyEndpoint>

    /** Configured MCP servers (secrets stay in memory). */
    fun mcpServers(): List<McpServerConfig>

    fun setMcpServers(servers: List<McpServerConfig>)

    suspend fun connectMcp(config: McpServerConfig): McpServerStatus

    suspend fun disconnectMcp(id: String)

    fun mcpStatuses(): List<McpServerStatus>

    /** All tools currently visible to the agent (built-ins + connected MCP). */
    fun tools(): List<ToolDefinition>

    fun setApprovalGate(gate: ApprovalGate)

    /** Clears today's demo quota. */
    fun resetUsage()

    /** Durable semantic facts the agent remembers about the user/project. */
    fun memoryFacts(): List<MemoryFact>

    fun clearMemory()
}
