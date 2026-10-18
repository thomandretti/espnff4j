package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.Owner
import com.thomandretti.espnff4j.model.Record
import com.thomandretti.espnff4j.model.RecordPart
import com.thomandretti.espnff4j.model.Streak
import com.thomandretti.espnff4j.model.StreakType
import com.thomandretti.espnff4j.model.Team
import kotlin.math.roundToLong

private const val CENTS = 100.0

/** A team as the mTeam view sends it. Owners are member ids, resolved against the response's `members`. */
internal data class EspnTeam(
    val id: Int,
    val name: String,
    val abbrev: String? = null,
    val playoffSeed: Int,
    val divisionId: Int? = null,
    val logo: String? = null,
    // 0 until the season is over.
    val rankCalculatedFinal: Int = 0,
    val owners: List<String> = emptyList(),
    val record: EspnRecord,
    // Only with the mRoster view.
    val roster: Roster? = null,
)

internal data class EspnRecord(
    val overall: EspnRecordPart,
    // ESPN fills in only wins, losses and ties for these; their points are always zero.
    val home: EspnRecordPart? = null,
    val away: EspnRecordPart? = null,
    val division: EspnRecordPart? = null,
)

internal data class EspnRecordPart(
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val pointsFor: Double = 0.0,
    val pointsAgainst: Double = 0.0,
    val streakLength: Int = 0,
    val streakType: String? = null,
) {
    fun toRecordPart() = RecordPart(losses = losses, wins = wins, ties = ties)
}

/** A league member, i.e. an ESPN account. Team owners refer to these by [id], ESPN's SWID. */
internal data class Member(
    val id: String,
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

/** Maps every team, resolving owners against [members]. An owner with no member entry is dropped. */
internal fun List<EspnTeam>.toTeams(members: List<Member>): List<Team> {
    val membersById = members.associateBy { it.id }
    return map { it.toTeam(membersById) }
}

private fun EspnTeam.toTeam(membersById: Map<String, Member>): Team {
    val overall = record.overall
    return Team(
        id = id,
        name = name,
        record =
            Record(
                overall = overall.toRecordPart(),
                home = record.home?.toRecordPart(),
                away = record.away?.toRecordPart(),
                division = record.division?.toRecordPart(),
            ),
        playoffSeed = playoffSeed,
        abbrev = abbrev,
        // ESPN sums in floating point, so 680.46 arrives as 680.4599999999999.
        pointsFor = (overall.pointsFor * CENTS).roundToLong() / CENTS,
        pointsAgainst = (overall.pointsAgainst * CENTS).roundToLong() / CENTS,
        streak =
            overall.streakType
                ?.takeIf { overall.streakLength > 0 }
                ?.let { type ->
                    Streak(
                        StreakType.entries.firstOrNull { it.name == type } ?: StreakType.UNKNOWN,
                        overall.streakLength,
                    )
                },
        owners =
            owners.mapNotNull { id ->
                membersById[id]?.let { Owner(it.id, it.displayName, it.firstName, it.lastName) }
            },
        divisionId = divisionId,
        logoUrl = logo,
        finalRank = rankCalculatedFinal.takeIf { it > 0 },
    )
}
