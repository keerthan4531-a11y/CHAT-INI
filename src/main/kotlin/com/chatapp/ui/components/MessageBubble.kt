package com.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.ui.theme.*

/**
 * Message bubble item rendering user queries and assistant streaming answers.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    isLiveStreaming: Boolean = false,
    liveThinkingText: String = "",
    isThinkingActive: Boolean = false,
    liveCitations: List<com.chatapp.aiengine.model.WebCitation> = emptyList(),
    isWebSearching: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Assistant Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AssistantAvatarColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 360.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                // User Message Bubble
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .background(UserBubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimaryDark,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                // 1. Thinking / Reasoning Card
                val thinkingToShow = if (isLiveStreaming) liveThinkingText else (message.reasoningContent ?: "")
                if (thinkingToShow.isNotEmpty() || isThinkingActive) {
                    ThinkingCard(
                        thinkingText = thinkingToShow,
                        isThinkingActive = isThinkingActive,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // 2. Web Search Card & Citations
                val citationsToShow = if (isLiveStreaming) liveCitations else message.citations
                if (isWebSearching || citationsToShow.isNotEmpty()) {
                    WebSearchCard(
                        isSearching = isWebSearching,
                        citations = citationsToShow,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // 3. Main Assistant Response Text
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        color = TextPrimaryDark,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )

                    // Action buttons (Copy)
                    if (!isLiveStreaming) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.content))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = TextMutedDark,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
