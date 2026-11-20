package com.puzzel.lab.cardsmania.net

import com.puzzel.lab.cardsmania.bhabhi.BhabhiAction
import com.puzzel.lab.cardsmania.bhabhi.BhabhiBot
import com.puzzel.lab.cardsmania.bhabhi.BhabhiEngine
import com.puzzel.lab.cardsmania.bhabhi.BhabhiSeat
import com.puzzel.lab.cardsmania.bhabhi.BhabhiSetup
import com.puzzel.lab.cardsmania.bhabhi.BhabhiState
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Bhabhi over Wi-Fi: messages, what each phone may see, full games over real sockets, re-joining, lobby seats. */
class BhabhiMultiplayerTest {

    private fun hostOf(scope: CoroutineScope) = RoomHost(scope, "Host", 0, { 1000 + it }, { i, _ -> "Bot$i" to i })

    @Test
    fun bhabhiMessagesRoundTrip() {
        val setup = BhabhiSetup(players = 5, safeFirstTrick = false, difficulty = Difficulty.HARD, turnTimer = 20)
        val steps = BhabhiEngine.newGame(setup, 42L)
        val seats = List(5) { BhabhiSeat("P$it", 1000 + it, isHuman = it < 3, Difficulty.HARD, remote = it in 1..2) }
        val s = steps.last().state
        val msgs = listOf(
            NetMsg.BhabhiAct(BhabhiAction.Play(2, 17)),
            NetMsg.BhabhiStart(2, setup, seats, 1),
            NetMsg.BhabhiSync(2, setup, seats, 2, BhabhiRedact.state(s, 2)),
            NetMsg.BhabhiSteps(steps.map { BhabhiRedact.step(it.event, it.state, 2) }),
            NetMsg.Lobby(RoomState(code = "AAAA-BBBB", game = RoomGame.BHABHI, seats = 5, bhabhi = setup, guests = listOf(RoomGuest("a", "b", 1)))),
        )
        for (m in msgs) {
            val back = Net.decode(Net.encode(m))
            assertNotNull("decode failed for $m", back)
            assertEquals(m, back)
        }
        val lobby = Net.decode(Net.encode(msgs.last())) as NetMsg.Lobby
        assertTrue(lobby.room.isBhabhi)
        assertFalse(lobby.room.isMindi)
        assertEquals(8, lobby.room.maxSeats)
    }

    @Test
    fun redactionHidesOtherHandsAndTheSeed() {
        for (players in 3..8) {
            // play a few tricks so there are pickups, voids and thrown-away cards in the state
            var s = BhabhiEngine.newGame(BhabhiSetup(players = players, safeFirstTrick = false), 11L + players).last().state
            val rnd = Random(players)
            repeat(players * 6) {
                if (s.isOver) return@repeat
                s = BhabhiEngine.apply(s, BhabhiBot.decide(s, s.turn, Difficulty.NORMAL, rnd)!!).last().state
            }
            for (viewer in 0 until players) {
                val v = BhabhiRedact.state(s, viewer)
                assertTrue("leak for $players viewer $viewer", BhabhiRedact.leaks(v, viewer).isEmpty())
                assertEquals(0L, v.seed)
                assertEquals(s.hands[viewer], v.hands[viewer])
                for (seat in 0 until players) {
                    assertEquals(s.hands[seat].size, v.hands[seat].size)
                    if (seat != viewer) assertTrue(v.hands[seat].all { BhabhiRedact.isPlaceholder(it) })
                }
                // public information is kept: the table, thrown-away cards, picked-up cards, voids
                assertEquals(s.trick, v.trick)
                assertEquals(s.discarded, v.discarded)
                assertEquals(s.known, v.known)
                assertEquals(s.voids, v.voids)
                // the viewer's own legal moves and the hint still work on the redacted view
                if (!s.isOver && s.turn == viewer) {
                    assertEquals(BhabhiEngine.legalCards(s, viewer), BhabhiEngine.legalCards(v, viewer))
                    assertNotNull(BhabhiBot.hint(v, viewer))
                }
                // the unredacted state does leak (sanity check of the helper)
                if (s.hands.withIndex().any { (seat, h) -> seat != viewer && h.isNotEmpty() }) assertFalse(BhabhiRedact.leaks(s, viewer).isEmpty())
            }
        }
    }

    /** Host + 3 guests over localhost sockets play complete games, with and without the safe first round. */
    @Test
    fun hostAndThreeGuestsPlayFullBhabhiGames() = runBlocking {
        playGame(players = 5, safeFirst = true, seed = 4242L)
        playGame(players = 4, safeFirst = false, seed = 777L)
    }

    private suspend fun playGame(players: Int, safeFirst: Boolean, seed: Long) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = hostOf(scope)
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.BHABHI, seats = players, bots = players - 4, botDifficulty = Difficulty.HARD, bhabhi = it.bhabhi.copy(players = players, safeFirstTrick = safeFirst)) }
            assertTrue(host.room.value.isBhabhi)
            assertEquals(players, host.room.value.bhabhi.players)

            val guests = List(3) { RoomClient(scope) }
            guests.forEachIndexed { i, g -> assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("Guest$i", i, "btoken-$i-$seed"))) }
            withTimeout(3000) { while (host.room.value.guests.size < 3) delay(20) }
            assertEquals(0, host.room.value.openSeats)
            withTimeout(3000) { guests.forEach { g -> while (g.room.value?.isBhabhi != true || g.room.value?.guests?.size != 3) delay(20) } }

            val (setup, seats) = host.startBhabhi(seed, BhabhiSeat("Host", 1000, isHuman = true))
            assertEquals(players, setup.players)
            assertEquals(safeFirst, setup.safeFirstTrick)
            assertEquals(Difficulty.HARD, setup.difficulty)
            assertEquals(players, seats.size)
            assertTrue(seats.subList(1, 4).all { it.isHuman && it.remote })
            assertTrue(seats.drop(4).none { it.isHuman })
            var state = BhabhiEngine.newGame(setup, seed).let { steps ->
                host.broadcastBhabhi(steps)
                steps.last().state
            }
            host.bhabhiStateProvider = { state }

            val guestDone = Channel<Int>(Channel.UNLIMITED)
            val guestError = Channel<Throwable>(Channel.UNLIMITED)
            val received = AtomicInteger()
            guests.forEachIndexed { gi, g ->
                scope.launch {
                    try {
                        g.status.first { it == ClientStatus.InGame }
                        var me = -1
                        var latest: BhabhiState? = null
                        var lastKey: Any? = null
                        val diff = listOf(Difficulty.EASY, Difficulty.HARD, Difficulty.GENIUS)[gi]
                        for (msg in g.game) {
                            when (msg) {
                                is NetMsg.BhabhiStart -> {
                                    me = msg.yourSeat
                                    assertEquals(setup, msg.setup)
                                    assertEquals(players, msg.seats.size)
                                }
                                is NetMsg.BhabhiSteps -> {
                                    for (st in msg.steps) {
                                        // nothing private from another seat may reach this phone
                                        assertTrue("leak to seat $me: ${BhabhiRedact.leaks(st.state, me)}", BhabhiRedact.leaks(st.state, me).isEmpty())
                                        assertEquals(0L, st.state.seed)
                                        received.incrementAndGet()
                                    }
                                    latest = msg.steps.last().state
                                }
                                is NetMsg.ActionRejected -> throw AssertionError("host rejected a legal move from seat $me: ${msg.reason}")
                                is NetMsg.Ended -> break
                                else -> Unit
                            }
                            val s = latest ?: continue
                            if (s.isOver) {
                                guestDone.trySend(gi)
                                continue
                            }
                            if (s.turn != me) continue
                            val key = listOf(s.trickNo, s.trick.size)
                            if (key == lastKey) continue
                            lastKey = key
                            val a = BhabhiBot.decide(s, me, diff, Random(s.trickNo * 100 + gi))
                                ?: throw AssertionError("bot found no move for seat $me")
                            // the guest's own (redacted) view must agree the move is legal
                            assertNull("guest-side validation failed for $a", BhabhiEngine.validate(s, a))
                            // claim the host's seat: the host must stamp it back to ours
                            g.send(NetMsg.BhabhiAct(a.withSeat(0)))
                        }
                    } catch (t: Throwable) {
                        guestError.trySend(t)
                    }
                }
            }

            // host loop: host seat + bots locally, guest seats from the network
            val rnd = Random(99)
            var guestMoves = 0
            var hostMoves = 0
            withTimeout(90_000) {
                while (!state.isOver) {
                    guestError.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                    val actor = state.turn
                    val action: BhabhiAction = if (seats[actor].remote) {
                        val (seat, act) = withTimeoutOrNull(500) { host.bhabhiActions.receive() } ?: continue
                        assertEquals("host must re-stamp the seat", seat, act.seat)
                        assertEquals("only the acting guest should move", actor, seat)
                        val err = BhabhiEngine.validate(state, act)
                        if (err != null) {
                            host.sendToSeat(seat, NetMsg.ActionRejected(err))
                            throw AssertionError("guest move rejected: $act ($err)")
                        }
                        guestMoves++
                        act
                    } else {
                        hostMoves++
                        BhabhiBot.decide(state, actor, Difficulty.NORMAL, rnd)!!
                    }
                    val steps = BhabhiEngine.apply(state, action)
                    state = steps.last().state
                    host.broadcastBhabhi(steps)
                }
            }
            withTimeout(15_000) {
                val seen = HashSet<Int>()
                while (seen.size < 3) {
                    guestError.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                    withTimeoutOrNull(200) { guestDone.receive() }?.let { seen += it }
                }
            }
            assertTrue(state.loser in 0 until players)
            assertTrue("guests played", guestMoves >= 3)
            assertTrue(hostMoves > 0)
            assertTrue(received.get() > 0)
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }

    /** A guest that drops and comes back gets a redacted snapshot of the running game. */
    @Test
    fun guestRejoinGetsRedactedSync() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = hostOf(scope)
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.BHABHI, seats = 6, bhabhi = it.bhabhi.copy(players = 6)) }
            val g = RoomClient(scope)
            assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("Guest", 1, "b-rejoin-token")))
            withTimeout(3000) { while (host.room.value.guests.size < 1) delay(20) }
            val (setup, seats) = host.startBhabhi(5L, BhabhiSeat("Host", 1000, isHuman = true))
            assertEquals(6, setup.players)
            assertEquals(6, seats.size)
            assertEquals(4, seats.count { !it.isHuman })
            var state = BhabhiEngine.newGame(setup, 5L).last().state
            val rnd = Random(3)
            repeat(8) { state = BhabhiEngine.apply(state, BhabhiBot.decide(state, state.turn, Difficulty.NORMAL, rnd)!!).last().state }
            host.bhabhiStateProvider = { state }
            g.status.first { it == ClientStatus.InGame }
            g.leave()
            delay(200)

            val again = RoomClient(scope)
            assertNull(again.join("127.0.0.1", host.port, NetMsg.Hello("Guest", 1, "b-rejoin-token")))
            val sync = withTimeout(3000) { again.starts.first { it is NetMsg.BhabhiSync } } as NetMsg.BhabhiSync
            assertEquals(1, sync.yourSeat)
            assertEquals(setup, sync.setup)
            assertEquals(state.hands[1], sync.state.hands[1])
            assertEquals(state.trickNo, sync.state.trickNo)
            assertTrue(BhabhiRedact.leaks(sync.state, 1).isEmpty())
            // a stranger can't join a running game
            assertNotNull(RoomClient(scope).join("127.0.0.1", host.port, NetMsg.Hello("Late", 0, "other")))
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }

    @Test
    fun roomSeatsFollowTheGame() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = hostOf(scope)
            assertTrue(host.start("127.0.0.1"))
            // Bhabhi seats 3 to 8
            host.update { it.copy(game = RoomGame.BHABHI, seats = 2) }
            assertEquals(3, host.room.value.seats)
            assertEquals(3, host.room.value.bhabhi.players)
            host.update { it.copy(seats = 12) }
            assertEquals(8, host.room.value.seats)
            host.update { it.copy(seats = 7, bots = 4, botDifficulty = Difficulty.GENIUS, bhabhi = it.bhabhi.copy(turnTimer = 99)) }
            assertEquals(7, host.room.value.bhabhi.players)
            assertEquals(Difficulty.GENIUS, host.room.value.bhabhi.difficulty)
            assertEquals(60, host.room.value.bhabhi.turnTimer)
            assertEquals(2, host.room.value.openSeats)
            // more players than a Mania table holds stays on Bhabhi (only an over-full Mania room switches game)
            val guests = List(6) { RoomClient(scope) }
            host.update { it.copy(bots = 0) }
            guests.forEachIndexed { i, g -> assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("G$i", i, "seat-$i"))) }
            withTimeout(3000) { while (host.room.value.guests.size < 6) delay(20) }
            assertTrue(host.room.value.isBhabhi)
            assertEquals(7, host.room.value.seats)
            // a Bhabhi move from a guest is ignored outside a Bhabhi game
            host.update { it.copy(game = RoomGame.MINDI) }
            assertTrue(host.room.value.isMindi)
            assertEquals(8, host.room.value.seats)
            guests[0].send(NetMsg.BhabhiAct(BhabhiAction.Play(1, 0)))
            delay(200)
            assertTrue(host.bhabhiActions.tryReceive().isFailure)
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }
}
