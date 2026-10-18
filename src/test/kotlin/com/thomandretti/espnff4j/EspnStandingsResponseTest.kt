package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.RecordPart
import com.thomandretti.espnff4j.model.Streak
import com.thomandretti.espnff4j.model.StreakType
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.TransactionCounts
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins standings against a trimmed capture of a real `mTeam` response for week 6 of the 2026 season,
 * with team and member names, logos and account ids anonymised.
 */
class EspnStandingsResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun standings(): List<Team> {
        val fixture = checkNotNull(javaClass.getResource("/espn-teams-week6.json")) { "missing ESPN fixture" }
        server.enqueue(MockResponse().setBody(fixture.readText()))
        val client = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())
        return runBlocking { client.getStandingsForWeek(6) }
    }

    @Test
    fun `the leader's record, points and streak`() {
        val leader = standings().first()

        assertEquals("Team A", leader.name)
        assertEquals(RecordPart(losses = 0, wins = 5, ties = 0), leader.record.overall)
        assertEquals(RecordPart(losses = 0, wins = 4, ties = 0), leader.record.home)
        assertEquals(RecordPart(losses = 0, wins = 1, ties = 0), leader.record.away)
        assertEquals(RecordPart(losses = 0, wins = 5, ties = 0), leader.record.division)
        assertEquals(596.12, leader.pointsFor)
        assertEquals(458.16, leader.pointsAgainst)
        assertEquals(Streak(StreakType.WIN, 5), leader.streak)
        assertEquals(0, leader.divisionId)
    }

    @Test
    fun `points ESPN sums in floating point are rounded to the cent`() {
        val teams = standings().associateBy { it.name }

        // ESPN sends 680.4599999999999 and 599.8000000000001.
        assertEquals(680.46, teams.getValue("Team B").pointsFor)
        assertEquals(599.8, teams.getValue("Team H").pointsFor)
    }

    @Test
    fun `owners are resolved to league members, co-owners included`() {
        val teams = standings().associateBy { it.name }

        val owner = teams.getValue("Team A").owners.single()
        assertEquals("owner10", owner.displayName)
        assertEquals("{00000000-0000-4000-8000-000000000010}", owner.id)
        assertEquals(3, teams.getValue("Team C").owners.size)
    }

    @Test
    fun `losing streaks and seeds come through`() {
        val teams = standings()

        assertEquals((1..10).toList(), teams.map { it.playoffSeed })
        assertEquals(Streak(StreakType.LOSS, 3), teams.single { it.name == "Team C" }.streak)
    }

    @Test
    fun `waiver order and season transaction counts`() {
        val teams = standings()

        assertEquals((1..10).toSet(), teams.mapNotNull { it.waiverRank }.toSet())
        val busiest = teams.single { it.name == "Team H" }
        assertEquals(4, busiest.waiverRank)
        assertEquals(
            TransactionCounts(
                acquisitions = 13,
                drops = 12,
                trades = 0,
                movesToActive = 13,
                movesToIr = 2,
                faabSpent = 0,
            ),
            busiest.transactionCounts,
        )
    }
}
