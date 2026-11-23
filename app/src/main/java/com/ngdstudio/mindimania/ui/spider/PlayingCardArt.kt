package com.ngdstudio.mindimania.ui.spider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Original standard-deck card faces for Spider Solitaire, drawn with android.graphics and cached.
 *
 * Cards are tiny and stacked in Spider, so the top ~30% strip carries a big rank (left) and a big suit (right).
 * Below it: one large suit symbol (2–10), an ornate ringed symbol (Ace) or a royal crown emblem (J/Q/K).
 */
object PlayingCardArt {
    /**
     * All faces for one card width, in a flat array indexed by (fourColor, suit, rank): drawing 104 cards every frame
     * allocates nothing. A new width swaps in a fresh sheet. A race only means a face may be rendered twice.
     */
    private class Sheet(val w: Int) {
        val faces = arrayOfNulls<ImageBitmap>(2 * 4 * 14)
    }

    @Volatile
    private var sheet = Sheet(96)

    val widthPx: Int get() = sheet.w
    val heightPx: Int get() = (sheet.w * 1.4f).toInt()

    fun configure(widthPx: Int) {
        val w = widthPx.coerceIn(40, 360)
        if (w != sheet.w) sheet = Sheet(w)
    }

    /** rank 1..13 (1 = A, 11 = J, 12 = Q, 13 = K); suit 0 = spades, 1 = hearts, 2 = diamonds, 3 = clubs */
    fun face(rank: Int, suit: Int, fourColor: Boolean = false): ImageBitmap {
        val s = sheet
        val r = rank.coerceIn(1, 13)
        val su = suit.coerceIn(0, 3)
        val i = ((if (fourColor) 4 else 0) + su) * 14 + r
        return s.faces[i] ?: render(r, su, fourColor, s.w, (s.w * 1.4f).toInt()).asImageBitmap().also { s.faces[i] = it }
    }

    fun suitColor(suit: Int, fourColor: Boolean = false): Int = when (suit) {
        1 -> RED
        2 -> if (fourColor) BLUE else RED
        3 -> if (fourColor) GREEN else BLACK
        else -> BLACK
    }

    fun rankLabel(rank: Int): String = when (rank) {
        1 -> "A"
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        else -> rank.toString()
    }

    fun clear() {
        sheet = Sheet(sheet.w)
    }

    /** Suit glyph centered at ([cx], [cy]); [size] ≈ full glyph height. */
    fun suitPath(suit: Int, cx: Float, cy: Float, size: Float): Path = when (suit) {
        1 -> heart(cx, cy, size)
        2 -> diamond(cx, cy, size)
        3 -> club(cx, cy, size)
        else -> spade(cx, cy, size)
    }

    // ------------------------------------------------------------------ colors

    private const val BLACK = 0xFF17151F.toInt()
    private const val RED = 0xFFD7263D.toInt()
    private const val GREEN = 0xFF12924A.toInt()
    private const val BLUE = 0xFF1565D8.toInt()
    private const val OPAQUE = 0xFF000000.toInt()

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (ch((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or
            (ch((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or
            ch(a and 0xFF, b and 0xFF)
    }

    private fun lighten(c: Int, f: Float) = mix(c, 0xFFFFFFFF.toInt(), f)
    private fun darken(c: Int, f: Float) = mix(c, OPAQUE, f)
    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    // ------------------------------------------------------------------ typefaces

    private val rankFace: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val royalFace: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    /** Cap height / text size for a typeface (measured once on "E"). */
    private val capRatios = ConcurrentHashMap<Typeface, Float>()

    private fun capRatio(tf: Typeface): Float = capRatios.getOrPut(tf) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = 100f }
        val b = Rect()
        p.getTextBounds("E", 0, 1, b)
        (-b.top / 100f).takeIf { it > 0.3f } ?: 0.72f
    }

    // ------------------------------------------------------------------ suit shapes

    private fun union(vararg parts: Path): Path {
        val out = Path(parts[0])
        for (i in 1 until parts.size) out.op(parts[i], Path.Op.UNION)
        return out
    }

    private fun heart(cx: Float, cy: Float, s: Float): Path {
        val h = s
        val w = s * 1.05f
        return Path().apply {
            moveTo(cx, cy + 0.5f * h)
            cubicTo(cx - 0.12f * w, cy + 0.28f * h, cx - 0.5f * w, cy + 0.12f * h, cx - 0.5f * w, cy - 0.16f * h)
            cubicTo(cx - 0.5f * w, cy - 0.42f * h, cx - 0.2f * w, cy - 0.56f * h, cx, cy - 0.3f * h)
            cubicTo(cx + 0.2f * w, cy - 0.56f * h, cx + 0.5f * w, cy - 0.42f * h, cx + 0.5f * w, cy - 0.16f * h)
            cubicTo(cx + 0.5f * w, cy + 0.12f * h, cx + 0.12f * w, cy + 0.28f * h, cx, cy + 0.5f * h)
            close()
        }
    }

    private fun diamond(cx: Float, cy: Float, s: Float): Path = Path().apply {
        moveTo(cx, cy - 0.5f * s)
        quadTo(cx + 0.17f * s, cy - 0.2f * s, cx + 0.38f * s, cy)
        quadTo(cx + 0.17f * s, cy + 0.2f * s, cx, cy + 0.5f * s)
        quadTo(cx - 0.17f * s, cy + 0.2f * s, cx - 0.38f * s, cy)
        quadTo(cx - 0.17f * s, cy - 0.2f * s, cx, cy - 0.5f * s)
        close()
    }

    private fun stem(cx: Float, topY: Float, cy: Float, s: Float): Path = Path().apply {
        moveTo(cx - 0.035f * s, topY)
        quadTo(cx - 0.05f * s, cy + 0.4f * s, cx - 0.2f * s, cy + 0.5f * s)
        lineTo(cx + 0.2f * s, cy + 0.5f * s)
        quadTo(cx + 0.05f * s, cy + 0.4f * s, cx + 0.035f * s, topY)
        close()
    }

    private fun spade(cx: Float, cy: Float, s: Float): Path {
        val w = s
        val by = cy - 0.07f * s
        val bh = 0.86f * s
        val body = Path().apply {
            moveTo(cx, by - 0.5f * bh)
            cubicTo(cx - 0.12f * w, by - 0.28f * bh, cx - 0.5f * w, by - 0.12f * bh, cx - 0.5f * w, by + 0.16f * bh)
            cubicTo(cx - 0.5f * w, by + 0.42f * bh, cx - 0.2f * w, by + 0.56f * bh, cx, by + 0.3f * bh)
            cubicTo(cx + 0.2f * w, by + 0.56f * bh, cx + 0.5f * w, by + 0.42f * bh, cx + 0.5f * w, by + 0.16f * bh)
            cubicTo(cx + 0.5f * w, by - 0.12f * bh, cx + 0.12f * w, by - 0.28f * bh, cx, by - 0.5f * bh)
            close()
        }
        return union(body, stem(cx, by + 0.2f * bh, cy, s))
    }

    private fun club(cx: Float, cy: Float, s: Float): Path {
        val r = 0.235f * s
        val top = Path().apply { addCircle(cx, cy - 0.5f * s + r, r, Path.Direction.CW) }
        val left = Path().apply { addCircle(cx - 0.25f * s, cy + 0.02f * s, r, Path.Direction.CW) }
        val right = Path().apply { addCircle(cx + 0.25f * s, cy + 0.02f * s, r, Path.Direction.CW) }
        val mid = Path().apply { addCircle(cx, cy - 0.04f * s, 0.14f * s, Path.Direction.CW) }
        return union(top, left, right, mid, stem(cx, cy + 0.02f * s, cy, s))
    }

    // ------------------------------------------------------------------ rendering

    private fun render(rank: Int, suit: Int, fourColor: Boolean, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fw = w.toFloat()
        val fh = h.toFloat()
        val col = suitColor(suit, fourColor)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // body
        val stroke = max(1f, fw * 0.012f)
        val half = stroke / 2f
        val r = fw * 0.09f
        val rect = RectF(half, half, fw - half, fh - half)
        p.color = OPAQUE
        p.shader = LinearGradient(0f, 0f, 0f, fh, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFAF6EC.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p)
        p.shader = null
        // grey border + faint inner line
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.color = 0xFF9E9AAB.toInt()
        c.drawRoundRect(rect, r, r, p)
        val innerInset = stroke + max(1f, fw * 0.01f)
        p.strokeWidth = max(1f, fw * 0.008f)
        p.color = 0x1C5A4A30
        c.drawRoundRect(RectF(innerInset, innerInset, fw - innerInset, fh - innerInset), r * 0.75f, r * 0.75f, p)
        p.style = Paint.Style.FILL

        // big index strip (top) and small upside-down index (bottom-right)
        drawBigIndex(c, rank, suit, col, fw)
        c.save()
        c.rotate(180f, fw / 2f, fh / 2f)
        drawSmallIndex(c, rank, suit, col, fw)
        c.restore()

        when (rank) {
            1 -> drawAce(c, suit, col, fw)
            in 11..13 -> drawRoyal(c, rank, suit, col, fw)
            else -> drawCenterSymbol(c, suit, col, fw / 2f, fw * 0.9f, fw * 0.55f, fw)
        }
        return bmp
    }

    private fun drawBigIndex(c: Canvas, rank: Int, suit: Int, col: Int, fw: Float) {
        val label = rankLabel(rank)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = rankFace
            color = col
            textAlign = Paint.Align.LEFT
        }
        val capH = fw * 0.35f
        tp.textSize = capH / capRatio(rankFace)
        val maxW = fw * 0.5f
        val measured = tp.measureText(label)
        if (measured > maxW) tp.textScaleX = (maxW / measured).coerceAtLeast(0.7f)
        val b = Rect()
        tp.getTextBounds(label, 0, label.length, b)
        val topY = fw * 0.06f
        val x = fw * 0.07f - b.left
        c.drawText(label, x, topY + capH, tp)

        val ss = fw * 0.28f
        val scx = fw - fw * 0.07f - ss * 0.5f
        val scy = topY + capH / 2f
        c.drawPath(suitPath(suit, scx, scy, ss), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    }

    private fun drawSmallIndex(c: Canvas, rank: Int, suit: Int, col: Int, fw: Float) {
        val label = rankLabel(rank)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = rankFace
            color = col
            textAlign = Paint.Align.LEFT
        }
        val capH = fw * 0.15f
        tp.textSize = capH / capRatio(rankFace)
        val maxW = fw * 0.2f
        val measured = tp.measureText(label)
        if (measured > maxW) tp.textScaleX = (maxW / measured).coerceAtLeast(0.7f)
        val b = Rect()
        tp.getTextBounds(label, 0, label.length, b)
        val topY = fw * 0.06f
        val x = fw * 0.07f
        c.drawText(label, x - b.left, topY + capH, tp)
        val ss = fw * 0.12f
        val scx = x + b.width() / 2f
        val scy = topY + capH + fw * 0.03f + ss / 2f
        c.drawPath(suitPath(suit, scx, scy, ss), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    }

    /** A large suit symbol with soft shadow and a subtle top-to-bottom shade. */
    private fun drawCenterSymbol(c: Canvas, suit: Int, col: Int, cx: Float, cy: Float, size: Float, fw: Float) {
        val path = suitPath(suit, cx, cy, size)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
        c.save()
        c.translate(fw * 0.012f, fw * 0.02f)
        c.drawPath(path, shadow)
        c.restore()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = OPAQUE
        fill.shader = LinearGradient(
            0f, cy - size / 2f, 0f, cy + size / 2f,
            intArrayOf(lighten(col, 0.22f), col, darken(col, 0.12f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawPath(path, fill)
        // gentle gloss on the upper-left
        c.save()
        c.clipPath(path)
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
        gloss.color = OPAQUE
        gloss.shader = LinearGradient(
            cx - size / 2f, cy - size / 2f, cx, cy,
            intArrayOf(0x40FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
        )
        c.drawRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, gloss)
        c.restore()
    }

    private fun drawAce(c: Canvas, suit: Int, col: Int, fw: Float) {
        val cx = fw / 2f
        val cy = fw * 0.9f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, fw * 0.02f)
            color = withAlpha(col, 0x70)
        }
        c.drawCircle(cx, cy, fw * 0.38f, ring)
        ring.strokeWidth = max(1f, fw * 0.01f)
        ring.color = withAlpha(col, 0x3A)
        c.drawCircle(cx, cy, fw * 0.335f, ring)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        val rr = fw * 0.38f
        val ds = fw * 0.03f
        // four small diamonds on the outer ring
        for ((dx, dy) in listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)) {
            val x = cx + dx * rr
            val y = cy + dy * rr
            c.drawPath(Path().apply {
                moveTo(x, y - ds); lineTo(x + ds, y); lineTo(x, y + ds); lineTo(x - ds, y); close()
            }, dot)
        }
        drawCenterSymbol(c, suit, col, cx, cy, fw * 0.6f, fw)
    }

    private fun drawRoyal(c: Canvas, rank: Int, suit: Int, col: Int, fw: Float) {
        val panel = RectF(fw * 0.1f, fw * 0.47f, fw * 0.9f, fw * 1.02f)
        val pr = fw * 0.08f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = OPAQUE
        p.shader = LinearGradient(0f, panel.top, 0f, panel.bottom, intArrayOf(mix(col, 0xFFFFFFFF.toInt(), 0.9f), mix(col, 0xFFFFFFFF.toInt(), 0.78f)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(panel, pr, pr, p)
        p.shader = null
        // gold border
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.2f, fw * 0.025f)
        p.color = OPAQUE
        p.shader = LinearGradient(panel.left, panel.top, panel.right, panel.bottom, intArrayOf(0xFFFFE27A.toInt(), 0xFFD9A015.toInt(), 0xFFFFE89A.toInt(), 0xFFB57E00.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(panel, pr, pr, p)
        p.shader = null
        p.strokeWidth = max(1f, fw * 0.008f)
        p.color = withAlpha(col, 0x45)
        val inset = fw * 0.04f
        c.drawRoundRect(RectF(panel.left + inset, panel.top + inset, panel.right - inset, panel.bottom - inset), pr * 0.6f, pr * 0.6f, p)
        p.style = Paint.Style.FILL

        // crown
        val cw = fw * 0.5f
        if (rank == 13) drawCrown(c, rank, fw / 2f, fw * 0.505f, cw, fw * 0.225f, col, fw)
        else drawCrown(c, rank, fw / 2f, fw * 0.52f, cw, fw * 0.2f, col, fw)

        // letter
        val label = rankLabel(rank)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = royalFace
            color = col
            textAlign = Paint.Align.CENTER
        }
        val capH = fw * 0.19f
        tp.textSize = capH / capRatio(royalFace)
        val baseline = fw * 0.97f
        c.drawText(label, fw / 2f, baseline, tp)
        val ss = fw * 0.11f
        val sy = baseline - capH / 2f
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        c.drawPath(suitPath(suit, fw * 0.25f, sy, ss), sp)
        c.drawPath(suitPath(suit, fw * 0.75f, sy, ss), sp)
    }

    /** Original crowns: J = 3-point coronet, Q = rounded 5-point crown with a jewel, K = tall crown with a cross. */
    private fun drawCrown(c: Canvas, rank: Int, cx: Float, top: Float, cw: Float, ch: Float, jewel: Int, fw: Float) {
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.color = OPAQUE
        gold.shader = LinearGradient(
            0f, top, 0f, top + ch,
            intArrayOf(0xFFFFE27A.toInt(), 0xFFF2B705.toInt(), 0xFFC98A00.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, fw * 0.016f)
            color = 0xFF7A4E00.toInt()
            strokeJoin = Paint.Join.ROUND
        }
        val jewelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = jewel }
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
        fun fillOutline(path: Path) {
            c.drawPath(path, gold)
            c.drawPath(path, outline)
        }
        fun ball(x: Float, y: Float, r: Float) {
            val b = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
            fillOutline(b)
        }
        val base = top + ch
        when (rank) {
            11 -> {
                val l = cx - cw * 0.4f
                val r = cx + cw * 0.4f
                fillOutline(Path().apply {
                    moveTo(l, base)
                    lineTo(l, top + ch * 0.3f)
                    lineTo(cx - cw * 0.2f, top + ch * 0.62f)
                    lineTo(cx, top + ch * 0.14f)
                    lineTo(cx + cw * 0.2f, top + ch * 0.62f)
                    lineTo(r, top + ch * 0.3f)
                    lineTo(r, base)
                    close()
                })
                fillOutline(Path().apply { addRoundRect(RectF(l - cw * 0.04f, top + ch * 0.74f, r + cw * 0.04f, base), ch * 0.06f, ch * 0.06f, Path.Direction.CW) })
                ball(l, top + ch * 0.3f, cw * 0.055f)
                ball(cx, top + ch * 0.14f, cw * 0.06f)
                ball(r, top + ch * 0.3f, cw * 0.055f)
                val jy = top + ch * 0.87f
                val js = ch * 0.1f
                c.drawPath(Path().apply { moveTo(cx, jy - js); lineTo(cx + js, jy); lineTo(cx, jy + js); lineTo(cx - js, jy); close() }, jewelPaint)
            }
            12 -> {
                val xs = floatArrayOf(-0.45f, -0.225f, 0f, 0.225f, 0.45f)
                val ys = floatArrayOf(0.34f, 0.2f, 0.1f, 0.2f, 0.34f)
                val l = cx + xs[0] * cw
                val r = cx + xs[4] * cw
                fillOutline(Path().apply {
                    moveTo(l, base)
                    lineTo(l, top + ys[0] * ch)
                    for (i in 0 until 4) {
                        val midX = cx + (xs[i] + xs[i + 1]) / 2f * cw
                        quadTo(midX, top + ch * 0.78f, cx + xs[i + 1] * cw, top + ys[i + 1] * ch)
                    }
                    lineTo(r, base)
                    close()
                })
                fillOutline(Path().apply { addRoundRect(RectF(l - cw * 0.03f, top + ch * 0.76f, r + cw * 0.03f, base), ch * 0.07f, ch * 0.07f, Path.Direction.CW) })
                for (i in xs.indices) ball(cx + xs[i] * cw, top + ys[i] * ch, cw * 0.05f)
                val jr = cw * 0.085f
                val jy = top + ch * 0.53f
                c.drawCircle(cx, jy, jr, jewelPaint)
                c.drawCircle(cx, jy, jr, outline)
                c.drawCircle(cx - jr * 0.3f, jy - jr * 0.3f, jr * 0.28f, shine)
            }
            else -> {
                val l = cx - cw * 0.42f
                val r = cx + cw * 0.42f
                val bodyTop = top + ch * 0.3f
                // cross (drawn first, the body covers its foot)
                fillOutline(Path().apply {
                    addRect(cx - cw * 0.04f, top, cx + cw * 0.04f, bodyTop + ch * 0.16f, Path.Direction.CW)
                    op(Path().apply { addRect(cx - cw * 0.12f, top + ch * 0.06f, cx + cw * 0.12f, top + ch * 0.14f, Path.Direction.CW) }, Path.Op.UNION)
                })
                fillOutline(Path().apply {
                    moveTo(l, base)
                    lineTo(l, bodyTop + ch * 0.25f)
                    cubicTo(l, bodyTop - ch * 0.04f, cx - cw * 0.08f, bodyTop - ch * 0.04f, cx, bodyTop + ch * 0.14f)
                    cubicTo(cx + cw * 0.08f, bodyTop - ch * 0.04f, r, bodyTop - ch * 0.04f, r, bodyTop + ch * 0.25f)
                    lineTo(r, base)
                    close()
                })
                // vertical fluting
                val flute = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, fw * 0.008f)
                    color = 0x887A4E00.toInt()
                }
                for (fx in floatArrayOf(-0.21f, 0.21f)) {
                    c.drawLine(cx + fx * cw, bodyTop + ch * 0.22f, cx + fx * cw, top + ch * 0.8f, flute)
                }
                fillOutline(Path().apply { addRoundRect(RectF(l - cw * 0.04f, top + ch * 0.8f, r + cw * 0.04f, base), ch * 0.06f, ch * 0.06f, Path.Direction.CW) })
                val jy = top + ch * 0.9f
                val jr = ch * 0.065f
                c.drawCircle(cx, jy, jr, jewelPaint)
                val side = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lighten(jewel, 0.45f) }
                c.drawCircle(cx - cw * 0.26f, jy, jr * 0.8f, side)
                c.drawCircle(cx + cw * 0.26f, jy, jr * 0.8f, side)
            }
        }
    }
}
