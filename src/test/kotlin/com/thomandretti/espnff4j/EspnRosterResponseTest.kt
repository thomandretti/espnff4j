package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.InjuryStatus
import com.thomandretti.espnff4j.model.LineupSlot
import com.thomandretti.espnff4j.model.Position
import com.thomandretti.espnff4j.model.RosterPlayer
import com.thomandretti.espnff4j.model.TeamRoster
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins rosters against the real ESPN payload. The fixture is a trimmed capture of a live
 * `mRoster,mTeam` response for week 6 of the 2026 season, two teams of a ten-team league (team names
 * anonymised), taken after Thursday's game: a couple of players have week-6 points, the rest only
 * projections.
 */
class EspnRosterResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun rosters(): List<TeamRoster> {
        val fixture = checkNotNull(javaClass.getResource("/espn-rosters-week6.json")) { "missing ESPN fixture" }
        server.enqueue(MockResponse().setBody(fixture.readText()))
        val client = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())
        return runBlocking { client.getRostersForWeek(6) }
    }

    private fun player(name: String): RosterPlayer = rosters().flatMap { it.players }.single { it.name == name }

    @Test
    fun `requests the roster and team views for the week`() {
        rosters()

        val path = requireNotNull(server.takeRequest().path)
        assertTrue("scoringPeriodId=6" in path, path)
        assertTrue("view=mRoster" in path && "view=mTeam" in path, path)
    }

    @Test
    fun `every team comes back with its full roster`() {
        val rosters = rosters()

        assertEquals(listOf("Team A", "Team B"), rosters.map { it.team.name })
        rosters.forEach { assertEquals(17, it.players.size, it.team.name) }
    }

    @Test
    fun `starters come first in lineup order`() {
        val teamA = rosters().first()

        assertEquals(
            listOf(
                LineupSlot.QB,
                LineupSlot.RB,
                LineupSlot.RB,
                LineupSlot.WR,
                LineupSlot.WR,
                LineupSlot.TE,
                LineupSlot.FLEX,
                LineupSlot.DST,
                LineupSlot.K,
            ),
            teamA.starters.map { it.lineupSlot },
        )
        assertEquals(teamA.starters, teamA.players.take(teamA.starters.size))
    }

    @Test
    fun `a starting kicker maps position, NFL team and projection`() {
        val butker = player("Harrison Butker")

        assertEquals(Position.K, butker.position)
        assertEquals("KC", butker.proTeam)
        assertEquals(LineupSlot.K, butker.lineupSlot)
        assertTrue(butker.isStarter)
        assertEquals(InjuryStatus.ACTIVE, butker.injuryStatus)
        assertEquals(9.547, requireNotNull(butker.projectedPoints), 0.001)
        assertNull(butker.points, "his game has not been played yet")
    }

    @Test
    fun `a D-ST with no injury status from ESPN reads as active`() {
        val dst = player("Chiefs D/ST")

        assertEquals(Position.DST, dst.position)
        assertEquals(LineupSlot.DST, dst.lineupSlot)
        assertEquals(InjuryStatus.ACTIVE, dst.injuryStatus)
    }

    @Test
    fun `a player on IR is not a starter`() {
        val charbonnet = player("Zach Charbonnet")

        assertEquals(LineupSlot.IR, charbonnet.lineupSlot)
        assertFalse(charbonnet.isStarter)
        assertEquals(InjuryStatus.OUT, charbonnet.injuryStatus)
    }

    @Test
    fun `points appear only for players whose game has been played`() {
        assertEquals(13.6, player("J.K. Dobbins").points)
        rosters().forEach { roster ->
            assertEquals(2, roster.players.count { it.points != null }, roster.team.name)
            assertTrue(roster.players.all { it.projectedPoints != null }, "every player has a projection")
        }
    }
}
