package com.ngdstudio.mindimania.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.GameFactory
import com.ngdstudio.mindimania.data.ModeInfo
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.data.Progression
import com.ngdstudio.mindimania.engine.model.Face
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.engine.model.Suit
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.design.GameIcons
import com.ngdstudio.mindimania.ui.design.HudBar
import com.ngdstudio.mindimania.ui.design.RibbonHeader
import com.ngdstudio.mindimania.ui.design.StrokeText
import com.ngdstudio.mindimania.ui.design.TagPill
import com.ngdstudio.mindimania.ui.design.lerpColor
import com.ngdstudio.mindimania.ui.design.shineSweep
import com.ngdstudio.mindimania.ui.kit.TreasureChest
import com.ngdstudio.mindimania.ui.kit.pressable
import com.ngdstudio.mindimania.ui.kit.rememberProfile
import com.ngdstudio.mindimania.ui.kit.rememberSettings
import com.ngdstudio.mindimania.ui.kit.staggerIn
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.theme.CM
import com.ngdstudio.mindimania.ui.theme.DisplayFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** The games shown in the lobby. [titleRes]/[tagRes] are resolved in the composables. */
private enum class LobbyGame(@StringRes val titleRes: Int, @StringRes val tagRes: Int, val colors: List<Color>, val rim: Color) {
    CLASSIC(R.string.shell_game_classic, R.string.shell_tag_classic, listOf(Color(0xFFFFB347), Color(0xFFE0245E), Color(0xFF7A0F3A)), Color(0xFFFFE27A)),
    MERCILESS(R.string.shell_game_merciless, R.string.shell_tag_merciless, listOf(Color(0xFFFF6A2A), Color(0xFF9E1030), Color(0xFF3A0614)), Color(0xFFFFB27A)),
    FLIP(R.string.shell_game_flip, R.string.shell_tag_flip, listOf(Color(0xFF3FD0E8), Color(0xFF5B30C2), Color(0xFF1E0B57)), Color(0xFFB6DBFF)),
    SPIDER(R.string.shell_game_spider, R.string.shell_tag_spider, listOf(Color(0xFF3FBF6E), Color(0xFF0E6B3A), Color(0xFF04331C)), Color(0xFFB8FFD9)),
    MINDI(R.string.shell_game_mindi, R.string.shell_tag_mindi, listOf(Color(0xFFFFB13B), Color(0xFFC0392B), Color(0xFF4A0E2A)), Color(0xFFFFE08A)),
    BHABHI(R.string.shell_game_bhabhi, R.string.shell_tag_bhabhi, listOf(Color(0xFFFF6FB5), Color(0xFFB0206A), Color(0xFF3A0B4D)), Color(0xFFFFC2E6)),
    TEEN_PATTI(R.string.shell_game_teenpatti, R.string.shell_tag_teenpatti, listOf(Color(0xFFFF8A4C), Color(0xFFE0245E), Color(0xFF4A0E5C)), Color(0xFFFFD08A)),
    RUMMY(R.string.shell_game_rummy, R.string.shell_tag_rummy, listOf(Color(0xFF3FD08A), Color(0xFF13734F), Color(0xFF0B3B2B)), Color(0xFFB8FFD9)),
}

@Composable
fun HomeTab(
    onOpenSetup: (GameMode?) -> Unit,
    onQuickPlay: () -> Unit,
    onResume: () -> Unit,
    onDaily: () -> Unit,
    onSettings: () -> Unit,
    onHowTo: (GameMode?) -> Unit,
    onMultiplayer: () -> Unit = {},
    onSpider: () -> Unit = {},
    onMindi: () -> Unit = {},
    onBhabhi: () -> Unit = {},
    onTeenPatti: () -> Unit = {},
    onRummy: () -> Unit = {},
    onOpenEvents: () -> Unit = onDaily,
    onGift: (amount: Int, streak: Int) -> Unit = { _, _ -> },
) {
    val app = LocalApp.current
    val profile by rememberProfile()
    val scope = rememberCoroutineScope()
    var hasSave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasSave = app.savedGames.exists() }

    fun claimGift() {
        scope.launch {
            var amount = 0
            var streak = 0
            app.profile.update {
                if (!Progression.giftReady(it)) it else {
                    val (np, a) = Progression.claimGift(it)
                    amount = a
                    streak = np.giftStreak
                    np
                }
            }
            if (amount > 0) {
                com.ngdstudio.mindimania.analytics.Analytics.event("daily_gift", "coins" to amount, "streak" to streak)
                app.sound.play(Sfx.CHEST)
                app.haptics.medium()
                onGift(amount, streak)
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        HudBar(
            profile,
            modifier = Modifier.staggerIn(0),
            onCoins = { if (Progression.giftReady(profile)) claimGift() else onOpenEvents() },
            onHelp = { onHowTo(null) },
            onSettings = onSettings,
        )
        Spacer(Modifier.height(4.dp))
        QuickButtons(profile, hasSave, onGift = ::claimGift, onEvents = onOpenEvents, onResume = onResume, modifier = Modifier.staggerIn(1))
        Spacer(Modifier.height(10.dp))
        RibbonHeader(stringResource(R.string.shell_lobby_pick_game), Modifier.align(Alignment.CenterHorizontally).staggerIn(2), color = Color(0xFFB0203F), icon = GameIcons.Lobby)
        Spacer(Modifier.height(10.dp))
        var section by remember { mutableStateOf(app.lobbySection.coerceIn(0, LobbySection.entries.lastIndex)) }
        SectionTabs(section, Modifier.padding(horizontal = 16.dp).staggerIn(3)) {
            section = it
            app.lobbySection = it
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { it * dir / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it * dir / 4 } + fadeOut(tween(180)))
            },
            label = "lobby-section",
            modifier = Modifier.fillMaxWidth(),
        ) { sec ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (LobbySection.entries[sec]) {
                    LobbySection.CARD_MANIA -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GameTile(LobbyGame.CLASSIC, 0, Modifier.weight(1f).staggerIn(4)) { onOpenSetup(GameMode.CLASSIC) }
                            GameTile(LobbyGame.MERCILESS, 1, Modifier.weight(1f).staggerIn(4)) { onOpenSetup(GameMode.CHAOS) }
                        }
                        WideGameTile(LobbyGame.FLIP, Modifier.staggerIn(5)) { onOpenSetup(GameMode.ECLIPSE) }
                        GameButton(
                            stringResource(R.string.shell_lobby_quick_play), onQuickPlay, style = ButtonStyle.GREEN, icon = GameIcons.Bolt,
                            modifier = Modifier.fillMaxWidth().staggerIn(6), height = 58.dp, fontSize = 21.sp, sound = Sfx.SELECT,
                        )
                        Text(
                            stringResource(R.string.shell_lobby_quick_hint, ModeInfo.title(profile.setup.mode)),
                            color = CM.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LobbySection.INDIAN_CARD -> {
                        Text(
                            stringResource(R.string.shell_lobby_indian_intro),
                            color = CM.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        WideGameTile(LobbyGame.MINDI, Modifier.staggerIn(4), onClick = onMindi)
                        WideGameTile(LobbyGame.RUMMY, Modifier.staggerIn(4), badge = stringResource(R.string.shell_lobby_badge_new), onClick = onRummy)
                        WideGameTile(LobbyGame.TEEN_PATTI, Modifier.staggerIn(4), onClick = onTeenPatti)
                        WideGameTile(LobbyGame.BHABHI, Modifier.staggerIn(5), onClick = onBhabhi)
                        WideGameTile(LobbyGame.SPIDER, Modifier.staggerIn(6), onClick = onSpider)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        RibbonHeader(stringResource(R.string.shell_lobby_play_friends), Modifier.align(Alignment.CenterHorizontally).staggerIn(6), color = Color(0xFF1E6FE8), icon = GameIcons.Wifi)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MultiplayerTile(onMultiplayer, Modifier.staggerIn(7))
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The two lobby sections, in tab order: the traditional Indian card games first (the lobby opens on them), then the
 * color-matching Mania games.
 */
private enum class LobbySection(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int, val icon: String, val colors: List<Color>) {
    INDIAN_CARD(R.string.shell_section_indian_card, R.string.shell_section_indian_card_sub, "🪔", listOf(Color(0xFFFF8F00), Color(0xFFE0245E), Color(0xFF8E24AA))),
    CARD_MANIA(R.string.shell_section_card_mania, R.string.shell_section_card_mania_sub, "🎨", listOf(Color(0xFFE0245E), Color(0xFFFF9F1A), Color(0xFF1E88E5))),
}

/** Segmented, game-style switch between the two lobby sections. */
@Composable
private fun SectionTabs(selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0B3D), Color(0xFF0E0526))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LobbySection.entries.forEachIndexed { i, sec ->
            val on = i == selected
            val segShape = RoundedCornerShape(17.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(segShape)
                    .background(if (on) Brush.linearGradient(sec.colors) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .then(if (on) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), segShape) else Modifier)
                    .pressable(onClick = {
                        if (!on) {
                            app.sound.play(Sfx.SELECT)
                            app.haptics.tick()
                            onSelect(i)
                        }
                    }, sound = null, pressedScale = 0.96f)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val secTitle = stringResource(sec.titleRes)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sec.icon, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    if (on) StrokeText(secTitle, fontSize = 18.sp, stroke = Color(0x99000000), strokeWidth = 6f)
                    else Text(secTitle, color = CM.TextDim, fontFamily = DisplayFamily, fontSize = 18.sp)
                }
                Text(
                    stringResource(sec.subtitleRes), color = if (on) Color.White.copy(alpha = 0.92f) else CM.TextMuted,
                    fontSize = 11.sp, maxLines = 1,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- quick buttons

@Composable
private fun QuickButtons(profile: Profile, hasSave: Boolean, onGift: () -> Unit, onEvents: () -> Unit, onResume: () -> Unit, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val giftReady = Progression.giftReady(profile, now)
    val left = (Progression.GIFT_COOLDOWN_MS - (now - profile.lastGiftAt)).coerceAtLeast(0L) / 1000
    val dailyStars = profile.daily[GameFactory.today()] ?: 0
    val inf = rememberInfiniteTransition(label = "quick")
    val wiggle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "w")
    val reduce = rememberSettings().value.reduceMotion
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickButton(
            label = if (giftReady) stringResource(R.string.shell_quick_open) else String.format(java.util.Locale.US, "%02d:%02d:%02d", left / 3600, (left / 60) % 60, left % 60),
            caption = stringResource(R.string.shell_quick_daily_gift),
            colors = listOf(Color(0xFFFFB13B), Color(0xFFB4560A)),
            badge = giftReady,
            modifier = Modifier.weight(1f),
            onClick = onGift,
        ) {
            TreasureChest(0f, Modifier.size(46.dp).graphicsLayer {
                // the wiggle is read here, in the draw layer, so the quick buttons don't recompose every frame
                val w = wiggle
                rotationZ = if (giftReady && !reduce && w < 0.3f) sin(w / 0.3f * 6 * PI).toFloat() * 9f else 0f
                alpha = if (giftReady) 1f else 0.75f
            })
        }
        QuickButton(
            label = if (dailyStars > 0) "★ $dailyStars/3" else stringResource(R.string.shell_play),
            caption = stringResource(R.string.shell_quick_challenges),
            colors = listOf(Color(0xFFFF5FA2), Color(0xFF7A1050)),
            badge = dailyStars == 0,
            modifier = Modifier.weight(1f),
            onClick = onEvents,
        ) { Icon(GameIcons.Calendar, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
        if (hasSave) {
            QuickButton(
                label = stringResource(R.string.shell_quick_resume), caption = stringResource(R.string.shell_quick_continue),
                colors = listOf(Color(0xFF5BF0A2), Color(0xFF0A7A45)),
                modifier = Modifier.weight(1f), onClick = onResume,
            ) { Icon(GameIcons.Play, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        } else {
            QuickButton(
                label = "${profile.achievements.size}", caption = stringResource(R.string.shell_trophies),
                colors = listOf(Color(0xFF8B5CF6), Color(0xFF3A1784)),
                modifier = Modifier.weight(1f), onClick = onEvents,
            ) { Icon(GameIcons.Trophy, null, tint = CM.GoldLight, modifier = Modifier.size(36.dp)) }
        }
    }
}

@Composable
private fun QuickButton(
    label: String,
    caption: String,
    colors: List<Color>,
    modifier: Modifier,
    badge: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(onClick = onClick, pressedScale = 0.94f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(lerpColor(colors[0], Color.White, 0.15f), colors[0], colors[1])))
                .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), colors[1])), shape)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center, content = icon)
            StrokeText(label, fontSize = 14.sp, stroke = lerpColor(colors[1], Color.Black, 0.4f), strokeWidth = 5f)
            Text(caption, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
        }
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(16.dp).clip(CircleShape)
                    .background(CM.Ruby).border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

// ------------------------------------------------------------------------------------------- game tiles

private fun maniaFaces(g: LobbyGame): List<Face> = when (g) {
    LobbyGame.CLASSIC -> listOf(Face(Suit.SAPPHIRE, Kind.REVERSE), Face(Suit.RUBY, Kind.NUMBER, 7), Face(Suit.WILD, Kind.PRISM_DRAW, 4))
    LobbyGame.MERCILESS -> listOf(Face(Suit.EMERALD, Kind.PURGE), Face(Suit.WILD, Kind.PRISM_DRAW, 10), Face(Suit.AMBER, Kind.DRAW, 4))
    LobbyGame.FLIP -> listOf(Face(Suit.AMBER, Kind.ECLIPSE), Face(Suit.VIOLET, Kind.NUMBER, 9), Face(Suit.WILD, Kind.PRISM_HUNT))
    else -> emptyList()
}

@Composable
private fun tileCards(g: LobbyGame): List<ImageBitmap> = remember(g) {
    when (g) {
        LobbyGame.SPIDER -> listOf(PlayingCardArt.face(13, 0), PlayingCardArt.face(12, 0), PlayingCardArt.face(1, 0))
        LobbyGame.MINDI -> listOf(PlayingCardArt.face(10, 0), PlayingCardArt.face(10, 1), PlayingCardArt.face(10, 3), PlayingCardArt.face(10, 2))
        LobbyGame.BHABHI -> listOf(PlayingCardArt.face(13, 1), PlayingCardArt.face(7, 3), PlayingCardArt.face(9, 2), PlayingCardArt.face(1, 0))
        LobbyGame.TEEN_PATTI -> listOf(PlayingCardArt.face(1, 1), PlayingCardArt.face(1, 0), PlayingCardArt.face(1, 2))
        LobbyGame.RUMMY -> listOf(PlayingCardArt.face(5, 1), PlayingCardArt.face(6, 1), PlayingCardArt.face(7, 1), PlayingCardArt.face(8, 1))
        else -> maniaFaces(g).map { CardArt.face(it) }
    }
}

/** Shared tile frame: game gradient, sunburst, gold rim, inner glow. */
private fun Modifier.tileFrame(g: LobbyGame, corner: Dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(Brush.verticalGradient(listOf(g.rim, lerpColor(g.rim, Color.Black, 0.55f))))
    .padding(3.dp)
    .clip(RoundedCornerShape(corner - 3.dp))
    .drawWithCache {
        val bg = Brush.radialGradient(g.colors, Offset(size.width / 2, size.height * 0.3f), size.maxDimension * 0.85f)
        val rays = Brush.sweepGradient(
            listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.0f)),
            Offset(size.width / 2, size.height * 0.35f),
        )
        val bottom = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), size.height * 0.5f, size.height)
        onDrawBehind {
            drawRect(bg)
            drawRect(rays)
            drawRect(bottom)
            drawRoundRect(
                Color.White.copy(alpha = 0.25f), topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                cornerRadius = CornerRadius((corner - 4.dp).toPx()), style = Stroke(1.dp.toPx()),
            )
        }
    }

@Composable
private fun CardFan(cards: List<ImageBitmap>, cardW: Dp, index: Int, modifier: Modifier = Modifier, spread: Float = 16f) {
    val reduce = rememberSettings().value.reduceMotion
    val inf = rememberInfiniteTransition(label = "fan")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t")
    // read only inside graphicsLayer {}: the fan bobs every frame without recomposing the tile
    fun bob(): Float = if (reduce) 0f else sin((t + index * 0.21f) * 2 * PI).toFloat()
    Box(modifier, contentAlignment = Alignment.Center) {
        val n = cards.size
        cards.forEachIndexed { i, bmp ->
            val k = i - (n - 1) / 2f
            val lift = 3f * (if (i == n / 2) 1.4f else 1f)
            Image(
                bmp, null,
                Modifier
                    .width(cardW)
                    .aspectRatio(1f / 1.4f)
                    .offset(x = (k * cardW.value * 0.42f).dp, y = (kotlin.math.abs(k) * 4f).dp)
                    .graphicsLayer {
                        val b = bob()
                        translationY = -(b * lift).dp.toPx()
                        rotationZ = k * spread + b * 2f
                        shadowElevation = 8.dp.toPx()
                        shape = RoundedCornerShape(cardW * 0.09f)
                        clip = false
                    }
            )
        }
    }
}

@Composable
private fun GameTile(g: LobbyGame, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val cards = tileCards(g)
    Box(modifier.aspectRatio(0.72f).pressable(onClick = onClick, sound = Sfx.SELECT, pressedScale = 0.95f)) {
        Box(Modifier.fillMaxSize().tileFrame(g, 22.dp)) {
            CardFan(cards, 44.dp, index, Modifier.fillMaxWidth().height(100.dp).padding(top = 10.dp))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StrokeText(
                    stringResource(g.titleRes), fontSize = 18.sp, stroke = lerpColor(g.colors.last(), Color.Black, 0.5f), strokeWidth = 7f,
                    maxLines = 2, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                TagPill(stringResource(g.tagRes))
                Spacer(Modifier.height(6.dp))
                PlayRibbon()
            }
        }
    }
}

@Composable
private fun PlayRibbon() {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFB300))))
            .border(1.5.dp, Color(0xFFB36B00), RoundedCornerShape(50))
            .shineSweep()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GameIcons.Play, null, tint = Color(0xFF4A2A00), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.shell_play), color = Color(0xFF4A2A00), fontFamily = DisplayFamily, fontSize = 14.sp)
    }
}

private fun wideIcon(g: LobbyGame): ImageVector = when (g) {
    LobbyGame.BHABHI -> GameIcons.Spade
    LobbyGame.TEEN_PATTI -> GameIcons.Star
    LobbyGame.RUMMY -> GameIcons.Team
    LobbyGame.MINDI -> GameIcons.Team
    LobbyGame.SPIDER -> GameIcons.Star
    else -> GameIcons.Refresh
}

@StringRes
private fun wideBlurb(g: LobbyGame): Int = when (g) {
    LobbyGame.BHABHI -> R.string.shell_blurb_bhabhi
    LobbyGame.TEEN_PATTI -> R.string.shell_blurb_teenpatti
    LobbyGame.RUMMY -> R.string.shell_blurb_rummy
    LobbyGame.MINDI -> R.string.shell_blurb_mindi
    LobbyGame.SPIDER -> R.string.shell_blurb_spider
    LobbyGame.FLIP -> R.string.shell_blurb_flip
    LobbyGame.MERCILESS -> R.string.shell_blurb_merciless
    LobbyGame.CLASSIC -> R.string.shell_blurb_classic
}

@Composable
private fun WideGameTile(g: LobbyGame, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    val cards = tileCards(g)
    // grows with its text (large font settings wrap the description) so the PLAY ribbon is never cut off
    Box(modifier.fillMaxWidth().heightIn(min = 132.dp).pressable(onClick = onClick, sound = Sfx.SELECT, pressedScale = 0.97f)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 132.dp).tileFrame(g, 24.dp), verticalAlignment = Alignment.CenterVertically) {
            CardFan(cards, 46.dp, 4, Modifier.width(150.dp).height(120.dp), spread = 13f)
            Column(Modifier.weight(1f).padding(end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                TagPill(stringResource(g.tagRes), icon = wideIcon(g))
                Spacer(Modifier.height(4.dp))
                val title = stringResource(g.titleRes).replace('\n', ' ')
                StrokeText(
                    title, fontSize = if (title.length <= 6) 30.sp else if (title.length <= 10) 26.sp else 22.sp,
                    stroke = lerpColor(g.colors.last(), Color.Black, 0.5f), strokeWidth = 9f, maxLines = 1,
                )
                Text(stringResource(wideBlurb(g)), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                PlayRibbon()
            }
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = (-8).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6BEFB0), Color(0xFF19C37D))))
                    .border(1.5.dp, Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) { Text(badge, color = Color(0xFF04331C), fontFamily = DisplayFamily, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun MultiplayerTile(onClick: () -> Unit, modifier: Modifier) {
    val reduce = rememberSettings().value.reduceMotion
    val inf = rememberInfiniteTransition(label = "mp")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    val rainbow = remember { listOf(Color(0xFFE53935), Color(0xFFFDC500), Color(0xFF2EAD4B), Color(0xFF1E88E5), Color(0xFF7E3FD6), Color(0xFFE53935)) }
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onClick = onClick, pressedScale = 0.97f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF5B21B6), Color(0xFFBE185D), Color(0xFF1E6FE8))))
                .drawWithCache {
                    // a moving rainbow border, drawn in the draw phase so the tile doesn't recompose every frame.
                    // The stroke is centred on the edge and the outer half is clipped away: 3dp visible, like border().
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val stroke = Stroke(3.dp.toPx() * 2)
                    onDrawWithContent {
                        drawContent()
                        val brush = Brush.linearGradient(
                            rainbow, start = Offset(t * 1200f - 600f, 0f), end = Offset(t * 1200f, 400f),
                            tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                        )
                        drawOutline(outline, brush, style = stroke)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(58.dp)) {
                    for (i in 0 until 2) {
                        val p = if (reduce) 0.5f else (t * 2f + i * 0.5f) % 1f
                        drawCircle(Color.White.copy(alpha = (1f - p) * 0.6f), radius = size.minDimension / 2 * (0.55f + 0.45f * p), style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(Color.White.copy(alpha = 0.25f), radius = size.minDimension * 0.3f)
                }
                Icon(GameIcons.Wifi, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StrokeText(stringResource(R.string.shell_mp_title), fontSize = 21.sp, stroke = Color(0xFF2A0E5C), strokeWidth = 7f)
                Text(stringResource(R.string.shell_mp_line1), color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
                Text(stringResource(R.string.shell_mp_line2), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Icon(GameIcons.Forward, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
