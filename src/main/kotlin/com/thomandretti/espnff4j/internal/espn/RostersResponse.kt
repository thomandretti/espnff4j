package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.InjuryStatus
import com.thomandretti.espnff4j.model.Position
import com.thomandretti.espnff4j.model.Record
import com.thomandretti.espnff4j.model.RosterPlayer
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.TeamRoster

// Selectors into PlayerStat: statSourceId 0 is what happened, 1 is ESPN's projection; statSplitTypeId 1 is a
// single week (0 and 2 are season totals and averages).
private const val STAT_SOURCE_ACTUAL = 0
private const val STAT_SOURCE_PROJECTED = 1
private const val STAT_SPLIT_WEEK = 1

internal data class RostersResponse(
    val teams: List<RosterTeam>,
)

internal data class RosterTeam(
    val id: Int,
    val name: String,
    val abbrev: String? = null,
    val record: Record,
    val playoffSeed: Int,
    val roster: Roster? = null,
) {
    fun toTeamRoster(
        seasonId: Int,
        week: Int,
    ): TeamRoster =
        TeamRoster(
            team = Team(id = id, name = name, record = record, playoffSeed = playoffSeed, abbrev = abbrev),
            players =
                roster
                    ?.entries
                    .orEmpty()
                    .map { it.toRosterPlayer(seasonId, week) }
                    // Starters in lineup order, then bench and IR; ESPN's own order is kept within a slot.
                    .sortedBy { it.lineupSlot.ordinal },
        )
}

internal data class Roster(
    val entries: List<RosterEntry> = emptyList(),
)

internal data class RosterEntry(
    val lineupSlotId: Int,
    val playerPoolEntry: PlayerPoolEntry,
) {
    fun toRosterPlayer(
        seasonId: Int,
        week: Int,
    ): RosterPlayer {
        val player = playerPoolEntry.player
        return RosterPlayer(
            id = player.id,
            name = player.fullName,
            position = POSITIONS[player.defaultPositionId] ?: Position.UNKNOWN,
            proTeam = PRO_TEAMS[player.proTeamId],
            lineupSlot = lineupSlot(lineupSlotId),
            eligibleSlots = player.eligibleSlots.map(::lineupSlot),
            injuryStatus =
                when (val status = player.injuryStatus) {
                    // ESPN sends null for D/ST, which cannot be injured.
                    null -> InjuryStatus.ACTIVE
                    else -> InjuryStatus.entries.firstOrNull { it.name == status } ?: InjuryStatus.UNKNOWN
                },
            points = player.weekTotal(seasonId, week, STAT_SOURCE_ACTUAL),
            projectedPoints = player.weekTotal(seasonId, week, STAT_SOURCE_PROJECTED),
        )
    }
}

internal data class PlayerPoolEntry(
    val player: Player,
)

internal data class Player(
    val id: Int,
    val fullName: String,
    val defaultPositionId: Int,
    val proTeamId: Int = 0,
    val injuryStatus: String? = null,
    val eligibleSlots: List<Int> = emptyList(),
    val stats: List<PlayerStat> = emptyList(),
) {
    fun weekTotal(
        seasonId: Int,
        week: Int,
        statSourceId: Int,
    ): Double? =
        stats
            .firstOrNull {
                it.seasonId == seasonId &&
                    it.scoringPeriodId == week &&
                    it.statSourceId == statSourceId &&
                    it.statSplitTypeId == STAT_SPLIT_WEEK
            }?.appliedTotal
}

internal data class PlayerStat(
    val seasonId: Int,
    val scoringPeriodId: Int,
    val statSourceId: Int,
    val statSplitTypeId: Int,
    // Fantasy points under the league's scoring settings.
    val appliedTotal: Double? = null,
)
