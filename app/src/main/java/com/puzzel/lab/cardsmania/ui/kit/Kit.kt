package com.puzzel.lab.cardsmania.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.data.Settings
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.render.CardBack
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ------------------------------------------------------------------ state helpers

@Composable
fun rememberProfile(): State<Profile> {
    val app = LocalApp.current
    return app.profile.profile.collectAsState(initial = ProfileCache.last)
        .also { ProfileCache.last = it.value }
}

@Composable
fun rememberSettings(): State<Settings> {
    val app = LocalApp.current
    return app.settings.settings.collectAsState(initial = SettingsCache.last)
        .also { SettingsCache.last = it.value }
}

/** Last seen values, so screens don't flash defaults while DataStore loads. */
object ProfileCache { @Volatile var last: Profile = Profile() }
object SettingsCache { @Volatile var last: Settings = Settings() }

// ------------------------------------------------------------------ animation helpers

/** Staggered spring entrance (fade + slide up + slight scale). */
@Composable
fun Modifier.staggerIn(index: Int, step: Long = 45L, distance: Float = 50f): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * step)
        a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 240f))
    }
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * distance * density
        val s = 0.94f + 0.06f * v
        scaleX = s
        scaleY = s
    }
}

// ------------------------------------------------------------------ cards

@Composable
fun CardImage(bitmap: ImageBitmap, width: Dp, modifier: Modifier = Modifier, elevation: Dp = 8.dp) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        modifier = modifier
            .width(width)
            .aspectRatio(1f / 1.4f)
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, RoundedCornerShape(width * 0.09f), clip = false) else Modifier)
    )
}

@Composable
fun FaceImage(face: Face, width: Dp, modifier: Modifier = Modifier, elevation: Dp = 8.dp) {
    val bmp = remember(face) { CardArt.face(face) }
    CardImage(bmp, width, modifier, elevation)
}

@Composable
fun BackImage(back: CardBack, width: Dp, modifier: Modifier = Modifier, elevation: Dp = 8.dp) {
    val bmp = remember(back) { CardArt.back(back) }
    CardImage(bmp, width, modifier, elevation)
}

// ------------------------------------------------------------------ gem badge

/** Gold faceted gem with the "CM" monogram. */
@Composable
fun GemBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val c = Offset(w / 2, h / 2)
            val r = w * 0.48f
            val top = Offset(c.x, c.y - r)
            val right = Offset(c.x + r, c.y)
            val bottom = Offset(c.x, c.y + r)
            val left = Offset(c.x - r, c.y)
            fun tri(a: Offset, b: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close() }
            val outline = Path().apply { moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(bottom.x, bottom.y); lineTo(left.x, left.y); close() }
            withTransform({ translate(0f, w * 0.03f) }) { drawPath(outline, Color(0x55000000)) }
            drawPath(outline, Brush.linearGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFC93C), Color(0xFFC98A00)), left, right))
            drawPath(tri(c, top, right), Color(0x33FFFFFF))
            drawPath(tri(c, left, top), Color(0x55FFFFFF))
            drawPath(tri(c, bottom, left), Color(0x22000000))
            drawPath(tri(c, right, bottom), Color(0x40000000))
            // inner table
            val ir = r * 0.58f
            val inner = Path().apply {
                moveTo(c.x, c.y - ir); lineTo(c.x + ir, c.y); lineTo(c.x, c.y + ir); lineTo(c.x - ir, c.y); close()
            }
            drawPath(inner, Brush.linearGradient(listOf(Color(0xFFFFE89A), Color(0xFFE6A100)), Offset(c.x, c.y - ir), Offset(c.x, c.y + ir)))
            drawPath(outline, Color(0xFF7A4E00), style = Stroke(w * 0.035f))
            drawPath(inner, Color(0xFFB36B00), style = Stroke(w * 0.018f))
            drawSparkle(this, Offset(c.x - r * 0.45f, c.y - r * 0.42f), w * 0.07f, Color.White)
        }
        Text(
            "CM",
            color = Color(0xFF2A0E5C),
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.22f).sp,
        )
    }
}

fun drawSparkle(scope: DrawScope, c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - s)
        quadraticTo(c.x, c.y, c.x + s, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + s)
        quadraticTo(c.x, c.y, c.x - s, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - s)
        close()
    }
    scope.drawPath(p, color)
}

// ------------------------------------------------------------------ stars

fun starPath(c: Offset, ro: Float, ri: Float, points: Int = 5): Path = Path().apply {
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) ro else ri
        val a = PI / points * i - PI / 2
        val x = c.x + cos(a).toFloat() * r
        val y = c.y + sin(a).toFloat() * r
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun StarIcon(filled: Boolean, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = center
        val ro = this.size.minDimension * 0.5f
        val path = starPath(c, ro, ro * 0.48f)
        if (filled) {
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFC93C), Color(0xFFE09A00))))
            drawPath(path, Color(0xFF8A5A00), style = Stroke(ro * 0.12f))
        } else {
            drawPath(path, Color(0x33000000))
            drawPath(path, Color(0x55FFFFFF), style = Stroke(ro * 0.1f))
        }
    }
}

@Composable
fun StarRow(stars: Int, total: Int = 3, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { StarIcon(it < stars, size) }
    }
}

// ------------------------------------------------------------------ progress bar

@Composable
fun GemProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    colors: List<Color> = listOf(Color(0xFFFFE27A), Color(0xFFFFB300)),
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color(0x55000000))
            .border(1.dp, Color(0x30FFFFFF), shape)
    ) {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(p.coerceAtLeast(0.04f))
                    .fillMaxHeight()
                    .clip(shape)
                    .background(Brush.horizontalGradient(colors))
                    .drawBehind {
                        drawRoundRect(
                            Color(0x50FFFFFF),
                            topLeft = Offset(size.height * 0.3f, size.height * 0.15f),
                            size = Size(size.width - size.height * 0.6f, size.height * 0.3f),
                            cornerRadius = CornerRadius(size.height)
                        )
                    }
            )
        }
    }
}

// ------------------------------------------------------------------ dialog

/** Royal game dialog with a springy entrance. */
@Composable
fun GemDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    /** Back-button action for non-dismissible dialogs (e.g. "go home"). Tapping outside still does nothing. */
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() else onBack?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissible, dismissOnBackPress = dismissible || onBack != null)
    ) {
        // dialogs get their own window: keep the system navigation bar hidden there too
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect { dialogWindow?.let { com.puzzel.lab.cardsmania.Immersive.apply(it) } }
        val a = remember { Animatable(0.7f) }
        LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 400f)) }
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = ((a.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                .shadow(24.dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF4A1F8F), Color(0xFF2A0E5C), Color(0xFF160640))))
                .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTitle(title, fontSize = 24.sp, outlineWidth = 8f)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// ------------------------------------------------------------------ slider

@Composable
fun GemSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onRelease: () -> Unit = {},
    accent: List<Color> = listOf(Color(0xFF8B5CF6), Color(0xFFFF5FA2)),
) {
    val onChange by androidx.compose.runtime.rememberUpdatedState(onChange)
    val onRelease by androidx.compose.runtime.rememberUpdatedState(onRelease)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    onChange((o.x / size.width).coerceIn(0f, 1f))
                    onRelease()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() },
                ) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackH = 10.dp.toPx()
            val y = size.height / 2
            val thumbR = 13.dp.toPx()
            val usable = size.width - thumbR * 2
            val x = thumbR + usable * value.coerceIn(0f, 1f)
            drawRoundRect(Color(0x55000000), Offset(0f, y - trackH / 2), Size(size.width, trackH), CornerRadius(trackH))
            drawRoundRect(Brush.horizontalGradient(accent), Offset(0f, y - trackH / 2), Size(x, trackH), CornerRadius(trackH))
            drawRoundRect(Color(0x40FFFFFF), Offset(4f, y - trackH / 2 + 2f), Size((x - 8f).coerceAtLeast(0f), trackH * 0.3f), CornerRadius(trackH))
            // gem thumb
            val d = thumbR
            val shadowY = 2.dp.toPx()
            val gem = Path().apply { moveTo(x, y - d); lineTo(x + d, y); lineTo(x, y + d); lineTo(x - d, y); close() }
            withTransform({ translate(0f, shadowY) }) { drawPath(gem, Color(0x66000000)) }
            drawPath(gem, Brush.linearGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFC93C), Color(0xFFC98A00)), Offset(x - d, y - d), Offset(x + d, y + d)))
            drawPath(gem, Color(0xFF7A4E00), style = Stroke(2.dp.toPx()))
            drawPath(Path().apply { moveTo(x, y - d); lineTo(x - d, y); lineTo(x, y); close() }, Color(0x55FFFFFF))
        }
    }
}

// ------------------------------------------------------------------ treasure chest

/** Original drawn treasure chest. [open] 0 = closed, 1 = open with light pouring out. */
@Composable
fun TreasureChest(open: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.48f
        val bodyLeft = w * 0.1f
        val bodyRight = w * 0.9f
        val bodyBottom = h * 0.95f
        // light from inside
        if (open > 0.01f) {
            val c = Offset(w / 2, bodyTop)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4C2).copy(alpha = 0.9f * open), Color.Transparent), c, w * 0.75f), w * 0.75f, c)
            for (i in 0 until 9) {
                val a = (-PI / 2 + (i - 4) * 0.22).toFloat()
                val ray = Path().apply {
                    moveTo(c.x - w * 0.05f, c.y); lineTo(c.x + w * 0.05f, c.y)
                    lineTo(c.x + cos(a + 0.06f) * w * 0.9f, c.y + sin(a + 0.06f) * w * 0.9f)
                    lineTo(c.x + cos(a - 0.06f) * w * 0.9f, c.y + sin(a - 0.06f) * w * 0.9f)
                    close()
                }
                drawPath(ray, Color(0xFFFFE89A).copy(alpha = 0.35f * open))
            }
        }
        // shadow
        drawOval(Color(0x55000000), Offset(w * 0.08f, h * 0.9f), Size(w * 0.84f, h * 0.1f))
        // body
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFA35A22), Color(0xFF6B3410)), bodyTop, bodyBottom),
            Offset(bodyLeft, bodyTop), Size(bodyRight - bodyLeft, bodyBottom - bodyTop), CornerRadius(w * 0.05f)
        )
        // planks
        for (i in 1..2) {
            val yy = bodyTop + (bodyBottom - bodyTop) * i / 3f
            drawLine(Color(0x55301400), Offset(bodyLeft, yy), Offset(bodyRight, yy), w * 0.012f)
        }
        val gold = Brush.verticalGradient(listOf(Color(0xFFFFE89A), Color(0xFFE09A00)))
        // bands
        for (bx in listOf(0.22f, 0.7f)) {
            drawRect(gold, Offset(w * bx, bodyTop), Size(w * 0.08f, bodyBottom - bodyTop))
        }
        drawRect(gold, Offset(bodyLeft, bodyTop), Size(bodyRight - bodyLeft, h * 0.04f))
        // lid
        val lift = open * h * 0.16f
        val lidScale = 1f - open * 0.45f
        val lidBottom = bodyTop + h * 0.02f - lift
        val lidHeight = h * 0.32f * lidScale
        val lidTop = lidBottom - lidHeight
        val lid = Path().apply {
            moveTo(bodyLeft - w * 0.02f, lidBottom)
            lineTo(bodyLeft - w * 0.02f, lidTop + lidHeight * 0.45f)
            cubicTo(bodyLeft, lidTop - lidHeight * 0.1f, bodyRight, lidTop - lidHeight * 0.1f, bodyRight + w * 0.02f, lidTop + lidHeight * 0.45f)
            lineTo(bodyRight + w * 0.02f, lidBottom)
            close()
        }
        drawPath(lid, Brush.verticalGradient(listOf(Color(0xFFC0742E), Color(0xFF7A3E14)), lidTop, lidBottom))
        drawPath(lid, Color(0xFF3A1A06), style = Stroke(w * 0.015f))
        for (bx in listOf(0.22f, 0.7f)) {
            drawRect(gold, Offset(w * bx, lidTop + lidHeight * 0.08f), Size(w * 0.08f, lidBottom - lidTop - lidHeight * 0.08f))
        }
        drawRect(gold, Offset(bodyLeft - w * 0.02f, lidBottom - h * 0.035f), Size(bodyRight - bodyLeft + w * 0.04f, h * 0.035f))
        // lock plate
        val lockW = w * 0.16f
        val lockTop = bodyTop - h * 0.02f
        drawRoundRect(gold, Offset(w / 2 - lockW / 2, lockTop), Size(lockW, h * 0.17f), CornerRadius(w * 0.03f))
        drawRoundRect(Color(0xFF8A5A00), Offset(w / 2 - lockW / 2, lockTop), Size(lockW, h * 0.17f), CornerRadius(w * 0.03f), style = Stroke(w * 0.01f))
        drawCircle(Color(0xFF3A1A06), w * 0.022f, Offset(w / 2, lockTop + h * 0.065f))
        drawRect(Color(0xFF3A1A06), Offset(w / 2 - w * 0.008f, lockTop + h * 0.065f), Size(w * 0.016f, h * 0.05f))
        // body outline
        drawRoundRect(Color(0xFF3A1A06), Offset(bodyLeft, bodyTop), Size(bodyRight - bodyLeft, bodyBottom - bodyTop), CornerRadius(w * 0.05f), style = Stroke(w * 0.015f))
        if (open > 0.3f) {
            drawSparkle(this, Offset(w * 0.3f, bodyTop - h * 0.2f * open), w * 0.05f, Color.White.copy(alpha = open))
            drawSparkle(this, Offset(w * 0.72f, bodyTop - h * 0.3f * open), w * 0.04f, Color.White.copy(alpha = open))
        }
    }
}

// ------------------------------------------------------------------ coin burst

/** Coins explode from the center and fall. Restart by changing [key]. */
@Composable
fun CoinBurst(key: Any, modifier: Modifier = Modifier, count: Int = 28) {
    val t = remember(key) { Animatable(0f) }
    val parts = remember(key) {
        List(count) {
            val a = Random.nextDouble(-PI * 0.95, -PI * 0.05)
            val sp = 0.5f + Random.nextFloat() * 0.8f
            floatArrayOf(cos(a).toFloat() * sp, sin(a).toFloat() * sp, Random.nextFloat() * 0.5f + 0.6f, Random.nextFloat() * 6f)
        }
    }
    LaunchedEffect(key) { t.snapTo(0f); t.animateTo(1f, tween(1500, easing = LinearEasing)) }
    Canvas(modifier) {
        val time = t.value
        if (time <= 0f || time >= 1f) return@Canvas
        val c = Offset(size.width / 2, size.height / 2)
        val scale = size.minDimension
        for (p in parts) {
            val x = c.x + p[0] * scale * time * 1.1f
            val y = c.y + p[1] * scale * time * 1.1f + 0.9f * scale * time * time
            val r = 10.dp.toPx() * p[2]
            val spin = cos((time * 12 + p[3]).toDouble()).toFloat()
            val alpha = (1f - time).coerceIn(0f, 1f).let { if (time < 0.8f) 1f else it * 5f }.coerceIn(0f, 1f)
            withTransform({ scale(spin.coerceIn(-1f, 1f).let { if (kotlin.math.abs(it) < 0.15f) 0.15f else kotlin.math.abs(it) }, 1f, Offset(x, y)) }) {
                drawCircle(Color(0xFFB36B00).copy(alpha = alpha), r, Offset(x, y + r * 0.12f))
                drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFC93C)), y - r, y + r), r * 0.9f, Offset(x, y), alpha = alpha)
                drawCircle(Color(0xFFC98A00).copy(alpha = alpha), r * 0.55f, Offset(x, y), style = Stroke(r * 0.15f))
            }
        }
    }
}

/** Speech bubble for emotes. */
@Composable
fun EmoteBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9E1FF))))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A0E5C), textAlign = TextAlign.Center)
    }
}

/** Small pill label. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f))))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** Gem-diamond bullet row for rule text. */
@Composable
fun GemBullet(text: String, color: Color = CM.Gold, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(Brush.linearGradient(listOf(color.copy(alpha = 1f), color.copy(alpha = 0.6f))))
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = CM.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** Ease helper used by illustrations. */
val EaseOut = FastOutSlowInEasing

/** Press-to-scale clickable with sound + haptic tick. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    sound: com.puzzel.lab.cardsmania.audio.Sfx? = com.puzzel.lab.cardsmania.audio.Sfx.TAP,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val app = LocalApp.current
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled, onClick) {
            detectTapGestures(onPress = {
                if (!enabled) return@detectTapGestures
                pressed = true
                val ok = tryAwaitRelease()
                pressed = false
                if (ok) {
                    sound?.let { app.sound.play(it) }
                    app.haptics.tick()
                    onClick()
                }
            })
        }
}
