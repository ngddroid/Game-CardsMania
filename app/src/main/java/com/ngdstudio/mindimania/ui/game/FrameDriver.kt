package com.ngdstudio.mindimania.ui.game

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A table canvas that only redraws while something on it moves.
 *
 * A card table is still most of the time. Redrawing it on every screen refresh (60–120 times a second) wastes battery
 * and heats the phone, so each sprite world says whether it is [isBusy] and the canvas sleeps when it is not.
 */
interface AnimatedWorld {
    /** True while anything moves or fades: a card in flight (or waiting on a delay), a drag, effects, a shake. */
    fun isBusy(now: Long): Boolean

    /**
     * Only asked when not busy: how many ms until the next frame is needed for a slow decoration that is visible right
     * now (a pulsing glow, a countdown ring), or 0 when nothing on screen changes with time.
     */
    fun idleFrameMs(now: Long): Long = 0L
}

/** Wakes a sleeping table canvas. Call [wake] after anything changes what the table shows. Safe from any thread. */
class FrameDriver {
    private val signal = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        signal.trySend(Unit)
    }

    internal suspend fun sleep(ms: Long) {
        withTimeoutOrNull(ms) { signal.receive() }
    }

    companion object {
        /**
         * Longest sleep. A safety net: if some change ever forgets to call [wake], the table still catches up within
         * this time instead of looking frozen. Two frames a second while idle cost next to nothing.
         */
        const val MAX_SLEEP_MS = 500L
    }
}

/**
 * Frame clock for a table canvas: read the returned value inside the Canvas draw block. It advances every frame while
 * [world] is busy, at the world's slow pace when only a decoration animates, and stops while the table is still.
 */
@Composable
fun rememberFrameTick(driver: FrameDriver, world: AnimatedWorld): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(driver, world) {
        while (isActive) {
            withFrameMillis { tick.longValue = it }
            val now = SystemClock.uptimeMillis()
            if (world.isBusy(now)) continue
            val slow = world.idleFrameMs(now)
            driver.sleep(if (slow > 0) minOf(slow, FrameDriver.MAX_SLEEP_MS) else FrameDriver.MAX_SLEEP_MS)
        }
    }
    return tick
}

/** Wakes [driver] on every touch, before the table's own gesture handling sees it. */
fun Modifier.wakeOnTouch(driver: FrameDriver): Modifier = pointerInput(driver) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            driver.wake()
        }
    }
}
