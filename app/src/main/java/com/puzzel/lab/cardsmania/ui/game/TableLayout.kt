package com.puzzel.lab.cardsmania.ui.game

import androidx.compose.ui.geometry.Offset
import com.puzzel.lab.cardsmania.data.SortMode
import com.puzzel.lab.cardsmania.engine.model.Card
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Side
import com.puzzel.lab.cardsmania.engine.model.Suit
import kotlin.math.abs
import kotlin.math.min

/** What a sprite shows: a card back, or a specific face. */
sealed interface Look {
    data object Back : Look
    data class Front(val face: Face) : Look
}

enum class Zone { HAND, OPP, DISCARD, DRAW }

data class Target(
    val x: Float,
    val y: Float,
    val rot: Float,
    val w: Float,
    val look: Look,
    val z: Float,
    val zone: Zone,
    val owner: Int = -1,
    val index: Int = 0,
)

/** Geometry of the table for a given screen size (all values in px). */
class TableLayout(
    val width: Float,
    val height: Float,
    val density: Float,
    val players: Int,
    val topInset: Float,
    val bottomInset: Float,
    largeCards: Boolean,
    /** The player sitting at the bottom of this screen (multiplayer guests are not seat 0). */
    val localId: Int = 0,
) {
    val handCardW = width * (if (largeCards) 0.235f else 0.205f)
    val handCardH = handCardW * 1.4f
    val pileCardW = width * (if (players >= 5) 0.19f else 0.205f)
    val oppCardW = width * 0.082f
    val avatar = 54f * density
    val pileY = topInset + (height - topInset - bottomInset) * 0.47f
    val drawPos = Offset(width * 0.355f, pileY)
    val discardPos = Offset(width * 0.645f, pileY)
    val center = Offset(width * 0.5f, pileY)
    val ringRx = width * 0.43f
    val ringRy = handCardH * 0.95f
    val handBaseY = height - bottomInset - handCardH * 0.42f - 10f * density
    val handTopY = handBaseY - handCardH * 0.75f
    val humanSeat = Offset(width * 0.11f, handTopY - 52f * density)

    /** Opponent avatar centers, clockwise from the human's left. */
    val seats: List<Offset> = run {
        val top = topInset + 64f * density + avatar * 0.5f
        val k = players - 1
        val playH = pileY - top
        fun p(fx: Float, fy: Float) = Offset(width * fx, top + playH * fy)
        when (k) {
            1 -> listOf(p(0.5f, 0f))
            2 -> listOf(p(0.24f, 0.02f), p(0.76f, 0.02f))
            3 -> listOf(p(0.12f, 0.44f), p(0.5f, 0f), p(0.88f, 0.44f))
            4 -> listOf(p(0.12f, 0.46f), p(0.3f, 0f), p(0.7f, 0f), p(0.88f, 0.46f))
            else -> listOf(p(0.11f, 0.5f), p(0.2f, 0.02f), p(0.5f, -0.03f), p(0.8f, 0.02f), p(0.89f, 0.5f))
        }
    }

    /** Seats are drawn clockwise starting from the local player. */
    fun seatOf(playerId: Int): Offset {
        val rel = ((playerId - localId) % players + players) % players
        return if (rel == 0) humanSeat else seats.getOrElse(rel - 1) { center }
    }

    /** Where an opponent's fanned hand sits: below the avatar and its name label. */
    private fun fanCenter(seat: Offset): Offset {
        val oppH = oppCardW * 1.4f
        val y = seat.y + avatar * 0.5f + 22f * density + oppH * 0.5f
        val x = when {
            seat.x < width * 0.25f -> maxOf(seat.x, width * 0.02f + width * 0.065f + oppCardW / 2)
            seat.x > width * 0.75f -> minOf(seat.x, width * 0.98f - width * 0.065f - oppCardW / 2)
            else -> seat.x
        }
        return Offset(x, y)
    }

    fun handOrder(hand: List<Card>, side: Side, sort: SortMode): List<Card> = when (sort) {
        SortMode.NONE -> hand
        SortMode.COLOR -> hand.sortedWith(compareBy<Card>({ suitOrder(it.face(side).suit) }, { kindOrder(it.face(side)) }, { it.face(side).value }, { it.id }))
        SortMode.VALUE -> hand.sortedWith(compareBy<Card>({ kindOrder(it.face(side)) }, { it.face(side).value }, { suitOrder(it.face(side).suit) }, { it.id }))
    }

    private fun suitOrder(s: Suit) = if (s == Suit.WILD) 99 else s.ordinal
    private fun kindOrder(f: Face) = when {
        f.kind == Kind.NUMBER -> 0
        f.isWild -> 2
        else -> 1
    }

    data class HandSlot(val x: Float, val y: Float, val rot: Float, val w: Float, val z: Float)

    fun handSlots(n: Int): List<HandSlot> {
        if (n == 0) return emptyList()
        val rows = if (n > 13) 2 else 1
        val result = ArrayList<HandSlot>(n)
        val perRow = (n + rows - 1) / rows
        var idx = 0
        for (r in 0 until rows) {
            val count = if (r == rows - 1) n - perRow * (rows - 1) else perRow
            val w = if (rows == 2 && r == 0) handCardW * 0.86f else handCardW
            val maxSpan = width * 0.94f - w
            val spacing = if (count <= 1) 0f else min(w * 0.64f, maxSpan / (count - 1))
            val span = spacing * (count - 1)
            val y0 = if (rows == 2 && r == 0) handBaseY - handCardH * 0.36f else handBaseY
            val stepAngle = if (count <= 1) 0f else min(4.5f, 46f / count)
            for (i in 0 until count) {
                val t = i - (count - 1) / 2f
                val x = width / 2 - span / 2 + spacing * i
                val norm = if (count <= 1) 0f else t / ((count - 1) / 2f)
                val y = y0 + abs(norm) * abs(norm) * handCardH * 0.07f
                result += HandSlot(x, y, t * stepAngle, w, 600f + r * 100 + i)
                idx++
            }
        }
        return result
    }

    fun discardJitter(id: Int): Triple<Float, Float, Float> {
        var h = id * 2654435761L
        h = h xor (h ushr 13)
        val a = ((h and 0xFF) / 255f - 0.5f)
        val b = (((h ushr 8) and 0xFF) / 255f - 0.5f)
        val r = (((h ushr 16) and 0xFF) / 255f - 0.5f)
        return Triple(a * width * 0.035f, b * width * 0.03f, r * 28f)
    }

    /**
     * Target transforms for every visible card in [s].
     * [lift] = ids raised (selected), [humanSort] ordering mode.
     */
    fun targets(s: GameState, selected: List<Int>, sort: SortMode, playable: Set<Int>, human: Int = 0): Map<Int, Target> {
        val out = HashMap<Int, Target>(128)
        val eclipse = s.mode == GameMode.ECLIPSE
        fun hiddenLook(c: Card): Look = if (eclipse) Look.Front(c.face(s.side.other)) else Look.Back

        // draw pile: only the top 2 cards are sprites
        val dp = s.drawPile
        for (i in maxOf(0, dp.size - 2) until dp.size) {
            val depth = minOf(dp.size, 6)
            val c = dp[i]
            out[c.id] = Target(drawPos.x - depth * 1.2f * density, drawPos.y - depth * 1.6f * density, 0f, pileCardW, hiddenLook(c), 100f + i * 0.01f, Zone.DRAW)
        }
        // discard: top 6
        val ds = s.discard
        for (i in maxOf(0, ds.size - 6) until ds.size) {
            val c = ds[i]
            val (jx, jy, jr) = discardJitter(c.id)
            val isTop = i == ds.size - 1
            out[c.id] = Target(discardPos.x + jx, discardPos.y + jy, jr, pileCardW * (if (isTop) 1.04f else 1f), Look.Front(c.face(s.side)), 200f + i * 0.01f, Zone.DISCARD)
        }
        // opponents
        for (p in s.players) {
            if (p.id == human) continue
            val seat = seatOf(p.id)
            val fc = fanCenter(seat)
            val n = p.hand.size
            val sideSeat = seat.x < width * 0.25f || seat.x > width * 0.75f
            val maxSpan = if (sideSeat) width * 0.13f else width * 0.2f
            val spacing = if (n <= 1) 0f else min(oppCardW * 0.42f, maxSpan / (n - 1))
            val span = spacing * (n - 1)
            val step = if (n <= 1) 0f else min(6f, 40f / n)
            p.hand.forEachIndexed { i, c ->
                val t = i - (n - 1) / 2f
                out[c.id] = Target(fc.x - span / 2 + spacing * i, fc.y + abs(t) * 0.6f * density, t * step, oppCardW, hiddenLook(c), 300f + p.id * 30 + i * 0.1f, Zone.OPP, p.id, i)
            }
        }
        // human
        val me = s.players[human]
        val ordered = handOrder(me.hand, s.side, sort)
        val slots = handSlots(ordered.size)
        ordered.forEachIndexed { i, c ->
            val sl = slots[i]
            val sel = c.id in selected
            val lift = when {
                sel -> handCardH * 0.2f
                c.id in playable -> handCardH * 0.06f
                else -> 0f
            }
            out[c.id] = Target(sl.x, sl.y - lift, if (sel) sl.rot * 0.4f else sl.rot, sl.w * (if (sel) 1.06f else 1f), Look.Front(c.face(s.side)), sl.z + if (sel) 50f else 0f, Zone.HAND, human, i)
        }
        return out
    }

    /** Rough zone position of a card that isn't currently a visible sprite. */
    fun zonePos(s: GameState, id: Int): Offset? {
        if (s.drawPile.any { it.id == id }) return drawPos
        if (s.discard.any { it.id == id }) return discardPos
        for (p in s.players) if (p.hand.any { it.id == id }) return if (p.id == localId) Offset(width / 2, handBaseY) else seatOf(p.id)
        return null
    }
}
