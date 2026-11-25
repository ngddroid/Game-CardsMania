package com.ngdstudio.mindimania.teenpatti

import com.ngdstudio.mindimania.data.AppJson
import com.ngdstudio.mindimania.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TeenPattiEngineTest {

    /** A card with its real deck id. Ranks 2..14, suits 0 ♠ 1 ♥ 2 ♦ 3 ♣. */
    private fun c(rank: Int, suit: Int) = TCard(suit * 13 + rank - 2, rank, suit)

    private fun sc(vararg cards: TCard, a23Second: Boolean = true) = TeenPattiEngine.score(cards.toList(), a23Second)

    /** A hand in progress with fixed cards; seat 0 deals, so seat 1 acts first. */
    private fun state(hands: List<List<TCard>>, chips: Int = 1000, sideshow: Boolean = true): TeenPattiState {
        val n = hands.size
        val base = TeenPattiEngine.newGame(TeenPattiSetup(players = n, sideshow = sideshow), 7L).last().state
        val put = List(n) { TeenPattiEngine.BOOT }
        return base.copy(
            dealer = 0, hands = hands, chips = List(n) { chips }, put = put, pot = put.sum(),
            turn = 1 % n, inHand = List(n) { true }, folded = List(n) { false }, seen = List(n) { false },
        )
    }

    private fun TeenPattiState.act(a: TPAction): TeenPattiState = TeenPattiEngine.apply(this, a).last().state

    @Test
    fun handKindsRankInTheRightOrder() {
        val trail = sc(c(2, 0), c(2, 1), c(2, 2))
        val pure = sc(c(14, 1), c(13, 1), c(12, 1))
        val seq = sc(c(14, 1), c(13, 2), c(12, 1))
        val color = sc(c(14, 3), c(10, 3), c(4, 3))
        val pair = sc(c(14, 0), c(14, 1), c(13, 2))
        val high = sc(c(14, 0), c(13, 1), c(11, 2))
        assertTrue(trail > pure)
        assertTrue(pure > seq)
        assertTrue(seq > color)
        assertTrue(color > pair)
        assertTrue(pair > high)
        assertEquals(TPKind.TRAIL, TeenPattiEngine.kind(listOf(c(2, 0), c(2, 1), c(2, 2))))
        assertEquals(TPKind.PURE_SEQUENCE, TeenPattiEngine.kind(listOf(c(4, 2), c(3, 2), c(2, 2))))
        assertEquals(TPKind.SEQUENCE, TeenPattiEngine.kind(listOf(c(14, 0), c(2, 1), c(3, 2))))
        assertEquals(TPKind.COLOR, TeenPattiEngine.kind(listOf(c(9, 1), c(5, 1), c(2, 1))))
        assertEquals(TPKind.PAIR, TeenPattiEngine.kind(listOf(c(9, 1), c(9, 3), c(2, 1))))
        assertEquals(TPKind.HIGH_CARD, TeenPattiEngine.kind(listOf(c(9, 1), c(8, 3), c(2, 1))))
    }

    @Test
    fun tiesInsideAKindAreBrokenByRanksNotSuits() {
        assertTrue(sc(c(14, 0), c(14, 1), c(14, 2)) > sc(c(13, 0), c(13, 1), c(13, 2)))
        // pair: the pair first, then the kicker
        assertTrue(sc(c(10, 0), c(10, 1), c(2, 2)) > sc(c(9, 0), c(9, 1), c(14, 2)))
        assertTrue(sc(c(10, 0), c(10, 1), c(5, 2)) > sc(c(10, 2), c(10, 3), c(4, 0)))
        // high card / colour: compare top card, then the next
        assertTrue(sc(c(14, 0), c(9, 1), c(3, 2)) > sc(c(14, 1), c(8, 2), c(7, 3)))
        assertTrue(sc(c(13, 1), c(9, 1), c(3, 1)) > sc(c(13, 3), c(9, 3), c(2, 3)))
        // same ranks, different suits: a tie
        assertEquals(sc(c(14, 0), c(9, 1), c(3, 2)), sc(c(14, 1), c(9, 2), c(3, 3)))
        assertEquals(sc(c(7, 0), c(6, 0), c(5, 0)), sc(c(7, 1), c(6, 1), c(5, 1)))
    }

    @Test
    fun aceTwoThreeIsSecondOrLowestDependingOnTheRule() {
        val akq = { a: Boolean -> sc(c(14, 0), c(13, 1), c(12, 2), a23Second = a) }
        val a23 = { a: Boolean -> sc(c(14, 0), c(2, 1), c(3, 2), a23Second = a) }
        val kqj = { a: Boolean -> sc(c(13, 0), c(12, 1), c(11, 2), a23Second = a) }
        val l432 = { a: Boolean -> sc(c(4, 0), c(3, 1), c(2, 2), a23Second = a) }
        assertTrue(akq(true) > a23(true))
        assertTrue(a23(true) > kqj(true))
        assertTrue(akq(false) > kqj(false))
        assertTrue(l432(false) > a23(false))
        // Q-K-A wraps only at the top: K-A-2 is not a run
        assertEquals(TPKind.HIGH_CARD, TeenPattiEngine.kind(listOf(c(13, 0), c(14, 1), c(2, 2))))
    }

    @Test
    fun percentileCoversTheWholeRange() {
        val best = TeenPattiEngine.percentile(sc(c(14, 0), c(14, 1), c(14, 2)), true)
        val worst = TeenPattiEngine.percentile(sc(c(5, 0), c(3, 1), c(2, 2)), true)
        assertTrue(best > 0.999)
        assertTrue(worst < 0.005)
        // a pair beats roughly 83% of hands
        val pair = TeenPattiEngine.percentile(sc(c(2, 0), c(2, 1), c(3, 2)), true)
        assertTrue("pair percentile $pair", pair in 0.70..0.80)
    }

    @Test
    fun newGameDealsThreeCardsAndCollectsTheBoot() {
        for (n in 3..6) for (seed in 1L..20L) {
            val s = TeenPattiEngine.newGame(TeenPattiSetup(players = n), seed).last().state
            assertEquals(1, s.handNo)
            val all = s.hands.flatten()
            assertEquals(3 * n, all.size)
            assertEquals(all.size, all.map { it.id }.toSet().size)
            assertTrue(s.chips.all { it == TeenPattiEngine.START_CHIPS - TeenPattiEngine.BOOT })
            assertEquals(n * TeenPattiEngine.BOOT, s.pot)
            assertEquals((s.dealer + 1) % n, s.turn)
            assertTrue(s.seen.none { it })
        }
    }

    @Test
    fun blindPaysTheStakeSeenPaysDoubleAndRaiseDoublesTheStake() {
        var s = state(listOf(listOf(c(2, 0), c(5, 1), c(9, 2)), listOf(c(3, 0), c(6, 1), c(10, 2)), listOf(c(4, 0), c(7, 1), c(11, 2))))
        s = s.act(TPAction.Chaal(1))
        assertEquals(1000 - 20, s.chips[1])
        assertEquals(2, s.turn)
        s = s.act(TPAction.See(2))
        assertEquals(2, s.turn) // looking does not end the turn
        assertNotNull(TeenPattiEngine.validate(s, TPAction.See(2)))
        s = s.act(TPAction.Chaal(2, raise = true))
        assertEquals(1000 - 80, s.chips[2])
        assertEquals(40, s.stake)
        assertEquals(0, s.turn)
        assertEquals(40, TeenPattiEngine.chaalCost(s, 0, false))
        assertEquals(60 + 20 + 80, s.pot)
    }

    @Test
    fun stakeNeverGoesAboveTheLimit() {
        var s = state(List(3) { i -> listOf(c(2 + i, 0), c(5 + i, 1), c(9 + i, 2)) }, chips = 100_000)
        repeat(20) {
            val o = TeenPattiEngine.options(s, s.turn)
            s = s.act(TPAction.Chaal(s.turn, raise = o.canRaise))
        }
        assertEquals(TeenPattiEngine.MAX_STAKE, s.stake)
        assertFalse(TeenPattiEngine.options(s, s.turn).canRaise)
    }

    @Test
    fun outOfTurnMovesAreRefusedButSeeingIsAllowed() {
        val s = state(List(3) { i -> listOf(c(2 + i, 0), c(5 + i, 1), c(9 + i, 2)) })
        assertNotNull(TeenPattiEngine.validate(s, TPAction.Chaal(2)))
        assertNotNull(TeenPattiEngine.validate(s, TPAction.Pack(0)))
        assertNull(TeenPattiEngine.validate(s, TPAction.See(0)))
        assertNotNull(TeenPattiEngine.validate(s, TPAction.Show(1))) // three players: no show yet
        assertNotNull(TeenPattiEngine.validate(s, TPAction.Sideshow(1))) // blind: no sideshow
    }

    @Test
    fun lastPlayerStandingTakesThePot() {
        var s = state(List(3) { i -> listOf(c(2 + i, 0), c(5 + i, 1), c(9 + i, 2)) })
        s = s.act(TPAction.Chaal(1))
        s = s.act(TPAction.Pack(2))
        val steps = TeenPattiEngine.apply(s, TPAction.Pack(0))
        val won = steps.map { it.event }.filterIsInstance<TPEvent.PotWon>().single()
        assertEquals(listOf(1), won.winners)
        assertEquals(WinReason.LAST_STANDING, won.reason)
        val end = steps.last().state
        assertEquals(1000 - 20 + 80, end.chips[1])
        assertTrue(end.phase is TPPhase.HandOver)
        assertTrue(end.shown.isEmpty()) // nobody has to show a hand nobody called
    }

    @Test
    fun showComparesBothHandsAndATieGoesAgainstTheAsker() {
        val same1 = listOf(c(14, 0), c(9, 1), c(3, 2))
        val same2 = listOf(c(14, 1), c(9, 2), c(3, 3))
        var s = state(listOf(listOf(c(2, 0), c(4, 1), c(7, 2)), same1, same2))
        s = s.act(TPAction.Chaal(1))
        s = s.act(TPAction.Chaal(2))
        s = s.act(TPAction.Pack(0))
        assertEquals(1, s.turn)
        val steps = TeenPattiEngine.apply(s, TPAction.Show(1))
        val sd = steps.map { it.event }.filterIsInstance<TPEvent.Showdown>().single()
        assertEquals(listOf(1, 2), sd.seats)
        assertEquals(listOf(2), sd.winners)
        assertEquals(listOf(1, 2), steps.last().state.shown)
    }

    @Test
    fun showWithTheBetterHandWins() {
        var s = state(listOf(listOf(c(2, 0), c(4, 1), c(7, 2)), listOf(c(13, 0), c(13, 1), c(2, 2)), listOf(c(14, 1), c(9, 2), c(3, 3))))
        s = s.act(TPAction.Pack(1))
        s = s.act(TPAction.Chaal(2))
        assertEquals(0, s.turn)
        val won = TeenPattiEngine.apply(s, TPAction.Show(0)).map { it.event }.filterIsInstance<TPEvent.PotWon>().single()
        assertEquals(listOf(2), won.winners)
    }

    @Test
    fun sideshowAcceptedFoldsTheLowerHandAndDeclinedKeepsBoth() {
        val strong = listOf(c(12, 0), c(12, 1), c(12, 2))
        val weak = listOf(c(2, 0), c(5, 1), c(9, 3))
        var s = state(listOf(listOf(c(3, 3), c(6, 3), c(8, 1)), weak, strong, listOf(c(4, 2), c(7, 2), c(11, 0))))
        s = s.act(TPAction.See(1)).act(TPAction.Chaal(1))
        s = s.act(TPAction.See(2))
        assertTrue(TeenPattiEngine.options(s, 2).canSideshow)
        val asked = s.act(TPAction.Sideshow(2))
        assertEquals(TPPhase.Sideshow(2, 1), asked.phase)
        assertEquals(1, asked.turn)
        assertNotNull(TeenPattiEngine.validate(asked, TPAction.Chaal(3)))
        val accepted = asked.act(TPAction.SideshowReply(1, accept = true))
        assertTrue(accepted.folded[1])
        assertFalse(accepted.folded[2])
        assertEquals(3, accepted.turn)
        val declined = asked.act(TPAction.SideshowReply(1, accept = false))
        assertFalse(declined.folded[1])
        assertEquals(3, declined.turn)
        assertEquals(TPPhase.Betting, declined.phase)
    }

    @Test
    fun allInShowsEveryoneLeftAndSplitsTies() {
        val tieA = listOf(c(8, 0), c(8, 1), c(2, 2))
        val tieB = listOf(c(8, 2), c(8, 3), c(2, 3))
        var s = state(listOf(tieA, listOf(c(2, 0), c(5, 1), c(9, 3)), tieB))
        s = s.copy(chips = listOf(1000, 10, 1000))
        assertTrue(TeenPattiEngine.options(s, 1).canAllIn)
        assertFalse(TeenPattiEngine.options(s, 1).canChaal)
        val steps = TeenPattiEngine.apply(s, TPAction.AllIn(1))
        val won = steps.map { it.event }.filterIsInstance<TPEvent.PotWon>().single()
        assertEquals(setOf(0, 2), won.winners.toSet())
        assertEquals(60 + 10, won.amounts.sum())
        assertEquals(listOf(35, 35), won.amounts)
    }

    @Test
    fun matchEndsAfterTheLastHand() {
        var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 3, hands = 2), 3L).last().state
        repeat(2) { h ->
            while (!s.handOver) s = s.act(TPAction.Pack(s.turn))
            if (h == 0) {
                assertTrue(s.phase is TPPhase.HandOver)
                s = TeenPattiEngine.nextHand(s).last().state
                assertEquals(2, s.handNo)
            }
        }
        assertTrue(s.isOver)
        assertEquals(3 * TeenPattiEngine.START_CHIPS, s.chips.sum())
    }

    @Test
    fun botsPlayWholeMatchesLegallyAndChipsAreNeverCreated() {
        val rnd = Random(42)
        for (n in 3..6) for (d in Difficulty.entries) for (g in 0 until 6) {
            var s = TeenPattiEngine.newGame(TeenPattiSetup(players = n, hands = 10, sideshow = g % 2 == 0, a23Second = g % 3 != 0), rnd.nextLong()).last().state
            var moves = 0
            while (!s.isOver) {
                if (s.phase is TPPhase.HandOver) {
                    s = TeenPattiEngine.nextHand(s).last().state
                    continue
                }
                val actor = s.turn
                val a = TeenPattiBot.decide(s, actor, d, rnd)
                assertNotNull("bot $actor has no move in $s", a)
                assertNull("illegal bot move $a", TeenPattiEngine.validate(s, a!!))
                s = TeenPattiEngine.apply(s, a).last().state
                assertEquals(n * TeenPattiEngine.START_CHIPS, s.chips.sum() + if (s.handOver) 0 else s.pot)
                assertTrue(s.chips.all { it >= 0 })
                assertTrue("runaway match", ++moves < 5000)
            }
            assertEquals(n * TeenPattiEngine.START_CHIPS, s.chips.sum())
        }
    }

    @Test
    fun stateSurvivesASaveAndLoad() {
        var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4), 9L).last().state
        s = s.act(TPAction.See(s.turn))
        val text = AppJson.encodeToString(TeenPattiState.serializer(), s)
        assertEquals(s, AppJson.decodeFromString(TeenPattiState.serializer(), text))
        val side = s.copy(phase = TPPhase.Sideshow(1, 0))
        assertEquals(side, AppJson.decodeFromString(TeenPattiState.serializer(), AppJson.encodeToString(TeenPattiState.serializer(), side)))
    }

    @Test
    fun anEndlessTableKeepsDealingAndRefillsBrokePlayers() {
        val rnd = Random(77)
        var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4), 21L).last().state
        assertTrue(s.endless)
        var refills = 0
        var hands = 0
        while (hands < 120) {
            if (s.phase is TPPhase.HandOver) {
                val steps = TeenPattiEngine.nextHand(s)
                steps.map { it.event }.filterIsInstance<TPEvent.Refilled>().forEach { r ->
                    refills += r.seats.size
                    r.seats.forEach { assertEquals(TeenPattiEngine.START_CHIPS, steps.last().state.chips[it] + steps.last().state.put[it]) }
                }
                s = steps.last().state
                hands++
                assertTrue("everyone is dealt in", s.inHand.all { it })
                continue
            }
            assertFalse(s.isOver)
            s = TeenPattiEngine.apply(s, TeenPattiBot.decide(s, s.turn, Difficulty.EASY, rnd)!!).last().state
        }
        assertFalse(s.isOver)
        assertTrue("someone went broke and was refilled in 120 hands", refills > 0)
    }

    @Test
    fun savesFromWhenVariationsExistedStillLoadAsClassic() {
        // a setup and a game saved by the old version, with a variation and a turned-up joker
        val setup = AppJson.decodeFromString(TeenPattiSetup.serializer(), """{"players":5,"variant":"AK47","sideshow":false}""")
        assertEquals(5, setup.players)
        assertFalse(setup.sideshow)
        val s = TeenPattiEngine.newGame(setup, 3L).last().state
        val json = AppJson.encodeToString(TeenPattiState.serializer(), s).dropLast(1) +
            ""","variant":"JOKER","joker":{"id":7,"rank":9,"suit":0}}"""
        val back = AppJson.decodeFromString(TeenPattiState.serializer(), json)
        assertEquals(s, back)
        assertEquals(TeenPattiEngine.BOOT, back.boot)
    }
}
