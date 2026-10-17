package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.BoxScore
import com.thomandretti.espnff4j.model.LineupSlot
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins box scores against the real ESPN payload. The fixture is a trimmed capture of a live
 * `mMatchupScore,mScoreboard,mTeam,mSettings` response for week 6 of the 2026 season: two of that
 * week's matchups (team names anonymised) taken after Thursday's game, plus a settled week-5 matchup
 * that must be filtered out.
 */
class EspnBoxScoreResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun boxScores(): List<BoxScore> {
        val fixture = checkNotNull(javaClass.getResource("/espn-box-scores-week6.json")) { "missing ESPN fixture" }
        server.enqueue(MockResponse().setBody(fixture.readText()))
        val client = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())
        return runBlocking { client.getBoxScoresForWeek(6) }
    }

    @Test
    fun `requests the scoreboard view, without which ESPN leaves lineup players anonymous`() {
        boxScores()

        val path = requireNotNull(server.takeRequest().path)
        assertTrue("view=mScoreboard" in path && "view=mMatchupScore" in path, path)
    }

    @Test
    fun `returns only the requested week's matchups`() {
        val boxScores = boxScores()

        assertEquals(
            listOf("Team A" to "Team B", "Team C" to "Team D"),
            boxScores.map { it.matchup.homeTeam.name to it.matchup.awayTeam.name },
        )
    }

    @Test
    fun `every lineup player is identified and projected`() {
        val lineups = boxScores().flatMap { listOf(it.homeLineup, it.awayLineup) }

        assertEquals(listOf(17, 17, 17, 16), lineups.map { it.size })
        lineups.flatten().forEach { player ->
            assertTrue(player.name.isNotBlank(), "unnamed player ${player.id}")
            assertTrue(player.projectedPoints != null, "no projection for ${player.name}")
        }
    }

    @Test
    fun `starters' points add up to ESPN's score for each side`() {
        boxScores().forEach { box ->
            assertEquals(box.matchup.homeScore, box.homeStarters.sumOf { it.points ?: 0.0 }, 0.001)
            assertEquals(box.matchup.awayScore, box.awayStarters.sumOf { it.points ?: 0.0 }, 0.001)
        }
        assertEquals(30.6, boxScores()[1].matchup.homeScore, 0.001)
    }

    @Test
    fun `bench points are reported but do not count`() {
        val teamA = boxScores().first()
        val dobbins = teamA.homeLineup.single { it.name == "J.K. Dobbins" }

        assertEquals(LineupSlot.BENCH, dobbins.lineupSlot)
        assertEquals(13.6, dobbins.points)
        assertEquals(0.0, teamA.matchup.homeScore)
    }
}
