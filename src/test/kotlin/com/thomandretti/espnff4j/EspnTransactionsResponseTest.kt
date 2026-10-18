package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.internal.espn.OBJECT_MAPPER
import com.thomandretti.espnff4j.internal.espn.OpenTrade
import com.thomandretti.espnff4j.internal.espn.TransactionsResponse
import com.thomandretti.espnff4j.internal.espn.openTrades
import com.thomandretti.espnff4j.internal.espn.toPendingTrade
import com.thomandretti.espnff4j.model.MoveType
import com.thomandretti.espnff4j.model.PendingTrade
import com.thomandretti.espnff4j.model.PendingTradeStatus
import com.thomandretti.espnff4j.model.Position
import com.thomandretti.espnff4j.model.Record
import com.thomandretti.espnff4j.model.RecordPart
import com.thomandretti.espnff4j.model.Team
import com.thomandretti.espnff4j.model.Transaction
import com.thomandretti.espnff4j.model.TransactionType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Pins league activity against trimmed captures of real responses for week 6 of the 2026 season:
 * `mTransactions2,mTeam`, then `kona_player_info` for the players those transactions moved. Team and
 * member names and account ids are anonymised.
 */
class EspnTransactionsResponseTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun client() = EspnFantasyFootball.client(2026, 12345, baseUrl = server.url("/seasons/").toString())

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/$name")) { "missing ESPN fixture $name" }.readText()

    private fun transactions(): List<Transaction> {
        server.enqueue(MockResponse().setBody(fixture("espn-transactions-week6.json")))
        server.enqueue(MockResponse().setBody(fixture("espn-transaction-players-week6.json")))
        return runBlocking { client().getTransactionsForWeek(6) }
    }

    @Test
    fun `keeps executed pickups and waiver claims, newest first`() {
        val transactions = transactions()

        // The capture also holds 23 lineup changes, a pending claim and a failed one.
        assertEquals(10, transactions.size)
        assertEquals(transactions.sortedByDescending { it.date }, transactions)
        assertEquals(
            mapOf(TransactionType.FREE_AGENT to 6, TransactionType.WAIVER to 4),
            transactions.groupingBy { it.type }.eachCount(),
        )
    }

    @Test
    fun `a pickup names the players and teams on each side`() {
        val latest = transactions().first()

        assertEquals("Team A", latest.team.name)
        assertEquals(TransactionType.FREE_AGENT, latest.type)
        assertNull(latest.waiverBid)
        val (add, drop) = latest.moves
        assertEquals(MoveType.ADD, add.type)
        assertEquals("Jameis Winston", add.player.name)
        assertNull(add.fromTeam)
        assertEquals("Team A", add.toTeam?.name)
        assertEquals(MoveType.DROP, drop.type)
        assertEquals("Brian Robinson Jr.", drop.player.name)
        assertEquals("Team A", drop.fromTeam?.name)
        assertNull(drop.toTeam)
    }

    @Test
    fun `a waiver claim is dated when waivers ran and resolves team defenses`() {
        val claim = transactions().single { it.type == TransactionType.WAIVER && it.team.name == "Team B" }

        assertEquals(Instant.ofEpochMilli(1791962945903), claim.date)
        assertEquals(0, claim.waiverBid)
        assertEquals(listOf("Chiefs D/ST", "Jets D/ST"), claim.moves.map { it.player.name })
        assertTrue(claim.moves.all { it.player.position == Position.DST })
    }

    @Test
    fun `players are looked up by id for the requested week in a second request`() {
        transactions()

        val first = requireNotNull(server.takeRequest().path)
        assertTrue("scoringPeriodId=6" in first && "view=mTransactions2" in first, first)
        val lookup = server.takeRequest()
        assertTrue("view=kona_player_info" in requireNotNull(lookup.path), lookup.path)
        val filter = requireNotNull(lookup.getHeader("X-Fantasy-Filter"))
        assertTrue(""""filterIds":{"value":[""" in filter && "-16012" in filter && "2969939" in filter, filter)
    }

    @Test
    fun `a week with no completed moves makes one request`() {
        server.enqueue(MockResponse().setBody("""{"transactions":[],"teams":[]}"""))

        assertTrue(runBlocking { client().getTransactionsForWeek(1) }.isEmpty())
        assertEquals(1, server.requestCount)
    }

    private fun client2024() = EspnFantasyFootball.client(2024, 12345, baseUrl = server.url("/seasons/").toString())

    @Test
    fun `a trade is reported once the review period ends, with the drop it forced`() {
        // Week 7 of 2024, when ESPN executed a trade accepted in week 6.
        server.enqueue(MockResponse().setBody(fixture("espn-transactions-2024-week7.json")))
        server.enqueue(MockResponse().setBody(fixture("espn-transaction-players-2024-week7.json")))

        val trade = runBlocking { client2024().getTransactionsForWeek(7) }.single { it.type == TransactionType.TRADE }

        assertEquals("Team A", trade.team.name)
        assertEquals(Instant.ofEpochMilli(1728975600000), trade.date)
        assertNull(trade.waiverBid)
        assertEquals(
            listOf(
                "TRADE Alexander Mattison Team A -> Team B",
                "TRADE Patrick Mahomes Team A -> Team B",
                "TRADE Chuba Hubbard Team B -> Team A",
                "DROP Tucker Kraft Team B -> null",
            ),
            trade.moves.map { "${it.type} ${it.player.name} ${it.fromTeam?.name} -> ${it.toTeam?.name}" },
        )
    }

    @Test
    fun `a trade still under review, and the votes on it, are not reported`() {
        // Week 6 of 2024: the proposal, an accept with no status, and two votes to uphold.
        server.enqueue(MockResponse().setBody(fixture("espn-transactions-2024-week6.json")))
        server.enqueue(MockResponse().setBody("""{"players":[]}"""))

        val transactions = runBlocking { client2024().getTransactionsForWeek(6) }

        assertTrue(transactions.none { it.type == TransactionType.TRADE }, transactions.toString())
        assertEquals(18, transactions.size)
    }

    @Test
    fun `an accepted trade awaiting review is pending, with the drop it forces`() {
        // Asked for in week 6 of 2024, when the trade was accepted; week 5 had no trades.
        server.enqueue(MockResponse().setBody(fixture("espn-transactions-2024-week6.json")))
        server.enqueue(MockResponse().setBody("""{"transactions":[]}"""))
        server.enqueue(MockResponse().setBody(fixture("espn-transaction-players-2024-week7.json")))

        val trade = runBlocking { client2024().getPendingTrades(6) }.single()

        assertEquals(PendingTradeStatus.UNDER_REVIEW, trade.status)
        assertEquals("Team A", trade.proposingTeam.name)
        assertEquals(listOf("Team A", "Team B"), trade.teams.map { it.name })
        assertEquals(Instant.ofEpochMilli(1728735914930), trade.proposedAt)
        assertEquals(Instant.ofEpochMilli(1728758649569), trade.acceptedAt)
        assertEquals(Instant.ofEpochMilli(1728908714970), trade.expiresAt)
        assertEquals(
            listOf(
                "TRADE Alexander Mattison Team A -> Team B",
                "TRADE Patrick Mahomes Team A -> Team B",
                "TRADE Chuba Hubbard Team B -> Team A",
                "DROP Tucker Kraft Team B -> null",
            ),
            trade.moves.map { "${it.type} ${it.player.name} ${it.fromTeam?.name} -> ${it.toTeam?.name}" },
        )
        val (thisWeek, lastWeek) = List(2) { requireNotNull(server.takeRequest().path) }
        assertTrue("scoringPeriodId=6" in thisWeek && "mTransactions2" in thisWeek, thisWeek)
        assertTrue("scoringPeriodId=5" in lastWeek && "mTransactions2" in lastWeek, lastWeek)
    }

    @Test
    fun `a trade offered last week and executed this week is no longer pending`() {
        server.enqueue(MockResponse().setBody(fixture("espn-transactions-2024-week7.json")))
        server.enqueue(MockResponse().setBody(fixture("espn-transactions-2024-week6.json")))

        assertEquals(emptyList<PendingTrade>(), runBlocking { client2024().getPendingTrades(7) })
        assertEquals(2, server.requestCount, "no player lookup without a pending trade")
    }

    @Test
    fun `an offer not yet accepted is open until it lapses, and a cancellation closes it`() {
        val week6 =
            OBJECT_MAPPER.readValue(
                fixture("espn-transactions-2024-week6.json"),
                TransactionsResponse::class.java,
            )
        val proposal = week6.transactions.single { it.type == "TRADE_PROPOSAL" }
        val beforeAccepted = week6.transactions.filterNot { it.type == "TRADE_ACCEPT" }
        val deadline = Instant.ofEpochMilli(checkNotNull(proposal.expirationDate))

        val open = beforeAccepted.openTrades(deadline.minusSeconds(60)).single()
        assertNull(open.acceptance)
        assertEquals(PendingTradeStatus.OFFERED, open.toPendingTrade(mapOf(1 to teamA), emptyMap())?.status)
        assertEquals(emptyList<OpenTrade>(), beforeAccepted.openTrades(deadline.plusSeconds(60)))
        // ESPN records a cancellation as a second proposal pointing at the first, which stays PENDING.
        val cancellation = proposal.copy(id = "cancel", status = "CANCELED", relatedTransactionId = proposal.id)
        assertEquals(emptyList<OpenTrade>(), (beforeAccepted + cancellation).openTrades(deadline.minusSeconds(60)))
    }

    private val teamA = Team(id = 1, name = "Team A", record = Record(RecordPart(0, 0, 0)), playoffSeed = 1)
}
