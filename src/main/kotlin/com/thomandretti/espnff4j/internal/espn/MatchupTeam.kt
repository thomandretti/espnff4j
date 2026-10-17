package com.thomandretti.espnff4j.internal.espn

internal data class MatchupTeam(
    val teamId: Int,
    // Stays 0.0 for the whole of a live week; ESPN only fills it in once the week is settled.
    val totalPoints: Double,
    // ESPN publishes these four only while a week is the current one. Once a week is settled it
    // drops them from the payload entirely and reports the result through Matchup.winner instead.
    val totalPointsLive: Double? = null,
    val totalProjectedPoints: Double? = null,
    val totalProjectedPointsLive: Double? = null,
    val winProbability: Double? = null,
)
