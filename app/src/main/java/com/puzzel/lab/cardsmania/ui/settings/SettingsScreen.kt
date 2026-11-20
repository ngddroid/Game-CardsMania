package com.puzzel.lab.cardsmania.ui.settings

import com.puzzel.lab.cardsmania.ui.design.GameIcons
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.data.Settings
import com.puzzel.lab.cardsmania.data.SortMode
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.ChoiceChip
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.GemSwitch
import com.puzzel.lab.cardsmania.ui.components.GlassPanel
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.components.SectionHeader
import com.puzzel.lab.cardsmania.ui.kit.GemDialog
import com.puzzel.lab.cardsmania.ui.kit.GemSlider
import com.puzzel.lab.cardsmania.ui.kit.ProfileCache
import com.puzzel.lab.cardsmania.ui.kit.SettingsCache
import com.puzzel.lab.cardsmania.ui.kit.pressable
import com.puzzel.lab.cardsmania.ui.kit.rememberSettings
import com.puzzel.lab.cardsmania.ui.kit.staggerIn
import com.puzzel.lab.cardsmania.ui.theme.CM
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onLegal: (String) -> Unit, onHowTo: () -> Unit, onLanguage: () -> Unit = {}) {
    val app = LocalApp.current
    val context = LocalContext.current
    val settings by rememberSettings()
    val scope = rememberCoroutineScope()
    var music by remember(settings.musicVolume) { mutableFloatStateOf(settings.musicVolume) }
    var sfx by remember(settings.sfxVolume) { mutableFloatStateOf(settings.sfxVolume) }
    var confirmReset by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    fun update(t: (Settings) -> Settings) {
        scope.launch { app.settings.update(t) }
    }

    RoyalBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ScreenTitleBar(stringResource(R.string.shell_settings), onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Group(androidx.compose.ui.res.stringResource(com.puzzel.lab.cardsmania.R.string.lang_settings_group), 0) {
                    LinkRow(
                        Icons.Rounded.Language,
                        androidx.compose.ui.res.stringResource(
                            com.puzzel.lab.cardsmania.R.string.lang_settings_row,
                            com.puzzel.lab.cardsmania.i18n.LocaleManager.current(context).nativeName,
                        ),
                        onLanguage,
                    )
                }
                Group(stringResource(R.string.shell_settings_audio), 0) {
                    SliderRow(GameIcons.Music, stringResource(R.string.shell_settings_music), music, onChange = {
                        music = it
                        app.music.volume = it
                    }, onRelease = { update { s -> s.copy(musicVolume = music) } })
                    Spacer(Modifier.height(6.dp))
                    SliderRow(GameIcons.Volume, stringResource(R.string.shell_settings_sfx), sfx, onChange = {
                        sfx = it
                        app.sound.volume = it
                    }, onRelease = {
                        app.sound.snap()
                        update { s -> s.copy(sfxVolume = sfx) }
                    })
                }
                Group(stringResource(R.string.shell_settings_gameplay), 1) {
                    ChipRow(
                        stringResource(R.string.shell_settings_speed),
                        listOf(
                            stringResource(R.string.shell_settings_speed_relaxed),
                            stringResource(R.string.shell_settings_speed_normal),
                            stringResource(R.string.shell_settings_speed_fast),
                        ),
                        settings.speed,
                    ) { i -> update { it.copy(speed = i) } }
                    Spacer(Modifier.height(12.dp))
                    val timers = listOf(0, 10, 15, 30)
                    val timerLabels = timers.map { t ->
                        if (t == 0) stringResource(R.string.shell_settings_timer_off) else stringResource(R.string.shell_settings_timer_secs, t)
                    }
                    ChipRow(stringResource(R.string.shell_settings_timer), timerLabels, timers.indexOf(settings.turnTimer).coerceAtLeast(0)) { i ->
                        update { it.copy(turnTimer = timers[i]) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ChipRow(
                        stringResource(R.string.shell_settings_sort),
                        listOf(
                            stringResource(R.string.shell_settings_sort_color),
                            stringResource(R.string.shell_settings_sort_value),
                            stringResource(R.string.shell_settings_sort_none),
                        ),
                        settings.sortMode.ordinal,
                    ) { i ->
                        update { it.copy(sortMode = SortMode.entries[i]) }
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(Icons.Rounded.ExitToApp, stringResource(R.string.shell_settings_confirm_exit), stringResource(R.string.shell_settings_confirm_exit_sub), settings.confirmExit) { v ->
                        update { it.copy(confirmExit = v) }
                    }
                }
                Group(stringResource(R.string.shell_settings_accessibility), 2) {
                    SwitchRow(GameIcons.Palette, stringResource(R.string.shell_settings_colorblind), stringResource(R.string.shell_settings_colorblind_sub), settings.colorBlind) { v -> update { it.copy(colorBlind = v) } }
                    SwitchRow(Icons.Rounded.ZoomIn, stringResource(R.string.shell_settings_large_cards), stringResource(R.string.shell_settings_large_cards_sub), settings.largeCards) { v -> update { it.copy(largeCards = v) } }
                    SwitchRow(Icons.Rounded.Accessibility, stringResource(R.string.shell_settings_reduce_motion), stringResource(R.string.shell_settings_reduce_motion_sub), settings.reduceMotion) { v -> update { it.copy(reduceMotion = v) } }
                    SwitchRow(Icons.Rounded.PanTool, stringResource(R.string.shell_settings_left_handed), stringResource(R.string.shell_settings_left_handed_sub), settings.leftHanded) { v -> update { it.copy(leftHanded = v) } }
                    SwitchRow(GameIcons.Vibrate, stringResource(R.string.shell_settings_haptics), stringResource(R.string.shell_settings_haptics_sub), settings.haptics) { v ->
                        app.haptics.enabled = v
                        if (v) app.haptics.medium()
                        update { it.copy(haptics = v) }
                    }
                }
                Group(stringResource(R.string.shell_settings_support), 3) {
                    LinkRow(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.shell_how_to_play), onHowTo)
                    LinkRow(GameIcons.Star, stringResource(R.string.shell_settings_rate)) { openStore(context) }
                    LinkRow(GameIcons.Share, stringResource(R.string.shell_settings_share)) { share(context) }
                }
                Group(stringResource(R.string.shell_settings_legal), 4) {
                    LinkRow(GameIcons.Doc, stringResource(R.string.shell_terms)) { onLegal("terms") }
                    LinkRow(GameIcons.Shield, stringResource(R.string.shell_privacy)) { onLegal("privacy") }
                }
                Group(stringResource(R.string.shell_settings_danger), 5) {
                    Text(
                        stringResource(R.string.shell_settings_danger_body),
                        color = CM.TextDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    GameButton(stringResource(R.string.shell_settings_reset_btn), { confirmReset = true }, style = ButtonStyle.RED, modifier = Modifier.fillMaxWidth(), height = 50.dp, fontSize = 16.sp)
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp).staggerIn(6), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Layers, null, tint = CM.Gold, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Cards Mania", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.shell_settings_version, version), color = CM.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.shell_settings_footer), color = CM.TextMuted, fontSize = 12.sp)
                }
            }
        }
    }

    if (confirmReset) {
        GemDialog(title = stringResource(R.string.shell_reset_title), onDismiss = { confirmReset = false }) {
            Text(
                stringResource(R.string.shell_reset_body),
                color = CM.Text,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(stringResource(R.string.shell_cancel), { confirmReset = false }, style = ButtonStyle.GLASS, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 16.sp)
                GameButton(
                    stringResource(R.string.shell_reset_confirm),
                    onClick = {
                        scope.launch {
                            app.profile.reset()
                            app.savedGames.clear()
                            ProfileCache.last = Profile()
                            SettingsCache.last = Settings()
                            app.sound.play(Sfx.OVERLOAD, 0.7f)
                            confirmReset = false
                            onBack()
                        }
                    },
                    style = ButtonStyle.RED,
                    modifier = Modifier.weight(1f),
                    height = 50.dp,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun Group(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().staggerIn(index)) {
        Spacer(Modifier.height(14.dp))
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        GlassPanel(Modifier.fillMaxWidth(), padding = 14.dp, content = content)
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5A2DAA), Color(0xFF2A0E5C))))
            .border(1.dp, CM.Gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = CM.GoldLight, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun SliderRow(icon: ImageVector, title: String, value: Float, onChange: (Float) -> Unit, onRelease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", color = CM.GoldLight, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
    GemSlider(value, onChange, Modifier.padding(start = 46.dp), onRelease = onRelease)
}

@Composable
private fun ChipRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            ChoiceChip(o, i == selected, { onSelect(i) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = CM.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        GemSwitch(checked, onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Icon(GameIcons.Forward, null, tint = CM.TextDim, modifier = Modifier.size(24.dp))
    }
}

private fun openStore(context: Context) {
    val pkg = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun share(context: Context) {
    val pkg = context.packageName
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Cards Mania")
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.shell_share_text, "https://play.google.com/store/apps/details?id=$pkg"))
    }
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.shell_share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
