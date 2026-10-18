package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.InjuryStatus
import com.thomandretti.espnff4j.model.PlayerDetail
import com.thomandretti.espnff4j.model.PlayerStatus
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFailsWith

/**
 * Pins player detail against trimmed captures for Breece Hall, ruled out with a quad injury, in week 6
 * of the 2026 season: a `kona_player_info,mTeam` lookup asking for every week of the season (team and
 * member anonymised), and the three latest items of his ESPN news feed (`news/players`).
 */
class EspnPlayerDetailResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun client() = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/$name")) { "missing ESPN fixture $name" }.readText()

    // The two requests run concurrently, so answer by path rather than by order.
    private fun serve(
        players: String = fixture("espn-player-2026-week6.json"),
        news: String = fixture("espn-player-news-week6.json"),
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setBody(if ("/news/players" in request.path.orEmpty()) news else players)
            }
    }

    private fun hall(newsLimit: Int = 5): PlayerDetail {
        serve()
        return requireNotNull(runBlocking { client().getPlayer(4427366, newsLimit) })
    }

    @Test
    fun `the player is as ESPN reports them now, with no single week`() {
        val player = hall().player

        assertEquals("Breece Hall", player.name)
        assertEquals("NYJ", player.proTeam)
        assertEquals(InjuryStatus.OUT, player.injuryStatus)
        assertEquals(PlayerStatus.ON_TEAM, player.status)
        assertEquals("Team B", player.team?.name)
        assertEquals(42.5, player.seasonPoints)
        assertNull(player.points)
        assertNull(player.projectedPoints)
    }

    @Test
    fun `weeks run through the whole season, with points only for the weeks played`() {
        val weeks = hall().weeks

        assertEquals((1..18).toList(), weeks.map { it.week })
        assertEquals(listOf(19.8, 14.2, 8.5, 0.0, 0.0), weeks.mapNotNull { it.points })
        assertEquals(16.4996, requireNotNull(weeks[0].projectedPoints), 0.0001)
        // Ruled out for weeks 4 to 6, and on bye in week 13: ESPN projects nothing, but still sends a figure.
        assertEquals(listOf(4, 5, 6, 13), weeks.filter { it.projectedPoints == 0.0 }.map { it.week })
        assertNull(weeks.single { it.week == 6 }.points)
    }

    @Test
    fun `news is newest first, with the injury in the headline`() {
        val news = hall().news

        assertEquals(3, news.size)
        val latest = news.first()
        assertEquals(Instant.parse("2026-10-16T15:29:50Z"), latest.published)
        assertTrue(latest.headline.startsWith("Hall (quadriceps) is out for Sunday's game"), latest.headline)
        assertTrue(requireNotNull(latest.story).contains("Braelon Allen"), latest.story)
        assertEquals("Rotowire", latest.source)
        assertEquals(news.sortedByDescending { it.published }, news)
    }

    @Test
    fun `the lookup asks for every week and the news for the limit`() {
        hall(newsLimit = 3)

        val requests = List(2) { server.takeRequest() }.associateBy { "/news/" in it.path.orEmpty() }
        val news = requireNotNull(requests[true])
        assertEquals("/seasons/news/players?playerId=4427366&limit=3", news.path)
        assertNull(news.getHeader("Cookie"))
        val lookup = requireNotNull(requests[false])
        assertTrue("scoringPeriodId=0" in lookup.path.orEmpty(), lookup.path)
        val filter = requireNotNull(lookup.getHeader("X-Fantasy-Filter"))
        assertTrue(""""filterIds":{"value":[4427366]}""" in filter, filter)
        val everyWeek = (0..18).joinToString(",")
        assertTrue(""""filterStatsForCurrentSeasonScoringPeriodId":{"value":[$everyWeek]}""" in filter, filter)
    }

    @Test
    fun `a news limit of 0 skips the news request`() {
        val detail = hall(newsLimit = 0)

        assertTrue(detail.news.isEmpty())
        assertEquals(1, server.requestCount)
        assertFailsWith<IllegalArgumentException> { runBlocking { client().getPlayer(4427366, newsLimit = -1) } }
    }

    @Test
    fun `a player ESPN doesn't know is null`() {
        // ESPN's real answers for an unknown id: no players, and an empty feed.
        serve(players = """{"players":[]}""", news = """{"feed":[],"resultsCount":0}""")

        assertNull(runBlocking { client().getPlayer(1) })
    }
}
