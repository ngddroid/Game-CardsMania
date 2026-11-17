package com.puzzel.lab.cardsmania.ui.spider

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.spider.SpiderRules
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.RoundIconButton
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun SpiderScreen(daily: String?, onExit: () -> Unit) {
    val vm: SpiderViewModel = viewModel()
    LaunchedEffect(vm) { vm.start(daily) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> vm.setForeground(true)
                Lifecycle.Event.ON_STOP -> vm.setForeground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler { if (ui.dialog != null) vm.showDialog(null) else onExit() }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B3D20))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val leftHanded = ui.prefs.leftHanded || ui.settings.leftHanded
        val layout = remember(wPx, hPx, topInset, bottomInset, leftHanded) {
            SpiderLayout(wPx, hPx, density.density, topInset, bottomInset, leftHanded).also {
                PlayingCardArt.configure((it.cardW * 1.2f).toInt())
            }
        }
        val world = remember(layout) { SpiderWorld(layout) }
        SpiderTable(vm, world, ui)

        SpiderTopBar(ui, onBack = onExit, onRules = { vm.showDialog(SpiderDialog.RULES) })
        SpiderBottomBar(
            ui,
            Modifier.align(Alignment.BottomCenter),
            onNew = { vm.showDialog(SpiderDialog.NEW_GAME) },
            onDaily = { vm.showDialog(SpiderDialog.DAILY) },
            onLook = { vm.showDialog(SpiderDialog.LOOK) },
            onHint = vm::hint,
            onUndo = vm::undo,
        )
        AnimatedVisibility(
            ui.autoFinish && ui.win == null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
        ) {
            GameButton(
                "AUTO FINISH", vm::autoFinish, style = ButtonStyle.GREEN, icon = Icons.Rounded.AutoAwesome,
                height = 50.dp, fontSize = 17.sp, modifier = Modifier.width(220.dp),
            )
        }
        Toast(ui, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (ui.autoFinish) 160.dp else 96.dp))
    }

    SpiderDialogs(vm, ui, onExit)
}

// ------------------------------------------------------------------------------------------- table

@Composable
private fun SpiderTable(vm: SpiderViewModel, world: SpiderWorld, ui: SpiderUi) {
    val layout = world.layout
    world.background = runCatching { SpiderBackground.valueOf(ui.prefs.background) }.getOrDefault(SpiderBackground.FELT)
    world.cardBack = ui.profile.cardBack
    world.fourColor = ui.prefs.fourColor
    world.reduceMotion = ui.settings.reduceMotion
    world.speed = when (ui.settings.speed) {
        0 -> 1.3f
        2 -> 0.7f
        else -> 1f
    }
    LaunchedEffect(world, vm) { vm.frames.collect { f -> if (f != null) world.apply(f) } }
    LaunchedEffect(world, vm) { vm.hints.collect { world.showHint(it) } }
    LaunchedEffect(world, vm) { vm.shakes.collect { (col, idx) -> world.shake(col, idx) } }
    LaunchedEffect(world, vm) {
        vm.cues.collect { cue ->
            when (cue) {
                SpiderCue.StockBlocked -> world.stockBlocked()
                is SpiderCue.SoftHint -> world.showHint(cue.hint, soft = true)
            }
        }
    }
    world.selected = ui.selected
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (isActive) withFrameMillis { tick = it } }
    val slop = LocalViewConfiguration.current.touchSlop

    Canvas(
        Modifier.fillMaxSize().pointerInput(world, vm) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x0 = down.position.x
                val y0 = down.position.y
                world.clearHint()
                val s = world.state
                // stock (mirrored in left-handed mode)
                if (s != null && layout.isStockHit(x0, y0)) {
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    } while (ch.pressed)
                    vm.tapStock()
                    return@awaitEachGesture
                }
                if (s == null || !layout.inTableau(y0)) {
                    vm.tapAway()
                    return@awaitEachGesture
                }
                // forgiving pick: hit areas span the gaps, empty space below a column = its last card,
                // face-up cards above the movable run snap to its first card
                val col = layout.columnAt(x0)
                val idx = if (col >= 0) layout.pickIndex(s, col, y0) else -1
                val canDrag = col >= 0 && idx >= 0 && vm.canPickUp(col, idx)
                var dragging = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val dx = ch.position.x - x0
                    val dy = ch.position.y - y0
                    if (!dragging && canDrag && dx * dx + dy * dy > slop * slop) {
                        dragging = true
                        world.beginDrag(col, idx, x0, y0)
                    }
                    if (dragging) {
                        world.dragX = ch.position.x
                        world.dragY = ch.position.y
                        ch.consume()
                    }
                }
                when {
                    dragging -> {
                        val (target, near) = world.dropTarget()
                        val placed = target >= 0 && vm.dragDrop(col, idx, target)
                        if (placed) world.dropDone() else {
                            world.endDrag()
                            if (target < 0) vm.invalidDrop(col, idx, near)
                        }
                    }
                    col < 0 -> vm.tapAway()
                    idx >= 0 -> vm.tapCard(col, idx)
                    else -> vm.tapColumn(col)
                }
            }
        }
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawIntoCanvas { world.draw(it.nativeCanvas, SystemClock.uptimeMillis()) }
    }
}

// ------------------------------------------------------------------------------------------- bars

@Composable
private fun SpiderTopBar(ui: SpiderUi, onBack: () -> Unit, onRules: () -> Unit) {
    val s = ui.state
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xE6241052), Color(0xE6100630))))
            .border(1.2.dp, Brush.verticalGradient(listOf(CM.GoldLight.copy(alpha = 0.7f), CM.GoldDeep.copy(alpha = 0.3f))), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, size = 40.dp, contentDescription = "Back")
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = buildString {
                append("SPIDER")
                if (s != null) append(" · ${s.suits} SUIT" + if (s.suits > 1) "S" else "")
                if (ui.daily != null) append(" · DAILY")
            }
            Text(label, color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.prefs.showTimer) StatChip("Time", SpiderRules.formatTime(ui.elapsedMs))
                StatChip("Moves", "${s?.moves ?: 0}")
                StatChip("Score", "${s?.score ?: 500}")
            }
        }
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Rounded.HelpOutline, onRules, size = 40.dp, contentDescription = "How to play")
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label ", color = CM.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SpiderBottomBar(
    ui: SpiderUi,
    modifier: Modifier,
    onNew: () -> Unit,
    onDaily: () -> Unit,
    onLook: () -> Unit,
    onHint: () -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC07031A))))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xEE3A1784), Color(0xEE1A0844))))
            .border(1.5.dp, Brush.verticalGradient(listOf(CM.GoldLight.copy(alpha = 0.8f), CM.GoldDeep.copy(alpha = 0.4f))), RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BarButton(Icons.Rounded.AddCircle, "New", onNew)
        BarButton(Icons.Rounded.CalendarMonth, "Daily", onDaily, badge = ui.profile.spider.dailySolved[com.puzzel.lab.cardsmania.data.GameFactory.today()] == null)
        BarButton(Icons.Rounded.Palette, "Theme", onLook)
        BarButton(Icons.Rounded.Lightbulb, "Hint", onHint, tint = CM.GoldLight)
        BarButton(Icons.AutoMirrored.Rounded.Undo, "Undo", onUndo, enabled = ui.canUndo)
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = Color.White, badge: Boolean = false) {
    val app = LocalApp.current
    Column(
        Modifier.width(62.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled) {
            app.sound.play(Sfx.TAP)
            app.haptics.tick()
            onClick()
        }.padding(vertical = 4.dp).alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
            if (badge) Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(RoundedCornerShape(50)).background(CM.Ruby))
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Toast(ui: SpiderUi, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.toastAt) {
        if (ui.toast != null && System.currentTimeMillis() - ui.toastAt < 1000) {
            text = ui.toast
            delay(2200)
            text = null
        }
    }
    AnimatedVisibility(text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xE6120634)).border(1.dp, CM.Gold, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) { Text(text ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

@Suppress("unused")
private fun unusedStats(): ImageVector = Icons.Rounded.Insights
