package com.ngdstudio.mindimania.ui.rummy

import com.ngdstudio.mindimania.rummy.RCard
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a card I just picked goes in my hand when Grouping is off. Suits: 0 ♠, 1 ♥, 2 ♦, 3 ♣. */
class NewCardPlacementTest {
    private var nextId = 1
    private fun c(rank: Int, suit: Int) = RCard(nextId++, rank, suit)
    private fun ids(vararg cs: RCard) = cs.map { it.id }

    @Test
    fun joinsTheNearestCardsOfItsSuitWithoutBreakingAFinishedSequence() {
        val s3 = c(3, 0); val s4 = c(4, 0); val s5 = c(5, 0)
        val hj = c(11, 1); val hq = c(12, 1); val hk = c(13, 1)
        val h3 = c(3, 1); val h7 = c(7, 1)
        val d5 = c(5, 2); val d10 = c(10, 2)
        val groups = listOf(ids(s3, s4, s5), ids(hj, hq, hk), ids(h3, h7), ids(d5, d10))
        val h8 = c(8, 1)
        val hand = listOf(s3, s4, s5, hj, hq, hk, h3, h7, d5, d10, h8)
        val out = withNewCards(groups, hand, wild = 2)
        assertEquals(ids(hj, hq, hk), out[1]) // the pure J-Q-K stays whole
        assertEquals(ids(h3, h7, h8), out[2]) // 8♥ goes beside 7♥, in rank order
    }

    @Test
    fun completesASequenceOrASetWhenItCan() {
        val c5 = c(5, 3); val c6 = c(6, 3)
        val h9 = c(9, 1); val s9 = c(9, 0)
        val groups = listOf(ids(c5, c6), ids(h9, s9))
        val c4 = c(4, 3)
        val out = withNewCards(groups, listOf(c5, c6, h9, s9, c4), wild = 13)
        assertEquals(ids(c4, c5, c6), out[0])
        val d9 = c(9, 2)
        val out2 = withNewCards(out, listOf(c4, c5, c6, h9, s9, d9), wild = 13)
        assertEquals(ids(h9, s9, d9), out2[1])
    }

    @Test
    fun aCardWithNoSuitGroupOrAPrintedJokerWithNoUseStandsAlone() {
        val s3 = c(3, 0); val s4 = c(4, 0); val s5 = c(5, 0)
        val h2 = c(2, 1)
        val groups = listOf(ids(s3, s4, s5), ids(h2))
        val d9 = c(9, 2)
        val out = withNewCards(groups, listOf(s3, s4, s5, h2, d9), wild = 13)
        assertEquals(listOf(ids(s3, s4, s5), ids(h2), ids(d9)), out)
        val joker = c(0, -1)
        val out2 = withNewCards(out, listOf(s3, s4, s5, h2, d9, joker), wild = 13)
        // a joker extends the run (still valid) rather than sitting alone
        assertEquals(ids(s3, s4, s5, joker), out2[0])
    }

    @Test
    fun cardsIHaveThrownLeaveTheirGroups() {
        val s3 = c(3, 0); val s4 = c(4, 0); val s5 = c(5, 0)
        val out = withNewCards(listOf(ids(s3, s4, s5)), listOf(s3, s5), wild = 13)
        assertEquals(listOf(ids(s3, s5)), out)
    }
}
