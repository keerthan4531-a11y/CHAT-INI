package com.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.LocalPawPalColors

/**
 * The doodle composer: a chunky paper card with a tool row, auto-growing
 * input, character counter and a paw send button that turns into a stop
 * button while streaming.
 */
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    isWebSearchEnabled: Boolean,
    isAgentMode: Boolean,
    isDeepMode: Boolean = false,
    onToggleWebSearch: () -> Unit,
    onToggleAgent: () -> Unit,
    onToggleDeep: () -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: () -> Unit = {}
) {
    val colors = LocalPawPalColors.current
    val canSend = inputText.isNotBlank() && !isGenerating

    WobblyCard(
        modifier = modifier,
        seed = 31,
        fill = colors.card,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolPill(emoji = "📎", onClick = onAttach, active = false)
            Spacer(Modifier.width(5.dp))
            ToolPill(
                emoji = "🌐",
                onClick = onToggleWebSearch,
                active = isWebSearchEnabled,
                label = "Search"
            )
            Spacer(Modifier.width(5.dp))
            ToolPill(
                emoji = "🐾",
                onClick = onToggleAgent,
                active = isAgentMode,
                label = "Agent"
            )
            Spacer(Modifier.width(5.dp))
            ToolPill(
                emoji = "🔬",
                onClick = onToggleDeep,
                active = isDeepMode,
                label = "Deep"
            )
            Spacer(Modifier.weight(1f))
            if (inputText.isNotEmpty()) {
                Text(
                    text = "${inputText.length}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = if (isAgentMode) "agent mode" else "chat mode",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMute
            )
        }

        Spacer(Modifier.size(6.dp))

        BasicTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontFamily = com.chatapp.ui.theme.NunitoFontFamily,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.ink
            ),
            cursorBrush = SolidColor(colors.coral),
            maxLines = 5,
            decorationBox = { inner ->
                if (inputText.isEmpty()) {
                    Text(
                        text = "Ask Mochi anything… she's a clever cat 🐱",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkMute
                    )
                }
                inner()
            }
        )

        Spacer(Modifier.size(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(kbd = "⏎", label = "send")
                Hint(kbd = "⇧⏎", label = "new line")
            }
            Spacer(Modifier.weight(1f))
            SendButton(
                isGenerating = isGenerating,
                canSend = canSend,
                onSend = onSend,
                onStop = onStop
            )
        }
    }
}

@Composable
private fun ToolPill(
    emoji: String,
    onClick: () -> Unit,
    active: Boolean,
    label: String? = null
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.mintSoft else colors.cardAlt)
            .border(
                if (active) 1.6.dp else 1.2.dp,
                if (active) colors.line else colors.lineSoft,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 13.sp)
        if (label != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) colors.ink else colors.inkMute
            )
        }
    }
}

@Composable
private fun Hint(kbd: String, label: String) {
    val colors = LocalPawPalColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(colors.paperDeep)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(text = kbd, style = MaterialTheme.typography.labelSmall, color = colors.inkMute, fontSize = 10.sp)
        }
        Spacer(Modifier.width(3.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.inkMute, fontSize = 10.sp)
    }
}

@Composable
private fun SendButton(
    isGenerating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val colors = LocalPawPalColors.current
    val background = when {
        isGenerating -> colors.rose
        canSend -> colors.coral
        else -> colors.cardAlt
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.8.dp, colors.line, CircleShape)
            .clickable(enabled = isGenerating || canSend) {
                if (isGenerating) onStop() else onSend()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isGenerating) {
            Box(
                Modifier
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.ink)
            )
        } else {
            Text(text = "🐾", fontSize = 20.sp)
        }
    }
}
