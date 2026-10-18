package com.thomandretti.espnff4j.model

import java.time.DayOfWeek

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
    // Earlier seasons of this league, oldest first. Each is readable with a client for that seasonId.
    val previousSeasons: List<Int> = emptyList(),
    // Null if ESPN omits the league's acquisition settings.
    val waivers: WaiverSettings? = null,
)

/** How players are claimed off waivers. */
data class WaiverSettings(
    // Whether claims are FAAB bids rather than by waiver order.
    val usesFaab: Boolean,
    // Each team's season budget and the smallest bid; null without FAAB. Team.transactionCounts has what
    // each team has spent.
    val faabBudget: Int?,
    val minimumBid: Int?,
    // Season limit on pickups, null if there is none.
    val acquisitionLimit: Int?,
    // How long a dropped player stays on waivers.
    val waiverHours: Int,
    // The days ESPN processes claims, Monday first.
    val processDays: List<DayOfWeek>,
)
