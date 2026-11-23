package com.ngdstudio.mindimania.spider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiderEngineTest {

    private fun c(id: Int, rank: Int, suit: Int = 0, up: Boolean = true) = SCard(id, rank, suit, up)

    private fun state(columns: List<List<SCard>>, stock: List<SCard> = emptyList(), suits: Int = 1): SpiderState {
        val cols = columns + List(10 - columns.size) { emptyList() }
        return SpiderState(suits, 1L, cols, stock)
    }

    @Test
    fun decksHave104CardsIn8Runs() {
        for (n in listOf(1, 2, 4)) {
            val d = SpiderEngine.deck(n)
            assertEquals(104, d.size)
            assertEquals(104, d.map { it.id }.toSet().size)
            assertEquals(n, d.map { it.suit }.toSet().size)
            // exactly 8 of each rank
            for (r in 1..13) assertEquals(8, d.count { it.rank == r })
            // each suit has a whole number of K..A runs
            d.groupBy { it.suit }.values.forEach { assertEquals(0, it.size % 13) }
        }
    }

    @Test
    fun newGameLayout() {
        val s = SpiderEngine.newGame(2, 42)
        assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), s.columns.map { it.size })
        assertEquals(50, s.stock.size)
        assertEquals(5, s.dealsLeft)
        s.columns.forEach { col ->
            assertTrue(col.last().up)
            assertTrue(col.dropLast(1).none { it.up })
        }
        assertEquals(500, s.score)
        // same seed = same deal (daily challenge)
        assertEquals(s, SpiderEngine.newGame(2, 42))
    }

    @Test
    fun movingRules() {
        // col0: 9♠ face up; col1: 8♥ ; col2: 10♠ 9♠ 8♠ (run); col3: down card, 7♥
        val s = state(listOf(
            listOf(c(1, 9, 0)),
            listOf(c(2, 8, 1)),
            listOf(c(3, 10, 0), c(4, 9, 0), c(5, 8, 0)),
            listOf(c(6, 12, 0, up = false), c(7, 7, 1)),
        ), suits = 2)
        assertTrue(SpiderEngine.canMove(s, SpiderMove(1, 0, 0)))   // 8♥ on 9♠: any suit
        assertFalse(SpiderEngine.canMove(s, SpiderMove(0, 0, 1)))  // 9 on 8: wrong rank
        assertEquals(0, SpiderEngine.runStart(s, 2))
        assertTrue(SpiderEngine.canMove(s, SpiderMove(2, 1, 4)))   // 9♠8♠ to empty column
        assertFalse(SpiderEngine.canPickUp(s, 3, 0))               // face-down card
        val r = SpiderEngine.move(s, SpiderMove(3, 1, 2))          // 7♥ onto 8♠ and flip the Q
        assertNotNull(r)
        r!!
        assertTrue(r.events.any { it is SpiderEvent.Flipped && it.cardId == 6 })
        assertTrue(r.state.columns[3].single().up)
        assertEquals(1, r.state.moves)
        assertEquals(499, r.state.score)
        // the mixed run 8♠7♥ can't be picked up from the 8
        assertEquals(3, SpiderEngine.runStart(r.state, 2))
        assertFalse(SpiderEngine.canPickUp(r.state, 2, 2))
        assertNull(SpiderEngine.move(r.state, SpiderMove(2, 2, 5)))
    }

    @Test
    fun completedRunIsCollected() {
        val run = (13 downTo 2).mapIndexed { i, rank -> c(100 + i, rank, 0) }
        val s = state(listOf(listOf(c(1, 5, 1, up = false)) + run, listOf(c(2, 1, 0))))
        val r = SpiderEngine.move(s, SpiderMove(1, 0, 0))!!
        val done = r.events.filterIsInstance<SpiderEvent.Completed>().single()
        assertEquals(0, done.column)
        assertEquals(13, done.cardIds.size)
        assertEquals(1, r.state.completed.size)
        assertEquals(1, r.state.columns[0].size)
        assertTrue(r.state.columns[0].single().up) // card under the run is turned over
        assertEquals(500 - 1 + 100, r.state.score)
    }

    @Test
    fun dealingAlsoFillsEmptyColumns() {
        val full = SpiderEngine.newGame(1, 7)
        val d = SpiderEngine.deal(full)!!
        assertEquals(40, d.state.stock.size)
        assertTrue(d.state.columns.all { it.last().up })
        assertEquals(10, (d.events.first() as SpiderEvent.Dealt).cardIds.size)

        // an empty column doesn't block the deal: it gets a face-up card like every other column
        val withEmpty = full.copy(columns = full.columns.mapIndexed { i, col -> if (i == 0) emptyList() else col })
        assertTrue(SpiderEngine.canDeal(withEmpty))
        val e = SpiderEngine.deal(withEmpty)!!.state
        assertEquals(1, e.columns[0].size)
        assertTrue(e.columns[0].single().up)
        assertEquals(40, e.stock.size)
        // with no useful move left, the hint is to deal
        assertEquals(SpiderHint.Deal, SpiderEngine.nextHint(SpiderState(1, 1L, List(10) { emptyList() }, full.stock)))
    }

    @Test
    fun hintsSkipPointlessMoves() {
        // whole column into an empty column is never suggested
        val s = state(listOf(listOf(c(1, 9, 0)), listOf(c(2, 3, 1))))
        assertTrue(SpiderEngine.hints(s).none { it.index == 0 && s.columns[it.to].isEmpty() })
        // same-suit destination is preferred
        val t = state(listOf(listOf(c(1, 9, 1)), listOf(c(2, 9, 0)), listOf(c(9, 4, 2, up = false), c(3, 8, 0))), suits = 4)
        assertEquals(SpiderMove(2, 1, 1), SpiderEngine.hints(t).first())
        assertEquals(1, SpiderEngine.bestDestination(t, 2, 1))
    }

    /** Plays many deals with the hint system and checks invariants (cards conserved, game always ends). */
    @Test
    fun autoplaySoak() {
        val wins = IntArray(5)
        for (suits in listOf(1, 2, 4)) {
            repeat(150) { g ->
                var s = SpiderEngine.newGame(suits, 1000L * suits + g)
                val seen = HashSet<List<List<SCard>>>()
                var lastFirst = -1
                var lastFrom = -1
                var steps = 0
                while (!s.won && steps < 3000) {
                    steps++
                    val hint = SpiderEngine.nextHint(s, lastFirst, lastFrom)
                    val next = when (hint) {
                        is SpiderHint.Move -> SpiderEngine.move(s, hint.move)?.also {
                            lastFirst = s.columns[hint.move.from][hint.move.index].id
                            lastFrom = hint.move.from
                        }
                        SpiderHint.Deal -> SpiderEngine.deal(s)
                        SpiderHint.None -> null
                    } ?: break
                    s = next.state
                    val total = s.columns.sumOf { it.size } + s.stock.size + s.completed.sumOf { it.size }
                    assertEquals(104, total)
                    assertEquals(104, (s.columns.flatten() + s.stock + s.completed.flatten()).map { it.id }.toSet().size)
                    if (!seen.add(s.columns)) {
                        // repeating position: deal if possible, otherwise this game is over
                        s = SpiderEngine.deal(s)?.state ?: break
                    }
                }
                if (s.won) wins[suits]++
            }
        }
        println("Greedy hint autoplay wins out of 150: 1 suit=${wins[1]}, 2 suits=${wins[2]}, 4 suits=${wins[4]}")
        assertTrue("greedy play should win some 1-suit games", wins[1] > 0)
    }

    @Test
    fun dailyDealsAreWinnableAndStable() {
        for (day in 1..20) {
            val date = String.format("2026-11-%02d", day)
            val seed = SpiderRules.dailySeed(date)
            assertEquals(seed, SpiderRules.dailySeed(date))
            assertTrue("daily $date must be winnable", SpiderSolver.greedyWins(SpiderEngine.newGame(1, seed)))
        }
    }
}
