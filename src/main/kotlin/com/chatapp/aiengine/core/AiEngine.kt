package com.chatapp.aiengine.core

import com.chatapp.aiengine.config.AiEngineConfig
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
import com.chatapp.aiengine.repository.AiChatRepository
import com.chatapp.aiengine.repository.AiChatRepositoryImpl
import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for the PawPal AI engine.
 *
 * ```kotlin
 * val engine = AiEngine.getInstance()
 *
 * // Agent run with plan + tools + usage events
 * engine.streamAgent(
 *     ChatRequest(
 *         model = AiModel.GROK_4_1,
 *         prompt = "Research the cutest cat UI and email me a summary",
 *         mode = AgentMode.AGENT,
 *         ipKey = myIp
 *     )
 * ).collect { event -> ... }
 * ```
 */
class AiEngine private constructor(
    val config: AiEngineConfig,
    customProviders: List<AiProvider> = emptyList()
) {
    val repository: AiChatRepository = AiChatRepositoryImpl(
        config = config,
        customProviders = customProviders
    )

    /** Direct streaming of a single prompt text with optional web search. */
    fun streamPrompt(
        prompt: String,
        model: AiModel = repository.getActiveModel(),
        enableWebSearch: Boolean = false
    ): Flow<StreamEvent> {
        val request = ChatRequest(
            model = model,
            prompt = prompt,
            enableWebSearch = enableWebSearch
        )
        return repository.streamChat(request)
    }

    /** Full request streaming including multi-turn history. */
    fun streamChat(request: ChatRequest): Flow<StreamEvent> = repository.streamChat(request)

    /** Agent run: plan -> act -> observe -> answer. */
    fun streamAgent(request: ChatRequest): Flow<StreamEvent> = repository.streamAgent(request)

    fun usage(ipKey: String): UsageSnapshot = repository.usage(ipKey)

    fun proxies(): List<ProxyEndpoint> = repository.proxies()

    fun setProxies(proxies: List<ProxyEndpoint>) = repository.setProxies(proxies)

    fun addProxy(proxy: ProxyEndpoint) = repository.addProxy(proxy)

    fun removeProxy(id: String) = repository.removeProxy(id)

    fun setRotationStrategy(strategy: RotationStrategy) = repository.setRotationStrategy(strategy)

    fun rotationStrategy(): RotationStrategy = repository.rotationStrategy()

    suspend fun checkProxyHealth(): List<ProxyEndpoint> = repository.checkProxyHealth()

    fun mcpServers(): List<McpServerConfig> = repository.mcpServers()

    fun setMcpServers(servers: List<McpServerConfig>) = repository.setMcpServers(servers)

    suspend fun connectMcp(config: McpServerConfig): McpServerStatus = repository.connectMcp(config)

    suspend fun disconnectMcp(id: String) = repository.disconnectMcp(id)

    fun mcpStatuses(): List<McpServerStatus> = repository.mcpStatuses()

    fun tools(): List<ToolDefinition> = repository.tools()

    fun setApprovalGate(gate: ApprovalGate) = repository.setApprovalGate(gate)

    fun resetUsage() = repository.resetUsage()

    fun memoryFacts(): List<MemoryFact> = repository.memoryFacts()

    fun clearMemory() = repository.clearMemory()

    companion object {
        @Volatile
        private var instance: AiEngine? = null

        /** Returns existing singleton or initializes with default configuration. */
        fun getInstance(): AiEngine {
            return instance ?: synchronized(this) {
                instance ?: AiEngine(AiEngineConfig()).also { instance = it }
            }
        }

        /** Configures and initializes the custom engine singleton. */
        fun initialize(
            config: AiEngineConfig = AiEngineConfig(),
            customProviders: List<AiProvider> = emptyList()
        ): AiEngine {
            return synchronized(this) {
                AiEngine(config, customProviders).also { instance = it }
            }
        }
    }
}
