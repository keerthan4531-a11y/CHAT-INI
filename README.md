# PawPal 🐾 — a tiny AI agent with whiskers

PawPal is a **Kotlin + Jetpack Compose Android app** with a hand-drawn doodle UI,
a multi-model AI engine, a real agent runtime, MCP tool connections, and a
per-IP + proxy-pool rate limiter.

The mascot is **Mochi the cat** (with **Biscuit the dog** as the second mascot).
Every AI action has a mood: thinking, searching the web, using a tool, typing,
happy, sad, or sleepy. Light and dark themes are both fully doodled — not a
generic dark dashboard with a cat pasted on top.

> The UI in this repo is the real Compose app. There is no web prototype.

---

## ✨ What's inside

### Doodle-first UI
- **Mochi & Biscuit** hand-drawn with Compose `Canvas` — blinking, tail wags,
  ear twitches, breathing, typing paws, thought bubbles, magnifier, laptop,
  hearts, rain cloud and `z z z`.
- Warm paper palette, ink outlines, pastel stickers, wobbly borders, offset
  shadows, washi tape, sticky notes, handwritten margin notes.
- **Baloo 2** (display), **Nunito** (body) and **Caveat** (handwriting) bundled
  in `src/main/res/font` — works offline.
- Full **light / dark / system** theme, plus an animations toggle.
- Responsive phone layout: navigation drawer, compact top bar, bottom sheets.
- Accessible: large touch targets, `contentDescription`s, semantic buttons,
  reduced-motion switch.

### AI features
- Token-by-token **streaming** from every provider (SSE), with a typewriter UI.
- **Thinking / reasoning stream** in a collapsible card (DeepSeek R1, GLM, Grok,
  Claude, Gemini).
- **Live web search** toggle + numbered source citations.
- **Multi-model** picker: Grok 4.1, GPT-4o, GPT-4.1 mini, GPT-6 Luna, Gemini 3
  Flash/Pro, DeepSeek V3/V4, Claude Haiku/Sonnet, GLM-5.3, DeepSeek R1.
- **Markdown + code blocks** with copy buttons, lists, quotes and links.
- Copy / regenerate / feedback actions on every answer.
- **Deep Research** mode: a larger step + tool budget for long-horizon tasks.
- **Memory**: working, episodic and durable semantic facts.
- **Agent mode** with a visible plan, tool calls, run budget and grounded
  fallback answer.

### Agent runtime (Grok-style)
- **Plan-and-Execute** at the top level, **ReAct** inside each step.
- The planner writes a short reviewable plan; each step asks the model for at
  most 3 typed tool calls or a final answer.
- **Hard caps**: 8 steps / 12 tool calls / 90s by default (16 / 24 / 180s in Deep
  Research). A runaway loop can never burn a whole day of quota.
- **Typed tool contracts** with JSON Schema, risk levels and approval gates.
- **Human-in-the-loop**: write/destructive tools pause for an explicit
  Approve / Nope card.
- **Grounded fallback**: if the loop runs out of steps, a summarizer answers
  from the observations and says what was missing.
- **Citations** from web search and tools are attached to the final message.

### MCP (Model Context Protocol)
- Host + client + server architecture over **JSON-RPC 2.0**.
- Two transports: **stdio** (desktop/JVM) and **Streamable HTTP** (Android).
- Tools, resources and prompts discovery.
- Presets:
  - **GitHub** — remote (`https://api.githubcopilot.com/mcp/`) and local
    (`npx @modelcontextprotocol/server-github`).
  - **Email** — remote hosted server and local full IMAP/SMTP server.
  - **Notion**, **Slack**, **Filesystem**, plus any custom HTTP server.
- Secrets stay in memory; they are never persisted to disk.
- The agent sees only the tools of connected servers, filtered by allowlist.

### Limits without the limit problem
- **No global fixed daily cap.**
- **50 messages per IP per rolling 24h window.**
- **Every healthy proxy adds another 50-message lane.**
- Rotation strategies: least-used, round-robin, random, fastest.
- Real rotating OkHttp client with HTTP/SOCKS5 proxy support and proxy auth.
- Per-lane counters, health checks, capacity preview and a reset for the demo.
- Internal agent/brain calls do **not** consume the user's message quota; the
  user request pays for the whole run.

---

## 🏗 Architecture

```
com.chatapp
├── MainActivity.kt                  # Compose entry point
├── aiengine
│   ├── model/                       # AiModel, ChatMessage, ChatRequest, StreamEvent,
│   │                                # AgentPlan, ToolCall, McpServerConfig, ProxyEndpoint…
│   ├── core/
│   │   ├── AiEngine.kt              # public facade
│   │   ├── AiProvider.kt            # provider contract
│   │   ├── ThinkingStreamParser.kt  # <think>…</think> splitter
│   │   ├── AgentLoop.kt             # plan → act → observe → answer
│   │   ├── AgentBrain.kt            # planner / decider / summarizer
│   │   ├── AgentMemory.kt           # working + episodic + semantic memory
│   │   └── ToolRegistry.kt          # typed tools + built-in tools
│   ├── network/
│   │   ├── RateLimiter.kt           # per-IP / per-proxy 50-per-day lanes
│   │   ├── ProxyPool.kt             # rotation, health, proxy-aware OkHttp
│   │   ├── SseStreamReader.kt
│   │   └── providers/               # GaiCloud, OxAlpha, OpenAI-compatible
│   ├── mcp/
│   │   ├── McpClient.kt             # JSON-RPC 2.0, stdio + Streamable HTTP
│   │   └── McpToolExecutor.kt
│   ├── repository/                  # wires providers + limiter + pool + MCP + agent
│   ├── config/AiEngineConfig.kt
│   └── example/ChatViewModelExample.kt
└── ui
    ├── theme/                       # doodle palette, typography, theme
    ├── components/                  # DoodleCat, DoodleUi, AgentCards, MessageBubble,
    │                                # ChatInputBar, sheets…
    └── screens/ChatScreen.kt
```

The engine has **no Android imports** — it is plain Kotlin/JVM and can be reused
on a server. The UI is Compose.

---

## 🚀 Getting started

### Requirements
- Android Studio (Ladybug or newer)
- JDK 17
- Android SDK 35
- Gradle 8.9 (the wrapper properties are included; run `gradle wrapper` once if
  the wrapper JAR is missing in your checkout)

### Open & run
1. Open the repository in Android Studio.
2. Let Gradle sync (it will download AGP 8.7.3, Kotlin 2.0.21, Compose BOM
   2024.10.01).
3. Run the `app` configuration on a device/emulator (minSdk 26).

The default `MainActivity` renders `ChatScreen()` with the engine's default
configuration. No API key is required to see the UI; add a key/endpoint to get
live answers.

### Configure the engine
`AiEngine.initialize(...)` in your `Application` class:

```kotlin
AiEngine.initialize(
    AiEngineConfig(
        defaultModel = AiModel.GROK_4_1,
        isWebSearchDefaultEnabled = true,
        defaultMode = AgentMode.AGENT,
        openAiEndpoint = "https://api.deepseek.com/chat/completions",
        openAiApiKey = "sk-...",
        rateLimitPolicy = RateLimitPolicy(
            perIpDailyLimit = 50,
            perProxyDailyLimit = 50,
            windowHours = 24
        ),
        proxies = listOf(
            ProxyEndpoint("p1", "Mumbai · IN", "proxy.example.com", 8080,
                username = "user", password = "pass", region = "IN")
        ),
        mcpServers = McpPresets.defaults(),
        requireApprovalForWrites = true,
        memoryFile = "pawpal-memory.json"
    )
)
```

Then use the facade:

```kotlin
val engine = AiEngine.getInstance()

engine.streamAgent(
    ChatRequest(
        model = AiModel.GROK_4_1,
        prompt = "Research the cutest doodle UI trends and email me a summary",
        mode = AgentMode.AGENT,
        enableWebSearch = true,
        ipKey = myPublicIp,
        budget = AgentBudget(maxSteps = 16, maxToolCalls = 24)
    )
).collect { event ->
    when (event) {
        is StreamEvent.AgentPlanUpdated -> renderPlan(event.plan)
        is StreamEvent.ToolCallCompleted -> renderTool(event.call)
        is StreamEvent.ContentDelta -> append(event.text)
        is StreamEvent.UsageUpdated -> renderUsage(event.snapshot)
        is StreamEvent.AgentCompleted -> commit(event.finalMessage)
        is StreamEvent.RateLimitReached -> showLimit(event.snapshot)
        else -> {}
    }
}
```

---

## 🔌 Connecting MCP servers

Open **MCP servers** from the drawer. Each server shows a status pill, its
discovered tools, and a connect form.

| Server | Transport | Credentials | Notes |
| --- | --- | --- | --- |
| GitHub (remote) | Streamable HTTP | GitHub PAT | Works on Android. `Authorization: Bearer <PAT>` |
| GitHub (local) | stdio | GitHub PAT | Desktop/JVM only (`npx`) |
| Email (remote) | Streamable HTTP | Email MCP API key | Works on Android |
| Email (local) | stdio | Email + app password + IMAP/SMTP | Desktop/JVM only |
| Notion | Streamable HTTP | Notion token | |
| Slack | Streamable HTTP | Slack bot token | |
| Files | stdio | — | Sandboxed folder |
| Custom | Streamable HTTP | URL | Any MCP-compatible server |

**Security:** tokens are held in memory for the current process and are never
written to disk or logged. Write tools (send email, create PR, write file) pause
for approval. Destructive tools always require approval.

**Tool budget:** keep one agent under ~12 tools. If you connect many servers,
split them into a research agent and a coding agent.

---

## 🛰️ The limit policy (50 / IP / day + proxy lanes)

There is **no global fixed daily cap**. The product policy is:

```
capacity = 50 (your IP) + 50 × healthy proxies
```

- A single IP gets 50 messages per rolling 24h window.
- Every healthy proxy adds another 50-message lane.
- The pool rotates automatically (least-used by default).
- A pool of 5 healthy proxies = **300 messages/day**.
- When a lane hits 50, the next request goes to the next healthy lane.
- When every lane is empty, the UI shows a cute "no lanes left" card with the
  reset time and a shortcut to the proxy pool.

The demo pool ships with simulated lanes so the UI works offline. Add real
proxies in `AiEngineConfig.proxies` (HTTP/SOCKS5, optional username/password).
`ProxyPool.checkAllHealth()` probes each proxy through the proxy itself.

> Internal agent/brain calls are marked `internal = true` and do not consume
> the user's message quota. One user request pays for the entire agent run.

---

## 🧠 Agent design notes (from 2026 research)

- **MCP** is the tool-connection standard: hosts create clients, clients own
  1:1 JSON-RPC sessions, servers expose tools/resources/prompts. We implement
  the client side and support both stdio and Streamable HTTP.
- **ReAct** is the safe default for tool agents; **Plan-and-Execute** anchors
  long-horizon tasks. PawPal uses plan-and-execute at the top level and ReAct
  inside each step.
- **Memory** is tiered: working (run scratchpad), episodic (recent runs) and
  semantic (durable facts). Keeping them separate prevents context sprawl.
- **Budget caps** are mandatory: one query can otherwise cascade into 50–500
  model calls. PawPal caps steps, tool calls, duration and tokens.
- **Reflection** only helps with a verifiable signal. PawPal uses a grounded
  summarizer instead of asking the model to grade itself.
- **Human-in-the-loop** for writes: least privilege, idempotency and rate
  limiting are enforced at the tool layer.
- **Grok DeepSearch** style research: split the query, search in parallel,
  summarize each batch in a scratchpad, cross-check, then synthesize with
  citations. PawPal's Deep Research mode increases the step/tool budget and
  keeps the plan visible.
- **Doodle UI**: hand-drawn outlines, pastel stickers, tape, sticky notes and
  mascot motion make the agent feel like a character, not a form.

Sources: MCP architecture guides (2026), LLM agent architecture patterns (ReAct,
Plan-and-Execute, Reflexion, memory tiers), Grok DeepSearch/Think/Build feature
overviews, and kawaii doodle UI references.

---

## 🧪 Build & test

```bash
# With a Gradle wrapper present:
./gradlew assembleDebug

# If the wrapper JAR is missing in this checkout:
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

The repository is a single-module Android application (`com.android.application`)
with the engine in the same module. `src/main/res/font` contains the bundled
Baloo 2 / Nunito / Caveat TTFs.

### What is simulated
- The default proxy pool is simulated (clearly badged `demo`).
- The built-in `web_search` tool returns clearly-marked simulated results when
  no search API is configured.
- The email draft tool creates a `mailto:` draft; it does not send without a
  connected email MCP server and approval.
- The GitHub/email **remote** MCP presets require real credentials.

---

## 📜 License

MIT (the original engine code). Fonts are SIL Open Font License via the
Expo Google Fonts packages.
