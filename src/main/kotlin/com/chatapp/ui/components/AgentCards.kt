package com.chatapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.AgentPlan
import com.chatapp.aiengine.model.StepStatus
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.ToolRisk
import com.chatapp.aiengine.model.WebCitation
import com.chatapp.ui.theme.HandSmallStyle
import com.chatapp.ui.theme.LocalPawPalColors
import org.json.JSONObject

/** The plan card: goal, progress and every step with its live status. */
@Composable
fun AgentPlanCard(
    plan: AgentPlan,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    defaultExpanded: Boolean = true
) {
    val colors = LocalPawPalColors.current
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val progress by animateFloatAsState(
        targetValue = plan.progress,
        animationSpec = tween(350),
        label = "plan-progress"
    )
    WobblyCard(
        modifier = modifier,
        seed = 21,
        fill = colors.butterSoft,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🐾", fontSize = 18.sp)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (live) "Mochi's plan" else "Plan",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Text(
                    text = plan.goal,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(
                text = when (plan.status) {
                    StepStatus.DONE -> "done"
                    StepStatus.FAILED -> "needs a look"
                    StepStatus.RUNNING -> "running"
                    else -> "planned"
                },
                color = when (plan.status) {
                    StepStatus.DONE -> colors.success
                    StepStatus.FAILED -> colors.danger
                    StepStatus.RUNNING -> colors.coral
                    else -> colors.inkMute
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Meter(progress = progress, color = colors.coral, height = 8.dp, showPaw = false)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${plan.completedSteps}/${plan.steps.size} steps",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMute
            )
            Text(
                text = if (expanded) "hide" else "show",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMute,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .padding(4.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + shrinkVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                plan.steps.forEachIndexed { index, step ->
                    StepRow(index = index + 1, step = step)
                    if (index != plan.steps.lastIndex) Spacer(Modifier.height(5.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun StepRow(index: Int, step: com.chatapp.aiengine.model.AgentStep) {
    val colors = LocalPawPalColors.current
    val running = step.status == StepStatus.RUNNING
    val done = step.status == StepStatus.DONE
    val failed = step.status == StepStatus.FAILED
    val skipped = step.status == StepStatus.SKIPPED
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        done -> colors.mint
                        failed -> colors.rose
                        running -> colors.coral
                        skipped -> colors.lineSoft
                        else -> colors.card
                    }
                )
                .border(1.4.dp, colors.line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    done -> "✓"
                    failed -> "!"
                    running -> "🐾"
                    skipped -> "–"
                    else -> "$index"
                },
                fontSize = if (running) 9.sp else 11.sp,
                color = colors.ink
            )
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (running) colors.ink else colors.inkSoft,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            if (step.detail.isNotBlank()) {
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (step.toolCalls.isNotEmpty()) {
                Row(Modifier.padding(top = 3.dp)) {
                    step.toolCalls.forEach { call ->
                        Chip(
                            text = "${call.tool.emoji} ${call.tool.name}",
                            background = colors.card,
                            color = colors.inkSoft
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

/** A single tool invocation with arguments, result and risk level. */
@Composable
fun ToolCallCard(
    call: ToolCall,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(call.status == StepStatus.RUNNING) }
    val running = call.status == StepStatus.RUNNING
    val done = call.status == StepStatus.DONE
    val failed = call.status == StepStatus.FAILED
    val skipped = call.status == StepStatus.SKIPPED
    val mailto = remember(call.result) {
        call.result?.let { text ->
            "mailto:".let { prefix ->
                text.substringAfter(prefix, "").substringBefore("\n").takeIf { text.contains(prefix) }
            }
        }
    }
    WobblyCard(
        modifier = modifier,
        seed = call.id.hashCode(),
        fill = colors.card,
        contentPadding = PaddingValues(11.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .padding(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.cardAlt)
                    .border(1.2.dp, colors.lineSoft, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = call.tool.emoji, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.tool.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = colors.ink
                    )
                    if (call.simulated) {
                        Spacer(Modifier.width(4.dp))
                        Chip(text = "simulated", background = colors.butterSoft, color = colors.inkMute, border = false)
                    }
                }
                Text(
                    text = call.tool.serverName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
            if (call.durationMs > 0) {
                Text(
                    text = "${call.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
            Spacer(Modifier.width(5.dp))
            StatusPill(
                text = when {
                    running -> "running"
                    done -> "done"
                    failed -> "failed"
                    skipped -> "skipped"
                    else -> "queued"
                },
                color = when {
                    running -> colors.coral
                    done -> colors.success
                    failed -> colors.danger
                    skipped -> colors.inkMute
                    else -> colors.inkMute
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (call.tool.risk != ToolRisk.READ_ONLY) {
                Chip(
                    text = if (call.tool.risk == ToolRisk.DESTRUCTIVE) "destructive" else "write",
                    background = colors.roseSoft,
                    color = colors.danger,
                    emoji = "⚠️"
                )
                Spacer(Modifier.width(4.dp))
            }
            Chip(text = call.tool.qualifiedName, background = colors.cardAlt, color = colors.inkMute, border = false)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "hide" else "details",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMute,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .padding(4.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + shrinkVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                if (call.arguments.isNotBlank() && call.arguments != "{}") {
                    Text(
                        text = "arguments",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute
                    )
                    Text(
                        text = prettyJson(call.arguments),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.paperDeep)
                            .padding(7.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                }
                if (call.result.isNotBlank()) {
                    Text(
                        text = "result",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute
                    )
                    Text(
                        text = call.result!!.take(700),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkSoft,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.paperDeep)
                            .padding(7.dp)
                    )
                }
                if (call.error.isNotBlank() && call.error != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "⚠️ ${call.error}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.danger
                    )
                }
                if (mailto != null) {
                    Spacer(Modifier.height(6.dp))
                    DoodleButton(
                        text = "Open email draft",
                        emoji = "📧",
                        onClick = { runCatching { uriHandler.openUri(mailto) } },
                        background = colors.mint,
                        seed = 33
                    )
                }
            }
        }
    }
}

/** Horizontal source chips. */
@Composable
fun CitationRow(
    citations: List<WebCitation>,
    modifier: Modifier = Modifier
) {
    if (citations.isEmpty()) return
    val colors = LocalPawPalColors.current
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        Text(
            text = "Sources (${citations.size})",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMute
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            citations.forEach { citation ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.skySoft)
                        .border(1.2.dp, colors.lineSoft, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .clickable { runCatching { uriHandler.openUri(citation.url) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔗", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = citation.title.ifEmpty { citation.url },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = citation.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Human-in-the-loop approval card for write tools. */
@Composable
fun ApprovalCard(
    call: ToolCall,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    WobblyCard(
        modifier = modifier,
        seed = 44,
        fill = colors.roseSoft,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✋", fontSize = 18.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Mochi needs your OK",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink
            )
        }
        Text(
            text = "She wants to run ${call.tool.name} on ${call.tool.serverName}. " +
                "This tool can change something outside the chat.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DoodleButton(
                text = "Approve",
                emoji = "👍",
                onClick = onApprove,
                background = colors.mint,
                modifier = Modifier.weight(1f),
                seed = 45
            )
            DoodleButton(
                text = "Nope",
                emoji = "🙅",
                onClick = onReject,
                background = colors.card,
                modifier = Modifier.weight(1f),
                seed = 46
            )
        }
    }
}

private fun prettyJson(raw: String): String {
    return runCatching {
        val json = org.json.JSONObject(raw)
        json.keys().asSequence().joinToString("\n") { key ->
            val value = json.opt(key)
            "$key: ${value?.toString()?.take(80)}"
        }
    }.getOrElse { raw.take(240) }
}
