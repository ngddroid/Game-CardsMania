package com.ngdstudio.mindimania.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cards Mania's own hand-drawn icon set (24×24 viewport, bold and rounded).
 * Icons are white so `Icon(tint = …)` colors them; secondary parts use reduced alpha for a two-tone look.
 */
object GameIcons {
    private const val SECOND = 0.45f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.fill(alpha: Float = 1f, evenOdd: Boolean = false, block: PathBuilder.() -> Unit) =
        path(
            fill = SolidColor(Color.White), fillAlpha = alpha,
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block,
        )

    private fun ImageVector.Builder.line(width: Float = 2.4f, alpha: Float = 1f, block: PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.White), strokeAlpha = alpha, strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
        )

    private fun PathBuilder.rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        quadTo(r, t, r, t + rad)
        lineTo(r, b - rad)
        quadTo(r, b, r - rad, b)
        lineTo(l + rad, b)
        quadTo(l, b, l, b - rad)
        lineTo(l, t + rad)
        quadTo(l, t, l + rad, t)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.diamond(cx: Float, cy: Float, w: Float, h: Float) {
        moveTo(cx, cy - h); lineTo(cx + w, cy); lineTo(cx, cy + h); lineTo(cx - w, cy); close()
    }

    // ------------------------------------------------------------------ navigation & chrome

    /** Lobby: a fan of two cards. */
    val Lobby: ImageVector by lazy {
        icon("Lobby") {
            group(rotate = -16f, pivotX = 12f, pivotY = 20f) { fill(SECOND) { rrect(4.5f, 3.5f, 15.5f, 19f, 2.4f) } }
            group(rotate = 10f, pivotX = 12f, pivotY = 20f) {
                fill(evenOdd = true) {
                    rrect(8.5f, 3f, 19.5f, 19f, 2.4f)
                    diamond(14f, 11f, 3f, 4.2f)
                }
            }
        }
    }

    val Trophy: ImageVector by lazy {
        icon("Trophy") {
            line(2.2f, SECOND) {
                moveTo(6.5f, 6f); curveTo(2.5f, 6f, 2.8f, 11.5f, 7.4f, 11.6f)
                moveTo(17.5f, 6f); curveTo(21.5f, 6f, 21.2f, 11.5f, 16.6f, 11.6f)
            }
            fill {
                moveTo(6f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 8f)
                curveTo(18f, 12.5f, 15f, 14.5f, 12f, 14.5f)
                curveTo(9f, 14.5f, 6f, 12.5f, 6f, 8f); close()
                rrect(10.6f, 14f, 13.4f, 18f, 0.6f)
                rrect(7f, 18f, 17f, 21f, 1.2f)
            }
        }
    }

    val Chest: ImageVector by lazy {
        icon("Chest") {
            fill(SECOND) {
                moveTo(3f, 10f); lineTo(3f, 8.5f); curveTo(3f, 5.5f, 5.5f, 4f, 8f, 4f); lineTo(16f, 4f)
                curveTo(18.5f, 4f, 21f, 5.5f, 21f, 8.5f); lineTo(21f, 10f); close()
            }
            fill(evenOdd = true) {
                rrect(3f, 10.8f, 21f, 20.5f, 1.6f)
                rrect(10f, 9.5f, 14f, 15f, 1f)
            }
            fill { rrect(10.9f, 10.6f, 13.1f, 13.9f, 0.6f) }
        }
    }

    val Profile: ImageVector by lazy {
        icon("Profile") {
            fill { circle(12f, 8f, 4.3f) }
            fill { moveTo(3.5f, 21f); curveTo(3.5f, 15.5f, 7.5f, 13.6f, 12f, 13.6f); curveTo(16.5f, 13.6f, 20.5f, 15.5f, 20.5f, 21f); close() }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            fill(evenOdd = true) {
                val teeth = 8
                val ro = 10.2f
                val ri = 7.6f
                for (i in 0 until teeth * 2) {
                    val a0 = (i * PI / teeth - PI / teeth / 2.6).toFloat()
                    val a1 = (i * PI / teeth + PI / teeth / 2.6).toFloat()
                    val r = if (i % 2 == 0) ro else ri
                    val x0 = 12f + cos(a0) * r
                    val y0 = 12f + sin(a0) * r
                    val x1 = 12f + cos(a1) * r
                    val y1 = 12f + sin(a1) * r
                    if (i == 0) moveTo(x0, y0) else lineTo(x0, y0)
                    lineTo(x1, y1)
                }
                close()
                circle(12f, 12f, 3.2f)
            }
        }
    }

    val Help: ImageVector by lazy {
        icon("Help") {
            fill(SECOND) { circle(12f, 12f, 10f) }
            line(2.6f) {
                moveTo(9f, 9.2f); curveTo(9f, 5.6f, 15f, 5.6f, 15f, 9.2f); curveTo(15f, 11.6f, 12f, 11.6f, 12f, 14f)
            }
            fill { circle(12f, 17.6f, 1.6f) }
        }
    }

    val Coin: ImageVector by lazy {
        icon("Coin") {
            fill(evenOdd = true) {
                circle(12f, 12f, 10f)
                circle(12f, 12f, 7f)
            }
            fill(SECOND) { circle(12f, 12f, 7f) }
            fill { diamond(12f, 12f, 3f, 4.4f) }
        }
    }

    val Plus: ImageVector by lazy { icon("Plus") { line(3.4f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } } }

    val Hint: ImageVector by lazy {
        icon("Hint") {
            fill { moveTo(12f, 2.5f); curveTo(7.6f, 2.5f, 5f, 5.8f, 5f, 9.2f); curveTo(5f, 12f, 6.6f, 13.6f, 8f, 14.8f); lineTo(8.4f, 16.5f); lineTo(15.6f, 16.5f); lineTo(16f, 14.8f); curveTo(17.4f, 13.6f, 19f, 12f, 19f, 9.2f); curveTo(19f, 5.8f, 16.4f, 2.5f, 12f, 2.5f); close() }
            fill(SECOND) { rrect(8.6f, 17.5f, 15.4f, 19.6f, 1f) }
            fill(SECOND) { rrect(9.8f, 20.2f, 14.2f, 22f, 0.9f) }
            line(1.6f, 0.6f) { moveTo(9.5f, 7.5f); quadTo(10.5f, 5.8f, 12.5f, 5.6f) }
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo") {
            line(2.8f) { moveTo(8f, 9f); lineTo(14f, 9f); curveTo(18f, 9f, 20f, 11.5f, 20f, 14.5f); curveTo(20f, 17.5f, 18f, 20f, 14f, 20f); lineTo(9f, 20f) }
            fill { moveTo(3f, 9f); lineTo(9.5f, 3.6f); lineTo(9.5f, 14.4f); close() }
        }
    }

    val Deal: ImageVector by lazy {
        icon("Deal") {
            fill(SECOND) { rrect(3f, 4f, 13f, 18f, 2f) }
            fill { rrect(8f, 7f, 18f, 21f, 2f) }
            line(2.4f) { moveTo(21f, 3f); lineTo(21f, 10f); moveTo(18.5f, 7.5f); lineTo(21f, 10f); lineTo(23.5f, 7.5f) }
        }
    }

    val Emote: ImageVector by lazy {
        icon("Emote") {
            fill(SECOND) { circle(12f, 12f, 10f) }
            fill { circle(8.6f, 10f, 1.7f); circle(15.4f, 10f, 1.7f) }
            line(2.4f) { moveTo(7.5f, 14.2f); curveTo(9f, 17.6f, 15f, 17.6f, 16.5f, 14.2f) }
        }
    }

    val Sort: ImageVector by lazy {
        icon("Sort") {
            line(3f) { moveTo(4f, 6f); lineTo(20f, 6f) }
            line(3f, 0.8f) { moveTo(4f, 12f); lineTo(15f, 12f) }
            line(3f, 0.6f) { moveTo(4f, 18f); lineTo(10f, 18f) }
        }
    }

    val Pause: ImageVector by lazy { icon("Pause") { fill { rrect(5.5f, 4f, 10f, 20f, 2f); rrect(14f, 4f, 18.5f, 20f, 2f) } } }

    val Back: ImageVector by lazy { icon("Back") { line(3.2f) { moveTo(14.5f, 4.5f); lineTo(7f, 12f); lineTo(14.5f, 19.5f) } } }

    val Forward: ImageVector by lazy { icon("Forward") { line(3.2f) { moveTo(9.5f, 4.5f); lineTo(17f, 12f); lineTo(9.5f, 19.5f) } } }

    val Close: ImageVector by lazy { icon("Close") { line(3.2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } } }

    val Check: ImageVector by lazy { icon("Check") { line(3.4f) { moveTo(4.5f, 12.5f); lineTo(9.8f, 17.6f); lineTo(19.5f, 6.5f) } } }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            line(2.6f, 0.75f) { moveTo(7.5f, 11f); lineTo(7.5f, 7.8f); curveTo(7.5f, 2.5f, 16.5f, 2.5f, 16.5f, 7.8f); lineTo(16.5f, 11f) }
            fill(evenOdd = true) { rrect(4.5f, 10.5f, 19.5f, 21.5f, 2.4f); circle(12f, 15.4f, 1.8f) }
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            fill {
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 10.5f else 4.6f
                    val a = (PI / 5 * i - PI / 2).toFloat()
                    val x = 12f + cos(a) * r
                    val y = 12.6f + sin(a) * r
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }

    val Crown: ImageVector by lazy {
        icon("Crown") {
            fill { moveTo(3f, 7f); lineTo(7.5f, 12f); lineTo(12f, 4f); lineTo(16.5f, 12f); lineTo(21f, 7f); lineTo(19.5f, 17f); lineTo(4.5f, 17f); close() }
            fill(SECOND) { rrect(4.5f, 18f, 19.5f, 21f, 1.2f) }
            fill { circle(3f, 6.5f, 1.6f); circle(12f, 3.5f, 1.6f); circle(21f, 6.5f, 1.6f) }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            fill(SECOND) { rrect(3f, 5f, 21f, 21f, 2.6f) }
            fill { rrect(3f, 5f, 21f, 9.6f, 2.6f) }
            line(2.4f) { moveTo(8f, 3f); lineTo(8f, 7f); moveTo(16f, 3f); lineTo(16f, 7f) }
            fill { circle(8f, 13.5f, 1.3f); circle(12f, 13.5f, 1.3f); circle(16f, 13.5f, 1.3f); circle(8f, 17.4f, 1.3f); circle(12f, 17.4f, 1.3f) }
        }
    }

    val Wifi: ImageVector by lazy {
        icon("Wifi") {
            line(2.6f, 0.55f) { moveTo(2.5f, 9f); curveTo(7.8f, 3.8f, 16.2f, 3.8f, 21.5f, 9f) }
            line(2.6f, 0.8f) { moveTo(5.8f, 12.4f); curveTo(9.3f, 9f, 14.7f, 9f, 18.2f, 12.4f) }
            line(2.6f) { moveTo(9f, 15.6f); curveTo(10.7f, 14.1f, 13.3f, 14.1f, 15f, 15.6f) }
            fill { circle(12f, 19.2f, 1.9f) }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") { fill { moveTo(7.5f, 4.6f); curveTo(7.5f, 3.7f, 8.4f, 3.2f, 9.2f, 3.7f); lineTo(19.4f, 10.6f); curveTo(20.4f, 11.3f, 20.4f, 12.7f, 19.4f, 13.4f); lineTo(9.2f, 20.3f); curveTo(8.4f, 20.8f, 7.5f, 20.3f, 7.5f, 19.4f); close() } }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            line(2.8f) { moveTo(19f, 12f); curveTo(19f, 16f, 15.9f, 19.5f, 12f, 19.5f); curveTo(8.1f, 19.5f, 5f, 16.3f, 5f, 12f); curveTo(5f, 7.8f, 8.2f, 4.5f, 12f, 4.5f); lineTo(15f, 4.5f) }
            fill { moveTo(14f, 0.8f); lineTo(19.5f, 4.6f); lineTo(14f, 8.4f); close() }
        }
    }

    val Gift: ImageVector by lazy {
        icon("Gift") {
            fill(SECOND) { rrect(4f, 11f, 20f, 21f, 1.6f) }
            fill { rrect(2.8f, 7.5f, 21.2f, 11.5f, 1.4f) }
            fill { rrect(10.6f, 7.5f, 13.4f, 21f, 0.4f) }
            line(2.2f) { moveTo(12f, 7.5f); curveTo(9f, 2.5f, 5f, 4.5f, 7.6f, 7.4f); moveTo(12f, 7.5f); curveTo(15f, 2.5f, 19f, 4.5f, 16.4f, 7.4f) }
        }
    }

    val Palette: ImageVector by lazy {
        icon("Palette") {
            fill(evenOdd = true) {
                moveTo(12f, 2.5f); curveTo(6.2f, 2.5f, 2.5f, 6.8f, 2.5f, 12f); curveTo(2.5f, 17.5f, 6.8f, 21.5f, 11.5f, 21.5f)
                curveTo(13.6f, 21.5f, 14f, 20f, 13.2f, 18.6f); curveTo(12.3f, 17f, 13.2f, 15.4f, 15f, 15.4f); lineTo(17f, 15.4f)
                curveTo(19.8f, 15.4f, 21.5f, 13.6f, 21.5f, 11f); curveTo(21.5f, 6.2f, 17.4f, 2.5f, 12f, 2.5f); close()
                circle(7.5f, 11.5f, 1.6f); circle(9.5f, 7f, 1.6f); circle(14.5f, 7f, 1.6f); circle(17.5f, 10.8f, 1.6f)
            }
        }
    }

    val Stats: ImageVector by lazy {
        icon("Stats") {
            fill(SECOND) { rrect(3.5f, 12f, 8f, 20.5f, 1.4f) }
            fill { rrect(9.8f, 4f, 14.2f, 20.5f, 1.4f) }
            fill(0.75f) { rrect(16f, 8.5f, 20.5f, 20.5f, 1.4f) }
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            fill(evenOdd = true) {
                moveTo(1.5f, 12f); curveTo(5f, 5.5f, 19f, 5.5f, 22.5f, 12f); curveTo(19f, 18.5f, 5f, 18.5f, 1.5f, 12f); close()
                circle(12f, 12f, 3.6f)
            }
            fill { circle(12f, 12f, 1.8f) }
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            fill { moveTo(12f, 2.2f); lineTo(20f, 5.2f); lineTo(20f, 11f); curveTo(20f, 16.2f, 16.6f, 19.8f, 12f, 21.8f); curveTo(7.4f, 19.8f, 4f, 16.2f, 4f, 11f); lineTo(4f, 5.2f); close() }
            line(2f, SECOND) { moveTo(8.5f, 11.8f); lineTo(11f, 14.3f); lineTo(15.8f, 9f) }
        }
    }

    val Volume: ImageVector by lazy {
        icon("Volume") {
            fill { moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12.5f, 4f); lineTo(12.5f, 20f); lineTo(7f, 15f); lineTo(3f, 15f); close() }
            line(2.4f, 0.8f) { moveTo(16f, 8.5f); curveTo(18f, 10.5f, 18f, 13.5f, 16f, 15.5f) }
            line(2.4f, SECOND) { moveTo(18.8f, 5.6f); curveTo(22.6f, 9.4f, 22.6f, 14.6f, 18.8f, 18.4f) }
        }
    }

    val Music: ImageVector by lazy {
        icon("Music") {
            fill { circle(7f, 18f, 3f); circle(17f, 16f, 3f) }
            fill { rrect(8.6f, 4.5f, 10.4f, 18f, 0.6f); rrect(18.6f, 3f, 20.4f, 16f, 0.6f) }
            fill(SECOND) { moveTo(8.6f, 4.5f); lineTo(20.4f, 2f); lineTo(20.4f, 6f); lineTo(8.6f, 8.5f); close() }
        }
    }

    val Vibrate: ImageVector by lazy {
        icon("Vibrate") {
            fill(evenOdd = true) { rrect(7.5f, 3f, 16.5f, 21f, 2.2f); rrect(9.4f, 5.4f, 14.6f, 17f, 0.8f) }
            line(2f, SECOND) { moveTo(4.5f, 8f); lineTo(4.5f, 16f); moveTo(19.5f, 8f); lineTo(19.5f, 16f) }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            fill(SECOND) { circle(12f, 12f, 10f) }
            fill { circle(12f, 7.2f, 1.6f) }
            line(2.8f) { moveTo(12f, 11f); lineTo(12f, 17f) }
        }
    }

    val Doc: ImageVector by lazy {
        icon("Doc") {
            fill(SECOND) { rrect(4.5f, 2.5f, 19.5f, 21.5f, 2.2f) }
            line(2.2f) { moveTo(8f, 8f); lineTo(16f, 8f); moveTo(8f, 12f); lineTo(16f, 12f); moveTo(8f, 16f); lineTo(13f, 16f) }
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            fill { circle(18f, 5.5f, 3f); circle(6f, 12f, 3f); circle(18f, 18.5f, 3f) }
            line(2.2f, 0.8f) { moveTo(8.6f, 10.6f); lineTo(15.4f, 6.9f); moveTo(8.6f, 13.4f); lineTo(15.4f, 17.1f) }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            fill(SECOND) { moveTo(5.5f, 7f); lineTo(18.5f, 7f); lineTo(17.4f, 20f); curveTo(17.3f, 21f, 16.5f, 21.5f, 15.6f, 21.5f); lineTo(8.4f, 21.5f); curveTo(7.5f, 21.5f, 6.7f, 21f, 6.6f, 20f); close() }
            line(2.4f) { moveTo(3.5f, 6f); lineTo(20.5f, 6f); moveTo(9.5f, 6f); lineTo(10f, 3f); lineTo(14f, 3f); lineTo(14.5f, 6f) }
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            fill { moveTo(15.5f, 3.5f); lineTo(20.5f, 8.5f); lineTo(9f, 20f); lineTo(3.5f, 20.5f); lineTo(4f, 15f); close() }
            fill(SECOND) { moveTo(13.5f, 5.5f); lineTo(18.5f, 10.5f); lineTo(20.5f, 8.5f); lineTo(15.5f, 3.5f); close() }
        }
    }

    val Bolt: ImageVector by lazy { icon("Bolt") { fill { moveTo(13.5f, 1.5f); lineTo(4.5f, 13.5f); lineTo(11f, 13.5f); lineTo(9.5f, 22.5f); lineTo(19.5f, 9.5f); lineTo(13f, 9.5f); close() } } }

    val Book: ImageVector by lazy {
        icon("Book") {
            fill { moveTo(2.5f, 5f); curveTo(5.5f, 3.8f, 8.8f, 3.8f, 11.4f, 5.6f); lineTo(11.4f, 20.2f); curveTo(8.8f, 18.6f, 5.5f, 18.6f, 2.5f, 19.6f); close() }
            fill(SECOND) { moveTo(21.5f, 5f); curveTo(18.5f, 3.8f, 15.2f, 3.8f, 12.6f, 5.6f); lineTo(12.6f, 20.2f); curveTo(15.2f, 18.6f, 18.5f, 18.6f, 21.5f, 19.6f); close() }
        }
    }

    val Robot: ImageVector by lazy {
        icon("Robot") {
            fill(evenOdd = true) { rrect(4f, 7f, 20f, 19f, 3f); circle(9f, 12.5f, 1.8f); circle(15f, 12.5f, 1.8f) }
            line(2.2f) { moveTo(12f, 7f); lineTo(12f, 3.5f) }
            fill { circle(12f, 3f, 1.6f) }
            fill(SECOND) { rrect(1.5f, 10.5f, 3.5f, 15.5f, 1f); rrect(20.5f, 10.5f, 22.5f, 15.5f, 1f) }
        }
    }

    val Team: ImageVector by lazy {
        icon("Team") {
            fill(SECOND) { circle(17f, 8.5f, 3.2f); moveTo(13f, 20f); curveTo(13f, 15.5f, 15f, 13.6f, 17.4f, 13.6f); curveTo(20.4f, 13.6f, 22.5f, 15.6f, 22.5f, 20f); close() }
            fill { circle(9f, 8f, 3.8f); moveTo(2f, 20.5f); curveTo(2f, 15.5f, 5f, 13.2f, 9f, 13.2f); curveTo(13f, 13.2f, 16f, 15.5f, 16f, 20.5f); close() }
        }
    }

    // ------------------------------------------------------------------ suits

    val Spade: ImageVector by lazy {
        icon("Spade") {
            fill {
                moveTo(12f, 2f); curveTo(15f, 6.5f, 21f, 9.5f, 21f, 13.6f); curveTo(21f, 17.4f, 16.3f, 18.6f, 13.2f, 15.6f)
                lineTo(14.6f, 21.5f); lineTo(9.4f, 21.5f); lineTo(10.8f, 15.6f)
                curveTo(7.7f, 18.6f, 3f, 17.4f, 3f, 13.6f); curveTo(3f, 9.5f, 9f, 6.5f, 12f, 2f); close()
            }
        }
    }

    val Heart: ImageVector by lazy {
        icon("Heart") {
            fill {
                moveTo(12f, 21f); curveTo(5.5f, 15.5f, 2.5f, 12.4f, 2.5f, 8.6f); curveTo(2.5f, 5.4f, 5f, 3.2f, 7.6f, 3.2f)
                curveTo(9.6f, 3.2f, 11f, 4.4f, 12f, 6f); curveTo(13f, 4.4f, 14.4f, 3.2f, 16.4f, 3.2f)
                curveTo(19f, 3.2f, 21.5f, 5.4f, 21.5f, 8.6f); curveTo(21.5f, 12.4f, 18.5f, 15.5f, 12f, 21f); close()
            }
        }
    }

    val Diamond: ImageVector by lazy {
        icon("Diamond") {
            fill { moveTo(12f, 2f); quadTo(16f, 8f, 20f, 12f); quadTo(16f, 16f, 12f, 22f); quadTo(8f, 16f, 4f, 12f); quadTo(8f, 8f, 12f, 2f); close() }
        }
    }

    val Club: ImageVector by lazy {
        icon("Club") {
            fill {
                circle(12f, 7.2f, 4.4f); circle(7f, 13.6f, 4.4f); circle(17f, 13.6f, 4.4f)
                moveTo(10.6f, 12f); lineTo(13.4f, 12f); lineTo(14.8f, 21.5f); lineTo(9.2f, 21.5f); close()
            }
        }
    }

    /** Suit icon by index (0 spades, 1 hearts, 2 diamonds, 3 clubs). */
    fun suit(i: Int): ImageVector = when (i) {
        1 -> Heart
        2 -> Diamond
        3 -> Club
        else -> Spade
    }

    @Suppress("unused")
    private fun deg(a: Float) = (a * PI / 180).toFloat()
}
