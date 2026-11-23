package com.ngdstudio.mindimania.ui.spider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.data.GameFactory
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.spider.SpiderRules
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GemSwitch
import com.ngdstudio.mindimania.ui.kit.GemDialog
import com.ngdstudio.mindimania.ui.kit.GemProgressBar
import com.ngdstudio.mindimania.ui.kit.StarIcon
import com.ngdstudio.mindimania.ui.theme.CM
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun SpiderDialogs(vm: SpiderViewModel, ui: SpiderUi, onExit: () -> Unit) {
    ui.win?.let { w ->
        WinDialog(w, ui, onNew = { vm.dismissWin(); vm.newGame(w.suits) }, onDaily = { vm.dismissWin(); vm.showDialog(SpiderDialog.DAILY) }, onHome = onExit)
        return
    }
    when (ui.dialog) {
        SpiderDialog.NEW_GAME -> NewGameDialog(ui, onPick = { vm.newGame(it) }, onReplay = { vm.newGame(ui.state?.suits ?: 1, replay = true) }, onDaily = { vm.showDialog(SpiderDialog.DAILY) }, onStats = { vm.showDialog(SpiderDialog.STATS) }, onDismiss = { vm.showDialog(null) })
        SpiderDialog.DAILY -> DailyDialog(ui, onPlay = { vm.startDaily(it) }, onDismiss = { vm.showDialog(null) })
        SpiderDialog.LOOK -> LookDialog(ui, vm, onDismiss = { vm.showDialog(null) })
        SpiderDialog.RULES -> RulesDialog(onDismiss = { vm.showDialog(null) })
        SpiderDialog.STUCK -> StuckDialog(ui, onUndo = { vm.showDialog(null); vm.undo() }, onNew = { vm.showDialog(SpiderDialog.NEW_GAME) }, onDismiss = { vm.showDialog(null) })
        SpiderDialog.STATS -> StatsDialog(ui, onDismiss = { vm.showDialog(null) })
        null -> Unit
    }
}

// ------------------------------------------------------------------------------------------- new game

@Composable
private fun NewGameDialog(ui: SpiderUi, onPick: (Int) -> Unit, onReplay: () -> Unit, onDaily: () -> Unit, onStats: () -> Unit, onDismiss: () -> Unit) {
    GemDialog(stringResource(R.string.spider_new_game), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SuitOption(1, stringResource(R.string.spider_level_easy), stringResource(R.string.spider_suits_1_desc), Color(0xFF19C37D), ui.prefs.lastSuits == 1) { onPick(1) }
            SuitOption(2, stringResource(R.string.spider_level_medium), stringResource(R.string.spider_suits_2_desc), Color(0xFFFF9F1A), ui.prefs.lastSuits == 2) { onPick(2) }
            SuitOption(4, stringResource(R.string.spider_level_hard), stringResource(R.string.spider_suits_4_desc), Color(0xFFE0245E), ui.prefs.lastSuits == 4) { onPick(4) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(stringResource(R.string.spider_replay_deal), onReplay, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14.sp, icon = Icons.Rounded.Refresh, enabled = ui.state != null)
            GameButton(stringResource(R.string.spider_daily), onDaily, style = ButtonStyle.BLUE, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.spider_view_stats), color = CM.GoldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onStats).padding(6.dp))
    }
}

@Composable
private fun SuitOption(suits: Int, level: String, desc: String, color: Color, last: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.35f), Color(0x22FFFFFF))))
            .border(if (last) 2.dp else 1.dp, if (last) CM.GoldLight else Color(0x40FFFFFF), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SuitGlyphs(suits, Modifier.width(58.dp).height(30.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluralStringResource(R.plurals.spider_suit_count, suits, suits), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text(level, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
            Text(desc, color = CM.TextDim, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
    }
}

/** Small row of suit symbols drawn with the card art paths. */
@Composable
fun SuitGlyphs(suits: Int, modifier: Modifier) {
    val list = when (suits) {
        1 -> listOf(0)
        2 -> listOf(0, 1)
        else -> listOf(0, 1, 2, 3)
    }
    Canvas(modifier) {
        val n = list.size
        val step = size.width / 4.2f
        val startX = size.width / 2 - step * (n - 1) / 2
        drawIntoCanvas { cv ->
            list.forEachIndexed { i, s ->
                val path = PlayingCardArt.suitPath(s, startX + i * step, size.height / 2, size.height * 0.8f)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = if (s == 0 || s == 3) 0xFFFFFFFF.toInt() else 0xFFFF5A6E.toInt() }
                cv.nativeCanvas.drawPath(path, paint)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- daily calendar

@Composable
private fun DailyDialog(ui: SpiderUi, onPlay: (String) -> Unit, onDismiss: () -> Unit) {
    val today = remember { Calendar.getInstance() }
    var monthOffset by remember { mutableIntStateOf(0) }
    val cal = remember(monthOffset) {
        (today.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
    }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val monthName = remember(monthOffset) { SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(cal.time) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    val todayStr = GameFactory.today()
    val solved = ui.profile.spider.dailySolved
    val monthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    val solvedThisMonth = solved.keys.count { it.startsWith(monthPrefix) }

    GemDialog(stringResource(R.string.spider_daily_title), onDismiss) {
        Text(stringResource(R.string.spider_daily_desc), color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.spider_cd_prev_month), tint = if (monthOffset > -11) Color.White else Color(0x33FFFFFF),
                modifier = Modifier.size(34.dp).clickable(enabled = monthOffset > -11) { monthOffset-- })
            Text(monthName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.spider_cd_next_month), tint = if (monthOffset < 0) Color.White else Color(0x33FFFFFF),
                modifier = Modifier.size(34.dp).clickable(enabled = monthOffset < 0) { monthOffset++ })
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(
                R.string.spider_dow_mon, R.string.spider_dow_tue, R.string.spider_dow_wed, R.string.spider_dow_thu,
                R.string.spider_dow_fri, R.string.spider_dow_sat, R.string.spider_dow_sun,
            ).map { stringResource(it) }.forEach {
                Text(it, color = CM.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val cells = firstDow + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = r * 7 + d - firstDow + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..daysInMonth) {
                            val c2 = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                            val date = fmt.format(c2.time)
                            val future = date > todayStr
                            val done = date in solved
                            val isToday = date == todayStr
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            done -> Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFE09A00)))
                                            future -> Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF)))
                                            else -> Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
                                        }
                                    )
                                    .border(if (isToday) 2.dp else 0.dp, if (isToday) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !future) { onPlay(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) Crown(CrownTier.GOLD, Modifier.size(20.dp))
                                else Text("$day", color = if (future) Color(0x44FFFFFF) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Crown(CrownTier.GOLD, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.spider_crowns_month, solvedThisMonth, daysInMonth), color = CM.GoldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        GemProgressBar(solvedThisMonth / daysInMonth.toFloat(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        // monthly crown tiers
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TierBadge(CrownTier.BRONZE, pluralStringResource(R.plurals.spider_tier_days, 10, 10), solvedThisMonth >= 10)
            TierBadge(CrownTier.SILVER, pluralStringResource(R.plurals.spider_tier_days, 20, 20), solvedThisMonth >= 20)
            TierBadge(CrownTier.GOLD, stringResource(R.string.spider_tier_every_day), solvedThisMonth >= daysInMonth)
        }
        Spacer(Modifier.height(10.dp))
        TrophyShelf(solved.keys, today)
        Spacer(Modifier.height(14.dp))
        val todayDone = todayStr in solved
        GameButton(
            stringResource(if (todayDone) R.string.spider_play_today_again else R.string.spider_play_today), { onPlay(todayStr) },
            style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), height = 52.dp, fontSize = 16.sp, icon = Icons.Rounded.PlayArrow,
        )
        Text(stringResource(R.string.spider_daily_bonus), color = CM.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// ------------------------------------------------------------------------------------------- look & feel

@Composable
private fun LookDialog(ui: SpiderUi, vm: SpiderViewModel, onDismiss: () -> Unit) {
    GemDialog(stringResource(R.string.spider_look_title), onDismiss) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.spider_look_background), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            SpiderBackground.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { bg ->
                        val sel = ui.prefs.background == bg.name
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val thumb = remember(bg) { SpiderBackgrounds.render(bg, 108, 150).asImageBitmap() }
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp))
                                    .background(Color(bg.swatch))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) CM.GoldLight else Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                                    .clickable { vm.setBackground(bg.name) },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(thumb, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                if (sel) Box(Modifier.size(24.dp).clip(CircleShape).background(CM.Gold), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF3A1F00), modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(bg.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.spider_look_card_backs), color = CM.TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            ToggleLine(stringResource(R.string.spider_four_color), stringResource(R.string.spider_four_color_desc), ui.prefs.fourColor, vm::setFourColor)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.spider_look_controls), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.spider_smart_tap), !ui.prefs.selectMode, { vm.setSelectMode(false) }, Modifier.weight(1f))
                ChoiceChip(stringResource(R.string.spider_tap_target), ui.prefs.selectMode, { vm.setSelectMode(true) }, Modifier.weight(1f))
            }
            Text(
                stringResource(if (ui.prefs.selectMode) R.string.spider_tap_target_desc else R.string.spider_smart_tap_desc),
                color = CM.TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            ToggleLine(stringResource(R.string.spider_left_handed), stringResource(R.string.spider_left_handed_desc), ui.prefs.leftHanded, vm::setLeftHanded)
            ToggleLine(stringResource(R.string.spider_show_timer), stringResource(R.string.spider_show_timer_desc), ui.prefs.showTimer, vm::setShowTimer)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.spider_view_stats), color = CM.GoldLight, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clickable { vm.showDialog(SpiderDialog.STATS) }.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ToggleLine(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(desc, color = CM.TextDim, fontSize = 11.sp)
        }
        GemSwitch(on, onChange)
    }
}

// ------------------------------------------------------------------------------------------- rules, stuck, stats

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    GemDialog(stringResource(R.string.spider_rules_title), onDismiss) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.spider_rules_goal_t to R.string.spider_rules_goal_b,
                R.string.spider_rules_moving_t to R.string.spider_rules_moving_b,
                R.string.spider_rules_groups_t to R.string.spider_rules_groups_b,
                R.string.spider_rules_empty_t to R.string.spider_rules_empty_b,
                R.string.spider_rules_dealing_t to R.string.spider_rules_dealing_b,
                R.string.spider_rules_hints_t to R.string.spider_rules_hints_b,
                R.string.spider_rules_score_t to R.string.spider_rules_score_b,
                R.string.spider_rules_tip_t to R.string.spider_rules_tip_b,
            ).map { (t, b) -> stringResource(t) to stringResource(b) }.forEach { (t, b) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF)).padding(10.dp)) {
                    Text(t, color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(b, color = CM.Text, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GameButton(stringResource(R.string.spider_got_it), onDismiss, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), height = 48.dp, fontSize = 16.sp)
    }
}

@Composable
private fun StuckDialog(ui: SpiderUi, onUndo: () -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    GemDialog(stringResource(R.string.spider_stuck_title), onDismiss) {
        Text(stringResource(R.string.spider_stuck_body),
            color = CM.Text, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(stringResource(R.string.spider_undo), onUndo, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), height = 48.dp, fontSize = 15.sp, icon = Icons.AutoMirrored.Rounded.Undo, enabled = ui.canUndo)
            GameButton(stringResource(R.string.spider_new_game), onNew, style = ButtonStyle.GOLD, modifier = Modifier.weight(1.2f), height = 48.dp, fontSize = 15.sp)
        }
        Text(stringResource(R.string.spider_keep_looking), color = CM.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
    }
}

@Composable
private fun StatsDialog(ui: SpiderUi, onDismiss: () -> Unit) {
    GemDialog(stringResource(R.string.spider_stats_title), onDismiss) {
        listOf(1, 2, 4).forEach { suits ->
            val st = ui.profile.spider.stat(suits)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuitGlyphs(suits, Modifier.width(48.dp).height(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pluralStringResource(R.plurals.spider_suit_count, suits, suits), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    StatCell(stringResource(R.string.spider_stat_played), "${st.played}")
                    StatCell(stringResource(R.string.spider_stat_won), "${st.won}")
                    StatCell(stringResource(R.string.spider_stat_win_pct), if (st.played == 0) "–" else "${st.won * 100 / st.played}%")
                    StatCell(stringResource(R.string.spider_stat_streak), "${st.bestStreak}")
                }
                Row(Modifier.fillMaxWidth()) {
                    StatCell(stringResource(R.string.spider_stat_best_time), if (st.bestTimeMs == 0L) "–" else SpiderRules.formatTime(st.bestTimeMs))
                    StatCell(stringResource(R.string.spider_stat_fewest_moves), if (st.bestMoves == 0) "–" else "${st.bestMoves}")
                    StatCell(stringResource(R.string.spider_stat_top_score), if (st.bestScore == 0) "–" else "${st.bestScore}")
                }
            }
        }
        Text(stringResource(R.string.spider_daily_solved_count, ui.profile.spider.dailySolved.size), color = CM.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        GameButton(stringResource(R.string.spider_close), onDismiss, style = ButtonStyle.GLASS, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 15.sp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(label: String, value: String) {
    Column(Modifier.weight(1f).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(label, color = CM.TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------------------------------- win

@Composable
private fun WinDialog(w: SpiderWin, ui: SpiderUi, onNew: () -> Unit, onDaily: () -> Unit, onHome: () -> Unit) {
    GemDialog(stringResource(if (w.daily != null) R.string.spider_win_daily else R.string.spider_win_title), onDismiss = {}, dismissible = false, onBack = onHome) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val stars = when {
                w.moves <= 120 -> 3
                w.moves <= 200 -> 2
                else -> 1
            }
            repeat(3) { StarIcon(it < stars, 40.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell(stringResource(R.string.spider_stat_time), SpiderRules.formatTime(w.timeMs))
            StatCell(stringResource(R.string.spider_stat_moves), "${w.moves}")
            StatCell(stringResource(R.string.spider_stat_score), "${w.score}")
        }
        if (w.bestTime) Text(stringResource(R.string.spider_new_best_time), color = CM.Emerald, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoinIcon(26.dp)
            Spacer(Modifier.width(6.dp))
            Text("+${w.coins}", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.width(18.dp))
            Text(stringResource(R.string.spider_xp, w.xp), color = Color(0xFFC0A6FF), fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        w.achievements.forEach { a ->
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFC93C)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.icon, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(a.description, color = CM.TextDim, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GameButton(stringResource(R.string.spider_new_game), onNew, style = ButtonStyle.GOLD, modifier = Modifier.fillMaxWidth(), height = 52.dp, fontSize = 17.sp, icon = Icons.Rounded.PlayArrow)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(stringResource(R.string.spider_home), onHome, style = ButtonStyle.PURPLE, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14.sp, icon = Icons.Rounded.Home)
            GameButton(stringResource(R.string.spider_daily), onDaily, style = ButtonStyle.BLUE, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14.sp)
        }
        @Suppress("UNUSED_VARIABLE") val unused = ui
    }
}

// ------------------------------------------------------------------------------------------- crowns & trophies

enum class CrownTier(val light: Long, val base: Long, val dark: Long, @StringRes val labelRes: Int) {
    BRONZE(0xFFF2B57A, 0xFFC9783C, 0xFF7A4218, R.string.spider_tier_bronze),
    SILVER(0xFFFFFFFF, 0xFFC9CFDB, 0xFF7C8496, R.string.spider_tier_silver),
    GOLD(0xFFFFF1A8, 0xFFFFC93C, 0xFFB97A00, R.string.spider_tier_gold);

    val label: String get() = Strings.get(labelRes)
}

/** A small crown drawn with paths (tinted by tier, or grey when [lit] is false). */
@Composable
fun Crown(tier: CrownTier, modifier: Modifier, lit: Boolean = true) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.1f, h * 0.78f)
            lineTo(w * 0.06f, h * 0.3f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.5f, h * 0.16f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.94f, h * 0.3f)
            lineTo(w * 0.9f, h * 0.78f)
            close()
        }
        val colors = if (lit) listOf(Color(tier.light), Color(tier.base), Color(tier.dark)) else listOf(Color(0x55FFFFFF), Color(0x33FFFFFF), Color(0x22FFFFFF))
        drawPath(path, Brush.verticalGradient(colors))
        drawRect(Brush.verticalGradient(colors), topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.8f), size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.12f))
        if (lit) {
            drawCircle(Color(0xFFE53935), radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.6f))
            drawPath(path, Color(tier.dark), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.04f))
        }
    }
}

@Composable
private fun TierBadge(tier: CrownTier, label: String, earned: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(if (earned) Brush.radialGradient(listOf(Color(tier.base).copy(alpha = 0.45f), Color(0x22000000))) else Brush.radialGradient(listOf(Color(0x22FFFFFF), Color(0x11000000))))
                .border(1.5.dp, if (earned) Color(tier.light) else Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) { Crown(tier, Modifier.size(30.dp), lit = earned) }
        Text(stringResource(tier.labelRes), color = if (earned) Color(tier.light) else CM.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Text(label, color = CM.TextMuted, fontSize = 10.sp)
    }
}

/** The last six months, each showing the best crown tier earned that month. */
@Composable
private fun TrophyShelf(solved: Set<String>, today: Calendar) {
    val months = remember(solved) {
        (5 downTo 0).map { back ->
            val cal = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
            val prefix = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
            val name = SimpleDateFormat("LLL", Locale.getDefault()).format(cal.time)
            val count = solved.count { it.startsWith(prefix) }
            val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val tier = when {
                count >= days -> CrownTier.GOLD
                count >= 20 -> CrownTier.SILVER
                count >= 10 -> CrownTier.BRONZE
                else -> null
            }
            Triple(name, tier, count)
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x55000000))))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(stringResource(R.string.spider_trophy_shelf), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            months.forEach { (name, tier, count) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Crown(tier ?: CrownTier.BRONZE, Modifier.size(24.dp), lit = tier != null)
                    Text(name, color = if (tier != null) Color.White else CM.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$count", color = CM.TextMuted, fontSize = 9.sp)
                }
            }
        }
        // a wooden shelf line
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF7A4A26), Color(0xFFB07A48), Color(0xFF7A4A26)))))
    }
}
