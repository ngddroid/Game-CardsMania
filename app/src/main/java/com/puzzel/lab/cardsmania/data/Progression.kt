package com.puzzel.lab.cardsmania.data

import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.HouseRules
import com.puzzel.lab.cardsmania.engine.model.PlayerSetup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Builds game configs from the saved setup, Quick Play, and the Daily Challenge. */
object GameFactory {

    fun fromSetup(setup: SetupPrefs, profile: Profile, seed: Long = System.nanoTime()): GameConfig {
        val human = PlayerSetup(profile.name.ifBlank { "You" }, AvatarRef.player(profile.avatar), isHuman = true)
        val bots = setup.bots.take(5).ifEmpty { listOf(BotPick(0, Difficulty.NORMAL)) }.map {
            val ch = Characters.byId(it.characterId)
            PlayerSetup(ch.name, ch.id, isHuman = false, difficulty = it.difficulty)
        }
        val rules = setup.rules[setup.mode] ?: ModeInfo.defaultRules(setup.mode)
        return GameConfig(
            mode = setup.mode,
            players = listOf(human) + bots,
            handSize = setup.handSize,
            deckCount = setup.deckCount,
            targetScore = setup.targetScore,
            rules = rules,
            seed = seed,
        )
    }

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = fmt().format(Date())

    fun dayBefore(date: String): String? = runCatching {
        val cal = Calendar.getInstance()
        cal.time = fmt().parse(date)!!
        cal.add(Calendar.DAY_OF_YEAR, -1)
        fmt().format(cal.time)
    }.getOrNull()

    fun currentWeek(): Int {
        val cal = Calendar.getInstance(Locale.US)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        return cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.WEEK_OF_YEAR)
    }

    /** Daily Challenge for [date] (yyyy-MM-dd): same deal for everyone on that day. */
    fun daily(date: String = today()): DailyChallenge {
        val seed = date.replace("-", "").toLong()
        val r = java.util.Random(seed)
        val mode = GameMode.entries[(seed % 3).toInt()]
        val botCount = 2 + r.nextInt(3)
        val chars = Characters.all.shuffled(r).take(botCount)
        val diffs = listOf(Difficulty.NORMAL, Difficulty.HARD, Difficulty.HARD, Difficulty.GENIUS)
        val bots = chars.mapIndexed { i, c -> BotPick(c.id, diffs[(i + r.nextInt(2)) % diffs.size]) }
        val rules = when (mode) {
            GameMode.CLASSIC -> HouseRules(challenge = true, stacking = r.nextBoolean(), sevenZero = r.nextBoolean())
            GameMode.CHAOS -> HouseRules(challenge = false)
            GameMode.ECLIPSE -> HouseRules(challenge = false, drawToMatch = r.nextBoolean())
        }
        val turnGoal = when (mode) {
            GameMode.CLASSIC -> 14
            GameMode.CHAOS -> 18
            GameMode.ECLIPSE -> 16
        }
        return DailyChallenge(date, seed, mode, bots, rules, turnGoal)
    }
}

data class DailyChallenge(
    val date: String,
    val seed: Long,
    val mode: GameMode,
    val bots: List<BotPick>,
    val rules: HouseRules,
    val turnGoal: Int,
) {
    val goals: List<String>
        get() = listOf(
            "Win the match",
            "Win in $turnGoal of your turns or fewer",
            "Win without ever holding more than 12 cards",
        )

    fun config(profile: Profile): GameConfig {
        val setup = SetupPrefs(mode = mode, bots = bots, handSize = 7, deckCount = 1, targetScore = 0, rules = mapOf(mode to rules))
        return GameFactory.fromSetup(setup, profile, seed)
    }
}

/** Summary of one finished match from the human's point of view. */
data class MatchSummary(
    val mode: GameMode,
    val won: Boolean,
    val place: Int,
    val players: Int,
    val points: Int,
    val humanTurns: Int,
    val maxHand: Int,
    val cardsPlayed: Int,
    val actionsPlayed: Int,
    val prismsPlayed: Int,
    val maniaCalls: Int,
    val catches: Int,
    val knockouts: Int,
    val biggestStackHit: Int,
    val biggestStackSeen: Int,
    val eclipses: Int,
    val maxPurge: Int,
    val swaps: Int,
    val cardsForcedOnRivals: Int,
    val lastStanding: Boolean,
    val hardestBeaten: Difficulty?,
    val daily: String?,
)

data class RewardResult(
    val coins: Int,
    val xp: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val newAchievements: List<Achievement>,
    val dailyStars: Int,
)

object Progression {
    const val GIFT_COOLDOWN_MS = 24L * 60 * 60 * 1000

    fun giftReady(p: Profile, now: Long = System.currentTimeMillis()) = now - p.lastGiftAt >= GIFT_COOLDOWN_MS

    fun giftAmount(p: Profile): Int = 150 + 50 * (p.giftStreak % 7)

    fun claimGift(p: Profile, now: Long = System.currentTimeMillis()): Pair<Profile, Int> {
        val continued = now - p.lastGiftAt < GIFT_COOLDOWN_MS * 2
        val streak = if (continued) p.giftStreak + 1 else 1
        val amount = 150 + 50 * ((streak - 1) % 7)
        return p.copy(coins = p.coins + amount, lastGiftAt = now, giftStreak = streak) to amount
    }

    /** Ensures missions belong to the current week. */
    fun rollMissions(p: Profile): Profile {
        val week = GameFactory.currentWeek()
        return if (p.missionWeek == week) p else p.copy(missionWeek = week, missionProgress = emptyMap(), missionClaimed = emptySet())
    }

    fun dailyStars(s: MatchSummary, d: DailyChallenge?): Int {
        if (d == null || !s.won) return 0
        var stars = 1
        if (s.humanTurns <= d.turnGoal) stars++
        if (s.maxHand <= 12) stars++
        return stars
    }

    fun apply(p0: Profile, s: MatchSummary): Pair<Profile, RewardResult> {
        var p = rollMissions(p0)
        val mult = s.hardestBeaten?.let { DifficultyInfo.multiplier(it) } ?: 1.0
        val placeBonus = when (s.place) {
            1 -> 120
            2 -> 50
            3 -> 30
            else -> 15
        }
        var coins = ((placeBonus + s.points / 4 + s.players * 10) * (if (s.won) mult else 1.0)).toInt()
        val xp = (if (s.won) 120 else 40) + s.cardsPlayed * 2 + s.actionsPlayed * 3 + s.players * 5

        val daily = s.daily?.let { GameFactory.daily(it) }
        val stars = dailyStars(s, daily)
        var dailyMap = p.daily
        var dailyStreak = p.dailyStreak
        var lastDaily = p.lastDailyDate
        if (s.daily != null) {
            val prev = p.daily[s.daily] ?: 0
            if (stars > prev) {
                coins += (stars - prev) * 100
                dailyMap = p.daily + (s.daily to stars)
            }
            if (stars > 0 && lastDaily != s.daily) {
                val yesterday = GameFactory.dayBefore(s.daily)
                dailyStreak = if (lastDaily == yesterday) dailyStreak + 1 else 1
                lastDaily = s.daily
            }
        }

        val st = p.stat(s.mode)
        val streak = if (s.won) st.streak + 1 else 0
        val newStats = st.copy(
            games = st.games + 1,
            wins = st.wins + if (s.won) 1 else 0,
            streak = streak,
            bestStreak = maxOf(st.bestStreak, streak),
            biggestStack = maxOf(st.biggestStack, s.biggestStackSeen),
            fastestWin = if (s.won) (if (st.fastestWin == 0) s.humanTurns else minOf(st.fastestWin, s.humanTurns)) else st.fastestWin,
            cardsPlayed = st.cardsPlayed + s.cardsPlayed,
            actionsPlayed = st.actionsPlayed + s.actionsPlayed,
            maniaCalls = st.maniaCalls + s.maniaCalls,
            catches = st.catches + s.catches,
            knockouts = st.knockouts + s.knockouts,
            points = st.points + s.points,
        )
        val stats = p.stats + (s.mode to newStats)

        // missions
        val missions = Missions.forWeek(p.missionWeek)
        val prog = p.missionProgress.toMutableMap()
        fun inc(id: String, by: Int) {
            if (by > 0 && missions.any { it.id == id }) prog[id] = (prog[id] ?: 0) + by
        }
        inc("win", if (s.won) 1 else 0)
        inc("play", 1)
        inc("actions", s.actionsPlayed)
        inc("chaos_play", if (s.mode == GameMode.CHAOS) 1 else 0)
        inc("eclipse_play", if (s.mode == GameMode.ECLIPSE) 1 else 0)
        inc("classic_win", if (s.won && s.mode == GameMode.CLASSIC) 1 else 0)
        inc("mania", s.maniaCalls)
        inc("prism", s.prismsPlayed)
        inc("draw_attack", s.cardsForcedOnRivals)

        val record = MatchRecord(System.currentTimeMillis(), s.mode, s.won, s.place, s.players, s.points, coins, s.humanTurns, s.daily != null)
        val levelBefore = p.level
        p = p.copy(
            coins = p.coins + coins,
            xp = p.xp + xp,
            stats = stats,
            history = (listOf(record) + p.history).take(20),
            missionProgress = prog,
            daily = dailyMap,
            dailyStreak = dailyStreak,
            lastDailyDate = lastDaily,
        )

        // achievements
        val earned = mutableListOf<Achievement>()
        fun award(id: String, cond: Boolean) {
            if (cond && id !in p.achievements) Achievements.byId(id)?.let { earned += it }
        }
        val totalWins = p.totalWins
        val totalGames = p.totalGames
        award("first_win", totalWins >= 1)
        award("win_10", totalWins >= 10)
        award("win_50", totalWins >= 50)
        award("play_10", totalGames >= 10)
        award("play_100", totalGames >= 100)
        award("classic_win", s.won && s.mode == GameMode.CLASSIC)
        award("chaos_win", s.won && s.mode == GameMode.CHAOS)
        award("eclipse_win", s.won && s.mode == GameMode.ECLIPSE)
        award("all_modes", GameMode.entries.all { p.stat(it).wins > 0 })
        award("beat_hard", s.won && s.hardestBeaten != null && s.hardestBeaten >= Difficulty.HARD)
        award("beat_genius", s.won && s.hardestBeaten == Difficulty.GENIUS)
        award("full_table", s.won && s.players == 6)
        award("streak_3", streak >= 3)
        award("streak_5", streak >= 5)
        award("stack_10", s.biggestStackSeen >= 10)
        award("stack_20", s.biggestStackHit >= 20)
        award("overload", s.knockouts > 0)
        award("survivor", s.won && s.lastStanding)
        award("eclipse_3", s.eclipses >= 3)
        award("mania_call", p.stats.values.sumOf { it.maniaCalls } >= 10)
        award("catcher", s.catches > 0)
        award("purge", s.maxPurge >= 3)
        award("swap", s.swaps > 0)
        award("quick_win", s.won && s.humanTurns <= 8)
        award("comeback", s.won && s.maxHand >= 12)
        award("daily_1", s.daily != null && stars >= 1)
        award("daily_3star", s.daily != null && stars >= 3)
        award("collector", p.unlocked.size >= 8)
        award("level_5", p.level >= 5)
        award("level_10", p.level >= 10)
        val achCoins = earned.sumOf { it.reward }
        p = p.copy(achievements = p.achievements + earned.map { it.id }, coins = p.coins + achCoins)
        return p to RewardResult(coins + achCoins, xp, levelBefore, p.level, earned, stars)
    }
}
