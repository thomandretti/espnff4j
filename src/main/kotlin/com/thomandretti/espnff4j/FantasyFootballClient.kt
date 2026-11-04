package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.internal.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.internal.espn.EspnNewsClient
import com.thomandretti.espnff4j.internal.espn.Matchup
import com.thomandretti.espnff4j.internal.espn.MatchupTeam
import com.thomandretti.espnff4j.internal.espn.OBJECT_MAPPER
import com.thomandretti.espnff4j.internal.espn.PlayerFilters
import com.thomandretti.espnff4j.internal.espn.PlayersResponse
import com.thomandretti.espnff4j.internal.espn.ScheduleResponse
import com.thomandretti.espnff4j.internal.espn.ScheduleSettings
import com.thomandretti.espnff4j.internal.espn.completed
import com.thomandretti.espnff4j.internal.espn.gamesFor
import com.thomandretti.espnff4j.internal.espn.openTrades
import com.thomandretti.espnff4j.internal.espn.playerIds
import com.thomandretti.espnff4j.internal.espn.toDraft
import com.thomandretti.espnff4j.internal.espn.toPendingTrade
import com.thomandretti.espnff4j.internal.espn.toPlayer
import com.thomandretti.espnff4j.internal.espn.toPlayerNote
import com.thomandretti.espnff4j.internal.espn.toPlayers
import com.thomandretti.espnff4j.internal.espn.toTeams
import com.thomandretti.espnff4j.internal.espn.toTransaction
import com.thomandretti.espnff4j.model.BoxScore
import com.thomandretti.espnff4j.model.Draft
import com.thomandretti.espnff4j.model.League
import com.thomandretti.espnff4j.model.MatchupInfo
import com.thomandretti.espnff4j.model.NflGame
import com.thomandretti.espnff4j.model.PendingTrade
import com.thomandretti.espnff4j.model.Player
import com.thomandretti.espnff4j.model.PlayerDetail
import com.thomandretti.espnff4j.model.Position
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.TeamRoster
import com.thomandretti.espnff4j.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.future
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.await
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import java.util.concurrent.CompletableFuture

private const val DEFAULT_FREE_AGENTS = 25
private const val DEFAULT_SEARCH_RESULTS = 10
private const val DEFAULT_PLAYER_NEWS = 5

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
    private val newsClient: EspnNewsClient,
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

    fun getScheduleAsync(): CompletableFuture<List<MatchupInfo>> = scope.future { getSchedule() }

    fun getDraftAsync(): CompletableFuture<Draft> = scope.future { getDraft() }

    fun getNflGamesForWeekAsync(week: Int): CompletableFuture<List<NflGame>> = scope.future { getNflGamesForWeek(week) }

    fun getBoxScoresForWeekAsync(week: Int): CompletableFuture<List<BoxScore>> =
        scope.future { getBoxScoresForWeek(week) }

    fun getTransactionsForWeekAsync(week: Int): CompletableFuture<List<Transaction>> =
        scope.future { getTransactionsForWeek(week) }

    fun getPendingTradesAsync(week: Int): CompletableFuture<List<PendingTrade>> =
        scope.future { getPendingTrades(week) }

    @JvmOverloads
    fun getFreeAgentsAsync(
        week: Int,
        position: Position? = null,
        limit: Int = DEFAULT_FREE_AGENTS,
    ): CompletableFuture<List<Player>> = scope.future { getFreeAgents(week, position, limit) }

    @JvmOverloads
    fun searchPlayersAsync(
        name: String,
        week: Int,
        limit: Int = DEFAULT_SEARCH_RESULTS,
    ): CompletableFuture<List<Player>> = scope.future { searchPlayers(name, week, limit) }

    @JvmOverloads
    fun getPlayerAsync(
        playerId: Int,
        newsLimit: Int = DEFAULT_PLAYER_NEWS,
    ): CompletableFuture<PlayerDetail?> = scope.future { getPlayer(playerId, newsLimit) }

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
            previousSeasons = response.status.previousSeasons,
            waivers = response.settings.acquisitionSettings?.toWaiverSettings(),
        )
    }

    @JvmSynthetic
    suspend fun getStandingsForWeek(week: Int): List<Team> =
        espnClient
            .getTeamsAtWeek(seasonId, leagueId, week)
            .awaitEspn()
            .let { it.teams.toTeams(it.members) }
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
            .toTeamRosters(seasonId, week)

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
     * Every matchup of the season, regular season and playoffs, in matchup-period order. Settled
     * matchups carry their final scores and [MatchupInfo.outcome]; the live one its running score; future
     * ones zero. Playoff matchups appear only once ESPN has seeded them, and first-round byes are left
     * out. For an earlier season, use a client built for that season (see [League.previousSeasons]).
     */
    @JvmSynthetic
    suspend fun getSchedule(): List<MatchupInfo> =
        espnClient
            .getSeasonSchedule(seasonId, leagueId)
            .awaitEspn()
            .matchups { true }
            .map { (_, info) -> info }
            .sortedBy { it.matchupPeriod }

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

    /**
     * Free-agent pickups, waiver claims, trades, drops on their own and IR moves completed during NFL
     * [week], newest first. Other lineup changes, failed claims and trade offers that didn't go through
     * are left out. A trade appears in the week its review period ended, which can be the week after it
     * was accepted. Costs two requests: ESPN identifies the players only by id.
     */
    @JvmSynthetic
    suspend fun getTransactionsForWeek(week: Int): List<Transaction> {
        val response = espnClient.getTransactionsAtWeek(seasonId, leagueId, week).awaitEspn()
        val completed = response.completed()
        if (completed.isEmpty()) return emptyList()
        val teamsById = response.teams.toTeams(response.members).associateBy { it.id }
        val playerIds = completed.flatMap { tx -> tx.items.map { it.playerId } }.toSet()
        val playersById = lookUpPlayers(playerIds, week, teamsById)
        return completed.mapNotNull { it.toTransaction(teamsById, playersById) }.sortedByDescending { it.date }
    }

    /**
     * Trades that haven't gone through as of NFL [week], normally the current week: offers awaiting the
     * other team, and accepted trades in their review period, oldest first. Trades resolve in a later week
     * than they're offered in, so this reads [week] and the week before. ESPN may show an offer only to
     * the teams involved, so which offers appear can depend on whose cookies the client uses. Costs up
     * to three requests: two weeks, then the players.
     */
    @JvmSynthetic
    suspend fun getPendingTrades(week: Int): List<PendingTrade> {
        val response = espnClient.getTransactionsAtWeek(seasonId, leagueId, week).awaitEspn()
        val earlier =
            if (week > 1) espnClient.getTransactionsAtWeek(seasonId, leagueId, week - 1).awaitEspn() else null
        val open = (earlier?.transactions.orEmpty() + response.transactions).openTrades(Instant.now())
        if (open.isEmpty()) return emptyList()
        val teamsById = response.teams.toTeams(response.members).associateBy { it.id }
        val playersById = lookUpPlayers(open.flatMap { it.playerIds() }.toSet(), week, teamsById)
        return open.mapNotNull { it.toPendingTrade(teamsById, playersById) }.sortedBy { it.proposedAt }
    }

    /**
     * The best available players for NFL [week], free agents and players on waivers, ordered by ESPN's
     * projection for the week. [position] narrows the list to players eligible there; it can't be
     * [Position.UNKNOWN].
     */
    @JvmSynthetic
    suspend fun getFreeAgents(
        week: Int,
        position: Position? = null,
        limit: Int = DEFAULT_FREE_AGENTS,
    ): List<Player> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return espnClient
            .getPlayers(seasonId, leagueId, week, PlayerFilters.freeAgents(seasonId, week, position, limit))
            .awaitEspn()
            .toPlayers(week)
    }

    /**
     * Players whose full name or surname starts with [name], ignoring case ("josh al" and "allen" both
     * find Josh Allen), most widely owned first. Each comes with who rosters them in this league and
     * their points and projection for NFL [week].
     */
    @JvmSynthetic
    suspend fun searchPlayers(
        name: String,
        week: Int,
        limit: Int = DEFAULT_SEARCH_RESULTS,
    ): List<Player> {
        require(name.isNotBlank()) { "name must not be blank" }
        require(limit > 0) { "limit must be positive, was $limit" }
        return espnClient
            .getPlayers(seasonId, leagueId, week, PlayerFilters.byName(name.trim(), week, limit))
            .awaitEspn()
            .toPlayers(week)
    }

    /**
     * The player with [playerId] (as on [Player.id] or [RosterPlayer.id]) as ESPN reports them now, with
     * their points and projection for every week of the season and up to [newsLimit] of ESPN's latest news
     * items on them, such as injury updates. Null if ESPN has no such player. Costs two requests, made
     * together; a [newsLimit] of 0 skips the news one.
     */
    @JvmSynthetic
    suspend fun getPlayer(
        playerId: Int,
        newsLimit: Int = DEFAULT_PLAYER_NEWS,
    ): PlayerDetail? {
        require(newsLimit >= 0) { "newsLimit must not be negative, was $newsLimit" }
        return coroutineScope {
            val news =
                async {
                    if (newsLimit == 0) {
                        emptyList()
                    } else {
                        newsClient
                            .getPlayerNews(playerId, newsLimit)
                            .awaitEspn()
                            .feed
                            .mapNotNull { it.toPlayerNote() }
                    }
                }
            val response =
                espnClient
                    // scoringPeriodId 0 is the season as a whole; the filter asks for every week.
                    .getPlayers(seasonId, leagueId, 0, PlayerFilters.wholeSeason(playerId))
                    .awaitEspn()
            val teamsById = response.teams.toTeams(response.members).associateBy { it.id }
            val entry = response.players.firstOrNull { it.id == playerId }
            entry?.let {
                PlayerDetail(
                    player = it.toPlayer(seasonId, null, teamsById),
                    weeks = it.player.weeks(seasonId),
                    news = news.await(),
                )
            }
        }
    }

    /**
     * The NFL games of NFL [week], by kickoff. A team with no game is on bye. Join on
     * [RosterPlayer.proTeam] to tell which players have yet to play. The schedule is the same for every
     * league and changes rarely, so it suits caching.
     */
    @JvmSynthetic
    suspend fun getNflGamesForWeek(week: Int): List<NflGame> =
        espnClient
            .getProTeamSchedules(seasonId)
            .awaitEspn()
            .gamesFor(week)

    /**
     * Every pick of this season's draft, in order, with each player's season points so far. Costs two
     * requests: ESPN identifies the players only by id, and the lookup for a full draft is about 1.5 MB.
     */
    @JvmSynthetic
    suspend fun getDraft(): Draft {
        val response = espnClient.getDraft(seasonId, leagueId).awaitEspn()
        val teamsById = response.teams.toTeams(response.members).associateBy { it.id }
        val playerIds =
            response.draftDetail.picks
                .map { it.playerId }
                .toSet()
        val playersById = if (playerIds.isEmpty()) emptyMap() else lookUpPlayers(playerIds, null, teamsById)
        return response.toDraft(teamsById, playersById)
    }

    /** ESPN's current view of the players with [ids], with stats for [week] if given. */
    private suspend fun lookUpPlayers(
        ids: Set<Int>,
        week: Int?,
        teamsById: Map<Int, Team>,
    ): Map<Int, Player> =
        espnClient
            // scoringPeriodId 0 is the season as a whole.
            .getPlayers(seasonId, leagueId, week ?: 0, PlayerFilters.byIds(ids, week), "kona_player_info")
            .awaitEspn()
            .players
            .map { it.toPlayer(seasonId, week, teamsById) }
            .associateBy { it.id }

    private fun PlayersResponse.toPlayers(week: Int): List<Player> {
        val teamsById = teams.toTeams(members).associateBy { it.id }
        return players.map { it.toPlayer(seasonId, week, teamsById) }
    }

    /** This week's matchups, byes dropped, each with the summary built from it. */
    private fun ScheduleResponse.matchupsFor(week: Int): List<Pair<Matchup, MatchupInfo>> {
        // ESPN numbers matchups by matchup period, not NFL week. The two line up one-to-one until a
        // multi-week playoff round, so fall back to the week itself if ESPN sends no mapping.
        val matchupPeriod = settings?.scheduleSettings?.matchupPeriodFor(week) ?: week
        return matchups { it.matchupPeriodId == matchupPeriod }
    }

    /** The schedule's matchups that pass [include], byes dropped, each with the summary built from it. */
    private fun ScheduleResponse.matchups(include: (Matchup) -> Boolean): List<Pair<Matchup, MatchupInfo>> {
        val teamsById = teams.toTeams(members).associateBy { it.id }
        return schedule
            .filter { include(it) && it.away != null }
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
                        matchupPeriod = matchup.matchupPeriodId,
                        playoffTier = matchup.playoffTier,
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
            newsClient: EspnNewsClient,
            seasonId: Int,
            leagueId: Int,
        ): FantasyFootballClient = FantasyFootballClient(espnClient, newsClient, seasonId, leagueId)
    }
}
