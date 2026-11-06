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
 * @property mode CHAT for a single answer, AGENT for a plan + tool loop.
 * @property budget Hard caps for the agent loop.
 * @property mcpServerIds MCP servers the agent is allowed to use this turn.
 * @property ipKey Lane key used by the rate limiter (public IP when available).
 * @property proxy The proxy lane selected for this request (internal).
 * @property autoApproveReadOnlyTools Read-only tools run without asking.
 * @property requireApprovalForWrites Write tools pause for user approval.
 * @property internal Internal agent/brain call. Does not consume the user's
 *   daily message quota (the user request already paid for the whole run).
 */
data class ChatRequest(
    val model: AiModel,
    val prompt: String,
    val history: List<ChatMessage> = emptyList(),
    val enableWebSearch: Boolean = false,
    val temperature: Float = 0.7f,
    val conversationId: String = UUID.randomUUID().toString(),
    val forceNewSession: Boolean = false,
    val mode: AgentMode = AgentMode.CHAT,
    val budget: AgentBudget = AgentBudget(),
    val mcpServerIds: Set<String> = emptySet(),
    val ipKey: String = "local-device",
    val proxy: ProxyEndpoint? = null,
    val autoApproveReadOnlyTools: Boolean = true,
    val requireApprovalForWrites: Boolean = true,
    val internal: Boolean = false
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

