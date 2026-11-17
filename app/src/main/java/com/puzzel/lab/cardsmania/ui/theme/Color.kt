package com.puzzel.lab.cardsmania.ui.theme

import androidx.compose.ui.graphics.Color
import com.puzzel.lab.cardsmania.engine.model.Suit

object CM {
    val Royal = Color(0xFF2A0E5C)
    val RoyalLight = Color(0xFF4A1F8F)
    val Midnight = Color(0xFF120634)
    val Abyss = Color(0xFF0A0320)
    val Gold = Color(0xFFFFC93C)
    val GoldLight = Color(0xFFFFE89A)
    val GoldDeep = Color(0xFFC98A00)
    val GoldDark = Color(0xFF7A4E00)
    val Text = Color(0xFFF3EEFF)
    val TextDim = Color(0xFFB7A9DA)
    val TextMuted = Color(0xFF8371B5)
    val Glass = Color(0x22FFFFFF)
    val GlassStrong = Color(0x33FFFFFF)
    val GlassBorder = Color(0x40FFFFFF)
    val Success = Color(0xFF19C37D)
    val Danger = Color(0xFFE0245E)
    val Info = Color(0xFF1E7BFF)

    val Ruby = Color(0xFFE53935)
    val Sapphire = Color(0xFF1E88E5)
    val Emerald = Color(0xFF2EAD4B)
    val Amber = Color(0xFFFDC500)
    val Rose = Color(0xFFEC4899)
    val Aqua = Color(0xFF00A99D)
    val Tangerine = Color(0xFFFF7A00)
    val Violet = Color(0xFF7E3FD6)
}

/** Base / light / dark tones for each gem suit. */
data class GemTone(val base: Color, val light: Color, val dark: Color, val deep: Color)

object Gems {
    fun tone(suit: Suit): GemTone = when (suit) {
        Suit.RUBY -> GemTone(CM.Ruby, Color(0xFFFF6B66), Color(0xFFB71C1C), Color(0xFF5E0B0B))
        Suit.SAPPHIRE -> GemTone(CM.Sapphire, Color(0xFF64B5F6), Color(0xFF0D47A1), Color(0xFF06214D))
        Suit.EMERALD -> GemTone(CM.Emerald, Color(0xFF6FD889), Color(0xFF1B7A33), Color(0xFF0B3D18))
        Suit.AMBER -> GemTone(CM.Amber, Color(0xFFFFE266), Color(0xFFD99A00), Color(0xFF6B4300))
        Suit.ROSE -> GemTone(CM.Rose, Color(0xFFF9A8D4), Color(0xFFBE185D), Color(0xFF5E0A2E))
        Suit.AQUA -> GemTone(CM.Aqua, Color(0xFF5EEAD4), Color(0xFF00756C), Color(0xFF003A35))
        Suit.TANGERINE -> GemTone(CM.Tangerine, Color(0xFFFFB066), Color(0xFFC25400), Color(0xFF5C2800))
        Suit.VIOLET -> GemTone(CM.Violet, Color(0xFFC4A1F5), Color(0xFF5521A6), Color(0xFF2A0E57))
        Suit.WILD -> GemTone(Color(0xFF222222), Color(0xFF3A3A3A), Color(0xFF111111), Color(0xFF000000))
    }

    fun color(suit: Suit): Color = tone(suit).base

    fun name(suit: Suit): String = when (suit) {
        Suit.RUBY -> "Red"
        Suit.SAPPHIRE -> "Blue"
        Suit.EMERALD -> "Green"
        Suit.AMBER -> "Yellow"
        Suit.ROSE -> "Pink"
        Suit.AQUA -> "Teal"
        Suit.TANGERINE -> "Orange"
        Suit.VIOLET -> "Purple"
        Suit.WILD -> "Wild"
    }
}
