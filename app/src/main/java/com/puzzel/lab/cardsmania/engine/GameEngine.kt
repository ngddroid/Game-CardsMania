package com.puzzel.lab.cardsmania.engine

import com.puzzel.lab.cardsmania.engine.model.Card
import com.puzzel.lab.cardsmania.engine.model.ColorReason
import com.puzzel.lab.cardsmania.engine.model.DeckFactory
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.PlayerState
import com.puzzel.lab.cardsmania.engine.model.Side
import com.puzzel.lab.cardsmania.engine.model.Suit

class IllegalActionException(message: String) : IllegalStateException(message)

/**
 * The complete rules of Cards Mania (Classic, Chaos, Eclipse).
 * Pure Kotlin: no Android dependencies. Mutates [state]; every visible change is recorded as a [Step].
 */
class GameEngine(val state: GameState, private val recordSnapshots: Boolean = true) {

    private val rng = Rng(state.rngState)
    private val steps = ArrayList<Step>()
    private val config: GameConfig get() = state.config

    companion object {
        const val MAX_TURNS = 3000

        fun newGame(config: GameConfig, recordSnapshots: Boolean = true): Pair<GameEngine, List<Step>> {
            val players = config.players.mapIndexed { i, s -> PlayerState(i, s) }
            val state = GameState(config, players, rngState = config.seed)
            val engine = GameEngine(state, recordSnapshots)
            state.dealer = engine.rng.nextInt(players.size)
            val steps = engine.run { startRound() }
            return engine to steps
        }
    }

    // ------------------------------------------------------------------ public API

    fun canPlay(card: Card): Boolean {
        val s = state
        if (s.discard.isEmpty()) return false
        val f = card.face(s.side)
        val top = s.topFace
        if (s.pendingDraw > 0) {
            if (!f.isDraw || f.drawAmount < s.pendingMin) return false
            return f.isWild || f.suit == s.color || f.sameSymbol(top)
        }
        return f.isWild || f.suit == s.color || f.sameSymbol(top)
    }

    fun playableCards(playerId: Int): List<Card> {
        val p = state.players[playerId]
        return when (val ph = state.phase) {
            is Phase.Play -> if (state.current == playerId) p.hand.filter { canPlay(it) } else emptyList()
            is Phase.DrawnCard -> if (ph.playerId == playerId) p.hand.filter { it.id == ph.cardId && canPlay(it) } else emptyList()
            else -> emptyList()
        }
    }

    /** Cards [playerId] could jump in with right now (out of turn). */
    fun jumpInCards(playerId: Int): List<Card> {
        val s = state
        if (!config.jumpIn || s.phase != Phase.Play || s.pendingDraw > 0 || playerId == s.current) return emptyList()
        val p = s.players[playerId]
        if (p.eliminated || s.discard.isEmpty()) return emptyList()
        val top = s.topFace
        if (top.isWild) return emptyList()
        return p.hand.filter { it.face(s.side) == top }
    }

    /** Returns null if the action is legal, otherwise a reason. */
    fun validate(a: Action): String? {
        val s = state
        if (a.playerId !in s.players.indices) return "no such player"
        val p = s.players[a.playerId]
        if (a is Action.NextRound) return if (s.phase is Phase.RoundOver) null else "round not over"
        if (s.isOver) return "round over"
        if (p.eliminated) return "eliminated"
        return when (a) {
            is Action.Play -> validatePlay(a, p)
            is Action.Draw -> if (s.phase == Phase.Play && s.current == a.playerId) null else "not your turn"
            is Action.Keep -> {
                val ph = s.phase
                if (ph is Phase.DrawnCard && ph.playerId == a.playerId && !config.forcedPlay) null else "cannot keep"
            }
            is Action.ChooseColor -> {
                val ph = s.phase
                when {
                    ph !is Phase.ChooseColor || ph.playerId != a.playerId -> "not choosing color"
                    a.suit !in Suit.forSide(s.side) -> "bad color"
                    else -> null
                }
            }
            is Action.ChooseSwap -> {
                val ph = s.phase
                when {
                    ph !is Phase.ChooseSwap || ph.playerId != a.playerId -> "not swapping"
                    a.targetId == a.playerId || a.targetId !in s.players.indices -> "bad target"
                    s.players[a.targetId].eliminated -> "target eliminated"
                    else -> null
                }
            }
            is Action.Challenge -> {
                val ph = s.phase
                if (ph is Phase.Challenge && ph.victimId == a.playerId) null else "nothing to challenge"
            }
            is Action.CallMania -> if (p.hand.size <= 2 && (s.maniaVulnerable == a.playerId || p.hand.size == 2)) null else "cannot call"
            is Action.Catch -> {
                val t = s.players.getOrNull(a.targetId)
                if (t != null && s.maniaVulnerable == a.targetId && a.targetId != a.playerId && t.hand.size == 1) null else "nothing to catch"
            }
            is Action.ArmShield -> if (config.powerUps && p.tokens > 0 && !p.shield) null else "no token"
            is Action.Peek -> if (config.powerUps && p.tokens > 0) null else "no token"
            is Action.NextRound -> null
        }
    }

    private fun validatePlay(a: Action.Play, p: PlayerState): String? {
        val s = state
        val card = p.hand.firstOrNull { it.id == a.cardId } ?: return "card not in hand"
        when (val ph = s.phase) {
            is Phase.Play -> {
                if (a.playerId != s.current) {
                    if (a.extraIds.isNotEmpty()) return "no combo on jump-in"
                    return if (jumpInCards(a.playerId).any { it.id == card.id }) null else "cannot jump in"
                }
                if (!canPlay(card)) return "card does not match"
                if (a.extraIds.isNotEmpty()) {
                    if (!config.comboDrop || s.pendingDraw > 0) return "combo not allowed"
                    val f = card.face(s.side)
                    if (f.kind != Kind.NUMBER) return "combo needs numbers"
                    if (a.extraIds.toSet().size != a.extraIds.size || a.cardId in a.extraIds) return "duplicate"
                    for (id in a.extraIds) {
                        val c = p.hand.firstOrNull { it.id == id } ?: return "extra not in hand"
                        val ef = c.face(s.side)
                        if (ef.kind != Kind.NUMBER || ef.value != f.value) return "extra must match number"
                    }
                }
                return null
            }
            is Phase.DrawnCard -> {
                if (ph.playerId != a.playerId || ph.cardId != a.cardId || a.extraIds.isNotEmpty()) return "only the drawn card"
                return if (canPlay(card)) null else "card does not match"
            }
            else -> return "not playing now"
        }
    }

    fun apply(a: Action): List<Step> {
        validate(a)?.let { throw IllegalActionException("${a::class.simpleName}: $it") }
        steps.clear()
        val s = state
        if (a !is Action.CallMania && a !is Action.Catch && a !is Action.Peek && a !is Action.ArmShield &&
            s.maniaVulnerable >= 0 && a.playerId != s.maniaVulnerable
        ) {
            s.maniaVulnerable = -1
        }
        when (a) {
            is Action.Play -> doPlay(a)
            is Action.Draw -> doDraw(s.players[a.playerId])
            is Action.Keep -> endTurn(1)
            is Action.ChooseColor -> doChooseColor(a)
            is Action.ChooseSwap -> doSwap(a)
            is Action.Challenge -> doChallenge(a)
            is Action.CallMania -> doCallMania(a)
            is Action.Catch -> {
                val t = s.players[a.targetId]
                s.maniaVulnerable = -1
                record(GameEvent.Caught(a.playerId, t.id))
                penalty(t, 2, DrawReason.CATCH)
            }
            is Action.ArmShield -> {
                val p = s.players[a.playerId]
                p.tokens--
                p.shield = true
                record(GameEvent.ShieldArmed(p.id))
            }
            is Action.Peek -> {
                val p = s.players[a.playerId]
                p.tokens--
                record(GameEvent.PeekRevealed(p.id, s.drawPile.takeLast(3).reversed()))
            }
            is Action.NextRound -> {
                s.dealer = (s.dealer + 1) % s.players.size
                startRound()
            }
        }
        return finish()
    }

    /** The last snapshot must reflect the final state (phase changes happen after the last event is recorded). */
    private fun finish(): List<Step> {
        state.rngState = rng.state
        if (recordSnapshots && steps.isNotEmpty()) {
            val last = steps.removeAt(steps.lastIndex)
            steps += Step(last.event, state.copyDeep())
        }
        return steps.toList()
    }

    // ------------------------------------------------------------------ round setup

    private fun startRound(): List<Step> {
        steps.clear()
        val s = state
        s.round++
        val deck = DeckFactory.build(config.mode, config.deckCount, rng)
        rng.shuffle(deck)
        s.players.forEach {
            it.hand.clear(); it.eliminated = false; it.maniaCalled = false
            it.meter = 0; it.tokens = 0; it.shield = false; it.voidMask = 0
        }
        s.side = Side.SUN
        s.direction = 1
        s.pendingDraw = 0
        s.pendingMin = 0
        s.maniaVulnerable = -1
        s.idlePasses = 0
        s.turnCount = 0
        s.biggestStack = 0
        s.lastPlayIllegal = false
        s.color = Suit.WILD
        s.discard = mutableListOf()
        s.drawPile = deck
        s.phase = Phase.Play
        record(GameEvent.RoundStarted(s.round, s.dealer))

        val n = s.players.size
        val order = ArrayList<Pair<Int, Card>>()
        repeat(config.handSize) {
            var idx = s.dealer
            repeat(n) {
                idx = (idx + 1) % n
                val c = s.drawPile.removeAt(s.drawPile.lastIndex)
                s.players[idx].hand.add(c)
                order += idx to c
            }
        }
        record(GameEvent.Dealt(order))

        // Starter card
        var starter: Card
        while (true) {
            starter = s.drawPile.removeAt(s.drawPile.lastIndex)
            val f = starter.face(s.side)
            val ok = if (config.mode == GameMode.CLASSIC) f.kind != Kind.PRISM_DRAW else f.kind == Kind.NUMBER
            if (ok) break
            s.drawPile.add(rng.nextInt(s.drawPile.size), starter)
        }
        s.discard.add(starter)
        val f = starter.face(s.side)
        s.color = if (f.isWild) Suit.WILD else f.suit
        record(GameEvent.StarterFlipped(starter))
        s.current = s.nextActive(s.dealer)

        if (config.mode == GameMode.CLASSIC) {
            when (f.kind) {
                Kind.BLOCK -> {
                    record(GameEvent.Blocked(listOf(s.current)))
                    s.current = s.nextActive(s.current)
                }
                Kind.REVERSE -> {
                    s.direction = -1
                    record(GameEvent.DirectionChanged(-1))
                    s.current = s.dealer
                }
                Kind.DRAW -> {
                    val victim = s.players[s.current]
                    penalty(victim, f.value, DrawReason.START)
                    record(GameEvent.Blocked(listOf(victim.id)))
                    s.current = s.nextActive(s.current)
                }
                Kind.PRISM -> {
                    s.phase = Phase.ChooseColor(s.current, ColorReason.START)
                    return finish()
                }
                else -> Unit
            }
        }
        s.phase = Phase.Play
        record(GameEvent.TurnChanged(s.current))
        return finish()
    }

    // ------------------------------------------------------------------ actions

    private fun doPlay(a: Action.Play) {
        val s = state
        val p = s.players[a.playerId]
        val jump = s.phase == Phase.Play && a.playerId != s.current
        val card = p.hand.first { it.id == a.cardId }
        val extras = a.extraIds.map { id -> p.hand.first { it.id == id } }
        val oldColor = s.color
        if (jump) s.current = a.playerId
        if (a.callMania) p.maniaCalled = true
        p.hand.remove(card)
        extras.forEach { p.hand.remove(it) }
        s.discard.add(card)
        s.discard.addAll(extras)
        s.idlePasses = 0
        val face = (extras.lastOrNull() ?: card).face(s.side)
        if (!face.isWild) p.voidMask = p.voidMask and (1 shl face.suit.ordinal).inv()
        s.lastPlayIllegal = face.kind == Kind.PRISM_DRAW &&
            p.hand.any { val hf = it.face(s.side); !hf.isWild && hf.suit == oldColor }
        record(GameEvent.CardPlayed(p.id, card, extras, jump))

        if (config.powerUps && face.isAction) {
            p.meter++
            if (p.meter >= 4) {
                p.meter = 0
                p.tokens++
                record(GameEvent.TokenEarned(p.id))
            }
        }
        updateMania(p)
        if (p.hand.isEmpty()) {
            finishWithLastCard(p, face)
            return
        }
        resolve(p, face)
    }

    private fun resolve(p: PlayerState, face: Face) {
        val s = state
        when (face.kind) {
            Kind.NUMBER -> {
                s.color = face.suit
                if (config.sevenZero && face.value == 7 && s.activeCount > 1) {
                    s.phase = Phase.ChooseSwap(p.id)
                    return
                }
                if (config.sevenZero && face.value == 0 && s.activeCount > 1) rotateHands()
                endTurn(1)
            }
            Kind.BLOCK -> {
                s.color = face.suit
                record(GameEvent.Blocked(listOf(s.nextActive(p.id))))
                endTurn(2)
            }
            Kind.REVERSE -> {
                s.color = face.suit
                s.direction = -s.direction
                record(GameEvent.DirectionChanged(s.direction))
                if (s.activeCount == 2) {
                    record(GameEvent.Blocked(listOf(s.nextActive(p.id))))
                    endTurn(2)
                } else endTurn(1)
            }
            Kind.DRAW -> {
                s.color = face.suit
                drawAttack(p, face.value)
            }
            Kind.BLOCK_ALL -> {
                s.color = face.suit
                record(GameEvent.Blocked(s.activePlayers.filter { it.id != p.id }.map { it.id }))
                record(GameEvent.PlayAgain(p.id))
                endTurn(0)
            }
            Kind.PURGE -> {
                s.color = face.suit
                val purged = p.hand.filter { val f = it.face(s.side); !f.isWild && f.suit == face.suit }
                if (purged.isNotEmpty()) {
                    p.hand.removeAll(purged.toSet())
                    s.discard.addAll(s.discard.size - 1, purged)
                    record(GameEvent.Purged(p.id, purged))
                    updateMania(p)
                    if (p.hand.isEmpty()) {
                        endRound(p.id)
                        return
                    }
                }
                endTurn(1)
            }
            Kind.ECLIPSE -> {
                flip()
                val nf = s.topFace
                if (nf.isWild) {
                    s.color = Suit.WILD
                    s.phase = Phase.ChooseColor(p.id, ColorReason.FLIP)
                } else {
                    s.color = nf.suit
                    endTurn(1)
                }
            }
            Kind.PRISM, Kind.PRISM_DRAW, Kind.PRISM_REBOUND, Kind.PRISM_HUNT -> {
                s.phase = Phase.ChooseColor(p.id, ColorReason.WILD)
            }
            Kind.COLOR_STORM -> {
                val victim = s.nextActive(p.id)
                record(GameEvent.StormStarted(victim))
                s.phase = Phase.ChooseColor(victim, ColorReason.STORM)
            }
        }
    }

    private fun finishWithLastCard(p: PlayerState, face: Face) {
        val s = state
        if (face.isDraw) {
            if (face.kind == Kind.PRISM_REBOUND) {
                s.direction = -s.direction
                record(GameEvent.DirectionChanged(s.direction))
            }
            val total = face.drawAmount + if (config.stacking) s.pendingDraw else 0
            s.pendingDraw = 0
            s.pendingMin = 0
            val victimId = s.nextActive(p.id)
            if (victimId != p.id) penalty(s.players[victimId], total, DrawReason.PENALTY)
        }
        endRound(p.id)
    }

    private fun drawAttack(attacker: PlayerState, amount: Int) {
        val s = state
        if (config.stacking) {
            s.pendingDraw += amount
            s.pendingMin = amount
            s.biggestStack = maxOf(s.biggestStack, s.pendingDraw)
            record(GameEvent.StackGrew(s.pendingDraw, amount))
            endTurn(1)
        } else {
            val victim = s.players[s.nextActive(attacker.id)]
            penalty(victim, amount, DrawReason.PENALTY)
            if (s.isOver) return
            record(GameEvent.Blocked(listOf(victim.id)))
            endTurn(if (victim.eliminated) 1 else 2)
        }
    }

    private fun doDraw(p: PlayerState) {
        val s = state
        if (s.pendingDraw > 0) {
            val n = s.pendingDraw
            s.pendingDraw = 0
            s.pendingMin = 0
            penalty(p, n, DrawReason.STACK)
            if (s.isOver) return
            endTurn(1)
            return
        }
        if (!s.color.isWild) p.voidMask = p.voidMask or (1 shl s.color.ordinal)
        val got = ArrayList<Card>()
        if (config.drawToMatch) {
            val limit = config.overloadLimit
            while (true) {
                val c = takeOne() ?: break
                p.hand.add(c)
                got += c
                if (canPlay(c)) break
                if (limit > 0 && p.hand.size >= limit) break
            }
        } else {
            takeOne()?.let { p.hand.add(it); got += it }
        }
        if (got.isEmpty()) {
            record(GameEvent.Passed(p.id))
            s.idlePasses++
            if (s.idlePasses >= s.activeCount * 2) deadlockEnd() else endTurn(1)
            return
        }
        s.idlePasses = 0
        record(GameEvent.CardsDrawn(p.id, got, if (config.drawToMatch) DrawReason.MATCH else DrawReason.NORMAL))
        updateMania(p)
        if (checkOverload(p)) {
            if (!s.isOver) endTurn(1)
            return
        }
        val last = got.last()
        if (canPlay(last)) s.phase = Phase.DrawnCard(p.id, last.id) else endTurn(1)
    }

    private fun doChooseColor(a: Action.ChooseColor) {
        val s = state
        val ph = s.phase as Phase.ChooseColor
        s.color = a.suit
        record(GameEvent.ColorChosen(a.playerId, a.suit))
        when (ph.reason) {
            ColorReason.START -> {
                s.phase = Phase.Play
                record(GameEvent.TurnChanged(s.current))
            }
            ColorReason.FLIP -> endTurn(1)
            ColorReason.STORM -> {
                val victim = s.players[a.playerId]
                s.current = victim.id
                reveal(victim, a.suit, DrawReason.STORM)
                if (s.isOver) return
                if (!victim.eliminated) record(GameEvent.Blocked(listOf(victim.id)))
                endTurn(1)
            }
            ColorReason.WILD -> {
                val p = s.players[a.playerId]
                val f = s.topFace
                when (f.kind) {
                    Kind.PRISM_DRAW -> when {
                        config.stacking -> drawAttack(p, f.value)
                        config.challenge -> {
                            s.phase = Phase.Challenge(s.nextActive(p.id), p.id, s.lastPlayIllegal, f.value)
                        }
                        else -> drawAttack(p, f.value)
                    }
                    Kind.PRISM_REBOUND -> {
                        s.direction = -s.direction
                        record(GameEvent.DirectionChanged(s.direction))
                        drawAttack(p, 4)
                    }
                    Kind.PRISM_HUNT -> {
                        val victim = s.players[s.nextActive(p.id)]
                        reveal(victim, a.suit, DrawReason.HUNT)
                        if (s.isOver) return
                        if (!victim.eliminated) record(GameEvent.Blocked(listOf(victim.id)))
                        endTurn(if (victim.eliminated) 1 else 2)
                    }
                    else -> endTurn(1)
                }
            }
        }
    }

    private fun doChallenge(a: Action.Challenge) {
        val s = state
        val ph = s.phase as Phase.Challenge
        val offender = s.players[ph.offenderId]
        val victim = s.players[ph.victimId]
        s.current = offender.id
        if (a.challenge) {
            if (ph.illegal) {
                record(GameEvent.ChallengeResult(victim.id, offender.id, true))
                penalty(offender, ph.amount, DrawReason.CHALLENGE)
                if (s.isOver) return
                endTurn(1)
            } else {
                record(GameEvent.ChallengeResult(victim.id, offender.id, false))
                penalty(victim, ph.amount + 2, DrawReason.CHALLENGE)
                if (s.isOver) return
                record(GameEvent.Blocked(listOf(victim.id)))
                endTurn(if (victim.eliminated) 1 else 2)
            }
        } else {
            record(GameEvent.NoChallenge(victim.id))
            penalty(victim, ph.amount, DrawReason.PENALTY)
            if (s.isOver) return
            record(GameEvent.Blocked(listOf(victim.id)))
            endTurn(if (victim.eliminated) 1 else 2)
        }
    }

    private fun doSwap(a: Action.ChooseSwap) {
        val s = state
        val me = s.players[a.playerId]
        val other = s.players[a.targetId]
        val mine = me.hand.toList()
        me.hand.clear(); me.hand.addAll(other.hand)
        other.hand.clear(); other.hand.addAll(mine)
        me.voidMask = 0; other.voidMask = 0
        me.maniaCalled = false; other.maniaCalled = false
        s.maniaVulnerable = -1
        record(GameEvent.HandsSwapped(me.id, other.id))
        checkOverload(other)
        if (s.isOver) return
        checkOverload(me)
        if (s.isOver) return
        endTurn(1)
    }

    private fun doCallMania(a: Action.CallMania) {
        val s = state
        val p = s.players[a.playerId]
        if (s.maniaVulnerable == p.id) {
            s.maniaVulnerable = -1
            record(GameEvent.ManiaCalled(p.id))
        } else {
            p.maniaCalled = true
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun updateMania(p: PlayerState) {
        val s = state
        when {
            p.hand.size == 1 && !p.eliminated -> {
                if (p.maniaCalled) {
                    p.maniaCalled = false
                    if (s.maniaVulnerable == p.id) s.maniaVulnerable = -1
                    record(GameEvent.ManiaCalled(p.id))
                } else if (s.maniaVulnerable != p.id) {
                    // Only becomes vulnerable when shedding down to one card (not when receiving cards).
                    s.maniaVulnerable = p.id
                }
            }
            p.hand.size > 2 -> p.maniaCalled = false
        }
        if (p.hand.size != 1 && s.maniaVulnerable == p.id) s.maniaVulnerable = -1
    }

    private fun penalty(victim: PlayerState, n: Int, reason: DrawReason) {
        if (n <= 0 || victim.eliminated) return
        if (victim.shield) {
            victim.shield = false
            record(GameEvent.ShieldBlocked(victim.id, n))
            return
        }
        val got = ArrayList<Card>(n)
        repeat(n) {
            val c = takeOne() ?: return@repeat
            victim.hand.add(c)
            got += c
        }
        if (got.isNotEmpty()) {
            record(GameEvent.CardsDrawn(victim.id, got, reason))
            victim.maniaCalled = false
            if (state.maniaVulnerable == victim.id) state.maniaVulnerable = -1
        }
        checkOverload(victim)
    }

    private fun reveal(victim: PlayerState, suit: Suit, reason: DrawReason) {
        val s = state
        if (victim.shield) {
            victim.shield = false
            record(GameEvent.ShieldBlocked(victim.id, 0))
            return
        }
        val got = ArrayList<Card>()
        val limit = config.overloadLimit
        while (true) {
            val c = takeOne() ?: break
            victim.hand.add(c)
            got += c
            if (c.face(s.side).suit == suit) break
            if (limit > 0 && victim.hand.size >= limit) break
        }
        if (got.isNotEmpty()) {
            record(GameEvent.CardsDrawn(victim.id, got, reason))
            victim.maniaCalled = false
            if (s.maniaVulnerable == victim.id) s.maniaVulnerable = -1
        }
        checkOverload(victim)
    }

    private fun takeOne(): Card? {
        val s = state
        if (s.drawPile.isEmpty() && !reshuffle()) return null
        return s.drawPile.removeAt(s.drawPile.lastIndex)
    }

    private fun reshuffle(): Boolean {
        val s = state
        if (s.discard.size <= 1) return false
        val top = s.discard.removeAt(s.discard.lastIndex)
        val rest = s.discard
        rng.shuffle(rest)
        s.drawPile.addAll(0, rest)
        s.discard = mutableListOf(top)
        record(GameEvent.Reshuffled(rest.size))
        return true
    }

    private fun checkOverload(p: PlayerState): Boolean {
        val s = state
        val limit = config.overloadLimit
        if (limit <= 0 || p.eliminated || p.hand.size < limit) return false
        p.eliminated = true
        val cards = p.hand.toList()
        p.hand.clear()
        for (c in cards) s.drawPile.add(rng.nextInt(s.drawPile.size + 1), c)
        if (s.maniaVulnerable == p.id) s.maniaVulnerable = -1
        record(GameEvent.PlayerEliminated(p.id, cards.size))
        if (s.activeCount == 1) endRound(s.activePlayers.first().id, lastStanding = true)
        return true
    }

    private fun rotateHands() {
        val s = state
        val active = s.activePlayers
        val hands = active.associate { it.id to it.hand.toList() }
        val newHands = HashMap<Int, List<Card>>()
        for (pl in active) newHands[s.nextActive(pl.id)] = hands.getValue(pl.id)
        for (pl in active) {
            pl.hand.clear()
            pl.hand.addAll(newHands.getValue(pl.id))
            pl.voidMask = 0
            pl.maniaCalled = false
        }
        s.maniaVulnerable = -1
        record(GameEvent.HandsRotated(s.direction))
    }

    private fun flip() {
        val s = state
        s.side = s.side.other
        s.drawPile.reverse()
        s.players.forEach { it.voidMask = 0 }
        record(GameEvent.SideFlipped(s.side))
    }

    private fun endTurn(steps: Int) {
        val s = state
        if (s.isOver) return
        var idx = s.current
        repeat(steps) { idx = s.nextActive(idx) }
        if (s.players[idx].eliminated) idx = s.nextActive(idx)
        s.current = idx
        s.phase = Phase.Play
        s.turnCount++
        if (s.turnCount > MAX_TURNS) {
            deadlockEnd()
            return
        }
        record(GameEvent.TurnChanged(idx))
    }

    private fun deadlockEnd() {
        val s = state
        val winner = s.activePlayers.minBy { s.handPoints(it) * 1000 + it.hand.size }
        endRound(winner.id, deadlock = true)
    }

    private fun endRound(winnerId: Int, lastStanding: Boolean = false, deadlock: Boolean = false) {
        val s = state
        if (s.isOver) return
        val w = s.players[winnerId]
        var pts = s.players.filter { it.id != winnerId }.sumOf { s.handPoints(it) }
        if (lastStanding) pts += 50 * s.players.count { it.eliminated }
        w.score += pts
        s.maniaVulnerable = -1
        s.pendingDraw = 0
        s.pendingMin = 0
        val matchOver = config.targetScore <= 0 || w.score >= config.targetScore
        s.phase = if (matchOver) Phase.MatchOver(winnerId, pts) else Phase.RoundOver(winnerId, pts)
        record(GameEvent.RoundEnded(winnerId, pts, lastStanding, deadlock))
        if (matchOver) record(GameEvent.MatchEnded(winnerId))
    }

    private fun record(e: GameEvent) {
        state.rngState = rng.state
        steps += Step(e, if (recordSnapshots) state.copyDeep() else null)
    }
}
