package com.chatapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.ReasoningAccent
import com.chatapp.ui.theme.ReasoningBackground
import com.chatapp.ui.theme.ReasoningBorder
import com.chatapp.ui.theme.TextSecondaryDark

/**
 * Expandable card showing AI reasoning / chain-of-thought tokens in real-time.
 */
@Composable
fun ThinkingCard(
    thinkingText: String,
    isThinkingActive: Boolean,
    durationMs: Long? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Pulsing animation for the active thinking indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ReasoningBackground)
            .border(1.dp, ReasoningBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Card Header (Clickable to collapse/expand)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = "Thinking",
                tint = ReasoningAccent,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            val headerTitle = when {
                isThinkingActive -> "Thinking..."
                durationMs != null -> "Thought for ${"%.1f".format(durationMs / 1000f)}s"
                else -> "Thinking Process"
            }

            Text(
                text = headerTitle,
                color = ReasoningAccent,
                fontSize = 13.sp
            )

            if (isThinkingActive) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ReasoningAccent.copy(alpha = alphaAnim))
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextSecondaryDark,
                modifier = Modifier.size(18.dp)
            )
        }

        // Expandable Thought Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ReasoningBorder)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = thinkingText.ifEmpty { "Analyzing prompt..." },
                    color = TextSecondaryDark,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
