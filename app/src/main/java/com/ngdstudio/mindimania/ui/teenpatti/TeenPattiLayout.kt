package com.ngdstudio.mindimania.ui.teenpatti

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometry of the Teen Patti table (px). The table is played in landscape (the route locks it), but a portrait size
 * still lays out sensibly (e.g. the moment before the rotation lands, or a device that ignores the lock).
 *
 * Landscape: the opponents sit on an arc from the left, over the top, to the right; my avatar is bottom-left, my three
 * cards bottom-centre and the betting buttons bottom-right. Each opponent's three small cards sit beside the avatar,
 * toward the middle. Portrait: as before, my cards above a full-width button bar.
 * [leftInset]/[rightInset] keep everything clear of a display cutout on the sides.
 */
class TeenPattiLayout(
    val width: Float,
    val height: Float,
    val density: Float,
    val players: Int,
    val me: Int,
    val topInset: Float,
    val bottomInset: Float,
    val leftInset: Float = 0f,
    val rightInset: Float = 0f,
) {
    val landscape = width > height
    private val d = density

    val hudH = (if (landscape) 44f else 52f) * d
    /** Landscape: the title sits top-left beside the pause button, so the table runs up to the top edge. */
    val tableTop = if (landscape) topInset + 4f * d else topInset + hudH + 8f * d
    val actionBarH = 112f * d
    /** The betting buttons' box. */
    val actionWidth = if (landscape) min(340f * d, width * 0.38f) else width
    val actionLeft = if (landscape) width - rightInset - actionWidth - 6f * d else 0f
    val actionTop = height - bottomInset - actionBarH - (if (landscape) 2f else 6f) * d

    /** Face-up cards (mine, and hands shown at the end) sit side by side: centre-to-centre = width × this. */
    val spread = 1.08f
    val myCardW = if (landscape) min(height * 0.15f, 64f * d) else min(width * 0.2f, 86f * d)
    val myCardH = myCardW * 1.4f
    /** Whole cards, with a clear gap above the bottom edge (or the gesture bar). */
    val myCardsY = if (landscape) height - bottomInset - myCardH / 2 - 12f * d else actionTop - 10f * d - myCardH / 2
    /** My avatar (landscape: centred under the dealer, just above my cards). */
    val myAvatar = if (landscape) min(40f * d, height * 0.1f) else 0f
    private val myAvatarY = myCardsY - myCardH / 2 - 6f * d - myAvatar / 2
    val tableBottom = if (landscape) myAvatarY - myAvatar / 2 - 4f * d else myCardsY - myCardH / 2 - 26f * d
    val avatar = (if (landscape) 46f else if (players >= 6) 48f else 54f) * d
    /** Landscape: the top seat's avatar centre (room above for its BLIND/SEEN tag). */
    private val topSeatY = tableTop + 18f * d + avatar / 2
    /** Landscape: bottom of the top seat's name and chips. */
    private val topSeatBottom = topSeatY + avatar / 2 + 42f * d
    /**
     * The dealer in the middle of the table, and only her (with the pot total under her). Landscape: as tall as fits
     * between the top seat and my cards.
     */
    val dealerH = if (landscape) min(112f * d, tableBottom - topSeatBottom - 6f * d - 28f * d).coerceAtLeast(56f * d) else 150f * d
    val dealerW = dealerH * 0.95f
    /** The table centre = the dealer's centre. */
    val center = if (landscape) Offset((leftInset + width - rightInset) / 2, topSeatBottom + 6f * d + dealerH / 2)
    else Offset(width / 2, (tableTop + tableBottom) / 2)
    /** Centre line of the pot total, just under the dealer. */
    val potLabelY = center.y + dealerH / 2 + 12f * d
    /** Where the cards come from on the deal: her hands. */
    val dealerHands: Offset get() = Offset(center.x, center.y + dealerH * 0.3f)
    /** Landscape: straight under the dealer and the pot, like a seat at the dealer's table. */
    val mySeat = if (landscape) Offset(center.x, myAvatarY) else Offset(width * 0.13f, myCardsY + 6f * d)
    /** Centre of my three cards. Landscape: right under my avatar. */
    private val myCardsX = if (landscape) center.x
    else maxOf(width / 2, mySeat.x + avatar * 0.75f + myCardW * (spread + 0.5f)).coerceAtMost(width - myCardW * (spread + 0.5f) - 4f * d)
    /** Opponents' face-down cards. */
    val smallCardW = if (landscape) min(height * 0.1f, 42f * d) else min(width * 0.1f, 50f * d)
    /** Opponents' cards once shown at the end of a hand. */
    val shownCardW = if (landscape) min(height * 0.13f, (if (players >= 5) 46f else 54f) * d) else min(width * 0.12f, 50f * d)
    val chipR = (if (landscape) 8f else 9f) * d

    /** Avatar centre of [seat]. */
    fun seatOf(seat: Int): Offset {
        val rel = rel(seat)
        if (rel == 0) return mySeat
        val k = players - 1
        val t = (rel - 1).toFloat() / (k - 1).coerceAtLeast(1)
        val angle = Math.toRadians(180.0 + 180.0 * t)
        val margin = if (landscape) 70f * d else maxOf(avatar * 0.75f, 52f * d)
        val rx = (width - leftInset - rightInset) / 2 - margin
        val ry = if (landscape) center.y - topSeatY else center.y - tableTop - avatar * 0.6f
        val x = center.x + (cos(angle) * rx).toFloat()
        val y = center.y + (sin(angle) * ry).toFloat() + (if (abs(cos(angle)) > 0.9) ry * (if (landscape) 0.25f else 0.12f) else 0f)
        // landscape: a seat's name, chips and hand name (about 66 dp under the avatar) stay above the button area and my cards
        if (landscape) return Offset(x, minOf(y, minOf(actionTop, myCardsY - myCardH * 0.5f) - avatar * 0.5f - 66f * d))
        return Offset(x, y)
    }

    fun rel(seat: Int) = ((seat - me) % players + players) % players

    /** Centre of [seat]'s three cards (mine: the big cards at the bottom). */
    fun cardsOf(seat: Int): Offset {
        if (rel(seat) == 0) return Offset(myCardsX, myCardsY)
        val s = seatOf(seat)
        if (landscape) {
            // beside the avatar, toward the middle (below would cover the name and chips)
            val dir = if (s.x <= center.x + 1f) 1f else -1f
            return Offset(s.x + dir * (avatar * 0.95f + smallCardW * 0.9f), s.y - avatar * 0.05f)
        }
        val k = 0.42f
        return Offset(s.x + (center.x - s.x) * k, s.y + (center.y - s.y) * k)
    }

    data class Slot(val x: Float, val y: Float, val rot: Float, val w: Float)

    /**
     * The three card positions of [seat]. Cards you can read (mine, and an opponent's when [shown]) lie flat and side
     * by side with a small gap, so every rank and suit is fully visible. An opponent's face-down cards stay a compact
     * fan (their backs carry nothing to read).
     */
    fun cardSlots(seat: Int, shown: Boolean = false): List<Slot> {
        if (rel(seat) == 0) {
            val c = cardsOf(seat)
            val w = myCardW
            return List(3) { i -> Slot(c.x + (i - 1) * w * spread, c.y, 0f, w) }
        }
        if (shown) {
            val c = shownCenter(seat)
            val w = shownCardW
            return List(3) { i -> Slot(c.x + (i - 1) * w * spread, c.y, 0f, w) }
        }
        val c = cardsOf(seat)
        val w = smallCardW
        return List(3) { i -> Slot(c.x + (i - 1) * w * 0.32f, c.y + abs(i - 1) * w * 0.05f, (i - 1) * 12f, w) }
    }

    /** Centre of an opponent's three face-up cards: beside the avatar, far enough out that the row never covers it. */
    private fun shownCenter(seat: Int): Offset {
        if (!landscape) return cardsOf(seat)
        val s = seatOf(seat)
        val dir = if (s.x <= center.x + 1f) 1f else -1f
        val half = shownCardW * (spread + 0.5f)
        // clear of the name tag under the avatar (about 50 dp each side of its centre)
        val x = s.x + dir * (maxOf(avatar * 0.5f, 50f * d) + 8f * d + half)
        // never low enough to reach the strip above my cards (where my wild cards step up at the reveal)
        val maxY = myCardsY - myCardH * 0.5f - wildLift(me) * myCardH - 8f * d - shownCardW * 1.4f / 2
        return Offset(x, minOf(s.y - avatar * 0.05f, maxY))
    }

    /**
     * How far (in card heights) a wild card steps up behind its row at the reveal. Mine only peeks out, so it stays
     * clear of the seats beside me on a short screen; an opponent's rises further.
     */
    fun wildLift(seat: Int): Float = if (rel(seat) == 0) (if (landscape) 0.2f else 0.26f) else 0.42f

    /** Landscape: my hand's name ends here, left of my avatar (right-aligned to it). */
    val myLabelRight: Float get() = mySeat.x - myAvatar / 2 - 10f * d
    /** Landscape: my name, chips and tag start here, right of my avatar. */
    val myInfoLeft: Float get() = mySeat.x + myAvatar / 2 + 8f * d

    /** Half the width of my row of cards (for tapping them). */
    val myRowHalf: Float get() = myCardW * (spread + 0.5f)

    /** Where chips fly to: the pot total under the dealer. */
    val potPos: Offset get() = Offset(center.x, potLabelY)

    /**
     * Where the turned-up joker card sits, out of the middle (which belongs to the dealer): top-left under the title in
     * landscape, beside the dealer in portrait.
     */
    val jokerPos: Offset get() = if (landscape) Offset(leftInset + 52f * d, topInset + hudH + 50f * d) else Offset(center.x + dealerW * 0.8f, center.y)

    /** Where [seat]'s chips leave from: just in front of the avatar. */
    fun chipFrom(seat: Int): Offset {
        val s = seatOf(seat)
        if (rel(seat) == 0) return Offset(myCardsX, myCardsY - myCardH * 0.4f)
        return Offset(s.x + (center.x - s.x) * 0.2f, s.y + (center.y - s.y) * 0.2f)
    }
}
