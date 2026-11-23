package com.ngdstudio.mindimania.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.LocalApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.data.AvatarFrame
import com.ngdstudio.mindimania.data.PlayerAvatars
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.ui.components.AvatarBadge
import com.ngdstudio.mindimania.ui.components.ButtonStyle
import com.ngdstudio.mindimania.ui.components.ChoiceChip
import com.ngdstudio.mindimania.ui.components.CoinChip
import com.ngdstudio.mindimania.ui.components.CoinIcon
import com.ngdstudio.mindimania.ui.components.GameButton
import com.ngdstudio.mindimania.ui.components.OutlinedTitle
import com.ngdstudio.mindimania.ui.kit.BackImage
import com.ngdstudio.mindimania.ui.kit.GemDialog
import com.ngdstudio.mindimania.ui.kit.pressable
import com.ngdstudio.mindimania.ui.kit.rememberProfile
import com.ngdstudio.mindimania.ui.kit.staggerIn
import com.ngdstudio.mindimania.ui.render.CardBack
import com.ngdstudio.mindimania.ui.theme.CM
import com.ngdstudio.mindimania.ui.theme.DisplayFamily
import com.ngdstudio.mindimania.ui.design.GameIcons
import com.ngdstudio.mindimania.ui.design.LevelGem
import com.ngdstudio.mindimania.ui.design.RewardPopup
import com.ngdstudio.mindimania.ui.kit.FaceImage
import com.ngdstudio.mindimania.ui.spider.PlayingCardArt
import com.ngdstudio.mindimania.ui.spider.SpiderBackground
import com.ngdstudio.mindimania.ui.spider.SpiderBackgrounds
import com.ngdstudio.mindimania.engine.model.Face
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.engine.model.Suit
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private enum class Category(@StringRes val labelRes: Int, val columns: Int) {
    TABLES(R.string.shell_cat_tables, 2), BACKS(R.string.shell_cat_backs, 3), FRAMES(R.string.shell_cat_frames, 3), SPIDER(R.string.shell_cat_spider, 3)
}

private data class ShopItem(val category: Category, val name: String, val title: String, val price: Int, val level: Int) {
    val key: String
        get() = when (category) {
            Category.TABLES -> "theme:$name"
            Category.BACKS -> "back:$name"
            Category.FRAMES -> "frame:$name"
            Category.SPIDER -> "spiderbg:$name"
        }

    fun owned(p: Profile) = category == Category.SPIDER || price == 0 || key in p.unlocked
    fun equipped(p: Profile) = when (category) {
        Category.TABLES -> p.theme == name
        Category.BACKS -> p.back == name
        Category.FRAMES -> p.frame == name
        Category.SPIDER -> p.spider.background == name
    }

    fun equip(p: Profile) = when (category) {
        Category.TABLES -> p.copy(theme = name)
        Category.BACKS -> p.copy(back = name)
        Category.FRAMES -> p.copy(frame = name)
        Category.SPIDER -> p.copy(spider = p.spider.copy(background = name))
    }
}

private fun itemsFor(c: Category): List<ShopItem> = when (c) {
    Category.TABLES -> TableTheme.entries.map { ShopItem(c, it.name, it.title, it.price, it.level) }
    Category.BACKS -> CardBack.entries.map { ShopItem(c, it.name, it.title, it.price, it.level) }
    Category.FRAMES -> AvatarFrame.entries.map { ShopItem(c, it.name, it.title, it.price, it.level) }
    Category.SPIDER -> SpiderBackground.entries.map { ShopItem(c, it.name, it.title, 0, 1) }
}

@Composable
fun CollectionTab() {
    val app = LocalApp.current
    val profile by rememberProfile()
    val scope = rememberCoroutineScope()
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<ShopItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastId by remember { mutableIntStateOf(0) }
    var unlockedItem by remember { mutableStateOf<ShopItem?>(null) }

    LaunchedEffect(toastId) {
        if (toast != null) {
            delay(2000)
            toast = null
        }
    }

    fun showToast(msg: String) {
        toast = msg
        toastId++
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).staggerIn(0), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTitle(stringResource(R.string.shell_tab_collection), fontSize = 30.sp, outlineWidth = 10f)
                Spacer(Modifier.weight(1f))
                CoinChip(profile.coins)
            }
            Row(Modifier.fillMaxWidth().staggerIn(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.forEachIndexed { i, c ->
                    ChoiceChip(stringResource(c.labelRes), cat == i, { cat = i }, Modifier.weight(1f))
                }
            }
            if (Category.entries[cat] == Category.SPIDER) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.shell_collection_spider_hint), color = CM.TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            val category = Category.entries[cat]
            key(cat) {
                itemsFor(category).chunked(category.columns).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().staggerIn(2 + r, step = 50L), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            ItemCard(
                                item, profile,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when {
                                        item.equipped(profile) -> app.sound.play(Sfx.TAP)
                                        item.owned(profile) -> scope.launch {
                                            app.profile.update { item.equip(it) }
                                            app.sound.play(Sfx.SELECT)
                                        }
                                        profile.level < item.level -> {
                                            app.sound.play(Sfx.ERROR)
                                            showToast(Strings.get(R.string.shell_collection_reach_level, item.level, item.title))
                                        }
                                        else -> confirm = item
                                    }
                                }
                            )
                        }
                        repeat(category.columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 24.dp, end = 24.dp)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xEE1A0844))
                    .border(1.5.dp, CM.Gold.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(toast ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }

    unlockedItem?.let { item ->
        RewardPopup(
            title = stringResource(R.string.shell_collection_unlocked), amount = 0,
            subtitle = stringResource(R.string.shell_collection_equipped_msg, item.title),
            buttonText = stringResource(R.string.shell_collection_awesome),
            onCollect = { unlockedItem = null },
            icon = { Box(Modifier.width(180.dp)) { ItemPreview(item, profile) } },
        )
    }

    confirm?.let { item ->
        GemDialog(title = stringResource(R.string.shell_collection_unlock_q, item.title), onDismiss = { confirm = null }) {
            Box(Modifier.width(170.dp)) { ItemPreview(item, profile) }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(26.dp)
                Spacer(Modifier.width(6.dp))
                Text("${item.price}", color = CM.GoldLight, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Text(pluralStringResource(R.plurals.shell_collection_you_have, profile.coins, profile.coins), color = CM.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(stringResource(R.string.shell_cancel), { confirm = null }, style = ButtonStyle.GLASS, modifier = Modifier.weight(1f), height = 50.dp, fontSize = 16.sp)
                GameButton(
                    stringResource(R.string.shell_collection_buy),
                    onClick = {
                        if (profile.coins < item.price) {
                            app.sound.play(Sfx.ERROR)
                            showToast(Strings.get(R.string.shell_collection_not_enough))
                            confirm = null
                        } else {
                            scope.launch {
                                app.profile.update { p ->
                                    if (p.coins < item.price || item.key in p.unlocked) p
                                    else item.equip(p.copy(coins = p.coins - item.price, unlocked = p.unlocked + item.key))
                                }
                                app.sound.play(Sfx.CHEST)
                                app.haptics.medium()
                                unlockedItem = item
                            }
                            confirm = null
                        }
                    },
                    style = ButtonStyle.GREEN,
                    modifier = Modifier.weight(1f),
                    height = 50.dp,
                    fontSize = 16.sp,
                    sound = Sfx.SELECT,
                )
            }
        }
    }
}

@Composable
private fun ItemCard(item: ShopItem, profile: Profile, modifier: Modifier, onClick: () -> Unit) {
    val equipped = item.equipped(profile)
    val owned = item.owned(profile)
    val lockedLevel = !owned && profile.level < item.level
    val shape = RoundedCornerShape(18.dp)
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .graphicsLayer { translationX = sin(shake.value * 20f) * 10f * (1f - shake.value) * density }
            .pressable(sound = null) {
                if (lockedLevel) scope.launch { shake.snapTo(0f); shake.animateTo(1f, tween(400)) }
                onClick()
            }
            .clip(shape)
            .background(
                if (equipped) Brush.verticalGradient(listOf(Color(0xFF7A4A10), Color(0xFF3A1F00)))
                else Brush.verticalGradient(listOf(Color(0xE0472094), Color(0xF0170742)))
            )
            .border(
                if (equipped) 2.5.dp else 1.5.dp,
                if (equipped) Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)) else Brush.verticalGradient(listOf(CM.GoldLight.copy(alpha = 0.6f), Color(0x55C98A00))),
                shape
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer { alpha = if (lockedLevel) 0.45f else 1f }) { ItemPreview(item, profile) }
            if (equipped) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(CM.GoldLight, CM.GoldDeep)))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(GameIcons.Check, null, tint = Color(0xFF3A1F00), modifier = Modifier.size(16.dp)) }
            }
            if (lockedLevel) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color(0xCC12042E)).border(2.dp, CM.GoldLight, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GameIcons.Lock, null, tint = CM.GoldLight, modifier = Modifier.size(22.dp)) }
                    LevelGem(item.level, Modifier.offset(y = (-6).dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = Color.White, fontSize = 13.sp, fontFamily = DisplayFamily, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        when {
            equipped -> Text(stringResource(R.string.shell_collection_equipped), color = CM.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Black)
            owned -> Text(stringResource(R.string.shell_collection_tap_equip), color = CM.Emerald, fontSize = 11.sp, fontWeight = FontWeight.Black)
            lockedLevel -> Text(stringResource(R.string.shell_collection_level, item.level), color = CM.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Black)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(14.dp)
                Spacer(Modifier.width(3.dp))
                Text("${item.price}", color = if (profile.coins >= item.price) CM.GoldLight else CM.Ruby, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ItemPreview(item: ShopItem, profile: Profile) {
    when (item.category) {
        Category.TABLES -> {
            val theme = TableTheme.valueOf(item.name)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height / 2)
                    drawRect(Brush.radialGradient(listOf(theme.center, theme.edge), c, size.maxDimension * 0.7f))
                    drawOval(
                        theme.accent.copy(alpha = 0.6f),
                        Offset(size.width * 0.12f, size.height * 0.22f),
                        Size(size.width * 0.76f, size.height * 0.56f),
                        style = Stroke(3f)
                    )
                    val r = java.util.Random(theme.ordinal.toLong())
                    repeat(16) {
                        drawCircle(theme.accent.copy(alpha = 0.3f + r.nextFloat() * 0.5f), 1.5f + r.nextFloat() * 2.5f, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height))
                    }
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x88000000)), c, size.maxDimension * 0.75f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
                    BackImage(profile.cardBack, 28.dp, Modifier.graphicsLayer { rotationZ = -12f }, elevation = 4.dp)
                    FaceImage(Face(Suit.RUBY, Kind.NUMBER, 7), 30.dp, Modifier.graphicsLayer { rotationZ = 4f; translationY = -4f }, elevation = 4.dp)
                    FaceImage(Face(Suit.WILD, Kind.PRISM), 28.dp, Modifier.graphicsLayer { rotationZ = 14f }, elevation = 4.dp)
                }
            }
        }
        Category.BACKS -> {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BackImage(CardBack.valueOf(item.name), maxWidth * 0.78f, elevation = 6.dp)
            }
        }
        Category.FRAMES -> {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                AvatarBadge(PlayerAvatars.get(profile.avatar), size = 64.dp, frame = AvatarFrame.valueOf(item.name))
            }
        }
        Category.SPIDER -> {
            val bg = SpiderBackground.valueOf(item.name)
            val thumb = remember(bg) { SpiderBackgrounds.render(bg, 120, 168).asImageBitmap() }
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.TopCenter) {
                androidx.compose.foundation.Image(thumb, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(13, 12, 11).forEach { r ->
                        androidx.compose.foundation.Image(PlayingCardArt.face(r, 0), null, Modifier.width(18.dp).aspectRatio(1f / 1.4f))
                    }
                }
            }
        }
    }
}
