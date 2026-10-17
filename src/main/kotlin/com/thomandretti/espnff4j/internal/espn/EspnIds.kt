// These are ESPN's own numeric ids, so naming each one as a constant would add nothing.
@file:Suppress("MagicNumber")

package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.LineupSlot
import com.thomandretti.espnff4j.model.Position

internal fun lineupSlot(id: Int): LineupSlot = LINEUP_SLOTS[id] ?: LineupSlot.UNKNOWN

private val LINEUP_SLOTS =
    mapOf(
        0 to LineupSlot.QB,
        2 to LineupSlot.RB,
        3 to LineupSlot.RB_WR,
        4 to LineupSlot.WR,
        5 to LineupSlot.WR_TE,
        6 to LineupSlot.TE,
        7 to LineupSlot.OP,
        8 to LineupSlot.DT,
        9 to LineupSlot.DE,
        10 to LineupSlot.LB,
        11 to LineupSlot.DL,
        12 to LineupSlot.CB,
        13 to LineupSlot.S,
        14 to LineupSlot.DB,
        15 to LineupSlot.DP,
        16 to LineupSlot.DST,
        17 to LineupSlot.K,
        18 to LineupSlot.P,
        19 to LineupSlot.HC,
        20 to LineupSlot.BENCH,
        21 to LineupSlot.IR,
        23 to LineupSlot.FLEX,
    )

internal val POSITIONS =
    mapOf(
        1 to Position.QB,
        2 to Position.RB,
        3 to Position.WR,
        4 to Position.TE,
        5 to Position.K,
        16 to Position.DST,
    )

internal val PRO_TEAMS =
    mapOf(
        0 to "FA",
        1 to "ATL",
        2 to "BUF",
        3 to "CHI",
        4 to "CIN",
        5 to "CLE",
        6 to "DAL",
        7 to "DEN",
        8 to "DET",
        9 to "GB",
        10 to "TEN",
        11 to "IND",
        12 to "KC",
        13 to "LV",
        14 to "LAR",
        15 to "MIA",
        16 to "MIN",
        17 to "NE",
        18 to "NO",
        19 to "NYG",
        20 to "NYJ",
        21 to "PHI",
        22 to "ARI",
        23 to "PIT",
        24 to "LAC",
        25 to "SF",
        26 to "SEA",
        27 to "TB",
        28 to "WSH",
        29 to "CAR",
        30 to "JAX",
        33 to "BAL",
        34 to "HOU",
    )
