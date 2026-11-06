package com.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.*

/**
 * Modern floating input bar with Web Search toggle, auto-expanding text input,
 * and dynamic Send / Stop generation button.
 */
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    isWebSearchEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Web Search Toggle Pill
        Box(
            modifier = Modifier
                .padding(bottom = 2.dp)
                .clip(CircleShape)
                .background(if (isWebSearchEnabled) SearchBackground else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (isWebSearchEnabled) SearchAccent else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(onClick = onToggleWebSearch)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = "Toggle Web Search",
                tint = if (isWebSearchEnabled) SearchAccent else TextMutedDark,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Auto-expanding Input TextField
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            if (inputText.isEmpty()) {
                Text(
                    text = if (isWebSearchEnabled) "Ask with web search..." else "Message AI...",
                    color = TextMutedDark,
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                textStyle = TextStyle(
                    color = TextPrimaryDark,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(PrimaryAccent),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Send / Stop Action Button
        val canSend = inputText.isNotBlank() && !isGenerating
        val buttonBackground = when {
            isGenerating -> ErrorColor
            canSend -> PrimaryAccent
            else -> SurfaceElevatedDark
        }

        Box(
            modifier = Modifier
                .padding(bottom = 2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(buttonBackground)
                .clickable(
                    enabled = isGenerating || canSend,
                    onClick = {
                        if (isGenerating) onStop() else onSend()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                contentDescription = if (isGenerating) "Stop generating" else "Send message",
                tint = if (canSend || isGenerating) Color.White else TextMutedDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
