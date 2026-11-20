package com.puzzel.lab.cardsmania

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.puzzel.lab.cardsmania.nav.AppNavHost
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.CardsManiaTheme

/** Hides the system navigation bar (immersive). A swipe from the bottom edge shows it briefly; back gestures still work. */
object Immersive {
    fun apply(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.navigationBars())
    }
}

/**
 * Runs the app at 60 Hz on 90/120 Hz phones. Card animations look the same at 60 frames a second, and drawing half
 * as many frames saves battery and keeps budget phones cool.
 */
object FrameRate {
    fun capAt60(activity: android.app.Activity) {
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) activity.display
        else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay
        display ?: return
        val now = display.mode
        val mode = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight && it.refreshRate in 55f..65f }
            .minByOrNull { kotlin.math.abs(it.refreshRate - 60f) } ?: return
        activity.window.attributes = activity.window.attributes.also {
            it.preferredDisplayModeId = mode.modeId
            it.preferredRefreshRate = mode.refreshRate
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        // the chosen app language (English, Hindi or Gujarati) for every stringResource() on screen
        super.attachBaseContext(com.puzzel.lab.cardsmania.i18n.LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Immersive.apply(window)
        FrameRate.capAt60(this)
        val container = (application as CardsManiaApp).container
        setContent {
            CompositionLocalProvider(LocalApp provides container) {
                CardsManiaTheme {
                    Box(Modifier.fillMaxSize().background(CM.Midnight)) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Immersive.apply(window)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // dialogs, the keyboard and transient swipes can bring the bar back
        if (hasFocus) Immersive.apply(window)
    }
}
