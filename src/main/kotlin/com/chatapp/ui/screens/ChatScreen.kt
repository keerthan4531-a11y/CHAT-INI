package com.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.example.ChatUiState
import com.chatapp.aiengine.example.ChatViewModelExample
import com.chatapp.aiengine.model.AiModel
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.ui.components.ChatInputBar
import com.chatapp.ui.components.MessageBubble
import com.chatapp.ui.components.ModelPickerSheet
import com.chatapp.ui.theme.*

/**
 * Main Chat Screen composable integrating reactive token streaming,
 * reasoning display, web search toggle, and model selector.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModelExample,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom as streaming tokens arrive in real-time
    LaunchedEffect(uiState.messages.size, uiState.liveContentText, uiState.liveThinkingText) {
        if (uiState.messages.isNotEmpty() || uiState.isGenerating) {
            val totalItems = uiState.messages.size + (if (uiState.isGenerating) 1 else 0)
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                selectedModel = uiState.selectedModel,
                onOpenModelPicker = { showModelPicker = true },
                onNewChat = {
                    viewModel.stopGeneration()
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                isGenerating = uiState.isGenerating,
                isWebSearchEnabled = uiState.isWebSearchEnabled,
                onToggleWebSearch = {
                    viewModel.toggleWebSearch(!uiState.isWebSearchEnabled)
                },
                onSend = {
                    val prompt = inputText
                    inputText = ""
                    viewModel.sendMessage(prompt)
                },
                onStop = {
                    viewModel.stopGeneration()
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                // Empty state with quick starter suggestions
                EmptyChatSuggestions(
                    onSelectPrompt = { prompt ->
                        viewModel.sendMessage(prompt)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(uiState.messages) { message ->
                        MessageBubble(message = message)
                    }

                    // Active live streaming assistant turn
                    if (uiState.isGenerating) {
                        item {
                            val liveMessage = ChatMessage(
                                role = ChatRole.ASSISTANT,
                                content = uiState.liveContentText,
                                reasoningContent = uiState.liveThinkingText.ifEmpty { null },
                                citations = uiState.liveCitations
                            )

                            MessageBubble(
                                message = liveMessage,
                                isLiveStreaming = true,
                                liveThinkingText = uiState.liveThinkingText,
                                isThinkingActive = uiState.isThinking,
                                liveCitations = uiState.liveCitations,
                                isWebSearching = uiState.isWebSearching
                            )
                        }
                    }
                }
            }

            // Error snackbar/banner
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorColor.copy(alpha = 0.9f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            availableModels = AiModel.DEFAULT_MODELS,
            selectedModel = uiState.selectedModel,
            onModelSelected = { model ->
                viewModel.selectModel(model)
            },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun TopAppBar(
    selectedModel: AiModel,
    onOpenModelPicker: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Model Selector Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenModelPicker)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedModel.displayName,
                color = TextPrimaryDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Switch model",
                tint = TextSecondaryDark,
                modifier = Modifier.size(16.dp)
            )
        }

        // New Chat Action
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, CircleShape)
                .clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Chat",
                tint = TextPrimaryDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyChatSuggestions(
    onSelectPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What would you like to explore?",
            color = TextPrimaryDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        val suggestions = listOf(
            "Explain quantum computing in simple terms",
            "Write a Kotlin coroutines flow streaming example",
            "Compare DeepSeek R1 reasoning vs GPT-4o",
            "Latest technological advancements in AI 2026"
        )

        for (prompt in suggestions) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                    .clickable { onSelectPrompt(prompt) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = prompt,
                    color = TextSecondaryDark,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}
