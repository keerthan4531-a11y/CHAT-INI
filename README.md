# Multi-Model AI Chat Engine (Kotlin)

A lightweight, high-performance, standalone AI Engine written in 100% Kotlin for Android and JVM applications.

---

## 🌟 Key Architecture Capabilities

1. **Zero-Wait Real-Time Streaming (ChatGPT Style)**:
   - Does not wait for the entire response to generate on the server.
   - Emits tokens immediately over HTTP/2 Server-Sent Events (SSE) via Kotlin Coroutines `Flow<StreamEvent>`.
   - Renders letter-by-letter / word-by-word with ultra-low latency.

2. **Native Thinking / Reasoning Process Streaming**:
   - Handles advanced reasoning models (DeepSeek-R1, Ox Alpha GLM, OpenAI o1/o3).
   - Segregates thoughts from answer content in real-time using `ThinkingStreamParser`.
   - Emits `StreamEvent.ThinkingDelta` first (for expandable "Thinking..." UI cards), followed by `StreamEvent.ThinkingCompleted` and `StreamEvent.ContentDelta`.

3. **Live Web Search Toggle**:
   - Allows enabling web search (`enableWebSearch = true`) per request or globally.
   - Streams `StreamEvent.WebSearchStarted` and `StreamEvent.WebSearchCompleted` with parsed citations (`WebCitation`).

4. **Multi-Model Pluggable Architecture**:
   - Easily switch between models (GPT-4o, Gemini 3 Flash, DeepSeek V3/V4, Claude Haiku, Grok 4, GLM 5.3 Flash, DeepSeek R1).
   - Unified domain API: UI only talks to `AiEngine` or `AiChatRepository`.

---

## 📂 Project Structure

```
src/main/kotlin/com/chatapp/aiengine/
├── model/
│   ├── AiModel.kt              # Model metadata, capabilities & registry
│   ├── ChatMessage.kt          # Conversation messages (role, content, reasoning)
│   ├── ChatRequest.kt          # Prompt, history, web search toggle, temperature
│   ├── ChatRole.kt             # USER, ASSISTANT, SYSTEM
│   ├── StreamEvent.kt          # Sealed events (ThinkingDelta, ContentDelta, WebSearch, etc.)
│   └── WebCitation.kt          # Citations & sources from search
├── core/
│   ├── AiEngine.kt             # Central singleton & facade
│   ├── AiProvider.kt           # Common contract for all AI network providers
│   └── ThinkingStreamParser.kt # Real-time tokenizer splitting <think> and reasoning tokens
├── network/
│   ├── HttpClientProvider.kt   # Tuned OkHttpClient with streaming timeouts
│   ├── SseStreamReader.kt      # Low-latency line-by-line SSE reader
│   └── providers/
│       ├── GaiCloudProvider.kt         # Direct cloud endpoint with AES encryption
│       ├── OxAlphaProvider.kt          # Ox Alpha GLM with CSRF & reasoning tokens
│       └── OpenAiCompatibleProvider.kt # Standard OpenAI / DeepSeek R1 / OpenRouter
├── repository/
│   ├── AiChatRepository.kt     # Repository interface for ViewModel
│   └── AiChatRepositoryImpl.kt # Dynamic routing to the appropriate provider
├── config/
│   └── AiEngineConfig.kt       # Dynamic URLs, API keys & defaults
└── example/
    └── ChatViewModelExample.kt # Full ViewModel demonstration
```

---

## 🚀 Quick Usage in Android / Kotlin

```kotlin
// 1. Get Engine Instance
val engine = AiEngine.getInstance()

// 2. Stream a response token-by-token
lifecycleScope.launch {
    val request = ChatRequest(
        model = AiModel.GLM_5_3_FLASH, // or AiModel.GPT_4O, AiModel.DEEPSEEK_V4_FLASH
        prompt = "Explain quantum computing step by step",
        enableWebSearch = true
    )

    engine.streamChat(request).collect { event ->
        when (event) {
            is StreamEvent.ThinkingDelta -> {
                // Live reasoning thoughts
                print("[Thinking] ${event.text}")
            }
            is StreamEvent.ThinkingCompleted -> {
                println("\n--- Thinking Finished in ${event.durationMs}ms ---\n")
            }
            is StreamEvent.ContentDelta -> {
                // Immediate response streaming (typing animation)
                print(event.text)
            }
            is StreamEvent.WebSearchCompleted -> {
                println("Sources found: ${event.citations.size}")
            }
            is StreamEvent.Completed -> {
                println("\nGeneration Finished!")
            }
            is StreamEvent.Error -> {
                System.err.println("Error: ${event.throwable.message}")
            }
            else -> {}
        }
    }
}
```

