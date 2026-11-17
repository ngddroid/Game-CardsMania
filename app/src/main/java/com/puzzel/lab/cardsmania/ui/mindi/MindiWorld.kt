package com.puzzel.lab.cardsmania.ui.mindi

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import com.puzzel.lab.cardsmania.data.TableTheme
import com.puzzel.lab.cardsmania.mindi.MCard
import com.puzzel.lab.cardsmania.mindi.MStep
import com.puzzel.lab.cardsmania.mindi.MindiEvent
import com.puzzel.lab.cardsmania.mindi.MindiState
import com.puzzel.lab.cardsmania.ui.game.Fx
import com.puzzel.lab.cardsmania.ui.render.CardArt
import com.puzzel.lab.cardsmania.ui.render.CardBack
import com.puzzel.lab.cardsmania.ui.spider.PlayingCardArt
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class MSprite(val id: Int, var card: MCard?) {
    var x = 0f; var y = 0f; var rot = 0f; var w = 0f
    var fx = 0f; var fy = 0f; var frot = 0f; var fw = 0f
    var tx = 0f; var ty = 0f; var trot = 0f; var tw = 0f
    var start = 0L; var delay = 0L; var dur = 1L; var arc = 0f
    var faceUp = true
    var flipAt = 0L; var flipDur = 0L; var flipFrom: Boolean? = null
    var z = 0f
    var removeAt = -1L
    var alphaFade = false
    var moving = false
}

/** All animated cards on the Mindi table, the felt, and effects. */
class MindiWorld(val layout: MindiLayout) {
    private val sprites = HashMap<Int, MSprite>(64)
    val fx = Fx(layout.density)
    var state: MindiState? = null
        private set
    var back: CardBack = CardBack.ROYAL
    var theme: TableTheme = TableTheme.FELT
    var speed = 1f
    var reduceMotion = false
        set(v) { field = v; fx.reduceMotion = v }
    var legal: Set<Int> = emptySet()
    var selected: Int? = null
    var hint: Int? = null
    var interactive = false
    var hideMode = false

    private var phantomId = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0.6f)
            postConcat(ColorMatrix(floatArrayOf(0.7f, 0f, 0f, 0f, 0f, 0f, 0.7f, 0f, 0f, 0f, 0f, 0f, 0.7f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        })
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val camera = Camera().apply { setLocation(0f, 0f, -14f) }
    private val matrix = Matrix()
    private var felt: Bitmap? = null
    private var feltTheme: TableTheme? = null
    private var shadow: Bitmap? = null
    private var glow: Bitmap? = null
    private val order = ArrayList<MSprite>(64)
    private val rnd = Random(5)

    // ------------------------------------------------------------------ steps

    fun apply(step: MStep, sf0: Float) {
        val s = step.state
        val e = step.event
        val prev = state
        state = s
        val L = layout
        val now = SystemClock.uptimeMillis()
        val sf = if (reduceMotion) sf0 * 0.6f else sf0
        when (e) {
            is MindiEvent.Dealt -> {
                sprites.clear()
                // backs fly to every other seat, my cards fly into my hand
                var k = 0
                val n = s.players
                val size = s.hands.maxOf { it.size }
                for (round in 0 until size) for (i in 1..n) {
                    val seat = (s.dealer + i) % n
                    val delay = (k++ * 18L * sf).toLong()
                    if (seat == L.me) continue
                    val sp = spawnPhantom(L.deckPos, null, false, L.trickCardW * 0.55f)
                    val seatPos = L.seatOf(seat)
                    tween(sp, seatPos.x, seatPos.y, 0f, L.trickCardW * 0.4f, now, delay, (260 * sf).toLong(), 0.1f)
                    sp.removeAt = now + delay + (260 * sf).toLong()
                }
                val mine = s.hands[L.me]
                val slots = L.handSlots(mine.size)
                mine.forEachIndexed { i, c ->
                    val sp = MSprite(c.id, c)
                    sp.x = L.deckPos.x; sp.y = L.deckPos.y; sp.w = L.trickCardW * 0.55f
                    sp.faceUp = false
                    sprites[c.id] = sp
                    val delay = ((i * n) * 18L * sf).toLong()
                    tween(sp, slots[i].x, slots[i].y, slots[i].rot, slots[i].w, now, delay, (300 * sf).toLong(), 0.12f)
                    flip(sp, true, now + delay + (150 * sf).toLong(), (220 * sf).toLong())
                    sp.z = slots[i].z
                }
            }
            is MindiEvent.TrumpShown -> {
                val sp = spawnPhantom(L.deckPos, e.card, true, L.trickCardW)
                fx.pop("TRUMP", L.center.x, L.center.y - L.trickCardH * 0.8f, 0xFFFFE27A.toInt(), now, 30f, 1100)
                tween(sp, L.trumpSlot.x, L.trumpSlot.y, 0f, L.trickCardW * 0.45f, now, (700 * sf).toLong(), (420 * sf).toLong(), 0.1f)
                sp.removeAt = now + (1120 * sf).toLong()
            }
            is MindiEvent.HiddenChosen -> {
                val hid = s.hidden
                val from = if (e.seat == L.me && prev != null && hid != null) handPos(prev, hid.id) ?: L.mySeat else L.seatOf(e.seat)
                val sp = spawnPhantom(from, null, false, if (e.seat == L.me) L.handCardW else L.trickCardW * 0.5f)
                sprites.remove(s.hidden?.id ?: -1) // my hand sprite of the hidden card leaves
                tween(sp, L.trumpSlot.x, L.trumpSlot.y, 0f, L.trickCardW * 0.45f, now, 0, (480 * sf).toLong(), 0.25f)
                sp.removeAt = now + (480 * sf).toLong()
                fx.pop("SECRET TRUMP", L.center.x, L.center.y, 0xFFFFE27A.toInt(), now + 200, 26f, 1100)
            }
            is MindiEvent.TrumpRevealed -> {
                fx.flash(0xFFFFE6A0.toInt(), now, 400, 0.25f)
                fx.ring(L.trumpSlot.x, L.trumpSlot.y, 0xFFFFC93C.toInt(), now, L.width * 0.5f, 700, 10f)
                fx.pop("TRUMP REVEALED!", L.center.x, L.center.y - L.trickCardH * 0.9f, 0xFFFFE27A.toInt(), now + 150, 30f, 1400)
                fx.sparkles(L.trumpSlot.x, L.trumpSlot.y, L.width * 0.12f, 24, 0xFFFFE89A.toInt())
                if (e.owner != L.me) {
                    val sp = spawnPhantom(L.trumpSlot, e.card, true, L.trickCardW * 0.6f)
                    val seat = L.seatOf(e.owner)
                    tween(sp, seat.x, seat.y, 0f, L.trickCardW * 0.4f, now, (650 * sf).toLong(), (420 * sf).toLong(), 0.15f)
                    sp.removeAt = now + (1070 * sf).toLong()
                } else {
                    val sp = MSprite(e.card.id, e.card)
                    sp.x = L.trumpSlot.x; sp.y = L.trumpSlot.y; sp.w = L.trickCardW * 0.6f
                    sprites[e.card.id] = sp
                }
            }
            is MindiEvent.TrumpCut -> {
                fx.pop("CUT! NEW TRUMP", L.center.x, L.center.y - L.trickCardH * 0.9f, 0xFFFF8A8A.toInt(), now, 28f, 1300)
                fx.ring(L.center.x, L.center.y, 0xFFE53935.toInt(), now, L.width * 0.45f, 650, 8f)
                fx.shake(4f, 300, now)
            }
            is MindiEvent.Played -> {
                val target = L.trickPos(e.seat)
                val sp = sprites[e.card.id] ?: run {
                    val start = L.seatOf(e.seat)
                    MSprite(e.card.id, e.card).also {
                        it.x = start.x; it.y = start.y; it.w = L.trickCardW * 0.45f; it.faceUp = false
                        sprites[e.card.id] = it
                    }
                }
                sp.card = e.card
                sp.z = 2000f + s.trick.size
                tween(sp, target.x, target.y, L.trickRotation(e.seat), L.trickCardW, now, 0, (330 * sf).toLong(), 0.15f)
                if (!sp.faceUp) flip(sp, true, now + (80 * sf).toLong(), (200 * sf).toLong())
                if (e.trump) {
                    fx.ring(target.x, target.y, 0xFFE53935.toInt(), now + (300 * sf).toLong(), L.trickCardW, 500, 6f)
                    fx.pop("TRUMP!", target.x, target.y - L.trickCardH * 0.6f, 0xFFFF8A8A.toInt(), now + (250 * sf).toLong(), 20f, 800)
                }
                if (e.card.isTen) fx.sparkles(target.x, target.y, L.trickCardW * 0.6f, 10, 0xFFFFE89A.toInt())
            }
            is MindiEvent.TrickWon -> {
                val pile = L.teamPile(e.team, s.team(L.me))
                val hold = (420 * sf).toLong()
                val winnerPos = L.trickPos(e.winner)
                fx.ring(winnerPos.x, winnerPos.y, 0xFFFFE27A.toInt(), now, L.trickCardW * 1.1f, 450, 7f)
                prev?.trick?.forEachIndexed { i, play ->
                    val sp = sprites[play.card.id] ?: return@forEachIndexed
                    tween(sp, pile.x, pile.y, 0f, L.trickCardW * 0.35f, now, hold + i * 40L, (380 * sf).toLong(), 0.18f)
                    sp.removeAt = now + hold + i * 40L + (380 * sf).toLong()
                }
                if (e.tens > 0) {
                    val at = now + hold + (380 * sf).toLong()
                    fx.pop(if (e.tens > 1) "MINDI ×${e.tens}!" else "MINDI!", pile.x, pile.y + layout.trackerH * 0.9f, 0xFFFFE27A.toInt(), at, 26f, 1200)
                    fx.burst(pile.x, pile.y, 30, intArrayOf(0xFFFFE27A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFB000.toInt()), 260f, 4f, 0f, 0.9f, 2)
                }
            }
            is MindiEvent.HandEnded -> {
                val r = e.result
                val mineWon = r.winnerTeam == s.team(L.me)
                if (r.kind != com.puzzel.lab.cardsmania.mindi.HandKind.NORMAL) {
                    fx.pop(if (r.kind == com.puzzel.lab.cardsmania.mindi.HandKind.MENDIKOT) "MENDIKOT!" else "WHITEWASH!", L.center.x, L.center.y, 0xFFFFE27A.toInt(), now, 44f, 1800)
                    fx.shake(6f, 450, now)
                }
                if (mineWon) fx.confetti(L.width, 120)
            }
            is MindiEvent.MatchEnded -> Unit
            is MindiEvent.Restored -> {
                sprites.clear()
                // cards already on the table in the current trick
                s.trick.forEachIndexed { i, play ->
                    val p = L.trickPos(play.seat)
                    val sp = MSprite(play.card.id, play.card)
                    sp.x = p.x; sp.y = p.y; sp.w = L.trickCardW; sp.rot = L.trickRotation(play.seat)
                    sp.tx = sp.x; sp.ty = sp.y; sp.tw = sp.w; sp.trot = sp.rot
                    sp.z = 2000f + i
                    sprites[play.card.id] = sp
                }
                val mine = s.hands[L.me]
                val slots = L.handSlots(mine.size)
                mine.forEachIndexed { i, c ->
                    val sp = MSprite(c.id, c)
                    sp.x = slots[i].x; sp.y = slots[i].y + L.handCardH; sp.w = slots[i].w
                    sprites[c.id] = sp
                }
            }
        }
        relayoutHand(now, sf)
    }

    private fun handPos(s: MindiState, id: Int): Offset? {
        val hand = s.hands[layout.me]
        val i = hand.indexOfFirst { it.id == id }
        if (i < 0) return null
        val sl = layout.handSlots(hand.size)[i]
        return Offset(sl.x, sl.y)
    }

    /** Moves my hand cards to their fan slots (after plays, reveals, selection changes). */
    fun relayoutHand(now: Long = SystemClock.uptimeMillis(), sf: Float = speed) {
        val s = state ?: return
        val L = layout
        val hand = s.hands[L.me]
        val slots = L.handSlots(hand.size)
        val ids = HashSet<Int>()
        hand.forEachIndexed { i, c ->
            ids += c.id
            val sl = slots[i]
            val sp = sprites[c.id] ?: MSprite(c.id, c).also {
                it.x = sl.x; it.y = sl.y + L.handCardH; it.w = L.handCardW
                sprites[c.id] = it
            }
            sp.card = c
            sp.removeAt = -1
            val lift = when {
                c.id == selected -> L.handCardH * 0.22f
                c.id in legal && interactive -> L.handCardH * 0.06f
                else -> 0f
            }
            sp.z = sl.z
            if (sp.flipFrom == null && !sp.faceUp) flip(sp, true, now, (200 * sf).toLong())
            tween(sp, sl.x, sl.y - lift, if (c.id == selected) sl.rot * 0.4f else sl.rot, sl.w * (if (c.id == selected) 1.06f else 1f), now, 0, (220 * sf).toLong(), 0f, keepIfSame = true)
        }
    }

    private fun spawnPhantom(at: Offset, card: MCard?, faceUp: Boolean, w: Float): MSprite {
        val sp = MSprite(phantomId--, card)
        sp.x = at.x; sp.y = at.y; sp.w = w; sp.faceUp = faceUp
        sp.tx = sp.x; sp.ty = sp.y; sp.tw = w
        sp.z = 3000f
        sprites[sp.id] = sp
        return sp
    }

    private fun tween(sp: MSprite, x: Float, y: Float, rot: Float, w: Float, now: Long, delay: Long, dur: Long, arc: Float, keepIfSame: Boolean = false) {
        if (keepIfSame && abs(sp.tx - x) < 0.5f && abs(sp.ty - y) < 0.5f && abs(sp.tw - w) < 0.5f && abs(sp.trot - rot) < 0.3f) return
        sp.fx = sp.x; sp.fy = sp.y; sp.frot = sp.rot; sp.fw = sp.w
        sp.tx = x; sp.ty = y; sp.trot = rot; sp.tw = w
        sp.start = now; sp.delay = delay; sp.dur = dur.coerceAtLeast(1)
        sp.arc = if (hypot(x - sp.fx, y - sp.fy) > layout.width * 0.15f) arc else 0f
        sp.moving = true
    }

    private fun flip(sp: MSprite, up: Boolean, at: Long, dur: Long) {
        if (sp.faceUp == up) return
        sp.flipFrom = sp.faceUp
        sp.faceUp = up
        sp.flipAt = at
        sp.flipDur = dur.coerceAtLeast(1)
    }

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
        val center = theme.center.toArgb()
        val edge = theme.edge.toArgb()
        pp.color = 0xFF000000.toInt()
        pp.shader = RadialGradient(L.center.x, L.center.y, h * 0.75f, intArrayOf(center, edge), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pp)
        // felt noise
        val r = java.util.Random(9)
        pp.shader = null
        repeat((w * h / 110).coerceAtMost(50000)) {
            val a = r.nextInt(6, 16)
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
        // centre emblem: faint "10"
        pp.color = 0x14FFFFFF
        pp.textSize = L.trickCardW * 1.4f
        pp.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        pp.textAlign = Paint.Align.CENTER
        c.drawText("10", L.center.x, L.center.y + pp.textSize * 0.35f, pp)
        // vignette + hand tray
        pp.color = 0xFF000000.toInt()
        pp.shader = RadialGradient(L.center.x, L.center.y, h * 0.8f, intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pp)
        pp.shader = LinearGradient(0f, L.handTop - L.handCardH * 0.4f, 0f, h.toFloat(), intArrayOf(0x00000000, 0x77000000), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, L.handTop - L.handCardH * 0.4f, w.toFloat(), h.toFloat(), pp)
        return b
    }

    private val drawOrder = Comparator<MSprite> { a, b -> (a.z + if (a.moving) 4000f else 0f).compareTo(b.z + if (b.moving) 4000f else 0f) }

    fun draw(c: Canvas, now: Long) {
        ensure()
        val (sx, sy) = fx.shakeOffset(now)
        c.save()
        c.translate(sx, sy)
        felt?.let { c.drawBitmap(it, 0f, 0f, null) }
        update(now)
        order.clear()
        order.addAll(sprites.values)
        order.sortWith(drawOrder)
        val s = state
        val myHand = s?.hands?.get(layout.me)?.map { it.id }?.toHashSet() ?: emptySet<Int>()
        for (sp in order) drawSprite(c, sp, now, sp.id in myHand)
        fx.draw(c, now, layout.width, layout.height)
        c.restore()
    }

    private fun update(now: Long) {
        val dead = ArrayList<Int>()
        for (sp in sprites.values) {
            if (sp.removeAt in 1..now) { dead += sp.id; continue }
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
        dead.forEach { sprites.remove(it) }
    }

    private fun drawSprite(c: Canvas, sp: MSprite, now: Long, inHand: Boolean) {
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
        val isLegal = inHand && sp.id in legal
        if (inHand && interactive && (sp.id == selected || sp.id == hint || (isLegal && legal.size < (state?.hands?.get(layout.me)?.size ?: 0)))) {
            glow?.let { g ->
                val pulse = 0.5f + 0.5f * sin(now / 250f + sp.id)
                val k = if (sp.id == selected || sp.id == hint) 1.3f else 1.14f
                val gw = w * k
                rect.set(-gw / 2, -gw * 0.7f, gw / 2, gw * 0.7f)
                paint.alpha = if (sp.id == selected) 255 else if (sp.id == hint) (170 + 85 * pulse).toInt() else (70 + 70 * pulse).toInt()
                c.drawBitmap(g, null, rect, paint)
                paint.alpha = 255
            }
        }
        rect.set(-w / 2, -h / 2, w / 2, h / 2)
        val dimIt = inHand && interactive && !hideMode && legal.isNotEmpty() && !isLegal
        c.drawBitmap(bmp, null, rect, if (dimIt) dim else paint)
        // trump suit marker on my cards
        if (up && card != null && inHand && state?.trump == card.suit) {
            p.color = 0xFFE53935.toInt()
            val r = w * 0.09f
            c.drawCircle(w / 2 - r * 1.3f, h / 2 - r * 1.3f, r, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textSize = r * 1.4f
            p.textAlign = Paint.Align.CENTER
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText("T", w / 2 - r * 1.3f, h / 2 - r * 1.3f + r * 0.5f, p)
        }
        if (angle != 0f) {
            p.color = Fx.withAlpha(0xFF000000.toInt(), abs(angle) / 90f * 0.3f)
            c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        }
        c.restore()
    }

    val busy: Boolean get() = sprites.values.any { it.moving } || fx.busy

    @Suppress("unused")
    private fun r() = rnd.nextFloat()
}
