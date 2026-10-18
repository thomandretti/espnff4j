package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.NflGame
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Pins NFL games against ESPN's public `proTeamSchedules_wl` view for 2026, trimmed to weeks 5 and 6.
 * The fixture was captured on the Saturday of week 6, after Thursday's game.
 */
class EspnNflScheduleResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun games(week: Int): List<NflGame> {
        val fixture = checkNotNull(javaClass.getResource("/espn-nfl-schedule-2026.json")) { "missing ESPN fixture" }
        server.enqueue(MockResponse().setBody(fixture.readText()))
        val client = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())
        return runBlocking { client.getNflGamesForWeek(week) }
    }

    @Test
    fun `each game once, by kickoff, from the season-level endpoint`() {
        val games = games(6)

        assertEquals(14, games.size)
        assertEquals(games.sortedBy { it.kickoff }, games)
        assertEquals("/seasons/2026?view=proTeamSchedules_wl", server.takeRequest().path)
    }

    @Test
    fun `Thursday's game has kicked off and is official, Sunday's haven't`() {
        val games = games(6)
        val saturday = Instant.parse("2026-10-17T12:00:00Z")

        val thursday = games.first()
        assertEquals("DEN" to "SEA", thursday.homeTeam to thursday.awayTeam)
        assertEquals(Instant.ofEpochMilli(1792109700000), thursday.kickoff)
        assertTrue(thursday.hasKickedOff(saturday) && thursday.statsOfficial)
        assertTrue(games.drop(1).none { it.hasKickedOff(saturday) || it.statsOfficial })
        assertEquals(1, games.count { it.involves("KC") })
    }

    @Test
    fun `teams on bye have no game`() {
        val teams = games(6).flatMap { listOf(it.homeTeam, it.awayTeam) }.toSet()

        assertEquals(28, teams.size)
        listOf("MIA", "MIN", "CIN", "DET").forEach { assertFalse(it in teams, it) }
    }
}
