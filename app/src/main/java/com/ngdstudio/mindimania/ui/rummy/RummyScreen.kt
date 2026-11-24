package com.ngdstudio.mindimania.ui.rummy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.ngdstudio.mindimania.rummy.Finish
import com.ngdstudio.mindimania.rummy.GroupKind
import com.ngdstudio.mindimania.rummy.RCard
import com.ngdstudio.mindimania.rummy.RPhase
import com.ngdstudio.mindimania.rummy.RummyEngine
import com.ngdstudio.mindimania.rummy.RummyMelds
import com.ngdstudio.mindimania.rummy.RummyState
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoundIconButton
import com.ngdstudio.mindimania.ui.components.rememberPulse
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val FeltDark = Color(0xFF0B3B2B)
private val Felt = Color(0xFF13734F)
private val TealPanel = Color(0xFF0F4A3A)

@Composable
fun RummyScreen(launch: RummyLaunch, vmKey: String, onExit: () -> Unit, onHowTo: () -> Unit, onBackToRoom: (() -> Unit)? = null) {
    val vm: RummyViewModel = viewModel(key = vmKey)
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
            showEmotes -> showEmotes = false
            ui.selected.isNotEmpty() -> vm.clearSelection()
            else -> showPause = !showPause
        }
    }
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val bottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val sideL = maxOf(WindowInsets.displayCutout.getLeft(density, dir), WindowInsets.navigationBars.getLeft(density, dir)).toFloat()
    val sideR = maxOf(WindowInsets.displayCutout.getRight(density, dir), WindowInsets.navigationBars.getRight(density, dir)).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(FeltDark)) {
        val s = ui.state
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        if (s == null || wPx <= 0f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OutlinedTitle(stringResource(R.string.tp_shuffling), fontSize = 28.sp) }
            return@BoxWithConstraints
        }
        val layout = remember(wPx, hPx, s.players, top, bottom, sideL, sideR, ui.me) {
            RummyLayout(wPx, hPx, density.density, s.players, ui.me, top, bottom, sideL, sideR).also {
                PlayingCardArt.configure((it.maxCardW * 1.1f).toInt())
            }
        }
        TableBackdrop(layout)
        for (seat in 0 until s.players) if (seat != ui.me) OpponentSeat(seat, s, ui, layout)
        val drag = remember { RummyDrag() }
        CenterPiles(s, ui, layout, vm, drag)
        FinishSlot(s, ui, layout, onDeclare = vm::declare)
        MyPlate(s, ui, layout)
        TopButtons(s, layout, onPause = { showPause = true }, onRules = onHowTo, onEmote = { showEmotes = !showEmotes })

        val ph = s.phase
        // someone declared and I lay out my cards, or my own card is in the finish slot and I arrange before declaring
        val declaring = ph is RPhase.Declaring && ph.seat == ui.me && ui.arrangeDeadline > 0
        val arranging = declaring || (ph is RPhase.Showdown && ui.me in ph.pending && ui.arrangeDeadline > 0)
        if (arranging) ArrangeOverlay(s, ui, layout, onDeclare = if (declaring) vm::declareNow else vm::arrange)
        LeftControls(ui, layout, vm, enabled = s.hands[ui.me].isNotEmpty() && s.finish[ui.me] != Finish.DROP)
        MyHand(s, ui, layout, vm, drag)
        ActionButtons(s, ui, layout, vm)
        if (!arranging) StatusLine(s, ui, layout)
        PileDragLayer(s, ui, layout, drag)
        FlyLayer(vm, layout, ui)
        if (showEmotes) {
            EmoteRow(Modifier.offset { IntOffset(0, (layout.topInset + 60 * density.density).roundToInt()) }) {
                vm.emote(it)
                showEmotes = false
            }
        }
        Banner(ui, Modifier.offset { IntOffset(0, (layout.center.y + layout.pileH * 0.7f).roundToInt()) })
        // on Wi-Fi only the host can deal early
        if (ph is RPhase.HandOver && ui.nextHandAt > 0) ResultOverlay(s, ui, onLeave = { vm.abandon(); onExit() }, onNext = if (ui.net == com.ngdstudio.mindimania.net.NetRole.GUEST) null else vm::nextHand)
        if (ui.confirmDrop) {
            Confirm(
                stringResource(R.string.rm_drop_title),
                stringResource(R.string.rm_drop_body, if (s.picks[ui.me] == 0) RummyEngine.FIRST_DROP else RummyEngine.MIDDLE_DROP),
                yes = stringResource(R.string.rm_btn_drop), no = stringResource(R.string.rm_cancel),
                onYes = { vm.confirmDrop(true) }, onNo = { vm.confirmDrop(false) },
            )
        }
        if (ui.confirmWrong != null) {
            Confirm(
                stringResource(R.string.rm_wrong_title), stringResource(R.string.rm_wrong_body),
                yes = stringResource(R.string.rm_wrong_go), no = stringResource(R.string.rm_wrong_fix),
                onYes = { vm.confirmWrong(true) }, onNo = { vm.confirmWrong(false) },
            )
        }
        ui.ended?.let { reason -> com.ngdstudio.mindimania.ui.game.EndedDialog(reason, onOk = onExit) }
        if (showPause) PauseMenu(
            net = ui.net,
            onResume = { showPause = false }, onHowTo = { showPause = false; onHowTo() }, onQuit = { showPause = false; vm.abandon(); onExit() },
            onRoom = onBackToRoom?.let { back -> { showPause = false; back() } },
        )
    }
}

private fun Modifier.at(pos: Offset, wPx: Float, hPx: Float) = this.offset { IntOffset((pos.x - wPx / 2).roundToInt(), (pos.y - hPx / 2).roundToInt()) }

// ------------------------------------------------------------------------------------------- table

/** Felt with a faint diamond pattern, and the oval table: dark padded rail, copper trim, green felt. */
@Composable
private fun TableBackdrop(L: RummyLayout) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF145A41), FeltDark), center = Offset(size.width / 2, size.height * 0.4f), radius = size.maxDimension * 0.7f))
        // faint diamond pattern
        val step = 34.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width) {
                drawCircle(Color(0x0DFFFFFF), 1.6.dp.toPx(), Offset(x, y))
                x += step
            }
            y += step / 2
            row++
        }
        val l = L.ovalLeft
        val t = L.ovalTop
        val w = L.ovalRight - L.ovalLeft
        val h = L.ovalBottom - L.ovalTop
        val r = h / 2
        // rail
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF151515))), Offset(l - 10.dp.toPx(), t - 8.dp.toPx()),
            Size(w + 20.dp.toPx(), h + 16.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(r + 8.dp.toPx()))
        // copper trim
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE0A070), Color(0xFF8A4A22))), Offset(l - 2.dp.toPx(), t),
            Size(w + 4.dp.toPx(), h), androidx.compose.ui.geometry.CornerRadius(r))
        // felt
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFF1C9466), Felt, Color(0xFF0E5A3E)), center = Offset(l + w / 2, t + h / 2), radius = w * 0.6f),
            Offset(l + 4.dp.toPx(), t + 5.dp.toPx()), Size(w - 4.dp.toPx(), h - 10.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(r - 5.dp.toPx()))
        drawRoundRect(Color(0x22FFFFFF), Offset(l + 24.dp.toPx(), t + 20.dp.toPx()), Size(w - 44.dp.toPx(), h - 40.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(r - 22.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun TopButtons(s: RummyState, L: RummyLayout, onPause: () -> Unit, onRules: () -> Unit, onEmote: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, (L.topInset + 4 * density.density).roundToInt()) }
            .padding(start = with(density) { L.side.toDp() }, end = with(density) { L.side.toDp() }),
        verticalAlignment = Alignment.Top,
    ) {
        RoundIconButton(Icons.Rounded.Pause, onPause, size = 42.dp, contentDescription = stringResource(R.string.tp_cd_pause))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stringResource(R.string.rm_title_hand, s.handNo), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            // the wild joker, named in words too
            Text(stringResource(R.string.rm_joker_is, if (s.cut.isPrinted) "🃏 → A" else rankName(s.wild)), color = Color(0xFFFFD08A), fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.EmojiEmotions, onEmote, size = 42.dp, contentDescription = stringResource(R.string.tp_cd_emote))
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Rounded.MenuBook, onRules, size = 42.dp, contentDescription = stringResource(R.string.bhabhi_how_to))
    }
}

@Composable
private fun OpponentSeat(seat: Int, s: RummyState, ui: RummyUi, L: RummyLayout) {
    val density = LocalDensity.current
    val info = ui.seats.getOrNull(seat) ?: return
    val pos = L.seatOf(seat)
    // the top row: a smaller avatar with the name to its right and the cards peeking out on its left
    val top = L.isTopRow(seat)
    val a = if (top) L.topAvatar else L.avatar
    val box = a * 2.6f
    val avDp = with(density) { a.toDp() }
    val ph = s.phase
    val active = !s.handOver && ph !is RPhase.Showdown && s.turn == seat
    val fin = s.finish.getOrElse(seat) { Finish.NONE }
    val out = fin == Finish.DROP || fin == Finish.WRONG_SHOW
    val winner = (ph as? RPhase.HandOver)?.winner == seat
    val pulse by rememberPulse(650)
    Box(Modifier.at(pos, box, box).size(with(density) { box.toDp() }), contentAlignment = Alignment.Center) {
        // their cards fanned behind the avatar
        if (!out && s.hands[seat].isNotEmpty()) {
            Box(Modifier.offset(x = if (top) -avDp * 0.42f else avDp * 0.32f, y = -avDp * 0.12f)) {
                for (i in 0 until 3) {
                    RummyCardView(null, avDp * 0.42f, false, s.wild, ui.profile.cardBack, Modifier.graphicsLayer { rotationZ = -12f + i * 12f; translationX = i * 6f })
                }
            }
        }
        if (active || winner) {
            Canvas(Modifier.size(avDp * 1.5f)) {
                drawCircle(Brush.radialGradient(listOf((if (winner) CM.Emerald else CM.Gold).copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent)), radius = size.minDimension / 2)
            }
        }
        Box(Modifier.alpha(if (out) 0.5f else 1f)) {
            AvatarBadge(AvatarRef.emoji(info.avatar), size = avDp, ringColor = when { winner -> CM.Emerald; active -> CM.GoldLight; else -> Color(0xFF6FD3A8) })
            if (s.dealer == seat) DealerChip(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-4).dp))
        }
        // name plate
        val plateAt = if (top) Modifier.align(Alignment.CenterStart).offset(x = with(density) { (box / 2 + a / 2 + 3 * density.density).toDp() }, y = avDp * 0.12f)
        else Modifier.align(Alignment.Center).offset(y = avDp * 0.66f)
        Row(
            plateAt.clip(RoundedCornerShape(8.dp)).background(Color(0xE60B2E22))
                .border(1.dp, Color(0x5539D39A), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(info.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (top) with(density) { L.topNameMax.toDp() } else 80.dp))
            if (!info.isHuman) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(DifficultyInfo.color(info.difficulty)))
            }
        }
        finishTag(fin, winner)?.let { (text, color) ->
            val at = if (top) Modifier.align(Alignment.CenterStart).offset(x = with(density) { (box / 2 + a / 2 + 3 * density.density).toDp() }, y = -avDp * 0.36f)
            else Modifier.align(Alignment.Center).offset(y = -avDp * 0.68f)
            Tag(text, color, at)
        }
        ui.emotes[seat]?.let { (text, at) -> EmoteBubble(text, at, Modifier.align(Alignment.TopEnd)) }
    }
}

@Composable
private fun finishTag(f: Finish, winner: Boolean): Pair<String, Color>? = when {
    winner -> stringResource(R.string.tp_winner) to Color(0xFFE0A100)
    f == Finish.DROP -> stringResource(R.string.rm_tag_dropped) to Color(0xFF8A2B3B)
    f == Finish.WRONG_SHOW -> stringResource(R.string.rm_tag_wrong) to Color(0xFFB0203F)
    f == Finish.WON -> stringResource(R.string.rm_tag_declared) to Color(0xFF13B36A)
    else -> null
}

@Composable
private fun Tag(text: String, color: Color, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) { Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) }
}

@Composable
private fun DealerChip(modifier: Modifier) {
    Box(
        modifier.size(18.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFE0A100))))
            .border(1.5.dp, Color(0xFF2A7A55), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("D", color = Color(0xFF2A4A00), fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

/** The open pile (top card face up) and the closed pile with the cut joker sideways under it. Tap to pick. */
@Composable
private fun CenterPiles(s: RummyState, ui: RummyUi, L: RummyLayout, vm: RummyViewModel, drag: RummyDrag) {
    val density = LocalDensity.current
    val w = with(density) { L.pileW.toDp() }
    val canPick = vm.canPick()
    val pulse by rememberPulse(700)
    val glow = if (canPick) CM.GoldLight.copy(alpha = 0.6f + 0.4f * pulse) else null
    // the cut joker, sideways, peeking out from under the closed pile
    Box(Modifier.at(Offset(L.closedPos.x + L.pileW * 0.45f, L.closedPos.y), L.pileW, L.pileH)) {
        RummyCardView(s.cut, w, true, s.wild, ui.profile.cardBack, Modifier.rotate(90f))
    }
    // closed pile: a few backs stacked
    Box(
        Modifier.at(L.closedPos, L.pileW, L.pileH).pileDrag(canPick, false, L.closedPos, drag, vm).clickable(enabled = canPick, interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.pickClosed() }
    ) {
        for (i in 2 downTo 0) RummyCardView(null, w, false, s.wild, ui.profile.cardBack, Modifier.offset(x = (i * 1.5).dp, y = (-i * 1.5).dp), glow = if (i == 0) glow else null)
    }
    // open pile
    val top = s.openTop
    Box(
        Modifier.at(L.openPos, L.pileW, L.pileH).pileDrag(canPick && top != null, true, L.openPos, drag, vm).clickable(enabled = canPick && top != null, interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.pickOpen() }
    ) {
        // while the top card is dragged away, the one under it shows
        val shown = if (drag.pile == 1) s.open.getOrNull(s.open.size - 2) else top
        if (shown != null) RummyCardView(shown, w, true, s.wild, ui.profile.cardBack, Modifier.rotate(-8f), glow = glow)
        else Box(Modifier.size(w, w * 1.4f).border(1.5.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp)))
    }
}

/** The finish slot: tap it (with one card selected, on my turn) to throw that card and declare. */
@Composable
private fun FinishSlot(s: RummyState, ui: RummyUi, L: RummyLayout, onDeclare: () -> Unit) {
    val density = LocalDensity.current
    val wPx = L.pileW * 1.25f
    val hPx = L.pileH * 1.1f
    val ready = !s.handOver && s.turn == ui.me && s.phase == RPhase.Discard && ui.selected.size == 1 && ui.idle
    val pulse by rememberPulse(600)
    Box(
        Modifier.at(L.finishPos, wPx, hPx).size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .clip(RoundedCornerShape(8.dp))
            .background(if (ready) CM.Gold.copy(alpha = 0.15f + 0.15f * pulse) else Color(0x22000000))
            .clickable(enabled = ready) { onDeclare() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(if (ready) CM.GoldLight else Color(0xAAFFFFFF), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        }
        // the four lines shrink with the slot on short screens
        val f = (hPx / density.density / 92f).coerceIn(0.6f, 1f)
        val dc = s.phase as? RPhase.Declaring
        // the card being declared with: face up for its owner, face down for everyone else
        if (dc != null) RummyCardView(dc.card.takeIf { dc.seat == ui.me }, with(density) { L.pileW.toDp() }, dc.seat == ui.me, s.wild, ui.profile.cardBack, Modifier.rotate(-6f))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.rm_finish_1), color = Color.White, fontSize = (15 * f).sp, lineHeight = (17 * f).sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.rm_finish_2), color = Color.White, fontSize = (10 * f).sp, lineHeight = (12 * f).sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.rm_finish_3), color = Color.White, fontSize = (15 * f).sp, lineHeight = (17 * f).sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.rm_finish_4), color = Color.White, fontSize = (10 * f).sp, lineHeight = (12 * f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Me: avatar on the left of a plate with my name, my live points and my chips (like a seat at the table's edge). */
@Composable
private fun MyPlate(s: RummyState, ui: RummyUi, L: RummyLayout) {
    val density = LocalDensity.current
    val me = ui.me
    val info = ui.seats.getOrNull(me) ?: return
    val pts = livePoints(s, ui)
    val active = ui.idle && !s.handOver && s.phase !is RPhase.Showdown && s.turn == me
    val winner = (s.phase as? RPhase.HandOver)?.winner == me
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.turnDeadline, active) {
        while (active && ui.turnDeadline > 0) { now = System.currentTimeMillis(); delay(100) }
    }
    val wDp = with(density) { L.plateW.toDp() }
    val hDp = with(density) { L.plateH.toDp() }
    Box(Modifier.at(L.plateCenter, L.plateW, L.plateH).size(wDp, hDp)) {
        Row(
            Modifier.fillMaxSize().padding(start = hDp * 0.6f).clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A6B54), TealPanel))).border(1.5.dp, Color(0xFF39D39A).copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
        ) {
            Spacer(Modifier.width(hDp * 0.55f))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(info.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rm_points, pts), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Box(Modifier.width(1.dp).height(16.dp).background(Color(0x66FFFFFF)))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFFFC93C)).border(1.5.dp, Color(0xFFB36B00), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text("${s.chips[me]}", color = CM.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.CenterStart)) {
            AvatarBadge(AvatarRef.emoji(info.avatar), size = hDp * 1.05f, ringColor = when { winner -> CM.Emerald; active -> CM.GoldLight; else -> Color(0xFF6FD3A8) })
            if (s.dealer == me) DealerChip(Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp))
            if (active && ui.turnDeadline > 0 && ui.turnTotal > 0) {
                val frac = ((ui.turnDeadline - now).toFloat() / ui.turnTotal).coerceIn(0f, 1f)
                Canvas(Modifier.size(hDp * 1.05f)) {
                    drawArc(if (frac < 0.3f) CM.Ruby else CM.Emerald, -90f, 360f * frac, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        finishTag(s.finish.getOrElse(me) { Finish.NONE }, winner)?.let { (text, color) -> Tag(text, color, Modifier.align(Alignment.TopCenter).offset(y = (-10).dp)) }
        ui.emotes[me]?.let { (text, at) -> EmoteBubble(text, at, Modifier.align(Alignment.TopStart).offset(x = hDp * 0.6f, y = -hDp * 0.6f)) }
    }
}

private fun myGroups(s: RummyState, ui: RummyUi): List<List<RCard>> = RummyEngine.groupsOf(s.hands[ui.me], ui.groups)

private fun livePoints(s: RummyState, ui: RummyUi): Int {
    val g = myGroups(s, ui)
    if (g.isEmpty()) return 0
    return RummyMelds.evaluate(g, s.wild).points
}

// ------------------------------------------------------------------------------------------- my hand

/** What is being dragged right now: cards from my hand ([ids], the grabbed one first) or a pile's card. Positions in px. */
@Stable
private class RummyDrag {
    var ids by mutableStateOf<List<Int>>(emptyList())
    /** 0 = the closed pile, 1 = the open pile, -1 = none. */
    var pile by mutableIntStateOf(-1)
    /** Centre of the grabbed card. */
    var pos by mutableStateOf(Offset.Zero)
    /** A card I threw by dropping it on the open pile waits there (instead of sliding back) until it leaves my hand. */
    var parked by mutableIntStateOf(-1)
    /** A card picked by dragging slides into my hand from here. */
    var landFrom: Offset? = null
    /** Where a drop at a point would land (set by my hand, which knows the layout). */
    var spotFor: (Offset) -> DropSpot? = { null }
    val active: Boolean get() = ids.isNotEmpty() || pile >= 0
}

private sealed interface DropSpot {
    /** Into my hand; [markX] is where the gold marker goes. */
    data class Hand(val drop: HandDrop, val markX: Float, val shiftIds: Set<Int>, val newGroup: Boolean) : DropSpot
    data object Discard : DropSpot
    data object Finish : DropSpot
}

/** The spot in my hand nearest to [x] (the dragged card's centre), with [moving] taken out of the hand. */
private fun handSlot(groups: List<List<Int>>, geo: RummyLayout.HandGeo, moving: Set<Int>, x: Float, centerX: Float): DropSpot.Hand {
    val w = geo.cardW
    val half = w / 2
    data class Slot(val x: Float, val spot: DropSpot.Hand)
    val slots = ArrayList<Slot>()
    val rest = groups.map { g -> g.filter { it !in moving } }.filter { it.isNotEmpty() }
    var prevRight: Float? = null
    rest.forEachIndexed { gi, g ->
        val cx = g.map { geo.x[it] ?: 0f }
        val left = cx.first() - half
        val nx = prevRight?.let { (it + left) / 2 } ?: (left - 0.15f * w)
        slots += Slot(nx, DropSpot.Hand(HandDrop(gi, 0, true), nx, emptySet(), true))
        cx.forEachIndexed { k, c ->
            slots += Slot(c - 0.3f * w, DropSpot.Hand(HandDrop(gi, k, false), c - 0.2f * w, g.drop(k).toSet(), false))
        }
        slots += Slot(cx.last() + 0.3f * w, DropSpot.Hand(HandDrop(gi, g.size, false), cx.last() + half + 2f, emptySet(), false))
        prevRight = cx.last() + half
    }
    val end = prevRight?.let { it + 0.15f * w } ?: centerX
    slots += Slot(end, DropSpot.Hand(HandDrop(rest.size, 0, true), end, emptySet(), true))
    return slots.minBy { abs(it.x - x) }.spot
}

/** My cards in their groups along the bottom, a coloured bar under each group. Tap a card to select it; drag cards to move them. */
@Composable
private fun MyHand(s: RummyState, ui: RummyUi, L: RummyLayout, vm: RummyViewModel, drag: RummyDrag) {
    val density = LocalDensity.current
    val hand = s.hands[ui.me]
    if (hand.isEmpty()) return
    val groups = ui.groups.map { g -> g.filter { id -> hand.any { it.id == id } } }.filter { it.isNotEmpty() }
    val geo = L.handGeo(groups)
    val wDp = with(density) { geo.cardW.toDp() }
    val cardH = geo.cardW * 1.4f
    val byId = hand.associateBy { it.id }
    val dim = s.finish[ui.me] == Finish.DROP || s.finish[ui.me] == Finish.WRONG_SHOW
    val labels = groupLabels(groups.map { g -> g.mapNotNull { byId[it] } }, s.wild)
    val order = groups.flatten()
    val canThrow = vm.canThrow()
    val scope = rememberCoroutineScope()
    val anims = remember { HashMap<Int, Animatable<Offset, AnimationVector2D>>() }

    // where a drop would land; the piles and my hand all ask this
    val spotFn: (Offset) -> DropSpot? = { p ->
        val single = drag.ids.size == 1
        when {
            single && canThrow && abs(p.x - L.openPos.x) < L.pileW * 0.8f && abs(p.y - L.openPos.y) < L.pileH * 0.8f -> DropSpot.Discard
            single && canThrow && abs(p.x - L.finishPos.x) < L.pileW * 0.8f && abs(p.y - L.finishPos.y) < L.pileH * 0.8f -> DropSpot.Finish
            p.y < geo.top - cardH * 0.35f -> null
            else -> handSlot(groups, geo, drag.ids.toSet(), p.x, L.width / 2)
        }
    }
    val latestSpot = rememberUpdatedState(spotFn)
    SideEffect { drag.spotFor = { latestSpot.value(it) } }
    val spot by remember(drag) { derivedStateOf { if (drag.active) latestSpot.value(drag.pos) else null } }
    LaunchedEffect(spot) { if (spot != null) vm.dragTick() }
    LaunchedEffect(drag.parked) {
        if (drag.parked >= 0) { delay(1500); drag.parked = -1 }
    }
    val spread = geo.cardW * 0.35f

    val latest = rememberUpdatedState(Triple(order, ui.selected, canThrow))
    fun finishDrag(commit: Boolean) {
        val ids = drag.ids
        if (ids.isEmpty()) return
        val p = drag.pos
        val sp = if (commit) latestSpot.value(p) else null
        scope.launch {
            ids.forEachIndexed { i, cid -> anims[cid]?.snapTo(p + Offset(i * spread, 0f)) }
            when (sp) {
                is DropSpot.Discard -> {
                    drag.parked = ids[0]
                    drag.ids = emptyList()
                    if (!vm.dragDiscard(ids[0])) drag.parked = -1
                    else anims[ids[0]]?.animateTo(L.openPos, tween(140, easing = FastOutSlowInEasing))
                }
                is DropSpot.Finish -> {
                    drag.ids = emptyList()
                    vm.dragDeclare(ids[0])
                }
                is DropSpot.Hand -> {
                    drag.ids = emptyList()
                    vm.placeCards(ids, sp.drop)
                }
                null -> drag.ids = emptyList()
            }
        }
    }

    // the gesture outlives this composition: it calls the latest version
    val onFinish = rememberUpdatedState<(Boolean) -> Unit> { commit -> finishDrag(commit) }

    // the bars
    groups.forEachIndexed { i, _ ->
        val (x0, x1) = geo.spans[i]
        val (text, color) = labels[i]
        val canMove = ui.selected.isNotEmpty()
        Box(
            Modifier.offset { IntOffset(x0.roundToInt(), (L.handBottom - L.labelH).roundToInt()) }
                .size(with(density) { (x1 - x0).toDp() }, with(density) { L.labelH.toDp() })
                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(color)
                .clickable(enabled = canMove) { vm.moveTo(i) },
            contentAlignment = Alignment.Center
        ) {
            Text(if (canMove) "⇣ $text" else text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }

    // the cards: they glide to their place when the groups change, and make room while a card is dragged over them
    val hs = spot as? DropSpot.Hand
    for (id in order) {
        val card = byId[id] ?: continue
        val baseX = geo.x[id] ?: continue
        key(id) {
            val sel = id in ui.selected
            val dragIndex = drag.ids.indexOf(id)
            val dragging = dragIndex >= 0
            val parked = drag.parked == id
            val shift = when {
                hs == null || dragging -> 0f
                hs.newGroup -> if (baseX > hs.markX) geo.cardW * 0.15f else -geo.cardW * 0.15f
                id in hs.shiftIds -> geo.cardW * 0.3f
                else -> 0f
            }
            val target = Offset(baseX + shift, geo.top + cardH / 2 - (if (sel && !drag.active) 16f * density.density else 0f))
            val anim = remember {
                val from = drag.landFrom?.takeIf { id == ui.newCard }?.also { drag.landFrom = null }
                Animatable(from ?: target, Offset.VectorConverter).also { anims[id] = it }
            }
            DisposableEffect(Unit) { onDispose { anims.remove(id) } }
            LaunchedEffect(target, dragging, parked) {
                if (!dragging && !parked) anim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 520f))
            }
            Box(
                Modifier.zIndex(if (dragging) 6f + dragIndex else 0f)
                    .offset {
                        val p = if (dragging) drag.pos + Offset(dragIndex * spread, 0f) else anim.value
                        IntOffset((p.x - geo.cardW / 2).roundToInt(), (p.y - cardH / 2).roundToInt())
                    }
                    .graphicsLayer {
                        if (dragging) {
                            scaleX = 1.08f; scaleY = 1.08f
                            rotationZ = -3f
                            shadowElevation = 14f * density.density
                        } else if (dim) alpha = 0.6f
                    }
                    .pointerInput(id) {
                        detectDragGestures(
                            onDragStart = {
                                val (ord, selNow, _) = latest.value
                                drag.landFrom = null
                                drag.parked = -1
                                drag.pos = anims[id]?.value ?: Offset(baseX, geo.top + cardH / 2)
                                drag.ids = if (id in selNow && selNow.size > 1) listOf(id) + ord.filter { it in selNow && it != id } else listOf(id)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.pos += amount
                            },
                            onDragEnd = { onFinish.value(true) },
                            onDragCancel = { onFinish.value(false) },
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.tapCard(id) }
            ) {
                RummyCardView(card, wDp, true, s.wild, ui.profile.cardBack, glow = when { dragging || sel -> CM.GoldLight; id == ui.newCard -> Color(0xFF8CFFB4); else -> null }, index = true)
            }
        }
    }

    // where it will land
    when (val sp = spot) {
        is DropSpot.Hand -> {
            val gold = CM.GoldLight
            Box(
                Modifier.zIndex(5f).offset { IntOffset((sp.markX - 2.5f * density.density).roundToInt(), (geo.top - 8f * density.density).roundToInt()) }
                    .size(5.dp, with(density) { (cardH + 16f * density.density).toDp() })
                    .clip(RoundedCornerShape(3.dp)).background(gold)
            )
            if (sp.newGroup) {
                Box(
                    Modifier.zIndex(5f).offset { IntOffset((sp.markX - 13f * density.density).roundToInt(), (geo.top - 34f * density.density).roundToInt()) }
                        .size(26.dp).clip(CircleShape).background(gold).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Color(0xFF4A2A00), fontSize = 18.sp, fontWeight = FontWeight.Black) }
            }
        }
        DropSpot.Discard -> DropGlow(L.openPos, L.pileW * 1.4f, L.pileH * 1.3f)
        DropSpot.Finish -> DropGlow(L.finishPos, L.pileW * 1.45f, L.pileH * 1.25f)
        null -> Unit
    }
}

@Composable
private fun DropGlow(center: Offset, wPx: Float, hPx: Float) {
    val density = LocalDensity.current
    Box(
        Modifier.zIndex(5f).at(center, wPx, hPx).size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .clip(RoundedCornerShape(10.dp)).background(CM.GoldLight.copy(alpha = 0.22f)).border(2.5.dp, CM.GoldLight, RoundedCornerShape(10.dp))
    )
}

/** The pile card being dragged towards my hand. */
@Composable
private fun PileDragLayer(s: RummyState, ui: RummyUi, L: RummyLayout, drag: RummyDrag) {
    if (drag.pile < 0) return
    val density = LocalDensity.current
    val w = with(density) { L.pileW.toDp() }
    val open = drag.pile == 1
    Box(
        Modifier.zIndex(7f)
            .offset { IntOffset((drag.pos.x - L.pileW / 2).roundToInt(), (drag.pos.y - L.pileH / 2).roundToInt()) }
            .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; rotationZ = -3f; shadowElevation = 14f * density.density }
    ) {
        RummyCardView(if (open) s.openTop else null, w, open, s.wild, ui.profile.cardBack, glow = CM.GoldLight)
    }
}

/** Drag a pile's card: drop it on my hand to pick it straight into that spot. */
private fun Modifier.pileDrag(enabled: Boolean, open: Boolean, start: Offset, drag: RummyDrag, vm: RummyViewModel): Modifier =
    if (!enabled) this else pointerInput(open) {
        detectDragGestures(
            onDragStart = {
                drag.landFrom = null
                drag.pos = start
                drag.pile = if (open) 1 else 0
            },
            onDrag = { change, amount ->
                change.consume()
                drag.pos += amount
            },
            onDragEnd = {
                val sp = drag.spotFor(drag.pos) as? DropSpot.Hand
                if (sp != null) {
                    drag.landFrom = drag.pos
                    if (!vm.dragPick(open, sp.drop)) drag.landFrom = null
                }
                drag.pile = -1
            },
            onDragCancel = { drag.pile = -1 },
        )
    }

@Composable
private fun groupLabels(groups: List<List<RCard>>, wild: Int): List<Pair<String, Color>> {
    val green = Color(0xE613A35E)
    val blue = Color(0xE61E6FC0)
    val red = Color(0xE6B3263C)
    val kinds = groups.map { RummyMelds.classify(it, wild) }
    val hasPure = GroupKind.PURE in kinds
    var firstDone = false
    var secondDone = false
    val first = stringResource(R.string.rm_lbl_first)
    val second = stringResource(R.string.rm_lbl_second)
    val pure = stringResource(R.string.rm_lbl_pure)
    val impure = stringResource(R.string.rm_lbl_impure)
    val set = stringResource(R.string.rm_lbl_set)
    val joker = stringResource(R.string.rm_lbl_joker)
    val invalid = stringResource(R.string.rm_lbl_invalid)
    // the first pure sequence is the 1st life; the next sequence (pure or not) the 2nd life
    val firstPure = kinds.indexOf(GroupKind.PURE)
    return kinds.mapIndexed { i, k ->
        when (k) {
            GroupKind.PURE -> if (i == firstPure) { firstDone = true; first to green } else if (!secondDone) { secondDone = true; second to green } else pure to green
            GroupKind.IMPURE -> if (hasPure && !secondDone) { secondDone = true; second to green } else impure to (if (hasPure) green else red)
            GroupKind.SET -> set to blue
            GroupKind.JOKERS -> joker to green
            GroupKind.INVALID -> invalid to red
        }
    }.also { if (!firstDone) Unit }
}

/** Grouping ON/OFF and Sort, on the left above my cards. */
@Composable
private fun LeftControls(ui: RummyUi, L: RummyLayout, vm: RummyViewModel, enabled: Boolean) {
    if (!enabled) return
    val density = LocalDensity.current
    Row(
        // bottom-anchored: however tall the row is, it ends just above my cards
        Modifier.layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
            layout(p.width, p.height) { p.place(L.side.roundToInt(), (L.controlsBottom - p.height).roundToInt()) }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E8A5E), Color(0xFF136B47))))
                .border(1.dp, Color(0x6639D39A), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.rm_grouping), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF0B3B2B))) {
                for (on in listOf(true, false)) {
                    val sel = ui.grouping == on
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) (if (on) Color(0xFF2ECC71) else Color(0xFF8A2B3B)) else Color.Transparent)
                            .clickable { vm.setGrouping(on) }.padding(horizontal = 9.dp, vertical = 2.dp)
                    ) { Text(stringResource(if (on) R.string.rm_on else R.string.rm_off), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E8A5E), Color(0xFF136B47))))
                .border(1.dp, Color(0x6639D39A), RoundedCornerShape(8.dp)).clickable { vm.sort() }.padding(horizontal = 18.dp, vertical = 8.dp)
        ) { Text(stringResource(R.string.rm_sort), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black) }
    }
}

/** DROP before picking; GROUP with 2+ cards selected; DISCARD / DECLARE with one card selected after picking. */
@Composable
private fun ActionButtons(s: RummyState, ui: RummyUi, L: RummyLayout, vm: RummyViewModel) {
    val density = LocalDensity.current
    val y = L.handTop(L.maxCardW) - 52 * density.density
    val canPick = vm.canPick()
    val canThrow = vm.canThrow()
    val one = ui.selected.size == 1
    val many = ui.selected.size >= 2
    val showdown = s.phase is RPhase.Showdown
    if (!(canPick || (canThrow && one) || many)) return
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) }.padding(end = with(density) { L.side.toDp() }),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        if (many) GameButton(stringResource(R.string.rm_btn_group), vm::group, style = ButtonStyle.BLUE, height = 40.dp, fontSize = 14.sp, modifier = Modifier.width(104.dp))
        if (canPick && !showdown) GameButton(stringResource(R.string.rm_btn_drop), vm::askDrop, style = ButtonStyle.RED, height = 40.dp, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        if (canThrow && one) {
            GameButton(stringResource(R.string.rm_btn_discard), vm::discard, style = ButtonStyle.GOLD, height = 40.dp, fontSize = 14.sp, modifier = Modifier.width(112.dp))
            GameButton(stringResource(R.string.rm_btn_declare), vm::declare, style = ButtonStyle.GREEN, height = 40.dp, fontSize = 14.sp, modifier = Modifier.width(112.dp))
        }
    }
}

@Composable
private fun StatusLine(s: RummyState, ui: RummyUi, L: RummyLayout) {
    val me = ui.me
    val ph = s.phase
    val player = stringResource(R.string.tp_player)
    val text = when {
        ph is RPhase.HandOver -> ""
        ph is RPhase.Showdown -> stringResource(R.string.rm_status_wait_show, ui.seats.getOrNull(ph.declarer)?.name ?: player)
        ph is RPhase.Declaring -> if (ph.seat == me) "" else stringResource(R.string.rm_status_declaring, ui.seats.getOrNull(ph.seat)?.name ?: player)
        !ui.idle -> ""
        s.finish[me] == Finish.DROP || s.finish[me] == Finish.WRONG_SHOW -> stringResource(R.string.rm_status_out)
        s.turn == me && s.phase == RPhase.Pick -> stringResource(R.string.rm_status_pick)
        s.turn == me -> stringResource(R.string.rm_status_discard)
        else -> stringResource(R.string.rm_status_thinking, ui.seats.getOrNull(s.turn)?.name ?: player)
    }
    val gold = ui.idle && s.turn == me && ph !is RPhase.Showdown && !s.handOver
    // between the piles and my plate, clear of both
    val y = minOf(L.center.y + L.pileH * 0.58f + 4 * L.density, L.plateCenter.y - L.plateH / 2 - 25 * L.density)
    Box(Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) }, contentAlignment = Alignment.Center) {
        AnimatedVisibility(text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Text(
                text, color = if (gold) Color(0xFF3A1F00) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (gold) Brush.horizontalGradient(listOf(Color(0xCCB36B00), Color(0xEEFFB300), Color(0xCCB36B00))) else Brush.horizontalGradient(listOf(Color(0x99000000), Color(0x99000000))))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------- flying cards

@Composable
private fun FlyLayer(vm: RummyViewModel, L: RummyLayout, ui: RummyUi) {
    val density = LocalDensity.current
    val flies = remember { mutableStateListOf<RFly>() }
    LaunchedEffect(vm) { vm.flies.collect { flies += it } }
    val wDp = with(density) { L.pileW.toDp() }
    for (f in flies.toList()) {
        androidx.compose.runtime.key(f.id) {
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                p.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                flies.remove(f)
            }
            val a = point(f.from, L)
            val b = point(f.to, L)
            val t = p.value
            val x = a.x + (b.x - a.x) * t
            val y = a.y + (b.y - a.y) * t - kotlin.math.sin(t * Math.PI).toFloat() * 30 * density.density
            Box(Modifier.at(Offset(x, y), L.pileW, L.pileH).scale(if (f.to is FlyPt.Seat || f.from is FlyPt.Seat) 1f - 0.3f * (if (f.to is FlyPt.Seat) t else 1 - t) else 1f)) {
                RummyCardView(f.card, wDp, f.faceUp, ui.state?.wild ?: 0, ui.profile.cardBack)
            }
        }
    }
}

private fun point(p: FlyPt, L: RummyLayout): Offset = when (p) {
    FlyPt.Closed -> L.closedPos
    FlyPt.Open -> L.openPos
    FlyPt.Finish -> L.finishPos
    FlyPt.Hand -> Offset(L.width / 2, L.handTop(L.maxCardW) + L.maxCardW * 0.7f)
    is FlyPt.Seat -> L.seatOf(p.seat)
}

// ------------------------------------------------------------------------------------------- overlays

/** Someone declared: arrange my cards (the hand stays live below) and confirm before the time runs out. */
@Composable
private fun ArrangeOverlay(s: RummyState, ui: RummyUi, L: RummyLayout, onDeclare: () -> Unit) {
    val density = LocalDensity.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.arrangeDeadline) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val left = ((ui.arrangeDeadline - now) / 1000).coerceAtLeast(0)
    Box(
        // covers the table and my plate; my cards (and Grouping / Sort) stay usable below
        Modifier.fillMaxWidth().height(with(density) { (L.handTop(L.maxCardW) - 2 * density.density).toDp() })
            .background(Brush.verticalGradient(listOf(Color(0xF00B3B2B), Color(0xF0124A37))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = with(density) { (L.topInset + 10 * density.density).toDp() }).fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) { Text(stringResource(R.string.rm_arrange_title), color = Color(0xFF8CE0A8), fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) }
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth(0.72f),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            InfoBox(stringResource(R.string.rm_points_loss), "${livePoints(s, ui)}", Color(0xFF1E6FE8), Color(0xFF0B3C8F))
            GameButton(stringResource(R.string.rm_click_declare), onDeclare, style = ButtonStyle.GOLD, height = 48.dp, fontSize = 16.sp, modifier = Modifier.width(230.dp))
            InfoBox(stringResource(R.string.rm_time_left), "0:%02d".format(left), Color(0xFFE53935), Color(0xFF8A0B1F))
        }
    }
}

@Composable
private fun InfoBox(label: String, value: String, color: Color, inner: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(inner).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** After a hand: every player's cards as they were grouped, with points and chips, then the next deal. */
@Composable
private fun ResultOverlay(s: RummyState, ui: RummyUi, onLeave: () -> Unit, onNext: (() -> Unit)?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.nextHandAt) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val secs = ((ui.nextHandAt - now + 999) / 1000).coerceAtLeast(0)
    val winner = (s.phase as? RPhase.HandOver)?.winner ?: -1
    val order = (listOf(winner) + (0 until s.players).filter { it != winner && s.inHand[it] }.sortedBy { s.points[it] }).filter { it >= 0 }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF1EBA0)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF3FB84A), Color(0xFF1F8A2E)))).statusBarsPadding().height(46.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(38.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF5A5A), Color(0xFFB0102A)))).border(2.dp, Color.White, CircleShape).clickable { onLeave() },
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.Icon(Icons.Rounded.Home, stringResource(R.string.rm_r_leave), tint = Color.White, modifier = Modifier.size(22.dp)) }
            Text(stringResource(R.string.rm_result), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            if (onNext != null) Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2F7BE8), Color(0xFF1450B8)))).border(1.5.dp, Color.White, RoundedCornerShape(6.dp))
                    .clickable { onNext() }.padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text(stringResource(R.string.rm_r_next_now), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            order.forEach { seat -> ResultRow(s, ui, seat, seat == winner) }
        }
        Box(Modifier.fillMaxWidth().background(Color(0xFF2E9E3A)).navigationBarsPadding().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.rm_r_next, secs), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultRow(s: RummyState, ui: RummyUi, seat: Int, winner: Boolean) {
    val info = ui.seats.getOrNull(seat)
    val delta = s.delta.getOrElse(seat) { 0 }
    val fin = s.finish[seat]
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rm_r_name, if (seat == ui.me) stringResource(R.string.tp_you) else info?.name ?: ""), color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (winner) {
                Spacer(Modifier.width(8.dp))
                Tag(stringResource(R.string.tp_winner), Color(0xFFE0A100), Modifier)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(stringResource(R.string.rm_r_points, s.points[seat]), color = Color(0xFF1A1A1A), fontSize = 14.sp)
            Text(
                if (delta >= 0) stringResource(R.string.rm_r_win, "%,d".format(delta)) else stringResource(R.string.rm_r_lose, "%,d".format(-delta)),
                color = if (delta >= 0) Color(0xFF1F7A2E) else Color(0xFFB0203F), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.rm_r_left, "%,d".format(s.chips[seat])), color = Color(0xFF1A1A1A), fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            if (info != null) AvatarBadge(AvatarRef.emoji(info.avatar), size = 54.dp)
            Spacer(Modifier.width(10.dp))
            val hand = s.hands[seat]
            val groups = s.shown[seat].takeIf { it.isNotEmpty() }?.let { RummyEngine.groupsOf(hand, it) }
                ?: listOf(hand)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (fin) {
                    Finish.DROP -> ResultGroup(hand, s.wild, ui, stringResource(R.string.rm_r_dropped), false)
                    else -> {
                        val kinds = groups.map { RummyMelds.classify(it, s.wild) }
                        val ev = RummyMelds.evaluate(groups, s.wild)
                        groups.forEachIndexed { i, g ->
                            val k = kinds[i]
                            val counts = k == GroupKind.INVALID || (ev.pure == 0 && k != GroupKind.JOKERS) || fin == Finish.WRONG_SHOW
                            val text = when {
                                fin == Finish.WRONG_SHOW && i == 0 -> stringResource(R.string.rm_r_wrong)
                                k == GroupKind.PURE -> stringResource(R.string.rm_k_pure)
                                k == GroupKind.IMPURE -> stringResource(R.string.rm_k_impure)
                                k == GroupKind.SET -> stringResource(R.string.rm_k_set)
                                k == GroupKind.JOKERS -> stringResource(R.string.rm_k_joker)
                                else -> stringResource(R.string.rm_k_invalid, g.sumOf { RummyMelds.value(it, s.wild) })
                            }
                            ResultGroup(g, s.wild, ui, text, ok = !counts)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF2E9E3A)))
    }
}

@Composable
private fun ResultGroup(cards: List<RCard>, wild: Int, ui: RummyUi, label: String, ok: Boolean) {
    val w = 40.dp
    Column(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (ok) Color(0xFF2E8B3E) else Color(0xFFD32F2F)).padding(6.dp)
    ) {
        Box(Modifier.width(w + w * 0.55f * (cards.size - 1).coerceAtLeast(0)).height(w * 1.4f)) {
            cards.forEachIndexed { i, c -> RummyCardView(c, w, true, wild, ui.profile.cardBack, Modifier.offset(x = w * 0.55f * i), index = true) }
        }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Confirm(title: String, body: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNo() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(Modifier.widthIn(max = 440.dp).padding(16.dp), padding = 18.dp) {
            OutlinedTitle(title, fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(no, onNo, style = ButtonStyle.BLUE, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14.sp)
                GameButton(yes, onYes, style = ButtonStyle.RED, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PauseMenu(net: com.ngdstudio.mindimania.net.NetRole, onResume: () -> Unit, onHowTo: () -> Unit, onQuit: () -> Unit, onRoom: (() -> Unit)?) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onResume() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(Modifier.widthIn(max = 420.dp).padding(16.dp).verticalScroll(rememberScrollState()), padding = 18.dp) {
            OutlinedTitle(stringResource(R.string.bhabhi_paused), fontSize = 30.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            GameButton(stringResource(R.string.bhabhi_resume), onResume, style = ButtonStyle.GREEN, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, height = 48.dp)
            Spacer(Modifier.height(8.dp))
            GameButton(stringResource(R.string.bhabhi_how_to), onHowTo, style = ButtonStyle.BLUE, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.MenuBook, height = 48.dp)
            Spacer(Modifier.height(8.dp))
            if (net == com.ngdstudio.mindimania.net.NetRole.LOCAL) {
                Text(stringResource(R.string.rm_saved_note), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            // Wi-Fi host: everyone back to the room (the table ends for all)
            if (onRoom != null) {
                GameButton(stringResource(R.string.mindi_room), onRoom, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                Spacer(Modifier.height(8.dp))
            }
            GameButton(
                stringResource(if (net == com.ngdstudio.mindimania.net.NetRole.LOCAL) R.string.bhabhi_quit else R.string.mindi_leave), onQuit,
                style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Home, height = 48.dp,
            )
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

@Composable
private fun EmoteRow(modifier: Modifier, onPick: (String) -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Row(
            Modifier.padding(end = 60.dp).clip(RoundedCornerShape(50)).background(Color(0xEE0B2E22)).border(1.5.dp, CM.Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("👍", "😂", "😎", "😮", "😡", "🎉", "🙏").forEach { e ->
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun Banner(ui: RummyUi, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.bannerAt) {
        if (ui.banner != null && System.currentTimeMillis() - ui.bannerAt < 1500) {
            text = ui.banner
            delay(1700)
            text = null
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(text != null, enter = scaleIn(spring(0.5f, 400f), initialScale = 0.6f) + fadeIn(), exit = fadeOut() + scaleOut()) {
            Box(
                Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE60B2E22)).border(1.5.dp, CM.Gold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(text ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }
        }
    }
}
