package com.puzzel.lab.cardsmania.ui.multiplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.GameLaunch
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.audio.Track
import com.puzzel.lab.cardsmania.data.ModeInfo
import com.puzzel.lab.cardsmania.net.ClientStatus
import com.puzzel.lab.cardsmania.net.NetRole
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.components.rememberPulse
import com.puzzel.lab.cardsmania.ui.game.EndedDialog
import com.puzzel.lab.cardsmania.ui.setup.ModeTile
import com.puzzel.lab.cardsmania.ui.theme.CM

@Composable
fun GuestLobbyScreen(onLeft: () -> Unit, onGameStart: (mindi: Boolean) -> Unit) {
    val app = LocalApp.current
    val mp = app.multiplayer
    val client = mp.client
    if (client == null) {
        EndedDialog("You're not in a room any more.", onOk = onLeft)
        return
    }
    val room by client.room.collectAsState()
    val status by client.status.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.music.play(Track.MENU) }
    LaunchedEffect(status) {
        if (status == ClientStatus.InGame) {
            app.sound.play(Sfx.SHUFFLE)
            val start = client.starts.replayCache.lastOrNull()
            val mindi = start is com.puzzel.lab.cardsmania.net.NetMsg.MindiStart || start is com.puzzel.lab.cardsmania.net.NetMsg.MindiSync
            if (mindi) app.pendingMindi = com.puzzel.lab.cardsmania.ui.mindi.MindiLaunch(net = NetRole.GUEST)
            else app.pendingLaunch = GameLaunch(null, net = NetRole.GUEST)
            onGameStart(mindi)
        }
    }
    BackHandler { confirmLeave = true }

    val ended = when (val s = status) {
        is ClientStatus.Ended -> s.reason
        is ClientStatus.Kicked -> "The host removed you from the room."
        is ClientStatus.Failed -> s.reason
        else -> null
    }

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenTitleBar("Room", { confirmLeave = true })
            val r = room
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pulse = rememberPulse(900)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E6FE8), Color(0xFF2A0E5C))))
                        .border(2.dp, CM.Gold, RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("YOU'RE IN!", color = CM.TextDim, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                    OutlinedTitle("${r?.hostName ?: "Host"}'s room", fontSize = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.scale(1f + 0.03f * pulse)) {
                        PulseDot(CM.Gold)
                        Spacer(Modifier.width(8.dp))
                        Text("Waiting for the host to start…", color = CM.GoldLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (r != null) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionHeader("Game")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            if (r.isMindi) com.puzzel.lab.cardsmania.ui.mindi.MindiGameTile(true, Modifier.fillMaxWidth()) {}
                            else ModeTile(r.mode, true, Modifier.width(130.dp)) {}
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(roomSummary(r), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (r.isMindi) "Two teams, alternate seats. Win 3 of the 4 tens to take the hand." else ModeInfo.tagline(r.mode),
                            color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(rulesSummary(r), color = CM.Aqua, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    SeatList(r, myGuestId = client.guestId)
                }
                Spacer(Modifier.height(4.dp))
            }
            GameButton(
                "Leave Room", { confirmLeave = true }, style = ButtonStyle.RED, icon = Icons.AutoMirrored.Rounded.ExitToApp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (confirmLeave) {
        ConfirmDialog("Leave room?", "You can join again while the host is still in the lobby.", "Leave", onConfirm = {
            confirmLeave = false
            mp.leaveRoom()
            onLeft()
        }, onDismiss = { confirmLeave = false })
    }
    ended?.let { reason ->
        EndedDialog(reason, onOk = {
            mp.leaveRoom()
            onLeft()
        })
    }
}
