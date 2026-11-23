package com.ngdstudio.mindimania.ui.rummy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.DifficultyInfo
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.rummy.RCard
import com.ngdstudio.mindimania.rummy.RummyEngine
import com.ngdstudio.mindimania.rummy.RummySetup
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.render.CardBack
import kotlinx.coroutines.launch

/** Rummy lobby + setup: hero header, continue banner, players, bots, turn timer, deal. */
@Composable
fun RummySetupScreen(onBack: () -> Unit, onStart: (RummyLaunch) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(RummySetup()) }
    var loaded by remember { mutableStateOf(false) }
    var hasSave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        setup = app.profile.current().rummy.setup
        hasSave = RummyViewModel.hasSave(ctx.applicationContext as android.app.Application)
        loaded = true
    }
    fun start(l: RummyLaunch) {
        scope.launch {
            if (l.setup != null) app.profile.update { it.copy(rummy = it.rummy.copy(setup = setup)) }
            onStart(l)
        }
    }
    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitleBar(stringResource(R.string.rm_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RummyHero()
                if (hasSave) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xCC0A8A55), Color(0xAA044D2F))))
                            .border(1.5.dp, Color(0xB3FFC93C), RoundedCornerShape(20.dp))
                            .clickable { app.sound.play(Sfx.SELECT); start(RummyLaunch(resume = true)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.rm_continue_title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(stringResource(R.string.rm_continue_sub), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tp_section_players))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (RummyEngine.MIN_PLAYERS..RummyEngine.MAX_PLAYERS).forEach { n ->
                            ChoiceChip("$n", setup.players == n, { setup = setup.copy(players = n) }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.rm_table_note, RummyEngine.START_CHIPS, RummyEngine.POINT_VALUE), color = Color(0xFFD9D2F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tp_section_table))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.tp_bots), color = Color(0xFFD9D2F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.entries.forEach { d ->
                            ChoiceChip(
                                if (d == Difficulty.GENIUS) stringResource(R.string.bhabhi_expert) else DifficultyInfo.label(d), setup.difficulty == d,
                                { setup = setup.copy(difficulty = d) }, Modifier.weight(1f), color = DifficultyInfo.color(d),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.bhabhi_turn_timer), color = Color(0xFFD9D2F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 20, 30, 45).forEach { t ->
                            ChoiceChip(if (t == 0) stringResource(R.string.bhabhi_off) else stringResource(R.string.bhabhi_seconds, t), setup.turnTimer == t, { setup = setup.copy(turnTimer = t) }, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0320))))
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GameButton(
                    stringResource(R.string.tp_deal_cards), { start(RummyLaunch(setup = setup)) }, style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow,
                    height = 60.dp, fontSize = 21.sp, modifier = Modifier.fillMaxWidth(), sound = Sfx.SHUFFLE, enabled = loaded,
                )
            }
        }
    }
}

@Composable
private fun RummyHero() {
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C9466), Color(0xFF0B3B2B))))
            .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFB36B00))), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // a pure sequence with a joker beside it
        Box(Modifier.offset(y = (-26).dp).size(240.dp, 110.dp), contentAlignment = Alignment.Center) {
            val cards = listOf(RCard(0, 5, 1), RCard(1, 6, 1), RCard(2, 7, 1), RCard(3, 0, -1))
            cards.forEachIndexed { i, c ->
                Box(Modifier.offset(x = ((i - 1.5f) * 40).dp, y = (kotlin.math.abs(i - 1.5f) * 5).dp).graphicsLayer { rotationZ = (i - 1.5f) * 9f }) {
                    RummyCardView(c, 58.dp, true, 99, CardBack.ROYAL)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle(stringResource(R.string.rm_hero_title), fontSize = 24.sp)
            Text(stringResource(R.string.rm_hero_sub), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}


/** Game-picker tile for Rummy (wide), used in Wi-Fi rooms: a pure sequence and a joker. */
@Composable
internal fun RummyGameTile(selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .graphicsLayer { val k = if (selected) 1.02f else 0.98f; scaleX = k; scaleY = k; alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1C9466).copy(alpha = if (selected) 0.95f else 0.45f), Color(0xFF0B3B2B).copy(alpha = if (selected) 0.95f else 0.45f))))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFFFE27A) else Color(0x40FFFFFF), shape)
            .clickable { app.sound.play(if (enabled) Sfx.SELECT else Sfx.ERROR); if (enabled) onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) {
            listOf(RCard(0, 5, 1), RCard(1, 6, 1), RCard(2, 7, 1), RCard(3, 0, -1)).forEachIndexed { i, c ->
                Box(Modifier.offset(x = ((i - 1.5f) * 18).dp, y = (kotlin.math.abs(i - 1.5f) * 3).dp).graphicsLayer { rotationZ = (i - 1.5f) * 10f }) {
                    RummyCardView(c, 34.dp, true, 99, CardBack.ROYAL)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.shell_game_rummy), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(stringResource(R.string.shell_blurb_rummy), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
