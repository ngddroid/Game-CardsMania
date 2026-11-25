package com.ngdstudio.mindimania.ui.multiplayer

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.GameLaunch
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.audio.Track
import com.ngdstudio.mindimania.data.DifficultyInfo
import com.ngdstudio.mindimania.data.ModeInfo
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.bhabhi.BhabhiEngine
import com.ngdstudio.mindimania.bhabhi.BhabhiSetup
import com.ngdstudio.mindimania.mindi.MindiSetup
import com.ngdstudio.mindimania.mindi.MindiTrumpMode
import com.ngdstudio.mindimania.net.NetRole
import com.ngdstudio.mindimania.net.RoomGame
import com.ngdstudio.mindimania.ui.bhabhi.BhabhiGameTile
import com.ngdstudio.mindimania.ui.bhabhi.BhabhiLaunch
import com.ngdstudio.mindimania.ui.bhabhi.tableLine
import com.ngdstudio.mindimania.ui.rummy.RummyGameTile
import com.ngdstudio.mindimania.ui.rummy.RummyLaunch
import com.ngdstudio.mindimania.ui.teenpatti.TeenPattiGameTile
import com.ngdstudio.mindimania.ui.teenpatti.TeenPattiLaunch
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiSetup
import com.ngdstudio.mindimania.rummy.RummyEngine
import com.ngdstudio.mindimania.rummy.RummySetup
import com.ngdstudio.mindimania.ui.mindi.MindiGameTile
import com.ngdstudio.mindimania.ui.mindi.MindiLaunch
import com.ngdstudio.mindimania.ui.mindi.SeatOption
import com.ngdstudio.mindimania.ui.mindi.TeamBlue
import com.ngdstudio.mindimania.ui.mindi.TeamRed
import com.ngdstudio.mindimania.ui.mindi.ToggleRow
import com.ngdstudio.mindimania.ui.mindi.TrumpModeCard
import com.ngdstudio.mindimania.ui.mindi.mindiTableLine
import com.ngdstudio.mindimania.net.RoomState
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.game.EndedDialog
import com.ngdstudio.mindimania.ui.setup.HouseRulesPanel
import com.ngdstudio.mindimania.ui.setup.Label
import com.ngdstudio.mindimania.ui.setup.ModeTile
import com.ngdstudio.mindimania.ui.theme.CM

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostRoomScreen(onClosed: () -> Unit, onStart: (game: RoomGame) -> Unit) {
    val app = LocalApp.current
    val mp = app.multiplayer
    val host = mp.host
    if (host == null) {
        EndedDialog(stringResource(R.string.mp_room_closed), onOk = onClosed)
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
            ScreenTitleBar(stringResource(R.string.mp_your_room), { confirmClose = true })
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
                        Text(stringResource(R.string.mp_room_open), color = CM.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.mp_room_code), color = CM.TextDim, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
                    OutlinedTitle(room.code.ifBlank { "—" }, fontSize = 34.sp)
                    Text(
                        stringResource(R.string.mp_room_code_hint),
                        color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }

                SeatList(room, onKick = { id -> host.kick(id) })

                // ---------- game
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.mp_game))
                    Spacer(Modifier.height(10.dp))
                    val maniaAllowed = room.players <= 6
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameMode.entries.forEach { m ->
                            ModeTile(m, room.isMania && m == room.mode, Modifier.weight(1f).graphicsLayer { alpha = if (maniaAllowed) 1f else 0.4f }) {
                                if (!maniaAllowed) app.sound.play(Sfx.ERROR)
                                else update { it.copy(game = RoomGame.MANIA, mode = m, rules = if (!it.isMania || it.mode != m) ModeInfo.defaultRules(m) else it.rules, seats = it.seats.coerceAtMost(6)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    MindiGameTile(room.isMindi, Modifier.fillMaxWidth()) {
                        update { if (it.isMindi) it else it.copy(game = RoomGame.MINDI, seats = it.mindi.players) }
                    }
                    Spacer(Modifier.height(10.dp))
                    BhabhiGameTile(room.isBhabhi, Modifier.fillMaxWidth()) {
                        update { if (it.isBhabhi) it else it.copy(game = RoomGame.BHABHI, seats = maxOf(it.bhabhi.players, it.players)) }
                    }
                    // Teen Patti and Rummy tables seat at most 6 (switched off for now: Net.TEEN_PATTI_RUMMY_ROOMS)
                    if (com.ngdstudio.mindimania.net.Net.TEEN_PATTI_RUMMY_ROOMS) {
                        Spacer(Modifier.height(10.dp))
                        TeenPattiGameTile(room.isTeenPatti, Modifier.fillMaxWidth(), enabled = maniaAllowed) {
                            update { if (it.isTeenPatti) it else it.copy(game = RoomGame.TEEN_PATTI, seats = maxOf(it.teenPatti.players, it.players).coerceAtMost(6)) }
                        }
                        Spacer(Modifier.height(10.dp))
                        RummyGameTile(room.isRummy, Modifier.fillMaxWidth(), enabled = maniaAllowed) {
                            update { if (it.isRummy) it else it.copy(game = RoomGame.RUMMY, seats = maxOf(it.rummy.players, it.players).coerceAtMost(6)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            room.isMindi -> stringResource(R.string.mp_mindi_tagline)
                            room.isBhabhi -> stringResource(R.string.mp_bhabhi_tagline)
                            room.isTeenPatti -> stringResource(R.string.mp_tp_tagline)
                            room.isRummy -> stringResource(R.string.mp_rummy_tagline)
                            else -> ModeInfo.tagline(room.mode)
                        },
                        color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                    if (!maniaAllowed) Text(stringResource(R.string.mp_mania_limit), color = CM.TextMuted, fontSize = 11.sp)
                }

                if (room.isMindi) {
                    MindiRoomOptions(room, ::update)
                } else if (room.isBhabhi) {
                    BhabhiRoomOptions(room, ::update)
                } else if (room.isTeenPatti) {
                    TeenPattiRoomOptions(room, ::update)
                } else if (room.isRummy) {
                    RummyRoomOptions(room, ::update)
                } else {
                // ---------- seats & bots
                GlassPanel(Modifier.fillMaxWidth().animateContentSize()) {
                    SectionHeader(stringResource(R.string.mp_seats_bots))
                    Spacer(Modifier.height(10.dp))
                    Label(stringResource(R.string.mp_seats_at_table))
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
                            Text(stringResource(R.string.mp_bots), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text(pluralStringResource(R.plurals.mp_seats_open_friends, room.openSeats, room.openSeats), color = CM.TextDim, fontSize = 12.sp)
                        }
                        Stepper(room.bots, 0, room.maxBots) { v -> update { it.copy(bots = v) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label(stringResource(R.string.mp_bot_difficulty))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.entries.forEach { d ->
                            ChoiceChip(DifficultyInfo.label(d), room.botDifficulty == d, { update { it.copy(botDifficulty = d) } }, Modifier.weight(1f), color = DifficultyInfo.color(d))
                        }
                    }
                }

                // ---------- cards
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.mp_cards))
                    Spacer(Modifier.height(10.dp))
                    Label(stringResource(R.string.mp_starting_hand))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 7, 10).forEach { n -> ChoiceChip(stringResource(R.string.mp_n_cards, n), room.handSize == n, { update { it.copy(handSize = n) } }, Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label(stringResource(R.string.mp_deck))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(stringResource(R.string.mp_standard), room.deckCount == 1, { update { it.copy(deckCount = 1) } }, Modifier.weight(1f))
                        ChoiceChip(stringResource(R.string.mp_double_deck), room.deckCount == 2, { update { it.copy(deckCount = 2) } }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Label(stringResource(R.string.mp_match_length))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 100, 250, 500, 1000).forEach { t ->
                            ChoiceChip(if (t == 0) stringResource(R.string.mp_single_round) else stringResource(R.string.mp_first_to, t), room.targetScore == t, { update { it.copy(targetScore = t) } })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label(stringResource(R.string.mp_game_speed))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(stringResource(R.string.mp_speed_relaxed), stringResource(R.string.mp_speed_normal), stringResource(R.string.mp_speed_fast)).forEachIndexed { i, s ->
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
                    if (friends == 0) stringResource(R.string.mp_no_friends) else pluralStringResource(R.plurals.mp_friends_ready, friends, friends),
                    color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                GameButton(
                    if (room.isMania) stringResource(R.string.mp_start_game) else stringResource(R.string.mp_deal_cards), {
                        val p = profile ?: return@GameButton
                        when (room.game) {
                            RoomGame.MINDI -> {
                                val h = mp.startHostedMindi(p) ?: return@GameButton
                                app.pendingMindi = MindiLaunch(setup = h.setup, net = NetRole.HOST, seats = h.seats, seed = h.seed)
                            }
                            RoomGame.BHABHI -> {
                                val h = mp.startHostedBhabhi(p) ?: return@GameButton
                                app.pendingBhabhi = BhabhiLaunch(setup = h.setup, net = NetRole.HOST, seats = h.seats, seed = h.seed)
                            }
                            RoomGame.TEEN_PATTI -> {
                                val h = mp.startHostedTeenPatti(p) ?: return@GameButton
                                app.pendingTeenPatti = TeenPattiLaunch(setup = h.setup, net = NetRole.HOST, seats = h.seats, seed = h.seed)
                            }
                            RoomGame.RUMMY -> {
                                val h = mp.startHostedRummy(p) ?: return@GameButton
                                app.pendingRummy = RummyLaunch(setup = h.setup, net = NetRole.HOST, seats = h.seats, seed = h.seed)
                            }
                            RoomGame.MANIA -> {
                                val cfg = mp.startHostedGame(p) ?: return@GameButton
                                app.pendingLaunch = GameLaunch(cfg, net = NetRole.HOST)
                            }
                        }
                        onStart(room.game)
                    },
                    style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow, height = 60.dp, fontSize = 21.sp,
                    modifier = Modifier.fillMaxWidth(), sound = if (room.isMania) Sfx.MANIA else Sfx.SHUFFLE, enabled = profile != null,
                )
            }
        }
    }

    if (confirmClose) {
        ConfirmDialog(
            title = stringResource(R.string.mp_close_title),
            text = if (room.guests.isEmpty()) stringResource(R.string.mp_close_text_empty) else stringResource(R.string.mp_close_text_guests),
            confirm = stringResource(R.string.mp_close_room),
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
        SectionHeader(stringResource(R.string.mp_table))
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
            ToggleRow(stringResource(R.string.mp_double_deck), stringResource(R.string.mp_double_deck_desc), setup.doubleDeck) { v -> mindi { it.copy(doubleDeck = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mp_bots), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(pluralStringResource(R.plurals.mp_seats_open_friends_bots, room.openSeats, room.openSeats), color = CM.TextDim, fontSize = 12.sp)
            }
            Stepper(room.bots, 0, room.maxBots) { v -> update { it.copy(bots = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_bot_difficulty))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Difficulty.entries.forEach { d ->
                ChoiceChip(if (d == Difficulty.GENIUS) stringResource(R.string.mp_expert) else DifficultyInfo.label(d), room.botDifficulty == d, { update { it.copy(botDifficulty = d) } }, Modifier.weight(1f), color = DifficultyInfo.color(d))
            }
        }
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_teams))
        TeamsPreview(room)
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.mp_trump))
        Spacer(Modifier.height(10.dp))
        MindiTrumpMode.entries.forEach { m ->
            TrumpModeCard(m, setup.trumpMode == m) { mindi { it.copy(trumpMode = m) } }
            Spacer(Modifier.height(8.dp))
        }
        if (setup.trumpMode == MindiTrumpMode.HIDE) {
            ToggleRow(stringResource(R.string.mp_rule_must_trump), stringResource(R.string.mp_must_trump_desc), setup.mustTrumpAfterReveal) { v ->
                mindi { it.copy(mustTrumpAfterReveal = v) }
            }
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.mp_match))
        Spacer(Modifier.height(10.dp))
        Label(stringResource(R.string.mp_points_to_win))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 5, 7, 10).forEach { t ->
                ChoiceChip(if (t == 1) stringResource(R.string.mp_one_hand) else "$t", setup.target == t, { mindi { it.copy(target = t) } }, Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.mp_points_note), color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_turn_timer))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 30, 45).forEach { t ->
                val sel = setup.turnTimer == t || (t == 30 && setup.turnTimer !in listOf(10, 20, 45))
                ChoiceChip(stringResource(R.string.mp_seconds, t), sel, { mindi { it.copy(turnTimer = t) } }, Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.mp_timer_note), color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_game_speed))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.mp_speed_relaxed), stringResource(R.string.mp_speed_normal), stringResource(R.string.mp_speed_fast)).forEachIndexed { i, s ->
                ChoiceChip(s, room.speed == i, { update { it.copy(speed = i) } }, Modifier.weight(1f))
            }
        }
    }
}

/** Lobby options for a Bhabhi room: table size, bots, the safe first round, turn timer and speed. */
@Composable
private fun BhabhiRoomOptions(room: RoomState, update: ((RoomState) -> RoomState) -> Unit) {
    val app = LocalApp.current
    val setup = room.bhabhi
    fun bhabhi(t: (BhabhiSetup) -> BhabhiSetup) = update { it.copy(bhabhi = t(it.bhabhi)) }

    GlassPanel(Modifier.fillMaxWidth().animateContentSize()) {
        SectionHeader(stringResource(R.string.mp_table))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (BhabhiEngine.MIN_PLAYERS..BhabhiEngine.MAX_PLAYERS).forEach { n ->
                val allowed = n >= room.players
                ChoiceChip("$n", room.seats == n, {
                    if (!allowed) app.sound.play(Sfx.ERROR)
                    else update { it.copy(seats = n, bots = it.bots.coerceAtMost(n - 1 - it.guests.size), bhabhi = it.bhabhi.copy(players = n)) }
                }, Modifier.weight(1f).graphicsLayer { alpha = if (allowed) 1f else 0.4f })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tableLine(setup.players), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mp_bots), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(pluralStringResource(R.plurals.mp_seats_open_friends_bots, room.openSeats, room.openSeats), color = CM.TextDim, fontSize = 12.sp)
            }
            Stepper(room.bots, 0, room.maxBots) { v -> update { it.copy(bots = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_bot_difficulty))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Difficulty.entries.forEach { d ->
                ChoiceChip(if (d == Difficulty.GENIUS) stringResource(R.string.mp_expert) else DifficultyInfo.label(d), room.botDifficulty == d, { update { it.copy(botDifficulty = d) } }, Modifier.weight(1f), color = DifficultyInfo.color(d))
            }
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.mp_match))
        Spacer(Modifier.height(6.dp))
        ToggleRow(stringResource(R.string.bhabhi_safe_first), stringResource(R.string.bhabhi_safe_first_desc), setup.safeFirstTrick) { v -> bhabhi { it.copy(safeFirstTrick = v) } }
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_turn_timer))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 30, 45).forEach { t ->
                val sel = setup.turnTimer == t || (t == 30 && setup.turnTimer !in listOf(10, 20, 45))
                ChoiceChip(stringResource(R.string.mp_seconds, t), sel, { bhabhi { it.copy(turnTimer = t) } }, Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.mp_timer_note), color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_game_speed))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.mp_speed_relaxed), stringResource(R.string.mp_speed_normal), stringResource(R.string.mp_speed_fast)).forEachIndexed { i, s ->
                ChoiceChip(s, room.speed == i, { update { it.copy(speed = i) } }, Modifier.weight(1f))
            }
        }
    }
}

/** Who will sit on which team when the match starts (seats alternate teams; bots fill the rest). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamsPreview(room: RoomState) {
    // (name, isBot) per seat
    val hostLabel = stringResource(R.string.mp_name_you, room.hostName.ifBlank { stringResource(R.string.mp_host) })
    val botLabel = stringResource(R.string.mp_bot)
    val names = buildList {
        add(hostLabel to false)
        room.guests.forEach { add(it.name to false) }
        while (size < room.seats) add(botLabel to true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 1).forEach { team ->
            val color = if (team == 0) TeamBlue else TeamRed
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f))
                    .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(14.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(if (team == 0) stringResource(R.string.mp_blue_team) else stringResource(R.string.mp_red_team), color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                names.forEachIndexed { seat, (n, isBot) ->
                    if (seat % 2 == team) Text(n, color = if (isBot) CM.TextDim else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
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
                GameButton(stringResource(R.string.mp_stay), onDismiss, style = ButtonStyle.GLASS, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 15.sp)
                GameButton(confirm, onConfirm, style = ButtonStyle.RED, modifier = Modifier.weight(1.3f), height = 50.dp, fontSize = 15.sp)
            }
        }
    }
}

/** The table size, bots and bot level, shared by the Teen Patti and Rummy room options. */
@Composable
private fun TableSeats(room: RoomState, range: IntRange, update: ((RoomState) -> RoomState) -> Unit, withPlayers: (RoomState, Int) -> RoomState) {
    val app = LocalApp.current
    GlassPanel(Modifier.fillMaxWidth().animateContentSize()) {
        SectionHeader(stringResource(R.string.mp_table))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            range.forEach { n ->
                val allowed = n >= room.players
                ChoiceChip("$n", room.seats == n, {
                    if (!allowed) app.sound.play(Sfx.ERROR)
                    else update { withPlayers(it.copy(seats = n, bots = it.bots.coerceAtMost(n - 1 - it.guests.size)), n) }
                }, Modifier.weight(1f).graphicsLayer { alpha = if (allowed) 1f else 0.4f })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mp_bots), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(pluralStringResource(R.plurals.mp_seats_open_friends_bots, room.openSeats, room.openSeats), color = CM.TextDim, fontSize = 12.sp)
            }
            Stepper(room.bots, 0, room.maxBots) { v -> update { it.copy(bots = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.mp_bot_difficulty))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Difficulty.entries.forEach { d ->
                ChoiceChip(if (d == Difficulty.GENIUS) stringResource(R.string.mp_expert) else DifficultyInfo.label(d), room.botDifficulty == d, { update { it.copy(botDifficulty = d) } }, Modifier.weight(1f), color = DifficultyInfo.color(d))
            }
        }
    }
}

/** Turn timer and game speed, shared by the Teen Patti and Rummy room options. */
@Composable
private fun TimerAndSpeed(room: RoomState, timer: Int, setTimer: (Int) -> Unit, update: ((RoomState) -> RoomState) -> Unit, note: Int = R.string.mp_timer_note) {
    Label(stringResource(R.string.mp_turn_timer))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(20, 30, 45, 60).forEach { t ->
            val sel = timer == t || (t == 30 && timer !in listOf(20, 45, 60))
            ChoiceChip(stringResource(R.string.mp_seconds, t), sel, { setTimer(t) }, Modifier.weight(1f))
        }
    }
    Text(stringResource(note), color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(12.dp))
    Label(stringResource(R.string.mp_game_speed))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(stringResource(R.string.mp_speed_relaxed), stringResource(R.string.mp_speed_normal), stringResource(R.string.mp_speed_fast)).forEachIndexed { i, s ->
            ChoiceChip(s, room.speed == i, { update { it.copy(speed = i) } }, Modifier.weight(1f))
        }
    }
}

/** Lobby options for a Teen Patti room: table size, bots, sideshow / A-2-3, turn timer and speed. */
@Composable
private fun TeenPattiRoomOptions(room: RoomState, update: ((RoomState) -> RoomState) -> Unit) {
    val setup = room.teenPatti
    fun tp(t: (TeenPattiSetup) -> TeenPattiSetup) = update { it.copy(teenPatti = t(it.teenPatti)) }
    TableSeats(room, TeenPattiEngine.MIN_PLAYERS..TeenPattiEngine.MAX_PLAYERS, update) { r, n -> r.copy(teenPatti = r.teenPatti.copy(players = n)) }
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.mp_match))
        Spacer(Modifier.height(6.dp))
        ToggleRow(stringResource(R.string.tp_sideshow), stringResource(R.string.tp_sideshow_desc), setup.sideshow) { v -> tp { it.copy(sideshow = v) } }
        ToggleRow(stringResource(R.string.tp_a23), stringResource(R.string.tp_a23_desc), setup.a23Second) { v -> tp { it.copy(a23Second = v) } }
        Spacer(Modifier.height(12.dp))
        TimerAndSpeed(room, setup.turnTimer, { v -> tp { it.copy(turnTimer = v) } }, update, R.string.mp_timer_note_tp)
    }
}

/** Lobby options for a Rummy room: table size, bots, turn timer and speed. */
@Composable
private fun RummyRoomOptions(room: RoomState, update: ((RoomState) -> RoomState) -> Unit) {
    val setup = room.rummy
    fun rm(t: (RummySetup) -> RummySetup) = update { it.copy(rummy = t(it.rummy)) }
    TableSeats(room, RummyEngine.MIN_PLAYERS..RummyEngine.MAX_PLAYERS, update) { r, n -> r.copy(rummy = r.rummy.copy(players = n)) }
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.mp_match))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.rm_table_note, RummyEngine.START_CHIPS, RummyEngine.POINT_VALUE), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        TimerAndSpeed(room, setup.turnTimer, { v -> rm { it.copy(turnTimer = v) } }, update, R.string.mp_timer_note_rummy)
    }
}
