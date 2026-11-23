package com.ngdstudio.mindimania.ui.game

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Lightweight particle / text-pop / flash effects drawn on the table canvas. */
class Fx(private val density: Float) {
    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val ax: Float, val ay: Float, var life: Float, val maxLife: Float,
        val size: Float, val color: Int, val shape: Int, var rot: Float, val vr: Float, val drag: Float,
    )

    private class Pop(
        val text: String, val x: Float, val y: Float, val color: Int, val outline: Int,
        val start: Long, val dur: Long, val size: Float, val rise: Float,
    )

    private class Ring(val x: Float, val y: Float, val color: Int, val start: Long, val dur: Long, val maxR: Float, val width: Float)
    private class Flash(val color: Int, val start: Long, val dur: Long, val peak: Float)
    private class Bolt(val points: FloatArray, val start: Long, val dur: Long)

    private val particles = ArrayList<Particle>(512)
    private val pops = ArrayList<Pop>()
    private val rings = ArrayList<Ring>()
    private val flashes = ArrayList<Flash>()
    private val bolts = ArrayList<Bolt>()
    private var shakeStart = 0L
    private var shakeDur = 0L
    private var shakeAmp = 0f
    var reduceMotion = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rnd = Random(System.nanoTime())
    private var last = 0L

    val gemColors = intArrayOf(0xFFE0245E.toInt(), 0xFF1E7BFF.toInt(), 0xFF19C37D.toInt(), 0xFFFFB000.toInt(), 0xFFFFE89A.toInt(), 0xFF8B5CF6.toInt())

    /** Screen-shake offset for this frame, set by [updateShake] (no allocation per frame). */
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    fun updateShake(now: Long) {
        if (reduceMotion || now - shakeStart > shakeDur) { shakeX = 0f; shakeY = 0f; return }
        val k = 1f - (now - shakeStart).toFloat() / shakeDur
        val a = shakeAmp * k * density
        shakeX = (rnd.nextFloat() * 2 - 1) * a
        shakeY = (rnd.nextFloat() * 2 - 1) * a
    }

    /** True while any particle, text pop, ring, flash, bolt or shake is still on screen. */
    fun isActive(now: Long): Boolean =
        particles.isNotEmpty() || pops.isNotEmpty() || rings.isNotEmpty() || flashes.isNotEmpty() || bolts.isNotEmpty() ||
            (!reduceMotion && now - shakeStart <= shakeDur)

    fun shake(amp: Float, dur: Long, now: Long) {
        shakeStart = now
        shakeDur = dur
        shakeAmp = amp
    }

    fun burst(x: Float, y: Float, count: Int, colors: IntArray, speed: Float = 260f, size: Float = 4f, gravity: Float = 300f, life: Float = 0.9f, shape: Int = 0) {
        val n = if (reduceMotion) count / 3 else count
        repeat(n) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val sp = speed * (0.35f + rnd.nextFloat() * 0.8f) * density
            particles += Particle(
                x, y, cos(a) * sp, sin(a) * sp, 0f, gravity * density,
                life * (0.6f + rnd.nextFloat() * 0.6f), life, size * density * (0.6f + rnd.nextFloat() * 0.8f),
                colors[rnd.nextInt(colors.size)], shape, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 720f, 1.6f,
            )
        }
    }

    fun confetti(width: Float, count: Int = 140) {
        val n = if (reduceMotion) 40 else count
        repeat(n) {
            particles += Particle(
                rnd.nextFloat() * width, -20f * density - rnd.nextFloat() * 200f * density,
                (rnd.nextFloat() - 0.5f) * 120f * density, (120f + rnd.nextFloat() * 200f) * density,
                0f, 160f * density, 3.2f + rnd.nextFloat(), 4f, (5f + rnd.nextFloat() * 5f) * density,
                gemColors[rnd.nextInt(gemColors.size)], 1, rnd.nextFloat() * 360, (rnd.nextFloat() - 0.5f) * 900f, 0.4f,
            )
        }
    }

    fun sparkles(x: Float, y: Float, radius: Float, count: Int, color: Int) {
        repeat(if (reduceMotion) count / 3 else count) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val r = rnd.nextFloat() * radius
            particles += Particle(
                x + cos(a) * r, y + sin(a) * r, 0f, -30f * density, 0f, 0f,
                0.6f + rnd.nextFloat() * 0.6f, 1.2f, (3f + rnd.nextFloat() * 4f) * density, color, 2, 0f, 0f, 0.5f,
            )
        }
    }

    fun pop(text: String, x: Float, y: Float, color: Int, now: Long, size: Float = 34f, dur: Long = 1100, outline: Int = 0xFF2A0E5C.toInt(), rise: Float = 40f) {
        pops += Pop(text, x, y, color, outline, now, dur, size * density, rise * density)
    }

    fun ring(x: Float, y: Float, color: Int, now: Long, maxR: Float, dur: Long = 600, width: Float = 6f) {
        rings += Ring(x, y, color, now, dur, maxR, width * density)
    }

    fun flash(color: Int, now: Long, dur: Long = 500, peak: Float = 0.6f) {
        flashes += Flash(color, now, dur, if (reduceMotion) peak * 0.4f else peak)
    }

    fun lightning(x0: Float, y0: Float, x1: Float, y1: Float, now: Long) {
        val segs = 9
        val pts = FloatArray((segs + 1) * 2)
        for (i in 0..segs) {
            val t = i / segs.toFloat()
            val jitter = if (i == 0 || i == segs) 0f else (rnd.nextFloat() - 0.5f) * 60f * density
            pts[i * 2] = x0 + (x1 - x0) * t + jitter
            pts[i * 2 + 1] = y0 + (y1 - y0) * t
        }
        bolts += Bolt(pts, now, 380)
    }

    val busy: Boolean get() = particles.isNotEmpty() || pops.isNotEmpty() || rings.isNotEmpty() || flashes.isNotEmpty() || bolts.isNotEmpty()

    fun draw(c: Canvas, now: Long, width: Float, height: Float) {
        val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1000f)
        last = now

        // rings
        val ri = rings.iterator()
        while (ri.hasNext()) {
            val r = ri.next()
            val t = (now - r.start).toFloat() / r.dur
            if (t >= 1f) { ri.remove(); continue }
            if (t < 0) continue
            val e = 1f - (1f - t) * (1f - t)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r.width * (1f - t * 0.7f)
            p.color = withAlpha(r.color, (1f - t))
            c.drawCircle(r.x, r.y, r.maxR * e, p)
        }
        p.style = Paint.Style.FILL

        // particles
        val it = particles.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.life -= dt
            if (q.life <= 0f) { it.remove(); continue }
            q.vx += q.ax * dt
            q.vy += q.ay * dt
            val dragK = 1f - q.drag * dt
            q.vx *= dragK
            q.vy *= dragK
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.rot += q.vr * dt
            val alpha = (q.life / q.maxLife).coerceIn(0f, 1f)
            p.color = withAlpha(q.color, if (q.shape == 1) min(1f, alpha * 2) else alpha)
            when (q.shape) {
                1 -> {
                    c.save()
                    c.translate(q.x, q.y)
                    c.rotate(q.rot)
                    c.scale(1f, 0.55f + 0.45f * sin(q.rot / 30f))
                    c.drawRect(-q.size / 2, -q.size / 3, q.size / 2, q.size / 3, p)
                    c.restore()
                }
                2 -> drawSparkle(c, q.x, q.y, q.size * (0.5f + alpha), p)
                3 -> { // shard
                    c.save()
                    c.translate(q.x, q.y)
                    c.rotate(q.rot)
                    val path = Path().apply { moveTo(0f, -q.size); lineTo(q.size * 0.6f, q.size * 0.4f); lineTo(-q.size * 0.5f, q.size * 0.6f); close() }
                    c.drawPath(path, p)
                    c.restore()
                }
                else -> c.drawCircle(q.x, q.y, q.size * (0.4f + 0.6f * alpha), p)
            }
        }

        // lightning
        val bi = bolts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            val t = (now - b.start).toFloat() / b.dur
            if (t >= 1f) { bi.remove(); continue }
            val alpha = if (t < 0.15f || (t > 0.35f && t < 0.5f)) 1f else 0.35f * (1f - t)
            val path = Path()
            path.moveTo(b.points[0], b.points[1])
            var i = 2
            while (i < b.points.size) { path.lineTo(b.points[i], b.points[i + 1]); i += 2 }
            glowPaint.color = withAlpha(0xFFB9E6FF.toInt(), alpha * 0.6f)
            glowPaint.strokeWidth = 14f * density
            glowPaint.maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
            c.drawPath(path, glowPaint)
            glowPaint.maskFilter = null
            glowPaint.color = withAlpha(0xFFFFFFFF.toInt(), alpha)
            glowPaint.strokeWidth = 3.5f * density
            c.drawPath(path, glowPaint)
        }

        // text pops
        val pi = pops.iterator()
        while (pi.hasNext()) {
            val q = pi.next()
            val t = (now - q.start).toFloat() / q.dur
            if (t >= 1f) { pi.remove(); continue }
            if (t < 0f) continue
            val scaleIn = if (t < 0.18f) easeOutBack(t / 0.18f) else 1f
            val alpha = if (t > 0.7f) (1f - (t - 0.7f) / 0.3f) else 1f
            val y = q.y - q.rise * t
            c.save()
            c.translate(q.x, y)
            c.scale(scaleIn, scaleIn)
            textPaint.textSize = q.size
            val fm = textPaint.fontMetrics
            val by = -(fm.ascent + fm.descent) / 2
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeJoin = Paint.Join.ROUND
            textPaint.strokeWidth = q.size * 0.22f
            textPaint.color = withAlpha(0x88000000.toInt(), alpha)
            c.drawText(q.text, 0f, by + q.size * 0.08f, textPaint)
            textPaint.color = withAlpha(q.outline, alpha)
            textPaint.strokeWidth = q.size * 0.16f
            c.drawText(q.text, 0f, by, textPaint)
            textPaint.style = Paint.Style.FILL
            textPaint.color = withAlpha(q.color, alpha)
            c.drawText(q.text, 0f, by, textPaint)
            textPaint.color = withAlpha(0x55FFFFFF, alpha)
            c.save()
            c.clipRect(-width, by - q.size * 0.75f, width, by - q.size * 0.38f)
            c.drawText(q.text, 0f, by, textPaint)
            c.restore()
            c.restore()
        }

        // full-screen flashes
        val fi = flashes.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            val t = (now - f.start).toFloat() / f.dur
            if (t >= 1f) { fi.remove(); continue }
            if (t < 0f) continue
            val a = if (t < 0.25f) t / 0.25f else 1f - (t - 0.25f) / 0.75f
            p.color = withAlpha(f.color, a * f.peak)
            c.drawRect(0f, 0f, width, height, p)
        }
    }

    private fun drawSparkle(c: Canvas, x: Float, y: Float, s: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x, y - s); quadTo(x, y, x + s, y); quadTo(x, y, x, y + s); quadTo(x, y, x - s, y); quadTo(x, y, x, y - s); close()
        }
        c.drawPath(path, paint)
    }

    companion object {
        fun withAlpha(color: Int, a: Float): Int {
            val base = (color ushr 24) and 0xFF
            val na = (base * a.coerceIn(0f, 1f)).toInt()
            return (color and 0x00FFFFFF) or (na shl 24)
        }

        fun easeOutBack(t: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1
            val x = t - 1
            return 1 + c3 * x * x * x + c1 * x * x
        }

        fun easeOutCubic(t: Float): Float {
            val x = 1 - t
            return 1 - x * x * x
        }

        fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2

        @Suppress("unused")
        fun radial(x: Float, y: Float, r: Float, color: Int) = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
    }
}
