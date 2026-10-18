package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.NflGame
import java.time.Instant

/** The season-level proTeamSchedules_wl view: every NFL team's games, the same for every league. */
internal data class ProTeamsResponse(
    val settings: ProTeamsSettings = ProTeamsSettings(),
)

internal data class ProTeamsSettings(
    // Includes a "FA" pseudo-team with no games.
    val proTeams: List<EspnProTeam> = emptyList(),
)

internal data class EspnProTeam(
    val id: Int,
    val abbrev: String,
    // Scoring period -> the team's games that week; a team on bye has no entry.
    val proGamesByScoringPeriod: Map<Int, List<EspnProGame>> = emptyMap(),
)

internal data class EspnProGame(
    val id: Long,
    // Epoch milliseconds.
    val date: Long,
    val homeProTeamId: Int,
    val awayProTeamId: Int,
    val startTimeTBD: Boolean = false,
    val statsOfficial: Boolean = false,
)

/** Each game appears under both of its teams, so it is kept once. */
internal fun ProTeamsResponse.gamesFor(week: Int): List<NflGame> {
    val abbrevs = settings.proTeams.associate { it.id to it.abbrev }
    return settings.proTeams
        .flatMap { it.proGamesByScoringPeriod[week].orEmpty() }
        .distinctBy { it.id }
        .map { game ->
            NflGame(
                id = game.id,
                week = week,
                kickoff = Instant.ofEpochMilli(game.date),
                isStartTimeTbd = game.startTimeTBD,
                homeTeam = abbrevs[game.homeProTeamId] ?: "${game.homeProTeamId}",
                awayTeam = abbrevs[game.awayProTeamId] ?: "${game.awayProTeamId}",
                statsOfficial = game.statsOfficial,
            )
        }.sortedBy { it.kickoff }
}
