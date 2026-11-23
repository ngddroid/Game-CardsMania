package com.ngdstudio.mindimania.engine

import com.ngdstudio.mindimania.engine.model.Card
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.Side
import com.ngdstudio.mindimania.engine.model.Suit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Action {
    val playerId: Int

    /** Play [cardId] (plus [extraIds] for Combo Drop). [callMania] = player called MANIA! while playing. */
    @Serializable
    @SerialName("Play")
    data class Play(
        override val playerId: Int,
        val cardId: Int,
        val extraIds: List<Int> = emptyList(),
        val callMania: Boolean = false,
    ) : Action

    @Serializable
    @SerialName("Draw")
    data class Draw(override val playerId: Int) : Action
    @Serializable
    @SerialName("Keep")
    data class Keep(override val playerId: Int) : Action
    @Serializable
    @SerialName("ChooseColor")
    data class ChooseColor(override val playerId: Int, val suit: Suit) : Action
    @Serializable
    @SerialName("ChooseSwap")
    data class ChooseSwap(override val playerId: Int, val targetId: Int) : Action
    @Serializable
    @SerialName("Challenge")
    data class Challenge(override val playerId: Int, val challenge: Boolean) : Action
    @Serializable
    @SerialName("CallMania")
    data class CallMania(override val playerId: Int) : Action
    @Serializable
    @SerialName("Catch")
    data class Catch(override val playerId: Int, val targetId: Int) : Action
    @Serializable
    @SerialName("ArmShield")
    data class ArmShield(override val playerId: Int) : Action
    @Serializable
    @SerialName("Peek")
    data class Peek(override val playerId: Int) : Action
    @Serializable
    @SerialName("NextRound")
    data class NextRound(override val playerId: Int) : Action
}

@Serializable
enum class DrawReason { NORMAL, MATCH, PENALTY, STACK, STORM, HUNT, CATCH, CHALLENGE, START }

@Serializable
sealed interface GameEvent {
    @Serializable
    @SerialName("RoundStarted")
    data class RoundStarted(val round: Int, val dealer: Int) : GameEvent
    @Serializable
    @SerialName("Dealt")
    data class Dealt(val order: List<Pair<Int, Card>>) : GameEvent
    @Serializable
    @SerialName("StarterFlipped")
    data class StarterFlipped(val card: Card) : GameEvent
    @Serializable
    @SerialName("StarterReturned")
    data class StarterReturned(val card: Card) : GameEvent
    @Serializable
    @SerialName("CardPlayed")
    data class CardPlayed(val playerId: Int, val card: Card, val extras: List<Card>, val jumpIn: Boolean) : GameEvent
    @Serializable
    @SerialName("CardsDrawn")
    data class CardsDrawn(val playerId: Int, val cards: List<Card>, val reason: DrawReason) : GameEvent
    @Serializable
    @SerialName("Reshuffled")
    data class Reshuffled(val count: Int) : GameEvent
    @Serializable
    @SerialName("ColorChosen")
    data class ColorChosen(val playerId: Int, val suit: Suit) : GameEvent
    @Serializable
    @SerialName("DirectionChanged")
    data class DirectionChanged(val direction: Int) : GameEvent
    @Serializable
    @SerialName("Blocked")
    data class Blocked(val playerIds: List<Int>) : GameEvent
    @Serializable
    @SerialName("PlayAgain")
    data class PlayAgain(val playerId: Int) : GameEvent
    @Serializable
    @SerialName("TurnChanged")
    data class TurnChanged(val playerId: Int) : GameEvent
    @Serializable
    @SerialName("StackGrew")
    data class StackGrew(val total: Int, val by: Int) : GameEvent
    @Serializable
    @SerialName("HandsSwapped")
    data class HandsSwapped(val a: Int, val b: Int) : GameEvent
    @Serializable
    @SerialName("HandsRotated")
    data class HandsRotated(val direction: Int) : GameEvent
    @Serializable
    @SerialName("Purged")
    data class Purged(val playerId: Int, val cards: List<Card>) : GameEvent
    @Serializable
    @SerialName("PlayerEliminated")
    data class PlayerEliminated(val playerId: Int, val cardCount: Int) : GameEvent
    @Serializable
    @SerialName("SideFlipped")
    data class SideFlipped(val side: Side) : GameEvent
    @Serializable
    @SerialName("ManiaCalled")
    data class ManiaCalled(val playerId: Int) : GameEvent
    @Serializable
    @SerialName("Caught")
    data class Caught(val catcherId: Int, val targetId: Int) : GameEvent
    @Serializable
    @SerialName("ChallengeResult")
    data class ChallengeResult(val challengerId: Int, val offenderId: Int, val success: Boolean) : GameEvent
    @Serializable
    @SerialName("NoChallenge")
    data class NoChallenge(val victimId: Int) : GameEvent
    @Serializable
    @SerialName("StormStarted")
    data class StormStarted(val victimId: Int) : GameEvent
    @Serializable
    @SerialName("ShieldArmed")
    data class ShieldArmed(val playerId: Int) : GameEvent
    @Serializable
    @SerialName("ShieldBlocked")
    data class ShieldBlocked(val playerId: Int, val amount: Int) : GameEvent
    @Serializable
    @SerialName("TokenEarned")
    data class TokenEarned(val playerId: Int) : GameEvent
    @Serializable
    @SerialName("PeekRevealed")
    data class PeekRevealed(val playerId: Int, val cards: List<Card>) : GameEvent
    @Serializable
    @SerialName("Passed")
    data class Passed(val playerId: Int) : GameEvent
    @Serializable
    @SerialName("RoundEnded")
    data class RoundEnded(val winnerId: Int, val points: Int, val lastStanding: Boolean, val deadlock: Boolean) : GameEvent
    @Serializable
    @SerialName("MatchEnded")
    data class MatchEnded(val winnerId: Int) : GameEvent
}

/** An event plus the state right after it (null when snapshots are disabled, e.g. inside AI rollouts). */
class Step(val event: GameEvent, val snapshot: GameState?)
