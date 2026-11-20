package com.puzzel.lab.cardsmania.ui.mindi

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.mindi.MindiSetup
import com.puzzel.lab.cardsmania.mindi.MindiTrumpMode
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GemSwitch
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.kit.StarIcon
import com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Mindi lobby + setup: hero header, continue/daily banners, table options, start. */
@Composable
fun MindiSetupScreen(onBack: () -> Unit, onStart: (MindiLaunch) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(MindiSetup()) }
    var loaded by remember { mutableStateOf(false) }
    var hasSave by remember { mutableStateOf(false) }
    var dailyStars by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val p = app.profile.current()
        setup = p.mindi.setup
        dailyStars = p.mindi.daily[MindiViewModel.today()] ?: 0
        hasSave = MindiViewModel.hasSave(ctx.applicationContext as android.app.Application)
        loaded = true
    }
    fun start(l: MindiLaunch) {
        scope.launch {
            if (l.setup != null && l.daily == null) app.profile.update { it.copy(mindi = it.mindi.copy(setup = setup)) }
            onStart(l)
        }
    }

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitleBar(stringResource(R.string.mindi_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MindiHero()
                if (hasSave) {
                    BannerRow(stringResource(R.string.mindi_continue_title), stringResource(R.string.mindi_continue_desc), Brush.horizontalGradient(listOf(Color(0xCC0A8A55), Color(0xAA044D2F)))) {
                        start(MindiLaunch(resume = true))
                    }
                }
                BannerRow(
                    stringResource(R.string.mindi_daily_title),
                    if (dailyStars > 0) stringResource(R.string.mindi_daily_desc_best, "★".repeat(dailyStars)) else stringResource(R.string.mindi_daily_desc_new),
                    Brush.horizontalGradient(listOf(Color(0xCC8E0F3A), Color(0xCC2A0E5C))), icon = Icons.Rounded.CalendarMonth, stars = dailyStars,
                ) { start(MindiLaunch(setup = MindiViewModel.dailySetup(), daily = MindiViewModel.today())) }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.mindi_players))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(4, 6, 8).forEach { n ->
                            SeatOption(n, setup.players == n, Modifier.weight(1f)) {
                                setup = setup.copy(players = n, doubleDeck = setup.doubleDeck && n >= 6)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(mindiTableLine(setup), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (setup.players >= 6) {
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(stringResource(R.string.mindi_double_deck), stringResource(R.string.mindi_double_deck_desc), setup.doubleDeck) { setup = setup.copy(doubleDeck = it) }
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.mindi_trump_section))
                    Spacer(Modifier.height(10.dp))
                    MindiTrumpMode.entries.forEach { m ->
                        TrumpModeCard(m, setup.trumpMode == m) { setup = setup.copy(trumpMode = m) }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (setup.trumpMode == MindiTrumpMode.HIDE) {
                        ToggleRow(stringResource(R.string.mindi_must_trump), stringResource(R.string.mindi_must_trump_desc), setup.mustTrumpAfterReveal) {
                            setup = setup.copy(mustTrumpAfterReveal = it)
                        }
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.mindi_match))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.mindi_points_to_win), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 5, 7, 10).forEach { t ->
                            ChoiceChip(if (t == 1) stringResource(R.string.mindi_one_hand) else "$t", setup.target == t, { setup = setup.copy(target = t) }, Modifier.weight(1f))
                        }
                    }
                    Text(stringResource(R.string.mindi_points_info), color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.mindi_bots), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.entries.forEach { d ->
                            ChoiceChip(if (d == Difficulty.GENIUS) stringResource(R.string.mindi_expert) else DifficultyInfo.label(d), setup.difficulty == d, { setup = setup.copy(difficulty = d) }, Modifier.weight(1f), color = DifficultyInfo.color(d))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.mindi_turn_timer), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 10, 20, 30).forEach { t ->
                            ChoiceChip(if (t == 0) stringResource(R.string.mindi_timer_off) else stringResource(R.string.mindi_timer_secs, t), setup.turnTimer == t, { setup = setup.copy(turnTimer = t) }, Modifier.weight(1f))
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
                    stringResource(R.string.mindi_deal_cards), { start(MindiLaunch(setup = setup)) }, style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow,
                    height = 60.dp, fontSize = 21.sp, modifier = Modifier.fillMaxWidth(), sound = Sfx.SHUFFLE, enabled = loaded,
                )
            }
        }
    }
}

@Composable
private fun MindiHero() {
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E7A4A), Color(0xFF053A24))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // the four 10s fanned
        Box(Modifier.offset(y = (-14).dp).size(220.dp, 110.dp), contentAlignment = Alignment.Center) {
            listOf(0, 1, 3, 2).forEachIndexed { i, suit ->
                Image(
                    PlayingCardArt.face(10, suit), null,
                    Modifier.size(58.dp, 81.dp)
                        .offset(x = ((i - 1.5f) * 34).dp, y = (kotlin.math.abs(i - 1.5f) * 6).dp)
                        .graphicsLayer { rotationZ = (i - 1.5f) * 12f }
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle(stringResource(R.string.mindi_hero_title), fontSize = 24.sp)
            Text(stringResource(R.string.mindi_hero_desc), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BannerRow(title: String, subtitle: String, bg: Brush, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.PlayArrow, stars: Int = -1, onClick: () -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.5.dp, CM.Gold.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .clickable { app.sound.play(Sfx.SELECT); onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (stars >= 0) Row { repeat(3) { StarIcon(it < stars, 18.dp) } }
    }
}

/** A seat-count option drawn as a mini round table with team-coloured seats. */
@Composable
internal fun SeatOption(n: Int, selected: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val app = LocalApp.current
    Column(
        modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brush.verticalGradient(listOf(Color(0xFF1E8A4C), Color(0xFF0B4D2A))) else Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
            .border(if (selected) 2.dp else 1.dp, if (selected) CM.GoldLight else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable { app.sound.play(Sfx.SELECT); app.haptics.tick(); onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(64.dp)) {
            val c = center
            val r = size.minDimension * 0.30f
            drawCircle(Color(0xFF0B3D20), r)
            drawCircle(CM.Gold.copy(alpha = 0.7f), r, style = Stroke(2.dp.toPx()))
            for (i in 0 until n) {
                val a = Math.toRadians(90.0 + 360.0 * i / n)
                val p = Offset(c.x + (cos(a) * r * 1.45f).toFloat(), c.y + (sin(a) * r * 1.45f).toFloat())
                drawCircle(if (i % 2 == 0) TeamBlue else TeamRed, size.minDimension * 0.075f, p)
                drawCircle(Color.White, size.minDimension * 0.075f, p, style = Stroke(1.5.dp.toPx()))
            }
        }
        Text(pluralStringResource(R.plurals.mindi_n_players, n, n), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(when (n) { 4 -> 2; 6 -> 3; else -> 4 }.let { stringResource(R.string.mindi_vs, it, it) }, color = CM.TextDim, fontSize = 11.sp)
    }
}

@Composable
internal fun TrumpModeCard(m: MindiTrumpMode, selected: Boolean, onClick: () -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(Color(0xFF6A3FE0), Color(0xFF2A0E5C))) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
            .border(if (selected) 2.dp else 1.dp, if (selected) CM.GoldLight else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable { app.sound.play(Sfx.SELECT); onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (m) { MindiTrumpMode.HIDE -> "🙈"; MindiTrumpMode.CUT -> "✂️"; MindiTrumpMode.OPEN -> "👁️" },
            fontSize = 26.sp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                if (m == MindiTrumpMode.HIDE) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(CM.Gold).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(stringResource(R.string.mindi_classic), color = Color(0xFF3A1F00), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(m.description, color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Start)
        }
    }
}

@Composable
internal fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(desc, color = CM.TextDim, fontSize = 12.sp)
        }
        GemSwitch(on, onChange)
    }
}

/** Game-picker tile for Mindi (wide): the four 10s fanned with a short pitch. */
@Composable
internal fun MindiGameTile(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .graphicsLayer { val k = if (selected) 1.02f else 0.98f; scaleX = k; scaleY = k }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0E7A4A).copy(alpha = if (selected) 0.95f else 0.45f), Color(0xFF053A24).copy(alpha = if (selected) 0.95f else 0.45f))))
            .border(if (selected) 3.dp else 1.dp, if (selected) CM.GoldLight else Color(0x40FFFFFF), shape)
            .clickable { app.sound.play(Sfx.SELECT); app.haptics.tick(); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) {
            listOf(0, 1, 3, 2).forEachIndexed { i, suit ->
                Image(
                    PlayingCardArt.face(10, suit), null,
                    Modifier.size(36.dp, 50.dp)
                        .offset(x = ((i - 1.5f) * 18).dp, y = (kotlin.math.abs(i - 1.5f) * 3).dp)
                        .graphicsLayer { rotationZ = (i - 1.5f) * 12f }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mindi_tile_title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(stringResource(R.string.mindi_tile_desc), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** One-line description of a Mindi table size. */
internal fun mindiTableLine(setup: MindiSetup): String = when (setup.players) {
    4 -> Strings.get(R.string.mindi_table_4)
    6 -> Strings.get(if (setup.doubleDeck) R.string.mindi_table_6_double else R.string.mindi_table_6)
    else -> Strings.get(if (setup.doubleDeck) R.string.mindi_table_8_double else R.string.mindi_table_8)
}
