package com.ngdstudio.mindimania.spider

import java.util.PriorityQueue

/**
 * Full-knowledge Spider solver, used only to grade deals (it sees face-down cards and the stock order, which a player
 * never does).
 *
 * Staged search: from each position it looks for move sequences that make real progress (a face-down card turned up,
 * a column emptied, a run finished), tries the best few results depth first, and deals onto its best arrangements when
 * nothing else works. Two styles are tried in turn: breadth-first stages (strong on 2 suits) and best-first stages
 * (go deeper; better on 4 suits). [Result.effort] (positions looked at before the win) is the deal's difficulty: an
 * easy deal is won almost at once, a hard one only after many dead ends.
 */
object SpiderDealSolver {
    data class Result(val won: Boolean, val effort: Int)

    fun solve(start: SpiderState): Result {
        val a = stagedSearch(start, deep = false, maxNodes = 200_000, stageNodes = 1500, branch = 4)
        if (a.won) return a
        val b = stagedSearch(start, deep = true, maxNodes = 300_000, stageNodes = 400, branch = 4)
        return Result(b.won, a.effort + b.effort)
    }

    /** One search; [deep] = best-first stages instead of breadth-first. [maxNodes] caps the positions looked at. */
    fun stagedSearch(start: SpiderState, deep: Boolean, maxNodes: Int, stageNodes: Int, branch: Int): Result {
        var nodes = 0
        val tried = HashSet<Long>()
        fun stage(s: SpiderState): Boolean {
            if (s.won) return true
            if (nodes >= maxNodes || !tried.add(key(s))) return false
            val base = progress(s)
            val local = HashSet<Long>()
            local += key(s)
            val bfs = ArrayDeque<SpiderState>()
            val best = PriorityQueue<Pair<Int, SpiderState>>(compareByDescending { it.first })
            if (deep) best += score(s) to s else bfs += s
            val better = ArrayList<SpiderState>()
            val arranged = ArrayList<SpiderState>()
            var localN = 0
            while (localN < stageNodes) {
                val cur = (if (deep) best.poll()?.second else bfs.removeFirstOrNull()) ?: break
                localN++
                nodes++
                arranged += cur
                for (m in candidates(cur)) {
                    if (m == null) continue
                    val next = SpiderEngine.move(cur, m)?.state ?: continue
                    if (next.won) return true
                    if (!local.add(key(next))) continue
                    when {
                        progress(next) > base -> better += next
                        deep -> best += score(next) to next
                        else -> bfs += next
                    }
                }
            }
            // keep only what will be tried before going deeper (the search can nest dozens of stages)
            val next = better.sortedByDescending { score(it) }.filter { key(it) !in tried }.take(branch)
            val toDeal = if (SpiderEngine.canDeal(s)) arranged.sortedByDescending { score(it) }.take(2) else emptyList()
            better.clear()
            arranged.clear()
            bfs.clear()
            best.clear()
            for (b in next) {
                if (stage(b)) return true
                if (nodes >= maxNodes) return false
            }
            for (a in toDeal) {
                val d = SpiderEngine.deal(a)?.state ?: continue
                if (stage(d)) return true
                if (nodes >= maxNodes) return false
            }
            return false
        }
        val won = stage(start)
        return Result(won, nodes)
    }

    /** Real progress: finished runs, then cards turned face up, then empty columns. */
    private fun progress(s: SpiderState): Int =
        s.completed.size * 100_000 - s.faceDownCount * 1000 + s.columns.count { it.isEmpty() }

    /** How promising a position is (higher = better). */
    fun score(s: SpiderState): Int {
        var v = s.completed.size * 1500 - s.stock.size * 6
        for (col in s.columns) {
            if (col.isEmpty()) {
                v += 90
                continue
            }
            for (i in col.indices) {
                val c = col[i]
                if (!c.up) {
                    v -= 60
                    continue
                }
                if (i == 0) continue
                val above = col[i - 1]
                if (above.up && above.rank == c.rank + 1) v += if (above.suit == c.suit) 22 else 6
            }
        }
        return v
    }

    /** Legal moves worth trying, then the deal (null). Empty columns are all alike: only the first is tried. */
    fun candidates(s: SpiderState): List<SpiderMove?> {
        val out = ArrayList<SpiderMove?>(32)
        val firstEmpty = s.columns.indexOfFirst { it.isEmpty() }
        for (from in 0 until SpiderEngine.COLUMNS) {
            val col = s.columns[from]
            val start = SpiderEngine.runStart(s, from)
            if (start < 0) continue
            for (idx in start..col.lastIndex) {
                val card = col[idx]
                val sitsInSuit = idx > start // inside a same-suit run
                for (to in 0 until SpiderEngine.COLUMNS) {
                    if (to == from) continue
                    val dest = s.columns[to]
                    if (dest.isEmpty()) {
                        if (to == firstEmpty && idx > 0) out += SpiderMove(from, idx, to)
                        continue
                    }
                    val top = dest.last()
                    if (!top.up || top.rank != card.rank + 1) continue
                    if (sitsInSuit && top.suit != card.suit) continue // breaking a run to put it on another suit
                    out += SpiderMove(from, idx, to)
                }
            }
        }
        if (SpiderEngine.canDeal(s)) out += null
        return out
    }

    /** Position key: the columns in any order (they are interchangeable) plus the stock size. */
    private fun key(s: SpiderState): Long {
        val hs = LongArray(SpiderEngine.COLUMNS)
        for (c in 0 until SpiderEngine.COLUMNS) {
            var h = 1469598103934665603L
            for (card in s.columns[c]) h = (h xor (card.id * 2L + if (card.up) 1 else 0)) * 1099511628211L
            hs[c] = h
        }
        hs.sort()
        var k = s.stock.size.toLong() * -0x61c8864680b583ebL
        for (h in hs) k = (k xor h) * -0x3b314601e57a13adL + 0x632BE59BD9B4E019L
        return k
    }
}
