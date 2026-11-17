package com.puzzel.lab.cardsmania.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily

/** Rim colors for [JewelPanel]. */
enum class Rim(val light: Color, val mid: Color, val dark: Color) {
    GOLD(Color(0xFFFFF0A8), Color(0xFFFFC93C), Color(0xFF9A5E00)),
    BRONZE(Color(0xFFFFD2A0), Color(0xFFD08A4A), Color(0xFF6A3810)),
    SILVER(Color(0xFFFFFFFF), Color(0xFFB8C0D8), Color(0xFF59607A)),
    EMERALD(Color(0xFFB8FFD9), Color(0xFF2EAD4B), Color(0xFF0B4D25)),
    RUBY(Color(0xFFFFB0B8), Color(0xFFE53935), Color(0xFF6E0E14)),
    SAPPHIRE(Color(0xFFB6DBFF), Color(0xFF1E88E5), Color(0xFF0B3570)),
}

/** Interior fill presets for [JewelPanel]. */
enum class PanelFill(val top: Color, val bottom: Color) {
    ROYAL(Color(0xFF4C2196), Color(0xFF1C0A47)),
    NIGHT(Color(0xFF2A1A5A), Color(0xFF0E0628)),
    FELT(Color(0xFF1D7A48), Color(0xFF0A3A21)),
    WINE(Color(0xFF7C1A4E), Color(0xFF2E0820)),
    OCEAN(Color(0xFF1A5AA0), Color(0xFF0A2350)),
    GLASS(Color(0x40FFFFFF), Color(0x14FFFFFF)),
}

/**
 * Beveled "jewel" panel: metallic outer rim, inner gradient with a top glow, a soft inner shadow and four corner
 * studs. An optional [title] is drawn as a ribbon across the top edge.
 */
@Composable
fun JewelPanel(
    modifier: Modifier = Modifier,
    rim: Rim = Rim.GOLD,
    fill: PanelFill = PanelFill.ROYAL,
    corner: Dp = 22.dp,
    padding: Dp = 16.dp,
    title: String? = null,
    titleColor: Color = CM.Ruby,
    studs: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.padding(top = if (title != null) 16.dp else 0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    // drop shadow
                    drawRoundRect(Color(0x66000000), topLeft = Offset(0f, 5.dp.toPx()), size = size, cornerRadius = CornerRadius(corner.toPx()))
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(rim.light, rim.mid, rim.dark)))
                .padding(3.dp)
                .clip(RoundedCornerShape(corner - 3.dp))
                .background(Brush.verticalGradient(listOf(fill.top, fill.bottom)))
                .drawWithCache {
                    val glow = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), 0f, size.height * 0.35f)
                    val inner = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), size.height * 0.6f, size.height)
                    val studR = 3.2.dp.toPx()
                    val inset = 9.dp.toPx()
                    val studBrush = Brush.radialGradient(listOf(rim.light, rim.mid, rim.dark), Offset.Zero, studR * 1.4f)
                    onDrawBehind {
                        drawRect(glow)
                        drawRect(inner)
                        drawRoundRect(
                            Color.White.copy(alpha = 0.18f), topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius((corner - 4.dp).toPx()), style = Stroke(1.dp.toPx()),
                        )
                        if (studs) {
                            for (p in listOf(Offset(inset, inset), Offset(size.width - inset, inset), Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset))) {
                                drawCircle(Color.Black.copy(alpha = 0.35f), studR, p + Offset(0f, 1f))
                                translateCircle(studBrush, studR, p)
                            }
                        }
                    }
                }
                .padding(top = padding + if (title != null) 10.dp else 0.dp, start = padding, end = padding, bottom = padding),
            content = content,
        )
        if (title != null) {
            RibbonHeader(title, Modifier.align(Alignment.TopCenter).offset(y = (-16).dp), color = titleColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.translateCircle(brush: Brush, r: Float, at: Offset) {
    drawContext.transform.translate(at.x, at.y)
    drawCircle(brush, r, Offset.Zero)
    drawContext.transform.translate(-at.x, -at.y)
}

/** Folded ribbon banner with a title in the display face. */
@Composable
fun RibbonHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CM.Ruby,
    textColor: Color = Color.White,
    fontSize: TextUnit = 17.sp,
    icon: ImageVector? = null,
) {
    val dark = lerpColor(color, Color.Black, 0.45f)
    val light = lerpColor(color, Color.White, 0.25f)
    Box(
        modifier
            .height(38.dp)
            .drawWithCache {
                val h = size.height
                val w = size.width
                val tail = h * 0.55f
                val band = h * 0.78f
                val top = (h - band) / 2
                val leftTail = Path().apply {
                    moveTo(0f, top + band * 0.18f); lineTo(tail * 1.25f, top + band * 0.18f); lineTo(tail * 1.25f, top + band * 1.05f)
                    lineTo(0f, top + band * 1.05f); lineTo(tail * 0.45f, top + band * 0.62f); close()
                }
                val rightTail = Path().apply {
                    moveTo(w, top + band * 0.18f); lineTo(w - tail * 1.25f, top + band * 0.18f); lineTo(w - tail * 1.25f, top + band * 1.05f)
                    lineTo(w, top + band * 1.05f); lineTo(w - tail * 0.45f, top + band * 0.62f); close()
                }
                val folds = Path().apply {
                    moveTo(tail, top + band); lineTo(tail * 1.25f, top + band * 1.05f); lineTo(tail * 1.25f, top + band); close()
                    moveTo(w - tail, top + band); lineTo(w - tail * 1.25f, top + band * 1.05f); lineTo(w - tail * 1.25f, top + band); close()
                }
                val main = Brush.verticalGradient(listOf(light, color, lerpColor(color, Color.Black, 0.18f)), top, top + band)
                onDrawBehind {
                    drawPath(leftTail, dark)
                    drawPath(rightTail, dark)
                    drawPath(folds, lerpColor(color, Color.Black, 0.65f))
                    drawRoundRect(Color(0x55000000), topLeft = Offset(tail, top + 2.dp.toPx()), size = Size(w - tail * 2, band), cornerRadius = CornerRadius(4.dp.toPx()))
                    drawRoundRect(main, topLeft = Offset(tail, top), size = Size(w - tail * 2, band), cornerRadius = CornerRadius(4.dp.toPx()))
                    drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(tail + 3.dp.toPx(), top + 2.5.dp.toPx()), size = Size(w - tail * 2 - 6.dp.toPx(), band - 5.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            StrokeText(text, fontSize = fontSize, color = textColor, stroke = dark, strokeWidth = 5f)
        }
    }
}

/** Display-face text with an outline and a small drop shadow (for labels on busy backgrounds). */
@Composable
fun StrokeText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.White,
    stroke: Color = Color(0xFF2A0E5C),
    strokeWidth: Float = 6f,
    maxLines: Int = 1,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    val lh = fontSize * 1.05f
    Box(modifier) {
        Text(
            text, fontSize = fontSize, fontFamily = DisplayFamily, maxLines = maxLines, textAlign = textAlign, lineHeight = lh,
            style = TextStyle(color = stroke, drawStyle = Stroke(strokeWidth, join = StrokeJoin.Round)),
        )
        Text(
            text, fontSize = fontSize, fontFamily = DisplayFamily, color = color, maxLines = maxLines, textAlign = textAlign, lineHeight = lh,
            style = TextStyle(shadow = Shadow(Color(0x66000000), Offset(0f, 2f), 2f)),
        )
    }
}

/** Small rounded pill label (e.g. "2–6 PLAYERS"). */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, color: Color = Color(0x99000000), textColor: Color = Color.White, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = textColor, fontSize = 10.sp, fontFamily = DisplayFamily, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
