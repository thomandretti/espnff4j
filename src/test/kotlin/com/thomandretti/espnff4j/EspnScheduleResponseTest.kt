package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.MatchupInfo
import com.thomandretti.espnff4j.model.MatchupOutcome
import com.thomandretti.espnff4j.model.PlayoffTier
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the season schedule and final standings against a trimmed capture of the real
 * `mMatchupScore,mTeam,mSettings` response for the whole 2025 season, playoffs included, with team and
 * member names, logos and account ids anonymised.
 */
class EspnScheduleResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun schedule(): List<MatchupInfo> {
        val fixture = checkNotNull(javaClass.getResource("/espn-schedule-2025.json")) { "missing ESPN fixture" }
        server.enqueue(MockResponse().setBody(fixture.readText()))
        val client = EspnFantasyFootball.client(2025, 12345, baseUrl = server.url("/seasons/").toString())
        return runBlocking { client.getSchedule() }
    }

    @Test
    fun `the whole season is requested in one call, with no week`() {
        schedule()

        val path = requireNotNull(server.takeRequest().path)
        assertTrue(path.startsWith("/seasons/2025/segments/0/leagues/12345?view=mMatchupScore"), path)
        assertFalse("scoringPeriodId" in path, path)
    }

    @Test
    fun `every matchup but the two first-round byes, in matchup-period order`() {
        val schedule = schedule()

        // 86 in ESPN's schedule, less two playoff byes.
        assertEquals(84, schedule.size)
        assertEquals((1..17).toList(), schedule.map { it.matchupPeriod }.distinct())
        assertEquals(70, schedule.count { it.playoffTier == PlayoffTier.REGULAR_SEASON })
        val opener = schedule.first()
        assertEquals("Team C" to "Team B", opener.homeTeam.name to opener.awayTeam.name)
        assertEquals(99.72 to 106.46, opener.homeScore to opener.awayScore)
        assertEquals(MatchupOutcome.AWAY, opener.outcome)
    }

    @Test
    fun `the championship is the last winners-bracket game, and its winner finished first`() {
        val schedule = schedule()

        val final = schedule.last { it.playoffTier == PlayoffTier.WINNERS_BRACKET }
        assertEquals(17, final.matchupPeriod)
        assertEquals(MatchupOutcome.HOME, final.outcome)
        assertEquals(106.66 to 77.6, final.homeScore to final.awayScore)
        assertEquals(1, final.homeTeam.finalRank)
        assertEquals(2, final.awayTeam.finalRank)
        assertEquals(
            setOf(PlayoffTier.WINNERS_CONSOLATION_LADDER, PlayoffTier.LOSERS_CONSOLATION_LADDER),
            schedule.filter { it.matchupPeriod == 17 && it != final }.map { it.playoffTier }.toSet(),
        )
    }
}
