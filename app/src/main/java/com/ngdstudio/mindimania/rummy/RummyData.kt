package com.ngdstudio.mindimania.rummy

import com.ngdstudio.mindimania.engine.model.Difficulty
import kotlinx.serialization.Serializable

/** Options for a Rummy table (also the saved "last setup"). */
@Serializable
data class RummySetup(
    /** 2 to 6 seats. */
    val players: Int = 3,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Seconds per turn, 0 = off. */
    val turnTimer: Int = 0,
)

@Serializable
data class RummyStats(
    val hands: Int = 0,
    val handsWon: Int = 0,
    /** Valid declarations made. */
    val declares: Int = 0,
    val drops: Int = 0,
    val wrongShows: Int = 0,
    /** Fewest of my own turns before a valid declaration (0 = never). */
    val fastestDeclare: Int = 0,
    val bestStack: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

/** Rummy progress stored inside the player Profile. */
@Serializable
data class RummyProfile(
    val stats: RummyStats = RummyStats(),
    val setup: RummySetup = RummySetup(),
)

/** Who sits in a seat. avatar uses AvatarRef (>= 1000 player avatars, else bot characters). */
@Serializable
data class RummySeat(val name: String, val avatar: Int, val isHuman: Boolean, val difficulty: Difficulty = Difficulty.NORMAL, val remote: Boolean = false)
