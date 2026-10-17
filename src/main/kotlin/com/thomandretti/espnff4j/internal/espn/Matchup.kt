package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.MatchupOutcome

internal data class Matchup(
    val matchupPeriodId: Int,
    val home: MatchupTeam,
    val away: MatchupTeam?,
    val winner: String? = null,
) {
    // ESPN sends HOME / AWAY / UNDECIDED; treat anything unrecognised as still in progress.
    val outcome: MatchupOutcome
        get() =
            MatchupOutcome.entries.firstOrNull { it.name.equals(winner, ignoreCase = true) }
                ?: MatchupOutcome.UNDECIDED
}
