package com.ngdstudio.mindimania.ui.spider

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import com.ngdstudio.mindimania.spider.SpiderEngine
import com.ngdstudio.mindimania.spider.SpiderEvent
import com.ngdstudio.mindimania.spider.SpiderHint
import com.ngdstudio.mindimania.spider.SpiderMove
import com.ngdstudio.mindimania.spider.SpiderState
import com.ngdstudio.mindimania.ui.game.AnimatedWorld
import com.ngdstudio.mindimania.ui.game.Fx
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.render.CardBack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class FrameKind { NEW_GAME, RESTORE, MOVE, DEAL, COLLECT, UNDO }

/** One state change for the table to animate. */
data class SpiderFrame(val state: SpiderState, val events: List<SpiderEvent>, val kind: FrameKind, val seq: Long)

private class SSprite(val id: Int, var rank: Int, var suit: Int) {
    var x = 0f; var y = 0f
    var fx = 0f; var fy = 0f
    var tx = 0f; var ty = 0f
    var start = 0L; var delay = 0L; var dur = 1L; var arc = 0f
    var up = false
    var prevUp: Boolean? = null
    var flipStart = 0L; var flipDur = 1L
    var z = 0f
    var zone = SZone.STOCK
    var col = -1; var index = -1
    var moving = false
    var shakeAt = 0L
    /** Leave a sparkle trail while flying until this time. */
    var trailUntil = 0L
    var lastTrail = 0L
}

private class WinCard(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var vr: Float, val rank: Int, val suit: Int, var life: Float)

class SpiderWorld(val layout: SpiderLayout) : AnimatedWorld {
    private val sprites = HashMap<Int, SSprite>(128)
    /** The same sprites as [sprites], as a list so per-frame loops don't allocate iterators. */
    private val all = ArrayList<SSprite>(128)
    var state: SpiderState? = null
        private set
    val fx = Fx(layout.density)

    // configuration (from Compose)
    var background: SpiderBackground = SpiderBackground.FELT
    var cardBack: CardBack = CardBack.ROYAL
    var fourColor = false
    var speed = 1f
    var reduceMotion = false
        set(v) { field = v; fx.reduceMotion = v }
    /** Tap-then-tap-target selection (column, index), drawn lifted with glowing targets. */
    var selected: Pair<Int, Int>? = null

    // drag
    var dragCol = -1
    var dragIndex = -1
    var dragX = 0f
    var dragY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var dragValid = BooleanArray(10)

    // hint
    private var hint: SpiderHint? = null
    private var hintAt = 0L

    // cues
    private var sweepCol = -1
    private var sweepTop = 0f
    private var sweepBottom = 0f
    private var sweepAt = 0L

    private val winCards = ArrayList<WinCard>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val camera = Camera().apply { setLocation(0f, 0f, -14f) }
    private val matrix = Matrix()
    private var bg: Bitmap? = null
    private var bgKey: SpiderBackground? = null
    /** 0 (dark) .. 1 (white): average brightness of the background, used for the readability scrim. */
    private var bgLuma = 0f
    private var shadow: Bitmap? = null
    private var glow: Bitmap? = null
    private val order = ArrayList<SSprite>(128)
    private val rnd = Random(3)
    private val dash = DashPathEffect(floatArrayOf(6f * layout.density, 4f * layout.density), 0f)

    // per-frame allocation caches
    private val zOrder = Comparator<SSprite> { a, b -> zOf(a).compareTo(zOf(b)) }
    private var scrimShader: Shader? = null
    private var scrimLuma = -1f
    /** Sweep band gradient centred on y = 0, moved with [sweepMatrix] each frame. */
    private val sweepShader: Shader by lazy {
        val band = layout.cardH * 0.8f
        LinearGradient(0f, -band / 2, 0f, band / 2, intArrayOf(0x00FFE27A, 0xAAFFE27A.toInt(), 0x00FFE27A), null, Shader.TileMode.CLAMP)
    }
    private val sweepMatrix = Matrix()
    private val fm = Paint.FontMetrics()
    private var dealsText = ""
    private var dealsTextFor = -1

    // ------------------------------------------------------------------ frames

    fun apply(f: SpiderFrame) {
        val s = f.state
        val now = SystemClock.uptimeMillis()
        val sf = if (reduceMotion) 0.5f * speed else speed
        // remember where a completed run sat before it flies, for the gold sweep
        if (f.kind == FrameKind.COLLECT) {
            f.events.filterIsInstance<SpiderEvent.Completed>().firstOrNull()?.let { e ->
                val ys = e.cardIds.mapNotNull { sprites[it]?.y }
                if (ys.isNotEmpty()) {
                    sweepCol = e.column
                    sweepTop = ys.min() - layout.cardH / 2
                    sweepBottom = ys.max() + layout.cardH / 2
                    sweepAt = now
                }
            }
        }
        state = s
        val targets = layout.targets(s)
        val delay = HashMap<Int, Long>()
        var baseDur = 230L
        var arc = 0.08f
        val trail = HashSet<Int>()
        when (f.kind) {
            FrameKind.NEW_GAME -> {
                var k = 0
                val maxRows = s.columns.maxOf { it.size }
                for (row in 0 until maxRows) for (c in s.columns.indices) {
                    s.columns[c].getOrNull(row)?.let { delay[it.id] = (k++ * 22L * sf).toLong() }
                }
                baseDur = 300
                arc = 0.15f
            }
            FrameKind.DEAL -> {
                f.events.filterIsInstance<SpiderEvent.Dealt>().firstOrNull()?.cardIds?.forEachIndexed { i, id -> delay[id] = (i * 55L * sf).toLong() }
                baseDur = 300
                arc = 0.12f
            }
            FrameKind.COLLECT -> {
                f.events.filterIsInstance<SpiderEvent.Completed>().forEach { e ->
                    // a short gold sweep first, then A flies first and the King last (so the King ends on top)
                    e.cardIds.reversed().forEachIndexed { i, id ->
                        delay[id] = ((260 + i * 38L) * sf).toLong()
                        trail += id
                    }
                }
                baseDur = 420
                arc = 0.25f
            }
            FrameKind.UNDO -> baseDur = 200
            FrameKind.RESTORE -> baseDur = 1
            FrameKind.MOVE -> baseDur = 210
        }
        for ((id, t) in targets) {
            val card = findCard(s, id)
            var sp = sprites[id]
            if (sp == null) {
                sp = SSprite(id, card?.rank ?: 1, card?.suit ?: 0)
                val start = if (f.kind == FrameKind.NEW_GAME) layout.stockTap else androidx.compose.ui.geometry.Offset(t.x, t.y)
                sp.x = start.x; sp.y = start.y; sp.tx = sp.x; sp.ty = sp.y
                sp.up = if (f.kind == FrameKind.NEW_GAME) false else t.up
                sprites[id] = sp
                all += sp
            }
            if (f.kind == FrameKind.NEW_GAME && sp.zone != SZone.STOCK && t.zone == SZone.COLUMN) {
                val st = layout.stockTap
                sp.x = st.x; sp.y = st.y; sp.up = false; sp.prevUp = null
            }
            if (card != null) { sp.rank = card.rank; sp.suit = card.suit }
            sp.z = t.z
            sp.zone = t.zone
            sp.col = t.col
            sp.index = t.index
            val d = delay[id] ?: 0L
            val dur = (baseDur * sf).toLong().coerceAtLeast(1)
            retarget(sp, t.x, t.y, now, d, dur, arc)
            if (id in trail && !reduceMotion) sp.trailUntil = now + d + dur
            if (sp.up != t.up) {
                sp.prevUp = sp.up
                sp.up = t.up
                sp.flipStart = now + d + dur / 3
                sp.flipDur = (240 * sf).toLong().coerceAtLeast(1)
            }
        }
        effects(f, now, sf)
        if (f.events.any { it is SpiderEvent.Won }) startWin(now)
    }

    private fun findCard(s: SpiderState, id: Int) =
        s.columns.firstNotNullOfOrNull { c -> c.firstOrNull { it.id == id } }
            ?: s.stock.firstOrNull { it.id == id }
            ?: s.completed.firstNotNullOfOrNull { r -> r.firstOrNull { it.id == id } }

    private fun retarget(sp: SSprite, x: Float, y: Float, now: Long, delay: Long, dur: Long, arc: Float) {
        // Skip only when the card is really resting on the target. A dragged card keeps its old target (tx/ty) while
        // its x/y follow the finger, so comparing tx/ty alone left a refused drop stuck where it was released.
        if (!sp.moving && abs(sp.x - x) < 0.5f && abs(sp.y - y) < 0.5f) {
            sp.tx = x; sp.ty = y
            return
        }
        sp.fx = sp.x; sp.fy = sp.y
        sp.tx = x; sp.ty = y
        sp.start = now; sp.delay = delay; sp.dur = dur
        sp.arc = if (hypot(x - sp.fx, y - sp.fy) > layout.cardW * 2) arc else 0f
        sp.moving = true
    }

    /** Re-place every card (used when the layout changes, e.g. left-handed mode). */
    fun relayout() {
        val s = state ?: return
        val now = SystemClock.uptimeMillis()
        for ((id, t) in layout.targets(s)) {
            val sp = sprites[id] ?: continue
            retarget(sp, t.x, t.y, now, 0, (220 * speed).toLong().coerceAtLeast(1), 0.05f)
        }
    }

    private fun effects(f: SpiderFrame, now: Long, sf: Float) {
        for (e in f.events) when (e) {
            is SpiderEvent.Completed -> {
                val slot = layout.foundation((state?.completed?.size ?: 1) - 1)
                val at = now + ((260 + 13 * 38) * sf).toLong() + (300 * sf).toLong()
                fx.pop("+100", slot.x + (if (layout.leftHanded) -layout.cardW else layout.cardW), slot.y + layout.cardH * 0.7f, 0xFFFFE27A.toInt(), at, 26f, 1000)
                fx.ring(slot.x, slot.y, 0xFFFFC93C.toInt(), at, layout.cardW * 1.6f, 600, 5f)
                val cx = layout.columnX(e.column)
                fx.sparkles(cx, (sweepTop + sweepBottom) / 2f, layout.cardW * 1.1f, 22, 0xFFFFE89A.toInt())
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ input helpers

    fun beginDrag(col: Int, index: Int, x: Float, y: Float) {
        val s = state ?: return
        val card = s.columns[col][index]
        val sp = sprites[card.id] ?: return
        dragCol = col
        dragIndex = index
        grabDx = x - sp.x
        grabDy = y - sp.y
        dragX = x
        dragY = y
        dragValid = BooleanArray(10) { to -> SpiderEngine.canMove(s, SpiderMove(col, index, to)) }
    }

    fun endDrag() {
        val s = state
        val col = dragCol
        val idx = dragIndex
        dragCol = -1
        dragIndex = -1
        if (s == null || col < 0) return
        val now = SystemClock.uptimeMillis()
        val targets = layout.targets(s)
        for (i in idx until s.columns.getOrNull(col).orEmpty().size) {
            val id = s.columns[col][i].id
            val sp = sprites[id] ?: continue
            val t = targets[id] ?: continue
            sp.moving = false
            retarget(sp, t.x, t.y, now, 0, (220 * speed).toLong(), 0f)
        }
    }

    /** Called after a successful drop: the cards are already where the new frame puts them. */
    fun dropDone() {
        dragCol = -1
        dragIndex = -1
    }

    /** Center of the dragged stack's first card. */
    fun dragCenter(): Pair<Float, Float> = (dragX - grabDx) to (dragY - grabDy)

    /**
     * Where a drop would land: the nearest VALID column within 1.3 card widths of the dragged card (magnetic),
     * plus the nearest column of any kind (for the "why not" message).
     */
    fun dropTarget(): Pair<Int, Int> {
        val cx = dragX - grabDx
        var bestValid = -1
        var bestValidD = Float.MAX_VALUE
        var nearest = -1
        var nearestD = Float.MAX_VALUE
        for (c in 0 until 10) {
            if (c == dragCol) continue
            val d = abs(layout.columnX(c) - cx)
            if (d < nearestD) { nearestD = d; nearest = c }
            if (dragValid[c] && d < bestValidD) { bestValidD = d; bestValid = c }
        }
        val target = if (bestValidD <= layout.cardW * 1.3f) bestValid else -1
        val any = if (nearestD <= layout.cardW * 1.3f) nearest else -1
        return target to any
    }

    fun showHint(h: SpiderHint) {
        hint = h
        hintAt = SystemClock.uptimeMillis()
    }

    fun clearHint() {
        hint = null
    }

    fun shake(col: Int, index: Int) {
        val s = state ?: return
        val now = SystemClock.uptimeMillis()
        val cards = s.columns.getOrNull(col) ?: return
        for (i in index.coerceAtLeast(0) until cards.size) sprites[cards[i].id]?.shakeAt = now
    }

    // ------------------------------------------------------------------ win

    private fun startWin(now: Long) {
        winCards.clear()
        lastFrame = 0L // the table may have been idle: start the throw with a normal frame step
        val d = layout.density
        repeat(if (reduceMotion) 16 else 52) { i ->
            val slot = layout.foundation(i % 8)
            val a = (-Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 1.6f).toFloat()
            val sp = (900f + rnd.nextFloat() * 900f) * d / 3f
            winCards += WinCard(
                slot.x, slot.y, cos(a) * sp, sin(a) * sp, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 540f,
                1 + rnd.nextInt(13), rnd.nextInt(4), -(i * 0.06f),
            )
        }
        fx.confetti(layout.width, 160)
        fx.flash(0xFFFFE6A0.toInt(), now + 300, 600, 0.3f)
    }

    private var lastFrame = 0L

    private fun drawWin(c: Canvas, now: Long) {
        if (winCards.isEmpty()) return
        val dt = if (lastFrame == 0L) 0.016f else min(0.05f, (now - lastFrame) / 1000f)
        val floor = layout.height - layout.cardH * 0.4f
        val w = layout.cardW * 1.15f
        val h = w * 1.4f
        var i = 0
        while (i < winCards.size) {
            val wc = winCards[i++]
            wc.life += dt
            if (wc.life < 0f) continue
            if (wc.life > 5.5f) { winCards.removeAt(--i); continue }
            wc.vy += 1400f * layout.density / 3f * dt
            wc.x += wc.vx * dt
            wc.y += wc.vy * dt
            wc.rot += wc.vr * dt
            if (wc.y > floor) { wc.y = floor; wc.vy = -abs(wc.vy) * 0.62f; wc.vr *= 0.8f }
            if (wc.x < w / 2 || wc.x > layout.width - w / 2) wc.vx = -wc.vx
            val alpha = if (wc.life > 4.5f) (5.5f - wc.life).coerceIn(0f, 1f) else 1f
            val bmp = PlayingCardArt.face(wc.rank, wc.suit, fourColor).asAndroidBitmap()
            c.save()
            c.translate(wc.x, wc.y)
            c.rotate(wc.rot)
            paint.alpha = (alpha * 255).toInt()
            rect.set(-w / 2, -h / 2, w / 2, h / 2)
            c.drawBitmap(bmp, null, rect, paint)
            paint.alpha = 255
            c.restore()
        }
    }

    // ------------------------------------------------------------------ draw

    private fun ensure() {
        if (bg == null || bgKey != background) {
            val b = SpiderBackgrounds.render(background, layout.width.toInt().coerceAtLeast(1), layout.height.toInt().coerceAtLeast(1))
            bg = b
            bgKey = background
            bgLuma = averageLuma(b)
        }
        if (shadow == null) {
            val w = layout.cardW.toInt().coerceAtLeast(8)
            val h = (w * 1.4f).toInt()
            val pad = (w * 0.25f).toInt().coerceAtLeast(4)
            fun soft(color: Int, blur: Float): Bitmap {
                val b = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
                val cc = Canvas(b)
                val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
                cc.drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + w).toFloat(), (pad + h).toFloat()), w * 0.1f, w * 0.1f, pp)
                return b
            }
            shadow = soft(0x99000000.toInt(), pad * 0.6f)
            glow = soft(0xFFFFD84A.toInt(), pad * 0.85f)
        }
    }

    private fun averageLuma(b: Bitmap): Float {
        var sum = 0.0
        var n = 0
        val stepX = (b.width / 24).coerceAtLeast(1)
        val stepY = (b.height / 48).coerceAtLeast(1)
        var y = stepY / 2
        while (y < b.height) {
            var x = stepX / 2
            while (x < b.width) {
                val px = b.getPixel(x, y)
                sum += (0.2126 * ((px shr 16) and 0xFF) + 0.7152 * ((px shr 8) and 0xFF) + 0.0722 * (px and 0xFF)) / 255.0
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }

    fun draw(c: Canvas, now: Long) {
        ensure()
        val s = state
        fx.updateShake(now)
        val sx = fx.shakeX
        val sy = fx.shakeY
        c.save()
        c.translate(sx, sy)
        bg?.let { c.drawBitmap(it, 0f, 0f, null) }
        drawScrim(c)
        drawSlots(c, s, now)
        update(now)
        order.clear()
        for (i in all.indices) order.add(all[i])
        order.sortWith(zOrder)
        // everything except the dragged stack, then guidance, then the dragged stack on top
        for (sp in order) if (!isDragged(sp)) drawCard(c, sp, now)
        if (s != null) {
            drawGuides(c, s)
            drawSweep(c, now)
        }
        for (sp in order) if (isDragged(sp)) drawCard(c, sp, now)
        if (s != null) drawHint(c, s, now)
        drawLabels(c, s)
        drawWin(c, now)
        fx.draw(c, now, layout.width, layout.height)
        lastFrame = now
        c.restore()
    }

    private fun isDragged(sp: SSprite) = dragCol >= 0 && sp.zone == SZone.COLUMN && sp.col == dragCol && sp.index >= dragIndex

    private fun isSelected(sp: SSprite): Boolean {
        val sel = selected ?: return false
        return dragCol < 0 && sp.zone == SZone.COLUMN && sp.col == sel.first && sp.index >= sel.second
    }

    private fun zOf(sp: SSprite): Float = sp.z + (if (sp.moving) 5000f else 0f) + (if (isDragged(sp)) 10000f else 0f)

    private fun update(now: Long) {
        val s = state
        val dragging = dragCol >= 0 && s != null
        for (i in all.indices) {
            val sp = all[i]
            if (dragging && isDragged(sp)) {
                val off = (sp.index - dragIndex) * layout.upGap(s!!, dragCol)
                val tx = dragX - grabDx
                val ty = dragY - grabDy + off
                sp.x += (tx - sp.x) * 0.55f
                sp.y += (ty - sp.y) * 0.55f
                continue
            }
            if (!sp.moving) continue
            val el = now - sp.start - sp.delay
            if (el <= 0) continue
            val t = (el.toFloat() / sp.dur).coerceIn(0f, 1f)
            val e = Fx.easeOutCubic(t)
            val dx = sp.tx - sp.fx
            val dy = sp.ty - sp.fy
            val cx = sp.fx + dx / 2
            val cy = sp.fy + dy / 2 - hypot(dx, dy) * sp.arc
            val u = 1 - e
            sp.x = u * u * sp.fx + 2 * u * e * cx + e * e * sp.tx
            sp.y = u * u * sp.fy + 2 * u * e * cy + e * e * sp.ty
            if (sp.trailUntil > now && now - sp.lastTrail > 45) {
                sp.lastTrail = now
                fx.sparkles(sp.x, sp.y, layout.cardW * 0.35f, 1, 0xFFFFE89A.toInt())
            }
            if (t >= 1f) sp.moving = false
        }
    }

    /** Light backgrounds get a soft dark scrim behind the play area so white cards stay readable. */
    private fun drawScrim(c: Canvas) {
        if (bgLuma < 0.40f) return
        val a = ((bgLuma - 0.30f) / 0.45f).coerceIn(0.18f, 0.5f)
        val top = layout.rowTop - 8f * layout.density
        val bottom = layout.tableauBottom + 6f * layout.density
        p.color = 0xFF000000.toInt()
        if (scrimShader == null || scrimLuma != bgLuma) {
            scrimShader = LinearGradient(0f, top, 0f, bottom, intArrayOf(Fx.withAlpha(0xFF0A0420.toInt(), a), Fx.withAlpha(0xFF0A0420.toInt(), a * 0.75f)), null, Shader.TileMode.CLAMP)
            scrimLuma = bgLuma
        }
        p.shader = scrimShader
        rect.set(0f, top, layout.width, bottom)
        c.drawRoundRect(rect, 18f * layout.density, 18f * layout.density, p)
        p.shader = null
    }

    private fun drawSlots(c: Canvas, s: SpiderState?, now: Long) {
        val L = layout
        val r = L.cardW * 0.1f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * L.density
        p.color = 0x66FFFFFF
        p.pathEffect = dash
        if (s != null) for (col in 0 until 10) if (s.columns[col].isEmpty()) {
            val x = L.columnX(col)
            rect.set(x - L.cardW / 2, L.tableauTop, x + L.cardW / 2, L.tableauTop + L.cardH)
            c.drawRoundRect(rect, r, r, p)
        }
        p.pathEffect = null
        p.strokeWidth = 1.6f * L.density
        for (i in 7 downTo 0) {
            val f = L.foundation(i)
            rect.set(f.x - L.cardW / 2, f.y - L.cardH / 2, f.x + L.cardW / 2, f.y + L.cardH / 2)
            p.style = Paint.Style.FILL
            p.color = 0x33000000
            c.drawRoundRect(rect, r, r, p)
            p.style = Paint.Style.STROKE
            p.color = 0x40FFFFFF
            c.drawRoundRect(rect, r, r, p)
        }
        p.style = Paint.Style.FILL
        if (s != null && s.stock.isEmpty()) {
            val st = L.stockTap
            rect.set(st.x - L.cardW / 2, st.y - L.cardH / 2, st.x + L.cardW / 2, st.y + L.cardH / 2)
            p.style = Paint.Style.STROKE
            p.color = 0x40FFFFFF
            c.drawRoundRect(rect, r, r, p)
            p.style = Paint.Style.FILL
        }
    }

    /**
     * The tap-selected run glows gold. No guidance on where it can go (no green targets, no dimmed columns, also not
     * while dragging): hints come only from the Hint button.
     */
    private fun drawGuides(c: Canvas, s: SpiderState) {
        if (dragCol >= 0) return
        val L = layout
        val sel = selected ?: return
        if (sel.first !in 0..9 || sel.second > s.columns[sel.first].lastIndex) return
        val top = L.cardTop(s, sel.first, sel.second) - 6f * L.density
        val bottom = L.columnBottom(s, sel.first) - 6f * L.density
        glow?.let { g -> glowRect(c, g, L.columnX(sel.first), top, bottom, 230) }
    }

    private fun glowRect(c: Canvas, g: Bitmap, x: Float, top: Float, bottom: Float, alpha: Int) {
        val pad = layout.cardW * 0.25f
        rect.set(x - layout.cardW / 2 - pad, top - pad, x + layout.cardW / 2 + pad, bottom + pad)
        paint.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(g, null, rect, paint)
        paint.alpha = 255
    }

    /** Gold light band sweeping down a finished run before it flies away. */
    private fun drawSweep(c: Canvas, now: Long) {
        if (sweepCol < 0 || reduceMotion) return
        val t = (now - sweepAt) / (420f * speed)
        if (t > 1f) {
            sweepCol = -1
            return
        }
        val x = layout.columnX(sweepCol)
        val band = layout.cardH * 0.8f
        val y = sweepTop - band + (sweepBottom - sweepTop + band * 2) * t
        c.save()
        rect.set(x - layout.cardW / 2, sweepTop, x + layout.cardW / 2, sweepBottom)
        c.clipRect(rect)
        p.color = 0xFF000000.toInt()
        val shader = sweepShader
        sweepMatrix.setTranslate(0f, y)
        shader.setLocalMatrix(sweepMatrix)
        p.shader = shader
        c.drawRect(rect, p)
        p.shader = null
        c.restore()
    }

    private fun drawLabels(c: Canvas, s: SpiderState?) {
        if (s == null) return
        val L = layout
        if (s.stock.isNotEmpty()) {
            val groups = (s.stock.size + 9) / 10
            val st = L.stock(groups - 1, groups)
            val bx = st.x + (if (L.leftHanded) -1 else 1) * L.cardW * 0.42f
            val by = st.y - L.cardH * 0.5f
            val rr = 10f * L.density
            p.color = 0xFFE53935.toInt()
            c.drawCircle(bx, by, rr, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * L.density
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(bx, by, rr, p)
            p.style = Paint.Style.FILL
            text.textSize = 11f * L.density
            text.color = 0xFFFFFFFF.toInt()
            text.getFontMetrics(fm)
            if (dealsTextFor != s.dealsLeft) { dealsTextFor = s.dealsLeft; dealsText = s.dealsLeft.toString() }
            c.drawText(dealsText, bx, by - (fm.ascent + fm.descent) / 2, text)
        }
    }

    private fun drawCard(c: Canvas, sp: SSprite, now: Long) {
        val L = layout
        val w = L.cardW
        val h = L.cardH
        var up = sp.up
        var angle = 0f
        val prev = sp.prevUp
        if (prev != null) {
            val t = (now - sp.flipStart).toFloat() / sp.flipDur
            when {
                t <= 0f -> up = prev
                t >= 1f -> sp.prevUp = null
                t < 0.5f -> { up = prev; angle = t * 180f }
                else -> angle = t * 180f - 180f
            }
        }
        var shakeX = 0f
        if (sp.shakeAt > 0) {
            val t = (now - sp.shakeAt) / 320f
            if (t < 1f) shakeX = sin(t * 30f) * (1 - t) * 5f * L.density else sp.shakeAt = 0
        }
        val dragged = isDragged(sp)
        val sel = isSelected(sp)
        val scale = if (dragged) 1.08f else 1f
        val liftY = if (sel) -6f * L.density else 0f
        c.save()
        c.translate(sp.x + shakeX, sp.y + liftY)
        if (scale != 1f) c.scale(scale, scale)
        if (angle != 0f) {
            camera.save(); camera.rotateY(angle); camera.getMatrix(matrix); camera.restore()
            c.concat(matrix)
        }
        if (sp.moving || dragged || sel || sp.zone == SZone.STOCK) shadow?.let { sb ->
            val pad = w * 0.25f
            val lift = when {
                dragged -> 3.4f
                sp.moving || sel -> 2.2f
                else -> 1f
            }
            rect.set(-w / 2 - pad + 1.5f * L.density * lift, -h / 2 - pad + 3f * L.density * lift, w / 2 + pad + 1.5f * L.density * lift, h / 2 + pad + 3f * L.density * lift)
            paint.alpha = if (dragged) 235 else 255
            c.drawBitmap(sb, null, rect, paint)
            paint.alpha = 255
        }
        val bmp = if (up) PlayingCardArt.face(sp.rank, sp.suit, fourColor).asAndroidBitmap() else CardArt.back(cardBack).asAndroidBitmap()
        rect.set(-w / 2, -h / 2, w / 2, h / 2)
        c.drawBitmap(bmp, null, rect, paint)
        if (angle != 0f) {
            p.color = Fx.withAlpha(0xFF000000.toInt(), abs(angle) / 90f * 0.3f)
            c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        }
        c.restore()
    }

    private fun drawHint(c: Canvas, s: SpiderState, now: Long) {
        val h = hint ?: return
        val el = now - hintAt
        if (el > 2400) {
            hint = null
            return
        }
        val L = layout
        val pulse = 0.5f + 0.5f * sin(el / 140f)
        val g = glow ?: return
        when (h) {
            is SpiderHint.Move -> {
                val m = h.move
                if (m.from !in s.columns.indices || m.index > s.columns[m.from].lastIndex) return
                val srcTop = L.cardTop(s, m.from, m.index)
                val srcBottom = L.columnBottom(s, m.from)
                glowRect(c, g, L.columnX(m.from), srcTop, srcBottom, (120 + 120 * pulse).toInt())
                val dest = s.columns[m.to]
                val dTop = if (dest.isEmpty()) L.tableauTop else L.cardTop(s, m.to, dest.lastIndex)
                glowRect(c, g, L.columnX(m.to), dTop, dTop + L.cardH, (120 + 120 * pulse).toInt())
                // ghost of the moving card sliding to the target
                val t = ((el % 1200) / 900f).coerceAtMost(1f)
                val e = Fx.easeInOutCubic(t)
                val fx0 = L.columnX(m.from)
                val fy0 = srcTop + L.cardH / 2
                val tx = L.columnX(m.to)
                val ty = L.nextTop(s, m.to) + L.cardH / 2
                val card = s.columns[m.from][m.index]
                val bmp = PlayingCardArt.face(card.rank, card.suit, fourColor).asAndroidBitmap()
                val x = fx0 + (tx - fx0) * e
                val y = fy0 + (ty - fy0) * e - sin(e * Math.PI).toFloat() * L.cardH * 0.4f
                rect.set(x - L.cardW / 2, y - L.cardH / 2, x + L.cardW / 2, y + L.cardH / 2)
                paint.alpha = (200 * (1 - t * 0.4f)).toInt()
                c.drawBitmap(bmp, null, rect, paint)
                paint.alpha = 255
            }
            SpiderHint.Deal -> {
                val groups = (s.stock.size + 9) / 10
                if (groups == 0) return
                val st = L.stock(groups - 1, groups)
                glowRect(c, g, st.x, st.y - L.cardH / 2, st.y + L.cardH / 2, (140 + 115 * pulse).toInt())
            }
            SpiderHint.None -> Unit
        }
    }

    // ------------------------------------------------------------------ frame pacing

    override fun isBusy(now: Long): Boolean {
        if (dragCol >= 0) return true // the dragged stack eases after the finger
        for (i in all.indices) {
            val sp = all[i]
            // moving includes a card still waiting on its start delay; prevUp/shakeAt are cleared by drawCard
            if (sp.moving || sp.prevUp != null || sp.shakeAt > 0) return true
        }
        val s = state
        if (s != null) {
            if (sweepCol >= 0 && !reduceMotion) return true // drawSweep clears it after its run
            // a full hint slides a ghost card; drawHint clears it after its life, which needs one more frame
            val h = hint
            if (h != null && h != SpiderHint.None) return true
        }
        return winCards.isNotEmpty() || fx.isActive(now)
    }

    /** Nothing pulses while idle (the hint is covered by isBusy; the selected run's glow is steady). */
    override fun idleFrameMs(now: Long): Long = 0L
}
