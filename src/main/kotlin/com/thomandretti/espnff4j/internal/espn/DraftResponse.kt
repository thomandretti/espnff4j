package com.thomandretti.espnff4j.internal.espn

import com.thomandretti.espnff4j.model.Draft
import com.thomandretti.espnff4j.model.DraftPick
import com.thomandretti.espnff4j.model.DraftType
import com.thomandretti.espnff4j.model.Player
import com.thomandretti.espnff4j.model.Team
import java.time.Instant

internal data class DraftResponse(
    val draftDetail: EspnDraftDetail = EspnDraftDetail(),
    val teams: List<EspnTeam> = emptyList(),
    val members: List<Member> = emptyList(),
    val settings: LeagueSettings? = null,
)

internal data class EspnDraftDetail(
    val drafted: Boolean = false,
    val inProgress: Boolean = false,
    // Epoch milliseconds.
    val completeDate: Long? = null,
    val picks: List<EspnDraftPick> = emptyList(),
)

internal data class EspnDraftPick(
    val overallPickNumber: Int,
    val roundId: Int,
    val roundPickNumber: Int,
    val teamId: Int,
    // Negative for a team defense, as in transactions.
    val playerId: Int,
    val keeper: Boolean = false,
    // The winning bid in an auction draft, 0 in a snake draft.
    val bidAmount: Int = 0,
)

internal fun DraftResponse.toDraft(
    teamsById: Map<Int, Team>,
    playersById: Map<Int, Player>,
): Draft {
    val type =
        when (settings?.draftSettings?.type) {
            "SNAKE" -> DraftType.SNAKE
            "AUCTION" -> DraftType.AUCTION
            else -> DraftType.UNKNOWN
        }
    return Draft(
        type = type,
        scheduledAt = settings?.draftSettings?.date?.let(Instant::ofEpochMilli),
        completedAt = draftDetail.completeDate?.let(Instant::ofEpochMilli),
        isComplete = draftDetail.drafted,
        picks =
            draftDetail.picks
                .sortedBy { it.overallPickNumber }
                .mapNotNull { pick ->
                    DraftPick(
                        overallPick = pick.overallPickNumber,
                        round = pick.roundId,
                        roundPick = pick.roundPickNumber,
                        team = teamsById[pick.teamId] ?: return@mapNotNull null,
                        player = playersById[pick.playerId] ?: unknownPlayer(pick.playerId),
                        isKeeper = pick.keeper,
                        auctionPrice = pick.bidAmount.takeIf { type == DraftType.AUCTION },
                    )
                },
    )
}
