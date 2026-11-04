package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.LineupSlot
import com.thomandretti.espnff4j.model.MoveType
import com.thomandretti.espnff4j.model.PendingTrade
import com.thomandretti.espnff4j.model.PendingTradeStatus
import com.thomandretti.espnff4j.model.Player
import com.thomandretti.espnff4j.model.PlayerMove
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.Transaction
import com.thomandretti.espnff4j.model.TransactionType
import java.time.Instant

internal data class TransactionsResponse(
    // Every transaction proposed in the requested week, including lineup changes, failed waiver
    // claims and trade offers that never went through.
    val transactions: List<EspnTransaction> = emptyList(),
    val teams: List<EspnTeam> = emptyList(),
    val members: List<Member> = emptyList(),
)

internal data class EspnTransaction(
    val id: String,
    // FREEAGENT, WAIVER, TRADE_*, ROSTER (lineup moves), FUTURE_ROSTER, DRAFT, ...
    val type: String,
    // EXECUTED, PENDING, CANCELED, FAILED_*, ... Absent on the TRADE_ACCEPT recorded while a trade awaits
    // review.
    val status: String? = null,
    // The team that made it; for an executed trade, the team that proposed it.
    val teamId: Int = 0,
    val bidAmount: Int = 0,
    // Epoch milliseconds. Waivers are proposed earlier and processed overnight.
    val proposedDate: Long? = null,
    val processDate: Long? = null,
    // A trade offer's deadline: it lapses then unless accepted, and an accepted trade's review period ends.
    val expirationDate: Long? = null,
    // The TRADE_PROPOSAL that every later record of a trade points back to.
    val relatedTransactionId: String? = null,
    val items: List<EspnTransactionItem> = emptyList(),
)

internal data class EspnTransactionItem(
    // ADD, DROP, TRADE, LINEUP, DRAFT
    val type: String,
    // Negative for a team defense: -16000 minus its NFL team id.
    val playerId: Int,
    // 0 is free agency or waivers.
    val fromTeamId: Int = 0,
    val toTeamId: Int = 0,
    // LINEUP items move a player between these; ADD and DROP use -1 for off the roster.
    val fromLineupSlotId: Int = -1,
    val toLineupSlotId: Int = -1,
)

/** Executed free-agent pickups, waiver claims, trades, drops on their own and IR moves, and failed waiver claims. */
internal fun TransactionsResponse.completed(): List<EspnTransaction> =
    transactions.filter { it.transactionType() != null && it.moves().isNotEmpty() }

internal fun EspnTransaction.toTransaction(
    teamsById: Map<Int, Team>,
    playersById: Map<Int, Player>,
): Transaction? {
    val type = transactionType()
    val team = teamsById[teamId]
    if (type == null || team == null) return null
    return Transaction(
        id = id,
        type = type,
        team = team,
        date = Instant.ofEpochMilli(processDate ?: proposedDate ?: 0),
        moves = moves().map { it.toPlayerMove(teamsById, playersById) },
        waiverBid = bidAmount.takeIf { type == TransactionType.WAIVER || type == TransactionType.FAILED_WAIVER },
    )
}

private fun EspnTransaction.transactionType(): TransactionType? =
    when {
        // FAILED_INVALIDPLAYERSOURCE, when the player went to a team ahead in the order, and others.
        type == "WAIVER" && status?.startsWith("FAILED_") == true -> TransactionType.FAILED_WAIVER
        status != "EXECUTED" -> null
        type == "FREEAGENT" -> TransactionType.FREE_AGENT
        type == "WAIVER" -> TransactionType.WAIVER
        // A trade leaves a chain: the TRADE_PROPOSAL (PENDING, later CANCELED), a TRADE_ACCEPT with no
        // status, and league votes (TRADE_UPHOLD, TRADE_VETO) or a TRADE_DECLINE, which are EXECUTED but move
        // no players. When the review period ends, often the next week, ESPN adds a second TRADE_ACCEPT that
        // is EXECUTED and carries the players, plus any drop the receiving team made to fit them.
        type == "TRADE_ACCEPT" -> TransactionType.TRADE
        // Usually only lineup changes, which moves() leaves out, so most of these are not completed().
        type == "ROSTER" -> TransactionType.ROSTER
        else -> null
    }

private fun EspnTransaction.moves(): List<EspnTransactionItem> = items.filter { it.moveType() != null }

// LINEUP items are slot changes, on their own or attached to a pickup or drop. Only IR moves count.
private fun EspnTransactionItem.moveType(): MoveType? =
    when {
        type == "LINEUP" && lineupSlot(toLineupSlotId) == LineupSlot.IR -> MoveType.TO_IR
        type == "LINEUP" && lineupSlot(fromLineupSlotId) == LineupSlot.IR -> MoveType.FROM_IR
        else -> MoveType.entries.firstOrNull { it.name == type }
    }

/** A trade offer that is still open, with the TRADE_ACCEPT recorded if the other team has accepted it. */
internal data class OpenTrade(
    val proposal: EspnTransaction,
    val acceptance: EspnTransaction?,
)

/**
 * Trade offers still waiting on the other team or on league review at [now]. ESPN never updates a
 * TRADE_PROPOSAL: a cancellation is a second, CANCELED proposal pointing back at it, and a trade that
 * went through is an EXECUTED TRADE_ACCEPT, often in the next week. So pass every week a pending trade
 * could have been resolved in.
 */
internal fun List<EspnTransaction>.openTrades(now: Instant): List<OpenTrade> {
    val chains = filter { it.relatedTransactionId != null }.groupBy { it.relatedTransactionId }
    return filter { it.type == "TRADE_PROPOSAL" && it.status == "PENDING" && it.relatedTransactionId == null }
        .distinctBy { it.id }
        .mapNotNull { proposal ->
            val chain = chains[proposal.id].orEmpty()
            val acceptance = chain.firstOrNull { it.type == "TRADE_ACCEPT" && it.status == null }
            val lapsed = acceptance == null && proposal.expirationDate?.let { now.toEpochMilli() > it } == true
            OpenTrade(proposal, acceptance).takeUnless { chain.any { it.resolvesTrade() } || lapsed }
        }
}

private fun EspnTransaction.resolvesTrade() =
    (type == "TRADE_PROPOSAL" && status == "CANCELED") ||
        type == "TRADE_DECLINE" ||
        (type == "TRADE_ACCEPT" && status == "EXECUTED")

internal fun OpenTrade.playerIds(): List<Int> = (proposal.items + acceptance?.items.orEmpty()).map { it.playerId }

internal fun OpenTrade.toPendingTrade(
    teamsById: Map<Int, Team>,
    playersById: Map<Int, Player>,
): PendingTrade? {
    val team = teamsById[proposal.teamId] ?: return null
    return PendingTrade(
        id = proposal.id,
        status = if (acceptance == null) PendingTradeStatus.OFFERED else PendingTradeStatus.UNDER_REVIEW,
        proposingTeam = team,
        // The players offered, then any drop the accepting team made to fit them.
        moves = (proposal.moves() + acceptance?.moves().orEmpty()).map { it.toPlayerMove(teamsById, playersById) },
        proposedAt = Instant.ofEpochMilli(proposal.proposedDate ?: 0),
        acceptedAt = acceptance?.proposedDate?.let(Instant::ofEpochMilli),
        expiresAt = proposal.expirationDate?.let(Instant::ofEpochMilli),
    )
}

private fun EspnTransactionItem.toPlayerMove(
    teamsById: Map<Int, Team>,
    playersById: Map<Int, Player>,
) = PlayerMove(
    type = checkNotNull(moveType()) { "Not a move: $type" },
    player = playersById[playerId] ?: unknownPlayer(playerId),
    fromTeam = teamsById[fromTeamId],
    toTeam = teamsById[toTeamId],
)
