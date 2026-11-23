package com.ngdstudio.mindimania.rummy

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlin.random.Random

/**
 * Rummy bots. They only use their own cards and the open pile.
 *  - Pick: take the open card when keeping it (and throwing something else) lowers their points, otherwise draw blind.
 *    Jokers on the open pile are always taken.
 *  - Throw: the card whose removal leaves the fewest points; on a tie, the one with the fewest neighbours (cards that
 *    could grow into a sequence or set), then the highest value. Never straight back the card just taken.
 *  - Declare as soon as a throw leaves a valid hand. Harder bots drop a hopeless opening hand.
 * Easy bots look at fewer options and make random mistakes; Expert also keeps cards the next player seems to collect.
 */
object RummyBot {
    fun decide(s: RummyState, seat: Int, diff: Difficulty, rnd: Random): RAction? {
        when (val ph = s.phase) {
            is RPhase.Showdown -> {
                if (seat !in ph.pending) return null
                val best = RummyMelds.best(s.hands[seat], s.wild)
                return RAction.Arrange(seat, best.groups.map { g -> g.map { it.id } })
            }
            is RPhase.HandOver -> return null
            else -> Unit
        }
        if (s.turn != seat) return null
        val hand = s.hands[seat]
        return if (s.phase == RPhase.Pick) pick(s, seat, hand, diff, rnd) else discard(s, seat, hand, diff, rnd)
    }

    private fun pick(s: RummyState, seat: Int, hand: List<RCard>, diff: Difficulty, rnd: Random): RAction {
        val now = RummyMelds.best(hand, s.wild).points
        // a hopeless first hand: drop it cheaply
        if (s.picks[seat] == 0 && diff >= Difficulty.HARD && now >= 75 && rnd.nextDouble() < 0.5) return RAction.Drop(seat)
        val top = s.openTop ?: return RAction.PickClosed(seat)
        if (RummyMelds.isJoker(top, s.wild)) return RAction.PickOpen(seat)
        if (diff == Difficulty.EASY && rnd.nextDouble() < 0.5) return RAction.PickClosed(seat)
        val with = hand + top
        // the best we could do after taking it (and throwing something other than it)
        val after = with.filter { it.id != top.id }.minOf { d -> RummyMelds.best(with - d, s.wild, budget = 8_000).points }
        val gain = if (diff == Difficulty.EASY) 8 else 3
        return if (after <= now - gain || (after == 0)) RAction.PickOpen(seat) else RAction.PickClosed(seat)
    }

    private fun discard(s: RummyState, seat: Int, hand: List<RCard>, diff: Difficulty, rnd: Random): RAction {
        val justTook = s.lastPickedOpen[seat]
        val candidates = hand.filter { it.id != justTook }.distinctBy { it.rank * 10 + it.suit }
        // 1. can we finish?
        for (c in candidates) {
            val best = RummyMelds.best(hand - c, s.wild)
            if (best.valid) return RAction.Declare(seat, c.id, best.groups.map { g -> g.map { it.id } })
        }
        // 2. throw the least useful card
        val next = RummyEngine.nextActive(s, seat)
        val nextCollects = s.lastPickedOpen.getOrNull(next)?.takeIf { it >= 0 }?.let { id -> s.hands[next].firstOrNull { it.id == id } }
        val scored = candidates.map { c ->
            val pts = if (RummyMelds.isJoker(c, s.wild)) 999 else RummyMelds.best(hand - c, s.wild, budget = 8_000).points
            val neigh = neighbours(c, hand - c, s.wild)
            // Expert: don't feed the next player the suit/rank they just picked up
            val feeds = if (diff == Difficulty.GENIUS && nextCollects != null &&
                (nextCollects.rank == c.rank || (nextCollects.suit == c.suit && kotlin.math.abs(nextCollects.rank - c.rank) <= 2))) 3 else 0
            Triple(c, pts + feeds, neigh)
        }
        val sorted = scored.sortedWith(compareBy<Triple<RCard, Int, Int>>({ it.second }, { it.third }, { -RummyMelds.value(it.first, s.wild) }))
        val pick = if (diff == Difficulty.EASY && sorted.size > 2 && rnd.nextDouble() < 0.25) sorted[1 + rnd.nextInt(minOf(2, sorted.size - 1))] else sorted.first()
        return RAction.Discard(seat, pick.first.id)
    }

    /** How many cards in [rest] could combine with [c] (same rank, or same suit within two ranks). */
    private fun neighbours(c: RCard, rest: List<RCard>, wild: Int): Int {
        if (RummyMelds.isJoker(c, wild)) return 9
        return rest.count { o ->
            !RummyMelds.isJoker(o, wild) && o.id != c.id &&
                ((o.rank == c.rank && o.suit != c.suit) || (o.suit == c.suit && kotlin.math.abs(o.rank - c.rank) in 1..2))
        }
    }
}
