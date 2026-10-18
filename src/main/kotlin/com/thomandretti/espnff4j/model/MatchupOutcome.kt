package com.thomandretti.espnff4j.model

enum class MatchupOutcome {
    HOME,
    AWAY,
    UNDECIDED,
}

/** Which part of the season a matchup belongs to. */
enum class PlayoffTier {
    REGULAR_SEASON,

    // The championship bracket.
    WINNERS_BRACKET,

    // Placing games for teams knocked out of the championship bracket.
    WINNERS_CONSOLATION_LADDER,

    // Placing games for teams that missed the playoffs.
    LOSERS_CONSOLATION_LADDER,
    UNKNOWN,
}
