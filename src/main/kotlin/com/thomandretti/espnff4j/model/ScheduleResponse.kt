package com.thomandretti.espnff4j.model

data class ScheduleResponse(
    val schedule: List<Matchup>,
    val teams: List<Team>,
    val settings: LeagueSettings? = null,
)
