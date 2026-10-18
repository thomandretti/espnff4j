package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.PlayoffTier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import kotlin.math.abs

/**
 * Calls every operation against the real ESPN API and checks the results parse and hang together.
 * Fixtures pin the shapes ESPN sent when they were captured; this catches the ones it sends now, such
 * as a live week's lineups (0.3.0 broke on those). Run it before tagging a release:
 *
 * ```
 * ESPN_LEAGUE_ID=... ESPN_SWID=... ESPN_S2=... ./gradlew liveTest
 * ```
 *
 * `ESPN_SEASON` defaults to the NFL season in progress. The cookies are only needed for a private
 * league. Excluded from `test` and `check`, so CI never calls ESPN.
 */
@Tag("live")
class EspnLiveSmokeTest {
    private val leagueId =
        checkNotNull(System.getenv("ESPN_LEAGUE_ID")?.toIntOrNull()) { "set ESPN_LEAGUE_ID to run the live tests" }
    private val season = System.getenv("ESPN_SEASON")?.toInt() ?: currentNflSeason()
    private val credentials =
        System.getenv("ESPN_SWID")?.let { swid -> EspnCredentials(swid, checkNotNull(System.getenv("ESPN_S2"))) }

    private fun client(season: Int) = EspnFantasyFootball.client(season, leagueId, credentials)

    @Test
    fun `every weekly operation parses the current week`() =
        runBlocking {
            val client = client(season)
            val league = client.getLeague()
            val week = league.currentWeek.coerceIn(league.firstWeek, league.finalWeek)

            assertTrue(league.waivers != null, "no acquisition settings")
            val standings = client.getStandingsForWeek(week)
            assertEquals(league.size, standings.size)
            assertEquals((1..league.size).toList(), standings.mapNotNull { it.waiverRank }.sorted())
            assertTrue(standings.all { it.transactionCounts != null }, "no transaction counts")
            assertEquals(league.size, client.getRostersForWeek(week).size)
            val matchups = client.getMatchupsForWeek(week)
            val boxScores = client.getBoxScoresForWeek(week)
            assertEquals(
                matchups.map { it.homeTeam.id to it.awayTeam.id },
                boxScores.map {
                    it.matchup.homeTeam.id to
                        it.matchup.awayTeam.id
                },
            )
            boxScores
                // A two-week playoff round's totals span both weeks; the lineups show one.
                .filter { league.matchupPeriods[it.matchup.matchupPeriod]?.size == 1 }
                .forEach {
                    assertNear(it.matchup.homeScore, it.homeStarters.sumOf { p -> p.points ?: 0.0 })
                    assertNear(it.matchup.awayScore, it.awayStarters.sumOf { p -> p.points ?: 0.0 })
                }
            client.getTransactionsForWeek(week)
            client.getPendingTrades(week)
            assertTrue(client.getFreeAgents(week, limit = 5).isNotEmpty())
            val rostered = client.getRostersForWeek(week).flatMap { it.players }.first()
            assertTrue(client.searchPlayers(rostered.name, week).any { it.id == rostered.id }, rostered.name)
            assertTrue(client.getNflGamesForWeek(week).isNotEmpty())
            assertTrue(client.getSchedule().isNotEmpty())
            // Ending on an assertion keeps the method void; JUnit silently skips a test that returns a value.
            val draft = client.getDraft()
            assertTrue(!draft.isComplete || draft.picks.isNotEmpty())
        }

    @Test
    fun `the last completed season has a champion, a draft and parseable transactions`() =
        runBlocking {
            val previous = client(season).getLeague().previousSeasons.maxOrNull() ?: return@runBlocking
            val client = client(previous)
            val league = client.getLeague()

            val standings = client.getStandingsForWeek(league.finalWeek)
            assertEquals((1..league.size).toList(), standings.mapNotNull { it.finalRank }.sorted())
            assertTrue(client.getSchedule().any { it.playoffTier == PlayoffTier.WINNERS_BRACKET })
            val draft = client.getDraft()
            assertTrue(draft.isComplete && draft.picks.isNotEmpty())
            // Trades leave a chain of half-finished records; every week of a season must still parse.
            (league.firstWeek..league.finalWeek).forEach { client.getTransactionsForWeek(it) }
        }

    private fun assertNear(
        expected: Double,
        actual: Double,
    ) = assertTrue(abs(expected - actual) < 0.01, "expected $expected, starters sum to $actual")

    // The NFL season named for a year runs into the February after it.
    private fun currentNflSeason(): Int =
        LocalDate.now().let { if (it.month <= Month.FEBRUARY) it.year - 1 else it.year }
}
