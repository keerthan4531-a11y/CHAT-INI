package com.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.WebCitation
import com.chatapp.ui.theme.LocalPawPalColors

/**
 * Live web search progress + source chips. Sources are horizontally
 * scrollable so they never blow up the bubble width.
 */
@Composable
fun WebSearchCard(
    isSearching: Boolean,
    citations: List<WebCitation>,
    modifier: Modifier = Modifier
) {
    if (!isSearching && citations.isEmpty()) return
    val colors = LocalPawPalColors.current
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        if (isSearching) {
            WobblyCard(
                seed = 5,
                fill = colors.skySoft,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.ink
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "sniffing the live web…",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink
                    )
                }
            }
        }
        if (citations.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Sources (${citations.size})",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMute
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                citations.forEach { citation ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.card)
                            .border(1.2.dp, colors.lineSoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .clickable { runCatching { uriHandler.openUri(citation.url) } },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔗", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = citation.title.ifEmpty { citation.url },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = citation.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
