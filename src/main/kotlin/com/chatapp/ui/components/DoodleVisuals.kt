package com.chatapp.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.LocalPawPalColors
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Data models for Doodle visual blocks
// ---------------------------------------------------------------------------

data class DiagramNode(
    val id: String,
    val label: String
)

data class DiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String = ""
)

data class ParsedDiagram(
    val isMindMap: Boolean,
    val nodes: Map<String, DiagramNode>,
    val edges: List<DiagramEdge>
)

data class RouteStop(
    val title: String,
    val detail: String = ""
)

data class BarEntry(
    val label: String,
    val value: Float,
    val displayValue: String
)

data class ChecklistItem(
    val text: String,
    val initialChecked: Boolean
)

// ---------------------------------------------------------------------------
// 1. Doodle Mind Map / Flowchart (Mermaid)
// ---------------------------------------------------------------------------

@Composable
fun DoodleDiagram(
    rawCode: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    var showRaw by remember { mutableStateOf(false) }
    val parsed = remember(rawCode) { parseMermaidDiagram(rawCode) }

    val nodeFills = listOf(
        colors.butterSoft,
        colors.mintSoft,
        colors.skySoft,
        colors.lilacSoft,
        colors.coralSoft,
        colors.roseSoft
    )

    WobblyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        seed = rawCode.hashCode(),
        fill = colors.card,
        borderColor = colors.line,
        borderWidth = 1.5.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (parsed.isMindMap) "🧠" else "🗺️",
                fontSize = 15.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (parsed.isMindMap) "doodle mind map" else "doodle flowchart",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (showRaw) "visual" else "code",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSoft,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.lineSoft, RoundedCornerShape(8.dp))
                    .clickable { showRaw = !showRaw }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (showRaw) {
            Text(
                text = rawCode,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.inkSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.paperDeep)
                    .padding(8.dp)
            )
        } else if (parsed.nodes.isEmpty()) {
            Text(
                text = "✏️ sketching diagram…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMute
            )
        } else {
            val levels = remember(parsed) { computeDiagramLevels(parsed) }
            val edgeMap = remember(parsed) { parsed.edges.groupBy { it.fromId } }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                levels.forEachIndexed { levelIndex, levelNodeIds ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        levelNodeIds.forEachIndexed { colIndex, nodeId ->
                            val node = parsed.nodes[nodeId] ?: DiagramNode(nodeId, nodeId)
                            val fill = nodeFills[(levelIndex + colIndex) % nodeFills.size]
                            WobblyCard(
                                seed = nodeId.hashCode(),
                                fill = fill,
                                borderColor = colors.line,
                                borderWidth = 1.5.dp,
                                shadow = false,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.widthIn(min = 72.dp, max = 175.dp)
                            ) {
                                Text(
                                    text = node.label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (levelIndex < levels.lastIndex) {
                        val outgoingLabels = levelNodeIds
                            .flatMap { edgeMap[it].orEmpty() }
                            .mapNotNull { it.label.takeIf(String::isNotBlank) }
                            .distinct()
                        DoodleCurvedArrowDown(
                            label = outgoingLabels.joinToString(" / "),
                            lineColor = colors.line,
                            labelBg = colors.butterSoft,
                            labelColor = colors.ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoodleCurvedArrowDown(
    label: String,
    lineColor: Color,
    labelBg: Color,
    labelColor: Color
) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .height(34.dp)
            .widthIn(min = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 36.dp, height = 34.dp)) {
            val midX = size.width / 2f
            val path = Path().apply {
                moveTo(midX, 2f)
                cubicTo(
                    midX - 10f, size.height * 0.35f,
                    midX + 10f, size.height * 0.65f,
                    midX, size.height - 4f
                )
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            val tipY = size.height - 3f
            val arrow = Path().apply {
                moveTo(midX - 6f, tipY - 7f)
                lineTo(midX, tipY)
                lineTo(midX + 6f, tipY - 7f)
            }
            drawPath(
                path = arrow,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(labelBg)
                    .border(1.dp, lineColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Doodle Location / Route Map Card
// ---------------------------------------------------------------------------

@Composable
fun DoodleRouteMap(
    stops: List<RouteStop>,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    if (stops.isEmpty()) return

    WobblyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        seed = stops.hashCode(),
        fill = colors.card,
        borderColor = colors.line,
        borderWidth = 1.5.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📍", fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: "doodle trip route",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink
            )
            Spacer(Modifier.weight(1f))
            Chip(
                text = "${stops.size} stops",
                background = colors.mintSoft,
                color = colors.ink
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            stops.forEachIndexed { index, stop ->
                val isFirst = index == 0
                val isLast = index == stops.lastIndex
                val pinBg = when {
                    isFirst -> colors.coralSoft
                    isLast -> colors.mintSoft
                    else -> colors.butterSoft
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    // Left pin + hand-drawn squiggle connector
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(30.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(pinBg)
                                .border(1.5.dp, colors.line, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    isLast && stops.size > 1 -> "🏁"
                                    else -> "${index + 1}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.ink
                            )
                        }
                        if (!isLast) {
                            val lineColor = colors.coral
                            Canvas(
                                modifier = Modifier
                                    .width(12.dp)
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val path = Path()
                                val centerX = size.width / 2f
                                val step = 4f
                                var y = 2f
                                path.moveTo(centerX, y)
                                while (y < size.height - 2f) {
                                    val xOffset = sin(y * 0.28f) * 3.2f
                                    path.lineTo(centerX + xOffset, y)
                                    y += step
                                }
                                drawPath(
                                    path = path,
                                    color = lineColor,
                                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Right stop card
                    WobblyCard(
                        seed = stop.title.hashCode() + index,
                        fill = colors.cardAlt,
                        borderColor = colors.lineSoft,
                        borderWidth = 1.2.dp,
                        shadow = false,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isLast) 0.dp else 8.dp)
                    ) {
                        Text(
                            text = stop.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink
                        )
                        if (stop.detail.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stop.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Doodle Bar Chart
// ---------------------------------------------------------------------------

@Composable
fun DoodleBarChart(
    entries: List<BarEntry>,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    if (entries.isEmpty()) return

    val isPercentScale = entries.all { it.displayValue.contains("%") && it.value <= 100f }
    val maxVal = if (isPercentScale) 100f else (entries.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 100f)
    val barColors = listOf(colors.coral, colors.mint, colors.sky, colors.lilac, colors.butter, colors.rose)

    WobblyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        seed = entries.hashCode(),
        fill = colors.card,
        borderColor = colors.line,
        borderWidth = 1.5.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📊", fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: "doodle chart",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEachIndexed { index, entry ->
                val fraction = (entry.value / maxVal).coerceIn(0.04f, 1f)
                val barColor = barColors[index % barColors.size]
                val barShape = remember(index) { WobblyShape(seed = 50 + index, radius = 8.dp) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.displayValue,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(barShape)
                            .background(colors.paperDeep)
                            .border(1.2.dp, colors.line.copy(alpha = 0.7f), barShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(barShape)
                                .background(barColor)
                                .border(1.2.dp, colors.line, barShape)
                        ) {
                            // Subtle hand-drawn diagonal hatch lines inside bar
                            val hatchColor = colors.line.copy(alpha = 0.14f)
                            Canvas(modifier = Modifier.matchParentSize()) {
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(
                                        color = hatchColor,
                                        start = Offset(x, size.height),
                                        end = Offset(x + size.height, 0f),
                                        strokeWidth = 2f
                                    )
                                    x += 12f
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Interactive Doodle Checklist (- [ ] / - [x])
// ---------------------------------------------------------------------------

@Composable
fun DoodleChecklist(
    items: List<ChecklistItem>,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    if (items.isEmpty()) return

    val checkedStates = remember(items) {
        mutableStateListOf(*items.map { it.initialChecked }.toTypedArray())
    }
    val doneCount = checkedStates.count { it }
    val allDone = doneCount == items.size

    WobblyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        seed = items.hashCode(),
        fill = colors.card,
        borderColor = colors.line,
        borderWidth = 1.5.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = if (allDone) "🎉" else "✅", fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "doodle checklist",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink
            )
            Spacer(Modifier.weight(1f))
            Chip(
                text = "$doneCount/${items.size} done",
                background = if (allDone) colors.mintSoft else colors.butterSoft,
                color = colors.ink
            )
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { index, item ->
                val isChecked = checkedStates.getOrElse(index) { item.initialChecked }
                val boxShape = remember(index) { WobblyShape(seed = 80 + index, radius = 6.dp) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (index < checkedStates.size) {
                                checkedStates[index] = !checkedStates[index]
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(boxShape)
                            .background(if (isChecked) colors.mint else colors.paperDeep)
                            .border(1.5.dp, colors.line, boxShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChecked) {
                            val checkColor = colors.ink
                            Canvas(modifier = Modifier.size(12.dp)) {
                                val path = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.52f)
                                    lineTo(size.width * 0.42f, size.height * 0.80f)
                                    lineTo(size.width * 0.88f, size.height * 0.22f)
                                }
                                drawPath(
                                    path = path,
                                    color = checkColor,
                                    style = Stroke(
                                        width = 3.2f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(9.dp))

                    RichInlineText(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isChecked) colors.inkMute else colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Parsers (fast, regex-minimal, safe during live streaming)
// ---------------------------------------------------------------------------

private fun parseMermaidDiagram(raw: String): ParsedDiagram {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val firstNonBlank = lines.firstOrNull { it.isNotBlank() }?.trim()?.lowercase().orEmpty()
    if (firstNonBlank.startsWith("mindmap")) {
        return parseMindMap(lines)
    }

    val nodes = linkedMapOf<String, DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()
    val connectors = listOf("-.->", "==>", "-->", "---", "->")

    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isEmpty() ||
            line.startsWith("%%") ||
            line.startsWith("graph ", ignoreCase = true) ||
            line.startsWith("flowchart ", ignoreCase = true) ||
            line.startsWith("subgraph ", ignoreCase = true) ||
            line == "end" ||
            line.startsWith("classDef ", ignoreCase = true) ||
            line.startsWith("style ", ignoreCase = true)
        ) continue

        val connector = connectors.firstOrNull { line.contains(it) }
        if (connector != null) {
            val parts = line.split(connector, limit = 2)
            if (parts.size == 2) {
                val left = parseMermaidNode(parts[0].trim())
                var rightRaw = parts[1].trim()
                var edgeLabel = ""
                if (rightRaw.startsWith("|")) {
                    val endPipe = rightRaw.indexOf('|', 1)
                    if (endPipe > 1) {
                        edgeLabel = rightRaw.substring(1, endPipe).trim()
                        rightRaw = rightRaw.substring(endPipe + 1).trim()
                    }
                }
                val right = parseMermaidNode(rightRaw)
                if (left.id.isNotBlank() && right.id.isNotBlank()) {
                    nodes.putIfAbsent(left.id, left)
                    if (left.label != left.id) nodes[left.id] = left
                    nodes.putIfAbsent(right.id, right)
                    if (right.label != right.id) nodes[right.id] = right
                    edges.add(DiagramEdge(left.id, right.id, edgeLabel))
                }
            }
        } else {
            val single = parseMermaidNode(line)
            if (single.id.isNotBlank() && single.label != single.id) {
                nodes[single.id] = single
            }
        }
    }
    return ParsedDiagram(isMindMap = false, nodes = nodes, edges = edges)
}

private fun parseMermaidNode(token: String): DiagramNode {
    val clean = token.trim().trimEnd(';')
    val bracketPairs = listOf('[' to ']', '(' to ')', '{' to '}')
    for ((open, close) in bracketPairs) {
        val openIdx = clean.indexOf(open)
        val closeIdx = clean.lastIndexOf(close)
        if (openIdx > 0 && closeIdx > openIdx) {
            val id = clean.substring(0, openIdx).trim()
            val label = clean.substring(openIdx + 1, closeIdx)
                .trim()
                .trim('"', '\'', '[', ']', '(', ')')
            return DiagramNode(id = id, label = label.ifEmpty { id })
        }
    }
    val idOnly = clean.substringBefore(' ').trim()
    return DiagramNode(id = idOnly, label = idOnly)
}

private fun parseMindMap(lines: List<String>): ParsedDiagram {
    val nodes = linkedMapOf<String, DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()
    val stack = mutableListOf<Pair<Int, String>>() // indent -> nodeId
    var counter = 0

    for (rawLine in lines) {
        if (rawLine.isBlank()) continue
        val trimmed = rawLine.trim()
        if (trimmed.equals("mindmap", ignoreCase = true) || trimmed.startsWith("%%")) continue

        val indent = rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val label = trimmed
            .removePrefix("root")
            .trim()
            .trim('(', ')', '[', ']', '{', '}', '"')
            .ifEmpty { trimmed }
        val nodeId = "mm_${counter++}"
        nodes[nodeId] = DiagramNode(nodeId, label)

        while (stack.isNotEmpty() && stack.last().first >= indent) {
            stack.removeAt(stack.lastIndex)
        }
        if (stack.isNotEmpty()) {
            edges.add(DiagramEdge(stack.last().second, nodeId))
        }
        stack.add(indent to nodeId)
    }
    return ParsedDiagram(isMindMap = true, nodes = nodes, edges = edges)
}

private fun computeDiagramLevels(parsed: ParsedDiagram): List<List<String>> {
    if (parsed.nodes.isEmpty()) return emptyList()
    if (parsed.edges.isEmpty()) return listOf(parsed.nodes.keys.toList())

    val incomingCount = parsed.nodes.keys.associateWith { 0 }.toMutableMap()
    val adj = mutableMapOf<String, MutableList<String>>()
    for (edge in parsed.edges) {
        incomingCount[edge.toId] = (incomingCount[edge.toId] ?: 0) + 1
        adj.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
    }

    val roots = parsed.nodes.keys.filter { (incomingCount[it] ?: 0) == 0 }
        .ifEmpty { listOf(parsed.nodes.keys.first()) }

    val visited = mutableSetOf<String>()
    val levels = mutableListOf<List<String>>()
    var current = roots
    while (current.isNotEmpty()) {
        val uniqueCurrent = current.filter { visited.add(it) }
        if (uniqueCurrent.isEmpty()) break
        levels.add(uniqueCurrent)
        current = uniqueCurrent.flatMap { adj[it].orEmpty() }.distinct().filter { it !in visited }
    }

    val remaining = parsed.nodes.keys.filter { it !in visited }
    if (remaining.isNotEmpty()) levels.add(remaining)
    return levels
}
