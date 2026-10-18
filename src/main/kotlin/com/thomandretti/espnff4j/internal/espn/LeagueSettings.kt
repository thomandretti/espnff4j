package com.thomandretti.espnff4j.internal.espn

internal data class LeagueSettings(
    val name: String = "",
    val size: Int = 0,
    val scheduleSettings: ScheduleSettings? = null,
    val scoringSettings: ScoringSettings? = null,
    val draftSettings: DraftSettings? = null,
)

internal data class ScheduleSettings(
    // Matchup period -> the NFL weeks (scoring periods) it spans. One week each in the regular
    // season, but a league can stretch a playoff round over two, e.g. {"15": [15, 16]}.
    val matchupPeriods: Map<Int, List<Int>> = emptyMap(),
    // Regular-season matchup periods only; playoff rounds follow.
    val matchupPeriodCount: Int = 0,
    val playoffTeamCount: Int = 0,
) {
    internal fun matchupPeriodFor(scoringPeriod: Int): Int? =
        matchupPeriods.entries.firstOrNull { scoringPeriod in it.value }?.key
}

internal data class ScoringSettings(
    // e.g. H2H_POINTS, H2H_CATEGORY, TOTAL_POINTS
    val scoringType: String? = null,
)

internal data class DraftSettings(
    // SNAKE, AUCTION, ...
    val type: String? = null,
    // Epoch milliseconds the draft was scheduled to start.
    val date: Long? = null,
)
