package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.model.Draft
import com.thomandretti.espnff4j.model.DraftType
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

/**
 * Pins the draft against trimmed captures of the real 2025 responses: `mDraftDetail,mTeam,mSettings`
 * with all 160 picks, then `kona_player_info` for the first two rounds' players only, so later picks
 * also exercise the unknown-player fallback. Team and member names and account ids are anonymised.
 */
class EspnDraftResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/$name")) { "missing ESPN fixture $name" }.readText()

    private fun draft(): Draft {
        server.enqueue(MockResponse().setBody(fixture("espn-draft-2025.json")))
        server.enqueue(MockResponse().setBody(fixture("espn-draft-players-2025.json")))
        val client = EspnFantasyFootball.client(2025, 12345, baseUrl = server.url("/seasons/").toString())
        return runBlocking { client.getDraft() }
    }

    @Test
    fun `a completed snake draft with every pick in order`() {
        val draft = draft()

        assertEquals(DraftType.SNAKE, draft.type)
        assertTrue(draft.isComplete)
        assertEquals(Instant.ofEpochMilli(1756771200000), draft.scheduledAt)
        assertEquals(Instant.ofEpochMilli(1756775441856), draft.completedAt)
        assertEquals((1..160).toList(), draft.picks.map { it.overallPick })
        assertTrue(draft.picks.none { it.isKeeper || it.auctionPrice != null })
    }

    @Test
    fun `picks name the player and their season total, and snake back each round`() {
        val picks = draft().picks

        val first = picks[0]
        assertEquals("Bijan Robinson", first.player.name)
        assertEquals(370.8, first.player.seasonPoints)
        assertNull(first.player.points)
        assertEquals("Team J", first.team.name)
        // The tenth pick of round 1 is also the first of round 2.
        assertEquals(picks[9].team, picks[10].team)
        assertEquals(2 to 1, picks[10].round to picks[10].roundPick)
        assertEquals("De'Von Achane", picks[10].player.name)
    }

    @Test
    fun `players the lookup doesn't return are kept as unknown`() {
        val picks = draft().picks

        assertTrue(picks.take(20).none { it.player.name.startsWith("Unknown") })
        assertEquals("Unknown player 4596448", picks[20].player.name)
    }

    @Test
    fun `players are looked up by id for the season as a whole`() {
        draft()

        server.takeRequest()
        val lookup = server.takeRequest()
        assertTrue("scoringPeriodId=0" in requireNotNull(lookup.path), lookup.path)
        val filter = requireNotNull(lookup.getHeader("X-Fantasy-Filter"))
        assertTrue(""""filterStatsForCurrentSeasonScoringPeriodId":{"value":[0]}""" in filter, filter)
    }
}
