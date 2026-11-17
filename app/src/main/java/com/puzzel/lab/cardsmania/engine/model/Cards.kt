package com.puzzel.lab.cardsmania.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameMode { CLASSIC, CHAOS, ECLIPSE }

@Serializable
enum class Side {
    SUN, MOON;

    val other: Side get() = if (this == SUN) MOON else SUN
}

/** Gem suits. Sun suits are used by Classic, Chaos and the Sun side of Eclipse; Moon suits by Eclipse's Moon side. */
@Serializable
enum class Suit(val isMoon: Boolean) {
    RUBY(false), SAPPHIRE(false), EMERALD(false), AMBER(false),
    ROSE(true), AQUA(true), TANGERINE(true), VIOLET(true),
    WILD(false);

    val isWild: Boolean get() = this == WILD

    companion object {
        val SUN_SUITS = listOf(RUBY, SAPPHIRE, EMERALD, AMBER)
        val MOON_SUITS = listOf(ROSE, AQUA, TANGERINE, VIOLET)
        fun forSide(side: Side) = if (side == Side.SUN) SUN_SUITS else MOON_SUITS
    }
}

@Serializable
enum class Kind {
    NUMBER, BLOCK, REVERSE, DRAW, BLOCK_ALL, PURGE, ECLIPSE,
    PRISM, PRISM_DRAW, PRISM_REBOUND, COLOR_STORM, PRISM_HUNT;

    val isWildKind: Boolean get() = ordinal >= PRISM.ordinal
}

@Serializable
data class Face(val suit: Suit, val kind: Kind, val value: Int = 0) {
    val isWild: Boolean get() = suit == Suit.WILD

    private fun numberWord(n: Int) = when (n) {
        1 -> "One"; 2 -> "Two"; 4 -> "Four"; 5 -> "Five"; 6 -> "Six"; 10 -> "Ten"; else -> n.toString()
    }

    /** Cards added to a stack / penalty. */
    val drawAmount: Int
        get() = when (kind) {
            Kind.DRAW, Kind.PRISM_DRAW -> value
            Kind.PRISM_REBOUND -> 4
            else -> 0
        }

    val isDraw: Boolean get() = drawAmount > 0

    val isAction: Boolean get() = kind != Kind.NUMBER

    /** Same symbol: same number, or same action (draw cards must also have the same amount). */
    fun sameSymbol(other: Face): Boolean {
        if (kind != other.kind) return false
        return when (kind) {
            Kind.NUMBER, Kind.DRAW, Kind.PRISM_DRAW -> value == other.value
            else -> true
        }
    }

    fun points(mode: GameMode): Int = when (kind) {
        Kind.NUMBER -> value
        Kind.DRAW -> when (value) {
            1 -> 10
            4 -> 30
            else -> 20
        }
        Kind.BLOCK, Kind.REVERSE, Kind.PURGE, Kind.ECLIPSE -> 20
        Kind.BLOCK_ALL -> 30
        Kind.PRISM -> if (mode == GameMode.ECLIPSE) 40 else 50
        Kind.PRISM_DRAW -> when (value) {
            6 -> 60
            10 -> 100
            else -> 50
        }
        Kind.PRISM_REBOUND, Kind.COLOR_STORM -> 50
        Kind.PRISM_HUNT -> 60
    }

    /** Short label for corners / accessibility. */
    val shortLabel: String
        get() = when (kind) {
            Kind.NUMBER -> value.toString()
            Kind.DRAW, Kind.PRISM_DRAW -> "+$value"
            Kind.PRISM_REBOUND -> "+4"
            else -> ""
        }

    val title: String
        get() = when (kind) {
            Kind.NUMBER -> value.toString()
            Kind.BLOCK -> "Skip"
            Kind.REVERSE -> "Reverse"
            Kind.DRAW -> "Draw ${numberWord(value)}"
            Kind.BLOCK_ALL -> "Skip Everyone"
            Kind.PURGE -> "Discard All"
            Kind.ECLIPSE -> "Flip"
            Kind.PRISM -> "Wild"
            Kind.PRISM_DRAW -> "Wild Draw ${numberWord(value)}"
            Kind.PRISM_REBOUND -> "Wild Reverse Draw Four"
            Kind.COLOR_STORM -> "Wild Color Storm"
            Kind.PRISM_HUNT -> "Wild Draw Color"
        }

    val description: String
        get() = when (kind) {
            Kind.NUMBER -> "Match by color or number."
            Kind.BLOCK -> "The next player loses their turn."
            Kind.REVERSE -> "Flips the direction of play. With 2 players it acts as Skip."
            Kind.DRAW -> "The next player draws $value and loses their turn."
            Kind.BLOCK_ALL -> "Every other player is skipped — you play again."
            Kind.PURGE -> "Also discard every other card of this color from your hand."
            Kind.ECLIPSE -> "Flips the whole game to the other side (Light ⇄ Dark)."
            Kind.PRISM -> "Choose the next color. Play it any time."
            Kind.PRISM_DRAW -> "Choose a color. The next player draws $value and loses their turn."
            Kind.PRISM_REBOUND -> "Choose a color, reverse direction, and the next player draws 4."
            Kind.COLOR_STORM -> "The next player names a color and reveals cards until it appears — keeping them all."
            Kind.PRISM_HUNT -> "Choose a color. The next player draws until they find it."
        }
}

/** A physical card. Eclipse cards have two faces. */
@Serializable
data class Card(val id: Int, val sun: Face, val moon: Face? = null) {
    fun face(side: Side): Face = if (side == Side.MOON && moon != null) moon else sun
}
