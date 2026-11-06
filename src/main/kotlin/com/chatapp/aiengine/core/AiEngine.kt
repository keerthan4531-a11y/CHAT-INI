package com.chatapp.aiengine.core

import com.chatapp.aiengine.config.AiEngineConfig
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.repository.AiChatRepository
import com.chatapp.aiengine.repository.AiChatRepositoryImpl
import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for the Multi-Model AI Engine.
 *
 * Example initialization in Application or ViewModel:
 * ```kotlin
 * // Basic usage:
 * val engine = AiEngine.getInstance()
 *
 * // Stream a prompt:
 * engine.streamPrompt("Explain quantum computing in simple terms", AiModel.GPT_4O)
 *     .collect { event ->
 *         when (event) {
 *             is StreamEvent.ThinkingDelta -> print(event.text)
 *             is StreamEvent.ContentDelta -> print(event.text)
 *             is StreamEvent.Completed -> println("\nDone!")
 *             else -> {}
 *         }
 *     }
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

    /**
     * Direct streaming of a single prompt text with optional web search.
     */
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

    /**
     * Full request streaming including multi-turn history.
     */
    fun streamChat(request: ChatRequest): Flow<StreamEvent> {
        return repository.streamChat(request)
    }

    companion object {
        @Volatile
        private var instance: AiEngine? = null

        /**
         * Returns existing singleton or initializes with default configuration.
         */
        fun getInstance(): AiEngine {
            return instance ?: synchronized(this) {
                instance ?: AiEngine(AiEngineConfig()).also { instance = it }
            }
        }

        /**
         * Configures and initializes custom engine singleton.
         */
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

