package com.chatapp.aiengine.model

/**
 * Sealed hierarchy of real-time stream events emitted during an AI generation cycle.
 *
 * Designed for immediate reactive rendering in Jetpack Compose, XML DataBinding,
 * or standard Kotlin Coroutines Flow collectors without blocking.
 */
sealed interface StreamEvent {

    /**
     * Fired as soon as the HTTP connection establishes and generation commences.
     */
    data class Started(
        val model: AiModel,
        val conversationId: String
    ) : StreamEvent

    /**
     * Fired when the model starts outputting internal reasoning / chain-of-thought tokens.
     */
    object ThinkingStarted : StreamEvent

    /**
     * Emitted token-by-token as the model thinks through the query.
     * Use this in the UI to stream thoughts live into an expandable "Thinking..." card.
     */
    data class ThinkingDelta(
        val text: String
    ) : StreamEvent

    /**
     * Fired when thinking finishes, before the main answer tokens start streaming.
     */
    data class ThinkingCompleted(
        val fullThinking: String,
        val durationMs: Long
    ) : StreamEvent

    /**
     * Fired when the engine initiates a live web search for relevant web sources.
     */
    data class WebSearchStarted(
        val query: String
    ) : StreamEvent

    /**
     * Fired when web search results and source citations have been retrieved.
     */
    data class WebSearchCompleted(
        val citations: List<WebCitation>
    ) : StreamEvent

    /**
     * Emitted token-by-token (letter-by-letter / word-by-word) for the primary response text.
     * This achieves the smooth ChatGPT-like typing animation with zero initial wait.
     */
    data class ContentDelta(
        val text: String
    ) : StreamEvent

    /**
     * Fired when generation has cleanly terminated with complete data.
     */
    data class Completed(
        val finalMessage: ChatMessage
    ) : StreamEvent

    // ---------------------------------------------------------------------
    // Agent / tool events
    // ---------------------------------------------------------------------

    /** The agent finished decomposing the goal into a reviewable plan. */
    data class AgentPlanUpdated(val plan: AgentPlan) : StreamEvent

    /** A single plan step started running. */
    data class AgentStepStarted(val step: AgentStep) : StreamEvent

    /** A single plan step finished (done / failed / skipped). */
    data class AgentStepCompleted(val step: AgentStep) : StreamEvent

    /** The agent wants to invoke a tool. */
    data class ToolCallStarted(val call: ToolCall) : StreamEvent

    /** A tool returned (or failed). */
    data class ToolCallCompleted(val call: ToolCall) : StreamEvent

    /** A write/destructive tool needs the human to approve it first. */
    data class ToolApprovalRequested(val call: ToolCall) : StreamEvent

    /** A new source was discovered by web search or a tool. */
    data class CitationAdded(val citation: WebCitation) : StreamEvent

    /** The agent learned something worth remembering. */
    data class MemoryUpdated(val fact: MemoryFact) : StreamEvent

    /** A proxy lane was selected for this request. */
    data class ProxyRotated(val proxy: ProxyEndpoint?) : StreamEvent

    /** Live quota snapshot for the UI meter. */
    data class UsageUpdated(val snapshot: UsageSnapshot) : StreamEvent

    /** The per-IP / proxy-pool budget is exhausted. */
    data class RateLimitReached(
        val snapshot: UsageSnapshot,
        val retryAt: Long
    ) : StreamEvent

    /** Agent finished with its plan + usage attached. */
    data class AgentCompleted(
        val finalMessage: ChatMessage,
        val plan: AgentPlan,
        val usage: UsageSnapshot
    ) : StreamEvent

    /**
     * Fired when a network error, timeout, or parsing failure occurs.
     */
    data class Error(
        val throwable: Throwable,
        val partialContent: String = "",
        val partialThinking: String = ""
    ) : StreamEvent
}

