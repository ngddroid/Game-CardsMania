package com.puzzel.lab.cardsmania.engine.model

import com.puzzel.lab.cardsmania.engine.Rng

object DeckFactory {

    fun classicFaces(): List<Face> = buildList {
        for (s in Suit.SUN_SUITS) {
            add(Face(s, Kind.NUMBER, 0))
            for (n in 1..9) repeat(2) { add(Face(s, Kind.NUMBER, n)) }
            repeat(2) { add(Face(s, Kind.BLOCK)) }
            repeat(2) { add(Face(s, Kind.REVERSE)) }
            repeat(2) { add(Face(s, Kind.DRAW, 2)) }
        }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM)) }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM_DRAW, 4)) }
    }

    fun chaosFaces(): List<Face> = buildList {
        for (s in Suit.SUN_SUITS) {
            for (n in 0..9) repeat(2) { add(Face(s, Kind.NUMBER, n)) }
            repeat(3) { add(Face(s, Kind.BLOCK)) }
            repeat(3) { add(Face(s, Kind.REVERSE)) }
            repeat(3) { add(Face(s, Kind.DRAW, 2)) }
            repeat(2) { add(Face(s, Kind.DRAW, 4)) }
            repeat(3) { add(Face(s, Kind.PURGE)) }
            repeat(2) { add(Face(s, Kind.BLOCK_ALL)) }
        }
        repeat(8) { add(Face(Suit.WILD, Kind.PRISM_REBOUND, 4)) }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM_DRAW, 6)) }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM_DRAW, 10)) }
        repeat(8) { add(Face(Suit.WILD, Kind.COLOR_STORM)) }
    }

    fun eclipseSunFaces(): List<Face> = buildList {
        for (s in Suit.SUN_SUITS) {
            for (n in 1..9) repeat(2) { add(Face(s, Kind.NUMBER, n)) }
            repeat(2) { add(Face(s, Kind.DRAW, 1)) }
            repeat(2) { add(Face(s, Kind.REVERSE)) }
            repeat(2) { add(Face(s, Kind.BLOCK)) }
            repeat(2) { add(Face(s, Kind.ECLIPSE)) }
        }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM)) }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM_DRAW, 2)) }
    }

    fun eclipseMoonFaces(): List<Face> = buildList {
        for (s in Suit.MOON_SUITS) {
            for (n in 1..9) repeat(2) { add(Face(s, Kind.NUMBER, n)) }
            repeat(2) { add(Face(s, Kind.DRAW, 5)) }
            repeat(2) { add(Face(s, Kind.REVERSE)) }
            repeat(2) { add(Face(s, Kind.BLOCK_ALL)) }
            repeat(2) { add(Face(s, Kind.ECLIPSE)) }
        }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM)) }
        repeat(4) { add(Face(Suit.WILD, Kind.PRISM_HUNT)) }
    }

    fun deckSize(mode: GameMode): Int = when (mode) {
        GameMode.CLASSIC -> 108
        GameMode.CHAOS -> 168
        GameMode.ECLIPSE -> 112
    }

    /** Builds [decks] copies of the deck for [mode]. Eclipse faces are paired randomly (our twist). Not shuffled. */
    fun build(mode: GameMode, decks: Int, rng: Rng): MutableList<Card> {
        val cards = ArrayList<Card>()
        var id = 0
        repeat(decks.coerceIn(1, 2)) {
            when (mode) {
                GameMode.CLASSIC -> classicFaces().forEach { cards += Card(id++, it) }
                GameMode.CHAOS -> chaosFaces().forEach { cards += Card(id++, it) }
                GameMode.ECLIPSE -> {
                    val moons = eclipseMoonFaces().toMutableList()
                    rng.shuffle(moons)
                    eclipseSunFaces().forEachIndexed { i, sun -> cards += Card(id++, sun, moons[i]) }
                }
            }
        }
        return cards
    }

    /** Every distinct face used by a mode (for pre-rendering card art). */
    fun distinctFaces(mode: GameMode): List<Face> = when (mode) {
        GameMode.CLASSIC -> classicFaces().distinct()
        GameMode.CHAOS -> chaosFaces().distinct()
        GameMode.ECLIPSE -> (eclipseSunFaces() + eclipseMoonFaces()).distinct()
    }
}
