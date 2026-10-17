package com.thomandretti.espnff4j.model

data class RecordPart(
    val losses: Int,
    val wins: Int,
    val ties: Int,
)

data class Record(
    val overall: RecordPart,
    // Null where ESPN omits the split.
    val home: RecordPart? = null,
    val away: RecordPart? = null,
    val division: RecordPart? = null,
)
