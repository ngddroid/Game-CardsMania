package com.puzzel.lab.cardsmania.ui.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.ui.kit.rememberSettings
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily

/** Colour identity of each dock tab (gem-cut tile when selected). */
private data class DockColors(val top: Color, val bottom: Color, val rim: Color, val glow: Color)

private val dockPalette = listOf(
    DockColors(Color(0xFFFFC24A), Color(0xFFE5701A), Color(0xFFFFE7A3), Color(0xFFFFA22E)), // Lobby: amber
    DockColors(Color(0xFFFF6A8E), Color(0xFFC2185B), Color(0xFFFFC2D2), Color(0xFFFF3D7F)), // Events: ruby
    DockColors(Color(0xFF4BE39A), Color(0xFF0E8F4F), Color(0xFFB9FFD9), Color(0xFF22D67E)), // Collection: emerald
    DockColors(Color(0xFF5AB2FF), Color(0xFF1E5FD1), Color(0xFFC8E4FF), Color(0xFF3D8BFF)), // Profile: sapphire
)

private val DockBody = Brush.verticalGradient(listOf(Color(0xFF241252), Color(0xFF140838), Color(0xFF0A0420)))

/**
 * Segmented game dock. Every tab is a panel separated by bevelled dividers. The selected tab widens, rises above the
 * bar as a gem-coloured tile (each tab has its own colour) with a big icon and a bold label; the others shrink to
 * muted icons. Springy width/raise animation, press squash, pulsing badge.
 */
@Composable
fun GameDock(
    tabs: List<GameTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Set<Int> = emptySet(),
) {
    val reduceMotion = rememberSettings().value.reduceMotion
    Box(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val rimH = 3.dp.toPx()
                val rim = Brush.horizontalGradient(listOf(Color(0xFF7A4E00), Color(0xFFFFE89A), Color(0xFFFFC93C), Color(0xFFFFE89A), Color(0xFF7A4E00)))
                val shine = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), rimH, rimH + 22.dp.toPx())
                val shadow = Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)), -14.dp.toPx(), 0f)
                onDrawBehind {
                    drawRect(shadow, topLeft = Offset(0f, -14.dp.toPx()), size = Size(size.width, 14.dp.toPx()))
                    drawRect(DockBody)
                    drawRect(shine, topLeft = Offset(0f, rimH), size = Size(size.width, 22.dp.toPx()))
                    drawRect(rim, size = Size(size.width, rimH))
                    drawRect(Color(0x66000000), topLeft = Offset(0f, rimH), size = Size(size.width, 1.dp.toPx()))
                }
            }
            .navigationBarsPadding()
            .padding(bottom = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(74.dp)) {
            tabs.forEachIndexed { i, tab ->
                val target = if (i == selected) 1f else 0f
                val sel by animateFloatAsState(target, spring(dampingRatio = 0.62f, stiffness = 420f), label = "sel$i")
                val weight = 1f + 0.7f * sel
                if (i > 0) Divider()
                DockSlot(
                    tab = tab,
                    colors = dockPalette[i % dockPalette.size],
                    sel = sel,
                    selectedNow = i == selected,
                    badge = i in badges && i != selected,
                    reduceMotion = reduceMotion,
                    onTap = { onSelect(i) },
                    modifier = Modifier.weight(weight).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxHeight().padding(vertical = 14.dp).size(width = 2.dp, height = 0.dp)
            .drawWithCache {
                onDrawBehind {
                    drawRect(Color(0x99000000), size = Size(1.dp.toPx(), size.height))
                    drawRect(Color(0x26FFFFFF), topLeft = Offset(1.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height))
                }
            }
    )
}

@Composable
private fun DockSlot(
    tab: GameTab,
    colors: DockColors,
    sel: Float,
    selectedNow: Boolean,
    badge: Boolean,
    reduceMotion: Boolean,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "press")
    val inf = rememberInfiniteTransition(label = "dock")
    val glowPulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    val badgePulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "badge")
    val raise = 10.dp * sel // the selected tile rises above the bar
    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true
                val ok = tryAwaitRelease()
                pressed = false
                if (ok) onTap()
            })
        },
        contentAlignment = Alignment.Center,
    ) {
        // the raised gem tile (drawn even while animating out, fading with `sel`)
        if (sel > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .offset(y = -raise)
                    .graphicsLayer { alpha = sel.coerceIn(0f, 1f); scaleX = press; scaleY = press }
                    .drawWithCache {
                        val r = 16.dp.toPx()
                        val w = size.width
                        val h = size.height + raise.toPx() * 0.6f
                        val body = Brush.verticalGradient(listOf(colors.top, colors.bottom), 0f, h)
                        val gloss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.0f)), 0f, h * 0.5f)
                        val outer = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(r))) }
                        onDrawBehind {
                            // soft glow halo (kept tight so it doesn't wash over the content above)
                            drawRoundRect(
                                Brush.radialGradient(listOf(colors.glow.copy(alpha = 0.35f + 0.2f * glowPulse * (if (reduceMotion) 0f else 1f)), Color.Transparent), Offset(w / 2, h * 0.55f), w * 0.7f),
                                topLeft = Offset(-w * 0.1f, -h * 0.05f), size = Size(w * 1.2f, h * 1.15f), cornerRadius = CornerRadius(r * 2),
                            )
                            // drop shadow + darker base (3D thickness)
                            drawRoundRect(Color(0x88000000), topLeft = Offset(0f, 5.dp.toPx()), size = Size(w, h), cornerRadius = CornerRadius(r))
                            drawRoundRect(colors.bottom.copy(red = colors.bottom.red * 0.6f, green = colors.bottom.green * 0.6f, blue = colors.bottom.blue * 0.6f), topLeft = Offset(0f, 3.dp.toPx()), size = Size(w, h), cornerRadius = CornerRadius(r))
                            drawRoundRect(body, size = Size(w, h), cornerRadius = CornerRadius(r))
                            clipPath(outer) {
                                // diagonal facet + top gloss
                                drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.62f, 0f); lineTo(0f, h * 0.7f); close() }, Color.White.copy(alpha = 0.10f))
                                drawRect(gloss, size = Size(w, h * 0.5f))
                            }
                            drawRoundRect(colors.rim, size = Size(w, h), cornerRadius = CornerRadius(r), style = Stroke(2.dp.toPx()))
                            drawRoundRect(Color(0x55000000), topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()), size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()), cornerRadius = CornerRadius(r - 1.5.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                    }
            )
        }
        // icon + label
        Column(
            Modifier.fillMaxSize().offset(y = -raise * 0.85f).graphicsLayer { scaleX = press; scaleY = press },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val iconSize = 24.dp + 10.dp * sel
                // round socket behind idle icons so they read as buttons
                if (sel < 0.99f) Box(
                    Modifier.size(40.dp).graphicsLayer { alpha = 1f - sel }
                        .drawWithCache {
                            val fill = Brush.verticalGradient(listOf(Color(0xFF0A0420), Color(0xFF2E1A66)))
                            onDrawBehind {
                                drawCircle(fill)
                                drawCircle(Color(0x40FFFFFF), style = Stroke(1.dp.toPx()), center = center.copy(y = center.y + 0.5.dp.toPx()))
                                drawCircle(Color(0x99000000), style = Stroke(1.dp.toPx()))
                            }
                        }
                )
                // icon shadow
                Icon(
                    tab.icon, null, tint = Color(0x66000000),
                    modifier = Modifier.size(iconSize).offset(y = 2.dp),
                )
                Icon(
                    tab.icon, tab.label,
                    tint = lerp(Color(0xFFC9BCF2), Color.White, sel),
                    modifier = Modifier.size(iconSize),
                )
                if (badge) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp)
                            .graphicsLayer {
                                val k = if (reduceMotion) 1f else 1f + 0.18f * badgePulse
                                scaleX = k; scaleY = k
                            }
                            .size(16.dp)
                            .drawWithCache {
                                onDrawBehind {
                                    drawCircle(Color(0x66000000), size.minDimension / 2, center.copy(y = center.y + 1.dp.toPx()))
                                    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFF6B7A), Color(0xFFD7193A))), size.minDimension / 2)
                                    drawCircle(Color.White, size.minDimension / 2, style = Stroke(2.dp.toPx()))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text("!", color = Color.White, fontFamily = DisplayFamily, fontSize = 10.sp) }
                }
            }
            if (selectedNow || sel > 0.3f) {
                Spacer(Modifier.height(2.dp))
                StrokeText(
                    tab.label.uppercase(),
                    Modifier.graphicsLayer { alpha = sel.coerceIn(0f, 1f) },
                    fontSize = 13.sp, color = Color.White, stroke = colors.bottom.copy(red = colors.bottom.red * 0.45f, green = colors.bottom.green * 0.45f, blue = colors.bottom.blue * 0.45f),
                    strokeWidth = 5f, textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    tab.label, color = Color(0xFFB3A4E0), fontFamily = DisplayFamily, fontSize = 11.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
