package com.puzzel.lab.cardsmania.data

import com.puzzel.lab.cardsmania.engine.Action
import com.puzzel.lab.cardsmania.engine.GameEngine
import com.puzzel.lab.cardsmania.engine.ai.BotAI
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.HouseRules
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.PlayerSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProgressionTest {

    private fun summary(won: Boolean, mode: GameMode = GameMode.CLASSIC) = MatchSummary(
        mode = mode, won = won, place = if (won) 1 else 3, players = 4, points = 120, humanTurns = 10, maxHand = 9,
        cardsPlayed = 9, actionsPlayed = 3, prismsPlayed = 1, maniaCalls = 1, catches = 0, knockouts = 0,
        biggestStackHit = 0, biggestStackSeen = 4, eclipses = 0, maxPurge = 0, swaps = 0, cardsForcedOnRivals = 4,
        lastStanding = false, hardestBeaten = if (won) Difficulty.HARD else null, daily = null,
    )

    @Test
    fun winAwardsCoinsXpHistoryAndAchievements() {
        val (p, r) = Progression.apply(Profile(), summary(true))
        assertTrue(p.coins > 500)
        assertTrue(p.xp > 0)
        assertEquals(1, p.history.size)
        assertTrue("first_win" in p.achievements)
        assertTrue("beat_hard" in p.achievements)
        assertEquals(1, p.stat(GameMode.CLASSIC).wins)
        assertEquals(p.coins - 500, r.coins)
    }

    @Test
    fun lossCountsGameAndResetsStreak() {
        val (p1, _) = Progression.apply(Profile(), summary(true))
        val (p2, _) = Progression.apply(p1, summary(false))
        assertEquals(2, p2.stat(GameMode.CLASSIC).games)
        assertEquals(0, p2.stat(GameMode.CLASSIC).streak)
        assertEquals(1, p2.stat(GameMode.CLASSIC).bestStreak)
    }

    @Test
    fun dailyChallengeIsDeterministic() {
        val a = GameFactory.daily("2026-11-05")
        val b = GameFactory.daily("2026-11-05")
        assertEquals(a.mode, b.mode)
        assertEquals(a.bots, b.bots)
        assertEquals(a.config(Profile()).seed, b.config(Profile()).seed)
    }

    @Test
    fun giftClaimAddsCoinsAndCooldown() {
        val now = 10_000_000_000L
        val (p, amount) = Progression.claimGift(Profile(), now)
        assertEquals(500 + amount, p.coins)
        assertTrue(!Progression.giftReady(p, now + 1000))
        assertTrue(Progression.giftReady(p, now + Progression.GIFT_COOLDOWN_MS))
    }

    @Test
    fun savedGameRoundTripsMidGameInEveryMode() {
        for (mode in GameMode.entries) {
            val cfg = GameConfig(
                mode, List(4) { PlayerSetup("P$it", it, it == 0, Difficulty.NORMAL) },
                rules = HouseRules(powerUps = true, comboDrop = true), seed = 11L + mode.ordinal,
            )
            val (eng, _) = GameEngine.newGame(cfg, recordSnapshots = false)
            val rnd = Random(3)
            repeat(25) {
                val s = eng.state
                if (s.isOver) return@repeat
                val a = BotAI.decide(s, s.actingPlayer, Difficulty.NORMAL, rnd) ?: Action.Draw(s.actingPlayer)
                if (eng.validate(a) == null) eng.apply(a)
            }
            val json = AppJson.encodeToString(SavedGame.serializer(), SavedGame(eng.state, "2026-11-05", 4))
            val back = AppJson.decodeFromString(SavedGame.serializer(), json)
            val a: GameState = eng.state
            val b: GameState = back.state
            assertEquals(a.players.map { it.hand }, b.players.map { it.hand })
            assertEquals(a.drawPile, b.drawPile)
            assertEquals(a.discard, b.discard)
            assertEquals(a.phase, b.phase)
            assertEquals(a.side, b.side)
            assertEquals(a.rngState, b.rngState)
            // the restored engine keeps playing legally
            val restored = GameEngine(b)
            if (b.phase !is Phase.MatchOver && b.phase !is Phase.RoundOver) {
                val act = BotAI.decide(b, b.actingPlayer, Difficulty.NORMAL, rnd)!!
                assertEquals(null, restored.validate(act))
            }
        }
    }
}
