package com.ngdstudio.mindimania.ui.game

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.engine.DrawReason
import com.ngdstudio.mindimania.engine.GameEvent
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.engine.model.Side
import com.ngdstudio.mindimania.engine.model.Suit
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.render.CardBack
import com.ngdstudio.mindimania.ui.render.SuitSymbols
import com.ngdstudio.mindimania.ui.theme.Gems
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Sprite(val id: Int) {
    var x = 0f; var y = 0f; var rot = 0f; var w = 0f
    var fx = 0f; var fy = 0f; var frot = 0f; var fw = 0f
    var tx = 0f; var ty = 0f; var trot = 0f; var tw = 0f
    var start = 0L; var dur = 1L; var delay = 0L
    var arc = 0f; var spin = 0f; var pop = 0f
    var look: Look = Look.Back
    var prevLook: Look? = null
    var flipStart = 0L; var flipDur = 1L
    var z = 0f
    var zone = Zone.DRAW
    var owner = -1
    var removeAt = -1L
    var flying = false
    /** In [SpriteWorld.playable] or [SpriteWorld.jumpIn] / in [SpriteWorld.selected]; refreshed when those change. */
    var hlPlayable = false
    var hlSelected = false
}

/**
 * All cards on the table as animated sprites, plus table rendering and effects.
 * Retargeted every time the game state changes; animated every frame.
 */
class SpriteWorld(val layout: TableLayout, private val humanId: Int = 0) : AnimatedWorld {
    val sprites = LinkedHashMap<Int, Sprite>()
    val fx = Fx(layout.density)
    private var prev: GameState? = null
    var state: GameState? = null
        private set
    private val rnd = Random(7)

    // configuration (updated from Compose)
    var speedFactor = 1f
    var reduceMotion = false
        set(v) { field = v; fx.reduceMotion = v }
    var colorBlind = true
    var theme: TableTheme = TableTheme.ROYAL
    var back: CardBack = CardBack.ROYAL
    var selected: List<Int> = emptyList()
        set(v) { field = v; hlDirty = true }
    var playable: Set<Int> = emptySet()
        set(v) { field = v; hlDirty = true }
    var jumpIn: Set<Int> = emptySet()
        set(v) { field = v; hlDirty = true }
    var hint: Int? = null
    var sortMode = com.ngdstudio.mindimania.data.SortMode.COLOR
    var interactive = false

    // drag state
    var dragId: Int? = null
    var dragX = 0f
    var dragY = 0f
    private var dragTilt = 0f

    // side mix for day/night background (0 = sun, 1 = moon)
    private var sideMix = 0f
    private var sideTarget = 0f

    private val camera = Camera().apply { setLocation(0f, 0f, -18f) }
    private val matrix = Matrix()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0.8f)
            postConcat(ColorMatrix(floatArrayOf(0.8f, 0f, 0f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        })
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private var shadowBmp: Bitmap? = null
    private var glowBmp: Bitmap? = null
    private var bgDay: Bitmap? = null
    private var bgNight: Bitmap? = null
    private var bgTheme: TableTheme? = null

    // ---------------------------------------------------------------- frames

    fun applyFrame(f: Frame) {
        val s = f.state
        val now = android.os.SystemClock.uptimeMillis()
        val old = state
        prev = old
        state = s
        hlDirty = true
        sideTarget = if (s.side == Side.MOON) 1f else 0f
        if (old == null) sideMix = sideTarget
        val targets = layout.targets(s, selected, sortMode, playable, humanId)
        val e = f.event
        val sf = if (reduceMotion) 0.6f * speedFactor else speedFactor

        // tween params per event
        val special = HashMap<Int, TweenSpec>()
        when (e) {
            is GameEvent.Dealt -> e.order.forEachIndexed { i, (_, c) ->
                special[c.id] = TweenSpec((300 * sf).toLong(), (i * Timing.DEAL_STAGGER * sf).toLong(), 0.12f, 200f, 0.1f)
            }
            is GameEvent.StarterFlipped -> special[e.card.id] = TweenSpec((460 * sf).toLong(), 0, 0.25f, 0f, 0.35f)
            is GameEvent.CardPlayed -> {
                val all = listOf(e.card) + e.extras
                all.forEachIndexed { i, c ->
                    special[c.id] = TweenSpec((400 * sf).toLong(), (i * 120 * sf).toLong(), 0.22f, (if (rnd.nextBoolean()) 1 else -1) * (60f + rnd.nextFloat() * 120f), 0.28f)
                }
            }
            is GameEvent.CardsDrawn -> e.cards.forEachIndexed { i, c ->
                special[c.id] = TweenSpec((360 * sf).toLong(), (min(i, 20) * Timing.DRAW_STAGGER * sf).toLong(), 0.14f, 0f, 0.12f)
            }
            is GameEvent.HandsSwapped, is GameEvent.HandsRotated -> {
                var i = 0
                for (pl in s.players) for (c in pl.hand) special[c.id] = TweenSpec((680 * sf).toLong(), (i++ % 12 * 18 * sf).toLong(), 0.32f, 0f, 0.15f)
            }
            is GameEvent.Purged -> e.cards.forEachIndexed { i, c -> special[c.id] = TweenSpec((420 * sf).toLong(), (i * 80 * sf).toLong(), 0.25f, 90f, 0.2f) }
            else -> Unit
        }
        val defaultSpec = TweenSpec((260 * sf).toLong(), 0, 0f, 0f, 0f)

        for ((id, t) in targets) {
            var sp = sprites[id]
            if (sp == null) {
                sp = Sprite(id)
                val from = old?.let { layout.zonePos(it, id) }
                if (from != null) {
                    sp.x = from.x; sp.y = from.y
                    val fromDraw = old.drawPile.any { it.id == id }
                    sp.w = if (fromDraw) layout.pileCardW else t.w
                    sp.rot = 0f
                    sp.look = if (fromDraw) hiddenLookOf(old, id) ?: t.look else t.look
                } else {
                    sp.x = t.x; sp.y = t.y; sp.w = t.w; sp.rot = t.rot; sp.look = t.look
                }
                sp.tx = sp.x; sp.ty = sp.y; sp.tw = sp.w; sp.trot = sp.rot
                sprites[id] = sp
            }
            sp.removeAt = -1
            sp.zone = t.zone
            sp.owner = t.owner
            sp.z = t.z
            val spec = special[id] ?: defaultSpec
            retarget(sp, t.x, t.y, t.rot, t.w, spec, now)
            if (sp.look != t.look) {
                val flipDelay = if (e is GameEvent.SideFlipped) ((sp.x / layout.width) * 420 * sf).toLong() + (rnd.nextInt(60)) else spec.delay + spec.dur / 4
                flip(sp, t.look, now + flipDelay, ((if (e is GameEvent.SideFlipped) 420 else 300) * sf).toLong())
            }
        }
        // sprites that left the visible set
        val gone = sprites.keys.filter { it !in targets }
        for (id in gone) {
            val sp = sprites[id] ?: continue
            if (sp.removeAt > 0) continue
            val pos = layout.zonePos(s, id)
            val toDiscard = s.discard.any { it.id == id }
            if (pos == null || toDiscard) {
                sprites.remove(id)
            } else {
                val spec = TweenSpec((520 * sf).toLong(), rnd.nextLong(0, (180 * sf).toLong() + 1), 0.2f, 0f, 0.05f)
                retarget(sp, pos.x, pos.y, 0f, layout.pileCardW, spec, now)
                sp.removeAt = now + spec.delay + spec.dur
                sp.z = 150f
            }
        }
        if (e != null) spawnEffects(e, s, old, now)
    }

    /** Recompute targets without an event (selection, sort or playable changed). */
    fun relayout() {
        val s = state ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val targets = layout.targets(s, selected, sortMode, playable, humanId)
        val spec = TweenSpec((220 * speedFactor).toLong(), 0, 0f, 0f, 0f)
        for ((id, t) in targets) {
            val sp = sprites[id] ?: continue
            if (sp.zone != Zone.HAND) continue
            sp.z = t.z
            retarget(sp, t.x, t.y, t.rot, t.w, spec, now)
        }
    }

    private data class TweenSpec(val dur: Long, val delay: Long, val arc: Float, val spin: Float, val pop: Float)

    private fun retarget(sp: Sprite, x: Float, y: Float, rot: Float, w: Float, spec: TweenSpec, now: Long) {
        val same = abs(sp.tx - x) < 0.5f && abs(sp.ty - y) < 0.5f && abs(sp.trot - rot) < 0.3f && abs(sp.tw - w) < 0.5f
        if (same) return
        sp.fx = sp.x; sp.fy = sp.y; sp.frot = sp.rot; sp.fw = sp.w
        sp.tx = x; sp.ty = y; sp.trot = rot; sp.tw = w
        sp.start = now
        sp.delay = spec.delay
        sp.dur = max(1L, spec.dur)
        val dist = hypot(x - sp.fx, y - sp.fy)
        sp.arc = if (dist > layout.width * 0.15f) spec.arc else 0f
        sp.spin = if (dist > layout.width * 0.15f) spec.spin else 0f
        sp.pop = if (dist > layout.width * 0.1f) spec.pop else 0f
    }

    private fun flip(sp: Sprite, to: Look, start: Long, dur: Long) {
        sp.prevLook = sp.look
        sp.look = to
        sp.flipStart = start
        sp.flipDur = max(1L, dur)
    }

    private fun hiddenLookOf(s: GameState, id: Int): Look? {
        val c = s.drawPile.firstOrNull { it.id == id } ?: return null
        return if (s.mode == com.ngdstudio.mindimania.engine.model.GameMode.ECLIPSE) Look.Front(c.face(s.side.other)) else Look.Back
    }

    // ---------------------------------------------------------------- effects

    private fun seat(id: Int) = layout.seatOf(id)

    private fun spawnEffects(e: GameEvent, s: GameState, old: GameState?, now: Long) {
        val L = layout
        val d = L.density
        val gold = 0xFFFFC93C.toInt()
        val white = 0xFFFFFFFF.toInt()
        when (e) {
            is GameEvent.RoundStarted -> fx.sparkles(L.center.x, L.center.y, L.width * 0.4f, 30, gold)
            is GameEvent.CardPlayed -> {
                val face = e.card.face(old?.side ?: s.side)
                val delayAt = now + (380 * speedFactor).toLong()
                fx.burst(L.discardPos.x, L.discardPos.y, 10, intArrayOf(0x88FFFFFF.toInt(), 0x66FFE89A), 160f, 3.5f, 0f, 0.5f)
                fx.ring(L.discardPos.x, L.discardPos.y, Fx.withAlpha(colorOf(face.suit), 0.9f), delayAt - 120, L.pileCardW * 1.1f, 520, 5f)
                val label = when (face.kind) {
                    Kind.BLOCK -> Strings.get(R.string.mania_fx_skip)
                    Kind.REVERSE -> Strings.get(R.string.mania_fx_reverse)
                    Kind.BLOCK_ALL -> Strings.get(R.string.mania_fx_skip_all)
                    Kind.PURGE -> Strings.get(R.string.mania_fx_discard_all)
                    Kind.ECLIPSE -> null
                    Kind.DRAW -> "+${face.value}"
                    Kind.PRISM_DRAW -> "+${face.value}"
                    Kind.PRISM_REBOUND -> Strings.get(R.string.mania_fx_reverse_four)
                    Kind.COLOR_STORM -> Strings.get(R.string.mania_fx_color_storm)
                    Kind.PRISM_HUNT -> Strings.get(R.string.mania_fx_draw_color)
                    Kind.PRISM -> null
                    Kind.NUMBER -> if (e.extras.isNotEmpty()) Strings.get(R.string.mania_fx_combo, e.extras.size + 1) else null
                }
                if (label != null) fx.pop(label, L.center.x, L.pileY - L.pileCardW * 1.05f, if (face.isWild) 0xFFFFE89A.toInt() else lighten(colorOf(face.suit)), now + 250, 30f, 1150)
                if (face.kind == Kind.COLOR_STORM) {
                    fx.lightning(L.center.x - L.width * 0.1f, L.topInset, L.discardPos.x, L.discardPos.y, now + 200)
                    fx.flash(0xFFE6F4FF.toInt(), now + 200, 350, 0.35f)
                }
                if (face.isDraw && face.drawAmount >= 6) fx.shake(6f, 420, now + 350)
            }
            is GameEvent.Blocked -> for (id in e.playerIds) {
                val pos = seat(id)
                fx.pop(Strings.get(R.string.mania_fx_skipped), pos.x.coerceIn(L.width * 0.16f, L.width * 0.84f), pos.y, 0xFFFF6F95.toInt(), now, 22f, 850, 0xFF5C0522.toInt(), 12f)
                fx.ring(pos.x, pos.y, 0xFFE0245E.toInt(), now, L.avatar * 0.9f, 500, 5f)
            }
            is GameEvent.DirectionChanged -> {
                fx.ring(L.center.x, L.center.y, gold, now, L.ringRx, 650, 4f)
            }
            is GameEvent.StackGrew -> {
                val col = when {
                    e.total >= 12 -> 0xFFFF3B3B.toInt()
                    e.total >= 6 -> 0xFFFF7A1A.toInt()
                    else -> 0xFFFFC93C.toInt()
                }
                fx.pop("+${e.total}", L.discardPos.x + L.pileCardW * 0.55f, L.pileY - L.pileCardW * 0.6f, col, now + 200, 26f + min(e.total, 20) * 1.2f, 900)
                if (e.total >= 10) fx.shake(4f + min(e.total, 30) * 0.3f, 380, now + 200)
            }
            is GameEvent.CardsDrawn -> {
                val penalty = e.reason != DrawReason.NORMAL && e.reason != DrawReason.MATCH && e.reason != DrawReason.START
                if (penalty || e.cards.size >= 3) {
                    val pos = if (e.playerId == humanId) androidx.compose.ui.geometry.Offset(L.width / 2, L.handTopY) else seat(e.playerId)
                    val col = if (penalty) 0xFFFF5F7A.toInt() else 0xFFB9E6FF.toInt()
                    fx.pop("+${e.cards.size}", pos.x, pos.y - L.avatar * 0.2f, col, now + 150, 30f, 1000)
                    if (penalty && e.cards.size >= 6) fx.shake(5f, 350, now + 150)
                }
            }
            is GameEvent.ColorChosen -> {
                val col = colorOf(e.suit)
                fx.ring(L.discardPos.x, L.discardPos.y, col, now, L.width * 0.9f, 700, 18f)
                fx.flash(col, now, 520, 0.22f)
                fx.burst(L.discardPos.x, L.discardPos.y, 26, intArrayOf(col, lighten(col), white), 320f, 4.5f, 120f, 0.9f, 2)
                fx.pop(com.ngdstudio.mindimania.ui.theme.Gems.name(e.suit).uppercase(), L.center.x, L.pileY + L.pileCardW * 0.95f, lighten(col), now, 26f, 900)
            }
            is GameEvent.HandsSwapped -> {
                val a = seat(e.a); val b = seat(e.b)
                fx.pop(Strings.get(R.string.mania_fx_swap), L.center.x, L.pileY - L.pileCardW * 1.05f, 0xFF7FF5EC.toInt(), now, 32f, 1000)
                fx.ring(a.x, a.y, 0xFF19D3C5.toInt(), now, L.avatar, 600)
                fx.ring(b.x, b.y, 0xFF19D3C5.toInt(), now, L.avatar, 600)
            }
            is GameEvent.HandsRotated -> {
                fx.pop(Strings.get(R.string.mania_fx_rotate), L.center.x, L.pileY - L.pileCardW * 1.05f, 0xFF7FF5EC.toInt(), now, 32f, 1100)
                fx.ring(L.center.x, L.center.y, 0xFF19D3C5.toInt(), now, L.ringRx, 800, 6f)
            }
            is GameEvent.Purged -> {
                fx.burst(L.discardPos.x, L.discardPos.y, 40, intArrayOf(0xFFFF7A1A.toInt(), 0xFFFFC93C.toInt(), 0xFFE0245E.toInt()), 220f, 5f, -260f, 1.0f)
            }
            is GameEvent.PlayerEliminated -> {
                val pos = seat(e.playerId)
                fx.burst(pos.x, pos.y, 60, intArrayOf(0xFFB9E6FF.toInt(), white, 0xFF8B5CF6.toInt()), 420f, 7f, 500f, 1.3f, 3)
                fx.pop(Strings.get(R.string.mania_fx_overload), pos.x, pos.y + L.avatar * 0.6f, 0xFFFF5F7A.toInt(), now, 30f, 1400)
                fx.shake(9f, 600, now)
                fx.flash(0xFFFFFFFF.toInt(), now, 260, 0.35f)
            }
            is GameEvent.SideFlipped -> {
                val moon = e.side == Side.MOON
                fx.flash(if (moon) 0xFF2E166B.toInt() else 0xFFFFE6A0.toInt(), now, 1100, 0.75f)
                fx.pop(if (moon) Strings.get(R.string.mania_fx_dark_side) else Strings.get(R.string.mania_fx_light_side), L.center.x, L.center.y, if (moon) 0xFFC0A6FF.toInt() else 0xFFFFE89A.toInt(), now + 150, 44f, 1400)
                fx.burst(L.center.x, L.center.y, 70, if (moon) intArrayOf(white, 0xFFC0A6FF.toInt(), 0xFF7FF5EC.toInt()) else intArrayOf(0xFFFFE89A.toInt(), 0xFFFFB000.toInt(), white), 520f, 4f, 0f, 1.4f, 2)
                fx.ring(L.center.x, L.center.y, if (moon) 0xFFC0A6FF.toInt() else 0xFFFFE89A.toInt(), now, L.height * 0.7f, 1100, 26f)
            }
            is GameEvent.ManiaCalled -> {
                val pos = if (e.playerId == humanId) L.humanSeat else seat(e.playerId)
                fx.ring(pos.x, pos.y, gold, now, L.width * 0.35f, 700, 10f)
                fx.pop("MANIA!", pos.x.coerceIn(L.width * 0.2f, L.width * 0.8f), pos.y - L.avatar * 0.3f, 0xFFFFE27A.toInt(), now, 40f, 1100, 0xFF8E0F3A.toInt())
                fx.burst(pos.x, pos.y, 30, intArrayOf(gold, 0xFFFFE89A.toInt(), white), 380f, 4f, 0f, 0.9f, 2)
                fx.shake(3f, 250, now)
            }
            is GameEvent.Caught -> {
                val pos = if (e.targetId == humanId) L.humanSeat else seat(e.targetId)
                fx.pop(Strings.get(R.string.mania_fx_caught), pos.x.coerceIn(L.width * 0.2f, L.width * 0.8f), pos.y, 0xFFFF5F7A.toInt(), now, 38f, 1100)
                fx.ring(pos.x, pos.y, 0xFFE0245E.toInt(), now, L.avatar * 1.4f, 600, 8f)
            }
            is GameEvent.ChallengeResult -> {
                val pos = seat(if (e.success) e.offenderId else e.challengerId).let { if ((if (e.success) e.offenderId else e.challengerId) == humanId) L.humanSeat else it }
                fx.pop(if (e.success) Strings.get(R.string.mania_fx_busted) else Strings.get(R.string.mania_fx_failed), L.center.x, L.pileY - L.pileCardW, if (e.success) 0xFF6BEFB0.toInt() else 0xFFFF5F7A.toInt(), now, 36f, 1100)
                fx.ring(pos.x, pos.y, 0xFFE0245E.toInt(), now, L.avatar * 1.3f, 600)
            }
            is GameEvent.StormStarted -> {
                val pos = seat(e.victimId).let { if (e.victimId == humanId) L.humanSeat else it }
                fx.lightning(pos.x, L.topInset, pos.x + 20 * d, pos.y, now + 120)
                fx.flash(0xFFE6F4FF.toInt(), now + 120, 300, 0.3f)
            }
            is GameEvent.ShieldArmed -> {
                val pos = if (e.playerId == humanId) L.humanSeat else seat(e.playerId)
                fx.ring(pos.x, pos.y, 0xFF19D3C5.toInt(), now, L.avatar, 600, 6f)
            }
            is GameEvent.ShieldBlocked -> {
                val pos = if (e.playerId == humanId) L.humanSeat else seat(e.playerId)
                fx.pop(Strings.get(R.string.mania_fx_shield), pos.x.coerceIn(L.width * 0.2f, L.width * 0.8f), pos.y, 0xFF7FF5EC.toInt(), now, 34f, 1000)
                fx.ring(pos.x, pos.y, 0xFF19D3C5.toInt(), now, L.avatar * 1.6f, 700, 10f)
            }
            is GameEvent.TokenEarned -> if (e.playerId == humanId) fx.sparkles(L.humanSeat.x, L.humanSeat.y, L.avatar, 16, 0xFF7FF5EC.toInt())
            is GameEvent.Reshuffled -> {
                fx.pop(Strings.get(R.string.mania_fx_reshuffle), L.drawPos.x, L.pileY - L.pileCardW * 0.95f, 0xFFB9E6FF.toInt(), now, 22f, 900)
            }
            is GameEvent.Passed -> {
                val pos = if (e.playerId == humanId) L.humanSeat else seat(e.playerId)
                fx.pop(Strings.get(R.string.mania_fx_pass), pos.x, pos.y, 0xFFB7A9DA.toInt(), now, 22f, 800)
            }
            is GameEvent.RoundEnded -> {
                val pos = if (e.winnerId == humanId) L.humanSeat else seat(e.winnerId)
                fx.burst(pos.x, pos.y, 50, fx.gemColors, 480f, 5f, 300f, 1.4f, 2)
                if (e.winnerId == humanId) fx.confetti(L.width, 160)
            }
            else -> Unit
        }
    }

    private fun colorOf(s: Suit): Int = if (s == Suit.WILD) 0xFFFFE89A.toInt() else Gems.color(s).toArgb()
    private fun lighten(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        fun l(v: Int) = (v + (255 - v) * 0.35f).toInt()
        return (0xFF shl 24) or (l(r) shl 16) or (l(g) shl 8) or l(b)
    }

    // ---------------------------------------------------------------- update & draw

    private val dead = ArrayList<Int>()
    private val drawList = ArrayList<Sprite>(256)
    private val drawOrder = Comparator<Sprite> { a, b ->
        val za = (if (a.flying) 10000f else 0f) + a.z + if (a.id == dragId) 20000f else 0f
        val zb = (if (b.flying) 10000f else 0f) + b.z + if (b.id == dragId) 20000f else 0f
        za.compareTo(zb)
    }
    private val shadowRect = RectF()
    private var chevron: Path? = null
    private val sheenShader = LinearGradient(-1f, 0f, 1f, 0f, intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    private val sheenMatrix = Matrix()

    // highlight flags on sprites go stale when selected/playable/jumpIn change or new sprites appear
    private var hlDirty = true
    // hint id for the frame being drawn (-1 = none), so drawSprite compares plain ints
    private var frameHint = -1
    // time of the last drawn frame: an animation counts as running until a frame has drawn its end
    private var drawnAt = 0L

    // cached overlay pieces (no allocation per frame)
    private var gemPath: Path? = null
    private var gemCol = 0
    private var gemShader: Shader? = null
    private var auraCol = 0
    private var auraShader: Shader? = null
    private var pileCount = -1
    private var pileCountText = ""
    private var stackCount = -1
    private var stackText = ""
    private val fm = Paint.FontMetrics()

    private fun refreshHighlights() {
        if (!hlDirty) return
        hlDirty = false
        for (sp in sprites.values) {
            sp.hlPlayable = sp.id in playable || sp.id in jumpIn
            sp.hlSelected = sp.id in selected
        }
    }

    // ---------------------------------------------------------------- frame pacing

    override fun isBusy(now: Long): Boolean {
        if (dragId != null) return true
        if (abs(sideTarget - sideMix) > 0.001f) return true
        if (fx.isActive(now) || fx.shakeX != 0f || fx.shakeY != 0f) return true
        for (sp in sprites.values) {
            // still moving (or waiting on its start delay), not yet drawn at its end, flipping, or waiting for removal
            if (sp.start + sp.delay + sp.dur > drawnAt) return true
            if (sp.prevLook != null || sp.removeAt > 0) return true
        }
        return false
    }

    /**
     * Time-based decorations in [draw] that can show while nothing moves: the direction ring's rotating chevrons
     * (drawn whenever a game is on the table), the draw-pile glow pulse (human's turn to draw), the pending-stack
     * badge pulse, the playable/jump-in/hint card glow pulse and the Prism sheen. All are smooth decorative motion,
     * so 30 fps. The chevrons alone keep this non-zero for as long as a state exists; the others need no extra check.
     */
    override fun idleFrameMs(now: Long): Long = if (state != null) DECOR_MS else 0L

    fun update(now: Long) {
        // side mix
        val dir = sideTarget - sideMix
        if (abs(dir) > 0.001f) sideMix += dir.coerceIn(-0.03f, 0.03f)

        dead.clear()
        for (sp in sprites.values) {
            if (sp.id == dragId) {
                val k = 0.45f
                val nx = sp.x + (dragX - sp.x) * k
                dragTilt = dragTilt * 0.8f + (nx - sp.x) * 0.25f
                sp.x = nx
                sp.y += (dragY - sp.y) * k
                sp.rot = dragTilt.coerceIn(-18f, 18f)
                sp.w += (layout.handCardW * 1.12f - sp.w) * 0.3f
                sp.flying = true
                continue
            }
            val el = now - sp.start - sp.delay
            if (el <= 0) {
                sp.flying = sp.delay > 0 && (sp.fx != sp.tx || sp.fy != sp.ty)
                continue
            }
            val t = (el.toFloat() / sp.dur).coerceIn(0f, 1f)
            val e = Fx.easeOutCubic(t)
            val dx = sp.tx - sp.fx
            val dy = sp.ty - sp.fy
            // quadratic bezier with perpendicular arc
            val cx = sp.fx + dx / 2 - dy * sp.arc
            val cy = sp.fy + dy / 2 + dx * sp.arc * 0.3f - hypot(dx, dy) * sp.arc * 0.6f
            val u = 1 - e
            sp.x = u * u * sp.fx + 2 * u * e * cx + e * e * sp.tx
            sp.y = u * u * sp.fy + 2 * u * e * cy + e * e * sp.ty
            sp.rot = sp.frot + (sp.trot - sp.frot) * e + sp.spin * (1 - e) * if (t < 1f) 1f else 0f
            sp.w = (sp.fw + (sp.tw - sp.fw) * e) * (1f + sp.pop * sin(PI.toFloat() * t))
            sp.flying = t < 1f && (abs(dx) > 1f || abs(dy) > 1f)
            if (sp.removeAt in 1..now) dead += sp.id
        }
        for (i in dead.indices) sprites.remove(dead[i])
    }

    fun draw(c: Canvas, now: Long) {
        val s = state
        ensureBitmaps()
        refreshHighlights()
        frameHint = hint ?: -1
        update(now)
        fx.updateShake(now)
        val sx = fx.shakeX
        val sy = fx.shakeY
        c.save()
        c.translate(sx, sy)
        drawTable(c, now, s)
        // sprites in z order; flying cards on top
        drawList.clear()
        for (sp in sprites.values) drawList.add(sp)
        drawList.sortWith(drawOrder)
        for (i in drawList.indices) drawSprite(c, drawList[i], now)
        if (s != null) drawOverlays(c, now, s)
        fx.draw(c, now, layout.width, layout.height)
        c.restore()
        drawnAt = now
    }

    // ---------------------------------------------------------------- table

    private fun ensureBitmaps() {
        if (shadowBmp == null) {
            val w = (layout.handCardW * 0.5f).toInt().coerceAtLeast(40)
            val h = (w * 1.4f).toInt()
            val pad = w / 5
            val b = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
            val cc = Canvas(b)
            val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xAA000000.toInt()
                maskFilter = BlurMaskFilter(pad * 0.8f, BlurMaskFilter.Blur.NORMAL)
            }
            cc.drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + w).toFloat(), (pad + h).toFloat()), w * 0.09f, w * 0.09f, pp)
            shadowBmp = b
            val g = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
            val gc = Canvas(g)
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD84A.toInt()
                maskFilter = BlurMaskFilter(pad * 0.9f, BlurMaskFilter.Blur.NORMAL)
            }
            gc.drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + w).toFloat(), (pad + h).toFloat()), w * 0.09f, w * 0.09f, gp)
            glowBmp = g
        }
        if (bgDay == null || bgTheme != theme) {
            bgTheme = theme
            bgDay = TableBackground.render(layout, theme, night = false)
            bgNight = TableBackground.render(layout, theme, night = true)
        }
    }

    private fun drawTable(c: Canvas, now: Long, s: GameState?) {
        val L = layout
        bgDay?.let { c.drawBitmap(it, 0f, 0f, null) }
        if (sideMix > 0.001f) bgNight?.let {
            p.alpha = (sideMix * 255).toInt()
            c.drawBitmap(it, 0f, 0f, p)
            p.alpha = 255
        }
        if (s == null) return
        val col = if (s.color == Suit.WILD) 0xFFFFE89A.toInt() else Gems.color(s.color).toArgb()

        // color aura around the piles
        val aura = auraShader?.takeIf { auraCol == col }
            ?: RadialGradient(0f, 0f, L.ringRx * 1.05f, Fx.withAlpha(col, 0.42f), Fx.withAlpha(col, 0f), Shader.TileMode.CLAMP)
                .also { auraShader = it; auraCol = col }
        c.save()
        c.translate(L.center.x, L.center.y)
        c.scale(1f, L.ringRy / L.ringRx * 1.25f)
        p.color = 0xFF000000.toInt()
        p.shader = aura
        c.drawCircle(0f, 0f, L.ringRx * 1.05f, p)
        p.shader = null
        c.restore()

        // direction ring
        rect.set(L.center.x - L.ringRx, L.center.y - L.ringRy, L.center.x + L.ringRx, L.center.y + L.ringRy)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f * L.density
        p.color = Fx.withAlpha(col, 0.18f)
        c.drawOval(rect, p)
        p.strokeWidth = 2.5f * L.density
        p.color = Fx.withAlpha(col, 0.75f)
        c.drawOval(rect, p)
        p.style = Paint.Style.FILL
        // moving chevrons (direction +1 = clockwise on screen)
        val n = 14
        val speed = 0.00012f * (if (reduceMotion) 0.4f else 1f)
        val phase = (now * speed * s.direction) % 1f
        for (i in 0 until n) {
            val tt = ((i.toFloat() / n + phase) % 1f + 1f) % 1f
            val a = tt * 2 * PI.toFloat()
            val x = L.center.x + cos(a) * L.ringRx
            val y = L.center.y + sin(a) * L.ringRy
            // tangent: derivative; for clockwise on screen with y-down, increasing angle moves clockwise
            val tx = -sin(a) * L.ringRx * s.direction
            val ty = cos(a) * L.ringRy * s.direction
            val ang = Math.toDegrees(kotlin.math.atan2(ty, tx).toDouble()).toFloat()
            c.save()
            c.translate(x, y)
            c.rotate(ang)
            val path = chevron ?: run {
                val sz = 7f * L.density
                Path().apply { moveTo(sz, 0f); lineTo(-sz * 0.7f, -sz * 0.75f); lineTo(-sz * 0.25f, 0f); lineTo(-sz * 0.7f, sz * 0.75f); close() }
                    .also { chevron = it }
            }
            p.color = Fx.withAlpha(0xFFFFFFFF.toInt(), 0.85f)
            c.drawPath(path, p)
            c.restore()
        }

        // draw pile depth
        val depth = min(s.drawPile.size, 7)
        val pw = L.pileCardW
        val ph = pw * 1.4f
        for (i in depth downTo 1) {
            val ox = L.drawPos.x - i * 1.2f * L.density + 1.2f * L.density
            val oy = L.drawPos.y - i * 1.6f * L.density + 1.6f * L.density
            rect.set(ox - pw / 2, oy - ph / 2, ox + pw / 2, oy + ph / 2)
            if (i == depth) shadowBmp?.let { sb ->
                val pad = pw * 0.25f
                shadowRect.set(rect.left - pad + 4 * L.density, rect.top - pad + 8 * L.density, rect.right + pad + 4 * L.density, rect.bottom + pad + 8 * L.density)
                c.drawBitmap(sb, null, shadowRect, shadowPaint)
            }
            p.color = 0xFF1A0B3D.toInt()
            c.drawRoundRect(rect, pw * 0.09f, pw * 0.09f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f * L.density
            p.color = 0xFFC98A00.toInt()
            c.drawRoundRect(rect, pw * 0.09f, pw * 0.09f, p)
            p.style = Paint.Style.FILL
        }
        // draw pile glow when it's the human's turn to draw
        if (interactive && playable.isEmpty() && s.actingPlayer == humanId && s.phase == com.ngdstudio.mindimania.engine.model.Phase.Play) {
            val pulse = 0.5f + 0.5f * sin(now / 220f)
            glowBmp?.let { g ->
                val gw = pw * 1.35f
                val gh = gw * 1.4f
                shadowPaint.alpha = (140 + 110 * pulse).toInt()
                shadowRect.set(L.drawPos.x - gw / 2, L.drawPos.y - gh / 2, L.drawPos.x + gw / 2, L.drawPos.y + gh / 2)
                c.drawBitmap(g, null, shadowRect, shadowPaint)
                shadowPaint.alpha = 255
            }
        }
    }

    private fun drawOverlays(c: Canvas, now: Long, s: GameState) {
        val L = layout
        val d = L.density
        // draw pile count
        text.textSize = 13f * d
        text.color = 0xCCFFFFFF.toInt()
        if (s.drawPile.size != pileCount) { pileCount = s.drawPile.size; pileCountText = pileCount.toString() }
        c.drawText(pileCountText, L.drawPos.x, L.drawPos.y + L.pileCardW * 0.7f + 18f * d, text)

        // current color gem next to the discard pile
        val gx = L.discardPos.x + L.pileCardW * 0.62f
        val gy = L.discardPos.y + L.pileCardW * 0.62f
        val r = 15f * d
        val col = if (s.color == Suit.WILD) 0xFF8B5CF6.toInt() else Gems.color(s.color).toArgb()
        p.color = 0x66000000
        c.drawCircle(gx, gy + 2 * d, r * 1.1f, p)
        val gem = gemPath ?: Path().apply { moveTo(gx, gy - r); lineTo(gx + r, gy); lineTo(gx, gy + r); lineTo(gx - r, gy); close() }
            .also { gemPath = it }
        p.color = 0xFF000000.toInt()
        p.shader = gemShader?.takeIf { gemCol == col }
            ?: LinearGradient(gx - r, gy - r, gx + r, gy + r, intArrayOf(lighten(col), col, darken(col)), null, Shader.TileMode.CLAMP)
                .also { gemShader = it; gemCol = col }
        c.drawPath(gem, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = 0xFFFFE89A.toInt()
        c.drawPath(gem, p)
        p.style = Paint.Style.FILL
        if (s.color != Suit.WILD && colorBlind) SuitSymbols.draw(c, s.color, gx, gy, r * 0.42f, 0xFFFFFFFF.toInt(), 0xAA000000.toInt())

        // pending stack badge
        if (s.pendingDraw > 0) {
            val pulse = 1f + 0.06f * sin(now / 120f)
            val bx = L.discardPos.x - L.pileCardW * 0.55f
            val by = L.discardPos.y - L.pileCardW * 0.75f
            val heat = when {
                s.pendingDraw >= 12 -> 0xFFFF3B3B.toInt()
                s.pendingDraw >= 6 -> 0xFFFF7A1A.toInt()
                else -> 0xFFFFB000.toInt()
            }
            c.save()
            c.translate(bx, by)
            c.scale(pulse, pulse)
            p.color = heat
            c.drawCircle(0f, 0f, 22f * d, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * d
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(0f, 0f, 22f * d, p)
            p.style = Paint.Style.FILL
            text.textSize = 17f * d
            text.color = 0xFFFFFFFF.toInt()
            text.getFontMetrics(fm)
            if (s.pendingDraw != stackCount) { stackCount = s.pendingDraw; stackText = "+$stackCount" }
            c.drawText(stackText, 0f, -(fm.ascent + fm.descent) / 2, text)
            c.restore()
        }
    }

    private fun darken(c: Int): Int {
        val r = ((c shr 16) and 0xFF) * 6 / 10
        val g = ((c shr 8) and 0xFF) * 6 / 10
        val b = (c and 0xFF) * 6 / 10
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun bitmapFor(look: Look): Bitmap = when (look) {
        is Look.Back -> CardArt.back(back).asAndroidBitmap()
        is Look.Front -> CardArt.face(look.face, colorBlind).asAndroidBitmap()
    }

    private fun drawSprite(c: Canvas, sp: Sprite, now: Long) {
        val w = sp.w
        val h = w * 1.4f
        // flip progress
        var look = sp.look
        var angle = 0f
        val prevLook = sp.prevLook
        if (prevLook != null) {
            val ft = (now - sp.flipStart).toFloat() / sp.flipDur
            when {
                ft <= 0f -> look = prevLook
                ft >= 1f -> sp.prevLook = null
                ft < 0.5f -> { look = prevLook; angle = ft * 180f }
                else -> angle = ft * 180f - 180f
            }
        }
        val bmp = bitmapFor(look)
        c.save()
        c.translate(sp.x, sp.y)
        c.rotate(sp.rot)
        if (angle != 0f) {
            camera.save()
            camera.rotateY(angle)
            camera.getMatrix(matrix)
            camera.restore()
            c.concat(matrix)
        }
        // shadow (bigger and softer while flying)
        val lift = if (sp.flying) 1.8f else 1f
        shadowBmp?.let { sb ->
            val pad = w * 0.22f
            val ox = 2.5f * layout.density * lift
            val oy = 5f * layout.density * lift
            rect.set(-w / 2 - pad + ox, -h / 2 - pad + oy, w / 2 + pad + ox, h / 2 + pad + oy)
            shadowPaint.alpha = if (sp.flying) 150 else 200
            c.drawBitmap(sb, null, rect, shadowPaint)
            shadowPaint.alpha = 255
        }
        val inHand = sp.zone == Zone.HAND
        val isPlayable = inHand && sp.hlPlayable
        val isSelected = inHand && sp.hlSelected
        if (inHand && interactive && (isPlayable || isSelected)) {
            glowBmp?.let { g ->
                val pulse = 0.5f + 0.5f * sin(now / 260f + sp.id)
                val k = if (isSelected || sp.id == frameHint) 1.3f else 1.16f
                val gw = w * k
                val gh = gw * 1.4f
                rect.set(-gw / 2, -gh / 2, gw / 2, gh / 2)
                shadowPaint.alpha = when {
                    isSelected -> 255
                    sp.id == frameHint -> (180 + 75 * pulse).toInt()
                    else -> (90 + 90 * pulse).toInt()
                }
                c.drawBitmap(g, null, rect, shadowPaint)
                shadowPaint.alpha = 255
            }
        }
        rect.set(-w / 2, -h / 2, w / 2, h / 2)
        val dim = inHand && interactive && !isPlayable && !isSelected && (playable.isNotEmpty() || jumpIn.isNotEmpty())
        c.drawBitmap(bmp, null, rect, if (dim) dimPaint else cardPaint)
        // holographic sheen on Prism cards in hand / on top of pile
        if (look is Look.Front && look.face.isWild && (inHand || sp.zone == Zone.DISCARD) && !reduceMotion) {
            val period = 2600L
            val t = ((now + sp.id * 137) % period) / period.toFloat()
            val bandX = -w + t * w * 3f
            c.save()
            c.clipRect(rect)
            sheenMatrix.reset()
            sheenMatrix.setScale(w * 0.25f, 1f)
            sheenMatrix.postRotate(25f)
            sheenMatrix.postTranslate(bandX, 0f)
            sheenShader.setLocalMatrix(sheenMatrix)
            p.color = 0xFF000000.toInt()
            p.shader = sheenShader
            c.drawRect(rect, p)
            p.shader = null
            c.restore()
        }
        // flip shading
        if (angle != 0f) {
            p.color = Fx.withAlpha(0xFF000000.toInt(), abs(angle) / 90f * 0.35f)
            c.drawRoundRect(rect, w * 0.09f, w * 0.09f, p)
        }
        c.restore()
    }

    // ---------------------------------------------------------------- hit testing

    fun hitHand(x: Float, y: Float): Int? {
        val hand = sprites.values.filter { it.zone == Zone.HAND && it.removeAt < 0 }.sortedByDescending { it.z }
        for (sp in hand) {
            val dx = x - sp.tx
            val dy = y - sp.ty
            val a = Math.toRadians(-sp.trot.toDouble())
            val lx = (dx * cos(a) - dy * sin(a)).toFloat()
            val ly = (dx * sin(a) + dy * cos(a)).toFloat()
            if (abs(lx) <= sp.tw / 2 && abs(ly) <= sp.tw * 0.7f + 6f) return sp.id
        }
        return null
    }

    fun hitDraw(x: Float, y: Float): Boolean {
        val w = layout.pileCardW * 0.6f
        val h = w * 1.4f
        return abs(x - layout.drawPos.x) <= w && abs(y - layout.drawPos.y) <= h
    }

    fun spriteOf(id: Int): Sprite? = sprites[id]

    /** After a drag ends, animate the card from where it was dropped back to (or toward) its target. */
    fun release(id: Int) {
        val sp = sprites[id] ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val tx = sp.tx; val ty = sp.ty; val tr = sp.trot; val tw = sp.tw
        sp.tx = Float.NaN
        retarget(sp, tx, ty, tr, tw, TweenSpec((260 * speedFactor).toLong(), 0, 0f, 0f, 0f), now)
    }

    private companion object {
        /** Frame interval for slow decorations while the table is otherwise still (30 fps). */
        const val DECOR_MS = 33L
    }

    @Suppress("unused")
    private fun sweep(cx: Float, cy: Float) = SweepGradient(cx, cy, intArrayOf(0, 0), null)
}
