package com.chatapp.aiengine.core

import com.chatapp.aiengine.model.AgentPlan
import com.chatapp.aiengine.model.AgentStep
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRequest
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.aiengine.model.StepStatus
import com.chatapp.aiengine.model.StreamEvent
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.UsageSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Asks the human before a write/destructive tool runs. */
fun interface ApprovalGate {
    suspend fun approve(call: ToolCall): Boolean
}

/** Safe default: read-only tools run, writes are rejected unless configured. */
object AutoApprovalGate : ApprovalGate {
    override suspend fun approve(call: ToolCall): Boolean = !call.tool.requiresApproval
}

/**
 * The agent runtime.
 *
 * Pattern: **Plan-and-Execute inside each step, ReAct for the action choice**.
 * - The planner writes a short, reviewable plan.
 * - Each step asks the brain for at most 3 typed tool calls or a final answer.
 * - Every tool call is validated against the registry and risk level.
 * - Writes pause for [ApprovalGate].
 * - Hard caps ([AgentBudget]) stop runaway loops and runaway spend.
 * - A grounded summarizer runs if the loop runs out of steps, so the user
 *   always gets an answer plus an honest note about what was missing.
 */
class AgentLoop(
    private val brain: AgentBrain,
    private val tools: ToolRegistry,
    private val executor: ToolExecutor,
    private val memory: AgentMemory = AgentMemory(),
    private val approvalGate: ApprovalGate = AutoApprovalGate,
    private val usage: UsageSnapshot
) {
    fun run(request: ChatRequest): Flow<StreamEvent> = flow {
        val startedAt = System.currentTimeMillis()
        val budget = request.budget
        val availableTools = tools.forServers(request.mcpServerIds)

        var plan: AgentPlan = brain.plan(request.prompt, availableTools, budget)
        emit(StreamEvent.AgentPlanUpdated(plan))

        val observations = mutableListOf<String>()
        val executedCalls = mutableListOf<ToolCall>()
        var stepsUsed = 0
        var toolCallsUsed = 0
        var finalAnswer: String? = null

        while (stepsUsed < budget.maxSteps && System.currentTimeMillis() - startedAt < budget.maxDurationMs) {
            val step = plan.nextPending() ?: break
            val running = step.copy(status = StepStatus.RUNNING, startedAt = System.currentTimeMillis())
            plan = plan.withStep(running)
            emit(StreamEvent.AgentPlanUpdated(plan))
            emit(StreamEvent.AgentStepStarted(running))
            stepsUsed += 1

            val decision = brain.decide(
                BrainInput(
                    goal = request.prompt,
                    plan = plan,
                    observations = observations,
                    tools = availableTools,
                    history = request.history,
                    model = request.model
                )
            )
            if (decision.reasoning.isNotBlank()) {
                emit(StreamEvent.ThinkingDelta(decision.reasoning))
            }

            var stepFailed = false
            for (call in decision.toolCalls) {
                if (toolCallsUsed >= budget.maxToolCalls) {
                    stepFailed = true
                    break
                }
                val approved = if (call.tool.requiresApproval && request.requireApprovalForWrites) {
                    approvalGate.approve(call)
                } else {
                    true
                }
                if (!approved) {
                    val rejected = call.copy(status = StepStatus.SKIPPED, error = "Rejected by user")
                    executedCalls.add(rejected)
                    emit(StreamEvent.ToolCallCompleted(rejected))
                    continue
                }

                emit(StreamEvent.ToolCallStarted(call))
                val result = executor.execute(call)
                val completed = call.copy(
                    status = if (result.ok) StepStatus.DONE else StepStatus.FAILED,
                    result = result.output,
                    error = if (result.ok) null else result.output,
                    durationMs = result.durationMs,
                    simulated = call.tool.name == "web_search"
                )
                executedCalls.add(completed)
                emit(StreamEvent.ToolCallCompleted(completed))
                toolCallsUsed += 1

                if (result.ok) {
                    observations.add("[${call.tool.name}] ${result.output.take(600)}")
                    memory.rememberObservation("[${call.tool.name}] ${result.output.take(300)}")
                } else {
                    stepFailed = true
                    observations.add("[${call.tool.name}] FAILED: ${result.output.take(300)}")
                }
            }

            if (decision.finalAnswer != null) {
                finalAnswer = decision.finalAnswer
                val done = running.copy(status = StepStatus.DONE, finishedAt = System.currentTimeMillis())
                plan = plan.withStep(done)
                emit(StreamEvent.AgentStepCompleted(done))
                emit(StreamEvent.AgentPlanUpdated(plan))
                break
            }

            val finished = running.copy(
                status = if (stepFailed) StepStatus.FAILED else StepStatus.DONE,
                finishedAt = System.currentTimeMillis()
            )
            plan = plan.withStep(finished)
            emit(StreamEvent.AgentStepCompleted(finished))
            emit(StreamEvent.AgentPlanUpdated(plan))
        }

        // Grounded fallback: never leave the user with an empty turn.
        if (finalAnswer.isNullOrBlank()) {
            val builder = StringBuilder()
            brain.summarize(
                BrainInput(
                    goal = request.prompt,
                    plan = plan,
                    observations = observations,
                    tools = availableTools,
                    history = request.history,
                    model = request.model
                )
            ).collect { chunk ->
                builder.append(chunk)
                emit(StreamEvent.ContentDelta(chunk))
            }
            finalAnswer = builder.toString().ifBlank {
                "I couldn't finish this one, but here's what I found:\n\n" +
                    observations.joinToString("\n").ifBlank { "No evidence was gathered." }
            }
        } else {
            // Typewriter effect for the UI.
            finalAnswer!!.chunked(5).forEach { chunk ->
                emit(StreamEvent.ContentDelta(chunk))
                delay(8)
            }
        }

        val completedPlan = plan.copy(status = StepStatus.DONE)
        val message = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = finalAnswer ?: "",
            agentPlan = completedPlan,
            toolCalls = executedCalls,
            modelName = request.model.displayName,
            latencyMs = System.currentTimeMillis() - startedAt
        )
        emit(StreamEvent.UsageUpdated(usage))
        emit(StreamEvent.AgentCompleted(message, completedPlan, usage))
        emit(StreamEvent.Completed(message))
    }
}
