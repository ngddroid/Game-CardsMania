package com.puzzel.lab.cardsmania.mindi

import com.puzzel.lab.cardsmania.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MindiEngineTest {

    private var nid = 1000
    private fun c(rank: Int, suit: Int) = MCard(nid++, rank, suit)

    /** A 4-player state in the middle of a hand with chosen hands. */
    private fun state(hands: List<List<MCard>>, mode: MindiTrumpMode = MindiTrumpMode.OPEN, trump: Int? = 0, turn: Int = 0, hidden: MCard? = null, owner: Int = -1): MindiState =
        MindiState(
            setup = MindiSetup(players = hands.size, trumpMode = mode),
            seed = 1, rngState = 1, hands = hands, dealer = hands.size - 1, turn = turn, leader = turn,
            trump = trump, hidden = hidden, hiddenOwner = owner, voids = List(hands.size) { emptySet() },
        )

    private fun play(s: MindiState, seat: Int, card: MCard): MindiState = MindiEngine.apply(s, MindiAction.Play(seat, card.id)).last().state

    @Test
    fun deckSizesAndDeal() {
        for ((players, dd, size) in listOf(Triple(4, false, 13), Triple(6, false, 8), Triple(8, false, 6), Triple(6, true, 17), Triple(8, true, 13))) {
            val setup = MindiSetup(players = players, doubleDeck = dd, trumpMode = MindiTrumpMode.CUT)
            val deck = MindiEngine.deck(setup)
            assertEquals(players * size, deck.size)
            assertEquals(deck.size, deck.map { it.id }.toSet().size)
            assertEquals(if (dd) 8 else 4, deck.count { it.isTen })
            val s = MindiEngine.newMatch(setup, 42).last().state
            assertTrue(s.hands.all { it.size == size })
            assertEquals((s.dealer + 1) % players, s.turn)
        }
    }

    @Test
    fun mustFollowSuit() {
        val a = c(5, 1); val b = c(9, 0); val x = c(14, 1)
        val s = state(listOf(listOf(a, b), listOf(x, c(3, 2)), listOf(c(4, 3), c(5, 3)), listOf(c(6, 2), c(7, 2))))
        val s1 = play(s, 0, a)
        assertEquals(listOf(x.id), MindiEngine.legalCards(s1, 1).map { it.id }) // has a heart → must play it
        assertNotNull(MindiEngine.validate(s1, MindiAction.Play(1, s1.hands[1].first { it.suit == 2 }.id)))
    }

    @Test
    fun trumpBeatsHighCardAndTensGoToWinner() {
        // trump = spades (0). Lead 10♥, others follow, seat 3 is void in hearts and trumps with 2♠.
        val ten = c(10, 1)
        val s0 = state(listOf(listOf(ten, c(3, 3)), listOf(c(14, 1), c(3, 2)), listOf(c(4, 1), c(5, 2)), listOf(c(2, 0), c(6, 2))))
        var s = play(s0, 0, ten)
        s = play(s, 1, s.hands[1].first { it.rank == 14 })
        s = play(s, 2, s.hands[2].first { it.suit == 1 })
        s = play(s, 3, s.hands[3].first { it.suit == 0 })
        assertEquals(3, s.lastWinner)
        assertEquals(1, s.tens[1].size) // team of seat 3
        assertEquals(1, s.tricks[1])
        assertEquals(3, s.turn)
    }

    @Test
    fun hideModeRevealsWhenSomeoneCantFollow() {
        val hid = c(8, 2) // diamonds will be trump
        val s0 = state(
            listOf(listOf(c(5, 1), c(6, 3)), listOf(c(9, 3), c(2, 2)), listOf(c(4, 1), c(5, 2)), listOf(c(6, 1), c(7, 2))),
            mode = MindiTrumpMode.HIDE, trump = null, hidden = hid, owner = 2,
        )
        val steps = MindiEngine.apply(s0, MindiAction.Play(0, s0.hands[0].first { it.suit == 1 }.id))
        // seat 1 has no heart → the trump is revealed before it plays
        val revealed = steps.map { it.event }.filterIsInstance<MindiEvent.TrumpRevealed>().single()
        assertEquals(1, revealed.bySeat)
        val s1 = steps.last().state
        assertEquals(2, s1.trump)
        assertNull(s1.hidden)
        assertTrue(s1.hands[2].any { it.id == hid.id }) // card went back to its owner
        // must trump after reveal: seat 1 holds 2♦ → only that is legal
        assertEquals(listOf(2), MindiEngine.legalCards(s1, 1).map { it.suit })
    }

    @Test
    fun cutModeFirstOffSuitCardSetsTrump() {
        val s0 = state(
            listOf(listOf(c(13, 1), c(3, 3)), listOf(c(3, 0), c(4, 0)), listOf(c(4, 1), c(5, 2)), listOf(c(12, 0), c(7, 2))),
            mode = MindiTrumpMode.CUT, trump = null,
        )
        var s = play(s0, 0, s0.hands[0].first { it.rank == 13 })
        val steps = MindiEngine.apply(s, MindiAction.Play(1, s.hands[1].first { it.rank == 3 }.id))
        assertTrue(steps.any { it.event is MindiEvent.TrumpCut })
        s = steps.last().state
        assertEquals(0, s.trump)
        s = play(s, 2, s.hands[2].first { it.suit == 1 })
        s = play(s, 3, s.hands[3].first { it.rank == 12 }) // over-trumps with Q♠
        assertEquals(3, s.lastWinner)
    }

    @Test
    fun followingTheRevealedSuitIsNotATrump() {
        // Lead 5♥ before trump is known; seat 1 can't follow → hearts revealed as trump. Seat 2's 3♥ is just a follow.
        val hid = c(9, 1)
        val s0 = state(
            listOf(listOf(c(5, 1), c(6, 3)), listOf(c(9, 3), c(2, 2)), listOf(c(3, 1), c(5, 2)), listOf(c(14, 1), c(7, 2))),
            mode = MindiTrumpMode.HIDE, trump = null, hidden = hid, owner = 0,
        )
        var s = play(s0, 0, s0.hands[0].first { it.suit == 1 })
        s = play(s, 1, MindiEngine.legalCards(s, 1).first())
        s = play(s, 2, s.hands[2].first { it.suit == 1 })
        s = play(s, 3, s.hands[3].first { it.suit == 1 })
        assertEquals(3, s.lastWinner) // A♥ wins as the highest heart, not 3♥ "as trump"
    }

    @Test
    fun scoringAndDealerRotation() {
        val base = state(listOf(emptyList(), emptyList(), emptyList(), emptyList()))
        fun tens(n: Int, team: Int) = List(n) { c(10, it % 4) }
        // 3 tens → normal win
        var s = base.copy(tens = listOf(tens(3, 0), tens(1, 1)), tricks = listOf(5, 8), trickNo = 13, lastWinner = 1, dealer = 1)
        var r = MindiEngine.scoreHand(s)
        assertEquals(0, r.winnerTeam); assertEquals(HandKind.NORMAL, r.kind); assertEquals(1, r.points)
        assertEquals(1, MindiEngine.nextDealer(s, r)) // dealer's team (1) lost → same dealer
        // 2-2 → tricks decide
        s = base.copy(tens = listOf(tens(2, 0), tens(2, 1)), tricks = listOf(6, 7), trickNo = 13, lastWinner = 0, dealer = 0)
        r = MindiEngine.scoreHand(s)
        assertEquals(1, r.winnerTeam)
        assertEquals(0, MindiEngine.nextDealer(s, r))
        // mendikot
        s = base.copy(tens = listOf(tens(4, 0), emptyList()), tricks = listOf(9, 4), trickNo = 13, lastWinner = 0, dealer = 0)
        r = MindiEngine.scoreHand(s)
        assertEquals(HandKind.MENDIKOT, r.kind); assertEquals(2, r.points)
        assertEquals(1, MindiEngine.nextDealer(s, r)) // dealer's team won → next player deals
        // whitewash against the dealer's team → dealer's partner deals
        s = base.copy(tens = listOf(emptyList(), tens(4, 1)), tricks = listOf(0, 13), trickNo = 13, lastWinner = 1, dealer = 0)
        r = MindiEngine.scoreHand(s)
        assertEquals(HandKind.WHITEWASH, r.kind); assertEquals(3, r.points)
        assertEquals(2, MindiEngine.nextDealer(s, r))
    }

    @Test
    fun partnerBotDoesNotTrumpAWinningPartner() {
        // Real deck cards so the bot's card memory works. Trump = spades. Seat 0 leads A♥ (the highest heart);
        // seat 2 (partner) has no hearts, holds a trump and the 10♣ → it must not trump, it should feed the 10.
        val deck = MindiEngine.deck(MindiSetup(players = 4))
        fun d(rank: Int, suit: Int) = deck.first { it.rank == rank && it.suit == suit }
        val s0 = state(
            listOf(
                listOf(d(14, 1), d(3, 3), d(4, 3)),
                listOf(d(4, 1), d(5, 3), d(6, 3)),
                listOf(d(10, 3), d(5, 0), d(7, 2)),
                listOf(d(3, 1), d(9, 1), d(8, 2)),
            ),
            mode = MindiTrumpMode.OPEN, trump = 0,
        )
        var s = play(s0, 0, d(14, 1))
        s = play(s, 1, d(4, 1))
        val pick = MindiBot.chooseCard(s, 2, Difficulty.HARD, Random(1))
        assertFalse("should not trump the partner's ace", pick.suit == 0)
        assertTrue("should feed the 10 to the partner", pick.isTen)
    }

    private fun playMatch(setup: MindiSetup, seed: Long, diffOf: (Int) -> Difficulty): MindiState {
        val rnd = Random(seed)
        var s = MindiEngine.newMatch(setup, seed).last().state
        val total = MindiEngine.deck(MindiEngine.normalize(setup)).size
        var guard = 0
        while (s.phase !is MindiPhase.MatchOver && guard++ < 5000) {
            if (s.phase is MindiPhase.HandOver) {
                s = MindiEngine.apply(s, MindiAction.NextHand()).last().state
                continue
            }
            val actor = when (val ph = s.phase) {
                is MindiPhase.ChooseHidden -> ph.seat
                else -> s.turn
            }
            val a = MindiBot.decide(s, actor, diffOf(actor), rnd)!!
            assertNull("illegal bot action $a in ${s.phase}", MindiEngine.validate(s, a))
            s = MindiEngine.apply(s, a).last().state
            val inPlay = s.hands.sumOf { it.size } + s.played.size + (if (s.hidden != null) 1 else 0)
            assertEquals("card conservation", total, inPlay)
        }
        assertTrue("match must end", s.phase is MindiPhase.MatchOver)
        return s
    }

    @Test
    fun soakEverySetup() {
        var seed = 1L
        for (players in listOf(4, 6, 8)) for (dd in listOf(false, true)) for (mode in MindiTrumpMode.entries) {
            if (dd && players == 4) continue
            repeat(25) {
                playMatch(MindiSetup(players = players, doubleDeck = dd, trumpMode = mode, target = 3), seed++) { Difficulty.entries[(it + seed.toInt()) % 3] }
            }
        }
    }

    @Test
    fun hardTeamBeatsEasyTeam() {
        var hardHands = 0
        var hands = 0
        for (g in 0 until 60) {
            val mode = MindiTrumpMode.entries[g % 3]
            val s = playMatch(MindiSetup(players = 4, trumpMode = mode, target = 5), 9000L + g) { if (it % 2 == 0) Difficulty.HARD else Difficulty.EASY }
            hardHands += s.history.count { it.winnerTeam == 0 }
            hands += s.history.size
        }
        println("Mindi: Hard team won $hardHands of $hands hands vs Easy")
        assertTrue(hardHands > hands * 0.6)
    }

    @Test
    fun expertPlaysLegallyAndWell() {
        MindiSearch.budgetMs = 25
        var expert = 0
        var hands = 0
        for (g in 0 until 6) {
            val s = playMatch(MindiSetup(players = 4, trumpMode = MindiTrumpMode.entries[g % 3], target = 3), 700L + g) { if (it % 2 == 0) Difficulty.GENIUS else Difficulty.NORMAL }
            expert += s.history.count { it.winnerTeam == 0 }
            hands += s.history.size
        }
        println("Mindi: Expert team won $expert of $hands hands vs Normal")
    }
}
