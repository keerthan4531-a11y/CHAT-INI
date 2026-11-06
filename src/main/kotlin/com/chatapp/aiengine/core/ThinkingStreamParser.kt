package com.chatapp.aiengine.core

import com.chatapp.aiengine.model.StreamEvent

/**
 * Stateful stream parser that seamlessly segregates "Thinking" reasoning tokens from
 * the main "Content" response tokens in real-time.
 *
 * Supported thinking styles:
 * 1. Explicit thinking tags: `<think> ... </think>` (DeepSeek-R1, Qwen 2.5 Coder, Ollama).
 * 2. Dedicated API tokens: Direct reasoning field deltas from models like Ox Alpha GLM or DeepSeek Reasoner.
 *
 * It prevents tag leakage into UI by maintaining a lookahead window for tag boundaries.
 */
class ThinkingStreamParser(
    private val onEvent: (StreamEvent) -> Unit
) {
    private enum class State {
        SEARCHING_FOR_TAG,
        INSIDE_THINK_TAG,
        IN_CONTENT
    }

    private var state = State.SEARCHING_FOR_TAG
    private val buffer = StringBuilder()
    private val fullThinking = StringBuilder()
    private val fullContent = StringBuilder()

    private var thinkingStartTime: Long = 0
    private var hasStartedThinking = false

    companion object {
        private const val THINK_START = "<think>"
        private const val THINK_END = "</think>"
    }

    /**
     * Feeds an explicit reasoning token (e.g. from a dedicated JSON field).
     */
    fun feedReasoningToken(token: String) {
        if (token.isEmpty()) return

        if (!hasStartedThinking) {
            hasStartedThinking = true
            thinkingStartTime = System.currentTimeMillis()
            onEvent(StreamEvent.ThinkingStarted)
        }

        fullThinking.append(token)
        onEvent(StreamEvent.ThinkingDelta(token))
    }

    /**
     * Marks explicit reasoning as completed and prepares parser for content tokens.
     */
    fun finishReasoning() {
        if (hasStartedThinking) {
            val duration = System.currentTimeMillis() - thinkingStartTime
            onEvent(StreamEvent.ThinkingCompleted(fullThinking.toString(), duration))
            hasStartedThinking = false
            state = State.IN_CONTENT
        }
    }

    /**
     * Feeds regular text stream chunks.
     * Automatically scans for `<think>` and `</think>` tags without letting them bleed into the output.
     */
    fun feedContentChunk(chunk: String) {
        if (chunk.isEmpty()) return

        // If we are already purely in content mode, stream directly
        if (state == State.IN_CONTENT) {
            fullContent.append(chunk)
            onEvent(StreamEvent.ContentDelta(chunk))
            return
        }

        buffer.append(chunk)

        while (buffer.isNotEmpty()) {
            when (state) {
                State.SEARCHING_FOR_TAG -> {
                    val currentText = buffer.toString()
                    val trimmed = currentText.trimStart()

                    if (trimmed.startsWith(THINK_START)) {
                        // Found opening tag
                        if (!hasStartedThinking) {
                            hasStartedThinking = true
                            thinkingStartTime = System.currentTimeMillis()
                            onEvent(StreamEvent.ThinkingStarted)
                        }
                        val index = currentText.indexOf(THINK_START)
                        buffer.delete(0, index + THINK_START.length)
                        state = State.INSIDE_THINK_TAG
                    } else if (THINK_START.startsWith(trimmed)) {
                        // Incomplete start tag (e.g. "<thi"), wait for more chunks
                        break
                    } else {
                        // No thinking tag present, transition straight to regular content
                        state = State.IN_CONTENT
                        val text = buffer.toString()
                        buffer.clear()
                        fullContent.append(text)
                        onEvent(StreamEvent.ContentDelta(text))
                    }
                }

                State.INSIDE_THINK_TAG -> {
                    val currentText = buffer.toString()
                    val endIndex = currentText.indexOf(THINK_END)

                    if (endIndex != -1) {
                        // Thinking finished via closing tag
                        val thinkingText = currentText.substring(0, endIndex)
                        if (thinkingText.isNotEmpty()) {
                            fullThinking.append(thinkingText)
                            onEvent(StreamEvent.ThinkingDelta(thinkingText))
                        }

                        val duration = System.currentTimeMillis() - thinkingStartTime
                        onEvent(StreamEvent.ThinkingCompleted(fullThinking.toString(), duration))

                        // Consume up to and including </think>
                        buffer.delete(0, endIndex + THINK_END.length)
                        // Trim leading newline if present right after </think>
                        if (buffer.startsWith("\n\n")) {
                            buffer.delete(0, 2)
                        } else if (buffer.startsWith("\n")) {
                            buffer.delete(0, 1)
                        }
                        state = State.IN_CONTENT
                    } else {
                        // Check if an incomplete closing tag is building up at the end (e.g. "</thi")
                        val potentialTagStart = currentText.lastIndexOf('<')
                        if (potentialTagStart != -1 && THINK_END.startsWith(currentText.substring(potentialTagStart))) {
                            // Emit everything up to the potential tag start
                            val readyText = currentText.substring(0, potentialTagStart)
                            if (readyText.isNotEmpty()) {
                                fullThinking.append(readyText)
                                onEvent(StreamEvent.ThinkingDelta(readyText))
                                buffer.delete(0, potentialTagStart)
                            }
                            break
                        } else {
                            // All buffered characters are safely part of thinking
                            fullThinking.append(currentText)
                            onEvent(StreamEvent.ThinkingDelta(currentText))
                            buffer.clear()
                        }
                    }
                }

                State.IN_CONTENT -> {
                    val text = buffer.toString()
                    buffer.clear()
                    fullContent.append(text)
                    onEvent(StreamEvent.ContentDelta(text))
                }
            }
        }
    }

    /**
     * Flushes any remaining bytes in the buffer when stream finishes.
     */
    fun flush() {
        if (hasStartedThinking && state == State.INSIDE_THINK_TAG) {
            val remaining = buffer.toString()
            if (remaining.isNotEmpty()) {
                fullThinking.append(remaining)
                onEvent(StreamEvent.ThinkingDelta(remaining))
            }
            val duration = System.currentTimeMillis() - thinkingStartTime
            onEvent(StreamEvent.ThinkingCompleted(fullThinking.toString(), duration))
            buffer.clear()
        } else if (buffer.isNotEmpty()) {
            val remaining = buffer.toString()
            fullContent.append(remaining)
            onEvent(StreamEvent.ContentDelta(remaining))
            buffer.clear()
        }
    }

    fun getFullThinking(): String = fullThinking.toString()
    fun getFullContent(): String = fullContent.toString()
}

