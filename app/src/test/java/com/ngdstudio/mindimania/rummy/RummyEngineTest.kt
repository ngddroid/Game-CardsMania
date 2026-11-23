package com.ngdstudio.mindimania.rummy

import com.ngdstudio.mindimania.data.AppJson
import com.ngdstudio.mindimania.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RummyEngineTest {
    private var nextId = 200
    /** rank 1..13, suit 0 ♠ 1 ♥ 2 ♦ 3 ♣; 0 = printed joker. */
    private fun c(rank: Int, suit: Int) = RCard(nextId++, rank, if (rank == 0) -1 else suit)
    private val J get() = c(0, -1)
    private val noWild = 99 // no wild rank in these tests unless given

    @Test
    fun groupKinds() {
        assertEquals(GroupKind.PURE, RummyMelds.classify(listOf(c(3, 2), c(4, 2), c(5, 2)), noWild))
        assertEquals(GroupKind.PURE, RummyMelds.classify(listOf(c(12, 1), c(13, 1), c(1, 1)), noWild)) // Q-K-A
        assertEquals(GroupKind.PURE, RummyMelds.classify(listOf(c(1, 0), c(2, 0), c(3, 0)), noWild)) // A-2-3
        assertEquals(GroupKind.INVALID, RummyMelds.classify(listOf(c(13, 0), c(1, 0), c(2, 0)), noWild)) // no wrap
        assertEquals(GroupKind.IMPURE, RummyMelds.classify(listOf(c(5, 1), J, c(7, 1)), noWild))
        assertEquals(GroupKind.IMPURE, RummyMelds.classify(listOf(c(1, 1), J, c(3, 1), c(4, 1)), noWild))
        assertEquals(GroupKind.SET, RummyMelds.classify(listOf(c(10, 3), c(10, 0), c(10, 1)), noWild))
        assertEquals(GroupKind.SET, RummyMelds.classify(listOf(c(5, 1), J, c(5, 0)), noWild))
        assertEquals(GroupKind.INVALID, RummyMelds.classify(listOf(c(10, 3), c(10, 3), c(10, 1)), noWild)) // same suit twice
        assertEquals(GroupKind.INVALID, RummyMelds.classify(listOf(c(2, 3), c(2, 2)), noWild))
        assertEquals(GroupKind.JOKERS, RummyMelds.classify(listOf(J, J), noWild))
        // a wild-rank card in its own place keeps a sequence pure
        assertEquals(GroupKind.PURE, RummyMelds.classify(listOf(c(6, 1), c(7, 1), c(8, 1)), 7))
        assertEquals(GroupKind.IMPURE, RummyMelds.classify(listOf(c(6, 1), c(7, 0), c(8, 1)), 7))
    }

    @Test
    fun pointsFollowTheRules() {
        val pure = listOf(c(3, 2), c(4, 2), c(5, 2))
        val impure = listOf(c(5, 1), J, c(7, 1))
        val set = listOf(c(10, 3), c(10, 0), c(10, 1))
        val junk = listOf(c(13, 0), c(2, 3), c(9, 1), c(1, 2))
        // valid: 0
        val ok = RummyMelds.evaluate(listOf(pure, impure, set, listOf(c(8, 0), c(8, 1), c(8, 2), J)), noWild)
        assertTrue(ok.valid)
        assertEquals(0, ok.points)
        // two sequences: only the junk counts (10 + 2 + 9 + 10)
        assertEquals(31, RummyMelds.evaluate(listOf(pure, impure, set, junk), noWild).points)
        // a pure but no second sequence: everything except the pure counts (sets count too)
        assertEquals(12 + 30 + 31, RummyMelds.evaluate(listOf(pure, set, junk, listOf(c(5, 1), c(7, 1))), noWild).points)
        // no pure: every card counts, capped at 80
        assertEquals(80, RummyMelds.evaluate(listOf(impure, set, junk, listOf(c(13, 1), c(12, 1))), noWild).points)
    }

    @Test
    fun bestFindsAValidHand() {
        val hand = listOf(c(3, 2), c(5, 2), c(4, 2), c(5, 1), J, c(5, 0), c(1, 1), J, c(3, 1), c(4, 1), c(10, 3), c(10, 0), c(10, 1))
        val b = RummyMelds.best(hand, noWild)
        assertTrue(b.groups.toString(), b.valid)
        assertEquals(0, b.points)
        assertEquals(13, b.groups.flatten().size)
        // with a wild rank: two 7s act as jokers
        val h2 = listOf(c(9, 0), c(10, 0), c(11, 0), c(2, 1), c(4, 1), c(7, 3), c(6, 2), c(6, 3), c(6, 0), c(13, 1), c(13, 2), c(7, 0), c(1, 3))
        val b2 = RummyMelds.best(h2, 7)
        assertEquals(b2.groups.toString(), 10, b2.points) // A♣ left over; 2-[7]-4♥, 6-6-6, K-K-[7]
    }

    @Test
    fun bestIsFastEnoughForBots() {
        val rnd = Random(3)
        val deck = RummyEngine.deck()
        var worst = 0L
        repeat(150) {
            val hand = deck.shuffled(rnd).take(14)
            val wild = 1 + rnd.nextInt(13)
            val t0 = System.nanoTime()
            for (d in hand) RummyMelds.best(hand - d, wild, budget = 8_000)
            worst = maxOf(worst, (System.nanoTime() - t0) / 1_000_000)
        }
        assertTrue("worst 14-throw scan took $worst ms", worst < 1500)
    }

    @Test
    fun dealPicksAndThrows() {
        val s0 = RummyEngine.newGame(RummySetup(players = 4), 5L).last().state
        assertTrue(s0.hands.all { it.size == 13 })
        assertEquals(106, s0.hands.sumOf { it.size } + s0.closed.size + s0.open.size + 1)
        assertEquals(RPhase.Pick, s0.phase)
        val me = s0.turn
        assertNotNull(RummyEngine.validate(s0, RAction.Discard(me, s0.hands[me][0].id)))
        val s1 = RummyEngine.apply(s0, RAction.PickClosed(me)).last().state
        assertEquals(14, s1.hands[me].size)
        assertEquals(RPhase.Discard, s1.phase)
        val s2 = RummyEngine.apply(s1, RAction.Discard(me, s1.hands[me][0].id)).last().state
        assertEquals(13, s2.hands[me].size)
        assertEquals((me + 1) % 4, s2.turn)
        assertEquals(2, s2.open.size)
    }

    @Test
    fun dropsCostTwentyThenForty() {
        var s = RummyEngine.newGame(RummySetup(players = 3), 9L).last().state
        val a = s.turn
        s = RummyEngine.apply(s, RAction.Drop(a)).last().state
        assertEquals(20, s.points[a])
        val b = s.turn
        s = RummyEngine.apply(s, RAction.PickClosed(b)).last().state
        s = RummyEngine.apply(s, RAction.Discard(b, s.hands[b][0].id)).last().state
        val c = s.turn
        s = RummyEngine.apply(s, RAction.PickClosed(c)).last().state
        s = RummyEngine.apply(s, RAction.Discard(c, s.hands[c][0].id)).last().state
        val steps = RummyEngine.apply(s, RAction.Drop(b))
        val end = steps.last().state
        assertEquals(40, end.points[b])
        assertTrue(end.handOver)
        val won = steps.map { it.event }.filterIsInstance<REvent.HandWon>().single()
        assertEquals(c, won.winner)
        assertEquals((20 + 40) * RummyEngine.POINT_VALUE, won.delta[c])
    }

    @Test
    fun aWrongShowCostsEightyAndPlayGoesOn() {
        var s = RummyEngine.newGame(RummySetup(players = 3), 2L).last().state
        val a = s.turn
        s = RummyEngine.apply(s, RAction.PickClosed(a)).last().state
        val card = s.hands[a][0]
        val rest = s.hands[a] - card
        s = RummyEngine.apply(s, RAction.Declare(a, card.id, listOf(rest.map { it.id }))).last().state
        assertEquals(Finish.WRONG_SHOW, s.finish[a])
        assertEquals(80, s.points[a])
        assertFalse(s.handOver)
        assertEquals(2, s.active.size)
    }

    @Test
    fun aValidDeclarationGoesToShowdownThenSettles() {
        var s = RummyEngine.newGame(RummySetup(players = 3), 4L).last().state
        val a = s.turn
        // give seat a a winning hand + one spare card
        val win = listOf(c(3, 2), c(5, 2), c(4, 2), c(5, 1), J, c(5, 0), c(1, 1), J, c(3, 1), c(4, 1), c(10, 3), c(10, 0), c(10, 1))
        s = s.copy(hands = s.hands.toMutableList().also { it[a] = win }, wild = 99)
        s = RummyEngine.apply(s, RAction.PickClosed(a)).last().state
        val spare = s.hands[a].last()
        val groups = RummyMelds.best(s.hands[a] - spare, s.wild).groups.map { g -> g.map { it.id } }
        s = RummyEngine.apply(s, RAction.Declare(a, spare.id, groups)).last().state
        val ph = s.phase as RPhase.Showdown
        assertEquals(2, ph.pending.size)
        for (p in ph.pending) {
            val g = RummyMelds.best(s.hands[p], s.wild).groups.map { gg -> gg.map { it.id } }
            s = RummyEngine.apply(s, RAction.Arrange(p, g)).last().state
        }
        assertTrue(s.handOver)
        assertEquals(0, s.delta.sum())
        assertTrue(s.delta[a] > 0)
        assertEquals(3 * RummyEngine.START_CHIPS, s.chips.sum())
    }

    @Test
    fun botsPlayEndlessTablesLegally() {
        val rnd = Random(11)
        for (n in 2..6) for (d in Difficulty.entries) {
            var s = RummyEngine.newGame(RummySetup(players = n, difficulty = d), rnd.nextLong()).last().state
            var hands = 0
            var moves = 0
            var declares = 0
            while (hands < 2) {
                if (s.handOver) {
                    hands++
                    s = RummyEngine.nextHand(s).last().state
                    continue
                }
                val actor = (s.phase as? RPhase.Showdown)?.pending?.first() ?: s.turn
                val a = RummyBot.decide(s, actor, d, rnd)
                assertNotNull("no move for $actor in ${s.phase}", a)
                assertNull("illegal $a", RummyEngine.validate(s, a!!))
                val steps = RummyEngine.apply(s, a)
                declares += steps.count { it.event is REvent.Declared }
                s = steps.last().state
                assertEquals(n * RummyEngine.START_CHIPS, s.chips.sum())
                assertTrue("runaway hand", ++moves < 3000)
            }
            assertTrue("$n players $d: nobody ever declared", declares > 0 || n == 2)
        }
    }

    @Test
    fun stateSurvivesASaveAndLoad() {
        var s = RummyEngine.newGame(RummySetup(players = 4), 8L).last().state
        s = RummyEngine.apply(s, RAction.PickOpen(s.turn)).last().state
        assertEquals(s, AppJson.decodeFromString(RummyState.serializer(), AppJson.encodeToString(RummyState.serializer(), s)))
    }
}
