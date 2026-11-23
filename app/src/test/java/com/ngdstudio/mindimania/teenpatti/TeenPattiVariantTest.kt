package com.ngdstudio.mindimania.teenpatti

import com.ngdstudio.mindimania.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TeenPattiVariantTest {

    private fun c(rank: Int, suit: Int) = TCard(suit * 13 + rank - 2, rank, suit)
    private fun hs(v: TPVariant, vararg cards: TCard, joker: TCard? = null) = TeenPattiEngine.handScore(cards.toList(), v, true, joker)

    @Test
    fun muflisTurnsTheRankingUpsideDown() {
        val trail = hs(TPVariant.MUFLIS, c(14, 0), c(14, 1), c(14, 2))
        val low = hs(TPVariant.MUFLIS, c(5, 0), c(3, 1), c(2, 2))
        val pair = hs(TPVariant.MUFLIS, c(9, 0), c(9, 1), c(2, 2))
        assertTrue(low > pair)
        assertTrue(pair > trail)
    }

    @Test
    fun jokerRankIsWildForEveryone() {
        val joker = c(7, 3)
        // 7 + pair of Queens = trail of Queens
        assertEquals(TPKind.TRAIL.ordinal * 100_000L + 12, hs(TPVariant.JOKER, c(12, 0), c(12, 1), c(7, 2), joker = joker))
        // 7 + J♥ 10♥ = pure sequence Q-J-10
        val pure = hs(TPVariant.JOKER, c(11, 1), c(10, 1), c(7, 0), joker = joker)
        assertEquals(TPKind.PURE_SEQUENCE, TeenPattiEngine.kindOf(pure))
        // 7 + A♠ K♥ = A-K-Q sequence, the best run
        assertEquals(TPKind.SEQUENCE.ordinal * 100_000L + 28, hs(TPVariant.JOKER, c(14, 0), c(13, 1), c(7, 0), joker = joker))
        // two jokers copy the third card
        assertEquals(TPKind.TRAIL.ordinal * 100_000L + 5, hs(TPVariant.JOKER, c(7, 0), c(7, 1), c(5, 2), joker = joker))
        // no joker: classic
        assertEquals(TeenPattiEngine.score(listOf(c(2, 0), c(9, 1), c(13, 2)), true), hs(TPVariant.JOKER, c(2, 0), c(9, 1), c(13, 2), joker = joker))
    }

    @Test
    fun ak47AndLowestJokerWilds() {
        assertEquals(TPKind.TRAIL, TeenPattiEngine.kindOf(hs(TPVariant.AK47, c(14, 0), c(4, 1), c(9, 2))))
        assertEquals(TPKind.TRAIL.ordinal * 100_000L + 14, hs(TPVariant.AK47, c(14, 0), c(13, 1), c(7, 2)))
        // lowest card (2) wild: 2 + 9 + 9 = trail of 9s
        assertEquals(TPKind.TRAIL.ordinal * 100_000L + 9, hs(TPVariant.LOWEST_JOKER, c(9, 0), c(9, 1), c(2, 2)))
        // a pair of lowest cards: both wild, trail of the third
        assertEquals(TPKind.TRAIL.ordinal * 100_000L + 11, hs(TPVariant.LOWEST_JOKER, c(3, 0), c(3, 1), c(11, 2)))
        // one wild with two suited cards makes at least a colour
        assertTrue(TeenPattiEngine.kindOf(hs(TPVariant.LOWEST_JOKER, c(2, 3), c(9, 1), c(13, 1))) >= TPKind.COLOR)
    }

    @Test
    fun nine99CountsTheDigits() {
        assertEquals(999, TeenPattiEngine.nine99(listOf(c(9, 0), c(9, 1), c(9, 2))))
        assertEquals(910, TeenPattiEngine.nine99(listOf(c(13, 0), c(9, 1), c(14, 2))))
        assertEquals(0, TeenPattiEngine.nine99(listOf(c(10, 0), c(11, 1), c(12, 2))))
        assertTrue(hs(TPVariant.NINE99, c(9, 0), c(8, 1), c(2, 2)) > hs(TPVariant.NINE99, c(14, 0), c(14, 1), c(14, 2)))
    }

    @Test
    fun fourXBootAndJokerDeals() {
        val s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4, variant = TPVariant.FOUR_X_BOOT), 5L).last().state
        assertEquals(TeenPattiEngine.BOOT * 4, s.boot)
        assertEquals(TeenPattiEngine.BOOT * 4, s.stake)
        assertEquals(4 * TeenPattiEngine.BOOT * 4, s.pot)
        assertTrue(s.chips.all { it == TeenPattiEngine.START_CHIPS - TeenPattiEngine.BOOT * 4 })
        val j = TeenPattiEngine.newGame(TeenPattiSetup(players = 5, variant = TPVariant.JOKER), 6L).last().state
        assertNotNull(j.joker)
        assertTrue(j.hands.flatten().none { it.id == j.joker!!.id })
        assertNull(TeenPattiEngine.newGame(TeenPattiSetup(players = 5), 6L).last().state.joker)
    }

    @Test
    fun fourXBootFallsBackWhenOnlyOneCanPay() {
        var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 3, variant = TPVariant.FOUR_X_BOOT), 1L).last().state
        while (!s.handOver) s = TeenPattiEngine.apply(s, TPAction.Pack(s.turn)).last().state
        s = s.copy(chips = listOf(5000, 50, 50))
        val next = TeenPattiEngine.nextHand(s).last().state
        assertEquals(TPVariant.CLASSIC, next.variant)
        assertEquals(TeenPattiEngine.BOOT, next.boot)
        assertTrue(next.inHand.all { it })
    }

    @Test
    fun mixedPlaysManyVariations() {
        var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4, hands = 40, variant = TPVariant.MIXED), 11L).last().state
        val seen = HashSet<TPVariant>()
        while (!s.isOver) {
            seen += s.variant
            while (!s.handOver) s = TeenPattiEngine.apply(s, TPAction.Pack(s.turn)).last().state
            if (!s.isOver) s = TeenPattiEngine.nextHand(s).last().state
        }
        assertTrue("variations seen: $seen", seen.size >= 5)
        assertTrue(TPVariant.MIXED !in seen)
    }

    @Test
    fun botsPlayEveryVariationLegally() {
        val rnd = Random(3)
        for (v in TPVariant.entries) for (d in Difficulty.entries) repeat(3) {
            var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 2 + rnd.nextInt(2, 5), hands = 8, variant = v), rnd.nextLong()).last().state
            var moves = 0
            while (!s.isOver) {
                if (s.phase is TPPhase.HandOver) { s = TeenPattiEngine.nextHand(s).last().state; continue }
                val a = TeenPattiBot.decide(s, s.turn, d, rnd)!!
                assertNull("$v illegal $a", TeenPattiEngine.validate(s, a))
                s = TeenPattiEngine.apply(s, a).last().state
                assertTrue(++moves < 5000)
            }
            assertEquals(s.players * TeenPattiEngine.START_CHIPS, s.chips.sum())
        }
    }

    @Test
    fun strengthIsJudgedInTheHandsOwnVariation() {
        val base = TeenPattiEngine.newGame(TeenPattiSetup(players = 3), 2L).last().state
        val hand = listOf(c(5, 0), c(3, 1), c(2, 2))
        val classic = base.copy(hands = base.hands.toMutableList().also { it[0] = hand })
        val muflis = classic.copy(variant = TPVariant.MUFLIS)
        assertTrue(TeenPattiEngine.strength(classic, 0) < 0.01)
        assertTrue(TeenPattiEngine.strength(muflis, 0) > 0.99)
    }

    @Test
    fun wildsAsShowsTheCardEachWildPlaysAs() {
        fun ak(vararg cs: TCard) = TeenPattiEngine.wildsAs(cs.toList(), TPVariant.AK47, true, null)
        // J + 4 (wild) + 2: best is a pair of Jacks, so the 4 plays as a Jack
        val a = ak(c(11, 2), c(4, 1), c(2, 0))
        assertEquals(1, a.size)
        assertEquals(11, a[c(4, 1).id]!!.rank)
        // two suited cards and a wild: the wild completes a pure sequence in that suit
        val b = ak(c(11, 1), c(10, 1), c(7, 0))
        assertEquals(12, b[c(7, 0).id]!!.rank)
        assertEquals(1, b[c(7, 0).id]!!.suit)
        // two wilds copy the natural card in other suits
        val t2 = ak(c(9, 3), c(13, 0), c(14, 1))
        assertEquals(setOf(9), t2.values.map { it.rank }.toSet())
        assertTrue(t2.values.none { it.suit == 3 })
        assertEquals(setOf(14), ak(c(14, 0), c(13, 1), c(7, 2)).values.map { it.rank }.toSet())
        // every substitute reproduces the best score
        val rnd = Random(9)
        val deck = TeenPattiEngine.deck()
        repeat(2000) {
            val hand = deck.shuffled(rnd).take(3)
            for (v in listOf(TPVariant.AK47, TPVariant.LOWEST_JOKER, TPVariant.JOKER)) {
                val joker = deck[rnd.nextInt(52)]
                val asMap = TeenPattiEngine.wildsAs(hand, v, true, joker)
                val played = hand.map { asMap[it.id] ?: it }
                assertEquals("$v $hand -> $played", TeenPattiEngine.handScore(hand, v, true, joker), TeenPattiEngine.score(played, true))
            }
        }
        assertTrue(TeenPattiEngine.wildsAs(listOf(c(4, 0), c(7, 1), c(9, 2)), TPVariant.CLASSIC, true, null).isEmpty())
    }
}
