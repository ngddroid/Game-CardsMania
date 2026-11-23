package com.ngdstudio.mindimania.ui.game

import com.ngdstudio.mindimania.ui.design.GameIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.AvatarRef
import com.ngdstudio.mindimania.data.GameFactory
import com.ngdstudio.mindimania.data.Leveling
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.engine.model.ColorReason
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.Side
import com.ngdstudio.mindimania.engine.model.Suit
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GemSwitch
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.rememberPulse
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.render.SuitSymbols
import com.ngdstudio.mindimania.ui.theme.CM
import com.ngdstudio.mindimania.ui.design.JewelPanel
import com.ngdstudio.mindimania.ui.design.PanelFill
import com.ngdstudio.mindimania.ui.design.Rim
import com.ngdstudio.mindimania.ui.theme.Gems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun Scrim(alpha: Float = 0.72f, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A0E5C).copy(alpha = alpha * 0.8f), Color.Black.copy(alpha = alpha))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) { content() }
}

// ------------------------------------------------------------------------------------------- color picker

@Composable
fun ColorPicker(side: Side, reason: ColorReason?, onPick: (Suit) -> Unit) {
    val suits = Suit.forSide(side)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.55f, 260f)) }
    val pulse by rememberPulse(900)
    Scrim(0.6f) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle(
                when (reason) {
                    ColorReason.STORM -> stringResource(R.string.mania_color_name_title)
                    else -> stringResource(R.string.mania_color_choose_title)
                },
                fontSize = 30.sp,
            )
            if (reason == ColorReason.STORM) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.mania_color_storm_desc),
                    color = CM.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                // center prism
                Canvas(Modifier.size(70.dp).graphicsLayer { rotationZ = pulse * 20f }) {
                    val c = center
                    val r = size.minDimension / 2
                    val cols = suits.map { Gems.color(it) }
                    val pts = listOf(Offset(c.x, c.y - r), Offset(c.x + r * 0.8f, c.y), Offset(c.x, c.y + r), Offset(c.x - r * 0.8f, c.y))
                    for (i in 0 until 4) {
                        val p = Path().apply { moveTo(c.x, c.y); lineTo(pts[i].x, pts[i].y); lineTo(pts[(i + 1) % 4].x, pts[(i + 1) % 4].y); close() }
                        drawPath(p, cols[i])
                    }
                }
                val offs = listOf(Offset(0f, -1f), Offset(1f, 0f), Offset(0f, 1f), Offset(-1f, 0f))
                suits.forEachIndexed { i, suit ->
                    val o = offs[i]
                    val d = 92.dp * appear.value
                    Box(
                        Modifier.offset(x = d * o.x, y = d * o.y).scale(appear.value),
                        contentAlignment = Alignment.Center
                    ) { GemButton(suit) { onPick(suit) } }
                }
            }
        }
    }
}

@Composable
private fun GemButton(suit: Suit, onClick: () -> Unit) {
    val tone = Gems.tone(suit)
    var pressed by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).scale(if (pressed) 0.9f else 1f)
                .clickable { pressed = true; onClick() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2 * 0.92f
                drawCircle(Brush.radialGradient(listOf(tone.base.copy(alpha = 0.55f), Color.Transparent)), radius = r * 1.15f)
                val p = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r * 0.86f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.86f, c.y); close() }
                drawPath(p, Brush.linearGradient(listOf(tone.light, tone.base, tone.dark), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
                val hl = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r * 0.86f, c.y); lineTo(c.x, c.y); close() }
                drawPath(hl, Color.White.copy(alpha = 0.22f))
                val sh = Path().apply { moveTo(c.x, c.y + r); lineTo(c.x - r * 0.86f, c.y); lineTo(c.x, c.y); close() }
                drawPath(sh, Color.Black.copy(alpha = 0.18f))
                drawPath(p, CM.GoldLight, style = Stroke(3.dp.toPx()))
                drawIntoCanvas { SuitSymbols.draw(it.nativeCanvas, suit, c.x, c.y, r * 0.3f, android.graphics.Color.WHITE, 0x66000000) }
            }
        }
        Text(Gems.name(suit).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

// ------------------------------------------------------------------------------------------- challenge

@Composable
fun ChallengeDialog(offender: String, amount: Int, onChallenge: () -> Unit, onAccept: () -> Unit) {
    Scrim {
        JewelPanel(Modifier.padding(horizontal = 28.dp).fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.ROYAL, padding = 20.dp) {
            OutlinedTitle(stringResource(R.string.mania_challenge_title, amount), fontSize = 30.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.mania_challenge_body, offender, amount, amount + 2),
                color = CM.Text, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(stringResource(R.string.mania_challenge_accept, amount), onAccept, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 15.sp)
                GameButton(stringResource(R.string.mania_challenge_button), onChallenge, style = ButtonStyle.RED, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 15.sp)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- pause

@Composable
fun PauseMenu(ui: GameUi, net: com.ngdstudio.mindimania.net.NetRole = com.ngdstudio.mindimania.net.NetRole.LOCAL, onResume: () -> Unit, onHowTo: () -> Unit, onQuit: () -> Unit) {
    val isHost = net == com.ngdstudio.mindimania.net.NetRole.HOST
    val isNet = net != com.ngdstudio.mindimania.net.NetRole.LOCAL
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var confirmQuit by remember { mutableStateOf(false) }
    Scrim(0.8f) {
        JewelPanel(Modifier.padding(horizontal = 32.dp).fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.ROYAL, padding = 20.dp) {
            OutlinedTitle(if (confirmQuit) (if (isHost) stringResource(R.string.mania_pause_end_q) else stringResource(R.string.mania_pause_leave_q)) else if (isNet) stringResource(R.string.mania_pause_menu) else stringResource(R.string.mania_pause_paused), fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            if (confirmQuit) {
                Text(
                    when {
                        isHost -> stringResource(R.string.mania_pause_host_warn)
                        isNet -> stringResource(R.string.mania_pause_net_warn)
                        else -> stringResource(R.string.mania_pause_saved)
                    }, color = CM.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                GameButton(stringResource(R.string.mania_leave), onQuit, style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = GameIcons.Lobby)
                Spacer(Modifier.height(10.dp))
                GameButton(stringResource(R.string.mania_stay), {
                    confirmQuit = false
                    onResume()
                }, style = ButtonStyle.GLASS, modifier = Modifier.fillMaxWidth())
            } else {
                if (isNet) {
                    Text(stringResource(R.string.mania_pause_net_note), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }
                GameButton(if (isNet) stringResource(R.string.mania_back_to_game) else stringResource(R.string.mania_resume), onResume, style = ButtonStyle.GREEN, modifier = Modifier.fillMaxWidth(), icon = GameIcons.Play)
                Spacer(Modifier.height(10.dp))
                GameButton(stringResource(R.string.mania_how_to_play), onHowTo, style = ButtonStyle.BLUE, modifier = Modifier.fillMaxWidth(), icon = GameIcons.Book)
                Spacer(Modifier.height(14.dp))
                ToggleRow(stringResource(R.string.mania_music), ui.settings.musicVolume > 0.01f) { on -> scope.launch { app.settings.update { it.copy(musicVolume = if (on) 0.6f else 0f) } } }
                ToggleRow(stringResource(R.string.mania_sfx), ui.settings.sfxVolume > 0.01f) { on -> scope.launch { app.settings.update { it.copy(sfxVolume = if (on) 0.9f else 0f) } } }
                ToggleRow(stringResource(R.string.mania_haptics), ui.settings.haptics) { on -> scope.launch { app.settings.update { it.copy(haptics = on) } } }
                Spacer(Modifier.height(14.dp))
                GameButton(if (isHost) stringResource(R.string.mania_end_game) else if (isNet) stringResource(R.string.mania_leave_game) else stringResource(R.string.mania_quit_home), {
                    if (ui.settings.confirmExit || isNet) confirmQuit = true else onQuit()
                }, style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = GameIcons.Lobby)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CM.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        GemSwitch(on, onChange)
    }
}

// ------------------------------------------------------------------------------------------- round result

@Composable
fun RoundResultOverlay(r: ResultInfo.Round, s: GameState, human: Int, onNext: (() -> Unit)?) {
    val won = r.winnerId == human
    Scrim(0.8f) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTitle(if (won) stringResource(R.string.mania_round_won) else stringResource(R.string.mania_round_winner, s.players[r.winnerId].name), fontSize = if (won) 38.sp else 26.sp)
            Spacer(Modifier.height(6.dp))
            Text(pluralStringResource(R.plurals.mania_round_points, r.points, r.points), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                val order = r.scores.sortedByDescending { it.second }
                order.forEachIndexed { i, (id, score) ->
                    val p = s.players[id]
                    val hand = r.hands.firstOrNull { it.first == id }?.second ?: emptyList()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(24.dp))
                        AvatarBadge(AvatarRef.emoji(p.setup.avatar), size = 38.dp, ringColor = AvatarRef.color(p.setup.avatar))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (id == human) stringResource(R.string.mania_name_you, p.name) else p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (p.eliminated) stringResource(R.string.mania_overloaded) else pluralStringResource(R.plurals.mania_cards_left, hand.size, hand.size), color = CM.TextDim, fontSize = 11.sp)
                        }
                        Text("$score", color = if (id == r.winnerId) CM.Emerald else Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.mania_first_to_wins, r.target), color = CM.TextMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(20.dp))
            if (onNext != null) {
                GameButton(stringResource(R.string.mania_next_round), onNext, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), icon = GameIcons.Play)
            } else {
                WaitingForHost()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- match result

@Composable
fun MatchResultOverlay(
    r: ResultInfo.Match,
    s: GameState,
    human: Int,
    profile: Profile,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
    onRoom: (() -> Unit)? = null,
) {
    val app = LocalApp.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.5f, 180f)) }
    var coins by remember { mutableIntStateOf(0) }
    val reward = r.reward
    LaunchedEffect(reward) {
        if (reward != null && reward.coins > 0) {
            delay(700)
            val steps = 24
            for (i in 1..steps) {
                coins = reward.coins * i / steps
                if (i % 3 == 0) app.sound.play(Sfx.COIN, 0.5f)
                delay(40)
            }
        }
    }
    val inf = rememberInfiniteTransition(label = "rays")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "r")
    Scrim(0.85f) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(rot)) {
                    val c = center
                    for (i in 0 until 12) {
                        val a0 = Math.toRadians(i * 30.0)
                        val a1 = Math.toRadians(i * 30.0 + 14)
                        val p = Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + kotlin.math.cos(a0).toFloat() * size.width, c.y + kotlin.math.sin(a0).toFloat() * size.width)
                            lineTo(c.x + kotlin.math.cos(a1).toFloat() * size.width, c.y + kotlin.math.sin(a1).toFloat() * size.width)
                            close()
                        }
                        drawPath(p, Brush.radialGradient(listOf((if (r.humanWon) CM.Gold else CM.Violet).copy(alpha = 0.35f), Color.Transparent), c, size.width / 2))
                    }
                }
                Box(Modifier.scale(appear.value)) {
                    if (r.humanWon) Trophy() else AvatarBadge(AvatarRef.emoji(s.players[r.winnerId].setup.avatar), size = 110.dp, ringColor = AvatarRef.color(s.players[r.winnerId].setup.avatar))
                }
            }
            OutlinedTitle(
                if (r.humanWon) stringResource(R.string.mania_victory) else stringResource(R.string.mania_player_wins, s.players[r.winnerId].name),
                fontSize = if (r.humanWon) 48.sp else 32.sp,
                modifier = Modifier.scale(appear.value)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    r.humanWon && r.lastStanding -> stringResource(R.string.mania_last_standing)
                    r.humanWon -> stringResource(R.string.mania_emptied_first)
                    else -> stringResource(R.string.mania_finished_place, r.place)
                },
                color = CM.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            if (r.daily != null && reward != null) {
                Spacer(Modifier.height(10.dp))
                Row {
                    repeat(3) { i ->
                        Icon(
                            GameIcons.Star, null,
                            tint = if (i < reward.dailyStars) CM.Gold else Color(0x33FFFFFF),
                            modifier = Modifier.size(40.dp).scale(if (i < reward.dailyStars) appear.value else 1f)
                        )
                    }
                }
                val goals = GameFactory.daily(r.daily).goals
                goals.forEachIndexed { i, g ->
                    Text((if (i < reward.dailyStars) "★ " else "☆ ") + g, color = if (i < reward.dailyStars) CM.GoldLight else CM.TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoinIcon(26.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("+$coins", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        }
                        Text(stringResource(R.string.mania_coins), color = CM.TextDim, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+${reward?.xp ?: 0}", color = Color(0xFFC0A6FF), fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text("XP", color = CM.TextDim, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.mania_level_short, profile.level), color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        val (prog, _, _) = Leveling.progress(profile.xp)
                        Box(Modifier.width(70.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(prog.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(CM.Violet, CM.Rose))))
                        }
                    }
                }
                if (reward != null && reward.levelAfter > reward.levelBefore) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.mania_level_up, reward.levelAfter), color = CM.Emerald, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                reward?.newAchievements?.forEach { a ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x22FFC93C)).border(1.dp, Color(0x55FFC93C), RoundedCornerShape(14.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(a.icon, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.mania_achievement, a.title), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text(a.description, color = CM.TextDim, fontSize = 11.sp)
                        }
                        Text("+${a.reward}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassPanel(Modifier.fillMaxWidth(), padding = 12.dp) {
                r.scores.forEachIndexed { i, (id, score) ->
                    val p = s.players[id]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = CM.GoldLight, fontWeight = FontWeight.Black, modifier = Modifier.width(22.dp))
                        Text(AvatarRef.emoji(p.setup.avatar), fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (id == human) stringResource(R.string.mania_name_you, p.name) else p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (id == r.winnerId) stringResource(R.string.mania_win) else pluralStringResource(R.plurals.mania_cards, p.hand.size, p.hand.size), color = if (id == r.winnerId) CM.Emerald else CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        if (s.config.targetScore > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text("$score", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(if (onPlayAgain == null) stringResource(R.string.mania_leave) else stringResource(R.string.mania_home), onHome, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), icon = GameIcons.Lobby)
                if (onRoom != null) GameButton(stringResource(R.string.mania_room), onRoom, style = ButtonStyle.BLUE, modifier = Modifier.weight(1f))
                if (r.daily == null && onPlayAgain != null) GameButton(if (onRoom != null) stringResource(R.string.mania_rematch) else stringResource(R.string.mania_play_again), onPlayAgain, style = ButtonStyle.GOLD, modifier = Modifier.weight(1.4f), icon = GameIcons.Refresh)
            }
        }
    }
}

@Composable
private fun WaitingForHost() {
    val pulse by rememberPulse(700)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.mania_waiting_host), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = 0.6f + 0.4f * pulse })
    }
}

@Composable
fun EndedDialog(reason: String, onOk: () -> Unit) {
    Scrim(0.85f) {
        JewelPanel(Modifier.padding(horizontal = 32.dp).fillMaxWidth(), rim = Rim.RUBY, fill = PanelFill.NIGHT, padding = 20.dp) {
            OutlinedTitle(stringResource(R.string.mania_game_over), fontSize = 30.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Text(localizedEndReason(reason), color = CM.Text, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            GameButton(stringResource(R.string.mania_ok), onOk, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), icon = GameIcons.Lobby)
        }
    }
}

/** End reasons arrive over the network in English (protocol text); show the known ones in the player's language. */
@Composable
private fun localizedEndReason(reason: String): String = when (reason) {
    "The host left the game." -> stringResource(R.string.mania_end_host_left)
    "The host closed the room." -> stringResource(R.string.mania_end_host_closed)
    "You were removed from the room." -> stringResource(R.string.mania_end_removed)
    "Lost connection to the host." -> stringResource(R.string.mania_end_lost_connection)
    "The room is closed." -> stringResource(R.string.mania_end_room_closed)
    "You're not in a room any more." -> stringResource(R.string.mania_end_not_in_room)
    else -> reason
}

@Composable
private fun Trophy() {
    val pulse by rememberPulse(1100)
    Canvas(Modifier.size(120.dp).graphicsLayer { scaleX = 1f + pulse * 0.04f; scaleY = 1f + pulse * 0.04f }) {
        val w = size.width
        val h = size.height
        val gold = Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFC93C), Color(0xFFC98A00)))
        // cup
        val cup = Path().apply {
            moveTo(w * 0.22f, h * 0.12f); lineTo(w * 0.78f, h * 0.12f)
            cubicTo(w * 0.78f, h * 0.5f, w * 0.64f, h * 0.6f, w * 0.5f, h * 0.62f)
            cubicTo(w * 0.36f, h * 0.6f, w * 0.22f, h * 0.5f, w * 0.22f, h * 0.12f); close()
        }
        drawPath(cup, gold)
        drawPath(cup, Color(0xFF7A4E00), style = Stroke(3.dp.toPx()))
        // handles
        drawArc(Color(0xFFE09A00), 90f, 180f, false, Offset(w * 0.08f, h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.24f), style = Stroke(6.dp.toPx()))
        drawArc(Color(0xFFE09A00), -90f, 180f, false, Offset(w * 0.72f, h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.24f), style = Stroke(6.dp.toPx()))
        // stem and base
        drawRect(gold, Offset(w * 0.45f, h * 0.6f), androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.16f))
        drawRoundRect(gold, Offset(w * 0.28f, h * 0.76f), androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.12f), androidx.compose.ui.geometry.CornerRadius(8f))
        // gem
        val g = Path().apply { moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.6f, h * 0.32f); lineTo(w * 0.5f, h * 0.44f); lineTo(w * 0.4f, h * 0.32f); close() }
        drawPath(g, Brush.linearGradient(listOf(Color(0xFFFF6F95), CM.Ruby, Color(0xFF9E0F3C))))
        drawPath(g, Color.White, style = Stroke(1.5.dp.toPx()))
        // shine
        drawLine(Color.White.copy(alpha = 0.6f), Offset(w * 0.3f, h * 0.18f), Offset(w * 0.34f, h * 0.42f), 4.dp.toPx())
    }
}

@Suppress("unused")
@Composable
private fun CardPreview(face: com.ngdstudio.mindimania.engine.model.Face) {
    Image(CardArt.face(face), null, Modifier.width(60.dp).height(84.dp))
}

@Suppress("unused")
private fun circle() = CircleShape
