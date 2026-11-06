package com.chatapp.aiengine.model

/**
 * Represents the author/role of a chat message in the conversation.
 */
enum class ChatRole {
    /**
     * End-user prompt or message.
     */
    USER,

    /**
     * AI model generated response.
     */
    ASSISTANT,

    /**
     * System instructions or developer context guiding model behavior.
     */
    SYSTEM
}

