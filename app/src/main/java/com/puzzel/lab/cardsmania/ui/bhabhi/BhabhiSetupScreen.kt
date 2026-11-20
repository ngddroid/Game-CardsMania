package com.puzzel.lab.cardsmania.ui.bhabhi

import androidx.compose.foundation.Image
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
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.bhabhi.BhabhiSetup
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.mindi.ToggleRow
import com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.launch

/** Bhabhi lobby + setup: hero header, continue banner, table options, deal. */
@Composable
fun BhabhiSetupScreen(onBack: () -> Unit, onStart: (BhabhiLaunch) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(BhabhiSetup()) }
    var loaded by remember { mutableStateOf(false) }
    var hasSave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        setup = app.profile.current().bhabhi.setup
        hasSave = BhabhiViewModel.hasSave(ctx.applicationContext as android.app.Application)
        loaded = true
    }
    fun start(l: BhabhiLaunch) {
        scope.launch {
            if (l.setup != null) app.profile.update { it.copy(bhabhi = it.bhabhi.copy(setup = setup)) }
            onStart(l)
        }
    }

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitleBar(stringResource(R.string.bhabhi_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BhabhiHero()
                if (hasSave) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xCC0A8A55), Color(0xAA044D2F))))
                            .border(1.5.dp, CM.Gold.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .clickable { app.sound.play(Sfx.SELECT); start(BhabhiLaunch(resume = true)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.bhabhi_continue_title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(stringResource(R.string.bhabhi_continue_sub), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.bhabhi_section_players))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (3..8).forEach { n ->
                            ChoiceChip("$n", setup.players == n, { setup = setup.copy(players = n) }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(tableLine(setup.players), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.bhabhi_section_rules))
                    Spacer(Modifier.height(6.dp))
                    ToggleRow(
                        stringResource(R.string.bhabhi_safe_first), stringResource(R.string.bhabhi_safe_first_desc),
                        setup.safeFirstTrick,
                    ) { setup = setup.copy(safeFirstTrick = it) }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.bhabhi_section_table))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.bhabhi_bots), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.bhabhi_turn_timer), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 10, 20, 30).forEach { t ->
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
                    stringResource(R.string.bhabhi_deal_cards), { start(BhabhiLaunch(setup = setup)) }, style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow,
                    height = 60.dp, fontSize = 21.sp, modifier = Modifier.fillMaxWidth(), sound = Sfx.SHUFFLE, enabled = loaded,
                )
            }
        }
    }
}

@Composable
private fun BhabhiHero() {
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFB0206A), Color(0xFF3A0B4D))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // the A♠ that opens every game, with a hand fanned behind it
        Box(Modifier.offset(y = (-26).dp).size(220.dp, 110.dp), contentAlignment = Alignment.Center) {
            listOf(13 to 1, 7 to 3, 1 to 0, 9 to 2, 12 to 1).forEachIndexed { i, (rank, suit) ->
                Image(
                    PlayingCardArt.face(rank, suit), null,
                    Modifier.size(if (i == 2) 64.dp else 54.dp, if (i == 2) 90.dp else 76.dp)
                        .offset(x = ((i - 2) * 30).dp, y = (kotlin.math.abs(i - 2) * 6).dp)
                        .graphicsLayer { rotationZ = (i - 2) * 11f }
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle(stringResource(R.string.bhabhi_hero_title), fontSize = 24.sp)
            Text(stringResource(R.string.bhabhi_hero_sub), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Game-picker tile for Bhabhi (wide), used in Wi-Fi rooms: the A♠ that opens every game, with a fanned hand. */
@Composable
internal fun BhabhiGameTile(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .graphicsLayer { val k = if (selected) 1.02f else 0.98f; scaleX = k; scaleY = k }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFB0206A).copy(alpha = if (selected) 0.95f else 0.45f), Color(0xFF3A0B4D).copy(alpha = if (selected) 0.95f else 0.45f))))
            .border(if (selected) 3.dp else 1.dp, if (selected) CM.GoldLight else Color(0x40FFFFFF), shape)
            .clickable { app.sound.play(Sfx.SELECT); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) {
            listOf(13 to 1, 7 to 3, 9 to 2, 1 to 0).forEachIndexed { i, (rank, suit) ->
                Image(
                    PlayingCardArt.face(rank, suit), null,
                    Modifier.size(36.dp, 50.dp)
                        .offset(x = ((i - 1.5f) * 18).dp, y = (kotlin.math.abs(i - 1.5f) * 3).dp)
                        .graphicsLayer { rotationZ = (i - 1.5f) * 12f }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.bhabhi_tile_title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(stringResource(R.string.shell_blurb_bhabhi), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** One-line description of a table size: how the 52 cards split. */
internal fun tableLine(players: Int): String {
    val base = 52 / players
    val extra = 52 % players
    return if (extra == 0) Strings.get(R.string.bhabhi_table_even, players, base)
    else Strings.get(R.string.bhabhi_table_uneven, players, base, base + 1)
}
