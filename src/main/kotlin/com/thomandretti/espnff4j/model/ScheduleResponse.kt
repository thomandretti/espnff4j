package com.thomandretti.espnff4j.model

internal data class ScheduleResponse(
    val schedule: List<Matchup>,
    val teams: List<Team>,
    val settings: LeagueSettings? = null,
)
