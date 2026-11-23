package com.ngdstudio.mindimania.nav

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
import com.ngdstudio.mindimania.GameLaunch
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.data.GameFactory
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.ui.game.GameScreen
import com.ngdstudio.mindimania.ui.home.MainScreen
import com.ngdstudio.mindimania.ui.howto.HowToPlayScreen
import com.ngdstudio.mindimania.ui.legal.LegalScreen
import com.ngdstudio.mindimania.ui.multiplayer.GuestLobbyScreen
import com.ngdstudio.mindimania.ui.multiplayer.HostRoomScreen
import com.ngdstudio.mindimania.ui.multiplayer.JoinRoomScreen
import com.ngdstudio.mindimania.ui.multiplayer.MultiplayerHubScreen
import com.ngdstudio.mindimania.ui.onboarding.OnboardingScreen
import com.ngdstudio.mindimania.ui.onboarding.ProfileSetupScreen
import com.ngdstudio.mindimania.ui.settings.SettingsScreen
import com.ngdstudio.mindimania.ui.setup.GameSetupScreen
import com.ngdstudio.mindimania.ui.splash.SplashScreen
import com.ngdstudio.mindimania.ui.spider.SpiderScreen
import com.ngdstudio.mindimania.ui.bhabhi.BhabhiLaunch
import com.ngdstudio.mindimania.ui.bhabhi.BhabhiScreen
import com.ngdstudio.mindimania.ui.bhabhi.BhabhiSetupScreen
import com.ngdstudio.mindimania.ui.teenpatti.TeenPattiLaunch
import com.ngdstudio.mindimania.ui.rummy.RummyLaunch
import com.ngdstudio.mindimania.ui.rummy.RummyScreen
import com.ngdstudio.mindimania.ui.rummy.RummySetupScreen
import com.ngdstudio.mindimania.ui.teenpatti.TeenPattiScreen
import com.ngdstudio.mindimania.ui.teenpatti.TeenPattiSetupScreen
import com.ngdstudio.mindimania.ui.mindi.MindiLaunch
import com.ngdstudio.mindimania.ui.mindi.MindiScreen
import com.ngdstudio.mindimania.ui.mindi.MindiSetupScreen
import com.ngdstudio.mindimania.ui.mindi.MindiViewModel
import kotlinx.coroutines.launch
import com.ngdstudio.mindimania.net.NetMsg
import com.ngdstudio.mindimania.net.NetRole

object Routes {
    const val STUDIO = "studio"
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
    const val TEEN_PATTI_SETUP = "teenpatti_setup"
    const val TEEN_PATTI = "teenpatti"
    const val RUMMY_SETUP = "rummy_setup"
    const val RUMMY = "rummy"
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
private fun com.ngdstudio.mindimania.net.RoomGame.route(): String = when (this) {
    com.ngdstudio.mindimania.net.RoomGame.MANIA -> Routes.GAME
    com.ngdstudio.mindimania.net.RoomGame.MINDI -> Routes.MINDI
    com.ngdstudio.mindimania.net.RoomGame.BHABHI -> Routes.BHABHI
    com.ngdstudio.mindimania.net.RoomGame.TEEN_PATTI -> Routes.TEEN_PATTI
    com.ngdstudio.mindimania.net.RoomGame.RUMMY -> Routes.RUMMY
}

/** Wi-Fi guests at an endless table follow the host back to the room lobby. */
@androidx.compose.runtime.Composable
private fun GuestFollowsHost(nav: NavHostController, net: NetRole, client: com.ngdstudio.mindimania.net.RoomClient?) {
    if (net != NetRole.GUEST || client == null) return
    androidx.compose.runtime.LaunchedEffect(Unit) {
        client.status.collect { st ->
            if (st == com.ngdstudio.mindimania.net.ClientStatus.InLobby) {
                if (!nav.popBackStack(Routes.MP_LOBBY, false)) nav.navigate(Routes.MP_LOBBY) { popUpTo(Routes.MAIN); launchSingleTop = true }
            }
        }
    }
}

private fun parseMode(s: String?): GameMode? = s?.takeIf { it.isNotBlank() }?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val app = LocalApp.current
    // screen views for Firebase Analytics (route names only, e.g. "spider", "mp_host")
    androidx.compose.runtime.DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, _ ->
            dest.route?.let { com.ngdstudio.mindimania.analytics.Analytics.screen(it) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    val scope = rememberCoroutineScope()

    fun startGame(launch: GameLaunch) {
        app.pendingLaunch = launch
        nav.navigate(Routes.GAME) { launchSingleTop = true }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.STUDIO,
        enterTransition = { fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f) },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(320)) },
        popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f) },
    ) {
        // "NG Studio" intro on every cold start, then the game splash
        composable(Routes.STUDIO) {
            // the intro is white: dark status-bar icons while it shows, back to light icons afterwards
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.DisposableEffect(view) {
                val window = (view.context as? android.app.Activity)?.window
                val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
                bars?.isAppearanceLightStatusBars = true
                onDispose { bars?.isAppearanceLightStatusBars = false }
            }
            com.ngdstudio.mindimania.ui.splash.StudioIntroScreen(onDone = {
                nav.navigate(Routes.SPLASH) { popUpTo(Routes.STUDIO) { inclusive = true } }
            })
        }
        composable(Routes.SPLASH) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            SplashScreen(onFinished = { onboardingDone, profileCreated ->
                val next = when {
                    !onboardingDone -> Routes.ONBOARDING
                    !profileCreated -> Routes.PROFILE_SETUP
                    else -> Routes.MAIN
                }
                // first launch: choose a language right after the splash
                if (!com.ngdstudio.mindimania.i18n.LocaleManager.hasChosen(ctx)) {
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
            com.ngdstudio.mindimania.ui.language.LanguageScreen(
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
                onTeenPatti = { nav.navigate(Routes.TEEN_PATTI_SETUP) { launchSingleTop = true } },
                onRummy = { nav.navigate(Routes.RUMMY_SETUP) { launchSingleTop = true } },
                onSpiderDaily = { nav.navigate(Routes.spider(GameFactory.today())) { launchSingleTop = true } },
                onMindiDaily = {
                    app.pendingMindi = MindiLaunch(setup = MindiViewModel.dailySetup(), daily = MindiViewModel.today())
                    nav.navigate(Routes.MINDI) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.RUMMY_SETUP) {
            RummySetupScreen(
                onBack = { nav.popBackStack() },
                onStart = { launch ->
                    app.pendingRummy = launch
                    nav.navigate(Routes.RUMMY) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.RUMMY) {
            // played sideways like Teen Patti; an endless table until the player leaves
            LandscapeWhileShown()
            val mp = app.multiplayer
            val launch = androidx.compose.runtime.remember {
                app.pendingRummy.also { app.pendingRummy = null }
                    // never start a local table while this phone is seated at a Wi-Fi one
                    ?: if (mp.client?.status?.value == com.ngdstudio.mindimania.net.ClientStatus.InGame) RummyLaunch(net = NetRole.GUEST)
                    else RummyLaunch(resume = true)
            }
            GuestFollowsHost(nav, launch.net, mp.client)
            RummyScreen(
                launch = launch,
                vmKey = "rummy",
                onExit = {
                    when (launch.net) {
                        NetRole.HOST -> mp.closeRoom(Strings.get(R.string.shell_host_left))
                        NetRole.GUEST -> mp.leaveRoom()
                        else -> Unit
                    }
                    if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain()
                },
                onHowTo = { nav.navigate("howto?mode=RUMMY") },
                onBackToRoom = if (launch.net == NetRole.HOST) ({
                    mp.host?.backToLobby()
                    if (!nav.popBackStack(Routes.MP_HOST, false)) nav.navigate(Routes.MP_HOST) { popUpTo(Routes.MAIN); launchSingleTop = true }
                }) else null,
            )
        }
        composable(Routes.TEEN_PATTI_SETUP) {
            TeenPattiSetupScreen(
                onBack = { nav.popBackStack() },
                onStart = { launch ->
                    app.pendingTeenPatti = launch
                    nav.navigate(Routes.TEEN_PATTI) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.TEEN_PATTI) {
            // the Teen Patti table is played sideways; the rest of the app stays portrait
            LandscapeWhileShown()
            // a new key = a fresh ViewModel and table for every new match (Play Again)
            val keyState = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
            val tpKey = keyState.intValue
            val mp = app.multiplayer
            val launch = androidx.compose.runtime.remember(tpKey) {
                app.pendingTeenPatti.also { app.pendingTeenPatti = null }
                    // never start a local table while this phone is seated at a Wi-Fi one
                    ?: if (mp.client?.status?.value == com.ngdstudio.mindimania.net.ClientStatus.InGame) TeenPattiLaunch(net = NetRole.GUEST)
                    else TeenPattiLaunch(resume = true)
            }
            GuestFollowsHost(nav, launch.net, mp.client)
            androidx.compose.runtime.key(tpKey) {
                TeenPattiScreen(
                    launch = launch,
                    vmKey = "teenpatti_$tpKey",
                    onExit = {
                        when (launch.net) {
                            NetRole.HOST -> mp.closeRoom(Strings.get(R.string.shell_host_left))
                            NetRole.GUEST -> mp.leaveRoom()
                            else -> Unit
                        }
                        if (!nav.popBackStack(Routes.MAIN, false)) nav.goMain()
                    },
                    onBackToRoom = if (launch.net == NetRole.HOST) ({
                        mp.host?.backToLobby()
                        if (!nav.popBackStack(Routes.MP_HOST, false)) nav.navigate(Routes.MP_HOST) { popUpTo(Routes.MAIN); launchSingleTop = true }
                    }) else null,
                    onHowTo = { nav.navigate("howto?mode=TEEN_PATTI") },
                    onPlayAgain = { l ->
                        app.pendingTeenPatti = l
                        keyState.intValue = tpKey + 1
                    },
                )
            }
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
                    ?: if (mp.client?.status?.value == com.ngdstudio.mindimania.net.ClientStatus.InGame) BhabhiLaunch(net = NetRole.GUEST)
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
                            if (st == com.ngdstudio.mindimania.net.ClientStatus.InLobby) {
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
                    ?: if (mp.client?.status?.value == com.ngdstudio.mindimania.net.ClientStatus.InGame) MindiLaunch(net = NetRole.GUEST)
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
                            if (st == com.ngdstudio.mindimania.net.ClientStatus.InLobby) {
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
            val m = entry.arguments?.getString("mode")
            HowToPlayScreen(initialMode = parseMode(m), onBack = { nav.popBackStack() }, initialPage = com.ngdstudio.mindimania.ui.howto.howToPageFor(m))
        }
    }
}

/** Turns the screen to landscape (either way up) while this is on screen, back to portrait when it leaves. */
@androidx.compose.runtime.Composable
private fun LandscapeWhileShown() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(ctx) {
        var c = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val activity = c as? android.app.Activity
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
