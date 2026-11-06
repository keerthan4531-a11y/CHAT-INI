package com.chatapp.aiengine.model

/**
 * Represents a reference or citation discovered during a web-search-augmented AI query.
 *
 * @property title The headline or page title of the citation source.
 * @property url The direct web link to the referenced article or site.
 * @property snippet Brief extract or summary snippet explaining why this source was used.
 */
data class WebCitation(
    val title: String,
    val url: String,
    val snippet: String = ""
)

