package com.chatapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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

private val VIDEO_URL_REGEX = Regex("https?://[^\\s\"')]+\\.mp4")
private val BAR_ENTRY_REGEX = Regex("""^(.+?)\s*[:\-–]\s*(\d+(?:\.\d+)?%?)$""")

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
    liveContentText: String = "",
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

                val content = if (isLiveStreaming) {
                    if (liveContentText.isNotEmpty()) liveContentText else message.content
                } else message.content

                if (content.isNotBlank()) {
                    MarkdownText(
                        text = content,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    val videoUrlMatch = remember(content) {
                        if (content.contains(".mp4")) {
                            VIDEO_URL_REGEX.find(content)?.value
                        } else null
                    }
                    if (videoUrlMatch != null) {
                        Spacer(Modifier.height(8.dp))
                        VideoAttachmentCard(videoUrl = videoUrlMatch)
                    }
                    if (isLiveStreaming) {
                        Spacer(Modifier.height(4.dp))
                        StreamingCursor()
                    }
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

/** Walking paw-print cursor shown while streaming tokens. */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    WalkingPawCursor(modifier = modifier)
}

/** Tappable video card shown when an MP4 video URL is present in the response. */
@Composable
fun VideoAttachmentCard(videoUrl: String, modifier: Modifier = Modifier) {
    val colors = LocalPawPalColors.current
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.butterSoft)
            .border(1.dp, colors.coral.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { uriHandler.openUri(videoUrl) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.coral),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "▶", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI Generated MP4 Video",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = colors.ink
            )
            Text(
                text = "Tap to open & play video in full quality",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSoft
            )
        }
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
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block
    data class Diagram(val rawCode: String) : Block
    data class RouteMap(val stops: List<RouteStop>, val title: String? = null) : Block
    data class BarChart(val entries: List<BarEntry>, val title: String? = null) : Block
    data class Checklist(val items: List<ChecklistItem>) : Block
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
                    RichInlineText(
                        text = block.text,
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
                                RichInlineText(
                                    text = item,
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
                        RichInlineText(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSoft
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                is Block.Table -> {
                    DoodleTable(headers = block.headers, rows = block.rows)
                    Spacer(Modifier.height(6.dp))
                }

                is Block.Diagram -> {
                    DoodleDiagram(rawCode = block.rawCode)
                    Spacer(Modifier.height(6.dp))
                }

                is Block.RouteMap -> {
                    DoodleRouteMap(stops = block.stops, title = block.title)
                    Spacer(Modifier.height(6.dp))
                }

                is Block.BarChart -> {
                    DoodleBarChart(entries = block.entries, title = block.title)
                    Spacer(Modifier.height(6.dp))
                }

                is Block.Checklist -> {
                    DoodleChecklist(items = block.items)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun DoodleTable(
    headers: List<String>,
    rows: List<List<String>>
) {
    val colors = LocalPawPalColors.current
    if (headers.isEmpty()) return

    val colWidths = remember(headers, rows) {
        headers.indices.map { col ->
            val maxLen = maxOf(
                headers[col].length,
                rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            )
            (maxLen * 8.2f + 22f).coerceIn(76f, 185f).dp
        }
    }

    WobblyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        seed = headers.hashCode(),
        fill = colors.card,
        borderColor = colors.line,
        borderWidth = 1.5.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .background(colors.butterSoft)
            ) {
                headers.forEachIndexed { colIndex, header ->
                    RichInlineText(
                        text = header,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        modifier = Modifier
                            .width(colWidths[colIndex])
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    if (colIndex < headers.lastIndex) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(colors.line.copy(alpha = 0.45f))
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(colors.line)
            )

            // Data rows
            rows.forEachIndexed { rowIndex, row ->
                val rowBg = if (rowIndex % 2 == 0) colors.card else colors.cardAlt.copy(alpha = 0.55f)
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(rowBg)
                ) {
                    headers.indices.forEach { colIndex ->
                        val cell = row.getOrNull(colIndex).orEmpty()
                        RichInlineText(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink,
                            modifier = Modifier
                                .width(colWidths[colIndex])
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                        if (colIndex < headers.lastIndex) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(colors.lineSoft)
                            )
                        }
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.lineSoft)
                    )
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

private fun isUnorderedList(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("- ") || trimmed.startsWith("* ")
}

private fun isOrderedList(line: String): Boolean {
    val trimmed = line.trimStart()
    val dotIndex = trimmed.indexOf(". ")
    if (dotIndex in 1..4) {
        return trimmed.substring(0, dotIndex).all { it.isDigit() }
    }
    return false
}

private fun stripUnorderedPrefix(line: String): String {
    val trimmed = line.trimStart()
    return if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) trimmed.substring(2) else line
}

private fun stripOrderedPrefix(line: String): String {
    val trimmed = line.trimStart()
    val dotIndex = trimmed.indexOf(". ")
    return if (dotIndex in 1..4 && trimmed.substring(0, dotIndex).all { it.isDigit() }) {
        trimmed.substring(dotIndex + 2)
    } else line
}

private fun isTableRow(line: String): Boolean = line.contains('|')

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('-')) return false
    return trimmed.all { it == '|' || it == '-' || it == ':' || it.isWhitespace() } &&
        trimmed.count { it == '-' } >= 2
}

private fun splitTableCells(line: String): List<String> {
    var trimmed = line.trim()
    if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
    if (trimmed.endsWith("|") && trimmed.isNotEmpty()) trimmed = trimmed.substring(0, trimmed.length - 1)
    return trimmed.split('|').map { it.trim() }
}

private fun tryParseChecklist(items: List<String>): List<ChecklistItem>? {
    if (items.isEmpty()) return null
    val parsed = items.mapNotNull { raw ->
        val t = raw.trimStart()
        when {
            t.startsWith("[ ] ") -> ChecklistItem(t.substring(4).trim(), initialChecked = false)
            t.startsWith("[x] ", ignoreCase = true) -> ChecklistItem(t.substring(4).trim(), initialChecked = true)
            t == "[ ]" -> ChecklistItem("", initialChecked = false)
            t.equals("[x]", ignoreCase = true) -> ChecklistItem("", initialChecked = true)
            else -> null
        }
    }
    return if (parsed.size == items.size) parsed else null
}

private fun tryParseBarChart(items: List<String>): List<BarEntry>? {
    if (items.size < 2) return null
    val parsed = items.mapNotNull { raw ->
        val clean = raw.replace("**", "").trim()
        val match = BAR_ENTRY_REGEX.matchEntire(clean) ?: return@mapNotNull null
        val label = match.groupValues[1].trim()
        val rawVal = match.groupValues[2].trim()
        val num = rawVal.removeSuffix("%").toFloatOrNull() ?: return@mapNotNull null
        BarEntry(label = label, value = num, displayValue = rawVal)
    }
    return if (parsed.size == items.size) parsed else null
}

private fun tryParseRouteStops(items: List<String>): List<RouteStop>? {
    if (items.size < 2) return null
    val looksLikeRoute = items.count { item ->
        val t = item.trimStart()
        t.startsWith("📍") ||
            t.startsWith("🗺️") ||
            t.startsWith("🏁") ||
            t.startsWith("Day ", ignoreCase = true) ||
            t.startsWith("Stop ", ignoreCase = true)
    } >= 2
    if (!looksLikeRoute) return null
    return items.map { raw ->
        val clean = raw.removePrefix("📍").removePrefix("🗺️").removePrefix("🏁").trim()
        val colonIdx = clean.indexOf(':')
        if (colonIdx in 1..28) {
            RouteStop(
                title = clean.substring(0, colonIdx).replace("**", "").trim(),
                detail = clean.substring(colonIdx + 1).replace("**", "").trim()
            )
        } else {
            RouteStop(title = clean.replace("**", "").trim())
        }
    }
}

private fun tryParseArrowRouteLine(line: String): List<RouteStop>? {
    val sep = when {
        line.contains("→") -> "→"
        line.contains("->") -> "->"
        else -> return null
    }
    val parts = line.split(sep).map { it.replace("**", "").trim() }.filter { it.isNotBlank() }
    return if (parts.size >= 3 && parts.all { it.length <= 45 }) {
        parts.map { RouteStop(title = it) }
    } else null
}

private fun parseBlocks(text: String): List<Block> {
    if (text.isEmpty()) return emptyList()
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
                val rawBlock = code.joinToString("\n")
                when {
                    language.equals("mermaid", ignoreCase = true) -> {
                        blocks.add(Block.Diagram(rawBlock))
                    }
                    language.equals("chart", ignoreCase = true) || language.equals("bar", ignoreCase = true) -> {
                        val chartLines = code.filter { it.isNotBlank() }
                        val entries = tryParseBarChart(chartLines)
                        if (entries != null) blocks.add(Block.BarChart(entries))
                        else blocks.add(Block.Code(language, rawBlock))
                    }
                    language.equals("route", ignoreCase = true) ||
                        language.equals("map", ignoreCase = true) ||
                        language.equals("itinerary", ignoreCase = true) -> {
                        val stops = code.filter { it.isNotBlank() }.map { raw ->
                            val clean = raw.trim().removePrefix("- ").removePrefix("* ").trim()
                            val colonIdx = clean.indexOf(':')
                            if (colonIdx in 1..28) {
                                RouteStop(clean.substring(0, colonIdx).trim(), clean.substring(colonIdx + 1).trim())
                            } else RouteStop(clean)
                        }
                        if (stops.isNotEmpty()) blocks.add(Block.RouteMap(stops))
                        else blocks.add(Block.Code(language, rawBlock))
                    }
                    else -> blocks.add(Block.Code(language, rawBlock))
                }
            }

            isTableRow(line) && index + 1 < lines.size && isTableSeparator(lines[index + 1]) -> {
                val headers = splitTableCells(line)
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].isNotBlank() && isTableRow(lines[index])) {
                    val rawCells = splitTableCells(lines[index])
                    val normalized = List(headers.size) { col -> rawCells.getOrElse(col) { "" } }
                    rows.add(normalized)
                    index += 1
                }
                blocks.add(Block.Table(headers, rows))
            }

            tryParseArrowRouteLine(line) != null -> {
                blocks.add(Block.RouteMap(tryParseArrowRouteLine(line)!!))
                index += 1
            }

            line.startsWith("# ") -> {
                blocks.add(Block.Heading(line.removePrefix("# ").trim()))
                index += 1
            }

            line.startsWith("## ") -> {
                blocks.add(Block.Heading(line.removePrefix("## ").trim()))
                index += 1
            }

            line.startsWith("### ") -> {
                blocks.add(Block.Heading(line.removePrefix("### ").trim()))
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

            isUnorderedList(line) -> {
                val items = mutableListOf<String>()
                while (index < lines.size && isUnorderedList(lines[index])) {
                    items.add(stripUnorderedPrefix(lines[index]))
                    index += 1
                }
                val checklist = tryParseChecklist(items)
                val barEntries = if (checklist == null) tryParseBarChart(items) else null
                val routeStops = if (checklist == null && barEntries == null) tryParseRouteStops(items) else null
                when {
                    checklist != null -> blocks.add(Block.Checklist(checklist))
                    barEntries != null -> blocks.add(Block.BarChart(barEntries))
                    routeStops != null -> blocks.add(Block.RouteMap(routeStops))
                    else -> blocks.add(Block.ListBlock(items, ordered = false))
                }
            }

            isOrderedList(line) -> {
                val items = mutableListOf<String>()
                while (index < lines.size && isOrderedList(lines[index])) {
                    items.add(stripOrderedPrefix(lines[index]))
                    index += 1
                }
                val checklist = tryParseChecklist(items)
                val barEntries = if (checklist == null) tryParseBarChart(items) else null
                val routeStops = if (checklist == null && barEntries == null) tryParseRouteStops(items) else null
                when {
                    checklist != null -> blocks.add(Block.Checklist(checklist))
                    barEntries != null -> blocks.add(Block.BarChart(barEntries))
                    routeStops != null -> blocks.add(Block.RouteMap(routeStops))
                    else -> blocks.add(Block.ListBlock(items, ordered = true))
                }
            }

            line.isBlank() -> index += 1

            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size &&
                    lines[index].isNotBlank() &&
                    !lines[index].startsWith("```") &&
                    !lines[index].startsWith("#") &&
                    !lines[index].startsWith("> ") &&
                    !isUnorderedList(lines[index]) &&
                    !isOrderedList(lines[index]) &&
                    !(isTableRow(lines[index]) && index + 1 < lines.size && isTableSeparator(lines[index + 1])) &&
                    tryParseArrowRouteLine(lines[index]) == null
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

@Composable
fun RichInlineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    val parsed = remember(text, colors) { inlineMarkdownWithSwatches(text, colors) }
    Text(
        text = parsed.text,
        style = style,
        color = color,
        inlineContent = parsed.inlineContent,
        modifier = modifier
    )
}

private data class RichMarkdownResult(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

private fun isHexDigit(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

private fun parseHexColorOrNull(raw: String): Color? {
    if (raw.length != 7 || raw[0] != '#') return null
    for (k in 1..6) if (!isHexDigit(raw[k])) return null
    val rgb = raw.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

private fun inlineMarkdownWithSwatches(
    text: String,
    colors: com.chatapp.ui.theme.PawPalColors
): RichMarkdownResult {
    if (!text.contains('*') && !text.contains('`') && !text.contains('[') && !text.contains('#')) {
        return RichMarkdownResult(AnnotatedString(text), emptyMap())
    }
    val inlineMap = mutableMapOf<String, InlineTextContent>()

    fun registerSwatch(hex: String, swatchColor: Color): String {
        val key = "swatch_${hex.uppercase()}"
        if (!inlineMap.containsKey(key)) {
            val outline = colors.line
            inlineMap[key] = InlineTextContent(
                Placeholder(
                    width = 16.sp,
                    height = 16.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier.padding(start = 2.dp, end = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = swatchColor)
                        drawCircle(color = outline, style = Stroke(width = 2.2f))
                    }
                }
            }
        }
        return key
    }

    val annotated = buildAnnotatedString {
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
                        val code = text.substring(i + 1, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = colors.butterSoft,
                                color = colors.ink
                            )
                        ) {
                            append(code)
                        }
                        val swatchColor = parseHexColorOrNull(code.trim())
                        if (swatchColor != null) {
                            appendInlineContent(registerSwatch(code.trim(), swatchColor), " ")
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i += 1
                    }
                }

                text[i] == '#' && i + 7 <= text.length &&
                    (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                    (i + 7 == text.length || !isHexDigit(text[i + 7])) -> {
                    val candidate = text.substring(i, i + 7)
                    val swatchColor = parseHexColorOrNull(candidate)
                    if (swatchColor != null) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = colors.butterSoft,
                                color = colors.ink
                            )
                        ) {
                            append(candidate)
                        }
                        appendInlineContent(registerSwatch(candidate, swatchColor), " ")
                        i += 7
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
                    val nextStar = text.indexOf('*', i).let { if (it == -1) Int.MAX_VALUE else it }
                    val nextBacktick = text.indexOf('`', i).let { if (it == -1) Int.MAX_VALUE else it }
                    val nextBracket = text.indexOf('[', i).let { if (it == -1) Int.MAX_VALUE else it }
                    val nextHash = text.indexOf('#', i).let { if (it == -1) Int.MAX_VALUE else it }
                    val next = minOf(minOf(nextStar, nextBacktick), minOf(nextBracket, nextHash))
                    if (next == Int.MAX_VALUE) {
                        append(text.substring(i))
                        break
                    } else if (next > i) {
                        append(text.substring(i, next))
                        i = next
                    } else {
                        append(text[i])
                        i += 1
                    }
                }
            }
        }
    }
    return RichMarkdownResult(annotated, inlineMap)
}

private fun formatTime(timestamp: Long): String =
    runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrElse { "" }
