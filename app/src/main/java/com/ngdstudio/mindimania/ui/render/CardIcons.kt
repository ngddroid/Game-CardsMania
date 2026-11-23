package com.ngdstudio.mindimania.ui.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ngdstudio.mindimania.engine.model.Suit
import kotlin.math.cos
import kotlin.math.sin

/** Original vector icons for card actions, drawn with android.graphics. s ≈ half-size of the icon. */
object CardIcons {

    private fun strokePaint(w: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }

    /** Draws [path] as a thick outline plus colored stroke. */
    private fun strokeBoth(c: Canvas, path: Path, w: Float, fill: Int, outline: Int) {
        c.drawPath(path, strokePaint(w * 1.75f, outline))
        c.drawPath(path, strokePaint(w, fill))
    }

    private fun fillBoth(c: Canvas, path: Path, w: Float, fill: Int, outline: Int) {
        c.drawPath(path, strokePaint(w, outline))
        c.drawPath(path, fillPaint(fill))
    }

    fun block(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val w = s * 0.24f
        val ring = Path().apply { addCircle(cx, cy, s * 0.78f, Path.Direction.CW) }
        val d = s * 0.55f
        val slash = Path().apply { moveTo(cx - d, cy + d); lineTo(cx + d, cy - d) }
        strokeBoth(c, ring, w, fill, outline)
        strokeBoth(c, slash, w, fill, outline)
    }

    fun blockAll(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        block(c, cx, cy, s * 0.72f, fill, outline)
        val w = s * 0.14f
        for (i in 0 until 4) {
            val start = i * 90f + 15f
            val arc = Path().apply { addArc(RectF(cx - s, cy - s, cx + s, cy + s), start, 60f) }
            strokeBoth(c, arc, w, fill, outline)
        }
    }

    fun reverse(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val w = s * 0.2f
        val r = s * 0.7f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        for (k in 0..1) {
            val start = if (k == 0) 200f else 20f
            val sweep = 125f
            val arc = Path().apply { addArc(rect, start, sweep) }
            val endA = Math.toRadians((start + sweep).toDouble())
            val ex = cx + cos(endA).toFloat() * r
            val ey = cy + sin(endA).toFloat() * r
            // arrow head tangent direction
            val tx = -sin(endA).toFloat()
            val ty = cos(endA).toFloat()
            val nx = cos(endA).toFloat()
            val ny = sin(endA).toFloat()
            val h = s * 0.38f
            val head = Path().apply {
                moveTo(ex + tx * h, ey + ty * h)
                lineTo(ex + nx * h * 0.75f - tx * h * 0.2f, ey + ny * h * 0.75f - ty * h * 0.2f)
                lineTo(ex - nx * h * 0.75f - tx * h * 0.2f, ey - ny * h * 0.75f - ty * h * 0.2f)
                close()
            }
            strokeBoth(c, arc, w, fill, outline)
            fillBoth(c, head, w * 0.9f, fill, outline)
        }
    }

    fun purge(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val flame = Path().apply {
            moveTo(cx, cy - s)
            cubicTo(cx + s * 0.25f, cy - s * 0.45f, cx + s * 0.85f, cy - s * 0.2f, cx + s * 0.7f, cy + s * 0.35f)
            cubicTo(cx + s * 0.6f, cy + s * 0.85f, cx - s * 0.6f, cy + s * 0.85f, cx - s * 0.7f, cy + s * 0.35f)
            cubicTo(cx - s * 0.8f, cy - s * 0.1f, cx - s * 0.35f, cy - s * 0.25f, cx - s * 0.25f, cy - s * 0.6f)
            cubicTo(cx - s * 0.05f, cy - s * 0.35f, cx - s * 0.05f, cy - s * 0.75f, cx, cy - s)
            close()
        }
        fillBoth(c, flame, s * 0.16f, fill, outline)
        val inner = Path().apply {
            moveTo(cx, cy - s * 0.15f)
            cubicTo(cx + s * 0.35f, cy + s * 0.1f, cx + s * 0.35f, cy + s * 0.55f, cx, cy + s * 0.6f)
            cubicTo(cx - s * 0.35f, cy + s * 0.55f, cx - s * 0.3f, cy + s * 0.15f, cx, cy - s * 0.15f)
            close()
        }
        c.drawPath(inner, fillPaint(0xFFFFFFFF.toInt()))
        // sweep streaks
        val st = strokePaint(s * 0.1f, outline)
        c.drawLine(cx - s * 1.05f, cy + s * 0.95f, cx + s * 1.05f, cy + s * 0.95f, st)
    }

    fun eclipse(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val r = s * 0.62f
        // sun rays on the left
        val rayP = strokePaint(s * 0.12f, outline)
        val rayF = strokePaint(s * 0.07f, fill)
        for (i in 0 until 7) {
            val a = Math.toRadians((100 + i * 26).toDouble())
            val x1 = cx + cos(a).toFloat() * r * 1.18f
            val y1 = cy + sin(a).toFloat() * r * 1.18f
            val x2 = cx + cos(a).toFloat() * r * 1.55f
            val y2 = cy + sin(a).toFloat() * r * 1.55f
            c.drawLine(x1, y1, x2, y2, rayP)
            c.drawLine(x1, y1, x2, y2, rayF)
        }
        val disc = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        c.drawPath(disc, strokePaint(s * 0.16f, outline))
        c.save()
        c.clipRect(cx - r * 2, cy - r * 2, cx, cy + r * 2)
        c.drawPath(disc, fillPaint(fill))
        c.restore()
        c.save()
        c.clipRect(cx, cy - r * 2, cx + r * 2, cy + r * 2)
        c.drawPath(disc, fillPaint(0xFF1A1045.toInt()))
        // crescent
        val moon = Path().apply {
            addCircle(cx + r * 0.25f, cy, r * 0.55f, Path.Direction.CW)
        }
        val cut = Path().apply { addCircle(cx + r * 0.45f, cy - r * 0.1f, r * 0.48f, Path.Direction.CW) }
        moon.op(cut, Path.Op.DIFFERENCE)
        c.drawPath(moon, fillPaint(0xFFFFF1B0.toInt()))
        c.restore()
        c.drawLine(cx, cy - r, cx, cy + r, strokePaint(s * 0.06f, outline))
    }

    fun storm(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val cloud = Path().apply {
            addCircle(cx - s * 0.38f, cy - s * 0.1f, s * 0.36f, Path.Direction.CW)
            addCircle(cx + s * 0.05f, cy - s * 0.32f, s * 0.46f, Path.Direction.CW)
            addCircle(cx + s * 0.45f, cy - s * 0.05f, s * 0.34f, Path.Direction.CW)
            addRoundRect(RectF(cx - s * 0.74f, cy - s * 0.1f, cx + s * 0.79f, cy + s * 0.29f), s * 0.18f, s * 0.18f, Path.Direction.CW)
        }
        val merged = Path().apply { op(cloud, Path.Op.UNION) }
        val union = Path()
        union.op(merged, cloud, Path.Op.UNION)
        fillBoth(c, union, s * 0.16f, fill, outline)
        val bolt = Path().apply {
            moveTo(cx + s * 0.05f, cy + s * 0.05f)
            lineTo(cx - s * 0.3f, cy + s * 0.6f)
            lineTo(cx - s * 0.02f, cy + s * 0.6f)
            lineTo(cx - s * 0.18f, cy + s * 1.05f)
            lineTo(cx + s * 0.35f, cy + s * 0.42f)
            lineTo(cx + s * 0.06f, cy + s * 0.42f)
            lineTo(cx + s * 0.25f, cy + s * 0.05f)
            close()
        }
        fillBoth(c, bolt, s * 0.1f, 0xFFFFD93C.toInt(), outline)
    }

    fun hunt(c: Canvas, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val w = s * 0.15f
        val ring = Path().apply { addCircle(cx, cy, s * 0.7f, Path.Direction.CW) }
        strokeBoth(c, ring, w, fill, outline)
        val ticks = Path().apply {
            moveTo(cx, cy - s); lineTo(cx, cy - s * 0.45f)
            moveTo(cx, cy + s); lineTo(cx, cy + s * 0.45f)
            moveTo(cx - s, cy); lineTo(cx - s * 0.45f, cy)
            moveTo(cx + s, cy); lineTo(cx + s * 0.45f, cy)
        }
        strokeBoth(c, ticks, w, fill, outline)
        miniPrism(c, cx, cy, s * 0.32f)
    }

    fun miniPrism(c: Canvas, cx: Float, cy: Float, s: Float) {
        val cols = intArrayOf(0xFFE0245E.toInt(), 0xFF1E7BFF.toInt(), 0xFF19C37D.toInt(), 0xFFFFB000.toInt())
        val pts = arrayOf(floatArrayOf(cx, cy - s), floatArrayOf(cx + s * 0.8f, cy), floatArrayOf(cx, cy + s), floatArrayOf(cx - s * 0.8f, cy))
        val outline = Path().apply { moveTo(pts[0][0], pts[0][1]); for (i in 1..3) lineTo(pts[i][0], pts[i][1]); close() }
        c.drawPath(outline, strokePaint(s * 0.3f, 0xFF0B0520.toInt()))
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            c.drawPath(Path().apply { moveTo(cx, cy); lineTo(a[0], a[1]); lineTo(b[0], b[1]); close() }, fillPaint(cols[i]))
        }
        c.drawPath(outline, strokePaint(s * 0.1f, 0xFFFFF1B0.toInt()))
    }
}

/** Suit symbols (color-blind support). */
object SuitSymbols {
    fun path(suit: Suit, cx: Float, cy: Float, s: Float): Path = when (suit) {
        Suit.RUBY -> Path().apply { // flame
            moveTo(cx, cy - s)
            cubicTo(cx + s * 0.9f, cy - s * 0.1f, cx + s * 0.8f, cy + s, cx, cy + s)
            cubicTo(cx - s * 0.8f, cy + s, cx - s * 0.85f, cy + s * 0.1f, cx - s * 0.3f, cy - s * 0.3f)
            cubicTo(cx - s * 0.2f, cy + s * 0.1f, cx + s * 0.05f, cy - s * 0.3f, cx, cy - s)
            close()
        }
        Suit.SAPPHIRE -> Path().apply { // droplet
            moveTo(cx, cy - s)
            cubicTo(cx + s * 0.4f, cy - s * 0.4f, cx + s * 0.85f, cy + s * 0.05f, cx + s * 0.75f, cy + s * 0.45f)
            cubicTo(cx + s * 0.6f, cy + s * 1.05f, cx - s * 0.6f, cy + s * 1.05f, cx - s * 0.75f, cy + s * 0.45f)
            cubicTo(cx - s * 0.85f, cy + s * 0.05f, cx - s * 0.4f, cy - s * 0.4f, cx, cy - s)
            close()
        }
        Suit.EMERALD -> Path().apply { // leaf
            moveTo(cx - s * 0.8f, cy + s * 0.8f)
            cubicTo(cx - s * 0.9f, cy - s * 0.2f, cx - s * 0.1f, cy - s * 0.9f, cx + s * 0.9f, cy - s * 0.9f)
            cubicTo(cx + s * 0.9f, cy + s * 0.1f, cx + s * 0.2f, cy + s * 0.9f, cx - s * 0.8f, cy + s * 0.8f)
            close()
        }
        Suit.AMBER -> star(cx, cy, s, s * 0.45f, 5)
        Suit.ROSE -> Path().apply { // heart
            moveTo(cx, cy + s * 0.9f)
            cubicTo(cx - s * 1.2f, cy, cx - s * 0.7f, cy - s * 1.0f, cx, cy - s * 0.4f)
            cubicTo(cx + s * 0.7f, cy - s * 1.0f, cx + s * 1.2f, cy, cx, cy + s * 0.9f)
            close()
        }
        Suit.AQUA -> Path().apply { // wave
            moveTo(cx - s, cy + s * 0.2f)
            cubicTo(cx - s * 0.6f, cy - s * 0.7f, cx - s * 0.1f, cy - s * 0.7f, cx, cy)
            cubicTo(cx + s * 0.1f, cy + s * 0.5f, cx + s * 0.6f, cy + s * 0.5f, cx + s, cy - s * 0.3f)
            lineTo(cx + s, cy + s * 0.6f)
            cubicTo(cx + s * 0.5f, cy + s * 1.1f, cx, cy + s * 0.9f, cx - s * 0.2f, cy + s * 0.5f)
            cubicTo(cx - s * 0.4f, cy + s * 0.1f, cx - s * 0.7f, cy + s * 0.2f, cx - s, cy + s * 0.9f)
            close()
        }
        Suit.TANGERINE -> star(cx, cy, s, s * 0.62f, 8) // sunburst
        Suit.VIOLET -> Path().apply { // crescent
            addCircle(cx, cy, s * 0.9f, Path.Direction.CW)
            val cut = Path().apply { addCircle(cx + s * 0.45f, cy - s * 0.3f, s * 0.75f, Path.Direction.CW) }
            op(cut, Path.Op.DIFFERENCE)
        }
        Suit.WILD -> star(cx, cy, s, s * 0.35f, 4)
    }

    private fun star(cx: Float, cy: Float, ro: Float, ri: Float, points: Int): Path = Path().apply {
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) ro else ri
            val a = Math.PI / points * i - Math.PI / 2
            val x = cx + cos(a).toFloat() * r
            val y = cy + sin(a).toFloat() * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun draw(c: Canvas, suit: Suit, cx: Float, cy: Float, s: Float, fill: Int, outline: Int) {
        val path = path(suit, cx, cy, s)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.35f; color = outline; strokeJoin = Paint.Join.ROUND })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
    }
}
