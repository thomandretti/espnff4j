package com.thomandretti.espnff4j.model

import java.time.Instant

data class Draft(
    val type: DraftType,
    // When the draft was scheduled to start, and when it finished; null if ESPN doesn't say.
    val scheduledAt: Instant?,
    val completedAt: Instant?,
    val isComplete: Boolean,
    // In pick order. Empty before the draft.
    val picks: List<DraftPick>,
)

data class DraftPick(
    val overallPick: Int,
    val round: Int,
    val roundPick: Int,
    val team: Team,
    // The player as ESPN reports them now, with their season points so far. Points and projection for a
    // week are null.
    val player: Player,
    val isKeeper: Boolean,
    // The winning bid in an auction draft; null in a snake draft.
    val auctionPrice: Int?,
)

enum class DraftType {
    SNAKE,
    AUCTION,
    UNKNOWN,
}
