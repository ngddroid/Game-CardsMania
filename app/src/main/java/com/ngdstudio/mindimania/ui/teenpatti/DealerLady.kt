package com.ngdstudio.mindimania.ui.teenpatti

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * The Teen Patti dealer: a woman behind the table, from the waist up, drawn in code (the app ships no artwork).
 * Long dark hair, a red dealer dress with a gold necklace, her hands resting on the table rail around the deck.
 * She blinks now and then; while [dealing] her right hand flicks cards off the deck.
 * Drawn in a 95 × 100 unit box of height [h] centred on [center], straight into the table canvas between the felt and
 * the cards, so dealt cards and table pops are drawn over her. Everything moves off the clock ([now]), so the table
 * only redraws for a blink ([DealerClock.nextFrameIn]).
 */
fun DrawScope.drawDealer(center: Offset, h: Float, now: Long, dealing: Boolean) {
    val u = h / 100f
    val ox = center.x - 95f * u / 2
    val oy = center.y - h / 2
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    val blink = DealerClock.blink(now)
    val flick = if (dealing) ((now % 520L) / 520f).let { if (it < 0.5f) it * 2 else 2 - it * 2 } else 0f
    // a soft shadow on the felt behind her
    drawOval(Color(0x33000000), p(4f, 80f), Size(87f * u, 24f * u))
    drawBody(::p, u)
    drawHead(::p, u, blink)
    drawRailAndHands(::p, u, flick, dealing)
}

/** Blink timing for the dealer: eyes shut briefly once every [PERIOD] ms. */
object DealerClock {
    private const val PERIOD = 4200L
    private const val START = 3900L
    private const val LEN = 180L

    fun blink(now: Long): Float {
        val t = now % PERIOD
        if (t < START || t > START + LEN) return 1f
        val k = (t - START).toFloat() / LEN
        return 0.1f + 0.9f * kotlin.math.abs(k * 2 - 1)
    }

    /** ms until the dealer next needs a frame (0 = right now, during a blink). */
    fun nextFrameIn(now: Long): Long {
        val t = now % PERIOD
        return if (t in START..START + LEN) 16L else ((START - t + PERIOD) % PERIOD).coerceAtLeast(16L)
    }
}

private val Skin = Color(0xFFF1C19B)
private val SkinShade = Color(0xFFD9A47E)
private val Hair = Color(0xFF2A1810)
private val HairShine = Color(0xFF4A2C1C)
private val Gold = Color(0xFFFFC93C)

private fun DrawScope.drawBody(p: (Float, Float) -> Offset, u: Float) {
    // long hair behind the head and shoulders
    val back = Path().apply {
        moveTo(p(30f, 24f).x, p(30f, 24f).y)
        cubicTo(p(28f, 8f).x, p(28f, 8f).y, p(67f, 8f).x, p(67f, 8f).y, p(65f, 24f).x, p(65f, 24f).y)
        cubicTo(p(72f, 40f).x, p(72f, 40f).y, p(74f, 62f).x, p(74f, 62f).y, p(70f, 76f).x, p(70f, 76f).y)
        lineTo(p(25f, 76f).x, p(25f, 76f).y)
        cubicTo(p(21f, 62f).x, p(21f, 62f).y, p(23f, 40f).x, p(23f, 40f).y, p(30f, 24f).x, p(30f, 24f).y)
        close()
    }
    drawPath(back, Hair)
    // neck
    drawRoundRect(Skin, p(41f, 48f), Size(13f * u, 18f * u), CornerRadius(4f * u))
    drawRect(SkinShade.copy(alpha = 0.6f), p(41f, 50f), Size(13f * u, 4f * u))
    // the dress: shoulders, V neckline
    val dress = Path().apply {
        moveTo(p(8f, 100f).x, p(8f, 100f).y)
        lineTo(p(11f, 80f).x, p(11f, 80f).y)
        quadraticTo(p(14f, 66f).x, p(14f, 66f).y, p(30f, 63f).x, p(30f, 63f).y)
        lineTo(p(40f, 61f).x, p(40f, 61f).y)
        lineTo(p(47.5f, 75f).x, p(47.5f, 75f).y)
        lineTo(p(55f, 61f).x, p(55f, 61f).y)
        lineTo(p(65f, 63f).x, p(65f, 63f).y)
        quadraticTo(p(81f, 66f).x, p(81f, 66f).y, p(84f, 80f).x, p(84f, 80f).y)
        lineTo(p(87f, 100f).x, p(87f, 100f).y)
        close()
    }
    drawPath(dress, Brush.verticalGradient(listOf(Color(0xFFD32F4F), Color(0xFF8E1230)), startY = 60f * u, endY = 100f * u))
    // gold trim along the neckline
    val trim = Path().apply {
        moveTo(p(40f, 61f).x, p(40f, 61f).y); lineTo(p(47.5f, 75f).x, p(47.5f, 75f).y); lineTo(p(55f, 61f).x, p(55f, 61f).y)
    }
    drawPath(trim, Gold, style = Stroke(1.4f * u, cap = StrokeCap.Round))
    // necklace with a pendant
    val neck = Path().apply {
        moveTo(p(40.5f, 60f).x, p(40.5f, 60f).y)
        quadraticTo(p(47.5f, 70f).x, p(47.5f, 70f).y, p(54.5f, 60f).x, p(54.5f, 60f).y)
    }
    drawPath(neck, Gold, style = Stroke(0.9f * u))
    drawCircle(Gold, 1.8f * u, p(47.5f, 66.2f))
    drawCircle(Color(0xFFE53935), 1f * u, p(47.5f, 66.2f))
    // hair falling over the shoulders, in front of the dress
    for (side in listOf(-1f, 1f)) {
        val cx = 47.5f + side * 15.5f
        val lock = Path().apply {
            moveTo(p(cx - side * 2f, 36f).x, p(cx - side * 2f, 36f).y)
            cubicTo(p(cx + side * 4f, 50f).x, p(cx + side * 4f, 50f).y, p(cx + side * 1f, 64f).x, p(cx + side * 1f, 64f).y, p(cx + side * 6f, 74f).x, p(cx + side * 6f, 74f).y)
            lineTo(p(cx + side * 0.5f, 70f).x, p(cx + side * 0.5f, 70f).y)
            cubicTo(p(cx - side * 3f, 60f).x, p(cx - side * 3f, 60f).y, p(cx - side * 2f, 48f).x, p(cx - side * 2f, 48f).y, p(cx - side * 5f, 38f).x, p(cx - side * 5f, 38f).y)
            close()
        }
        drawPath(lock, Hair)
    }
}

private fun DrawScope.drawHead(p: (Float, Float) -> Offset, u: Float, blink: Float) {
    // face
    drawOval(Brush.radialGradient(listOf(Skin, SkinShade), center = p(46f, 34f), radius = 20f * u), p(33.5f, 19f), Size(28f * u, 34f * u))
    // earrings
    for (x in listOf(33.6f, 61.4f)) {
        drawCircle(Gold, 1.5f * u, p(x, 41.5f))
        drawLine(Gold, p(x, 42.5f), p(x, 46f), 0.7f * u)
        drawCircle(Gold, 1.2f * u, p(x, 46.5f))
    }
    // blush
    drawCircle(Color(0xFFFF8A80).copy(alpha = 0.35f), 3.2f * u, p(39.5f, 42.5f))
    drawCircle(Color(0xFFFF8A80).copy(alpha = 0.35f), 3.2f * u, p(55.5f, 42.5f))
    // eyes (squash to a line on a blink), lashes, brows
    for (x in listOf(41.5f, 53.5f)) {
        val ry = 2.3f * blink
        drawOval(Color(0xFF2B1B14), Offset(p(x, 37.5f).x - 1.8f * u, p(x, 37.5f).y - ry * u), Size(3.6f * u, 2 * ry * u))
        if (blink > 0.5f) drawCircle(Color.White, 0.6f * u, p(x + 0.6f, 36.8f))
        drawArc(Color(0xFF2B1B14), 200f, 140f, false, Offset(p(x, 37f).x - 2.6f * u, p(x, 37f).y - 2.4f * u), Size(5.2f * u, 4.8f * u), style = Stroke(0.7f * u, cap = StrokeCap.Round))
        drawArc(Hair, 205f, 130f, false, Offset(p(x, 33.8f).x - 3f * u, p(x, 33.8f).y - 1.6f * u), Size(6f * u, 3.6f * u), style = Stroke(0.8f * u, cap = StrokeCap.Round))
    }
    // nose
    val nose = Path().apply {
        moveTo(p(47.6f, 39.5f).x, p(47.6f, 39.5f).y); lineTo(p(46.6f, 43.4f).x, p(46.6f, 43.4f).y); lineTo(p(48.4f, 43.8f).x, p(48.4f, 43.8f).y)
    }
    drawPath(nose, SkinShade, style = Stroke(0.7f * u, cap = StrokeCap.Round))
    // smile
    val lips = Path().apply {
        moveTo(p(43.6f, 46.6f).x, p(43.6f, 46.6f).y)
        quadraticTo(p(47.5f, 45.6f).x, p(47.5f, 45.6f).y, p(51.4f, 46.6f).x, p(51.4f, 46.6f).y)
        quadraticTo(p(47.5f, 50.6f).x, p(47.5f, 50.6f).y, p(43.6f, 46.6f).x, p(43.6f, 46.6f).y)
        close()
    }
    drawPath(lips, Color(0xFFC62848))
    // fringe swept to one side
    val fringe = Path().apply {
        moveTo(p(32f, 38f).x, p(32f, 38f).y)
        cubicTo(p(29f, 20f).x, p(29f, 20f).y, p(42f, 14f).x, p(42f, 14f).y, p(50f, 15.5f).x, p(50f, 15.5f).y)
        cubicTo(p(60f, 16.5f).x, p(60f, 16.5f).y, p(66f, 25f).x, p(66f, 25f).y, p(63.5f, 38f).x, p(63.5f, 38f).y)
        cubicTo(p(61f, 29f).x, p(61f, 29f).y, p(55f, 24.5f).x, p(55f, 24.5f).y, p(45f, 26f).x, p(45f, 26f).y)
        cubicTo(p(39f, 27.5f).x, p(39f, 27.5f).y, p(35f, 31f).x, p(35f, 31f).y, p(32f, 38f).x, p(32f, 38f).y)
        close()
    }
    drawPath(fringe, Hair)
    drawArc(HairShine, 200f, 70f, false, Offset(p(36f, 17f).x, p(36f, 17f).y), Size(18f * u, 14f * u), style = Stroke(1.2f * u, cap = StrokeCap.Round))
}

/** The table rail in front of her, the deck, and her hands around it. [flick] 0..1 moves the dealing hand. */
private fun DrawScope.drawRailAndHands(p: (Float, Float) -> Offset, u: Float, flick: Float, dealing: Boolean) {
    // padded rail
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF6B3A22), Color(0xFF3A1E10)), startY = 88f * u, endY = 100f * u),
        p(2f, 88f), Size(91f * u, 12f * u), CornerRadius(6f * u),
    )
    drawRoundRect(Color(0x33FFFFFF), p(4f, 89f), Size(87f * u, 2f * u), CornerRadius(1f * u))
    // left arm and hand holding the deck
    drawLine(Color(0xFFB71C3C), p(15f, 80f), p(26f, 88f), 7f * u, cap = StrokeCap.Round)
    drawLine(Skin, p(26f, 88f), p(37f, 90f), 5.5f * u, cap = StrokeCap.Round)
    // the deck
    withTransform({ rotate(if (dealing) -4f + flick * 3f else -4f, p(47.5f, 89f)) }) {
        drawRoundRect(Color(0xFF3A1A99), p(40f, 83f), Size(15f * u, 11f * u), CornerRadius(1.6f * u))
        drawRoundRect(Gold, p(40f, 83f), Size(15f * u, 11f * u), CornerRadius(1.6f * u), style = Stroke(0.8f * u))
        drawRoundRect(Color(0x55FFFFFF), p(41.5f, 84.2f), Size(12f * u, 1.2f * u), CornerRadius(0.6f * u))
    }
    drawOval(Skin, p(34f, 86.5f), Size(8f * u, 6f * u))
    // right arm: still, or flicking cards out when dealing
    val dx = flick * 6f
    val dy = -flick * 2.5f
    drawLine(Color(0xFFB71C3C), p(80f, 80f), p(69f, 88f), 7f * u, cap = StrokeCap.Round)
    drawLine(Skin, p(69f, 88f), p(58f + dx, 89f + dy), 5.5f * u, cap = StrokeCap.Round)
    drawOval(Skin, Offset(p(53f + dx, 86f + dy).x, p(53f + dx, 86f + dy).y), Size(8f * u, 6f * u))
    if (dealing) {
        // a card leaving her fingers
        withTransform({ rotate(-20f + flick * 40f, p(58f + dx, 85f + dy)) }) {
            drawRoundRect(Color(0xFF3A1A99), p(55f + dx, 80f + dy), Size(6f * u, 8.4f * u), CornerRadius(1f * u))
            drawRoundRect(Gold, p(55f + dx, 80f + dy), Size(6f * u, 8.4f * u), CornerRadius(1f * u), style = Stroke(0.6f * u))
        }
    }
}
