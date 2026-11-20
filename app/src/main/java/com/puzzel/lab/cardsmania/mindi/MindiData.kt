package com.puzzel.lab.cardsmania.mindi

import androidx.annotation.StringRes
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.i18n.Strings
import kotlinx.serialization.Serializable

/** How the trump suit is decided in a Mindi hand. */
@Serializable
enum class MindiTrumpMode(@StringRes val titleRes: Int, @StringRes val shortRes: Int, @StringRes val descriptionRes: Int) {
    HIDE(R.string.data_mindi_trump_hide_title, R.string.data_mindi_trump_hide_short, R.string.data_mindi_trump_hide_desc),
    CUT(R.string.data_mindi_trump_cut_title, R.string.data_mindi_trump_cut_short, R.string.data_mindi_trump_cut_desc),
    OPEN(R.string.data_mindi_trump_open_title, R.string.data_mindi_trump_open_short, R.string.data_mindi_trump_open_desc);

    val title: String get() = Strings.get(titleRes)
    val short: String get() = Strings.get(shortRes)
    val description: String get() = Strings.get(descriptionRes)
}

/** Options for a Mindi match (also the saved "last setup"). */
@Serializable
data class MindiSetup(
    /** 4, 6 or 8 seats. */
    val players: Int = 4,
    /** Double deck: 104 cards, 8 tens (6 or 8 players only). */
    val doubleDeck: Boolean = false,
    val trumpMode: MindiTrumpMode = MindiTrumpMode.HIDE,
    /** Hide mode: the player who triggers the reveal must play a trump if they hold one. */
    val mustTrumpAfterReveal: Boolean = true,
    /** Points to win the match: 1 = single hand. */
    val target: Int = 5,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Seconds per turn, 0 = off. */
    val turnTimer: Int = 0,
)

@Serializable
data class MindiStats(
    val matches: Int = 0,
    val matchWins: Int = 0,
    val hands: Int = 0,
    val handWins: Int = 0,
    val mendikots: Int = 0,
    val whitewashes: Int = 0,
    val tensCaptured: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

/** Mindi progress stored inside the player Profile. */
@Serializable
data class MindiProfile(
    val stats: MindiStats = MindiStats(),
    /** Daily Challenge date (yyyy-MM-dd) → stars earned (1..3). */
    val daily: Map<String, Int> = emptyMap(),
    val setup: MindiSetup = MindiSetup(),
)

/** Who sits in a Mindi seat. avatar uses AvatarRef (>= 1000 player avatars, else bot characters). */
@Serializable
data class MindiSeat(val name: String, val avatar: Int, val isHuman: Boolean, val difficulty: Difficulty = Difficulty.NORMAL, val remote: Boolean = false)
