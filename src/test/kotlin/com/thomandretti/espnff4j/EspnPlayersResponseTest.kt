package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.InjuryStatus
import com.thomandretti.espnff4j.model.Player
import com.thomandretti.espnff4j.model.PlayerStatus
import com.thomandretti.espnff4j.model.Position
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFailsWith

/**
 * Pins free agents and player search against trimmed captures of real `kona_player_info,mTeam`
 * responses for week 6 of the 2026 season (the five best-projected available RBs, and a search for
 * "allen"), with team and member names and account ids anonymised.
 */
class EspnPlayersResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun client() = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())

    private fun enqueueFixture(name: String) {
        val fixture = checkNotNull(javaClass.getResource("/$name")) { "missing ESPN fixture $name" }
        server.enqueue(MockResponse().setBody(fixture.readText()))
    }

    private fun freeAgents(): List<Player> {
        enqueueFixture("espn-free-agents-week6.json")
        return runBlocking { client().getFreeAgents(6, Position.RB, limit = 5) }
    }

    @Test
    fun `free agents are ordered by projection and include players on waivers`() {
        val players = freeAgents()

        assertEquals(
            listOf("Tyler Allgeier", "Kendre Miller", "Tyjae Spears", "Pierre Strong Jr.", "George Holani"),
            players.map { it.name },
        )
        assertEquals(players.sortedByDescending { it.projectedPoints }, players)
        assertTrue(players.all { it.position == Position.RB && it.team == null })
        val holani = players.last()
        assertEquals(PlayerStatus.WAIVERS, holani.status)
        assertEquals(Instant.ofEpochMilli(1792566000000), holani.waiverClearsAt)
    }

    @Test
    fun `a free agent's week figures and ownership`() {
        val allgeier = freeAgents().first()

        assertEquals(PlayerStatus.FREE_AGENT, allgeier.status)
        assertEquals("ARI", allgeier.proTeam)
        assertEquals(InjuryStatus.ACTIVE, allgeier.injuryStatus)
        assertEquals(12.346796, requireNotNull(allgeier.projectedPoints), 0.0001)
        assertEquals(47.21, allgeier.percentOwned)
        assertNull(allgeier.waiverClearsAt)
        assertEquals(InjuryStatus.QUESTIONABLE, freeAgents()[2].injuryStatus)
    }

    @Test
    fun `the free-agent filter asks for available players at the position, sorted by week projection`() {
        freeAgents()

        val request = server.takeRequest()
        assertTrue("scoringPeriodId=6" in requireNotNull(request.path), request.path)
        val filter = requireNotNull(request.getHeader("X-Fantasy-Filter"))
        assertTrue(""""filterStatus":{"value":["FREEAGENT","WAIVERS"]}""" in filter, filter)
        assertTrue(""""filterSlotIds":{"value":[2]}""" in filter, filter)
        assertTrue(""""limit":5""" in filter, filter)
        assertTrue(""""value":"1120266"""" in filter, filter)
    }

    @Test
    fun `search finds rostered players with their fantasy team`() {
        enqueueFixture("espn-player-search-week6.json")

        val players = runBlocking { client().searchPlayers(" allen ", 6, limit = 5) }

        val josh = players.first()
        assertEquals("Josh Allen", josh.name)
        assertEquals(PlayerStatus.ON_TEAM, josh.status)
        assertEquals("Team J", josh.team?.name)
        assertEquals(PlayerStatus.FREE_AGENT, players.single { it.name == "Keenan Allen" }.status)
        val filter = requireNotNull(server.takeRequest().getHeader("X-Fantasy-Filter"))
        assertTrue(""""filterName":{"value":"allen"}""" in filter, filter)
    }

    @Test
    fun `arguments ESPN cannot answer are rejected before any request`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { client().getFreeAgents(6, Position.UNKNOWN) } }
        assertFailsWith<IllegalArgumentException> { runBlocking { client().getFreeAgents(6, limit = 0) } }
        assertFailsWith<IllegalArgumentException> { runBlocking { client().searchPlayers(" ", 6) } }
        assertEquals(0, server.requestCount)
    }
}
