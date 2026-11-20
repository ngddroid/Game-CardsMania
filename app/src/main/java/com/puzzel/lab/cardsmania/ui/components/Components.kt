package com.puzzel.lab.cardsmania.ui.components

import androidx.compose.runtime.State
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.AvatarFrame
import com.puzzel.lab.cardsmania.data.TableTheme
import com.puzzel.lab.cardsmania.ui.design.GameIcons
import com.puzzel.lab.cardsmania.ui.design.shineSweep
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --------------------------------------------------------------------------- buttons

enum class ButtonStyle(val top: Color, val bottom: Color, val lip: Color, val text: Color, val outline: Color) {
    GOLD(Color(0xFFFFE27A), Color(0xFFFFB300), Color(0xFFB36B00), Color(0xFF4A2A00), Color(0x00000000)),
    GREEN(Color(0xFF5BF0A2), Color(0xFF13B36A), Color(0xFF07693D), Color.White, Color(0xFF064A2B)),
    BLUE(Color(0xFF6FB6FF), Color(0xFF1E6FE8), Color(0xFF0B3C8F), Color.White, Color(0xFF06275E)),
    RED(Color(0xFFFF7FA0), Color(0xFFE0245E), Color(0xFF8A0B33), Color.White, Color(0xFF5C0522)),
    PURPLE(Color(0xFFA889FF), Color(0xFF6A3FE0), Color(0xFF3A1A99), Color.White, Color(0xFF26106B)),
    GLASS(Color(0x40FFFFFF), Color(0x20FFFFFF), Color(0x30000000), Color.White, Color(0x00000000)),
}

/** Chunky 3D game button with a press-down lip, gloss and click sound. */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.GOLD,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    fontSize: TextUnit = 18.sp,
    sound: Sfx = Sfx.TAP,
) {
    val app = LocalApp.current
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val lip = height * 0.12f
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "press")
    val shape = RoundedCornerShape(height * 0.36f)
    Box(
        modifier
            .height(height + lip)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        pressed = true
                        val ok = tryAwaitRelease()
                        pressed = false
                        if (ok) {
                            app.sound.play(sound)
                            app.haptics.tick()
                            click()
                        }
                    }
                )
            }
    ) {
        // lip
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = lip)
                .clip(shape)
                .background(style.lip)
        )
        // face
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = lip * press)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x10FFFFFF))), shape)
                .shineSweep(enabled = enabled && (style == ButtonStyle.GOLD || style == ButtonStyle.GREEN))
                .drawBehind {
                    // gloss band
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)), 0f, size.height * 0.55f),
                        topLeft = Offset(size.width * 0.04f, size.height * 0.06f),
                        size = Size(size.width * 0.92f, size.height * 0.42f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.3f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = style.text, modifier = Modifier.size((height.value * 0.42f).dp))
                    if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
                }
                if (text.isNotEmpty()) {
                    Text(
                        text,
                        color = style.text,
                        fontSize = fontSize,
                        fontFamily = DisplayFamily,
                        letterSpacing = 0.5.sp,
                        style = TextStyle(shadow = Shadow(Color(0x55000000), Offset(0f, 3f), 4f)),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Round icon button with gem ring. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = Color.White,
    badge: String? = null,
    contentDescription: String? = null,
) {
    val app = LocalApp.current
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.45f, Spring.StiffnessMedium), label = "s")
    Box(
        modifier
            .size(size)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    val ok = tryAwaitRelease()
                    pressed = false
                    if (ok) {
                        app.sound.play(Sfx.TAP)
                        app.haptics.tick()
                        click()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF5A2DAA), Color(0xFF2A0E5C))))
                .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(CM.Ruby)
                    .border(1.5.dp, Color.White, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text(badge, color = Color.White, fontSize = 10.sp, fontFamily = DisplayFamily) }
        }
    }
}

// --------------------------------------------------------------------------- text

/** Big game-title text with a thick outline, gradient-ish fill and drop shadow. */
@Composable
fun OutlinedTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    fill: Brush = Brush.verticalGradient(listOf(CM.GoldLight, CM.Gold, CM.GoldDeep)),
    outline: Color = Color(0xFF3A1460),
    outlineWidth: Float = 10f,
    textAlign: TextAlign = TextAlign.Center,
) {
    Box(modifier) {
        Text(
            text, fontSize = fontSize, fontFamily = DisplayFamily, textAlign = textAlign, letterSpacing = 0.5.sp,
            style = TextStyle(color = Color(0x66000000), drawStyle = Stroke(outlineWidth * 1.4f, join = StrokeJoin.Round)),
            modifier = Modifier.offset(y = 3.dp)
        )
        Text(
            text, fontSize = fontSize, fontFamily = DisplayFamily, textAlign = textAlign, letterSpacing = 0.5.sp,
            style = TextStyle(color = outline, drawStyle = Stroke(outlineWidth, join = StrokeJoin.Round))
        )
        Text(text, fontSize = fontSize, fontFamily = DisplayFamily, textAlign = textAlign, letterSpacing = 0.5.sp, style = TextStyle(brush = fill))
    }
}

// --------------------------------------------------------------------------- panels

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    padding: Dp = 16.dp,
    highlight: Color = Color(0x55FFFFFF),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Beveled game panel: thin gold rim, deep royal interior, top glow and an inner light line.
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .drawBehind {
                drawRoundRect(Color(0x55000000), topLeft = Offset(0f, 4.dp.toPx()), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()))
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE0472094), Color(0xE62A1068), Color(0xF0170742))))
            .border(1.6.dp, Brush.verticalGradient(listOf(CM.GoldLight.copy(alpha = 0.85f), CM.GoldDeep.copy(alpha = 0.55f), Color(0x66C98A00))), shape)
            .drawWithCache {
                val glow = Brush.verticalGradient(listOf(highlight.copy(alpha = 0.22f), Color.Transparent), 0f, size.height * 0.3f)
                onDrawBehind {
                    drawRect(glow)
                    drawRoundRect(
                        Color.White.copy(alpha = 0.10f), topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius((corner - 2.dp).toPx()), style = Stroke(1.dp.toPx()),
                    )
                }
            }
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).graphicsLayer { rotationZ = 45f }.background(CM.Gold))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), color = CM.GoldLight, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

// --------------------------------------------------------------------------- backgrounds

/** Animated royal background: radial glow, slowly turning light rays and drifting sparkles. */
@Composable
fun RoyalBackground(
    modifier: Modifier = Modifier,
    theme: TableTheme = TableTheme.ROYAL,
    rays: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val inf = rememberInfiniteTransition(label = "bg")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "t")
    val sparkles = remember { List(36) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }
    Box(
        modifier
            .fillMaxSize()
            .background(theme.edge)
            .drawWithCache {
                val c = Offset(size.width / 2, size.height * 0.38f)
                val base = Brush.radialGradient(listOf(theme.center, theme.edge), c, size.maxDimension * 0.75f)
                val vignette = Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), c, size.maxDimension * 0.9f)
                val rayPath = Path().apply {
                    val n = 16
                    val r = size.maxDimension
                    for (i in 0 until n) {
                        val a0 = (i * 2 * PI / n).toFloat()
                        val a1 = a0 + (PI / n).toFloat() * 0.6f
                        moveTo(c.x, c.y)
                        lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r)
                        lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r)
                        close()
                    }
                }
                val rayColor = Color.White.copy(alpha = 0.035f)
                onDrawBehind {
                    drawRect(base)
                    if (rays) rotate(t * 360f, c) { drawPath(rayPath, rayColor) }
                    for (s in sparkles) {
                        val y = ((s[1] - t * (0.6f + s[2])) % 1f + 1f) % 1f
                        val x = s[0] + sin((t * 20 + s[3] * 6).toDouble()).toFloat() * 0.01f
                        val alpha = (0.25f + 0.5f * sin((t * 60 + s[3] * 10).toDouble()).toFloat().let { it * it })
                        drawCircle(theme.accent.copy(alpha = alpha * 0.8f), radius = 1.5f + s[2] * 3.5f, center = Offset(x * size.width, y * size.height))
                    }
                    drawRect(vignette)
                }
            },
        content = content
    )
}

// --------------------------------------------------------------------------- avatar & chips

@Composable
fun AvatarBadge(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    frame: AvatarFrame = AvatarFrame.GOLD,
    ringColor: Color? = null,
    glow: Float = 0f,
) {
    val colors = if (ringColor != null) listOf(ringColor.copy(alpha = 1f), ringColor.copy(alpha = 0.6f), ringColor) else frame.colors
    Box(
        modifier
            .size(size)
            .drawBehind {
                if (glow > 0f) drawCircle(Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.7f * glow), Color.Transparent)), radius = this.size.minDimension * 0.85f)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.sweepGradient(if (colors.size > 1) colors else colors + colors))
                .padding(size * 0.07f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF5A2DAA), Color(0xFF1A0844)))),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

@Composable
fun CoinIcon(size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Color(0xFFB36B00), r, center.copy(y = center.y + r * 0.08f))
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFC93C), Color(0xFFE09A00))), r * 0.92f)
        drawCircle(Color(0xFFC98A00), r * 0.62f, style = Stroke(r * 0.12f))
        val p = Path().apply {
            moveTo(center.x, center.y - r * 0.38f); lineTo(center.x + r * 0.28f, center.y)
            lineTo(center.x, center.y + r * 0.38f); lineTo(center.x - r * 0.28f, center.y); close()
        }
        drawPath(p, Color(0xFFFFF6D0))
    }
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x55000000))
            .border(1.dp, Color(0x40FFC93C), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinIcon(22.dp)
        Spacer(Modifier.width(6.dp))
        Text(formatNumber(coins), color = CM.GoldLight, fontFamily = DisplayFamily, fontSize = 16.sp)
    }
}

@Composable
fun LevelChip(level: Int, progress: Float, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x55000000))
            .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = 45f }
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFC0A6FF), Color(0xFF5B30C2)))),
            contentAlignment = Alignment.Center
        ) {
            Text("$level", color = Color.White, fontFamily = DisplayFamily, fontSize = 11.sp, modifier = Modifier.graphicsLayer { rotationZ = -45f })
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(48.dp).height(7.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(7.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFFF5FA2)))))
        }
    }
}

fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f)
    n >= 10_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000f)
    else -> String.format(java.util.Locale.US, "%,d", n)
}

/** A pill-shaped selectable chip. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = CM.Gold) {
    val app = LocalApp.current
    val click by rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(50)
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, label = "chip")
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (selected) Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.7f))) else Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x14FFFFFF))))
            .border(1.5.dp, if (selected) Color.White.copy(alpha = 0.7f) else Color(0x33FFFFFF), shape)
            .pointerInput(text) {
                detectTapGestures {
                    app.sound.play(Sfx.SELECT)
                    app.haptics.tick()
                    click()
                }
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected && color == CM.Gold) Color(0xFF3A1F00) else Color.White, fontFamily = DisplayFamily, fontSize = 14.sp, letterSpacing = 0.3.sp, maxLines = 1)
    }
}

/** Gem-styled toggle switch. */
@Composable
fun GemSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val change by rememberUpdatedState(onChange)
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(0.6f, 500f), label = "sw")
    Box(
        modifier
            .width(54.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) Brush.horizontalGradient(listOf(Color(0xFF13B36A), Color(0xFF5BF0A2))) else Brush.horizontalGradient(listOf(Color(0x40000000), Color(0x30FFFFFF))))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50))
            .pointerInput(checked) {
                detectTapGestures {
                    app.sound.play(Sfx.TOGGLE)
                    app.haptics.tick()
                    change(!checked)
                }
            }
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = 24.dp * pos)
                .size(24.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCD0F5))))
        )
    }
}

/**
 * A 0..1..0 pulse. Use it as `val pulse by rememberPulse()` and read `pulse` only inside draw-phase lambdas
 * (`graphicsLayer {}`, `drawBehind {}`, a Canvas block): then each frame only redraws, instead of recomposing the
 * whole composable 60 times a second.
 */
@Composable
fun rememberPulse(period: Int = 1200): State<Float> {
    val inf = rememberInfiniteTransition(label = "pulse")
    return inf.animateFloat(0f, 1f, infiniteRepeatable(tween(period), RepeatMode.Reverse), label = "p")
}

@Composable
fun ScreenTitleBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(GameIcons.Back, onBack, size = 44.dp, contentDescription = stringResource(R.string.shell_back))
        Spacer(Modifier.width(12.dp))
        OutlinedTitle(title, fontSize = 26.sp, outlineWidth = 8f, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        trailing?.invoke()
    }
}
