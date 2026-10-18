package com.thomandretti.espnff4j.model

import java.time.Instant

/** A real NFL game. Teams are abbreviations, as in [RosterPlayer.proTeam] and [Player.proTeam]. */
data class NflGame(
    // ESPN's game id.
    val id: Long,
    val week: Int,
    // A placeholder time while [isStartTimeTbd], e.g. before the NFL sets late-season flex games.
    val kickoff: Instant,
    val isStartTimeTbd: Boolean,
    val homeTeam: String,
    val awayTeam: String,
    // ESPN has marked the game's stats official, which comes some time after the final whistle. A game
    // that has kicked off but isn't official yet may be in progress or just finished.
    val statsOfficial: Boolean,
) {
    fun involves(team: String): Boolean = team == homeTeam || team == awayTeam

    @JvmOverloads
    fun hasKickedOff(now: Instant = Instant.now()): Boolean = !now.isBefore(kickoff)
}
