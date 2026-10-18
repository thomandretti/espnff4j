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

/** A trade that hasn't gone through: offered and awaiting the other team, or accepted and under review. */
data class PendingTrade(
    // ESPN's id for the offer.
    val id: String,
    val status: PendingTradeStatus,
    val proposingTeam: Team,
    // The players changing hands, then any drop the accepting team made to make room.
    val moves: List<PlayerMove>,
    val proposedAt: Instant,
    // Null until the other team accepts.
    val acceptedAt: Instant?,
    // ESPN's deadline: an offer lapses then, and an accepted trade's review period ends, after which the
    // trade goes through when ESPN next processes transactions.
    val expiresAt: Instant?,
) {
    /** Every team the trade involves, the proposer first. */
    val teams: List<Team>
        get() = (listOf(proposingTeam) + moves.flatMap { listOfNotNull(it.fromTeam, it.toTeam) }).distinctBy { it.id }
}

enum class PendingTradeStatus {
    // Waiting for the other team to accept or decline.
    OFFERED,

    // Accepted, and waiting out the league's review period (and any votes).
    UNDER_REVIEW,
}
