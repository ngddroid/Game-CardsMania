package com.puzzel.lab.cardsmania.ui.setup

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import com.puzzel.lab.cardsmania.ui.design.StrokeText
import com.puzzel.lab.cardsmania.ui.design.GameBackdrop
import com.puzzel.lab.cardsmania.ui.design.BackdropStyle
import com.puzzel.lab.cardsmania.ui.design.GameIcons
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.GameLaunch
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.BotPick
import com.puzzel.lab.cardsmania.data.Characters
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.GameFactory
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.data.SetupPrefs
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.HouseRules
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Suit
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GemSwitch
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(initialMode: GameMode?, onBack: () -> Unit, onStart: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var setup by remember { mutableStateOf(SetupPrefs()) }
    var speed by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        val p = app.profile.current()
        profile = p
        setup = p.setup.let { if (initialMode != null) it.copy(mode = initialMode) else it }
        speed = app.settings.current().speed
    }

    val mode = setup.mode
    val rules = setup.rules[mode] ?: ModeInfo.defaultRules(mode)
    fun setRules(r: HouseRules) {
        setup = setup.copy(rules = setup.rules + (mode to r))
    }

    GameBackdrop(style = BackdropStyle.LOBBY) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitleBar("Game Setup", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---------------- hero
                ModeHero(mode)

                // ---------------- mode
                SectionHeader("Choose your mode")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameMode.entries.forEach { m ->
                        ModeTile(m, m == mode, Modifier.weight(1f)) { setup = setup.copy(mode = m) }
                    }
                }
                Text(ModeInfo.tagline(mode) + "  ·  " + ModeInfo.deckLabel(mode), color = CM.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                // ---------------- players
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader("Players", trailing = {
                        Text("${setup.bots.size + 1} / 6", color = CM.GoldLight, fontWeight = FontWeight.Black)
                    })
                    Spacer(Modifier.height(6.dp))
                    SeatTable(setup.bots, profile)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (2..6).forEach { n ->
                            ChoiceChip("$n", setup.bots.size + 1 == n, {
                                setup = setup.copy(bots = resizeBots(setup.bots, n - 1))
                            }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("All bots", color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.entries.forEach { d ->
                            ChoiceChip(
                                DifficultyInfo.label(d), setup.bots.all { it.difficulty == d },
                                { setup = setup.copy(bots = setup.bots.map { it.copy(difficulty = d) }) },
                                Modifier.weight(1f), color = DifficultyInfo.color(d)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        setup.bots.forEachIndexed { i, b ->
                            BotRow(
                                pick = b,
                                onCycle = {
                                    val used = setup.bots.map { it.characterId }.toSet()
                                    var next = (b.characterId + 1) % Characters.all.size
                                    while (next in used && next != b.characterId) next = (next + 1) % Characters.all.size
                                    setup = setup.copy(bots = setup.bots.toMutableList().also { it[i] = b.copy(characterId = next) })
                                },
                                onDifficulty = { d -> setup = setup.copy(bots = setup.bots.toMutableList().also { it[i] = b.copy(difficulty = d) }) },
                            )
                        }
                    }
                }

                // ---------------- cards
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader("Cards")
                    Spacer(Modifier.height(10.dp))
                    Label("Starting hand")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 7, 10).forEach { n -> ChoiceChip("$n cards", setup.handSize == n, { setup = setup.copy(handSize = n) }, Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Deck")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Standard", setup.deckCount == 1, { setup = setup.copy(deckCount = 1) }, Modifier.weight(1f))
                        ChoiceChip("Double deck", setup.deckCount == 2, { setup = setup.copy(deckCount = 2) }, Modifier.weight(1f))
                    }
                    if (setup.bots.size >= 4 && setup.deckCount == 1 && mode == GameMode.CHAOS) {
                        Spacer(Modifier.height(6.dp))
                        Text("Tip: a double deck keeps big Merciless tables flowing.", color = CM.Aqua, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Match length")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 100, 250, 500, 1000).forEach { t ->
                            ChoiceChip(if (t == 0) "Single round" else "First to $t", setup.targetScore == t, { setup = setup.copy(targetScore = t) })
                        }
                    }
                }

                // ---------------- house rules
                HouseRulesPanel(mode, rules, ::setRules)

                // ---------------- speed
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader("Game speed")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Relaxed", "Normal", "Fast").forEachIndexed { i, s ->
                            ChoiceChip(s, speed == i, {
                                speed = i
                                scope.launch { app.settings.update { it.copy(speed = i) } }
                            }, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            // ---------------- start
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0320))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GameButton(
                    "START GAME", {
                        val p = profile ?: return@GameButton
                        scope.launch {
                            val saved = app.profile.update { it.copy(setup = setup) }
                            app.savedGames.clear()
                            app.pendingLaunch = GameLaunch(GameFactory.fromSetup(setup, saved))
                            onStart()
                        }
                        if (p.name.isEmpty()) Unit
                    },
                    style = ButtonStyle.GOLD, icon = GameIcons.Play, height = 60.dp, fontSize = 21.sp,
                    modifier = Modifier.fillMaxWidth(), sound = Sfx.MANIA, enabled = profile != null,
                )
            }
        }
    }
}

private fun resizeBots(bots: List<BotPick>, n: Int): List<BotPick> {
    if (n <= bots.size) return bots.take(n)
    val out = bots.toMutableList()
    val diff = bots.lastOrNull()?.difficulty ?: Difficulty.NORMAL
    while (out.size < n) {
        val used = out.map { it.characterId }.toSet()
        val next = Characters.all.first { it.id !in used }
        out += BotPick(next.id, diff)
    }
    return out
}

@Composable
internal fun Label(text: String) {
    Text(text, color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
internal fun ModeTile(mode: GameMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalApp.current
    val scale by animateFloatAsState(if (selected) 1.04f else 0.96f, spring(0.5f, 400f), label = "tile")
    val cols = ModeInfo.colors(mode)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .scale(scale)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(cols[0].copy(alpha = if (selected) 0.95f else 0.45f), cols[1].copy(alpha = if (selected) 0.95f else 0.45f))))
            .border(if (selected) 3.dp else 1.dp, if (selected) CM.GoldLight else Color(0x40FFFFFF), shape)
            .clickable {
                app.sound.play(Sfx.SELECT)
                app.haptics.tick()
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val faces = when (mode) {
            GameMode.CLASSIC -> listOf(Face(Suit.RUBY, Kind.NUMBER, 7), Face(Suit.WILD, Kind.PRISM))
            GameMode.CHAOS -> listOf(Face(Suit.EMERALD, Kind.PURGE), Face(Suit.WILD, Kind.PRISM_DRAW, 10))
            GameMode.ECLIPSE -> listOf(Face(Suit.SAPPHIRE, Kind.ECLIPSE), Face(Suit.VIOLET, Kind.NUMBER, 5))
        }
        Box(Modifier.height(78.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            faces.forEachIndexed { i, f ->
                Image(
                    CardArt.face(f), null,
                    Modifier.width(44.dp).height(62.dp)
                        .offset(x = if (i == 0) (-12).dp else 12.dp)
                        .graphicsLayer { rotationZ = if (i == 0) -12f else 12f }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(ModeInfo.short(mode).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BotRow(pick: BotPick, onCycle: () -> Unit, onDifficulty: (Difficulty) -> Unit) {
    val ch = Characters.byId(pick.characterId)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable(onClick = onCycle)) {
            AvatarBadge(ch.emoji, size = 44.dp, ringColor = ch.color)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
            Text("${ch.style} · tap avatar to change", color = CM.TextMuted, fontSize = 10.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Difficulty.entries.forEach { d ->
                val sel = d == pick.difficulty
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (sel) DifficultyInfo.color(d) else Color(0x22FFFFFF))
                        .border(1.dp, if (sel) Color.White else Color(0x22FFFFFF), RoundedCornerShape(9.dp))
                        .clickable { onDifficulty(d) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(DifficultyInfo.label(d).take(1), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
internal fun RuleRow(title: String, desc: String, checked: Boolean, enabled: Boolean = true, badge: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(CM.Rose).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(desc, color = CM.TextDim, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        GemSwitch(checked, { if (enabled) onChange(it) })
    }
}

/** House-rule toggles for [mode] (shared by Game Setup and the multiplayer room). */
@Composable
internal fun HouseRulesPanel(mode: GameMode, rules: HouseRules, setRules: (HouseRules) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader("House rules", trailing = {
            Icon(
                GameIcons.Refresh, "Reset rules", tint = CM.TextDim,
                modifier = Modifier.size(22.dp).clickable { setRules(ModeInfo.defaultRules(mode)) }
            )
        })
        Spacer(Modifier.height(6.dp))
        if (mode == GameMode.CHAOS) {
            Text("Always on in Merciless: stacking, Seven swap & Zero rotate, draw until you match, and the 25-card knockout rule.", color = CM.TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (mode != GameMode.CHAOS) {
            RuleRow("Stacking", "Answer a +card with an equal or bigger +card to pass the total on.", rules.stacking) { setRules(rules.copy(stacking = it)) }
            RuleRow("Seven & Zero", if (mode == GameMode.ECLIPSE) "A 7 swaps hands with a rival of your choice." else "A 7 swaps hands with a rival; a 0 passes every hand along.", rules.sevenZero) { setRules(rules.copy(sevenZero = it)) }
            RuleRow("Draw to Match", "Keep drawing until you get a card you can play.", rules.drawToMatch) { setRules(rules.copy(drawToMatch = it)) }
            RuleRow(
                "Challenge",
                if (mode == GameMode.CLASSIC) "Wild Draw Four may be challenged. Off when stacking is on." else "Wild Draw Two may be challenged. Off when stacking is on.",
                rules.challenge && !rules.stacking, enabled = !rules.stacking,
            ) { setRules(rules.copy(challenge = it)) }
        }
        RuleRow("Jump-In", "Hold the exact same card as the top card? Play it out of turn!", rules.jumpIn) { setRules(rules.copy(jumpIn = it)) }
        RuleRow("Forced Play", "A playable drawn card must be played.", rules.forcedPlay) { setRules(rules.copy(forcedPlay = it)) }
        RuleRow("Combo Drop", "Play several cards with the same number in one go.", rules.comboDrop, badge = "NEW") { setRules(rules.copy(comboDrop = it)) }
        RuleRow("Mania Power-ups", "Action cards fill a meter that earns Peek and Shield tokens.", rules.powerUps, badge = "NEW") { setRules(rules.copy(powerUps = it)) }
    }
}

/** Big title header for the selected mode, in the mode's colors. */
@Composable
private fun ModeHero(mode: GameMode) {
    val colors = ModeInfo.colors(mode)
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors[0], colors[1], Color(0xFF12042E))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StrokeText(ModeInfo.title(mode), fontSize = 26.sp, strokeWidth = 8f)
                Text(ModeInfo.deckLabel(mode), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            val faces = when (mode) {
                GameMode.CLASSIC -> listOf(Face(Suit.RUBY, Kind.NUMBER, 7), Face(Suit.WILD, Kind.PRISM_DRAW, 4))
                GameMode.CHAOS -> listOf(Face(Suit.EMERALD, Kind.PURGE), Face(Suit.WILD, Kind.PRISM_DRAW, 10))
                GameMode.ECLIPSE -> listOf(Face(Suit.SAPPHIRE, Kind.ECLIPSE), Face(Suit.VIOLET, Kind.NUMBER, 5))
            }
            Box(Modifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                faces.forEachIndexed { i, f ->
                    Image(
                        CardArt.face(f), null,
                        Modifier.width(48.dp).height(67.dp)
                            .offset(x = if (i == 0) (-14).dp else 14.dp)
                            .graphicsLayer { rotationZ = if (i == 0) -12f else 12f; shadowElevation = 6.dp.toPx() }
                    )
                }
            }
        }
    }
}

/** A mini table showing who sits where: you at the bottom, bots around the oval. */
@Composable
private fun SeatTable(bots: List<BotPick>, profile: Profile?) {
    val n = bots.size + 1
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val rx = size.width * 0.36f
            val ry = size.height * 0.30f
            drawOval(
                Brush.radialGradient(listOf(Color(0xFF1D7A48), Color(0xFF0A3A21)), Offset(cx, cy), rx),
                topLeft = Offset(cx - rx, cy - ry), size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2),
            )
            drawOval(
                CM.GoldLight.copy(alpha = 0.8f), topLeft = Offset(cx - rx, cy - ry),
                size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
            )
        }
        for (i in 0 until n) {
            // seat 0 at the bottom, then clockwise
            val a = Math.PI / 2 + i * 2 * Math.PI / n
            val x = w / 2 + (w.value * 0.40f * kotlin.math.cos(a)).dp
            val y = h / 2 + (h.value * 0.36f * kotlin.math.sin(a)).dp
            val emoji = if (i == 0) com.puzzel.lab.cardsmania.data.PlayerAvatars.get(profile?.avatar ?: 0) else Characters.byId(bots[i - 1].characterId).emoji
            val ring = if (i == 0) CM.Gold else DifficultyInfo.color(bots[i - 1].difficulty)
            Box(Modifier.offset(x = x - 20.dp, y = y - 20.dp)) {
                AvatarBadge(emoji, size = 40.dp, ringColor = ring)
            }
        }
    }
}
