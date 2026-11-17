package com.puzzel.lab.cardsmania.ui.multiplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.data.PlayerAvatars
import com.puzzel.lab.cardsmania.net.RoomState
import com.puzzel.lab.cardsmania.ui.components.AvatarBadge
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.components.rememberPulse
import com.puzzel.lab.cardsmania.ui.theme.CM

/** Android 17+ asks the user before an app may talk to devices on the local network. */
object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"
    val needed: Boolean get() = Build.VERSION.SDK_INT >= 37

    fun granted(context: android.content.Context): Boolean =
        !needed || ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED

    @Suppress("unused")
    private val legacy = Manifest.permission.INTERNET
}

/** Seats of a room: host, guests, bots and open seats. */
@Composable
fun SeatList(room: RoomState, myGuestId: String? = null, onKick: ((String) -> Unit)? = null) {
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHeader("Players", trailing = {
            Text("${room.players + room.bots} / ${room.seats}", color = CM.GoldLight, fontWeight = FontWeight.Black)
        })
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SeatRow(PlayerAvatars.get(room.hostAvatar), room.hostName, "Host", CM.Gold, crown = true)
            room.guests.forEach { g ->
                val me = g.id == myGuestId
                SeatRow(
                    PlayerAvatars.get(g.avatar), g.name + if (me) " (You)" else "",
                    if (g.connected) "Ready" else "Reconnecting…", if (g.connected) CM.Emerald else CM.TextMuted,
                    onKick = if (onKick != null && !room.inGame) ({ onKick(g.id) }) else null,
                )
            }
            repeat(room.bots) {
                SeatRow(null, "Bot", DifficultyInfo.label(room.botDifficulty), DifficultyInfo.color(room.botDifficulty), bot = true)
            }
            repeat(room.openSeats) { OpenSeatRow() }
        }
    }
}

@Composable
private fun SeatRow(
    emoji: String?,
    name: String,
    tag: String,
    tagColor: Color,
    crown: Boolean = false,
    bot: Boolean = false,
    onKick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bot) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SmartToy, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        } else {
            AvatarBadge(emoji ?: "🙂", size = 42.dp, ringColor = if (crown) CM.Gold else null)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                if (crown) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Star, null, tint = CM.Gold, modifier = Modifier.size(16.dp))
                }
            }
            Text(tag, color = tagColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        if (onKick != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(0x33E53935)).clickable(onClick = onKick),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, "Remove player", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun OpenSeatRow() {
    val pulse = rememberPulse(900)
    Box(Modifier.fillMaxWidth().height(58.dp)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f + 0.2f * pulse),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }
        Text(
            "Open seat — waiting for a friend (a bot plays if empty)",
            color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp)
        )
    }
}

/** One-line summary of the room's match settings. */
fun roomSummary(room: RoomState): String = if (room.isMindi) buildString {
    val m = room.mindi
    append("Mindi · ${m.players} players")
    append(if (m.players == 4) " · 2 vs 2" else if (m.players == 6) " · 3 vs 3" else " · 4 vs 4")
    if (m.doubleDeck) append(" · double deck")
    append(if (m.target <= 1) " · single hand" else " · first to ${m.target}")
} else buildString {
    append(ModeInfo.title(room.mode))
    append(" · ${room.handSize} cards")
    if (room.deckCount == 2) append(" · double deck")
    append(if (room.targetScore > 0) " · first to ${room.targetScore}" else " · single round")
}

fun rulesSummary(room: RoomState): String {
    if (room.isMindi) {
        val m = room.mindi
        return buildList {
            add(m.trumpMode.title)
            if (m.trumpMode == com.puzzel.lab.cardsmania.mindi.MindiTrumpMode.HIDE && m.mustTrumpAfterReveal) add("Must trump after reveal")
            add("${if (m.turnTimer > 0) m.turnTimer else 30}s turns")
        }.joinToString(" · ")
    }
    val r = room.rules
    val list = buildList {
        if (r.stacking) add("Stacking")
        if (r.sevenZero) add("Seven & Zero")
        if (r.drawToMatch) add("Draw to Match")
        if (r.challenge && !r.stacking) add("Challenge")
        if (r.jumpIn) add("Jump-In")
        if (r.forcedPlay) add("Forced Play")
        if (r.comboDrop) add("Combo Drop")
        if (r.powerUps) add("Power-ups")
    }
    return if (list.isEmpty()) "No house rules" else list.joinToString(" · ")
}

@Composable
fun PulseDot(color: Color = CM.Emerald) {
    val pulse = rememberPulse(700)
    Box(
        Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = 0.5f + 0.5f * pulse))
            .border(2.dp, color.copy(alpha = 0.3f), CircleShape)
    )
}
