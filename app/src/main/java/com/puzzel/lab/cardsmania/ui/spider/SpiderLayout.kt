package com.puzzel.lab.cardsmania.ui.spider

import androidx.compose.ui.geometry.Offset
import com.puzzel.lab.cardsmania.spider.SpiderEngine
import com.puzzel.lab.cardsmania.spider.SpiderState
import kotlin.math.floor

enum class SZone { COLUMN, STOCK, FOUNDATION }

data class SpiderTarget(val x: Float, val y: Float, val up: Boolean, val z: Float, val zone: SZone, val col: Int = -1, val index: Int = -1)

/**
 * Geometry of the Spider table (px). Cards are positioned by their CENTER. Pure Kotlin (unit-testable).
 *
 * Top: completed-run slots and the stock (mirrored when [leftHanded]). Middle: 10 columns. Bottom: the button bar.
 * Face-up cards spread out to use the full height (up to [MAX_UP] of a card visible) and compress only when a
 * column gets too long, so touch targets stay as big as possible.
 */
class SpiderLayout(
    val width: Float,
    val height: Float,
    val density: Float,
    topInset: Float,
    bottomInset: Float,
    val leftHanded: Boolean = false,
) {
    companion object {
        // 0.48 (was 0.45) keeps the visible strip of a face-up card ~21dp now that the side margins make cards a bit narrower
        const val MAX_UP = 0.48f
        const val MIN_UP = 0.28f
        const val DOWN = 0.13f
        /** How far (dp) above a column's first face-up card a touch still counts as that card. */
        const val FACE_UP_SNAP_DP = 14f
    }

    /**
     * Space between the table and the screen edges. 10dp keeps the cards off the rounded screen corners and leaves room
     * for the stock's deal-count badge, which pokes ~9dp past the card's outer edge.
     */
    val margin = 10f * density
    val gap = 3f * density
    val pitch: Float get() = cardW + gap
    val cardW = (width - 2 * margin - 9 * gap) / 10f
    val cardH = cardW * 1.4f
    val topBarH = 64f * density
    val bottomBarH = 76f * density
    val rowTop = topInset + topBarH + 6f * density
    val rowCenterY = rowTop + cardH / 2
    val tableauTop = rowTop + cardH + 14f * density
    val tableauBottom = height - bottomInset - bottomBarH - 8f * density
    val downGap = cardH * DOWN
    /** The largest face-up gap; the actual gap depends on the state (see [upGap]). */
    val maxUpGap = cardH * MAX_UP
    val minUpGap = cardH * MIN_UP
    val foundationStep = cardW * 0.46f
    val stockStep = cardW * 0.24f
    private val avail: Float get() = tableauBottom - tableauTop - cardH

    fun columnX(c: Int): Float = margin + c * pitch + cardW / 2

    /** Center of completed-run slot [i] (0..7), fanned away from the screen edge. */
    fun foundation(i: Int): Offset = if (!leftHanded) {
        Offset(margin + cardW / 2 + i * foundationStep, rowCenterY)
    } else {
        Offset(width - margin - cardW / 2 - i * foundationStep, rowCenterY)
    }

    /** Center of deal group [g] (the last group is dealt next and drawn outermost, on top). */
    fun stock(g: Int, groups: Int): Offset {
        val k = (groups - 1 - g).coerceAtLeast(0) * stockStep
        return if (!leftHanded) Offset(width - margin - cardW / 2 - k, rowCenterY)
        else Offset(margin + cardW / 2 + k, rowCenterY)
    }

    /** The point to tap to deal. */
    val stockTap: Offset get() = stock(0, 1)

    fun isStockHit(x: Float, y: Float): Boolean {
        if (y < rowTop - 10f * density || y > rowTop + cardH + 10f * density) return false
        val reach = cardW + 4 * stockStep + 10f * density
        return if (!leftHanded) x >= width - margin - reach else x <= margin + reach
    }

    // ------------------------------------------------------------------ smart spacing

    private var cacheState: SpiderState? = null
    private var cacheUp = 0f
    private var cacheTops: Array<FloatArray> = emptyArray()
    private var cacheScale = FloatArray(10)

    private fun ensure(s: SpiderState) {
        if (cacheState === s) return
        // 1) one face-up gap for the whole table: as big as the tallest column allows
        var up = maxUpGap
        for (col in s.columns) {
            if (col.size <= 1) continue
            val downs = col.dropLast(1).count { !it.up }
            val ups = col.size - 1 - downs
            if (ups > 0) up = minOf(up, (avail - downs * downGap) / ups)
        }
        up = up.coerceIn(minUpGap, maxUpGap)
        // 2) per column: compress further only if it still doesn't fit
        val tops = Array(s.columns.size) { c ->
            val col = s.columns[c]
            val downs = if (col.isEmpty()) 0 else col.dropLast(1).count { !it.up }
            val ups = if (col.isEmpty()) 0 else col.size - 1 - downs
            val need = downs * downGap + ups * up
            val k = if (need > avail && need > 0f) avail / need else 1f
            cacheScale[c] = k
            val arr = FloatArray(col.size)
            var y = tableauTop
            for (i in col.indices) {
                arr[i] = y
                y += (if (col[i].up) up else downGap) * k
            }
            arr
        }
        cacheUp = up
        cacheTops = tops
        cacheState = s
    }

    /** The face-up gap actually used in column [col] for state [s]. */
    fun upGap(s: SpiderState, col: Int): Float {
        ensure(s)
        return cacheUp * cacheScale[col]
    }

    /** Top-left Y of card [index] in column [col]. */
    fun cardTop(s: SpiderState, col: Int, index: Int): Float {
        ensure(s)
        return cacheTops[col].getOrElse(index) { tableauTop }
    }

    /** Y where a card added to column [col] would land (top edge). */
    fun nextTop(s: SpiderState, col: Int): Float {
        val cards = s.columns[col]
        if (cards.isEmpty()) return tableauTop
        return cardTop(s, col, cards.lastIndex) + upGap(s, col)
    }

    /** Bottom Y of column [col] (bottom edge of its last card, or of the empty slot). */
    fun columnBottom(s: SpiderState, col: Int): Float {
        val cards = s.columns[col]
        return if (cards.isEmpty()) tableauTop + cardH else cardTop(s, col, cards.lastIndex) + cardH
    }

    fun targets(s: SpiderState): Map<Int, SpiderTarget> {
        val out = HashMap<Int, SpiderTarget>(128)
        // stock: groups of 10, the last group (top of stock) is dealt next
        val groups = (s.stock.size + 9) / 10
        s.stock.forEachIndexed { i, card ->
            val g = i / 10
            val p = stock(g, groups)
            out[card.id] = SpiderTarget(p.x, p.y, false, 100f + i * 0.01f, SZone.STOCK)
        }
        // completed runs: K on top, so draw A first
        s.completed.forEachIndexed { r, run ->
            val p = foundation(r)
            run.forEachIndexed { i, card ->
                out[card.id] = SpiderTarget(p.x, p.y, true, 50f + r + (12 - i) * 0.01f, SZone.FOUNDATION)
            }
        }
        for (c in 0 until SpiderEngine.COLUMNS) {
            val x = columnX(c)
            s.columns[c].forEachIndexed { i, card ->
                val top = cardTop(s, c, i)
                out[card.id] = SpiderTarget(x, top + cardH / 2, card.up, 300f + i, SZone.COLUMN, c, i)
            }
        }
        return out
    }

    // ------------------------------------------------------------------ hit testing (forgiving)

    /** Column under x. The hit areas span the gaps, so every x on screen maps to a column. */
    fun columnAt(x: Float): Int {
        if (x < 0f || x > width) return -1
        val c = floor((x - margin + gap / 2) / pitch).toInt()
        return c.coerceIn(0, SpiderEngine.COLUMNS - 1)
    }

    /** Is y inside the tableau (where columns can be tapped)? */
    fun inTableau(y: Float): Boolean = y >= tableauTop - 8f * density && y <= tableauBottom + 4f * density

    /** The card index of column [col] under y (topmost visible card wins). Empty space below a column = its last card. */
    fun cardAt(s: SpiderState, col: Int, y: Float): Int {
        val cards = s.columns[col]
        if (cards.isEmpty() || y < tableauTop - 8f * density) return -1
        for (i in cards.indices.reversed()) {
            val top = cardTop(s, col, i)
            if (y >= top && y <= top + cardH) return i
        }
        return if (y > cardTop(s, col, cards.lastIndex) + cardH) cards.lastIndex else -1
    }

    /**
     * The card a finger at y most likely means:
     * a touch just above the first face-up card counts as that card, and a face-up card above the movable run
     * snaps to the run's first card.
     */
    fun pickIndex(s: SpiderState, col: Int, y: Float): Int {
        val cards = s.columns[col]
        var idx = cardAt(s, col, y)
        if (idx < 0) return idx
        if (!cards[idx].up) {
            val fu = cards.indexOfFirst { it.up }
            if (fu > idx && cardTop(s, col, fu) - y <= FACE_UP_SNAP_DP * density) idx = fu
        }
        if (cards[idx].up) {
            val start = SpiderEngine.runStart(s, col)
            if (start in 0..cards.lastIndex && idx < start) idx = start
        }
        return idx
    }
}
