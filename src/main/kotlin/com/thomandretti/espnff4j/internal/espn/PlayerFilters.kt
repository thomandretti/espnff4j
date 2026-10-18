package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.Position

/**
 * Builders for the X-Fantasy-Filter header that kona_player_info needs. ESPN rejects a `limit`
 * without a sort. Every filter limits the stats to the season total (scoring period 0) and the weeks
 * asked for. Leaving the stats filter out does not bring the whole season: ESPN then sends one week.
 */
internal object PlayerFilters {
    // ESPN encodes a stat to sort by as source + split + season + week, so "11" + "2026" + "6" is the
    // single-week projection for week 6 of 2026.
    private const val WEEK_PROJECTION_SORT_PREFIX = "11"

    // The most scoring periods an NFL season has: 17 games and a bye.
    private const val MAX_NFL_WEEK = 18

    fun byIds(
        ids: Collection<Int>,
        week: Int?,
    ): String = players(listOfNotNull(week)) { put("filterIds", values(ids.toList())) }

    // Every week of the season, played or not, which is about 20 KB a player.
    fun wholeSeason(id: Int): String = players((1..MAX_NFL_WEEK).toList()) { put("filterIds", values(listOf(id))) }

    fun freeAgents(
        seasonId: Int,
        week: Int,
        position: Position?,
        limit: Int,
    ): String =
        players(listOf(week)) {
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
        players(listOf(week)) {
            put("filterName", mapOf("value" to name))
            put("limit", limit)
            put("sortPercOwned", sort())
        }

    private fun players(
        weeks: List<Int>,
        filters: MutableMap<String, Any>.() -> Unit,
    ): String {
        val players =
            buildMap(filters) + ("filterStatsForCurrentSeasonScoringPeriodId" to values(listOf(0) + weeks))
        return OBJECT_MAPPER.writeValueAsString(mapOf("players" to players))
    }

    private fun values(values: List<Any>) = mapOf("value" to values)

    private fun sort() = mapOf("sortPriority" to 1, "sortAsc" to false)
}
