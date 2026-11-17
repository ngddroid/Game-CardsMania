package com.puzzel.lab.cardsmania.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty { EASY, NORMAL, HARD, GENIUS }

@Serializable
data class HouseRules(
    val stacking: Boolean = false,
    val jumpIn: Boolean = false,
    val sevenZero: Boolean = false,
    val drawToMatch: Boolean = false,
    val forcedPlay: Boolean = false,
    val challenge: Boolean = true,
    val comboDrop: Boolean = false,
    val powerUps: Boolean = false,
)

@Serializable
data class PlayerSetup(
    val name: String,
    val avatar: Int,
    val isHuman: Boolean,
    val difficulty: Difficulty = Difficulty.NORMAL,
)

@Serializable
data class GameConfig(
    val mode: GameMode,
    val players: List<PlayerSetup>,
    val handSize: Int = 7,
    val deckCount: Int = 1,
    /** 0 = single round. */
    val targetScore: Int = 0,
    val rules: HouseRules = HouseRules(),
    val seed: Long = 0L,
) {
    val stacking: Boolean get() = mode == GameMode.CHAOS || rules.stacking
    val sevenZero: Boolean get() = mode == GameMode.CHAOS || rules.sevenZero
    val drawToMatch: Boolean get() = mode == GameMode.CHAOS || rules.drawToMatch
    val challenge: Boolean get() = mode != GameMode.CHAOS && rules.challenge && !stacking
    val jumpIn: Boolean get() = rules.jumpIn
    val comboDrop: Boolean get() = rules.comboDrop
    val powerUps: Boolean get() = rules.powerUps
    val forcedPlay: Boolean get() = rules.forcedPlay
    val overloadLimit: Int get() = if (mode == GameMode.CHAOS) 25 else 0
}

@Serializable
class PlayerState(
    val id: Int,
    val setup: PlayerSetup,
    val hand: MutableList<Card> = mutableListOf(),
    var eliminated: Boolean = false,
    var score: Int = 0,
    var maniaCalled: Boolean = false,
    var meter: Int = 0,
    var tokens: Int = 0,
    var shield: Boolean = false,
    /** Bit mask of suits (ordinal) this player is believed not to hold (public information). */
    var voidMask: Int = 0,
) {
    val name: String get() = setup.name
    val isHuman: Boolean get() = setup.isHuman

    fun copyDeep() = PlayerState(
        id, setup, hand.toMutableList(), eliminated, score, maniaCalled, meter, tokens, shield, voidMask
    )
}

@Serializable
enum class ColorReason { START, WILD, FLIP, STORM }

@Serializable
sealed interface Phase {
    @Serializable
    @SerialName("play")
    data object Play : Phase

    @Serializable
    @SerialName("color")
    data class ChooseColor(val playerId: Int, val reason: ColorReason) : Phase

    @Serializable
    @SerialName("swap")
    data class ChooseSwap(val playerId: Int) : Phase

    @Serializable
    @SerialName("challenge")
    data class Challenge(val victimId: Int, val offenderId: Int, val illegal: Boolean, val amount: Int) : Phase

    @Serializable
    @SerialName("drawn")
    data class DrawnCard(val playerId: Int, val cardId: Int) : Phase

    @Serializable
    @SerialName("roundOver")
    data class RoundOver(val winnerId: Int, val points: Int) : Phase

    @Serializable
    @SerialName("matchOver")
    data class MatchOver(val winnerId: Int, val points: Int) : Phase
}

@Serializable
class GameState(
    val config: GameConfig,
    val players: List<PlayerState>,
    var drawPile: MutableList<Card> = mutableListOf(),
    var discard: MutableList<Card> = mutableListOf(),
    var color: Suit = Suit.WILD,
    var side: Side = Side.SUN,
    var current: Int = 0,
    var direction: Int = 1,
    var dealer: Int = 0,
    var pendingDraw: Int = 0,
    var pendingMin: Int = 0,
    var phase: Phase = Phase.Play,
    var round: Int = 0,
    var maniaVulnerable: Int = -1,
    var rngState: Long = 0L,
    var idlePasses: Int = 0,
    var turnCount: Int = 0,
    var biggestStack: Int = 0,
    /** True when the Prism draw card just played was played while holding the active color (for challenges). */
    var lastPlayIllegal: Boolean = false,
) {
    val mode: GameMode get() = config.mode
    val top: Card get() = discard.last()
    val topFace: Face get() = discard.last().face(side)
    val activePlayers: List<PlayerState> get() = players.filter { !it.eliminated }
    val activeCount: Int get() = players.count { !it.eliminated }
    val isOver: Boolean get() = phase is Phase.RoundOver || phase is Phase.MatchOver

    fun nextActive(from: Int, dir: Int = direction): Int {
        val n = players.size
        var j = from
        repeat(n) {
            j = ((j + dir) % n + n) % n
            if (!players[j].eliminated) return j
        }
        return from
    }

    /** The player whose input the game is waiting for. */
    val actingPlayer: Int
        get() = when (val p = phase) {
            is Phase.ChooseColor -> p.playerId
            is Phase.ChooseSwap -> p.playerId
            is Phase.Challenge -> p.victimId
            is Phase.DrawnCard -> p.playerId
            else -> current
        }

    fun copyDeep(): GameState = GameState(
        config, players.map { it.copyDeep() }, drawPile.toMutableList(), discard.toMutableList(),
        color, side, current, direction, dealer, pendingDraw, pendingMin, phase, round, maniaVulnerable,
        rngState, idlePasses, turnCount, biggestStack, lastPlayIllegal
    )

    fun handPoints(p: PlayerState): Int = p.hand.sumOf { it.face(side).points(mode) }
}
