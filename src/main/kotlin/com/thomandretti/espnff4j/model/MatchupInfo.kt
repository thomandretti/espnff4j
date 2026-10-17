package com.thomandretti.espnff4j.model

data class MatchupInfo(
    val homeTeam: Team,
    val homeScore: Double,
    val awayTeam: Team,
    val awayScore: Double,
    // Null once ESPN settles the week and stops publishing projections.
    val homeProjected: Double? = null,
    val awayProjected: Double? = null,
    // ESPN's own win probability for the home side, when it publishes one.
    val espnHomeWinProbability: Double? = null,
    val outcome: MatchupOutcome = MatchupOutcome.UNDECIDED,
)
