package com.ngdstudio.mindimania.spider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One test per Spider Solitaire rule, in the same order as the rules list in README §8.1, plus whole games played
 * with every rule re-checked after each move.
 * Suits: 0 = ♠, 1 = ♥, 2 = ♦, 3 = ♣. Ranks: 1 = Ace … 13 = King.
 */
class SpiderRulesChecklistTest {

    private var nextId = 1000
    private fun up(rank: Int, suit: Int = 0) = SCard(nextId++, rank, suit, up = true)
    private fun down(rank: Int, suit: Int = 0) = SCard(nextId++, rank, suit, up = false)

    private fun table(vararg columns: List<SCard>, stock: List<SCard> = emptyList(), completed: Int = 0): SpiderState {
        val cols = columns.toList() + List(10 - columns.size) { listOf(down(1), up(2)) }
        val done = List(completed) { (13 downTo 1).map { r -> up(r) } }
        return SpiderState(1, 1L, cols, stock, done)
    }

    /** A full stock of 10 cards so dealing is possible. */
    private fun stock10() = List(10) { down(5, 2) }

    // 1 ─ Setup
    @Test
    fun rule1_setup_104Cards_10Columns_6and5_onlyBottomFaceUp_50InStock() {
        for (suits in listOf(1, 2, 4)) for (seed in 1L..30L) {
            val s = SpiderEngine.newGame(suits, seed)
            assertEquals(10, s.columns.size)
            assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), s.columns.map { it.size })
            assertEquals(54, s.columns.sumOf { it.size })
            assertEquals(50, s.stock.size)
            assertTrue("stock is face down", s.stock.none { it.up })
            val all = s.columns.flatten() + s.stock
            assertEquals(104, all.size)
            assertEquals("every card is a different card", 104, all.map { it.id }.toSet().size)
            s.columns.forEach { col ->
                assertTrue("bottom card face up", col.last().up)
                assertTrue("all other cards face down", col.dropLast(1).none { it.up })
            }
            assertTrue(s.completed.isEmpty())
            assertEquals(0, s.moves)
        }
    }

    // 2 ─ Moving one card
    @Test
    fun rule2_anyFaceUpCard_goesOnOneRankHigher_anySuit() {
        val seven = up(7, 1)                    // 7♥
        val s = table(listOf(up(8, 0)), listOf(seven), listOf(up(9, 2)), listOf(up(6, 3)), listOf(up(8, 1)))
        assertTrue("7♥ on 8♠ (other suit)", SpiderEngine.canMove(s, SpiderMove(1, 0, 0)))
        assertTrue("7♥ on 8♥ (same suit)", SpiderEngine.canMove(s, SpiderMove(1, 0, 4)))
        assertFalse("7 on 9: two ranks higher", SpiderEngine.canMove(s, SpiderMove(1, 0, 2)))
        assertFalse("7 on 6: lower", SpiderEngine.canMove(s, SpiderMove(1, 0, 3)))
        assertNull("illegal move is refused", SpiderEngine.move(s, SpiderMove(1, 0, 2)))

        val r = SpiderEngine.move(s, SpiderMove(1, 0, 0))!!.state
        assertEquals(seven.id, r.columns[0].last().id)
        assertTrue(r.columns[1].isEmpty())

        // a face-down card can never be picked up
        val hidden = table(listOf(down(7), up(3)), listOf(up(8)))
        assertFalse(SpiderEngine.canPickUp(hidden, 0, 0))
        assertFalse(SpiderEngine.canMove(hidden, SpiderMove(0, 0, 1)))
        // nothing goes on a King
        val king = table(listOf(up(13)), listOf(up(12, 1)))
        assertTrue(SpiderEngine.canMove(king, SpiderMove(1, 0, 0)))
        assertFalse(SpiderEngine.canMove(king, SpiderMove(0, 0, 1)))
    }

    // 3 ─ Moving a group
    @Test
    fun rule3_groupMovesOnlyWhenOneSuitInOrder() {
        // col0: 9♠ 8♠ 7♠  · col1: 9♠ 8♥ (mixed) · col2: 10♦ (target) · col3: 9♠ 7♠ (gap) · col4: 10♣
        val s = table(
            listOf(up(9, 0), up(8, 0), up(7, 0)),
            listOf(up(9, 0), up(8, 1)),
            listOf(up(10, 2)),
            listOf(up(9, 0), up(7, 0)),
            listOf(up(10, 3)),
        )
        assertTrue("9♠8♠7♠ can be picked up as a group", SpiderEngine.canPickUp(s, 0, 0))
        assertTrue("…and placed on a 10 of another suit", SpiderEngine.canMove(s, SpiderMove(0, 0, 2)))
        val moved = SpiderEngine.move(s, SpiderMove(0, 0, 2))!!.state
        assertEquals(listOf(10, 9, 8, 7), moved.columns[2].map { it.rank })
        assertTrue(moved.columns[0].isEmpty())
        assertTrue("part of a run can move too (8♠7♠)", SpiderEngine.canPickUp(s, 0, 1))

        assertFalse("mixed 9♠8♥ can't move as a group", SpiderEngine.canPickUp(s, 1, 0))
        assertFalse(SpiderEngine.canMove(s, SpiderMove(1, 0, 4)))
        assertTrue("…but the 8♥ alone can move", SpiderEngine.canPickUp(s, 1, 1))
        assertFalse("9♠7♠ (gap) is not a group", SpiderEngine.canPickUp(s, 3, 0))

        // a mixed pile can still be BUILT on the table: 8♥ onto 9♠
        val build = table(listOf(up(9, 0)), listOf(up(8, 1)))
        val built = SpiderEngine.move(build, SpiderMove(1, 0, 0))!!.state
        assertEquals(listOf(9, 8), built.columns[0].map { it.rank })
        assertFalse("and once built it can't be lifted together", SpiderEngine.canPickUp(built, 0, 0))
    }

    // 4 ─ Empty column
    @Test
    fun rule4_emptyColumnTakesAnyCardOrSameSuitGroup() {
        val s = table(
            emptyList(),
            listOf(down(1), up(5, 2)),                      // a single 5♦
            listOf(down(1), up(12, 1), up(11, 1), up(10, 1)), // Q♥ J♥ 10♥ group
            listOf(down(1), up(6, 0), up(5, 1)),            // mixed 6♠ 5♥
            listOf(down(1), up(13, 3)),                     // King
        )
        assertTrue("any single card (5♦)", SpiderEngine.canMove(s, SpiderMove(1, 1, 0)))
        assertTrue("a King", SpiderEngine.canMove(s, SpiderMove(4, 1, 0)))
        assertTrue("a same-suit group", SpiderEngine.canMove(s, SpiderMove(2, 1, 0)))
        assertFalse("not a mixed group", SpiderEngine.canMove(s, SpiderMove(3, 1, 0)))
        assertTrue("the last card of the mixed pile can go alone", SpiderEngine.canMove(s, SpiderMove(3, 2, 0)))
        val r = SpiderEngine.move(s, SpiderMove(2, 1, 0))!!.state
        assertEquals(listOf(12, 11, 10), r.columns[0].map { it.rank })
    }

    // 5 ─ Face-down cards turn over
    @Test
    fun rule5_uncoveredFaceDownCardTurnsFaceUp() {
        val hidden = down(4, 3)
        val s = table(listOf(down(9), hidden, up(7, 1)), listOf(up(8, 0)))
        val r = SpiderEngine.move(s, SpiderMove(0, 2, 1))!!
        val top = r.state.columns[0].last()
        assertEquals(hidden.id, top.id)
        assertTrue("uncovered card is now face up", top.up)
        assertFalse("the card under it stays face down", r.state.columns[0][0].up)
        assertTrue(r.events.any { it is SpiderEvent.Flipped && it.cardId == hidden.id && it.column == 0 })

        // also after a finished run leaves the column
        val under = down(3, 2)
        val run = (13 downTo 2).map { up(it, 0) }
        val s2 = table(listOf(under) + run, listOf(up(1, 0)))
        val r2 = SpiderEngine.move(s2, SpiderMove(1, 0, 0))!!
        assertEquals(listOf(under.id), r2.state.columns[0].map { it.id })
        assertTrue(r2.state.columns[0].single().up)
    }

    // 6 ─ Dealing
    @Test
    fun rule6_dealPutsOneFaceUpCardOnEachColumn_emptyColumnsIncluded() {
        val s = SpiderEngine.newGame(2, 7)
        val r = SpiderEngine.deal(s)!!
        val after = r.state
        for (c in 0 until 10) {
            assertEquals("one more card on column $c", s.columns[c].size + 1, after.columns[c].size)
            assertTrue("dealt card is face up", after.columns[c].last().up)
            assertEquals(s.columns[c], after.columns[c].dropLast(1))
        }
        assertEquals(40, after.stock.size)
        assertEquals(1, after.moves)
        assertTrue(r.events.any { it is SpiderEvent.Dealt && it.cardIds.size == 10 })

        // exactly 5 deals per game
        var g = s
        repeat(5) { g = SpiderEngine.deal(g)!!.state }
        assertTrue(g.stock.isEmpty())
        assertFalse(SpiderEngine.canDeal(g))
        assertNull(SpiderEngine.deal(g))

        // empty columns don't block the deal: each one gets a face-up card
        val withEmpty = table(emptyList(), listOf(up(9)), emptyList(), stock = stock10())
        assertTrue("can deal with empty columns", SpiderEngine.canDeal(withEmpty))
        val dealt = SpiderEngine.deal(withEmpty)!!.state
        assertEquals(1, dealt.columns[0].size)
        assertEquals(1, dealt.columns[2].size)
        assertTrue(dealt.columns[0].single().up && dealt.columns[2].single().up)
        assertEquals(2, dealt.columns[1].size)
        assertTrue("no column is empty after a deal", dealt.columns.none { it.isEmpty() })
        assertTrue(dealt.stock.isEmpty())
        // even a completely empty table gets a card in every column
        val bare = SpiderState(1, 1L, List(10) { emptyList() }, stock10())
        assertTrue(SpiderEngine.deal(bare)!!.state.columns.all { it.size == 1 && it[0].up })
    }

    // 7 ─ Completing a suit
    @Test
    fun rule7_kingToAceOfOneSuitLeavesTheTable() {
        // by a move: K♠…2♠ + A♠
        val ace = up(1, 0)
        val s = table((13 downTo 2).map { up(it, 0) }, listOf(down(9), ace))
        val r = SpiderEngine.move(s, SpiderMove(1, 1, 0))!!
        assertTrue("run removed from column", r.state.columns[0].isEmpty())
        assertEquals(1, r.state.completed.size)
        assertEquals((13 downTo 1).toList(), r.state.completed.single().map { it.rank })
        assertTrue(r.events.any { it is SpiderEvent.Completed && it.column == 0 })

        // by a deal: the dealt Ace finishes K♥…2♥
        val dealAce = SCard(nextId++, 1, 1, false)
        val stock = List(9) { down(5, 2) } + dealAce // the last card is dealt onto column 0
        val s2 = SpiderState(2, 1L, listOf((13 downTo 2).map { up(it, 1) }) + List(9) { listOf(up(3, 2)) }, stock)
        val r2 = SpiderEngine.deal(s2)!!
        assertTrue(r2.state.columns[0].isEmpty())
        assertEquals(1, r2.state.completed.size)

        // NOT removed: mixed suits, or a run that is not complete
        val mixed = table((13 downTo 2).map { up(it, if (it == 7) 1 else 0) }, listOf(up(1, 0)))
        val rm = SpiderEngine.move(mixed, SpiderMove(1, 0, 0))!!.state
        assertEquals(13, rm.columns[0].size)
        assertTrue(rm.completed.isEmpty())
        val partial = table((12 downTo 2).map { up(it, 0) }, listOf(up(1, 0)))
        assertTrue(SpiderEngine.move(partial, SpiderMove(1, 0, 0))!!.state.completed.isEmpty())
    }

    // 8 ─ Winning
    @Test
    fun rule8_eighthRunWinsAndClearsTheTable() {
        val s = SpiderState(
            1, 1L,
            listOf((13 downTo 2).map { up(it) }, listOf(up(1))) + List(8) { emptyList() },
            emptyList(),
            List(7) { (13 downTo 1).map { r -> up(r) } },
        )
        assertFalse(s.won)
        val r = SpiderEngine.move(s, SpiderMove(1, 0, 0))!!
        assertTrue(r.state.won)
        assertEquals(8, r.state.completed.size)
        assertTrue("table is clear", r.state.columns.all { it.isEmpty() } && r.state.stock.isEmpty())
        assertTrue(r.events.last() is SpiderEvent.Won)
        // 7 runs is not a win
        assertFalse(s.copy(completed = s.completed.take(7)).won)
    }

    // 9 ─ Difficulty
    @Test
    fun rule9_oneTwoOrFourSuits() {
        val easy = SpiderEngine.deck(1)
        assertEquals(setOf(0), easy.map { it.suit }.toSet())
        assertEquals(104, easy.count { it.suit == 0 })

        val medium = SpiderEngine.deck(2)
        assertEquals(setOf(0, 1), medium.map { it.suit }.toSet())
        assertEquals(52, medium.count { it.suit == 0 })
        assertEquals(52, medium.count { it.suit == 1 })

        val hard = SpiderEngine.deck(4)
        assertEquals(setOf(0, 1, 2, 3), hard.map { it.suit }.toSet())
        for (suit in 0..3) assertEquals(26, hard.count { it.suit == suit })

        for (d in listOf(easy, medium, hard)) for (rank in 1..13) assertEquals(8, d.count { it.rank == rank })
        for (n in listOf(1, 2, 4)) assertEquals(n, SpiderEngine.newGame(n, 3).suits)
        assertEquals("unknown value falls back to 1 suit", 1, SpiderEngine.newGame(3, 3).suits)
    }

    // 10 ─ Score
    @Test
    fun rule10_scoreStarts500_minus1PerMove_plus100PerRun() {
        val s = (1L..50L).map { SpiderEngine.newGame(1, it) }.first { SpiderEngine.hints(it).isNotEmpty() }
        assertEquals(500, s.score)
        val m = SpiderEngine.hints(s).first()
        val a = SpiderEngine.move(s, m)!!.state
        assertEquals(499, a.score)
        val b = SpiderEngine.deal(a)!!.state
        assertEquals("a deal is a move too", 498, b.score)

        val run = table((13 downTo 2).map { up(it) }, listOf(up(1))).copy(moves = 40)
        assertEquals(460, run.score)
        val done = SpiderEngine.move(run, SpiderMove(1, 0, 0))!!.state
        assertEquals("−1 for the move, +100 for the run", 559, done.score)
        assertEquals("score never goes below 0", 0, s.copy(moves = 900).score)
    }

    // All rules together, over whole games
    @Test
    fun wholeGames_everyRuleHoldsAfterEveryMove() {
        var wins = 0
        for (suits in listOf(1, 2, 4)) for (seed in 1L..40L) {
            var s = SpiderEngine.newGame(suits, if (suits == 1) SpiderSolver.winnableSeed(1, seed * 7919) else seed)
            var lastFirst = -1
            var lastFrom = -1
            val seen = HashSet<List<List<SCard>>>()
            var steps = 0
            while (!s.won && steps++ < 3000) {
                val before = s
                val next = when (val h = SpiderEngine.nextHint(s, lastFirst, lastFrom)) {
                    is SpiderHint.Move -> {
                        assertTrue(SpiderEngine.canMove(s, h.move))
                        lastFirst = s.columns[h.move.from][h.move.index].id
                        lastFrom = h.move.from
                        SpiderEngine.move(s, h.move)
                    }
                    SpiderHint.Deal -> SpiderEngine.deal(s)
                    SpiderHint.None -> null
                } ?: break
                s = next.state
                check(before, s)
                if (!seen.add(s.columns)) {
                    val looped = s
                    s = SpiderEngine.deal(s)?.state ?: break
                    check(looped, s)
                }
            }
            if (s.won) {
                wins++
                assertTrue(s.columns.all { it.isEmpty() } && s.stock.isEmpty())
            }
        }
        assertTrue("1-suit games from winnable seeds are won (won $wins)", wins >= 40)
    }

    private fun check(before: SpiderState, s: SpiderState) {
        val all = s.columns.flatten() + s.stock + s.completed.flatten()
        assertEquals("no card lost or added", 104, all.size)
        assertEquals(104, all.map { it.id }.toSet().size)
        s.columns.forEach { col ->
            if (col.isNotEmpty()) assertTrue("bottom card always face up", col.last().up)
            val firstUp = col.indexOfFirst { it.up }
            if (firstUp >= 0) assertTrue("face-down cards only above face-up ones", col.drop(firstUp).all { it.up })
        }
        assertTrue(s.stock.none { it.up })
        assertEquals(0, s.stock.size % 10)
        s.completed.forEach { run ->
            assertEquals((13 downTo 1).toList(), run.map { it.rank })
            assertEquals(1, run.map { it.suit }.toSet().size)
        }
        assertEquals(before.moves + 1, s.moves)
        assertEquals((500 - s.moves + 100 * s.completed.size).coerceAtLeast(0), s.score)
    }
}
