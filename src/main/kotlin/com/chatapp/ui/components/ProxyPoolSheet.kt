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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.RotationStrategy
import com.chatapp.aiengine.model.UsageSnapshot
import com.chatapp.ui.theme.LocalPawPalColors

/**
 * Proxy pool manager. This is the answer to "limit problem varakoodathu":
 * 50 messages per IP per day, and every healthy proxy lane adds 50 more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyPoolSheet(
    proxies: List<ProxyEndpoint>,
    usage: UsageSnapshot?,
    strategy: RotationStrategy,
    onStrategy: (RotationStrategy) -> Unit,
    onRotate: () -> Unit,
    onCheckHealth: () -> Unit = {},
    onAddProxy: (ProxyEndpoint) -> Unit,
    onRemoveProxy: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPawPalColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var region by remember { mutableStateOf("IN") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    val healthy = proxies.count { it.healthy }
    val capacity = (usage?.totalCapacity ?: (50 + healthy * 50))

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
                    text = "Limit booster",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.ink
                )
                Text(
                    text = "No global daily cap. Your IP gets 50 messages/day. Every healthy proxy adds another 50-message lane, and Mochi rotates automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMute
                )
            }

            item {
                WobblyCard(
                    seed = 61,
                    fill = colors.mintSoft,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "$capacity",
                                                style = MaterialTheme.typography.displayMedium,
                                color = colors.ink
                            )
                            Text(
                                text = "messages / day capacity",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${usage?.totalUsed ?: 0} used",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            Text(
                                text = "$healthy healthy lanes",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Meter(
                        progress = usage?.percent ?: 0f,
                        color = colors.coral,
                        height = 9.dp
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip(text = "🏠 IP: ${usage?.ipUsed ?: 0}/50", background = colors.card, color = colors.inkSoft)
                        Chip(
                            text = "🛰️ proxies: ${usage?.proxyUsed ?: 0}/${usage?.proxyLimit ?: healthy * 50}",
                            background = colors.card,
                            color = colors.inkSoft
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Rotation strategy",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        DoodleButton(
                            text = "Check",
                            emoji = "🩺",
                            onClick = onCheckHealth,
                            background = colors.card,
                            seed = 66
                        )
                        DoodleButton(
                            text = "Rotate now",
                            emoji = "↻",
                            onClick = onRotate,
                            background = colors.butter,
                            seed = 62
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StrategyChip("Least used", strategy == RotationStrategy.LEAST_USED) {
                        onStrategy(RotationStrategy.LEAST_USED)
                    }
                    StrategyChip("Round robin", strategy == RotationStrategy.ROUND_ROBIN) {
                        onStrategy(RotationStrategy.ROUND_ROBIN)
                    }
                    StrategyChip("Random", strategy == RotationStrategy.RANDOM) {
                        onStrategy(RotationStrategy.RANDOM)
                    }
                    StrategyChip("Fastest", strategy == RotationStrategy.FASTEST) {
                        onStrategy(RotationStrategy.FASTEST)
                    }
                }
            }

            items(proxies, key = { it.id }) { proxy ->
                val lane = usage?.lanes?.firstOrNull { it.id == proxy.id }
                WobblyCard(
                    seed = proxy.id.hashCode(),
                    fill = colors.card,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (proxy.healthy) colors.success else colors.danger)
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = proxy.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Spacer(Modifier.width(5.dp))
                                Chip(
                                    text = proxy.region,
                                    background = colors.cardAlt,
                                    color = colors.inkMute,
                                    border = false
                                )
                                if (proxy.simulated) {
                                    Spacer(Modifier.width(3.dp))
                                    Chip(
                                        text = "demo",
                                        background = colors.butterSoft,
                                        color = colors.inkMute,
                                        border = false
                                    )
                                }
                            }
                            Text(
                                text = "${proxy.address} · ${proxy.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                        Text(
                            text = "${lane?.used ?: proxy.usedToday}/${lane?.limit ?: 50}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSoft
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onRemoveProxy(proxy.id) }
                                .padding(4.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Meter(
                        progress = lane?.percent ?: 0f,
                        color = if (proxy.healthy) colors.mint else colors.rose,
                        height = 6.dp,
                        showPaw = false
                    )
                }
            }

            item {
                WobblyCard(
                    seed = 63,
                    fill = colors.cardAlt,
                    contentPadding = PaddingValues(11.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdd = !showAdd },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "➕", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add a proxy lane",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (showAdd) "close" else "open",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute
                        )
                    }
                    if (showAdd) {
                        Spacer(Modifier.height(7.dp))
                        OutlinedField("Label", label, { label = it })
                        OutlinedField("Host", host, { host = it })
                        OutlinedField("Port", port, { port = it })
                        OutlinedField("Region", region, { region = it })
                        OutlinedField("Username (optional)", username, { username = it })
                        OutlinedField("Password (optional)", password, { password = it })
                        DoodleButton(
                            text = "Add lane",
                            emoji = "🛰️",
                            onClick = {
                                val portNumber = port.toIntOrNull() ?: 8080
                                if (host.isNotBlank()) {
                                    onAddProxy(
                                        ProxyEndpoint(
                                            id = "custom-${System.currentTimeMillis()}",
                                            label = label.ifBlank { host },
                                            host = host,
                                            port = portNumber,
                                            username = username,
                                            password = password,
                                            region = region
                                        )
                                    )
                                    label = ""
                                    host = ""
                                    username = ""
                                    password = ""
                                    showAdd = false
                                }
                            },
                            background = colors.mint,
                            seed = 64
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Demo quota reset",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute,
                        modifier = Modifier.weight(1f)
                    )
                    DoodleButton(
                        text = "Reset lanes",
                        emoji = "🧹",
                        onClick = onReset,
                        background = colors.card,
                        seed = 65
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The Kotlin engine ships with a real rotating OkHttp client, proxy auth and per-lane counters. Add paid proxies for production scale.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
        }
    }
}

@Composable
private fun StrategyChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.butter else colors.card)
            .border(
                if (active) 1.6.dp else 1.2.dp,
                if (active) colors.line else colors.lineSoft,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.ink)
    }
}

@Composable
private fun OutlinedField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
}
