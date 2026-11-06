package com.chatapp.aiengine.core

import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.ProviderType
import com.chatapp.aiengine.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Common pluggable contract for AI service connectors.
 * Allows effortless addition of new LLM endpoints without altering UI or Repository code.
 */
interface AiProvider {

    /**
     * Identifies which provider family this implementation services.
     */
    val providerType: ProviderType

    /**
     * Checks if this provider has credentials, network capability, and support for the model.
     */
    fun canHandle(model: AiModel): Boolean = model.providerType == providerType

    /**
     * Initiates a real-time Server-Sent Events (SSE) stream yielding token-by-token events.
     */
    fun streamChat(request: ChatRequest): Flow<StreamEvent>
}

