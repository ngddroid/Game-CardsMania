package com.ngdstudio.mindimania.ui.mindi

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry of the Mindi table (px), portrait. The local player [me] sits at the bottom; the other seats are spread
 * clockwise from the left, over the top, to the right (partner opposite in 4-player games).
 */
class MindiLayout(
    val width: Float,
    val height: Float,
    val density: Float,
    val players: Int,
    val me: Int,
    val topInset: Float,
    val bottomInset: Float,
) {
    val hudH = 52f * density
    val trackerTop = topInset + hudH
    val trackerH = 84f * density
    val tableTop = trackerTop + trackerH + 8f * density
    val handCardW = min(width * 0.158f, 84f * density)
    val handCardH = handCardW * 1.4f
    val trickCardW = min(width * 0.165f, 84f * density)
    val trickCardH = trickCardW * 1.4f
    val actionBarH = 52f * density
    val handBaseY = height - bottomInset - handCardH * 0.5f - 10f * density
    val handTop = handBaseY - handCardH * 0.5f
    val actionY = handTop - 22f * density - actionBarH / 2
    val avatar = (if (players >= 8) 44f else if (players >= 6) 48f else 54f) * density
    val tableBottom = actionY - actionBarH / 2 - 6f * density
    val center = Offset(width / 2, (tableTop + tableBottom) / 2 + 10f * density)
    val mySeat = Offset(width * 0.11f, actionY)

    /** Avatar centre of [seat]. */
    fun seatOf(seat: Int): Offset {
        val rel = ((seat - me) % players + players) % players
        if (rel == 0) return mySeat
        val k = players - 1
        val t = (rel - 1).toFloat() / (k - 1).coerceAtLeast(1)
        val angle = Math.toRadians(180.0 + 180.0 * t)
        // keep the avatar AND its name label on screen
        val rx = width / 2 - maxOf(avatar * 0.75f, 52f * density)
        val ry = center.y - tableTop - avatar * 0.55f
        val x = center.x + (cos(angle) * rx).toFloat()
        // side seats sit a little lower so the top doesn't get crowded
        val y = center.y + (sin(angle) * ry).toFloat() + (if (abs(cos(angle)) > 0.9) ry * 0.18f else 0f)
        return Offset(x, y)
    }

    /** Where [seat]'s card lands in the trick: nudged from the centre toward that player. */
    fun trickPos(seat: Int): Offset {
        val rel = ((seat - me) % players + players) % players
        val target = if (rel == 0) Offset(center.x, center.y + 400f) else seatOf(seat)
        val dx = target.x - center.x
        val dy = target.y - center.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val r = if (players == 4) trickCardW * 0.62f else trickCardW * 0.78f
        return Offset(center.x + dx / len * r, center.y + dy / len * r * 0.85f)
    }

    fun trickRotation(seat: Int): Float {
        val rel = ((seat - me) % players + players) % players
        return ((rel * 37) % 17 - 8).toFloat()
    }

    /** Collected tricks fly to the team panel in the tracker (team 0 left, team 1 right, from the local team's view). */
    fun teamPile(team: Int, myTeam: Int): Offset {
        val left = team == myTeam
        return Offset(if (left) width * 0.2f else width * 0.8f, trackerTop + trackerH * 0.55f)
    }

    val trumpSlot: Offset get() = Offset(width / 2, trackerTop + trackerH * 0.5f)
    val deckPos: Offset get() = center

    data class Slot(val x: Float, val y: Float, val rot: Float, val z: Float, val w: Float)

    /**
     * My hand as an arc fan: each card is overlapped by the next one, so the spacing is chosen to keep the top-left
     * rank + suit index (about 46% of a card's width) visible. Long hands (double deck) use two rows.
     */
    fun handSlots(n: Int): List<Slot> {
        if (n == 0) return emptyList()
        val rows = if (n > 13) 2 else 1
        val per = (n + rows - 1) / rows
        val out = ArrayList<Slot>(n)
        for (r in 0 until rows) {
            val count = if (r == rows - 1) n - per * (rows - 1) else per
            // shrink cards for long hands so at least half of each card (its rank corner) stays visible
            val cw = if (count <= 1) handCardW else min(handCardW, width * 0.97f / (1f + 0.52f * (count - 1)))
            val maxSpan = width * 0.97f - cw
            val spacing = if (count <= 1) 0f else min(cw * 0.62f, maxSpan / (count - 1))
            val span = spacing * (count - 1)
            val y0 = if (rows == 2 && r == 0) handBaseY - handCardH * 0.42f else handBaseY
            val step = if (count <= 1) 0f else min(3.2f, 30f / count)
            for (i in 0 until count) {
                val t = i - (count - 1) / 2f
                val norm = if (count <= 1) 0f else t / ((count - 1) / 2f)
                out += Slot(width / 2 - span / 2 + spacing * i, y0 + (handCardH - cw * 1.4f) / 2 + norm * norm * handCardH * 0.08f, t * step, 500f + r * 100 + i, cw)
            }
        }
        return out
    }
}
