package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.PlayerNote
import java.time.Instant

/** ESPN's fantasy news feed for one player: Rotowire's blurbs, newest first. */
internal data class PlayerNewsResponse(
    val feed: List<NewsItem> = emptyList(),
)

internal data class NewsItem(
    // ISO-8601, e.g. "2026-10-16T15:29:50Z".
    val published: String? = null,
    val headline: String? = null,
    val story: String? = null,
    // "Rotowire" for every item seen so far.
    val type: String? = null,
)

internal fun NewsItem.toPlayerNote(): PlayerNote? {
    val headline = headline ?: return null
    return PlayerNote(
        published = published?.let { runCatching { Instant.parse(it) }.getOrNull() },
        headline = headline,
        story = story,
        source = type,
    )
}
