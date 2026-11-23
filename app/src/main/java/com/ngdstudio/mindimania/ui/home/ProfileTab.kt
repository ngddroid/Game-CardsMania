package com.ngdstudio.mindimania.ui.home

import androidx.compose.foundation.horizontalScroll

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.data.Achievements
import com.ngdstudio.mindimania.data.Leveling
import com.ngdstudio.mindimania.data.MatchRecord
import com.ngdstudio.mindimania.data.ModeInfo
import com.ngdstudio.mindimania.data.ModeStats
import com.ngdstudio.mindimania.data.PlayerAvatars
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoundIconButton
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.components.formatNumber
import com.ngdstudio.mindimania.ui.kit.GemDialog
import com.ngdstudio.mindimania.ui.kit.GemProgressBar
import com.ngdstudio.mindimania.ui.kit.Pill
import com.ngdstudio.mindimania.ui.kit.rememberProfile
import com.ngdstudio.mindimania.ui.kit.staggerIn
import com.ngdstudio.mindimania.ui.onboarding.ProfileEditorContent
import com.ngdstudio.mindimania.ui.theme.CM
import com.ngdstudio.mindimania.ui.theme.DisplayFamily
import com.ngdstudio.mindimania.ui.design.GameIcons
import com.ngdstudio.mindimania.ui.design.JewelPanel
import com.ngdstudio.mindimania.ui.design.LevelGem
import com.ngdstudio.mindimania.ui.design.PanelFill
import com.ngdstudio.mindimania.ui.design.RibbonHeader
import com.ngdstudio.mindimania.ui.design.Rim
import com.ngdstudio.mindimania.ui.design.StrokeText
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch

@Composable
fun ProfileTab(onSettings: () -> Unit) {
    val app = LocalApp.current
    val profile by rememberProfile()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val (prog, into, need) = Leveling.progress(profile.xp)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).staggerIn(0), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTitle(stringResource(R.string.shell_tab_profile), fontSize = 30.sp, outlineWidth = 10f)
            Spacer(Modifier.weight(1f))
            RoundIconButton(GameIcons.Settings, onSettings, size = 42.dp, contentDescription = stringResource(R.string.shell_settings))
        }

        JewelPanel(Modifier.fillMaxWidth().staggerIn(1), rim = Rim.GOLD, fill = PanelFill.ROYAL, padding = 16.dp, title = stringResource(R.string.shell_player_card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AvatarBadge(PlayerAvatars.get(profile.avatar), size = 92.dp, frame = profile.avatarFrame, glow = 0.6f)
                    LevelGem(profile.level, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    StrokeText(profile.name.ifBlank { stringResource(R.string.shell_player_default) }, fontSize = 24.sp, strokeWidth = 7f)
                    Text(stringResource(R.string.shell_profile_level_frame, profile.level, profile.avatarFrame.title), color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    GemProgressBar(prog, Modifier.fillMaxWidth(), height = 12.dp, colors = listOf(Color(0xFF8B5CF6), Color(0xFFFF5FA2)))
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.shell_profile_xp, into, need), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(GameIcons.Edit, { editing = true }, size = 40.dp, contentDescription = stringResource(R.string.shell_profile_edit_cd))
            }
        }

        Spacer(Modifier.height(12.dp))
        val games = profile.totalGames
        val wins = profile.totalWins
        Row(Modifier.fillMaxWidth().staggerIn(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.shell_stat_matches), formatNumber(games), Modifier.weight(1f))
            StatTile(stringResource(R.string.shell_stat_wins), formatNumber(wins), Modifier.weight(1f))
            StatTile(stringResource(R.string.shell_stat_win_rate), if (games == 0) "—" else "${wins * 100 / games}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().staggerIn(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.shell_trophies), "${profile.achievements.size}/${Achievements.all.size}", Modifier.weight(1f))
            StatTile(stringResource(R.string.shell_stat_coins), formatNumber(profile.coins), Modifier.weight(1f), coin = true)
            StatTile(stringResource(R.string.shell_stat_daily_streak), "${profile.dailyStreak}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        TrophyCabinet(profile.achievements, Modifier.staggerIn(4))

        Spacer(Modifier.height(22.dp))
        RibbonHeader(stringResource(R.string.shell_profile_game_stats), Modifier.align(Alignment.CenterHorizontally).staggerIn(5), color = Color(0xFF1E6FE8), icon = GameIcons.Stats)
        Spacer(Modifier.height(10.dp))
        var statTab by rememberSaveable { mutableIntStateOf(0) }
        // six games: the chips scroll sideways rather than squeezing their names
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).staggerIn(5), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.shell_chip_mania, R.string.shell_chip_spider, R.string.shell_chip_mindi, R.string.shell_chip_bhabhi, R.string.shell_chip_teenpatti, R.string.shell_chip_rummy)
                .forEachIndexed { i, t -> ChoiceChip(stringResource(t), statTab == i, { statTab = i }, Modifier.width(92.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        when (statTab) {
            0 -> GameMode.entries.forEachIndexed { i, mode ->
                ModeStatsCard(mode, profile.stat(mode), Modifier.staggerIn(6 + i))
                Spacer(Modifier.height(10.dp))
            }
            1 -> listOf(1, 2, 4).forEach { suits ->
                SpiderStatsCard(suits, profile.spider.stat(suits))
                Spacer(Modifier.height(10.dp))
            }
            2 -> MindiStatsCard(profile.mindi.stats)
            3 -> BhabhiStatsCard(profile.bhabhi.stats)
            4 -> TeenPattiStatsCard(profile.teenPatti.stats)
            else -> RummyStatsCard(profile.rummy.stats)
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.shell_profile_recent), Modifier.staggerIn(8))
        Spacer(Modifier.height(10.dp))
        if (profile.history.isEmpty()) {
            GlassPanel(Modifier.fillMaxWidth().staggerIn(9)) {
                Text(
                    stringResource(R.string.shell_profile_no_matches),
                    color = CM.TextDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            val now = System.currentTimeMillis()
            profile.history.forEachIndexed { i, r ->
                HistoryRow(r, now, Modifier.staggerIn(9 + i, step = 30L))
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (editing) {
        GemDialog(title = stringResource(R.string.shell_profile_edit_title), onDismiss = { editing = false }) {
            ProfileEditorContent(
                initialName = profile.name,
                initialAvatar = profile.avatar,
                confirmText = stringResource(R.string.shell_save),
                frame = profile.avatarFrame,
                onSave = { name, avatar ->
                    scope.launch { app.profile.update { it.copy(name = name, avatar = avatar, profileCreated = true) } }
                    editing = false
                }
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, coin: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE0472094), Color(0xF0170742))))
            .border(1.5.dp, Brush.verticalGradient(listOf(CM.GoldLight.copy(alpha = 0.7f), Color(0x55C98A00))), shape)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (coin) {
                CoinIcon(18.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(value, color = Color.White, fontSize = 21.sp, fontFamily = DisplayFamily, maxLines = 1)
        }
        Text(label.uppercase(), color = CM.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

@Composable
private fun ModeStatsCard(mode: GameMode, s: ModeStats, modifier: Modifier = Modifier) {
    val colors = ModeInfo.colors(mode)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(colors[1].copy(alpha = 0.45f), Color(0x22FFFFFF))))
            .border(1.2.dp, Brush.horizontalGradient(listOf(colors[0], Color(0x22FFFFFF))), shape)
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(Brush.verticalGradient(colors)))
        Column(Modifier.weight(1f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ModeInfo.title(mode), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Pill(stringResource(R.string.shell_profile_played, s.games), Color(0x66000000))
            }
            Spacer(Modifier.height(8.dp))
            val winRate = if (s.games == 0) "—" else "${s.wins * 100 / s.games}%"
            val cells = listOf(
                stringResource(R.string.shell_stat_wins) to "${s.wins}",
                stringResource(R.string.shell_stat_win_pct) to winRate,
                stringResource(R.string.shell_stat_best_streak) to "${s.bestStreak}",
                stringResource(R.string.shell_stat_biggest_stack) to if (s.biggestStack > 0) "+${s.biggestStack}" else "—",
                stringResource(R.string.shell_stat_fastest_win) to
                    if (s.fastestWin > 0) pluralStringResource(R.plurals.shell_stat_turns, s.fastestWin, s.fastestWin) else "—",
                stringResource(R.string.shell_stat_points) to formatNumber(s.points),
            )
            cells.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(label, color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(r: MatchRecord, now: Long, modifier: Modifier = Modifier) {
    val colors = ModeInfo.colors(r.mode)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x2EFFFFFF), Color(0x10FFFFFF))))
            .border(1.dp, if (r.won) CM.Gold.copy(alpha = 0.6f) else Color(0x22FFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(colors))
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (r.won) stringResource(R.string.shell_history_first) else "#${r.place}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (r.won) stringResource(R.string.shell_history_victory) else stringResource(R.string.shell_history_defeat), color = if (r.won) CM.GoldLight else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                if (r.daily) {
                    Spacer(Modifier.width(6.dp))
                    Pill(stringResource(R.string.shell_history_daily), CM.Violet)
                }
            }
            Text(
                stringResource(
                    R.string.shell_history_line,
                    ModeInfo.short(r.mode),
                    pluralStringResource(R.plurals.shell_history_players, r.players, r.players),
                    DateUtils.getRelativeTimeSpanString(r.time, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                ),
                color = CM.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.shell_history_pts, r.points), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(14.dp)
                Text(" +${r.coins}", color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- cabinet & per-game stats

@Composable
private fun TrophyCabinet(unlocked: Set<String>, modifier: Modifier = Modifier) {
    val shown = Achievements.all.filter { it.id in unlocked }.takeLast(8)
    JewelPanel(modifier.fillMaxWidth(), rim = Rim.BRONZE, fill = PanelFill.NIGHT, title = stringResource(R.string.shell_trophy_cabinet), titleColor = Color(0xFF8E5A00), padding = 12.dp) {
        Text(stringResource(R.string.shell_trophy_count, unlocked.size, Achievements.all.size), color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        listOf(shown.take(4), shown.drop(4)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                for (i in 0 until 4) {
                    val a = row.getOrNull(i)
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        if (a != null) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.45f), Color.Transparent))))
                            Text(a.icon, fontSize = 26.sp)
                        } else {
                            Icon(GameIcons.Trophy, null, tint = Color(0x33FFFFFF), modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            // shelf
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFB57A3C), Color(0xFF5A3410)))),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StatGrid(cells: List<Pair<String, String>>) {
    cells.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(value, color = Color.White, fontSize = 16.sp, fontFamily = DisplayFamily, maxLines = 1)
                    Text(label, color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SpiderStatsCard(suits: Int, s: com.ngdstudio.mindimania.spider.SpiderModeStats) {
    val label = when (suits) {
        1 -> stringResource(R.string.shell_spider_stats_1)
        2 -> stringResource(R.string.shell_spider_stats_2)
        else -> stringResource(R.string.shell_spider_stats_4)
    }
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.EMERALD, fill = PanelFill.FELT, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row { repeat(if (suits == 4) 4 else suits) { Icon(GameIcons.suit(it), null, tint = if (it == 1 || it == 2) Color(0xFFFF6B7A) else Color.White, modifier = Modifier.size(18.dp)) } }
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
            Pill(stringResource(R.string.shell_profile_played, s.played), Color(0x66000000))
        }
        Spacer(Modifier.height(8.dp))
        fun time(ms: Long) = if (ms <= 0) "—" else String.format(java.util.Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)
        StatGrid(
            listOf(
                stringResource(R.string.shell_stat_wins) to "${s.won}",
                stringResource(R.string.shell_stat_win_pct) to if (s.played == 0) "—" else "${s.won * 100 / s.played}%",
                stringResource(R.string.shell_stat_best_streak) to "${s.bestStreak}",
                stringResource(R.string.shell_stat_best_time) to time(s.bestTimeMs),
                stringResource(R.string.shell_stat_fewest_moves) to if (s.bestMoves > 0) "${s.bestMoves}" else "—",
                stringResource(R.string.shell_stat_top_score) to if (s.bestScore > 0) "${s.bestScore}" else "—",
            )
        )
    }
}

@Composable
private fun RummyStatsCard(s: com.ngdstudio.mindimania.rummy.RummyStats) {
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.WINE, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Team, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shell_game_rummy), color = Color.White, fontSize = 18.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                stringResource(R.string.shell_stat_hands_won) to "${s.handsWon}/${s.hands}",
                stringResource(R.string.shell_stat_win_pct) to if (s.hands == 0) "—" else "${s.handsWon * 100 / s.hands}%",
                stringResource(R.string.rm_stat_declares) to "${s.declares}",
                stringResource(R.string.rm_stat_fastest) to if (s.fastestDeclare == 0) "—" else "${s.fastestDeclare}",
                stringResource(R.string.rm_stat_drops) to "${s.drops}",
                stringResource(R.string.tp_stat_best_stack) to if (s.bestStack == 0) "—" else "${s.bestStack}",
                stringResource(R.string.shell_stat_best_streak) to "${s.bestStreak}",
            )
        )
    }
}

@Composable
private fun TeenPattiStatsCard(s: com.ngdstudio.mindimania.teenpatti.TeenPattiStats) {
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.WINE, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Star, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shell_game_teenpatti), color = Color.White, fontSize = 18.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
            Pill(pluralStringResource(R.plurals.shell_profile_matches_count, s.games, s.games), Color(0x66000000))
        }
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                // an endless table: everything is counted per hand
                stringResource(R.string.shell_stat_hands_won) to "${s.handsWon}/${s.hands}",
                stringResource(R.string.shell_stat_win_pct) to if (s.hands == 0) "—" else "${s.handsWon * 100 / s.hands}%",
                stringResource(R.string.tp_stat_best_stack) to if (s.bestStack == 0) "—" else "${s.bestStack}",
                stringResource(R.string.shell_stat_best_streak) to "${s.bestStreak}",
                stringResource(R.string.tp_stat_blind_wins) to "${s.blindWins}",
                stringResource(R.string.tp_stat_biggest_pot) to if (s.biggestPot == 0) "—" else "${s.biggestPot}",
                stringResource(R.string.tp_stat_best_hand) to if (s.bestKind < 0) "—" else com.ngdstudio.mindimania.ui.teenpatti.kindName(com.ngdstudio.mindimania.teenpatti.TPKind.entries[s.bestKind]),
            )
        )
    }
}

@Composable
private fun BhabhiStatsCard(s: com.ngdstudio.mindimania.bhabhi.BhabhiStats) {
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.WINE, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Spade, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shell_game_bhabhi), color = Color.White, fontSize = 18.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
            Pill(pluralStringResource(R.plurals.shell_profile_games_count, s.games, s.games), Color(0x66000000))
        }
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                stringResource(R.string.shell_stat_got_away) to "${s.escapes}",
                stringResource(R.string.shell_stat_escape_pct) to if (s.games == 0) "—" else "${s.escapes * 100 / s.games}%",
                stringResource(R.string.shell_stat_first_out) to "${s.firsts}",
                stringResource(R.string.shell_stat_times_bhabhi) to "${s.bhabhis}",
                stringResource(R.string.shell_stat_best_streak) to "${s.bestStreak}",
                stringResource(R.string.shell_stat_thullas) to "${s.thullas}",
                stringResource(R.string.shell_stat_pickups) to "${s.pickups}",
            )
        )
    }
}

@Composable
private fun MindiStatsCard(s: com.ngdstudio.mindimania.mindi.MindiStats) {
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.WINE, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Team, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shell_game_mindi), color = Color.White, fontSize = 18.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
            Pill(pluralStringResource(R.plurals.shell_profile_matches_count, s.matches, s.matches), Color(0x66000000))
        }
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                stringResource(R.string.shell_stat_match_wins) to "${s.matchWins}",
                stringResource(R.string.shell_stat_win_pct) to if (s.matches == 0) "—" else "${s.matchWins * 100 / s.matches}%",
                stringResource(R.string.shell_stat_best_streak) to "${s.bestStreak}",
                stringResource(R.string.shell_stat_hands_won) to "${s.handWins}/${s.hands}",
                stringResource(R.string.shell_stat_mendikots) to "${s.mendikots}",
                stringResource(R.string.shell_stat_whitewashes) to "${s.whitewashes}",
                stringResource(R.string.shell_stat_tens) to "${s.tensCaptured}",
            )
        )
    }
}
