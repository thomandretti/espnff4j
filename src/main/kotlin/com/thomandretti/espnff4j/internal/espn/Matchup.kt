package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.MatchupOutcome
import com.thomandretti.espnff4j.model.PlayoffTier

internal data class Matchup(
    val matchupPeriodId: Int,
    val home: MatchupTeam,
    val away: MatchupTeam?,
    val winner: String? = null,
    // NONE in the regular season, else WINNERS_BRACKET, WINNERS_CONSOLATION_LADDER or LOSERS_CONSOLATION_LADDER.
    val playoffTierType: String? = null,
) {
    // ESPN sends HOME / AWAY / UNDECIDED; treat anything unrecognised as still in progress.
    val outcome: MatchupOutcome
        get() =
            MatchupOutcome.entries.firstOrNull { it.name.equals(winner, ignoreCase = true) }
                ?: MatchupOutcome.UNDECIDED

    val playoffTier: PlayoffTier
        get() =
            when (playoffTierType) {
                null, "NONE" -> PlayoffTier.REGULAR_SEASON
                else -> PlayoffTier.entries.firstOrNull { it.name == playoffTierType } ?: PlayoffTier.UNKNOWN
            }
}
