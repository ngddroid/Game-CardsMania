package com.ngdstudio.mindimania.spider

import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.i18n.Strings
import kotlinx.serialization.Serializable

@Serializable
data class SpiderModeStats(
    val played: Int = 0,
    val won: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val bestTimeMs: Long = 0L,
    val bestMoves: Int = 0,
    val bestScore: Int = 0,
    val totalTimeMs: Long = 0L,
)

/** Spider Solitaire progress and preferences (stored inside the player Profile). */
@Serializable
data class SpiderProfile(
    /** Keyed by number of suits (1, 2, 4). */
    val stats: Map<Int, SpiderModeStats> = emptyMap(),
    /** Daily Challenge date (yyyy-MM-dd) → best time in ms. */
    val dailySolved: Map<String, Long> = emptyMap(),
    /** SpiderBackground name. */
    val background: String = "FELT",
    val fourColor: Boolean = false,
    val tapToMove: Boolean = true,
    val showTimer: Boolean = true,
    val lastSuits: Int = 1,
    /** Tap-then-tap-target controls instead of Smart tap. */
    val selectMode: Boolean = false,
    /** Unused: hints now come only from the Hint button. Kept so saved profiles still load. */
    val idleHint: Boolean = false,
    /** Stock and completed runs on the left. */
    val leftHanded: Boolean = false,
    /** Graded 2/4-suit deals played lately (newest last), so the same deal doesn't come back soon. */
    val recentDeals: List<Long> = emptyList(),
) {
    fun stat(suits: Int) = stats[suits] ?: SpiderModeStats()
}

/** A Spider game in progress (file: spider_save.json). */
@Serializable
data class SpiderSave(
    val state: SpiderState,
    val history: List<SpiderState> = emptyList(),
    val elapsedMs: Long = 0L,
    val daily: String? = null,
    val counted: Boolean = false,
)

object SpiderRules {
    fun difficulty(suits: Int) = when (suits) {
        1 -> Strings.get(R.string.data_spider_difficulty_easy)
        2 -> Strings.get(R.string.data_spider_difficulty_medium)
        else -> Strings.get(R.string.data_spider_difficulty_hard)
    }

    fun winCoins(suits: Int) = when (suits) {
        1 -> 80
        2 -> 200
        else -> 450
    }

    fun winXp(suits: Int) = when (suits) {
        1 -> 90
        2 -> 160
        else -> 300
    }

    /** How many recent graded deals are remembered (and not dealt again). */
    const val RECENT_DEALS = 60

    /** The graded deals for 2 or 4 suits: every one can be won, and none is an easy one (see SpiderDealSolver). */
    fun gradedDeals(suits: Int): LongArray = when (suits) {
        2 -> SpiderDeals.SUITS_2
        4 -> SpiderDeals.SUITS_4
        else -> LongArray(0)
    }

    /** A graded deal for [suits] that isn't in [recent] (random among the rest), or null for 1 suit. */
    fun pickGradedDeal(suits: Int, recent: List<Long>, random: kotlin.random.Random): Long? {
        val all = gradedDeals(suits)
        if (all.isEmpty()) return null
        val fresh = all.filter { it !in recent }
        return (fresh.ifEmpty { all.toList() }).random(random)
    }

    /** Daily Challenge seed for a date: a guaranteed-winnable 1-suit deal (same for everyone). */
    fun dailySeed(date: String): Long = SpiderSolver.winnableSeed(1, date.replace("-", "").toLong() * 7919L + 17L)

    fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
        else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }
}
