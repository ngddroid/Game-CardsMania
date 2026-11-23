package com.ngdstudio.mindimania.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.Leveling
import com.ngdstudio.mindimania.data.PlayerAvatars
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.RoundIconButton
import com.ngdstudio.mindimania.ui.components.formatNumber
import com.ngdstudio.mindimania.ui.theme.CM
import com.ngdstudio.mindimania.ui.theme.DisplayFamily

/**
 * Top game HUD: avatar with a level ring and level badge, the player name, a coin capsule with a "+" bubble
 * (lands flying coins and bounces), and optional help / settings buttons.
 */
@Composable
fun HudBar(
    profile: Profile,
    modifier: Modifier = Modifier,
    onAvatar: () -> Unit = {},
    onCoins: () -> Unit = {},
    onSettings: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
) {
    val app = LocalApp.current
    val (prog, _, _) = Leveling.progress(profile.xp)
    val fly = LocalCoinFly.current
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(fly?.landed) {
        if ((fly?.landed ?: 0) > 0) {
            bounce.snapTo(1f)
            bounce.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 700f))
        }
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // avatar + level ring
        Box(
            Modifier.size(58.dp).pointerInput(Unit) { detectTapGestures { app.sound.play(Sfx.TAP); onAvatar() } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                drawArc(Color(0x55000000), 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                drawArc(
                    Brush.sweepGradient(listOf(Color(0xFF8B5CF6), Color(0xFFFF5FA2), Color(0xFFFFC93C), Color(0xFF8B5CF6))),
                    -90f, 360f * prog.coerceIn(0.03f, 1f), false,
                    topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            AvatarBadge(PlayerAvatars.get(profile.avatar), size = 48.dp, frame = profile.avatarFrame)
            LevelGem(profile.level, Modifier.align(Alignment.BottomCenter).offset(y = 6.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name.ifBlank { stringResource(R.string.shell_player_default) }, color = Color.White, fontFamily = DisplayFamily, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.shell_hud_level, profile.level), color = CM.TextDim, fontSize = 11.sp, maxLines = 1)
        }
        // coin capsule
        Row(
            Modifier
                .graphicsLayer { val s = 1f + bounce.value * 0.12f; scaleX = s; scaleY = s }
                .coinFlyTarget()
                .height(38.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(0xCC1A0844), Color(0xEE0A0320))))
                .border(1.5.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), RoundedCornerShape(50))
                .pointerInput(Unit) { detectTapGestures { app.sound.play(Sfx.TAP); onCoins() } }
                .padding(start = 3.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinIcon(30.dp)
            Spacer(Modifier.width(6.dp))
            Text(formatNumber(profile.coins), color = CM.GoldLight, fontFamily = DisplayFamily, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF5BF0A2), Color(0xFF13B36A))))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(GameIcons.Plus, stringResource(R.string.shell_hud_get_coins), tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        if (onHelp != null) {
            Spacer(Modifier.width(6.dp))
            RoundIconButton(GameIcons.Help, onHelp, size = 40.dp, contentDescription = stringResource(R.string.shell_how_to_play))
        }
        if (onSettings != null) {
            Spacer(Modifier.width(6.dp))
            RoundIconButton(GameIcons.Settings, onSettings, size = 40.dp, contentDescription = stringResource(R.string.shell_settings))
        }
    }
}

/** Diamond-shaped level badge. */
@Composable
fun LevelGem(level: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 30.dp, height = 22.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = Path().apply { moveTo(w * 0.18f, 0f); lineTo(w * 0.82f, 0f); lineTo(w, h / 2); lineTo(w * 0.82f, h); lineTo(w * 0.18f, h); lineTo(0f, h / 2); close() }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFD9C6FF), Color(0xFF8B5CF6), Color(0xFF4A1F9A))))
            drawPath(p, Color.White.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
        }
        Text("$level", color = Color.White, fontFamily = DisplayFamily, fontSize = 12.sp)
    }
}

data class GameTab(val icon: ImageVector, val label: String)

/** The lobby's bottom navigation: see [GameDock] (segmented dock with a raised gem tile for the selected tab). */
@Composable
fun GameTabBar(
    tabs: List<GameTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Set<Int> = emptySet(),
) = GameDock(tabs, selected, onSelect, modifier, badges)
