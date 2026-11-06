package com.chatapp.aiengine.mcp

import com.chatapp.aiengine.core.ToolExecutor
import com.chatapp.aiengine.model.ToolCall

/** Bridges the agent's tool executor to live MCP servers. */
class McpToolExecutor(
    private val manager: McpManager
) : ToolExecutor {
    override suspend fun execute(call: ToolCall): ToolCallResult {
        return manager.execute(call)
    }
}
