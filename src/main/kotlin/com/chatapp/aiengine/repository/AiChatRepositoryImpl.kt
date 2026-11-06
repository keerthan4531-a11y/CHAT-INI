package com.chatapp.aiengine.repository

import com.chatapp.aiengine.config.AiEngineConfig
import com.chatapp.aiengine.core.AiProvider
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.network.providers.GaiCloudProvider
import com.chatapp.aiengine.network.providers.OpenAiCompatibleProvider
import com.chatapp.aiengine.network.providers.OxAlphaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Default implementation of [AiChatRepository].
 * Dynamically routes requests to the proper provider according to model specification.
 */
class AiChatRepositoryImpl(
    private val config: AiEngineConfig = AiEngineConfig(),
    customProviders: List<AiProvider> = emptyList()
) : AiChatRepository {

    private var activeModel: AiModel = config.defaultModel
    private var webSearchEnabled: Boolean = config.isWebSearchDefaultEnabled

    private val providers = mutableListOf<AiProvider>()

    init {
        // Register standard providers
        providers.add(GaiCloudProvider(baseUrl = config.gaiBaseUrl))
        providers.add(OxAlphaProvider(baseUrl = config.oxAlphaBaseUrl))
        providers.add(
            OpenAiCompatibleProvider(
                endpointUrl = config.openAiEndpoint,
                apiKey = config.openAiApiKey
            )
        )
        // Add any custom or external providers passed in
        providers.addAll(customProviders)
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> {
        val targetModel = request.model
        val provider = providers.firstOrNull { it.canHandle(targetModel) }

        if (provider == null) {
            return flow {
                emit(
                    StreamEvent.Error(
                        IllegalStateException("No provider registered capable of handling model: ${targetModel.displayName} (${targetModel.id})")
                    )
                )
            }
        }

        // Apply global web search preference if not explicitly set on request
        val finalRequest = if (request.enableWebSearch != webSearchEnabled) {
            request.copy(enableWebSearch = request.enableWebSearch || webSearchEnabled)
        } else {
            request
        }

        return provider.streamChat(finalRequest).flowOn(Dispatchers.IO)
    }

    override fun getAvailableModels(): List<AiModel> {
        return AiModel.DEFAULT_MODELS
    }

    override fun getActiveModel(): AiModel {
        return activeModel
    }

    override fun setActiveModel(model: AiModel) {
        activeModel = model
    }

    override fun isWebSearchEnabled(): Boolean {
        return webSearchEnabled
    }

    override fun setWebSearchEnabled(enabled: Boolean) {
        webSearchEnabled = enabled
    }
}

