package com.thomandretti.espnff4j.model

enum class MatchupOutcome {
    HOME,
    AWAY,
    UNDECIDED,
    ;

    companion object {
        // ESPN sends HOME / AWAY / UNDECIDED; treat anything unrecognised as still in progress.
        fun parse(winner: String?): MatchupOutcome =
            entries.firstOrNull { it.name.equals(winner, ignoreCase = true) } ?: UNDECIDED
    }
}

internal data class Matchup(
    val matchupPeriodId: Int,
    val home: MatchupTeam,
    val away: MatchupTeam?,
    val winner: String? = null,
)
