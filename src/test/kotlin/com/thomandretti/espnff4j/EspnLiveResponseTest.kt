package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.internal.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.internal.espn.OBJECT_MAPPER
import com.thomandretti.espnff4j.internal.espn.ScheduleResponse
import com.thomandretti.espnff4j.model.MatchupOutcome
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Guards the model against the real ESPN payload. The fixture is a trimmed capture of a live
 * `mMatchupScore` response from a ten-team league (team names anonymised), so a field ESPN renames or
 * drops shows up here rather than as silently missing data downstream.
 */
class EspnLiveResponseTest {
    private fun client(): FantasyFootballClient {
        val parsed = response()
        val call = mock<Call<ScheduleResponse>>()
        // retrofit2.await() resumes from enqueue(), not execute() — stubbing only the latter hangs.
        whenever(call.enqueue(any())).thenAnswer { invocation ->
            invocation.getArgument<Callback<ScheduleResponse>>(0).onResponse(call, Response.success(parsed))
        }
        val espn = mock<EspnFantasyFootballClient>()
        whenever(espn.getScheduleAtWeek(any(), any(), any(), anyVararg())).thenReturn(call)
        return FantasyFootballClient.create(espn, seasonId = 2026, leagueId = 12345)
    }

    private fun response(): ScheduleResponse =
        OBJECT_MAPPER.readValue(
            checkNotNull(javaClass.getResourceAsStream("/espn-live-week1.json")) {
                "missing ESPN fixture"
            },
            ScheduleResponse::class.java,
        )

    @Test
    fun `real payload deserializes into the schedule model`() {
        val parsed = response()
        assertEquals(5, parsed.schedule.size)
        assertEquals(10, parsed.teams.size)
    }

    @Test
    fun `ESPN publishes a win probability for both sides of every matchup`() {
        response().schedule.forEach { matchup ->
            val home = requireNotNull(matchup.home.winProbability)
            val away = requireNotNull(requireNotNull(matchup.away).winProbability)
            assertEquals(1.0, home + away, 0.001, "probabilities should sum to 1")
        }
    }

    @Test
    fun `ESPN publishes both projection figures for the current week`() {
        response().schedule.forEach { matchup ->
            listOfNotNull(matchup.home, matchup.away).forEach { side ->
                assertNotNull(side.totalProjectedPoints, "missing totalProjectedPoints")
                assertNotNull(side.totalProjectedPointsLive, "missing totalProjectedPointsLive")
            }
        }
    }

    @Test
    fun `an unplayed week parses as undecided`() {
        response().schedule.forEach { assertEquals(MatchupOutcome.UNDECIDED, it.outcome) }
    }

    @Test
    fun `a live week reports its running score in totalPointsLive, not totalPoints`() {
        // The capture is a week in progress: ESPN holds totalPoints at zero all week and only fills
        // it in once the week settles, so reading it alone shows 0.00 for every team.
        val sides = response().schedule.flatMap { listOfNotNull(it.home, it.away) }
        assertTrue(sides.all { it.totalPoints == 0.0 }, "fixture should be a live week")
        assertTrue(
            sides.any { (it.totalPointsLive ?: 0.0) > 0.0 },
            "fixture should have at least one team on the board",
        )
    }

    @Test
    fun `the client surfaces the live score rather than the settled zero`() =
        runBlocking {
            val scores = client().getMatchupsForWeek(1).flatMap { listOf(it.homeScore, it.awayScore) }
            assertTrue(scores.any { it > 0.0 }, "expected live points to reach MatchupInfo, got $scores")
        }

    @Test
    fun `teams carry the abbreviation the overview bar labels itself with`() {
        response().teams.forEach { assertTrue(!it.abbrev.isNullOrBlank(), "no abbrev for ${it.name}") }
    }
}
