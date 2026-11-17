package com.puzzel.lab.cardsmania.ui.game

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.puzzel.lab.cardsmania.data.TableTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Pre-renders the felt table (day and night variants) into a screen-sized bitmap. */
object TableBackground {

    fun render(layout: TableLayout, theme: TableTheme, night: Boolean): Bitmap {
        val w = layout.width.toInt().coerceAtLeast(1)
        val h = layout.height.toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var center = theme.center.toArgb()
        var edge = theme.edge.toArgb()
        var accent = theme.accent.toArgb()
        if (night) {
            center = mix(center, 0xFF1B1460.toInt(), 0.65f)
            edge = mix(edge, 0xFF02010E.toInt(), 0.7f)
            accent = 0xFFC0A6FF.toInt()
        }
        val cx = w / 2f
        val cy = layout.pileY
        // base gradient
        p.color = 0xFF000000.toInt()
        p.shader = RadialGradient(cx, cy, h * 0.75f, intArrayOf(lighten(center, 0.12f), center, edge), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // felt noise
        val rnd = Random(42)
        val count = (w * h / 90).coerceAtMost(60000)
        repeat(count) {
            val a = rnd.nextInt(6, 18)
            p.color = if (rnd.nextBoolean()) (a shl 24) or 0xFFFFFF else (a shl 24)
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            c.drawRect(x, y, x + 1.3f, y + 1.3f, p)
        }

        // ornamental table oval
        val rx = w * 0.47f
        val ry = (layout.ringRy * 1.75f)
        val oval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
        p.color = 0xFF000000.toInt()
        p.shader = RadialGradient(cx, cy, rx, intArrayOf(0x22FFFFFF, 0x08FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(oval, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * layout.density
        p.color = withAlpha(accent, 0.35f)
        c.drawOval(oval, p)
        p.strokeWidth = 0.8f * layout.density
        p.color = withAlpha(accent, 0.2f)
        val inner = RectF(oval.left + 8 * layout.density, oval.top + 8 * layout.density, oval.right - 8 * layout.density, oval.bottom - 8 * layout.density)
        c.drawOval(inner, p)
        p.style = Paint.Style.FILL

        // gem diamonds around the oval
        for (i in 0 until 16) {
            val a = i / 16f * 2 * Math.PI
            val x = cx + cos(a).toFloat() * rx
            val y = cy + sin(a).toFloat() * ry
            val s = 4f * layout.density
            val path = Path().apply { moveTo(x, y - s); lineTo(x + s * 0.7f, y); lineTo(x, y + s); lineTo(x - s * 0.7f, y); close() }
            p.color = withAlpha(accent, 0.55f)
            c.drawPath(path, p)
        }

        // central emblem watermark
        val ew = w * 0.1f
        val emblem = Path().apply { moveTo(cx, cy - ew); lineTo(cx + ew * 0.8f, cy); lineTo(cx, cy + ew); lineTo(cx - ew * 0.8f, cy); close() }
        p.color = withAlpha(accent, 0.06f)
        c.drawPath(emblem, p)

        if (night) {
            // stars
            repeat(140) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val r = (0.5f + rnd.nextFloat() * 1.6f) * layout.density
                p.color = withAlpha(0xFFFFFFFF.toInt(), 0.15f + rnd.nextFloat() * 0.5f)
                c.drawCircle(x, y, r, p)
            }
            // moon glow top-right
            p.color = 0xFF000000.toInt()
            p.shader = RadialGradient(w * 0.85f, h * 0.08f, w * 0.5f, intArrayOf(0x40C0A6FF, 0x00C0A6FF), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
        } else {
            // warm light from the top
            p.color = 0xFF000000.toInt()
            p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, intArrayOf(withAlpha(accent, 0.10f), 0x00000000), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h * 0.5f, p)
            p.shader = null
        }

        // vignette
        p.color = 0xFF000000.toInt()
        p.shader = RadialGradient(cx, cy, h * 0.78f, intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // hand tray glow at the bottom
        p.color = 0xFF000000.toInt()
        p.shader = LinearGradient(0f, layout.handTopY - layout.handCardH * 0.3f, 0f, h.toFloat(), intArrayOf(0x00000000, 0x66000000), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, layout.handTopY - layout.handCardH * 0.3f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.maskFilter = BlurMaskFilter(20f * layout.density, BlurMaskFilter.Blur.NORMAL)
        p.color = withAlpha(accent, 0.12f)
        c.drawOval(RectF(w * 0.1f, layout.handBaseY - layout.handCardH * 0.2f, w * 0.9f, layout.handBaseY + layout.handCardH * 0.5f), p)
        p.maskFilter = null
        return bmp
    }

    private fun withAlpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((255 * a).toInt().coerceIn(0, 255) shl 24)

    private fun lighten(c: Int, f: Float): Int {
        fun l(v: Int) = (v + (255 - v) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (l((c shr 16) and 0xFF) shl 16) or (l((c shr 8) and 0xFF) shl 8) or l(c and 0xFF)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or (ch((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or ch(a and 0xFF, b and 0xFF)
    }
}
