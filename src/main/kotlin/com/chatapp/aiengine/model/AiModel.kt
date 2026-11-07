package com.chatapp.aiengine.model

/**
 * Categorization of which network provider service communicates with the model.
 */
enum class ProviderType {
    /**
     * ChatGPT Free endpoint (https://chatgpt-proxy-chi-five.vercel.app/v1/chat/completions).
     */
    CHATGPT_FREE,

    /**
     * OneSearch Begamob 2API gateway with HS256 JWT auth (Claude 3.7, DeepSeek R1, GPT-5, o3, etc.).
     */
    ONESEARCH,

    /**
     * Zyra Google Cloud Functions failover endpoint (Qwen 3.5 Heavy Context).
     */
    ZYRA,

    /**
     * Direct cloud endpoint with native AES encryption (GPT, Gemini, DeepSeek, Grok, Claude Haiku).
     */
    GAI_CLOUD,

    /**
     * Ox Alpha platform supporting GLM-5.3-Flash with real-time thinking tokens.
     */
    OX_ALPHA,

    /**
     * Standard OpenAI/DeepSeek/OpenRouter compatible SSE endpoint.
     */
    OPENAI_COMPATIBLE,

    /**
     * Standalone AI Video Generation service (Sora / Aritek T2V v1.9.3).
     */
    ARITEK_T2V
}

/**
 * Metadata definition of an AI Model available within the engine.
 *
 * @property id The backend identifier string sent over the wire.
 * @property displayName Human-readable label displayed in UI picker.
 * @property providerType The routing provider used to connect to this model.
 * @property supportsReasoning Whether the model streams reasoning/thinking tokens before the final content.
 * @property supportsWebSearch Whether web search can be enabled for this model.
 * @property supportsTools Whether the model can emit typed tool calls (MCP).
 * @property contextWindow Approximate context window in tokens.
 * @property description Brief summary of model strengths or context window.
 */
data class AiModel(
    val id: String,
    val displayName: String,
    val providerType: ProviderType,
    val supportsReasoning: Boolean = false,
    val supportsWebSearch: Boolean = true,
    val supportsTools: Boolean = true,
    val contextWindow: Int = 128_000,
    val description: String = ""
) {
    companion object {
        // --- ChatGPT Free Models (Default) ---
        val GPT_6 = AiModel(
            id = "gpt6",
            displayName = "GPT-6",
            providerType = ProviderType.CHATGPT_FREE,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Flagship GPT-6 via high-speed ChatGPT Free proxy"
        )

        // --- Gai Cloud Models (Direct Cloud, No Laptop Needed) ---
        val GPT_4O = AiModel(
            id = "GPT:4o",
            displayName = "GPT-4o",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Flagship multimodal intelligence from OpenAI"
        )

        val GPT_4_1_MINI = AiModel(
            id = "GPT:4.1-mini",
            displayName = "GPT-4.1 Mini",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Fast, efficient general purpose model"
        )

        val GPT_6_LUNA = AiModel(
            id = "GPT:gpt-6-luna",
            displayName = "GPT-6 Luna",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Advanced reasoning experimental model"
        )

        val GEMINI_3_FLASH = AiModel(
            id = "GEMINI:gemini-3-flash-preview",
            displayName = "Gemini 3 Flash",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "High-speed Google multimodal model"
        )

        val GEMINI_3_5_LITE = AiModel(
            id = "GEMINI:gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash Lite",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Ultra-lightweight fast responder"
        )

        val DEEPSEEK_V3 = AiModel(
            id = "DEEPSEEK:v3",
            displayName = "DeepSeek V3",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Top-tier open architecture code & reasoning"
        )

        val DEEPSEEK_V4_FLASH = AiModel(
            id = "DEEPSEEK_OFFICIAL:deepseek-v4-flash",
            displayName = "DeepSeek V4 Flash",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Next-gen DeepSeek with integrated reasoning"
        )

        val CLAUDE_HAIKU = AiModel(
            id = "BOLATU:claude-haiku-5-5",
            displayName = "Claude 5.5 Haiku",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Rapid, nuanced language generation"
        )

        val GROK_4 = AiModel(
            id = "XAI:grok-4",
            displayName = "Grok 4",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            contextWindow = 256_000,
            description = "Direct, witty intelligence with live capabilities"
        )

        val GROK_4_1 = AiModel(
            id = "XAI:grok-4.1",
            displayName = "Grok 4.1",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            contextWindow = 256_000,
            description = "Agentic Grok with stronger tool calling + DeepSearch"
        )

        val CLAUDE_SONNET_4_5 = AiModel(
            id = "BOLATU:claude-sonnet-4-5",
            displayName = "Claude Sonnet 4.5",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            contextWindow = 200_000,
            description = "Careful long-form writing and computer use"
        )

        val GEMINI_3_PRO = AiModel(
            id = "GEMINI:gemini-3-pro",
            displayName = "Gemini 3 Pro",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            contextWindow = 1_000_000,
            description = "Huge context multimodal reasoning"
        )

        val GPT_5_6_LUNA = AiModel(
            id = "GPT:gpt-5.6-luna",
            displayName = "GPT-5.6 Luna",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "High intelligence experimental model"
        )

        val GEMINI_2_5_FLASH = AiModel(
            id = "GEMINI:v2.5-flash",
            displayName = "Gemini 2.5 Flash",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Google fast reasoning & multimodal"
        )

        val DEEPSEEK_V3_0324 = AiModel(
            id = "DEEPSEEK:v3-0324",
            displayName = "DeepSeek V3 (0324)",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "DeepSeek V3 optimized build"
        )

        val GROK_4_REASONING = AiModel(
            id = "XAI:grok-4-fast-reasoning",
            displayName = "Grok 4 (Reasoning)",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Grok 4 with deep thinking process"
        )

        val GROK_3 = AiModel(
            id = "BOLATU:grok-3",
            displayName = "Grok 3",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "xAI Grok 3 flagship foundation model"
        )

        val CLAUDE_HAIKU_4_5 = AiModel(
            id = "BOLATU:claude-haiku-4-5-20251001",
            displayName = "Claude 4.5 Haiku",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Lightning-fast Anthropic intelligence"
        )

        val GEMMA_4_31B = AiModel(
            id = "OPEN_ROUTER:google/gemma-4-31b-it",
            displayName = "Google Gemma 31B",
            providerType = ProviderType.GAI_CLOUD,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Google open weights 31B instruction model"
        )

        // --- Ox Alpha Models ---
        val GLM_5_3_FLASH = AiModel(
            id = "z-ai/glm-5.3-flash",
            displayName = "Ox Alpha (GLM-5.3-Flash)",
            providerType = ProviderType.OX_ALPHA,
            supportsReasoning = true,
            supportsWebSearch = false,
            description = "Advanced reasoning model with real-time thinking stream"
        )

        // --- Standard OpenAI / DeepSeek R1 Compatible ---
        val DEEPSEEK_R1_OFFICIAL = AiModel(
            id = "deepseek-reasoner",
            displayName = "DeepSeek R1 (Reasoner)",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Pure reinforcement learning reasoning model"
        )

        // ==========================================
        // ONESEARCH FRONTIER MODELS (68 Upstream Gateway)
        // ==========================================
        // --- Anthropic Claude (OneSearch) ---
        val CLAUDE_3_7_SONNET = AiModel(
            id = "claude-3-7-sonnet",
            displayName = "Claude 3.7 Sonnet",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Anthropic flagship hybrid reasoning & coding model"
        )

        val CLAUDE_OPUS_4_6 = AiModel(
            id = "claude-opus-4.6",
            displayName = "Claude Opus 4.6",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Deep capability frontier intelligence from Anthropic"
        )

        val CLAUDE_3_5_SONNET = AiModel(
            id = "claude-3-5-sonnet",
            displayName = "Claude 3.5 Sonnet",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Top-tier code generation and complex visual comprehension"
        )

        val CLAUDE_SONNET_4 = AiModel(
            id = "claude-sonnet-4",
            displayName = "Claude Sonnet 4",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Next-generation balanced reasoning model"
        )

        val CLAUDE_OPUS_4 = AiModel(
            id = "claude-opus-4",
            displayName = "Claude Opus 4",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Heavy context analytical reasoning"
        )

        val CLAUDE_3_5_HAIKU = AiModel(
            id = "claude-3-5-haiku",
            displayName = "Claude 3.5 Haiku",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Lightning fast responses with high intelligence"
        )

        // --- DeepSeek Reasoning & Chat (OneSearch) ---
        val DEEPSEEK_R1_ONESEARCH = AiModel(
            id = "deepseek-r1",
            displayName = "DeepSeek R1",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Open reasoning model with complete thinking steps"
        )

        val DEEPSEEK_V3_ONESEARCH = AiModel(
            id = "deepseek-v3",
            displayName = "DeepSeek V3 (OneSearch)",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "High-speed DeepSeek V3 open architecture"
        )

        // --- OpenAI o-Series & Next-Gen (OneSearch) ---
        val OPENAI_O3 = AiModel(
            id = "o3",
            displayName = "OpenAI o3",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "OpenAI frontier reasoning model for ultra-hard tasks"
        )

        val OPENAI_O3_MINI = AiModel(
            id = "o3-mini",
            displayName = "OpenAI o3-mini",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Fast, efficient STEM reasoning model"
        )

        val OPENAI_O1 = AiModel(
            id = "o1",
            displayName = "OpenAI o1",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "OpenAI deliberate chain-of-thought model"
        )

        val OPENAI_O4_MINI = AiModel(
            id = "o4-mini",
            displayName = "OpenAI o4-mini",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "High-velocity compact reasoning model"
        )

        val GPT_5 = AiModel(
            id = "gpt-5",
            displayName = "GPT-5",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "OpenAI next-generation flagship foundation model"
        )

        val GPT_5_1 = AiModel(
            id = "gpt-5.1",
            displayName = "GPT-5.1",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Refined GPT-5 with precision instruction following"
        )

        val GPT_5_MINI = AiModel(
            id = "gpt-5-mini",
            displayName = "GPT-5 Mini",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Compact, low-latency GPT-5 variant"
        )

        val GPT_4_1_OS = AiModel(
            id = "gpt-4.1",
            displayName = "GPT-4.1",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Upgraded GPT-4 general intelligence"
        )

        // --- Meta & Open Source (OneSearch) ---
        val LLAMA_4_MAVERICK = AiModel(
            id = "llama-4-maverick",
            displayName = "Llama 4 Maverick",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Meta next-generation foundation open model"
        )

        val LLAMA_4_SCOUT = AiModel(
            id = "llama-4-scout",
            displayName = "Llama 4 Scout",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Fast, efficient Llama 4 instruction tuned model"
        )

        val LLAMA_3_3_70B = AiModel(
            id = "llama-3.3-70b",
            displayName = "Llama 3.3 70B",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Meta flagship 70B open weight foundation model"
        )

        val QWEN_MAX = AiModel(
            id = "qwen-max",
            displayName = "Qwen Max",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Alibaba flagship frontier multilingual model"
        )

        val MISTRAL_LARGE = AiModel(
            id = "mistral-large",
            displayName = "Mistral Large",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Mistral top-tier reasoning and code intelligence"
        )

        val KIMI = AiModel(
            id = "kimi",
            displayName = "Moonshot Kimi",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Moonshot AI long-context specialist model"
        )

        // --- Google Gemini (OneSearch) ---
        val GEMINI_2_5_PRO = AiModel(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = true,
            supportsWebSearch = true,
            description = "Google advanced multimodal reasoning model"
        )

        val GEMINI_2_0_FLASH_OS = AiModel(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            providerType = ProviderType.ONESEARCH,
            supportsReasoning = false,
            supportsWebSearch = true,
            description = "Google real-time streaming multimodal model"
        )

        // ==========================================
        // ZYRA ZERO-LIMIT FAILOVER MODEL
        // ==========================================
        val ZYRA_QWEN_3_5 = AiModel(
            id = "zyra-qwen-3.5",
            displayName = "Qwen 3.5 (Unlimited Failover)",
            providerType = ProviderType.ZYRA,
            supportsReasoning = false,
            supportsWebSearch = false,
            contextWindow = 128_000,
            description = "High-speed zero-rate-limit failover via Zyra Cloud Functions"
        )

        // ==========================================
        // ARITEK SORA AI VIDEO GENERATOR
        // ==========================================
        val SORA_T2V = AiModel(
            id = "sora-t2v",
            displayName = "Sora AI Video (T2V)",
            providerType = ProviderType.ARITEK_T2V,
            supportsReasoning = false,
            supportsWebSearch = false,
            description = "Generates high quality real MP4 videos from text descriptions"
        )

        /**
         * Full default registry of supported models.
         */
        val DEFAULT_MODELS = listOf(
            GPT_6,
            SORA_T2V,
            CLAUDE_3_7_SONNET,
            DEEPSEEK_R1_ONESEARCH,
            CLAUDE_OPUS_4_6,
            CLAUDE_3_5_SONNET,
            OPENAI_O3,
            OPENAI_O3_MINI,
            OPENAI_O1,
            OPENAI_O4_MINI,
            GPT_5,
            GPT_5_1,
            GPT_5_MINI,
            GPT_4_1_OS,
            CLAUDE_SONNET_4,
            CLAUDE_OPUS_4,
            CLAUDE_3_5_HAIKU,
            DEEPSEEK_V3_ONESEARCH,
            GEMINI_2_5_PRO,
            GEMINI_2_0_FLASH_OS,
            LLAMA_4_MAVERICK,
            LLAMA_4_SCOUT,
            LLAMA_3_3_70B,
            QWEN_MAX,
            MISTRAL_LARGE,
            KIMI,
            ZYRA_QWEN_3_5,
            GLM_5_3_FLASH,
            GPT_4O,
            DEEPSEEK_V3,
            GEMINI_3_FLASH,
            GROK_4,
            GROK_4_REASONING,
            CLAUDE_HAIKU,
            CLAUDE_HAIKU_4_5,
            GPT_4_1_MINI,
            GPT_6_LUNA,
            GPT_5_6_LUNA,
            GEMINI_3_5_LITE,
            GEMINI_2_5_FLASH,
            DEEPSEEK_V4_FLASH,
            DEEPSEEK_V3_0324,
            GROK_3,
            GEMMA_4_31B,
            CLAUDE_SONNET_4_5,
            GROK_4_1,
            GEMINI_3_PRO,
            DEEPSEEK_R1_OFFICIAL
        )

        fun findById(id: String): AiModel? {
            return DEFAULT_MODELS.find { it.id.equals(id, ignoreCase = true) }
        }
    }
}

