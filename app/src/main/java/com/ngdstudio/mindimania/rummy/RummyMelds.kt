package com.ngdstudio.mindimania.rummy

import kotlinx.serialization.Serializable

/**
 * A Rummy card. rank 1..13 (1 = Ace, 11 J, 12 Q, 13 K), suit 0 spades, 1 hearts, 2 diamonds, 3 clubs.
 * A printed joker has rank 0 and suit -1. Two decks are used, so the same rank and suit can appear twice (different ids).
 */
@Serializable
data class RCard(val id: Int, val rank: Int, val suit: Int) {
    val isPrinted: Boolean get() = rank == 0
}

/** What a group of cards is. */
enum class GroupKind { PURE, IMPURE, SET, JOKERS, INVALID }

/**
 * Meld rules and the best way to arrange a hand. Pure Kotlin.
 *
 * Jokers: printed jokers, and every card of the wild rank (the "cut joker"). A joker can stand for any card.
 *  - Pure sequence: 3+ cards of one suit in a row, no joker standing in (a wild-rank card in its own place is fine).
 *    Ace is low (A-2-3) or high (Q-K-A), never both (no K-A-2).
 *  - Impure sequence: the same with jokers filling gaps.
 *  - Set: 3 or 4 cards of one rank, all different suits (jokers may fill in).
 *  - A group of nothing but jokers is fine (they count 0 and can always join another group).
 * A hand can be declared when every card is in a valid group, with at least two sequences and at least one pure.
 *
 * Points (lower is better, capped at [MAX_POINTS]): A, K, Q, J = 10, number cards their value, jokers 0.
 *  - no pure sequence: every card counts;
 *  - a pure sequence but fewer than two sequences: everything except the pure sequence(s) counts;
 *  - otherwise only the cards in invalid groups count.
 */
object RummyMelds {
    const val MAX_POINTS = 80

    fun isJoker(c: RCard, wild: Int): Boolean = c.isPrinted || c.rank == wild

    fun value(c: RCard, wild: Int): Int = when {
        isJoker(c, wild) -> 0
        c.rank == 1 || c.rank >= 10 -> 10
        else -> c.rank
    }

    fun classify(cards: List<RCard>, wild: Int): GroupKind {
        if (cards.isEmpty()) return GroupKind.INVALID
        if (cards.all { isJoker(it, wild) }) return GroupKind.JOKERS
        if (cards.size < 3) return GroupKind.INVALID
        if (isRun(cards)) return GroupKind.PURE
        val naturals = cards.filter { !isJoker(it, wild) }
        val jokers = cards.size - naturals.size
        if (isRunWithJokers(naturals, jokers)) return GroupKind.IMPURE
        if (cards.size <= 4 && naturals.map { it.rank }.toSet().size == 1 && naturals.map { it.suit }.toSet().size == naturals.size) return GroupKind.SET
        return GroupKind.INVALID
    }

    /** Natural run: no printed joker, one suit, distinct ranks in a row (Ace low or high). */
    private fun isRun(cards: List<RCard>): Boolean {
        if (cards.any { it.isPrinted }) return false
        if (cards.map { it.suit }.toSet().size != 1) return false
        val ranks = cards.map { it.rank }
        if (ranks.toSet().size != ranks.size) return false
        return consecutive(ranks.sorted()) || (1 in ranks && consecutive(ranks.map { if (it == 1) 14 else it }.sorted()))
    }

    private fun consecutive(r: List<Int>): Boolean = r.zipWithNext().all { (a, b) -> b == a + 1 }

    /** [naturals] of one suit with distinct ranks, fitting in a window of naturals + jokers cards. */
    private fun isRunWithJokers(naturals: List<RCard>, jokers: Int): Boolean {
        if (naturals.isEmpty()) return false
        if (naturals.map { it.suit }.toSet().size != 1) return false
        val ranks = naturals.map { it.rank }
        if (ranks.toSet().size != ranks.size) return false
        val total = naturals.size + jokers
        if (total > 13) return false
        fun fits(r: List<Int>) = r.max() - r.min() + 1 <= total
        return fits(ranks) || (1 in ranks && fits(ranks.map { if (it == 1) 14 else it }))
    }

    data class Eval(val kinds: List<GroupKind>, val points: Int, val valid: Boolean, val pure: Int, val sequences: Int)

    /** Kinds, points and declarability of an arrangement. */
    fun evaluate(groups: List<List<RCard>>, wild: Int): Eval {
        val kinds = groups.map { classify(it, wild) }
        val pure = kinds.count { it == GroupKind.PURE }
        val seqs = kinds.count { it == GroupKind.PURE || it == GroupKind.IMPURE }
        val total = groups.sumOf { g -> g.sumOf { value(it, wild) } }
        val pts = when {
            pure == 0 -> total
            seqs < 2 -> total - groups.withIndex().filter { kinds[it.index] == GroupKind.PURE }.sumOf { g -> g.value.sumOf { value(it, wild) } }
            else -> groups.withIndex().filter { kinds[it.index] == GroupKind.INVALID }.sumOf { g -> g.value.sumOf { value(it, wild) } }
        }.coerceAtMost(MAX_POINTS)
        val valid = pure >= 1 && seqs >= 2 && kinds.none { it == GroupKind.INVALID }
        return Eval(kinds, pts, valid, pure, seqs)
    }

    // ------------------------------------------------------------------ best arrangement

    /** A candidate meld: the (rank, suit) slots it needs (rank 14 = Ace high), and whether it's a run. */
    private class Template(val slots: List<Pair<Int, Int>>, val run: Boolean)

    data class Best(val groups: List<List<RCard>>, val points: Int, val valid: Boolean)

    /**
     * The arrangement of [hand] with the fewest points (ties: more valid groups). Searches melds made of the hand's
     * cards with jokers filling gaps; leftover jokers join a group. [budget] caps the search on unusual hands.
     */
    fun best(hand: List<RCard>, wild: Int, budget: Int = 60_000): Best {
        val jokerCards = hand.filter { it.isPrinted || it.rank == wild }
        val natural = hand.filter { !it.isPrinted && it.rank != wild }.sortedWith(compareBy({ it.suit }, { it.rank }))
        val wildNatural = hand.filter { !it.isPrinted && it.rank == wild }
        val nJ = jokerCards.size
        // what's in hand, by (rank, suit) (wild-rank cards may play as themselves)
        val have = HashMap<Long, Int>()
        fun key(r: Int, s: Int) = r * 10L + s
        for (c in hand) if (!c.isPrinted) have[key(c.rank, c.suit)] = (have[key(c.rank, c.suit)] ?: 0) + 1

        val templates = ArrayList<Template>()
        // runs: windows of ranks in one suit (14 = Ace high), at least one natural (non-wild) card in it
        for (s in 0..3) {
            for (lo in 1..12) for (hi in lo + 2..minOf(14, lo + 12)) {
                if (lo == 1 && hi == 14) continue
                val slots = (lo..hi).map { (if (it == 14) 1 else it) to s }
                val present = slots.count { (r, su) -> (have[key(r, su)] ?: 0) > 0 }
                val missing = slots.size - present
                if (missing > nJ || present == 0) continue
                if (slots.none { (r, su) -> r != wild && (have[key(r, su)] ?: 0) > 0 }) continue
                templates += Template(slots, true)
            }
        }
        // sets: a rank, a choice of suits (some may be jokers), 3 or 4 cards
        for (r in 1..13) {
            if (r == wild) continue
            val suits = (0..3).filter { (have[key(r, it)] ?: 0) > 0 }
            if (suits.isEmpty()) continue
            for (mask in 1 until (1 shl 4)) {
                val chosen = (0..3).filter { mask and (1 shl it) != 0 }
                if (chosen.size > 4) continue
                val present = chosen.count { it in suits }
                if (present == 0) continue
                val missing = chosen.size - present
                if (chosen.size < 3 || missing > nJ) continue
                templates += Template(chosen.map { r to it }, false)
            }
        }

        // DFS over the natural (non-wild) cards in order: each is left over or starts/joins a meld
        val byCard = natural.map { c -> templates.filter { t -> t.slots.any { it.first == c.rank && it.second == c.suit } } }
        val used = HashSet<Int>()
        /** Natural cards left over (not in any meld). */
        val skipped = HashSet<Int>()
        val chosen = ArrayList<List<RCard>>()
        var bestPts = Int.MAX_VALUE
        var bestValidGroups = -1
        var bestGroups: List<List<RCard>> = emptyList()
        var nodes = 0

        // cards available for a slot: an unused natural first, then an unused wild-rank card of that rank/suit
        fun take(r: Int, s: Int): RCard? =
            natural.firstOrNull { it.rank == r && it.suit == s && it.id !in used && it.id !in skipped }
                ?: wildNatural.firstOrNull { it.rank == r && it.suit == s && it.id !in used }

        fun leaf() {
            // leftover jokers (printed + unused wild cards) go into the first sequence/set that can take them, else their own group
            val freeJokers = jokerCards.filter { it.id !in used }
            val groups = chosen.map { it.toMutableList() }
            val left = natural.filter { it.id in skipped }
            var rest = freeJokers
            if (rest.isNotEmpty()) {
                // never into a pure sequence (it would stop being pure)
                val target = groups.indexOfFirst { g ->
                    classify(g, wild).let { it == GroupKind.IMPURE || it == GroupKind.SET } &&
                        classify(g + rest, wild).let { it == GroupKind.IMPURE || it == GroupKind.SET }
                }
                if (target >= 0) {
                    groups[target].addAll(rest)
                    rest = emptyList()
                }
            }
            val all = ArrayList<List<RCard>>()
            all.addAll(groups)
            if (rest.isNotEmpty()) all += rest
            // leftover cards: one group per suit (easier to see what could still join up)
            for (s in listOf(0, 1, 3, 2)) left.filter { it.suit == s }.sortedBy { it.rank }.takeIf { it.isNotEmpty() }?.let { all += it }
            val ev = evaluate(all, wild)
            val validGroups = ev.kinds.count { it != GroupKind.INVALID }
            if (ev.points < bestPts || (ev.points == bestPts && validGroups > bestValidGroups)) {
                bestPts = ev.points
                bestValidGroups = validGroups
                bestGroups = all
            }
        }

        fun dfs(i: Int) {
            if (++nodes > budget || bestPts == 0 && bestValidGroups >= 0 && nodes > 2000) return
            var k = i
            while (k < natural.size && (natural[k].id in used || natural[k].id in skipped)) k++
            if (k >= natural.size) {
                leaf()
                return
            }
            val c = natural[k]
            for (t in byCard[k]) {
                // this card must fill its own slot; the rest from hand or jokers
                val picked = ArrayList<RCard>()
                var jokersNeeded = 0
                var ok = true
                used += c.id
                picked += c
                var ownTaken = false
                for ((r, s) in t.slots) {
                    if (!ownTaken && r == c.rank && s == c.suit) { ownTaken = true; continue }
                    val got = take(r, s)
                    if (got != null) {
                        used += got.id
                        picked += got
                    } else jokersNeeded++
                }
                if (jokersNeeded > jokerCards.count { it.id !in used }) ok = false
                if (ok) {
                    // take specific jokers: printed first, then free wild-rank cards
                    val js = jokerCards.filter { it.id !in used }.sortedBy { if (it.isPrinted) 0 else 1 }.take(jokersNeeded)
                    if (js.size < jokersNeeded) ok = false
                    else {
                        js.forEach { used += it.id }
                        chosen += orderGroup(picked + js, t, wild)
                        dfs(k + 1)
                        chosen.removeAt(chosen.lastIndex)
                        js.forEach { used -= it.id }
                    }
                }
                picked.forEach { used -= it.id }
            }
            // or leave this card over
            skipped += c.id
            dfs(k + 1)
            skipped -= c.id
        }
        dfs(0)
        if (bestGroups.isEmpty()) bestGroups = listOf(hand)
        return Best(sortGroups(bestGroups, wild), bestPts.coerceAtLeast(0), evaluate(bestGroups, wild).valid)
    }

    /** A meld's cards in display order (runs by rank with jokers in their gaps; sets by suit). */
    private fun orderGroup(cards: List<RCard>, t: Template, wild: Int): List<RCard> {
        if (!t.run) return cards.sortedBy { if (isJoker(it, wild) && it.rank != t.slots[0].first) 9 else it.suit }
        val out = ArrayList<RCard>()
        val pool = cards.toMutableList()
        for ((r, s) in t.slots) {
            val c = pool.firstOrNull { it.rank == r && it.suit == s } ?: pool.firstOrNull { isJoker(it, wild) }
            if (c != null) { out += c; pool.remove(c) }
        }
        return out + pool
    }

    /** Pure sequences first, then impure, sets, jokers, and the leftover cards last. */
    fun sortGroups(groups: List<List<RCard>>, wild: Int): List<List<RCard>> {
        val order = mapOf(GroupKind.PURE to 0, GroupKind.IMPURE to 1, GroupKind.SET to 2, GroupKind.JOKERS to 3, GroupKind.INVALID to 4)
        return groups.filter { it.isNotEmpty() }.sortedBy { order[classify(it, wild)] }
    }

    /** One group per suit (jokers in their own group), high to low: the "Sort" button. */
    fun bySuit(hand: List<RCard>, wild: Int): List<List<RCard>> {
        val jokers = hand.filter { isJoker(it, wild) }
        val rest = hand.filter { !isJoker(it, wild) }
        val suits = listOf(0, 1, 3, 2).map { s -> rest.filter { it.suit == s }.sortedBy { it.rank } }.filter { it.isNotEmpty() }
        return (if (jokers.isNotEmpty()) listOf(jokers) else emptyList()) + suits
    }
}
