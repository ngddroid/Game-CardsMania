package com.ngdstudio.mindimania.net

import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.rummy.RAction
import com.ngdstudio.mindimania.rummy.REvent
import com.ngdstudio.mindimania.rummy.RPhase
import com.ngdstudio.mindimania.rummy.RummyBot
import com.ngdstudio.mindimania.rummy.RummyEngine
import com.ngdstudio.mindimania.rummy.RummySeat
import com.ngdstudio.mindimania.rummy.RummySetup
import com.ngdstudio.mindimania.rummy.RummyState
import com.ngdstudio.mindimania.teenpatti.TPAction
import com.ngdstudio.mindimania.teenpatti.TPEvent
import com.ngdstudio.mindimania.teenpatti.TPPhase
import com.ngdstudio.mindimania.teenpatti.TPVariant
import com.ngdstudio.mindimania.teenpatti.TeenPattiBot
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiSeat
import com.ngdstudio.mindimania.teenpatti.TeenPattiSetup
import com.ngdstudio.mindimania.teenpatti.TeenPattiState
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

/** Teen Patti and Rummy over Wi-Fi: messages, what each phone may see, full tables over real sockets, re-joining. */
class TeenPattiRummyMultiplayerTest {

    private fun hostOf(scope: CoroutineScope) = RoomHost(scope, "Host", 0, { 1000 + it }, { i, _ -> "Bot$i" to i })

    @Test
    fun messagesRoundTrip() {
        val tps = TeenPattiSetup(players = 4, variant = TPVariant.AK47, hands = 0)
        val tpSteps = TeenPattiEngine.newGame(tps, 5L)
        val tpSeats = List(4) { TeenPattiSeat("P$it", 1000 + it, isHuman = it < 2, remote = it == 1) }
        val rs = RummySetup(players = 3, turnTimer = 30)
        val rSteps = RummyEngine.newGame(rs, 6L)
        val rSeats = List(3) { RummySeat("R$it", 1000 + it, isHuman = it < 2, remote = it == 1) }
        val msgs = listOf(
            NetMsg.TeenPattiAct(TPAction.Chaal(1, raise = true)),
            NetMsg.TeenPattiStart(1, tps, tpSeats, 1),
            NetMsg.TeenPattiSync(1, tps, tpSeats, 2, TeenPattiRedact.state(tpSteps.last().state, 1)),
            NetMsg.TeenPattiSteps(tpSteps.map { TeenPattiRedact.step(it.event, it.state, 1) }),
            NetMsg.RummyAct(RAction.Declare(1, 5, listOf(listOf(1, 2, 3)))),
            NetMsg.RummyStart(1, rs, rSeats, 0),
            NetMsg.RummySync(1, rs, rSeats, 1, RummyRedact.state(rSteps.last().state, 1)),
            NetMsg.RummySteps(rSteps.map { RummyRedact.step(it.event, it.state, 1) }),
            NetMsg.SeatsChanged(tpSeats, rSeats),
            NetMsg.Lobby(RoomState(game = RoomGame.TEEN_PATTI, seats = 4, teenPatti = tps)),
            NetMsg.Lobby(RoomState(game = RoomGame.RUMMY, seats = 3, rummy = rs)),
        )
        for (m in msgs) {
            val back = Net.decode(Net.encode(m))
            assertNotNull("decode failed for $m", back)
            assertEquals(m, back)
        }
        assertEquals(6, RoomState(game = RoomGame.TEEN_PATTI).maxSeats)
        assertEquals(6, RoomState(game = RoomGame.RUMMY).maxSeats)
        assertEquals(6, Net.PROTOCOL)
    }

    @Test
    fun teenPattiRedactionHidesHandsUntilTheyAreOpened() {
        val rnd = Random(1)
        var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4, hands = 0), 77L).last().state
        var sideshowsChecked = 0
        var showdownsChecked = 0
        repeat(400) {
            if (s.phase is TPPhase.HandOver) {
                s = TeenPattiEngine.nextHand(s).last().state
                return@repeat
            }
            val steps = TeenPattiEngine.apply(s, TeenPattiBot.decide(s, s.turn, Difficulty.HARD, rnd)!!)
            for (st in steps) for (viewer in 0 until 4) {
                val net = TeenPattiRedact.step(st.event, st.state, viewer)
                assertEquals(0L, net.state.seed)
                assertEquals(st.state.hands[viewer], net.state.hands[viewer])
                val e = st.event
                val partner = (e as? TPEvent.SideshowResult)?.takeIf { it.accepted && (it.from == viewer || it.to == viewer) }
                    ?.let { if (it.from == viewer) it.to else it.from }
                if (partner != null) {
                    // the two sideshow players compare privately
                    assertEquals(st.state.hands[partner], net.state.hands[partner])
                    sideshowsChecked++
                }
                assertTrue("leak to $viewer at ${st.event}", TeenPattiRedact.leaks(net.state, viewer, setOfNotNull(partner)).isEmpty())
                // opened hands are visible to everyone
                for (seat in st.state.shown) assertEquals(st.state.hands[seat], net.state.hands[seat])
                if (e is TPEvent.Showdown) showdownsChecked++
                // a guest's own options are the same on the redacted view
                assertEquals(TeenPattiEngine.options(st.state, viewer), TeenPattiEngine.options(net.state, viewer))
            }
            s = steps.last().state
        }
        assertTrue("some showdowns", showdownsChecked > 0)
        assertTrue("some sideshows", sideshowsChecked > 0)
    }

    @Test
    fun rummyRedactionHidesHandsAndTheClosedPile() {
        val rnd = Random(2)
        var s = RummyEngine.newGame(RummySetup(players = 3), 88L).last().state
        var hands = 0
        var blindDraws = 0
        while (hands < 3) {
            if (s.handOver) {
                hands++
                // the result screen shows everyone's cards
                for (viewer in 0 until 3) assertEquals(s.hands, RummyRedact.state(s, viewer).hands)
                s = RummyEngine.nextHand(s).last().state
                continue
            }
            val actor = (s.phase as? RPhase.Showdown)?.pending?.first() ?: s.turn
            val steps = RummyEngine.apply(s, RummyBot.decide(s, actor, Difficulty.NORMAL, rnd)!!)
            for (st in steps) for (viewer in 0 until 3) {
                val net = RummyRedact.step(st.event, st.state, viewer)
                assertEquals(0L, net.state.seed)
                assertEquals(st.state.hands[viewer], net.state.hands[viewer])
                assertEquals(st.state.open, net.state.open)
                assertEquals(st.state.closed.size, net.state.closed.size)
                assertTrue("leak to $viewer", RummyRedact.leaks(net.state, viewer).isEmpty())
                val e = st.event
                if (e is REvent.PickedClosed && e.seat != viewer) {
                    assertTrue(RummyRedact.isPlaceholder((net.event as REvent.PickedClosed).card))
                    blindDraws++
                }
                // the viewer's own moves still validate on the redacted view
                if (!st.state.handOver && st.state.turn == viewer && st.state.phase == RPhase.Pick) {
                    assertNull(RummyEngine.validate(net.state, RAction.PickClosed(viewer)))
                }
            }
            s = steps.last().state
        }
        assertTrue(blindDraws > 0)
    }

    /** Host + 2 guests over localhost sockets play several Teen Patti hands (sideshows, shows, refills…). */
    @Test
    fun hostAndTwoGuestsPlayTeenPatti() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = hostOf(scope)
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.TEEN_PATTI, seats = 4, bots = 1, teenPatti = it.teenPatti.copy(variant = TPVariant.MIXED)) }
            assertTrue(host.room.value.isTeenPatti)
            val guests = List(2) { RoomClient(scope) }
            guests.forEachIndexed { i, g -> assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("G$i", i, "tp-token-$i"))) }
            withTimeout(3000) { while (host.room.value.guests.size < 2) delay(20) }
            val (setup, seats) = host.startTeenPatti(31L, TeenPattiSeat("Host", 1000, isHuman = true))
            assertEquals(0, setup.hands) // always an endless table over Wi-Fi
            assertEquals(4, seats.size)
            assertTrue(seats.subList(1, 3).all { it.isHuman && it.remote })
            var state = TeenPattiEngine.newGame(setup, 31L).also { host.broadcastTeenPatti(it) }.last().state
            host.tpStateProvider = { state }

            val errors = Channel<Throwable>(Channel.UNLIMITED)
            val received = AtomicInteger()
            guests.forEachIndexed { gi, g ->
                scope.launch {
                    try {
                        g.status.first { it == ClientStatus.InGame }
                        var me = -1
                        var latest: TeenPattiState? = null
                        var lastKey: Any? = null
                        for (msg in g.game) {
                            when (msg) {
                                is NetMsg.TeenPattiStart -> me = msg.yourSeat
                                is NetMsg.TeenPattiSteps -> {
                                    for (st in msg.steps) {
                                        val e = st.event
                                        val partner = (e as? TPEvent.SideshowResult)?.takeIf { it.accepted && (it.from == me || it.to == me) }
                                            ?.let { if (it.from == me) it.to else it.from }
                                        assertTrue("leak to seat $me", TeenPattiRedact.leaks(st.state, me, setOfNotNull(partner)).isEmpty())
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
                            if (s.handOver || s.turn != me) continue
                            val key = listOf(s.handNo, s.actions, s.seen[me], s.phase.toString())
                            if (key == lastKey) continue
                            lastKey = key
                            val a = TeenPattiBot.decide(s, me, Difficulty.NORMAL, Random(s.handNo * 31L + s.actions + gi)) ?: continue
                            assertNull("guest-side validation failed for $a", TeenPattiEngine.validate(s, a))
                            // claim the host's seat: the host must stamp it back
                            g.send(NetMsg.TeenPattiAct(a.withSeat(0)))
                        }
                    } catch (t: Throwable) {
                        errors.trySend(t)
                    }
                }
            }

            val rnd = Random(9)
            var guestMoves = 0
            withTimeout(90_000) {
                while (state.handNo <= 6) {
                    errors.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                    if (state.phase is TPPhase.HandOver) {
                        state = TeenPattiEngine.nextHand(state).also { host.broadcastTeenPatti(it) }.last().state
                        continue
                    }
                    val actor = state.turn
                    val action: TPAction = if (seats[actor].remote) {
                        val (seat, act) = withTimeoutOrNull(500) { host.tpActions.receive() } ?: continue
                        assertEquals(seat, act.seat)
                        assertEquals(actor, seat)
                        assertNull(TeenPattiEngine.validate(state, act))
                        guestMoves++
                        act
                    } else TeenPattiBot.decide(state, actor, Difficulty.NORMAL, rnd)!!
                    val steps = TeenPattiEngine.apply(state, action)
                    state = steps.last().state
                    host.broadcastTeenPatti(steps)
                }
            }
            assertTrue("guests played", guestMoves >= 4)
            assertTrue(received.get() > 0)
            assertEquals(4 * TeenPattiEngine.START_CHIPS, state.chips.sum() + state.pot)
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }

    /** Host + 2 guests over localhost sockets play Rummy hands, guests arranging their cards after a declaration. */
    @Test
    fun hostAndTwoGuestsPlayRummy() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = hostOf(scope)
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.RUMMY, seats = 3, bots = 0) }
            assertTrue(host.room.value.isRummy)
            val guests = List(2) { RoomClient(scope) }
            guests.forEachIndexed { i, g -> assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("G$i", i, "rm-token-$i"))) }
            withTimeout(3000) { while (host.room.value.guests.size < 2) delay(20) }
            val (setup, seats) = host.startRummy(41L, RummySeat("Host", 1000, isHuman = true))
            assertEquals(3, setup.players)
            var state = RummyEngine.newGame(setup, 41L).also { host.broadcastRummy(it) }.last().state
            host.rummyStateProvider = { state }

            val errors = Channel<Throwable>(Channel.UNLIMITED)
            val arranged = AtomicInteger()
            guests.forEachIndexed { gi, g ->
                scope.launch {
                    try {
                        g.status.first { it == ClientStatus.InGame }
                        var me = -1
                        var latest: RummyState? = null
                        var lastKey: Any? = null
                        for (msg in g.game) {
                            when (msg) {
                                is NetMsg.RummyStart -> me = msg.yourSeat
                                is NetMsg.RummySteps -> {
                                    for (st in msg.steps) {
                                        assertTrue("leak to seat $me", RummyRedact.leaks(st.state, me).isEmpty())
                                        assertEquals(0L, st.state.seed)
                                    }
                                    latest = msg.steps.last().state
                                }
                                is NetMsg.ActionRejected -> throw AssertionError("host rejected a legal move from seat $me: ${msg.reason}")
                                is NetMsg.Ended -> break
                                else -> Unit
                            }
                            val s = latest ?: continue
                            val ph = s.phase
                            val mine = if (ph is RPhase.Showdown) me in ph.pending else !s.handOver && s.turn == me
                            if (!mine) continue
                            val key = listOf(s.handNo, s.picks.sum(), s.phase::class.simpleName, s.hands[me].size)
                            if (key == lastKey) continue
                            lastKey = key
                            val a = RummyBot.decide(s, me, Difficulty.NORMAL, Random(s.handNo * 31L + s.picks.sum() + gi)) ?: continue
                            assertNull("guest-side validation failed for $a", RummyEngine.validate(s, a))
                            if (a is RAction.Arrange) arranged.incrementAndGet()
                            g.send(NetMsg.RummyAct(a.withSeat(0)))
                        }
                    } catch (t: Throwable) {
                        errors.trySend(t)
                    }
                }
            }

            val rnd = Random(10)
            var hands = 0
            withTimeout(120_000) {
                while (hands < 3) {
                    errors.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                    if (state.handOver) {
                        hands++
                        state = RummyEngine.nextHand(state).also { host.broadcastRummy(it) }.last().state
                        continue
                    }
                    val ph = state.phase
                    val actor = if (ph is RPhase.Showdown) ph.pending.first() else state.turn
                    val action: RAction = if (seats[actor].remote) {
                        val (seat, act) = withTimeoutOrNull(500) { host.rummyActions.receive() } ?: continue
                        assertEquals(seat, act.seat)
                        assertNull("rejected $act", RummyEngine.validate(state, act))
                        act
                    } else RummyBot.decide(state, actor, Difficulty.NORMAL, rnd)!!
                    val steps = RummyEngine.apply(state, action)
                    state = steps.last().state
                    host.broadcastRummy(steps)
                }
            }
            assertEquals(3 * RummyEngine.START_CHIPS, state.chips.sum())
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }

    /** A guest that drops and comes back gets a redacted snapshot of the running table. */
    @Test
    fun guestsRejoinWithRedactedSnapshots() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // Teen Patti
            val host = hostOf(scope)
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(game = RoomGame.TEEN_PATTI, seats = 3) }
            val g = RoomClient(scope)
            assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("G", 1, "tp-rejoin")))
            withTimeout(3000) { while (host.room.value.guests.size < 1) delay(20) }
            val (setup, _) = host.startTeenPatti(7L, TeenPattiSeat("Host", 1000, isHuman = true))
            val state = TeenPattiEngine.newGame(setup, 7L).last().state
            host.tpStateProvider = { state }
            g.status.first { it == ClientStatus.InGame }
            g.leave()
            delay(200)
            val again = RoomClient(scope)
            assertNull(again.join("127.0.0.1", host.port, NetMsg.Hello("G", 1, "tp-rejoin")))
            val sync = withTimeout(3000) { again.starts.first { it is NetMsg.TeenPattiSync } } as NetMsg.TeenPattiSync
            assertEquals(1, sync.yourSeat)
            assertEquals(state.hands[1], sync.state.hands[1])
            assertTrue(TeenPattiRedact.leaks(sync.state, 1).isEmpty())
            host.close("done")

            // Rummy
            val host2 = hostOf(scope)
            assertTrue(host2.start("127.0.0.1"))
            host2.update { it.copy(game = RoomGame.RUMMY, seats = 2) }
            val r = RoomClient(scope)
            assertNull(r.join("127.0.0.1", host2.port, NetMsg.Hello("R", 1, "rm-rejoin")))
            withTimeout(3000) { while (host2.room.value.guests.size < 1) delay(20) }
            val (rs, _) = host2.startRummy(8L, RummySeat("Host", 1000, isHuman = true))
            val rstate = RummyEngine.newGame(rs, 8L).last().state
            host2.rummyStateProvider = { rstate }
            r.status.first { it == ClientStatus.InGame }
            r.leave()
            delay(200)
            val again2 = RoomClient(scope)
            assertNull(again2.join("127.0.0.1", host2.port, NetMsg.Hello("R", 1, "rm-rejoin")))
            val rsync = withTimeout(3000) { again2.starts.first { it is NetMsg.RummySync } } as NetMsg.RummySync
            assertEquals(rstate.hands[1], rsync.state.hands[1])
            assertTrue(RummyRedact.leaks(rsync.state, 1).isEmpty())
            host2.close("done")
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
            host.update { it.copy(game = RoomGame.TEEN_PATTI, seats = 2) }
            assertEquals(3, host.room.value.seats) // Teen Patti: 3 to 6
            host.update { it.copy(seats = 9) }
            assertEquals(6, host.room.value.seats)
            assertEquals(0, host.room.value.teenPatti.hands)
            host.update { it.copy(game = RoomGame.RUMMY, seats = 2) }
            assertEquals(2, host.room.value.seats) // Rummy: 2 to 6
            assertEquals(2, host.room.value.rummy.players)
            // a Rummy move is ignored outside a Rummy table
            val g = RoomClient(scope)
            assertNull(g.join("127.0.0.1", host.port, NetMsg.Hello("G", 0, "x")))
            withTimeout(3000) { while (host.room.value.guests.size < 1) delay(20) }
            g.send(NetMsg.RummyAct(RAction.PickClosed(1)))
            delay(200)
            assertTrue(host.rummyActions.tryReceive().isFailure)
            assertFalse(host.room.value.inGame)
            host.close("done")
        } finally {
            delay(300)
            scope.cancel()
        }
    }
}
