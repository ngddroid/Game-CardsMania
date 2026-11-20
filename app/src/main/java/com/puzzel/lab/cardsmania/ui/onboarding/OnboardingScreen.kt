package com.puzzel.lab.cardsmania.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.Characters
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Suit
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.kit.BackImage
import com.puzzel.lab.cardsmania.ui.kit.CoinBurst
import com.puzzel.lab.cardsmania.ui.kit.EmoteBubble
import com.puzzel.lab.cardsmania.ui.kit.FaceImage
import com.puzzel.lab.cardsmania.ui.kit.GemBadge
import com.puzzel.lab.cardsmania.ui.kit.Pill
import com.puzzel.lab.cardsmania.ui.kit.TreasureChest
import com.puzzel.lab.cardsmania.ui.render.CardBack
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class OnboardPage(@StringRes val title: Int, @StringRes val body: Int)

private val pages = listOf(
    OnboardPage(R.string.shell_onb1_title, R.string.shell_onb1_body),
    OnboardPage(R.string.shell_onb2_title, R.string.shell_onb2_body),
    OnboardPage(R.string.shell_onb3_title, R.string.shell_onb3_body),
    OnboardPage(R.string.shell_onb4_title, R.string.shell_onb4_body),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    // back goes to the previous page (on the first page it leaves the app as usual)
    androidx.activity.compose.BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage > 0) app.sound.play(Sfx.WHOOSH, 0.6f)
    }

    RoyalBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val o = abs(offset).coerceIn(0f, 1f)
                            alpha = 1f - o * 0.6f
                            val s = 1f - o * 0.12f
                            scaleX = s
                            scaleY = s
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().weight(0.6f), contentAlignment = Alignment.Center) {
                        val active = pager.currentPage == page
                        when (page) {
                            0 -> WelcomeIllustration(active)
                            1 -> ModesIllustration(active)
                            2 -> RivalsIllustration(active)
                            else -> CollectIllustration(active)
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(0.4f).padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTitle(stringResource(pages[page].title), fontSize = 34.sp, outlineWidth = 11f)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(pages[page].body),
                            color = CM.Text,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            // page dots
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { i ->
                    val selected = i == pager.currentPage
                    val w by animateDpAsState(if (selected) 30.dp else 10.dp, spring(0.6f, 400f), label = "dot")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(w)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(CM.GoldLight, CM.Gold))
                                else Brush.horizontalGradient(listOf(Color(0x55FFFFFF), Color(0x40FFFFFF)))
                            )
                    )
                }
            }
            GameButton(
                text = if (last) stringResource(R.string.shell_onb_lets_play) else stringResource(R.string.shell_onb_next),
                onClick = {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                style = if (last) ButtonStyle.GREEN else ButtonStyle.GOLD,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                height = 60.dp,
                fontSize = 21.sp,
            )
            Spacer(Modifier.height(22.dp))
        }
    }
}

// ------------------------------------------------------------------ illustrations

@Composable
private fun rememberTime(period: Int = 4000): Float {
    val inf = rememberInfiniteTransition(label = "t")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "tv")
    return t
}

@Composable
private fun Burst(modifier: Modifier = Modifier, color: Color = CM.Gold) {
    val t = rememberTime(14000)
    Canvas(modifier) {
        val c = center
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), c, size.minDimension / 2), size.minDimension / 2, c)
        rotate(t * 360f, c) {
            for (i in 0 until 14) {
                val a0 = (i * 2 * PI / 14).toFloat()
                val a1 = a0 + 0.12f
                val r = size.minDimension * 0.55f
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r)
                    lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r)
                    close()
                }
                drawPath(p, Color.White.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun WelcomeIllustration(active: Boolean) {
    val fan = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            fan.snapTo(0f)
            delay(150)
            fan.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 150f))
        }
    }
    val t = rememberTime(5000)
    val faces = remember {
        listOf(
            Face(Suit.EMERALD, Kind.REVERSE),
            Face(Suit.SAPPHIRE, Kind.NUMBER, 9),
            Face(Suit.WILD, Kind.PRISM),
            Face(Suit.RUBY, Kind.NUMBER, 5),
            Face(Suit.AMBER, Kind.BLOCK),
        )
    }
    Box(Modifier.size(340.dp), contentAlignment = Alignment.Center) {
        Burst(Modifier.size(330.dp))
        faces.forEachIndexed { i, face ->
            val k = i - 2
            val f = fan.value
            val sway = sin((t * 2 * PI + i).toFloat()) * 3f
            FaceImage(
                face, 92.dp,
                Modifier
                    .offset(x = (k * 52f * f).dp, y = (-30f + abs(k) * 16f * f).dp)
                    .graphicsLayer {
                        rotationZ = (k * 16f + sway) * f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.2f)
                        alpha = (f * 1.5f).coerceIn(0f, 1f)
                    },
                elevation = 10.dp
            )
        }
        GemBadge(
            110.dp,
            Modifier
                .offset(y = 86.dp)
                .graphicsLayer {
                    val s = 0.6f + 0.4f * fan.value
                    scaleX = s
                    scaleY = s
                    rotationZ = sin((t * 2 * PI).toFloat()) * 4f
                }
        )
    }
}

@Composable
private fun ModesIllustration(active: Boolean) {
    val t = rememberTime(9000)
    val flip = rememberTime(2600)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            appear.snapTo(0f)
            appear.animateTo(1f, spring(0.6f, 140f))
        }
    }
    val classic = remember { Face(Suit.RUBY, Kind.NUMBER, 7) }
    val chaos = remember { Face(Suit.WILD, Kind.PRISM_DRAW, 10) }
    val sun = remember { Face(Suit.AMBER, Kind.ECLIPSE) }
    val moon = remember { Face(Suit.VIOLET, Kind.NUMBER, 4) }
    Box(Modifier.size(340.dp), contentAlignment = Alignment.Center) {
        Burst(Modifier.size(320.dp), color = CM.Violet)
        val games = listOf(
            R.string.shell_short_classic, R.string.shell_short_merciless, R.string.shell_short_flip,
            R.string.shell_short_spider, R.string.shell_game_mindi, R.string.shell_game_bhabhi,
        )
        val gameColors = listOf(ModeInfo.colors(GameMode.CLASSIC)[1], ModeInfo.colors(GameMode.CHAOS)[1], ModeInfo.colors(GameMode.ECLIPSE)[1], Color(0xFF0E6B3A), Color(0xFFC0392B), Color(0xFFB0206A))
        val spiderAce = remember { com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(1, 0) }
        val mindiTen = remember { com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(10, 1) }
        val bhabhiQueen = remember { com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(12, 1) }
        games.forEachIndexed { i, game ->
            val ang = (t * 2 * PI + i * 2 * PI / games.size).toFloat()
            val depth = sin(ang)
            val x = cos(ang) * 112f * appear.value
            val y = depth * 40f * appear.value - 20f
            val scale = 0.68f + 0.36f * (depth + 1f) / 2f
            Column(
                Modifier
                    .offset(x = x.dp, y = y.dp)
                    .zIndex(depth)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.55f + 0.45f * (depth + 1f) / 2f
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (i) {
                    0 -> FaceImage(classic, 84.dp, elevation = 12.dp)
                    1 -> FaceImage(chaos, 84.dp, elevation = 12.dp)
                    2 -> {
                        val a = (flip * 2 * PI).toFloat()
                        val c = cos(a)
                        Box(Modifier.graphicsLayer { scaleX = abs(c).coerceAtLeast(0.02f) }) {
                            FaceImage(if (c >= 0) sun else moon, 84.dp, elevation = 12.dp)
                        }
                    }
                    3 -> com.puzzel.lab.cardsmania.ui.kit.CardImage(spiderAce, 84.dp, elevation = 12.dp)
                    4 -> com.puzzel.lab.cardsmania.ui.kit.CardImage(mindiTen, 84.dp, elevation = 12.dp)
                    else -> com.puzzel.lab.cardsmania.ui.kit.CardImage(bhabhiQueen, 84.dp, elevation = 12.dp)
                }
                Spacer(Modifier.height(8.dp))
                Pill(stringResource(game), gameColors[i])
            }
        }
    }
}

@Composable
private fun RivalsIllustration(active: Boolean) {
    val t = rememberTime(2400)
    val rivals = remember {
        listOf(
            Triple(Characters.byId(9), Difficulty.EASY, "😄"),
            Triple(Characters.byId(0), Difficulty.NORMAL, "😎"),
            Triple(Characters.byId(4), Difficulty.HARD, "😤"),
            Triple(Characters.byId(6), Difficulty.GENIUS, "🤔"),
        )
    }
    val pops = remember { List(4) { Animatable(0f) } }
    LaunchedEffect(active) {
        if (active) {
            pops.forEach { it.snapTo(0f) }
            pops.forEachIndexed { i, a ->
                launch {
                    delay(150L + i * 180L)
                    a.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
                }
            }
        }
    }
    val positions = listOf(Offset(-92f, -72f), Offset(92f, -72f), Offset(-92f, 78f), Offset(92f, 78f))
    Box(Modifier.size(340.dp), contentAlignment = Alignment.Center) {
        Burst(Modifier.size(320.dp), color = CM.Sapphire)
        BackImage(CardBack.ROYAL, 74.dp, Modifier.graphicsLayer { rotationZ = -8f })
        BackImage(CardBack.ROYAL, 74.dp, Modifier.offset(x = 6.dp, y = (-4).dp).graphicsLayer { rotationZ = 6f })
        rivals.forEachIndexed { i, (ch, diff, emote) ->
            val p = pops[i].value
            val bob = sin((t * 2 * PI + i * 1.5).toFloat()) * 4f
            Column(
                Modifier
                    .offset(x = positions[i].x.dp, y = positions[i].y.dp)
                    .graphicsLayer { scaleX = p; scaleY = p },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    AvatarBadge(ch.emoji, size = 74.dp, ringColor = ch.color, glow = 0.4f)
                    EmoteBubble(emote, Modifier.align(Alignment.TopEnd).offset(x = 22.dp, y = (bob - 14f).dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(ch.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Pill(DifficultyInfo.label(diff), DifficultyInfo.color(diff))
            }
        }
    }
}

@Composable
private fun CollectIllustration(active: Boolean) {
    val open = remember { Animatable(0f) }
    val app = LocalApp.current
    var burstKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (active) {
            open.snapTo(0f)
            delay(450)
            app.sound.play(Sfx.CHEST, 0.7f)
            burstKey++
            open.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 120f))
        }
    }
    val t = rememberTime(9000)
    val backs = remember { CardBack.entries.toList() }
    BoxWithConstraints(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
        Burst(Modifier.size(320.dp), color = CM.Amber)
        // marquee of card backs
        val itemW = 70f
        val total = backs.size * (itemW + 14f)
        Box(Modifier.fillMaxWidth().offset(y = (-96).dp).height(110.dp)) {
            backs.forEachIndexed { i, b ->
                val x = ((i * (itemW + 14f) - t * total) % total + total) % total - itemW
                BackImage(
                    b, itemW.dp,
                    Modifier
                        .offset(x = x.dp, y = (sin((x / 60f).toDouble()).toFloat() * 6f).dp)
                        .graphicsLayer { rotationZ = sin((x / 90f).toDouble()).toFloat() * 8f },
                    elevation = 6.dp
                )
            }
        }
        TreasureChest(open.value, Modifier.size(190.dp).offset(y = 60.dp))
        if (burstKey > 0) CoinBurst(burstKey, Modifier.size(280.dp).offset(y = 40.dp))
    }
}
