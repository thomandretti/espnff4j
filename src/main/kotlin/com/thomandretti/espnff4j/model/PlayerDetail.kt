package com.thomandretti.espnff4j.model

import java.time.Instant

/**
 * One player's season so far: who they are and where they stand now, their points week by week, and
 * ESPN's latest news on them. [player] has no week, so its [Player.points] and [Player.projectedPoints]
 * are null; read [weeks].
 */
data class PlayerDetail(
    val player: Player,
    // Every NFL week of the season, in order: past weeks with points, the rest with only a projection. On
    // a bye, or a week the player is ruled out, ESPN projects 0.0; a player who sat out also scores 0.0.
    val weeks: List<PlayerWeek>,
    // Newest first: injury updates, practice reports and game recaps.
    val news: List<PlayerNote>,
)

data class PlayerWeek(
    val week: Int,
    // Fantasy points under the league's scoring. Null until the player's game starts.
    val points: Double?,
    // ESPN's projection for the week, null if it publishes none.
    val projectedPoints: Double?,
)

/** A news blurb as ESPN shows it on the player card, e.g. "Hall (quadriceps) is out for Sunday's game". */
data class PlayerNote(
    val published: Instant?,
    val headline: String,
    // The analysis under the headline: what it means for the player's role and fantasy value.
    val story: String?,
    // Who wrote it; "Rotowire" for everything ESPN has sent so far.
    val source: String?,
)
