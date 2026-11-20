package com.puzzel.lab.cardsmania.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.puzzel.lab.cardsmania.GameLaunch
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.data.GameFactory
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.ui.game.GameScreen
import com.puzzel.lab.cardsmania.ui.home.MainScreen
import com.puzzel.lab.cardsmania.ui.howto.HowToPlayScreen
import com.puzzel.lab.cardsmania.ui.legal.LegalScreen
import com.puzzel.lab.cardsmania.ui.multiplayer.GuestLobbyScreen
import com.puzzel.lab.cardsmania.ui.multiplayer.HostRoomScreen
import com.puzzel.lab.cardsmania.ui.multiplayer.JoinRoomScreen
import com.puzzel.lab.cardsmania.ui.multiplayer.MultiplayerHubScreen
import com.puzzel.lab.cardsmania.ui.onboarding.OnboardingScreen
import com.puzzel.lab.cardsmania.ui.onboarding.ProfileSetupScreen
import com.puzzel.lab.cardsmania.ui.settings.SettingsScreen
import com.puzzel.lab.cardsmania.ui.setup.GameSetupScreen
import com.puzzel.lab.cardsmania.ui.splash.SplashScreen
import com.puzzel.lab.cardsmania.ui.spider.SpiderScreen
import com.puzzel.lab.cardsmania.ui.bhabhi.BhabhiLaunch
import com.puzzel.lab.cardsmania.ui.bhabhi.BhabhiScreen
import com.puzzel.lab.cardsmania.ui.bhabhi.BhabhiSetupScreen
import com.puzzel.lab.cardsmania.ui.mindi.MindiLaunch
import com.puzzel.lab.cardsmania.ui.mindi.MindiScreen
import com.puzzel.lab.cardsmania.ui.mindi.MindiSetupScreen
import com.puzzel.lab.cardsmania.ui.mindi.MindiViewModel
import kotlinx.coroutines.launch
import com.puzzel.lab.cardsmania.net.NetMsg
import com.puzzel.lab.cardsmania.net.NetRole

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PROFILE_SETUP = "profile_setup"
    const val MAIN = "main"
    const val SETUP = "setup?mode={mode}"
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val LEGAL = "legal/{doc}"
    const val HOWTO = "howto?mode={mode}"
    const val MP_HUB = "mp_hub"
    const val SPIDER = "spider?daily={daily}"
    const val MINDI_SETUP = "mindi_setup"
    const val MINDI = "mindi"
    const val BHABHI_SETUP = "bhabhi_setup"
    const val BHABHI = "bhabhi"
    const val LANGUAGE = "language?first={first}"
    fun language(first: Boolean) = "language?first=$first"
    fun spider(daily: String? = null) = "spider?daily=${daily ?: ""}"
    const val MP_HOST = "mp_host"
    const val MP_JOIN = "mp_join"
    const val MP_LOBBY = "mp_lobby"

    fun setup(mode: GameMode?) = "setup?mode=${mode?.name ?: ""}"
    fun legal(doc: String) = "legal/$doc"
    fun howto(mode: GameMode?) = "howto?mode=${mode?.name ?: ""}"
}

private fun NavHostController.goMain() = navigate(Routes.MAIN) {
    popUpTo(0) { inclusive = true }
    launchSingleTop = true
}

/** Where a room's game is played. */
private fun com.puzzel.lab.cardsmania.net.RoomGame.route(): String = when (this) {
    com.puzzel.lab.cardsmania.net.RoomGame.MANIA -> Routes.GAME
    com.puzzel.lab.cardsmania.net.RoomGame.MINDI -> Routes.MINDI
    com.puzzel.lab.cardsmania.net.RoomGame.BHABHI -> Routes.BHABHI
}

private fun parseMode(s: String?): GameMode? = s?.takeIf { it.isNotBlank() }?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val app = LocalApp.current
    val scope = rememberCoroutineScope()

    fun startGame(launch: GameLaunch) {
        app.pendingLaunch = launch
        nav.navigate(Routes.GAME) { launchSingleTop = true }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f) },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(320)) },
        popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f) },
    ) {
        composable(Routes.SPLASH) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            SplashScreen(onFinished = { onboardingDone, profileCreated ->
                val next = when {
                    !onboardingDone -> Routes.ONBOARDING
                    !profileCreated -> Routes.PROFILE_SETUP
                    else -> Routes.MAIN
                }
                // first launch: choose a language right after the splash
                if (!com.puzzel.lab.cardsmania.i18n.LocaleManager.hasChosen(ctx)) {
                    app.afterLanguage = next
                    nav.navigate(Routes.language(true)) { popUpTo(0) { inclusive = true } }
                } else if (next == Routes.MAIN) nav.goMain()
                else nav.navigate(next) { popUpTo(0) { inclusive = true } }
            })
        }
        composable(
            Routes.LANGUAGE,
            arguments = listOf(androidx.navigation.navArgument("first") { type = androidx.navigation.NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val first = entry.arguments?.getBoolean("first") ?: false
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            com.puzzel.lab.cardsmania.ui.language.LanguageScreen(
                firstRun = first,
                onBack = { nav.popBackStack() },
                onDone = { changed ->
                    if (first) {
                        val next = app.afterLanguage ?: Routes.ONBOARDING
                        app.afterLanguage = null
                        nav.navigate(next) { popUpTo(0) { inclusive = true } }
                    } else nav.popBackStack()
                    // a new language needs a fresh activity so every screen re-reads its text
                    if (changed) activity?.recreate()
                },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                scope.launch { app.settings.update { it.copy(onboardingDone = true) } }
                nav.navigate(Routes.PROFILE_SETUP) { popUpTo(0) { inclusive = true } }
            })
        }
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(onDone = { nav.goMain() })
        }
        composable(Routes.MAIN) {
            MainScreen(
                onOpenSetup = { mode -> nav.navigate(Routes.setup(mode)) },
                onQuickPlay = {
                    scope.launch {
                        val p = app.profile.current()
                        startGame(GameLaunch(GameFactory.fromSetup(p.setup, p)))
                    }
                },
                onResume = { startGame(GameLaunch(null, resume = true)) },
                onDaily = {
                    scope.launch {
                        val p = app.profile.current()
                        val d = GameFactory.daily()
                        startGame(GameLaunch(d.config(p), daily = d.date))
                    }
                },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onHowTo = { mode -> nav.navigate(Routes.howto(mode)) },
                onMultiplayer = { nav.navigate(Routes.MP_HUB) { launchSingleTop = true } },
                onSpider = { nav.navigate(Routes.spider()) { launchSingleTop = true } },
                onMindi = { nav.navigate(Routes.MINDI_SETUP) { launchSingleTop = true } },
                onBhabhi = { nav.navigate(Routes.BHABHI_SETUP) { launchSingleTop = true } },
                onSpiderDaily = { nav.navigate(Routes.spider(GameFactory.today())) { launchSingleTop = true } },
                onMindiDaily = {
                    app.pendingMindi = MindiLaunch(setup = MindiViewModel.dailySetup(), daily = MindiViewModel.today())
                    nav.navigate(Routes.MINDI) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.BHABHI_SETUP) {
            BhabhiSetupScreen(
                onBack = { nav.popBackStack() },
                onStart = { launch ->
                    app.pendingBhabhi = launch
                    nav.navigate(Routes.BHABHI) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.BHABHI) {
            // a new key = a fresh ViewModel and table for every new game (Play Again)
            val bhabhiKeyState = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
            val bhabhiKey = bhabhiKeyState.intValue
            val mp = app.multiplayer
            val launch = androidx.compose.runtime.remember(bhabhiKey) {
                app.pendingBhabhi.also { app.pendingBhabhi = null }
                    // never start a local game while this phone is seated in a multiplayer Bhabhi game
                    ?: if (mp.client?.status?.value == com.puzzel.lab.cardsmania.net.ClientStatus.InGame) BhabhiLaunch(net = NetRole.GUEST)
                    else BhabhiLaunch(resume = true)
            }
            // Guests follow the host: a new BhabhiStart = rematch, the lobby again = back to the room.
            if (launch.net == NetRole.GUEST) {
                val client = mp.client
                if (client != null) {
                    val firstStart = androidx.compose.runtime.remember(bhabhiKey) { client.starts.replayCache.lastOrNull() }
                    androidx.compose.runtime.LaunchedEffect(bhabhiKey) {
                        client.starts.collect { st ->
                            if (st !== firstStart && st is NetMsg.BhabhiStart) {
                                app.pendingBhabhi = BhabhiLaunch(net = NetRole.GUEST)
                                bhabhiKeyState.intValue = bhabhiKey + 1
                            }
                        }
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        client.status.collect { st ->
                            if (st == com.puzzel.lab.cardsmania.net.ClientStatus.InLobby) {
                                if (!nav.popBackStack(Routes.MP_LOBBY, false)) nav.navigate(Routes.MP_LOBBY) { popUpTo(Routes.MAIN); launchSingleTop = true }
                            }
                        }
                    }
                }
            }
            androidx.compose.runtime.key(bhabhiKey) {
                BhabhiScreen(
                    launch = launch,
                    vmKey = "bhabhi_$bhabhiKey",
                    onExit = {
                        when (launch.net) {
                            NetRole.HOST -> mp.closeRoom(Strings.get(R.string.shell_host_left))
                            NetRole.GUEST -> mp.leaveRoom()
                            else -> Unit
                        }
                        if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain()
                    },
                    onHowTo = { nav.navigate(Routes.howto(null)) },
                    onPlayAgain = { l ->
                        if (launch.net == NetRole.HOST) {
                            scope.launch {
                                val hosted = mp.startHostedBhabhi(app.profile.current()) ?: return@launch
                                app.pendingBhabhi = BhabhiLaunch(setup = hosted.setup, net = NetRole.HOST, seats = hosted.seats, seed = hosted.seed)
                                bhabhiKeyState.intValue = bhabhiKey + 1
                            }
                        } else {
                            app.pendingBhabhi = l
                            bhabhiKeyState.intValue = bhabhiKey + 1
                        }
                    },
                    onBackToRoom = if (launch.net == NetRole.HOST) ({
                        mp.host?.backToLobby()
                        if (!nav.popBackStack(Routes.MP_HOST, false)) nav.navigate(Routes.MP_HOST) { popUpTo(Routes.MAIN); launchSingleTop = true }
                    }) else null,
                )
            }
        }
        composable(Routes.MINDI_SETUP) {
            MindiSetupScreen(
                onBack = { nav.popBackStack() },
                onStart = { launch ->
                    app.pendingMindi = launch
                    nav.navigate(Routes.MINDI) { launchSingleTop = true }
                },
            )
        }
        composable(
            Routes.MINDI,
            enterTransition = { fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.06f) },
        ) {
            val mindiKeyState = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
            val mindiKey = mindiKeyState.intValue
            val mp = app.multiplayer
            val launch = androidx.compose.runtime.remember(mindiKey) {
                app.pendingMindi.also { app.pendingMindi = null }
                    // never start a local match while this phone is seated in a multiplayer Mindi match
                    ?: if (mp.client?.status?.value == com.puzzel.lab.cardsmania.net.ClientStatus.InGame) MindiLaunch(net = NetRole.GUEST)
                    else MindiLaunch(resume = true)
            }
            // Guests follow the host: a new MindiStart = rematch, the lobby again = back to the room.
            if (launch.net == NetRole.GUEST) {
                val client = mp.client
                if (client != null) {
                    val firstStart = androidx.compose.runtime.remember(mindiKey) { client.starts.replayCache.lastOrNull() }
                    androidx.compose.runtime.LaunchedEffect(mindiKey) {
                        client.starts.collect { st ->
                            if (st !== firstStart && st is NetMsg.MindiStart) {
                                app.pendingMindi = MindiLaunch(net = NetRole.GUEST)
                                mindiKeyState.intValue = mindiKey + 1
                            }
                        }
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        client.status.collect { st ->
                            if (st == com.puzzel.lab.cardsmania.net.ClientStatus.InLobby) {
                                if (!nav.popBackStack(Routes.MP_LOBBY, false)) nav.navigate(Routes.MP_LOBBY) { popUpTo(Routes.MAIN); launchSingleTop = true }
                            }
                        }
                    }
                }
            }
            androidx.compose.runtime.key(mindiKey) {
                MindiScreen(
                    launch = launch,
                    vmKey = "mindi_$mindiKey",
                    onExit = {
                        when (launch.net) {
                            NetRole.HOST -> mp.closeRoom(Strings.get(R.string.shell_host_left))
                            NetRole.GUEST -> mp.leaveRoom()
                            else -> Unit
                        }
                        if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain()
                    },
                    onHowTo = { nav.navigate(Routes.howto(null)) },
                    onPlayAgain = { l ->
                        if (launch.net == NetRole.HOST) {
                            scope.launch {
                                val hosted = mp.startHostedMindi(app.profile.current()) ?: return@launch
                                app.pendingMindi = MindiLaunch(setup = hosted.setup, net = NetRole.HOST, seats = hosted.seats, seed = hosted.seed)
                                mindiKeyState.intValue = mindiKey + 1
                            }
                        } else {
                            app.pendingMindi = l
                            mindiKeyState.intValue = mindiKey + 1
                        }
                    },
                    onBackToRoom = if (launch.net == NetRole.HOST) ({
                        mp.host?.backToLobby()
                        if (!nav.popBackStack(Routes.MP_HOST, false)) nav.navigate(Routes.MP_HOST) { popUpTo(Routes.MAIN); launchSingleTop = true }
                    }) else null,
                )
            }
        }
        composable(
            Routes.SPIDER,
            arguments = listOf(navArgument("daily") { type = NavType.StringType; defaultValue = ""; nullable = true }),
            enterTransition = { fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.06f) },
        ) { entry ->
            SpiderScreen(
                daily = entry.arguments?.getString("daily")?.takeIf { it.isNotBlank() },
                onExit = { if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain() },
            )
        }
        composable(Routes.MP_HUB) {
            MultiplayerHubScreen(
                onBack = { nav.popBackStack() },
                onHosted = { nav.navigate(Routes.MP_HOST) { popUpTo(Routes.MAIN); launchSingleTop = true } },
                onJoin = { nav.navigate(Routes.MP_JOIN) { launchSingleTop = true } },
            )
        }
        composable(Routes.MP_HOST) {
            HostRoomScreen(
                onClosed = { if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain() },
                onStart = { game ->
                    nav.navigate(game.route()) { popUpTo(Routes.MP_HOST); launchSingleTop = true }
                },
            )
        }
        composable(Routes.MP_JOIN) {
            JoinRoomScreen(
                onBack = { nav.popBackStack() },
                onJoined = { nav.navigate(Routes.MP_LOBBY) { popUpTo(Routes.MAIN); launchSingleTop = true } },
            )
        }
        composable(Routes.MP_LOBBY) {
            GuestLobbyScreen(
                onLeft = { if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain() },
                onGameStart = { game ->
                    nav.navigate(game.route()) { popUpTo(Routes.MP_LOBBY); launchSingleTop = true }
                },
            )
        }
        composable(
            Routes.SETUP,
            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = ""; nullable = true }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(380)) + fadeIn() },
        ) { entry ->
            GameSetupScreen(
                initialMode = parseMode(entry.arguments?.getString("mode")),
                onBack = { nav.popBackStack() },
                onStart = {
                    nav.navigate(Routes.GAME) {
                        popUpTo(Routes.MAIN)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            Routes.GAME,
            enterTransition = { fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 1.08f) },
        ) {
            GameScreen(
                onExit = { if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain() },
                onHowTo = { mode -> nav.navigate(Routes.howto(mode)) },
                onBackToRoom = { host ->
                    val route = if (host) Routes.MP_HOST else Routes.MP_LOBBY
                    if (!nav.popBackStack(route, false)) {
                        nav.navigate(route) { popUpTo(Routes.MAIN); launchSingleTop = true }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLegal = { doc -> nav.navigate(Routes.legal(doc)) },
                onHowTo = { nav.navigate(Routes.howto(null)) },
                onLanguage = { nav.navigate(Routes.language(false)) { launchSingleTop = true } },
            )
        }
        composable(Routes.LEGAL, arguments = listOf(navArgument("doc") { type = NavType.StringType })) { entry ->
            LegalScreen(doc = entry.arguments?.getString("doc") ?: "terms", onBack = { nav.popBackStack() })
        }
        composable(
            Routes.HOWTO,
            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = ""; nullable = true }),
        ) { entry ->
            HowToPlayScreen(initialMode = parseMode(entry.arguments?.getString("mode")), onBack = { nav.popBackStack() })
        }
    }
}
