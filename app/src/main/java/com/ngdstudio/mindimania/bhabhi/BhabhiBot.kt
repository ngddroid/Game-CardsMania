package com.ngdstudio.mindimania.bhabhi

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlin.math.pow
import kotlin.random.Random

/**
 * Bhabhi bots. They only use what a player at that seat could know: their own hand, the cards played and thrown
 * away, cards they saw someone pick up, and the suits each player has shown they're out of.
 *
 * The danger in Bhabhi is holding the highest card of a trick when someone cuts it (thulla). So the bots
 * dump their high cards while a trick looks safe, stay under the current high card when a cut looks likely,
 * lead suits nobody is out of, and use their own thullas to throw away their biggest cards.
 */
object BhabhiBot {

    fun decide(s: BhabhiState, seat: Int, d: Difficulty, rnd: Random): BhabhiAction? {
        if (s.isOver || s.turn != seat) return null
        val card = chooseCard(s, seat, d, rnd) ?: return null
        return BhabhiAction.Play(seat, card.id)
    }

    fun chooseCard(s: BhabhiState, seat: Int, d: Difficulty, rnd: Random): BCard? {
        val legal = BhabhiEngine.legalCards(s, seat)
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        return when (d) {
            Difficulty.EASY -> if (rnd.nextDouble() < 0.6) legal.random(rnd) else heuristic(s, seat, legal, careful = false)
            Difficulty.NORMAL -> heuristic(s, seat, legal, careful = false)
            Difficulty.HARD -> heuristic(s, seat, legal, careful = true)
            Difficulty.GENIUS -> BhabhiSearch.best(s, seat, legal, rnd) ?: heuristic(s, seat, legal, careful = true)
        }
    }


    // ------------------------------------------------------------------ knowledge

    /** Card for a deck id (ids follow BhabhiEngine.deck(): suit * 13 + rank - 2). */
    fun cardOf(id: Int) = BCard(id, id % 13 + 2, id / 13)

    private fun knownHas(s: BhabhiState, p: Int, suit: Int) = s.known[p].any { it / 13 == suit }

    /** Certainly out of [suit]: showed a void and hasn't been seen picking that suit up since. */
    fun isVoid(s: BhabhiState, p: Int, suit: Int) = suit in s.voids[p] && !knownHas(s, p, suit)

    /** Seats that still play in this trick after [seat]. */
    fun afterMe(s: BhabhiState, seat: Int): List<Int> {
        val i = s.order.indexOf(seat)
        return if (i < 0) emptyList() else s.order.drop(i + 1)
    }

    /**
     * Chance that at least one of [players] can't follow [suit] (so the trick gets cut), seen from [me].
     * Careful bots estimate it from the unseen cards; others only react to voids they have seen.
     */
    fun cutRisk(s: BhabhiState, me: Int, suit: Int, players: List<Int>, careful: Boolean): Double {
        if (players.isEmpty()) return 0.0
        if (players.any { isVoid(s, it, suit) }) return 1.0
        if (!careful) return 0.15
        val unseen = BhabhiEngine.unseen(s, me)
        // known cards are visible to everyone: take them out of the guess
        val knownIds = HashSet<Int>()
        for (p in 0 until s.players) if (p != me) knownIds += s.known[p]
        val hidden = unseen.filter { it.id !in knownIds }
        val total = hidden.size.coerceAtLeast(1)
        val ofSuit = hidden.count { it.suit == suit }
        var safe = 1.0
        for (p in players) {
            if (knownHas(s, p, suit)) continue
            val h = (s.hands[p].size - s.known[p].size).coerceAtLeast(0)
            val pNone = ((total - ofSuit).toDouble() / total).pow(h)
            safe *= 1 - pNone
        }
        return 1 - safe
    }

    // ------------------------------------------------------------------ heuristic play

    fun heuristic(s: BhabhiState, seat: Int, legal: List<BCard>, careful: Boolean): BCard {
        if (s.trick.isEmpty()) return lead(s, seat, legal, careful)
        val lead = s.leadSuit!!
        val follow = legal.filter { it.suit == lead }
        if (follow.isEmpty()) return dump(s, seat, legal)
        val high = s.highPlay()!!.card.rank
        val after = afterMe(s, seat)
        // last to play and everyone followed: the trick will be thrown away, so dump the biggest card
        if (after.isEmpty()) return follow.maxBy { it.rank }
        val risk = cutRisk(s, seat, lead, after, careful)
        val under = follow.filter { it.rank < high }
        val over = follow.filter { it.rank > high }
        val danger = if (careful) 0.35 else 0.5
        return when {
            risk >= danger && under.isNotEmpty() -> under.maxBy { it.rank } // stay under the high card, shed what we can
            risk >= danger -> over.minBy { it.rank } // forced over: lowest, so someone after us may still overtake
            else -> follow.maxBy { it.rank } // looks safe: get rid of the biggest card
        }
    }

    private fun lead(s: BhabhiState, seat: Int, legal: List<BCard>, careful: Boolean): BCard {
        val hand = s.hands[seat]
        val others = s.order.filter { it != seat }
        val bySuit = legal.groupBy { it.suit }
        // safest suit to lead; among equals, the shortest (running out of a suit lets us thulla later)
        val scored = bySuit.keys.map { suit ->
            val risk = cutRisk(s, seat, suit, others, careful)
            Triple(suit, risk, hand.count { it.suit == suit })
        }
        val best = scored.minWith(compareBy<Triple<Int, Double, Int>>({ (it.second * 10).toInt() }, { it.third }))
        val cards = bySuit.getValue(best.first)
        val safe = if (careful) best.second < 0.2 else best.second < 0.5
        return if (safe) cards.maxBy { it.rank } else cards.minBy { it.rank }
    }

    /** Can't follow: throw the most dangerous card, preferring a short suit so we run out of it. */
    private fun dump(s: BhabhiState, seat: Int, legal: List<BCard>): BCard {
        val hand = s.hands[seat]
        return legal.maxWith(compareBy<BCard>({ it.rank }, { -hand.count { c -> c.suit == it.suit } }))
    }
}

/**
 * Expert bot: plays each candidate card in many random deals of the unseen cards (consistent with what is public)
 * and finishes the game with the Hard heuristic for everyone, keeping the card with the best average outcome.
 */
object BhabhiSearch {
    fun best(s: BhabhiState, seat: Int, legal: List<BCard>, rnd: Random, budgetMs: Long = 160): BCard? {
        if (legal.size <= 1) return legal.firstOrNull()
        val deadline = System.nanoTime() + budgetMs * 1_000_000
        val total = DoubleArray(legal.size)
        val count = IntArray(legal.size)
        var i = 0
        while (i < 6000) {
            if (i >= legal.size * 4 && System.nanoTime() > deadline) break
            val k = i % legal.size
            val world = sampleWorld(s, seat, rnd)
            val after = BhabhiEngine.apply(world, BhabhiAction.Play(seat, legal[k].id)).last().state
            total[k] += playout(after, seat)
            count[k]++
            i++
        }
        var bestK = 0
        for (k in legal.indices) if (total[k] / count[k].coerceAtLeast(1) > total[bestK] / count[bestK].coerceAtLeast(1)) bestK = k
        return legal[bestK]
    }

    /** 1.0 for getting out first … just above 0 for getting out last; −1 for being the Bhabhi. */
    private fun playout(start: BhabhiState, me: Int): Double {
        var st = start
        var guard = 0
        while (!st.isOver && guard++ < 1500) {
            val p = st.turn
            if (st.hands[me].isEmpty() && me in st.out) break
            val legal = BhabhiEngine.legalCards(st, p)
            val c = if (legal.size == 1) legal[0] else BhabhiBot.heuristic(st, p, legal, careful = true)
            st = BhabhiEngine.apply(st, BhabhiAction.Play(p, c.id)).last().state
        }
        if (st.loser == me) return -1.0
        val place = st.placeOf(me) ?: return 0.0
        return (st.players - place).toDouble() / (st.players - 1) + 0.05
    }

    /** The real state with every other hand replaced by a random deal of the cards [seat] can't see. */
    fun sampleWorld(s: BhabhiState, seat: Int, rnd: Random): BhabhiState {
        val knownIds = HashSet<Int>()
        for (p in 0 until s.players) if (p != seat) knownIds += s.known[p]
        val pool = BhabhiEngine.unseen(s, seat).filter { it.id !in knownIds }.toMutableList()
        val others = (0 until s.players).filter { it != seat && s.hands[it].isNotEmpty() }
        val need = IntArray(s.players) { p -> if (p == seat) 0 else (s.hands[p].size - s.known[p].size).coerceAtLeast(0) }
        repeat(25) { attempt ->
            pool.shuffle(rnd)
            val got = HashMap<Int, MutableList<BCard>>()
            val left = need.copyOf()
            var ok = true
            for (c in pool) {
                // respect voids on the first tries; drop them if no consistent deal turns up
                val eligible = others.filter { left[it] > 0 && (attempt > 20 || c.suit !in s.voids[it]) }
                if (eligible.isEmpty()) { ok = false; break }
                val p = eligible[rnd.nextInt(eligible.size)]
                got.getOrPut(p) { mutableListOf() } += c
                left[p]--
            }
            if (ok) {
                val hands = (0 until s.players).map { p ->
                    when {
                        p == seat -> s.hands[p]
                        s.hands[p].isEmpty() -> emptyList()
                        else -> BhabhiEngine.sortHand(s.known[p].map { BhabhiBot.cardOf(it) } + got[p].orEmpty())
                    }
                }
                return s.copy(hands = hands)
            }
        }
        // unreachable: without the void rule (last tries) the counts always fit, because the pool holds exactly the
        // unknown cards of the other hands. Never fall back to the real hands, which would let the bot cheat.
        error("could not deal a sample world")
    }
}
