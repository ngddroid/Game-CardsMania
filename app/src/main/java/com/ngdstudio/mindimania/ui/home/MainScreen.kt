package com.ngdstudio.mindimania.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.audio.Track
import com.ngdstudio.mindimania.data.GameFactory
import com.ngdstudio.mindimania.data.Missions
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.data.Progression
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.ui.design.GameBackdrop
import com.ngdstudio.mindimania.ui.design.GameIcons
import com.ngdstudio.mindimania.ui.design.GameOverlayHost
import com.ngdstudio.mindimania.ui.design.GameTab
import com.ngdstudio.mindimania.ui.design.GameTabBar
import com.ngdstudio.mindimania.ui.design.RewardPopup
import com.ngdstudio.mindimania.ui.design.backdropFor
import com.ngdstudio.mindimania.ui.kit.CoinBurst
import com.ngdstudio.mindimania.ui.kit.TreasureChest
import com.ngdstudio.mindimania.ui.kit.rememberProfile
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    onOpenSetup: (GameMode?) -> Unit,
    onQuickPlay: () -> Unit,
    onResume: () -> Unit,
    onDaily: () -> Unit,
    onSettings: () -> Unit,
    onHowTo: (GameMode?) -> Unit,
    onMultiplayer: () -> Unit = {},
    onSpider: () -> Unit = {},
    onMindi: () -> Unit = {},
    onBhabhi: () -> Unit = {},
    onTeenPatti: () -> Unit = {},
    onRummy: () -> Unit = {},
    onSpiderDaily: () -> Unit = {},
    onMindiDaily: () -> Unit = {},
) {
    val app = LocalApp.current
    val profile by rememberProfile()
    LaunchedEffect(Unit) { app.music.play(Track.MENU) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var gift by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    // Back: other tabs return to the Lobby; on the Lobby ask before leaving the app.
    androidx.activity.compose.BackHandler(enabled = gift == null) {
        if (tab != 0) tab = 0 else confirmExit = true
    }
    if (confirmExit) {
        com.ngdstudio.mindimania.ui.kit.GemDialog(stringResource(R.string.shell_exit_title), onDismiss = { confirmExit = false }) {
            androidx.compose.material3.Text(
                stringResource(R.string.shell_exit_body),
                color = com.ngdstudio.mindimania.ui.theme.CM.Text, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                com.ngdstudio.mindimania.ui.components.GameButton(
                    stringResource(R.string.shell_exit_stay), { confirmExit = false }, style = com.ngdstudio.mindimania.ui.components.ButtonStyle.GREEN,
                    modifier = Modifier.weight(1f), height = 50.dp, fontSize = 16.sp,
                )
                com.ngdstudio.mindimania.ui.components.GameButton(
                    stringResource(R.string.shell_exit_exit), { confirmExit = false; activity?.finish() }, style = com.ngdstudio.mindimania.ui.components.ButtonStyle.RED,
                    modifier = Modifier.weight(1f), height = 50.dp, fontSize = 16.sp,
                )
            }
        }
    }

    GameOverlayHost {
        GameBackdrop(style = backdropFor(profile.tableTheme)) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(320)) { it * dir / 5 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it * dir / 5 } + fadeOut(tween(200)))
                    },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> HomeTab(
                            onOpenSetup, onQuickPlay, onResume, onDaily, onSettings, onHowTo, onMultiplayer, onSpider,
                            onMindi = onMindi,
                            onBhabhi = onBhabhi,
                            onTeenPatti = onTeenPatti,
                            onRummy = onRummy,
                            onOpenEvents = { tab = 1 },
                            onGift = { amount, streak -> gift = amount to streak },
                        )
                        1 -> EventsTab(onDaily, onSpiderDaily = onSpiderDaily, onMindiDaily = onMindiDaily)
                        2 -> CollectionTab()
                        else -> ProfileTab(onSettings)
                    }
                }
                GameTabBar(
                    tabs = tabs.map { (icon, label) -> GameTab(icon, stringResource(label)) },
                    selected = tab,
                    badges = badgesFor(profile),
                    onSelect = {
                        if (it != tab) {
                            app.sound.play(Sfx.SELECT)
                            app.haptics.tick()
                            tab = it
                        }
                    }
                )
            }
        }
        gift?.let { (amount, streak) ->
            RewardPopup(
                title = stringResource(R.string.shell_gift_title),
                amount = amount,
                subtitle = if (streak > 1) stringResource(R.string.shell_gift_streak, streak) else stringResource(R.string.shell_gift_come_back),
                onCollect = { gift = null },
                icon = { GiftChestPop() },
            )
        }
    }
}

@Composable
private fun GiftChestPop() {
    val app = LocalApp.current
    val open = remember { Animatable(0f) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(250)
        burst = 1
        app.sound.play(Sfx.COIN)
        open.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 140f))
    }
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        TreasureChest(open.value, Modifier.size(160.dp))
        if (burst > 0) CoinBurst(burst, Modifier.size(240.dp))
    }
}

private fun badgesFor(p: Profile): Set<Int> {
    val set = HashSet<Int>()
    if (Progression.giftReady(p)) set += 0
    val dailyDone = (p.daily[GameFactory.today()] ?: 0) > 0
    val week = GameFactory.currentWeek()
    val missionClaimable = p.missionWeek == week && Missions.forWeek(week).any {
        (p.missionProgress[it.id] ?: 0) >= it.target && it.id !in p.missionClaimed
    }
    if (!dailyDone || missionClaimable) set += 1
    return set
}

/** Dock tabs: icon + label resource (resolved in the composable so the label follows the app language). */
private val tabs = listOf(
    GameIcons.Lobby to R.string.shell_tab_lobby,
    GameIcons.Trophy to R.string.shell_tab_events,
    GameIcons.Chest to R.string.shell_tab_collection,
    GameIcons.Profile to R.string.shell_tab_profile,
)
