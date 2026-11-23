package com.ngdstudio.mindimania.ui.multiplayer

import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.GameLaunch
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.audio.Track
import com.ngdstudio.mindimania.data.ModeInfo
import com.ngdstudio.mindimania.net.ClientStatus
import com.ngdstudio.mindimania.net.NetRole
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.components.rememberPulse
import com.ngdstudio.mindimania.ui.game.EndedDialog
import com.ngdstudio.mindimania.ui.setup.ModeTile
import com.ngdstudio.mindimania.ui.theme.CM

@Composable
fun GuestLobbyScreen(onLeft: () -> Unit, onGameStart: (game: com.ngdstudio.mindimania.net.RoomGame) -> Unit) {
    val app = LocalApp.current
    val mp = app.multiplayer
    val client = mp.client
    if (client == null) {
        EndedDialog(stringResource(R.string.mp_not_in_room), onOk = onLeft)
        return
    }
    val room by client.room.collectAsState()
    val status by client.status.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.music.play(Track.MENU) }
    LaunchedEffect(status) {
        if (status == ClientStatus.InGame) {
            app.sound.play(Sfx.SHUFFLE)
            val game = when (client.starts.replayCache.lastOrNull()) {
                is com.ngdstudio.mindimania.net.NetMsg.MindiStart, is com.ngdstudio.mindimania.net.NetMsg.MindiSync -> com.ngdstudio.mindimania.net.RoomGame.MINDI
                is com.ngdstudio.mindimania.net.NetMsg.BhabhiStart, is com.ngdstudio.mindimania.net.NetMsg.BhabhiSync -> com.ngdstudio.mindimania.net.RoomGame.BHABHI
                is com.ngdstudio.mindimania.net.NetMsg.TeenPattiStart, is com.ngdstudio.mindimania.net.NetMsg.TeenPattiSync -> com.ngdstudio.mindimania.net.RoomGame.TEEN_PATTI
                is com.ngdstudio.mindimania.net.NetMsg.RummyStart, is com.ngdstudio.mindimania.net.NetMsg.RummySync -> com.ngdstudio.mindimania.net.RoomGame.RUMMY
                else -> com.ngdstudio.mindimania.net.RoomGame.MANIA
            }
            when (game) {
                com.ngdstudio.mindimania.net.RoomGame.MINDI -> app.pendingMindi = com.ngdstudio.mindimania.ui.mindi.MindiLaunch(net = NetRole.GUEST)
                com.ngdstudio.mindimania.net.RoomGame.BHABHI -> app.pendingBhabhi = com.ngdstudio.mindimania.ui.bhabhi.BhabhiLaunch(net = NetRole.GUEST)
                com.ngdstudio.mindimania.net.RoomGame.TEEN_PATTI -> app.pendingTeenPatti = com.ngdstudio.mindimania.ui.teenpatti.TeenPattiLaunch(net = NetRole.GUEST)
                com.ngdstudio.mindimania.net.RoomGame.RUMMY -> app.pendingRummy = com.ngdstudio.mindimania.ui.rummy.RummyLaunch(net = NetRole.GUEST)
                com.ngdstudio.mindimania.net.RoomGame.MANIA -> app.pendingLaunch = GameLaunch(null, net = NetRole.GUEST)
            }
            onGameStart(game)
        }
    }
    BackHandler { confirmLeave = true }

    val ended = when (val s = status) {
        is ClientStatus.Ended -> s.reason
        is ClientStatus.Kicked -> stringResource(R.string.mp_kicked)
        is ClientStatus.Failed -> s.reason
        else -> null
    }

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenTitleBar(stringResource(R.string.mp_room), { confirmLeave = true })
            val r = room
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pulse by rememberPulse(900)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E6FE8), Color(0xFF2A0E5C))))
                        .border(2.dp, CM.Gold, RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.mp_youre_in), color = CM.TextDim, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                    OutlinedTitle(stringResource(R.string.mp_hosts_room, r?.hostName ?: stringResource(R.string.mp_host)), fontSize = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { scaleX = 1f + 0.03f * pulse; scaleY = scaleX }) {
                        PulseDot(CM.Gold)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.mp_waiting_host), color = CM.GoldLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (r != null) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionHeader(stringResource(R.string.mp_game))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            if (r.isMindi) com.ngdstudio.mindimania.ui.mindi.MindiGameTile(true, Modifier.fillMaxWidth()) {}
                            else if (r.isBhabhi) com.ngdstudio.mindimania.ui.bhabhi.BhabhiGameTile(true, Modifier.fillMaxWidth()) {}
                            else if (r.isTeenPatti) com.ngdstudio.mindimania.ui.teenpatti.TeenPattiGameTile(true, Modifier.fillMaxWidth()) {}
                            else if (r.isRummy) com.ngdstudio.mindimania.ui.rummy.RummyGameTile(true, Modifier.fillMaxWidth()) {}
                            else ModeTile(r.mode, true, Modifier.width(130.dp)) {}
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(roomSummary(r), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(
                            when {
                                r.isMindi -> stringResource(R.string.mp_mindi_tagline)
                                r.isBhabhi -> stringResource(R.string.mp_bhabhi_tagline)
                                r.isTeenPatti -> stringResource(R.string.mp_tp_tagline)
                                r.isRummy -> stringResource(R.string.mp_rummy_tagline)
                                else -> ModeInfo.tagline(r.mode)
                            },
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
                stringResource(R.string.mp_leave_room), { confirmLeave = true }, style = ButtonStyle.RED, icon = Icons.AutoMirrored.Rounded.ExitToApp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (confirmLeave) {
        ConfirmDialog(stringResource(R.string.mp_leave_title), stringResource(R.string.mp_leave_text), stringResource(R.string.mp_leave), onConfirm = {
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
