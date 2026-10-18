package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.InjuryStatus
import com.thomandretti.espnff4j.model.Position
import com.thomandretti.espnff4j.model.RosterPlayer
import com.thomandretti.espnff4j.model.TeamRoster

// Selectors into PlayerStat: statSourceId 0 is what happened, 1 is ESPN's projection; statSplitTypeId 1 is a
// single week (0 and 2 are season totals and averages).
private const val STAT_SOURCE_ACTUAL = 0
private const val STAT_SOURCE_PROJECTED = 1
private const val STAT_SPLIT_WEEK = 1
private const val STAT_SPLIT_SEASON = 0

internal data class RostersResponse(
    val teams: List<EspnTeam>,
    val members: List<Member> = emptyList(),
) {
    fun toTeamRosters(
        seasonId: Int,
        week: Int,
    ): List<TeamRoster> =
        teams.toTeams(members).zip(teams) { team, espnTeam ->
            TeamRoster(team = team, players = espnTeam.roster.toPlayers(seasonId, week))
        }
}

internal data class Roster(
    val entries: List<RosterEntry> = emptyList(),
)

/**
 * Starters in lineup order, then bench and IR; ESPN's own order is kept within a slot. Entries with no
 * player identity are skipped: `mMatchupScore` without `mScoreboard` sends lineups that way.
 */
internal fun Roster?.toPlayers(
    seasonId: Int,
    week: Int,
): List<RosterPlayer> =
    this
        ?.entries
        .orEmpty()
        .mapNotNull { it.toRosterPlayer(seasonId, week) }
        .sortedBy { it.lineupSlot.ordinal }

internal data class RosterEntry(
    val lineupSlotId: Int,
    val playerPoolEntry: PlayerPoolEntry,
) {
    fun toRosterPlayer(
        seasonId: Int,
        week: Int,
    ): RosterPlayer? {
        val player = playerPoolEntry.player
        val name = player.fullName ?: return null
        return RosterPlayer(
            id = player.id,
            name = name,
            position = player.position(),
            proTeam = player.proTeam(),
            lineupSlot = lineupSlot(lineupSlotId),
            eligibleSlots = player.eligibleSlots.map(::lineupSlot),
            injuryStatus = player.injury(),
            points = player.weekPoints(seasonId, week),
            projectedPoints = player.weekProjection(seasonId, week),
        )
    }
}

internal data class PlayerPoolEntry(
    val player: EspnPlayer,
)

/** A player as ESPN sends one in rosters, lineups and player searches. */
internal data class EspnPlayer(
    // id, fullName and defaultPositionId are absent from the anonymous lineup entries mMatchupScore sends
    // without mScoreboard.
    val id: Int = 0,
    val fullName: String? = null,
    val defaultPositionId: Int = 0,
    val proTeamId: Int = 0,
    val injuryStatus: String? = null,
    val eligibleSlots: List<Int> = emptyList(),
    val stats: List<PlayerStat> = emptyList(),
    // Only in player searches.
    val ownership: Ownership? = null,
) {
    fun position(): Position = POSITIONS[defaultPositionId] ?: Position.UNKNOWN

    fun proTeam(): String? = PRO_TEAMS[proTeamId]

    fun injury(): InjuryStatus =
        when (val status = injuryStatus) {
            // ESPN sends null for D/ST, which cannot be injured.
            null -> InjuryStatus.ACTIVE
            else -> InjuryStatus.entries.firstOrNull { it.name == status } ?: InjuryStatus.UNKNOWN
        }

    // The season total: scoring period 0, statSplitTypeId 0.
    fun seasonPoints(seasonId: Int): Double? =
        stats
            .firstOrNull {
                it.seasonId == seasonId &&
                    it.scoringPeriodId == 0 &&
                    it.statSourceId == STAT_SOURCE_ACTUAL &&
                    it.statSplitTypeId == STAT_SPLIT_SEASON
            }?.appliedTotal

    fun weekPoints(
        seasonId: Int,
        week: Int,
    ): Double? = weekTotal(seasonId, week, STAT_SOURCE_ACTUAL)

    fun weekProjection(
        seasonId: Int,
        week: Int,
    ): Double? = weekTotal(seasonId, week, STAT_SOURCE_PROJECTED)

    private fun weekTotal(
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

internal data class Ownership(
    // Share of all ESPN leagues, not just this one, that roster the player.
    val percentOwned: Double? = null,
)

internal data class PlayerStat(
    val seasonId: Int,
    val scoringPeriodId: Int,
    val statSourceId: Int,
    val statSplitTypeId: Int,
    // Fantasy points under the league's scoring settings.
    val appliedTotal: Double? = null,
)
