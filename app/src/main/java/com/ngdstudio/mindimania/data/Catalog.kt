package com.ngdstudio.mindimania.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.i18n.Strings

/** Original bot characters. */
data class Character(
    val id: Int,
    val name: String,
    val emoji: String,
    val color: Color,
    @StringRes val styleRes: Int,
    @StringRes val taglineRes: Int,
) {
    val style: String get() = Strings.get(styleRes)
    val tagline: String get() = Strings.get(taglineRes)
}

object Characters {
    val all = listOf(
        Character(0, "Captain Ace", "🦁", Color(0xFFFFB000), R.string.data_char_captain_ace_style, R.string.data_char_captain_ace_tagline),
        Character(1, "Lady Luck", "🦊", Color(0xFFFF7A1A), R.string.data_char_lady_luck_style, R.string.data_char_lady_luck_tagline),
        Character(2, "Ziggy", "🐸", Color(0xFF19C37D), R.string.data_char_ziggy_style, R.string.data_char_ziggy_tagline),
        Character(3, "Momo", "🐼", Color(0xFFB7A9DA), R.string.data_char_momo_style, R.string.data_char_momo_tagline),
        Character(4, "Rex", "🦖", Color(0xFF6BEFB0), R.string.data_char_rex_style, R.string.data_char_rex_tagline),
        Character(5, "Nova", "🦄", Color(0xFFFF5FA2), R.string.data_char_nova_style, R.string.data_char_nova_tagline),
        Character(6, "Bolt", "🤖", Color(0xFF1E7BFF), R.string.data_char_bolt_style, R.string.data_char_bolt_tagline),
        Character(7, "Pixel", "👾", Color(0xFF8B5CF6), R.string.data_char_pixel_style, R.string.data_char_pixel_tagline),
        Character(8, "Sage", "🦉", Color(0xFFC0A6FF), R.string.data_char_sage_style, R.string.data_char_sage_tagline),
        Character(9, "Kiki", "🐱", Color(0xFFFFD86B), R.string.data_char_kiki_style, R.string.data_char_kiki_tagline),
        Character(10, "Bruno", "🐻", Color(0xFFC77800), R.string.data_char_bruno_style, R.string.data_char_bruno_tagline),
        Character(11, "Lumi", "🐙", Color(0xFF19D3C5), R.string.data_char_lumi_style, R.string.data_char_lumi_tagline),
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
    @StringRes val titleRes: Int,
    val center: Color,
    val edge: Color,
    val accent: Color,
    val particle: Int,
    val price: Int,
    val level: Int,
) {
    ROYAL(R.string.data_theme_royal, Color(0xFF4A1F8F), Color(0xFF0E0428), Color(0xFFFFC93C), 0, 0, 1),
    FELT(R.string.data_theme_felt, Color(0xFF0E7A4A), Color(0xFF02200F), Color(0xFFFFE08A), 0, 500, 2),
    NEON(R.string.data_theme_neon, Color(0xFF1B1050), Color(0xFF020010), Color(0xFF2CFFE1), 2, 900, 4),
    OCEAN(R.string.data_theme_ocean, Color(0xFF0F5AA0), Color(0xFF021230), Color(0xFF9FE3FF), 3, 1200, 6),
    DESERT(R.string.data_theme_desert, Color(0xFFC0582A), Color(0xFF2E0A06), Color(0xFFFFE0A0), 1, 1500, 8),
    COSMIC(R.string.data_theme_cosmic, Color(0xFF3B1E8F), Color(0xFF000000), Color(0xFFFF9DF2), 4, 2200, 10);

    val title: String get() = Strings.get(titleRes)
}

enum class AvatarFrame(@StringRes val titleRes: Int, val colors: List<Color>, val price: Int, val level: Int) {
    BRONZE(R.string.data_frame_bronze, listOf(Color(0xFFE0A06A), Color(0xFF8A4A1A)), 0, 1),
    SILVER(R.string.data_frame_silver, listOf(Color(0xFFFFFFFF), Color(0xFF8C93A8)), 300, 2),
    GOLD(R.string.data_frame_gold, listOf(Color(0xFFFFE89A), Color(0xFFC98A00)), 700, 3),
    RUBY(R.string.data_frame_ruby, listOf(Color(0xFFFF6F95), Color(0xFF9E0F3C)), 900, 5),
    SAPPHIRE(R.string.data_frame_sapphire, listOf(Color(0xFF6FB0FF), Color(0xFF0F4BB0)), 900, 5),
    EMERALD(R.string.data_frame_emerald, listOf(Color(0xFF6BEFB0), Color(0xFF0A8A55)), 900, 5),
    PRISM(R.string.data_frame_prism, listOf(Color(0xFFE0245E), Color(0xFFFFB000), Color(0xFF19C37D), Color(0xFF1E7BFF), Color(0xFFE0245E)), 1800, 8),
    COSMIC(R.string.data_frame_cosmic, listOf(Color(0xFFFF9DF2), Color(0xFF8B5CF6), Color(0xFF19D3C5), Color(0xFFFF9DF2)), 2500, 12);

    val title: String get() = Strings.get(titleRes)
}

object ModeInfo {
    fun title(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> Strings.get(R.string.data_mode_classic_title)
        GameMode.CHAOS -> Strings.get(R.string.data_mode_chaos_title)
        GameMode.ECLIPSE -> Strings.get(R.string.data_mode_eclipse_title)
    }

    fun short(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> Strings.get(R.string.data_mode_classic_short)
        GameMode.CHAOS -> Strings.get(R.string.data_mode_chaos_short)
        GameMode.ECLIPSE -> Strings.get(R.string.data_mode_eclipse_short)
    }

    fun tagline(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> Strings.get(R.string.data_mode_classic_tagline)
        GameMode.CHAOS -> Strings.get(R.string.data_mode_chaos_tagline)
        GameMode.ECLIPSE -> Strings.get(R.string.data_mode_eclipse_tagline)
    }

    fun deckLabel(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> Strings.get(R.string.data_mode_classic_deck)
        GameMode.CHAOS -> Strings.get(R.string.data_mode_chaos_deck)
        GameMode.ECLIPSE -> Strings.get(R.string.data_mode_eclipse_deck)
    }

    fun colors(mode: GameMode): List<Color> = when (mode) {
        GameMode.CLASSIC -> listOf(Color(0xFFFFC93C), Color(0xFFE0245E))
        GameMode.CHAOS -> listOf(Color(0xFFFF5F1F), Color(0xFF8E0F3A))
        GameMode.ECLIPSE -> listOf(Color(0xFF19D3C5), Color(0xFF5B30C2))
    }

    fun defaultRules(mode: GameMode) = when (mode) {
        GameMode.CLASSIC -> com.ngdstudio.mindimania.engine.model.HouseRules(challenge = true)
        GameMode.CHAOS -> com.ngdstudio.mindimania.engine.model.HouseRules(challenge = false)
        GameMode.ECLIPSE -> com.ngdstudio.mindimania.engine.model.HouseRules(challenge = false)
    }
}

object DifficultyInfo {
    fun label(d: Difficulty) = when (d) {
        Difficulty.EASY -> Strings.get(R.string.data_difficulty_easy)
        Difficulty.NORMAL -> Strings.get(R.string.data_difficulty_normal)
        Difficulty.HARD -> Strings.get(R.string.data_difficulty_hard)
        Difficulty.GENIUS -> Strings.get(R.string.data_difficulty_genius)
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

data class Achievement(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: String,
    val reward: Int,
) {
    val title: String get() = Strings.get(titleRes)
    val description: String get() = Strings.get(descriptionRes)
}

object Achievements {
    val all = listOf(
        Achievement("first_win", R.string.data_ach_first_win_title, R.string.data_ach_first_win_desc, "🏆", 100),
        Achievement("win_10", R.string.data_ach_win_10_title, R.string.data_ach_win_10_desc, "🦈", 250),
        Achievement("win_50", R.string.data_ach_win_50_title, R.string.data_ach_win_50_desc, "👑", 1000),
        Achievement("play_10", R.string.data_ach_play_10_title, R.string.data_ach_play_10_desc, "🎴", 100),
        Achievement("play_100", R.string.data_ach_play_100_title, R.string.data_ach_play_100_desc, "💎", 800),
        Achievement("classic_win", R.string.data_ach_classic_win_title, R.string.data_ach_classic_win_desc, "⭐", 150),
        Achievement("chaos_win", R.string.data_ach_chaos_win_title, R.string.data_ach_chaos_win_desc, "🔥", 200),
        Achievement("eclipse_win", R.string.data_ach_eclipse_win_title, R.string.data_ach_eclipse_win_desc, "🌗", 200),
        Achievement("all_modes", R.string.data_ach_all_modes_title, R.string.data_ach_all_modes_desc, "🎖️", 500),
        Achievement("beat_hard", R.string.data_ach_beat_hard_title, R.string.data_ach_beat_hard_desc, "⚔️", 200),
        Achievement("beat_genius", R.string.data_ach_beat_genius_title, R.string.data_ach_beat_genius_desc, "🧠", 500),
        Achievement("full_table", R.string.data_ach_full_table_title, R.string.data_ach_full_table_desc, "🎪", 300),
        Achievement("streak_3", R.string.data_ach_streak_3_title, R.string.data_ach_streak_3_desc, "🔥", 250),
        Achievement("streak_5", R.string.data_ach_streak_5_title, R.string.data_ach_streak_5_desc, "⚡", 600),
        Achievement("stack_10", R.string.data_ach_stack_10_title, R.string.data_ach_stack_10_desc, "🗼", 200),
        Achievement("stack_20", R.string.data_ach_stack_20_title, R.string.data_ach_stack_20_desc, "🏙️", 500),
        Achievement("overload", R.string.data_ach_overload_title, R.string.data_ach_overload_desc, "💥", 250),
        Achievement("survivor", R.string.data_ach_survivor_title, R.string.data_ach_survivor_desc, "🛡️", 300),
        Achievement("eclipse_3", R.string.data_ach_eclipse_3_title, R.string.data_ach_eclipse_3_desc, "🌓", 200),
        Achievement("mania_call", R.string.data_ach_mania_call_title, R.string.data_ach_mania_call_desc, "📣", 150),
        Achievement("catcher", R.string.data_ach_catcher_title, R.string.data_ach_catcher_desc, "🦅", 150),
        Achievement("purge", R.string.data_ach_purge_title, R.string.data_ach_purge_desc, "🧹", 200),
        Achievement("swap", R.string.data_ach_swap_title, R.string.data_ach_swap_desc, "🔄", 100),
        Achievement("quick_win", R.string.data_ach_quick_win_title, R.string.data_ach_quick_win_desc, "⚡", 300),
        Achievement("comeback", R.string.data_ach_comeback_title, R.string.data_ach_comeback_desc, "📈", 300),
        Achievement("daily_1", R.string.data_ach_daily_1_title, R.string.data_ach_daily_1_desc, "📅", 150),
        Achievement("daily_3star", R.string.data_ach_daily_3star_title, R.string.data_ach_daily_3star_desc, "🌟", 300),
        Achievement("collector", R.string.data_ach_collector_title, R.string.data_ach_collector_desc, "🎨", 300),
        Achievement("level_5", R.string.data_ach_level_5_title, R.string.data_ach_level_5_desc, "🚀", 300),
        Achievement("level_10", R.string.data_ach_level_10_title, R.string.data_ach_level_10_desc, "🏅", 800),
        Achievement("spider_win", R.string.data_ach_spider_win_title, R.string.data_ach_spider_win_desc, "🕸️", 150),
        Achievement("spider_2", R.string.data_ach_spider_2_title, R.string.data_ach_spider_2_desc, "♥️", 300),
        Achievement("spider_4", R.string.data_ach_spider_4_title, R.string.data_ach_spider_4_desc, "🕷️", 800),
        Achievement("spider_daily", R.string.data_ach_spider_daily_title, R.string.data_ach_spider_daily_desc, "📆", 200),
        Achievement("spider_fast", R.string.data_ach_spider_fast_title, R.string.data_ach_spider_fast_desc, "⏱️", 300),
        Achievement("mindi_win", R.string.data_ach_mindi_win_title, R.string.data_ach_mindi_win_desc, "🤝", 150),
        Achievement("mindi_mendikot", R.string.data_ach_mindi_mendikot_title, R.string.data_ach_mindi_mendikot_desc, "🔟", 300),
        Achievement("mindi_whitewash", R.string.data_ach_mindi_whitewash_title, R.string.data_ach_mindi_whitewash_desc, "🧺", 600),
        Achievement("mindi_hide", R.string.data_ach_mindi_hide_title, R.string.data_ach_mindi_hide_desc, "🙈", 200),
        Achievement("mindi_cut", R.string.data_ach_mindi_cut_title, R.string.data_ach_mindi_cut_desc, "✂️", 200),
        Achievement("mindi_big", R.string.data_ach_mindi_big_title, R.string.data_ach_mindi_big_desc, "🎪", 300),
        Achievement("mindi_expert", R.string.data_ach_mindi_expert_title, R.string.data_ach_mindi_expert_desc, "🧠", 500),
        Achievement("mindi_daily", R.string.data_ach_mindi_daily_title, R.string.data_ach_mindi_daily_desc, "📅", 250),
        Achievement("bhabhi_escape", R.string.data_ach_bhabhi_escape_title, R.string.data_ach_bhabhi_escape_desc, "🏃", 150),
        Achievement("bhabhi_first", R.string.data_ach_bhabhi_first_title, R.string.data_ach_bhabhi_first_desc, "🥇", 200),
        Achievement("bhabhi_thulla", R.string.data_ach_bhabhi_thulla_title, R.string.data_ach_bhabhi_thulla_desc, "💥", 200),
        Achievement("bhabhi_clean", R.string.data_ach_bhabhi_clean_title, R.string.data_ach_bhabhi_clean_desc, "🧼", 300),
        Achievement("bhabhi_big", R.string.data_ach_bhabhi_big_title, R.string.data_ach_bhabhi_big_desc, "🎪", 250),
        Achievement("bhabhi_expert", R.string.data_ach_bhabhi_expert_title, R.string.data_ach_bhabhi_expert_desc, "🧠", 500),
        Achievement("tp_win", R.string.data_ach_tp_win_title, R.string.data_ach_tp_win_desc, "👑", 150),
        Achievement("tp_trail", R.string.data_ach_tp_trail_title, R.string.data_ach_tp_trail_desc, "🎰", 300),
        Achievement("tp_pure", R.string.data_ach_tp_pure_title, R.string.data_ach_tp_pure_desc, "🌈", 250),
        Achievement("tp_blind", R.string.data_ach_tp_blind_title, R.string.data_ach_tp_blind_desc, "🙈", 200),
        Achievement("tp_bluff", R.string.data_ach_tp_bluff_title, R.string.data_ach_tp_bluff_desc, "🃏", 250),
        Achievement("tp_expert", R.string.data_ach_tp_expert_title, R.string.data_ach_tp_expert_desc, "🧠", 500),
        Achievement("rm_win", R.string.data_ach_rm_win_title, R.string.data_ach_rm_win_desc, "🃏", 150),
        Achievement("rm_fast", R.string.data_ach_rm_fast_title, R.string.data_ach_rm_fast_desc, "⚡", 300),
        Achievement("rm_clean", R.string.data_ach_rm_clean_title, R.string.data_ach_rm_clean_desc, "✨", 300),
        Achievement("rm_expert", R.string.data_ach_rm_expert_title, R.string.data_ach_rm_expert_desc, "🧠", 500),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** [title] is a format string with one %d for [target] (callers do `title.format(target)`). */
data class MissionDef(val id: String, @StringRes val titleRes: Int, val target: Int, val reward: Int, val icon: String) {
    val title: String get() = Strings.get(titleRes)
}

object Missions {
    private val pool = listOf(
        MissionDef("win", R.string.data_mission_win, 5, 300, "🏆"),
        MissionDef("play", R.string.data_mission_play, 8, 250, "🎴"),
        MissionDef("actions", R.string.data_mission_actions, 40, 250, "⚡"),
        MissionDef("chaos_play", R.string.data_mission_chaos_play, 3, 250, "🔥"),
        MissionDef("eclipse_play", R.string.data_mission_eclipse_play, 3, 250, "🌗"),
        MissionDef("classic_win", R.string.data_mission_classic_win, 2, 300, "⭐"),
        MissionDef("mania", R.string.data_mission_mania, 6, 200, "📣"),
        MissionDef("prism", R.string.data_mission_prism, 15, 250, "💎"),
        MissionDef("draw_attack", R.string.data_mission_draw_attack, 60, 300, "🎯"),
    )

    /** Three missions for the given week number (deterministic). */
    fun forWeek(week: Int): List<MissionDef> {
        val r = java.util.Random(week.toLong() * 7919)
        return pool.shuffled(r).take(3)
    }
}
