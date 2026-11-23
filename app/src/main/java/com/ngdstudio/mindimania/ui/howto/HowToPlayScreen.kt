package com.ngdstudio.mindimania.ui.howto

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.ModeInfo
import com.ngdstudio.mindimania.engine.model.DeckFactory
import com.ngdstudio.mindimania.engine.model.Face
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import com.ngdstudio.mindimania.ui.components.GlassPanel
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.components.SectionHeader
import com.ngdstudio.mindimania.ui.kit.BackImage
import com.ngdstudio.mindimania.ui.kit.FaceImage
import com.ngdstudio.mindimania.ui.kit.GemBullet
import com.ngdstudio.mindimania.ui.kit.Pill
import com.ngdstudio.mindimania.ui.kit.pressable
import com.ngdstudio.mindimania.ui.kit.rememberProfile
import com.ngdstudio.mindimania.ui.render.CardBack
import com.ngdstudio.mindimania.ui.theme.CM
import kotlinx.coroutines.launch

private data class RuleSection(@StringRes val titleRes: Int, val bullets: List<Int>)

private val basics = RuleSection(R.string.mania_howto_basics_title, listOf(R.string.mania_howto_basics_1, R.string.mania_howto_basics_2, R.string.mania_howto_basics_3, R.string.mania_howto_basics_4, R.string.mania_howto_basics_5, R.string.mania_howto_basics_6, R.string.mania_howto_basics_7))

private fun modeRules(mode: GameMode): RuleSection = when (mode) {
    GameMode.CLASSIC -> RuleSection(R.string.mania_howto_classic_title, listOf(R.string.mania_howto_classic_1, R.string.mania_howto_classic_2, R.string.mania_howto_classic_3, R.string.mania_howto_classic_4, R.string.mania_howto_classic_5))
    GameMode.CHAOS -> RuleSection(R.string.mania_howto_merciless_title, listOf(R.string.mania_howto_merciless_1, R.string.mania_howto_merciless_2, R.string.mania_howto_merciless_3, R.string.mania_howto_merciless_4, R.string.mania_howto_merciless_5, R.string.mania_howto_merciless_6, R.string.mania_howto_merciless_7, R.string.mania_howto_merciless_8))
    GameMode.ECLIPSE -> RuleSection(R.string.mania_howto_flip_title, listOf(R.string.mania_howto_flip_1, R.string.mania_howto_flip_2, R.string.mania_howto_flip_3, R.string.mania_howto_flip_4, R.string.mania_howto_flip_5, R.string.mania_howto_flip_6))
}

/** A group of related faces shown together in the gallery. */
private data class FaceGroup(val title: String, val description: String, val points: String, val faces: List<Face>)

private fun groupsFor(faces: List<Face>, mode: GameMode): List<FaceGroup> {
    val groups = LinkedHashMap<String, MutableList<Face>>()
    faces.distinct().forEach { f ->
        val key = if (f.kind == Kind.NUMBER) "NUMBER" else "${f.kind}-${f.value}"
        groups.getOrPut(key) { mutableListOf() } += f
    }
    return groups.map { (key, list) ->
        if (key == "NUMBER") {
            val min = list.minOf { it.value }
            val bySuit = list.groupBy { it.suit }.values.toList()
            val picks = bySuit.mapIndexed { i, l -> l[(i * 3 + 1).coerceAtMost(l.lastIndex)] }
            FaceGroup(Strings.get(R.string.mania_howto_number_cards, min), Strings.get(R.string.mania_howto_number_desc), Strings.get(R.string.mania_howto_face_value), picks.take(4))
        } else {
            val f = list.first()
            FaceGroup(f.title, f.description, Strings.get(R.string.mania_howto_worth_points, f.points(mode)), list.take(4))
        }
    }
}

@Composable
fun HowToPlayScreen(initialMode: GameMode?, onBack: () -> Unit, initialPage: Int? = null) {
    var modeIdx by rememberSaveable { mutableStateOf(initialPage ?: (initialMode ?: GameMode.CLASSIC).ordinal) }
    val mode = GameMode.entries[modeIdx.coerceAtMost(GameMode.entries.lastIndex)]
    val extra = modeIdx >= GameMode.entries.size
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val cardBack = rememberProfile().value.cardBack

    val galleries: List<Pair<String?, List<FaceGroup>>> = remember(mode) {
        when (mode) {
            GameMode.CLASSIC -> listOf(null to groupsFor(DeckFactory.classicFaces(), mode))
            GameMode.CHAOS -> listOf(null to groupsFor(DeckFactory.chaosFaces(), mode))
            GameMode.ECLIPSE -> listOf(
                Strings.get(R.string.mania_howto_light_side) to groupsFor(DeckFactory.eclipseSunFaces(), mode),
                Strings.get(R.string.mania_howto_dark_side) to groupsFor(DeckFactory.eclipseMoonFaces(), mode),
            )
        }
    }

    RoyalBackground(rays = false) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenTitleBar(stringResource(R.string.mania_how_to_play), onBack)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GameMode.entries.forEachIndexed { i, m ->
                    ChoiceChip(ModeInfo.short(m), i == modeIdx, {
                        modeIdx = i
                        scope.launch { listState.scrollToItem(0) }
                    }, color = if (i == modeIdx && m != GameMode.CLASSIC) ModeInfo.colors(m)[1] else CM.Gold)
                }
                ChoiceChip(stringResource(R.string.mania_howto_chip_spider), modeIdx == GameMode.entries.size, {
                    modeIdx = GameMode.entries.size
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFF0E6B3A))
                ChoiceChip(stringResource(R.string.mania_howto_chip_mindi), modeIdx == GameMode.entries.size + 1, {
                    modeIdx = GameMode.entries.size + 1
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFFC0392B))
                ChoiceChip(stringResource(R.string.mania_howto_chip_bhabhi), modeIdx == GameMode.entries.size + 2, {
                    modeIdx = GameMode.entries.size + 2
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFFB0206A))
                ChoiceChip(stringResource(R.string.mania_howto_chip_teenpatti), modeIdx == GameMode.entries.size + 3, {
                    modeIdx = GameMode.entries.size + 3
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFFE0245E))
                ChoiceChip(stringResource(R.string.mania_howto_chip_rummy), modeIdx == GameMode.entries.size + 4, {
                    modeIdx = GameMode.entries.size + 4
                    scope.launch { listState.scrollToItem(0) }
                }, color = Color(0xFF13734F))
            }
            if (extra) {
                ExtraGameRules(ExtraGame.entries[(modeIdx - GameMode.entries.size).coerceIn(0, ExtraGame.entries.lastIndex)])
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero-$mode") { ModeHero(mode) }
                item(key = "basics") { RulesPanel(basics, CM.Gold) }
                item(key = "mode-$mode") { RulesPanel(modeRules(mode), ModeInfo.colors(mode)[0]) }
                galleries.forEach { (side, groups) ->
                    item(key = "gallery-$mode-$side") {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            SectionHeader(if (side == null) stringResource(R.string.mania_howto_gallery) else stringResource(R.string.mania_howto_gallery_side, side))
                            Text(stringResource(R.string.mania_howto_tap_flip), color = CM.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
                        }
                    }
                    items(groups, key = { "g-$mode-$side-${it.faces.first()}" }) { g -> GalleryRow(g, cardBack) }
                }
                item(key = "spacer") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun ModeHero(mode: GameMode) {
    val colors = ModeInfo.colors(mode)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors[0].copy(alpha = 0.85f), colors[1].copy(alpha = 0.85f), Color(0xDD12042E))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTitle(ModeInfo.title(mode), fontSize = 28.sp, outlineWidth = 10f)
        Spacer(Modifier.height(4.dp))
        Text(ModeInfo.tagline(mode), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Pill(ModeInfo.deckLabel(mode), Color(0x66000000))
    }
}

@Composable
private fun RulesPanel(section: RuleSection, accent: Color) {
    GlassPanel(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(stringResource(section.titleRes).uppercase(), color = CM.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        section.bullets.forEach { GemBullet(stringResource(it), accent) }
    }
}

@Composable
private fun GalleryRow(g: FaceGroup, cardBack: CardBack) {
    val app = LocalApp.current
    val rot = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x2EFFFFFF), Color(0x10FFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val n = g.faces.size
        val cardW = 60.dp
        val step = 20
        Box(
            Modifier
                .width(cardW + (step * 3).dp)
                .height(96.dp)
                .pressable(sound = Sfx.CARD_SLIDE, pressedScale = 0.95f) {
                    if (!rot.isRunning) scope.launch {
                        rot.snapTo(0f)
                        rot.animateTo(360f, tween(750, easing = FastOutSlowInEasing))
                        rot.snapTo(0f)
                    }
                    app.haptics.tick()
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val r = rot.value
            val backSide = r in 90f..270f
            g.faces.forEachIndexed { i, f ->
                val k = i - (n - 1) / 2f
                Box(
                    Modifier
                        .offset(x = (i * step).dp + ((3 - (n - 1)) * step / 2).dp)
                        .graphicsLayer {
                            rotationZ = k * 7f
                            rotationY = r
                            cameraDistance = 10f * density
                            if (backSide) scaleX = -1f
                        }
                ) {
                    if (backSide) BackImage(cardBack, cardW, elevation = 6.dp) else FaceImage(f, cardW, elevation = 6.dp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(g.description, color = CM.Text, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(g.points, color = CM.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ------------------------------------------------------------------------------------------- Spider, Mindi & Bhabhi

private enum class ExtraGame { SPIDER, MINDI, BHABHI, TEEN_PATTI, RUMMY }

private val rummyRules = listOf(
    RuleSection(R.string.rm_howto_goal_title, listOf(R.string.rm_howto_goal_1, R.string.rm_howto_goal_2)),
    RuleSection(R.string.rm_howto_deal_title, listOf(R.string.rm_howto_deal_1, R.string.rm_howto_deal_2, R.string.rm_howto_deal_3)),
    RuleSection(R.string.rm_howto_turn_title, listOf(R.string.rm_howto_turn_1, R.string.rm_howto_turn_2, R.string.rm_howto_turn_3, R.string.rm_howto_turn_4)),
    RuleSection(R.string.rm_howto_melds_title, listOf(R.string.rm_howto_melds_1, R.string.rm_howto_melds_2, R.string.rm_howto_melds_3, R.string.rm_howto_melds_4)),
    RuleSection(R.string.rm_howto_declare_title, listOf(R.string.rm_howto_declare_1, R.string.rm_howto_declare_2, R.string.rm_howto_declare_3)),
    RuleSection(R.string.rm_howto_points_title, listOf(R.string.rm_howto_points_1, R.string.rm_howto_points_2, R.string.rm_howto_points_3, R.string.rm_howto_points_4)),
)

/** Page index for a non-Mania game name in the how-to route (e.g. "TEEN_PATTI"), or null. */
fun howToPageFor(name: String?): Int? =
    ExtraGame.entries.firstOrNull { it.name == name }?.let { GameMode.entries.size + it.ordinal }

private val teenPattiRules = listOf(
    RuleSection(R.string.tp_howto_goal_title, listOf(R.string.tp_howto_goal_1, R.string.tp_howto_goal_2)),
    RuleSection(R.string.tp_howto_deal_title, listOf(R.string.tp_howto_deal_1, R.string.tp_howto_deal_2, R.string.tp_howto_deal_3)),
    RuleSection(R.string.tp_howto_bet_title, listOf(R.string.tp_howto_bet_1, R.string.tp_howto_bet_2, R.string.tp_howto_bet_3, R.string.tp_howto_bet_4, R.string.tp_howto_bet_5)),
    RuleSection(R.string.tp_howto_show_title, listOf(R.string.tp_howto_show_1, R.string.tp_howto_show_2, R.string.tp_howto_show_3)),
    RuleSection(R.string.tp_howto_rank_title, listOf(R.string.tp_howto_rank_1, R.string.tp_howto_rank_2, R.string.tp_howto_rank_3)),
    RuleSection(R.string.tp_section_variant, listOf(R.string.tp_var_muflis_desc, R.string.tp_var_joker_desc, R.string.tp_var_lowest_desc, R.string.tp_var_ak47_desc, R.string.tp_var_999_desc, R.string.tp_var_4x_desc, R.string.tp_var_mixed_desc, R.string.tp_howto_var_wild)),
    RuleSection(R.string.tp_howto_tips_title, listOf(R.string.tp_howto_tips_1, R.string.tp_howto_tips_2, R.string.tp_howto_tips_3)),
)

private val spiderRules = listOf(
    RuleSection(R.string.mania_howto_spider_goal_title, listOf(R.string.mania_howto_spider_goal_1, R.string.mania_howto_spider_goal_2)),
    RuleSection(R.string.mania_howto_spider_table_title, listOf(R.string.mania_howto_spider_table_1, R.string.mania_howto_spider_table_2, R.string.mania_howto_spider_table_3)),
    RuleSection(R.string.mania_howto_spider_moving_title, listOf(R.string.mania_howto_spider_moving_1, R.string.mania_howto_spider_moving_2, R.string.mania_howto_spider_moving_3, R.string.mania_howto_spider_moving_4, R.string.mania_howto_spider_moving_5)),
    RuleSection(R.string.mania_howto_spider_deal_title, listOf(R.string.mania_howto_spider_deal_1, R.string.mania_howto_spider_deal_2, R.string.mania_howto_spider_deal_3)),
)

private val mindiRules = listOf(
    RuleSection(R.string.mania_howto_mindi_teams_title, listOf(R.string.mania_howto_mindi_teams_1, R.string.mania_howto_mindi_teams_2, R.string.mania_howto_mindi_teams_3)),
    RuleSection(R.string.mania_howto_mindi_trick_title, listOf(R.string.mania_howto_mindi_trick_1, R.string.mania_howto_mindi_trick_2, R.string.mania_howto_mindi_trick_3)),
    RuleSection(R.string.mania_howto_mindi_trump_title, listOf(R.string.mania_howto_mindi_trump_1, R.string.mania_howto_mindi_trump_2, R.string.mania_howto_mindi_trump_3)),
    RuleSection(R.string.mania_howto_mindi_tens_title, listOf(R.string.mania_howto_mindi_tens_1, R.string.mania_howto_mindi_tens_2, R.string.mania_howto_mindi_tens_3, R.string.mania_howto_mindi_tens_4, R.string.mania_howto_mindi_tens_5)),
)

private val bhabhiRules = listOf(
    RuleSection(R.string.mania_howto_bhabhi_goal_title, listOf(R.string.mania_howto_bhabhi_goal_1, R.string.mania_howto_bhabhi_goal_2)),
    RuleSection(R.string.mania_howto_bhabhi_deal_title, listOf(R.string.mania_howto_bhabhi_deal_1, R.string.mania_howto_bhabhi_deal_2, R.string.mania_howto_bhabhi_deal_3)),
    RuleSection(R.string.mania_howto_bhabhi_trick_title, listOf(R.string.mania_howto_bhabhi_trick_1, R.string.mania_howto_bhabhi_trick_2, R.string.mania_howto_bhabhi_trick_3, R.string.mania_howto_bhabhi_trick_4, R.string.mania_howto_bhabhi_trick_5)),
    RuleSection(R.string.mania_howto_bhabhi_out_title, listOf(R.string.mania_howto_bhabhi_out_1, R.string.mania_howto_bhabhi_out_2, R.string.mania_howto_bhabhi_out_3)),
    RuleSection(R.string.mania_howto_bhabhi_tips_title, listOf(R.string.mania_howto_bhabhi_tips_1, R.string.mania_howto_bhabhi_tips_2, R.string.mania_howto_bhabhi_tips_3, R.string.mania_howto_bhabhi_tips_4)),
)

@Composable
private fun ExtraGameRules(game: ExtraGame) {
    val sections = when (game) {
        ExtraGame.SPIDER -> spiderRules
        ExtraGame.MINDI -> mindiRules
        ExtraGame.BHABHI -> bhabhiRules
        ExtraGame.TEEN_PATTI -> teenPattiRules
        ExtraGame.RUMMY -> rummyRules
    }
    val accent = when (game) {
        ExtraGame.SPIDER -> Color(0xFF3FBF6E)
        ExtraGame.MINDI -> Color(0xFFFFB13B)
        ExtraGame.BHABHI -> Color(0xFFFF6FB5)
        ExtraGame.TEEN_PATTI -> Color(0xFFFF8A4C)
        ExtraGame.RUMMY -> Color(0xFF3FD08A)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero-$game") { ExtraHero(game) }
        sections.forEach { s -> item(key = "s-$game-${s.titleRes}") { RulesPanel(s, accent) } }
        item(key = "examples-$game") {
            GlassPanel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Text(stringResource(R.string.mania_howto_examples), color = CM.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                if (game == ExtraGame.SPIDER) {
                    ExampleRow(listOf(13 to 0, 12 to 0, 11 to 0, 10 to 0), stringResource(R.string.mania_howto_ex_spider_1))
                    ExampleRow(listOf(8 to 0, 7 to 1), stringResource(R.string.mania_howto_ex_spider_2))
                } else if (game == ExtraGame.RUMMY) {
                    ExampleRow(listOf(5 to 1, 6 to 1, 7 to 1), stringResource(R.string.rm_howto_ex_1))
                    ExampleRow(listOf(10 to 3, 10 to 0, 10 to 1), stringResource(R.string.rm_howto_ex_2))
                } else if (game == ExtraGame.TEEN_PATTI) {
                    ExampleRow(listOf(12 to 1, 12 to 0, 12 to 3), stringResource(R.string.tp_howto_ex_1))
                    ExampleRow(listOf(1 to 2, 2 to 0, 3 to 1), stringResource(R.string.tp_howto_ex_2))
                } else if (game == ExtraGame.BHABHI) {
                    ExampleRow(listOf(5 to 1, 9 to 1, 12 to 1), stringResource(R.string.mania_howto_ex_bhabhi_1))
                    ExampleRow(listOf(5 to 1, 12 to 1, 13 to 3), stringResource(R.string.mania_howto_ex_bhabhi_2))
                } else {
                    ExampleRow(listOf(10 to 0, 10 to 1, 10 to 2, 10 to 3), stringResource(R.string.mania_howto_ex_mindi_1))
                    ExampleRow(listOf(5 to 1, 12 to 1, 3 to 0), stringResource(R.string.mania_howto_ex_mindi_2))
                }
            }
        }
        item(key = "spacer-$game") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun ExtraHero(game: ExtraGame) {
    val spider = game == ExtraGame.SPIDER
    val bhabhi = game == ExtraGame.BHABHI
    val tp = game == ExtraGame.TEEN_PATTI
    val rm = game == ExtraGame.RUMMY
    val colors = when (game) {
        ExtraGame.SPIDER -> listOf(Color(0xFF3FBF6E), Color(0xFF0E6B3A))
        ExtraGame.MINDI -> listOf(Color(0xFFFFB13B), Color(0xFFC0392B))
        ExtraGame.BHABHI -> listOf(Color(0xFFFF6FB5), Color(0xFFB0206A))
        ExtraGame.TEEN_PATTI -> listOf(Color(0xFFFF8A4C), Color(0xFFE0245E))
        ExtraGame.RUMMY -> listOf(Color(0xFF3FD08A), Color(0xFF13734F))
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors[0].copy(alpha = 0.85f), colors[1].copy(alpha = 0.9f), Color(0xDD12042E))))
            .border(2.dp, Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTitle(if (rm) stringResource(R.string.rm_title) else if (tp) stringResource(R.string.tp_howto_name) else if (spider) stringResource(R.string.mania_howto_spider_name) else if (bhabhi) stringResource(R.string.mania_howto_bhabhi_name) else stringResource(R.string.mania_howto_mindi_name), fontSize = 28.sp, outlineWidth = 10f)
        Spacer(Modifier.height(4.dp))
        Text(
            if (rm) stringResource(R.string.rm_howto_tagline) else if (tp) stringResource(R.string.tp_howto_tagline) else if (spider) stringResource(R.string.mania_howto_spider_tagline) else if (bhabhi) stringResource(R.string.mania_howto_bhabhi_tagline) else stringResource(R.string.mania_howto_mindi_tagline),
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Pill(if (rm) stringResource(R.string.rm_howto_pill) else if (tp) stringResource(R.string.tp_howto_pill) else if (spider) stringResource(R.string.mania_howto_spider_pill) else if (bhabhi) stringResource(R.string.mania_howto_bhabhi_pill) else stringResource(R.string.mania_howto_mindi_pill), Color(0x66000000))
    }
}

@Composable
private fun ExampleRow(cards: List<Pair<Int, Int>>, caption: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(110.dp).height(72.dp), contentAlignment = Alignment.CenterStart) {
            cards.forEachIndexed { i, (rank, suit) ->
                androidx.compose.foundation.Image(
                    com.ngdstudio.mindimania.ui.spider.PlayingCardArt.face(rank, suit), null,
                    Modifier.offset(x = (i * 18).dp).width(46.dp).height(64.dp).graphicsLayer { rotationZ = (i - cards.size / 2f) * 5f }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(caption, color = CM.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}
