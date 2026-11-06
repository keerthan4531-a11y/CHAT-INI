package com.chatapp.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.McpServerConfig
import com.chatapp.aiengine.model.McpServerStatus
import com.chatapp.aiengine.model.ToolDefinition
import com.chatapp.ui.theme.LocalPawPalColors

/**
 * MCP connection manager. This is the "give Mochi hands" screen: connect
 * GitHub, email, Notion, files or a custom Streamable HTTP server, then the
 * agent can discover and call those tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSheet(
    servers: List<McpServerConfig>,
    statuses: List<McpServerStatus>,
    tools: List<ToolDefinition>,
    onConnect: (McpServerConfig) -> Unit,
    onDisconnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPawPalColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expanded by remember { mutableStateOf<String?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf("imap.gmail.com") }
    var smtpHost by remember { mutableStateOf("smtp.gmail.com") }

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
                    text = "Give Mochi hands",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.ink
                )
                Text(
                    text = "MCP servers expose tools over JSON-RPC 2.0. Connect one and the agent can use it on the next run. Secrets stay in memory only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMute
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip(text = "tools", emoji = "🔧", background = colors.mintSoft, color = colors.inkSoft)
                    Chip(text = "resources", emoji = "📄", background = colors.skySoft, color = colors.inkSoft)
                    Chip(text = "prompts", emoji = "💬", background = colors.lilacSoft, color = colors.inkSoft)
                }
            }

            items(servers, key = { it.id }) { server ->
                val status = statuses.firstOrNull { it.serverId == server.id }
                val connected = status?.connected == true || (server.enabled && status == null)
                val serverTools = tools.filter { it.serverId == server.id }
                WobblyCard(
                    seed = server.id.hashCode(),
                    fill = if (connected) colors.mintSoft else colors.card,
                    contentPadding = PaddingValues(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardAlt)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = server.emoji, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Spacer(Modifier.width(5.dp))
                                StatusPill(
                                    text = when {
                                        connected -> "connected"
                                        server.enabled -> "connecting"
                                        else -> "off"
                                    },
                                    color = when {
                                        connected -> colors.success
                                        server.enabled -> colors.coral
                                        else -> colors.inkMute
                                    }
                                )
                            }
                            Text(
                                text = server.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                        if (connected) {
                            DoodleButton(
                                text = "Unplug",
                                onClick = { onDisconnect(server.id) },
                                background = colors.card,
                                seed = 51
                            )
                        } else {
                            DoodleButton(
                                text = if (expanded == server.id) "Close" else "Connect",
                                emoji = "🔌",
                                onClick = {
                                    expanded = if (expanded == server.id) null else server.id
                                },
                                background = colors.butter,
                                seed = 52
                            )
                        }
                    }

                    if (serverTools.isNotEmpty()) {
                        Text(
                            text = "Tools: ${serverTools.joinToString(", ") { it.name }}",
                                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }

                    if (expanded == server.id) {
                        Spacer(Modifier.height(8.dp))
                        when {
                            server.id.startsWith("github") -> {
                                SecretField(
                                    label = "GitHub personal access token",
                                    value = token,
                                    onChange = { token = it },
                                    placeholder = "ghp_..."
                                )
                                DoodleButton(
                                    text = "Save & connect",
                                    emoji = "🐙",
                                    onClick = {
                                        onConnect(
                                            server.copy(
                                                enabled = true,
                                                env = mapOf("GITHUB_PERSONAL_ACCESS_TOKEN" to token),
                                                headers = if (server.transport == com.chatapp.aiengine.model.McpTransport.STREAMABLE_HTTP) {
                                                    mapOf("Authorization" to "Bearer $token")
                                                } else {
                                                    emptyMap()
                                                }
                                            )
                                        )
                                        expanded = null
                                    },
                                    background = colors.mint,
                                    seed = 53
                                )
                            }

                            server.id == "email-remote" -> {
                                SecretField(
                                    label = "Email MCP API key",
                                    value = apiKey,
                                    onChange = { apiKey = it },
                                    placeholder = "mcp_live_..."
                                )
                                DoodleButton(
                                    text = "Save & connect",
                                    emoji = "📧",
                                    onClick = {
                                        onConnect(
                                            server.copy(
                                                enabled = true,
                                                env = mapOf("MCP_API_KEY" to apiKey),
                                                headers = mapOf("Authorization" to "Bearer $apiKey")
                                            )
                                        )
                                        expanded = null
                                    },
                                    background = colors.mint,
                                    seed = 54
                                )
                            }

                            server.id == "email-local" -> {
                                SecretField(
                                    label = "Email address",
                                    value = email,
                                    onChange = { email = it },
                                    placeholder = "you@gmail.com"
                                )
                                SecretField(
                                    label = "App password",
                                    value = appPassword,
                                    onChange = { appPassword = it },
                                    placeholder = "16-char app password",
                                    password = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedField(
                                        label = "IMAP host",
                                        value = imapHost,
                                        onChange = { imapHost = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedField(
                                        label = "SMTP host",
                                        value = smtpHost,
                                        onChange = { smtpHost = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                DoodleButton(
                                    text = "Save & connect",
                                    emoji = "📧",
                                    onClick = {
                                        onConnect(
                                            server.copy(
                                                enabled = true,
                                                env = mapOf(
                                                    "EMAIL_ADDRESS" to email,
                                                    "EMAIL_APP_PASSWORD" to appPassword,
                                                    "IMAP_HOST" to imapHost,
                                                    "SMTP_HOST" to smtpHost
                                                )
                                            )
                                        )
                                        expanded = null
                                    },
                                    background = colors.mint,
                                    seed = 55
                                )
                            }

                            server.id == "notion" || server.id == "slack" -> {
                                SecretField(
                                    label = "Access token",
                                    value = token,
                                    onChange = { token = it },
                                    placeholder = "secret_..."
                                )
                                DoodleButton(
                                    text = "Save & connect",
                                    emoji = "🔌",
                                    onClick = {
                                        onConnect(
                                            server.copy(
                                                enabled = true,
                                                env = mapOf(
                                                    if (server.id == "notion") "NOTION_TOKEN" else "SLACK_BOT_TOKEN" to token
                                                ),
                                                headers = mapOf("Authorization" to "Bearer $token")
                                            )
                                        )
                                        expanded = null
                                    },
                                    background = colors.mint,
                                    seed = 56
                                )
                            }

                            else -> {
                                Text(
                                    text = "This server runs locally over stdio. It will start on the next agent run.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.inkMute
                                )
                                DoodleButton(
                                    text = "Enable server",
                                    emoji = "📂",
                                    onClick = {
                                        onConnect(server.copy(enabled = true))
                                        expanded = null
                                    },
                                    background = colors.mint,
                                    seed = 57
                                )
                            }
                        }
                    }
                }
            }

            item {
                WobblyCard(
                    seed = 77,
                    fill = colors.cardAlt,
                    contentPadding = PaddingValues(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "➕", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add a custom MCP server",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (showCustom) "close" else "open",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickableNoRipple { showCustom = !showCustom }
                                .padding(4.dp)
                        )
                    }
                    if (showCustom) {
                        Spacer(Modifier.height(7.dp))
                        OutlinedField(
                            label = "Server name",
                            value = customName,
                            onChange = { customName = it }
                        )
                        OutlinedField(
                            label = "Streamable HTTP URL",
                            value = customUrl,
                            onChange = { customUrl = it }
                        )
                        DoodleButton(
                            text = "Add server",
                            emoji = "🔌",
                            onClick = {
                                if (customName.isNotBlank() && customUrl.isNotBlank()) {
                                    onConnect(
                                        McpServerConfig(
                                            id = customName.lowercase().replace(Regex("\\s+"), "-"),
                                            name = customName,
                                            emoji = "🔌",
                                            description = "Custom MCP server",
                                            transport = com.chatapp.aiengine.model.McpTransport.STREAMABLE_HTTP,
                                            url = customUrl,
                                            enabled = true
                                        )
                                    )
                                    customName = ""
                                    customUrl = ""
                                    showCustom = false
                                }
                            },
                            background = colors.butter,
                            seed = 78
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Tip: keep one agent under ~12 tools. If you connect many servers, split them into a research agent and a coding agent.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

@Composable
private fun OutlinedField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
