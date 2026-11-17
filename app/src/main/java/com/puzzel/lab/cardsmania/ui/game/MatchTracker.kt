package com.puzzel.lab.cardsmania.ui.game

import com.puzzel.lab.cardsmania.data.MatchSummary
import com.puzzel.lab.cardsmania.engine.DrawReason
import com.puzzel.lab.cardsmania.engine.GameEvent
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.Kind

/** Collects the human's match statistics from the event stream. */
class MatchTracker(private val human: Int) {
    var humanTurns = 0
    var maxHand = 0
    var cardsPlayed = 0
    var actionsPlayed = 0
    var prismsPlayed = 0
    var maniaCalls = 0
    var catches = 0
    var knockouts = 0
    var biggestStackHit = 0
    var biggestStackSeen = 0
    var eclipses = 0
    var maxPurge = 0
    var swaps = 0
    var forced = 0
    var lastStanding = false
    private var lastAttacker = -1

    fun onEvent(e: GameEvent, s: GameState) {
        maxHand = maxOf(maxHand, s.players[human].hand.size)
        when (e) {
            is GameEvent.TurnChanged -> if (e.playerId == human) humanTurns++
            is GameEvent.CardPlayed -> {
                val all = listOf(e.card) + e.extras
                val face = e.card.face(s.side)
                if (face.isDraw || face.kind == Kind.COLOR_STORM || face.kind == Kind.PRISM_HUNT || face.kind == Kind.ECLIPSE) lastAttacker = e.playerId
                if (e.playerId == human) {
                    cardsPlayed += all.size
                    if (face.isAction) actionsPlayed++
                    if (face.isWild) prismsPlayed++
                    // CardPlayed is recorded before the flip, so the snapshot's side is the side it was played on.
                    if (face.kind == Kind.ECLIPSE) eclipses++
                }
            }
            is GameEvent.SideFlipped -> Unit
            is GameEvent.StackGrew -> biggestStackSeen = maxOf(biggestStackSeen, e.total)
            is GameEvent.CardsDrawn -> {
                val penalty = e.reason == DrawReason.PENALTY || e.reason == DrawReason.STACK || e.reason == DrawReason.STORM ||
                    e.reason == DrawReason.HUNT || e.reason == DrawReason.CHALLENGE
                if (penalty) biggestStackSeen = maxOf(biggestStackSeen, e.cards.size)
                if (penalty && e.playerId != human && lastAttacker == human) {
                    forced += e.cards.size
                    biggestStackHit = maxOf(biggestStackHit, e.cards.size)
                }
            }
            is GameEvent.ManiaCalled -> if (e.playerId == human) maniaCalls++
            is GameEvent.Caught -> if (e.catcherId == human) catches++
            is GameEvent.PlayerEliminated -> if (e.playerId != human && lastAttacker == human) knockouts++
            is GameEvent.Purged -> if (e.playerId == human) maxPurge = maxOf(maxPurge, e.cards.size + 1)
            is GameEvent.HandsSwapped -> {
                if (e.a == human) swaps++
                lastAttacker = e.a
            }
            is GameEvent.RoundEnded -> if (e.winnerId == human && e.lastStanding) lastStanding = true
            else -> Unit
        }
    }

    fun summary(s: GameState, won: Boolean, daily: String?): MatchSummary {
        val me = s.players[human]
        val place = if (s.config.targetScore > 0) {
            1 + s.players.count { it.id != human && it.score > me.score }
        } else if (won) 1 else {
            // rank non-winners by remaining hand points; eliminated players rank last
            val winner = (s.phase as? com.puzzel.lab.cardsmania.engine.model.Phase.MatchOver)?.winnerId ?: -1
            val others = s.players.filter { it.id != winner }
                .sortedWith(compareBy({ it.eliminated }, { s.handPoints(it) }))
            1 + 1 + others.indexOfFirst { it.id == human }.coerceAtLeast(0)
        }
        val bots = s.players.filter { !it.isHuman }.map { it.setup.difficulty }
        return MatchSummary(
            mode = s.mode,
            won = won,
            place = place,
            players = s.players.size,
            points = me.score,
            humanTurns = humanTurns,
            maxHand = maxHand,
            cardsPlayed = cardsPlayed,
            actionsPlayed = actionsPlayed,
            prismsPlayed = prismsPlayed,
            maniaCalls = maniaCalls,
            catches = catches,
            knockouts = knockouts,
            biggestStackHit = biggestStackHit,
            biggestStackSeen = biggestStackSeen,
            eclipses = eclipses,
            maxPurge = maxPurge,
            swaps = swaps,
            cardsForcedOnRivals = forced,
            lastStanding = lastStanding,
            hardestBeaten = if (won) bots.maxOrNull() ?: Difficulty.EASY else null,
            daily = daily,
        )
    }
}
