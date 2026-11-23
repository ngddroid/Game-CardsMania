package com.ngdstudio.mindimania.ui.game

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.ui.render.CardBack

@Composable
fun TableCanvas(
    vm: GameViewModel,
    world: SpriteWorld,
    ui: GameUi,
    theme: TableTheme,
    back: CardBack,
    modifier: Modifier = Modifier,
) {
    val layout = world.layout
    val driver = remember { FrameDriver() }
    SideEffect {
        val speed = Timing.speedFactor(ui.settings.speed)
        val interactive = ui.idle && !ui.paused
        val changed = world.speedFactor != speed || world.reduceMotion != ui.settings.reduceMotion ||
            world.colorBlind != ui.settings.colorBlind || world.theme != theme || world.back != back ||
            world.interactive != interactive || world.hint != ui.hint
        world.speedFactor = speed
        world.reduceMotion = ui.settings.reduceMotion
        world.colorBlind = ui.settings.colorBlind
        world.theme = theme
        world.back = back
        world.interactive = interactive
        world.hint = ui.hint
        if (changed) driver.wake()
    }
    LaunchedEffect(world, vm) {
        vm.frame.value?.let {
            if (world.state == null) {
                world.applyFrame(it)
                driver.wake()
            }
        }
        vm.fx.collect {
            world.applyFrame(it)
            driver.wake()
        }
    }
    LaunchedEffect(world, vm, ui.selected, ui.playable, ui.jumpIn, ui.sortMode) {
        world.selected = ui.selected
        world.playable = ui.playable
        world.jumpIn = ui.jumpIn
        world.sortMode = ui.sortMode
        world.relayout()
        driver.wake()
    }
    // redraws every frame only while the table animates; slow pace or asleep otherwise (see FrameDriver)
    val tick by rememberFrameTick(driver, world)
    val uiState by rememberUpdatedState(ui)
    val slop = LocalViewConfiguration.current.touchSlop

    Canvas(
        modifier
            .fillMaxSize()
            .wakeOnTouch(driver)
            .pointerInput(world, vm) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = down.position.x
                    val y0 = down.position.y
                    val id = world.hitHand(x0, y0)
                    if (id != null) {
                        var dragging = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val dx = ch.position.x - x0
                            val dy = ch.position.y - y0
                            val u = uiState
                            if (!dragging && (dx * dx + dy * dy) > slop * slop * 2 && (id in u.playable || id in u.jumpIn) && u.idle) {
                                dragging = true
                                world.dragId = id
                            }
                            if (dragging) {
                                world.dragX = ch.position.x
                                world.dragY = ch.position.y - layout.handCardH * 0.25f
                                ch.consume()
                            }
                        }
                        if (dragging) {
                            val releasedY = world.dragY
                            world.dragId = null
                            val played = releasedY < layout.handTopY - layout.handCardH * 0.2f && vm.dragPlay(id)
                            if (!played) world.release(id)
                            driver.wake()
                        } else {
                            vm.tapCard(id)
                        }
                    } else if (world.hitDraw(x0, y0)) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                        }
                        vm.drawCard()
                    } else {
                        vm.deselect()
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawIntoCanvas { world.draw(it.nativeCanvas, SystemClock.uptimeMillis()) }
    }
}
