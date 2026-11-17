package com.puzzel.lab.cardsmania.engine

import com.puzzel.lab.cardsmania.engine.model.Card
import com.puzzel.lab.cardsmania.engine.model.ColorReason
import com.puzzel.lab.cardsmania.engine.model.DeckFactory
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.HouseRules
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.PlayerSetup
import com.puzzel.lab.cardsmania.engine.model.PlayerState
import com.puzzel.lab.cardsmania.engine.model.Side
import com.puzzel.lab.cardsmania.engine.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRulesTest {

    private var nextId = 1000
    private fun c(suit: Suit, kind: Kind, value: Int = 0, moon: Face? = null) = Card(nextId++, Face(suit, kind, value), moon)
    private fun n(suit: Suit, v: Int) = c(suit, Kind.NUMBER, v)

    private fun state(
        mode: GameMode,
        hands: List<List<Card>>,
        top: Card,
        rules: HouseRules = HouseRules(),
        draw: List<Card> = List(40) { n(Suit.AMBER, it % 10) },
        current: Int = 0,
        side: Side = Side.SUN,
    ): GameEngine {
        val config = GameConfig(mode, hands.indices.map { PlayerSetup("P$it", 0, it == 0) }, rules = rules, seed = 7)
        val players = hands.mapIndexed { i, h -> PlayerState(i, config.players[i], h.toMutableList()) }
        val s = GameState(
            config, players, draw.toMutableList(), mutableListOf(top),
            color = top.face(side).suit, side = side, current = current, rngState = 1
        )
        return GameEngine(s)
    }

    @Test
    fun deckCompositions() {
        assertEquals(108, DeckFactory.classicFaces().size)
        assertEquals(168, DeckFactory.chaosFaces().size)
        assertEquals(112, DeckFactory.eclipseSunFaces().size)
        assertEquals(112, DeckFactory.eclipseMoonFaces().size)
        val classic = DeckFactory.classicFaces()
        assertEquals(8, classic.count { it.kind == Kind.PRISM || it.kind == Kind.PRISM_DRAW })
        assertEquals(4, classic.count { it.kind == Kind.NUMBER && it.value == 0 })
        val chaos = DeckFactory.chaosFaces()
        assertEquals(24, chaos.count { it.isWild })
        assertEquals(12, chaos.count { it.kind == Kind.PURGE })
        assertEquals(0, DeckFactory.eclipseSunFaces().count { it.kind == Kind.NUMBER && it.value == 0 })
        val eclipse = DeckFactory.build(GameMode.ECLIPSE, 1, Rng(3))
        assertTrue(eclipse.all { it.moon != null && it.moon!!.suit.isMoon || it.moon!!.isWild })
        assertEquals(224, DeckFactory.build(GameMode.ECLIPSE, 2, Rng(3)).size)
        assertEquals(eclipse.size, eclipse.map { it.id }.toSet().size)
    }

    @Test
    fun matchingByColorNumberSymbol() {
        val e = state(GameMode.CLASSIC, listOf(listOf(n(Suit.RUBY, 3), n(Suit.SAPPHIRE, 5), c(Suit.EMERALD, Kind.BLOCK), c(Suit.WILD, Kind.PRISM)), listOf(n(Suit.AMBER, 1))), n(Suit.SAPPHIRE, 3))
        val playable = e.playableCards(0).map { it.face(Side.SUN) }
        assertTrue(Face(Suit.RUBY, Kind.NUMBER, 3) in playable)
        assertTrue(Face(Suit.SAPPHIRE, Kind.NUMBER, 5) in playable)
        assertTrue(Face(Suit.WILD, Kind.PRISM) in playable)
        assertFalse(Face(Suit.EMERALD, Kind.BLOCK) in playable)
    }

    @Test
    fun reverseActsAsBlockWithTwoPlayers() {
        val rev = c(Suit.RUBY, Kind.REVERSE)
        val e = state(GameMode.CLASSIC, listOf(listOf(rev, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1), n(Suit.AMBER, 2))), n(Suit.RUBY, 9))
        e.apply(Action.Play(0, rev.id))
        assertEquals(0, e.state.current)
    }

    @Test
    fun blockSkipsNextPlayer() {
        val b = c(Suit.RUBY, Kind.BLOCK)
        val e = state(GameMode.CLASSIC, listOf(listOf(b, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2))), n(Suit.RUBY, 9))
        e.apply(Action.Play(0, b.id))
        assertEquals(2, e.state.current)
    }

    @Test
    fun classicPlusTwoWithoutStacking() {
        val d = c(Suit.RUBY, Kind.DRAW, 2)
        val e = state(GameMode.CLASSIC, listOf(listOf(d, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2))), n(Suit.RUBY, 9))
        e.apply(Action.Play(0, d.id))
        assertEquals(3, e.state.players[1].hand.size)
        assertEquals(2, e.state.current)
    }

    @Test
    fun stackingPassesAccumulatedTotal() {
        val d1 = c(Suit.RUBY, Kind.DRAW, 2)
        val d2 = c(Suit.SAPPHIRE, Kind.DRAW, 2)
        val e = state(
            GameMode.CLASSIC,
            listOf(listOf(d1, n(Suit.RUBY, 1)), listOf(d2, n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2))),
            n(Suit.RUBY, 9), HouseRules(stacking = true)
        )
        e.apply(Action.Play(0, d1.id))
        assertEquals(2, e.state.pendingDraw)
        // A number can't be played on a pending stack
        assertFalse(e.canPlay(n(Suit.RUBY, 2)))
        e.apply(Action.Play(1, d2.id))
        assertEquals(4, e.state.pendingDraw)
        e.apply(Action.Draw(2))
        assertEquals(5, e.state.players[2].hand.size)
        assertEquals(0, e.state.current)
    }

    @Test
    fun chaosReboundReversesAndStacks() {
        val r = c(Suit.WILD, Kind.PRISM_REBOUND, 4)
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(r, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2)), listOf(n(Suit.AMBER, 3))),
            n(Suit.RUBY, 9)
        )
        e.apply(Action.Play(0, r.id))
        e.apply(Action.ChooseColor(0, Suit.EMERALD))
        assertEquals(-1, e.state.direction)
        assertEquals(3, e.state.current)
        assertEquals(4, e.state.pendingDraw)
    }

    @Test
    fun overloadEliminatesAt25() {
        val big = c(Suit.WILD, Kind.PRISM_DRAW, 10)
        val victimHand = List(20) { n(Suit.AMBER, 5) }
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(big, n(Suit.RUBY, 1)), victimHand, listOf(n(Suit.EMERALD, 2))),
            n(Suit.RUBY, 9), draw = List(60) { n(Suit.SAPPHIRE, 4) }
        )
        e.apply(Action.Play(0, big.id))
        e.apply(Action.ChooseColor(0, Suit.RUBY))
        e.apply(Action.Draw(1))
        assertTrue(e.state.players[1].eliminated)
        assertEquals(0, e.state.players[1].hand.size)
        assertEquals(2, e.state.current)
    }

    @Test
    fun lastStandingWins() {
        val big = c(Suit.WILD, Kind.PRISM_DRAW, 10)
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(big, n(Suit.RUBY, 1)), List(20) { n(Suit.AMBER, 5) }),
            n(Suit.RUBY, 9), draw = List(60) { n(Suit.SAPPHIRE, 4) }
        )
        e.apply(Action.Play(0, big.id))
        e.apply(Action.ChooseColor(0, Suit.RUBY))
        e.apply(Action.Draw(1))
        val ph = e.state.phase
        assertTrue(ph is Phase.MatchOver && ph.winnerId == 0)
    }

    @Test
    fun sevenSwapsAndZeroRotates() {
        val seven = n(Suit.RUBY, 7)
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(seven, n(Suit.RUBY, 1), n(Suit.RUBY, 2)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2), n(Suit.AMBER, 3))),
            n(Suit.RUBY, 9)
        )
        e.apply(Action.Play(0, seven.id))
        assertTrue(e.state.phase is Phase.ChooseSwap)
        e.apply(Action.ChooseSwap(0, 1))
        assertEquals(1, e.state.players[0].hand.size)
        assertEquals(2, e.state.players[1].hand.size)

        val zero = n(Suit.RUBY, 0)
        val a = listOf(zero, n(Suit.RUBY, 1))
        val b = listOf(n(Suit.AMBER, 1), n(Suit.AMBER, 2), n(Suit.AMBER, 3))
        val cc = listOf(n(Suit.EMERALD, 1))
        val e2 = state(GameMode.CHAOS, listOf(a, b, cc), n(Suit.RUBY, 9))
        e2.apply(Action.Play(0, zero.id))
        // direction +1: 0 -> 1 -> 2 -> 0
        assertEquals(1, e2.state.players[0].hand.size) // got cc
        assertEquals(1, e2.state.players[1].hand.size) // got my remaining card
        assertEquals(3, e2.state.players[2].hand.size)
    }

    @Test
    fun purgeDiscardsAllOfColor() {
        val purge = c(Suit.RUBY, Kind.PURGE)
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(purge, n(Suit.RUBY, 1), n(Suit.RUBY, 2), n(Suit.AMBER, 3)), listOf(n(Suit.AMBER, 1))),
            n(Suit.RUBY, 9)
        )
        e.apply(Action.Play(0, purge.id))
        assertEquals(1, e.state.players[0].hand.size)
        assertEquals(Kind.PURGE, e.state.topFace.kind)
    }

    @Test
    fun blockAllPlaysAgain() {
        val ba = c(Suit.RUBY, Kind.BLOCK_ALL)
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(ba, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2))),
            n(Suit.RUBY, 9)
        )
        e.apply(Action.Play(0, ba.id))
        assertEquals(0, e.state.current)
    }

    @Test
    fun colorStormVictimRevealsUntilColor() {
        val storm = c(Suit.WILD, Kind.COLOR_STORM)
        val draw = listOf(n(Suit.EMERALD, 1)) + List(5) { n(Suit.AMBER, 2) } // top of pile is the end
        val e = state(
            GameMode.CHAOS,
            listOf(listOf(storm, n(Suit.RUBY, 1)), listOf(n(Suit.SAPPHIRE, 1)), listOf(n(Suit.AMBER, 2))),
            n(Suit.RUBY, 9), draw = draw
        )
        e.apply(Action.Play(0, storm.id))
        val ph = e.state.phase
        assertTrue(ph is Phase.ChooseColor && ph.playerId == 1 && ph.reason == ColorReason.STORM)
        e.apply(Action.ChooseColor(1, Suit.EMERALD))
        assertEquals(7, e.state.players[1].hand.size)
        assertEquals(2, e.state.current)
        assertEquals(Suit.EMERALD, e.state.color)
    }

    @Test
    fun eclipseFlipsEverything() {
        val ecl = Card(1, Face(Suit.RUBY, Kind.ECLIPSE), Face(Suit.VIOLET, Kind.NUMBER, 4))
        val other = Card(2, Face(Suit.RUBY, Kind.NUMBER, 1), Face(Suit.AQUA, Kind.NUMBER, 2))
        val top = Card(3, Face(Suit.RUBY, Kind.NUMBER, 9), Face(Suit.ROSE, Kind.NUMBER, 9))
        val d1 = Card(4, Face(Suit.AMBER, Kind.NUMBER, 1), Face(Suit.ROSE, Kind.NUMBER, 1))
        val d2 = Card(5, Face(Suit.AMBER, Kind.NUMBER, 2), Face(Suit.ROSE, Kind.NUMBER, 2))
        val e = state(
            GameMode.ECLIPSE,
            listOf(listOf(ecl, other), listOf(Card(6, Face(Suit.AMBER, Kind.NUMBER, 3), Face(Suit.ROSE, Kind.NUMBER, 3)))),
            top, draw = listOf(d1, d2)
        )
        e.apply(Action.Play(0, ecl.id))
        assertEquals(Side.MOON, e.state.side)
        assertEquals(Suit.VIOLET, e.state.color)
        assertEquals(d2.id, e.state.drawPile.first().id) // pile turned over
        assertEquals(1, e.state.current)
    }

    @Test
    fun challengeOutcomes() {
        // Illegal Prism +4 (holding the active color): challenger wins, offender draws 4.
        val p4 = c(Suit.WILD, Kind.PRISM_DRAW, 4)
        val e = state(
            GameMode.CLASSIC,
            listOf(listOf(p4, n(Suit.RUBY, 1), n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2))),
            n(Suit.RUBY, 9)
        )
        e.apply(Action.Play(0, p4.id))
        e.apply(Action.ChooseColor(0, Suit.EMERALD))
        val ph = e.state.phase
        assertTrue(ph is Phase.Challenge && ph.illegal)
        e.apply(Action.Challenge(1, true))
        assertEquals(6, e.state.players[0].hand.size)
        assertEquals(1, e.state.current)

        // Legal play: challenger draws 6 and is skipped.
        val p4b = c(Suit.WILD, Kind.PRISM_DRAW, 4)
        val e2 = state(
            GameMode.CLASSIC,
            listOf(listOf(p4b, n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 1)), listOf(n(Suit.AMBER, 2))),
            n(Suit.RUBY, 9)
        )
        e2.apply(Action.Play(0, p4b.id))
        e2.apply(Action.ChooseColor(0, Suit.EMERALD))
        e2.apply(Action.Challenge(1, true))
        assertEquals(7, e2.state.players[1].hand.size)
        assertEquals(2, e2.state.current)
    }

    @Test
    fun maniaCatchPenalty() {
        val a = n(Suit.RUBY, 2)
        val e = state(GameMode.CLASSIC, listOf(listOf(a, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1), n(Suit.AMBER, 3))), n(Suit.RUBY, 9))
        e.apply(Action.Play(0, a.id))
        assertEquals(0, e.state.maniaVulnerable)
        e.apply(Action.Catch(1, 0))
        assertEquals(3, e.state.players[0].hand.size)

        val b = n(Suit.RUBY, 2)
        val e2 = state(GameMode.CLASSIC, listOf(listOf(b, n(Suit.RUBY, 1)), listOf(n(Suit.AMBER, 1), n(Suit.AMBER, 3))), n(Suit.RUBY, 9))
        val steps = e2.apply(Action.Play(0, b.id, callMania = true))
        assertTrue(steps.any { it.event is GameEvent.ManiaCalled })
        assertEquals(-1, e2.state.maniaVulnerable)
    }

    @Test
    fun winningScoresOpponentHands() {
        val last = n(Suit.RUBY, 2)
        val e = state(
            GameMode.CLASSIC,
            listOf(listOf(last), listOf(n(Suit.AMBER, 5), c(Suit.WILD, Kind.PRISM)), listOf(c(Suit.AMBER, Kind.BLOCK))),
            n(Suit.RUBY, 9)
        )
        e.apply(Action.Play(0, last.id))
        val ph = e.state.phase
        assertTrue(ph is Phase.MatchOver)
        assertEquals(75, (ph as Phase.MatchOver).points)
    }

    @Test
    fun newGameDealsCorrectly() {
        for (mode in GameMode.entries) {
            val cfg = GameConfig(mode, List(6) { PlayerSetup("P$it", 0, it == 0) }, handSize = 10, seed = 99)
            val (eng, steps) = GameEngine.newGame(cfg)
            assertNotNull(steps.firstOrNull { it.event is GameEvent.Dealt })
            val s = eng.state
            val total = s.players.sumOf { it.hand.size } + s.drawPile.size + s.discard.size
            assertEquals(DeckFactory.deckSize(mode), total)
            if (mode != GameMode.CLASSIC) assertEquals(Kind.NUMBER, s.topFace.kind)
        }
    }
}
