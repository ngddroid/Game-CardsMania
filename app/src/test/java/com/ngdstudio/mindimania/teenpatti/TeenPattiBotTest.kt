package com.ngdstudio.mindimania.teenpatti

import com.ngdstudio.mindimania.engine.model.Difficulty
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TeenPattiBotTest {

    /** Average final chips of seat 0 played by [hero] against three Normal bots. */
    private fun avgChips(hero: Difficulty, games: Int, seed: Int): Double {
        val rnd = Random(seed)
        var sum = 0L
        repeat(games) {
            var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4, hands = 10), rnd.nextLong()).last().state
            while (!s.isOver) {
                if (s.phase is TPPhase.HandOver) { s = TeenPattiEngine.nextHand(s).last().state; continue }
                val d = if (s.turn == 0) hero else Difficulty.NORMAL
                s = TeenPattiEngine.apply(s, TeenPattiBot.decide(s, s.turn, d, rnd)!!).last().state
            }
            sum += s.chips[0]
        }
        return sum.toDouble() / games
    }

    @Test
    fun harderBotsEndWithMoreChips() {
        // measured: Easy ≈ 1885, Normal ≈ 1981, Hard ≈ 2082, Expert ≈ 2171 (start 2000), 400 games each; luck is large, so this uses many games
        val easy = avgChips(Difficulty.EASY, 1500, 1)
        val expert = avgChips(Difficulty.GENIUS, 1500, 2)
        assertTrue("easy $easy vs expert $expert", expert > easy + 60)
    }

    @Test
    fun aTrailIsNeverPackedAndAlwaysAcceptsASideshow() {
        val trail = listOf(TCard(12, 14, 0), TCard(25, 14, 1), TCard(38, 14, 2))
        val rnd = Random(5)
        repeat(200) {
            var s = TeenPattiEngine.newGame(TeenPattiSetup(players = 4), rnd.nextLong()).last().state
            val me = s.turn
            s = s.copy(hands = s.hands.toMutableList().also { it[me] = trail }, seen = s.seen.toMutableList().also { it[me] = true })
            val a = TeenPattiBot.decide(s, me, Difficulty.EASY, rnd)
            assertTrue("$a", a !is TPAction.Pack)
            val asked = s.copy(phase = TPPhase.Sideshow((me + 1) % 4, me))
            assertTrue(TeenPattiBot.decide(asked, me, Difficulty.NORMAL, rnd) == TPAction.SideshowReply(me, true))
        }
    }
}
