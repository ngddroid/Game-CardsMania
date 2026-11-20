package com.puzzel.lab.cardsmania.ui.language

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzel.lab.cardsmania.LocalApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.i18n.AppLanguage
import com.puzzel.lab.cardsmania.i18n.LocaleManager
import com.puzzel.lab.cardsmania.ui.components.ButtonStyle
import com.puzzel.lab.cardsmania.ui.components.GameButton
import com.puzzel.lab.cardsmania.ui.components.OutlinedTitle
import com.puzzel.lab.cardsmania.ui.components.RoyalBackground
import com.puzzel.lab.cardsmania.ui.components.ScreenTitleBar
import com.puzzel.lab.cardsmania.ui.kit.pressable
import com.puzzel.lab.cardsmania.ui.kit.staggerIn
import com.puzzel.lab.cardsmania.ui.theme.CM
import com.puzzel.lab.cardsmania.ui.theme.DisplayFamily

/**
 * Pick English, Hindi or Gujarati. Shown once after the splash on first launch ([firstRun]: no back button) and from
 * Settings. [onDone] receives true when the language changed (the caller then recreates the activity).
 */
@Composable
fun LanguageScreen(firstRun: Boolean, onBack: () -> Unit, onDone: (changed: Boolean) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val before = remember { LocaleManager.current(ctx) }
    var picked by remember { mutableStateOf(before) }
    if (firstRun) BackHandler { /* a language must be chosen first */ }

    RoyalBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (!firstRun) ScreenTitleBar(stringResource(R.string.lang_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (firstRun) {
                    Spacer(Modifier.height(36.dp))
                    Text("🌐", fontSize = 56.sp, modifier = Modifier.staggerIn(0))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTitle(stringResource(R.string.lang_title), fontSize = 32.sp, modifier = Modifier.staggerIn(0))
                    Spacer(Modifier.height(6.dp))
                    // the same question in every language, so everyone can read it before choosing
                    Text(
                        "Choose your language · अपनी भाषा चुनें · તમારી ભાષા પસંદ કરો",
                        color = CM.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.staggerIn(1),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(24.dp))
                AppLanguage.entries.forEachIndexed { i, lang ->
                    LanguageOption(lang, lang == picked, Modifier.staggerIn(2 + i)) {
                        if (picked != lang) {
                            app.sound.play(Sfx.SELECT)
                            app.haptics.tick()
                            picked = lang
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.lang_change_later), color = CM.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                GameButton(
                    stringResource(if (firstRun) R.string.lang_continue else R.string.lang_save),
                    {
                        LocaleManager.apply(ctx, picked)
                        onDone(picked != before)
                    },
                    style = ButtonStyle.GOLD, icon = Icons.Rounded.PlayArrow, height = 60.dp, fontSize = 21.sp,
                    modifier = Modifier.fillMaxWidth(), sound = Sfx.SELECT,
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(lang: AppLanguage, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, sound = null, pressedScale = 0.97f)
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(Color(0xFF6A3FE0), Color(0xFFB0206A)))
                else Brush.horizontalGradient(listOf(Color(0x26FFFFFF), Color(0x14FFFFFF)))
            )
            .border(if (selected) 2.5.dp else 1.dp, if (selected) CM.GoldLight else Color(0x40FFFFFF), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep))),
            contentAlignment = Alignment.Center,
        ) { Text(lang.glyph, color = Color(0xFF3A1F00), fontSize = 28.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(lang.nativeName, color = Color.White, fontSize = 24.sp, fontFamily = if (lang == AppLanguage.EN) DisplayFamily else null, fontWeight = FontWeight.Bold)
            if (lang != AppLanguage.EN) Text(lang.englishName, color = CM.TextDim, fontSize = 13.sp)
        }
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (selected) CM.Emerald else Color.Transparent)
                .border(2.dp, if (selected) Color.White else Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}
