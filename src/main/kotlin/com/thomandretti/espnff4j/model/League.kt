package com.thomandretti.espnff4j.model

data class League(
    val id: Int,
    val seasonId: Int,
    val name: String,
    val size: Int,
    // ESPN's scoring type, e.g. H2H_POINTS. Null if ESPN omits it.
    val scoringType: String?,
    // Weeks are NFL weeks (ESPN scoring periods), the unit getMatchupsForWeek and getStandingsForWeek take.
    val currentWeek: Int,
    val firstWeek: Int,
    val finalWeek: Int,
    val currentMatchupPeriod: Int,
    val regularSeasonMatchupPeriods: Int,
    val playoffTeamCount: Int,
    // Matchup period -> the weeks it spans; a multi-week playoff round lists more than one.
    val matchupPeriods: Map<Int, List<Int>>,
    val isActive: Boolean,
)
