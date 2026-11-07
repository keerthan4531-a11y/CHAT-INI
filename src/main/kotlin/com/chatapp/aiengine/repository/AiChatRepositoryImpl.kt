package com.chatapp.aiengine.repository

import com.chatapp.aiengine.config.AiEngineConfig
import com.chatapp.aiengine.core.AgentLoop
import com.chatapp.aiengine.core.AgentMemory
import com.chatapp.aiengine.core.ApprovalGate
import com.chatapp.aiengine.core.AutoApprovalGate
import com.chatapp.aiengine.core.BuiltInTools
import com.chatapp.aiengine.core.CompositeToolExecutor
import com.chatapp.aiengine.core.LlmAgentBrain
import com.chatapp.aiengine.core.ToolRegistry
import com.chatapp.aiengine.mcp.McpManager
import com.chatapp.aiengine.mcp.McpToolExecutor
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
import com.chatapp.aiengine.network.ProxyPool
import com.chatapp.aiengine.network.RateLimiter
import com.chatapp.aiengine.network.providers.AritekT2vProvider
import com.chatapp.aiengine.network.providers.ChatGptFreeProvider
import com.chatapp.aiengine.network.providers.GaiCloudProvider
import com.chatapp.aiengine.network.providers.OneSearchProvider
import com.chatapp.aiengine.network.providers.OpenAiCompatibleProvider
import com.chatapp.aiengine.network.providers.OxAlphaProvider
import com.chatapp.aiengine.network.providers.ZyraProvider
import com.chatapp.aiengine.core.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Default implementation of [AiChatRepository].
 *
 * Adds three production concerns on top of the original streaming engine:
 * 1. **Per-IP quota + proxy lanes** — no global daily cap, 50 msgs/IP/day,
 *    every healthy proxy adds another lane.
 * 2. **MCP tool layer** — connect GitHub/email/Notion/files, discover tools,
 *    execute them through the agent.
 * 3. **Agent runtime** — plan, act, observe, reflect, answer with hard caps.
 */
class AiChatRepositoryImpl(
    private val config: AiEngineConfig = AiEngineConfig(),
    customProviders: List<AiProvider> = emptyList()
) : AiChatRepository {

    private var activeModel: AiModel = config.defaultModel
    private var webSearchEnabled: Boolean = config.isWebSearchDefaultEnabled
    private var rotationStrategy: RotationStrategy = RotationStrategy.LEAST_USED
    private var mcpServers: List<McpServerConfig> = config.mcpServers
    private var approvalGate: ApprovalGate = AutoApprovalGate

    private val rateLimiter = RateLimiter(config.rateLimitPolicy)
    private val proxyPool = ProxyPool(
        initialProxies = config.proxies,
        rateLimiter = rateLimiter,
        policy = config.rateLimitPolicy
    )
    private val mcpManager = McpManager()
    private val builtIns = BuiltInTools()
    private val toolRegistry = ToolRegistry()
    private val memory = AgentMemory(config.memoryFile?.let(::File))

    private val providers: List<AiProvider>

    init {
        proxyPool.setStrategy(rotationStrategy)
        toolRegistry.registerAll(builtIns.definitions())
        providers = listOf(
            ChatGptFreeProvider(baseUrl = config.chatGptFreeBaseUrl, proxyPool = proxyPool),
            AritekT2vProvider(proxyPool = proxyPool),
            OneSearchProvider(proxyPool = proxyPool),
            ZyraProvider(proxyPool = proxyPool),
            GaiCloudProvider(baseUrl = config.gaiBaseUrl, proxyPool = proxyPool),
            OxAlphaProvider(baseUrl = config.oxAlphaBaseUrl, proxyPool = proxyPool),
            OpenAiCompatibleProvider(
                endpointUrl = config.openAiEndpoint,
                apiKey = config.openAiApiKey,
                proxyPool = proxyPool
            )
        ) + customProviders
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = flow {
        // Internal brain calls never consume the user's message quota.
        if (request.internal) {
            emitAll(route(request))
            return@flow
        }

        val selection = proxyPool.acquire(request.ipKey)
        if (selection == null) {
            val snapshot = proxyPool.snapshot(request.ipKey)
            emit(StreamEvent.UsageUpdated(snapshot))
            emit(
                StreamEvent.RateLimitReached(
                    snapshot = snapshot,
                    retryAt = snapshot.resetAt
                )
            )
            emit(
                StreamEvent.Error(
                    IllegalStateException(
                        "All lanes are empty for today. Add a healthy proxy to get another " +
                            "${config.rateLimitPolicy.perProxyDailyLimit} messages, or try again after the reset."
                    )
                )
            )
            return@flow
        }

        emit(StreamEvent.ProxyRotated(selection.proxy))
        emit(StreamEvent.UsageUpdated(selection.snapshot))
        emitAll(route(request.copy(proxy = selection.proxy)))
    }.flowOn(Dispatchers.IO)

    override fun streamAgent(request: ChatRequest): Flow<StreamEvent> = flow {
        val selection = proxyPool.acquire(request.ipKey)
        if (selection == null) {
            val snapshot = proxyPool.snapshot(request.ipKey)
            emit(StreamEvent.UsageUpdated(snapshot))
            emit(StreamEvent.RateLimitReached(snapshot, snapshot.resetAt))
            emit(
                StreamEvent.Error(
                    IllegalStateException(
                        "No message lanes left today. Rotate in a proxy or wait for the reset."
                    )
                )
            )
            return@flow
        }

        emit(StreamEvent.ProxyRotated(selection.proxy))
        emit(StreamEvent.UsageUpdated(selection.snapshot))

        // Bring up enabled MCP servers (idempotent).
        val enabled = mcpServers.filter { it.enabled }
        enabled.forEach { server ->
            if (!mcpManager.isConnected(server.id)) {
                runCatching { mcpManager.connect(server) }
            }
        }

        // Rebuild the visible tool catalog: built-ins always, MCP when connected.
        toolRegistry.clear()
        toolRegistry.registerAll(builtIns.definitions())
        toolRegistry.registerAll(mcpManager.toolsFor(enabled))

        val requestedServers = request.mcpServerIds
        val serverIds = (if (requestedServers.isEmpty()) enabled.map { it.id }.toSet() else requestedServers) + "pawpal"

        val brain = LlmAgentBrain(
            repository = this@AiChatRepositoryImpl,
            model = request.model,
            proxy = selection.proxy,
            ipKey = request.ipKey
        )
        val executor = CompositeToolExecutor(
            mcp = McpToolExecutor(mcpManager),
            builtIns = builtIns
        )
        val loop = AgentLoop(
            brain = brain,
            tools = toolRegistry,
            executor = executor,
            memory = memory,
            approvalGate = approvalGate,
            usage = selection.snapshot
        )

        emitAll(loop.run(request.copy(proxy = selection.proxy, mcpServerIds = serverIds)))
    }.flowOn(Dispatchers.IO)

    private fun route(request: ChatRequest): Flow<StreamEvent> {
        val provider = providers.firstOrNull { it.canHandle(request.model) }
        if (provider == null) {
            return flowOf(
                StreamEvent.Error(
                    IllegalStateException(
                        "No provider registered for ${request.model.displayName} (${request.model.id})"
                    )
                )
            )
        }
        val finalRequest = if (request.enableWebSearch != webSearchEnabled) {
            request.copy(enableWebSearch = request.enableWebSearch || webSearchEnabled)
        } else {
            request
        }
        return provider.streamChat(finalRequest)
    }

    override fun getAvailableModels(): List<AiModel> = AiModel.DEFAULT_MODELS

    override fun getActiveModel(): AiModel = activeModel

    override fun setActiveModel(model: AiModel) {
        activeModel = model
    }

    override fun isWebSearchEnabled(): Boolean = webSearchEnabled

    override fun setWebSearchEnabled(enabled: Boolean) {
        webSearchEnabled = enabled
    }

    override fun usage(ipKey: String): UsageSnapshot = proxyPool.snapshot(ipKey)

    override fun proxies(): List<ProxyEndpoint> = proxyPool.proxies.value

    override fun setProxies(proxies: List<ProxyEndpoint>) = proxyPool.setProxies(proxies)

    override fun addProxy(proxy: ProxyEndpoint) = proxyPool.addProxy(proxy)

    override fun removeProxy(id: String) = proxyPool.removeProxy(id)

    override fun setRotationStrategy(strategy: RotationStrategy) {
        rotationStrategy = strategy
        proxyPool.setStrategy(strategy)
    }

    override fun rotationStrategy(): RotationStrategy = rotationStrategy

    override suspend fun checkProxyHealth(): List<ProxyEndpoint> = proxyPool.checkAllHealth()

    override fun mcpServers(): List<McpServerConfig> = mcpServers

    override fun setMcpServers(servers: List<McpServerConfig>) {
        mcpServers = servers
    }

    override suspend fun connectMcp(config: McpServerConfig): McpServerStatus {
        val enabledConfig = config.copy(enabled = true)
        mcpServers = mcpServers.map { if (it.id == enabledConfig.id) enabledConfig else it }
        val status = mcpManager.connect(enabledConfig)
        if (status.connected) {
            mcpManager.toolsFor(listOf(enabledConfig)).forEach { toolRegistry.register(it) }
        }
        return status
    }

    override suspend fun disconnectMcp(id: String) {
        mcpManager.disconnect(id)
        mcpServers = mcpServers.map { if (it.id == id) it.copy(enabled = false) else it }
        toolRegistry.removeServer(id)
    }

    override fun mcpStatuses(): List<McpServerStatus> = mcpManager.allStatuses()

    override fun tools(): List<ToolDefinition> = toolRegistry.all()

    override fun setApprovalGate(gate: ApprovalGate) {
        approvalGate = gate
    }

    override fun resetUsage() = proxyPool.resetUsage()

    override fun memoryFacts(): List<MemoryFact> = memory.allFacts()

    override fun clearMemory() = memory.clear()
}
