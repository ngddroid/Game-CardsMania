package com.puzzel.lab.cardsmania.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.design.GameIcons
import com.puzzel.lab.cardsmania.ui.design.JewelPanel
import com.puzzel.lab.cardsmania.ui.design.PanelFill
import com.puzzel.lab.cardsmania.ui.design.RibbonHeader
import com.puzzel.lab.cardsmania.ui.design.Rim
import com.puzzel.lab.cardsmania.ui.kit.TreasureChest
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.Achievement
import com.puzzel.lab.cardsmania.data.Achievements
import com.puzzel.lab.cardsmania.data.Characters
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.GameFactory
import com.puzzel.lab.cardsmania.data.MissionDef
import com.puzzel.lab.cardsmania.data.Missions
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.data.Progression
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.CoinChip
import com.puzzel.lab.cardsmania.ui.components.CoinIcon
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.kit.GemProgressBar
import com.puzzel.lab.cardsmania.ui.kit.Pill
import com.puzzel.lab.cardsmania.ui.kit.StarIcon
import com.puzzel.lab.cardsmania.ui.kit.rememberProfile
import com.puzzel.lab.cardsmania.ui.kit.staggerIn
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun EventsTab(onDaily: () -> Unit, onSpiderDaily: () -> Unit = {}, onMindiDaily: () -> Unit = {}) {
    val app = LocalApp.current
    val profile by rememberProfile()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.profile.update { Progression.rollMissions(it) } }
    val week = GameFactory.currentWeek()
    val missions = remember(week) { Missions.forWeek(week) }
    val daysLeft = remember {
        val idx = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
        7 - idx
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).staggerIn(0), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTitle(stringResource(R.string.shell_tab_events), fontSize = 30.sp, outlineWidth = 10f)
            Spacer(Modifier.weight(1f))
            CoinChip(profile.coins)
        }
        RibbonHeader(stringResource(R.string.shell_events_daily_challenges), Modifier.align(Alignment.CenterHorizontally).staggerIn(1), color = Color(0xFFB0203F), icon = GameIcons.Calendar)
        Spacer(Modifier.height(10.dp))
        var game by rememberSaveable { mutableIntStateOf(0) }
        Row(Modifier.fillMaxWidth().staggerIn(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.shell_chip_mania, R.string.shell_chip_spider, R.string.shell_chip_mindi).forEachIndexed { i, t ->
                ChoiceChip(stringResource(t), game == i, { game = i }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(game, label = "daily") { g ->
            when (g) {
                0 -> DailyPanel(profile.daily[GameFactory.today()] ?: 0, profile.dailyStreak, onDaily)
                1 -> SpiderDailyPanel(profile.spider.dailySolved, onSpiderDaily)
                else -> MindiDailyPanel(profile.mindi.daily, onMindiDaily)
            }
        }

        Spacer(Modifier.height(24.dp))
        RibbonHeader(stringResource(R.string.shell_events_weekly_missions), Modifier.align(Alignment.CenterHorizontally).staggerIn(2), color = Color(0xFF1E6FE8), icon = GameIcons.Chest)
        Text(stringResource(R.string.shell_events_resets_in, daysLeft), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        missions.forEachIndexed { i, m ->
            val progress = if (profile.missionWeek == week) profile.missionProgress[m.id] ?: 0 else 0
            val claimed = profile.missionWeek == week && m.id in profile.missionClaimed
            MissionRow(
                m, progress, claimed,
                onClaim = {
                    scope.launch {
                        app.profile.update { p ->
                            val prog = p.missionProgress[m.id] ?: 0
                            if (p.missionWeek != week || m.id in p.missionClaimed || prog < m.target) p
                            else p.copy(coins = p.coins + m.reward, missionClaimed = p.missionClaimed + m.id)
                        }
                        app.sound.play(Sfx.COIN)
                        app.haptics.medium()
                    }
                },
                modifier = Modifier.staggerIn(3 + i)
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))
        val unlocked = profile.achievements
        RibbonHeader(stringResource(R.string.shell_events_trophy_wall), Modifier.align(Alignment.CenterHorizontally).staggerIn(6), color = Color(0xFF8E5A00), icon = GameIcons.Trophy)
        Text(stringResource(R.string.shell_events_unlocked_count, unlocked.size, Achievements.all.size), color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        val sorted = remember(unlocked) { Achievements.all.sortedBy { if (it.id in unlocked) 0 else 1 } }
        sorted.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().staggerIn(7 + r, step = 30L), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> AchievementCard(a, a.id in unlocked, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DailyPanel(stars: Int, streak: Int, onDaily: () -> Unit, modifier: Modifier = Modifier) {
    val daily = remember { GameFactory.daily() }
    val colors = ModeInfo.colors(daily.mode)
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors[1].copy(alpha = 0.85f), Color(0xDD1A0844))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, colors[0].copy(alpha = 0.5f))), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Calendar, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shell_daily_today), color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text(daily.date, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTitle(ModeInfo.title(daily.mode), fontSize = 26.sp, outlineWidth = 9f)
        Text(stringResource(R.string.shell_daily_beat_rivals), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            daily.bots.forEach { b ->
                val ch = Characters.byId(b.characterId)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarBadge(ch.emoji, size = 50.dp, ringColor = ch.color)
                    Spacer(Modifier.height(3.dp))
                    Text(ch.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Pill(DifficultyInfo.label(b.difficulty), DifficultyInfo.color(b.difficulty))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        daily.goals.forEachIndexed { i, g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33000000))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarIcon(stars > i, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(g, color = if (stars > i) CM.GoldLight else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = CM.Tangerine, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.shell_daily_streak, streak), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            GameButton(
                if (stars >= 3) stringResource(R.string.shell_play_again) else stringResource(R.string.shell_play),
                onDaily,
                style = ButtonStyle.GOLD,
                modifier = Modifier.width(140.dp),
                height = 48.dp,
                fontSize = 16.sp,
                sound = Sfx.SELECT,
            )
        }
    }
}

@Composable
private fun MissionRow(m: MissionDef, progress: Int, claimed: Boolean, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    val done = progress >= m.target
    JewelPanel(
        modifier.fillMaxWidth(), rim = if (done && !claimed) Rim.EMERALD else if (claimed) Rim.SILVER else Rim.GOLD,
        fill = PanelFill.NIGHT, corner = 18.dp, padding = 12.dp, studs = false,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF5A2DAA), Color(0xFF2A0E5C))))
                    .border(1.5.dp, CM.Gold.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(m.icon, fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title.format(m.target), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                GemProgressBar(
                    progress / m.target.toFloat(),
                    Modifier.fillMaxWidth(),
                    height = 10.dp,
                    colors = if (done) listOf(Color(0xFF5BF0A2), Color(0xFF13B36A)) else listOf(Color(0xFFFFE27A), Color(0xFFFFB300))
                )
                Spacer(Modifier.height(3.dp))
                Text("${progress.coerceAtMost(m.target)} / ${m.target}", color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            when {
                claimed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GameIcons.Check, null, tint = CM.Emerald, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.shell_mission_done), color = CM.Emerald, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                done -> GameButton(stringResource(R.string.shell_mission_claim), onClaim, style = ButtonStyle.GREEN, modifier = Modifier.width(84.dp), height = 40.dp, fontSize = 14.sp, sound = Sfx.SELECT)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TreasureChest(0f, Modifier.size(38.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(14.dp)
                        Spacer(Modifier.width(3.dp))
                        Text("${m.reward}", color = CM.GoldLight, fontSize = 13.sp, fontFamily = DisplayFamily)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, unlocked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                if (unlocked) Brush.verticalGradient(listOf(Color(0x66FFC93C), Color(0x22FFC93C)))
                else Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x0FFFFFFF)))
            )
            .border(1.5.dp, if (unlocked) Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)) else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF))), shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(50.dp)
                    .drawBehind {
                        if (unlocked) drawCircle(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.6f), Color.Transparent)), size.minDimension * 0.75f)
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF5A2DAA), Color(0xFF1A0844))))
                    .border(2.dp, if (unlocked) CM.Gold else Color(0x44FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(a.icon, fontSize = 24.sp, modifier = Modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.3f })
            }
            if (!unlocked) Icon(GameIcons.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(a.title, color = if (unlocked) Color.White else CM.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
        Text(a.description, color = CM.TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoinIcon(14.dp)
            Spacer(Modifier.width(3.dp))
            Text("${a.reward}", color = if (unlocked) CM.GoldLight else CM.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ------------------------------------------------------------------------------------------- Spider & Mindi dailies

@Composable
private fun SpiderDailyPanel(solved: Map<String, Long>, onPlay: () -> Unit) {
    val today = GameFactory.today()
    val cal = remember { Calendar.getInstance() }
    val month = remember { java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(cal.time) }
    val monthName = remember { java.text.SimpleDateFormat("LLLL", java.util.Locale.getDefault()).format(cal.time) }
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val done = solved.keys.count { it.startsWith(month) }
    val tier = when {
        done >= days -> Rim.GOLD
        done >= 20 -> Rim.SILVER
        done >= 10 -> Rim.BRONZE
        else -> null
    }
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.EMERALD, fill = PanelFill.FELT, title = stringResource(R.string.shell_spider_daily_title), titleColor = Color(0xFF0E6B3A)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(monthName, color = Color.White, fontFamily = DisplayFamily, fontSize = 22.sp)
                Text(stringResource(R.string.shell_spider_daily_sub), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            Box(contentAlignment = Alignment.Center) {
                Icon(GameIcons.Trophy, null, tint = when (tier) {
                    Rim.GOLD -> CM.Gold
                    Rim.SILVER -> Color(0xFFD8DEEE)
                    Rim.BRONZE -> Color(0xFFD08A4A)
                    else -> Color(0x55FFFFFF)
                }, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val dayFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US) }
        (1..days).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val c2 = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
                    val date = dayFmt.format(c2.time)
                    val isDone = date in solved
                    val future = date > today
                    Box(
                        Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isDone -> Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFE09A00)))
                                    future -> Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF)))
                                    else -> Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
                                }
                            )
                            .border(if (date == today) 2.dp else 0.dp, if (date == today) Color.White else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDone) Icon(GameIcons.Crown, null, tint = Color(0xFF6B3A00), modifier = Modifier.size(16.dp))
                        else Text("$d", color = if (future) Color(0x44FFFFFF) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(Triple(R.string.shell_spider_tier_bronze, 10, Rim.BRONZE), Triple(R.string.shell_spider_tier_silver, 20, Rim.SILVER), Triple(R.string.shell_spider_tier_gold, days, Rim.GOLD)).forEach { (name, need, rim) ->
                val reached = done >= need
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GameIcons.Crown, null, tint = if (reached) rim.mid else Color(0x44FFFFFF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(name, need), color = if (reached) Color.White else CM.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        GemProgressBar(done / days.toFloat(), Modifier.fillMaxWidth(), height = 10.dp)
        Spacer(Modifier.height(12.dp))
        GameButton(
            if (today in solved) stringResource(R.string.shell_spider_play_again) else stringResource(R.string.shell_spider_play_today), onPlay, style = ButtonStyle.GOLD,
            modifier = Modifier.fillMaxWidth(), height = 50.dp, fontSize = 16.sp, icon = GameIcons.Play, sound = Sfx.SELECT,
        )
    }
}

@Composable
private fun MindiDailyPanel(daily: Map<String, Int>, onPlay: () -> Unit) {
    val today = GameFactory.today()
    val stars = daily[today] ?: 0
    val total = daily.values.sum()
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.WINE, title = stringResource(R.string.shell_mindi_daily_title), titleColor = Color(0xFFC0392B)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 70.dp), contentAlignment = Alignment.Center) {
                listOf(0, 1, 3, 2).forEachIndexed { i, suit ->
                    androidx.compose.foundation.Image(
                        com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(10, suit), null,
                        Modifier.size(width = 34.dp, height = 48.dp)
                            .graphicsLayer { translationX = (i - 1.5f) * 14.dp.toPx(); rotationZ = (i - 1.5f) * 10f }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.shell_mindi_daily_heading), color = Color.White, fontFamily = DisplayFamily, fontSize = 18.sp)
                Text(stringResource(R.string.shell_mindi_daily_body), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { StarIcon(it < stars, 34.dp) }
        }
        Text(
            if (stars == 0) stringResource(R.string.shell_mindi_daily_earn) else stringResource(R.string.shell_mindi_daily_today, stars),
            color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Text(pluralStringResource(R.plurals.shell_mindi_daily_total, total, total), color = CM.TextDim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        GameButton(
            if (stars >= 3) stringResource(R.string.shell_play_again) else stringResource(R.string.shell_play), onPlay, style = ButtonStyle.GOLD,
            modifier = Modifier.fillMaxWidth(), height = 50.dp, fontSize = 16.sp, icon = GameIcons.Play, sound = Sfx.SELECT,
        )
    }
}
