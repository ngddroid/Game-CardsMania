package com.puzzel.lab.cardsmania.ui.mindi

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.data.AvatarRef
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.mindi.MindiPhase
import com.puzzel.lab.cardsmania.mindi.MindiState
import com.puzzel.lab.cardsmania.mindi.MindiTrumpMode
import com.puzzel.lab.cardsmania.ui.game.FrameDriver
import com.puzzel.lab.cardsmania.ui.game.rememberFrameTick
import com.puzzel.lab.cardsmania.ui.game.wakeOnTouch
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoundIconButton
import com.puzzel.lab.cardsmania.ui.components.rememberPulse
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val TeamBlue = Color(0xFF2F8BFF)
val TeamRed = Color(0xFFFF4D5E)

@Composable
fun MindiScreen(
    launch: MindiLaunch,
    vmKey: String,
    onExit: () -> Unit,
    onHowTo: () -> Unit,
    onPlayAgain: (MindiLaunch) -> Unit,
    /** Multiplayer host only: return everyone to the room lobby after a match. */
    onBackToRoom: (() -> Unit)? = null,
) {
    val vm: MindiViewModel = viewModel(key = vmKey)
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
            ui.matchResult != null -> onExit()
            ui.showLastTrick -> vm.toggleLastTrick(false)
            showEmotes -> showEmotes = false
            showPause -> showPause = false
            else -> showPause = true
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OutlinedTitle(stringResource(R.string.mindi_shuffling), fontSize = 28.sp) }
            return@BoxWithConstraints
        }
        val layout = remember(wPx, hPx, s.players, topInset, bottomInset, ui.me) {
            MindiLayout(wPx, hPx, density.density, s.players, ui.me, topInset, bottomInset).also {
                PlayingCardArt.configure((it.handCardW * 1.1f).toInt())
            }
        }
        val world = remember(layout, vm) { MindiWorld(layout) }
        MindiTable(vm, world, ui)

        // seats
        for (seat in 0 until s.players) {
            MindiSeatView(seat, s, ui, layout)
        }
        MindiTopBar(s, ui, layout, onPause = { showPause = true })
        MindiTracker(s, ui, layout)
        MindiActionBar(
            ui, layout,
            onHint = vm::hint,
            onLastTrick = { vm.toggleLastTrick(true) },
            onEmote = { showEmotes = !showEmotes },
        )
        StatusPill(s, ui, layout)
        if (showEmotes) {
            EmoteRow(Modifier.offset { IntOffset(0, (layout.actionY - 110 * density.density).roundToInt()) }) {
                vm.emote(it)
                showEmotes = false
            }
        }
        Banner(ui, Modifier.offset { IntOffset(0, (layout.center.y - 30 * density.density).roundToInt()) })

        if (ui.showLastTrick) LastTrickDialog(s, ui, onClose = { vm.toggleLastTrick(false) })
        val hr = ui.handResult
        val isGuest = ui.net == com.puzzel.lab.cardsmania.net.NetRole.GUEST
        val isHost = ui.net == com.puzzel.lab.cardsmania.net.NetRole.HOST
        if (hr != null && ui.matchResult == null && s.phase is MindiPhase.HandOver) {
            HandResultOverlay(s, ui, hr, onNext = if (isGuest) null else vm::nextHand)
        }
        ui.matchResult?.let { mr ->
            MatchResultOverlay(
                s, ui, mr, onHome = onExit,
                onAgain = if (isGuest) null else ({ onPlayAgain(MindiLaunch(setup = s.setup, daily = null)) }),
                onRoom = if (isHost) onBackToRoom else null,
            )
        }
        ui.ended?.let { reason -> com.puzzel.lab.cardsmania.ui.game.EndedDialog(reason, onOk = onExit) }
        if (showPause) {
            MindiPauseMenu(
                net = ui.net,
                onResume = { showPause = false },
                onHowTo = { showPause = false; onHowTo() },
                onQuit = { showPause = false; vm.abandon(); onExit() },
            )
        }
    }
}

// ------------------------------------------------------------------------------------------- table canvas

@Composable
private fun MindiTable(vm: MindiViewModel, world: MindiWorld, ui: MindiUi) {
    val layout = world.layout
    val driver = remember { FrameDriver() }
    SideEffect {
        val theme = ui.profile.tableTheme.let { if (it == com.puzzel.lab.cardsmania.data.TableTheme.ROYAL) com.puzzel.lab.cardsmania.data.TableTheme.FELT else it }
        val changed = world.back != ui.profile.cardBack || world.theme != theme || world.reduceMotion != ui.settings.reduceMotion
        world.back = ui.profile.cardBack
        world.theme = theme
        world.reduceMotion = ui.settings.reduceMotion
        world.speed = when (ui.settings.speed) { 0 -> 1.3f; 2 -> 0.65f; else -> 1f }
        if (changed) driver.wake()
    }
    LaunchedEffect(world, vm) {
        vm.lastShown?.let { if (world.state == null) { world.apply(it, 0.01f); driver.wake() } }
        vm.steps.collect { world.apply(it, world.speed); driver.wake() }
    }
    LaunchedEffect(world, ui.legal, ui.selected, ui.hint, ui.idle, ui.hidePick) {
        world.legal = ui.legal
        world.selected = ui.selected
        world.hint = ui.hint
        world.interactive = ui.idle
        world.hideMode = ui.hidePick
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
                    dragged && lastY < layout.handTop - layout.handCardH * 0.3f -> vm.dragPlay(id)
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

@Composable
private fun MindiTopBar(s: MindiState, ui: MindiUi, layout: MindiLayout, onPause: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, layout.topInset.roundToInt()) }.height(with(density) { layout.hudH.toDp() }).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Rounded.Pause, onPause, size = 40.dp, contentDescription = stringResource(R.string.mindi_cd_pause))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.mindi_hud_title, if (ui.daily != null) stringResource(R.string.mindi_hud_daily) else s.setup.trumpMode.title.uppercase()),
                color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp,
            )
            Text(
                if (s.setup.target <= 1) stringResource(R.string.mindi_hud_line_single, s.handNo, s.players)
                else stringResource(R.string.mindi_hud_line_target, s.handNo, s.setup.target, s.players),
                color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(48.dp))
    }
}

/** Score + 10s tracker for both teams, with the trump indicator in the middle. */
@Composable
private fun MindiTracker(s: MindiState, ui: MindiUi, layout: MindiLayout) {
    val density = LocalDensity.current
    val myTeam = s.team(ui.me)
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, layout.trackerTop.roundToInt()) }.height(with(density) { layout.trackerH.toDp() }).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TeamPanel(stringResource(R.string.mindi_your_team), TeamBlue, s, myTeam, Modifier.weight(1f))
        TrumpIndicator(s, ui)
        TeamPanel(stringResource(R.string.mindi_rivals), TeamRed, s, 1 - myTeam, Modifier.weight(1f))
    }
}

@Composable
private fun TeamPanel(title: String, color: Color, s: MindiState, team: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xCC0B1A33), Color(0xCC050A18))))
            .border(1.5.dp, color.copy(alpha = 0.8f), shape)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(4.dp))
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
            Text("★${s.scores[team]}", color = CM.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(3.dp))
        // the 10s this team holds
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val tens = s.tens[team]
            repeat(s.totalTens.coerceAtMost(8)) { i ->
                val c = tens.getOrNull(i)
                Box(
                    Modifier.size(width = if (s.totalTens > 4) 13.dp else 18.dp, height = if (s.totalTens > 4) 18.dp else 24.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (c != null) Color.White else Color(0x22FFFFFF))
                        .border(1.dp, if (c != null) CM.Gold else Color(0x33FFFFFF), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (c != null) Text(
                        MindiViewModel.suitSymbol(c.suit), fontSize = if (s.totalTens > 4) 10.sp else 13.sp, fontWeight = FontWeight.Black,
                        color = if (c.suit == 1 || c.suit == 2) Color(0xFFD7263D) else Color(0xFF17151F),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${s.tricks[team]}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(" " + stringResource(R.string.mindi_tricks_label), color = CM.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrumpIndicator(s: MindiState, ui: MindiUi) {
    val pulse by rememberPulse(900)
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xEE3A1784), Color(0xEE1A0844))))
            .border(1.5.dp, CM.Gold, RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.mindi_trump_label), color = CM.GoldLight, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        val trump = s.trump
        when {
            trump != null -> {
                Text(
                    MindiViewModel.suitSymbol(trump), fontSize = 30.sp, fontWeight = FontWeight.Black,
                    color = if (trump == 1 || trump == 2) Color(0xFFFF5A6E) else Color.White,
                    modifier = Modifier.graphicsLayer { scaleX = 1f + pulse * 0.06f; scaleY = 1f + pulse * 0.06f },
                )
                Text(MindiViewModel.suitName(trump), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            s.hidden != null -> {
                val mine = s.hiddenOwner == ui.me
                val card = s.hidden
                Box(Modifier.size(width = 26.dp, height = 36.dp)) {
                    Image(
                        if (mine && card != null) PlayingCardArt.face(card.artRank, card.suit) else CardArt.back(ui.profile.cardBack),
                        null, Modifier.fillMaxSize()
                    )
                }
                Text(stringResource(if (mine) R.string.mindi_trump_yours else R.string.mindi_trump_hidden), color = CM.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            s.setup.trumpMode == MindiTrumpMode.CUT -> {
                Text("?", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(stringResource(R.string.mindi_trump_not_cut), color = CM.TextDim, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            else -> {
                Text("…", fontSize = 26.sp, color = Color.White)
                Text(stringResource(R.string.mindi_trump_choosing), color = CM.TextDim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MindiSeatView(seat: Int, s: MindiState, ui: MindiUi, layout: MindiLayout) {
    val density = LocalDensity.current
    val info = ui.seats.getOrNull(seat) ?: return
    val pos = layout.seatOf(seat)
    val av = if (seat == ui.me) layout.avatar * 0.85f else layout.avatar
    val boxPx = av * 2.3f
    val avDp = with(density) { av.toDp() }
    val active = !s.isOver && (s.phase == MindiPhase.Play && s.turn == seat || (s.phase as? MindiPhase.ChooseHidden)?.seat == seat)
    val teamColor = if (s.team(seat) == s.team(ui.me)) TeamBlue else TeamRed
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
        Box {
            AvatarBadge(AvatarRef.emoji(info.avatar), size = avDp, ringColor = teamColor)
            // multiplayer: a real player on Wi-Fi, or a bot covering for someone who dropped
            ui.netSeats[seat]?.takeIf { seat != ui.me }?.let { connected ->
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
            if (seat == s.dealer) {
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 2.dp).size(18.dp).clip(CircleShape)
                        .background(Color.White).border(1.5.dp, CM.Gold, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.mindi_dealer_badge), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A1F00)) }
            }
            if (seat != ui.me) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).clip(RoundedCornerShape(50))
                        .background(Color(0xFF1A0B3D)).border(1.5.dp, CM.Gold, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text("${s.hands[seat].size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
            if (active && seat == ui.me && ui.turnDeadline > 0 && ui.turnTotal > 0) {
                val frac = ((ui.turnDeadline - now).toFloat() / ui.turnTotal).coerceIn(0f, 1f)
                Canvas(Modifier.size(avDp)) {
                    drawArc(if (frac < 0.3f) CM.Ruby else CM.Emerald, -90f, 360f * frac, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        if (seat != ui.me) {
            Row(
                Modifier.align(Alignment.Center).offset(y = avDp * 0.72f).clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(teamColor))
                Spacer(Modifier.width(3.dp))
                Text(
                    if (s.partnersOf(ui.me).contains(seat)) stringResource(R.string.mindi_partner_name, info.name) else info.name,
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
private fun MindiActionBar(ui: MindiUi, layout: MindiLayout, onHint: () -> Unit, onLastTrick: () -> Unit, onEmote: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, (layout.actionY - 21 * density.density).roundToInt()) }.padding(start = 64.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Rounded.Lightbulb, onHint, size = 42.dp, tint = if (ui.idle && ui.legal.isNotEmpty()) CM.GoldLight else Color.White, contentDescription = stringResource(R.string.mindi_cd_hint))
        RoundIconButton(Icons.Rounded.History, onLastTrick, size = 42.dp, contentDescription = stringResource(R.string.mindi_cd_last_trick))
        RoundIconButton(Icons.Rounded.EmojiEmotions, onEmote, size = 42.dp, contentDescription = stringResource(R.string.mindi_cd_emote))
    }
}

@Composable
private fun StatusPill(s: MindiState, ui: MindiUi, layout: MindiLayout) {
    val density = LocalDensity.current
    val myTurn = ui.idle && (s.phase == MindiPhase.Play && s.turn == ui.me || ui.hidePick)
    val text = when {
        !ui.idle || s.isOver -> ""
        ui.hidePick -> stringResource(if (ui.selected != null) R.string.mindi_status_hide_confirm else R.string.mindi_status_hide_pick)
        s.phase == MindiPhase.Play && s.turn == ui.me -> when {
            ui.selected != null -> stringResource(R.string.mindi_status_tap_again)
            s.trick.isEmpty() -> stringResource(R.string.mindi_status_your_lead)
            else -> stringResource(R.string.mindi_status_your_turn)
        }
        else -> {
            val actor = (s.phase as? MindiPhase.ChooseHidden)?.seat ?: s.turn
            stringResource(R.string.mindi_status_thinking, ui.seats.getOrNull(actor)?.name ?: stringResource(R.string.mindi_player))
        }
    }
    Box(Modifier.fillMaxWidth().offset { IntOffset(0, (layout.center.y + layout.trickCardH * 1.05f).roundToInt()) }, contentAlignment = Alignment.Center) {
        AnimatedVisibility(text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (myTurn) Brush.horizontalGradient(listOf(Color(0xCCB36B00), Color(0xCCFFB300), Color(0xCCB36B00))) else Brush.horizontalGradient(listOf(Color(0x99000000), Color(0x99000000))))
                    .border(1.dp, if (myTurn) CM.GoldLight else Color(0x33FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) { Text(text, color = if (myTurn) Color(0xFF3A1F00) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = density
}

@Composable
private fun EmoteRow(modifier: Modifier, onPick: (String) -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xEE1A0B3D)).border(1.5.dp, CM.Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("👍", "🤝", "😂", "😮", "😡", "🎉", "🙏").forEach { e ->
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun Banner(ui: MindiUi, modifier: Modifier) {
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
