package com.chatapp.aiengine.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.aiengine.core.AiEngine
import com.chatapp.aiengine.model.AgentMode
import com.chatapp.aiengine.model.AgentPlan
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.aiengine.model.McpServerConfig
import com.chatapp.aiengine.model.McpServerStatus
import com.chatapp.aiengine.model.MemoryFact
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.RotationStrategy
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.ToolDefinition
import com.chatapp.aiengine.model.UsageSnapshot
import com.chatapp.aiengine.model.WebCitation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class Mascot { MOCHI, BISCUIT }

enum class SheetKind { NONE, MODEL, MCP, PROXY, USAGE, SETTINGS }

/**
 * UI state for the doodle chat screen. One immutable snapshot keeps Compose
 * recomposition predictable while tokens stream in.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val selectedModel: AiModel = AiModel.GPT_4O,
    val mode: AgentMode = AgentMode.CHAT,
    val isWebSearchEnabled: Boolean = false,
    val isThinkEnabled: Boolean = false,
    val isDeepMode: Boolean = false,
    val isGenerating: Boolean = false,
    val isThinking: Boolean = false,
    val liveThinkingText: String = "",
    val liveContentText: String = "",
    val livePlan: AgentPlan? = null,
    val liveToolCalls: List<ToolCall> = emptyList(),
    val liveCitations: List<WebCitation> = emptyList(),
    val isWebSearching: Boolean = false,
    val errorMessage: String? = null,
    val usage: UsageSnapshot? = null,
    val ipKey: String = "local-device",
    val mcpServers: List<McpServerConfig> = emptyList(),
    val mcpStatuses: List<McpServerStatus> = emptyList(),
    val proxies: List<ProxyEndpoint> = emptyList(),
    val rotationStrategy: RotationStrategy = RotationStrategy.LEAST_USED,
    val tools: List<ToolDefinition> = emptyList(),
    val memoryFacts: List<MemoryFact> = emptyList(),
    val darkTheme: Boolean? = null,
    val mascot: Mascot = Mascot.MOCHI,
    val showAnimations: Boolean = true,
    val pendingApproval: ToolCall? = null,
    val sheet: SheetKind = SheetKind.NONE,
    val isAgentPanelOpen: Boolean = false,
    val lastProxy: ProxyEndpoint? = null
) {
    val assistantName: String get() = if (mascot == Mascot.MOCHI) "Mochi" else "Biscuit"
    val assistantEmoji: String get() = if (mascot == Mascot.MOCHI) "🐱" else "🐶"
    val usagePercent: Float get() = usage?.percent ?: 0f
    val messagesUsed: Int get() = usage?.totalUsed ?: 0
    val messageLimit: Int get() = usage?.totalCapacity ?: 50
}

/**
 * Full ViewModel for PawPal.
 *
 * - Streams chat or agent runs from [AiEngine].
 * - Mirrors plan / tool / usage events into a single UI state.
 * - Bridges write-tool approval back to the UI (human-in-the-loop).
 * - Owns MCP connections, proxy pool settings and theme/mascot preferences.
 */
class ChatViewModelExample(
    private val engine: AiEngine = AiEngine.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            selectedModel = engine.repository.getActiveModel(),
            isWebSearchEnabled = engine.repository.isWebSearchEnabled(),
            mcpServers = engine.mcpServers(),
            proxies = engine.proxies(),
            rotationStrategy = engine.rotationStrategy(),
            tools = engine.tools(),
            usage = engine.usage("local-device")
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    init {
        refreshUsage()
        refreshMcp()
        engine.setApprovalGate { call ->
            val deferred = CompletableDeferred<Boolean>()
            pendingApprovals[call.id] = deferred
            _uiState.update { it.copy(pendingApproval = call) }
            val approved = deferred.await()
            pendingApprovals.remove(call.id)
            _uiState.update { it.copy(pendingApproval = null) }
            approved
        }
    }

    fun selectModel(model: AiModel) {
        engine.repository.setActiveModel(model)
        _uiState.update { it.copy(selectedModel = model, errorMessage = null) }
    }

    fun setMode(mode: AgentMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun toggleWebSearch(enabled: Boolean) {
        engine.repository.setWebSearchEnabled(enabled)
        _uiState.update { it.copy(isWebSearchEnabled = enabled) }
    }

    fun toggleThink(enabled: Boolean) {
        _uiState.update { it.copy(isThinkEnabled = enabled) }
    }

    fun toggleDeepMode() {
        _uiState.update { it.copy(isDeepMode = !it.isDeepMode) }
    }

    fun toggleTheme() {
        _uiState.update { current ->
            val next = current.darkTheme ?: false
            current.copy(darkTheme = !next)
        }
    }

    fun setDarkTheme(value: Boolean?) {
        _uiState.update { it.copy(darkTheme = value) }
    }

    fun setMascot(mascot: Mascot) {
        _uiState.update { it.copy(mascot = mascot) }
    }

    fun toggleAnimations() {
        _uiState.update { it.copy(showAnimations = !it.showAnimations) }
    }

    fun openSheet(sheet: SheetKind) {
        _uiState.update { it.copy(sheet = sheet) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheet = SheetKind.NONE) }
    }

    fun toggleAgentPanel() {
        _uiState.update { it.copy(isAgentPanelOpen = !it.isAgentPanelOpen) }
    }

    fun closeAgentPanel() {
        _uiState.update { it.copy(isAgentPanelOpen = false) }
    }

    /** Sends a user prompt and streams the answer (chat or agent). */
    fun sendMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(role = ChatRole.USER, content = trimmed)
        val history = _uiState.value.messages
        _uiState.update {
            it.copy(
                messages = history + userMessage,
                isGenerating = true,
                isThinking = false,
                liveThinkingText = "",
                liveContentText = "",
                livePlan = null,
                liveToolCalls = emptyList(),
                liveCitations = emptyList(),
                isWebSearching = false,
                errorMessage = null
            )
        }

        val request = ChatRequest(
            model = _uiState.value.selectedModel,
            prompt = trimmed,
            history = history,
            enableWebSearch = _uiState.value.isWebSearchEnabled,
            mode = _uiState.value.mode,
            budget = if (_uiState.value.isDeepMode) {
                com.chatapp.aiengine.model.AgentBudget(
                    maxSteps = 16,
                    maxToolCalls = 24,
                    maxDurationMs = 180_000L,
                    maxTokens = 48_000
                )
            } else {
                com.chatapp.aiengine.model.AgentBudget()
            },
            ipKey = _uiState.value.ipKey,
            mcpServerIds = _uiState.value.mcpServers.filter { it.enabled }.map { it.id }.toSet(),
            requireApprovalForWrites = true
        )

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val thinking = StringBuilder()
            val content = StringBuilder()
            val citations = mutableListOf<WebCitation>()
            val tools = mutableListOf<ToolCall>()
            var plan: AgentPlan? = null

            val stream = if (request.mode == AgentMode.AGENT) {
                engine.streamAgent(request)
            } else {
                engine.streamChat(request)
            }

            runCatching {
                stream.collect { event ->
                    when (event) {
                        is StreamEvent.ProxyRotated -> {
                            _uiState.update { it.copy(lastProxy = event.proxy) }
                        }

                        is StreamEvent.UsageUpdated -> {
                            _uiState.update { it.copy(usage = event.snapshot) }
                        }

                        is StreamEvent.RateLimitReached -> {
                            _uiState.update {
                                it.copy(
                                    usage = event.snapshot,
                                    errorMessage = "All message lanes are empty for today. " +
                                        "Add a proxy lane or wait for the reset."
                                )
                            }
                        }

                        is StreamEvent.AgentPlanUpdated -> {
                            plan = event.plan
                            _uiState.update { it.copy(livePlan = event.plan) }
                        }

                        is StreamEvent.AgentStepStarted -> {
                            plan = plan?.withStep(event.step) ?: event.step.let { step ->
                                AgentPlan(goal = request.prompt, steps = listOf(step))
                            }
                            _uiState.update { it.copy(livePlan = plan) }
                        }

                        is StreamEvent.AgentStepCompleted -> {
                            plan = plan?.withStep(event.step)
                            _uiState.update { it.copy(livePlan = plan) }
                        }

                        is StreamEvent.ToolCallStarted -> {
                            tools.add(event.call)
                            _uiState.update { it.copy(liveToolCalls = tools.toList()) }
                        }

                        is StreamEvent.ToolCallCompleted -> {
                            tools.removeAll { it.id == event.call.id }
                            tools.add(event.call)
                            _uiState.update { it.copy(liveToolCalls = tools.toList()) }
                        }

                        is StreamEvent.ToolApprovalRequested -> {
                            _uiState.update { it.copy(pendingApproval = event.call) }
                        }

                        is StreamEvent.ThinkingStarted -> {
                            _uiState.update { it.copy(isThinking = true) }
                        }

                        is StreamEvent.ThinkingDelta -> {
                            thinking.append(event.text)
                            _uiState.update {
                                it.copy(
                                    isThinking = true,
                                    liveThinkingText = thinking.toString()
                                )
                            }
                        }

                        is StreamEvent.ThinkingCompleted -> {
                            _uiState.update { it.copy(isThinking = false) }
                        }

                        is StreamEvent.WebSearchStarted -> {
                            _uiState.update { it.copy(isWebSearching = true) }
                        }

                        is StreamEvent.WebSearchCompleted -> {
                            citations.clear()
                            citations.addAll(event.citations)
                            _uiState.update {
                                it.copy(
                                    isWebSearching = false,
                                    liveCitations = citations.toList()
                                )
                            }
                        }

                        is StreamEvent.ContentDelta -> {
                            content.append(event.text)
                            _uiState.update {
                                it.copy(
                                    isThinking = false,
                                    liveContentText = content.toString()
                                )
                            }
                        }

                        is StreamEvent.CitationAdded -> {
                            citations.add(event.citation)
                            _uiState.update { it.copy(liveCitations = citations.toList()) }
                        }

                        is StreamEvent.MemoryUpdated -> {
                            _uiState.update {
                                it.copy(memoryFacts = it.memoryFacts + event.fact)
                            }
                        }

                        is StreamEvent.AgentCompleted -> {
                            commitAssistant(event.finalMessage)
                            _uiState.update {
                                it.copy(
                                    usage = event.usage,
                                    livePlan = null,
                                    liveToolCalls = emptyList(),
                                    liveContentText = "",
                                    liveThinkingText = "",
                                    liveCitations = emptyList(),
                                    isGenerating = false,
                                    isThinking = false,
                                    isWebSearching = false
                                )
                            }
                            refreshUsage()
                        }

                        is StreamEvent.Completed -> {
                            commitAssistant(event.finalMessage)
                            _uiState.update {
                                it.copy(
                                    liveContentText = "",
                                    liveThinkingText = "",
                                    liveCitations = emptyList(),
                                    isGenerating = false,
                                    isThinking = false,
                                    isWebSearching = false
                                )
                            }
                            refreshUsage()
                        }

                        is StreamEvent.Error -> {
                            val partial = content.toString()
                            if (partial.isNotBlank()) {
                                commitAssistant(
                                    ChatMessage(
                                        role = ChatRole.ASSISTANT,
                                        content = partial,
                                        reasoningContent = thinking.toString().ifEmpty { null },
                                        citations = citations.toList(),
                                        agentPlan = plan,
                                        toolCalls = tools.toList(),
                                        modelName = request.model.displayName
                                    )
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    errorMessage = event.throwable.message ?: "Something went wrong",
                                    isGenerating = false,
                                    isThinking = false,
                                    isWebSearching = false,
                                    liveContentText = "",
                                    liveThinkingText = "",
                                    livePlan = null,
                                    liveToolCalls = emptyList()
                                )
                            }
                            refreshUsage()
                        }

                        else -> { /* Started, etc. */ }
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message ?: "Something went wrong",
                        isGenerating = false,
                        isThinking = false,
                        isWebSearching = false
                    )
                }
            }
        }
    }

    private fun commitAssistant(message: ChatMessage) {
        _uiState.update { current ->
            if (current.messages.any { it.id == message.id }) current
            else current.copy(messages = current.messages + message)
        }
    }

    fun stopGeneration() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                isThinking = false,
                isWebSearching = false,
                liveContentText = "",
                liveThinkingText = "",
                livePlan = null,
                liveToolCalls = emptyList()
            )
        }
    }

    fun approveTool(call: ToolCall) {
        pendingApprovals[call.id]?.complete(true)
        _uiState.update { it.copy(pendingApproval = null) }
    }

    fun rejectTool(call: ToolCall) {
        pendingApprovals[call.id]?.complete(false)
        _uiState.update { it.copy(pendingApproval = null) }
    }

    fun clearChat() {
        stopGeneration()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                livePlan = null,
                liveToolCalls = emptyList(),
                liveContentText = "",
                liveThinkingText = "",
                errorMessage = null
            )
        }
    }

    fun regenerate() {
        val lastUser = _uiState.value.messages.lastOrNull { it.role == ChatRole.USER } ?: return
        val withoutLastAssistant = _uiState.value.messages.let { messages ->
            val index = messages.indexOfLast { it.role == ChatRole.ASSISTANT }
            if (index >= 0) messages.toMutableList().apply { removeAt(index) } else messages
        }
        _uiState.update { it.copy(messages = withoutLastAssistant) }
        sendMessage(lastUser.content)
    }

    fun refreshUsage() {
        _uiState.update { it.copy(usage = engine.usage(it.ipKey)) }
    }

    fun refreshMcp() {
        _uiState.update {
            it.copy(
                mcpServers = engine.mcpServers(),
                mcpStatuses = engine.mcpStatuses(),
                tools = engine.tools()
            )
        }
    }

    fun connectMcp(config: McpServerConfig) {
        viewModelScope.launch {
            runCatching { engine.connectMcp(config) }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            mcpServers = engine.mcpServers(),
                            mcpStatuses = engine.mcpStatuses(),
                            tools = engine.tools(),
                            errorMessage = if (status.connected) null else status.message
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun disconnectMcp(id: String) {
        viewModelScope.launch {
            runCatching { engine.disconnectMcp(id) }
            refreshMcp()
        }
    }

    fun setRotationStrategy(strategy: RotationStrategy) {
        engine.setRotationStrategy(strategy)
        _uiState.update { it.copy(rotationStrategy = strategy) }
    }

    fun rotateNow() {
        engine.setRotationStrategy(RotationStrategy.ROUND_ROBIN)
        _uiState.update {
            it.copy(
                rotationStrategy = RotationStrategy.ROUND_ROBIN,
                usage = engine.usage(it.ipKey)
            )
        }
    }

    fun checkProxyHealth() {
        viewModelScope.launch {
            runCatching { engine.checkProxyHealth() }
                .onSuccess { checked ->
                    _uiState.update {
                        it.copy(proxies = checked, usage = engine.usage(it.ipKey))
                    }
                }
        }
    }

    fun addProxy(proxy: ProxyEndpoint) {
        engine.addProxy(proxy)
        _uiState.update {
            it.copy(
                proxies = engine.proxies(),
                usage = engine.usage(it.ipKey)
            )
        }
    }

    fun removeProxy(id: String) {
        engine.removeProxy(id)
        _uiState.update {
            it.copy(
                proxies = engine.proxies(),
                usage = engine.usage(it.ipKey)
            )
        }
    }

    fun resetUsage() {
        engine.resetUsage()
        refreshUsage()
    }

    fun clearMemory() {
        engine.clearMemory()
        _uiState.update { it.copy(memoryFacts = emptyList()) }
    }

    fun setIpKey(ipKey: String) {
        _uiState.update { it.copy(ipKey = ipKey, usage = engine.usage(ipKey)) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
