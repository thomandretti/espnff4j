package com.thomandretti.espnff4j.model

data class Team(
    val id: Int = 0,
    val name: String,
    val record: Record,
    val playoffSeed: Int,
    val abbrev: String? = null,
)
