package com.chatapp.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.UsageSnapshot
import com.chatapp.ui.theme.LocalPawPalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Daily quota screen: per-IP lane, proxy lanes, capacity and reset time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSheet(
    usage: UsageSnapshot?,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPawPalColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val used = usage?.totalUsed ?: 0
    val capacity = usage?.totalCapacity ?: 50
    val progress = usage?.percent ?: 0f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.paper,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.lineSoft)
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Text(
                    text = "Today's lanes",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.ink
                )
                Text(
                    text = "No fixed global cap. 50 messages per IP per day, plus 50 for every healthy proxy lane.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMute
                )
            }

            item {
                WobblyCard(
                    seed = 71,
                    fill = colors.card,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                            UsageRing(progress = progress, size = 104.dp)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$used",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = colors.ink
                                )
                                Text(
                                    text = "/ $capacity",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.inkMute
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${capacity - used} left today",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.ink
                            )
                            Text(
                                text = "IP lane: ${usage?.ipUsed ?: 0}/50",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSoft
                            )
                            Text(
                                text = "Proxy lanes: ${usage?.proxyUsed ?: 0}/${usage?.proxyLimit ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSoft
                            )
                            Text(
                                text = "Resets ${formatReset(usage?.resetAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lane breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    Spacer(Modifier.weight(1f))
                    DoodleButton(
                        text = "Reset demo",
                        emoji = "🧹",
                        onClick = onReset,
                        background = colors.cardAlt,
                        seed = 72
                    )
                }
            }

            items(usage?.lanes ?: emptyList(), key = { it.id }) { lane ->
                WobblyCard(
                    seed = lane.id.hashCode(),
                    fill = colors.card,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = lane.emoji, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = lane.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink
                            )
                            Text(
                                text = if (lane.isDirect) "your current IP" else "${lane.region} · ${lane.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                        Text(
                            text = "${lane.used}/${lane.limit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSoft
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Meter(
                        progress = lane.percent,
                        color = if (lane.healthy) colors.mint else colors.rose,
                        height = 6.dp,
                        showPaw = false
                    )
                }
            }

            item {
                Text(
                    text = "How the math works: capacity = 50 (your IP) + 50 × healthy proxies. A pool of 5 healthy proxies gives 300 messages/day. When a lane hits 50, Mochi rotates to the next one automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
        }
    }
}

@Composable
private fun UsageRing(progress: Float, size: androidx.compose.ui.unit.Dp) {
    val colors = LocalPawPalColors.current
    Canvas(modifier = Modifier.size(size)) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = colors.lineSoft,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = colors.coral,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(colors.butter, 5.dp.toPx(), Offset(this.size.width - 16.dp.toPx(), 16.dp.toPx()))
    }
}

private fun formatReset(resetAt: Long?): String {
    if (resetAt == null) return "—"
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(resetAt))
    }.getOrElse { "—" }
}
