package com.puzzel.lab.cardsmania.ui.howto

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.engine.model.DeckFactory
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.kit.BackImage
import com.puzzel.lab.cardsmania.ui.kit.FaceImage
import com.puzzel.lab.cardsmania.ui.kit.GemBullet
import com.puzzel.lab.cardsmania.ui.kit.Pill
import com.puzzel.lab.cardsmania.ui.kit.pressable
import com.puzzel.lab.cardsmania.ui.kit.rememberProfile
import com.puzzel.lab.cardsmania.ui.render.CardBack
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.launch

private data class RuleSection(val title: String, val bullets: List<String>)

private val basics = RuleSection(
    "The Basics",
    listOf(
        "Goal: be the first player to get rid of every card in your hand.",
        "Setup: everyone is dealt the same number of cards (7 by default — choose 5, 7 or 10 before the match). One card is flipped face up to start the discard pile.",
        "Your turn: play one card that matches the top card by color, by number, or by symbol. Wild cards fit on anything.",
        "Can't play? Draw a card. If it fits you may play it right away; otherwise your turn ends.",
        "MANIA! — when you're about to drop to your last card, tap the MANIA! button. Forget, and a sharp-eyed rival can catch you: you draw 2.",
        "You can catch rivals too: when a bot forgets, tap Catch! above its seat.",
        "Scoring: the round winner scores the cards left in everyone else's hand — numbers are worth their value, action cards 10–30, Wild cards 40–100. Play one round or race to a target score.",
    )
)

private fun modeRules(mode: GameMode): RuleSection = when (mode) {
    GameMode.CLASSIC -> RuleSection(
        "Classic Mania",
        listOf(
            "108 cards in four colors: Red, Blue, Green and Yellow. Every color also has its own symbol for color-blind players.",
            "Skip skips the next player, Reverse flips the direction and Draw Two makes the next player draw two and miss a turn.",
            "With only two players, Reverse acts like Skip — you go again.",
            "Wild Draw Four is only allowed when you hold no card of the current color. The next player may challenge: catch a bluff and you draw 4 instead — but a wrong challenge costs them 6.",
            "Optional house rules: Stacking, Jump-In, Seven-Zero swaps, Draw to Match, Forced Play, Combo Drop and Mania Power-ups.",
        )
    )
    GameMode.CHAOS -> RuleSection(
        "Merciless Mania",
        listOf(
            "168 cards loaded with heavy hitters — expect big swings!",
            "Draw to Match: if you can't play, keep drawing until you find a card that fits.",
            "Stacking: answer a draw card with one of equal or higher value to pass the growing total on. Can't stack? Take the whole pile and lose your turn.",
            "Wild Reverse Draw Four reverses direction and adds 4 to the stack.",
            "Seven: swap hands with any rival. Zero: every hand slides one seat along in the direction of play.",
            "Discard All also throws away every other card of its color in your hand. Skip Everyone skips all rivals — play again!",
            "Wild Color Storm: the next player names a color, then reveals cards until it shows up, keeping all of them.",
            "Knockout rule: hold 25 cards or more and you're out. The last player standing wins too.",
        )
    )
    GameMode.ECLIPSE -> RuleSection(
        "Flip Mania",
        listOf(
            "112 two-sided cards. The match begins on the Light side.",
            "You can see the hidden side of your rivals' cards — but never the other side of your own. Use it!",
            "Flip card: the whole table flips. Hands, the draw pile and the discard pile all turn to the Dark side (or back to the Light side).",
            "Light side: gentler cards like Draw One, Skip and Wild Draw Two. Dark side hits harder: Draw Five, Skip Everyone and Wild Draw Color.",
            "Wild Draw Color: pick a color — the next player draws until they find it.",
            "Every match pairs Light and Dark faces differently, so no two games play alike.",
        )
    )
}

/** A group of related faces shown together in the gallery. */
private data class FaceGroup(val title: String, val description: String, val points: String, val faces: List<Face>)

private fun groupsFor(faces: List<Face>, mode: GameMode): List<FaceGroup> {
    val groups = LinkedHashMap<String, MutableList<Face>>()
    faces.distinct().forEach { f ->
        val key = if (f.kind == Kind.NUMBER) "NUMBER" else "${f.kind}-${f.value}"
        groups.getOrPut(key) { mutableListOf() } += f
    }
    return groups.map { (key, list) ->
        if (key == "NUMBER") {
            val min = list.minOf { it.value }
            val bySuit = list.groupBy { it.suit }.values.toList()
            val picks = bySuit.mapIndexed { i, l -> l[(i * 3 + 1).coerceAtMost(l.lastIndex)] }
            FaceGroup("Number Cards $min–9", "Match by color or by number.", "Worth their face value", picks.take(4))
        } else {
            val f = list.first()
            FaceGroup(f.title, f.description, "Worth ${f.points(mode)} points", list.take(4))
        }
    }
}

@Composable
fun HowToPlayScreen(initialMode: GameMode?, onBack: () -> Unit) {
    var modeIdx by rememberSaveable { mutableStateOf((initialMode ?: GameMode.CLASSIC).ordinal) }
    val mode = GameMode.entries[modeIdx.coerceAtMost(GameMode.entries.lastIndex)]
    val extra = modeIdx >= GameMode.entries.size
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val cardBack = rememberProfile().value.cardBack

    val galleries: List<Pair<String?, List<FaceGroup>>> = remember(mode) {
        when (mode) {
            GameMode.CLASSIC -> listOf(null to groupsFor(DeckFactory.classicFaces(), mode))
            GameMode.CHAOS -> listOf(null to groupsFor(DeckFactory.chaosFaces(), mode))
            GameMode.ECLIPSE -> listOf(
                "Light Side" to groupsFor(DeckFactory.eclipseSunFaces(), mode),
                "Dark Side" to groupsFor(DeckFactory.eclipseMoonFaces(), mode),
            )
        }
    }

    RoyalBackground(rays = false) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenTitleBar("How to Play", onBack)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GameMode.entries.forEachIndexed { i, m ->
                    ChoiceChip(ModeInfo.short(m), i == modeIdx, {
                        modeIdx = i
                        scope.launch { listState.scrollToItem(0) }
                    }, color = if (i == modeIdx && m != GameMode.CLASSIC) ModeInfo.colors(m)[1] else CM.Gold)
                }
                ChoiceChip("Spider", modeIdx == GameMode.entries.size, {
                    modeIdx = GameMode.entries.size
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFF0E6B3A))
                ChoiceChip("Mindi", modeIdx == GameMode.entries.size + 1, {
                    modeIdx = GameMode.entries.size + 1
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFFC0392B))
            }
            if (extra) {
                ExtraGameRules(if (modeIdx == GameMode.entries.size) ExtraGame.SPIDER else ExtraGame.MINDI)
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero-$mode") { ModeHero(mode) }
                item(key = "basics") { RulesPanel(basics, CM.Gold) }
                item(key = "mode-$mode") { RulesPanel(modeRules(mode), ModeInfo.colors(mode)[0]) }
                galleries.forEach { (side, groups) ->
                    item(key = "gallery-$mode-$side") {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            SectionHeader(if (side == null) "Card Gallery" else "Card Gallery · $side")
                            Text("Tap a card to flip it.", color = CM.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
                        }
                    }
                    items(groups, key = { "g-$mode-$side-${it.title}" }) { g -> GalleryRow(g, cardBack) }
                }
                item(key = "spacer") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun ModeHero(mode: GameMode) {
    val colors = ModeInfo.colors(mode)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors[0].copy(alpha = 0.85f), colors[1].copy(alpha = 0.85f), Color(0xDD12042E))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTitle(ModeInfo.title(mode), fontSize = 28.sp, outlineWidth = 10f)
        Spacer(Modifier.height(4.dp))
        Text(ModeInfo.tagline(mode), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Pill(ModeInfo.deckLabel(mode), Color(0x66000000))
    }
}

@Composable
private fun RulesPanel(section: RuleSection, accent: Color) {
    GlassPanel(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(section.title.uppercase(), color = CM.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        section.bullets.forEach { GemBullet(it, accent) }
    }
}

@Composable
private fun GalleryRow(g: FaceGroup, cardBack: CardBack) {
    val app = LocalApp.current
    val rot = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x2EFFFFFF), Color(0x10FFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val n = g.faces.size
        val cardW = 60.dp
        val step = 20
        Box(
            Modifier
                .width(cardW + (step * 3).dp)
                .height(96.dp)
                .pressable(sound = Sfx.CARD_SLIDE, pressedScale = 0.95f) {
                    if (!rot.isRunning) scope.launch {
                        rot.snapTo(0f)
                        rot.animateTo(360f, tween(750, easing = FastOutSlowInEasing))
                        rot.snapTo(0f)
                    }
                    app.haptics.tick()
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val r = rot.value
            val backSide = r in 90f..270f
            g.faces.forEachIndexed { i, f ->
                val k = i - (n - 1) / 2f
                Box(
                    Modifier
                        .offset(x = (i * step).dp + ((3 - (n - 1)) * step / 2).dp)
                        .graphicsLayer {
                            rotationZ = k * 7f
                            rotationY = r
                            cameraDistance = 10f * density
                            if (backSide) scaleX = -1f
                        }
                ) {
                    if (backSide) BackImage(cardBack, cardW, elevation = 6.dp) else FaceImage(f, cardW, elevation = 6.dp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(g.description, color = CM.Text, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(g.points, color = CM.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ------------------------------------------------------------------------------------------- Spider & Mindi

private enum class ExtraGame { SPIDER, MINDI }

private val spiderRules = listOf(
    RuleSection(
        "Goal",
        listOf(
            "Build 8 complete runs from King down to Ace, each in a single suit. A finished run leaves the table on its own.",
            "Clear every run to win. Score starts at 500: each move or undo costs 1 point, each finished run earns 100.",
        )
    ),
    RuleSection(
        "The table",
        listOf(
            "104 cards in 10 columns. The first four columns get 6 cards, the other six get 5; only the bottom card is face up.",
            "The remaining 50 cards wait in the stock (top right) and come out 10 at a time — one onto every column.",
            "Choose 1 suit (easy), 2 suits (medium) or 4 suits (hard).",
        )
    ),
    RuleSection(
        "Moving cards",
        listOf(
            "Put any face-up card on a card exactly one rank higher, in any suit — a 7 goes on any 8.",
            "A group of cards moves together only when it runs down in order and is all one suit.",
            "Any card or same-suit run can go into an empty column.",
            "When a face-down card is uncovered it turns over automatically.",
            "Tap a card to send it to the best spot, or drag it where you want it.",
        )
    ),
    RuleSection(
        "Dealing, hints & undo",
        listOf(
            "Tap the stock to deal. Every column needs at least one card first.",
            "Hint shows a good move (tap again for another). Undo is unlimited.",
            "Daily Challenge: one winnable deal per day, the same for every player.",
        )
    ),
)

private val mindiRules = listOf(
    RuleSection(
        "Teams & deal",
        listOf(
            "Mindi is a team trick-taking game. With 4 players, partners sit opposite each other (2 vs 2).",
            "Big tables: 6 players (3 vs 3) or 8 players (4 vs 4), seated alternately, with the four 2s removed (48 cards). A double deck is also possible.",
            "The whole deck is dealt out. The player after the dealer leads the first trick.",
        )
    ),
    RuleSection(
        "Playing a trick",
        listOf(
            "Everyone plays one card. You must follow the suit that was led if you can; otherwise play any card.",
            "The highest trump wins the trick. With no trump in it, the highest card of the suit led wins (A high, 2 low).",
            "The winner of a trick leads the next one.",
        )
    ),
    RuleSection(
        "Choosing trump",
        listOf(
            "Hide Trump: one player secretly sets aside a card; its suit is trump. It's revealed the first time anyone can't follow suit — from then on that suit is trump.",
            "Cut Trump: there's no trump at first. The first card played by someone who can't follow suit decides trump for the rest of the hand.",
            "Open Trump: trump is shown before play — the easiest way to learn.",
        )
    ),
    RuleSection(
        "The 10s win the hand",
        listOf(
            "Only the four 10s (the \"mindis\") count. The team that captures 3 or 4 of them wins the hand.",
            "If the 10s are split 2–2, the team with more tricks wins.",
            "Mendikot: capturing all four 10s. Whitewash: winning every single trick.",
            "Points: a normal win is 1, a Mendikot 2, a whitewash 3. First team to the target (5 by default) wins the match.",
            "The losing team deals the next hand.",
        )
    ),
)

@Composable
private fun ExtraGameRules(game: ExtraGame) {
    val sections = if (game == ExtraGame.SPIDER) spiderRules else mindiRules
    val accent = if (game == ExtraGame.SPIDER) Color(0xFF3FBF6E) else Color(0xFFFFB13B)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero-$game") { ExtraHero(game) }
        sections.forEach { s -> item(key = "s-$game-${s.title}") { RulesPanel(s, accent) } }
        item(key = "examples-$game") {
            GlassPanel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Text("EXAMPLES", color = CM.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                if (game == ExtraGame.SPIDER) {
                    ExampleRow(listOf(13 to 0, 12 to 0, 11 to 0, 10 to 0), "A same-suit run (K-Q-J-10) moves as one group.")
                    ExampleRow(listOf(8 to 0, 7 to 1), "A 7 of hearts can go on an 8 of spades — but they can't move together.")
                } else {
                    ExampleRow(listOf(10 to 0, 10 to 1, 10 to 2, 10 to 3), "The four 10s. Capture 3 or more with your partner to win the hand.")
                    ExampleRow(listOf(5 to 1, 12 to 1, 3 to 0), "Hearts were led; a player out of hearts can play a trump (here spades) to win.")
                }
            }
        }
        item(key = "spacer-$game") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun ExtraHero(game: ExtraGame) {
    val spider = game == ExtraGame.SPIDER
    val colors = if (spider) listOf(Color(0xFF3FBF6E), Color(0xFF0E6B3A)) else listOf(Color(0xFFFFB13B), Color(0xFFC0392B))
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors[0].copy(alpha = 0.85f), colors[1].copy(alpha = 0.9f), Color(0xDD12042E))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTitle(if (spider) "Spider Solitaire" else "Mindi", fontSize = 28.sp, outlineWidth = 10f)
        Spacer(Modifier.height(4.dp))
        Text(
            if (spider) "Patience classic: build King-to-Ace runs." else "Partner up and capture the 10s.",
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Pill(if (spider) "Solo · 1, 2 or 4 suits" else "4, 6 or 8 players · teams", Color(0x66000000))
    }
}

@Composable
private fun ExampleRow(cards: List<Pair<Int, Int>>, caption: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(110.dp).height(72.dp), contentAlignment = Alignment.CenterStart) {
            cards.forEachIndexed { i, (rank, suit) ->
                androidx.compose.foundation.Image(
                    com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(rank, suit), null,
                    Modifier.offset(x = (i * 18).dp).width(46.dp).height(64.dp).graphicsLayer { rotationZ = (i - cards.size / 2f) * 5f }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(caption, color = CM.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}
