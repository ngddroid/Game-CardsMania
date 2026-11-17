package com.puzzel.lab.cardsmania.ui.multiplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.net.NetworkInfo
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.rememberPulse
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MultiplayerHubScreen(onBack: () -> Unit, onHosted: () -> Unit, onJoin: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(NetworkInfo.link(context)) }
    var permitted by remember { mutableStateOf(LocalNetworkPermission.granted(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permitted = ok
        if (ok) pendingAction?.invoke() else error = "Cards Mania needs permission to find phones on your Wi-Fi."
        pendingAction = null
    }

    fun withPermission(action: () -> Unit) {
        if (LocalNetworkPermission.granted(context)) action() else {
            pendingAction = action
            launcher.launch(LocalNetworkPermission.NAME)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            link = NetworkInfo.link(context)
            permitted = LocalNetworkPermission.granted(context)
            delay(2000)
        }
    }

    RoyalBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenTitleBar("Multiplayer", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WifiHero()
                OutlinedTitle("Play with friends", fontSize = 28.sp)
                Text(
                    "Everyone joins the same Wi-Fi. One phone hosts a room, the others join it — no internet needed.",
                    color = CM.Text, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp)
                )
                WifiStatus(link)
                ChoiceCard(
                    title = "Host a Room",
                    subtitle = "You choose the mode, seats and bots",
                    icon = Icons.Rounded.Router,
                    colors = listOf(Color(0xFFFFC93C), Color(0xFFE08A00)),
                    textColor = Color(0xFF3A1F00),
                    enabled = !busy,
                ) {
                    error = null
                    withPermission {
                        scope.launch {
                            busy = true
                            val p = app.profile.current()
                            val err = app.multiplayer.hostRoom(p)
                            busy = false
                            if (err != null) {
                                error = err
                                app.sound.play(Sfx.ERROR)
                            } else onHosted()
                        }
                    }
                }
                ChoiceCard(
                    title = "Join a Room",
                    subtitle = "Find a room nearby or enter its code",
                    icon = Icons.Rounded.Groups,
                    colors = listOf(Color(0xFF6FB6FF), Color(0xFF1E6FE8)),
                    textColor = Color.White,
                    enabled = !busy,
                ) {
                    error = null
                    withPermission(onJoin)
                }
                error?.let {
                    Text(it, color = Color(0xFFFF8A8A), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (!permitted && LocalNetworkPermission.needed) {
                    Text(
                        "Android will ask to let Cards Mania find devices on your network. It's only used to connect phones in the same room.",
                        color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("HOW IT WORKS", color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "1. Connect every phone to the same Wi-Fi (or to one phone's hotspot).",
                        "2. One player taps Host a Room and picks the mode, seats and bots.",
                        "3. Friends tap Join a Room and pick the room — or type its code.",
                        "4. The host taps Start. Up to 6 players, mixed with bots.",
                    ).forEach {
                        Text(it, color = CM.Text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WifiHero() {
    val inf = rememberInfiniteTransition(label = "wifi")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t")
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                drawCircle(CM.Gold.copy(alpha = (1f - p) * 0.55f), radius = size.minDimension / 2 * p, style = Stroke(3.dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFF6A3FE0), Color(0xFF2A0E5C)), center, size.minDimension * 0.28f), radius = size.minDimension * 0.27f)
            drawCircle(CM.GoldLight, radius = size.minDimension * 0.27f, center = Offset(center.x, center.y), style = Stroke(3.dp.toPx()))
        }
        Icon(Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun WifiStatus(link: NetworkInfo.Link) {
    val ok = link != NetworkInfo.Link.NONE
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (ok) Color(0x3319C37D) else Color(0x33E53935))
            .border(1.dp, if (ok) CM.Emerald else CM.Ruby, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (ok) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, null, tint = if (ok) CM.Emerald else CM.Ruby, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            when (link) {
                NetworkInfo.Link.WIFI -> "Connected to Wi-Fi"
                NetworkInfo.Link.ETHERNET -> "Connected to a local network"
                NetworkInfo.Link.HOTSPOT -> "Hotspot is on — friends can join it"
                NetworkInfo.Link.NONE -> "Not on Wi-Fi — connect to play together"
            },
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val app = LocalApp.current
    val pulse = rememberPulse(1400)
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().scale(1f + 0.01f * pulse)
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(2.dp, Color.White.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled) {
                app.sound.play(Sfx.SELECT)
                app.haptics.tick()
                onClick()
            }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(34.dp).rotate(0f))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontWeight = FontWeight.Black, fontSize = 21.sp)
            Text(subtitle, color = textColor.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
