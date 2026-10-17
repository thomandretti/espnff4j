package com.thomandretti.espnff4j.model

data class LeagueResponse(
    val id: Int,
    val seasonId: Int,
    // The current NFL week.
    val scoringPeriodId: Int,
    val status: LeagueStatus,
    val settings: LeagueSettings,
)

data class LeagueStatus(
    val currentMatchupPeriod: Int,
    val firstScoringPeriod: Int,
    val finalScoringPeriod: Int,
    val isActive: Boolean = false,
)
