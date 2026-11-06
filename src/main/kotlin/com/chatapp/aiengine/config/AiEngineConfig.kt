package com.chatapp.aiengine.config

import com.chatapp.aiengine.model.AiModel

/**
 * Runtime configuration for the AI Engine.
 * Allows dynamic customization of endpoints, API keys, and default flags.
 *
 * @property defaultModel The initial model selected on boot.
 * @property isWebSearchDefaultEnabled Whether web search toggle starts ON by default.
 * @property gaiBaseUrl Base URL for Gai cloud service.
 * @property oxAlphaBaseUrl Base URL for Ox Alpha service.
 * @property openAiEndpoint Base URL for OpenAI/DeepSeek compatible endpoint.
 * @property openAiApiKey Optional API key for OpenAI/DeepSeek/OpenRouter.
 */
data class AiEngineConfig(
    val defaultModel: AiModel = AiModel.GPT_4O,
    val isWebSearchDefaultEnabled: Boolean = false,
    val gaiBaseUrl: String = "https://api.chatgai.fun",
    val oxAlphaBaseUrl: String = "https://oxalpha.com",
    val openAiEndpoint: String = "https://api.deepseek.com/chat/completions",
    val openAiApiKey: String = ""
)

