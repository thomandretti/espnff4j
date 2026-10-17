package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.Player
import com.thomandretti.espnff4j.model.PlayerStatus
import com.thomandretti.espnff4j.model.Team
import java.time.Instant

/** The kona_player_info view, filtered by the X-Fantasy-Filter header (see [PlayerFilters]). */
internal data class PlayersResponse(
    val players: List<EspnPoolPlayer> = emptyList(),
    val teams: List<EspnTeam> = emptyList(),
    val members: List<Member> = emptyList(),
)

internal data class EspnPoolPlayer(
    val id: Int,
    // 0 when no team in the league rosters the player.
    val onTeamId: Int = 0,
    // FREEAGENT, WAIVERS or ONTEAM.
    val status: String? = null,
    // Epoch milliseconds when a player on waivers becomes a free agent.
    val waiverProcessDate: Long? = null,
    val player: EspnPlayer,
)

internal fun EspnPoolPlayer.toPlayer(
    seasonId: Int,
    week: Int,
    teamsById: Map<Int, Team>,
): Player =
    Player(
        id = id,
        name = player.fullName ?: "Unknown player $id",
        position = player.position(),
        proTeam = player.proTeam(),
        injuryStatus = player.injury(),
        status =
            when (status) {
                "FREEAGENT" -> PlayerStatus.FREE_AGENT
                "WAIVERS" -> PlayerStatus.WAIVERS
                "ONTEAM" -> PlayerStatus.ON_TEAM
                else -> PlayerStatus.UNKNOWN
            },
        team = teamsById[onTeamId],
        waiverClearsAt = waiverProcessDate?.let(Instant::ofEpochMilli),
        percentOwned = player.ownership?.percentOwned,
        points = player.weekPoints(seasonId, week),
        projectedPoints = player.weekProjection(seasonId, week),
    )
