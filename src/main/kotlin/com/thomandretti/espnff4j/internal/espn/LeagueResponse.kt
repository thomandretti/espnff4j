package com.thomandretti.espnff4j.internal.espn

internal data class LeagueResponse(
    val id: Int,
    val seasonId: Int,
    // The current NFL week.
    val scoringPeriodId: Int,
    val status: LeagueStatus,
    val settings: LeagueSettings,
)

internal data class LeagueStatus(
    val currentMatchupPeriod: Int,
    val firstScoringPeriod: Int,
    val finalScoringPeriod: Int,
    val isActive: Boolean = false,
    // Earlier seasons of this league, oldest first.
    val previousSeasons: List<Int> = emptyList(),
)
