package com.puzzel.lab.cardsmania.data

import androidx.compose.ui.graphics.Color
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameMode

/** Original bot characters. */
data class Character(
    val id: Int,
    val name: String,
    val emoji: String,
    val color: Color,
    val style: String,
    val tagline: String,
)

object Characters {
    val all = listOf(
        Character(0, "Captain Ace", "🦁", Color(0xFFFFB000), "Bold", "Fortune favors the brave!"),
        Character(1, "Lady Luck", "🦊", Color(0xFFFF7A1A), "Lucky", "Watch me flip it!"),
        Character(2, "Ziggy", "🐸", Color(0xFF19C37D), "Chaotic", "Ribbit... chaos time!"),
        Character(3, "Momo", "🐼", Color(0xFFB7A9DA), "Calm", "Slow and steady."),
        Character(4, "Rex", "🦖", Color(0xFF6BEFB0), "Aggressive", "Stack 'em high!"),
        Character(5, "Nova", "🦄", Color(0xFFFF5FA2), "Dreamy", "Sparkles incoming ✨"),
        Character(6, "Bolt", "🤖", Color(0xFF1E7BFF), "Logical", "Calculating optimal play."),
        Character(7, "Pixel", "👾", Color(0xFF8B5CF6), "Tricky", "Glitching your plans!"),
        Character(8, "Sage", "🦉", Color(0xFFC0A6FF), "Wise", "Every card has a story."),
        Character(9, "Kiki", "🐱", Color(0xFFFFD86B), "Playful", "Pounce!"),
        Character(10, "Bruno", "🐻", Color(0xFFC77800), "Hoarder", "I keep the good stuff."),
        Character(11, "Lumi", "🐙", Color(0xFF19D3C5), "Sneaky", "Eight arms, eight plans."),
    )

    fun byId(id: Int) = all.getOrElse(id) { all[0] }
}

object PlayerAvatars {
    val all = listOf("😎", "🦊", "🐼", "🐯", "🦁", "🐸", "🐵", "🐨", "🐙", "🦄", "🐲", "🤖", "👽", "🐧", "🦉", "🐺", "🐱", "🐰", "🦋", "🐳")
    fun get(i: Int) = all.getOrElse(i) { all[0] }
}

/** Avatar in a game config: values >= 1000 are player avatars, else bot character ids. */
object AvatarRef {
    fun player(i: Int) = 1000 + i
    fun emoji(ref: Int): String = if (ref >= 1000) PlayerAvatars.get(ref - 1000) else Characters.byId(ref).emoji
    fun color(ref: Int): Color = if (ref >= 1000) Color(0xFFFFC93C) else Characters.byId(ref).color
}

enum class TableTheme(
    val title: String,
    val center: Color,
    val edge: Color,
    val accent: Color,
    val particle: Int,
    val price: Int,
    val level: Int,
) {
    ROYAL("Royal Velvet", Color(0xFF4A1F8F), Color(0xFF0E0428), Color(0xFFFFC93C), 0, 0, 1),
    FELT("Emerald Felt", Color(0xFF0E7A4A), Color(0xFF02200F), Color(0xFFFFE08A), 0, 500, 2),
    NEON("Neon Night", Color(0xFF1B1050), Color(0xFF020010), Color(0xFF2CFFE1), 2, 900, 4),
    OCEAN("Ocean Deep", Color(0xFF0F5AA0), Color(0xFF021230), Color(0xFF9FE3FF), 3, 1200, 6),
    DESERT("Desert Sunset", Color(0xFFC0582A), Color(0xFF2E0A06), Color(0xFFFFE0A0), 1, 1500, 8),
    COSMIC("Cosmic", Color(0xFF3B1E8F), Color(0xFF000000), Color(0xFFFF9DF2), 4, 2200, 10),
}

enum class AvatarFrame(val title: String, val colors: List<Color>, val price: Int, val level: Int) {
    BRONZE("Bronze", listOf(Color(0xFFE0A06A), Color(0xFF8A4A1A)), 0, 1),
    SILVER("Silver", listOf(Color(0xFFFFFFFF), Color(0xFF8C93A8)), 300, 2),
    GOLD("Gold", listOf(Color(0xFFFFE89A), Color(0xFFC98A00)), 700, 3),
    RUBY("Ruby", listOf(Color(0xFFFF6F95), Color(0xFF9E0F3C)), 900, 5),
    SAPPHIRE("Sapphire", listOf(Color(0xFF6FB0FF), Color(0xFF0F4BB0)), 900, 5),
    EMERALD("Emerald", listOf(Color(0xFF6BEFB0), Color(0xFF0A8A55)), 900, 5),
    PRISM("Prism", listOf(Color(0xFFE0245E), Color(0xFFFFB000), Color(0xFF19C37D), Color(0xFF1E7BFF), Color(0xFFE0245E)), 1800, 8),
    COSMIC("Cosmic", listOf(Color(0xFFFF9DF2), Color(0xFF8B5CF6), Color(0xFF19D3C5), Color(0xFFFF9DF2)), 2500, 12),
}

object ModeInfo {
    fun title(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> "Classic Mania"
        GameMode.CHAOS -> "Merciless Mania"
        GameMode.ECLIPSE -> "Flip Mania"
    }

    fun short(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> "Classic"
        GameMode.CHAOS -> "Merciless"
        GameMode.ECLIPSE -> "Flip"
    }

    fun tagline(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> "Match colors & numbers. First to empty their hand wins!"
        GameMode.CHAOS -> "Stack draws, swap hands, skip everyone. 25 cards and you're out!"
        GameMode.ECLIPSE -> "Two-sided cards. Flip from the Light side to the Dark side!"
    }

    fun deckLabel(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> "108 cards"
        GameMode.CHAOS -> "168 cards"
        GameMode.ECLIPSE -> "112 two-sided cards"
    }

    fun colors(mode: GameMode): List<Color> = when (mode) {
        GameMode.CLASSIC -> listOf(Color(0xFFFFC93C), Color(0xFFE0245E))
        GameMode.CHAOS -> listOf(Color(0xFFFF5F1F), Color(0xFF8E0F3A))
        GameMode.ECLIPSE -> listOf(Color(0xFF19D3C5), Color(0xFF5B30C2))
    }

    fun defaultRules(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> com.puzzel.lab.cardsmania.engine.model.HouseRules(challenge = true)
        GameMode.CHAOS -> com.puzzel.lab.cardsmania.engine.model.HouseRules(challenge = false)
        GameMode.ECLIPSE -> com.puzzel.lab.cardsmania.engine.model.HouseRules(challenge = false)
    }
}

object DifficultyInfo {
    fun label(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Easy"
        Difficulty.NORMAL -> "Normal"
        Difficulty.HARD -> "Hard"
        Difficulty.GENIUS -> "Genius"
    }

    fun color(d: Difficulty) = when (d) {
        Difficulty.EASY -> Color(0xFF19C37D)
        Difficulty.NORMAL -> Color(0xFF1E7BFF)
        Difficulty.HARD -> Color(0xFFFF7A1A)
        Difficulty.GENIUS -> Color(0xFFE0245E)
    }

    /** Coin multiplier for beating harder bots. */
    fun multiplier(d: Difficulty) = when (d) {
        Difficulty.EASY -> 1.0
        Difficulty.NORMAL -> 1.3
        Difficulty.HARD -> 1.7
        Difficulty.GENIUS -> 2.2
    }
}

data class Achievement(val id: String, val title: String, val description: String, val icon: String, val reward: Int)

object Achievements {
    val all = listOf(
        Achievement("first_win", "First Victory", "Win your first match.", "🏆", 100),
        Achievement("win_10", "Card Shark", "Win 10 matches.", "🦈", 250),
        Achievement("win_50", "Mania Master", "Win 50 matches.", "👑", 1000),
        Achievement("play_10", "Regular", "Play 10 matches.", "🎴", 100),
        Achievement("play_100", "Devoted", "Play 100 matches.", "💎", 800),
        Achievement("classic_win", "Classic Champion", "Win a Classic Mania match.", "⭐", 150),
        Achievement("chaos_win", "Merciless Master", "Win a Merciless Mania match.", "🔥", 200),
        Achievement("eclipse_win", "Flip Master", "Win a Flip Mania match.", "🌗", 200),
        Achievement("all_modes", "Triple Crown", "Win in all three modes.", "🎖️", 500),
        Achievement("beat_hard", "Giant Slayer", "Win against a Hard bot.", "⚔️", 200),
        Achievement("beat_genius", "Mastermind", "Win against a Genius bot.", "🧠", 500),
        Achievement("full_table", "Full House", "Win a match with 6 players.", "🎪", 300),
        Achievement("streak_3", "Hot Streak", "Win 3 matches in a row.", "🔥", 250),
        Achievement("streak_5", "Unstoppable", "Win 5 matches in a row.", "⚡", 600),
        Achievement("stack_10", "Tower of Pain", "Be part of a +10 or bigger stack.", "🗼", 200),
        Achievement("stack_20", "Skyscraper", "Hit someone with a +20 stack.", "🏙️", 500),
        Achievement("overload", "Overloader", "Knock out a rival with the Overload rule.", "💥", 250),
        Achievement("survivor", "Last One Standing", "Win Merciless as the last player standing.", "🛡️", 300),
        Achievement("eclipse_3", "Light & Dark", "Play 3 Flip cards in one match.", "🌓", 200),
        Achievement("mania_call", "Loud & Proud", "Call MANIA! 10 times.", "📣", 150),
        Achievement("catcher", "Eagle Eye", "Catch a rival who forgot to call MANIA!.", "🦅", 150),
        Achievement("purge", "Clean Sweep", "Discard 3 or more cards at once with Discard All.", "🧹", 200),
        Achievement("swap", "Hand Thief", "Swap hands with a Seven.", "🔄", 100),
        Achievement("quick_win", "Lightning Round", "Win a match in 8 of your own turns or fewer.", "⚡", 300),
        Achievement("comeback", "Comeback Kid", "Win after holding 12+ cards.", "📈", 300),
        Achievement("daily_1", "Daily Player", "Complete a Daily Challenge.", "📅", 150),
        Achievement("daily_3star", "Perfect Day", "Earn 3 stars in a Daily Challenge.", "🌟", 300),
        Achievement("collector", "Collector", "Unlock 5 cosmetic items.", "🎨", 300),
        Achievement("level_5", "Rising Star", "Reach level 5.", "🚀", 300),
        Achievement("level_10", "Legend", "Reach level 10.", "🏅", 800),
        Achievement("spider_win", "Web Weaver", "Win a game of Spider Solitaire.", "🕸️", 150),
        Achievement("spider_2", "Double Trouble", "Win Spider Solitaire with 2 suits.", "♥️", 300),
        Achievement("spider_4", "Eight Legs Champion", "Win Spider Solitaire with 4 suits.", "🕷️", 800),
        Achievement("spider_daily", "Daily Spinner", "Solve a Spider Daily Challenge.", "📆", 200),
        Achievement("spider_fast", "Speed Spinner", "Win a 1-suit Spider game in under 5 minutes.", "⏱️", 300),
        Achievement("mindi_win", "Team Player", "Win a Mindi match.", "🤝", 150),
        Achievement("mindi_mendikot", "Mendikot!", "Capture all four 10s in one hand.", "🔟", 300),
        Achievement("mindi_whitewash", "Whitewash", "Win every trick of a Mindi hand.", "🧺", 600),
        Achievement("mindi_hide", "Secret Keeper", "Win a Mindi match in Hide Trump mode.", "🙈", 200),
        Achievement("mindi_cut", "Sharp Cutter", "Win a Mindi match in Cut Trump mode.", "✂️", 200),
        Achievement("mindi_big", "Big Table", "Win a 6- or 8-player Mindi match.", "🎪", 300),
        Achievement("mindi_expert", "Grandmaster", "Win a Mindi match against Expert bots.", "🧠", 500),
        Achievement("mindi_daily", "Daily Dehla", "Earn 3 stars in a Mindi Daily Challenge.", "📅", 250),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

data class MissionDef(val id: String, val title: String, val target: Int, val reward: Int, val icon: String)

object Missions {
    private val pool = listOf(
        MissionDef("win", "Win %d matches", 5, 300, "🏆"),
        MissionDef("play", "Play %d matches", 8, 250, "🎴"),
        MissionDef("actions", "Play %d action cards", 40, 250, "⚡"),
        MissionDef("chaos_play", "Play %d Merciless matches", 3, 250, "🔥"),
        MissionDef("eclipse_play", "Play %d Flip matches", 3, 250, "🌗"),
        MissionDef("classic_win", "Win %d Classic matches", 2, 300, "⭐"),
        MissionDef("mania", "Call MANIA! %d times", 6, 200, "📣"),
        MissionDef("prism", "Play %d Wild cards", 15, 250, "💎"),
        MissionDef("draw_attack", "Make rivals draw %d cards", 60, 300, "🎯"),
    )

    /** Three missions for the given week number (deterministic). */
    fun forWeek(week: Int): List<MissionDef> {
        val r = java.util.Random(week.toLong() * 7919)
        return pool.shuffled(r).take(3)
    }
}
