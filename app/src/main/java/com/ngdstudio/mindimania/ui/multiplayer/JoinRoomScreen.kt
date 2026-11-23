package com.ngdstudio.mindimania.ui.multiplayer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.ModeInfo
import com.ngdstudio.mindimania.net.FoundRoom
import com.ngdstudio.mindimania.net.RoomCode
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.launch

@Composable
fun JoinRoomScreen(onBack: () -> Unit, onJoined: () -> Unit) {
    val app = LocalApp.current
    val mp = app.multiplayer
    val scope = rememberCoroutineScope()
    val rooms by mp.discovery.rooms.collectAsState()
    var codeField by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    val code = codeField.text
    var joining by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    DisposableEffect(Unit) {
        mp.discovery.startDiscovery()
        onDispose { mp.discovery.stopDiscovery() }
    }

    fun join(ip: String, port: Int, key: String) {
        if (joining != null) return
        joining = key
        error = null
        scope.launch {
            val p = app.profile.current()
            val err = mp.join(ip, port, p)
            joining = null
            if (err == null) {
                app.sound.play(Sfx.TURN)
                onJoined()
            } else {
                error = err
                app.sound.play(Sfx.ERROR)
            }
        }
    }

    RoyalBackground(rays = false) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenTitleBar(stringResource(R.string.mp_join_room), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SectionHeader(stringResource(R.string.mp_rooms_on_wifi))
                if (rooms.isEmpty()) {
                    Radar()
                } else {
                    rooms.sortedBy { it.name }.forEach { r -> RoomCard(r, joining == r.key) { join(r.host, r.port, r.key) } }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.mp_have_code))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = codeField,
                            onValueChange = { v ->
                                val formatted = formatCode(v.text)
                                // keep the cursor after the same number of code characters (the dash is inserted automatically)
                                val charsBefore = v.text.take(v.selection.end).count { it.isLetterOrDigit() }.coerceAtMost(8)
                                val pos = if (charsBefore > 4) charsBefore + 1 else charsBefore
                                codeField = androidx.compose.ui.text.input.TextFieldValue(formatted, androidx.compose.ui.text.TextRange(pos.coerceAtMost(formatted.length)))
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, letterSpacing = 3.sp),
                            cursorBrush = SolidColor(CM.Gold),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = {
                                focus.clearFocus()
                                RoomCode.decode(code)?.let { (ip, port) -> join(ip, port, "code") } ?: run { error = Strings.get(R.string.mp_bad_code) }
                            }),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x33000000))
                                .border(1.5.dp, CM.Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (code.isEmpty()) Text("XXXX-XXXX", color = Color(0x55FFFFFF), fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                    inner()
                                }
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        GameButton(stringResource(R.string.mp_join), {
                            focus.clearFocus()
                            val d = RoomCode.decode(code)
                            if (d == null) {
                                error = Strings.get(R.string.mp_bad_code)
                                app.sound.play(Sfx.ERROR)
                            } else join(d.first, d.second, "code")
                        }, style = ButtonStyle.GREEN, height = 48.dp, fontSize = 16.sp, modifier = Modifier.width(92.dp), enabled = joining == null)
                    }
                    Text(stringResource(R.string.mp_code_hint), color = CM.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (joining == "code") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = CM.Gold, strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.mp_joining), color = CM.Text, fontWeight = FontWeight.Bold)
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF8A8A), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun formatCode(input: String): String {
    val clean = input.uppercase().filter { it.isLetterOrDigit() }.take(8)
    return if (clean.length > 4) clean.substring(0, 4) + "-" + clean.substring(4) else clean
}

@Composable
private fun RoomCard(r: FoundRoom, joining: Boolean, onJoin: () -> Unit) {
    val room = r.room
    val full = room != null && room.openSeats <= 0 && !room.inGame
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0x40FFFFFF), Color(0x18FFFFFF))))
            .border(1.5.dp, if (full) Color(0x33FFFFFF) else CM.Gold.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarBadge(com.ngdstudio.mindimania.data.PlayerAvatars.get(room?.hostAvatar ?: 0), size = 50.dp, ringColor = CM.Gold)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mp_hosts_room, room?.hostName ?: r.name), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
            if (room != null) {
                if (room.isMindi) Text(stringResource(R.string.mp_mindi_room_line, room.seats, room.mindi.trumpMode.title), color = Color(0xFF3DDC84), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else if (room.isBhabhi) Text(stringResource(R.string.mp_bhabhi_room_line, room.seats), color = Color(0xFFFF6FB5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else if (room.isTeenPatti) Text(stringResource(R.string.mp_tp_room_line, room.seats), color = Color(0xFFFF8A4C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else if (room.isRummy) Text(stringResource(R.string.mp_rummy_room_line, room.seats), color = Color(0xFF3FD08A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else Text(ModeInfo.title(room.mode), color = ModeInfo.colors(room.mode)[0], fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    when {
                        room.inGame -> stringResource(R.string.mp_in_progress)
                        room.openSeats <= 0 -> stringResource(R.string.mp_room_full)
                        else -> stringResource(
                            R.string.mp_room_line,
                            pluralStringResource(R.plurals.mp_players_count, room.players, room.players),
                            pluralStringResource(R.plurals.mp_seats_open_count, room.openSeats, room.openSeats),
                        )
                    },
                    color = CM.TextDim, fontSize = 12.sp
                )
            } else {
                Text(stringResource(R.string.mp_checking_room), color = CM.TextDim, fontSize = 12.sp)
            }
        }
        if (joining) {
            CircularProgressIndicator(color = CM.Gold, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        } else {
            GameButton(
                if (room?.inGame == true) stringResource(R.string.mp_rejoin) else stringResource(R.string.mp_join), onJoin,
                style = if (full) ButtonStyle.GLASS else if (room?.inGame == true) ButtonStyle.PURPLE else ButtonStyle.GREEN,
                enabled = !full, height = 44.dp, fontSize = 15.sp, modifier = Modifier.width(92.dp),
            )
        }
    }
}

@Composable
private fun Radar() {
    val inf = rememberInfiniteTransition(label = "radar")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                for (i in 1..3) drawCircle(Color.White.copy(alpha = 0.12f), radius = size.minDimension / 2 * i / 3f, style = Stroke(1.5.dp.toPx()))
                for (i in 0 until 2) {
                    val p = (t + i * 0.5f) % 1f
                    drawCircle(CM.Gold.copy(alpha = (1 - p) * 0.6f), radius = size.minDimension / 2 * p, style = Stroke(3.dp.toPx()))
                }
                drawCircle(CM.Gold, radius = 6.dp.toPx())
            }
        }
        Text(stringResource(R.string.mp_looking), color = CM.GoldLight, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.mp_looking_hint),
            color = CM.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
