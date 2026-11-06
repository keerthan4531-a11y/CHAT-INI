package com.chatapp.aiengine.model

import java.util.UUID

/**
 * Payload configuration for requesting an AI generation.
 *
 * @property model The targeted AI model specification.
 * @property prompt The user prompt string to be executed.
 * @property history Prior turns in this conversation thread (for multi-turn context).
 * @property enableWebSearch Whether web search should be engaged for real-time live data.
 * @property temperature Creativity / randomness parameter (typically 0.0f - 1.0f).
 * @property conversationId Thread identifier for tracking chat sessions.
 * @property forceNewSession Whether to forcefully discard cached sessions or quotas.
 */
data class ChatRequest(
    val model: AiModel,
    val prompt: String,
    val history: List<ChatMessage> = emptyList(),
    val enableWebSearch: Boolean = false,
    val temperature: Float = 0.7f,
    val conversationId: String = UUID.randomUUID().toString(),
    val forceNewSession: Boolean = false
) {
    /**
     * Consolidates prior chat history with the current active user prompt.
     */
    fun allMessages(): List<ChatMessage> {
        val current = ChatMessage(
            role = ChatRole.USER,
            content = prompt
        )
        return history + current
    }
}

