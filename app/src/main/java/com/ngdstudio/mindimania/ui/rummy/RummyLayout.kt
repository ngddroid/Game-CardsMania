package com.ngdstudio.mindimania.ui.rummy

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/**
 * Geometry of the Rummy table (px), landscape. My cards run along the bottom in groups (a coloured bar under each);
 * my name plate sits on the table's bottom edge; the oval table above holds the opponents around its rim, the closed
 * and open piles in the middle with the cut joker under the closed pile, and the finish slot on the left.
 */
class RummyLayout(
    val width: Float,
    val height: Float,
    val density: Float,
    val players: Int,
    val me: Int,
    val topInset: Float,
    val bottomInset: Float,
    val leftInset: Float,
    val rightInset: Float,
) {
    private val d = density
    val side = maxOf(leftInset, rightInset) + 10f * d

    // ---- my hand along the bottom
    val labelH = 22f * d
    val maxCardW = min(height * 0.2f, 78f * d)
    val handBottom = height - bottomInset - 4f * d
    val handLeft = side
    val handRight = width - side

    // ---- the table (above the hand, with my plate on its bottom edge)
    val plateH = 50f * d
    val plateW = min(300f * d, width * 0.34f)
    /** My plate's centre: just above my cards. */
    val plateCenter: Offset get() = Offset(width / 2, handTop(maxCardW) - plateH / 2 - 8f * d)
    val ovalLeft = width * 0.17f
    val ovalRight = width * 0.83f
    val ovalTop = topInset + 8f * d
    val ovalBottom: Float get() = plateCenter.y + 4f * d
    private val naturalY: Float get() = (ovalTop + ovalBottom) / 2 - 6f * d

    /** Opponents. Two sit at the table's sides; otherwise some sit in a row along its top edge. */
    private val k = players - 1
    val hasTopRow: Boolean = k != 2
    private val topCount: Int = when (k) { 1, 3 -> 1; 2 -> 0; 4 -> 2; else -> 3 }

    val avatar = 50f * d
    /** Avatars in the top row are smaller, with the name beside them (the table is short). */
    val topAvatar = 42f * d
    val topRowY: Float get() = ovalTop + topAvatar / 2 + 1f * d
    private val topRowBottom: Float get() = ovalTop + topAvatar + 2f * d

    val pileW = min(height * (if (hasTopRow) 0.135f else 0.15f), (if (hasTopRow) 56f else 60f) * d)
    val pileH = pileW * 1.4f
    /** Middle of the piles: lower when the top row needs the room. */
    val center: Offset get() = Offset(width / 2, if (hasTopRow) maxOf(naturalY, topRowBottom + 6f * d + pileH / 2) else naturalY)
    val closedPos: Offset get() = Offset(center.x + pileW * 0.45f, center.y)
    val openPos: Offset get() = Offset(center.x - pileW * 0.75f, center.y)
    val finishPos: Offset get() = Offset(ovalLeft + (center.x - ovalLeft) * 0.42f, center.y)

    fun handTop(cardW: Float) = handBottom - labelH - 2f * d - cardW * 1.4f

    /** Bottom of the Grouping / Sort row on the left: just above my cards (level with the action buttons on the right). */
    val controlsBottom: Float get() = handTop(maxCardW) - 8f * d

    /** Side seats: level with the piles (the controls sit lower, beside my plate). */
    val sideY: Float get() = naturalY

    fun rel(seat: Int) = ((seat - me) % players + players) % players

    /** Whether [seat] sits in the top row (smaller avatar, name to its right). */
    fun isTopRow(seat: Int): Boolean {
        val r = rel(seat)
        return r != 0 && (k == 1 || (k >= 3 && r in 2 until k))
    }

    /** The widest a top-row name may be, so the row fits between the corner buttons (and the side seats). */
    val topNameMax: Float get() {
        if (topCount == 0) return 80f * d
        val (lo, hi) = topRange
        return ((hi - lo) / topCount - 8f * d - 86f * d).coerceIn(40f * d, 80f * d)
    }

    private val topRange: Pair<Float, Float> get() {
        var lo = side + 190f * d // clear of the pause button and the title
        var hi = width - side - 100f * d // clear of the emote and rules buttons
        if (k >= 2 && sideY - avatar / 2 < topRowBottom + 2f * d) {
            lo = maxOf(lo, ovalLeft + 48f * d)
            hi = minOf(hi, ovalRight - 48f * d)
        }
        val cx = width / 2
        val half = minOf(cx - lo, hi - cx)
        return (cx - half) to (cx + half)
    }

    /** Avatar centre of the [i]-th top-row seat: each seat (cards, avatar, name) gets an equal slot of the row. */
    private fun topAt(i: Int): Offset {
        val (lo, hi) = topRange
        val slot = (hi - lo) / topCount
        val elemW = topNameMax + 86f * d
        val mid = lo + slot * (i + 0.5f)
        return Offset(mid - elemW / 2 + 35f * d, topRowY)
    }

    /** Avatar centre of an opponent: from my left, over the top, to my right. */
    fun seatOf(seat: Int): Offset {
        val r = rel(seat)
        if (r == 0) return plateCenter
        val leftSide = Offset(maxOf(ovalLeft - 4f * d, side + 50f * d), sideY)
        val rightSide = Offset(minOf(ovalRight + 4f * d, width - side - 50f * d), sideY)
        return when {
            k == 1 -> topAt(0)
            k == 2 -> if (r == 1) leftSide else rightSide
            r == 1 -> leftSide
            r == k -> rightSide
            else -> topAt(r - 2)
        }
    }

    /** Where my hand's cards go: centre x of each card, the card width, and each group's span (for its bar). */
    data class HandGeo(val cardW: Float, val top: Float, val x: Map<Int, Float>, val spans: List<Pair<Float, Float>>)

    fun handGeo(groups: List<List<Int>>): HandGeo {
        val n = groups.sumOf { it.size }
        val g = groups.size.coerceAtLeast(1)
        val step = 0.6f
        val gap = 0.22f
        val units = (n - g) * step + g + (g - 1) * gap
        val avail = handRight - handLeft
        val w = min(maxCardW, avail / units.coerceAtLeast(1f))
        val total = units * w
        var x = handLeft + (avail - total) / 2
        val xs = HashMap<Int, Float>()
        val spans = ArrayList<Pair<Float, Float>>()
        for (gr in groups) {
            val start = x
            gr.forEachIndexed { i, id ->
                xs[id] = x + w / 2
                if (i < gr.lastIndex) x += w * step
            }
            x += w
            spans += start to x
            x += w * gap
        }
        return HandGeo(w, handTop(w), xs, spans)
    }
}
