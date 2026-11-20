package com.puzzel.lab.cardsmania.spider

import com.puzzel.lab.cardsmania.ui.spider.SpiderLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry (spacing + forgiving hit-testing), move reasons and auto-finish. Phone: 720x1600, density 2, insets 80/96. */
class SpiderLayoutTest {

    private fun layout(left: Boolean = false) = SpiderLayout(720f, 1600f, 2f, 80f, 96f, left)
    private var nextId = 5000
    private fun c(rank: Int, suit: Int = 0, up: Boolean = true) = SCard(nextId++, rank, suit, up)
    private fun state(columns: List<List<SCard>>, suits: Int = 1, completed: Int = 0, stock: List<SCard> = emptyList()): SpiderState {
        val cols = columns + List(10 - columns.size) { emptyList() }
        val done = List(completed) { r -> (13 downTo 1).map { rank -> SCard(9000 + r * 13 + rank, rank, 0, true) } }
        return SpiderState(suits, 1L, cols, stock, done)
    }

    @Test
    fun cardsAreWiderAndFaceUpGapGrowsWhenThereIsRoom() {
        val l = layout()
        // 10 columns fill the width inside the 10dp side margins: (720 - 2*20 - 9*6) / 10 = 62.6 px
        assertEquals(62.6f, l.cardW, 0.01f)
        assertTrue("cards stay a usable size", l.cardW > 60f)
        val s = SpiderEngine.newGame(1, 3)
        // a fresh deal fits easily: the face-up gap uses the maximum
        assertEquals(l.maxUpGap, l.upGap(s, 0), 0.01f)
        assertEquals(l.tableauTop, l.cardTop(s, 0, 0), 0.01f)
        // the visible strip of a face-up card is ~0.48 of a card (about 21-24 dp)
        assertTrue(l.maxUpGap / 2f >= 21f)
    }

    @Test
    fun longColumnsCompressToFitTheScreen() {
        val l = layout()
        val long = List(5) { c(13, up = false) } + (13 downTo 1).map { c(it) } + (13 downTo 1).map { c(it) }
        val s = state(listOf(long))
        val lastBottom = l.cardTop(s, 0, long.lastIndex) + l.cardH
        assertTrue("last card ($lastBottom) must stay above ${l.tableauBottom}", lastBottom <= l.tableauBottom + 0.5f)
        assertTrue(l.upGap(s, 0) < l.maxUpGap)
    }

    @Test
    fun everyXMapsToAColumnIncludingTheGaps() {
        val l = layout()
        var x = 0f
        while (x <= l.width) {
            assertTrue(l.columnAt(x) in 0..9)
            x += 1f
        }
        for (col in 0..9) assertEquals(col, l.columnAt(l.columnX(col)))
        assertEquals(-1, l.columnAt(-5f))
    }

    @Test
    fun forgivingCardPicking() {
        val l = layout()
        // col0: two face-down, then 9♥ 8♠ 7♠ (movable run starts at the 8♠)
        val col = listOf(c(5, 0, false), c(6, 0, false), c(9, 1), c(8, 0), c(7, 0))
        val s = state(listOf(col), suits = 2)
        assertEquals(3, SpiderEngine.runStart(s, 0))
        // empty space below the column = its last card
        assertEquals(4, l.cardAt(s, 0, l.cardTop(s, 0, 4) + l.cardH + 200f))
        assertEquals(-1, l.cardAt(s, 0, l.tableauTop - 100f))
        // tapping the 9♥ (above the movable run) snaps to the run start
        assertEquals(3, l.pickIndex(s, 0, l.cardTop(s, 0, 2) + 4f))
        // a touch just above the first face-up card (on the last face-down sliver) counts as that card → run start
        assertEquals(3, l.pickIndex(s, 0, l.cardTop(s, 0, 2) - 6f))
        // a touch high up on a deep face-down stack stays face-down (so the "can't move" reason is shown)
        val deep = state(listOf(List(5) { c(5, 0, false) } + c(9, 0)))
        assertEquals(0, l.pickIndex(deep, 0, l.tableauTop + 2f))
        // the empty column has nothing to pick
        assertEquals(-1, l.pickIndex(s, 5, l.tableauTop + 10f))
    }

    @Test
    fun leftHandedMirrorsStockAndCompletedRuns() {
        val r = layout(false)
        val l = layout(true)
        assertTrue(r.stockTap.x > r.width / 2 && l.stockTap.x < l.width / 2)
        assertTrue(r.foundation(0).x < r.width / 2 && l.foundation(0).x > l.width / 2)
        assertTrue(l.isStockHit(l.stockTap.x, l.stockTap.y))
        assertTrue(!l.isStockHit(r.stockTap.x, r.stockTap.y))
        assertEquals(r.columnX(0), l.columnX(0), 0.01f)
    }

    @Test
    fun friendlyMoveReasons() {
        val s = state(listOf(listOf(c(7, 0)), listOf(c(10, 1)), listOf(c(4, 0, false), c(9, 1), c(8, 0)), listOf(c(13, 0)), listOf(c(2, 0))), suits = 2)
        assertEquals("A 7 goes on an 8", SpiderEngine.moveError(s, SpiderMove(0, 0, 1)))
        assertEquals("Only same-suit runs move together", SpiderEngine.moveError(s, SpiderMove(2, 1, 0)))
        assertEquals("Face-down cards can't move", SpiderEngine.moveError(s, SpiderMove(2, 0, 1)))
        assertEquals("A King can only go to an empty column", SpiderEngine.moveError(s, SpiderMove(3, 0, 1)))
        assertNull(SpiderEngine.moveError(s, SpiderMove(0, 0, 9)))
    }

    @Test
    fun smartTapDestinationsAreOrdered() {
        // 8♠ can go on 9♠ (same suit, preferred), on 9♥, or into an empty column (last)
        val s = state(listOf(listOf(c(9, 1)), listOf(c(9, 0)), listOf(c(3, 1, false), c(8, 0))), suits = 2)
        assertEquals(listOf(1, 0) + (3..9).toList(), SpiderEngine.destinations(s, 2, 1))
        // moving a whole column into an empty column is never offered
        val t = state(listOf(listOf(c(9, 1))))
        assertTrue(SpiderEngine.destinations(t, 0, 0).isEmpty())
    }

    @Test
    fun autoFinishFindsAWinningLine() {
        // 6 runs done. Left: K♠..8♠ + 3♥ blocking, 7♠..A♠, K♥..4♥, 2♥ A♥ — everything face up, no stock.
        val spadesHigh = (13 downTo 8).map { c(it, 0) } + c(3, 1)
        val spadesLow = (7 downTo 1).map { c(it, 0) }
        val heartsHigh = (13 downTo 4).map { c(it, 1) }
        val heartsLow = listOf(c(2, 1), c(1, 1))
        val s = state(listOf(spadesHigh, spadesLow, emptyList(), heartsHigh, heartsLow), suits = 2, completed = 6)
        assertTrue(SpiderEngine.allOpen(s))
        val moves = SpiderSolver.finishMoves(s)
        assertNotNull(moves)
        var cur = s
        for (m in moves!!) cur = SpiderEngine.move(cur, m)!!.state
        assertTrue(cur.won)
        // not offered while cards are still face down or the stock has cards
        assertNull(SpiderSolver.finishMoves(SpiderEngine.newGame(1, 9)))
    }
}
