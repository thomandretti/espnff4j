package com.thomandretti.espnff4j.model

import java.time.Instant

/** A completed roster move: a free-agent pickup, a waiver claim or a trade. */
data class Transaction(
    val id: String,
    val type: TransactionType,
    // The team that made the move, or proposed the trade.
    val team: Team,
    // When it took effect; for a waiver claim, when waivers were processed.
    val date: Instant,
    val moves: List<PlayerMove>,
    // The FAAB bid for a waiver claim; null for other types.
    val waiverBid: Int?,
)

/** One player changing hands. Free agency and waivers are a null team. */
data class PlayerMove(
    val type: MoveType,
    // The player as ESPN reports them now, which may differ from when the move was made.
    val player: Player,
    val fromTeam: Team?,
    val toTeam: Team?,
)

enum class TransactionType {
    FREE_AGENT,
    WAIVER,
    TRADE,
}

enum class MoveType {
    ADD,
    DROP,
    TRADE,
}
