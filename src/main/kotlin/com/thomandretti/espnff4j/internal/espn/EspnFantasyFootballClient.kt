package com.thomandretti.espnff4j.internal.espn

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
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
        // mTeam alone carries records and seeds; adding mRoster grows the response from ~40 KB to ~3 MB.
        @Query("view") vararg views: String = arrayOf("mTeam"),
    ): Call<TeamsResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getRostersAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        @Query("view") vararg views: String = arrayOf("mRoster", "mTeam"),
    ): Call<RostersResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getScheduleAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        // mMatchupScore is a superset of mMatchup: it adds per-week rosters and player projections.
        // mSettings carries the scoring period -> matchup period mapping.
        @Query("view") vararg views: String = arrayOf("mMatchupScore", "mTeam", "mSettings"),
    ): Call<ScheduleResponse>

    // Not a league endpoint: the same NFL schedule for every league, readable without cookies.
    @GET("{seasonId}")
    fun getProTeamSchedules(
        @Path("seasonId") seasonId: Int,
        @Query("view") vararg views: String = arrayOf("proTeamSchedules_wl"),
    ): Call<ProTeamsResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getDraft(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        // mSettings carries the draft type and date.
        @Query("view") vararg views: String = arrayOf("mDraftDetail", "mTeam", "mSettings"),
    ): Call<DraftResponse>

    // With no scoringPeriodId, mMatchupScore covers the whole season in ~200 KB. mMatchup, oddly, is ten
    // times that: it adds every side's per-stat breakdown.
    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getSeasonSchedule(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("view") vararg views: String = arrayOf("mMatchupScore", "mTeam", "mSettings"),
    ): Call<ScheduleResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getBoxScoresAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        // mScoreboard fills in who each lineup entry is; without it ESPN sends only the stats, roughly
        // halving the response.
        @Query("view") vararg views: String = arrayOf("mMatchupScore", "mScoreboard", "mTeam", "mSettings"),
    ): Call<ScheduleResponse>

    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getTransactionsAtWeek(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        @Query("view") vararg views: String = arrayOf("mTransactions2", "mTeam"),
    ): Call<TransactionsResponse>

    // kona_player_info searches every player in the game, narrowed by the filter (see PlayerFilters). Add
    // mTeam to resolve onTeamId to a team.
    @GET("{seasonId}/segments/0/leagues/{leagueId}")
    fun getPlayers(
        @Path("seasonId") seasonId: Int,
        @Path("leagueId") leagueId: Int,
        @Query("scoringPeriodId") scoringPeriodId: Int,
        @Header("X-Fantasy-Filter") filter: String,
        @Query("view") vararg views: String = arrayOf("kona_player_info", "mTeam"),
    ): Call<PlayersResponse>
}
