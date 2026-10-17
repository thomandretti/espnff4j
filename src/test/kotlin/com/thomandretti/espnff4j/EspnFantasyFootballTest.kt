package com.thomandretti.espnff4j

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

class EspnFantasyFootballTest {
    private val server = MockWebServer()

    private val teamsBody =
        """{"teams":[{"id":1,"name":"Team A","playoffSeed":1,"record":{"overall":{"wins":1,"losses":0,"ties":0}}}]}"""

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun client(credentials: EspnCredentials? = null) =
        EspnFantasyFootball.client(
            seasonId = 2026,
            leagueId = 12345,
            credentials = credentials,
            baseUrl = server.url("/seasons/").toString(),
        )

    @Test
    fun `requests the league and season path with the requested week`() =
        runBlocking {
            server.enqueue(MockResponse().setBody(teamsBody))

            val teams = client().getStandingsForWeek(3L)

            assertEquals("Team A", teams.single().name)
            val path = requireNotNull(server.takeRequest().path)
            assertTrue(path.startsWith("/seasons/2026/segments/0/leagues/12345?"), path)
            assertTrue("scoringPeriodId=3" in path, path)
        }

    @Test
    fun `the second week of a two-week playoff round returns that round's matchups`() =
        runBlocking {
            val team = """"record":{"overall":{"wins":0,"losses":0,"ties":0}},"playoffSeed":1"""
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"teams":[{"id":1,"name":"Team A",$team},{"id":2,"name":"Team B",$team}],
                     "schedule":[{"matchupPeriodId":15,"home":{"teamId":1,"totalPoints":0.0},
                                  "away":{"teamId":2,"totalPoints":0.0}}],
                     "settings":{"scheduleSettings":{"matchupPeriods":{"14":[14],"15":[15,16]}}}}
                    """.trimIndent(),
                ),
            )

            val matchups = client().getMatchupsForWeek(16L)

            assertEquals("Team A", matchups.single().homeTeam.name)
            val path = requireNotNull(server.takeRequest().path)
            assertTrue("view=mSettings" in path, path)
        }

    @Test
    fun `sends no cookies when no credentials are given`() =
        runBlocking {
            server.enqueue(MockResponse().setBody(teamsBody))

            client().getStandingsForWeek(1L)

            assertNull(server.takeRequest().getHeader("Cookie"))
        }

    @Test
    fun `configure hook can add interceptors`() =
        runBlocking {
            server.enqueue(MockResponse().setBody(teamsBody))
            var intercepted = false
            val client =
                EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString()) {
                    addInterceptor { chain ->
                        intercepted = true
                        chain.proceed(chain.request())
                    }
                }

            client.getStandingsForWeek(1L)

            assertTrue(intercepted)
        }

    @Test
    fun `credentials never appear in toString`() {
        val text = EspnCredentials("{SECRET-SWID}", "secret-s2").toString()
        assertFalse("SECRET" in text || "secret" in text, text)
    }
}
