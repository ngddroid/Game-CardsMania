package com.ngdstudio.mindimania.mindi

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlin.random.Random

/**
 * Mindi computer players. They only use what a real player in that seat could know: their own hand, the cards
 * played, hand sizes, the known trump, and which suits each player has shown they're out of.
 * Partners play as a team: never trump a trick the partner is surely winning, feed 10s to a winning partner.
 */
object MindiBot {

    fun decide(s: MindiState, me: Int, d: Difficulty, rnd: Random): MindiAction? = when (val ph = s.phase) {
        is MindiPhase.ChooseHidden -> if (ph.seat == me) MindiAction.Hide(me, chooseHidden(s, me, d, rnd).id) else null
        MindiPhase.Play -> if (s.turn == me) MindiAction.Play(me, chooseCard(s, me, d, rnd).id) else null
        else -> null
    }

    // ------------------------------------------------------------------ hidden trump

    fun chooseHidden(s: MindiState, me: Int, d: Difficulty, rnd: Random): MCard {
        val hand = s.hands[me]
        if (d == Difficulty.EASY) return hand.filter { !it.isTen }.ifEmpty { hand }.random(rnd)
        // trump = my longest suit (ties: more high cards); hide its lowest non-ten card
        val bySuit = hand.groupBy { it.suit }
        val best = bySuit.maxBy { (_, cards) -> cards.size * 10 + cards.count { it.rank >= 11 } * 3 + cards.sumOf { it.rank } / 20 }.value
        return best.filter { !it.isTen }.minByOrNull { it.rank } ?: best.minBy { it.rank }
    }

    // ------------------------------------------------------------------ card play

    fun chooseCard(s: MindiState, me: Int, d: Difficulty, rnd: Random): MCard {
        val legal = MindiEngine.legalCards(s, me)
        if (legal.size == 1) return legal.first()
        if (d == Difficulty.GENIUS) MindiSearch.best(s, me, rnd)?.let { return it }
        if (d == Difficulty.EASY) {
            val safe = legal.filter { !it.isTen }
            return if (safe.isNotEmpty() && rnd.nextDouble() < 0.75) safe.random(rnd) else legal.random(rnd)
        }
        val k = Knowledge(s, me)
        val lead = s.leadSuit ?: return chooseLead(k, legal, d, rnd)
        return if (legal.all { it.suit == lead }) chooseFollow(k, legal) else chooseOffSuit(k, legal, d)
    }

    /** Facts a player at [me] can know. */
    class Knowledge(val s: MindiState, val me: Int) {
        val team = s.team(me)
        val unseen: List<MCard> = MindiEngine.unseen(s, me)
        val trump: Int? = s.trump
        val winning: MindiPlay? = s.winningPlay()
        val partnerWinning: Boolean = winning != null && s.team(winning.seat) == team
        val trickHasTen: Boolean = s.trick.any { it.card.isTen }
        val trickTens: Int = s.trick.count { it.card.isTen }
        /** Seats still to play after me in this trick. */
        val after: List<Int> = run {
            val l = ArrayList<Int>()
            var p = s.next(me)
            repeat(s.players - s.trick.size - 1) { l += p; p = s.next(p) }
            l
        }
        val opponentsAfter: List<Int> = after.filter { s.team(it) != team }
        val last: Boolean = after.isEmpty()

        fun higherUnseen(card: MCard) = unseen.any { it.suit == card.suit && it.rank > card.rank }
        fun isBoss(card: MCard) = !higherUnseen(card)

        /** Can an opponent who plays after me still beat [play]? (best-effort, uses public voids) */
        fun threatened(play: MindiPlay): Boolean {
            if (opponentsAfter.isEmpty()) return false
            val lead = s.leadSuit ?: play.card.suit
            if (play.trump) {
                // only higher trumps beat it
                return unseen.any { it.suit == play.card.suit && it.rank > play.card.rank } &&
                    opponentsAfter.any { play.card.suit !in s.voids[it] }
            }
            if (unseen.any { it.suit == lead && it.rank > play.card.rank } && opponentsAfter.any { lead !in s.voids[it] }) return true
            // an opponent out of the led suit could trump (or set trump in Cut mode)
            val cutPossible = when {
                trump != null -> unseen.any { it.suit == trump }
                s.setup.trumpMode == MindiTrumpMode.CUT -> true
                s.setup.trumpMode == MindiTrumpMode.HIDE && s.hidden != null -> true
                else -> false
            }
            return cutPossible && opponentsAfter.any { lead in s.voids[it] || s.hands[it].size <= 2 }
        }

        /** Would [card] (played now by me) take the lead of the trick? */
        fun beats(card: MCard): Boolean {
            val w = winning ?: return true
            val lead = s.leadSuit!!
            val asTrump = when {
                trump != null -> card.suit == trump && (lead != trump || s.trick.first().trump)
                s.setup.trumpMode == MindiTrumpMode.CUT && card.suit != lead -> true // sets trump now
                else -> false
            }
            return when {
                asTrump && !w.trump -> true
                asTrump && w.trump -> card.rank > w.card.rank
                w.trump -> false
                else -> card.suit == lead && card.rank > w.card.rank
            }
        }

        fun asPlay(card: MCard): MindiPlay {
            val lead = s.leadSuit
            val t = when {
                trump != null -> card.suit == trump && (lead == null || lead != trump || s.trick.first().trump)
                s.setup.trumpMode == MindiTrumpMode.CUT && lead != null && card.suit != lead -> true
                else -> false
            }
            return MindiPlay(me, card, t)
        }
    }

    private fun lowest(cards: List<MCard>, avoidTens: Boolean = true): MCard {
        val pool = if (avoidTens) cards.filter { !it.isTen }.ifEmpty { cards } else cards
        return pool.minWith(compareBy({ it.rank }, { it.id }))
    }

    private fun chooseFollow(k: Knowledge, legal: List<MCard>): MCard {
        val w = k.winning!!
        val partnerSafe = k.partnerWinning && !k.threatened(w)
        val tens = legal.filter { it.isTen }
        // 1) partner is surely winning: give them a 10 if I can, otherwise my lowest card
        if (partnerSafe) return tens.firstOrNull() ?: lowest(legal)
        val winners = legal.filter { k.beats(it) }
        if (winners.isNotEmpty()) {
            // 2) last to play: win as cheaply as possible (a 10 that wins is captured by us!)
            if (k.last) return tens.firstOrNull { it in winners } ?: winners.minBy { it.rank }
            // 3) a safe winner: the cheapest card that can't be beaten afterwards
            val safe = winners.filter { !k.threatened(k.asPlay(it)) }
            if (safe.isNotEmpty() && (k.trickHasTen || tens.isNotEmpty() || !k.partnerWinning)) {
                return safe.firstOrNull { it.isTen } ?: safe.minBy { it.rank }
            }
            if (k.trickHasTen && !k.partnerWinning) return winners.maxBy { it.rank }
        }
        // 4) can't win safely: keep my 10s, throw my lowest
        return lowest(legal)
    }

    private fun chooseOffSuit(k: Knowledge, legal: List<MCard>, d: Difficulty): MCard {
        val s = k.s
        val w = k.winning!!
        val partnerSafe = k.partnerWinning && !k.threatened(w)
        val trump = k.trump
        val bySuit = s.hands[k.me].groupBy { it.suit }
        if (partnerSafe) {
            // feed a 10 from another suit, but never "cut" my own partner (in Cut mode any off-suit card sets trump)
            val tens = legal.filter { it.isTen && it.suit != trump }
            if (tens.isNotEmpty() && (trump != null || s.setup.trumpMode != MindiTrumpMode.CUT)) return tens.first()
            return discard(k, legal)
        }
        if (trump != null) {
            val trumps = legal.filter { it.suit == trump }
            val beating = trumps.filter { k.beats(it) }
            val worth = k.trickHasTen || k.last || d == Difficulty.HARD || k.s.trick.size >= s.players / 2
            if (beating.isNotEmpty() && worth) {
                val safe = beating.filter { !k.threatened(k.asPlay(it)) }
                val pick = (safe.ifEmpty { beating })
                return pick.firstOrNull { it.isTen && !k.threatened(k.asPlay(it)) } ?: pick.minBy { it.rank }
            }
            return discard(k, legal)
        }
        if (s.setup.trumpMode == MindiTrumpMode.CUT) {
            // my card will become trump: choose it from my longest suit
            val longest = bySuit.maxBy { (_, c) -> c.size * 10 + c.count { it.rank >= 11 } }.key
            val inSuit = legal.filter { it.suit == longest }
            return if (k.trickHasTen || !k.partnerWinning) (inSuit.filter { !it.isTen }.minByOrNull { it.rank } ?: inSuit.first())
            else lowest(inSuit.ifEmpty { legal })
        }
        return discard(k, legal)
    }

    /** Throw a low card from my shortest side suit (to create voids), keeping 10s and trumps. */
    private fun discard(k: Knowledge, legal: List<MCard>): MCard {
        val pool = legal.filter { !it.isTen && it.suit != k.trump }.ifEmpty { legal.filter { !it.isTen } }.ifEmpty { legal }
        val counts = k.s.hands[k.me].groupingBy { it.suit }.eachCount()
        return pool.minWith(compareBy({ counts[it.suit] ?: 0 }, { it.rank }))
    }

    private fun chooseLead(k: Knowledge, legal: List<MCard>, d: Difficulty, rnd: Random): MCard {
        val s = k.s
        val trump = k.trump
        val partners = s.partnersOf(k.me)
        val opponents = (0 until s.players).filter { s.team(it) != k.team }
        // a boss 10 (every higher card of its suit is gone) is a free mindi unless an opponent can trump it
        val bossTens = legal.filter { it.isTen && k.isBoss(it) && it.suit != trump && opponents.none { o -> it.suit in s.voids[o] } }
        if (bossTens.isNotEmpty()) return bossTens.first()
        // boss cards (e.g. aces) in side suits draw out the opponents' cards
        val bosses = legal.filter { !it.isTen && it.suit != trump && k.isBoss(it) && it.rank >= 12 && opponents.none { o -> it.suit in s.voids[o] } }
        if (bosses.isNotEmpty() && (d == Difficulty.HARD || rnd.nextDouble() < 0.7)) return bosses.maxBy { it.rank }
        // Hard: lead low into a suit my partner can trump
        if (d == Difficulty.HARD && trump != null) {
            val partnerVoid = legal.filter { c -> c.suit != trump && !c.isTen && partners.any { c.suit in s.voids[it] } && opponents.none { c.suit in s.voids[it] } }
            if (partnerVoid.isNotEmpty()) return partnerVoid.minBy { it.rank }
        }
        // otherwise a low card from my longest non-trump suit, never a 10
        val counts = s.hands[k.me].groupingBy { it.suit }.eachCount()
        val pool = legal.filter { !it.isTen && it.suit != trump }.ifEmpty { legal.filter { !it.isTen } }.ifEmpty { legal }
        return pool.minWith(compareBy({ -(counts[it.suit] ?: 0) }, { it.rank }))
    }

    /** The card the Hint button suggests for a human at [me]. */
    fun hint(s: MindiState, me: Int): MCard? {
        if (s.phase != MindiPhase.Play || s.turn != me) return null
        return chooseCard(s, me, Difficulty.HARD, Random(7))
    }
}

/**
 * Expert bot: guesses the unseen cards many times (consistent with what is known) and plays each candidate card out
 * to the end of the hand with the Hard heuristic, picking the card with the best average result for its team.
 */
object MindiSearch {
    var budgetMs = 180L

    fun best(s: MindiState, me: Int, rnd: Random): MCard? {
        val legal = MindiEngine.legalCards(s, me).distinctBy { it.suit * 100 + it.rank }
        if (legal.size <= 1) return legal.firstOrNull()
        val total = DoubleArray(legal.size)
        val count = IntArray(legal.size)
        val deadline = System.nanoTime() + budgetMs * 1_000_000
        var rounds = 0
        while (System.nanoTime() < deadline && rounds < 400) {
            val det = determinize(s, me, rnd)
            for (i in legal.indices) {
                total[i] += rollout(det, me, legal[i], rnd)
                count[i]++
            }
            rounds++
        }
        if (count.all { it == 0 }) return null
        return legal[legal.indices.maxBy { if (count[it] == 0) -1e9 else total[it] / count[it] }]
    }

    /** Re-deals the cards [me] can't see to the other seats (hand sizes and known voids respected when possible). */
    private fun determinize(s: MindiState, me: Int, rnd: Random): MindiState {
        val pool = MindiEngine.unseen(s, me).toMutableList()
        pool.shuffle(rnd)
        val hidden: MCard? = if (s.hidden != null && s.hiddenOwner != me) pool.removeAt(pool.lastIndex) else s.hidden
        val hands = s.hands.map { it.toMutableList() }
        for (p in 0 until s.players) if (p != me) hands[p].clear()
        for (p in 0 until s.players) {
            if (p == me) continue
            val need = s.hands[p].size
            var i = 0
            while (hands[p].size < need && i < pool.size) {
                if (pool[i].suit !in s.voids[p]) hands[p] += pool.removeAt(i) else i++
            }
            while (hands[p].size < need && pool.isNotEmpty()) hands[p] += pool.removeAt(pool.lastIndex)
        }
        return s.copy(hands = hands.map { it.toList() }, hidden = hidden)
    }

    private fun rollout(start: MindiState, me: Int, first: MCard, rnd: Random): Double {
        var s = start
        val team = s.team(me)
        var steps = MindiEngine.apply(s, MindiAction.Play(me, first.id))
        s = steps.last().state
        var guard = 0
        while (s.phase == MindiPhase.Play && guard++ < 200) {
            val seat = s.turn
            val legal = MindiEngine.legalCards(s, seat)
            val card = if (legal.size == 1) legal.first() else MindiBot.chooseCard(s, seat, Difficulty.HARD, rnd)
            steps = MindiEngine.apply(s, MindiAction.Play(seat, card.id))
            s = steps.last().state
        }
        val r = when (val ph = s.phase) {
            is MindiPhase.HandOver -> ph.result
            is MindiPhase.MatchOver -> ph.result
            else -> return 0.5
        }
        val won = if (r.winnerTeam == team) 1.0 else 0.0
        val tensDiff = (r.tens[team] - r.tens[1 - team]).toDouble() / s.totalTens
        return won * (if (r.kind != HandKind.NORMAL) 1.4 else 1.0) + 0.3 * tensDiff
    }
}
