package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.model.League
import com.thomandretti.espnff4j.model.MatchupInfo
import com.thomandretti.espnff4j.model.MatchupOutcome
import com.thomandretti.espnff4j.model.MatchupTeam
import com.thomandretti.espnff4j.model.ScheduleSettings
import com.thomandretti.espnff4j.model.Team
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.await
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/**
 * One league's data for one season. Build it with [EspnFantasyFootball.client].
 *
 * Weeks are NFL weeks, which ESPN calls scoring periods. Every call throws an [EspnException] on failure.
 */
class FantasyFootballClient internal constructor(
    private val espnClient: EspnFantasyFootballClient,
    private val seasonId: Int,
    private val leagueId: Int,
) {
    /** League settings and where the season stands, including the current week. */
    suspend fun getLeague(): League {
        val response = espnClient.getLeague(seasonId, leagueId).awaitEspn()
        val schedule = response.settings.scheduleSettings ?: ScheduleSettings()
        return League(
            id = response.id,
            seasonId = response.seasonId,
            name = response.settings.name,
            size = response.settings.size,
            scoringType = response.settings.scoringSettings?.scoringType,
            currentWeek = response.scoringPeriodId,
            firstWeek = response.status.firstScoringPeriod,
            finalWeek = response.status.finalScoringPeriod,
            currentMatchupPeriod = response.status.currentMatchupPeriod,
            regularSeasonMatchupPeriods = schedule.matchupPeriodCount,
            playoffTeamCount = schedule.playoffTeamCount,
            matchupPeriods = schedule.matchupPeriods,
            isActive = response.status.isActive,
        )
    }

    suspend fun getStandingsForWeek(week: Int): List<Team> =
        espnClient
            .getTeamsAtWeek(seasonId, leagueId, week)
            .awaitEspn()
            .teams
            .sortedBy { it.playoffSeed }

    /**
     * The matchups in play during NFL [week]. When the league stretches a playoff round over two
     * weeks, both weeks return that round's matchups.
     */
    suspend fun getMatchupsForWeek(week: Int): List<MatchupInfo> {
        val response = espnClient.getScheduleAtWeek(seasonId, leagueId, week).awaitEspn()
        // ESPN numbers matchups by matchup period, not NFL week. The two line up one-to-one until a
        // multi-week playoff round, so fall back to the week itself if ESPN sends no mapping.
        val matchupPeriod = response.settings?.scheduleSettings?.matchupPeriodFor(week) ?: week
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

    /** Awaits the call, translating Retrofit and I/O failures into [EspnException]s. */
    private suspend fun <T : Any> Call<T>.awaitEspn(): T =
        try {
            await()
        } catch (e: HttpException) {
            val code = e.code()
            val detail = espnMessage(e)?.let { ": $it" }.orEmpty()
            throw when (code) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN ->
                    EspnUnauthorizedException(
                        code,
                        "ESPN refused league $leagueId (HTTP $code)$detail. Private leagues need valid " +
                            "SWID and espn_s2 cookies; ESPN also answers this way for a league that does not exist.",
                        e,
                    )
                HTTP_NOT_FOUND -> EspnNotFoundException("ESPN has no season $seasonId for league $leagueId$detail", e)
                else -> EspnApiException(code, "ESPN returned HTTP $code$detail", e)
            }
        } catch (e: IOException) {
            // Covers both network failures and Jackson errors from a payload that no longer fits the model.
            throw EspnApiException(null, "Could not reach ESPN or read its response: ${e.message}", e)
        }

    /** ESPN's own explanation, e.g. "You are not authorized to view this League.", when it sends one. */
    private fun espnMessage(e: HttpException): String? =
        runCatching {
            e.response()?.errorBody()?.string()?.let { body ->
                OBJECT_MAPPER
                    .readTree(body)
                    .path("messages")
                    .path(0)
                    .textValue()
            }
        }.getOrNull()
}
