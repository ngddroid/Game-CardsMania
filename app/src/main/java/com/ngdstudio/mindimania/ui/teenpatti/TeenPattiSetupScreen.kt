package com.ngdstudio.mindimania.ui.teenpatti

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
import androidx.compose.material.icons.rounded.Info
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
import com.ngdstudio.mindimania.teenpatti.TPVariant
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiSetup
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoundIconButton
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.mindi.ToggleRow
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.launch

/** Teen Patti lobby + setup: hero header, continue banner, table options, deal. */
@Composable
fun TeenPattiSetupScreen(onBack: () -> Unit, onStart: (TeenPattiLaunch) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(TeenPattiSetup()) }
    var loaded by remember { mutableStateOf(false) }
    var hasSave by remember { mutableStateOf(false) }
    var showRanks by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        setup = app.profile.current().teenPatti.setup
        hasSave = TeenPattiViewModel.hasSave(ctx.applicationContext as android.app.Application)
        loaded = true
    }
    fun start(l: TeenPattiLaunch) {
        scope.launch {
            // always an endless table: hand after hand until the player leaves
            if (l.setup != null) app.profile.update { it.copy(teenPatti = it.teenPatti.copy(setup = setup.copy(hands = 0))) }
            onStart(l)
        }
    }

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitleBar(stringResource(R.string.tp_title), onBack) {
                RoundIconButton(Icons.Rounded.Info, { showRanks = true }, size = 44.dp, contentDescription = stringResource(R.string.tp_cd_rankings))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TeenPattiHero()
                if (hasSave) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xCC0A8A55), Color(0xAA044D2F))))
                            .border(1.5.dp, CM.Gold.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .clickable { app.sound.play(Sfx.SELECT); start(TeenPattiLaunch(resume = true)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tp_continue_title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(stringResource(R.string.tp_continue_sub), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tp_section_players))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (TeenPattiEngine.MIN_PLAYERS..TeenPattiEngine.MAX_PLAYERS).forEach { n ->
                            ChoiceChip("$n", setup.players == n, { setup = setup.copy(players = n) }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tp_chips_note, TeenPattiEngine.START_CHIPS, TeenPattiEngine.BOOT), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tp_section_variant))
                    Spacer(Modifier.height(10.dp))
                    // 3 per row so longer names ("Low Joker") fit
                    TPVariant.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { v ->
                                ChoiceChip(
                                    stringResource(variantNameRes(v)), setup.variant == v, { setup = setup.copy(variant = v) }, Modifier.weight(1f),
                                    color = if (v == TPVariant.CLASSIC || v == TPVariant.MIXED) CM.Gold else Color(0xFFFF8A4C),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(variantDescRes(setup.variant)), color = CM.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tp_section_rules))
                    Spacer(Modifier.height(6.dp))
                    ToggleRow(stringResource(R.string.tp_sideshow), stringResource(R.string.tp_sideshow_desc), setup.sideshow) { setup = setup.copy(sideshow = it) }
                    ToggleRow(stringResource(R.string.tp_a23), stringResource(R.string.tp_a23_desc), setup.a23Second) { setup = setup.copy(a23Second = it) }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tp_section_table))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.tp_bots), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    stringResource(R.string.tp_deal_cards), { start(TeenPattiLaunch(setup = setup.copy(hands = 0))) }, style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow,
                    height = 60.dp, fontSize = 21.sp, modifier = Modifier.fillMaxWidth(), sound = Sfx.SHUFFLE, enabled = loaded,
                )
            }
        }
        if (showRanks) HandRankings(setup.a23Second, onClose = { showRanks = false })
    }
}

@Composable
private fun TeenPattiHero() {
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE0245E), Color(0xFF4A0E5C))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // a trail of aces: the best hand there is
        Box(Modifier.offset(y = (-26).dp).size(220.dp, 110.dp), contentAlignment = Alignment.Center) {
            listOf(1 to 1, 1 to 0, 1 to 2).forEachIndexed { i, (rank, suit) ->
                Image(
                    PlayingCardArt.face(rank, suit), null,
                    Modifier.size(if (i == 1) 66.dp else 58.dp, if (i == 1) 92.dp else 81.dp)
                        .offset(x = ((i - 1) * 42).dp, y = (kotlin.math.abs(i - 1) * 6).dp)
                        .graphicsLayer { rotationZ = (i - 1) * 13f }
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle(stringResource(R.string.tp_hero_title), fontSize = 24.sp)
            Text(stringResource(R.string.tp_hero_sub), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Game-picker tile for Teen Patti (wide), used in Wi-Fi rooms: a trail of aces. */
@Composable
internal fun TeenPattiGameTile(selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .graphicsLayer { val k = if (selected) 1.02f else 0.98f; scaleX = k; scaleY = k; alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFE0245E).copy(alpha = if (selected) 0.95f else 0.45f), Color(0xFF4A0E5C).copy(alpha = if (selected) 0.95f else 0.45f))))
            .border(if (selected) 3.dp else 1.dp, if (selected) CM.GoldLight else Color(0x40FFFFFF), shape)
            .clickable { app.sound.play(if (enabled) Sfx.SELECT else Sfx.ERROR); if (enabled) onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) {
            listOf(1 to 1, 1 to 0, 1 to 2).forEachIndexed { i, (rank, suit) ->
                Image(
                    PlayingCardArt.face(rank, suit), null,
                    Modifier.size(36.dp, 50.dp).offset(x = ((i - 1) * 22).dp, y = (kotlin.math.abs(i - 1) * 3).dp).graphicsLayer { rotationZ = (i - 1) * 12f }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.shell_game_teenpatti), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(stringResource(R.string.shell_blurb_teenpatti), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
