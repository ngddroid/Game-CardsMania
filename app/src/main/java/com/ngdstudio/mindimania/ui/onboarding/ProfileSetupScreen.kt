package com.ngdstudio.mindimania.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.AvatarFrame
import com.ngdstudio.mindimania.data.PlayerAvatars
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.rememberPulse
import com.ngdstudio.mindimania.ui.kit.rememberProfile
import com.ngdstudio.mindimania.ui.kit.staggerIn
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.launch

const val MAX_NAME = 14

@Composable
fun ProfileSetupScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val profile by rememberProfile()
    val scope = rememberCoroutineScope()
    RoyalBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            OutlinedTitle(stringResource(R.string.shell_setup_title), fontSize = 32.sp, outlineWidth = 11f, modifier = Modifier.staggerIn(0))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.shell_setup_sub),
                color = CM.TextDim,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggerIn(1)
            )
            Spacer(Modifier.height(18.dp))
            GlassPanel(Modifier.fillMaxWidth().staggerIn(2), padding = 18.dp) {
                ProfileEditorContent(
                    initialName = if (profile.profileCreated) profile.name else "",
                    initialAvatar = profile.avatar,
                    confirmText = stringResource(R.string.shell_setup_lets_go),
                    frame = AvatarFrame.GOLD,
                    onSave = { name, avatar ->
                        scope.launch {
                            app.sound.play(Sfx.WIN, 0.5f)
                            app.profile.update { it.copy(name = name, avatar = avatar, profileCreated = true) }
                            onDone()
                        }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Shared name + avatar editor (used by first-run setup and the Profile tab). */
@Composable
fun ProfileEditorContent(
    initialName: String,
    initialAvatar: Int,
    confirmText: String,
    frame: AvatarFrame,
    onSave: (name: String, avatar: Int) -> Unit,
) {
    val app = LocalApp.current
    var name by remember { mutableStateOf(initialName.take(MAX_NAME)) }
    var avatar by remember { mutableIntStateOf(initialAvatar) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val pulse by rememberPulse(1400)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarBadge(PlayerAvatars.get(avatar), size = 104.dp, frame = frame, glow = 0.4f + 0.5f * pulse)
        Spacer(Modifier.height(14.dp))
        val shape = RoundedCornerShape(18.dp)
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME).replace("\n", "") },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(CM.Gold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0x55000000), Color(0x33000000))))
                        .border(
                            2.dp,
                            if (focused) Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)) else Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x22FFFFFF))),
                            shape
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (name.isEmpty()) {
                        Text(stringResource(R.string.shell_name_hint), color = CM.TextMuted, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    inner()
                }
            }
        )
        Text(
            "${name.length}/$MAX_NAME",
            color = CM.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 6.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.shell_choose_avatar), color = CM.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        PlayerAvatars.all.indices.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { i ->
                    val selected = i == avatar
                    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(0.4f, 500f), label = "av")
                    Box(
                        Modifier
                            .padding(5.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = if (selected) 1f else 0.82f
                            }
                            .pointerInput(i) {
                                detectTapGestures {
                                    avatar = i
                                    app.sound.play(Sfx.SELECT)
                                    app.haptics.tick()
                                }
                            }
                    ) {
                        AvatarBadge(
                            PlayerAvatars.get(i),
                            size = 52.dp,
                            frame = if (selected) AvatarFrame.GOLD else AvatarFrame.BRONZE,
                            ringColor = if (selected) null else Color(0x66FFFFFF),
                            glow = if (selected) 0.6f else 0f,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val valid = name.isNotBlank()
        GameButton(
            confirmText,
            onClick = {
                focus.clearFocus()
                onSave(name.trim(), avatar)
            },
            enabled = valid,
            style = ButtonStyle.GREEN,
            modifier = Modifier.fillMaxWidth(),
            height = 58.dp,
            fontSize = 20.sp,
        )
        if (!valid) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.shell_name_required), color = CM.TextDim, fontSize = 13.sp)
        }
    }
}
