package com.puzzel.lab.cardsmania.engine

import com.puzzel.lab.cardsmania.engine.ai.BotAI
import com.puzzel.lab.cardsmania.engine.ai.MonteCarlo
import com.puzzel.lab.cardsmania.engine.model.DeckFactory
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.HouseRules
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.PlayerSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Bot-vs-bot soak test: card conservation, termination, no illegal actions. */
class SimulationTest {

    private fun playOne(
        mode: GameMode, players: Int, seed: Long, rules: HouseRules, diffs: List<Difficulty>, deckCount: Int = 1,
        targetScore: Int = 0,
    ): Int {
        val rnd = Random(seed)
        val setups = List(players) { PlayerSetup("B$it", it, false, diffs[it % diffs.size]) }
        val cfg = GameConfig(mode, setups, handSize = listOf(5, 7, 10)[rnd.nextInt(3)], deckCount = deckCount, targetScore = targetScore, rules = rules, seed = seed)
        val (eng, _) = GameEngine.newGame(cfg, recordSnapshots = false)
        val s = eng.state
        val total = DeckFactory.deckSize(mode) * deckCount
        var actions = 0
        while (s.phase !is Phase.MatchOver) {
            if (s.phase is Phase.RoundOver) {
                eng.apply(Action.NextRound(0))
                continue
            }
            // Occasionally catch a vulnerable player
            if (s.maniaVulnerable >= 0 && rnd.nextDouble() < 0.5) {
                val catcher = s.activePlayers.firstOrNull { it.id != s.maniaVulnerable }
                if (catcher != null) eng.apply(Action.Catch(catcher.id, s.maniaVulnerable))
            }
            if (rules.jumpIn && rnd.nextDouble() < 0.3) {
                val jumper = s.activePlayers.firstOrNull { BotAI.jumpIn(s, it.id, Difficulty.HARD, rnd) != null }
                if (jumper != null) {
                    BotAI.jumpIn(s, jumper.id, Difficulty.HARD, Random(1))?.let { a -> if (eng.validate(a) == null) eng.apply(a) }
                }
            }
            if (s.phase is Phase.MatchOver || s.phase is Phase.RoundOver) continue
            val actor = s.actingPlayer
            val action = BotAI.decide(s, actor, s.players[actor].setup.difficulty, rnd)
                ?: error("bot had no action in ${s.phase}")
            val err = eng.validate(action)
            if (err != null) error("illegal bot action $action: $err in ${s.phase}")
            eng.apply(action)
            val count = s.players.sumOf { it.hand.size } + s.drawPile.size + s.discard.size
            assertEquals("card conservation broken after $action", total, count)
            assertEquals("duplicate cards", total, (s.players.flatMap { it.hand } + s.drawPile + s.discard).map { it.id }.toSet().size)
            actions++
            assertTrue("game did not terminate", actions < 20000)
        }
        return (s.phase as Phase.MatchOver).winnerId
    }

    @Test
    fun soakAllModes() {
        val diffs = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)
        var seed = 1L
        for (mode in GameMode.entries) {
            repeat(300) {
                val rnd = Random(seed)
                val rules = HouseRules(
                    stacking = rnd.nextBoolean(), jumpIn = rnd.nextBoolean(), sevenZero = rnd.nextBoolean(),
                    drawToMatch = rnd.nextBoolean(), forcedPlay = rnd.nextBoolean(), challenge = rnd.nextBoolean(),
                    comboDrop = rnd.nextBoolean(), powerUps = rnd.nextBoolean(),
                )
                val players = 2 + (it % 5)
                val decks = if (rnd.nextInt(5) == 0) 2 else 1
                playOne(mode, players, seed++, rules, diffs.shuffled(rnd), decks, if (it % 10 == 0) 200 else 0)
            }
        }
    }

    @Test
    fun hardBeatsEasy() {
        for (mode in GameMode.entries) {
            var hardWins = 0
            val games = 300
            repeat(games) { i ->
                // Seat order alternates so neither bot always starts.
                val diffs = if (i % 2 == 0) listOf(Difficulty.HARD, Difficulty.EASY) else listOf(Difficulty.EASY, Difficulty.HARD)
                val w = playOne(mode, 2, 10_000L + i, HouseRules(), diffs)
                if (diffs[w] == Difficulty.HARD) hardWins++
            }
            println("$mode: HARD won $hardWins / $games vs EASY")
            assertTrue("$mode hard bot should beat easy bot", hardWins > games * 0.5)
        }
    }

    @Test
    fun geniusPlaysLegally() {
        MonteCarlo.budgetMs = 15
        var geniusWins = 0
        repeat(16) { i ->
            val diffs = if (i % 2 == 0) listOf(Difficulty.GENIUS, Difficulty.NORMAL) else listOf(Difficulty.NORMAL, Difficulty.GENIUS)
            val w = playOne(GameMode.entries[i % 3], 2, 500L + i, HouseRules(), diffs)
            if (diffs[w] == Difficulty.GENIUS) geniusWins++
        }
        println("GENIUS won $geniusWins / 16 vs NORMAL")
    }
}
