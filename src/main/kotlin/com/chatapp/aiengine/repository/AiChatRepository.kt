package com.chatapp.aiengine.repository

import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Clean domain repository for driving AI conversations.
 * Exposes simple, reactive Kotlin flows directly consumed by Android ViewModels.
 */
interface AiChatRepository {

    /**
     * Streams real-time tokens (thinking, answer content, web search events) for the given request.
     */
    fun streamChat(request: ChatRequest): Flow<StreamEvent>

    /**
     * Lists all models supported by the engine.
     */
    fun getAvailableModels(): List<AiModel>

    /**
     * Gets currently selected active model.
     */
    fun getActiveModel(): AiModel

    /**
     * Updates currently selected active model.
     */
    fun setActiveModel(model: AiModel)

    /**
     * Checks if web search is enabled globally by default.
     */
    fun isWebSearchEnabled(): Boolean

    /**
     * Toggles global web search flag.
     */
    fun setWebSearchEnabled(enabled: Boolean)
}

