package com.ngdstudio.mindimania.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.R

/**
 * Display face: Lilita One (one weight only — always request [FontWeight.Normal] so no fake-bold is synthesized).
 * Bundled under the SIL Open Font License (see assets/licenses).
 */
val DisplayFamily: FontFamily = FontFamily(Font(R.font.lilita_one, FontWeight.Normal))

@OptIn(ExperimentalTextApi::class)
private fun nunito(w: FontWeight) = Font(
    R.font.nunito,
    weight = w,
    variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
)

/** Body face: Nunito variable font (wght axis). On API 24–25 the default instance is used. */
val BodyFamily: FontFamily = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
    nunito(FontWeight.Black),
)

/** Ready-made display style (titles, big numbers, button labels). */
val DisplayStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 44.sp, letterSpacing = 1.sp),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 34.sp, letterSpacing = 0.5.sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 26.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 19.sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.4.sp),
)
