package com.thomandretti.espnff4j.model

data class LeagueSettings(
    val scheduleSettings: ScheduleSettings? = null,
)

data class ScheduleSettings(
    // Matchup period -> the NFL weeks (scoring periods) it spans. One week each in the regular
    // season, but a league can stretch a playoff round over two, e.g. {"15": [15, 16]}.
    val matchupPeriods: Map<Int, List<Int>> = emptyMap(),
) {
    internal fun matchupPeriodFor(scoringPeriod: Int): Int? =
        matchupPeriods.entries.firstOrNull { scoringPeriod in it.value }?.key
}
