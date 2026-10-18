package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.WaiverSettings
import java.time.DayOfWeek

internal data class LeagueSettings(
    val name: String = "",
    val size: Int = 0,
    val scheduleSettings: ScheduleSettings? = null,
    val scoringSettings: ScoringSettings? = null,
    val draftSettings: DraftSettings? = null,
    val acquisitionSettings: AcquisitionSettings? = null,
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

internal data class AcquisitionSettings(
    // WAIVERS_TRADITIONAL, ...
    val acquisitionType: String? = null,
    val isUsingAcquisitionBudget: Boolean = false,
    // The FAAB budget, sent even when the league doesn't use one.
    val acquisitionBudget: Int = 0,
    val minimumBid: Int = 0,
    // Season limit on pickups; -1 is none.
    val acquisitionLimit: Int = -1,
    val waiverHours: Int = 0,
    // MONDAY, TUESDAY, ...
    val waiverProcessDays: List<String> = emptyList(),
) {
    fun toWaiverSettings() =
        WaiverSettings(
            usesFaab = isUsingAcquisitionBudget,
            faabBudget = acquisitionBudget.takeIf { isUsingAcquisitionBudget },
            minimumBid = minimumBid.takeIf { isUsingAcquisitionBudget },
            acquisitionLimit = acquisitionLimit.takeIf { it >= 0 },
            waiverHours = waiverHours,
            processDays =
                waiverProcessDays
                    .mapNotNull { day -> DayOfWeek.entries.firstOrNull { it.name == day } }
                    .sorted(),
        )
}
