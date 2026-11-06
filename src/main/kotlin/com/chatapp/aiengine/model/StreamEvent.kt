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

    /**
     * Fired when a network error, timeout, or parsing failure occurs.
     */
    data class Error(
        val throwable: Throwable,
        val partialContent: String = "",
        val partialThinking: String = ""
    ) : StreamEvent
}

