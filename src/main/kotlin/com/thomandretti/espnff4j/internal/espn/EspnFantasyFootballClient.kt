package com.thomandretti.espnff4j.internal.espn

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface EspnFantasyFootballClient {
    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getLeague(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("view") vararg views: String = arrayOf("mSettings"),
    ): Call<LeagueResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getTeamsAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        @Query("view") vararg views: String = arrayOf("mRoster", "mTeam"),
    ): Call<TeamsResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getScheduleAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        // mMatchupScore is a superset of mMatchup: it adds per-week rosters and player projections.
        // mSettings carries the scoring period -> matchup period mapping.
        @Query("view") vararg views: String = arrayOf("mMatchupScore", "mTeam", "mSettings"),
    ): Call<ScheduleResponse>
}
