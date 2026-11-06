package com.chatapp.aiengine.config

import com.chatapp.aiengine.model.AgentBudget
import com.chatapp.aiengine.model.AgentMode
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.McpServerConfig
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.RateLimitPolicy
import com.chatapp.aiengine.mcp.McpPresets
import com.chatapp.aiengine.network.ProxyPool

/**
 * Runtime configuration for the AI Engine.
 *
 * @property defaultModel The initial model selected on boot.
 * @property isWebSearchDefaultEnabled Whether web search toggle starts ON by default.
 * @property defaultMode CHAT for a single answer, AGENT for the plan+tool loop.
 * @property agentBudget Hard caps for one agent run.
 * @property rateLimitPolicy Per-IP / per-proxy message budget.
 * @property proxies Initial proxy pool. Each healthy proxy adds a 50 msg/day lane.
 * @property mcpServers MCP server presets. Secrets are injected at runtime.
 * @property requireApprovalForWrites Pause before send / write / delete tools.
 * @property memoryFile Optional JSON file for durable agent memory.
 */
data class AiEngineConfig(
    val defaultModel: AiModel = AiModel.GROK_4_1,
    val isWebSearchDefaultEnabled: Boolean = true,
    val defaultMode: AgentMode = AgentMode.AGENT,
    val agentBudget: AgentBudget = AgentBudget(),
    val rateLimitPolicy: RateLimitPolicy = RateLimitPolicy(),
    val proxies: List<ProxyEndpoint> = ProxyPool.demoPool(),
    val mcpServers: List<McpServerConfig> = McpPresets.defaults(),
    val mcpEnabled: Boolean = true,
    val requireApprovalForWrites: Boolean = true,
    val autoApproveReadOnlyTools: Boolean = true,
    val memoryFile: String? = null,
    val gaiBaseUrl: String = "https://api.chatgai.fun",
    val oxAlphaBaseUrl: String = "https://oxalpha.com",
    val openAiEndpoint: String = "https://api.deepseek.com/chat/completions",
    val openAiApiKey: String = ""
)
