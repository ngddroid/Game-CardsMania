package com.ngdstudio.mindimania.ui.game

import com.ngdstudio.mindimania.engine.GameEvent

/** How long each event's animation takes (ms) at normal speed. Shared by the ViewModel pump and the canvas tweens. */
object Timing {
    const val DEAL_STAGGER = 55L
    const val DRAW_STAGGER = 90L

    fun speedFactor(speed: Int): Float = when (speed) {
        0 -> 1.35f
        2 -> 0.62f
        else -> 1f
    }

    fun base(e: GameEvent): Long = when (e) {
        is GameEvent.RoundStarted -> 750
        is GameEvent.Dealt -> DEAL_STAGGER * e.order.size + 480
        is GameEvent.StarterFlipped -> 720
        is GameEvent.StarterReturned -> 300
        is GameEvent.CardPlayed -> 470L + e.extras.size * 120L
        is GameEvent.CardsDrawn -> 300L + DRAW_STAGGER * minOf(e.cards.size, 20)
        is GameEvent.Reshuffled -> 900
        is GameEvent.ColorChosen -> 650
        is GameEvent.DirectionChanged -> 450
        is GameEvent.Blocked -> 560
        is GameEvent.PlayAgain -> 450
        is GameEvent.TurnChanged -> 140
        is GameEvent.StackGrew -> 430
        is GameEvent.HandsSwapped -> 900
        is GameEvent.HandsRotated -> 1000
        is GameEvent.Purged -> 520L + 80L * e.cards.size
        is GameEvent.PlayerEliminated -> 1450
        is GameEvent.SideFlipped -> 1550
        is GameEvent.ManiaCalled -> 700
        is GameEvent.Caught -> 850
        is GameEvent.ChallengeResult -> 1050
        is GameEvent.NoChallenge -> 250
        is GameEvent.StormStarted -> 750
        is GameEvent.ShieldArmed -> 500
        is GameEvent.ShieldBlocked -> 850
        is GameEvent.TokenEarned -> 500
        is GameEvent.PeekRevealed -> 80
        is GameEvent.Passed -> 450
        is GameEvent.RoundEnded -> 1400
        is GameEvent.MatchEnded -> 200
    }

    fun of(e: GameEvent, speed: Int): Long = (base(e) * speedFactor(speed)).toLong()
}
