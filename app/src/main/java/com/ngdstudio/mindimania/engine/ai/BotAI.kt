package com.ngdstudio.mindimania.engine.ai

import com.ngdstudio.mindimania.engine.Action
import com.ngdstudio.mindimania.engine.GameEngine
import com.ngdstudio.mindimania.engine.model.Card
import com.ngdstudio.mindimania.engine.model.ColorReason
import com.ngdstudio.mindimania.engine.model.DeckFactory
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.Face
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.engine.model.Phase
import com.ngdstudio.mindimania.engine.model.PlayerState
import com.ngdstudio.mindimania.engine.model.Side
import com.ngdstudio.mindimania.engine.model.Suit
import kotlin.math.pow
import kotlin.random.Random

/**
 * Computer opponents. Bots only use information a human in their seat could see:
 * their own hand, the discard pile, hand sizes, public void hints and (Eclipse) the other side of opponents' cards.
 */
object BotAI {

    /** Main entry: the action [me] wants to take in [state], or null if nothing to do. */
    fun decide(state: GameState, me: Int, difficulty: Difficulty, rnd: Random): Action? {
        if (state.isOver) return null
        val engine = GameEngine(state.copyDeep(), recordSnapshots = false)
        val s = engine.state
        val p = s.players[me]
        if (p.eliminated) return null
        if (s.actingPlayer != me) return null

        if (difficulty == Difficulty.GENIUS) {
            val ph = s.phase
            if (ph == Phase.Play || (ph is Phase.ChooseColor && ph.reason != ColorReason.STORM)) {
                MonteCarlo.choose(s, me, rnd)?.let { return it }
            }
        }
        return heuristic(engine, me, difficulty, rnd)
    }

    fun heuristic(engine: GameEngine, me: Int, difficulty: Difficulty, rnd: Random): Action {
        val s = engine.state
        val p = s.players[me]
        return when (val ph = s.phase) {
            is Phase.Play -> {
                if (s.config.powerUps && p.tokens > 0 && !p.shield) return Action.ArmShield(me)
                choosePlay(engine, me, difficulty, rnd)
            }
            is Phase.DrawnCard -> {
                val card = p.hand.first { it.id == ph.cardId }
                val f = card.face(s.side)
                // Normal+ keep a drawn wild if they still hold several cards (unless forced).
                val keep = !s.config.forcedPlay && difficulty != Difficulty.EASY && f.isWild &&
                    p.hand.size > 4 && f.kind == Kind.PRISM && rnd.nextDouble() < 0.5
                if (keep) Action.Keep(me) else Action.Play(me, card.id, callMania = callMania(p, difficulty, rnd))
            }
            is Phase.ChooseColor -> Action.ChooseColor(me, chooseColor(s, me, difficulty, rnd, ph.reason))
            is Phase.ChooseSwap -> Action.ChooseSwap(me, chooseSwapTarget(s, me, difficulty, rnd))
            is Phase.Challenge -> Action.Challenge(me, shouldChallenge(s, ph, difficulty, rnd))
            else -> Action.Draw(me)
        }
    }

    private fun callMania(p: PlayerState, difficulty: Difficulty, rnd: Random): Boolean {
        if (p.hand.size != 2) return false
        val forget = when (difficulty) {
            Difficulty.EASY -> 0.35
            Difficulty.NORMAL -> 0.10
            else -> 0.0
        }
        return rnd.nextDouble() >= forget
    }

    private fun choosePlay(engine: GameEngine, me: Int, difficulty: Difficulty, rnd: Random): Action {
        val s = engine.state
        val p = s.players[me]
        val playable = engine.playableCards(me)
        if (playable.isEmpty()) return Action.Draw(me)
        val mania = callMania(p, difficulty, rnd)

        if (difficulty == Difficulty.EASY) {
            val nonWild = playable.filter { !it.face(s.side).isWild }
            val pick = if (nonWild.isNotEmpty() && rnd.nextDouble() < 0.7) nonWild.random(rnd) else playable.random(rnd)
            return Action.Play(me, pick.id, callMania = mania)
        }

        val scored = playable.map { it to scoreCard(s, me, it, difficulty, rnd) }
        val best = scored.maxBy { it.second }.first
        val f = best.face(s.side)
        var extras = emptyList<Int>()
        if (s.config.comboDrop && s.pendingDraw == 0 && f.kind == Kind.NUMBER) {
            extras = p.hand.filter { it.id != best.id && it.face(s.side).let { o -> o.kind == Kind.NUMBER && o.value == f.value } }
                .sortedBy { c -> if (c.face(s.side).suit == bestColorAfter(s, p, best)) 1 else 0 }
                .map { it.id }
            if (extras.size + 1 >= p.hand.size) extras = extras.take((p.hand.size - 2).coerceAtLeast(0))
        }
        val callNow = if (extras.isNotEmpty()) p.hand.size - extras.size - 1 == 1 && mania else mania
        return Action.Play(me, best.id, extras, callNow)
    }

    private fun bestColorAfter(s: GameState, p: PlayerState, played: Card): Suit {
        val counts = suitCounts(p.hand.filter { it.id != played.id }, s.side)
        return counts.maxByOrNull { it.value }?.key ?: Suit.WILD
    }

    fun suitCounts(cards: List<Card>, side: Side): Map<Suit, Int> {
        val m = HashMap<Suit, Int>()
        for (c in cards) {
            val f = c.face(side)
            if (!f.isWild) m[f.suit] = (m[f.suit] ?: 0) + 1
        }
        return m
    }

    private fun scoreCard(s: GameState, me: Int, card: Card, difficulty: Difficulty, rnd: Random): Double {
        val side = s.side
        val f = card.face(side)
        val p = s.players[me]
        val after = p.hand.filter { it.id != card.id }
        val others = s.activePlayers.filter { it.id != me }
        val next = s.players[s.nextActive(me)]
        val prev = s.players[s.nextActive(me, -s.direction)]
        val minOpp = others.minOfOrNull { it.hand.size } ?: 7
        val threat = when {
            minOpp <= 1 -> 3.0
            minOpp == 2 -> 2.0
            minOpp <= 4 -> 1.0
            else -> 0.35
        }
        val hard = difficulty == Difficulty.HARD || difficulty == Difficulty.GENIUS
        var sc = rnd.nextDouble() * 0.3

        if (s.pendingDraw > 0) return 20.0 - f.drawAmount * 0.5 - if (f.isWild) 2.0 else 0.0

        sc += f.points(s.mode) * 0.04 * threat
        if (!f.isWild) sc += after.count { it.face(side).suit == f.suit } * 0.6
        if (f.isWild) sc -= if (after.size <= 2) 0.0 else 3.0 / threat.coerceAtLeast(0.5)

        val nextThreat = if (next.hand.size <= 2) 4.0 else if (next.hand.size <= 4) 1.5 else 0.6
        when (f.kind) {
            Kind.NUMBER -> {
                if (s.config.sevenZero && f.value == 7) {
                    val target = others.minByOrNull { it.hand.size }
                    sc += if (target != null && target.hand.size < after.size - 1) (after.size - target.hand.size) * 1.3 else -3.0
                }
                if (s.config.sevenZero && f.value == 0) sc += (after.size - prev.hand.size) * 1.0
                sc += 0.5
            }
            Kind.BLOCK -> sc += nextThreat
            Kind.REVERSE -> sc += if (next.hand.size <= 2 && prev.hand.size > next.hand.size) 3.0 else 0.5
            Kind.DRAW -> sc += nextThreat + f.value * 0.4
            Kind.BLOCK_ALL -> {
                sc += threat
                if (after.any { val af = it.face(side); af.isWild || af.suit == f.suit }) sc += 2.0
            }
            Kind.PURGE -> sc += after.count { it.face(side).suit == f.suit } * 1.6
            Kind.ECLIPSE -> sc += eclipseValue(s, me) + 0.5
            Kind.PRISM -> sc += 0.0
            Kind.PRISM_DRAW, Kind.PRISM_REBOUND -> {
                sc += nextThreat + f.drawAmount * 0.3
                if (s.config.challenge && f.kind == Kind.PRISM_DRAW) {
                    val illegal = after.any { val af = it.face(side); !af.isWild && af.suit == s.color }
                    if (illegal) sc -= if (hard) 6.0 else 2.0
                }
            }
            Kind.COLOR_STORM, Kind.PRISM_HUNT -> sc += nextThreat * 1.2
        }
        if (hard && !f.isWild && (next.voidMask and (1 shl f.suit.ordinal)) != 0) sc += 2.0
        if (hard && next.hand.size <= 2 && f.kind == Kind.NUMBER && others.size > 1) sc -= 0.5
        return sc
    }

    /** Eclipse: compare the leading opponent's strength on both sides (their other side is public). */
    private fun eclipseValue(s: GameState, me: Int): Double {
        val opp = s.activePlayers.filter { it.id != me }.minByOrNull { it.hand.size } ?: return 0.0
        fun strength(cards: List<Card>, side: Side) = cards.sumOf {
            val f = it.face(side)
            when {
                f.isWild -> 3.0
                f.isAction -> 1.5
                else -> 0.2
            }
        }
        val now = strength(opp.hand, s.side)
        val then = strength(opp.hand, s.side.other)
        return (now - then) * 0.8
    }

    fun chooseColor(s: GameState, me: Int, difficulty: Difficulty, rnd: Random, reason: ColorReason): Suit {
        val suits = Suit.forSide(s.side)
        if (difficulty == Difficulty.EASY && rnd.nextDouble() < 0.5) return suits.random(rnd)
        val p = s.players[me]
        if (reason == ColorReason.STORM) {
            // I must reveal until this color: pick the color with most unseen cards.
            val unseen = unseenCounts(s, me)
            return suits.maxBy { (unseen[it] ?: 0) + rnd.nextDouble() * 0.5 }
        }
        val counts = suitCounts(p.hand, s.side)
        val hard = difficulty == Difficulty.HARD || difficulty == Difficulty.GENIUS
        val next = s.players[s.nextActive(me)]
        return suits.maxBy { suit ->
            var v = (counts[suit] ?: 0).toDouble() * 2 + rnd.nextDouble() * 0.5
            if (hard && (next.voidMask and (1 shl suit.ordinal)) != 0) v += 1.5
            v
        }
    }

    /** Cards of each suit not visible to [me] (not in my hand and not in the discard). */
    fun unseenCounts(s: GameState, me: Int): Map<Suit, Int> {
        val total = HashMap<Suit, Int>()
        val faces: List<Face> = when (s.mode) {
            com.ngdstudio.mindimania.engine.model.GameMode.ECLIPSE ->
                if (s.side == Side.SUN) DeckFactory.eclipseSunFaces() else DeckFactory.eclipseMoonFaces()
            com.ngdstudio.mindimania.engine.model.GameMode.CLASSIC -> DeckFactory.classicFaces()
            com.ngdstudio.mindimania.engine.model.GameMode.CHAOS -> DeckFactory.chaosFaces()
        }
        repeat(s.config.deckCount) { faces.forEach { if (!it.isWild) total[it.suit] = (total[it.suit] ?: 0) + 1 } }
        val seen = s.players[me].hand + s.discard
        seen.forEach {
            val f = it.face(s.side)
            if (!f.isWild) total[f.suit] = (total[f.suit] ?: 0) - 1
        }
        return total
    }

    private fun chooseSwapTarget(s: GameState, me: Int, difficulty: Difficulty, rnd: Random): Int {
        val others = s.activePlayers.filter { it.id != me }
        if (difficulty == Difficulty.EASY) return others.random(rnd).id
        return others.minBy { it.hand.size * 10 + rnd.nextInt(5) }.id
    }

    private fun shouldChallenge(s: GameState, ph: Phase.Challenge, difficulty: Difficulty, rnd: Random): Boolean {
        val h = s.players[ph.offenderId].hand.size
        val pHadColor = 1.0 - (1.0 - 0.22).pow(h)
        return when (difficulty) {
            Difficulty.EASY -> rnd.nextDouble() < 0.1
            Difficulty.NORMAL -> pHadColor > 0.85 && rnd.nextDouble() < 0.5
            else -> pHadColor > 0.78
        }
    }

    /** Jump-in decision for bots (out of turn). */
    fun jumpIn(state: GameState, me: Int, difficulty: Difficulty, rnd: Random): Action? {
        val engine = GameEngine(state.copyDeep(), recordSnapshots = false)
        val cards = engine.jumpInCards(me)
        if (cards.isEmpty()) return null
        val chance = when (difficulty) {
            Difficulty.EASY -> 0.25
            Difficulty.NORMAL -> 0.55
            else -> 0.9
        }
        if (rnd.nextDouble() >= chance) return null
        val p = state.players[me]
        return Action.Play(me, cards.first().id, callMania = p.hand.size == 2)
    }

    /** Chance (0..1) a bot notices a missed MANIA! call, and how long it takes. */
    fun catchProfile(difficulty: Difficulty): Pair<Double, Long> = when (difficulty) {
        Difficulty.EASY -> 0.35 to 2600L
        Difficulty.NORMAL -> 0.7 to 1700L
        Difficulty.HARD -> 0.95 to 900L
        Difficulty.GENIUS -> 1.0 to 700L
    }

    /** Best card for the human's Hint button. */
    fun hint(state: GameState, me: Int): Card? {
        val engine = GameEngine(state.copyDeep(), recordSnapshots = false)
        val playable = engine.playableCards(me)
        if (playable.isEmpty()) return null
        val rnd = Random(42)
        return playable.maxBy { scoreCard(engine.state, me, it, Difficulty.HARD, rnd) }
    }
}
