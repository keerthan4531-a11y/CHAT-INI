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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.example.Mascot
import com.chatapp.ui.theme.LocalPawPalColors

/** Preferences: theme, mascot, motion, memory and data controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    darkTheme: Boolean?,
    mascot: Mascot,
    showAnimations: Boolean,
    onTheme: (Boolean?) -> Unit,
    onMascot: (Mascot) -> Unit,
    onToggleAnimations: () -> Unit,
    onClearChat: () -> Unit,
    onClearMemory: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPawPalColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Make it yours",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.ink
                )
                Text(
                    text = "PawPal is a doodle-first agent. Pick a mascot, a mode and how much motion you want.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMute
                )
            }

            item {
                WobblyCard(
                    seed = 81,
                    fill = colors.card,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeChip("Light", darkTheme == false) { onTheme(false) }
                        ThemeChip("Dark", darkTheme == true) { onTheme(true) }
                        ThemeChip("System", darkTheme == null) { onTheme(null) }
                    }
                }
            }

            item {
                WobblyCard(
                    seed = 82,
                    fill = colors.card,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "Mascot",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    Text(
                        text = "Mochi the cat does the thinking. Biscuit the dog is here for morale.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MascotChoice(
                            label = "Mochi",
                            emoji = "🐱",
                            selected = mascot == Mascot.MOCHI,
                            onClick = { onMascot(Mascot.MOCHI) }
                        )
                        MascotChoice(
                            label = "Biscuit",
                            emoji = "🐶",
                            selected = mascot == Mascot.BISCUIT,
                            onClick = { onMascot(Mascot.BISCUIT) }
                        )
                    }
                }
            }

            item {
                WobblyCard(
                    seed = 83,
                    fill = colors.card,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Animations",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            Text(
                                text = "Tail wags, blinks, typing paws and celebrations.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute
                            )
                        }
                        DoodleButton(
                            text = if (showAnimations) "On" else "Off",
                            onClick = onToggleAnimations,
                            background = if (showAnimations) colors.mint else colors.cardAlt,
                            seed = 84
                        )
                    }
                }
            }

            item {
                WobblyCard(
                    seed = 85,
                    fill = colors.cardAlt,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        DoodleButton(
                            text = "Clear chat",
                            emoji = "🧽",
                            onClick = onClearChat,
                            background = colors.card,
                            seed = 86
                        )
                        DoodleButton(
                            text = "Forget memory",
                            emoji = "🧶",
                            onClick = onClearMemory,
                            background = colors.card,
                            seed = 87
                        )
                    }
                    Text(
                        text = "MCP secrets and proxy passwords never leave this process. Chat history stays on-device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMute,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item {
                Text(
                    text = "PawPal 1.0 · Kotlin + Jetpack Compose · MCP host + agent runtime",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMute
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.butter else colors.cardAlt)
            .border(
                if (active) 1.6.dp else 1.2.dp,
                if (active) colors.line else colors.lineSoft,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.ink)
    }
}

@Composable
private fun MascotChoice(
    label: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.butterSoft else colors.cardAlt)
            .border(
                if (selected) 1.8.dp else 1.2.dp,
                if (selected) colors.line else colors.lineSoft,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.ink)
    }
}
