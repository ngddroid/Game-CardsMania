package com.ngdstudio.mindimania.bhabhi

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BhabhiEngineTest {

    /** A card with its real deck id (suit * 13 + rank - 2). Ranks 2..14, suits 0 ♠ 1 ♥ 2 ♦ 3 ♣. */
    private fun c(rank: Int, suit: Int) = BCard(suit * 13 + rank - 2, rank, suit)

    private fun state(hands: List<List<BCard>>, leader: Int, trickNo: Int = 1, safe: Boolean = true, out: List<Int> = emptyList()): BhabhiState {
        val n = hands.size
        val s0 = BhabhiState(
            BhabhiSetup(players = n, safeFirstTrick = safe), 1L, hands.map { BhabhiEngine.sortHand(it) }, dealer = 0,
            leader = leader, turn = leader, order = emptyList(), trickNo = trickNo, out = out,
            voids = List(n) { emptySet() }, known = List(n) { emptyList() }, pickups = List(n) { 0 }, thullas = List(n) { 0 },
        )
        return s0.copy(order = BhabhiEngine.orderFrom(s0, leader))
    }

    private fun play(s: BhabhiState, seat: Int, card: BCard) = BhabhiEngine.apply(s, BhabhiAction.Play(seat, card.id))

    @Test
    fun dealGivesOutTheWholeDeckAndTheAceOfSpadesLeads() {
        for (n in 3..8) for (seed in 1L..20L) {
            val s = BhabhiEngine.newGame(BhabhiSetup(players = n), seed).last().state
            val all = s.hands.flatten()
            assertEquals(52, all.size)
            assertEquals(52, all.map { it.id }.toSet().size)
            val sizes = s.hands.map { it.size }
            assertTrue("sizes differ by at most one: $sizes", sizes.max() - sizes.min() <= 1)
            assertTrue(s.hands[s.leader].any { it.isAceOfSpades })
            assertEquals(s.leader, s.turn)
            assertEquals(n, s.order.size)
            assertEquals(s.leader, s.order.first())
            assertEquals(listOf(BhabhiEngine.deck().first { it.isAceOfSpades }), BhabhiEngine.legalCards(s, s.leader))
        }
        // same seed = same deal; player count is clamped to 3..8
        assertEquals(BhabhiEngine.newGame(BhabhiSetup(), 9).last().state, BhabhiEngine.newGame(BhabhiSetup(), 9).last().state)
        assertEquals(3, BhabhiEngine.newGame(BhabhiSetup(players = 2), 1).last().state.players)
        assertEquals(8, BhabhiEngine.newGame(BhabhiSetup(players = 12), 1).last().state.players)
    }

    @Test
    fun openingLeadAndTurnOrderAreEnforced() {
        val s = BhabhiEngine.newGame(BhabhiSetup(players = 4), 3).last().state
        val other = s.hands[s.leader].first { !it.isAceOfSpades }
        assertEquals("Open with the A♠", BhabhiEngine.validate(s, BhabhiAction.Play(s.leader, other.id)))
        val notMe = (s.leader + 1) % 4
        assertEquals("Not your turn", BhabhiEngine.validate(s, BhabhiAction.Play(notMe, s.hands[notMe][0].id)))
        val after = play(s, s.leader, c(14, 0)).last().state
        assertEquals((s.leader + 1) % 4, after.turn)
        assertEquals(0, after.leadSuit)
    }

    @Test
    fun followingSuitIsTheCorrectMoveButAnyCardMayBePlayed() {
        val s = state(listOf(listOf(c(5, 1), c(9, 3)), listOf(c(7, 1), c(13, 3)), listOf(c(3, 2), c(4, 2))), leader = 0)
        val t = play(s, 0, c(5, 1)).last().state
        assertEquals(listOf(c(7, 1)), BhabhiEngine.legalCards(t, 1))
        // nothing blocks another suit (no hints); it is a wrong thulla and gets fined
        assertEquals(2, BhabhiEngine.playableCards(t, 1).size)
        assertNull(BhabhiEngine.validate(t, BhabhiAction.Play(1, c(13, 3).id)))
        assertTrue(BhabhiEngine.isWrongThulla(t, 1, c(13, 3)))
        assertFalse(BhabhiEngine.isWrongThulla(t, 1, c(7, 1)))
        // no hearts at all: any card may be played
        val u = play(t, 1, c(7, 1)).last().state
        assertEquals(2, BhabhiEngine.legalCards(u, 2).size)
        assertTrue(BhabhiEngine.mustThulla(u, 2))
    }

    @Test
    fun wrongThullaDoesNotCutTheTrickAndCostsFiveCards() {
        // 4 players; seat 1 still has 7♥ but plays K♣
        val s = state(listOf(
            listOf(c(5, 1), c(2, 3)),
            listOf(c(7, 1), c(13, 3)),
            listOf(c(3, 2), c(4, 2), c(6, 2), c(8, 2)),
            listOf(c(9, 1), c(10, 0), c(11, 0), c(12, 0)),
        ), leader = 0)
        val t = play(s, 0, c(5, 1)).last().state
        val steps = play(t, 1, c(13, 3))
        assertEquals(listOf(BhabhiEvent.Played(1, c(13, 3), false)), steps.take(1).map { it.event })
        val fine = steps[1].event as BhabhiEvent.Penalty
        assertEquals(2, steps.size)
        assertEquals(1, fine.seat)
        assertEquals(BhabhiEngine.PENALTY_CARDS, fine.given.size)
        // round from the left: seat 2, seat 3, (seat 0 has its last card: skipped), 2, 3, 2
        assertEquals(listOf(2, 3, 2, 3, 2), fine.given.map { it.seat })
        val u = steps.last().state
        assertEquals(6, u.hands[1].size)
        assertTrue(u.hands[1].containsAll(fine.given.map { it.card }))
        assertEquals(listOf(1, 6, 1, 2), u.hands.map { it.size })
        // the trick goes on, the K♣ is just thrown in, seat 1 is not marked out of hearts and nobody got a thulla
        assertEquals(2, u.turn)
        assertEquals(listOf(c(5, 1), c(13, 3)), u.trick.map { it.card })
        assertTrue(u.voids[1].isEmpty())
        assertEquals(0, u.thullas.sum())
        // everyone saw the cards change hands
        assertTrue(u.known[1].containsAll(fine.given.map { it.card.id }))
        assertEquals(12, u.hands.flatten().size + u.trick.size)
    }

    @Test
    fun wrongThullaAsTheLastCardStillThrowsTheTrickAway() {
        val s = state(listOf(listOf(c(5, 1), c(2, 3), c(3, 3)), listOf(c(9, 1), c(4, 3), c(5, 3)), listOf(c(12, 1), c(6, 3), c(7, 3))), leader = 0)
        var t = play(s, 0, c(5, 1)).last().state
        t = play(t, 1, c(9, 1)).last().state
        val steps = play(t, 2, c(6, 3))
        assertEquals(listOf("Played", "Penalty", "Discarded"), steps.map { it.event::class.simpleName })
        val d = steps[2].event as BhabhiEvent.Discarded
        assertEquals(1, d.high)
        assertTrue(c(6, 3) in d.cards)
        val fine = steps[1].event as BhabhiEvent.Penalty
        // seats 0 and 1 have 2 cards each and keep their last one: only 2 cards to give
        assertEquals(listOf(0, 1), fine.given.map { it.seat })
        val end = steps.last().state
        assertEquals(listOf(1, 1, 4), end.hands.map { it.size })
        assertEquals(1, end.turn)
        assertEquals(9, end.hands.flatten().size + end.discarded.size)
    }

    /** A careless player keeps playing wrong thullas against every bot level: the game still ends and no card is lost. */
    @Test
    fun gamesWithACarelessPlayerStillFinish() {
        val rnd = Random(77)
        var fines = 0
        for (g in 0 until 30) {
            val n = 3 + g % 6
            var s = BhabhiEngine.newGame(BhabhiSetup(players = n, safeFirstTrick = g % 2 == 0), rnd.nextLong()).last().state
            var guard = 0
            while (!s.isOver) {
                assertTrue("game too long", guard++ < 6000)
                val seat = s.turn
                val wrong = if (seat == 0 && rnd.nextDouble() < 0.3) s.hands[0].firstOrNull { BhabhiEngine.isWrongThulla(s, 0, it) } else null
                val a = wrong?.let { BhabhiAction.Play(0, it.id) } ?: BhabhiBot.decide(s, seat, Difficulty.entries[(seat + g) % Difficulty.entries.size], rnd)!!
                assertNull(BhabhiEngine.validate(s, a))
                val steps = BhabhiEngine.apply(s, a)
                fines += steps.count { it.event is BhabhiEvent.Penalty }
                for (st in steps) check(st.state)
                s = steps.last().state
            }
            assertEquals(n - 1, s.out.size)
        }
        assertTrue(fines > 20)
    }

    @Test
    fun wrongThullaIsFinedOnTheSafeFirstTrickToo() {
        var s = state(listOf(listOf(c(14, 0), c(2, 1), c(3, 1)), listOf(c(5, 0), c(4, 1), c(6, 1)), listOf(c(7, 2), c(8, 2), c(9, 2))), leader = 0, trickNo = 0, safe = true)
        s = play(s, 0, c(14, 0)).last().state
        val steps = play(s, 1, c(4, 1))
        assertTrue(steps.any { it.event is BhabhiEvent.Penalty })
        // seat 2 has no spade: whatever it throws is a plain safe discard, no fine
        val t = steps.last().state
        assertFalse(play(t, 2, t.hands[2].first()).any { it.event is BhabhiEvent.Penalty })
    }

    @Test
    fun botsNeverPlayAWrongThulla() {
        val rnd = Random(21)
        for (g in 0 until 40) {
            var s = BhabhiEngine.newGame(BhabhiSetup(players = 3 + g % 6), rnd.nextLong()).last().state
            while (!s.isOver) {
                val a = BhabhiBot.decide(s, s.turn, Difficulty.entries[g % Difficulty.entries.size], rnd)!! as BhabhiAction.Play
                assertFalse(BhabhiEngine.isWrongThulla(s, a.seat, s.hands[a.seat].first { it.id == a.cardId }))
                s = BhabhiEngine.apply(s, a).last().state
            }
        }
    }

    @Test
    fun whenEveryoneFollowsTheTrickIsThrownAwayAndTheHighestLeads() {
        val s = state(listOf(listOf(c(5, 1), c(2, 3)), listOf(c(13, 1), c(3, 3)), listOf(c(9, 1), c(4, 3))), leader = 0)
        var t = play(s, 0, c(5, 1)).last().state
        t = play(t, 1, c(13, 1)).last().state
        val steps = play(t, 2, c(9, 1))
        val end = steps.last().state
        assertTrue(steps.any { it.event is BhabhiEvent.Discarded && (it.event as BhabhiEvent.Discarded).high == 1 })
        assertEquals(3, end.discarded.size)
        assertTrue(end.trick.isEmpty())
        assertEquals(1, end.leader)
        assertEquals(1, end.turn)
        assertEquals(listOf(1, 2, 0), end.order)
        assertEquals(2, end.trickNo)
        assertEquals(-1, end.lastTaker)
        assertEquals(1, end.hands[0].size + end.hands[1].size + end.hands[2].size - 2)
    }

    @Test
    fun aThullaStopsTheTrickAndTheHighestCardPicksEverythingUp() {
        val s = state(
            listOf(
                listOf(c(5, 1), c(2, 3)),
                listOf(c(12, 1), c(3, 3)),
                listOf(c(13, 3), c(4, 0)), // no hearts
                listOf(c(14, 1), c(6, 2)), // never gets to play this trick
            ), leader = 0,
        )
        var t = play(s, 0, c(5, 1)).last().state
        t = play(t, 1, c(12, 1)).last().state
        val steps = play(t, 2, c(13, 3))
        val end = steps.last().state
        val played = steps.first().event as BhabhiEvent.Played
        assertTrue(played.thulla)
        val pick = steps.map { it.event }.filterIsInstance<BhabhiEvent.PickedUp>().single()
        assertEquals(1, pick.taker)
        assertEquals(2, pick.bySeat)
        assertEquals(setOf(c(5, 1), c(12, 1), c(13, 3)), pick.cards.toSet())
        assertEquals(setOf(c(3, 3), c(5, 1), c(12, 1), c(13, 3)), end.hands[1].toSet())
        assertEquals(2, end.hands[3].size) // the 4th player kept the A♥
        assertEquals(1, end.leader)
        assertEquals(1, end.turn)
        assertTrue(1 in end.voids[2])
        assertEquals(setOf(c(5, 1).id, c(12, 1).id, c(13, 3).id), end.known[1].toSet())
        assertEquals(1, end.thullas[2])
        assertEquals(1, end.pickups[1])
        assertTrue(end.discarded.isEmpty())
        // the picked-up cards are public: playing one removes it from the known list
        val after = play(end, 1, c(12, 1)).last().state
        assertFalse(c(12, 1).id in after.known[1])
    }

    @Test
    fun openingTrickIsSafeUnlessTheHouseRuleIsOff() {
        val hands = listOf(listOf(c(14, 0), c(2, 1)), listOf(c(9, 3), c(3, 1)), listOf(c(5, 0), c(4, 1)))
        // safe: the player without a spade just discards and the trick goes all the way round
        var t = play(state(hands, leader = 0, trickNo = 0, safe = true), 0, c(14, 0)).last().state
        val steps = play(t, 1, c(9, 3))
        assertFalse((steps.first().event as BhabhiEvent.Played).thulla)
        t = steps.last().state
        assertEquals(2, t.turn)
        t = play(t, 2, c(5, 0)).last().state
        assertEquals(3, t.discarded.size)
        assertEquals(0, t.leader)
        assertTrue(0 in t.voids[1]) // the void is still public
        // not safe: the same card is a thulla and the A♠ player picks up
        var u = play(state(hands, leader = 0, trickNo = 0, safe = false), 0, c(14, 0)).last().state
        u = play(u, 1, c(9, 3)).last().state
        assertEquals(setOf(c(2, 1), c(14, 0), c(9, 3)), u.hands[0].toSet())
        assertEquals(0, u.turn)
    }

    @Test
    fun emptyingYourHandMakesYouSafeAndTheLastOneIsTheBhabhi() {
        val s = state(listOf(listOf(c(5, 1)), listOf(c(9, 1)), listOf(c(7, 1), c(2, 3))), leader = 0, trickNo = 3)
        var t = play(s, 0, c(5, 1)).last().state
        t = play(t, 1, c(9, 1)).last().state
        val steps = play(t, 2, c(7, 1))
        val end = steps.last().state
        val outs = steps.map { it.event }.filterIsInstance<BhabhiEvent.Out>()
        assertEquals(listOf(0 to 1, 1 to 2), outs.map { it.seat to it.place })
        assertEquals(BhabhiPhase.GameOver(2), end.phase)
        assertEquals(2, end.loser)
        assertEquals(1, end.placeOf(0))
        assertEquals(2, end.placeOf(1))
        assertEquals(3, end.placeOf(2))
        assertEquals(-1, end.turn)
        assertTrue(steps.last().event is BhabhiEvent.GameOver)
        assertEquals("The game is over", BhabhiEngine.validate(end, BhabhiAction.Play(2, c(2, 3).id)))
    }

    @Test
    fun ifEveryoneLeftRunsOutTogetherTheHighestCardIsTheBhabhi() {
        // seat 2 already got away; seats 0 and 1 each hold one heart
        val s = state(listOf(listOf(c(5, 1)), listOf(c(9, 1)), emptyList()), leader = 0, trickNo = 6, out = listOf(2))
        assertEquals(listOf(0, 1), s.order)
        val t = play(s, 0, c(5, 1)).last().state
        val end = play(t, 1, c(9, 1)).last().state
        assertEquals(1, end.loser)
        assertEquals(listOf(2, 0), end.out)
        assertEquals(3, end.placeOf(1))
    }

    @Test
    fun aThullaWithYourLastCardGetsYouOut() {
        val s = state(listOf(listOf(c(5, 1), c(3, 2)), listOf(c(12, 1)), listOf(c(13, 3)), listOf(c(2, 1), c(4, 1))), leader = 0)
        var t = play(s, 0, c(5, 1)).last().state
        t = play(t, 1, c(12, 1)).last().state // seat 1 plays its last card...
        val steps = play(t, 2, c(13, 3)) // ...but seat 2 cuts with its last card
        val end = steps.last().state
        assertEquals(listOf(2), end.out) // the thulla player got away
        assertEquals(3, end.hands[1].size) // seat 1 picked the trick up, so it is still in
        assertEquals(1, end.turn)
        assertFalse(end.isOver)
    }

    @Test
    fun whenTheHighCardsOwnerRanOutTheNextPlayerLeads() {
        val s = state(listOf(listOf(c(5, 1), c(2, 3)), listOf(c(13, 1)), listOf(c(9, 1), c(4, 3)), listOf(c(3, 1), c(6, 2))), leader = 0)
        var t = play(s, 0, c(5, 1)).last().state
        t = play(t, 1, c(13, 1)).last().state
        t = play(t, 2, c(9, 1)).last().state
        val end = play(t, 3, c(3, 1)).last().state
        assertEquals(listOf(1), end.out)
        assertEquals(2, end.leader) // seat 1 is out, so the next seat with cards leads
        assertEquals(listOf(2, 3, 0), end.order)
    }

    @Test
    fun stateSurvivesSaveAndLoad() {
        val json = Json { ignoreUnknownKeys = true }
        var s = BhabhiEngine.newGame(BhabhiSetup(players = 5), 11).last().state
        val rnd = Random(4)
        repeat(30) {
            if (s.isOver) return@repeat
            val a = BhabhiBot.decide(s, s.turn, Difficulty.NORMAL, rnd)!!
            s = BhabhiEngine.apply(s, a).last().state
        }
        assertEquals(s, json.decodeFromString(BhabhiState.serializer(), json.encodeToString(BhabhiState.serializer(), s)))
    }

    /** Plays a full game with the given bot levels, checking the rules after every card. */
    private fun playGame(players: Int, levels: List<Difficulty>, seed: Long, rnd: Random, safe: Boolean = true): BhabhiState {
        var s = BhabhiEngine.newGame(BhabhiSetup(players = players, safeFirstTrick = safe), seed).last().state
        var guard = 0
        while (!s.isOver) {
            assertTrue("game too long", guard++ < 3000)
            val seat = s.turn
            val a = BhabhiBot.decide(s, seat, levels[seat], rnd)
            assertNotNull(a)
            assertNull(BhabhiEngine.validate(s, a!!))
            val steps = BhabhiEngine.apply(s, a)
            for (st in steps) check(st.state)
            s = steps.last().state
        }
        assertEquals(players - 1, s.out.size)
        assertFalse(s.loser in s.out)
        assertEquals((0 until players).toSet(), (s.out + s.loser).toSet())
        return s
    }

    private fun check(s: BhabhiState) {
        val cards = s.hands.flatten() + s.discarded + s.trick.map { it.card }
        assertEquals("no card lost or added", 52, cards.size)
        assertEquals(52, cards.map { it.id }.toSet().size)
        for (p in 0 until s.players) {
            val ids = s.hands[p].map { it.id }.toSet()
            assertTrue("known cards are in the hand", s.known[p].all { it in ids })
            if (p in s.out) assertTrue(s.hands[p].isEmpty())
        }
        if (!s.isOver && s.turn >= 0) assertTrue(s.hands[s.turn].isNotEmpty())
    }

    @Test
    fun botsPlayThousandsOfLegalGames() {
        val rnd = Random(2026)
        val levels = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)
        var games = 0
        for (n in 3..8) repeat(180) { g ->
            playGame(n, List(n) { levels.random(rnd) }, rnd.nextLong(), rnd, safe = g % 3 != 0)
            games++
        }
        assertEquals(1080, games)
    }

    @Test
    fun hardBotsAreTheBhabhiLessOftenThanEasyBots() {
        val rnd = Random(77)
        var hardLosses = 0
        var easyLosses = 0
        val games = 500
        repeat(games) {
            // seat 0 is Hard, the other three are Easy
            val s = playGame(4, listOf(Difficulty.HARD, Difficulty.EASY, Difficulty.EASY, Difficulty.EASY), rnd.nextLong(), rnd)
            if (s.loser == 0) hardLosses++ else easyLosses++
        }
        val hardRate = hardLosses.toDouble() / games
        val easyRate = easyLosses.toDouble() / (games * 3)
        assertTrue("hard $hardRate vs easy $easyRate", hardRate < easyRate)
    }

    @Test
    fun expertSearchPlaysLegallyAndOnlyGuessesHiddenCards() {
        val rnd = Random(5)
        repeat(3) { g ->
            var s = BhabhiEngine.newGame(BhabhiSetup(players = 4 + g), rnd.nextLong()).last().state
            var guard = 0
            while (!s.isOver && guard++ < 3000) {
                val seat = s.turn
                val legal = BhabhiEngine.legalCards(s, seat)
                val card = if (seat == 0) BhabhiSearch.best(s, seat, legal, rnd, budgetMs = 4)!! else BhabhiBot.heuristic(s, seat, legal, careful = true)
                assertTrue(card in legal)
                if (seat == 0) {
                    val w = BhabhiSearch.sampleWorld(s, seat, rnd)
                    assertEquals(s.hands[0], w.hands[0])
                    assertEquals(s.hands.map { it.size }, w.hands.map { it.size })
                    for (p in 1 until s.players) {
                        val ids = w.hands[p].map { it.id }.toSet()
                        assertTrue("known cards stay where everyone saw them go", s.known[p].all { it in ids })
                    }
                }
                s = BhabhiEngine.apply(s, BhabhiAction.Play(seat, card.id)).last().state
            }
            assertTrue(s.isOver)
        }
    }
}
