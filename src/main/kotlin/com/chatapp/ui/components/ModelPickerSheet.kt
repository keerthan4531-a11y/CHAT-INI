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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.chatapp.aiengine.model.AiModel
import com.chatapp.ui.theme.LocalPawPalColors

/**
 * Bottom sheet model picker. Each model card shows what it is good at, so the
 * choice is about the task, not just a logo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    availableModels: List<AiModel>,
    selectedModel: AiModel,
    onModelSelected: (AiModel) -> Unit,
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "Pick a brain",
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink
            )
            Text(
                text = "Mochi routes each model to the right cloud provider. Agent tools work with all of them.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMute
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(availableModels, key = { it.id }) { model ->
                    val selected = model.id == selectedModel.id
                    WobblyCard(
                        seed = model.id.hashCode(),
                        fill = if (selected) colors.mintSoft else colors.card,
                        contentPadding = PaddingValues(10.dp),
                        onClick = {
                            onModelSelected(model)
                            onDismiss()
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = modelEmoji(model), fontSize = 20.sp)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            if (selected) {
                                Spacer(Modifier.weight(1f))
                                Text(text = "✓", color = colors.success, fontSize = 15.sp)
                            }
                        }
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMute,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Row(Modifier.padding(top = 5.dp)) {
                            if (model.supportsReasoning) {
                                Chip(text = "reasoning", emoji = "🧠", background = colors.butterSoft, color = colors.inkSoft)
                                Spacer(Modifier.width(3.dp))
                            }
                            if (model.supportsTools) {
                                Chip(text = "tools", emoji = "🔧", background = colors.skySoft, color = colors.inkSoft)
                                Spacer(Modifier.width(3.dp))
                            }
                            Chip(
                                text = "${model.contextWindow / 1000}k",
                                background = colors.cardAlt,
                                color = colors.inkMute,
                                border = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun modelEmoji(model: AiModel): String = when {
    model.id.contains("grok", ignoreCase = true) -> "🐾"
    model.id.contains("gpt", ignoreCase = true) -> "🌤️"
    model.id.contains("gemini", ignoreCase = true) -> "⚡"
    model.id.contains("deepseek", ignoreCase = true) -> "🧠"
    model.id.contains("claude", ignoreCase = true) -> "🎀"
    model.id.contains("glm", ignoreCase = true) -> "🦊"
    else -> "✨"
}
