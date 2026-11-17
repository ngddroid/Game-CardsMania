package com.puzzel.lab.cardsmania.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.puzzel.lab.cardsmania.data.TableTheme
import com.puzzel.lab.cardsmania.ui.kit.rememberSettings
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.render.CardBack
import kotlin.math.sin
import kotlin.random.Random

/** A diagonal light band that sweeps across the content every [periodMs] (off when Reduce motion is on). */
fun Modifier.shineSweep(enabled: Boolean = true, periodMs: Int = 3200, intensity: Float = 0.38f): Modifier = composed {
    val reduce = rememberSettings().value.reduceMotion
    if (!enabled || reduce) return@composed this
    val inf = rememberInfiniteTransition(label = "shine")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "t")
    drawWithContent {
        drawContent()
        // the band moves during the first 35% of the period, then rests
        val p = (t / 0.35f).coerceAtMost(1.2f)
        if (p < 1.15f) {
            val w = size.width
            val x = -w * 0.4f + p * w * 1.8f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = intensity), Color.Transparent),
                    start = Offset(x - size.height, 0f), end = Offset(x, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

enum class BackdropStyle(val center: Color, val edge: Color, val accent: Color) {
    /** Lobby: royal spotlight table. */
    LOBBY(Color(0xFF5A27A8), Color(0xFF0B0322), Color(0xFFFFC93C)),
    /** Events / rewards. */
    FESTIVE(Color(0xFF8E1F6B), Color(0xFF14041F), Color(0xFFFFB0E0)),
    /** Profile / collection. */
    VELVET(Color(0xFF3A1784), Color(0xFF07021A), Color(0xFFC0A6FF)),
    /** Mindi / green felt. */
    FELT(Color(0xFF1C7A49), Color(0xFF021A0E), Color(0xFFFFE08A)),
}

fun backdropFor(theme: TableTheme): BackdropStyle = when (theme) {
    TableTheme.FELT -> BackdropStyle.FELT
    else -> BackdropStyle.LOBBY
}

/**
 * Lobby "spotlight table" scene: a warm spotlight cone from the top, an oval table glow, depth vignette,
 * slowly drifting bokeh and twinkling sparkles. Paths/brushes are cached; only a time value changes per frame.
 */
@Composable
fun GameBackdrop(
    modifier: Modifier = Modifier,
    style: BackdropStyle = BackdropStyle.LOBBY,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = rememberSettings().value.reduceMotion
    val inf = rememberInfiniteTransition(label = "backdrop")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "t")
    val bokeh = remember(style) {
        val r = Random(style.ordinal * 31 + 7)
        List(22) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.02f + r.nextFloat() * 0.06f, r.nextFloat(), 0.04f + r.nextFloat() * 0.1f) }
    }
    val sparks = remember(style) {
        val r = Random(style.ordinal * 17 + 3)
        List(28) { floatArrayOf(r.nextFloat(), r.nextFloat(), 1f + r.nextFloat() * 2.4f, r.nextFloat() * 6.28f) }
    }
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val base = Brush.radialGradient(listOf(style.center, style.edge), Offset(w / 2, h * 0.32f), size.maxDimension * 0.8f)
                val spot = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f), Color.Transparent),
                    Offset(w / 2, h * 0.05f), h * 0.6f,
                )
                val tableGlow = Brush.radialGradient(
                    listOf(style.accent.copy(alpha = 0.10f), Color.Transparent),
                    Offset(w / 2, h * 0.58f), w * 0.9f,
                )
                val vignette = Brush.radialGradient(
                    listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    Offset(w / 2, h * 0.45f), size.maxDimension * 0.85f,
                )
                val bokehBrushes = bokeh.map { b ->
                    Brush.radialGradient(listOf(style.accent.copy(alpha = b[4]), Color.Transparent), Offset.Zero, w * b[2] * 2f)
                }
                onDrawBehind {
                    drawRect(base)
                    drawRect(spot)
                    drawRect(tableGlow)
                    val tt = if (reduce) 0f else t
                    bokeh.forEachIndexed { i, b ->
                        val x = ((b[0] + sin((tt * 6.28f * 2f + b[3] * 6f).toDouble()).toFloat() * 0.03f) % 1f) * w
                        val y = (((b[1] - tt * (0.3f + b[3] * 0.4f)) % 1f + 1f) % 1f) * h
                        translate(x, y) { drawCircle(bokehBrushes[i], radius = w * b[2] * 2f, center = Offset.Zero) }
                    }
                    for (s in sparks) {
                        val tw = if (reduce) 0.5f else (0.5f + 0.5f * sin((tt * 60f + s[3]).toDouble()).toFloat())
                        drawCircle(Color.White.copy(alpha = 0.15f + 0.55f * tw * tw), radius = s[2], center = Offset(s[0] * w, s[1] * h))
                    }
                    drawRect(vignette)
                }
            },
        content = content,
    )
}

/**
 * Card-shuffle wipe: a fan of card backs sweeps across the screen. Show it while a game screen opens.
 * Calls [onCovered] when the screen is fully covered (navigate then), and finishes by itself.
 */
@Composable
fun ShuffleWipe(visible: Boolean, back: CardBack = CardBack.ROYAL, onCovered: () -> Unit = {}, onDone: () -> Unit = {}) {
    if (!visible) return
    val reduce = rememberSettings().value.reduceMotion
    val p = remember { Animatable(0f) }
    val bmp: ImageBitmap = remember(back) { CardArt.back(back) }
    LaunchedEffect(Unit) {
        val dur = if (reduce) 250 else 520
        p.animateTo(0.5f, tween(dur, easing = FastOutSlowInEasing))
        onCovered()
        p.animateTo(1f, tween(dur, easing = FastOutSlowInEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val v = p.value
        // dark cover that peaks in the middle
        val cover = (1f - kotlin.math.abs(v - 0.5f) * 2f).coerceIn(0f, 1f)
        drawRect(Color(0xFF0B0322).copy(alpha = cover))
        val cw = size.width * 0.34f
        val ch = cw * 1.4f
        val n = 9
        for (i in 0 until n) {
            val k = i / (n - 1f)
            val x = -cw + (size.width + cw * 2) * ((v * 1.6f - k * 0.6f).coerceIn(0f, 1f))
            val y = size.height * (0.15f + 0.7f * k)
            rotate(-25f + k * 50f, Offset(x, y)) {
                drawImage(
                    bmp, srcOffset = IntOffset.Zero, srcSize = IntSize(bmp.width, bmp.height),
                    dstOffset = IntOffset((x - cw / 2).toInt(), (y - ch / 2).toInt()), dstSize = IntSize(cw.toInt(), ch.toInt()),
                    alpha = cover.coerceAtLeast(0.2f),
                )
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = Size.Zero
}
