package com.chatapp.aiengine.core

import com.chatapp.aiengine.model.AgentBudget
import com.chatapp.aiengine.model.AgentDecision
import com.chatapp.aiengine.model.AgentPlan
import com.chatapp.aiengine.model.AgentStep
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.ToolDefinition
import com.chatapp.aiengine.repository.AiChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

data class BrainInput(
    val goal: String,
    val plan: AgentPlan,
    val observations: List<String>,
    val tools: List<ToolDefinition>,
    val history: List<ChatMessage>,
    val model: AiModel
)

/**
 * The "brain" of the agent. It is deliberately small: plan, decide, summarize.
 * Swapping the model or provider does not touch the loop, tools or UI.
 */
interface AgentBrain {
    suspend fun plan(goal: String, tools: List<ToolDefinition>, budget: AgentBudget): AgentPlan
    suspend fun decide(input: BrainInput): AgentDecision
    fun summarize(input: BrainInput): Flow<String>
}

/**
 * Default brain backed by the existing multi-provider streaming repository.
 *
 * The model is asked for a small JSON object per step. That keeps the loop
 * provider-agnostic (Grok, GPT, Gemini, DeepSeek, GLM...) and makes tool
 * arguments type-checkable before execution.
 */
class LlmAgentBrain(
    private val repository: AiChatRepository,
    private val model: AiModel,
    private val proxy: ProxyEndpoint? = null,
    private val ipKey: String = "local-device"
) : AgentBrain {

    override suspend fun plan(goal: String, tools: List<ToolDefinition>, budget: AgentBudget): AgentPlan {
        val system = """
            You are the planner inside PawPal, a friendly Android agent.
            Break the user's goal into at most ${budget.maxSteps} concrete steps.
            Each step must be doable with the available tools or a single model call.
            Return ONLY minified JSON:
            {"steps":[{"title":"short imperative title","detail":"one sentence on how"}]}
            No markdown, no commentary.
        """.trimIndent()
        val user = """
            Goal: $goal
            Available tools: ${tools.joinToString(", ") { "${it.name} (${it.description.take(90)})" }.ifEmpty { "none" }}
            Plan JSON:
        """.trimIndent()
        val content = runPrompt(system, user, emptyList())
        val steps = parseSteps(content)
        val normalized = steps.mapIndexed { index, step ->
            AgentStep(
                title = step.title.ifBlank { "Step ${index + 1}" },
                detail = step.detail
            )
        }
        return AgentPlan(
            goal = goal,
            steps = normalized.ifEmpty { defaultSteps(goal) },
            maxSteps = budget.maxSteps
        )
    }

    override suspend fun decide(input: BrainInput): AgentDecision {
        val system = """
            You are the decision engine of an Android agent.
            Given the goal, the plan and the observations so far, choose the next action.
            Return ONLY minified JSON with this shape:
            {"reasoning":"one or two sentences","tool_calls":[{"tool":"server__tool","arguments":{"key":"value"}}],"final_answer":"markdown answer or empty string"}
            Rules:
            - Use at most 3 tool calls per step.
            - Prefer read-only tools. Never invent tool names.
            - When you have enough evidence, put the final answer in final_answer and use no tool calls.
            - Cite observations like [1], [2] when useful.
            - If a tool failed, try a different approach instead of repeating it.
        """.trimIndent()
        val user = buildStatePrompt(input)
        val content = runPrompt(system, user, input.history)
        return parseDecision(content, input.tools)
    }

    override fun summarize(input: BrainInput): Flow<String> = flow {
        val system = """
            You are the answer writer of PawPal. Write a warm, concise, well-structured answer.
            Use markdown. Cite numbered observations like [1]. Never invent facts that are not
            in the observations. If evidence is missing, say so plainly.
        """.trimIndent()
        val user = buildStatePrompt(input, askForAnswer = true)
        repository.streamChat(
            ChatRequest(
                model = input.model,
                prompt = user,
                history = listOf(ChatMessage(role = ChatRole.SYSTEM, content = system)),
                internal = true,
                proxy = proxy,
                ipKey = ipKey
            )
        ).collect { event ->
            if (event is StreamEvent.ContentDelta) emit(event.text)
            if (event is StreamEvent.Error) throw event.throwable
        }
    }

    private suspend fun runPrompt(
        system: String,
        user: String,
        history: List<ChatMessage>
    ): String {
        val builder = StringBuilder()
        repository.streamChat(
            ChatRequest(
                model = model,
                prompt = user,
                history = listOf(ChatMessage(role = ChatRole.SYSTEM, content = system)) + history,
                internal = true,
                proxy = proxy,
                ipKey = ipKey
            )
        ).collect { event ->
            when (event) {
                is StreamEvent.ContentDelta -> builder.append(event.text)
                is StreamEvent.ThinkingDelta -> { /* reasoning is not JSON; ignore */ }
                is StreamEvent.Error -> throw event.throwable
                else -> { /* ignore other events */ }
            }
        }
        return builder.toString()
    }

    private fun buildStatePrompt(input: BrainInput, askForAnswer: Boolean = false): String {
        val tools = input.tools.joinToString("\n") { tool ->
            "- ${tool.qualifiedName}: ${tool.description}\n  schema: ${tool.inputSchema.take(280)}"
        }.ifEmpty { "(no tools connected)" }
        val observations = if (input.observations.isEmpty()) "(none yet)"
        else input.observations.mapIndexed { index, text -> "[${index + 1}] $text" }.joinToString("\n")
        val plan = input.plan.steps.mapIndexed { index, step ->
            "${index + 1}. [${step.status}] ${step.title} — ${step.detail}"
        }.joinToString("\n")
        return """
            Goal: ${input.goal}
            Plan:
            $plan
            Observations:
            $observations
            Available tools:
            $tools
            ${if (askForAnswer) "Write the final answer now." else "What is the next action?"}
        """.trimIndent()
    }

    private fun parseSteps(content: String): List<AgentStep> {
        val json = extractJson(content) ?: return emptyList()
        val array = json.optJSONArray("steps") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            AgentStep(
                title = item.optString("title", ""),
                detail = item.optString("detail", "")
            )
        }
    }

    private fun parseDecision(content: String, tools: List<ToolDefinition>): AgentDecision {
        val json = extractJson(content)
        if (json == null) {
            return AgentDecision(reasoning = "", finalAnswer = content.trim())
        }
        val reasoning = json.optString("reasoning", "")
        val finalAnswer = json.optString("final_answer", "").trim().ifEmpty { null }
        val callsArray = json.optJSONArray("tool_calls") ?: JSONArray()
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until callsArray.length()) {
            val item = callsArray.optJSONObject(i) ?: continue
            val toolName = item.optString("tool", "")
            val definition = tools.firstOrNull { it.qualifiedName == toolName || it.name == toolName }
                ?: continue
            val rawArgs = item.opt("arguments")
            val arguments = when (rawArgs) {
                is String -> rawArgs.ifBlank { "{}" }
                else -> rawArgs?.toString() ?: "{}"
            }
            calls.add(ToolCall(tool = definition, arguments = arguments))
        }
        return AgentDecision(
            reasoning = reasoning,
            toolCalls = calls,
            finalAnswer = finalAnswer
        )
    }

    private fun extractJson(content: String): JSONObject? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return runCatching { JSONObject(content.substring(start, end + 1)) }.getOrNull()
    }

    private fun defaultSteps(goal: String): List<AgentStep> = listOf(
        AgentStep(title = "Understand the goal", detail = "Clarify what a great answer for '$goal' looks like."),
        AgentStep(title = "Gather evidence", detail = "Use web search and the connected MCP tools."),
        AgentStep(title = "Cross-check", detail = "Validate the important claims against a second source."),
        AgentStep(title = "Write the answer", detail = "Synthesize a concise, cited response.")
    )
}
