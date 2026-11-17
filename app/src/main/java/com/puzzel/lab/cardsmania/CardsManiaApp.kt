package com.puzzel.lab.cardsmania

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.puzzel.lab.cardsmania.audio.Haptics
import com.puzzel.lab.cardsmania.audio.MusicPlayer
import com.puzzel.lab.cardsmania.audio.SoundManager
import com.puzzel.lab.cardsmania.data.ProfileRepository
import com.puzzel.lab.cardsmania.data.SavedGameStore
import com.puzzel.lab.cardsmania.data.SettingsRepository
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CardsManiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** What the Game screen should start: a new game from [config], or the saved game. */
data class GameLaunch(
    val config: GameConfig?,
    val resume: Boolean = false,
    val daily: String? = null,
    val net: com.puzzel.lab.cardsmania.net.NetRole = com.puzzel.lab.cardsmania.net.NetRole.LOCAL,
)

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app)
    val profile = ProfileRepository(app)
    val savedGames = SavedGameStore(app)
    val sound = SoundManager(app)
    val music = MusicPlayer(app)
    val haptics = Haptics(app)
    val multiplayer = com.puzzel.lab.cardsmania.net.MultiplayerSession(app)

    @Volatile var pendingLaunch: GameLaunch? = null
    @Volatile var pendingMindi: com.puzzel.lab.cardsmania.ui.mindi.MindiLaunch? = null

    init {
        scope.launch {
            settings.settings.collect {
                sound.volume = it.sfxVolume
                music.volume = it.musicVolume
                haptics.enabled = it.haptics
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = music.resume()
            override fun onStop(owner: LifecycleOwner) = music.pause()
        })
    }
}

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
