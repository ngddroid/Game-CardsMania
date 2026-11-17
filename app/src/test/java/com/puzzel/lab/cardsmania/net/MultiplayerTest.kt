package com.puzzel.lab.cardsmania.net

import com.puzzel.lab.cardsmania.engine.Action
import com.puzzel.lab.cardsmania.engine.GameEngine
import com.puzzel.lab.cardsmania.engine.GameEvent
import com.puzzel.lab.cardsmania.engine.ai.BotAI
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.PlayerSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MultiplayerTest {

    @Test
    fun roomCodeRoundTrip() {
        for (ip in listOf("192.168.1.23", "10.69.188.183", "172.16.0.1", "255.255.255.255", "0.0.0.0")) {
            for (port in Net.PORT_BASE until Net.PORT_BASE + Net.PORT_COUNT) {
                val code = RoomCode.encode(ip, port)
                assertEquals(9, code.length)
                assertEquals(ip to port, RoomCode.decode(code))
                assertEquals(ip to port, RoomCode.decode(code.lowercase().replace("-", " ")))
            }
        }
        assertNull(RoomCode.decode("ABC"))
    }

    @Test
    fun messagesRoundTrip() {
        val cfg = com.puzzel.lab.cardsmania.engine.model.GameConfig(GameMode.ECLIPSE, List(3) { PlayerSetup("P$it", it, it < 2) }, seed = 5)
        val (eng, steps) = GameEngine.newGame(cfg)
        val msgs = listOf(
            NetMsg.Hello("Raj", 3, "tok"), NetMsg.Probe, NetMsg.Act(Action.Play(1, 7, listOf(8), true)), NetMsg.Leave,
            NetMsg.Welcome("g1"), NetMsg.Rejected("full"), NetMsg.Lobby(RoomState(code = "AAAA-BBBB", guests = listOf(RoomGuest("a", "b", 1)))),
            NetMsg.Start(1, cfg, 2), NetMsg.Sync(1, cfg, 1, Redact.state(eng.state, 1)),
            NetMsg.Steps(steps.map { NetStep(Redact.event(it.event, it.snapshot!!, 1), Redact.state(it.snapshot!!, 1)) }),
            NetMsg.ActionRejected("no"), NetMsg.Ended("bye"), NetMsg.Kicked, NetMsg.Emote(2, "😂"), NetMsg.Ping,
        )
        for (m in msgs) {
            val back = Net.decode(Net.encode(m))
            assertNotNull("decode failed for $m", back)
            assertEquals(Net.encode(m), Net.encode(back!!))
        }
    }

    @Test
    fun redactionHidesOtherHands() {
        for (mode in GameMode.entries) {
            val cfg = com.puzzel.lab.cardsmania.engine.model.GameConfig(mode, List(4) { PlayerSetup("P$it", it, true) }, seed = 9)
            val (eng, _) = GameEngine.newGame(cfg)
            val s = eng.state
            val v = Redact.state(s, 2)
            for (p in v.players) {
                for ((i, c) in p.hand.withIndex()) {
                    val real = s.players[p.id].hand[i]
                    assertEquals(real.id, c.id)
                    if (p.id == 2) assertEquals(real.face(s.side), c.face(s.side))
                    else assertEquals(Redact.HIDDEN, c.face(s.side))
                    if (mode == GameMode.ECLIPSE && p.id != 2) assertEquals(real.face(s.side.other), c.face(s.side.other))
                    if (mode == GameMode.ECLIPSE && p.id == 2) assertEquals(Redact.HIDDEN, c.face(s.side.other))
                }
            }
            assertTrue(v.drawPile.all { it.face(s.side) == Redact.HIDDEN })
            assertEquals(0L, v.rngState)
            assertEquals(s.discard, v.discard)
        }
    }

    /** A real host and two guests over localhost sockets play complete matches. */
    @Test
    fun hostAndTwoGuestsPlayFullMatches() = runBlocking {
        for (mode in GameMode.entries) playMatch(mode)
    }

    private suspend fun playMatch(mode: GameMode) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val host = RoomHost(scope, "Host", 0, { 1000 + it }, { i, _ -> "Bot$i" to i })
            assertTrue(host.start("127.0.0.1"))
            host.update { it.copy(mode = mode, seats = 4, bots = 1, handSize = 5) }

            val guests = List(2) { RoomClient(scope) }
            guests.forEachIndexed { i, g ->
                val err = g.join("127.0.0.1", host.port, NetMsg.Hello("Guest$i", i, "token-$i"))
                assertNull(err)
            }
            withTimeout(3000) { while (host.room.value.guests.size < 2) delay(20) }
            assertEquals(0, host.room.value.openSeats)
            // a third guest is rejected: the room is full
            val extra = RoomClient(scope)
            assertNotNull(extra.join("127.0.0.1", host.port, NetMsg.Hello("Late", 0, "token-x")))

            val cfg = host.startGame(1234L + mode.ordinal, PlayerSetup("Host", 1000, true))
            assertEquals(4, cfg.players.size)
            val (eng, initial) = GameEngine.newGame(cfg)
            host.stateProvider = { eng.state.copyDeep() }
            host.broadcastSteps(initial)

            // guests: decide from their own redacted view and send actions
            val guestDone = Channel<Int>(Channel.UNLIMITED)
            val guestError = Channel<Throwable>(Channel.UNLIMITED)
            guests.forEachIndexed { gi, g ->
                scope.launch {
                  try {
                    g.status.first { it == ClientStatus.InGame }
                    var me = -1
                    var latest: GameState? = null
                    var lastActedTurn = -1 to Any()
                    for (msg in g.game) {
                        when (msg) {
                            is NetMsg.Start -> me = msg.yourId
                            is NetMsg.Steps -> {
                                for (st in msg.steps) {
                                    // no hidden card from another player may leak to this guest
                                    for (p in st.state.players) if (p.id != me) assertTrue(p.hand.all { it.face(st.state.side) == Redact.HIDDEN })
                                    if (st.event is GameEvent.CardsDrawn && (st.event as GameEvent.CardsDrawn).playerId != me) {
                                        assertTrue((st.event as GameEvent.CardsDrawn).cards.all { it.face(st.state.side) == Redact.HIDDEN })
                                    }
                                }
                                latest = msg.steps.last().state
                            }
                            is NetMsg.Ended -> break
                            else -> Unit
                        }
                        val s = latest ?: continue
                        if (s.isOver) {
                            if (s.phase is Phase.MatchOver) guestDone.trySend(gi)
                            continue
                        }
                        if (s.actingPlayer == me) {
                            val key = s.turnCount to s.phase
                            if (key == lastActedTurn) continue
                            lastActedTurn = key
                            val a = BotAI.decide(s, me, Difficulty.NORMAL, Random(s.turnCount + gi)) ?: continue
                            // claim a fake seat: the host must force it back to ours
                            g.send(NetMsg.Act(a.withPlayer(0)))
                        }
                    }
                  } catch (t: Throwable) {
                    guestError.trySend(t)
                  }
                }
            }

            // host loop: bots + host seat locally, guest seats from the network
            val rnd = Random(77)
            var actions = 0
            withTimeout(60_000) {
                while (eng.state.phase !is Phase.MatchOver) {
                    val s = eng.state
                    if (s.phase is Phase.RoundOver) {
                        host.broadcastSteps(eng.apply(Action.NextRound(0)))
                        continue
                    }
                    val actor = s.actingPlayer
                    val action: Action = if (cfg.players[actor].isHuman && actor != 0) {
                        var a: Action? = null
                        while (a == null) {
                            guestError.tryReceive().getOrNull()?.let { throw AssertionError("guest failed", it) }
                            val (seat, act) = withTimeoutOrNull(500) { host.actions.receive() } ?: continue
                            assertEquals(seat, act.playerId)
                            if (seat == eng.state.actingPlayer && eng.validate(act) == null) a = act
                        }
                        a
                    } else {
                        BotAI.decide(s, actor, Difficulty.NORMAL, rnd) ?: Action.Draw(actor)
                    }
                    val st = eng.apply(action)
                    host.broadcastSteps(st)
                    actions++
                }
            }
            withTimeout(10_000) {
                val seen = HashSet<Int>()
                while (seen.size < 2) seen += guestDone.receive()
            }
            assertTrue(actions > 3)
            host.close("done")
        } finally {
            delay(500)
            scope.cancel()
        }
    }
}
