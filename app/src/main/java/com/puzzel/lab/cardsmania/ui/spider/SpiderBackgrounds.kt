package com.puzzel.lab.cardsmania.ui.spider

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.StringRes
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.i18n.Strings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

@kotlinx.serialization.Serializable
enum class SpiderBackground(@StringRes val titleRes: Int, val swatch: Long) {
    FELT(R.string.data_spider_bg_felt, 0xFF1E7B43),
    ROYAL(R.string.data_spider_bg_royal, 0xFF4A1F8F),
    WOOD(R.string.data_spider_bg_wood, 0xFF7A4A26),
    OCEAN(R.string.data_spider_bg_ocean, 0xFF0A5A7A),
    SUNSET(R.string.data_spider_bg_sunset, 0xFFE8547A),
    NIGHT(R.string.data_spider_bg_night, 0xFF0B1A3F),
    MEADOW(R.string.data_spider_bg_meadow, 0xFF3E9B4F),
    CANDY(R.string.data_spider_bg_candy, 0xFFB0457F);

    val title: String get() = Strings.get(titleRes)
}

/** Procedural full-screen table backgrounds for Spider Solitaire (deterministic, no image assets). */
object SpiderBackgrounds {
    private const val OPAQUE = 0xFF000000.toInt()

    fun render(bg: SpiderBackground, w: Int, h: Int): Bitmap {
        val bw = w.coerceAtLeast(1)
        val bh = h.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(0x5EED + bg.ordinal * 7919)
        val s = bw / 1080f
        val fw = bw.toFloat()
        val fh = bh.toFloat()
        when (bg) {
            SpiderBackground.FELT -> felt(c, fw, fh, rnd)
            SpiderBackground.ROYAL -> royal(c, fw, fh, s, rnd)
            SpiderBackground.WOOD -> wood(c, fw, fh, s, rnd)
            SpiderBackground.OCEAN -> ocean(c, fw, fh, s, rnd)
            SpiderBackground.SUNSET -> sunset(c, fw, fh, s, rnd)
            SpiderBackground.NIGHT -> night(c, fw, fh, s, rnd)
            SpiderBackground.MEADOW -> meadow(c, fw, fh, s, rnd)
            SpiderBackground.CANDY -> candy(c, fw, fh, s, rnd)
        }
        return bmp
    }

    // ------------------------------------------------------------------ helpers

    private fun paint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OPAQUE }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (ch((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or
            (ch((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or
            ch(a and 0xFF, b and 0xFF)
    }

    private fun lighten(c: Int, f: Float) = mix(c, 0xFFFFFFFF.toInt(), f)
    private fun darken(c: Int, f: Float) = mix(c, OPAQUE, f)
    private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

    private fun fill(c: Canvas, w: Float, h: Float, shader: Shader) {
        val p = paint()
        p.shader = shader
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)

    private fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray? = null) =
        RadialGradient(x, y, max(1f, r), colors, pos, Shader.TileMode.CLAMP)

    /** A tiny repeating noise tile, applied as a shader: cheap fine grain over the whole screen. */
    private fun noise(c: Canvas, w: Float, h: Float, rnd: Random, amp: Int) {
        val n = 128
        val px = IntArray(n * n) {
            val v = rnd.nextInt(-amp, amp + 1)
            if (v >= 0) (v shl 24) or 0xFFFFFF else ((-v) shl 24)
        }
        val tile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        val p = paint()
        p.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun vignette(c: Canvas, w: Float, h: Float, strength: Int = 0x8C, color: Int = OPAQUE) {
        val r = hypot(w, h) * 0.62f
        fill(
            c, w, h,
            radial(w / 2f, h * 0.46f, r, intArrayOf(withAlpha(color, 0), withAlpha(color, 0), withAlpha(color, strength)), floatArrayOf(0f, 0.55f, 1f)),
        )
    }

    /** Soft round "bokeh" discs with a slightly brighter rim. */
    private fun bokeh(c: Canvas, w: Float, h: Float, rnd: Random, count: Int, colors: IntArray, minR: Float, maxR: Float, maxAlpha: Int) {
        val p = paint()
        repeat(count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = rnd.range(minR, maxR)
            val col = colors[rnd.nextInt(colors.size)]
            val a = (maxAlpha * rnd.range(0.35f, 1f)).toInt()
            p.shader = radial(
                x, y, r,
                intArrayOf(withAlpha(col, (a * 0.5f).toInt()), withAlpha(col, (a * 0.42f).toInt()), withAlpha(col, a), withAlpha(col, 0)),
                floatArrayOf(0f, 0.72f, 0.88f, 1f),
            )
            c.drawCircle(x, y, r, p)
        }
    }

    private fun softGlow(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        val p = paint()
        p.shader = radial(x, y, r, intArrayOf(withAlpha(color, alpha), withAlpha(color, (alpha * 0.4f).toInt()), withAlpha(color, 0)), floatArrayOf(0f, 0.45f, 1f))
        c.drawCircle(x, y, r, p)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, sz: Float, p: Paint) {
        val path = Path().apply {
            moveTo(x, y - sz); quadTo(x, y, x + sz, y); quadTo(x, y, x, y + sz); quadTo(x, y, x - sz, y); quadTo(x, y, x, y - sz); close()
        }
        c.drawPath(path, p)
    }

    // ------------------------------------------------------------------ backgrounds

    private fun felt(c: Canvas, w: Float, h: Float, rnd: Random) {
        fill(
            c, w, h,
            radial(w / 2f, h * 0.42f, max(w, h) * 0.75f, intArrayOf(0xFF2FA05A.toInt(), 0xFF1C7C42.toInt(), 0xFF0B4223.toInt()), floatArrayOf(0f, 0.5f, 1f)),
        )
        noise(c, w, h, rnd, 16)
        // faint woven diagonal texture
        val p = paint()
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w / 1080f * 1.2f)
        p.color = 0x07FFFFFF
        var x = -h
        val step = max(6f, w / 160f)
        while (x < w) {
            c.drawLine(x, 0f, x + h, h, p)
            x += step
        }
        vignette(c, w, h, 0x9A)
    }

    private fun royal(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        fill(
            c, w, h,
            radial(w / 2f, h * 0.38f, max(w, h) * 0.8f, intArrayOf(0xFF5A2AA8.toInt(), 0xFF4A1F8F.toInt(), 0xFF0E0428.toInt()), floatArrayOf(0f, 0.38f, 1f)),
        )
        noise(c, w, h, rnd, 10)
        val gold = 0xFFFFC93C.toInt()
        val p = paint()
        repeat(170) {
            p.color = withAlpha(gold, rnd.nextInt(30, 150))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.range(0.8f, 3.2f) * s, p)
        }
        repeat(16) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            softGlow(c, x, y, rnd.range(18f, 36f) * s, gold, 0x30)
            p.color = withAlpha(0xFFFFE9A8.toInt(), rnd.nextInt(120, 200))
            sparkle(c, x, y, rnd.range(6f, 13f) * s, p)
        }
        vignette(c, w, h, 0x99)
    }

    private fun wood(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        val planks = 4
        val pw = w / planks
        val palette = intArrayOf(0xFF7B4A27.toInt(), 0xFF6F421F.toInt(), 0xFF865331.toInt(), 0xFF744624.toInt())
        data class Knot(val plank: Int, val x: Float, val y: Float, val r: Float)
        val knotPlanks = (0 until planks).shuffled(rnd).take(3)
        val knots = knotPlanks.map { i ->
            Knot(i, i * pw + rnd.range(0.3f, 0.7f) * pw, rnd.range(0.15f, 0.85f) * h, rnd.range(16f, 30f) * s)
        }
        val p = paint()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val step = max(6f, 10f * s)
        for (i in 0 until planks) {
            val x0 = i * pw
            val base = mix(palette[i % palette.size], OPAQUE, rnd.range(0f, 0.06f))
            p.shader = linear(
                x0, 0f, x0 + pw, 0f,
                intArrayOf(darken(base, 0.18f), lighten(base, 0.06f), base, lighten(base, 0.08f), darken(base, 0.2f)),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            )
            c.drawRect(x0, 0f, x0 + pw, h, p)
            p.shader = linear(0f, 0f, 0f, h, intArrayOf(0x16000000, 0x00000000, 0x10FFFFFF, 0x00000000, 0x1A000000), floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f))
            c.drawRect(x0, 0f, x0 + pw, h, p)

            val mine = knots.filter { it.plank == i }
            c.save()
            c.clipRect(x0, 0f, x0 + pw, h)
            repeat(30) {
                val lx = x0 + rnd.nextFloat() * pw
                val a1 = rnd.range(2f, 8f) * s
                val f1 = rnd.range(0.0015f, 0.004f) / s
                val p1 = rnd.range(0f, 6.28f)
                val a2 = rnd.range(0.5f, 2f) * s
                val f2 = rnd.range(0.01f, 0.03f) / s
                val p2 = rnd.range(0f, 6.28f)
                val path = Path()
                var y = 0f
                var first = true
                while (y <= h + step) {
                    var x = lx + a1 * sin(y * f1 + p1) + a2 * sin(y * f2 + p2)
                    for (k in mine) {
                        val dx = lx - k.x
                        val reach = k.r * 2.4f
                        if (abs(dx) < reach) {
                            val dy = y - k.y
                            val sign = if (dx >= 0f) 1f else -1f
                            x += k.r * 1.3f * exp(-(dy / (k.r * 2.6f)) * (dy / (k.r * 2.6f))) * sign * (1f - abs(dx) / reach)
                        }
                    }
                    if (first) path.moveTo(x, y) else path.lineTo(x, y)
                    first = false
                    y += step
                }
                stroke.strokeWidth = rnd.range(0.6f, 2.4f) * s
                stroke.color = if (rnd.nextFloat() < 0.72f) withAlpha(darken(base, 0.45f), rnd.nextInt(30, 85))
                else withAlpha(lighten(base, 0.3f), rnd.nextInt(16, 36))
                c.drawPath(path, stroke)
            }
            for (k in mine) {
                p.shader = radial(k.x, k.y, k.r, intArrayOf(darken(base, 0.62f), withAlpha(darken(base, 0.4f), 0xB0), withAlpha(base, 0)), floatArrayOf(0f, 0.55f, 1f))
                c.drawOval(RectF(k.x - k.r * 0.7f, k.y - k.r * 1.2f, k.x + k.r * 0.7f, k.y + k.r * 1.2f), p)
                for (ring in 1..6) {
                    val rx = k.r * (0.25f + ring * 0.2f)
                    stroke.strokeWidth = max(1f, 1.3f * s)
                    stroke.color = withAlpha(darken(base, 0.5f), 70 - ring * 8)
                    c.drawOval(RectF(k.x - rx, k.y - rx * 1.8f, k.x + rx, k.y + rx * 1.8f), stroke)
                }
            }
            // butt joint
            val jy = rnd.range(0.25f, 0.75f) * h
            stroke.strokeWidth = max(1.5f, 2.5f * s)
            stroke.color = 0x70000000
            c.drawLine(x0, jy, x0 + pw, jy, stroke)
            stroke.strokeWidth = max(1f, 1f * s)
            stroke.color = 0x22FFFFFF
            c.drawLine(x0, jy + 2.5f * s, x0 + pw, jy + 2.5f * s, stroke)
            c.restore()
        }
        // plank seams
        for (i in 1 until planks) {
            val x = i * pw
            stroke.strokeWidth = max(1.5f, 3f * s)
            stroke.color = 0x80000000.toInt()
            c.drawLine(x, 0f, x, h, stroke)
            stroke.strokeWidth = max(1f, 1.2f * s)
            stroke.color = 0x24FFFFFF
            c.drawLine(x + 2.5f * s, 0f, x + 2.5f * s, h, stroke)
        }
        noise(c, w, h, rnd, 8)
        // varnish highlight
        fill(c, w, h, linear(0f, 0f, w, h, intArrayOf(0x00FFFFFF, 0x1EFFFFFF, 0x00FFFFFF, 0x10FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.3f, 0.45f, 0.7f, 1f)))
        vignette(c, w, h, 0x9A)
    }

    private fun ocean(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        fill(c, w, h, linear(0f, 0f, 0f, h, intArrayOf(0xFF0F7491.toInt(), 0xFF0A5275.toInt(), 0xFF062B4A.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        // light rays from the surface
        val p = paint()
        repeat(6) {
            val x = rnd.range(-0.1f, 1.1f) * w
            val spread = rnd.range(0.05f, 0.12f) * w
            val len = rnd.range(0.45f, 0.8f) * h
            val tilt = rnd.range(-0.15f, 0.15f) * w
            p.shader = linear(0f, 0f, 0f, len, intArrayOf(0x22D8FFFF, 0x00D8FFFF))
            c.drawPath(Path().apply {
                moveTo(x - spread * 0.25f, 0f); lineTo(x + spread * 0.25f, 0f)
                lineTo(x + tilt + spread, len); lineTo(x + tilt - spread, len); close()
            }, p)
        }
        // wave bands
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val cyan = 0xFF7FF0E6.toInt()
        val step = max(6f, 8f * s)
        repeat(10) { i ->
            val yBase = h * (0.06f + i * 0.1f) + rnd.range(-20f, 20f) * s
            val amp = rnd.range(14f, 38f) * s
            val freq = (2f * PI.toFloat()) / (w * rnd.range(0.6f, 1.3f))
            val ph = rnd.range(0f, 6.28f)
            val path = Path()
            var x = -step
            var first = true
            while (x <= w + step) {
                val y = yBase + amp * sin(x * freq + ph) + amp * 0.3f * sin(x * freq * 2.7f + ph * 1.3f)
                if (first) path.moveTo(x, y) else path.lineTo(x, y)
                first = false
                x += step
            }
            for (k in 0..3) {
                stroke.strokeWidth = (90f - k * 24f) * s
                stroke.color = withAlpha(cyan, 6 + k * 4)
                c.drawPath(path, stroke)
            }
        }
        // caustic blobs
        repeat(70) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = rnd.range(30f, 110f) * s
            c.save()
            c.translate(x, y)
            c.scale(1f, rnd.range(0.45f, 0.8f))
            softGlow(c, 0f, 0f, r, 0xFF9FF6FF.toInt(), rnd.nextInt(16, 40))
            c.restore()
        }
        noise(c, w, h, rnd, 6)
        vignette(c, w, h, 0x8C, 0xFF02101E.toInt())
    }

    private fun hill(c: Canvas, w: Float, h: Float, top: Float, amp: Float, color: Int, rnd: Random, s: Float): Path {
        val f1 = rnd.range(1.2f, 2.2f) * 2f * PI.toFloat() / w
        val f2 = rnd.range(2.5f, 4.5f) * 2f * PI.toFloat() / w
        val p1 = rnd.range(0f, 6.28f)
        val p2 = rnd.range(0f, 6.28f)
        val path = Path()
        path.moveTo(0f, h)
        var x = 0f
        val step = max(4f, 6f * s)
        while (x <= w + step) {
            val y = top + amp * (0.65f * sin(x * f1 + p1) + 0.35f * sin(x * f2 + p2))
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(w, h)
        path.close()
        val p = paint()
        p.shader = linear(0f, top - amp, 0f, h, intArrayOf(color, darken(color, 0.35f)))
        c.drawPath(path, p)
        return path
    }

    private fun sunset(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        fill(
            c, w, h,
            linear(
                0f, 0f, 0f, h,
                intArrayOf(0xFF3B1F6B.toInt(), 0xFF7E2B7A.toInt(), 0xFFD9507A.toInt(), 0xFFF5804F.toInt(), 0xFFFFB866.toInt()),
                floatArrayOf(0f, 0.3f, 0.55f, 0.74f, 0.86f),
            ),
        )
        val sx = w * 0.62f
        val sy = h * 0.8f
        softGlow(c, sx, sy, w * 0.75f, 0xFFFFD27A.toInt(), 0x90)
        softGlow(c, sx, sy, w * 0.28f, 0xFFFFF0C0.toInt(), 0xC0)
        val disc = paint()
        disc.color = 0xFFFFE9B0.toInt()
        c.drawCircle(sx, sy, w * 0.1f, disc)
        // soft clouds
        repeat(7) {
            val x = rnd.nextFloat() * w
            val y = rnd.range(0.12f, 0.62f) * h
            c.save()
            c.translate(x, y)
            c.scale(1f, 0.22f)
            softGlow(c, 0f, 0f, rnd.range(0.2f, 0.42f) * w, 0xFFFFD6E8.toInt(), rnd.nextInt(30, 60))
            c.restore()
        }
        // rolling hills (back to front)
        hill(c, w, h, h * 0.8f, 26f * s, 0xFF8A3566.toInt(), rnd, s)
        hill(c, w, h, h * 0.86f, 22f * s, 0xFF55204F.toInt(), rnd, s)
        hill(c, w, h, h * 0.92f, 18f * s, 0xFF2A1033.toInt(), rnd, s)
        // a few pine silhouettes on the front hill
        val tree = paint()
        tree.color = 0xFF1C0A24.toInt()
        repeat(9) {
            val tx = rnd.nextFloat() * w
            val th = rnd.range(40f, 90f) * s
            val ty = h * 0.92f + rnd.range(-6f, 14f) * s
            c.drawPath(Path().apply {
                moveTo(tx, ty - th); lineTo(tx + th * 0.28f, ty); lineTo(tx - th * 0.28f, ty); close()
            }, tree)
        }
        noise(c, w, h, rnd, 6)
        vignette(c, w, h, 0x70, 0xFF1A0620.toInt())
    }

    private fun night(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        fill(c, w, h, linear(0f, 0f, 0f, h, intArrayOf(0xFF0E2250.toInt(), 0xFF0A1434.toInt(), 0xFF04071A.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        // aurora curtain
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val layers = intArrayOf(0xFF3DDC97.toInt(), 0xFF19D3C5.toInt(), 0xFF8B5CF6.toInt())
        val step = max(6f, 8f * s)
        layers.forEachIndexed { li, col ->
            val yBase = h * (0.2f + li * 0.05f)
            val amp = rnd.range(30f, 60f) * s
            val f = 2f * PI.toFloat() / (w * rnd.range(0.7f, 1.2f))
            val ph = rnd.range(0f, 6.28f)
            for (k in 0 until 10) {
                val path = Path()
                var x = -step
                var first = true
                while (x <= w + step) {
                    val y = yBase + amp * sin(x * f + ph) + k * 11f * s
                    if (first) path.moveTo(x, y) else path.lineTo(x, y)
                    first = false
                    x += step
                }
                stroke.strokeWidth = 18f * s
                stroke.color = withAlpha(col, ((0x1A - li * 4) * (1f - k / 10f)).toInt())
                c.drawPath(path, stroke)
            }
        }
        // stars
        val p = paint()
        val tints = intArrayOf(0xFFFFFFFF.toInt(), 0xFFCFE3FF.toInt(), 0xFFFFE9C4.toInt())
        repeat(260) {
            val r = rnd.nextFloat()
            p.color = withAlpha(tints[rnd.nextInt(tints.size)], rnd.nextInt(70, 235))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (0.5f + r * r * r * 2.0f) * 1.6f * s, p)
        }
        repeat(18) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h * 0.85f
            softGlow(c, x, y, rnd.range(14f, 26f) * s, 0xFFCFE3FF.toInt(), 0x48)
            p.color = withAlpha(0xFFFFFFFF.toInt(), rnd.nextInt(170, 240))
            sparkle(c, x, y, rnd.range(5f, 10f) * s, p)
        }
        // crescent moon
        val mx = w * 0.8f
        val my = h * 0.11f
        val mr = w * 0.07f
        softGlow(c, mx, my, mr * 4.5f, 0xFFFFF3D6.toInt(), 0x50)
        val moon = Path().apply { addCircle(mx, my, mr, Path.Direction.CW) }
        moon.op(Path().apply { addCircle(mx + mr * 0.42f, my - mr * 0.22f, mr * 0.9f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        p.color = 0xFFFFF4D6.toInt()
        c.drawPath(moon, p)
        noise(c, w, h, rnd, 5)
        vignette(c, w, h, 0x90, 0xFF01020A.toInt())
    }

    private fun meadow(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        fill(c, w, h, linear(0f, 0f, 0f, h, intArrayOf(0xFF6CC26E.toInt(), 0xFF3E9B4F.toInt(), 0xFF205E30.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        softGlow(c, w * 0.15f, h * 0.05f, w * 0.9f, 0xFFFFF3A8.toInt(), 0x55)
        bokeh(c, w, h, rnd, 70, intArrayOf(0xFFFFFFFF.toInt(), 0xFFE8FF9A.toInt(), 0xFFFFF3A8.toInt()), 18f * s, 120f * s, 0x40)
        noise(c, w, h, rnd, 8)
        vignette(c, w, h, 0x80, 0xFF0A2A12.toInt())
    }

    private fun candy(c: Canvas, w: Float, h: Float, s: Float, rnd: Random) {
        // toned down so white cards keep strong contrast
        fill(c, w, h, linear(0f, 0f, w, h, intArrayOf(0xFFB0457F.toInt(), 0xFF7A4FB0.toInt(), 0xFF4E5DA8.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        // soft diagonal stripes
        val p = paint()
        p.color = 0x10FFFFFF
        c.save()
        c.rotate(-28f, w / 2f, h / 2f)
        val stripeW = w * 0.08f
        var x = -h
        while (x < w + h) {
            c.drawRect(x, -h, x + stripeW, h * 2f, p)
            x += stripeW * 2f
        }
        c.restore()
        bokeh(c, w, h, rnd, 40, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE0F0.toInt(), 0xFFE6D9FF.toInt()), 16f * s, 90f * s, 0x26)
        noise(c, w, h, rnd, 6)
        vignette(c, w, h, 0x90, 0xFF2A1040.toInt())
    }
}
