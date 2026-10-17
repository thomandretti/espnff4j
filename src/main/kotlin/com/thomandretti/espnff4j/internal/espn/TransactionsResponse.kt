package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.InjuryStatus
import com.thomandretti.espnff4j.model.MoveType
import com.thomandretti.espnff4j.model.Player
import com.thomandretti.espnff4j.model.PlayerMove
import com.thomandretti.espnff4j.model.PlayerStatus
import com.thomandretti.espnff4j.model.Position
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.Transaction
import com.thomandretti.espnff4j.model.TransactionType
import java.time.Instant

internal data class TransactionsResponse(
    // Every transaction proposed in the requested week, including lineup changes, failed waiver
    // claims and trade offers that never went through.
    val transactions: List<EspnTransaction> = emptyList(),
    val teams: List<EspnTeam>,
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
)

/** Executed free-agent pickups, waiver claims and trades. */
internal fun TransactionsResponse.completed(): List<EspnTransaction> =
    transactions.filter { it.status == "EXECUTED" && it.transactionType() != null && it.moves().isNotEmpty() }

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
        moves =
            moves().map { item ->
                PlayerMove(
                    type = MoveType.valueOf(item.type),
                    player = playersById[item.playerId] ?: unknownPlayer(item.playerId),
                    fromTeam = teamsById[item.fromTeamId],
                    toTeam = teamsById[item.toTeamId],
                )
            },
        waiverBid = bidAmount.takeIf { type == TransactionType.WAIVER },
    )
}

private fun EspnTransaction.transactionType(): TransactionType? =
    when {
        type == "FREEAGENT" -> TransactionType.FREE_AGENT
        type == "WAIVER" -> TransactionType.WAIVER
        // A trade leaves a chain: the TRADE_PROPOSAL (PENDING, later CANCELED), a TRADE_ACCEPT with no
        // status, and league votes (TRADE_UPHOLD, TRADE_VETO) or a TRADE_DECLINE, which are EXECUTED but move
        // no players. When the review period ends, often the next week, ESPN adds a second TRADE_ACCEPT that
        // is EXECUTED and carries the players, plus any drop the receiving team made to fit them.
        type == "TRADE_ACCEPT" -> TransactionType.TRADE
        else -> null
    }

// LINEUP items are the slot changes ESPN attaches to some pickups.
private fun EspnTransaction.moves(): List<EspnTransactionItem> =
    items.filter { item -> MoveType.entries.any { it.name == item.type } }

// ESPN could not find the player, e.g. one who has since retired. Keep the move rather than drop it.
private fun unknownPlayer(id: Int) =
    Player(
        id = id,
        name = "Unknown player $id",
        position = Position.UNKNOWN,
        proTeam = null,
        injuryStatus = InjuryStatus.UNKNOWN,
        status = PlayerStatus.UNKNOWN,
        team = null,
        waiverClearsAt = null,
        percentOwned = null,
        points = null,
        projectedPoints = null,
    )
