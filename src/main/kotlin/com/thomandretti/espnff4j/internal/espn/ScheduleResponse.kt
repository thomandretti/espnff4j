package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.Team

internal data class ScheduleResponse(
    val schedule: List<Matchup>,
    val teams: List<Team>,
    val settings: LeagueSettings? = null,
)
