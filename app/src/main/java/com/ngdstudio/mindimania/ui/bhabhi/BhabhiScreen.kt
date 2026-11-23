package com.ngdstudio.mindimania.ui.bhabhi

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.bhabhi.BhabhiState
import com.ngdstudio.mindimania.data.AvatarRef
import com.ngdstudio.mindimania.data.DifficultyInfo
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.net.NetRole
import com.ngdstudio.mindimania.ui.game.FrameDriver
import com.ngdstudio.mindimania.ui.game.rememberFrameTick
import com.ngdstudio.mindimania.ui.game.wakeOnTouch
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoundIconButton
import com.ngdstudio.mindimania.ui.components.rememberPulse
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun BhabhiScreen(
    launch: BhabhiLaunch,
    vmKey: String,
    onExit: () -> Unit,
    onHowTo: () -> Unit,
    onPlayAgain: (BhabhiLaunch) -> Unit,
    /** Multiplayer host only: return everyone to the room lobby after a game. */
    onBackToRoom: (() -> Unit)? = null,
) {
    val vm: BhabhiViewModel = viewModel(key = vmKey)
    LaunchedEffect(vm) { vm.start(launch) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPause by remember { mutableStateOf(false) }
    var showEmotes by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.abandon() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler {
        when {
            ui.result != null -> onExit()
            showEmotes -> showEmotes = false
            else -> showPause = !showPause
        }
    }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF052014))) {
        val s = ui.state
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        if (s == null || wPx <= 0f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OutlinedTitle(stringResource(R.string.bhabhi_shuffling), fontSize = 28.sp) }
            return@BoxWithConstraints
        }
        val layout = remember(wPx, hPx, s.players, topInset, bottomInset, ui.me) {
            BhabhiLayout(wPx, hPx, density.density, s.players, ui.me, topInset, bottomInset).also {
                PlayingCardArt.configure((it.handCardW * 1.1f).toInt())
            }
        }
        val world = remember(layout, vm) { BhabhiWorld(layout) }
        BhabhiTable(vm, world, ui)

        for (seat in 0 until s.players) BhabhiSeatView(seat, s, ui, layout)
        BhabhiTopBar(s, layout, onPause = { showPause = true })
        FinishStrip(s, ui, layout)
        BhabhiActionBar(
            s, ui, layout,
            onEmote = { showEmotes = !showEmotes },
            onSkip = vm::fastForward,
        )
        StatusPill(s, ui, layout)
        if (showEmotes) {
            EmoteRow(Modifier.offset { IntOffset(0, (layout.actionY - 110 * density.density).roundToInt()) }) {
                vm.emote(it)
                showEmotes = false
            }
        }
        Banner(ui, Modifier.offset { IntOffset(0, (layout.center.y - 30 * density.density).roundToInt()) })

        val isGuest = ui.net == NetRole.GUEST
        val isHost = ui.net == NetRole.HOST
        ui.result?.let { r ->
            BhabhiResultOverlay(
                s, ui, r, onHome = onExit,
                // guests follow the host: only the host deals again or goes back to the room
                onAgain = if (isGuest) null else ({ onPlayAgain(BhabhiLaunch(setup = s.setup)) }),
                onRoom = if (isHost) onBackToRoom else null,
            )
        }
        ui.ended?.let { reason -> com.ngdstudio.mindimania.ui.game.EndedDialog(reason, onOk = onExit) }
        if (showPause) {
            if (ui.net != NetRole.LOCAL) {
                // same menu as a Mindi room: the game keeps running, leaving asks first
                com.ngdstudio.mindimania.ui.mindi.MindiPauseMenu(
                    net = ui.net,
                    onResume = { showPause = false },
                    onHowTo = { showPause = false; onHowTo() },
                    onQuit = { showPause = false; onExit() },
                )
            } else {
                BhabhiPauseMenu(
                    onResume = { showPause = false },
                    onHowTo = { showPause = false; onHowTo() },
                    onQuit = { showPause = false; vm.abandon(); onExit() },
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- table canvas

@Composable
private fun BhabhiTable(vm: BhabhiViewModel, world: BhabhiWorld, ui: BhabhiUi) {
    val layout = world.layout
    val driver = remember { FrameDriver() }
    SideEffect {
        val theme = ui.profile.tableTheme.let { if (it == TableTheme.ROYAL) TableTheme.FELT else it }
        val changed = world.back != ui.profile.cardBack || world.theme != theme || world.reduceMotion != ui.settings.reduceMotion
        world.back = ui.profile.cardBack
        world.theme = theme
        world.reduceMotion = ui.settings.reduceMotion
        world.speed = (when (ui.settings.speed) { 0 -> 1.3f; 2 -> 0.65f; else -> 1f }) * (if (ui.fastForward) 0.3f else 1f)
        if (changed) driver.wake()
    }
    LaunchedEffect(world, vm) {
        vm.lastShown?.let { if (world.state == null) { world.apply(it, 0.01f); driver.wake() } }
        vm.steps.collect { world.apply(it, world.speed); driver.wake() }
    }
    LaunchedEffect(world, ui.selected, ui.idle) {
        world.selected = ui.selected
        world.interactive = ui.idle && ui.state?.turn == ui.me
        world.relayoutHand()
        driver.wake()
    }
    val tick by rememberFrameTick(driver, world)
    val slop = LocalViewConfiguration.current.touchSlop
    Canvas(
        Modifier.fillMaxSize().wakeOnTouch(driver).pointerInput(world, vm) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = world.hitHand(down.position.x, down.position.y)
                var dragged = false
                var lastY = down.position.y
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    lastY = ch.position.y
                    if (down.position.y - ch.position.y > slop * 3) dragged = true
                }
                when {
                    id == null -> vm.deselect()
                    dragged && lastY < layout.handTop - layout.handCardH * 0.2f -> vm.dragPlay(id)
                    else -> vm.tapCard(id)
                }
            }
        }
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawIntoCanvas { world.draw(it.nativeCanvas, SystemClock.uptimeMillis()) }
    }
}

// ------------------------------------------------------------------------------------------- HUD

private fun Modifier.centeredAt(pos: Offset, sizePx: Float) = this.offset { IntOffset((pos.x - sizePx / 2).roundToInt(), (pos.y - sizePx / 2).roundToInt()) }

private fun medal(place: Int) = when (place) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$place" }

@Composable
private fun BhabhiTopBar(s: BhabhiState, layout: BhabhiLayout, onPause: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, layout.topInset.roundToInt()) }.height(with(density) { layout.hudH.toDp() }).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Rounded.Pause, onPause, size = 40.dp, contentDescription = stringResource(R.string.bhabhi_cd_pause))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.bhabhi_top_title, s.players), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
            Text(
                pluralStringResource(
                    if (s.setup.safeFirstTrick && s.trickNo == 0) R.plurals.bhabhi_top_trick_safe else R.plurals.bhabhi_top_trick,
                    s.discarded.size, s.trickNo + 1, s.discarded.size,
                ),
                color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(48.dp))
    }
}

/** Who has got away so far, in order. */
@Composable
private fun FinishStrip(s: BhabhiState, ui: BhabhiUi, layout: BhabhiLayout) {
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().offset { IntOffset(0, layout.stripTop.roundToInt()) }.height(with(density) { layout.stripH.toDp() }).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (s.out.isEmpty()) {
            Text(stringResource(R.string.bhabhi_finish_hint), color = CM.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                s.out.forEachIndexed { i, seat ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (seat == ui.me) Color(0xCC0A8A55) else Color(0x66000000))
                            .border(1.dp, CM.Gold.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(medal(i + 1), fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(if (seat == ui.me) stringResource(R.string.bhabhi_you) else ui.seats.getOrNull(seat)?.name ?: stringResource(R.string.bhabhi_player), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun BhabhiSeatView(seat: Int, s: BhabhiState, ui: BhabhiUi, layout: BhabhiLayout) {
    val density = LocalDensity.current
    val info = ui.seats.getOrNull(seat) ?: return
    val pos = layout.seatOf(seat)
    val av = if (seat == ui.me) layout.avatar * 0.85f else layout.avatar
    val boxPx = av * 2.3f
    val avDp = with(density) { av.toDp() }
    val active = !s.isOver && s.turn == seat
    val place = s.out.indexOf(seat).takeIf { it >= 0 }?.plus(1)
    val isBhabhi = s.loser == seat
    val pulse by rememberPulse(650)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.turnDeadline, active) {
        while (active && ui.turnDeadline > 0) { now = System.currentTimeMillis(); delay(100) }
    }
    Box(Modifier.centeredAt(pos, boxPx).size(with(density) { boxPx.toDp() }), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(Modifier.size(avDp * 1.55f)) {
                drawCircle(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent)), radius = size.minDimension / 2)
            }
        }
        Box(Modifier.alpha(if (place != null) 0.75f else 1f)) {
            AvatarBadge(
                AvatarRef.emoji(info.avatar), size = avDp,
                ringColor = when { isBhabhi -> CM.Ruby; place != null -> CM.Emerald; active -> CM.GoldLight; else -> Color(0xFF8E7CC3) },
            )
            if (seat != ui.me && place == null && !isBhabhi) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).clip(RoundedCornerShape(50))
                        .background(Color(0xFF1A0B3D)).border(1.5.dp, CM.Gold, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text("${s.hands[seat].size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
            // multiplayer: a real player on Wi-Fi, or a bot covering for someone who dropped
            ui.netSeats[seat]?.takeIf { seat != ui.me && place == null && !isBhabhi }?.let { connected ->
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 2.dp).clip(RoundedCornerShape(50))
                        .background(if (connected) CM.Emerald else Color(0xFF6B6B7A))
                        .border(1.5.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (connected) androidx.compose.material3.Icon(Icons.Rounded.Wifi, stringResource(R.string.mindi_cd_online_player), tint = Color.White, modifier = Modifier.size(11.dp))
                    else Text(stringResource(R.string.mindi_bot_badge), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            if (active && seat == ui.me && ui.turnDeadline > 0 && ui.turnTotal > 0) {
                val frac = ((ui.turnDeadline - now).toFloat() / ui.turnTotal).coerceIn(0f, 1f)
                Canvas(Modifier.size(avDp)) {
                    drawArc(if (frac < 0.3f) CM.Ruby else CM.Emerald, -90f, 360f * frac, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        if (place != null) {
            // outside the dimmed avatar layer (Modifier.alpha clips to its bounds)
            Text(
                medal(place), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White,
                modifier = Modifier.align(Alignment.Center).offset(x = avDp * 0.5f, y = -avDp * 0.45f),
            )
        }
        if (isBhabhi) {
            Box(
                Modifier.align(Alignment.Center).offset(y = -avDp * 0.72f).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFB0203F)))).border(1.5.dp, Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) { Text(stringResource(R.string.bhabhi_badge), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
        if (seat != ui.me) {
            Row(
                Modifier.align(Alignment.Center).offset(y = avDp * 0.72f).clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    info.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(if (s.players >= 6) 54.dp else 70.dp),
                )
                if (!info.isHuman) Box(Modifier.size(6.dp).clip(CircleShape).background(DifficultyInfo.color(info.difficulty)))
            }
        }
        ui.emotes[seat]?.let { (text, at) -> EmoteBubble(text, at, Modifier.align(Alignment.TopCenter).offset(x = avDp * 0.6f, y = (-avDp * 0.2f))) }
    }
}

@Composable
private fun EmoteBubble(text: String, at: Long, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(at) {
        if (System.currentTimeMillis() - at < 2000) {
            visible = true
            delay(1800)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier = modifier, enter = scaleIn(spring(0.45f, 500f)) + fadeIn(), exit = fadeOut()) {
        Box(Modifier.clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 3.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text, fontSize = 22.sp)
        }
    }
}

@Composable
private fun BhabhiActionBar(s: BhabhiState, ui: BhabhiUi, layout: BhabhiLayout, onEmote: () -> Unit, onSkip: () -> Unit) {
    val density = LocalDensity.current
    val iAmOut = s.hands[ui.me].isEmpty() && !s.isOver
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, (layout.actionY - 21 * density.density).roundToInt()) }.padding(start = 64.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Rounded.EmojiEmotions, onEmote, size = 42.dp, contentDescription = stringResource(R.string.bhabhi_cd_emote))
        // offline only: in a Wi-Fi game friends are still playing, so the pace stays the same for everyone
        if (iAmOut && !ui.fastForward && ui.net == NetRole.LOCAL) {
            GameButton(stringResource(R.string.bhabhi_finish), onSkip, style = ButtonStyle.GREEN, icon = Icons.Rounded.FastForward, height = 42.dp, fontSize = 15.sp)
        }
    }
}

@Composable
private fun StatusPill(s: BhabhiState, ui: BhabhiUi, layout: BhabhiLayout) {
    val myTurn = ui.idle && !s.isOver && s.turn == ui.me
    // no hints: the pill never says which suit to play or that you can thulla
    val text = when {
        s.isOver -> ""
        s.hands[ui.me].isEmpty() -> stringResource(R.string.bhabhi_status_got_away)
        !ui.idle -> ""
        myTurn -> when {
            ui.selected != null -> stringResource(R.string.bhabhi_status_tap_again)
            s.trick.isEmpty() && s.trickNo == 0 -> stringResource(R.string.bhabhi_status_open)
            s.trick.isEmpty() -> stringResource(R.string.bhabhi_status_lead)
            else -> stringResource(R.string.bhabhi_status_your_turn)
        }
        else -> stringResource(R.string.bhabhi_status_thinking, ui.seats.getOrNull(s.turn)?.name ?: stringResource(R.string.bhabhi_player))
    }
    Box(Modifier.fillMaxWidth().offset { IntOffset(0, (layout.center.y + layout.trickCardH * 1.4f).roundToInt()) }, contentAlignment = Alignment.Center) {
        AnimatedVisibility(text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(
                        when {
                            myTurn -> Brush.horizontalGradient(listOf(Color(0xCCB36B00), Color(0xCCFFB300), Color(0xCCB36B00)))
                            else -> Brush.horizontalGradient(listOf(Color(0x99000000), Color(0x99000000)))
                        }
                    )
                    .border(1.dp, if (myTurn) CM.GoldLight else Color(0x33FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(text, color = if (myTurn) Color(0xFF3A1F00) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun EmoteRow(modifier: Modifier, onPick: (String) -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xEE1A0B3D)).border(1.5.dp, CM.Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("👍", "😂", "😈", "😮", "😡", "🎉", "🙏").forEach { e ->
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun Banner(ui: BhabhiUi, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.bannerAt) {
        if (ui.banner != null && System.currentTimeMillis() - ui.bannerAt < 1500) {
            text = ui.banner
            delay(1600)
            text = null
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(text != null, enter = scaleIn(spring(0.5f, 400f), initialScale = 0.6f) + fadeIn(), exit = fadeOut() + scaleOut()) {
            Box(
                Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6120634)).border(1.5.dp, CM.Gold, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text(text ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }
        }
    }
}

// ------------------------------------------------------------------------------------------- overlays

@Composable
private fun Scrim(alpha: Float = 0.8f, onTap: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF0B2A1E).copy(alpha = alpha * 0.85f), Color.Black.copy(alpha = alpha))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap?.invoke() },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun BhabhiResultOverlay(s: BhabhiState, ui: BhabhiUi, r: BhabhiResult, onHome: () -> Unit, onAgain: (() -> Unit)?, onRoom: (() -> Unit)? = null) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.45f, 200f)) }
    Scrim(0.88f) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(if (r.bhabhi) "🙈" else if (r.place == 1) "🏆" else "🎉", fontSize = 72.sp, modifier = Modifier.scale(pop.value))
            OutlinedTitle(
                when {
                    r.bhabhi -> stringResource(R.string.bhabhi_result_you_bhabhi)
                    r.place == 1 -> stringResource(R.string.bhabhi_result_first)
                    else -> stringResource(R.string.bhabhi_result_got_away)
                },
                fontSize = if (r.bhabhi) 34.sp else 42.sp, modifier = Modifier.scale(pop.value),
            )
            Text(
                if (r.bhabhi) stringResource(R.string.bhabhi_result_bhabhi_sub) else stringResource(R.string.bhabhi_result_finished, ordinal(r.place), s.players),
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                // finishing order, Bhabhi last
                (s.out + s.loser).forEachIndexed { i, seat ->
                    val info = ui.seats.getOrNull(seat)
                    val loser = seat == s.loser
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (loser) Color(0x44E53935) else if (seat == ui.me) Color(0x330A8A55) else Color(0x14FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (loser) "😝" else medal(i + 1), fontSize = 18.sp, modifier = Modifier.width(34.dp))
                        if (info != null) AvatarBadge(AvatarRef.emoji(info.avatar), size = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (seat == ui.me) stringResource(R.string.bhabhi_you) else info?.name ?: stringResource(R.string.bhabhi_player),
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1,
                        )
                        Text(
                            if (loser) stringResource(R.string.bhabhi_result_left, s.hands[seat].size) else pluralStringResource(R.plurals.bhabhi_pickups, s.pickups[seat], s.pickups[seat]),
                            color = if (loser) Color(0xFFFF8A8A) else CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(26.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("+${r.coins}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    }
                    Text(stringResource(R.string.bhabhi_result_xp, r.xp), color = Color(0xFFC0A6FF), fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                r.achievements.forEach { a ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFC93C)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(a.icon, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.bhabhi_achievement, a.title), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text(a.description, color = CM.TextDim, fontSize = 11.sp)
                        }
                        Text("+${a.reward}", color = CM.GoldLight, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(stringResource(if (onAgain == null) R.string.mindi_leave else R.string.bhabhi_home), onHome, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), icon = Icons.Rounded.Home)
                if (onRoom != null) GameButton(stringResource(R.string.mindi_room), onRoom, style = ButtonStyle.BLUE, modifier = Modifier.weight(1f))
                if (onAgain != null) GameButton(stringResource(if (onRoom != null) R.string.mindi_rematch else R.string.bhabhi_play_again), onAgain, style = ButtonStyle.GOLD, modifier = Modifier.weight(1.4f), icon = Icons.Rounded.Refresh)
            }
            if (ui.net == NetRole.GUEST) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.mindi_waiting_host), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}

/** A finishing place in words, e.g. "3rd" / "तीसरे" / "ત્રીજા" (tables have at most 8 players). */
@Composable
private fun ordinal(n: Int): String = when (n) {
    1 -> stringResource(R.string.bhabhi_ordinal_1)
    2 -> stringResource(R.string.bhabhi_ordinal_2)
    3 -> stringResource(R.string.bhabhi_ordinal_3)
    4 -> stringResource(R.string.bhabhi_ordinal_4)
    5 -> stringResource(R.string.bhabhi_ordinal_5)
    6 -> stringResource(R.string.bhabhi_ordinal_6)
    7 -> stringResource(R.string.bhabhi_ordinal_7)
    8 -> stringResource(R.string.bhabhi_ordinal_8)
    else -> stringResource(R.string.bhabhi_ordinal_n, n)
}

@Composable
private fun BhabhiPauseMenu(onResume: () -> Unit, onHowTo: () -> Unit, onQuit: () -> Unit) {
    Scrim(0.8f, onTap = onResume) {
        GlassPanel(Modifier.padding(horizontal = 32.dp).fillMaxWidth(), padding = 20.dp) {
            OutlinedTitle(stringResource(R.string.bhabhi_paused), fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            GameButton(stringResource(R.string.bhabhi_resume), onResume, style = ButtonStyle.GREEN, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.bhabhi_how_to), onHowTo, style = ButtonStyle.BLUE, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.MenuBook)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.bhabhi_saved_note), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.bhabhi_quit), onQuit, style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Home)
        }
    }
}
