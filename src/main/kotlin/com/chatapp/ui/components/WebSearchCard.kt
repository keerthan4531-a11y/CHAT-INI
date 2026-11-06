package com.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.aiengine.model.WebCitation
import com.chatapp.ui.theme.*

/**
 * Component displaying live web search progress and retrieved external source cards.
 */
@Composable
fun WebSearchCard(
    isSearching: Boolean,
    citations: List<WebCitation>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (isSearching) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SearchBackground)
                    .border(1.dp, SearchBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = SearchAccent
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Searching live web sources...",
                    color = SearchAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (citations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sources (${citations.size})",
                color = TextSecondaryDark,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(citations) { citation ->
                    CitationChip(
                        citation = citation,
                        onClick = {
                            if (citation.url.isNotEmpty()) {
                                try {
                                    uriHandler.openUri(citation.url)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CitationChip(
    citation: WebCitation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevatedDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .widthIn(max = 220.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Language,
            contentDescription = null,
            tint = SearchAccent,
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = citation.title.ifEmpty { citation.url },
                color = TextPrimaryDark,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (citation.snippet.isNotEmpty()) {
                Text(
                    text = citation.snippet,
                    color = TextMutedDark,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = "Open link",
            tint = TextMutedDark,
            modifier = Modifier.size(12.dp)
        )
    }
}
