package com.ngdstudio.mindimania.spider

import com.ngdstudio.mindimania.engine.Rng
import kotlinx.serialization.Serializable

/**
 * Spider Solitaire rules (public-domain patience game), pure Kotlin.
 *
 * 104 cards (8 runs of 13), 10 columns. The first 4 columns get 6 cards, the other 6 get 5 (54 cards, top card face up);
 * the remaining 50 stay in the stock and are dealt 10 at a time, one onto each column (empty columns get one too).
 * Any face-up card may go onto a card one rank higher (any suit); several cards move together only when they form a
 * descending run of ONE suit. A complete K→A run of one suit is removed automatically. Remove all 8 runs to win.
 * Scoring: 500 − moves + 100 × completed runs (dealing and undo also count as moves).
 */

/** rank 1..13 (1 = Ace, 13 = King); suit 0 = spades, 1 = hearts, 2 = diamonds, 3 = clubs. */
@Serializable
data class SCard(val id: Int, val rank: Int, val suit: Int, val up: Boolean = false) {
    fun faceUp() = if (up) this else copy(up = true)
}

@Serializable
data class SpiderMove(val from: Int, val index: Int, val to: Int)

/** Immutable game state, so undo is a simple stack of previous states. */
@Serializable
data class SpiderState(
    val suits: Int,
    val seed: Long,
    val columns: List<List<SCard>>,
    val stock: List<SCard>,
    /** Completed K→A runs, in the order they were finished (each list is K first … A last). */
    val completed: List<List<SCard>> = emptyList(),
    val moves: Int = 0,
) {
    val score: Int get() = (500 - moves + 100 * completed.size).coerceAtLeast(0)
    val won: Boolean get() = completed.size == 8
    val dealsLeft: Int get() = stock.size / 10
    val faceDownCount: Int get() = columns.sumOf { c -> c.count { !it.up } }
}

sealed interface SpiderEvent {
    data class Moved(val cardIds: List<Int>, val from: Int, val to: Int) : SpiderEvent
    data class Flipped(val cardId: Int, val column: Int) : SpiderEvent
    data class Dealt(val cardIds: List<Int>) : SpiderEvent
    data class Completed(val column: Int, val suit: Int, val cardIds: List<Int>) : SpiderEvent
    data object Won : SpiderEvent
    data object Undone : SpiderEvent
}

data class SpiderResult(val state: SpiderState, val events: List<SpiderEvent>)

object SpiderEngine {
    const val COLUMNS = 10

    fun suitsFor(count: Int): List<Int> = when (count) {
        1 -> listOf(0)
        2 -> listOf(0, 1)
        else -> listOf(0, 1, 2, 3)
    }

    /** All 104 cards for [suits] (1, 2 or 4): 8 complete runs spread evenly over the suits. */
    fun deck(suits: Int): List<SCard> {
        val list = suitsFor(suits)
        val runsPerSuit = 8 / list.size
        var id = 0
        return buildList {
            for (s in list) repeat(runsPerSuit) { for (r in 1..13) add(SCard(id++, r, s)) }
        }
    }

    fun newGame(suits: Int, seed: Long): SpiderState {
        val n = if (suits in listOf(1, 2, 4)) suits else 1
        val cards = deck(n).toMutableList()
        Rng(seed).shuffle(cards)
        val cols = List(COLUMNS) { mutableListOf<SCard>() }
        var k = 0
        // deal round by round, like a real table: 54 cards
        for (round in 0 until 6) {
            for (c in 0 until COLUMNS) {
                if (round == 5 && c >= 4) continue
                cols[c] += cards[k++]
            }
        }
        val columns = cols.map { col -> col.mapIndexed { i, card -> if (i == col.lastIndex) card.faceUp() else card } }
        return SpiderState(n, seed, columns, cards.subList(k, cards.size).toList())
    }

    // ------------------------------------------------------------------ queries

    /** Index of the first card of the movable run at the bottom of column [c] (the cards you can pick up). */
    fun runStart(s: SpiderState, c: Int): Int {
        val col = s.columns[c]
        if (col.isEmpty()) return -1
        var i = col.lastIndex
        while (i > 0) {
            val above = col[i - 1]
            val cur = col[i]
            if (above.up && above.suit == cur.suit && above.rank == cur.rank + 1) i-- else break
        }
        return i
    }

    fun canPickUp(s: SpiderState, c: Int, index: Int): Boolean {
        val start = runStart(s, c)
        return start >= 0 && index >= start && index <= s.columns[c].lastIndex
    }

    fun canMove(s: SpiderState, m: SpiderMove): Boolean {
        if (m.from == m.to || m.from !in 0 until COLUMNS || m.to !in 0 until COLUMNS) return false
        if (!canPickUp(s, m.from, m.index)) return false
        val moving = s.columns[m.from][m.index]
        val dest = s.columns[m.to]
        if (dest.isEmpty()) return true
        val top = dest.last()
        return top.up && top.rank == moving.rank + 1
    }

    /** You can deal whenever the stock has cards. Empty columns are allowed: the deal puts a card in them too. */
    fun canDeal(s: SpiderState): Boolean = s.stock.isNotEmpty()

    // ------------------------------------------------------------------ actions

    fun move(s: SpiderState, m: SpiderMove): SpiderResult? {
        if (!canMove(s, m)) return null
        val events = ArrayList<SpiderEvent>()
        val cols = s.columns.map { it.toMutableList() }
        val moving = cols[m.from].subList(m.index, cols[m.from].size).toList()
        repeat(moving.size) { cols[m.from].removeAt(cols[m.from].lastIndex) }
        cols[m.to].addAll(moving)
        events += SpiderEvent.Moved(moving.map { it.id }, m.from, m.to)
        flipTop(cols, m.from, events)
        var state = s.copy(columns = cols.map { it.toList() }, moves = s.moves + 1)
        state = collect(state, m.to, events)
        if (state.won) events += SpiderEvent.Won
        return SpiderResult(state, events)
    }

    fun deal(s: SpiderState): SpiderResult? {
        if (!canDeal(s)) return null
        val events = ArrayList<SpiderEvent>()
        val cols = s.columns.map { it.toMutableList() }
        val stock = s.stock.toMutableList()
        val dealt = ArrayList<Int>()
        for (c in 0 until COLUMNS) {
            val card = stock.removeAt(stock.lastIndex).faceUp()
            cols[c] += card
            dealt += card.id
        }
        events += SpiderEvent.Dealt(dealt)
        var state = s.copy(columns = cols.map { it.toList() }, stock = stock.toList(), moves = s.moves + 1)
        for (c in 0 until COLUMNS) state = collect(state, c, events)
        if (state.won) events += SpiderEvent.Won
        return SpiderResult(state, events)
    }

    private fun flipTop(cols: List<MutableList<SCard>>, c: Int, events: MutableList<SpiderEvent>) {
        val col = cols[c]
        if (col.isNotEmpty() && !col.last().up) {
            col[col.lastIndex] = col.last().faceUp()
            events += SpiderEvent.Flipped(col.last().id, c)
        }
    }

    /** Removes a finished K→A run of one suit from the bottom of column [c], if there is one. */
    private fun collect(s: SpiderState, c: Int, events: MutableList<SpiderEvent>): SpiderState {
        val col = s.columns[c]
        if (col.size < 13) return s
        val tail = col.subList(col.size - 13, col.size)
        val suit = tail[0].suit
        for (i in 0 until 13) {
            val card = tail[i]
            if (!card.up || card.suit != suit || card.rank != 13 - i) return s
        }
        val cols = s.columns.map { it.toMutableList() }
        val run = cols[c].subList(col.size - 13, col.size).toList()
        repeat(13) { cols[c].removeAt(cols[c].lastIndex) }
        events += SpiderEvent.Completed(c, suit, run.map { it.id })
        flipTop(cols, c, events)
        return s.copy(columns = cols.map { it.toList() }, completed = s.completed + listOf(run))
    }

    // ------------------------------------------------------------------ hints & smart moves

    /**
     * Every useful legal move, best first. Pointless moves (e.g. moving a whole column into an empty one) are left out,
     * and so is moving the cards of the previous move ([lastFirstId] from column [lastFrom]) straight back.
     */
    fun hints(s: SpiderState, lastFirstId: Int = -1, lastFrom: Int = -1): List<SpiderMove> {
        val scored = ArrayList<Pair<SpiderMove, Int>>()
        for (from in 0 until COLUMNS) {
            val col = s.columns[from]
            val start = runStart(s, from)
            if (start < 0) continue
            for (idx in start..col.lastIndex) {
                for (to in 0 until COLUMNS) {
                    val m = SpiderMove(from, idx, to)
                    if (!canMove(s, m)) continue
                    val v = value(s, m)
                    if (v <= 0) continue
                    if (col[idx].id == lastFirstId && to == lastFrom) continue
                    scored += m to v
                }
            }
        }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    /** Heuristic value of a move; ≤ 0 means "not worth suggesting". */
    fun value(s: SpiderState, m: SpiderMove): Int {
        val src = s.columns[m.from]
        val card = src[m.index]
        val dest = s.columns[m.to]
        val below = if (m.index > 0) src[m.index - 1] else null
        val revealsFaceDown = below != null && !below.up
        val emptiesColumn = m.index == 0
        val sitsNaturally = below != null && below.up && below.rank == card.rank + 1
        val sitsInSuit = sitsNaturally && below!!.suit == card.suit
        var v = 0
        if (dest.isEmpty()) {
            if (emptiesColumn) return 0 // whole column to an empty column changes nothing
            if (sitsNaturally) return 0 // already resting on a valid card
            v += if (revealsFaceDown) 25 else 2
            return v
        }
        val top = dest.last()
        val sameSuit = top.suit == card.suit
        if (sitsInSuit) return 0 // breaking a same-suit run gains nothing
        if (sitsNaturally && !sameSuit) return 0 // swapping one off-suit parent for another
        v += 10
        if (sameSuit) v += 30 + runLengthAbove(dest) * 3
        if (revealsFaceDown) v += 40
        if (emptiesColumn) v += 35
        if (sitsNaturally && sameSuit) v += 15
        v += (src.size - m.index) // longer runs first
        return v
    }

    private fun runLengthAbove(col: List<SCard>): Int {
        if (col.isEmpty()) return 0
        var n = 1
        var i = col.lastIndex
        while (i > 0 && col[i - 1].up && col[i - 1].suit == col[i].suit && col[i - 1].rank == col[i].rank + 1) { n++; i-- }
        return n
    }

    /** Where a tapped card should go: same-suit home first, then any valid card, then an empty column. */
    /**
     * Every column the run starting at [index] can legally go to, best first: same-suit homes (longest run first),
     * then any valid card, then empty columns. Moving a whole column into an empty one is left out.
     */
    fun destinations(s: SpiderState, from: Int, index: Int): List<Int> {
        if (!canPickUp(s, from, index)) return emptyList()
        val card = s.columns[from][index]
        val scored = ArrayList<Pair<Int, Int>>()
        for (to in 0 until COLUMNS) {
            if (!canMove(s, SpiderMove(from, index, to))) continue
            val dest = s.columns[to]
            if (dest.isEmpty() && index == 0) continue // pointless
            var v = if (dest.isEmpty()) -50 else if (dest.last().suit == card.suit) 100 + runLengthAbove(dest) else 50
            v = v * 100 - kotlin.math.abs(to - from) // prefer nearby columns when tied
            scored += to to v
        }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    /** Where a tapped card should go: same-suit home first, then any valid card, then an empty column. */
    fun bestDestination(s: SpiderState, from: Int, index: Int): Int? = destinations(s, from, index).firstOrNull()

    fun rankName(r: Int): String = when (r) {
        1 -> "Ace"
        11 -> "Jack"
        12 -> "Queen"
        13 -> "King"
        else -> r.toString()
    }

    private fun article(r: Int) = if (r == 1 || r == 8) "an" else "a"

    /** A short, friendly reason why [m] is not allowed (null if it is allowed). */
    fun moveError(s: SpiderState, m: SpiderMove): String? {
        if (canMove(s, m)) return null
        val src = s.columns.getOrNull(m.from) ?: return "That move isn't allowed"
        val card = src.getOrNull(m.index) ?: return "That move isn't allowed"
        if (!card.up) return "Face-down cards can't move"
        if (!canPickUp(s, m.from, m.index)) return "Only same-suit runs move together"
        val dest = s.columns.getOrNull(m.to) ?: return "That move isn't allowed"
        if (m.to == m.from) return null
        if (dest.isEmpty()) return null
        if (card.rank == 13) return "A King can only go to an empty column"
        val need = card.rank + 1
        return "${article(card.rank).replaceFirstChar { it.uppercase() }} ${rankName(card.rank)} goes on ${article(need)} ${rankName(need)}"
    }

    /** Reason a picked-up card can't move at all, or null if it can be picked up. */
    fun pickError(s: SpiderState, col: Int, index: Int): String? {
        val card = s.columns.getOrNull(col)?.getOrNull(index) ?: return null
        if (!card.up) return "Face-down cards can't move"
        if (!canPickUp(s, col, index)) return "Only same-suit runs move together"
        return null
    }

    /** True when the stock is empty and every card on the table is face up (auto-finish may be possible). */
    fun allOpen(s: SpiderState): Boolean = s.stock.isEmpty() && s.columns.all { c -> c.all { it.up } }

    fun nextHint(s: SpiderState, lastFirstId: Int = -1, lastFrom: Int = -1, skip: Int = 0): SpiderHint {
        if (s.won) return SpiderHint.None
        val moves = hints(s, lastFirstId, lastFrom)
        if (moves.isNotEmpty()) return SpiderHint.Move(moves[skip % moves.size])
        if (canDeal(s)) return SpiderHint.Deal
        return SpiderHint.None
    }

    /** True when there is no useful move and nothing left to deal. */
    fun stuck(s: SpiderState): Boolean = !s.won && hints(s).isEmpty() && !canDeal(s)
}

sealed interface SpiderHint {
    data class Move(val move: SpiderMove) : SpiderHint
    data object Deal : SpiderHint
    data object None : SpiderHint
}

/** Finds deals the hint system can win on its own, so "winnable" deals and Daily Challenges are guaranteed solvable. */
object SpiderSolver {
    /** Plays [start] with the hint system (no undo). True if it wins. */
    fun greedyWins(start: SpiderState, maxSteps: Int = 2500): Boolean {
        var s = start
        val seen = HashSet<List<List<SCard>>>()
        var lastFirst = -1
        var lastFrom = -1
        var steps = 0
        while (!s.won && steps++ < maxSteps) {
            val next = when (val h = SpiderEngine.nextHint(s, lastFirst, lastFrom)) {
                is SpiderHint.Move -> SpiderEngine.move(s, h.move)?.also {
                    lastFirst = s.columns[h.move.from][h.move.index].id
                    lastFrom = h.move.from
                }
                SpiderHint.Deal -> SpiderEngine.deal(s)
                SpiderHint.None -> null
            } ?: return false
            s = next.state
            if (!seen.add(s.columns)) s = SpiderEngine.deal(s)?.state ?: return false
        }
        return s.won
    }

    /**
     * When every card is face up and the stock is empty, finds a sequence of moves that wins (depth-first search
     * over useful moves, then any legal move; repeated positions are skipped). Null if none found within [maxNodes].
     */
    fun finishMoves(start: SpiderState, maxNodes: Int = 40_000): List<SpiderMove>? {
        if (!SpiderEngine.allOpen(start)) return null
        if (start.won) return emptyList()
        val seen = HashSet<List<List<Int>>>()
        var nodes = 0
        val path = ArrayList<SpiderMove>()
        fun key(s: SpiderState) = s.columns.map { c -> c.map { it.id } }.sortedBy { it.firstOrNull() ?: -1 }
        fun dfs(s: SpiderState): Boolean {
            if (s.won) return true
            if (++nodes > maxNodes) return false
            if (!seen.add(key(s))) return false
            val useful = SpiderEngine.hints(s)
            val others = ArrayList<SpiderMove>()
            for (from in 0 until SpiderEngine.COLUMNS) {
                val st = SpiderEngine.runStart(s, from)
                if (st < 0) continue
                for (idx in st..s.columns[from].lastIndex) for (to in 0 until SpiderEngine.COLUMNS) {
                    val m = SpiderMove(from, idx, to)
                    if (m !in useful && SpiderEngine.canMove(s, m) && !(idx == 0 && s.columns[to].isEmpty())) others += m
                }
            }
            for (m in useful + others) {
                val r = SpiderEngine.move(s, m) ?: continue
                path += m
                if (dfs(r.state)) return true
                path.removeAt(path.lastIndex)
                if (nodes > maxNodes) return false
            }
            return false
        }
        return if (dfs(start)) path.toList() else null
    }

    /** First seed at or after [base] whose 1-suit deal is winnable (tries up to [tries] seeds). */
    fun winnableSeed(suits: Int, base: Long, tries: Int = 60): Long {
        var seed = base
        repeat(tries) {
            if (greedyWins(SpiderEngine.newGame(suits, seed))) return seed
            seed = seed * 6364136223846793005L + 1442695040888963407L
        }
        return base
    }
}
