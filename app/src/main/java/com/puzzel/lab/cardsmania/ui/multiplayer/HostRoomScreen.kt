package com.puzzel.lab.cardsmania.ui.multiplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.GameLaunch
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.audio.Track
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.mindi.MindiSetup
import com.puzzel.lab.cardsmania.mindi.MindiTrumpMode
import com.puzzel.lab.cardsmania.net.NetRole
import com.puzzel.lab.cardsmania.net.RoomGame
import com.puzzel.lab.cardsmania.ui.mindi.MindiGameTile
import com.puzzel.lab.cardsmania.ui.mindi.MindiLaunch
import com.puzzel.lab.cardsmania.ui.mindi.SeatOption
import com.puzzel.lab.cardsmania.ui.mindi.TeamBlue
import com.puzzel.lab.cardsmania.ui.mindi.TeamRed
import com.puzzel.lab.cardsmania.ui.mindi.ToggleRow
import com.puzzel.lab.cardsmania.ui.mindi.TrumpModeCard
import com.puzzel.lab.cardsmania.ui.mindi.mindiTableLine
import com.puzzel.lab.cardsmania.net.RoomState
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.game.EndedDialog
import com.puzzel.lab.cardsmania.ui.setup.HouseRulesPanel
import com.puzzel.lab.cardsmania.ui.setup.Label
import com.puzzel.lab.cardsmania.ui.setup.ModeTile
import com.puzzel.lab.cardsmania.ui.theme.CM

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostRoomScreen(onClosed: () -> Unit, onStart: (mindi: Boolean) -> Unit) {
    val app = LocalApp.current
    val mp = app.multiplayer
    val host = mp.host
    if (host == null) {
        EndedDialog("The room is closed.", onOk = onClosed)
        return
    }
    val room by host.room.collectAsState()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        profile = app.profile.current()
        app.music.play(Track.MENU)
    }
    // play a chime when a friend joins
    var lastCount by remember { mutableStateOf(room.guests.size) }
    LaunchedEffect(room.guests.size) {
        if (room.guests.size > lastCount) app.sound.play(Sfx.TURN)
        lastCount = room.guests.size
    }
    BackHandler { confirmClose = true }

    fun update(t: (RoomState) -> RoomState) = host.update(t)

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitleBar("Your Room", { confirmClose = true })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---------- room code
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6A3FE0), Color(0xFF2A0E5C))))
                        .border(2.dp, CM.Gold, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot()
                        Spacer(Modifier.width(8.dp))
                        Text("Room open on your Wi-Fi", color = CM.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("ROOM CODE", color = CM.TextDim, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
                    OutlinedTitle(room.code.ifBlank { "—" }, fontSize = 34.sp)
                    Text(
                        "Friends will see your room under Join a Room. If it doesn't show up, they can type this code.",
                        color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }

                SeatList(room, onKick = { id -> host.kick(id) })

                // ---------- game
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader("Game")
                    Spacer(Modifier.height(10.dp))
                    val maniaAllowed = room.players <= 6
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameMode.entries.forEach { m ->
                            ModeTile(m, !room.isMindi && m == room.mode, Modifier.weight(1f).graphicsLayer { alpha = if (maniaAllowed) 1f else 0.4f }) {
                                if (!maniaAllowed) app.sound.play(Sfx.ERROR)
                                else update { it.copy(game = RoomGame.MANIA, mode = m, rules = if (it.isMindi || it.mode != m) ModeInfo.defaultRules(m) else it.rules, seats = it.seats.coerceAtMost(6)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    MindiGameTile(room.isMindi, Modifier.fillMaxWidth()) {
                        update { if (it.isMindi) it else it.copy(game = RoomGame.MINDI, seats = it.mindi.players) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (room.isMindi) "Two teams, alternate seats. Win 3 of the 4 tens to take the hand." else ModeInfo.tagline(room.mode),
                        color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                    if (!maniaAllowed) Text("Mania tables seat up to 6 — Mindi fits up to 8.", color = CM.TextMuted, fontSize = 11.sp)
                }

                if (room.isMindi) {
                    MindiRoomOptions(room, ::update)
                } else {
                // ---------- seats & bots
                GlassPanel(Modifier.fillMaxWidth().animateContentSize()) {
                    SectionHeader("Seats & bots")
                    Spacer(Modifier.height(10.dp))
                    Label("Seats at the table")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (2..6).forEach { n ->
                            val allowed = n >= 1 + room.guests.size
                            ChoiceChip("$n", room.seats == n, {
                                if (allowed) update { it.copy(seats = n, bots = it.bots.coerceAtMost(n - 1 - it.guests.size)) } else app.sound.play(Sfx.ERROR)
                            }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bots", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("${room.openSeats} seat(s) open for friends", color = CM.TextDim, fontSize = 12.sp)
                        }
                        Stepper(room.bots, 0, room.maxBots) { v -> update { it.copy(bots = v) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Bot difficulty")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.entries.forEach { d ->
                            ChoiceChip(DifficultyInfo.label(d), room.botDifficulty == d, { update { it.copy(botDifficulty = d) } }, Modifier.weight(1f), color = DifficultyInfo.color(d))
                        }
                    }
                }

                // ---------- cards
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader("Cards")
                    Spacer(Modifier.height(10.dp))
                    Label("Starting hand")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 7, 10).forEach { n -> ChoiceChip("$n cards", room.handSize == n, { update { it.copy(handSize = n) } }, Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Deck")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Standard", room.deckCount == 1, { update { it.copy(deckCount = 1) } }, Modifier.weight(1f))
                        ChoiceChip("Double deck", room.deckCount == 2, { update { it.copy(deckCount = 2) } }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Match length")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 100, 250, 500, 1000).forEach { t ->
                            ChoiceChip(if (t == 0) "Single round" else "First to $t", room.targetScore == t, { update { it.copy(targetScore = t) } })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Game speed")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Relaxed", "Normal", "Fast").forEachIndexed { i, s ->
                            ChoiceChip(s, room.speed == i, { update { it.copy(speed = i) } }, Modifier.weight(1f))
                        }
                    }
                }

                HouseRulesPanel(room.mode, room.rules) { r -> update { it.copy(rules = r) } }
                }
                Spacer(Modifier.height(4.dp))
            }
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0320))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val friends = room.guests.count { it.connected }
                Text(
                    if (friends == 0) "No friends yet — you can still start with bots." else "$friends friend(s) ready",
                    color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                GameButton(
                    if (room.isMindi) "DEAL CARDS" else "START GAME", {
                        val p = profile ?: return@GameButton
                        if (room.isMindi) {
                            val h = mp.startHostedMindi(p) ?: return@GameButton
                            app.pendingMindi = MindiLaunch(setup = h.setup, net = NetRole.HOST, seats = h.seats, seed = h.seed)
                            onStart(true)
                        } else {
                            val cfg = mp.startHostedGame(p) ?: return@GameButton
                            app.pendingLaunch = GameLaunch(cfg, net = NetRole.HOST)
                            onStart(false)
                        }
                    },
                    style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow, height = 60.dp, fontSize = 21.sp,
                    modifier = Modifier.fillMaxWidth(), sound = if (room.isMindi) Sfx.SHUFFLE else Sfx.MANIA, enabled = profile != null,
                )
            }
        }
    }

    if (confirmClose) {
        ConfirmDialog(
            title = "Close room?",
            text = if (room.guests.isEmpty()) "Your room will disappear from the Wi-Fi." else "Everyone in the room will be sent back home.",
            confirm = "Close room",
            onConfirm = {
                confirmClose = false
                mp.closeRoom()
                onClosed()
            },
            onDismiss = { confirmClose = false },
        )
    }
    @Suppress("UNUSED_VARIABLE") val unused = scope
}

/** Lobby options for a Mindi room: table size, bots, teams, trump and match settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MindiRoomOptions(room: RoomState, update: ((RoomState) -> RoomState) -> Unit) {
    val app = LocalApp.current
    val setup = room.mindi
    fun mindi(t: (MindiSetup) -> MindiSetup) = update { it.copy(mindi = t(it.mindi)) }

    GlassPanel(Modifier.fillMaxWidth().animateContentSize()) {
        SectionHeader("Table")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(4, 6, 8).forEach { n ->
                val allowed = n >= room.players
                SeatOption(n, room.seats == n, Modifier.weight(1f), enabled = allowed) {
                    if (!allowed) app.sound.play(Sfx.ERROR)
                    else update { it.copy(seats = n, bots = it.bots.coerceAtMost(n - 1 - it.guests.size), mindi = it.mindi.copy(players = n, doubleDeck = it.mindi.doubleDeck && n >= 6)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(mindiTableLine(setup), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (room.seats >= 6) {
            Spacer(Modifier.height(8.dp))
            ToggleRow("Double deck", "104 cards and 8 tens — 5 tens win the hand", setup.doubleDeck) { v -> mindi { it.copy(doubleDeck = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bots", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("${room.openSeats} seat(s) open for friends · bots fill any empty seat", color = CM.TextDim, fontSize = 12.sp)
            }
            Stepper(room.bots, 0, room.maxBots) { v -> update { it.copy(bots = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Label("Bot difficulty")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Difficulty.entries.forEach { d ->
                ChoiceChip(if (d == Difficulty.GENIUS) "Expert" else DifficultyInfo.label(d), room.botDifficulty == d, { update { it.copy(botDifficulty = d) } }, Modifier.weight(1f), color = DifficultyInfo.color(d))
            }
        }
        Spacer(Modifier.height(12.dp))
        Label("Teams")
        TeamsPreview(room)
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader("Trump")
        Spacer(Modifier.height(10.dp))
        MindiTrumpMode.entries.forEach { m ->
            TrumpModeCard(m, setup.trumpMode == m) { mindi { it.copy(trumpMode = m) } }
            Spacer(Modifier.height(8.dp))
        }
        if (setup.trumpMode == MindiTrumpMode.HIDE) {
            ToggleRow("Must trump after reveal", "Whoever reveals the trump must play one if they can", setup.mustTrumpAfterReveal) { v ->
                mindi { it.copy(mustTrumpAfterReveal = v) }
            }
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader("Match")
        Spacer(Modifier.height(10.dp))
        Label("Points to win")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 5, 7, 10).forEach { t ->
                ChoiceChip(if (t == 1) "1 hand" else "$t", setup.target == t, { mindi { it.copy(target = t) } }, Modifier.weight(1f))
            }
        }
        Text("Win = 1 point · Mendikot = 2 · Whitewash = 3", color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Label("Turn timer")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 30, 45).forEach { t ->
                val sel = setup.turnTimer == t || (t == 30 && setup.turnTimer !in listOf(10, 20, 45))
                ChoiceChip("${t}s", sel, { mindi { it.copy(turnTimer = t) } }, Modifier.weight(1f))
            }
        }
        Text("When time runs out, a safe card is played for that player.", color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Label("Game speed")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Relaxed", "Normal", "Fast").forEachIndexed { i, s ->
                ChoiceChip(s, room.speed == i, { update { it.copy(speed = i) } }, Modifier.weight(1f))
            }
        }
    }
}

/** Who will sit on which team when the match starts (seats alternate teams; bots fill the rest). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamsPreview(room: RoomState) {
    val names = buildList {
        add(room.hostName.ifBlank { "Host" } + " (you)")
        room.guests.forEach { add(it.name) }
        while (size < room.seats) add("Bot")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 1).forEach { team ->
            val color = if (team == 0) TeamBlue else TeamRed
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f))
                    .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(14.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(if (team == 0) "BLUE TEAM" else "RED TEAM", color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                names.forEachIndexed { seat, n ->
                    if (seat % 2 == team) Text(n, color = if (n == "Bot") CM.TextDim else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val app = LocalApp.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Rounded.Remove, value > min) { onChange(value - 1); app.sound.play(Sfx.SELECT) }
        Text("$value", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
        StepButton(Icons.Rounded.Add, value < max) { onChange(value + 1); app.sound.play(Sfx.SELECT) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (enabled) Brush.verticalGradient(listOf(CM.GoldLight, CM.Gold)) else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (enabled) Color(0xFF3A1F00) else Color(0x66FFFFFF)) }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(Modifier.padding(horizontal = 32.dp).fillMaxWidth().clickable(enabled = false) {}, padding = 20.dp) {
            OutlinedTitle(title, fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Text(text, color = CM.Text, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton("Stay", onDismiss, style = ButtonStyle.GLASS, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 15.sp)
                GameButton(confirm, onConfirm, style = ButtonStyle.RED, modifier = Modifier.weight(1.3f), height = 50.dp, fontSize = 15.sp)
            }
        }
    }
}
