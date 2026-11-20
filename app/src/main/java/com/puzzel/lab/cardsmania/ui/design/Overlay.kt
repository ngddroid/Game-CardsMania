package com.puzzel.lab.cardsmania.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.CoinIcon
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ------------------------------------------------------------------------------------------- coin fly

/** Coins flying to the HUD coin capsule. Get it with [LocalCoinFly]; register the target with [coinFlyTarget]. */
class CoinFly internal constructor() {
    internal var target by mutableStateOf<Offset?>(null)
    internal var hostOrigin = Offset.Zero
    internal val coins = mutableStateListOf<FlyingCoin>()
    internal var clock by mutableLongStateOf(0L)

    /** Bumps every time a coin lands (the HUD capsule bounces on it). */
    var landed by mutableIntStateOf(0)
        internal set

    /** Sends [count] coins from [from] (root coordinates) to the coin counter. */
    fun fly(from: Offset, count: Int = 12) {
        val now = System.currentTimeMillis()
        val r = Random(now)
        repeat(count.coerceIn(1, 30)) { i ->
            coins += FlyingCoin(
                from + Offset((r.nextFloat() - 0.5f) * 60f, (r.nextFloat() - 0.5f) * 40f),
                start = now + i * 45L,
                curve = (r.nextFloat() - 0.5f) * 2f,
                tick = i % 3 == 0,
            )
        }
    }
}

internal class FlyingCoin(val from: Offset, val start: Long, val curve: Float, val tick: Boolean) {
    var done = false
}

val LocalCoinFly = staticCompositionLocalOf<CoinFly?> { null }

/** Marks this element (e.g. the HUD coin capsule) as where flying coins land. */
fun Modifier.coinFlyTarget(): Modifier = composed {
    val fly = LocalCoinFly.current
    onGloballyPositioned { fly?.target = it.boundsInRoot().center }
}

// ------------------------------------------------------------------------------------------- toasts

class GameToasts internal constructor() {
    internal val queue = mutableStateListOf<ToastData>()

    /** Shows a top banner (e.g. "Achievement unlocked"). [icon] is an emoji or short text. */
    fun show(icon: String, title: String, subtitle: String = "", color: Color = CM.Gold) {
        queue += ToastData(System.nanoTime(), icon, title, subtitle, color)
    }

    fun achievement(icon: String, title: String, reward: Int) =
        show(icon, Strings.get(R.string.shell_toast_achievement, title), Strings.plural(R.plurals.shell_toast_plus_coins, reward))
    fun levelUp(level: Int) =
        show("⭐", Strings.get(R.string.shell_toast_level_up), Strings.get(R.string.shell_toast_reached_level, level), Color(0xFFC0A6FF))
}

internal data class ToastData(val id: Long, val icon: String, val title: String, val subtitle: String, val color: Color)

val LocalGameToasts = staticCompositionLocalOf<GameToasts?> { null }

/**
 * Hosts screen-wide game effects: flying coins and toasts. Wrap a screen (or the whole app) in it.
 * Children reach the effects through [LocalCoinFly] and [LocalGameToasts].
 */
@Composable
fun GameOverlayHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val app = LocalApp.current
    val fly = remember { CoinFly() }
    val toasts = remember { GameToasts() }
    CompositionLocalProvider(LocalCoinFly provides fly, LocalGameToasts provides toasts) {
        Box(modifier.fillMaxSize().onGloballyPositioned { fly.hostOrigin = it.boundsInRoot().topLeft }) {
            content()
            // coins
            if (fly.coins.isNotEmpty()) {
                LaunchedEffect(fly.coins.size > 0) {
                    while (fly.coins.isNotEmpty()) {
                        withFrameMillis { fly.clock = System.currentTimeMillis() }
                        val now = fly.clock
                        fly.coins.removeAll { c ->
                            val arrived = now - c.start > 720
                            if (arrived && !c.done) {
                                c.done = true
                                fly.landed++
                                if (c.tick) app.sound.play(Sfx.COIN, 0.45f)
                            }
                            arrived
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val now = fly.clock
                    val target = (fly.target ?: Offset(size.width - 60f, 60f)) - fly.hostOrigin
                    val r = 11.dp.toPx()
                    for (c in fly.coins) {
                        val t = ((now - c.start) / 720f)
                        if (t < 0f) continue
                        val e = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }
                        val from = c.from - fly.hostOrigin
                        val ctrl = Offset((from.x + target.x) / 2 + c.curve * 160f, minOf(from.y, target.y) - 120f)
                        val u = 1 - e
                        val p = Offset(
                            u * u * from.x + 2 * u * e * ctrl.x + e * e * target.x,
                            u * u * from.y + 2 * u * e * ctrl.y + e * e * target.y,
                        )
                        val s = 1f - 0.35f * e
                        drawCircle(Color(0xFFB36B00), r * s, p + Offset(0f, 2f))
                        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFC93C), Color(0xFFE09A00)), p.y - r, p.y + r), r * s * 0.92f, p)
                        drawCircle(Color(0xFFFFF6D0), r * s * 0.32f, p)
                    }
                }
            }
            // toasts
            val head = toasts.queue.firstOrNull()
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(head?.id) {
                if (head != null) {
                    shown = true
                    app.sound.play(Sfx.CHEST, 0.5f)
                    delay(2600)
                    shown = false
                    delay(350)
                    toasts.queue.remove(head)
                }
            }
            AnimatedVisibility(
                shown && head != null, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                enter = slideInVertically { -it * 2 } + fadeIn(), exit = slideOutVertically { -it * 2 } + fadeOut(),
            ) { head?.let { ToastCard(it) } }
        }
    }
}

@Composable
private fun ToastCard(t: ToastData) {
    JewelPanel(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), rim = Rim.GOLD, fill = PanelFill.NIGHT, corner = 18.dp, padding = 10.dp, studs = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Text(t.icon, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = CM.GoldLight, fontFamily = DisplayFamily, fontSize = 15.sp, maxLines = 1)
                if (t.subtitle.isNotEmpty()) Text(t.subtitle, color = CM.Text, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- reward popup

/**
 * Full-screen reward moment: spinning light rays, the [icon] pops in, the amount counts up, and Collect sends coins
 * flying to the HUD (when a [GameOverlayHost] is present) before calling [onCollect].
 * Drawn in the same window (not a Dialog) so coin positions line up.
 */
@Composable
fun RewardPopup(
    title: String,
    amount: Int,
    onCollect: () -> Unit,
    subtitle: String? = null,
    buttonText: String = stringResource(R.string.shell_collect),
    icon: @Composable () -> Unit = { CoinIcon(84.dp) },
) {
    val app = LocalApp.current
    val fly = LocalCoinFly.current
    val pop = remember { Animatable(0f) }
    val count = remember { Animatable(0f) }
    var collecting by remember { mutableStateOf(false) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }
    val inf = rememberInfiniteTransition(label = "rays")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "r")
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
        if (amount > 0) count.animateTo(amount.toFloat(), tween(700))
    }
    fun collect() {
        if (collecting) return
        collecting = true
        if (fly != null && amount > 0) fly.fly(buttonCenter, (amount / 25).coerceIn(6, 18))
        onCollect()
    }
    BackHandler { collect() }
    val rays = remember {
        Path().apply {
            for (i in 0 until 14) {
                val a0 = (i * 2 * PI / 14).toFloat()
                val a1 = a0 + (PI / 14).toFloat()
                moveTo(0f, 0f); lineTo(cos(a0) * 1000f, sin(a0) * 1000f); lineTo(cos(a1) * 1000f, sin(a1) * 1000f); close()
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xD9080216))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(420.dp).rotate(rot).scale(pop.value)) {
            drawContext.transform.translate(size.width / 2, size.height / 2)
            drawPath(rays, Brush.radialGradient(listOf(CM.Gold.copy(alpha = 0.45f), Color.Transparent), Offset.Zero, size.minDimension / 2))
            drawContext.transform.translate(-size.width / 2, -size.height / 2)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            RibbonHeader(title, color = CM.Ruby, fontSize = 20.sp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.scale(pop.value), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.height(14.dp))
            if (amount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(34.dp)
                    Spacer(Modifier.width(8.dp))
                    StrokeText("+${count.value.toInt()}", fontSize = 40.sp, color = CM.GoldLight, stroke = Color(0xFF4A2A00), strokeWidth = 10f)
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = CM.Text, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.onGloballyPositioned { buttonCenter = it.boundsInRoot().center }) {
                GameButton(buttonText, { collect() }, style = ButtonStyle.GREEN, modifier = Modifier.width(220.dp), sound = Sfx.COIN)
            }
        }
    }
}
