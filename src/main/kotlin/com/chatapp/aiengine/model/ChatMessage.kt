package com.chatapp.aiengine.model

import java.util.UUID

/**
 * Encapsulates a single chat turn within a multi-turn conversation.
 *
 * @property id Unique identifier for the message item.
 * @property role Whether this message is from USER, ASSISTANT, or SYSTEM.
 * @property content The final visible text of the message.
 * @property reasoningContent The internal thinking or chain-of-thought tokens (if supported by model like DeepSeek R1 / GLM).
 * @property citations External web citations discovered when web search was enabled.
 * @property timestamp Unix epoch timestamp in milliseconds when the message was recorded.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val reasoningContent: String? = null,
    val citations: List<WebCitation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

