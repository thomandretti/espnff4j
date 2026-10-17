package com.thomandretti.espnff4j.internal.espn

internal data class TeamsResponse(
    val teams: List<EspnTeam>,
    val members: List<Member> = emptyList(),
)
