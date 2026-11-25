package com.ngdstudio.mindimania.ui.bhabhi

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.bhabhi.BCard
import com.ngdstudio.mindimania.bhabhi.BStep
import com.ngdstudio.mindimania.bhabhi.BhabhiEvent
import com.ngdstudio.mindimania.bhabhi.BhabhiState
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.ui.game.AnimatedWorld
import com.ngdstudio.mindimania.ui.game.Fx
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.render.CardBack
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import kotlin.math.abs
import kotlin.math.hypot

private class BSprite(val id: Int, var card: BCard?) {
    var x = 0f; var y = 0f; var rot = 0f; var w = 0f
    var fx = 0f; var fy = 0f; var frot = 0f; var fw = 0f
    var tx = 0f; var ty = 0f; var trot = 0f; var tw = 0f
    var start = 0L; var delay = 0L; var dur = 1L; var arc = 0f
    var faceUp = true
    var flipAt = 0L; var flipDur = 0L; var flipFrom: Boolean? = null
    var z = 0f
    var removeAt = -1L
    var moving = false
}

/** All animated cards on the Bhabhi table, the felt, and effects. */
class BhabhiWorld(val layout: BhabhiLayout) : AnimatedWorld {
    private val sprites = HashMap<Int, BSprite>(80)
    val fx = Fx(layout.density)
    var state: BhabhiState? = null
        private set
    var back: CardBack = CardBack.ROYAL
    var theme: TableTheme = TableTheme.FELT
    var speed = 1f
    var reduceMotion = false
        set(v) { field = v; fx.reduceMotion = v }
    var selected: Int? = null
    var interactive = false

    private var phantomId = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val camera = Camera().apply { setLocation(0f, 0f, -14f) }
    private val matrix = Matrix()
    private var felt: Bitmap? = null
    private var feltTheme: TableTheme? = null
    private var shadow: Bitmap? = null
    private var glow: Bitmap? = null
    /** Ids of my hand cards, refilled each frame (no per-frame allocation). */
    private val myHandIds = HashSet<Int>(64)
    private val order = ArrayList<BSprite>(80)

    // ------------------------------------------------------------------ steps

    fun apply(step: BStep, sf0: Float) {
        val s = step.state
        val e = step.event
        val prev = state
        state = s
        val L = layout
        val now = SystemClock.uptimeMillis()
        val sf = if (reduceMotion) sf0 * 0.6f else sf0
        var handDelay = 0L
        when (e) {
            is BhabhiEvent.Dealt -> {
                sprites.clear()
                // backs fly to every other seat, my cards fly into my hand
                val n = s.players
                var k = 0
                val rounds = s.hands.maxOf { it.size }
                for (round in 0 until rounds) for (i in 1..n) {
                    val seat = (e.dealer + i) % n
                    if (round >= s.hands[seat].size) continue
                    val delay = (k++ * 14L * sf).toLong()
                    if (seat == L.me) continue
                    val sp = spawnPhantom(L.deckPos, null, false, L.trickCardW * 0.55f)
                    val seatPos = L.seatOf(seat)
                    tween(sp, seatPos.x, seatPos.y, 0f, L.trickCardW * 0.4f, now, delay, (240 * sf).toLong(), 0.1f)
                    sp.removeAt = now + delay + (240 * sf).toLong()
                }
                val mine = s.hands[L.me]
                val slots = L.handSlots(mine.size)
                mine.forEachIndexed { i, c ->
                    val sp = BSprite(c.id, c)
                    sp.x = L.deckPos.x; sp.y = L.deckPos.y; sp.w = L.trickCardW * 0.55f
                    sp.faceUp = false
                    sprites[c.id] = sp
                    val delay = ((i * n) * 14L * sf).toLong()
                    tween(sp, slots[i].x, slots[i].y, slots[i].rot, slots[i].w, now, delay, (280 * sf).toLong(), 0.12f)
                    flip(sp, true, now + delay + (140 * sf).toLong(), (200 * sf).toLong())
                    sp.z = slots[i].z
                }
                val lead = L.seatOf(e.leader)
                fx.pop(Strings.get(if (e.leader == L.me) R.string.bhabhi_pop_you_have_ace else R.string.bhabhi_pop_ace_opens), lead.x, lead.y - L.avatar, 0xFFFFE27A.toInt(), now + (k * 14L * sf).toLong(), 22f, 1300)
            }
            is BhabhiEvent.Played -> {
                val target = L.trickPos(e.seat)
                val sp = sprites[e.card.id] ?: run {
                    val start = L.seatOf(e.seat)
                    BSprite(e.card.id, e.card).also {
                        it.x = start.x; it.y = start.y; it.w = L.trickCardW * 0.45f; it.faceUp = false
                        sprites[e.card.id] = it
                    }
                }
                sp.card = e.card
                sp.removeAt = -1
                sp.z = 2000f + (prev?.trick?.size ?: 0)
                tween(sp, target.x, target.y, L.trickRotation(e.seat), L.trickCardW, now, 0, (320 * sf).toLong(), 0.15f)
                if (!sp.faceUp) flip(sp, true, now + (80 * sf).toLong(), (200 * sf).toLong())
                if (e.thulla) {
                    val at = now + (300 * sf).toLong()
                    fx.flash(0xFFFF6B6B.toInt(), at, 300, 0.22f)
                    fx.ring(target.x, target.y, 0xFFE53935.toInt(), at, L.trickCardW * 1.6f, 600, 9f)
                    fx.pop(Strings.get(R.string.bhabhi_pop_thulla), L.center.x, L.center.y - L.trickCardH * 0.95f, 0xFFFF8A8A.toInt(), at, 40f, 1300)
                    fx.shake(7f, 380, at)
                } else if (e.card.isAceOfSpades && (prev?.trickNo ?: 0) == 0) {
                    fx.sparkles(target.x, target.y, L.trickCardW * 0.7f, 14, 0xFFFFE89A.toInt())
                }
            }
            is BhabhiEvent.Discarded -> {
                // the trick is thrown away: cards gather in the middle, turn over and fade out
                val hold = (380 * sf).toLong()
                val high = L.trickPos(e.high)
                fx.ring(high.x, high.y, 0xFFFFE27A.toInt(), now, L.trickCardW, 420, 6f)
                e.cards.forEachIndexed { i, c ->
                    val sp = sprites[c.id] ?: return@forEachIndexed
                    tween(sp, L.center.x + (i - e.cards.size / 2f) * 2f, L.center.y, (i * 23 % 13 - 6).toFloat(), L.trickCardW * 0.7f, now, hold + i * 30L, (320 * sf).toLong(), 0f)
                    flip(sp, false, now + hold + (200 * sf).toLong(), (180 * sf).toLong())
                    sp.removeAt = now + hold + (620 * sf).toLong()
                }
            }
            is BhabhiEvent.PickedUp -> {
                val hold = (520 * sf).toLong()
                val takerPos = L.seatOf(e.taker)
                val from = prev?.trick?.let { t -> t.firstOrNull { it.seat == e.taker } }?.let { L.trickPos(it.seat) } ?: L.center
                fx.ring(from.x, from.y, 0xFFFF5252.toInt(), now, L.trickCardW * 1.2f, 500, 7f)
                fx.pop(Strings.get(if (e.taker == L.me) R.string.bhabhi_pop_you_pick_up else R.string.bhabhi_pop_pick_up, e.cards.size), takerPos.x, takerPos.y - L.avatar * 0.9f, 0xFFFF8A8A.toInt(), now + hold, 22f, 1200)
                if (e.taker == L.me) {
                    handDelay = hold // the cards join my hand after a short pause
                } else {
                    e.cards.forEachIndexed { i, c ->
                        val sp = sprites[c.id] ?: return@forEachIndexed
                        tween(sp, takerPos.x, takerPos.y, 0f, L.trickCardW * 0.38f, now, hold + i * 45L, (380 * sf).toLong(), 0.2f)
                        flip(sp, false, now + hold + i * 45L + (120 * sf).toLong(), (180 * sf).toLong())
                        sp.removeAt = now + hold + i * 45L + (380 * sf).toLong()
                    }
                }
            }
            is BhabhiEvent.Penalty -> {
                // wrong thulla: the fined player gets a card from each giver, one after another, face up
                val hold = (450 * sf).toLong()
                val to = L.seatOf(e.seat)
                val mine = e.seat == L.me
                fx.flash(0xFFFF6B6B.toInt(), now, 260, 0.18f)
                fx.ring(to.x, to.y, 0xFFE53935.toInt(), now, L.avatar * 1.6f, 600, 8f)
                fx.shake(5f, 300, now)
                val text = Strings.get(if (mine) R.string.bhabhi_pop_you_wrong_thulla else R.string.bhabhi_pop_wrong_thulla, e.given.size)
                if (mine) fx.pop(text, L.center.x, L.center.y - L.trickCardH * 0.95f, 0xFFFF8A8A.toInt(), now, 30f, 1600)
                else fx.pop(text, to.x, to.y - L.avatar * 0.9f, 0xFFFF8A8A.toInt(), now, 22f, 1500)
                e.given.forEachIndexed { i, g ->
                    val delay = hold + i * (110 * sf).toLong()
                    val from = L.seatOf(g.seat)
                    if (mine) {
                        // starts at the giver; relayoutHand below brings it into my fan
                        val sp = BSprite(g.card.id, g.card)
                        sp.x = from.x; sp.y = from.y; sp.w = L.trickCardW * 0.45f; sp.faceUp = true
                        sprites[g.card.id] = sp
                    } else {
                        val sp = sprites[g.card.id]?.takeIf { g.seat == L.me } ?: spawnPhantom(from, g.card, true, L.trickCardW * 0.45f)
                        sp.z = 2100f + i
                        tween(sp, to.x, to.y, 0f, L.trickCardW * 0.4f, now, delay, (360 * sf).toLong(), 0.18f)
                        sp.removeAt = now + delay + (360 * sf).toLong()
                    }
                }
                if (mine) handDelay = hold
            }
            is BhabhiEvent.Out -> {
                val pos = L.seatOf(e.seat)
                val medal = when (e.place) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${e.place}" }
                fx.pop(Strings.get(if (e.seat == L.me) R.string.bhabhi_pop_you_got_away else R.string.bhabhi_pop_safe, medal), pos.x, pos.y - L.avatar * 0.9f, 0xFF8CFFB4.toInt(), now, if (e.seat == L.me) 30f else 22f, 1500)
                fx.burst(pos.x, pos.y, 28, intArrayOf(0xFF8CFFB4.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE27A.toInt()), 240f, 4f, 0f, 0.9f, 2)
                if (e.seat == L.me) fx.confetti(L.width, 90)
            }
            is BhabhiEvent.GameOver -> {
                val pos = L.seatOf(e.loser)
                fx.ring(pos.x, pos.y, 0xFFE53935.toInt(), now, L.width * 0.4f, 800, 10f)
                fx.pop(Strings.get(if (e.loser == L.me) R.string.bhabhi_pop_you_bhabhi else R.string.bhabhi_pop_bhabhi), L.center.x, L.center.y, 0xFFFF8A8A.toInt(), now + 150, 40f, 2000)
                fx.shake(6f, 420, now)
            }
            is BhabhiEvent.Restored -> {
                sprites.clear()
                s.trick.forEachIndexed { i, play ->
                    val pos = L.trickPos(play.seat)
                    val sp = BSprite(play.card.id, play.card)
                    sp.x = pos.x; sp.y = pos.y; sp.w = L.trickCardW; sp.rot = L.trickRotation(play.seat)
                    sp.tx = sp.x; sp.ty = sp.y; sp.tw = sp.w; sp.trot = sp.rot
                    sp.z = 2000f + i
                    sprites[play.card.id] = sp
                }
                val mine = s.hands[L.me]
                val slots = L.handSlots(mine.size)
                mine.forEachIndexed { i, c ->
                    val sp = BSprite(c.id, c)
                    sp.x = slots[i].x; sp.y = slots[i].y + L.handCardH; sp.w = slots[i].w
                    sprites[c.id] = sp
                }
            }
        }
        relayoutHand(now, sf, handDelay)
    }

    /** Moves my hand cards to their fan slots (after plays, pickups, selection changes). */
    fun relayoutHand(now: Long = SystemClock.uptimeMillis(), sf: Float = speed, delay: Long = 0L) {
        val s = state ?: return
        val L = layout
        val hand = s.hands[L.me]
        val slots = L.handSlots(hand.size)
        hand.forEachIndexed { i, c ->
            val sl = slots[i]
            val sp = sprites[c.id] ?: BSprite(c.id, c).also {
                it.x = sl.x; it.y = sl.y + L.handCardH; it.w = L.handCardW
                sprites[c.id] = it
            }
            sp.card = c
            sp.removeAt = -1
            val lift = if (c.id == selected) L.handCardH * 0.22f else 0f
            sp.z = sl.z
            if (!sp.faceUp) flip(sp, true, now + delay, (200 * sf).toLong())
            tween(sp, sl.x, sl.y - lift, if (c.id == selected) sl.rot * 0.4f else sl.rot, sl.w * (if (c.id == selected) 1.06f else 1f), now, delay, ((if (delay > 0) 380 else 220) * sf).toLong(), if (delay > 0) 0.15f else 0f, keepIfSame = delay == 0L)
        }
    }

    private fun spawnPhantom(at: Offset, card: BCard?, faceUp: Boolean, w: Float): BSprite {
        val sp = BSprite(phantomId--, card)
        sp.x = at.x; sp.y = at.y; sp.w = w; sp.faceUp = faceUp
        sp.tx = sp.x; sp.ty = sp.y; sp.tw = w
        sp.z = 3000f
        sprites[sp.id] = sp
        return sp
    }

    private fun tween(sp: BSprite, x: Float, y: Float, rot: Float, w: Float, now: Long, delay: Long, dur: Long, arc: Float, keepIfSame: Boolean = false) {
        if (keepIfSame && abs(sp.tx - x) < 0.5f && abs(sp.ty - y) < 0.5f && abs(sp.tw - w) < 0.5f && abs(sp.trot - rot) < 0.3f) return
        sp.fx = sp.x; sp.fy = sp.y; sp.frot = sp.rot; sp.fw = sp.w
        sp.tx = x; sp.ty = y; sp.trot = rot; sp.tw = w
        sp.start = now; sp.delay = delay; sp.dur = dur.coerceAtLeast(1)
        sp.arc = if (hypot(x - sp.fx, y - sp.fy) > layout.width * 0.15f) arc else 0f
        sp.moving = true
    }

    private fun flip(sp: BSprite, up: Boolean, at: Long, dur: Long) {
        if (sp.faceUp == up) return
        sp.flipFrom = sp.faceUp
        sp.faceUp = up
        sp.flipAt = at
        sp.flipDur = dur.coerceAtLeast(1)
    }

    // ------------------------------------------------------------------ frame pacing

    /**
     * Busy while a card moves (or waits on its start delay), a flip is still turning, a card is waiting to be removed
     * at [BSprite.removeAt] (removal happens in [update]) or any effect / shake is running.
     */
    override fun isBusy(now: Long): Boolean {
        if (fx.isActive(now)) return true
        for (sp in sprites.values) {
            if (sp.moving || sp.flipFrom != null || sp.removeAt > 0) return true
        }
        return false
    }

    /** Nothing in [draw] animates while idle (no hint or playable-card glow; the selected card's glow is steady). */
    override fun idleFrameMs(now: Long): Long = 0L

    // ------------------------------------------------------------------ hit test

    fun hitHand(x: Float, y: Float): Int? {
        val s = state ?: return null
        val hand = s.hands[layout.me]
        val list = hand.mapNotNull { sprites[it.id] }.sortedByDescending { it.z }
        for (sp in list) {
            val hw = sp.tw / 2
            val hh = sp.tw * 0.7f
            if (abs(x - sp.tx) <= hw && y >= sp.ty - hh - 8 && y <= sp.ty + hh + layout.handCardH * 0.3f) return sp.id
        }
        return null
    }

    // ------------------------------------------------------------------ draw

    private fun ensure() {
        if (felt == null || feltTheme != theme) {
            feltTheme = theme
            felt = renderFelt()
        }
        if (shadow == null) {
            val w = (layout.handCardW * 0.5f).toInt().coerceAtLeast(16)
            val h = (w * 1.4f).toInt()
            val pad = w / 5
            fun soft(color: Int, blur: Float): Bitmap {
                val b = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + w).toFloat(), (pad + h).toFloat()), w * 0.1f, w * 0.1f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) })
                return b
            }
            shadow = soft(0xAA000000.toInt(), pad * 0.7f)
            glow = soft(0xFFFFD84A.toInt(), pad * 0.9f)
        }
    }

    private fun renderFelt(): Bitmap {
        val L = layout
        val w = L.width.toInt().coerceAtLeast(1)
        val h = L.height.toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.color = 0xFF000000.toInt()
        pp.shader = RadialGradient(L.center.x, L.center.y, h * 0.75f, intArrayOf(theme.center.toArgb(), theme.edge.toArgb()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pp)
        // felt noise
        val r = java.util.Random(11)
        pp.shader = null
        repeat((w * h / 110).coerceAtMost(50000)) {
            val a = 6 + r.nextInt(10) // not nextInt(6, 16): that one only exists from Android 15
            pp.color = if (r.nextBoolean()) (a shl 24) or 0xFFFFFF else (a shl 24)
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            c.drawRect(x, y, x + 1.3f, y + 1.3f, pp)
        }
        // table oval
        val accent = theme.accent.toArgb()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2f * L.density
        pp.color = (accent and 0x00FFFFFF) or 0x55000000
        val rx = L.width * 0.46f
        val ry = (L.center.y - L.tableTop) * 0.98f
        c.drawOval(RectF(L.center.x - rx, L.center.y - ry, L.center.x + rx, L.center.y + ry), pp)
        pp.strokeWidth = 1f * L.density
        pp.color = (accent and 0x00FFFFFF) or 0x30000000
        c.drawOval(RectF(L.center.x - rx + 10 * L.density, L.center.y - ry + 10 * L.density, L.center.x + rx - 10 * L.density, L.center.y + ry - 10 * L.density), pp)
        pp.style = Paint.Style.FILL
        // centre emblem: a faint spade (the A♠ opens every game)
        pp.color = 0x14FFFFFF
        pp.textSize = L.trickCardW * 1.6f
        pp.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        pp.textAlign = Paint.Align.CENTER
        c.drawText("♠", L.center.x, L.center.y + pp.textSize * 0.35f, pp)
        // vignette + hand tray
        pp.color = 0xFF000000.toInt()
        pp.shader = RadialGradient(L.center.x, L.center.y, h * 0.8f, intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pp)
        pp.shader = LinearGradient(0f, L.handTop - L.handCardH * 0.3f, 0f, h.toFloat(), intArrayOf(0x00000000, 0x77000000), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, L.handTop - L.handCardH * 0.3f, w.toFloat(), h.toFloat(), pp)
        return b
    }

    private val drawOrder = Comparator<BSprite> { a, b -> (a.z + if (a.moving) 4000f else 0f).compareTo(b.z + if (b.moving) 4000f else 0f) }

    fun draw(c: Canvas, now: Long) {
        ensure()
        fx.updateShake(now)
        val sx = fx.shakeX
        val sy = fx.shakeY
        c.save()
        c.translate(sx, sy)
        felt?.let { c.drawBitmap(it, 0f, 0f, null) }
        update(now)
        order.clear()
        for (sp in sprites.values) order += sp
        order.sortWith(drawOrder)
        myHandIds.clear()
        state?.hands?.get(layout.me)?.let { hand -> for (card in hand) myHandIds += card.id }
        for (sp in order) drawSprite(c, sp, now, sp.id in myHandIds)
        fx.draw(c, now, layout.width, layout.height)
        c.restore()
    }

    private fun update(now: Long) {
        val iter = sprites.values.iterator()
        while (iter.hasNext()) {
            val sp = iter.next()
            if (sp.removeAt in 1..now) { iter.remove(); continue }
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
            sp.rot = sp.frot + (sp.trot - sp.frot) * e
            sp.w = sp.fw + (sp.tw - sp.fw) * e
            if (t >= 1f) sp.moving = false
        }
    }

    private fun drawSprite(c: Canvas, sp: BSprite, now: Long, inHand: Boolean) {
        val w = sp.w
        val h = w * 1.4f
        var up = sp.faceUp
        var angle = 0f
        val from = sp.flipFrom
        if (from != null) {
            val t = (now - sp.flipAt).toFloat() / sp.flipDur
            when {
                t <= 0f -> up = from
                t >= 1f -> sp.flipFrom = null
                t < 0.5f -> { up = from; angle = t * 180f }
                else -> angle = t * 180f - 180f
            }
        }
        val card = sp.card
        val bmp = if (up && card != null) PlayingCardArt.face(card.artRank, card.suit).asAndroidBitmap() else CardArt.back(back).asAndroidBitmap()
        c.save()
        c.translate(sp.x, sp.y)
        c.rotate(sp.rot)
        if (angle != 0f) {
            camera.save(); camera.rotateY(angle); camera.getMatrix(matrix); camera.restore()
            c.concat(matrix)
        }
        shadow?.let { sb ->
            val pad = w * 0.22f
            val lift = if (sp.moving) 2f else 1f
            rect.set(-w / 2 - pad + 2f * lift, -h / 2 - pad + 4f * lift, w / 2 + pad + 2f * lift, h / 2 + pad + 4f * lift)
            c.drawBitmap(sb, null, rect, paint)
        }
        // only the card I picked glows: no hint and no marking of which cards follow suit
        if (inHand && interactive && sp.id == selected) {
            glow?.let { g ->
                val gw = w * 1.3f
                rect.set(-gw / 2, -gw * 0.7f, gw / 2, gw * 0.7f)
                c.drawBitmap(g, null, rect, paint)
            }
        }
        rect.set(-w / 2, -h / 2, w / 2, h / 2)
        c.drawBitmap(bmp, null, rect, paint)
        if (angle != 0f) {
            p.color = Fx.withAlpha(0xFF000000.toInt(), abs(angle) / 90f * 0.3f)
            c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        }
        c.restore()
    }

    val busy: Boolean get() = sprites.values.any { it.moving } || fx.busy
}
