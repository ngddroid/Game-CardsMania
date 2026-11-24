package com.ngdstudio.mindimania.rummy

import com.ngdstudio.mindimania.data.AppJson
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.net.Net
import com.ngdstudio.mindimania.net.NetMsg
import com.ngdstudio.mindimania.net.RummyRedact
import com.ngdstudio.mindimania.net.withSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Declaring in two steps: the card goes to the finish slot, the player arranges, then shows the groups. */
class RummyDeclareStepTest {
    private var nextId = 500
    /** rank 1..13, suit 0 ♠ 1 ♥ 2 ♦ 3 ♣. */
    private fun c(rank: Int, suit: Int) = RCard(nextId++, rank, suit)

    /** A table where seat [seat] holds [hand] (14 cards) and must throw or declare. No wild rank in play. */
    private fun tableWith(hand: List<RCard>, seat: Int = 0): RummyState {
        val s = RummyEngine.newGame(RummySetup(players = 3), 3L).last().state
        return s.copy(hands = s.hands.mapIndexed { i, h -> if (i == seat) hand else h }, turn = seat, phase = RPhase.Discard, wild = 99)
    }

    // a complete hand: two pure sequences, a third sequence and a set, plus one card to finish with
    private val a = listOf(c(3, 2), c(4, 2), c(5, 2))
    private val b = listOf(c(7, 1), c(8, 1), c(9, 1), c(10, 1))
    private val d = listOf(c(11, 3), c(12, 3), c(13, 3))
    private val e = listOf(c(6, 0), c(6, 1), c(6, 3))
    private val finish = c(2, 0)
    private val full = a + b + d + e + finish
    private fun ids(vararg g: List<RCard>) = g.map { gr -> gr.map { it.id } }

    @Test
    fun theCardGoesToTheSlotAndOnlyADeclarationMayFollow() {
        val s = tableWith(full)
        val steps = RummyEngine.apply(s, RAction.StartDeclare(0, finish.id))
        val st = steps.last().state
        assertEquals(REvent.DeclareStarted(0, finish), steps.last().event)
        assertEquals(RPhase.Declaring(0, finish), st.phase)
        assertEquals(13, st.hands[0].size)
        assertEquals(0, st.turn)
        assertNotNull(RummyEngine.validate(st, RAction.Discard(0, a[0].id)))
        assertNotNull(RummyEngine.validate(st, RAction.Drop(0)))
        assertNotNull(RummyEngine.validate(st, RAction.PickClosed(0)))
        assertNotNull(RummyEngine.validate(st, RAction.Declare(1, finish.id, ids(a, b, d, e)))) // someone else
        assertNotNull(RummyEngine.validate(st, RAction.Declare(0, a[0].id, ids(a, b, d, e)))) // not the slot card
        assertNotNull(RummyEngine.validate(st, RAction.Declare(0, finish.id, ids(a, b, d)))) // a card left out
        // and it can't start before picking
        assertNotNull(RummyEngine.validate(s.copy(phase = RPhase.Pick), RAction.StartDeclare(0, finish.id)))
    }

    @Test
    fun theArrangementShownIsTheOneThatCounts() {
        val st = RummyEngine.apply(tableWith(full), RAction.StartDeclare(0, finish.id)).last().state
        // arranged well: a valid declaration
        val good = RummyEngine.apply(st, RAction.Declare(0, finish.id, ids(a, b, d, e)))
        assertTrue(good.any { it.event == REvent.Declared(0, finish) })
        assertTrue(good.last().state.phase is RPhase.Showdown)
        assertEquals(Finish.WON, good.last().state.finish[0])
        // the same cards left in a muddle: a wrong show, and the slot card goes to the open pile
        val muddle = listOf((a + b).map { it.id }, (d + e).map { it.id })
        val bad = RummyEngine.apply(st, RAction.Declare(0, finish.id, muddle)).last().state
        assertEquals(Finish.WRONG_SHOW, bad.finish[0])
        assertEquals(RummyEngine.WRONG_SHOW, bad.points[0])
        assertEquals(finish, bad.openTop)
        assertTrue(bad.phase == RPhase.Pick && bad.turn != 0)
    }

    @Test
    fun aBotFinishesForAPlayerWhoLeftMidDeclaration() {
        val st = RummyEngine.apply(tableWith(full), RAction.StartDeclare(0, finish.id)).last().state
        val act = RummyBot.decide(st, 0, Difficulty.NORMAL, Random(1))
        assertTrue(act is RAction.Declare)
        assertNull(RummyEngine.validate(st, act!!))
        assertEquals(Finish.WON, RummyEngine.apply(st, act).last().state.finish[0])
        assertNull(RummyBot.decide(st, 1, Difficulty.NORMAL, Random(1)))
        // bots still declare in one go from the discard step
        assertTrue(RummyBot.decide(tableWith(full), 0, Difficulty.NORMAL, Random(1)) is RAction.Declare)
    }

    @Test
    fun othersSeeTheSlotCardFaceDown() {
        val steps = RummyEngine.apply(tableWith(full), RAction.StartDeclare(0, finish.id))
        val st = steps.last().state
        val mine = RummyRedact.step(steps.last().event, st, 0)
        assertEquals(RPhase.Declaring(0, finish), mine.state.phase)
        assertEquals(REvent.DeclareStarted(0, finish), mine.event)
        for (viewer in 1..2) {
            val net = RummyRedact.step(steps.last().event, st, viewer)
            val ph = net.state.phase as RPhase.Declaring
            assertTrue(RummyRedact.isPlaceholder(ph.card))
            assertTrue(RummyRedact.isPlaceholder((net.event as REvent.DeclareStarted).card))
            assertTrue(RummyRedact.leaks(net.state, viewer).isEmpty())
        }
        // a real card there would be reported
        assertFalse(RummyRedact.leaks(st.copy(hands = RummyRedact.state(st, 1).hands, closed = RummyRedact.state(st, 1).closed), 1).isEmpty())
    }

    @Test
    fun theNewMovesTravelOverWifiAndSurviveASave() {
        val st = RummyEngine.apply(tableWith(full), RAction.StartDeclare(0, finish.id)).last().state
        for (m in listOf<NetMsg>(NetMsg.RummyAct(RAction.StartDeclare(2, finish.id).withSeat(1)))) {
            val back = Net.decode(Net.encode(m)) as NetMsg.RummyAct
            assertEquals(RAction.StartDeclare(1, finish.id), back.action)
        }
        val json = AppJson.encodeToString(RummyState.serializer(), st)
        assertEquals(st, AppJson.decodeFromString(RummyState.serializer(), json))
    }
}
