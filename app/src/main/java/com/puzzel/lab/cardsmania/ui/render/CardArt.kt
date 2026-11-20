package com.puzzel.lab.cardsmania.ui.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Suit
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.ui.theme.Gems
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Card back designs (collectible). */
enum class CardBack(@StringRes val titleRes: Int, val c1: Int, val c2: Int, val accent: Int, val pattern: Int, val price: Int, val level: Int) {
    ROYAL(R.string.data_back_royal, 0xFF3A1784.toInt(), 0xFF150638.toInt(), 0xFFFFC93C.toInt(), 0, 0, 1),
    CRIMSON(R.string.data_back_crimson, 0xFF8E0F3A.toInt(), 0xFF2E0414.toInt(), 0xFFFFD27A.toInt(), 1, 400, 1),
    SAPPHIRE(R.string.data_back_sapphire, 0xFF0F4BB0.toInt(), 0xFF061A47.toInt(), 0xFF9FE3FF.toInt(), 2, 400, 2),
    EMERALD(R.string.data_back_emerald, 0xFF0A7A4C.toInt(), 0xFF032A1A.toInt(), 0xFFB8FFD9.toInt(), 1, 600, 3),
    OBSIDIAN(R.string.data_back_obsidian, 0xFF2A2A35.toInt(), 0xFF07070B.toInt(), 0xFFE8E8F0.toInt(), 3, 800, 4),
    ROSE_GOLD(R.string.data_back_rose_gold, 0xFFB5577A.toInt(), 0xFF4A1530.toInt(), 0xFFFFD1B8.toInt(), 0, 900, 5),
    ARCTIC(R.string.data_back_arctic, 0xFF4FA7C9.toInt(), 0xFF123A55.toInt(), 0xFFFFFFFF.toInt(), 2, 1100, 6),
    SUNBURST(R.string.data_back_sunburst, 0xFFE06A00.toInt(), 0xFF5A1C00.toInt(), 0xFFFFF0A0.toInt(), 3, 1300, 8),
    GALAXY(R.string.data_back_galaxy, 0xFF3B1E8F.toInt(), 0xFF05021A.toInt(), 0xFFFF9DF2.toInt(), 4, 1600, 10),
    NEON(R.string.data_back_neon, 0xFF0B0B2A.toInt(), 0xFF000008.toInt(), 0xFF2CFFE1.toInt(), 5, 2000, 12);

    val title: String get() = Strings.get(titleRes)
}

/**
 * Renders original Cards Mania card art into bitmaps with android.graphics, and caches them.
 * Card proportions are 5:7.
 */
object CardArt {
    /**
     * All bitmaps for one card width. Looked up with the Face / CardBack itself as the key, so drawing a card every
     * frame allocates nothing (no key strings). A new width swaps in a fresh set.
     */
    private class Sheet(val w: Int) {
        val h: Int = (w * 1.4f).toInt()
        val faces = ConcurrentHashMap<Face, ImageBitmap>()
        val facesNoCb = ConcurrentHashMap<Face, ImageBitmap>()
        val backs = ConcurrentHashMap<CardBack, ImageBitmap>()
    }

    @Volatile private var sheet = Sheet(240)

    val widthPx: Int get() = sheet.w

    fun configure(width: Int) {
        val w = width.coerceIn(140, 340)
        if (w != sheet.w) sheet = Sheet(w)
    }

    val heightPx: Int get() = sheet.h

    fun face(face: Face, colorBlind: Boolean = true): ImageBitmap {
        val s = sheet
        val map = if (colorBlind) s.faces else s.facesNoCb
        return map[face] ?: map.getOrPut(face) { renderFace(face, s.w, s.h, colorBlind).asImageBitmap() }
    }

    fun back(back: CardBack): ImageBitmap {
        val s = sheet
        return s.backs[back] ?: s.backs.getOrPut(back) { renderBack(back, s.w, s.h).asImageBitmap() }
    }

    fun clear() {
        sheet = Sheet(sheet.w)
    }

    // ---------------------------------------------------------------- rendering

    private val bold: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD)

    /**
     * Bold, high-contrast card face: solid color body, white frame (dark frame on the Dark side),
     * a big white diamond in the middle with a huge outlined number/symbol, and large corner indices
     * that stay readable when cards overlap in the hand.
     */
    fun renderFace(face: Face, w: Int, h: Int, colorBlind: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fw = w.toFloat()
        val fh = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tone = Gems.tone(face.suit)
        val base = tone.base.toArgb()
        val light = tone.light.toArgb()
        val dark = tone.dark.toArgb()
        val deep = tone.deep.toArgb()
        val darkSide = face.suit.isMoon
        val ink = 0xFF14101F.toInt()

        // Frame
        val r = fw * 0.1f
        val outer = RectF(0f, 0f, fw, fh)
        p.color = if (darkSide) 0xFF15111F.toInt() else 0xFFFFFFFF.toInt()
        c.drawRoundRect(outer, r, r, p)
        if (darkSide) {
            // thin colored rim so the Dark side reads at a glance
            p.style = Paint.Style.STROKE
            p.strokeWidth = fw * 0.018f
            p.color = light
            c.drawRoundRect(inset(outer, fw * 0.03f), r * 0.85f, r * 0.85f, p)
            p.style = Paint.Style.FILL
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = fw * 0.012f
        p.color = 0x55000000
        c.drawRoundRect(inset(outer, fw * 0.006f), r, r, p)
        p.style = Paint.Style.FILL

        // Body
        val body = inset(outer, fw * 0.058f)
        val br = r * 0.7f
        p.color = 0xFF000000.toInt()
        p.shader = if (face.isWild) {
            LinearGradient(0f, body.top, 0f, body.bottom, intArrayOf(0xFF333340.toInt(), 0xFF1A1A22.toInt(), 0xFF0A0A0F.toInt()), null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, body.top, 0f, body.bottom, intArrayOf(lighten(base, 0.18f), base, darken(base, 0.18f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(body, br, br, p)
        p.shader = null
        val clip = Path().apply { addRoundRect(body, br, br, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        // soft diagonal gloss
        p.color = 0x1FFFFFFF
        c.drawPath(Path().apply { moveTo(body.left, body.top); lineTo(body.left + body.width() * 0.7f, body.top); lineTo(body.left, body.top + body.height() * 0.45f); close() }, p)
        if (darkSide) {
            val rnd = java.util.Random(7)
            p.color = 0x55FFFFFF
            repeat(14) { c.drawCircle(body.left + rnd.nextFloat() * body.width(), body.top + rnd.nextFloat() * body.height(), fw * (0.004f + rnd.nextFloat() * 0.006f), p) }
        }
        c.restore()

        // Center diamond
        val cx = fw / 2f
        val cy = fh / 2f
        val ew = fw * 0.40f
        val eh = fh * 0.31f
        val dia = roundedDiamond(cx, cy, ew, eh, fw * 0.05f)
        p.color = 0x40000000
        c.save(); c.translate(fw * 0.012f, fw * 0.022f); c.drawPath(dia, p); c.restore()
        if (face.isWild) {
            drawWildDiamond(c, cx, cy, ew, eh, fw)
        } else {
            p.color = 0xFFFFFFFF.toInt()
            c.drawPath(dia, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = fw * 0.014f
            p.color = dark
            c.drawPath(dia, p)
            p.style = Paint.Style.FILL
        }

        // Center content
        drawCenter(c, face, cx, cy, ew, eh, base, ink, fw)

        // Corner indices
        drawCorner(c, face, fw, fh, colorBlind, darkSide)
        c.save()
        c.rotate(180f, cx, cy)
        drawCorner(c, face, fw, fh, colorBlind, darkSide)
        c.restore()
        @Suppress("UNUSED_VARIABLE") val unused = deep
        return bmp
    }

    private fun inset(r: RectF, d: Float) = RectF(r.left + d, r.top + d, r.right - d, r.bottom - d)

    private fun roundedDiamond(cx: Float, cy: Float, ew: Float, eh: Float, round: Float): Path {
        val pts = arrayOf(floatArrayOf(cx, cy - eh), floatArrayOf(cx + ew, cy), floatArrayOf(cx, cy + eh), floatArrayOf(cx - ew, cy))
        val path = Path()
        for (i in 0 until 4) {
            val prev = pts[(i + 3) % 4]
            val cur = pts[i]
            val next = pts[(i + 1) % 4]
            fun towards(a: FloatArray, b: FloatArray, d: Float): FloatArray {
                val dx = b[0] - a[0]; val dy = b[1] - a[1]
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                return floatArrayOf(a[0] + dx / len * d, a[1] + dy / len * d)
            }
            val a = towards(cur, prev, round)
            val b = towards(cur, next, round)
            if (i == 0) path.moveTo(a[0], a[1]) else path.lineTo(a[0], a[1])
            path.quadTo(cur[0], cur[1], b[0], b[1])
        }
        path.close()
        return path
    }

    private val wildColors = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFDC500.toInt(), 0xFF2EAD4B.toInt())

    /** Four-color diamond used on every Wild card. */
    private fun drawWildDiamond(c: Canvas, cx: Float, cy: Float, ew: Float, eh: Float, fw: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val dia = roundedDiamond(cx, cy, ew, eh, fw * 0.05f)
        c.save()
        c.clipPath(dia)
        // quadrants: top-left red, top-right blue, bottom-right yellow, bottom-left green
        val quads = arrayOf(
            RectF(cx - ew, cy - eh, cx, cy), RectF(cx, cy - eh, cx + ew, cy),
            RectF(cx, cy, cx + ew, cy + eh), RectF(cx - ew, cy, cx, cy + eh),
        )
        for (i in 0 until 4) {
            p.color = 0xFF000000.toInt()
            p.shader = LinearGradient(quads[i].left, quads[i].top, quads[i].right, quads[i].bottom, intArrayOf(lighten(wildColors[i], 0.25f), wildColors[i]), null, Shader.TileMode.CLAMP)
            c.drawRect(quads[i], p)
        }
        p.shader = null
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = fw * 0.022f
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(dia, p)
        p.style = Paint.Style.FILL
    }

    private fun lighten(color: Int, f: Float): Int {
        val a = color ushr 24 and 0xFF
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        fun l(v: Int) = (v + (255 - v) * f).toInt().coerceIn(0, 255)
        return (a shl 24) or (l(r) shl 16) or (l(g) shl 8) or l(b)
    }

    fun drawSparkle(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        val path = Path().apply {
            moveTo(x, y - s); quadTo(x, y, x + s, y); quadTo(x, y, x, y + s); quadTo(x, y, x - s, y); quadTo(x, y, x, y - s); close()
        }
        c.drawPath(path, p)
    }

    private fun textPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bold
        textSize = size
        this.color = color
        textAlign = Paint.Align.CENTER
    }

    private fun drawOutlinedText(c: Canvas, text: String, x: Float, y: Float, size: Float, fill: Int, outline: Int, stroke: Float, skew: Boolean = true, shadow: Boolean = true) {
        val tp = textPaint(size, outline)
        if (skew) tp.textSkewX = -0.1f
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = stroke
        tp.strokeJoin = Paint.Join.ROUND
        val fm = tp.fontMetrics
        val by = y - (fm.ascent + fm.descent) / 2
        if (shadow) {
            tp.color = 0x59000000
            c.drawText(text, x + stroke * 0.35f, by + stroke * 0.55f, tp)
        }
        tp.color = outline
        c.drawText(text, x, by, tp)
        tp.style = Paint.Style.FILL
        tp.color = fill
        c.drawText(text, x, by, tp)
    }

    private fun drawCenter(c: Canvas, face: Face, cx: Float, cy: Float, ew: Float, eh: Float, base: Int, ink: Int, fw: Float) {
        val white = 0xFFFFFFFF.toInt()
        when (face.kind) {
            Kind.NUMBER -> {
                drawOutlinedText(c, face.value.toString(), cx, cy, fw * 0.62f, base, ink, fw * 0.05f)
                if (face.value == 6 || face.value == 9) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
                    c.drawRoundRect(RectF(cx - fw * 0.12f, cy + fw * 0.25f, cx + fw * 0.12f, cy + fw * 0.29f), fw * 0.02f, fw * 0.02f, p)
                }
            }
            Kind.DRAW -> drawOutlinedText(c, "+${face.value}", cx, cy, fw * 0.48f, base, ink, fw * 0.045f)
            Kind.BLOCK -> CardIcons.block(c, cx, cy, fw * 0.25f, base, ink)
            Kind.BLOCK_ALL -> CardIcons.blockAll(c, cx, cy, fw * 0.27f, base, ink)
            Kind.REVERSE -> CardIcons.reverse(c, cx, cy, fw * 0.27f, base, ink)
            Kind.PURGE -> CardIcons.purge(c, cx, cy, fw * 0.24f, base, ink)
            Kind.ECLIPSE -> CardIcons.eclipse(c, cx, cy, fw * 0.25f, base, ink)
            Kind.PRISM -> Unit
            Kind.PRISM_DRAW -> drawOutlinedText(c, "+${face.value}", cx, cy, fw * (if (face.value >= 10) 0.4f else 0.48f), white, ink, fw * 0.055f)
            Kind.PRISM_REBOUND -> {
                CardIcons.reverse(c, cx, cy - eh * 0.25f, fw * 0.17f, white, ink)
                drawOutlinedText(c, "+4", cx, cy + eh * 0.3f, fw * 0.32f, white, ink, fw * 0.045f)
            }
            Kind.COLOR_STORM -> CardIcons.storm(c, cx, cy, fw * 0.25f, white, ink)
            Kind.PRISM_HUNT -> {
                CardIcons.hunt(c, cx, cy - eh * 0.22f, fw * 0.16f, white, ink)
                drawOutlinedText(c, "+?", cx, cy + eh * 0.32f, fw * 0.28f, white, ink, fw * 0.04f)
            }
        }
    }

    private fun drawCorner(c: Canvas, face: Face, fw: Float, fh: Float, colorBlind: Boolean, darkSide: Boolean) {
        val x = fw * 0.18f
        val y = fh * 0.13f
        val white = 0xFFFFFFFF.toInt()
        val outline = if (face.isWild) 0xFF000000.toInt() else Gems.tone(face.suit).deep.toArgb()
        val label = when (face.kind) {
            Kind.NUMBER -> face.value.toString()
            Kind.DRAW, Kind.PRISM_DRAW -> "+${face.value}"
            Kind.PRISM_REBOUND -> "+4"
            Kind.PRISM_HUNT -> "+?"
            else -> null
        }
        if (label != null) {
            val size = when {
                label.length >= 3 -> fw * 0.17f
                label.length == 2 -> fw * 0.2f
                else -> fw * 0.25f
            }
            drawOutlinedText(c, label, x, y, size, white, outline, fw * 0.03f, skew = false, shadow = false)
        } else {
            val s = fw * 0.085f
            when (face.kind) {
                Kind.BLOCK -> CardIcons.block(c, x, y, s, white, outline)
                Kind.BLOCK_ALL -> CardIcons.blockAll(c, x, y, s * 1.05f, white, outline)
                Kind.REVERSE -> CardIcons.reverse(c, x, y, s * 1.1f, white, outline)
                Kind.PURGE -> CardIcons.purge(c, x, y, s, white, outline)
                Kind.ECLIPSE -> CardIcons.eclipse(c, x, y, s, white, outline)
                Kind.COLOR_STORM -> CardIcons.storm(c, x, y, s * 1.1f, white, outline)
                else -> CardIcons.miniPrism(c, x, y, s)
            }
        }
        if (colorBlind && !face.isWild) {
            SuitSymbols.draw(c, face.suit, x, y + fh * 0.1f, fw * 0.045f, white, outline)
        }
        @Suppress("UNUSED_VARIABLE") val unused = darkSide
    }

    fun renderBack(back: CardBack, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fw = w.toFloat()
        val fh = h.toFloat()
        val r = fw * 0.09f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val outer = RectF(0f, 0f, fw, fh)
        p.color = 0xFF000000.toInt()
        p.shader = LinearGradient(0f, 0f, fw, fh, intArrayOf(lighten(back.accent, 0.5f), back.accent, darken(back.accent, 0.45f), back.accent), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(outer, r, r, p)
        val body = inset(outer, fw * 0.045f)
        val br = r * 0.75f
        p.color = 0xFF000000.toInt()
        p.shader = RadialGradient(fw / 2, fh / 2, fh * 0.65f, intArrayOf(back.c1, back.c2), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(body, br, br, p)
        p.shader = null
        val clip = Path().apply { addRoundRect(body, br, br, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        val cx = fw / 2
        val cy = fh / 2
        val accentSoft = (back.accent and 0x00FFFFFF) or 0x2A000000
        p.color = accentSoft
        p.style = Paint.Style.STROKE
        p.strokeWidth = fw * 0.008f
        when (back.pattern) {
            0, 1 -> { // guilloché rings
                for (i in 1..14) c.drawCircle(cx, cy, fw * 0.06f * i, p)
                for (i in 0 until 24) {
                    val a = Math.toRadians(i * 15.0)
                    c.drawLine(cx, cy, cx + cos(a).toFloat() * fh, cy + sin(a).toFloat() * fh, p)
                }
            }
            2 -> { // waves
                for (row in 0..22) {
                    val path = Path()
                    val y0 = row * fh / 20f
                    path.moveTo(0f, y0)
                    var x = 0f
                    while (x <= fw) { path.quadTo(x + fw * 0.05f, y0 - fw * 0.04f, x + fw * 0.1f, y0); path.quadTo(x + fw * 0.15f, y0 + fw * 0.04f, x + fw * 0.2f, y0); x += fw * 0.2f }
                    c.drawPath(path, p)
                }
            }
            3 -> { // diamond lattice
                val step = fw * 0.12f
                var k = -fh
                while (k < fh * 2) { c.drawLine(0f, k, fw, k + fw, p); c.drawLine(0f, k + fw, fw, k, p); k += step }
            }
            4 -> { // galaxy stars
                p.style = Paint.Style.FILL
                val rnd = java.util.Random(11)
                repeat(70) {
                    p.color = (back.accent and 0x00FFFFFF) or ((60 + rnd.nextInt(160)) shl 24)
                    c.drawCircle(rnd.nextFloat() * fw, rnd.nextFloat() * fh, fw * (0.003f + rnd.nextFloat() * 0.01f), p)
                }
                p.style = Paint.Style.STROKE
            }
            else -> { // neon grid
                val step = fw * 0.1f
                var x = 0f
                while (x <= fw) { c.drawLine(x, 0f, x, fh, p); x += step }
                var y = 0f
                while (y <= fh) { c.drawLine(0f, y, fw, y, p); y += step }
            }
        }
        p.style = Paint.Style.FILL
        // diagonal gloss
        p.color = 0x18FFFFFF
        c.drawPath(Path().apply { moveTo(body.left, body.top); lineTo(body.left + fw * 0.7f, body.top); lineTo(body.left, body.top + fh * 0.5f); close() }, p)
        c.restore()

        p.style = Paint.Style.STROKE
        p.strokeWidth = fw * 0.012f
        p.color = (back.accent and 0x00FFFFFF) or 0x99000000.toInt()
        c.drawRoundRect(inset(body, fw * 0.03f), br * 0.7f, br * 0.7f, p)
        p.style = Paint.Style.FILL

        // Central emblem: gem badge with CM monogram
        val ew = fw * 0.3f
        val eh = fw * 0.3f
        val rh = Path().apply { moveTo(cx, cy - eh); lineTo(cx + ew, cy); lineTo(cx, cy + eh); lineTo(cx - ew, cy); close() }
        p.color = 0x66000000
        p.maskFilter = BlurMaskFilter(fw * 0.04f, BlurMaskFilter.Blur.NORMAL)
        c.save(); c.translate(0f, fw * 0.02f); c.drawPath(rh, p); c.restore()
        p.maskFilter = null
        p.color = 0xFF000000.toInt()
        p.shader = LinearGradient(cx - ew, cy - eh, cx + ew, cy + eh, intArrayOf(lighten(back.accent, 0.6f), back.accent, darken(back.accent, 0.5f)), null, Shader.TileMode.CLAMP)
        c.drawPath(rh, p)
        p.shader = null
        p.color = 0x30FFFFFF
        c.drawPath(Path().apply { moveTo(cx, cy - eh); lineTo(cx + ew, cy); lineTo(cx, cy); close() }, p)
        p.color = 0x22000000
        c.drawPath(Path().apply { moveTo(cx, cy + eh); lineTo(cx - ew, cy); lineTo(cx, cy); close() }, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = fw * 0.012f
        p.color = darken(back.accent, 0.6f)
        c.drawPath(rh, p)
        p.style = Paint.Style.FILL
        val tp = textPaint(fw * 0.15f, back.c2)
        val fm = tp.fontMetrics
        c.drawText("CM", cx, cy - (fm.ascent + fm.descent) / 2, tp)
        p.color = 0xFFFFFFFF.toInt()
        drawSparkle(c, cx - ew * 0.5f, cy - eh * 0.45f, fw * 0.04f, p)
        drawSparkle(c, fw * 0.2f, fh * 0.16f, fw * 0.03f, p.apply { color = (back.accent and 0x00FFFFFF) or 0xAA000000.toInt() })
        drawSparkle(c, fw * 0.8f, fh * 0.84f, fw * 0.03f, p)
        return bmp
    }

    private fun darken(color: Int, f: Float): Int {
        val a = color ushr 24 and 0xFF
        fun d(v: Int) = (v * (1 - f)).toInt().coerceIn(0, 255)
        return (a shl 24) or (d(color shr 16 and 0xFF) shl 16) or (d(color shr 8 and 0xFF) shl 8) or d(color and 0xFF)
    }

    /** Render with Matrix-free helper for previews in Compose (non-cached). */
    fun scaled(src: Bitmap, w: Int): Bitmap {
        val s = w / src.width.toFloat()
        val m = Matrix().apply { setScale(s, s) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    @Suppress("unused")
    private fun minDim(a: Float, b: Float) = min(a, b)
}
