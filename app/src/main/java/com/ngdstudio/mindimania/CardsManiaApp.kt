package com.ngdstudio.mindimania

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ngdstudio.mindimania.audio.Haptics
import com.ngdstudio.mindimania.audio.MusicPlayer
import com.ngdstudio.mindimania.audio.SoundManager
import com.ngdstudio.mindimania.data.ProfileRepository
import com.ngdstudio.mindimania.data.SavedGameStore
import com.ngdstudio.mindimania.data.Settings
import com.ngdstudio.mindimania.data.SettingsRepository
import com.ngdstudio.mindimania.engine.model.GameConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CardsManiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.ngdstudio.mindimania.i18n.LocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        com.ngdstudio.mindimania.i18n.Strings.init(this)
        com.ngdstudio.mindimania.analytics.Analytics.init(this)
        com.ngdstudio.mindimania.notify.Notifications.createChannels(this)
        container = AppContainer(this)
    }
}

/** What the Game screen should start: a new game from [config], or the saved game. */
data class GameLaunch(
    val config: GameConfig?,
    val resume: Boolean = false,
    val daily: String? = null,
    val net: com.ngdstudio.mindimania.net.NetRole = com.ngdstudio.mindimania.net.NetRole.LOCAL,
)

class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app)
    val profile = ProfileRepository(app)
    val savedGames = SavedGameStore(app)
    val sound = SoundManager(app)
    val music = MusicPlayer(app)
    val haptics = Haptics(app)
    val multiplayer = com.ngdstudio.mindimania.net.MultiplayerSession(app)

    @Volatile var pendingLaunch: GameLaunch? = null
    @Volatile var pendingMindi: com.ngdstudio.mindimania.ui.mindi.MindiLaunch? = null
    @Volatile var pendingBhabhi: com.ngdstudio.mindimania.ui.bhabhi.BhabhiLaunch? = null
    @Volatile var pendingTeenPatti: com.ngdstudio.mindimania.ui.teenpatti.TeenPattiLaunch? = null
    @Volatile var pendingRummy: com.ngdstudio.mindimania.ui.rummy.RummyLaunch? = null
    /** Lobby section last shown (0 = Indian Card, 1 = Card Mania); kept while the app is running. */
    var lobbySection: Int = 0
    /** Where to go after the first-run Language screen. */
    var afterLanguage: String? = null

    init {
        // privacy choice, daily reminder and push topics follow Settings (applied once at start, then on every change)
        scope.launch {
            val lang = com.ngdstudio.mindimania.i18n.LocaleManager.current(app).tag
            com.ngdstudio.mindimania.analytics.Analytics.userProperty("app_language", lang)
            var last: Settings? = null
            settings.settings.collect { s ->
                if (last?.shareData != s.shareData) com.ngdstudio.mindimania.analytics.Analytics.setSharing(s.shareData)
                if (last?.reminders != s.reminders) com.ngdstudio.mindimania.notify.Reminders.sync(app, s.reminders)
                if (last?.newsPush != s.newsPush) com.ngdstudio.mindimania.notify.Push.sync(app, s.newsPush, lang)
                last = s
            }
        }
        scope.launch {
            settings.settings.collect {
                sound.volume = it.sfxVolume
                music.volume = it.musicVolume
                haptics.enabled = it.haptics
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                music.resume()
                // the daily reminder stays quiet for a while after the player was here
                scope.launch { settings.update { it.copy(lastOpenAt = System.currentTimeMillis()) } }
            }
            override fun onStop(owner: LifecycleOwner) = music.pause()
        })
    }
}

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
