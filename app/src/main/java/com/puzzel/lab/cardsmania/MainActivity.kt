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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Immersive.apply(window)
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
