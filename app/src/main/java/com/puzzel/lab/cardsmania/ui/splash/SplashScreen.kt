package com.puzzel.lab.cardsmania.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Track
import com.puzzel.lab.cardsmania.engine.model.DeckFactory
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Suit
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.kit.FaceImage
import com.puzzel.lab.cardsmania.ui.kit.GemBadge
import com.puzzel.lab.cardsmania.ui.kit.GemProgressBar
import com.puzzel.lab.cardsmania.ui.kit.ProfileCache
import com.puzzel.lab.cardsmania.ui.kit.SettingsCache
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val messages = listOf(
    R.string.shell_splash_msg_shuffle, R.string.shell_splash_msg_polish, R.string.shell_splash_msg_wake, R.string.shell_splash_msg_table,
)

@Composable
fun SplashScreen(onFinished: (onboardingDone: Boolean, profileCreated: Boolean) -> Unit) {
    val app = LocalApp.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        remember { CardArt.configure((widthPx * 0.22f).toInt()) }

        val badge = remember { Animatable(0f) }
        val fan = remember { Animatable(0f) }
        val title = remember { Animatable(0f) }
        val progress = remember { Animatable(0f) }
        var msg by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            app.music.play(Track.MENU)
            val start = System.currentTimeMillis()
            val warm = async(Dispatchers.Default) {
                val settings = runCatching { app.settings.current() }.getOrNull()
                val profile = runCatching { app.profile.current() }.getOrNull()
                if (settings != null) SettingsCache.last = settings
                if (profile != null) ProfileCache.last = profile
                val cb = settings?.colorBlind ?: true
                runCatching {
                    DeckFactory.distinctFaces(GameMode.CLASSIC).forEach { CardArt.face(it, cb) }
                    if (!cb) DeckFactory.distinctFaces(GameMode.CLASSIC).forEach { CardArt.face(it, true) }
                    profile?.let { CardArt.back(it.cardBack) }
                }
                (settings?.onboardingDone ?: false) to (profile?.profileCreated ?: false)
            }
            launch { delay(80); badge.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
            launch { delay(380); fan.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 170f)) }
            launch { delay(700); title.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 220f)) }
            launch {
                while (true) {
                    delay(650)
                    msg = (msg + 1) % messages.size
                }
            }
            progress.animateTo(0.85f, tween(1700, easing = FastOutSlowInEasing))
            val (onboarding, created) = warm.await()
            progress.animateTo(1f, tween(260))
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 2200) delay(2200 - elapsed)
            onFinished(onboarding, created)
        }

        val inf = rememberInfiniteTransition(label = "splash")
        val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
        val sweep by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "sweep")
        val bob by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "bob")

        RoyalBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(320.dp, 270.dp), contentAlignment = Alignment.Center) {
                    // rotating glow ring
                    Canvas(Modifier.size(260.dp).graphicsLayer { alpha = badge.value.coerceIn(0f, 1f) }) {
                        val c = center
                        drawCircle(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.45f), Color.Transparent), c, size.minDimension / 2), size.minDimension / 2, c)
                        rotate(spin, c) {
                            for (i in 0 until 12) {
                                val a = (i * PI / 6).toFloat()
                                val r1 = size.minDimension * 0.32f
                                val r2 = size.minDimension * 0.5f
                                drawLine(
                                    CM.GoldLight.copy(alpha = 0.35f),
                                    Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                                    Offset(c.x + cos(a) * r2, c.y + sin(a) * r2),
                                    strokeWidth = 5f
                                )
                            }
                        }
                    }
                    // a Spider ace, a Mania 7 and a Mindi 10 — the games in the box
                    val cards = remember {
                        listOf(
                            com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(1, 0),
                            CardArt.face(Face(Suit.RUBY, Kind.NUMBER, 7)),
                            com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt.face(10, 1),
                        )
                    }
                    val angles = listOf(-24f, 0f, 24f)
                    val xs = listOf(-78f, 0f, 78f)
                    val ys = listOf(6f, -26f, 6f)
                    cards.forEachIndexed { i, bmp ->
                        val f = fan.value
                        val float = sin(bob + i * 1.3f) * 4f
                        com.puzzel.lab.cardsmania.ui.kit.CardImage(
                            bmp, 96.dp,
                            Modifier
                                .offset(x = (xs[i] * f).dp, y = ((ys[i] - 20f) * f + float * f).dp)
                                .graphicsLayer {
                                    rotationZ = angles[i] * f
                                    val s = 0.55f + 0.45f * f.coerceIn(0f, 1.2f)
                                    scaleX = s
                                    scaleY = s
                                    alpha = (f * 2f).coerceIn(0f, 1f)
                                },
                            elevation = 12.dp
                        )
                    }
                    GemBadge(
                        116.dp,
                        Modifier
                            .offset(y = 62.dp)
                            .graphicsLayer {
                                scaleX = badge.value
                                scaleY = badge.value
                                rotationZ = (1f - badge.value) * -90f
                            }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .graphicsLayer {
                            alpha = title.value.coerceIn(0f, 1f)
                            translationY = (1f - title.value) * 40f * this.density
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            val x = size.width * sweep
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                                    start = Offset(x - size.width * 0.18f, 0f),
                                    end = Offset(x + size.width * 0.18f, size.height)
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTitle("CARDS", fontSize = 50.sp, outlineWidth = 14f)
                        OutlinedTitle(
                            "MANIA",
                            fontSize = 58.sp,
                            outlineWidth = 16f,
                            fill = Brush.verticalGradient(listOf(Color(0xFFFFB3C9), CM.Ruby, Color(0xFF9E0F3C)))
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.shell_splash_tagline),
                    color = CM.TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.graphicsLayer { alpha = title.value.coerceIn(0f, 1f) }
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GemProgressBar(
                        progress.value,
                        Modifier.fillMaxWidth(),
                        height = 14.dp,
                        colors = listOf(Color(0xFFE0245E), Color(0xFFFFB000), Color(0xFF19C37D), Color(0xFF1E7BFF)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(messages[msg]), color = CM.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
