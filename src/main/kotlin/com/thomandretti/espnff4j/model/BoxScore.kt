package com.thomandretti.espnff4j.model

/**
 * A matchup with both lineups for the week. Each player's [RosterPlayer.points] covers this NFL week
 * only, so in a two-week playoff round the lineups show one week while [matchup] totals span both.
 */
data class BoxScore(
    val matchup: MatchupInfo,
    // Starters in lineup order, then bench and IR. Only starters count toward the score.
    val homeLineup: List<RosterPlayer>,
    val awayLineup: List<RosterPlayer>,
) {
    val homeStarters: List<RosterPlayer>
        get() = homeLineup.filter { it.isStarter }

    val awayStarters: List<RosterPlayer>
        get() = awayLineup.filter { it.isStarter }
}
