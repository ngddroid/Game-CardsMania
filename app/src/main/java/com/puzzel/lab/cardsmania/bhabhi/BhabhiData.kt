package com.puzzel.lab.cardsmania.bhabhi

import com.puzzel.lab.cardsmania.engine.model.Difficulty
import kotlinx.serialization.Serializable

/** Options for a game of Bhabhi (also the saved "last setup"). */
@Serializable
data class BhabhiSetup(
    /** 3 to 8 seats. */
    val players: Int = 4,
    /** House rule: nobody can thulla on the opening spade trick; players without a spade just discard. */
    val safeFirstTrick: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Seconds per turn, 0 = off. */
    val turnTimer: Int = 0,
)

@Serializable
data class BhabhiStats(
    val games: Int = 0,
    /** Games where you got away (were not the Bhabhi). */
    val escapes: Int = 0,
    /** Games where you emptied your hand first. */
    val firsts: Int = 0,
    /** Games where you were the Bhabhi. */
    val bhabhis: Int = 0,
    val thullas: Int = 0,
    val pickups: Int = 0,
    /** Games in a row without being the Bhabhi. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

/** Bhabhi progress stored inside the player Profile. */
@Serializable
data class BhabhiProfile(
    val stats: BhabhiStats = BhabhiStats(),
    val setup: BhabhiSetup = BhabhiSetup(),
)

/**
 * Who sits in a Bhabhi seat. avatar uses AvatarRef (>= 1000 player avatars, else bot characters).
 * [remote] = a friend playing on another phone (Wi-Fi multiplayer).
 */
@Serializable
data class BhabhiSeat(val name: String, val avatar: Int, val isHuman: Boolean, val difficulty: Difficulty = Difficulty.NORMAL, val remote: Boolean = false)
