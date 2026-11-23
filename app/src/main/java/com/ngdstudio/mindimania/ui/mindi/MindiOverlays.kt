package com.ngdstudio.mindimania.ui.mindi

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.data.AvatarRef
import com.ngdstudio.mindimania.mindi.HandKind
import com.ngdstudio.mindimania.mindi.HandResult
import com.ngdstudio.mindimania.mindi.MindiState
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.kit.StarIcon
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.theme.CM

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
fun LastTrickDialog(s: MindiState, ui: MindiUi, onClose: () -> Unit) {
    Scrim(0.75f, onTap = onClose) {
        GlassPanel(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), padding = 16.dp) {
            OutlinedTitle(stringResource(R.string.mindi_last_trick), fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            if (s.lastTrick.isEmpty()) {
                Text(stringResource(R.string.mindi_no_trick_yet), color = CM.TextDim, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    s.lastTrick.forEach { play ->
                        val win = play.seat == s.lastWinner
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                PlayingCardArt.face(play.card.artRank, play.card.suit), null,
                                Modifier.width(if (s.players > 4) 40.dp else 56.dp).height(if (s.players > 4) 56.dp else 78.dp)
                                    .graphicsLayer { scaleX = if (win) 1.08f else 1f; scaleY = if (win) 1.08f else 1f }
                                    .border(if (win) 2.dp else 0.dp, if (win) CM.Gold else Color.Transparent, RoundedCornerShape(6.dp))
                            )
                            Text(
                                ui.seats.getOrNull(play.seat)?.name?.take(8) ?: "", color = if (s.team(play.seat) == s.team(ui.me)) TeamBlue else TeamRed,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            )
                            if (play.trump) Text(stringResource(R.string.mindi_trump_label), color = Color(0xFFFF8A8A), fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mindi_won_it, ui.seats.getOrNull(s.lastWinner)?.name ?: ""), color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Spacer(Modifier.height(12.dp))
            GameButton(stringResource(R.string.mindi_close), onClose, style = ButtonStyle.GLASS, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 15.sp)
        }
    }
}

@Composable
private fun kindLabel(k: HandKind) = when (k) {
    HandKind.NORMAL -> stringResource(R.string.mindi_kind_normal)
    HandKind.MENDIKOT -> stringResource(R.string.mindi_kind_mendikot)
    HandKind.WHITEWASH -> stringResource(R.string.mindi_kind_whitewash)
}

@Composable
fun HandResultOverlay(s: MindiState, ui: MindiUi, r: HandResult, onNext: (() -> Unit)?) {
    val myTeam = s.team(ui.me)
    val won = r.winnerTeam == myTeam
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.5f, 220f)) }
    Scrim(0.82f) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            OutlinedTitle(stringResource(if (won) R.string.mindi_your_team_wins else R.string.mindi_rivals_win), fontSize = if (won) 34.sp else 28.sp, modifier = Modifier.scale(pop.value))
            Text(pluralStringResource(R.plurals.mindi_result_points, r.points, kindLabel(r.kind), r.points), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            // podium: winning team in front with crowns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                listOf(r.winnerTeam, 1 - r.winnerTeam).forEach { team ->
                    val color = if (team == myTeam) TeamBlue else TeamRed
                    val top = team == r.winnerTeam
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (top) Text("👑", fontSize = 30.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                            (0 until s.players).filter { s.team(it) == team }.forEach { seat ->
                                AvatarBadge(AvatarRef.emoji(ui.seats.getOrNull(seat)?.avatar ?: 0), size = if (top) 52.dp else 40.dp, ringColor = color)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.width(140.dp).height(if (top) 92.dp else 64.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.35f))))
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(if (team == myTeam) R.string.mindi_your_team else R.string.mindi_rivals), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                Text(stringResource(R.string.mindi_tens_tricks, r.tens[team], r.tricks[team]), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("★ ${s.scores[team]}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // captured 10s of the winning team
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.tens[r.winnerTeam].forEach { c ->
                    Image(PlayingCardArt.face(c.artRank, c.suit), null, Modifier.width(44.dp).height(62.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            // scoreboard
            GlassPanel(Modifier.fillMaxWidth(), padding = 12.dp) {
                Text(stringResource(R.string.mindi_sb_title), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf(R.string.mindi_sb_hand, R.string.mindi_sb_winner, R.string.mindi_sb_tens, R.string.mindi_sb_tricks, R.string.mindi_sb_pts)
                        .map { stringResource(it) }.forEachIndexed { i, h ->
                        Text(h, color = CM.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(if (i == 1) 1.5f else 1f), textAlign = TextAlign.Center)
                    }
                }
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                    s.history.reversed().forEach { h ->
                        val mine = h.winnerTeam == myTeam
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${h.hand}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text(
                                stringResource(if (mine) R.string.mindi_sb_you else R.string.mindi_sb_rivals) + when (h.kind) { HandKind.MENDIKOT -> " ★"; HandKind.WHITEWASH -> " ★★"; else -> "" },
                                color = if (mine) TeamBlue else TeamRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center,
                            )
                            Text("${h.tens[myTeam]}–${h.tens[1 - myTeam]}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text("${h.tricks[myTeam]}–${h.tricks[1 - myTeam]}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text("+${h.points}", color = CM.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val nextDealer = com.ngdstudio.mindimania.mindi.MindiEngine.nextDealer(s, r)
                Text(
                    stringResource(R.string.mindi_next_dealer, ui.seats.getOrNull(nextDealer)?.name ?: "-"),
                    color = CM.TextDim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (onNext != null) {
                GameButton(stringResource(R.string.mindi_next_hand), onNext, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            } else {
                WaitingForHost()
            }
        }
    }
}

@Composable
fun MatchResultOverlay(s: MindiState, ui: MindiUi, mr: MindiWin, onHome: () -> Unit, onAgain: (() -> Unit)?, onRoom: (() -> Unit)? = null) {
    val myTeam = s.team(ui.me)
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.45f, 200f)) }
    Scrim(0.88f) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(if (mr.won) "🏆" else "🤝", fontSize = 72.sp, modifier = Modifier.scale(pop.value))
            OutlinedTitle(stringResource(if (mr.won) R.string.mindi_victory else R.string.mindi_good_game), fontSize = if (mr.won) 46.sp else 34.sp, modifier = Modifier.scale(pop.value))
            Text(
                stringResource(R.string.mindi_final_score, s.scores[myTeam], s.scores[1 - myTeam]), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp,
            )
            if (ui.daily != null) {
                Spacer(Modifier.height(8.dp))
                Row { repeat(3) { StarIcon(it < mr.stars, 36.dp) } }
                Text(stringResource(R.string.mindi_daily_stars_info), color = CM.TextDim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(26.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("+${mr.coins}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    }
                    Text(stringResource(R.string.mindi_xp, mr.xp), color = Color(0xFFC0A6FF), fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                mr.achievements.forEach { a ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFC93C)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(a.icon, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.mindi_achievement, a.title), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text(a.description, color = CM.TextDim, fontSize = 11.sp)
                        }
                        Text("+${a.reward}", color = CM.GoldLight, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(stringResource(if (onAgain == null) R.string.mindi_leave else R.string.mindi_home), onHome, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), icon = Icons.Rounded.Home)
                if (onRoom != null) GameButton(stringResource(R.string.mindi_room), onRoom, style = ButtonStyle.BLUE, modifier = Modifier.weight(1f))
                if (ui.daily == null && onAgain != null) GameButton(stringResource(if (onRoom != null) R.string.mindi_rematch else R.string.mindi_play_again), onAgain, style = ButtonStyle.GOLD, modifier = Modifier.weight(1.4f), icon = Icons.Rounded.Refresh)
            }
        }
    }
}

@Composable
fun MindiPauseMenu(
    net: com.ngdstudio.mindimania.net.NetRole = com.ngdstudio.mindimania.net.NetRole.LOCAL,
    onResume: () -> Unit,
    onHowTo: () -> Unit,
    onQuit: () -> Unit,
) {
    val isHost = net == com.ngdstudio.mindimania.net.NetRole.HOST
    val isNet = net != com.ngdstudio.mindimania.net.NetRole.LOCAL
    val confirmState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val confirm = confirmState.value
    Scrim(0.8f, onTap = onResume) {
        GlassPanel(Modifier.padding(horizontal = 32.dp).fillMaxWidth(), padding = 20.dp) {
            OutlinedTitle(
                stringResource(if (confirm) (if (isHost) R.string.mindi_end_game_q else R.string.mindi_leave_game_q) else if (isNet) R.string.mindi_menu else R.string.mindi_paused),
                fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            if (confirm) {
                Text(
                    stringResource(if (isHost) R.string.mindi_end_host_desc else R.string.mindi_leave_desc),
                    color = CM.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                GameButton(stringResource(R.string.mindi_leave), onQuit, style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Home)
                Spacer(Modifier.height(10.dp))
                GameButton(stringResource(R.string.mindi_stay), { confirmState.value = false; onResume() }, style = ButtonStyle.GLASS, modifier = Modifier.fillMaxWidth())
            } else {
                if (isNet) {
                    Text(stringResource(R.string.mindi_net_running), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }
                GameButton(stringResource(if (isNet) R.string.mindi_back_to_game else R.string.mindi_resume), onResume, style = ButtonStyle.GREEN, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                Spacer(Modifier.height(10.dp))
                GameButton(stringResource(R.string.mindi_how_to_play), onHowTo, style = ButtonStyle.BLUE, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.MenuBook)
                Spacer(Modifier.height(10.dp))
                if (!isNet) {
                    Text(stringResource(R.string.mindi_saved_note), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }
                GameButton(
                    stringResource(if (isHost) R.string.mindi_end_game else if (isNet) R.string.mindi_leave_game else R.string.mindi_quit_home),
                    { if (isNet) confirmState.value = true else onQuit() },
                    style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Home,
                )
            }
        }
    }
}

@Composable
private fun WaitingForHost() {
    val pulse by com.ngdstudio.mindimania.ui.components.rememberPulse(700)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.mindi_waiting_host), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = 0.6f + 0.4f * pulse })
    }
}
