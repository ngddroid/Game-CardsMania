package com.puzzel.lab.cardsmania.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = CM.Gold,
    onPrimary = CM.GoldDark,
    secondary = CM.Sapphire,
    onSecondary = CM.Text,
    tertiary = CM.Emerald,
    background = CM.Midnight,
    onBackground = CM.Text,
    surface = CM.Royal,
    onSurface = CM.Text,
    surfaceVariant = CM.RoyalLight,
    onSurfaceVariant = CM.TextDim,
    error = CM.Danger,
    outline = CM.GlassBorder,
)

@Composable
fun CardsManiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
