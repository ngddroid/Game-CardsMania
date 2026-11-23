package com.ngdstudio.mindimania.ui.teenpatti

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
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.teenpatti.TCard
import com.ngdstudio.mindimania.teenpatti.TPEvent
import com.ngdstudio.mindimania.teenpatti.TPStep
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiState
import com.ngdstudio.mindimania.ui.game.AnimatedWorld
import com.ngdstudio.mindimania.ui.game.Fx
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.render.CardBack
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import kotlin.math.abs
import kotlin.math.hypot

private class TSprite(val id: Int, var card: TCard?) {
    var x = 0f; var y = 0f; var rot = 0f; var w = 0f
    var fx = 0f; var fy = 0f; var frot = 0f; var fw = 0f
    var tx = 0f; var ty = 0f; var trot = 0f; var tw = 0f
    var start = 0L; var delay = 0L; var dur = 1L; var arc = 0f
    var faceUp = false
    var flipAt = 0L; var flipDur = 0L; var flipFrom: Boolean? = null
    var z = 0f
    var removeAt = -1L
    var moving = false
    /** A substitute: the card a wild card plays as, drawn small on top of it with a gold frame. */
    var ghost = false
    /** Not drawn before this time (uptime ms). */
    var showAt = 0L
}

/** A chip flying between a seat and the pot. */
private class Chip(val fx: Float, val fy: Float, val tx: Float, val ty: Float, val start: Long, val dur: Long, val color: Int)

/** All animated cards and chips on the Teen Patti table, the felt, and effects. */
class TeenPattiWorld(val layout: TeenPattiLayout) : AnimatedWorld {
    private val sprites = HashMap<Int, TSprite>(24)
    private val chips = ArrayList<Chip>(64)
    val fx = Fx(layout.density)
    var state: TeenPattiState? = null
        private set
    var back: CardBack = CardBack.ROYAL
    var theme: TableTheme = TableTheme.FELT
    var speed = 1f
    var reduceMotion = false
        set(v) { field = v; fx.reduceMotion = v }
    /** My cards glow softly while I can tap them to look. */
    var canSee = false

    /** The pile in the middle shows from the boot until the pot flies to the winner. */
    private var potShowFrom = 0L
    private var potHideAt = Long.MAX_VALUE
    private var potAmount = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val camera = Camera().apply { setLocation(0f, 0f, -14f) }
    private val matrix = Matrix()
    private var felt: Bitmap? = null
    private var feltTheme: TableTheme? = null
    private var shadow: Bitmap? = null
    private var glow: Bitmap? = null
    private val order = ArrayList<TSprite>(24)
    /** Ids of wild cards in every hand this deal (they get a star badge when face up). */
    private var wild: Set<Int> = emptySet()
    /** The turned-up joker card (JOKER variation) sits left of the pot under this sprite id. */
    private val jokerSpriteId = 10_000
    private companion object {
        const val GHOST_BASE = 20_000
        const val SEAT_BASE = 30_000
    }

    /**
     * Sprite id of [seat]'s card number [i]. Keyed by seat and position, not card id: on a guest's phone other players'
     * cards arrive as placeholders and only show their real face (a new id) when they are opened.
     */
    private fun sid(seat: Int, i: Int) = SEAT_BASE + seat * 10 + i

    /** Where the turned-up joker card sits. */
    val jokerPos: Offset get() = layout.jokerPos

    // ------------------------------------------------------------------ steps

    fun apply(step: TPStep, sf0: Float) {
        val s = step.state
        val e = step.event
        val prev = state
        state = s
        val L = layout
        val now = SystemClock.uptimeMillis()
        val sf = if (reduceMotion) sf0 * 0.6f else sf0
        wild = (0 until s.players).flatMap { TeenPattiEngine.wildIds(s, it) }.toSet()
        when (e) {
            is TPEvent.Dealt -> {
                sprites.clear()
                chips.clear()
                val n = s.players
                val seats = (1..n).map { (e.dealer + it) % n }.filter { s.inHand[it] }
                // boot chips first, then the cards go round three times
                seats.forEachIndexed { i, seat -> chipFly(L.chipFrom(seat), L.potPos, now + i * 40L, (380 * sf).toLong()) }
                potAmount = s.pot
                potShowFrom = now + (380 * sf).toLong()
                potHideAt = Long.MAX_VALUE
                var k = 0
                val start = now + (300 * sf).toLong()
                // the dealer deals from her hands
                val from = L.dealerHands
                dealingUntil = now + (300 * sf).toLong() + seats.size * 3 * (70 * sf).toLong() + (260 * sf).toLong()
                for (round in 0 until 3) for (seat in seats) {
                    val c = s.hands[seat][round]
                    val sl = L.cardSlots(seat)[round]
                    val sp = TSprite(sid(seat, round), c)
                    sp.x = from.x; sp.y = from.y; sp.w = L.smallCardW * 0.6f; sp.rot = 0f
                    sp.z = 100f * L.rel(seat) + round
                    sprites[sp.id] = sp
                    val delay = start - now + (k++ * 70L * sf).toLong()
                    tween(sp, sl.x, sl.y, sl.rot, sl.w, now, delay, (260 * sf).toLong(), 0.1f)
                }
                // one line, "Hand 4/10 · AK47"; in landscape under the pot (above it is the top seat's name)
                val handText = (if (s.endless) Strings.get(R.string.tp_pop_hand_n, e.handNo) else Strings.get(R.string.tp_pop_hand, e.handNo, s.setup.hands)) +
                    (if (e.variant != com.ngdstudio.mindimania.teenpatti.TPVariant.CLASSIC) " · " + variantName(e.variant) else "")
                val popY = L.center.y + L.dealerH * 0.12f
                fx.pop(handText, L.center.x, popY, 0xFFFFE27A.toInt(), now, 26f, 1400)
                e.joker?.let { j ->
                    // the joker is turned up from the deck once everyone has their cards
                    val sp = TSprite(jokerSpriteId, j)
                    sp.x = L.dealerHands.x; sp.y = L.dealerHands.y; sp.w = L.smallCardW * 0.6f
                    sp.z = 50f
                    sprites[jokerSpriteId] = sp
                    val at = start - now + (k * 70L * sf).toLong()
                    tween(sp, jokerPos.x, jokerPos.y, -8f, L.smallCardW * 1.05f, now, at, (300 * sf).toLong(), 0f)
                    flip(sp, true, now + at + (200 * sf).toLong(), (240 * sf).toLong())
                    fx.sparkles(jokerPos.x, jokerPos.y, L.smallCardW, 10, 0xFFFFE89A.toInt())
                }
            }
            is TPEvent.Saw -> {
                if (e.seat == L.me) {
                    s.hands[L.me].forEachIndexed { i, c -> sprites[sid(L.me, i)]?.let { it.card = c; flip(it, true, now + i * 70L, (240 * sf).toLong()) } }
                    fx.sparkles(L.cardsOf(L.me).x, L.cardsOf(L.me).y, L.myCardW, 10, 0xFFFFE89A.toInt())
                } else {
                    val pos = L.seatOf(e.seat)
                    popAt(Strings.get(R.string.tp_pop_seen), pos.x, pos.y - L.avatar * 0.85f, 0xFFB6DBFF.toInt(), now, 18f, 900)
                    // the cards lift a little as they are picked up and looked at
                    s.hands[e.seat].forEachIndexed { i, _ ->
                        val sp = sprites[sid(e.seat, i)] ?: return@forEachIndexed
                        val sl = L.cardSlots(e.seat)[i]
                        tween(sp, sl.x, sl.y - L.smallCardW * 0.25f, sl.rot * 1.6f, sl.w, now, 0, (200 * sf).toLong(), 0f)
                    }
                }
            }
            is TPEvent.Chaal -> {
                val from = L.chipFrom(e.seat)
                val count = if (e.raised) 3 else if (e.blind) 1 else 2
                repeat(count) { chipFly(from, L.potPos, now + it * 70L, (360 * sf).toLong()) }
                potAmount = s.pot
                val pos = L.seatOf(e.seat)
                val text = if (e.raised) Strings.get(R.string.tp_pop_raise, e.amount) else Strings.get(if (e.blind) R.string.tp_pop_blind else R.string.tp_pop_chaal, e.amount)
                popAt(text, pos.x, pos.y - L.avatar * 0.85f, if (e.raised) 0xFFFFB74D.toInt() else 0xFFFFFFFF.toInt(), now, if (e.raised) 22f else 18f, 1000)
                if (e.raised) fx.ring(pos.x, pos.y, 0xFFFFB74D.toInt(), now, L.avatar, 450, 6f)
            }
            is TPEvent.Packed -> {
                packCards(prev ?: s, e.seat, now, sf)
                val pos = L.seatOf(e.seat)
                popAt(Strings.get(R.string.tp_pop_pack), pos.x, pos.y - L.avatar * 0.85f, 0xFFFF8A8A.toInt(), now, 20f, 1000)
            }
            is TPEvent.SideshowAsked -> {
                repeat(2) { chipFly(L.chipFrom(e.from), L.potPos, now + it * 70L, (360 * sf).toLong()) }
                potAmount = s.pot
                val a = L.seatOf(e.from)
                val b = L.seatOf(e.to)
                popAt(Strings.get(R.string.tp_pop_sideshow_ask), a.x, a.y - L.avatar * 0.85f, 0xFFC0A6FF.toInt(), now, 20f, 1400)
                fx.ring(b.x, b.y, 0xFFC0A6FF.toInt(), now + 200, L.avatar * 1.2f, 600, 6f)
            }
            is TPEvent.SideshowResult -> {
                val a = L.seatOf(e.from)
                if (!e.accepted) {
                    val b = L.seatOf(e.to)
                    popAt(Strings.get(R.string.tp_pop_sideshow_no), b.x, b.y - L.avatar * 0.85f, 0xFFFFFFFF.toInt(), now, 18f, 1100)
                } else {
                    // only the two players in a sideshow see each other's cards: show them to me if it's mine
                    val mine = e.from == L.me || e.to == L.me
                    val other = if (e.from == L.me) e.to else e.from
                    val hold = if (mine) (1500 * sf).toLong() else 0L
                    if (mine) {
                        val slots = L.cardSlots(other, shown = true)
                        s.hands[other].forEachIndexed { i, c ->
                            val sp = sprites[sid(other, i)] ?: return@forEachIndexed
                            sp.card = c
                            sp.z = 900f + i
                            tween(sp, slots[i].x, slots[i].y, slots[i].rot, slots[i].w, now, 0, (260 * sf).toLong(), 0f)
                            flip(sp, true, now + 120, (220 * sf).toLong())
                            if (e.loser != other) {
                                // they stay in: their cards go face down again
                                val back = L.cardSlots(other)[i]
                                flip(sp, false, now + hold, (220 * sf).toLong())
                                tween(sp, back.x, back.y, back.rot, back.w, now, hold, (260 * sf).toLong(), 0f)
                            }
                        }
                    }
                    val l = L.seatOf(e.loser)
                    popAt(Strings.get(R.string.tp_pop_sideshow_lost), l.x, l.y - L.avatar * 0.85f, 0xFFFF8A8A.toInt(), now + hold / 2, 18f, 1200)
                    fx.ring(a.x, a.y, 0xFFC0A6FF.toInt(), now, L.avatar * 1.2f, 500, 5f)
                    packCards(prev ?: s, e.loser, now + hold, sf, keepFaces = mine && e.loser == other)
                }
            }
            is TPEvent.ShowCalled -> {
                repeat(2) { chipFly(L.chipFrom(e.seat), L.potPos, now + it * 70L, (360 * sf).toLong()) }
                potAmount = s.pot
                fx.pop(Strings.get(R.string.tp_pop_show), L.center.x, L.center.y - L.myCardH * 0.45f, 0xFFFFE27A.toInt(), now, 38f, 1100)
                fx.flash(0xFFFFE27A.toInt(), now, 260, 0.15f)
            }
            is TPEvent.AllIn -> {
                repeat(4) { chipFly(L.chipFrom(e.seat), L.potPos, now + it * 60L, (380 * sf).toLong()) }
                potAmount = s.pot
                fx.pop(Strings.get(R.string.tp_pop_all_in), L.center.x, L.center.y - L.myCardH * 0.45f, 0xFFFF8A8A.toInt(), now, 38f, 1200)
                fx.shake(6f, 360, now)
                fx.flash(0xFFFF6B6B.toInt(), now, 300, 0.2f)
            }
            is TPEvent.Showdown -> {
                e.seats.forEachIndexed { k, seat ->
                    val at = now + k * (260 * sf).toLong()
                    val slots = L.cardSlots(seat, shown = true)
                    s.hands[seat].forEachIndexed { i, c ->
                        val sp = sprites[sid(seat, i)] ?: return@forEachIndexed
                        sp.card = c
                        sp.z = 800f + k * 10 + i
                        if (seat != L.me) tween(sp, slots[i].x, slots[i].y, slots[i].rot, slots[i].w, now, at - now, (280 * sf).toLong(), 0f)
                        flip(sp, true, at + (140 * sf).toLong(), (220 * sf).toLong())
                    }
                    // once the cards are up, each wild card steps back and the card it plays as flips into its place
                    swapWilds(s, seat, k, at + (700 * sf).toLong(), sf)
                }
            }
            is TPEvent.PotWon -> {
                val hold = (if (prev?.shown?.isNotEmpty() == true || s.shown.isNotEmpty()) 1300 else 300) * sf
                val at = now + hold.toLong()
                potHideAt = at
                e.winners.forEachIndexed { i, w ->
                    val to = L.seatOf(w)
                    repeat(7) { k -> chipFly(L.potPos, to, at + k * 55L + i * 30L, (420 * sf).toLong(), spread = true) }
                    popAt("+${e.amounts[i]}", to.x, to.y - L.avatar * 0.95f, 0xFF8CFFB4.toInt(), at + (420 * sf).toLong(), if (w == L.me) 30f else 22f, 1400)
                    fx.ring(to.x, to.y, 0xFFFFE27A.toInt(), at + (420 * sf).toLong(), L.avatar * 1.5f, 600, 8f)
                    if (w == L.me) {
                        fx.burst(to.x, to.y, 26, intArrayOf(0xFFFFE27A.toInt(), 0xFFFFFFFF.toInt(), 0xFF8CFFB4.toInt()), 240f, 4f, 0f, 0.9f, 2)
                    }
                }
                if (L.me in e.winners) fx.confetti(L.width, 70)
            }
            is TPEvent.MatchOver -> Unit
            is TPEvent.Refilled -> e.seats.forEach { seat ->
                val pos = L.seatOf(seat)
                popAt("+${e.chips}", pos.x, pos.y - L.avatar * 0.9f, 0xFF8CFFB4.toInt(), now, 22f, 1300)
                fx.burst(pos.x, pos.y, 18, intArrayOf(0xFF8CFFB4.toInt(), 0xFFFFE27A.toInt()), 200f, 4f, 0f, 0.8f, 2)
                repeat(4) { chipFly(L.dealerHands, pos, now + it * 70L, (380 * sf).toLong()) }
            }
            is TPEvent.Restored -> {
                sprites.clear()
                chips.clear()
                potAmount = s.pot
                potShowFrom = 0L
                potHideAt = if (s.handOver) 0L else Long.MAX_VALUE
                for (seat in 0 until s.players) {
                    if (!s.inHand[seat] || s.folded[seat]) continue
                    val shown = seat in s.shown
                    val slots = L.cardSlots(seat, shown = shown && seat != L.me)
                    s.hands[seat].forEachIndexed { i, c ->
                        val sp = TSprite(sid(seat, i), c)
                        sp.x = slots[i].x; sp.y = slots[i].y; sp.w = slots[i].w; sp.rot = slots[i].rot
                        sp.tx = sp.x; sp.ty = sp.y; sp.tw = sp.w; sp.trot = sp.rot
                        sp.faceUp = shown || (seat == L.me && s.seen[seat])
                        sp.z = 100f * L.rel(seat) + i
                        sprites[sp.id] = sp
                    }
                }
                for (seat in 0 until s.players) {
                    if (!s.inHand[seat] || s.folded[seat]) continue
                    if (seat in s.shown) swapWilds(s, seat, s.shown.indexOf(seat), 0L, 0.01f)
                }
                s.joker?.let { j ->
                    val sp = TSprite(jokerSpriteId, j)
                    sp.x = jokerPos.x; sp.y = jokerPos.y; sp.w = L.smallCardW * 1.05f; sp.rot = -8f
                    sp.tx = sp.x; sp.ty = sp.y; sp.tw = sp.w; sp.trot = sp.rot
                    sp.faceUp = true
                    sp.z = 50f
                    sprites[jokerSpriteId] = sp
                }
            }
        }
    }

    /** A text pop kept fully on screen (seat pops near the edges would be cut off). */
    private fun popAt(text: String, x: Float, y: Float, color: Int, now: Long, size: Float = 34f, dur: Long = 1100, rise: Float = 40f) {
        val half = text.length * size * layout.density * 0.3f
        val margin = 8f * layout.density
        val cx = if (half * 2 + margin * 2 >= layout.width) layout.width / 2 else x.coerceIn(half + margin, layout.width - half - margin)
        fx.pop(text, cx, y, color, now, size, dur, rise = rise)
    }

    /**
     * The reveal for wild cards (Joker, Low Joker, AK47): each wild card rises half a card and slips behind the row,
     * and the card it plays as flips face up in its place (gold frame). The row then reads as the hand that counts,
     * e.g. J♦ [K→J♣] 8♠ = a pair of Jacks, while the wild card still peeks out behind it.
     * Lifts downward instead when there's no room above (a seat at the top of the table).
     */
    private fun swapWilds(s: TeenPattiState, seat: Int, k: Int, at: Long, sf: Float) {
        val subs = TeenPattiEngine.wildsAs(s, seat)
        if (subs.isEmpty()) return
        val L = layout
        val now = SystemClock.uptimeMillis()
        val slots = if (seat == L.me) L.cardSlots(seat) else L.cardSlots(seat, shown = true)
        s.hands[seat].forEachIndexed { i, c ->
            val asCard = subs[c.id] ?: return@forEachIndexed
            val sl = slots[i]
            val h = sl.w * 1.4f
            val delay = (at - now).coerceAtLeast(0) + i * (120 * sf).toLong()
            val k = L.wildLift(seat)
            val up = sl.y - h * 0.5f - h * k >= L.tableTop
            val wildSprite = sprites[sid(seat, i)]
            if (wildSprite != null) {
                wildSprite.z = 700f + k * 10 + i
                tween(wildSprite, sl.x, sl.y + (if (up) -h * k else h * k), 0f, sl.w * 0.94f, now, delay, (300 * sf).toLong().coerceAtLeast(1), 0f)
            }
            val g = TSprite(GHOST_BASE + sid(seat, i), asCard)
            g.ghost = true
            g.faceUp = false
            g.x = sl.x; g.y = sl.y; g.w = sl.w; g.rot = 0f
            g.tx = g.x; g.ty = g.y; g.tw = g.w; g.trot = 0f
            g.z = 800f + k * 10 + i + 0.5f
            // hidden until the wild card starts to move, then turns face up in the slot
            g.showAt = now + delay + (120 * sf).toLong()
            sprites[g.id] = g
            flip(g, true, g.showAt + (60 * sf).toLong(), (280 * sf).toLong())
        }
    }

    /** [seat]'s cards slide to the middle, turn over and vanish. */
    private fun packCards(s: TeenPattiState, seat: Int, at: Long, sf: Float, keepFaces: Boolean = false) {
        val L = layout
        val now = SystemClock.uptimeMillis()
        s.hands.getOrNull(seat)?.forEachIndexed { i, _ ->
            val sp = sprites[sid(seat, i)] ?: return@forEachIndexed
            val d = at - now + i * 40L
            tween(sp, L.center.x + (i - 1) * 4f, L.center.y + L.smallCardW * 0.3f, (i - 1) * 9f, L.smallCardW * 0.7f, now, d, (320 * sf).toLong(), 0f)
            if (!keepFaces || sp.faceUp) flip(sp, false, at + (160 * sf).toLong(), (180 * sf).toLong())
            sp.removeAt = at + (520 * sf).toLong()
            sprites[GHOST_BASE + sid(seat, i)]?.removeAt = at
        }
    }

    private fun chipFly(from: Offset, to: Offset, start: Long, dur: Long, spread: Boolean = false) {
        val r = layout.chipR
        val jx = (chips.size * 37 % 13 - 6) * r * 0.35f
        val jy = (chips.size * 53 % 11 - 5) * r * 0.25f
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFF1E6FE8.toInt(), 0xFF13B36A.toInt(), 0xFF6A3FE0.toInt(), 0xFFFFB300.toInt())
        val color = colors[chips.size % colors.size]
        chips += if (spread) Chip(from.x + jx, from.y + jy, to.x, to.y, start, dur, color)
        else Chip(from.x, from.y, to.x + jx, to.y + jy, start, dur, color)
    }

    private fun tween(sp: TSprite, x: Float, y: Float, rot: Float, w: Float, now: Long, delay: Long, dur: Long, arc: Float) {
        sp.fx = sp.x; sp.fy = sp.y; sp.frot = sp.rot; sp.fw = sp.w
        sp.tx = x; sp.ty = y; sp.trot = rot; sp.tw = w
        sp.start = now; sp.delay = delay.coerceAtLeast(0); sp.dur = dur.coerceAtLeast(1)
        sp.arc = if (hypot(x - sp.fx, y - sp.fy) > layout.width * 0.15f) arc else 0f
        sp.moving = true
    }

    private fun flip(sp: TSprite, up: Boolean, at: Long, dur: Long) {
        if (sp.faceUp == up) return
        sp.flipFrom = sp.faceUp
        sp.faceUp = up
        sp.flipAt = at
        sp.flipDur = dur.coerceAtLeast(1)
    }

    // ------------------------------------------------------------------ frame pacing

    override fun isBusy(now: Long): Boolean {
        if (fx.isActive(now)) return true
        if (chips.isNotEmpty() || now < dealingUntil) return true
        if (now < potShowFrom || (potHideAt != Long.MAX_VALUE && now < potHideAt)) return true
        for (sp in sprites.values) if (sp.moving || sp.flipFrom != null || sp.removeAt > 0 || sp.showAt > now) return true
        return false
    }

    /** My unseen cards breathe gently while I may look at them. */
    override fun idleFrameMs(now: Long): Long = if (canSee) 50L else DealerClock.nextFrameIn(now)

    // ------------------------------------------------------------------ hit test

    /** True when ([x], [y]) is on my three cards. */
    fun hitMine(x: Float, y: Float): Boolean {
        val c = layout.cardsOf(layout.me)
        return abs(x - c.x) <= layout.myRowHalf + 8f * layout.density && abs(y - c.y) <= layout.myCardH * 0.6f
    }

    // ------------------------------------------------------------------ draw

    private fun ensure() {
        if (felt == null || feltTheme != theme) {
            feltTheme = theme
            felt = renderFelt()
        }
        if (shadow == null) {
            val w = (layout.myCardW * 0.5f).toInt().coerceAtLeast(16)
            val h = (w * 1.4f).toInt()
            val pad = w / 5
            fun soft(color: Int, blur: Float): Bitmap {
                val b = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
                Canvas(b).drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + w).toFloat(), (pad + h).toFloat()), w * 0.1f, w * 0.1f,
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
        pp.shader = RadialGradient(L.center.x, L.center.y, h * 0.75f, intArrayOf(theme.center.toArgb(), theme.edge.toArgb()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pp)
        val r = java.util.Random(23)
        pp.shader = null
        repeat((w * h / 110).coerceAtMost(50000)) {
            val a = r.nextInt(6, 16)
            pp.color = if (r.nextBoolean()) (a shl 24) or 0xFFFFFF else (a shl 24)
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            c.drawRect(x, y, x + 1.3f, y + 1.3f, pp)
        }
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
        // the middle stays clear for the dealer
        pp.style = Paint.Style.FILL
        pp.shader = RadialGradient(L.center.x, L.center.y, h * 0.8f, intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pp)
        pp.shader = LinearGradient(0f, L.tableBottom, 0f, h.toFloat(), intArrayOf(0x00000000, 0x88000000.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, L.tableBottom, w.toFloat(), h.toFloat(), pp)
        return b
    }

    private val drawOrder = Comparator<TSprite> { a, b -> (a.z + if (a.moving) 4000f else 0f).compareTo(b.z + if (b.moving) 4000f else 0f) }

    /** The deal is on: the dealer's hand moves until then. */
    var dealingUntil = 0L
        private set

    /** First pass: the felt (the dealer is drawn over it by the screen, then [drawTop]). */
    fun drawBase(c: Canvas, now: Long) {
        ensure()
        fx.updateShake(now)
        c.save()
        c.translate(fx.shakeX, fx.shakeY)
        felt?.let { c.drawBitmap(it, 0f, 0f, null) }
        c.restore()
    }

    /** Second pass: cards, chips and effects, over the dealer. */
    fun drawTop(c: Canvas, now: Long) {
        c.save()
        c.translate(fx.shakeX, fx.shakeY)
        update(now)
        order.clear()
        for (sp in sprites.values) order += sp
        order.sortWith(drawOrder)
        val mine0 = sid(layout.me, 0)
        for (sp in order) if (sp.showAt <= now) drawSprite(c, sp, now, sp.id in mine0..mine0 + 2)
        drawChips(c, now)
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
        chips.removeAll { now > it.start + it.dur }
    }

    /** A small stack of chips in the middle; taller as the pot grows. */
    private fun drawPot(c: Canvas, now: Long) {
        if (now < potShowFrom || now >= potHideAt || potAmount <= 0) return
        val L = layout
        val stacks = 3
        val perStack = (1 + potAmount / (TeenPattiEngine.BOOT * 6)).coerceIn(1, 9)
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFF1E6FE8.toInt(), 0xFF13B36A.toInt())
        for (st in 0 until stacks) {
            val x = L.center.x + (st - 1) * L.chipR * 2.2f
            val count = (perStack - (if (st == 1) 0 else 1)).coerceAtLeast(1)
            for (k in 0 until count) drawChip(c, x, L.center.y + L.chipR * 0.6f - k * L.chipR * 0.38f, colors[st], 1f)
        }
    }

    private fun drawChips(c: Canvas, now: Long) {
        for (ch in chips) {
            val t = ((now - ch.start).toFloat() / ch.dur)
            if (t < 0f) continue
            val e = Fx.easeOutCubic(t.coerceIn(0f, 1f))
            val x = ch.fx + (ch.tx - ch.fx) * e
            val y = ch.fy + (ch.ty - ch.fy) * e - kotlin.math.sin(e * Math.PI).toFloat() * layout.chipR * 3f
            drawChip(c, x, y, ch.color, if (t > 0.85f) (1f - (t - 0.85f) / 0.15f).coerceIn(0f, 1f) else 1f)
        }
    }

    /** A gold star in the bottom-left corner of a wild card (the part a fanned hand never covers). */
    private fun drawWildBadge(c: Canvas, w: Float, h: Float) {
        val r = w * 0.17f
        val x = -w / 2 + r * 1.25f
        val y = h / 2 - r * 1.25f
        p.style = Paint.Style.FILL
        p.color = 0xFFFFB300.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 1.4f
        p.color = 0xFF4A2A00.toInt()
        c.drawText("★", x, y + r * 0.5f, p)
    }

    private fun drawChip(c: Canvas, x: Float, y: Float, color: Int, alpha: Float) {
        val r = layout.chipR
        p.style = Paint.Style.FILL
        p.color = Fx.withAlpha(0xFF000000.toInt(), 0.35f * alpha)
        c.drawCircle(x + r * 0.12f, y + r * 0.2f, r, p)
        p.color = Fx.withAlpha(color, alpha)
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.28f
        p.color = Fx.withAlpha(0xFFFFFFFF.toInt(), 0.85f * alpha)
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(r * 0.5f, r * 0.5f), 0f)
        c.drawCircle(x, y, r * 0.82f, p)
        p.pathEffect = null
        p.strokeWidth = r * 0.1f
        p.color = Fx.withAlpha(0xFFFFFFFF.toInt(), 0.5f * alpha)
        c.drawCircle(x, y, r * 0.5f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawSprite(c: Canvas, sp: TSprite, now: Long, mine: Boolean) {
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
        if (mine && canSee && !up && !sp.moving) {
            glow?.let { g ->
                val k = 0.55f + 0.45f * ((kotlin.math.sin(now / 260.0) + 1) / 2).toFloat()
                paint.alpha = (k * 255).toInt()
                val gw = w * 1.25f
                rect.set(-gw / 2, -gw * 0.7f, gw / 2, gw * 0.7f)
                c.drawBitmap(g, null, rect, paint)
                paint.alpha = 255
            }
        }
        rect.set(-w / 2, -h / 2, w / 2, h / 2)
        c.drawBitmap(bmp, null, rect, paint)
        if (sp.ghost) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.07f
            p.color = 0xFFFFC93C.toInt()
            c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
            p.style = Paint.Style.FILL
        } else if (up && card != null && sp.id != jokerSpriteId && card.id in wild) drawWildBadge(c, w, h)
        if (angle != 0f) {
            p.color = Fx.withAlpha(0xFF000000.toInt(), abs(angle) / 90f * 0.3f)
            c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        }
        c.restore()
    }
}

/** What [seat]'s hand is called in this hand's variation: the kind (wilds used), or the 999 score. */
fun handLabel(s: TeenPattiState, seat: Int): String {
    if (s.variant == com.ngdstudio.mindimania.teenpatti.TPVariant.NINE99) return Strings.get(R.string.tp_score_999, TeenPattiEngine.nine99(s.hands[seat]))
    return TeenPattiEngine.handKind(s, seat)?.let { kindName(it) } ?: ""
}

/** Localised name of a variation. */
fun variantName(v: com.ngdstudio.mindimania.teenpatti.TPVariant): String = Strings.get(variantNameRes(v))

fun variantNameRes(v: com.ngdstudio.mindimania.teenpatti.TPVariant): Int = when (v) {
    com.ngdstudio.mindimania.teenpatti.TPVariant.CLASSIC -> R.string.tp_var_classic
    com.ngdstudio.mindimania.teenpatti.TPVariant.MUFLIS -> R.string.tp_var_muflis
    com.ngdstudio.mindimania.teenpatti.TPVariant.JOKER -> R.string.tp_var_joker
    com.ngdstudio.mindimania.teenpatti.TPVariant.LOWEST_JOKER -> R.string.tp_var_lowest
    com.ngdstudio.mindimania.teenpatti.TPVariant.AK47 -> R.string.tp_var_ak47
    com.ngdstudio.mindimania.teenpatti.TPVariant.NINE99 -> R.string.tp_var_999
    com.ngdstudio.mindimania.teenpatti.TPVariant.FOUR_X_BOOT -> R.string.tp_var_4x
    com.ngdstudio.mindimania.teenpatti.TPVariant.MIXED -> R.string.tp_var_mixed
}

fun variantDescRes(v: com.ngdstudio.mindimania.teenpatti.TPVariant): Int = when (v) {
    com.ngdstudio.mindimania.teenpatti.TPVariant.CLASSIC -> R.string.tp_var_classic_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.MUFLIS -> R.string.tp_var_muflis_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.JOKER -> R.string.tp_var_joker_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.LOWEST_JOKER -> R.string.tp_var_lowest_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.AK47 -> R.string.tp_var_ak47_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.NINE99 -> R.string.tp_var_999_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.FOUR_X_BOOT -> R.string.tp_var_4x_desc
    com.ngdstudio.mindimania.teenpatti.TPVariant.MIXED -> R.string.tp_var_mixed_desc
}

/** Short reminder of this hand's special rule for the table, or null in classic. */
fun variantHint(s: TeenPattiState): String? = when (s.variant) {
    com.ngdstudio.mindimania.teenpatti.TPVariant.MUFLIS -> Strings.get(R.string.tp_hint_muflis)
    com.ngdstudio.mindimania.teenpatti.TPVariant.JOKER -> s.joker?.let { Strings.get(R.string.tp_hint_joker, rankLabel(it.rank)) }
    com.ngdstudio.mindimania.teenpatti.TPVariant.LOWEST_JOKER -> Strings.get(R.string.tp_hint_lowest)
    com.ngdstudio.mindimania.teenpatti.TPVariant.AK47 -> Strings.get(R.string.tp_hint_ak47)
    com.ngdstudio.mindimania.teenpatti.TPVariant.NINE99 -> Strings.get(R.string.tp_hint_999)
    com.ngdstudio.mindimania.teenpatti.TPVariant.FOUR_X_BOOT -> Strings.get(R.string.tp_hint_4x)
    else -> null
}

fun rankLabel(rank: Int): String = when (rank) { 14 -> "A"; 13 -> "K"; 12 -> "Q"; 11 -> "J"; else -> "$rank" }

/** Localised name of a hand kind. */
fun kindName(k: com.ngdstudio.mindimania.teenpatti.TPKind): String = Strings.get(
    when (k) {
        com.ngdstudio.mindimania.teenpatti.TPKind.TRAIL -> R.string.tp_kind_trail
        com.ngdstudio.mindimania.teenpatti.TPKind.PURE_SEQUENCE -> R.string.tp_kind_pure
        com.ngdstudio.mindimania.teenpatti.TPKind.SEQUENCE -> R.string.tp_kind_sequence
        com.ngdstudio.mindimania.teenpatti.TPKind.COLOR -> R.string.tp_kind_color
        com.ngdstudio.mindimania.teenpatti.TPKind.PAIR -> R.string.tp_kind_pair
        com.ngdstudio.mindimania.teenpatti.TPKind.HIGH_CARD -> R.string.tp_kind_high
    }
)
