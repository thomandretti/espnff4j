package com.thomandretti.espnff4j.internal.espn

internal data class ScheduleResponse(
    val schedule: List<Matchup>,
    val teams: List<EspnTeam>,
    val members: List<Member> = emptyList(),
    val settings: LeagueSettings? = null,
)
