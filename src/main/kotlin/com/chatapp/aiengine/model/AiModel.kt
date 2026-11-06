package com.chatapp.aiengine.model

/**
 * Categorization of which network provider service communicates with the model.
 */
enum class ProviderType {
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
    OPENAI_COMPATIBLE
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

        /**
         * Full default registry of supported models.
         */
        val DEFAULT_MODELS = listOf(
            GPT_4O,
            GPT_4_1_MINI,
            GPT_6_LUNA,
            GEMINI_3_FLASH,
            GEMINI_3_5_LITE,
            DEEPSEEK_V3,
            DEEPSEEK_V4_FLASH,
            CLAUDE_HAIKU,
            CLAUDE_SONNET_4_5,
            GROK_4,
            GROK_4_1,
            GEMINI_3_PRO,
            GLM_5_3_FLASH,
            DEEPSEEK_R1_OFFICIAL
        )

        fun findById(id: String): AiModel? {
            return DEFAULT_MODELS.find { it.id.equals(id, ignoreCase = true) }
        }
    }
}

