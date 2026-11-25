package com.ngdstudio.mindimania.ui.teenpatti

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.ngdstudio.mindimania.data.AvatarRef
import com.ngdstudio.mindimania.data.DifficultyInfo
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.teenpatti.TPKind
import com.ngdstudio.mindimania.teenpatti.TPPhase
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiState
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoundIconButton
import com.ngdstudio.mindimania.ui.components.rememberPulse
import com.ngdstudio.mindimania.ui.game.FrameDriver
import com.ngdstudio.mindimania.ui.game.rememberFrameTick
import com.ngdstudio.mindimania.ui.game.wakeOnTouch
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TeenPattiScreen(
    launch: TeenPattiLaunch,
    vmKey: String,
    onExit: () -> Unit,
    onHowTo: () -> Unit,
    onPlayAgain: (TeenPattiLaunch) -> Unit,
    /** Wi-Fi host only: everyone goes back to the room lobby. */
    onBackToRoom: (() -> Unit)? = null,
) {
    val vm: TeenPattiViewModel = viewModel(key = vmKey)
    LaunchedEffect(vm) { vm.start(launch) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPause by remember { mutableStateOf(false) }
    var showEmotes by remember { mutableStateOf(false) }
    var showRanks by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.abandon() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler {
        when {
            ui.result != null -> onExit()
            showRanks -> showRanks = false
            showEmotes -> showEmotes = false
            else -> showPause = !showPause
        }
    }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
    // landscape: keep clear of a camera cutout or a side navigation bar
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val sideL = maxOf(WindowInsets.displayCutout.getLeft(density, dir), WindowInsets.navigationBars.getLeft(density, dir)).toFloat()
    val sideR = maxOf(WindowInsets.displayCutout.getRight(density, dir), WindowInsets.navigationBars.getRight(density, dir)).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF14051F))) {
        val s = ui.state
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        if (s == null || wPx <= 0f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OutlinedTitle(stringResource(R.string.tp_shuffling), fontSize = 28.sp) }
            return@BoxWithConstraints
        }
        val layout = remember(wPx, hPx, s.players, topInset, bottomInset, sideL, sideR, ui.me) {
            TeenPattiLayout(wPx, hPx, density.density, s.players, ui.me, topInset, bottomInset, sideL, sideR).also {
                PlayingCardArt.configure((it.myCardW * 1.1f).toInt())
            }
        }
        val world = remember(layout, vm) { TeenPattiWorld(layout) }
        TeenPattiTable(vm, world, ui)

        for (seat in 0 until s.players) if (seat != ui.me) SeatView(seat, s, ui, layout)
        MySeatView(s, ui, layout, onSee = vm::see)
        TopBar(s, layout, onPause = { showPause = true }, onRanks = { showRanks = true }, onEmote = { showEmotes = !showEmotes })
        // the middle of the table: only the dealer (drawn on the canvas) and the pot total under her
        PotLabel(s, layout)
        ActionBar(
            s, ui, layout, vm,
            onEmote = { showEmotes = !showEmotes },
        )
        if (showEmotes) {
            // landscape: under the top bar (where the button now is); portrait: above the buttons
            EmoteRow(Modifier.offset { IntOffset(0, (if (layout.landscape) layout.topInset + layout.hudH + 22 * density.density else layout.actionTop - 60 * density.density).roundToInt()) }) {
                vm.emote(it)
                showEmotes = false
            }
        }
        Banner(ui, Modifier.offset { IntOffset(0, (layout.center.y - 30 * density.density).roundToInt()) })

        val ph = s.phase
        if (ui.idle && ph is TPPhase.Sideshow && ph.to == ui.me && ui.result == null) {
            SideshowAsk(ui.seats.getOrNull(ph.from)?.name ?: stringResource(R.string.tp_player), onAccept = { vm.reply(true) }, onDecline = { vm.reply(false) })
        }
        if (showRanks) HandRankings(s.setup.a23Second, onClose = { showRanks = false })
        ui.result?.let { r ->
            ResultOverlay(s, ui, r, onHome = onExit, onAgain = { onPlayAgain(TeenPattiLaunch(setup = s.setup)) })
        }
        ui.ended?.let { reason -> com.ngdstudio.mindimania.ui.game.EndedDialog(reason, onOk = onExit) }
        if (showPause) {
            PauseMenu(
                net = ui.net,
                onResume = { showPause = false },
                onRanks = { showPause = false; showRanks = true },
                onHowTo = { showPause = false; onHowTo() },
                onQuit = { showPause = false; vm.abandon(); onExit() },
                onRoom = onBackToRoom?.let { back -> { showPause = false; back() } },
            )
        }
    }
}

// ------------------------------------------------------------------------------------------- table canvas

@Composable
private fun TeenPattiTable(vm: TeenPattiViewModel, world: TeenPattiWorld, ui: TeenPattiUi) {
    val driver = remember { FrameDriver() }
    SideEffect {
        val theme = ui.profile.tableTheme.let { if (it == TableTheme.ROYAL) TableTheme.FELT else it }
        val changed = world.back != ui.profile.cardBack || world.theme != theme || world.reduceMotion != ui.settings.reduceMotion
        world.back = ui.profile.cardBack
        world.theme = theme
        world.reduceMotion = ui.settings.reduceMotion
        world.speed = (when (ui.settings.speed) { 0 -> 1.3f; 2 -> 0.65f; else -> 1f }) * (if (ui.fastForward) 0.4f else 1f)
        val see = ui.options.canSee
        if (world.canSee != see) { world.canSee = see; driver.wake() }
        if (changed) driver.wake()
    }
    LaunchedEffect(world, vm) {
        // a new world (first frame, or the size changed on rotation) catches up with the table as it is now
        vm.lastShown?.let { if (world.state == null) { world.apply(com.ngdstudio.mindimania.teenpatti.TPStep(com.ngdstudio.mindimania.teenpatti.TPEvent.Restored(it.state.handNo), it.state), 0.01f); driver.wake() } }
        vm.steps.collect { world.apply(it, world.speed); driver.wake() }
    }
    val tick by rememberFrameTick(driver, world)
    Canvas(
        Modifier.fillMaxSize().wakeOnTouch(driver).pointerInput(world, vm) {
            detectTapGestures { pos -> if (world.hitMine(pos.x, pos.y)) vm.tapCards() }
        }
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        val now = SystemClock.uptimeMillis()
        drawIntoCanvas { world.drawBase(it.nativeCanvas, now) }
        // the dealer sits in the middle, between the felt and the cards
        val L = world.layout
        translate(world.fx.shakeX, world.fx.shakeY) {
            drawDealer(L.center, L.dealerH, now, dealing = now < world.dealingUntil)
        }
        drawIntoCanvas { world.drawTop(it.nativeCanvas, now) }
    }
}

// ------------------------------------------------------------------------------------------- HUD

private fun Modifier.centeredAt(pos: Offset, sizePx: Float) = this.offset { IntOffset((pos.x - sizePx / 2).roundToInt(), (pos.y - sizePx / 2).roundToInt()) }

@Composable
private fun TopBar(s: TeenPattiState, layout: TeenPattiLayout, onPause: () -> Unit, onRanks: () -> Unit, onEmote: () -> Unit) {
    val density = LocalDensity.current
    Row(
        // landscape: a little taller so the variation rule fits as a third line (the title overlaps no seat there)
        Modifier.fillMaxWidth().offset { IntOffset(0, layout.topInset.roundToInt()) }.height(with(density) { layout.hudH.toDp() } + (if (layout.landscape) 16.dp else 0.dp))
            .padding(start = with(density) { layout.leftInset.toDp() } + 10.dp, end = with(density) { layout.rightInset.toDp() } + 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Rounded.Pause, onPause, size = 40.dp, contentDescription = stringResource(R.string.tp_cd_pause))
        Spacer(Modifier.width(8.dp))
        // landscape: title top-left, leaving the top-centre seat free
        Column(Modifier.weight(1f), horizontalAlignment = if (layout.landscape) Alignment.Start else Alignment.CenterHorizontally) {
            Text(
                if (s.endless) stringResource(R.string.tp_top_title_endless, stringResource(R.string.tp_title), s.handNo)
                else stringResource(R.string.tp_top_title, stringResource(R.string.tp_title), s.handNo, s.setup.hands),
                color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
            Text(stringResource(R.string.tp_top_sub, s.boot, s.stake), color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        // landscape: the emote button lives up here (the bottom row is next to my seat's name and chips)
        if (layout.landscape) {
            RoundIconButton(Icons.Rounded.EmojiEmotions, onEmote, size = 40.dp, contentDescription = stringResource(R.string.tp_cd_emote))
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(Icons.Rounded.Info, onRanks, size = 40.dp, contentDescription = stringResource(R.string.tp_cd_rankings))
    }
}

@Composable
private fun PotLabel(s: TeenPattiState, layout: TeenPattiLayout) {
    if (s.handOver || s.pot <= 0) return
    Box(Modifier.fillMaxWidth().offset { IntOffset((layout.center.x - layout.width / 2).roundToInt(), (layout.potLabelY - 12 * layout.density).roundToInt()) }, contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC1A0B3D)).border(1.5.dp, CM.Gold, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFE53935)).border(2.dp, Color.White, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.tp_pot, s.pot), color = CM.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** BLIND / SEEN / PACKED / OUT for a seat. */
@Composable
private fun seatTag(s: TeenPattiState, seat: Int): Pair<String, Color>? = when {
    !s.inHand[seat] -> stringResource(R.string.tp_tag_out) to Color(0xFF6B6B7A)
    s.folded[seat] -> stringResource(R.string.tp_tag_packed) to Color(0xFF8A2B3B)
    seat in s.shown -> null
    s.seen[seat] -> stringResource(R.string.tp_tag_seen) to Color(0xFF1E6FE8)
    else -> stringResource(R.string.tp_tag_blind) to Color(0xFF3A1A99)
}

@Composable
private fun SeatView(seat: Int, s: TeenPattiState, ui: TeenPattiUi, layout: TeenPattiLayout) {
    val density = LocalDensity.current
    val info = ui.seats.getOrNull(seat) ?: return
    val pos = layout.seatOf(seat)
    val av = layout.avatar
    val boxPx = av * 2.3f
    val avDp = with(density) { av.toDp() }
    val ph = s.phase
    val active = !s.handOver && s.turn == seat
    val winner = ph is TPPhase.HandOver && seat in ph.winners
    val out = !s.inHand[seat] || s.folded[seat]
    val pulse by rememberPulse(650)
    Box(Modifier.centeredAt(pos, boxPx).size(with(density) { boxPx.toDp() }), contentAlignment = Alignment.Center) {
        if (active || winner) {
            Canvas(Modifier.size(avDp * 1.55f)) {
                drawCircle(Brush.radialGradient(listOf((if (winner) CM.Emerald else CM.Gold).copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent)), radius = size.minDimension / 2)
            }
        }
        Box(Modifier.alpha(if (out) 0.55f else 1f)) {
            AvatarBadge(
                AvatarRef.emoji(info.avatar), size = avDp,
                ringColor = when { winner -> CM.Emerald; active -> CM.GoldLight; else -> Color(0xFF8E7CC3) },
            )
            if (s.dealer == seat) DealerBadge(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-4).dp))
        }
        if (winner) WinnerRibbon(Modifier.align(Alignment.Center).offset(y = -avDp * 0.72f))
        else seatTag(s, seat)?.let { (text, color) ->
            Box(
                Modifier.align(Alignment.Center).offset(y = -avDp * 0.7f).clip(RoundedCornerShape(50)).background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) }
        }
        Column(Modifier.align(Alignment.Center).offset(y = avDp * 0.78f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    info.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(if (s.players >= 6) 54.dp else 66.dp),
                )
                if (!info.isHuman) Box(Modifier.size(6.dp).clip(CircleShape).background(DifficultyInfo.color(info.difficulty)))
            }
            ChipCount(s.chips[seat])
            // once the hands are opened: what this player held
            if (seat in s.shown && !s.folded[seat]) ShownHandLabel(handLabel(s, seat), winner)
        }
        ui.emotes[seat]?.let { (text, at) -> EmoteBubble(text, at, Modifier.align(Alignment.TopCenter).offset(x = avDp * 0.6f, y = (-avDp * 0.2f))) }
    }
}

/** Gold "WINNER" ribbon over the avatar of whoever took the pot. */
@Composable
private fun WinnerRibbon(modifier: Modifier) {
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.4f, 300f)) }
    Box(
        modifier.scale(pop.value).clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFE0A100))))
            .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 1.dp)
    ) { Text(stringResource(R.string.tp_winner), color = Color(0xFF4A2A00), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
}

/** The name of an opened hand ("TRAIL", "PAIR", "Score 963") under the player's chips. */
@Composable
private fun ShownHandLabel(text: String, winner: Boolean) {
    Text(
        text.uppercase(), color = if (winner) Color(0xFF4A2A00) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50))
            .background(if (winner) Brush.horizontalGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFB300))) else Brush.horizontalGradient(listOf(Color(0xCC3A1A99), Color(0xCC3A1A99))))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun DealerBadge(modifier: Modifier) {
    Box(
        modifier.size(18.dp).clip(CircleShape).background(Color.White).border(1.5.dp, CM.GoldDeep, CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(stringResource(R.string.tp_dealer), color = Color(0xFF3A1F00), fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun ChipCount(chips: Int, big: Boolean = false) {
    Row(
        Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(Color(0xCC1A0B3D)).border(1.dp, CM.Gold.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (big) 11.dp else 9.dp).clip(CircleShape).background(Color(0xFFE53935)).border(1.5.dp, Color.White, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("$chips", color = CM.GoldLight, fontSize = if (big) 13.sp else 11.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Me: avatar, name, chips, tag, my hand's name once seen, and the turn timer.
 * Landscape: avatar centred under the dealer above my cards, name + chips + tag on its right, hand name on its left.
 */
@Composable
private fun MySeatView(s: TeenPattiState, ui: TeenPattiUi, layout: TeenPattiLayout, onSee: () -> Unit) {
    val density = LocalDensity.current
    val me = ui.me
    val info = ui.seats.getOrNull(me) ?: return
    if (layout.landscape) {
        MySeatLandscape(s, ui, layout, info, onSee)
        return
    }
    val av = layout.avatar * 0.9f
    val avDp = with(density) { av.toDp() }
    val boxPx = av * 2.2f
    val active = ui.idle && !s.handOver && s.turn == me
    val ph = s.phase
    val winner = ph is TPPhase.HandOver && me in ph.winners
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.turnDeadline, active) {
        while (active && ui.turnDeadline > 0) { now = System.currentTimeMillis(); delay(100) }
    }
    Box(Modifier.centeredAt(layout.mySeat, boxPx).size(with(density) { boxPx.toDp() }), contentAlignment = Alignment.Center) {
        Box {
            AvatarBadge(AvatarRef.emoji(info.avatar), size = avDp, ringColor = when { winner -> CM.Emerald; active -> CM.GoldLight; else -> Color(0xFF8E7CC3) })
            if (s.dealer == me) DealerBadge(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-4).dp))
            if (active && ui.turnDeadline > 0 && ui.turnTotal > 0) {
                val frac = ((ui.turnDeadline - now).toFloat() / ui.turnTotal).coerceIn(0f, 1f)
                Canvas(Modifier.size(avDp)) {
                    drawArc(if (frac < 0.3f) CM.Ruby else CM.Emerald, -90f, 360f * frac, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        if (winner) WinnerRibbon(Modifier.align(Alignment.Center).offset(y = -avDp * 0.72f))
        else seatTag(s, me)?.let { (text, color) ->
            Box(
                Modifier.align(Alignment.Center).offset(y = -avDp * 0.7f).clip(RoundedCornerShape(50)).background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }
        Box(Modifier.align(Alignment.Center).offset(y = avDp * 0.72f)) { ChipCount(s.chips[me], big = true) }
        ui.emotes[me]?.let { (text, at) -> EmoteBubble(text, at, Modifier.align(Alignment.TopCenter).offset(x = avDp * 0.6f, y = (-avDp * 0.2f))) }
    }
    // SEE sits right on my face-down cards
    if (ui.options.canSee && s.inHand[me] && !s.folded[me] && s.hands[me].size == 3) {
        val c = layout.cardsOf(me)
        Box(Modifier.centeredAt(c, layout.myCardW * 1.9f).size(with(density) { (layout.myCardW * 1.9f).toDp() }), contentAlignment = Alignment.Center) {
            GameButton(
                stringResource(R.string.tp_btn_see), onSee, style = ButtonStyle.GREEN, icon = Icons.Rounded.Visibility,
                height = 42.dp, fontSize = 16.sp, modifier = Modifier.width(118.dp),
            )
        }
    }
    // the name of my hand once I've looked
    if (s.inHand[me] && !s.folded[me] && s.hands[me].size == 3 && (s.seen[me] || me in s.shown)) {
        val c = layout.cardsOf(me)
        val text = handLabel(s, me)
        val strong = s.seen[me] && TeenPattiEngine.handKind(s, me) >= TPKind.PAIR
        val textColor = if (strong) CM.GoldLight else Color.White.copy(alpha = 0.85f)
        run {
            // portrait: above my cards
            Box(
                Modifier.fillMaxWidth().offset { IntOffset(0, (c.y - layout.myCardH * 0.5f - 24 * density.density).roundToInt()) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text, color = textColor, fontSize = if (s.seen[me]) 14.sp else 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.offset { IntOffset((c.x - layout.width / 2).roundToInt(), 0) }
                        .clip(RoundedCornerShape(50)).background(Color(0x88000000)).padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Landscape: my seat in the middle, straight under the dealer and the pot (like sitting across from her). */
@Composable
private fun MySeatLandscape(s: TeenPattiState, ui: TeenPattiUi, layout: TeenPattiLayout, info: com.ngdstudio.mindimania.teenpatti.TeenPattiSeat, onSee: () -> Unit) {
    val density = LocalDensity.current
    val me = ui.me
    val av = layout.myAvatar
    val avDp = with(density) { av.toDp() }
    val active = ui.idle && !s.handOver && s.turn == me
    val ph = s.phase
    val winner = ph is TPPhase.HandOver && me in ph.winners
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.turnDeadline, active) {
        while (active && ui.turnDeadline > 0) { now = System.currentTimeMillis(); delay(100) }
    }
    val seat = layout.mySeat
    // the avatar, with my turn glow and timer ring
    Box(Modifier.centeredAt(seat, av * 1.5f).size(avDp * 1.5f), contentAlignment = Alignment.Center) {
        if (active || winner) {
            val pulse by rememberPulse(650)
            Canvas(Modifier.size(avDp * 1.5f)) {
                drawCircle(Brush.radialGradient(listOf((if (winner) CM.Emerald else CM.Gold).copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent)), radius = size.minDimension / 2)
            }
        }
        Box {
            AvatarBadge(AvatarRef.emoji(info.avatar), size = avDp, ringColor = when { winner -> CM.Emerald; active -> CM.GoldLight; else -> Color(0xFF8E7CC3) })
            if (s.dealer == me) DealerBadge(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-4).dp))
            if (active && ui.turnDeadline > 0 && ui.turnTotal > 0) {
                val frac = ((ui.turnDeadline - now).toFloat() / ui.turnTotal).coerceIn(0f, 1f)
                Canvas(Modifier.size(avDp)) {
                    drawArc(if (frac < 0.3f) CM.Ruby else CM.Emerald, -90f, 360f * frac, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        ui.emotes[me]?.let { (text, at) -> EmoteBubble(text, at, Modifier.align(Alignment.TopEnd)) }
    }
    // right of the avatar, one row at its height: my name and chips (never down on my cards)
    Row(
        Modifier.offset { IntOffset(layout.myInfoLeft.roundToInt(), (seat.y - 15 * density.density).roundToInt()) }.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            info.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        ChipCount(s.chips[me], big = true)
    }
    // SEE sits right on my face-down cards
    if (ui.options.canSee && s.inHand[me] && !s.folded[me] && s.hands[me].size == 3) {
        val c = layout.cardsOf(me)
        Box(Modifier.centeredAt(c, layout.myCardW * 1.9f).size(with(density) { (layout.myCardW * 1.9f).toDp() }), contentAlignment = Alignment.Center) {
            GameButton(
                stringResource(R.string.tp_btn_see), onSee, style = ButtonStyle.GREEN, icon = Icons.Rounded.Visibility,
                height = 40.dp, fontSize = 15.sp, modifier = Modifier.width(112.dp),
            )
        }
    }
    // left of the avatar, one row at its height: the name of my hand once I've looked, then my tag
    // (BLIND / SEEN / PACKED, or WINNER) right next to the avatar
    val showHand = s.inHand[me] && !s.folded[me] && s.hands[me].size == 3 && (s.seen[me] || me in s.shown)
    Row(
        Modifier.offset { IntOffset(0, (seat.y - 15 * density.density).roundToInt()) }.width(with(density) { layout.myLabelRight.toDp() }).height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showHand) {
            val strong = TeenPattiEngine.handKind(s, me) >= TPKind.PAIR
            Text(
                handLabel(s, me), color = if (strong) CM.GoldLight else Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
        if (winner) WinnerRibbon(Modifier)
        else seatTag(s, me)?.let { (text, color) ->
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(color).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) }
        }
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

/** The table's status line ("Your turn", "Bruno is thinking…", "Momo won this round") and whether it's gold. */
@Composable
private fun statusLine(s: TeenPattiState, ui: TeenPattiUi): Pair<String, Boolean> {
    val me = ui.me
    val ph = s.phase
    val wonBy = (ph as? TPPhase.HandOver)?.winners.orEmpty()
    val youName = stringResource(R.string.tp_you)
    val playerName = stringResource(R.string.tp_player)
    val roundText = when {
        wonBy.isEmpty() -> null
        wonBy == listOf(me) -> stringResource(R.string.tp_you_won_round)
        else -> stringResource(R.string.tp_won_round, wonBy.joinToString(" & ") { if (it == me) youName else ui.seats.getOrNull(it)?.name ?: playerName })
    }
    val myTurn = ui.idle && !s.handOver && s.turn == me
    val text = when {
        roundText != null -> roundText
        s.handOver || !ui.idle -> ""
        !s.inHand[me] -> stringResource(R.string.tp_status_out)
        s.folded[me] -> stringResource(R.string.tp_status_packed)
        ph is TPPhase.Sideshow && ph.to == me -> stringResource(R.string.tp_status_reply)
        ph is TPPhase.Sideshow -> stringResource(R.string.tp_side_waiting, ui.seats.getOrNull(ph.to)?.name ?: playerName)
        myTurn -> stringResource(R.string.tp_status_your_turn)
        else -> stringResource(R.string.tp_status_thinking, ui.seats.getOrNull(s.turn)?.name ?: playerName)
    }
    return text to (myTurn || roundText != null)
}

@Composable
private fun StatusText(text: String, gold: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Text(
                text, color = if (gold) Color(0xFF3A1F00) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (gold) Brush.horizontalGradient(listOf(Color(0xCCB36B00), Color(0xEEFFB300), Color(0xCCB36B00))) else Brush.horizontalGradient(listOf(Color(0x99000000), Color(0x99000000))))
                    .border(1.dp, if (gold) CM.GoldLight else Color(0x33FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** Betting buttons: emote · SHOW or SIDESHOW on top, PACK · CHAAL · RAISE below (or PACK · ALL IN). SEE is on my cards. */
@Composable
private fun ActionBar(s: TeenPattiState, ui: TeenPattiUi, layout: TeenPattiLayout, vm: TeenPattiViewModel, onEmote: () -> Unit) {
    val density = LocalDensity.current
    val o = ui.options
    val betting = o.myTurn && !o.mustReply && ui.idle
    Column(
        Modifier.offset { IntOffset(layout.actionLeft.roundToInt(), layout.actionTop.roundToInt()) }
            .width(with(density) { layout.actionWidth.toDp() }).height(with(density) { layout.actionBarH.toDp() }).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!layout.landscape) RoundIconButton(Icons.Rounded.EmojiEmotions, onEmote, size = 44.dp, contentDescription = stringResource(R.string.tp_cd_emote))
            // the status line lives here now (the middle of the table belongs to the dealer); SEE is on my cards
            val (status, gold) = statusLine(s, ui)
            val extra = betting && (o.canShow || o.canSideshow)
            StatusText(status, gold, Modifier.weight(if (extra) 1f else 2.2f))
            when {
                betting && o.canShow -> GameButton(stringResource(R.string.tp_btn_show, o.chaalCost), vm::show, style = ButtonStyle.PURPLE, height = 44.dp, fontSize = 15.sp, modifier = Modifier.weight(1.2f))
                betting && o.canSideshow -> GameButton(stringResource(R.string.tp_btn_sideshow, o.chaalCost), vm::sideshow, style = ButtonStyle.PURPLE, height = 44.dp, fontSize = 14.sp, modifier = Modifier.weight(1.2f))
            }
        }
        if (betting) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(stringResource(R.string.tp_btn_pack), vm::pack, style = ButtonStyle.RED, height = 50.dp, fontSize = 15.sp, modifier = Modifier.weight(0.9f))
                if (o.canAllIn) {
                    GameButton(stringResource(R.string.tp_btn_all_in, s.chips[ui.me]), vm::allIn, style = ButtonStyle.GOLD, height = 50.dp, fontSize = 15.sp, modifier = Modifier.weight(2f))
                } else {
                    val blind = !s.seen[ui.me]
                    GameButton(
                        stringResource(if (blind) R.string.tp_btn_blind else R.string.tp_btn_chaal, o.chaalCost), { vm.chaal(false) },
                        style = ButtonStyle.GREEN, height = 50.dp, fontSize = 15.sp, modifier = Modifier.weight(1.1f),
                    )
                    GameButton(
                        stringResource(R.string.tp_btn_raise, o.raiseCost), { vm.chaal(true) }, style = ButtonStyle.GOLD, enabled = o.canRaise,
                        height = 50.dp, fontSize = 15.sp, modifier = Modifier.weight(1.1f),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(50.dp))
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
            listOf("👍", "😂", "😎", "😮", "😡", "🎉", "🙏").forEach { e ->
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun Banner(ui: TeenPattiUi, modifier: Modifier) {
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
            .background(Brush.radialGradient(listOf(Color(0xFF2A0B3D).copy(alpha = alpha * 0.85f), Color.Black.copy(alpha = alpha))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap?.invoke() },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Centres a dialog panel, keeps it a readable width in landscape and lets it scroll on a short screen. */
@Composable
private fun DialogColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) { Box(Modifier.widthIn(max = 480.dp)) { content() } }
}

@Composable
private fun SideshowAsk(name: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { DialogColumn {
        GlassPanel(Modifier.padding(horizontal = 28.dp).fillMaxWidth(), padding = 18.dp) {
            OutlinedTitle(stringResource(R.string.tp_side_title), fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tp_side_body, name), color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(stringResource(R.string.tp_side_decline), onDecline, style = ButtonStyle.RED, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 15.sp)
                GameButton(stringResource(R.string.tp_side_accept), onAccept, style = ButtonStyle.GREEN, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 15.sp)
            }
        }
    } }
}

/** The six hand kinds, best first, each with an example. */
@Composable
internal fun HandRankings(a23Second: Boolean, onClose: () -> Unit) {
    Scrim(0.85f, onTap = onClose) { DialogColumn {
        GlassPanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = 16.dp) {
            OutlinedTitle(stringResource(R.string.tp_rankings_title), fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            rankExamples.forEachIndexed { i, (kind, cards) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.width(20.dp))
                    MiniHand(cards)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(kindName(kind), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(stringResource(kindDesc(kind)), color = CM.TextDim, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (a23Second) R.string.tp_rankings_a23_second else R.string.tp_rankings_a23_low),
                color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GameButton(stringResource(R.string.tp_close), onClose, style = ButtonStyle.BLUE, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 16.sp)
        }
    } }
}

/** Example hands as (art rank 1 = Ace, suit). */
internal val rankExamples: List<Pair<TPKind, List<Pair<Int, Int>>>> = listOf(
    TPKind.TRAIL to listOf(1 to 0, 1 to 1, 1 to 2),
    TPKind.PURE_SEQUENCE to listOf(1 to 1, 13 to 1, 12 to 1),
    TPKind.SEQUENCE to listOf(7 to 0, 6 to 2, 5 to 3),
    TPKind.COLOR to listOf(13 to 2, 9 to 2, 4 to 2),
    TPKind.PAIR to listOf(10 to 0, 10 to 3, 6 to 1),
    TPKind.HIGH_CARD to listOf(1 to 3, 9 to 1, 4 to 0),
)

internal fun kindDesc(k: TPKind): Int = when (k) {
    TPKind.TRAIL -> R.string.tp_kind_trail_desc
    TPKind.PURE_SEQUENCE -> R.string.tp_kind_pure_desc
    TPKind.SEQUENCE -> R.string.tp_kind_sequence_desc
    TPKind.COLOR -> R.string.tp_kind_color_desc
    TPKind.PAIR -> R.string.tp_kind_pair_desc
    TPKind.HIGH_CARD -> R.string.tp_kind_high_desc
}

@Composable
internal fun MiniHand(cards: List<Pair<Int, Int>>) {
    Box(Modifier.width(76.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
        cards.forEachIndexed { i, (rank, suit) ->
            Image(
                PlayingCardArt.face(rank, suit), null,
                Modifier.offset(x = (i * 20).dp).width(33.dp).height(46.dp).graphicsLayer { rotationZ = (i - 1) * 6f }
            )
        }
    }
}

@Composable
private fun ResultOverlay(s: TeenPattiState, ui: TeenPattiUi, r: TeenPattiResult, onHome: () -> Unit, onAgain: () -> Unit) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.45f, 200f)) }
    Scrim(0.88f) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(if (r.place == 1) "🏆" else if (r.place == s.players) "😅" else "🎉", fontSize = 72.sp, modifier = Modifier.scale(pop.value))
            OutlinedTitle(
                stringResource(if (r.place == 1) R.string.tp_result_win else R.string.tp_result_over),
                fontSize = 38.sp, modifier = Modifier.scale(pop.value),
            )
            Text(
                stringResource(R.string.tp_result_place, ordinal(r.place), s.players),
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                s.ranking.forEachIndexed { i, seat ->
                    val info = ui.seats.getOrNull(seat)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (seat == ui.me) Color(0x330A8A55) else Color(0x14FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${i + 1}" }, fontSize = 18.sp, modifier = Modifier.width(34.dp))
                        if (info != null) AvatarBadge(AvatarRef.emoji(info.avatar), size = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (seat == ui.me) stringResource(R.string.tp_you) else info?.name ?: stringResource(R.string.tp_player),
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1,
                            )
                            Text(pluralStringResource(R.plurals.tp_result_hands, s.handsWon[seat], s.handsWon[seat]), color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        ChipCount(s.chips[seat], big = true)
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
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.tp_result_coins_note), color = CM.TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                GameButton(stringResource(R.string.bhabhi_home), onHome, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), icon = Icons.Rounded.Home)
                GameButton(stringResource(R.string.bhabhi_play_again), onAgain, style = ButtonStyle.GOLD, modifier = Modifier.weight(1.4f), icon = Icons.Rounded.Refresh)
            }
        }
    }
}

/** A finishing place in words (tables have at most 6 players). */
@Composable
private fun ordinal(n: Int): String = when (n) {
    1 -> stringResource(R.string.bhabhi_ordinal_1)
    2 -> stringResource(R.string.bhabhi_ordinal_2)
    3 -> stringResource(R.string.bhabhi_ordinal_3)
    4 -> stringResource(R.string.bhabhi_ordinal_4)
    5 -> stringResource(R.string.bhabhi_ordinal_5)
    6 -> stringResource(R.string.bhabhi_ordinal_6)
    else -> stringResource(R.string.bhabhi_ordinal_n, n)
}

@Composable
private fun PauseMenu(net: com.ngdstudio.mindimania.net.NetRole, onResume: () -> Unit, onRanks: () -> Unit, onHowTo: () -> Unit, onQuit: () -> Unit, onRoom: (() -> Unit)?) {
    Scrim(0.8f, onTap = onResume) { DialogColumn {
        GlassPanel(Modifier.padding(horizontal = 32.dp).fillMaxWidth(), padding = 20.dp) {
            OutlinedTitle(stringResource(R.string.bhabhi_paused), fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            GameButton(stringResource(R.string.bhabhi_resume), onResume, style = ButtonStyle.GREEN, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.tp_rankings_title), onRanks, style = ButtonStyle.PURPLE, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Info)
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.bhabhi_how_to), onHowTo, style = ButtonStyle.BLUE, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.MenuBook)
            Spacer(Modifier.height(10.dp))
            if (net == com.ngdstudio.mindimania.net.NetRole.LOCAL) {
                Text(stringResource(R.string.tp_saved_note), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            // Wi-Fi host: everyone back to the room (the table ends for all)
            if (onRoom != null) {
                GameButton(stringResource(R.string.mindi_room), onRoom, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            GameButton(
                stringResource(if (net == com.ngdstudio.mindimania.net.NetRole.LOCAL) R.string.bhabhi_quit else R.string.mindi_leave), onQuit,
                style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Home,
            )
        }
    } }
}
