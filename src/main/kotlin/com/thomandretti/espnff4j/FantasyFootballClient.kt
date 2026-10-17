package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.internal.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.internal.espn.Matchup
import com.thomandretti.espnff4j.internal.espn.MatchupTeam
import com.thomandretti.espnff4j.internal.espn.OBJECT_MAPPER
import com.thomandretti.espnff4j.internal.espn.ScheduleResponse
import com.thomandretti.espnff4j.internal.espn.ScheduleSettings
import com.thomandretti.espnff4j.internal.espn.toPlayers
import com.thomandretti.espnff4j.model.BoxScore
import com.thomandretti.espnff4j.model.League
import com.thomandretti.espnff4j.model.MatchupInfo
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.TeamRoster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.await
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.concurrent.CompletableFuture

/**
 * One league's data for one season. Build it with [EspnFantasyFootball.client].
 *
 * Weeks are NFL weeks, which ESPN calls scoring periods. Kotlin callers use the `suspend` functions,
 * which throw an [EspnException] on failure. Java sees only the `*Async` variants, whose futures
 * complete exceptionally with one (wrapped in a `CompletionException` by `join()`).
 */
@Suppress("TooManyFunctions") // Each operation is a suspend function plus its *Async twin for Java.
class FantasyFootballClient private constructor(
    private val espnClient: EspnFantasyFootballClient,
    private val seasonId: Int,
    private val leagueId: Int,
) {
    // Only runs the *Async wrappers. Requests never block it: Retrofit awaits on OkHttp's own threads.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun getLeagueAsync(): CompletableFuture<League> = scope.future { getLeague() }

    fun getStandingsForWeekAsync(week: Int): CompletableFuture<List<Team>> = scope.future { getStandingsForWeek(week) }

    fun getMatchupsForWeekAsync(week: Int): CompletableFuture<List<MatchupInfo>> =
        scope.future { getMatchupsForWeek(week) }

    fun getRostersForWeekAsync(week: Int): CompletableFuture<List<TeamRoster>> =
        scope.future { getRostersForWeek(week) }

    fun getBoxScoresForWeekAsync(week: Int): CompletableFuture<List<BoxScore>> =
        scope.future { getBoxScoresForWeek(week) }

    /** League settings and where the season stands, including the current week. */
    @JvmSynthetic
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

    @JvmSynthetic
    suspend fun getStandingsForWeek(week: Int): List<Team> =
        espnClient
            .getTeamsAtWeek(seasonId, leagueId, week)
            .awaitEspn()
            .teams
            .sortedBy { it.playoffSeed }

    /**
     * Every team's roster as set for NFL [week], with each player's points and ESPN projection for that
     * week. Earlier weeks return the lineup as it was then.
     */
    @JvmSynthetic
    suspend fun getRostersForWeek(week: Int): List<TeamRoster> =
        espnClient
            .getRostersAtWeek(seasonId, leagueId, week)
            .awaitEspn()
            .teams
            .map { it.toTeamRoster(seasonId, week) }

    /**
     * The matchups in play during NFL [week]. When the league stretches a playoff round over two
     * weeks, both weeks return that round's matchups.
     */
    @JvmSynthetic
    suspend fun getMatchupsForWeek(week: Int): List<MatchupInfo> =
        espnClient
            .getScheduleAtWeek(seasonId, leagueId, week)
            .awaitEspn()
            .matchupsFor(week)
            .map { (_, info) -> info }

    /**
     * The matchups in play during NFL [week], as [getMatchupsForWeek], plus both lineups with each
     * player's points and projection for the week. The response is about twice the size, so prefer
     * [getMatchupsForWeek] when the lineups aren't needed.
     */
    @JvmSynthetic
    suspend fun getBoxScoresForWeek(week: Int): List<BoxScore> =
        espnClient
            .getBoxScoresAtWeek(seasonId, leagueId, week)
            .awaitEspn()
            .matchupsFor(week)
            .map { (matchup, info) ->
                BoxScore(
                    matchup = info,
                    homeLineup = matchup.home.rosterForCurrentScoringPeriod.toPlayers(seasonId, week),
                    awayLineup = matchup.away?.rosterForCurrentScoringPeriod.toPlayers(seasonId, week),
                )
            }

    /** This week's matchups, byes dropped, each with the summary built from it. */
    private fun ScheduleResponse.matchupsFor(week: Int): List<Pair<Matchup, MatchupInfo>> {
        // ESPN numbers matchups by matchup period, not NFL week. The two line up one-to-one until a
        // multi-week playoff round, so fall back to the week itself if ESPN sends no mapping.
        val matchupPeriod = settings?.scheduleSettings?.matchupPeriodFor(week) ?: week
        val teamsById = teams.associateBy { it.id }
        return schedule
            .filter { it.matchupPeriodId == matchupPeriod && it.away != null }
            .mapNotNull { matchup ->
                val homeTeam = teamsById[matchup.home.teamId] ?: return@mapNotNull null
                val awayTeam = teamsById[matchup.away!!.teamId] ?: return@mapNotNull null
                matchup to
                    MatchupInfo(
                        homeTeam = homeTeam,
                        homeScore = matchup.home.liveTotal(),
                        awayTeam = awayTeam,
                        awayScore = matchup.away.liveTotal(),
                        homeProjected = matchup.home.projectedTotal(),
                        awayProjected = matchup.away.projectedTotal(),
                        espnHomeWinProbability = matchup.home.winProbability,
                        outcome = matchup.outcome,
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

    internal companion object {
        // Java ignores Kotlin's internal, so the constructor is private and this factory is synthetic.
        @JvmSynthetic
        internal fun create(
            espnClient: EspnFantasyFootballClient,
            seasonId: Int,
            leagueId: Int,
        ): FantasyFootballClient = FantasyFootballClient(espnClient, seasonId, leagueId)
    }
}
