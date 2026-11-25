package com.ngdstudio.mindimania.teenpatti

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlin.math.pow
import kotlin.random.Random

/**
 * Teen Patti bots. They only use what a real player knows: their own cards once seen, and what everyone did.
 *
 * Strength = the share of all possible hands this hand beats ([TeenPattiEngine.strength]). The chance to beat
 * every opponent is roughly strength ^ opponents; seen opponents who keep paying make that worse ("pressure").
 * That chance is weighed against the price of the call (pot odds) and against the bot's own stack.
 * Easy bots misjudge their hand a lot and bluff often; Hard and Expert read the betting and almost never call
 * off their whole stack with a weak hand.
 */
object TeenPattiBot {
    private data class Style(
        /** Chance per turn to keep playing blind (while under the blind limit). */
        val blindChance: Double,
        val blindLimit: Int,
        val blindRaise: Double,
        val bluff: Double,
        /** Random error added to the bot's read of its own hand. */
        val noise: Double,
        /** How much seen opponents' betting counts as strength. */
        val read: Double,
        val sideshow: Double,
    )

    private fun style(d: Difficulty) = when (d) {
        Difficulty.EASY -> Style(0.55, 3, 0.10, 0.16, 0.40, 0.0, 0.25)
        Difficulty.NORMAL -> Style(0.50, 2, 0.12, 0.08, 0.14, 0.10, 0.35)
        Difficulty.HARD -> Style(0.45, 2, 0.15, 0.06, 0.05, 0.20, 0.45)
        Difficulty.GENIUS -> Style(0.50, 3, 0.18, 0.07, 0.0, 0.28, 0.50)
    }

    /** Share of all hands [seat]'s cards beat. */
    fun strength(s: TeenPattiState, seat: Int): Double = TeenPattiEngine.strength(s, seat)

    fun decide(s: TeenPattiState, seat: Int, diff: Difficulty, rnd: Random): TPAction? {
        val o = TeenPattiEngine.options(s, seat)
        val st = style(diff)
        if (o.mustReply) return TPAction.SideshowReply(seat, accept = reply(s, seat, st, rnd))
        if (!o.myTurn) return null

        if (!s.seen[seat]) {
            val cheap = s.stake <= TeenPattiEngine.MAX_STAKE / 4 && o.canChaal && o.chaalCost * 6 <= s.chips[seat]
            if (cheap && s.blindChaals[seat] < st.blindLimit && rnd.nextDouble() < st.blindChance) {
                if (o.canShow && s.blindChaals[seat] >= 2 && rnd.nextDouble() < 0.25) return TPAction.Show(seat)
                return TPAction.Chaal(seat, raise = o.canRaise && rnd.nextDouble() < st.blindRaise)
            }
            return TPAction.See(seat)
        }

        val win = winChance(s, seat, st, rnd)
        val bluff = rnd.nextDouble() < st.bluff
        if (o.canAllIn) return if (win > 0.45 || (bluff && win > 0.2)) TPAction.AllIn(seat) else TPAction.Pack(seat)

        val cost = o.chaalCost
        val odds = cost.toDouble() / (s.pot + cost)
        // a call that eats a big part of the stack needs a real hand
        val stackShare = cost.toDouble() / s.chips[seat].coerceAtLeast(1)
        val tooDear = stackShare > 0.25 && win < 0.5 && !bluff
        // long hands wear everyone down: weak hands give up
        val tired = s.actions > s.active.size * 4 && win < 0.4 && rnd.nextDouble() < 0.5

        if (o.canShow) {
            if (win > 0.8 && o.canRaise && rnd.nextDouble() < 0.45) return TPAction.Chaal(seat, raise = true)
            if (win > 0.6 || (s.actions >= 6 && win > 0.4)) return TPAction.Show(seat)
            if ((win < odds * 1.1 || tooDear || tired) && !bluff) return TPAction.Pack(seat)
            return if (win > 0.42 && rnd.nextDouble() < 0.5) TPAction.Show(seat) else TPAction.Chaal(seat)
        }
        if ((win < odds * 0.9 || tooDear || tired) && !bluff) return TPAction.Pack(seat)
        if (o.canSideshow && win in 0.2..0.6 && rnd.nextDouble() < st.sideshow) return TPAction.Sideshow(seat)
        if (o.canRaise && (win > 0.68 || bluff) && rnd.nextDouble() < 0.5) return TPAction.Chaal(seat, raise = true)
        return TPAction.Chaal(seat)
    }

    /** Rough chance that [seat]'s hand beats everyone still in. */
    private fun winChance(s: TeenPattiState, seat: Int, st: Style, rnd: Random): Double {
        val pct = (strength(s, seat) + (rnd.nextDouble() - 0.5) * st.noise).coerceIn(0.0, 1.0)
        val opponents = s.active.filter { it != seat }
        // every seen opponent that has paid this far is likely holding something
        val pressure = opponents.count { s.seen[it] } * st.read * (1 + s.actions / 4.0).coerceAtMost(3.0)
        return pct.pow(opponents.size + pressure)
    }

    private fun reply(s: TeenPattiState, seat: Int, st: Style, rnd: Random): Boolean {
        val pct = (strength(s, seat) + (rnd.nextDouble() - 0.5) * st.noise).coerceIn(0.0, 1.0)
        // the asker is seen and paid to ask: usually a middling hand
        val threshold = 0.5 + st.read * 0.4
        return pct >= threshold
    }
}
