package com.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatapp.aiengine.example.ChatViewModelExample
import com.chatapp.aiengine.example.Mascot
import com.chatapp.aiengine.example.SheetKind
import com.chatapp.aiengine.model.AgentMode
import com.chatapp.aiengine.model.AgentPlan
import com.chatapp.aiengine.model.ChatMessage
import com.chatapp.aiengine.model.ChatRole
import com.chatapp.aiengine.model.StepStatus
import com.chatapp.aiengine.model.ToolCall
import com.chatapp.ui.components.AgentPlanCard
import com.chatapp.ui.components.ApprovalCard
import com.chatapp.ui.components.ChatInputBar
import com.chatapp.ui.components.CatMood
import com.chatapp.ui.components.Chip
import com.chatapp.ui.components.DoodleArrow
import com.chatapp.ui.components.DoodleButton
import com.chatapp.ui.components.DoodleCat
import com.chatapp.ui.components.DoodleDog
import com.chatapp.ui.components.DoodleIconButton
import com.chatapp.ui.components.MascotAvatar
import com.chatapp.ui.components.MascotKind
import com.chatapp.ui.components.McpSheet
import com.chatapp.ui.components.MessageBubble
import com.chatapp.ui.components.Meter
import com.chatapp.ui.components.ModelPickerSheet
import com.chatapp.ui.components.ProxyPoolSheet
import com.chatapp.ui.components.SettingsSheet
import com.chatapp.ui.components.SquiggleDivider
import com.chatapp.ui.components.StickyNote
import com.chatapp.ui.components.ToggleChip
import com.chatapp.ui.components.ToolCallCard
import com.chatapp.ui.components.UsageSheet
import com.chatapp.ui.components.WobblyCard
import com.chatapp.ui.theme.HandSmallStyle
import com.chatapp.ui.theme.LocalPawPalColors
import com.chatapp.ui.theme.PawPalTheme
import kotlinx.coroutines.launch

/**
 * The whole PawPal app: navigation drawer, doodle top bar, chat surface,
 * composer, approval banner and every bottom sheet.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModelExample = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = uiState.darkTheme ?: isSystemInDarkTheme()
    PawPalTheme(darkTheme = darkTheme) {
        ChatScreenContent(uiState = uiState, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    uiState: com.chatapp.aiengine.example.ChatUiState,
    viewModel: ChatViewModelExample
) {
    val colors = LocalPawPalColors.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val width = LocalConfiguration.current.screenWidthDp

    LaunchedEffect(uiState.messages.size, uiState.liveContentText, uiState.isGenerating) {
        if ((uiState.messages.isNotEmpty() || uiState.isGenerating) && listState.itemCount > 0) {
            listState.animateScrollToItem(listState.itemCount - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.card,
                drawerContentColor = colors.ink,
                modifier = Modifier.width(310.dp)
            ) {
                SidebarContent(
                    uiState = uiState,
                    onNewChat = {
                        viewModel.clearChat()
                        scope.launch { drawerState.close() }
                    },
                    onOpenSheet = { sheet ->
                        viewModel.openSheet(sheet)
                        scope.launch { drawerState.close() }
                    },
                    onOpenAgent = {
                        viewModel.toggleAgentPanel()
                        scope.launch { drawerState.close() }
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = colors.paper,
            topBar = {
                TopBar(
                    uiState = uiState,
                    width = width,
                    onMenu = { scope.launch { drawerState.open() } },
                    onModel = { viewModel.openSheet(SheetKind.MODEL) },
                    onMode = { viewModel.setMode(it) },
                    onToggleSearch = { viewModel.toggleWebSearch(!uiState.isWebSearchEnabled) },
                    onToggleThink = { viewModel.toggleThink(!uiState.isThinkEnabled) },
                    onUsage = { viewModel.openSheet(SheetKind.USAGE) },
                    onTheme = { viewModel.toggleTheme() },
                    onAgentPanel = { viewModel.toggleAgentPanel() }
                )
            },
            bottomBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 10.dp)
                ) {
                    if (uiState.pendingApproval != null) {
                        ApprovalCard(
                            call = uiState.pendingApproval!!,
                            onApprove = { viewModel.approveTool(uiState.pendingApproval!!) },
                            onReject = { viewModel.rejectTool(uiState.pendingApproval!!) },
                            modifier = Modifier.padding(bottom = 7.dp)
                        )
                    }
                    if (uiState.messages.isEmpty()) {
                        DoodleArrow(
                            text = "ask me anything!",
                            modifier = Modifier.padding(start = 12.dp, bottom = 1.dp)
                        )
                    }
                    ChatInputBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        isGenerating = uiState.isGenerating,
                        isWebSearchEnabled = uiState.isWebSearchEnabled,
                        isAgentMode = uiState.mode == AgentMode.AGENT,
                        isDeepMode = uiState.isDeepMode,
                        onToggleWebSearch = { viewModel.toggleWebSearch(!uiState.isWebSearchEnabled) },
                        onToggleDeep = { viewModel.toggleDeepMode() },
                        onToggleAgent = {
                            viewModel.setMode(
                                if (uiState.mode == AgentMode.AGENT) AgentMode.CHAT else AgentMode.AGENT
                            )
                        },
                        onSend = {
                            val prompt = inputText
                            inputText = ""
                            viewModel.sendMessage(prompt)
                        },
                        onStop = { viewModel.stopGeneration() }
                    )
                    Text(
                        text = "Demo build · add your API key in AiEngineConfig. " +
                            "Mochi can make mistakes — double-check important answers. 🐾",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(colors.paper)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyState(
                        uiState = uiState,
                        onPrompt = { prompt ->
                            viewModel.setMode(AgentMode.AGENT)
                            viewModel.sendMessage(prompt)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        item {
                            DayDivider()
                        }
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                mascot = if (uiState.mascot == Mascot.MOCHI) MascotKind.CAT else MascotKind.DOG,
                                assistantName = uiState.assistantName,
                                onRegenerate = { viewModel.regenerate() }
                            )
                        }
                        if (uiState.isGenerating) {
                            item(key = "live") {
                                val live = ChatMessage(
                                    id = "live",
                                    role = ChatRole.ASSISTANT,
                                    content = uiState.liveContentText,
                                    reasoningContent = uiState.liveThinkingText.ifEmpty { null },
                                    citations = uiState.liveCitations,
                                    agentPlan = uiState.livePlan,
                                    toolCalls = uiState.liveToolCalls,
                                    modelName = uiState.selectedModel.displayName
                                )
                                MessageBubble(
                                    message = live,
                                    mascot = if (uiState.mascot == Mascot.MOCHI) MascotKind.CAT else MascotKind.DOG,
                                    assistantName = uiState.assistantName,
                                    isLiveStreaming = true,
                                    liveThinkingText = uiState.liveThinkingText,
                                    isThinkingActive = uiState.isThinking,
                                    livePlan = uiState.livePlan,
                                    liveToolCalls = uiState.liveToolCalls,
                                    liveCitations = uiState.liveCitations,
                                    isWebSearching = uiState.isWebSearching
                                )
                            }
                        }
                        item { Spacer(Modifier.height(18.dp)) }
                    }
                }

                if (uiState.errorMessage != null) {
                    ErrorBanner(
                        message = uiState.errorMessage!!,
                        onDismiss = { viewModel.dismissError() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    when (uiState.sheet) {
        SheetKind.MODEL -> ModelPickerSheet(
            availableModels = com.chatapp.aiengine.model.AiModel.DEFAULT_MODELS,
            selectedModel = uiState.selectedModel,
            onModelSelected = { viewModel.selectModel(it) },
            onDismiss = { viewModel.closeSheet() }
        )

        SheetKind.MCP -> McpSheet(
            servers = uiState.mcpServers,
            statuses = uiState.mcpStatuses,
            tools = uiState.tools,
            onConnect = { viewModel.connectMcp(it) },
            onDisconnect = { viewModel.disconnectMcp(it) },
            onDismiss = { viewModel.closeSheet() }
        )

        SheetKind.PROXY -> ProxyPoolSheet(
            proxies = uiState.proxies,
            usage = uiState.usage,
            strategy = uiState.rotationStrategy,
            onStrategy = { viewModel.setRotationStrategy(it) },
            onRotate = { viewModel.rotateNow() },
            onCheckHealth = { viewModel.checkProxyHealth() },
            onAddProxy = { viewModel.addProxy(it) },
            onRemoveProxy = { viewModel.removeProxy(it) },
            onReset = { viewModel.resetUsage() },
            onDismiss = { viewModel.closeSheet() }
        )

        SheetKind.USAGE -> UsageSheet(
            usage = uiState.usage,
            onReset = { viewModel.resetUsage() },
            onDismiss = { viewModel.closeSheet() }
        )

        SheetKind.SETTINGS -> SettingsSheet(
            darkTheme = uiState.darkTheme,
            mascot = uiState.mascot,
            showAnimations = uiState.showAnimations,
            onTheme = { viewModel.setDarkTheme(it) },
            onMascot = { viewModel.setMascot(it) },
            onToggleAnimations = { viewModel.toggleAnimations() },
            onClearChat = { viewModel.clearChat() },
            onClearMemory = { viewModel.clearMemory() },
            onDismiss = { viewModel.closeSheet() }
        )

        SheetKind.NONE -> Unit
    }

    if (uiState.isAgentPanelOpen) {
        AgentPanelSheet(
            uiState = uiState,
            onDismiss = { viewModel.closeAgentPanel() }
        )
    }
}

@Composable
private fun TopBar(
    uiState: com.chatapp.aiengine.example.ChatUiState,
    width: Int,
    onMenu: () -> Unit,
    onModel: () -> Unit,
    onMode: (AgentMode) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleThink: () -> Unit,
    onUsage: () -> Unit,
    onTheme: () -> Unit,
    onAgentPanel: () -> Unit
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DoodleIconButton(emoji = "☰", onClick = onMenu, size = 38.dp)
        Spacer(Modifier.width(6.dp))
        WobblyCard(
            seed = 4,
            fill = colors.card,
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
            onClick = onModel,
            modifier = Modifier.widthIn(max = if (width < 420) 132.dp else 190.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        uiState.selectedModel.id.contains("grok") -> "🐾"
                        uiState.selectedModel.id.contains("gpt") -> "🌤️"
                        uiState.selectedModel.id.contains("gemini") -> "⚡"
                        uiState.selectedModel.id.contains("deepseek") -> "🧠"
                        uiState.selectedModel.id.contains("claude") -> "🎀"
                        else -> "✨"
                    },
                    fontSize = 15.sp
                )
                Spacer(Modifier.width(5.dp))
                Column {
                    Text(
                        text = uiState.selectedModel.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        maxLines = 1
                    )
                    if (width >= 520) {
                        Text(
                            text = if (uiState.mode == AgentMode.AGENT) "agent · tools ready" else "chat · fast",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute,
                            maxLines = 1
                        )
                    }
                }
                Text(text = "▾", style = MaterialTheme.typography.labelSmall, color = colors.inkMute)
            }
        }

        Spacer(Modifier.width(6.dp))

        if (width >= 560) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.paperDeep)
                    .border(1.4.dp, colors.lineSoft, RoundedCornerShape(50))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeButton("💬 Chat", uiState.mode == AgentMode.CHAT) { onMode(AgentMode.CHAT) }
                ModeButton("🐾 Agent", uiState.mode == AgentMode.AGENT) { onMode(AgentMode.AGENT) }
            }
        } else {
            DoodleIconButton(
                emoji = if (uiState.mode == AgentMode.AGENT) "🐾" else "💬",
                onClick = {
                    onMode(if (uiState.mode == AgentMode.AGENT) AgentMode.CHAT else AgentMode.AGENT)
                },
                size = 34.dp,
                active = uiState.mode == AgentMode.AGENT
            )
        }

        Spacer(Modifier.weight(1f))

        if (width >= 620) {
            ToggleChip(
                label = "Think",
                emoji = "🧠",
                active = uiState.isThinkEnabled,
                onClick = onToggleThink
            )
            Spacer(Modifier.width(4.dp))
        } else {
            DoodleIconButton(
                emoji = "🧠",
                onClick = onToggleThink,
                size = 34.dp,
                active = uiState.isThinkEnabled
            )
            Spacer(Modifier.width(4.dp))
        }

        if (width >= 700) {
            ToggleChip(
                label = "Search",
                emoji = "🌐",
                active = uiState.isWebSearchEnabled,
                onClick = onToggleSearch
            )
            Spacer(Modifier.width(4.dp))
        } else {
            DoodleIconButton(
                emoji = "🌐",
                onClick = onToggleSearch,
                size = 34.dp,
                active = uiState.isWebSearchEnabled
            )
            Spacer(Modifier.width(4.dp))
        }

        UsagePill(
            used = uiState.messagesUsed,
            capacity = uiState.messageLimit,
            percent = uiState.usagePercent,
            onClick = onUsage,
            compact = width < 620
        )
        Spacer(Modifier.width(4.dp))
        if (width >= 430) {
            DoodleIconButton(
                emoji = if (uiState.darkTheme == false) "☀️" else "🌙",
                onClick = onTheme,
                size = 34.dp
            )
            Spacer(Modifier.width(4.dp))
        }
        if (width >= 620) {
            DoodleIconButton(emoji = "🐾", onClick = onAgentPanel, size = 34.dp)
        }
    }
}

@Composable
private fun ModeButton(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.card else Color.Transparent)
            .border(
                if (active) 1.4.dp else 0.dp,
                if (active) colors.line else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.ink)
    }
}

@Composable
private fun UsagePill(
    used: Int,
    capacity: Int,
    percent: Float,
    onClick: () -> Unit,
    compact: Boolean
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.card)
            .border(1.4.dp, colors.lineSoft, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Meter(progress = percent, color = colors.coral, height = 5.dp, showPaw = false)
        }
        if (!compact) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$used/$capacity",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSoft
            )
        } else {
            Spacer(Modifier.width(3.dp))
            Text(text = "$used", style = MaterialTheme.typography.labelSmall, color = colors.inkSoft)
        }
    }
}

@Composable
private fun SidebarContent(
    uiState: com.chatapp.aiengine.example.ChatUiState,
    onNewChat: () -> Unit,
    onOpenSheet: (SheetKind) -> Unit,
    onOpenAgent: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalPawPalColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MascotAvatar(
                kind = if (uiState.mascot == Mascot.MOCHI) MascotKind.CAT else MascotKind.DOG,
                size = 46.dp
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "PawPal ✦",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.ink
                )
                Text(
                    text = "a tiny agent with whiskers",
                    style = HandSmallStyle,
                    color = colors.inkMute
                )
            }
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkMute,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(5.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        DoodleButton(
            text = "New chat",
            emoji = "＋",
            onClick = onNewChat,
            background = colors.coral,
            seed = 91
        )

        Spacer(Modifier.height(12.dp))
        NavItem("💬", "Chats", active = true, onClick = onClose)
        NavItem("🐾", "Agent lab", active = false, onClick = onOpenAgent)
        NavItem("🔌", "MCP servers", active = false, onClick = { onOpenSheet(SheetKind.MCP) })
        NavItem("🛰️", "Proxy pool", active = false, onClick = { onOpenSheet(SheetKind.PROXY) })
        NavItem("📊", "Usage", active = false, onClick = { onOpenSheet(SheetKind.USAGE) })

        Spacer(Modifier.height(12.dp))
        SquiggleDivider()
        Spacer(Modifier.height(8.dp))

        WobblyCard(
            seed = 92,
            fill = colors.cardAlt,
            contentPadding = PaddingValues(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🗂️", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Today's chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink
                    )
                    Text(
                        text = "${uiState.messages.size} messages · ${uiState.assistantName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute
                    )
                }
                Text(text = "›", style = MaterialTheme.typography.labelMedium, color = colors.inkMute)
            }
        }

        Spacer(Modifier.height(10.dp))
        WobblyCard(
            seed = 93,
            fill = colors.mintSoft,
            contentPadding = PaddingValues(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Daily lanes",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${uiState.messagesUsed}/${uiState.messageLimit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkSoft
                )
            }
            Spacer(Modifier.height(4.dp))
            Meter(progress = uiState.usagePercent, color = colors.coral, height = 7.dp)
            Text(
                text = "50 / IP / day + proxy lanes",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMute,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = { onOpenSheet(SheetKind.SETTINGS) })
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MascotAvatar(
                kind = if (uiState.mascot == Mascot.MOCHI) MascotKind.CAT else MascotKind.DOG,
                size = 34.dp
            )
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(text = "You", style = MaterialTheme.typography.bodySmall, color = colors.ink)
                Text(
                    text = "Free plan · 50 msgs / IP",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
            Text(text = "⚙️", fontSize = 14.sp)
        }
    }
}

@Composable
private fun NavItem(
    emoji: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) colors.mintSoft else Color.Transparent)
            .border(
                if (active) 1.4.dp else 0.dp,
                if (active) colors.lineSoft else Color.Transparent,
                RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 15.sp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyState(
    uiState: com.chatapp.aiengine.example.ChatUiState,
    onPrompt: (String) -> Unit
) {
    val colors = LocalPawPalColors.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        val compact = maxWidth < 380.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = if (compact) 8.dp else 18.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(if (compact) 150.dp else 196.dp),
                contentAlignment = Alignment.Center
            ) {
                DoodleCat(
                    mood = if (uiState.isGenerating) CatMood.THINKING else CatMood.IDLE,
                    size = if (compact) 150.dp else 196.dp,
                    animate = uiState.showAnimations
                )
            }
            Text(
                text = "Hi, I'm ${uiState.assistantName}!",
                style = MaterialTheme.typography.displayMedium,
                color = colors.ink
            )
            Text(
                text = "your tiny agent with whiskers 🐾",
                style = HandSmallStyle,
                color = colors.inkMute
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Chip(text = "MCP tools", emoji = "🔌", background = colors.mintSoft, color = colors.inkSoft)
                Chip(text = "agent mode", emoji = "🐾", background = colors.butterSoft, color = colors.inkSoft)
                Chip(text = "proxy lanes", emoji = "🛰️", background = colors.skySoft, color = colors.inkSoft)
            }

            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StickyNote(
                        text = "Research the cutest cat UI trends",
                        emoji = "🐱",
                        background = colors.butterSoft,
                        onClick = { onPrompt("Research the cutest cat UI trends for a doodle-style Android app and give me a short design brief.") },
                        modifier = Modifier.weight(1f),
                        rotation = -1.6f,
                        seed = 101
                    )
                    StickyNote(
                        text = "Summarize my GitHub issues",
                        emoji = "🐙",
                        background = colors.mintSoft,
                        onClick = { onPrompt("Search GitHub for the most-starred open-source Android UI libraries and summarize what makes them good.") },
                        modifier = Modifier.weight(1f),
                        rotation = 1.2f,
                        seed = 102
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StickyNote(
                        text = "Draft an email to my team",
                        emoji = "📧",
                        background = colors.skySoft,
                        onClick = { onPrompt("Draft a friendly email to my team summarizing this week's progress and asking for feedback.") },
                        modifier = Modifier.weight(1f),
                        rotation = 1.4f,
                        seed = 103
                    )
                    StickyNote(
                        text = "Explain this Kotlin code",
                        emoji = "🧩",
                        background = colors.lilacSoft,
                        onClick = { onPrompt("Explain how a Kotlin coroutine Flow streams tokens from an OkHttp SSE response, with a small example.") },
                        modifier = Modifier.weight(1f),
                        rotation = -1.1f,
                        seed = 104
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            DoodleButton(
                text = "Try an agent run",
                emoji = "🐾",
                onClick = {
                    onPrompt("Research the best practices for cute doodle UI design in 2026, then draft a short email to my team with the key takeaways.")
                },
                background = colors.coral,
                seed = 105
            )
            Text(
                text = "Mochi plans, uses tools, and shows her work.",
                style = HandSmallStyle,
                color = colors.inkMute,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Biscuit peeks in from the corner (or Mochi, if Biscuit is the mascot).
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp, bottom = 2.dp)
                .size(96.dp)
        ) {
            if (uiState.mascot == Mascot.MOCHI) {
                DoodleDog(size = 96.dp, animate = uiState.showAnimations)
            } else {
                DoodleCat(mood = CatMood.HAPPY, size = 96.dp, animate = uiState.showAnimations)
            }
        }
    }
}

@Composable
private fun DayDivider() {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.lineSoft))
        Text(
            text = "today",
            style = HandSmallStyle,
            color = colors.inkMute,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.lineSoft))
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    WobblyCard(
        modifier = modifier,
        seed = 111,
        fill = colors.roseSoft,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🙀", fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkMute,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPanelSheet(
    uiState: com.chatapp.aiengine.example.ChatUiState,
    onDismiss: () -> Unit
) {
    val colors = LocalPawPalColors.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text(
                text = "Agent lab",
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink
            )
            Text(
                text = "Live plan, tool calls, budget and memory.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMute
            )
            Spacer(Modifier.height(8.dp))
            if (uiState.livePlan != null) {
                AgentPlanCard(plan = uiState.livePlan!!, live = true)
            } else {
                WobblyCard(
                    seed = 112,
                    fill = colors.cardAlt,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🐾", fontSize = 18.sp)
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(
                                text = "No active plan",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            Text(
                                text = "Switch to Agent mode and give Mochi a goal. She'll plan, call tools and show every step here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMute
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (uiState.liveToolCalls.isNotEmpty()) {
                Text(
                    text = "Tool calls",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                uiState.liveToolCalls.forEach { call: ToolCall ->
                    ToolCallCard(call = call, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            WobblyCard(
                seed = 113,
                fill = colors.card,
                contentPadding = PaddingValues(11.dp)
            ) {
                Text(
                    text = "Run budget",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                val steps = uiState.livePlan?.steps?.count { it.status == StepStatus.DONE } ?: 0
                Text(
                    text = "Steps: $steps / ${uiState.livePlan?.maxSteps ?: 8}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
                Meter(
                    progress = if ((uiState.livePlan?.maxSteps ?: 8) == 0) 0f else steps.toFloat() / (uiState.livePlan?.maxSteps ?: 8),
                    color = colors.coral,
                    height = 6.dp,
                    showPaw = false
                )
                Text(
                    text = "Tool calls: ${uiState.liveToolCalls.size} / 12",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    text = "Writes pause for your approval. Caps stop runaway loops.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
            Spacer(Modifier.height(8.dp))
            WobblyCard(
                seed = 114,
                fill = colors.butterSoft,
                contentPadding = PaddingValues(11.dp)
            ) {
                Text(
                    text = "Mochi's memory",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                if (uiState.memoryFacts.isEmpty()) {
                    Text(
                        text = "Nothing saved yet. Facts she learns will show up here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute
                    )
                } else {
                    uiState.memoryFacts.take(6).forEach { fact ->
                        Text(
                            text = "• ${fact.text}",
                                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSoft
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
