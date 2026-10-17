package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.internal.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.internal.espn.EspnRecord
import com.thomandretti.espnff4j.internal.espn.EspnRecordPart
import com.thomandretti.espnff4j.internal.espn.EspnTeam
import com.thomandretti.espnff4j.internal.espn.LeagueSettings
import com.thomandretti.espnff4j.internal.espn.Matchup
import com.thomandretti.espnff4j.internal.espn.MatchupTeam
import com.thomandretti.espnff4j.internal.espn.ScheduleResponse
import com.thomandretti.espnff4j.internal.espn.ScheduleSettings
import com.thomandretti.espnff4j.internal.espn.TeamsResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Response

class FantasyFootballClientTest {
    private val espnClient = mock<EspnFantasyFootballClient>()
    private val client = FantasyFootballClient.create(espnClient, seasonId = 2024, leagueId = 123)

    private fun team(
        id: Int,
        name: String,
    ) = teamWithSeed(id, name, playoffSeed = id)

    private fun teamWithSeed(
        id: Int,
        name: String,
        playoffSeed: Int,
    ) = EspnTeam(
        id = id,
        name = name,
        record = EspnRecord(EspnRecordPart(wins = 0, losses = 0, ties = 0)),
        playoffSeed = playoffSeed,
    )

    private fun matchupTeam(
        teamId: Int,
        score: Double,
    ) = MatchupTeam(teamId = teamId, totalPoints = score)

    private fun stubSchedule(
        response: ScheduleResponse,
        week: Int = 5,
    ) {
        val call = mock<Call<ScheduleResponse>>()
        whenever(espnClient.getScheduleAtWeek(2024, 123, week)).thenReturn(call)
        // retrofit2.await() suspends on the Call; stub the synchronous response path via execute()
        whenever(call.execute()).thenReturn(Response.success(response))
        whenever(call.enqueue(org.mockito.kotlin.any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<retrofit2.Callback<ScheduleResponse>>(0)
            callback.onResponse(call, Response.success(response))
        }
    }

    private fun stubTeams(
        week: Int,
        response: TeamsResponse,
    ) {
        val call = mock<Call<TeamsResponse>>()
        whenever(espnClient.getTeamsAtWeek(2024, 123, week)).thenReturn(call)
        // retrofit2.await() suspends on the Call; stub the synchronous response path via execute()
        whenever(call.execute()).thenReturn(Response.success(response))
        whenever(call.enqueue(org.mockito.kotlin.any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<retrofit2.Callback<TeamsResponse>>(0)
            callback.onResponse(call, Response.success(response))
        }
    }

    @Test
    fun `getMatchupsForWeek returns matchups for the requested week`() =
        runBlocking {
            val teams = listOf(team(1, "Team A"), team(2, "Team B"))
            val schedule =
                listOf(Matchup(matchupPeriodId = 5, home = matchupTeam(1, 95.5), away = matchupTeam(2, 88.25)))
            stubSchedule(ScheduleResponse(schedule = schedule, teams = teams))

            val result = client.getMatchupsForWeek(5)

            assertEquals(1, result.size)
            assertEquals("Team A", result[0].homeTeam.name)
            assertEquals("Team B", result[0].awayTeam.name)
            assertEquals(95.5, result[0].homeScore)
            assertEquals(88.25, result[0].awayScore)
        }

    @Test
    fun `getMatchupsForWeek filters out matchups from other weeks`() =
        runBlocking {
            val teams = listOf(team(1, "Team A"), team(2, "Team B"), team(3, "Team C"), team(4, "Team D"))
            val schedule =
                listOf(
                    Matchup(matchupPeriodId = 5, home = matchupTeam(1, 100.0), away = matchupTeam(2, 90.0)),
                    Matchup(matchupPeriodId = 4, home = matchupTeam(3, 80.0), away = matchupTeam(4, 70.0)),
                )
            stubSchedule(ScheduleResponse(schedule = schedule, teams = teams))

            val result = client.getMatchupsForWeek(5)

            assertEquals(1, result.size)
            assertEquals("Team A", result[0].homeTeam.name)
        }

    @Test
    fun `getMatchupsForWeek filters out bye-week matchups`() =
        runBlocking {
            val teams = listOf(team(1, "Team A"), team(2, "Team B"))
            val schedule =
                listOf(
                    Matchup(matchupPeriodId = 5, home = matchupTeam(1, 100.0), away = matchupTeam(2, 90.0)),
                    Matchup(matchupPeriodId = 5, home = matchupTeam(1, 80.0), away = null),
                )
            stubSchedule(ScheduleResponse(schedule = schedule, teams = teams))

            val result = client.getMatchupsForWeek(5)

            assertEquals(1, result.size)
        }

    @Test
    fun `getMatchupsForWeek skips matchup when home team id is unknown`() =
        runBlocking {
            val teams = listOf(team(2, "Team B"))
            val schedule =
                listOf(Matchup(matchupPeriodId = 5, home = matchupTeam(99, 100.0), away = matchupTeam(2, 90.0)))
            stubSchedule(ScheduleResponse(schedule = schedule, teams = teams))

            val result = client.getMatchupsForWeek(5)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getMatchupsForWeek skips matchup when away team id is unknown`() =
        runBlocking {
            val teams = listOf(team(1, "Team A"))
            val schedule =
                listOf(Matchup(matchupPeriodId = 5, home = matchupTeam(1, 100.0), away = matchupTeam(99, 90.0)))
            stubSchedule(ScheduleResponse(schedule = schedule, teams = teams))

            val result = client.getMatchupsForWeek(5)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getMatchupsForWeek returns empty list when no matchups exist for week`() =
        runBlocking {
            stubSchedule(ScheduleResponse(schedule = emptyList(), teams = emptyList()))

            val result = client.getMatchupsForWeek(5)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getMatchupsForWeek maps both weeks of a two-week playoff round to that round`() =
        runBlocking {
            val teams = listOf(team(1, "Team A"), team(2, "Team B"), team(3, "Team C"), team(4, "Team D"))
            val schedule =
                listOf(
                    Matchup(matchupPeriodId = 14, home = matchupTeam(3, 80.0), away = matchupTeam(4, 70.0)),
                    Matchup(matchupPeriodId = 15, home = matchupTeam(1, 100.0), away = matchupTeam(2, 90.0)),
                )
            val settings =
                LeagueSettings(
                    scheduleSettings = ScheduleSettings(matchupPeriods = mapOf(14 to listOf(14), 15 to listOf(15, 16))),
                )
            stubSchedule(ScheduleResponse(schedule = schedule, teams = teams, settings = settings), week = 16)

            val result = client.getMatchupsForWeek(16)

            assertEquals("Team A", result.single().homeTeam.name)
        }

    @Test
    fun `getStandingsForWeek returns teams sorted by playoff seed`() =
        runBlocking {
            val teams =
                listOf(
                    teamWithSeed(1, "Team C", playoffSeed = 3),
                    teamWithSeed(2, "Team A", playoffSeed = 1),
                    teamWithSeed(3, "Team B", playoffSeed = 2),
                )
            stubTeams(5, TeamsResponse(teams = teams))

            val result = client.getStandingsForWeek(5)

            assertEquals(3, result.size)
            assertEquals(1, result[0].playoffSeed)
            assertEquals(2, result[1].playoffSeed)
            assertEquals(3, result[2].playoffSeed)
        }

    @Test
    fun `getStandingsForWeek returns empty list when no teams`() =
        runBlocking {
            stubTeams(5, TeamsResponse(teams = emptyList()))

            val result = client.getStandingsForWeek(5)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getStandingsForWeek returns single team`() =
        runBlocking {
            val teams = listOf(teamWithSeed(1, "Team A", playoffSeed = 1))
            stubTeams(5, TeamsResponse(teams = teams))

            val result = client.getStandingsForWeek(5)

            assertEquals(1, result.size)
            assertEquals("Team A", result[0].name)
        }

    @Test
    fun `getStandingsForWeek passes correct week to ESPN client`() =
        runBlocking {
            val teams = listOf(teamWithSeed(1, "Team A", playoffSeed = 1))
            stubTeams(7, TeamsResponse(teams = teams))

            val result = client.getStandingsForWeek(7)

            assertEquals(1, result.size)
            assertEquals("Team A", result[0].name)
        }
}
