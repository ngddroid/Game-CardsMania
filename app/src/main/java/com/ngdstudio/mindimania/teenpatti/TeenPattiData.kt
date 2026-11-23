package com.ngdstudio.mindimania.teenpatti

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlinx.serialization.Serializable

/** Options for a Teen Patti match (also the saved "last setup"). */
@Serializable
data class TeenPattiSetup(
    /** 3 to 6 seats. */
    val players: Int = 4,
    /**
     * Hands in a match; 0 = an endless table (the game's default): hand after hand until the player leaves, and anyone
     * who runs out of chips gets a fresh stack. A fixed count ends the match after that many hands (or when only one
     * player has chips left).
     */
    val hands: Int = 0,
    /** A seen player may ask the previous seen player for a private comparison. */
    val sideshow: Boolean = true,
    /** House rule: A-2-3 is the second-best sequence (after A-K-Q). Off = A-2-3 is the lowest sequence. */
    val a23Second: Boolean = true,
    /** Which variation is played (MIXED = a different one every hand). */
    val variant: com.ngdstudio.mindimania.teenpatti.TPVariant = com.ngdstudio.mindimania.teenpatti.TPVariant.CLASSIC,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Seconds per turn, 0 = off. */
    val turnTimer: Int = 0,
)

@Serializable
data class TeenPattiStats(
    val games: Int = 0,
    /** Matches finished with the most chips (fixed-length matches only). */
    val wins: Int = 0,
    /** Most chips ever held at the table. */
    val bestStack: Int = 0,
    /** Free refills taken after running out of chips. */
    val refills: Int = 0,
    val hands: Int = 0,
    val handsWon: Int = 0,
    /** Hands won without ever looking at the cards. */
    val blindWins: Int = 0,
    val biggestPot: Int = 0,
    /** Best hand kind ever held (TPKind ordinal), -1 = none yet. */
    val bestKind: Int = -1,
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

/** Teen Patti progress stored inside the player Profile. */
@Serializable
data class TeenPattiProfile(
    val stats: TeenPattiStats = TeenPattiStats(),
    val setup: TeenPattiSetup = TeenPattiSetup(),
)

/** Who sits in a seat. avatar uses AvatarRef (>= 1000 player avatars, else bot characters). */
@Serializable
data class TeenPattiSeat(val name: String, val avatar: Int, val isHuman: Boolean, val difficulty: Difficulty = Difficulty.NORMAL, val remote: Boolean = false)
