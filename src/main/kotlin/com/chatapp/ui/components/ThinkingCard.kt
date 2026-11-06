package com.chatapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.LocalPawPalColors

/**
 * The "thinking" card. Warm butter paper, a pulsing paw and a collapsible
 * stream of the model's reasoning tokens.
 */
@Composable
fun ThinkingCard(
    thinkingText: String,
    isThinkingActive: Boolean,
    durationMs: Long? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    var expanded by remember { mutableStateOf(isThinkingActive) }
    WobblyCard(
        modifier = modifier,
        seed = 12,
        fill = colors.butterSoft,
        contentPadding = PaddingValues(11.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .padding(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🧠", fontSize = 16.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = when {
                    isThinkingActive -> "thinking…"
                    durationMs != null -> "thought for ${"%.1f".format(durationMs / 1000f)}s"
                    else -> "thinking process"
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink
            )
            if (isThinkingActive) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.coral)
                        .border(1.dp, colors.line, CircleShape)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "hide" else "show",
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
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.lineSoft)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = thinkingText.ifEmpty { "Sniffing around the problem…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.card.copy(alpha = 0.55f))
                        .padding(8.dp)
                )
            }
        }
    }
}
