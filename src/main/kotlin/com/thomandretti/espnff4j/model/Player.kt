package com.thomandretti.espnff4j.model

import java.time.Instant

/** A player as ESPN reports them now, with points and projection for the requested week. */
data class Player(
    val id: Int,
    val name: String,
    val position: Position,
    // NFL team abbreviation, e.g. "KC"; "FA" for an NFL free agent. Null for a team id this library does
    // not know.
    val proTeam: String?,
    val injuryStatus: InjuryStatus,
    val status: PlayerStatus,
    // The fantasy team that rosters the player, null for a free agent or a player on waivers.
    val team: Team?,
    // When a player on waivers becomes a free agent.
    val waiverClearsAt: Instant?,
    // Share of all ESPN leagues, not just this one, that roster the player, from 0 to 100.
    val percentOwned: Double?,
    val points: Double?,
    val projectedPoints: Double?,
)

enum class PlayerStatus {
    FREE_AGENT,
    WAIVERS,
    ON_TEAM,
    UNKNOWN,
}
