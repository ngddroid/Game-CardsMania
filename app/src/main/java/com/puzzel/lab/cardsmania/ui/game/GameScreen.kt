package com.puzzel.lab.cardsmania.ui.game

import com.puzzel.lab.cardsmania.ui.design.GameIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.puzzel.lab.cardsmania.GameLaunch
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.data.AvatarRef
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.engine.model.DeckFactory
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.PlayerState
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoundIconButton
import com.puzzel.lab.cardsmania.ui.components.rememberPulse
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun GameScreen(onExit: () -> Unit, onHowTo: (GameMode?) -> Unit, onBackToRoom: (host: Boolean) -> Unit = {}) {
    val app = LocalApp.current
    val mp = app.multiplayer
    var gameKey by rememberSaveable { mutableIntStateOf(0) }
    val launch = remember(gameKey) {
        app.pendingLaunch.also { app.pendingLaunch = null }
            // Safety net: never start a local game while this phone is seated in a multiplayer game.
            ?: if (mp.client?.status?.value == com.puzzel.lab.cardsmania.net.ClientStatus.InGame) {
                GameLaunch(null, net = com.puzzel.lab.cardsmania.net.NetRole.GUEST)
            } else null
    }
    val role = launch?.net ?: com.puzzel.lab.cardsmania.net.NetRole.LOCAL
    val vm: GameViewModel = viewModel(key = "game_$gameKey")
    LaunchedEffect(vm) { vm.start(launch) }

    // Guests follow the host: a new Start = rematch, lobby again = back to the room.
    if (role == com.puzzel.lab.cardsmania.net.NetRole.GUEST) {
        val client = mp.client
        if (client != null) {
            val firstStart = remember(gameKey) { client.starts.replayCache.lastOrNull() }
            LaunchedEffect(gameKey) {
                client.starts.collect { s ->
                    if (s === firstStart) return@collect
                    if (s is com.puzzel.lab.cardsmania.net.NetMsg.MindiStart || s is com.puzzel.lab.cardsmania.net.NetMsg.MindiSync) {
                        // the host switched the room to Mindi: go back to the lobby, which opens the Mindi table
                        onBackToRoom(false)
                    } else {
                        app.pendingLaunch = GameLaunch(null, net = com.puzzel.lab.cardsmania.net.NetRole.GUEST)
                        gameKey++
                    }
                }
            }
            LaunchedEffect(Unit) {
                client.status.collect { st -> if (st == com.puzzel.lab.cardsmania.net.ClientStatus.InLobby) onBackToRoom(false) }
            }
        }
    }

    // Everything on the table (sprites, overlays, dialogs) belongs to one game: a new game key = fresh table.
    key(gameKey) { GameContent(
        vm = vm,
        onExit = {
            vm.abandonIfOver()
            when (role) {
                com.puzzel.lab.cardsmania.net.NetRole.HOST -> mp.closeRoom("The host left the game.")
                com.puzzel.lab.cardsmania.net.NetRole.GUEST -> mp.leaveRoom()
                else -> Unit
            }
            onExit()
        },
        onHowTo = onHowTo,
        onPlayAgain = { config ->
            if (role == com.puzzel.lab.cardsmania.net.NetRole.HOST) {
                val p = vm.ui.value.profile
                val cfg = mp.startHostedGame(p)
                if (cfg != null) {
                    app.pendingLaunch = GameLaunch(cfg, net = com.puzzel.lab.cardsmania.net.NetRole.HOST)
                    gameKey++
                }
            } else {
                app.pendingLaunch = GameLaunch(config.copy(seed = System.nanoTime()))
                gameKey++
            }
        },
        onBackToRoom = {
            mp.host?.backToLobby()
            onBackToRoom(true)
        },
    ) }
}

@Composable
private fun GameContent(
    vm: GameViewModel,
    onExit: () -> Unit,
    onHowTo: (GameMode?) -> Unit,
    onPlayAgain: (com.puzzel.lab.cardsmania.engine.model.GameConfig) -> Unit,
    onBackToRoom: () -> Unit,
) {
    val frame by vm.frame.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPause by remember { mutableStateOf(false) }
    var showEmotes by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) vm.onBackground()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    BackHandler {
        when {
            ui.result is ResultInfo.Match -> onExit()
            showEmotes -> showEmotes = false
            showPause -> {
                showPause = false
                vm.setPaused(false)
            }
            else -> {
                showPause = true
                vm.setPaused(true)
            }
        }
    }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0A0320))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val state = frame?.state
        val players = state?.players?.size ?: 0
        if (state == null || players == 0 || wPx <= 0f) {
            LoadingTable()
            return@BoxWithConstraints
        }
        val layout = remember(wPx, hPx, players, ui.settings.largeCards, topInset, bottomInset, vm.human) {
            TableLayout(wPx, hPx, density.density, players, topInset, bottomInset, ui.settings.largeCards, vm.human).also {
                CardArt.configure(maxOf(wPx * 0.22f, it.handCardW).toInt())
            }
        }
        val world = remember(layout, vm) { SpriteWorld(layout, vm.human) }
        val profile = ui.profile
        LaunchedEffect(state.mode, ui.settings.colorBlind, layout) {
            withContext(Dispatchers.Default) {
                DeckFactory.distinctFaces(state.mode).forEach { CardArt.face(it, ui.settings.colorBlind) }
                CardArt.back(profile.cardBack)
            }
        }

        TableCanvas(vm, world, ui, profile.tableTheme, profile.cardBack)

        // ---------- seats
        state.players.forEach { p ->
            if (p.id != vm.human) {
                OpponentSeat(
                    p = p, state = state, ui = ui, pos = layout.seatOf(p.id), avatarPx = layout.avatar, localId = vm.human,
                    onCatch = { vm.catchPlayer(p.id) }, onSwap = { vm.chooseSwap(p.id) },
                )
            }
        }
        HumanSeat(state.players[vm.human], state, ui, layout.humanSeat, layout.avatar)

        // ---------- top bar
        TopBar(state, Modifier.padding(top = with(density) { topInset.toDp() }), onPause = {
            showPause = true
            vm.setPaused(true)
        }, onHelp = {
            showPause = true
            vm.setPaused(true)
            onHowTo(state.mode)
        })

        // ---------- status pill
        StatusPill(state, ui, vm.human, Modifier.offset { IntOffset(0, (layout.pileY + layout.pileCardW * 0.95f + 26 * density.density).roundToInt()) })

        // ---------- action bar
        ActionBar(
            state = state, ui = ui, human = vm.human,
            y = layout.handTopY - layout.handCardH * (if (state.players[vm.human].hand.size > 13) 0.75f else 0.42f) - 58 * density.density,
            onSort = vm::cycleSort, onHint = vm::hint, onMania = vm::callMania, onKeep = vm::keepDrawn,
            onEmote = { showEmotes = !showEmotes }, onShield = vm::armShield, onPeek = vm::peek,
        )
        if (showEmotes) {
            EmotePicker(Modifier.offset { IntOffset(0, (layout.handTopY - layout.handCardH * 0.42f - 130 * density.density).roundToInt()) }) {
                vm.emote(it)
                showEmotes = false
            }
        }

        // ---------- banners & peek
        Banner(ui, Modifier.offset { IntOffset(0, (layout.seats.minOfOrNull { it.y } ?: 0f).let { (layout.pileY - layout.pileCardW * 1.35f).roundToInt() }) })
        ui.peek?.let { PeekOverlay(it, state) }

        // ---------- human decisions
        val ph = state.phase
        val idle = ui.idle && !ui.paused
        AnimatedVisibility(idle && ph is Phase.ChooseColor && ph.playerId == vm.human, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut() + scaleOut()) {
            val reason = (ph as? Phase.ChooseColor)?.reason
            ColorPicker(state.side, reason, onPick = vm::chooseColor)
        }
        AnimatedVisibility(idle && ph is Phase.Challenge && ph.victimId == vm.human, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
            val c = ph as? Phase.Challenge
            if (c != null) ChallengeDialog(state.players[c.offenderId].name, c.amount, onChallenge = { vm.challenge(true) }, onAccept = { vm.challenge(false) })
        }
        if (idle && ph is Phase.ChooseSwap && ph.playerId == vm.human) {
            SwapHint()
        }

        // ---------- results
        when (val r = ui.result) {
            is ResultInfo.Round -> RoundResultOverlay(r, state, vm.human, onNext = if (ui.net == com.puzzel.lab.cardsmania.net.NetRole.GUEST) null else vm::nextRound)
            is ResultInfo.Match -> MatchResultOverlay(
                r, state, vm.human, ui.profile,
                onPlayAgain = if (ui.net == com.puzzel.lab.cardsmania.net.NetRole.GUEST) null else ({ onPlayAgain(state.config) }),
                onHome = onExit,
                onRoom = if (ui.net == com.puzzel.lab.cardsmania.net.NetRole.HOST) onBackToRoom else null,
            )
            null -> Unit
        }

        ui.ended?.let { reason ->
            EndedDialog(reason, onOk = onExit)
        }

        if (showPause) {
            PauseMenu(
                ui = ui,
                net = ui.net,
                onResume = {
                    showPause = false
                    vm.setPaused(false)
                },
                onHowTo = { onHowTo(state.mode) },
                onQuit = {
                    showPause = false
                    onExit()
                },
            )
        }
    }
}

@Composable
private fun LoadingTable() {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(CM.RoyalLight, CM.Abyss))), contentAlignment = Alignment.Center) {
        val pulse = rememberPulse(700)
        OutlinedTitle("Shuffling…", fontSize = 30.sp, modifier = Modifier.scale(0.95f + pulse * 0.08f))
    }
}

// ------------------------------------------------------------------------------------------- top bar

@Composable
private fun TopBar(s: GameState, modifier: Modifier, onPause: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(GameIcons.Pause, onPause, size = 42.dp, contentDescription = "Pause")
        Spacer(Modifier.width(10.dp))
        // HUD plate: dark glass with a gold rim so it reads on any table theme
        Column(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xCC1A0844), Color(0xE60A0320))))
                .border(1.5.dp, Brush.verticalGradient(listOf(CM.GoldLight.copy(alpha = 0.85f), CM.GoldDeep.copy(alpha = 0.4f))), RoundedCornerShape(16.dp))
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(ModeInfo.title(s.mode).uppercase(), color = CM.GoldLight, fontFamily = DisplayFamily, fontSize = 16.sp, letterSpacing = 1.sp)
            val sub = buildString {
                if (s.config.targetScore > 0) append("Round ${s.round} · First to ${s.config.targetScore}") else append("Single round")
                if (s.mode == GameMode.ECLIPSE) append(if (s.side == com.puzzel.lab.cardsmania.engine.model.Side.SUN) " · Light side" else " · Dark side")
                if (s.mode == GameMode.CHAOS) append(" · 25 = out")
            }
            Text(sub, color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        RoundIconButton(GameIcons.Help, onHelp, size = 42.dp, contentDescription = "How to play")
    }
}

// ------------------------------------------------------------------------------------------- seats

private fun Modifier.centeredAt(pos: Offset, sizePx: Float) = this.offset { IntOffset((pos.x - sizePx / 2).roundToInt(), (pos.y - sizePx / 2).roundToInt()) }

@Composable
private fun OpponentSeat(
    p: PlayerState,
    state: GameState,
    ui: GameUi,
    pos: Offset,
    avatarPx: Float,
    localId: Int,
    onCatch: () -> Unit,
    onSwap: () -> Unit,
) {
    val density = LocalDensity.current
    val boxPx = avatarPx * 2.4f
    val boxDp = with(density) { boxPx.toDp() }
    val avatarDp = with(density) { avatarPx.toDp() }
    val active = !state.isOver && state.actingPlayer == p.id
    val pulse = rememberPulse(650)
    val activeScale by animateFloatAsState(if (active) 1.12f else 1f, spring(0.5f, 400f), label = "seat")
    Box(Modifier.centeredAt(pos, boxPx).size(boxDp), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(Modifier.size(avatarDp * 1.5f)) {
                drawCircle(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent)), radius = size.minDimension / 2)
                drawCircle(CM.GoldLight.copy(alpha = 0.9f), radius = size.minDimension * 0.36f, style = Stroke(3.dp.toPx()))
            }
        }
        Box(Modifier.scale(activeScale).alpha(if (p.eliminated) 0.4f else 1f)) {
            AvatarBadge(AvatarRef.emoji(p.setup.avatar), size = avatarDp, ringColor = AvatarRef.color(p.setup.avatar))
            // card count
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (p.hand.size == 1) CM.Ruby else Color(0xFF1A0B3D))
                    .border(1.5.dp, if (p.hand.size == 1) Color.White else CM.Gold, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(if (p.eliminated) "OUT" else "${p.hand.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            if (p.shield) {
                Box(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-4).dp).size(18.dp).clip(CircleShape).background(CM.Aqua), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(GameIcons.Shield, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            // multiplayer: a real player (Wi-Fi badge) or a bot covering for a dropped player
            ui.netSeats[p.id]?.let { connected ->
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (connected) CM.Emerald else Color(0xFF6B6B7A))
                        .border(1.5.dp, Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (connected) androidx.compose.material3.Icon(GameIcons.Wifi, "Online player", tint = Color.White, modifier = Modifier.size(11.dp))
                    else Text("BOT", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        // name
        Column(
            Modifier.align(Alignment.Center).offset(y = avatarDp * 0.72f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(DifficultyInfo.color(p.setup.difficulty)))
                Spacer(Modifier.width(4.dp))
                Text(p.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(IntrinsicNameWidth))
                if (state.config.targetScore > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("${p.score}", color = CM.GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        // emote
        EmoteBubble(ui.emotes[p.id], Modifier.align(Alignment.TopCenter).offset(x = avatarDp * 0.55f, y = (-avatarDp * 0.15f)))
        // catch!
        if (p.id in ui.catchTargets) {
            Box(
                Modifier.align(Alignment.Center).offset(y = -(avatarDp * 0.75f)).scale(1f + 0.08f * pulse)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF7FA0), CM.Ruby)))
                    .border(2.dp, Color.White, RoundedCornerShape(50))
                    .clickable(onClick = onCatch)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("CATCH!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
        // swap target
        val ph = state.phase
        if (ui.idle && ph is Phase.ChooseSwap && ph.playerId == localId && !p.eliminated) {
            Box(
                Modifier.align(Alignment.Center).offset(y = -(avatarDp * 0.78f)).scale(1f + 0.06f * pulse)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFF7FF5EC), CM.Aqua)))
                    .border(2.dp, Color.White, RoundedCornerShape(50))
                    .clickable(onClick = onSwap)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("SWAP", color = Color(0xFF04504A), fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
    }
}

private val IntrinsicNameWidth = 64.dp

@Composable
private fun HumanSeat(p: PlayerState, state: GameState, ui: GameUi, pos: Offset, avatarPx: Float) {
    val density = LocalDensity.current
    val sizePx = avatarPx * 0.9f
    val avatarDp = with(density) { sizePx.toDp() }
    val active = !state.isOver && state.actingPlayer == p.id
    val pulse = rememberPulse(650)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.turnDeadline) {
        while (ui.turnDeadline > 0) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    Box(Modifier.centeredAt(pos, sizePx * 1.6f).size(avatarDp * 1.6f), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(Modifier.size(avatarDp * 1.55f)) {
                drawCircle(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.5f + 0.3f * pulse), Color.Transparent)), radius = size.minDimension / 2)
            }
        }
        AvatarBadge(AvatarRef.emoji(p.setup.avatar), size = avatarDp, frame = ui.profile.avatarFrame)
        if (ui.turnDeadline > 0 && ui.turnTotal > 0) {
            val frac = ((ui.turnDeadline - now).toFloat() / ui.turnTotal).coerceIn(0f, 1f)
            Canvas(Modifier.size(avatarDp * 1.12f)) {
                drawArc(
                    if (frac < 0.3f) CM.Ruby else CM.Emerald, -90f, 360f * frac, false,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        if (p.shield) {
            Box(Modifier.align(Alignment.TopStart).size(18.dp).clip(CircleShape).background(CM.Aqua), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(GameIcons.Shield, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        EmoteBubble(ui.emotes[p.id], Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-10).dp))
        if (state.config.targetScore > 0) {
            Box(
                Modifier.align(Alignment.BottomCenter).offset(y = 8.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text("${p.score}", color = CM.GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun EmoteBubble(e: Emote?, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(e?.at) {
        if (e != null && System.currentTimeMillis() - e.at < 2000) {
            visible = true
            delay(1800)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier = modifier, enter = scaleIn(spring(0.45f, 500f)) + fadeIn(), exit = fadeOut() + scaleOut()) {
        Box(
            Modifier.clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 3.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp)
        ) { Text(e?.text ?: "", fontSize = 22.sp) }
    }
}

// ------------------------------------------------------------------------------------------- status & actions

@Composable
private fun StatusPill(s: GameState, ui: GameUi, human: Int, modifier: Modifier) {
    val me = s.players[human]
    val actor = s.players.getOrNull(s.actingPlayer)
    val text = when {
        s.isOver -> ""
        !ui.idle -> ""
        me.eliminated -> "You're out — watching the rivals battle!"
        s.actingPlayer == human -> when (val ph = s.phase) {
            is Phase.Play -> when {
                s.pendingDraw > 0 && ui.playable.isNotEmpty() -> "Stack a +card or tap the deck to take ${s.pendingDraw}"
                s.pendingDraw > 0 -> "Tap the deck to take ${s.pendingDraw} cards"
                ui.playable.isEmpty() -> "No match — tap the deck to draw"
                ui.selected.isNotEmpty() -> "Tap again or swipe up to play"
                else -> "Your turn!"
            }
            is Phase.DrawnCard -> "Play the drawn card or keep it"
            is Phase.ChooseSwap -> "Choose a rival to swap hands with"
            else -> ""
        }
        actor != null -> when (s.phase) {
            is Phase.ChooseColor -> "${actor.name} is choosing a color…"
            is Phase.Challenge -> "${actor.name} is deciding…"
            is Phase.ChooseSwap -> "${actor.name} is picking a swap…"
            else -> if (ui.jumpIn.isNotEmpty()) "Jump in with your matching card!" else "${actor.name} is thinking…"
        }
        else -> ""
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            val mine = s.actingPlayer == human
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (mine) Brush.horizontalGradient(listOf(Color(0xCCB36B00), Color(0xCCFFB300), Color(0xCCB36B00))) else Brush.horizontalGradient(listOf(Color(0x99000000), Color(0x99000000))))
                    .border(1.dp, if (mine) CM.GoldLight else Color(0x33FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(text, color = if (mine) Color(0xFF3A1F00) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ActionBar(
    state: GameState,
    ui: GameUi,
    human: Int,
    y: Float,
    onSort: () -> Unit,
    onHint: () -> Unit,
    onMania: () -> Unit,
    onKeep: () -> Unit,
    onEmote: () -> Unit,
    onShield: () -> Unit,
    onPeek: () -> Unit,
) {
    val me = state.players[human]
    val pulse = rememberPulse(420)
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(56.dp)) // room for the human avatar
        RoundIconButton(GameIcons.Sort, onSort, size = 40.dp, contentDescription = "Sort")
        Spacer(Modifier.width(8.dp))
        RoundIconButton(GameIcons.Hint, onHint, size = 40.dp, tint = if (ui.myTurn) CM.GoldLight else Color.White, contentDescription = "Hint")
        Spacer(Modifier.width(8.dp))
        RoundIconButton(GameIcons.Emote, onEmote, size = 40.dp, contentDescription = "Emote")
        if (state.config.powerUps && me.tokens > 0 && !me.eliminated) {
            Spacer(Modifier.width(8.dp))
            RoundIconButton(GameIcons.Eye, onPeek, size = 40.dp, tint = CM.Aqua, badge = "${me.tokens}", contentDescription = "Peek")
            if (!me.shield) {
                Spacer(Modifier.width(8.dp))
                RoundIconButton(GameIcons.Shield, onShield, size = 40.dp, tint = CM.Aqua, contentDescription = "Shield")
            }
        }
        Spacer(Modifier.weight(1f))
        val ph = state.phase
        if (ui.idle && ph is Phase.DrawnCard && ph.playerId == human && !state.config.forcedPlay) {
            GameButton("KEEP", onKeep, style = ButtonStyle.PURPLE, height = 40.dp, fontSize = 14.sp, modifier = Modifier.width(84.dp))
            Spacer(Modifier.width(8.dp))
        }
        AnimatedVisibility(ui.showMania, enter = scaleIn(spring(0.4f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            val armed = ui.maniaArmed
            Box(Modifier.scale(if (armed) 1f else 1f + 0.07f * pulse)) {
                GameButton(
                    if (armed) "MANIA! ✓" else "MANIA!", onMania,
                    style = if (armed) ButtonStyle.GREEN else ButtonStyle.RED, height = 46.dp, fontSize = 17.sp,
                    modifier = Modifier.width(128.dp), sound = com.puzzel.lab.cardsmania.audio.Sfx.SELECT,
                )
            }
        }
    }
}

@Composable
private fun EmotePicker(modifier: Modifier, onPick: (String) -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xEE1A0B3D)).border(1.5.dp, CM.Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("👍", "😂", "😮", "😡", "🎉", "😎", "🙏").forEach { e ->
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun Banner(ui: GameUi, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.bannerAt) {
        if (ui.banner != null && System.currentTimeMillis() - ui.bannerAt < 1500) {
            text = ui.banner
            delay(1300)
            text = null
        }
    }
    Box(modifier.fillMaxWidth().wrapContentSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(text != null, enter = scaleIn(spring(0.45f, 380f), initialScale = 0.3f) + fadeIn(), exit = fadeOut() + scaleOut(targetScale = 1.3f)) {
            OutlinedTitle(text ?: "", fontSize = 38.sp, outlineWidth = 12f)
        }
    }
}

@Composable
private fun SwapHint() {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.0f })
}

@Composable
private fun PeekOverlay(cards: List<com.puzzel.lab.cardsmania.engine.model.Card>, s: GameState) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle("Next cards", fontSize = 26.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.forEach { c ->
                    androidx.compose.foundation.Image(
                        CardArt.face(c.face(s.side)), null,
                        modifier = Modifier.width(86.dp).height(120.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Top of the deck, in draw order", color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Suppress("unused")
private fun Modifier.onSize(block: (Int, Int) -> Unit) = onSizeChanged { block(it.width, it.height) }
