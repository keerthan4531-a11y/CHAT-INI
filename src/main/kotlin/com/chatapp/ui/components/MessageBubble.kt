package com.chatapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.AgentPlan
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.aiengine.model.WebCitation
import com.chatapp.ui.theme.HandSmallStyle
import com.chatapp.ui.theme.LocalPawPalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A chat turn. User messages are chunky pastel bubbles; assistant messages are
 * paper with a mascot avatar, live plan, tool cards, sources and a typewriter
 * stream. Everything is built from the doodle kit.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    mascot: MascotKind,
    assistantName: String,
    modifier: Modifier = Modifier,
    isLiveStreaming: Boolean = false,
    liveThinkingText: String = "",
    isThinkingActive: Boolean = false,
    livePlan: AgentPlan? = null,
    liveToolCalls: List<ToolCall> = emptyList(),
    liveCitations: List<WebCitation> = emptyList(),
    isWebSearching: Boolean = false,
    onRegenerate: (() -> Unit)? = null
) {
    val colors = LocalPawPalColors.current
    val isUser = message.role == ChatRole.USER
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            MascotAvatar(
                kind = mascot,
                size = 38.dp,
                mood = when {
                    isThinkingActive -> CatMood.THINKING
                    isWebSearching -> CatMood.SEARCHING
                    liveToolCalls.isNotEmpty() -> CatMood.TOOL
                    isLiveStreaming -> CatMood.TYPING
                    message.content.isNotBlank() -> CatMood.HAPPY
                    else -> CatMood.IDLE
                }
            )
            Spacer(Modifier.width(9.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 360.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                WobblyCard(
                    seed = message.id.hashCode(),
                    fill = colors.lilacSoft,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = assistantName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    if (message.modelName != null) {
                        Spacer(Modifier.width(5.dp))
                        Chip(
                            text = message.modelName!!,
                            background = colors.cardAlt,
                            color = colors.inkMute,
                            border = false
                        )
                    }
                    if (message.hasAgentWork) {
                        Spacer(Modifier.width(4.dp))
                        Chip(text = "agent", emoji = "🐾", background = colors.mintSoft, color = colors.ink)
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute
                    )
                }

                val thinking = if (isLiveStreaming) liveThinkingText else message.reasoningContent.orEmpty()
                if (thinking.isNotBlank() || isThinkingActive) {
                    ThinkingCard(
                        thinkingText = thinking,
                        isThinkingActive = isThinkingActive,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }

                val plan = if (isLiveStreaming) livePlan else message.agentPlan
                if (plan != null) {
                    AgentPlanCard(
                        plan = plan,
                        live = isLiveStreaming,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }

                val tools = if (isLiveStreaming) liveToolCalls else message.toolCalls
                if (tools.isNotEmpty()) {
                    Column(Modifier.padding(top = 5.dp)) {
                        tools.forEach { call ->
                            ToolCallCard(call = call, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }

                val citations = if (isLiveStreaming) liveCitations else message.citations
                if (isWebSearching || citations.isNotEmpty()) {
                    WebSearchCard(
                        isSearching = isWebSearching,
                        citations = citations,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }

                val content = if (isLiveStreaming) liveContentText else message.content
                if (content.isNotBlank()) {
                    MarkdownText(
                        text = content,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else if (isLiveStreaming) {
                    TypingDots(
                        text = when {
                            isThinkingActive -> "Mochi is thinking"
                            isWebSearching -> "Mochi is sniffing the web"
                            liveToolCalls.isNotEmpty() -> "Mochi is using a tool"
                            else -> "Mochi is typing"
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (!isLiveStreaming && content.isNotBlank()) {
                    MessageActions(
                        onCopy = { clipboard.setText(AnnotatedString(content)) },
                        onRegenerate = onRegenerate,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActions(
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ActionIcon(emoji = "📋", onClick = onCopy, label = "Copy")
        if (onRegenerate != null) {
            Spacer(Modifier.width(2.dp))
            ActionIcon(emoji = "🔄", onClick = onRegenerate, label = "Regenerate")
        }
        Spacer(Modifier.width(2.dp))
        ActionIcon(emoji = "👍", onClick = {}, label = "Good answer")
        Spacer(Modifier.width(2.dp))
        ActionIcon(emoji = "👎", onClick = {}, label = "Bad answer")
        Spacer(Modifier.weight(1f))
        Text(text = "made with 🐾", style = HandSmallStyle, color = colors.inkMute, fontSize = 15.sp)
    }
}

@Composable
private fun ActionIcon(emoji: String, onClick: () -> Unit, label: String) {
    val colors = LocalPawPalColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 13.sp)
    }
}

/** Three bouncing dots + a hand-written status line. */
@Composable
fun TypingDots(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620, delayMillis = index * 130),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot-$index"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.coral.copy(alpha = alpha))
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(text = text, style = HandSmallStyle, color = colors.inkMute, fontSize = 17.sp)
    }
}

// ---------------------------------------------------------------------------
// Tiny markdown renderer (safe: no HTML, only the subset models actually use)
// ---------------------------------------------------------------------------

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Code(val language: String, val code: String) : Block
    data class ListBlock(val items: List<String>, val ordered: Boolean) : Block
    data class Quote(val text: String) : Block
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph -> {
                    Text(
                        text = inlineMarkdown(block.text, colors),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is Block.Heading -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                }

                is Block.Code -> CodeBlock(block.language, block.code)
                is Block.ListBlock -> {
                    Column(Modifier.padding(start = 2.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = if (block.ordered) "${index + 1}." else "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.coral,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = inlineMarkdown(item, colors),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }

                is Block.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.butterSoft)
                            .padding(8.dp)
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.coral))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = inlineMarkdown(block.text, colors),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSoft
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val colors = LocalPawPalColors.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.codeBg)
            .border(1.5.dp, colors.line, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifEmpty { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.codeText.copy(alpha = 0.6f)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "copy",
                style = MaterialTheme.typography.labelSmall,
                color = colors.codeText.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(4.dp)
            )
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.codeText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 9.dp)
        )
    }
}

private fun parseBlocks(text: String): List<Block> {
    val lines = text.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<Block>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim()
                val code = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].startsWith("```")) {
                    code.add(lines[index])
                    index += 1
                }
                index += 1
                blocks.add(Block.Code(language, code.joinToString("\n")))
            }

            line.startsWith("# ") -> {
                blocks.add(Block.Heading(line.removePrefix("# ").trim()))
                index += 1
            }

            line.startsWith("## ") -> {
                blocks.add(Block.Heading(line.removePrefix("## ").trim()))
                index += 1
            }

            line.startsWith("> ") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].startsWith("> ")) {
                    quote.add(lines[index].removePrefix("> "))
                    index += 1
                }
                blocks.add(Block.Quote(quote.joinToString(" ")))
            }

            line.matches(Regex("^\\s*[-*]\\s+.*")) -> {
                val items = mutableListOf<String>()
                while (index < lines.size && lines[index].matches(Regex("^\\s*[-*]\\s+.*"))) {
                    items.add(lines[index].replace(Regex("^\\s*[-*]\\s+"), ""))
                    index += 1
                }
                blocks.add(Block.ListBlock(items, ordered = false))
            }

            line.matches(Regex("^\\s*\\d+\\.\\s+.*")) -> {
                val items = mutableListOf<String>()
                while (index < lines.size && lines[index].matches(Regex("^\\s*\\d+\\.\\s+.*"))) {
                    items.add(lines[index].replace(Regex("^\\s*\\d+\\.\\s+"), ""))
                    index += 1
                }
                blocks.add(Block.ListBlock(items, ordered = true))
            }

            line.isBlank() -> index += 1

            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size &&
                    lines[index].isNotBlank() &&
                    !lines[index].startsWith("```") &&
                    !lines[index].startsWith("#") &&
                    !lines[index].startsWith("> ") &&
                    !lines[index].matches(Regex("^\\s*[-*]\\s+.*")) &&
                    !lines[index].matches(Regex("^\\s*\\d+\\.\\s+.*"))
                ) {
                    paragraph.add(lines[index])
                    index += 1
                }
                blocks.add(Block.Paragraph(paragraph.joinToString(" ")))
            }
        }
    }
    return blocks
}

private fun inlineMarkdown(text: String, colors: com.chatapp.ui.theme.PawPalColors): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i]); i += 1
                    }
                }

                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = colors.butterSoft,
                                color = colors.ink
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i += 1
                    }
                }

                text[i] == '[' -> {
                    val close = text.indexOf(']', i)
                    if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                        val endUrl = text.indexOf(')', close + 2)
                        if (endUrl > close + 2) {
                            val label = text.substring(i + 1, close)
                            val url = text.substring(close + 2, endUrl)
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                withLink(LinkAnnotation.Url(url)) {
                                    withStyle(SpanStyle(color = colors.coral)) {
                                        append(label)
                                    }
                                }
                            } else {
                                append(label)
                            }
                            i = endUrl + 1
                        } else {
                            append(text[i]); i += 1
                        }
                    } else {
                        append(text[i]); i += 1
                    }
                }

                else -> {
                    append(text[i])
                    i += 1
                }
            }
        }
    }

private fun formatTime(timestamp: Long): String =
    runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrElse { "" }
