package com.puzzel.lab.cardsmania.engine.model

import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.i18n.Strings
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
        1 -> Strings.get(R.string.data_face_num_one)
        2 -> Strings.get(R.string.data_face_num_two)
        4 -> Strings.get(R.string.data_face_num_four)
        5 -> Strings.get(R.string.data_face_num_five)
        6 -> Strings.get(R.string.data_face_num_six)
        10 -> Strings.get(R.string.data_face_num_ten)
        else -> n.toString()
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
            Kind.BLOCK -> Strings.get(R.string.data_face_skip)
            Kind.REVERSE -> Strings.get(R.string.data_face_reverse)
            Kind.DRAW -> Strings.get(R.string.data_face_draw, numberWord(value))
            Kind.BLOCK_ALL -> Strings.get(R.string.data_face_skip_all)
            Kind.PURGE -> Strings.get(R.string.data_face_discard_all)
            Kind.ECLIPSE -> Strings.get(R.string.data_face_flip)
            Kind.PRISM -> Strings.get(R.string.data_face_wild)
            Kind.PRISM_DRAW -> Strings.get(R.string.data_face_wild_draw, numberWord(value))
            Kind.PRISM_REBOUND -> Strings.get(R.string.data_face_wild_reverse_draw_four)
            Kind.COLOR_STORM -> Strings.get(R.string.data_face_wild_color_storm)
            Kind.PRISM_HUNT -> Strings.get(R.string.data_face_wild_draw_color)
        }

    val description: String
        get() = when (kind) {
            Kind.NUMBER -> Strings.get(R.string.data_face_desc_number)
            Kind.BLOCK -> Strings.get(R.string.data_face_desc_skip)
            Kind.REVERSE -> Strings.get(R.string.data_face_desc_reverse)
            Kind.DRAW -> Strings.get(R.string.data_face_desc_draw, value)
            Kind.BLOCK_ALL -> Strings.get(R.string.data_face_desc_skip_all)
            Kind.PURGE -> Strings.get(R.string.data_face_desc_discard_all)
            Kind.ECLIPSE -> Strings.get(R.string.data_face_desc_flip)
            Kind.PRISM -> Strings.get(R.string.data_face_desc_wild)
            Kind.PRISM_DRAW -> Strings.get(R.string.data_face_desc_wild_draw, value)
            Kind.PRISM_REBOUND -> Strings.get(R.string.data_face_desc_wild_reverse_draw_four)
            Kind.COLOR_STORM -> Strings.get(R.string.data_face_desc_wild_color_storm)
            Kind.PRISM_HUNT -> Strings.get(R.string.data_face_desc_wild_draw_color)
        }
}

/** A physical card. Eclipse cards have two faces. */
@Serializable
data class Card(val id: Int, val sun: Face, val moon: Face? = null) {
    fun face(side: Side): Face = if (side == Side.MOON && moon != null) moon else sun
}
