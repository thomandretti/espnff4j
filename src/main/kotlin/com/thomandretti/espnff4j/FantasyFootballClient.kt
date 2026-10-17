package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.model.MatchupInfo
import com.thomandretti.espnff4j.model.MatchupOutcome
import com.thomandretti.espnff4j.model.MatchupTeam
import com.thomandretti.espnff4j.model.Team
import retrofit2.await

class FantasyFootballClient(
    private val espnClient: EspnFantasyFootballClient,
    private val seasonId: Int,
    private val leagueId: Int,
) {
    suspend fun getStandingsForWeek(week: Long): List<Team> =
        espnClient
            .getTeamsAtWeek(seasonId, leagueId, week)
            .await()
            .teams
            .sortedBy { it.playoffSeed }

    /**
     * The matchups in play during NFL [week]. When the league stretches a playoff round over two
     * weeks, both weeks return that round's matchups.
     */
    suspend fun getMatchupsForWeek(week: Long): List<MatchupInfo> {
        val response = espnClient.getScheduleAtWeek(seasonId, leagueId, week).await()
        // ESPN numbers matchups by matchup period, not NFL week. The two line up one-to-one until a
        // multi-week playoff round, so fall back to the week itself if ESPN sends no mapping.
        val matchupPeriod =
            response.settings?.scheduleSettings?.matchupPeriodFor(week.toInt()) ?: week.toInt()
        val teamsById = response.teams.associateBy { it.id }
        return response.schedule
            .filter { it.matchupPeriodId == matchupPeriod && it.away != null }
            .mapNotNull { matchup ->
                val homeTeam = teamsById[matchup.home.teamId] ?: return@mapNotNull null
                val awayTeam = teamsById[matchup.away!!.teamId] ?: return@mapNotNull null
                MatchupInfo(
                    homeTeam = homeTeam,
                    homeScore = matchup.home.liveTotal(),
                    awayTeam = awayTeam,
                    awayScore = matchup.away.liveTotal(),
                    homeProjected = matchup.home.projectedTotal(),
                    awayProjected = matchup.away.projectedTotal(),
                    espnHomeWinProbability = matchup.home.winProbability,
                    outcome = MatchupOutcome.parse(matchup.winner),
                )
            }
    }

    /**
     * Points on the board right now. ESPN keeps `totalPoints` at zero for the whole of a live
     * week and reports the running score in `totalPointsLive`, then does the reverse once the
     * week settles and drops the live field. Reading only `totalPoints` shows 0.00 all week.
     */
    private fun MatchupTeam.liveTotal(): Double = totalPointsLive ?: totalPoints

    /**
     * The live projection tracks players as their games finish, so it is preferred over the
     * pre-week figure. Both vanish once ESPN settles the week.
     */
    private fun MatchupTeam.projectedTotal(): Double? = totalProjectedPointsLive ?: totalProjectedPoints
}
