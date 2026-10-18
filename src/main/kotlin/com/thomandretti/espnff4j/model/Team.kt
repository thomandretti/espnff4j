package com.thomandretti.espnff4j.model

data class Team(
    val id: Int = 0,
    val name: String,
    val record: Record,
    val playoffSeed: Int,
    val abbrev: String? = null,
    // Season totals, rounded to two decimal places.
    val pointsFor: Double = 0.0,
    val pointsAgainst: Double = 0.0,
    // Null before the team has played.
    val streak: Streak? = null,
    val owners: List<Owner> = emptyList(),
    val divisionId: Int? = null,
    val logoUrl: String? = null,
    // Where the team finished once the season, playoffs included, is over: 1 is the champion. Null until then.
    val finalRank: Int? = null,
    // Position in the waiver order, 1 picking first. Null if ESPN didn't send it.
    val waiverRank: Int? = null,
    val transactionCounts: TransactionCounts? = null,
)

/** A team's roster moves so far this season, as ESPN counts them. */
data class TransactionCounts(
    // Players added from free agency or waivers, and players dropped.
    val acquisitions: Int,
    val drops: Int,
    val trades: Int,
    // Lineup moves into a starting slot.
    val movesToActive: Int,
    val movesToIr: Int,
    // FAAB spent on waiver claims; always 0 in a league without a budget (see League.waivers).
    val faabSpent: Int,
)

data class Streak(
    val type: StreakType,
    val length: Int,
)

enum class StreakType {
    WIN,
    LOSS,
    TIE,

    // A type ESPN added after this library was written.
    UNKNOWN,
}

/** A league member who owns or co-owns a team. */
data class Owner(
    // ESPN's account id (the SWID), stable across seasons and leagues.
    val id: String,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
)
