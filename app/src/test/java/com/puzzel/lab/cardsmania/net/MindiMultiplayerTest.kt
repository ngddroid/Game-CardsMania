package com.puzzel.lab.cardsmania.net

import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.mindi.MindiAction
import com.puzzel.lab.cardsmania.mindi.MindiBot
import com.puzzel.lab.cardsmania.mindi.MindiEngine
import com.puzzel.lab.cardsmania.mindi.MindiPhase
import com.puzzel.lab.cardsmania.mindi.MindiSearch
import com.puzzel.lab.cardsmania.mindi.MindiSeat
import com.puzzel.lab.cardsmania.mindi.MindiSetup
import com.puzzel.lab.cardsmania.mindi.MindiState
import com.puzzel.lab.cardsmania.mindi.MindiTrumpMode
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

class MindiMultiplayerTest {

    private fun actorOf(s: MindiState): Int = when (val ph = s.phase) {
        is MindiPhase.ChooseHidden -> ph.seat
        MindiPhase.Play -> s.turn
        else -> -1
    }

    @Test
    fun mindiMessagesRoundTrip() {
        val setup = MindiSetup(players = 6, doubleDeck = true, trumpMode = MindiTrumpMode.CUT, target = 3, turnTimer = 20)
        val steps = MindiEngine.newMatch(setup, 42L)
        val seats = List(6) { MindiSeat("P$it", 1000 + it, isHuman = it < 3, Difficulty.HARD, remote = it in 1..2) }
        val s = steps.last().state
        val msgs = listOf(
            NetMsg.MindiAct(MindiAction.Play(2, 17)), NetMsg.MindiAct(MindiAction.Hide(1, 3)), NetMsg.MindiAct(MindiAction.NextHand()),
            NetMsg.MindiStart(2, setup, seats, 1),
            NetMsg.MindiSync(2, setup, seats, 2, MindiRedact.state(s, 2)),
            NetMsg.MindiSteps(steps.map { MindiRedact.step(it.event, it.state, 2) }),
            NetMsg.Lobby(RoomState(code = "AAAA-BBBB", game = RoomGame.MINDI, seats = 6, mindi = setup, guests = listOf(RoomGuest("a", "b", 1)))),
        )
        for (m in msgs) {
            val back = Net.decode(Net.encode(m))
            assertNotNull("decode failed for $m", back)
            assertEquals(m, back)
        }
        val lobby = Net.decode(Net.encode(msgs.last())) as NetMsg.Lobby
        assertTrue(lobby.room.isMindi)
        assertEquals(8, lobby.room.maxSeats)
        assertEquals(2, Net.PROTOCOL)
    }

    @Test
    fun redactionHidesHandsAndHiddenTrump() {
        for (players in listOf(4, 6, 8)) for (mode in MindiTrumpMode.entries) {
            val setup = MindiSetup(players = players, trumpMode = mode)
            var s = MindiEngine.newMatch(setup, 7L + players).last().state
            // hide mode: let the chooser hide a card so there is a secret to protect
            (s.phase as? MindiPhase.ChooseHidden)?.let { ph ->
                s = MindiEngine.apply(s, MindiBot.decide(s, ph.seat, Difficulty.NORMAL, Random(1))!!).last().state
                assertNotNull(s.hidden)
            }
            for (viewer in 0 until players) {
                val v = MindiRedact.state(s, viewer)
                assertTrue("leak for $players/$mode viewer $viewer", MindiRedact.leaks(v, viewer).isEmpty())
                assertEquals(0L, v.seed)
                assertEquals(0L, v.rngState)
                assertEquals(s.hands[viewer], v.hands[viewer])
                for (seat in 0 until players) {
                    assertEquals(s.hands[seat].size, v.hands[seat].size)
                    if (seat != viewer) assertTrue(v.hands[seat].all { MindiRedact.isPlaceholder(it) })
                }
                if (s.hidden != null) {
                    assertNotNull(v.hidden)
                    if (viewer == s.hiddenOwner) assertEquals(s.hidden, v.hidden) else assertEquals(MindiRedact.HIDDEN_TRUMP, v.hidden)
                }
                // the unredacted state does leak (sanity check of the helper)
                if (viewer != 0) assertFalse(MindiRedact.leaks(s, viewer).isEmpty())
            }
        }
    }

    /** Host + 3 guests over localhost sockets play complete matches in every trump mode. */
    @Test
    fun hostAndThreeGuestsPlayFullMindiMatches() = runBlocking {
        val oldBudget = MindiSearch.budgetMs
        MindiSearch.budgetMs = 30
        try {
            for (mode in MindiTrumpMode.entries) playMatch(mode)
        } finally {
            MindiSearch.budgetMs = oldBudget
        }
    }

    private suspend fun playMatch(mode: MindiTrumpMode) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = RoomHost(scope, "Host", 0, { 1000 + it }, { i, _ -> "Bot$i" to i })
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.MINDI, seats = 4, botDifficulty = Difficulty.HARD, mindi = it.mindi.copy(players = 4, trumpMode = mode, target = 2)) }
            assertTrue(host.room.value.isMindi)
            assertEquals(4, host.room.value.mindi.players)

            val guests = List(3) { RoomClient(scope) }
            guests.forEachIndexed { i, g -> assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("Guest$i", i, "mtoken-$i"))) }
            withTimeout(3000) { while (host.room.value.guests.size < 3) delay(20) }
            assertEquals(0, host.room.value.openSeats)
            // every guest sees a Mindi room in the lobby
            withTimeout(3000) { guests.forEach { g -> while (g.room.value?.isMindi != true || g.room.value?.guests?.size != 3) delay(20) } }

            val seed = 4242L + mode.ordinal
            val (setup, seats) = host.startMindi(seed, MindiSeat("Host", 1000, isHuman = true))
            assertEquals(4, setup.players)
            assertEquals(mode, setup.trumpMode)
            assertEquals(Difficulty.HARD, setup.difficulty)
            assertEquals(4, seats.size)
            assertTrue(seats.drop(1).all { it.isHuman && it.remote })
            var state = MindiEngine.newMatch(setup, seed).let { steps ->
                host.broadcastMindi(steps)
                steps.last().state
            }
            host.mindiStateProvider = { state }

            val guestDone = Channel<Int>(Channel.UNLIMITED)
            val guestError = Channel<Throwable>(Channel.UNLIMITED)
            val received = AtomicInteger()
            guests.forEachIndexed { gi, g ->
                scope.launch {
                    try {
                        g.status.first { it == ClientStatus.InGame }
                        var me = -1
                        var latest: MindiState? = null
                        var lastKey: Any? = null
                        val diff = listOf(Difficulty.EASY, Difficulty.HARD, Difficulty.GENIUS)[gi]
                        for (msg in g.game) {
                            when (msg) {
                                is NetMsg.MindiStart -> {
                                    me = msg.yourSeat
                                    assertEquals(setup, msg.setup)
                                    assertEquals(4, msg.seats.size)
                                }
                                is NetMsg.MindiSteps -> {
                                    for (st in msg.steps) {
                                        // nothing private from another seat may reach this phone
                                        assertTrue("leak to seat $me: ${MindiRedact.leaks(st.state, me)}", MindiRedact.leaks(st.state, me).isEmpty())
                                        assertEquals(0L, st.state.seed)
                                        assertEquals(0L, st.state.rngState)
                                        received.incrementAndGet()
                                    }
                                    latest = msg.steps.last().state
                                }
                                is NetMsg.ActionRejected -> throw AssertionError("host rejected a legal move from seat $me: ${msg.reason}")
                                is NetMsg.Ended -> break
                                else -> Unit
                            }
                            val s = latest ?: continue
                            if (s.phase is MindiPhase.MatchOver) {
                                guestDone.trySend(gi)
                                continue
                            }
                            if (actorOf(s) != me) continue
                            val key = listOf(s.handNo, s.trickNo, s.trick.size, s.phase::class.simpleName)
                            if (key == lastKey) continue
                            lastKey = key
                            val a = MindiBot.decide(s, me, diff, Random(s.handNo * 1000 + s.trickNo * 10 + gi))
                                ?: throw AssertionError("bot found no move for seat $me in ${s.phase}")
                            // the guest's own (redacted) view must agree the move is legal
                            assertNull("guest-side validation failed for $a", MindiEngine.validate(s, a))
                            // claim the host's seat: the host must stamp it back to ours
                            g.send(NetMsg.MindiAct(a.withSeat(0)))
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
                while (state.phase !is MindiPhase.MatchOver) {
                    guestError.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                    if (state.phase is MindiPhase.HandOver) {
                        val steps = MindiEngine.apply(state, MindiAction.NextHand())
                        state = steps.last().state
                        host.broadcastMindi(steps)
                        continue
                    }
                    val actor = actorOf(state)
                    val action: MindiAction = if (seats[actor].remote) {
                        val (seat, act) = withTimeoutOrNull(500) { host.mindiActions.receive() } ?: continue
                        assertEquals("host must re-stamp the seat", seat, act.seat)
                        assertEquals("only the acting guest should move", actor, seat)
                        val err = MindiEngine.validate(state, act)
                        if (err != null) {
                            host.sendToSeat(seat, NetMsg.ActionRejected(err))
                            throw AssertionError("guest move rejected: $act ($err)")
                        }
                        guestMoves++
                        act
                    } else {
                        hostMoves++
                        MindiBot.decide(state, actor, Difficulty.NORMAL, rnd)!!
                    }
                    val steps = MindiEngine.apply(state, action)
                    state = steps.last().state
                    host.broadcastMindi(steps)
                }
            }
            withTimeout(15_000) {
                val seen = HashSet<Int>()
                while (seen.size < 3) {
                    guestError.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                    withTimeoutOrNull(200) { guestDone.receive() }?.let { seen += it }
                }
            }
            val mo = state.phase as MindiPhase.MatchOver
            assertTrue(state.scores[mo.winnerTeam] >= setup.target)
            assertTrue("guests played ($mode)", guestMoves >= 3 * 4)
            assertTrue(hostMoves > 0)
            assertTrue(received.get() > 0)
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }

    /** A guest that drops and comes back gets a redacted snapshot of the running match. */
    @Test
    fun guestRejoinGetsRedactedSync() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = RoomHost(scope, "Host", 0, { 1000 + it }, { i, _ -> "Bot$i" to i })
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.MINDI, seats = 6, mindi = it.mindi.copy(players = 6, trumpMode = MindiTrumpMode.HIDE)) }
            val g = RoomClient(scope)
            assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("Guest", 1, "rejoin-token")))
            withTimeout(3000) { while (host.room.value.guests.size < 1) delay(20) }
            val (setup, seats) = host.startMindi(5L, MindiSeat("Host", 1000, isHuman = true))
            assertEquals(6, setup.players)
            assertEquals(6, seats.size)
            assertEquals(4, seats.count { !it.isHuman })
            var state = MindiEngine.newMatch(setup, 5L).last().state
            (state.phase as? MindiPhase.ChooseHidden)?.let { ph ->
                state = MindiEngine.apply(state, MindiBot.decide(state, ph.seat, Difficulty.NORMAL, Random(2))!!).last().state
            }
            host.mindiStateProvider = { state }
            g.status.first { it == ClientStatus.InGame }
            g.leave()
            delay(200)

            val again = RoomClient(scope)
            assertNull(again.join("127.0.0.1", host.port, NetMsg.Hello("Guest", 1, "rejoin-token")))
            val sync = withTimeout(3000) { again.starts.first { it is NetMsg.MindiSync } } as NetMsg.MindiSync
            assertEquals(1, sync.yourSeat)
            assertEquals(setup, sync.setup)
            assertEquals(state.hands[1], sync.state.hands[1])
            assertTrue(MindiRedact.leaks(sync.state, 1).isEmpty())
            if (state.hidden != null && state.hiddenOwner != 1) assertEquals(MindiRedact.HIDDEN_TRUMP, sync.state.hidden)
            // a stranger can't join a running match
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
            val host = RoomHost(scope, "Host", 0, { 1000 + it }, { i, _ -> "Bot$i" to i })
            assertTrue(host.start("127.0.0.1"))
            // Mindi only seats 4, 6 or 8: anything else falls back to the setup's table size
            host.update { it.copy(game = RoomGame.MINDI, seats = 5, mindi = it.mindi.copy(players = 6)) }
            assertEquals(6, host.room.value.seats)
            assertEquals(6, host.room.value.mindi.players)
            host.update { it.copy(seats = 8, mindi = it.mindi.copy(players = 8, doubleDeck = true), bots = 3, botDifficulty = Difficulty.GENIUS) }
            assertEquals(8, host.room.value.seats)
            assertTrue(host.room.value.mindi.doubleDeck)
            assertEquals(Difficulty.GENIUS, host.room.value.mindi.difficulty)
            assertEquals(4, host.room.value.openSeats)
            // back to a Mania table: at most 6 seats
            host.update { it.copy(game = RoomGame.MANIA) }
            assertFalse(host.room.value.isMindi)
            assertEquals(6, host.room.value.seats)
            // double deck is only for 6+ players
            host.update { it.copy(game = RoomGame.MINDI, seats = 4, mindi = it.mindi.copy(players = 4, doubleDeck = true)) }
            assertEquals(4, host.room.value.seats)
            assertFalse(host.room.value.mindi.doubleDeck)
            host.close("done")
        } finally {
            delay(200)
            scope.cancel()
        }
    }
}
