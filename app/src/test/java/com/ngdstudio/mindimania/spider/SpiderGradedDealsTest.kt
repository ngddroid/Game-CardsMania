package com.ngdstudio.mindimania.spider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The graded 2- and 4-suit deals (SpiderDeals.kt, built by SpiderDealGenerator). */
class SpiderGradedDealsTest {

    @Test
    fun thereAreEnoughDistinctDeals() {
        assertTrue(SpiderDeals.SUITS_2.size >= 100)
        assertTrue(SpiderDeals.SUITS_4.size >= 100)
        assertEquals(SpiderDeals.SUITS_2.size, SpiderDeals.SUITS_2.toSet().size)
        assertEquals(SpiderDeals.SUITS_4.size, SpiderDeals.SUITS_4.toSet().size)
        assertTrue(SpiderRules.gradedDeals(1).isEmpty())
    }

    /** Guards the deal shuffle: if SpiderEngine.newGame ever changes, the stored seeds would no longer be checked deals. */
    @Test
    fun sampledDealsAreStillWinnable() {
        for ((suits, seeds) in listOf(2 to SpiderDeals.SUITS_2, 4 to SpiderDeals.SUITS_4)) {
            for (seed in listOf(seeds.first(), seeds[seeds.size / 2], seeds.last())) {
                val r = SpiderDealSolver.solve(SpiderEngine.newGame(suits, seed))
                assertTrue("$suits-suit deal $seed must still be winnable", r.won)
            }
        }
    }

    @Test
    fun gradedDealsAreNotWonByTheHintSystemAlone() {
        // the hint system playing on its own (what an easy deal gives in to) loses every sampled graded deal
        for (seed in SpiderDeals.SUITS_2.take(10)) assertFalse(SpiderSolver.greedyWins(SpiderEngine.newGame(2, seed)))
        for (seed in SpiderDeals.SUITS_4.take(10)) assertFalse(SpiderSolver.greedyWins(SpiderEngine.newGame(4, seed)))
    }

    @Test
    fun pickingSkipsRecentDeals() {
        val all = SpiderDeals.SUITS_2.toList()
        val recent = all.drop(1)
        assertEquals(all.first(), SpiderRules.pickGradedDeal(2, recent, Random(1)))
        // everything played lately: any graded deal is fine again
        assertNotNull(SpiderRules.pickGradedDeal(4, SpiderDeals.SUITS_4.toList(), Random(2)))
        assertNull(SpiderRules.pickGradedDeal(1, emptyList(), Random(3)))
        val picks = (0 until 50).map { SpiderRules.pickGradedDeal(4, emptyList(), Random(it))!! }
        assertTrue(picks.all { it in SpiderDeals.SUITS_4 })
        assertTrue("random, not always the same deal", picks.toSet().size > 10)
    }
}
