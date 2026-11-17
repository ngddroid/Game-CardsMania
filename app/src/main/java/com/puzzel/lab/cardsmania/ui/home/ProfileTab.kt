package com.puzzel.lab.cardsmania.ui.home

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
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.data.Achievements
import com.puzzel.lab.cardsmania.data.Leveling
import com.puzzel.lab.cardsmania.data.MatchRecord
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.data.ModeStats
import com.puzzel.lab.cardsmania.data.PlayerAvatars
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.CoinIcon
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoundIconButton
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.components.formatNumber
import com.puzzel.lab.cardsmania.ui.kit.GemDialog
import com.puzzel.lab.cardsmania.ui.kit.GemProgressBar
import com.puzzel.lab.cardsmania.ui.kit.Pill
import com.puzzel.lab.cardsmania.ui.kit.rememberProfile
import com.puzzel.lab.cardsmania.ui.kit.staggerIn
import com.puzzel.lab.cardsmania.ui.onboarding.ProfileEditorContent
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily
import com.puzzel.lab.cardsmania.ui.design.GameIcons
import com.puzzel.lab.cardsmania.ui.design.JewelPanel
import com.puzzel.lab.cardsmania.ui.design.LevelGem
import com.puzzel.lab.cardsmania.ui.design.PanelFill
import com.puzzel.lab.cardsmania.ui.design.RibbonHeader
import com.puzzel.lab.cardsmania.ui.design.Rim
import com.puzzel.lab.cardsmania.ui.design.StrokeText
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
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
            OutlinedTitle("Profile", fontSize = 30.sp, outlineWidth = 10f)
            Spacer(Modifier.weight(1f))
            RoundIconButton(GameIcons.Settings, onSettings, size = 42.dp, contentDescription = "Settings")
        }

        JewelPanel(Modifier.fillMaxWidth().staggerIn(1), rim = Rim.GOLD, fill = PanelFill.ROYAL, padding = 16.dp, title = "PLAYER CARD") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AvatarBadge(PlayerAvatars.get(profile.avatar), size = 92.dp, frame = profile.avatarFrame, glow = 0.6f)
                    LevelGem(profile.level, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    StrokeText(profile.name.ifBlank { "Player" }, fontSize = 24.sp, strokeWidth = 7f)
                    Text("Level ${profile.level} · ${profile.avatarFrame.title} frame", color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    GemProgressBar(prog, Modifier.fillMaxWidth(), height = 12.dp, colors = listOf(Color(0xFF8B5CF6), Color(0xFFFF5FA2)))
                    Spacer(Modifier.height(3.dp))
                    Text("$into / $need XP", color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(GameIcons.Edit, { editing = true }, size = 40.dp, contentDescription = "Edit profile")
            }
        }

        Spacer(Modifier.height(12.dp))
        val games = profile.totalGames
        val wins = profile.totalWins
        Row(Modifier.fillMaxWidth().staggerIn(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Matches", formatNumber(games), Modifier.weight(1f))
            StatTile("Wins", formatNumber(wins), Modifier.weight(1f))
            StatTile("Win rate", if (games == 0) "—" else "${wins * 100 / games}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().staggerIn(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Trophies", "${profile.achievements.size}/${Achievements.all.size}", Modifier.weight(1f))
            StatTile("Coins", formatNumber(profile.coins), Modifier.weight(1f), coin = true)
            StatTile("Daily streak", "${profile.dailyStreak}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        TrophyCabinet(profile.achievements, Modifier.staggerIn(4))

        Spacer(Modifier.height(22.dp))
        RibbonHeader("GAME STATS", Modifier.align(Alignment.CenterHorizontally).staggerIn(5), color = Color(0xFF1E6FE8), icon = GameIcons.Stats)
        Spacer(Modifier.height(10.dp))
        var statTab by rememberSaveable { mutableIntStateOf(0) }
        Row(Modifier.fillMaxWidth().staggerIn(5), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mania", "Spider", "Mindi").forEachIndexed { i, t -> ChoiceChip(t, statTab == i, { statTab = i }, Modifier.weight(1f)) }
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
            else -> MindiStatsCard(profile.mindi.stats)
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Recent Matches", Modifier.staggerIn(8))
        Spacer(Modifier.height(10.dp))
        if (profile.history.isEmpty()) {
            GlassPanel(Modifier.fillMaxWidth().staggerIn(9)) {
                Text(
                    "No matches yet. Deal yourself in and your results will show up here!",
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
        GemDialog(title = "Edit Profile", onDismiss = { editing = false }) {
            ProfileEditorContent(
                initialName = profile.name,
                initialAvatar = profile.avatar,
                confirmText = "Save",
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
                Pill("${s.games} played", Color(0x66000000))
            }
            Spacer(Modifier.height(8.dp))
            val winRate = if (s.games == 0) "—" else "${s.wins * 100 / s.games}%"
            val cells = listOf(
                "Wins" to "${s.wins}",
                "Win %" to winRate,
                "Best streak" to "${s.bestStreak}",
                "Biggest stack" to if (s.biggestStack > 0) "+${s.biggestStack}" else "—",
                "Fastest win" to if (s.fastestWin > 0) "${s.fastestWin} turns" else "—",
                "Points" to formatNumber(s.points),
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
            Text(if (r.won) "1st" else "#${r.place}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (r.won) "Victory" else "Defeat", color = if (r.won) CM.GoldLight else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                if (r.daily) {
                    Spacer(Modifier.width(6.dp))
                    Pill("DAILY", CM.Violet)
                }
            }
            Text(
                "${ModeInfo.short(r.mode)} · ${r.players} players · ${DateUtils.getRelativeTimeSpanString(r.time, now, DateUtils.MINUTE_IN_MILLIS)}",
                color = CM.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${r.points} pts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
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
    JewelPanel(modifier.fillMaxWidth(), rim = Rim.BRONZE, fill = PanelFill.NIGHT, title = "TROPHY CABINET", titleColor = Color(0xFF8E5A00), padding = 12.dp) {
        Text("${unlocked.size} of ${Achievements.all.size} trophies", color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
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
private fun SpiderStatsCard(suits: Int, s: com.puzzel.lab.cardsmania.spider.SpiderModeStats) {
    val label = when (suits) {
        1 -> "1 Suit · Easy"
        2 -> "2 Suits · Medium"
        else -> "4 Suits · Hard"
    }
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.EMERALD, fill = PanelFill.FELT, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row { repeat(if (suits == 4) 4 else suits) { Icon(GameIcons.suit(it), null, tint = if (it == 1 || it == 2) Color(0xFFFF6B7A) else Color.White, modifier = Modifier.size(18.dp)) } }
            Spacer(Modifier.width(8.dp))
            Text("Spider $label", color = Color.White, fontSize = 16.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
            Pill("${s.played} played", Color(0x66000000))
        }
        Spacer(Modifier.height(8.dp))
        fun time(ms: Long) = if (ms <= 0) "—" else String.format(java.util.Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)
        StatGrid(
            listOf(
                "Wins" to "${s.won}",
                "Win %" to if (s.played == 0) "—" else "${s.won * 100 / s.played}%",
                "Best streak" to "${s.bestStreak}",
                "Best time" to time(s.bestTimeMs),
                "Fewest moves" to if (s.bestMoves > 0) "${s.bestMoves}" else "—",
                "Top score" to if (s.bestScore > 0) "${s.bestScore}" else "—",
            )
        )
    }
}

@Composable
private fun MindiStatsCard(s: com.puzzel.lab.cardsmania.mindi.MindiStats) {
    JewelPanel(Modifier.fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.WINE, padding = 12.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Team, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mindi", color = Color.White, fontSize = 18.sp, fontFamily = DisplayFamily, modifier = Modifier.weight(1f))
            Pill("${s.matches} matches", Color(0x66000000))
        }
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "Match wins" to "${s.matchWins}",
                "Win %" to if (s.matches == 0) "—" else "${s.matchWins * 100 / s.matches}%",
                "Best streak" to "${s.bestStreak}",
                "Hands won" to "${s.handWins}/${s.hands}",
                "Mendikots" to "${s.mendikots}",
                "Whitewashes" to "${s.whitewashes}",
                "10s captured" to "${s.tensCaptured}",
            )
        )
    }
}
