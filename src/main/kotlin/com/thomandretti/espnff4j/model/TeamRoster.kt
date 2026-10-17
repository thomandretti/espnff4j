package com.thomandretti.espnff4j.model

data class TeamRoster(
    val team: Team,
    // Starters in lineup order (QB, RB, WR, TE, FLEX, ...), then bench and IR.
    val players: List<RosterPlayer>,
) {
    val starters: List<RosterPlayer>
        get() = players.filter { it.isStarter }
}

data class RosterPlayer(
    val id: Int,
    val name: String,
    val position: Position,
    // NFL team abbreviation, e.g. "KC"; "FA" for a free agent. Null if ESPN sends a team id this library
    // does not know.
    val proTeam: String?,
    val lineupSlot: LineupSlot,
    val eligibleSlots: List<LineupSlot>,
    val injuryStatus: InjuryStatus,
    // Fantasy points for the week under the league's scoring. Null until ESPN records any, i.e. before
    // the player's game starts, and for a bye.
    val points: Double?,
    // ESPN's projection for the week, null if it publishes none.
    val projectedPoints: Double?,
) {
    val isStarter: Boolean
        get() = lineupSlot.isStarter
}

enum class Position {
    QB,
    RB,
    WR,
    TE,
    K,
    DST,

    // Anything else, such as individual defensive players.
    UNKNOWN,
}

/** Where a player sits in the lineup. Declared in lineup order, which is how rosters are sorted. */
enum class LineupSlot(
    val isStarter: Boolean = true,
) {
    QB,
    RB,
    WR,
    TE,

    // RB/WR/TE
    FLEX,
    RB_WR,
    WR_TE,

    // Superflex: QB/RB/WR/TE
    OP,
    DST,
    K,
    P,
    DT,
    DE,
    LB,
    DL,
    CB,
    S,
    DB,

    // Any defensive player
    DP,

    // Head coach
    HC,
    BENCH(isStarter = false),
    IR(isStarter = false),
    UNKNOWN(isStarter = false),
}

enum class InjuryStatus {
    ACTIVE,
    QUESTIONABLE,
    DOUBTFUL,
    OUT,
    DAY_TO_DAY,
    INJURY_RESERVE,
    SUSPENSION,

    // A status ESPN sent that this library does not recognise.
    UNKNOWN,
}
