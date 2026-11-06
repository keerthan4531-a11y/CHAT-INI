package com.chatapp.aiengine.example

import com.chatapp.aiengine.core.AiEngine
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.model.WebCitation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State model representing the live chat screen.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val selectedModel: AiModel = AiModel.GPT_4O,
    val isWebSearchEnabled: Boolean = false,
    val isGenerating: Boolean = false,
    val isThinking: Boolean = false,
    val liveThinkingText: String = "",
    val liveContentText: String = "",
    val isWebSearching: Boolean = false,
    val liveCitations: List<WebCitation> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Example ViewModel demonstrating real-time token streaming with live thinking,
 * instant letter rendering, and web search citations.
 */
class ChatViewModelExample(
    private val engine: AiEngine = AiEngine.getInstance(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(
        ChatUiState(
            selectedModel = engine.repository.getActiveModel(),
            isWebSearchEnabled = engine.repository.isWebSearchEnabled()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    /**
     * Changes the active model (e.g. from GPT-4o to Ox Alpha GLM or DeepSeek R1).
     */
    fun selectModel(model: AiModel) {
        engine.repository.setActiveModel(model)
        _uiState.update { it.copy(selectedModel = model) }
    }

    /**
     * Toggles live web search on/off.
     */
    fun toggleWebSearch(enabled: Boolean) {
        engine.repository.setWebSearchEnabled(enabled)
        _uiState.update { it.copy(isWebSearchEnabled = enabled) }
    }

    /**
     * Sends user prompt and immediately starts rendering streaming response without blocking.
     */
    fun sendMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = trimmed
        )

        // 1. Instantly append user message to UI state
        val updatedHistory = _uiState.value.messages + userMessage
        _uiState.update {
            it.copy(
                messages = updatedHistory,
                isGenerating = true,
                isThinking = false,
                liveThinkingText = "",
                liveContentText = "",
                isWebSearching = false,
                liveCitations = emptyList(),
                errorMessage = null
            )
        }

        val request = ChatRequest(
            model = _uiState.value.selectedModel,
            prompt = trimmed,
            history = _uiState.value.messages,
            enableWebSearch = _uiState.value.isWebSearchEnabled
        )

        streamJob?.cancel()
        streamJob = scope.launch {
            val thinkingAccumulator = StringBuilder()
            val contentAccumulator = StringBuilder()
            val citationAccumulator = mutableListOf<WebCitation>()

            engine.streamChat(request).collect { event ->
                when (event) {
                    is StreamEvent.Started -> {
                        // Connection established!
                    }

                    is StreamEvent.WebSearchStarted -> {
                        _uiState.update { it.copy(isWebSearching = true) }
                    }

                    is StreamEvent.WebSearchCompleted -> {
                        citationAccumulator.addAll(event.citations)
                        _uiState.update {
                            it.copy(
                                isWebSearching = false,
                                liveCitations = citationAccumulator.toList()
                            )
                        }
                    }

                    is StreamEvent.ThinkingStarted -> {
                        _uiState.update { it.copy(isThinking = true) }
                    }

                    is StreamEvent.ThinkingDelta -> {
                        // Immediate token-by-token thinking streaming!
                        thinkingAccumulator.append(event.text)
                        _uiState.update {
                            it.copy(liveThinkingText = thinkingAccumulator.toString())
                        }
                    }

                    is StreamEvent.ThinkingCompleted -> {
                        _uiState.update { it.copy(isThinking = false) }
                    }

                    is StreamEvent.ContentDelta -> {
                        // Immediate token-by-token content streaming (like ChatGPT typing effect)!
                        contentAccumulator.append(event.text)
                        _uiState.update {
                            it.copy(
                                isThinking = false,
                                liveContentText = contentAccumulator.toString()
                            )
                        }
                    }

                    is StreamEvent.Completed -> {
                        // Commit completed assistant message into message list
                        val finalAssistantMessage = event.finalMessage
                        _uiState.update {
                            it.copy(
                                messages = it.messages + finalAssistantMessage,
                                isGenerating = false,
                                isThinking = false,
                                liveThinkingText = "",
                                liveContentText = "",
                                liveCitations = emptyList(),
                                isWebSearching = false
                            )
                        }
                    }

                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                isThinking = false,
                                errorMessage = event.throwable.localizedMessage ?: "Unknown error"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Cancels active generation in real-time.
     */
    fun stopGeneration() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                isThinking = false,
                isWebSearching = false
            )
        }
    }
}

