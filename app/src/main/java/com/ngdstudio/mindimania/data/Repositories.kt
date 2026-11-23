package com.ngdstudio.mindimania.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.HouseRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "cardsmania")

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
}

// ----------------------------------------------------------------------------------- settings

enum class SortMode { COLOR, VALUE, NONE }

data class Settings(
    val musicVolume: Float = 0.6f,
    val sfxVolume: Float = 0.9f,
    val haptics: Boolean = true,
    /** 0 relaxed, 1 normal, 2 fast */
    val speed: Int = 1,
    val reduceMotion: Boolean = false,
    val colorBlind: Boolean = true,
    val largeCards: Boolean = false,
    val leftHanded: Boolean = false,
    val sortMode: SortMode = SortMode.COLOR,
    /** seconds, 0 = off */
    val turnTimer: Int = 0,
    val confirmExit: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Daily reminder notification (local, scheduled with WorkManager). */
    val reminders: Boolean = true,
    /** Game news and events sent from the Firebase console (push). */
    val newsPush: Boolean = true,
    /** Anonymous usage statistics (Firebase Analytics) and crash reports (Crashlytics). */
    val shareData: Boolean = true,
    /** We already asked for the notification permission (Android 13+), so don't ask again on start. */
    val notifAsked: Boolean = false,
    /** When the app was last opened (the reminder is skipped if the player was here recently). */
    val lastOpenAt: Long = 0L,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val music = floatPreferencesKey("music")
        val sfx = floatPreferencesKey("sfx")
        val haptics = booleanPreferencesKey("haptics")
        val speed = intPreferencesKey("speed")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val colorBlind = booleanPreferencesKey("color_blind")
        val largeCards = booleanPreferencesKey("large_cards")
        val leftHanded = booleanPreferencesKey("left_handed")
        val sortMode = stringPreferencesKey("sort_mode")
        val turnTimer = intPreferencesKey("turn_timer")
        val confirmExit = booleanPreferencesKey("confirm_exit")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val reminders = booleanPreferencesKey("reminders")
        val newsPush = booleanPreferencesKey("news_push")
        val shareData = booleanPreferencesKey("share_data")
        val notifAsked = booleanPreferencesKey("notif_asked")
        val lastOpenAt = longPreferencesKey("last_open_at")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            musicVolume = p[K.music] ?: 0.6f,
            sfxVolume = p[K.sfx] ?: 0.9f,
            haptics = p[K.haptics] ?: true,
            speed = p[K.speed] ?: 1,
            reduceMotion = p[K.reduceMotion] ?: false,
            colorBlind = p[K.colorBlind] ?: true,
            largeCards = p[K.largeCards] ?: false,
            leftHanded = p[K.leftHanded] ?: false,
            sortMode = runCatching { SortMode.valueOf(p[K.sortMode] ?: "COLOR") }.getOrDefault(SortMode.COLOR),
            turnTimer = p[K.turnTimer] ?: 0,
            confirmExit = p[K.confirmExit] ?: true,
            onboardingDone = p[K.onboarding] ?: false,
            reminders = p[K.reminders] ?: true,
            newsPush = p[K.newsPush] ?: true,
            shareData = p[K.shareData] ?: true,
            notifAsked = p[K.notifAsked] ?: false,
            lastOpenAt = p[K.lastOpenAt] ?: 0L,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.store.edit { p ->
            val cur = Settings(
                musicVolume = p[K.music] ?: 0.6f,
                sfxVolume = p[K.sfx] ?: 0.9f,
                haptics = p[K.haptics] ?: true,
                speed = p[K.speed] ?: 1,
                reduceMotion = p[K.reduceMotion] ?: false,
                colorBlind = p[K.colorBlind] ?: true,
                largeCards = p[K.largeCards] ?: false,
                leftHanded = p[K.leftHanded] ?: false,
                sortMode = runCatching { SortMode.valueOf(p[K.sortMode] ?: "COLOR") }.getOrDefault(SortMode.COLOR),
                turnTimer = p[K.turnTimer] ?: 0,
                confirmExit = p[K.confirmExit] ?: true,
                onboardingDone = p[K.onboarding] ?: false,
                reminders = p[K.reminders] ?: true,
                newsPush = p[K.newsPush] ?: true,
                shareData = p[K.shareData] ?: true,
                notifAsked = p[K.notifAsked] ?: false,
                lastOpenAt = p[K.lastOpenAt] ?: 0L,
            )
            val n = transform(cur)
            p[K.music] = n.musicVolume
            p[K.sfx] = n.sfxVolume
            p[K.haptics] = n.haptics
            p[K.speed] = n.speed
            p[K.reduceMotion] = n.reduceMotion
            p[K.colorBlind] = n.colorBlind
            p[K.largeCards] = n.largeCards
            p[K.leftHanded] = n.leftHanded
            p[K.sortMode] = n.sortMode.name
            p[K.turnTimer] = n.turnTimer
            p[K.confirmExit] = n.confirmExit
            p[K.onboarding] = n.onboardingDone
            p[K.reminders] = n.reminders
            p[K.newsPush] = n.newsPush
            p[K.shareData] = n.shareData
            p[K.notifAsked] = n.notifAsked
            p[K.lastOpenAt] = n.lastOpenAt
        }
    }
}

// ----------------------------------------------------------------------------------- profile

@Serializable
data class ModeStats(
    val games: Int = 0,
    val wins: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val biggestStack: Int = 0,
    val fastestWin: Int = 0,
    val cardsPlayed: Int = 0,
    val actionsPlayed: Int = 0,
    val maniaCalls: Int = 0,
    val catches: Int = 0,
    val knockouts: Int = 0,
    val points: Int = 0,
)

@Serializable
data class MatchRecord(
    val time: Long,
    val mode: GameMode,
    val won: Boolean,
    val place: Int,
    val players: Int,
    val points: Int,
    val coins: Int,
    val turns: Int,
    val daily: Boolean = false,
)

@Serializable
data class BotPick(val characterId: Int, val difficulty: Difficulty)

@Serializable
data class SetupPrefs(
    val mode: GameMode = GameMode.CLASSIC,
    val bots: List<BotPick> = listOf(BotPick(0, Difficulty.NORMAL), BotPick(5, Difficulty.NORMAL), BotPick(6, Difficulty.NORMAL)),
    val handSize: Int = 7,
    val deckCount: Int = 1,
    val targetScore: Int = 0,
    val rules: Map<GameMode, HouseRules> = mapOf(
        GameMode.CLASSIC to ModeInfo.defaultRules(GameMode.CLASSIC),
        GameMode.CHAOS to ModeInfo.defaultRules(GameMode.CHAOS),
        GameMode.ECLIPSE to ModeInfo.defaultRules(GameMode.ECLIPSE),
    ),
)

@Serializable
data class Profile(
    val name: String = "Player",
    val avatar: Int = 0,
    val coins: Int = 500,
    val xp: Int = 0,
    val unlocked: Set<String> = setOf("theme:ROYAL", "back:ROYAL", "frame:BRONZE"),
    val theme: String = "ROYAL",
    val back: String = "ROYAL",
    val frame: String = "BRONZE",
    val stats: Map<GameMode, ModeStats> = emptyMap(),
    val achievements: Set<String> = emptySet(),
    val lastGiftAt: Long = 0L,
    val giftStreak: Int = 0,
    val daily: Map<String, Int> = emptyMap(),
    val dailyStreak: Int = 0,
    val lastDailyDate: String = "",
    val history: List<MatchRecord> = emptyList(),
    val missionWeek: Int = 0,
    val missionProgress: Map<String, Int> = emptyMap(),
    val missionClaimed: Set<String> = emptySet(),
    val setup: SetupPrefs = SetupPrefs(),
    val profileCreated: Boolean = false,
    val spider: com.ngdstudio.mindimania.spider.SpiderProfile = com.ngdstudio.mindimania.spider.SpiderProfile(),
    val mindi: com.ngdstudio.mindimania.mindi.MindiProfile = com.ngdstudio.mindimania.mindi.MindiProfile(),
    val bhabhi: com.ngdstudio.mindimania.bhabhi.BhabhiProfile = com.ngdstudio.mindimania.bhabhi.BhabhiProfile(),
    val teenPatti: com.ngdstudio.mindimania.teenpatti.TeenPattiProfile = com.ngdstudio.mindimania.teenpatti.TeenPattiProfile(),
    val rummy: com.ngdstudio.mindimania.rummy.RummyProfile = com.ngdstudio.mindimania.rummy.RummyProfile(),
) {
    val level: Int get() = Leveling.levelFor(xp)
    val totalWins: Int get() = stats.values.sumOf { it.wins }
    val totalGames: Int get() = stats.values.sumOf { it.games }
    fun stat(mode: GameMode) = stats[mode] ?: ModeStats()
    val tableTheme: TableTheme get() = runCatching { TableTheme.valueOf(theme) }.getOrDefault(TableTheme.ROYAL)
    val cardBack: com.ngdstudio.mindimania.ui.render.CardBack
        get() = runCatching { com.ngdstudio.mindimania.ui.render.CardBack.valueOf(back) }.getOrDefault(com.ngdstudio.mindimania.ui.render.CardBack.ROYAL)
    val avatarFrame: AvatarFrame get() = runCatching { AvatarFrame.valueOf(frame) }.getOrDefault(AvatarFrame.BRONZE)
}

object Leveling {
    /** XP needed to go from level n to n+1. */
    fun stepFor(level: Int) = 200 + (level - 1) * 100

    fun levelFor(xp: Int): Int {
        var lvl = 1
        var rem = xp
        while (rem >= stepFor(lvl)) {
            rem -= stepFor(lvl)
            lvl++
        }
        return lvl
    }

    /** Progress 0..1 within the current level, and xp into level / needed. */
    fun progress(xp: Int): Triple<Float, Int, Int> {
        var lvl = 1
        var rem = xp
        while (rem >= stepFor(lvl)) {
            rem -= stepFor(lvl)
            lvl++
        }
        val need = stepFor(lvl)
        return Triple(rem / need.toFloat(), rem, need)
    }
}

class ProfileRepository(private val context: Context) {
    private val key = stringPreferencesKey("profile_json")

    val profile: Flow<Profile> = context.store.data.map { p ->
        p[key]?.let { runCatching { AppJson.decodeFromString(Profile.serializer(), it) }.getOrNull() } ?: Profile()
    }

    suspend fun current(): Profile = profile.first()

    suspend fun update(transform: (Profile) -> Profile): Profile {
        var result = Profile()
        context.store.edit { p ->
            val cur = p[key]?.let { runCatching { AppJson.decodeFromString(Profile.serializer(), it) }.getOrNull() } ?: Profile()
            result = transform(cur)
            p[key] = AppJson.encodeToString(Profile.serializer(), result)
        }
        return result
    }

    suspend fun reset() {
        context.store.edit { it.clear() }
    }
}

// ----------------------------------------------------------------------------------- saved game

@Serializable
data class SavedGame(val state: GameState, val daily: String? = null, val humanTurns: Int = 0)

class SavedGameStore(context: Context) {
    private val file = File(context.filesDir, "saved_game.json")

    /** Queues the save (ordered and crash-safe, see [SaveFiles]). */
    fun saveLater(game: SavedGame) = SaveFiles.write(file) { AppJson.encodeToString(SavedGame.serializer(), game) }

    suspend fun load(): SavedGame? {
        val text = SaveFiles.read(file) ?: return null
        return withContext(Dispatchers.Default) { runCatching { AppJson.decodeFromString(SavedGame.serializer(), text) }.getOrNull() }
    }

    fun exists(): Boolean = file.exists()

    suspend fun clear() = SaveFiles.delete(file)
}
