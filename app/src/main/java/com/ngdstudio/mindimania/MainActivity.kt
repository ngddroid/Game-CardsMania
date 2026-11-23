package com.ngdstudio.mindimania

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
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ngdstudio.mindimania.analytics.Analytics
import com.ngdstudio.mindimania.nav.AppNavHost
import com.ngdstudio.mindimania.notify.Notifications
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.update.InAppUpdates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ngdstudio.mindimania.ui.theme.CM
import com.ngdstudio.mindimania.ui.theme.CardsManiaTheme

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
    private lateinit var updates: InAppUpdates
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Analytics.event("notif_permission", "granted" to granted)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        // the chosen app language (English, Hindi or Gujarati) for every stringResource() on screen
        super.attachBaseContext(com.ngdstudio.mindimania.i18n.LocaleManager.wrap(newBase))
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
        updates = InAppUpdates(this).also { it.check() }
        askForNotificationsOnce(container)
        setContent {
            CompositionLocalProvider(LocalApp provides container) {
                CardsManiaTheme {
                    Box(Modifier.fillMaxSize().background(CM.Midnight)) {
                        AppNavHost()
                        val ready by updates.ready.collectAsState()
                        if (ready) UpdateReadyBanner(Modifier.align(Alignment.BottomCenter)) { updates.restart() }
                    }
                }
            }
        }
    }

    /**
     * Android 13+ needs permission to show notifications. Asked once, right after the first-run screens (so the
     * first thing a new player sees is the game, not a system dialog). Turning a notification switch on in Settings
     * asks again.
     */
    private fun askForNotificationsOnce(container: AppContainer) {
        if (Build.VERSION.SDK_INT < 33) return
        lifecycleScope.launch {
            container.settings.settings.first { it.onboardingDone && !it.notifAsked }
            container.settings.update { it.copy(notifAsked = true) }
            if (!Notifications.allowed(this@MainActivity)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        Immersive.apply(window)
        updates.resume()
    }

    override fun onDestroy() {
        updates.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // dialogs, the keyboard and transient swipes can bring the bar back
        if (hasFocus) Immersive.apply(window)
    }
}

/** Shown when a flexible Play update has finished downloading: one tap installs it and restarts the app. */
@Composable
private fun UpdateReadyBanner(modifier: Modifier, onRestart: () -> Unit) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF21A0B3D))
            .border(1.5.dp, CM.Gold, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.update_ready), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        GameButton(stringResource(R.string.update_restart), onRestart, height = 42.dp, fontSize = 15.sp)
    }
}
