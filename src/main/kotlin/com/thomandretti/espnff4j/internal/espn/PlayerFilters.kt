package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.Position

/**
 * Builders for the X-Fantasy-Filter header that kona_player_info needs. ESPN rejects a `limit`
 * without a sort. Every filter limits the stats to the season total (scoring period 0) and the requested
 * week; otherwise each player brings every week of the season.
 */
internal object PlayerFilters {
    // ESPN encodes a stat to sort by as source + split + season + week, so "11" + "2026" + "6" is the
    // single-week projection for week 6 of 2026.
    private const val WEEK_PROJECTION_SORT_PREFIX = "11"

    fun byIds(
        ids: Collection<Int>,
        week: Int?,
    ): String = players(week) { put("filterIds", values(ids.toList())) }

    fun freeAgents(
        seasonId: Int,
        week: Int,
        position: Position?,
        limit: Int,
    ): String =
        players(week) {
            put("filterStatus", values(listOf("FREEAGENT", "WAIVERS")))
            position?.let { put("filterSlotIds", values(listOf(slotId(it)))) }
            put("limit", limit)
            put("sortAppliedStatTotal", sort() + ("value" to "$WEEK_PROJECTION_SORT_PREFIX$seasonId$week"))
        }

    fun byName(
        name: String,
        week: Int,
        limit: Int,
    ): String =
        players(week) {
            put("filterName", mapOf("value" to name))
            put("limit", limit)
            put("sortPercOwned", sort())
        }

    private fun players(
        week: Int?,
        filters: MutableMap<String, Any>.() -> Unit,
    ): String {
        val players =
            buildMap(filters) + ("filterStatsForCurrentSeasonScoringPeriodId" to values(listOfNotNull(0, week)))
        return OBJECT_MAPPER.writeValueAsString(mapOf("players" to players))
    }

    private fun values(values: List<Any>) = mapOf("value" to values)

    private fun sort() = mapOf("sortPriority" to 1, "sortAsc" to false)
}
