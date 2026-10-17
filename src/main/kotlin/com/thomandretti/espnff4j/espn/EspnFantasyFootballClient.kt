package com.thomandretti.espnff4j.espn

import com.thomandretti.espnff4j.model.ScheduleResponse
import com.thomandretti.espnff4j.model.TeamsResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface EspnFantasyFootballClient {
    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getTeamsAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Long,
        @Query("view") vararg views: String = arrayOf("mRoster", "mTeam"),
    ): Call<TeamsResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getScheduleAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Long,
        // mMatchupScore is a superset of mMatchup: it adds per-week rosters and player projections.
        @Query("view") vararg views: String = arrayOf("mMatchupScore", "mTeam"),
    ): Call<ScheduleResponse>
}
