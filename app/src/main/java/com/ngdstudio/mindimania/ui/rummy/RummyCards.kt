package com.ngdstudio.mindimania.ui.rummy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.rummy.RCard
import com.ngdstudio.mindimania.rummy.RummyMelds
import com.ngdstudio.mindimania.ui.render.CardArt
import com.ngdstudio.mindimania.ui.render.CardBack
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt

/**
 * One Rummy card, [w] wide (1.4 × as tall). Face down shows [back]. A printed joker is drawn here (the card art has
 * none); a wild-rank card gets a small gold "J" in its lower-left corner so jokers are easy to spot.
 */
@Composable
fun RummyCardView(card: RCard?, w: Dp, faceUp: Boolean, wild: Int, back: CardBack, modifier: Modifier = Modifier, glow: Color? = null, index: Boolean = false) {
    val shape = RoundedCornerShape(w * 0.1f)
    Box(
        modifier.size(w, w * 1.4f).shadow(3.dp, shape)
            .then(if (glow != null) Modifier.border(2.dp, glow, shape) else Modifier)
    ) {
        when {
            !faceUp || card == null -> Image(CardArt.back(back), null, Modifier.fillMaxSize())
            card.isPrinted -> PrintedJoker(w)
            else -> {
                Image(PlayingCardArt.face(card.rank, card.suit), null, Modifier.fillMaxSize())
                if (index) {
                    // cards in a fanned hand overlap: the suit goes under the rank, where it stays visible
                    Text(
                        suitSymbol(card.suit), color = if (card.suit == 1 || card.suit == 2) Color(0xFFD32F2F) else Color(0xFF1A1A1A),
                        fontSize = (w.value * 0.22f).sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.TopStart).offset(x = w * 0.04f, y = w * 0.36f).clip(RoundedCornerShape(3.dp)).background(Color.White).padding(horizontal = 1.dp),
                    )
                }
                if (card.rank == wild) {
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(w * 0.05f).size(w * 0.3f).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFE0A100)))).border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("J", color = Color(0xFF4A2A00), fontSize = (w.value * 0.17f).sp, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun PrintedJoker(w: Dp) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(w * 0.1f)).background(Color.White)
            .border(1.dp, Color(0x33000000), RoundedCornerShape(w * 0.1f))
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(start = w * 0.06f, top = w * 0.04f)) {
            "JOKER".forEach { ch ->
                Text("$ch", color = Color(0xFFD32F2F), fontSize = (w.value * 0.16f).sp, fontWeight = FontWeight.Black, lineHeight = (w.value * 0.17f).sp)
            }
        }
        Text("🃏", fontSize = (w.value * 0.5f).sp, modifier = Modifier.align(Alignment.Center).offset(x = w * 0.1f, y = w * 0.1f))
    }
}

/** Card value text for a mini card in results etc. */
fun RCard.label(): String = if (isPrinted) "🃏" else when (rank) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$rank" } + suitSymbol(suit)

fun suitSymbol(s: Int) = when (s) { 0 -> "♠"; 1 -> "♥"; 2 -> "♦"; 3 -> "♣"; else -> "" }

fun rankName(r: Int) = when (r) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$r" }

/** Whether [c] is a joker under [wild]. */
fun isJokerCard(c: RCard, wild: Int) = RummyMelds.isJoker(c, wild)
