package com.ngdstudio.mindimania.engine.ai

import com.ngdstudio.mindimania.engine.Action
import com.ngdstudio.mindimania.engine.GameEngine
import com.ngdstudio.mindimania.engine.model.Card
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.Phase
import com.ngdstudio.mindimania.engine.model.Suit
import kotlin.random.Random

/**
 * "Genius" bot: determinized Monte Carlo search. Hidden cards (opponents' hands + draw pile) are re-dealt at random
 * consistent with what the bot can see, each candidate move is played out with a fast policy, and the move with the
 * best average outcome wins.
 */
object MonteCarlo {
    var budgetMs: Long = 220L
    private const val MAX_PLIES = 90
    private const val MAX_ITER = 2500

    fun choose(state: GameState, me: Int, rnd: Random): Action? {
        val candidates = candidates(state, me)
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        val total = DoubleArray(candidates.size)
        val count = IntArray(candidates.size)
        val deadline = System.nanoTime() + budgetMs * 1_000_000
        var iter = 0
        while (iter < MAX_ITER && System.nanoTime() < deadline) {
            for (i in candidates.indices) {
                val det = determinize(state, me, rnd)
                val eng = GameEngine(det, recordSnapshots = false)
                val ok = runCatching { eng.apply(candidates[i]) }.isSuccess
                if (!ok) continue
                total[i] += rollout(eng, me, rnd)
                count[i]++
                iter++
            }
        }
        var best = 0
        var bestVal = Double.NEGATIVE_INFINITY
        for (i in candidates.indices) {
            if (count[i] == 0) continue
            val v = total[i] / count[i]
            if (v > bestVal) { bestVal = v; best = i }
        }
        return candidates[best]
    }

    private fun candidates(s: GameState, me: Int): List<Action> {
        val eng = GameEngine(s.copyDeep(), recordSnapshots = false)
        return when (val ph = s.phase) {
            is Phase.Play -> {
                val p = s.players[me]
                val plays = eng.playableCards(me).distinctBy { it.face(s.side) }
                    .map { Action.Play(me, it.id, callMania = p.hand.size == 2) }
                plays.ifEmpty { listOf(Action.Draw(me)) }
            }
            is Phase.ChooseColor -> Suit.forSide(s.side).map { Action.ChooseColor(me, it) }
            else -> emptyList()
        }
    }

    private fun determinize(s: GameState, me: Int, rnd: Random): GameState {
        val c = s.copyDeep()
        val pool = ArrayList<Card>(c.drawPile.size + 64)
        pool.addAll(c.drawPile)
        c.players.forEach { if (it.id != me) pool.addAll(it.hand) }
        pool.shuffle(rnd)
        var idx = 0
        c.players.forEach { pl ->
            if (pl.id != me) {
                val n = pl.hand.size
                pl.hand.clear()
                repeat(n) { pl.hand.add(pool[idx++]) }
            }
        }
        c.drawPile = pool.subList(idx, pool.size).toMutableList()
        c.rngState = rnd.nextLong()
        return c
    }

    private fun rollout(eng: GameEngine, me: Int, rnd: Random): Double {
        val s = eng.state
        var plies = 0
        while (!s.isOver && plies < MAX_PLIES) {
            val actor = s.actingPlayer
            val action = BotAI.heuristic(eng, actor, Difficulty.EASY, rnd)
            if (runCatching { eng.apply(action) }.isFailure) {
                if (runCatching { eng.apply(Action.Draw(actor)) }.isFailure) break
            }
            plies++
        }
        return evaluate(s, me)
    }

    private fun evaluate(s: GameState, me: Int): Double {
        val ph = s.phase
        val winner = when (ph) {
            is Phase.RoundOver -> ph.winnerId
            is Phase.MatchOver -> ph.winnerId
            else -> -1
        }
        if (winner >= 0) return if (winner == me) 1.0 else 0.0
        val mine = s.players[me]
        if (mine.eliminated) return 0.0
        var sum = 0.0
        for (p in s.activePlayers) sum += 1.0 / (1 + p.hand.size)
        return 0.9 * (1.0 / (1 + mine.hand.size)) / sum
    }
}
