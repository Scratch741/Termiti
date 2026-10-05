package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrGold   = Color(0xFFD4A843)
private val PrText   = Color(0xFFEDE0C4)
private val PrMuted  = Color(0xFF7A6E5F)
private val PrGems   = Color(0xFF7EC8E3)
private val PrGreen  = Color(0xFF3DBFAD)
private val PrDust   = Color(0xFFB39DDB)
private val PrDebug  = Color(0xFFCC6655)

/** Hráčské ikony — všechny odemčeny na levelu 1. */
private val AVATARS = listOf(
    "player_icon_1"  to 1,
    "player_icon_2"  to 1,
    "player_icon_3"  to 1,
    "player_icon_4"  to 1,
    "player_icon_5"  to 1,
    "player_icon_6"  to 1,
    "player_icon_7"  to 1,
    "player_icon_8"  to 1,
    "player_icon_9"  to 1,
    "player_icon_10" to 1,
    "player_icon_11" to 1,
    "player_icon_12" to 1
)

private val ENEMY_AVATARS = listOf("enemy_icon_1", "enemy_icon_2", "enemy_icon_3")

/** Vrátí náhodnou enemy ikonu pro AI oponenta (bez kampáňového avataru). */
fun randomEnemyAvatar(): String = ENEMY_AVATARS.random()

/** Vrátí drawable resource ID pro ikonky hráče i oponentů, null pro emoji avatary. */
fun avatarResId(avatar: String): Int? = when (avatar) {
    "player_icon_1"  -> R.drawable.player_icon_1
    "player_icon_2"  -> R.drawable.player_icon_2
    "player_icon_3"  -> R.drawable.player_icon_3
    "player_icon_4"  -> R.drawable.player_icon_4
    "player_icon_5"  -> R.drawable.player_icon_5
    "player_icon_6"  -> R.drawable.player_icon_6
    "player_icon_7"  -> R.drawable.player_icon_7
    "player_icon_8"  -> R.drawable.player_icon_8
    "player_icon_9"  -> R.drawable.player_icon_9
    "player_icon_10" -> R.drawable.player_icon_10
    "player_icon_11" -> R.drawable.player_icon_11
    "player_icon_12" -> R.drawable.player_icon_12
    "enemy_icon_1"   -> R.drawable.enemy_icon_1
    "enemy_icon_2"   -> R.drawable.enemy_icon_2
    "enemy_icon_3"   -> R.drawable.enemy_icon_3
    "hammer_icon"    -> R.drawable.hammer_icon
    "goblin_kral_profil" -> R.drawable.goblin_kral_profil
    else             -> null
}

/**
 * Zobrazí avatar hráče jako Image (player_icon_*) nebo Text (emoji pro oponenty/AI).
 * Backward compatible — emoji avatary oponentů fungují beze změny.
 */
@Composable
fun AvatarDisplay(avatar: String, sizeDp: Float, modifier: Modifier = Modifier) {
    val resId = avatarResId(avatar)
    if (resId != null) {
        Image(
            painter            = painterResource(resId),
            contentDescription = null,
            modifier           = modifier.size(sizeDp.dp),
            contentScale       = ContentScale.Crop
        )
    } else {
        Text(avatar, fontSize = (sizeDp * 0.65f).sp, modifier = modifier)
    }
}

// ── Obrazovka profilu ─────────────────────────────────────────────────────────
//
// Stejná scéna jako hlavní menu a obchod (menu_bg, pochodně, vlevo karta hráče,
// vpravo Zpět). Střední rám drží záložky:
//   PŘEHLED     – jméno, úroveň, XP, statistiky a denní úkoly
//   VZHLED      – ikona, hrad, hradby, rub karet (mřížka bez vodorovného scrollu)
//   SCHOPNOSTI  – pasivní schopnosti (koupě, zapnutí)
//   DEBUG       – testovací tlačítka

private enum class ProfileTab { OVERVIEW, LOOK, ABILITIES, DEBUG }
private enum class LookTab { AVATAR, CASTLE, WALL, CARD_BACK }

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    var profile by remember { mutableStateOf(PlayerProfileManager.profile) }
    var tab     by remember { mutableStateOf(ProfileTab.OVERVIEW) }
    val s = LocalStrings.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // Pochodně – stejný výpočet jako v hlavním menu a obchodu
        val imgAR  = 1791f / 975f
        val dispAR = W.value / H.value.coerceAtLeast(1f)
        val imgDispW: Dp
        val imgDispH: Dp
        val cropX: Dp
        val cropY: Dp
        if (dispAR >= imgAR) {
            imgDispW = W; imgDispH = W / imgAR; cropX = 0.dp; cropY = (imgDispH - H) / 2f
        } else {
            imgDispW = H * imgAR; imgDispH = H; cropX = (imgDispW - W) / 2f; cropY = 0.dp
        }
        val torchSize = H * 0.15f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 1.7f
        )

        val p = profile
        if (p != null) {
            // Střední rám menu_bg je širší než sloupec tlačítek v menu – profil ho využije celý.
            val centerW  = minOf(W * 0.53f, H * 1.25f)
            val iconSize = H * 0.12f
            // Širší střed zužuje boční sloupce → jejich obsah by ujel ke kraji. Posun ho
            // vrací na stejné místo (do výklenků pozadí) jako v menu a obchodu.
            val sideFix  = (centerW - minOf(W * 0.46f, H)) / 4

            Row(
                modifier          = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                                        .padding(vertical = H * 0.02f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Levý sloupec – karta hráče (stejná jako v menu) ───────────
                Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        modifier            = Modifier.offset(x = (-5).dp + sideFix, y = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(H * 0.025f)
                    ) {
                        ProfileInfo(p, H)
                    }
                }

                // ── Střed – záložky a obsah ───────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(centerW)
                        .offset(x = 12.dp)
                        .padding(top = H * 0.05f, bottom = H * 0.085f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CampaignTitle(s.profileTitle, fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileTab.entries.forEach { t ->
                            val selected = t == tab
                            PlainButton(
                                text = when (t) {
                                    ProfileTab.OVERVIEW  -> s.profileTabOverview
                                    ProfileTab.LOOK      -> s.profileTabLook
                                    ProfileTab.ABILITIES -> s.profileTabAbilities
                                    ProfileTab.DEBUG     -> s.profileTabDebug
                                }.uppercase(),
                                modifier  = Modifier.weight(1f).height(30.dp),
                                textColor = when {
                                    t == ProfileTab.DEBUG -> PrDebug
                                    selected              -> PrGold
                                    else                  -> PrText
                                },
                                fontSize  = 10.sp,
                                selected  = selected,
                                paddingH  = 4.dp,
                                onClick   = { tab = t }
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (tab) {
                            ProfileTab.OVERVIEW  -> OverviewTab(p) { profile = PlayerProfileManager.profile }
                            ProfileTab.LOOK      -> LookTabContent(p) { profile = it }
                            ProfileTab.ABILITIES -> AbilitiesTab(p) { profile = it }
                            ProfileTab.DEBUG     -> DebugTab(p) { profile = PlayerProfileManager.profile }
                        }
                    }
                }

                // ── Pravý sloupec – Zpět na stejném místě jako v obchodu ──────
                Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        modifier            = Modifier.offset(x = 25.dp - sideFix, y = 35.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(H * 0.005f)
                    ) {
                        Box(Modifier.graphicsLayer { alpha = 0f }) {
                            IconMenuButton(imageRes = R.drawable.button_7, label = s.shop, size = iconSize, onClick = {})
                        }
                        Box(Modifier.graphicsLayer { alpha = 0f }) {
                            IconMenuButton(imageRes = R.drawable.button_5, label = s.settings, size = iconSize, onClick = {})
                        }
                        IconMenuButton(imageRes = R.drawable.button_6, label = s.back.removePrefix("← "), size = iconSize, onClick = { onBack() })
                    }
                }
            }
        }
    }
}

// ── Sdílené stavební prvky ────────────────────────────────────────────────────

/** Panel s herní texturou jako podkladem – velikost určuje obsah / modifier, ne textura. */
@Composable
private fun TexturedPanel(
    @DrawableRes textureRes: Int,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier, contentAlignment = contentAlignment) {
        Image(
            painter            = painterResource(textureRes),
            contentDescription = null,
            modifier           = Modifier.matchParentSize(),
            contentScale       = ContentScale.FillBounds
        )
        content()
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null, trailingColor: Color = PrMuted) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            Text(title.uppercase(), color = PrGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (trailing != null) Text(trailing, color = trailingColor, fontSize = 9.sp)
        }
        Image(
            painter            = painterResource(R.drawable.bg_separator),
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth(),
            contentScale       = ContentScale.FillWidth
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color(0xFF14100C))
            .border(0.5.dp, color.copy(alpha = 0.35f), shape)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
        )
    }
}

// ── Záložka PŘEHLED ───────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(profile: PlayerProfile, onProfileChanged: () -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Jméno, úroveň a XP
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            CampaignTitle(profile.name, fontSize = 17.sp)
            Text(
                "${s.profileLevel.format(profile.level)}  ·  ${profile.xp} / ${profile.xpNeeded()} XP",
                color = PrGold, fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
        ProgressBar(profile.xp.toFloat() / profile.xpNeeded(), PrGold, Modifier.fillMaxWidth())

        // Statistiky
        val wins    = profile.winsOffline + profile.winsOnline
        val winRate = if (profile.totalGames > 0) wins * 100 / profile.totalGames else 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile(R.drawable.trophy_icon, "$wins",                  s.profileWins,       Modifier.weight(1f))
            StatTile(R.drawable.utok_icon,   "${profile.winsOnline}",  s.profileWinsOnline, Modifier.weight(1f))
            StatTile(R.drawable.card_icon,   "${profile.totalGames}",  s.profilePlayed,     Modifier.weight(1f))
            StatTile(R.drawable.star_icon,   "$winRate %",             s.profileWinRate,    Modifier.weight(1f))
        }

        QuestSection(onProfileChanged)
    }
}

@Composable
private fun StatTile(@DrawableRes iconRes: Int, value: String, label: String, modifier: Modifier = Modifier) {
    TexturedPanel(R.drawable.plain_button, modifier.height(46.dp)) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), contentScale = ContentScale.Fit)
            Column {
                Text(value, color = PrText, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, color = PrMuted, fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1)
            }
        }
    }
}

// ── Denní úkoly ───────────────────────────────────────────────────────────────

@Composable
private fun QuestSection(onProfileChanged: () -> Unit) {
    var quests    by remember { mutableStateOf(QuestManager.quests) }
    val canReroll = QuestManager.canReroll()
    val s = LocalStrings.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(s.questsTitle, trailing = s.questsReset)

        val open = quests.filter { !it.claimed }
        if (open.isEmpty()) {
            Text(
                s.questsAllDone,
                color = PrMuted, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center
            )
        }
        open.forEach { quest ->
            QuestRow(
                quest     = quest,
                canReroll = canReroll && !quest.completed,
                onClaim   = {
                    QuestManager.claimQuest(quest.id)
                    quests = QuestManager.quests
                    onProfileChanged()
                },
                onReroll  = {
                    QuestManager.reroll(quest.id)
                    quests = QuestManager.quests
                }
            )
        }
    }
}

@Composable
private fun QuestRow(quest: DailyQuest, canReroll: Boolean, onClaim: () -> Unit, onReroll: () -> Unit) {
    val accent = if (quest.completed) PrGreen else PrGold
    TexturedPanel(R.drawable.plain_button_longer, Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(painterResource(quest.iconRes()), contentDescription = null, modifier = Modifier.size(22.dp), contentScale = ContentScale.Fit)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(quest.label(), color = PrText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressBar(quest.progress.toFloat() / quest.target, accent, Modifier.weight(1f), height = 5.dp)
                    Text("${quest.progress.coerceAtMost(quest.target)} / ${quest.target}", color = PrMuted, fontSize = 8.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (quest.rewardXp   > 0) Reward(R.drawable.star_icon,     "${quest.rewardXp} XP", Color(0xFF7EE8A2))
                if (quest.rewardGold > 0) Reward(R.drawable.goldcoin_icon, "${quest.rewardGold}",  PrGold)
                if (quest.rewardGems > 0) Reward(R.drawable.diamond_icon,  "${quest.rewardGems}",  PrGems)
            }

            when {
                quest.canClaim -> PlainButton(
                    text = LocalStrings.current.questClaim, modifier = Modifier.width(72.dp).height(26.dp),
                    textColor = PrGreen, fontSize = 9.sp, paddingH = 4.dp, onClick = onClaim
                )
                canReroll -> PlainButton(
                    text = "↺", modifier = Modifier.size(26.dp), buttonRes = R.drawable.plain_button_mini,
                    textColor = PrMuted, fontSize = 12.sp, paddingH = 0.dp, paddingV = 0.dp, onClick = onReroll
                )
            }
        }
    }
}

@Composable
private fun Reward(@DrawableRes iconRes: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(11.dp))
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Záložka VZHLED ────────────────────────────────────────────────────────────

// castle_player_4 (Tábor psanců) tu záměrně chybí – je to hrad goblinské lokace
// v kampani (CampaignData.kt: loc_goblins), ne skin volitelný hráčem.
private val CASTLE_SKINS = listOf(
    "castle_player", "castle_player_2", "castle_player_3", "castle_player_5",
    "castle_player_6", "castle_player_7", "castle_player_8", "castle_player_9", "castle_player_10",
    "castle_player_11", "castle_player_12", "castle_player_13"
)
private val WALL_SKINS = listOf("wall_player", "wall_player2", "wall_player3", "wall_player4", "wall_player5", "wall_player6")
private val CARD_BACK_SKINS = listOf("card_back_frame", "card_back_frame_2", "card_back_frame_3")

/** Kolik dlaždic se vejde na řádek mřížky vzhledu. */
private const val LOOK_COLUMNS = 6

@Composable
private fun LookTabContent(profile: PlayerProfile, onChanged: (PlayerProfile) -> Unit) {
    var look by remember { mutableStateOf(LookTab.AVATAR) }
    val s = LocalStrings.current

    fun save(updated: PlayerProfile) {
        PlayerProfileManager.save(updated)
        onChanged(updated)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LookTab.entries.forEach { t ->
                PlainButton(
                    text = when (t) {
                        LookTab.AVATAR    -> s.profileLookAvatar
                        LookTab.CASTLE    -> s.profileLookCastle
                        LookTab.WALL      -> s.profileLookWall
                        LookTab.CARD_BACK -> s.profileLookCardBack
                    },
                    modifier  = Modifier.weight(1f).height(24.dp),
                    textColor = if (t == look) PrGold else PrText,
                    fontSize  = 9.sp,
                    selected  = t == look,
                    paddingH  = 4.dp,
                    paddingV  = 2.dp,
                    onClick   = { look = t }
                )
            }
        }
        Image(
            painter            = painterResource(R.drawable.bg_separator),
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth(),
            contentScale       = ContentScale.FillWidth
        )

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val gap   = 6.dp
            val tileW = (maxWidth - gap * (LOOK_COLUMNS - 1)) / LOOK_COLUMNS
            Column(
                modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (look) {
                    LookTab.AVATAR -> LookGrid(AVATARS.map { it.first }, profile.avatar, tileW, tileW, gap,
                        onPick = { save(profile.copy(avatar = it)) }
                    ) { id -> AvatarDisplay(id, sizeDp = (tileW - 12.dp).value, modifier = Modifier.clip(RoundedCornerShape(4.dp))) }

                    LookTab.CASTLE -> {
                        LookGrid(CASTLE_SKINS, profile.castleSkin, tileW, tileW * 1.25f, gap,
                            onPick = { save(profile.copy(castleSkin = it)) }
                        ) { id -> SkinImage(castleSkinDrawable(id)) }
                        SelectedLabel(castleSkinLabel(profile.castleSkin))
                    }

                    LookTab.WALL -> {
                        LookGrid(WALL_SKINS, profile.wallSkin, tileW, tileW * 1.25f, gap,
                            onPick = { save(profile.copy(wallSkin = it)) }
                        ) { id -> SkinImage(wallSkinDrawable(id)) }
                        SelectedLabel(wallSkinLabel(profile.wallSkin))
                    }

                    LookTab.CARD_BACK -> {
                        LookGrid(CARD_BACK_SKINS, profile.cardBackSkin, tileW, tileW * 1.4f, gap,
                            onPick = { save(profile.copy(cardBackSkin = it)) }
                        ) { id ->
                            Image(
                                painter            = painterResource(cardBackSkinDrawable(id)),
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize().padding(7.dp),
                                contentScale       = ContentScale.FillBounds
                            )
                        }
                        SelectedLabel(cardBackLabel(profile.cardBackSkin))
                    }
                }
            }
        }
    }
}

/** Mřížka dlaždic vzhledu: [LOOK_COLUMNS] na řádek, neúplný řádek na střed. */
@Composable
private fun LookGrid(
    ids: List<String>,
    current: String,
    tileW: Dp,
    tileH: Dp,
    gap: Dp,
    onPick: (String) -> Unit,
    preview: @Composable BoxScope.(String) -> Unit
) {
    ids.chunked(LOOK_COLUMNS).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            row.forEach { id ->
                val selected = id == current
                val shape    = RoundedCornerShape(6.dp)
                TexturedPanel(
                    R.drawable.plain_button_mini,
                    Modifier
                        .size(tileW, tileH)
                        .then(if (selected) Modifier.border(1.5.dp, PrGold, shape) else Modifier)
                        .clip(shape)
                        .clickable(enabled = !selected) { SoundManager.playMenuTap(); onPick(id) }
                ) {
                    preview(id)
                    if (selected) Image(
                        painter            = painterResource(R.drawable.check_icon),
                        contentDescription = null,
                        modifier           = Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinImage(@DrawableRes resId: Int) {
    Image(
        painter            = painterResource(resId),
        contentDescription = null,
        modifier           = Modifier.fillMaxSize().padding(7.dp),
        contentScale       = ContentScale.Fit
    )
}

@Composable
private fun SelectedLabel(label: String) {
    Text(label, color = PrGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun castleSkinLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "castle_player"   -> s.castleClassic
        "castle_player_2" -> s.castleStone
        "castle_player_3" -> s.castleDark
        else              -> s.castleVariant.format(id.substringAfterLast('_').toIntOrNull() ?: 0)
    }
}

@Composable
private fun wallSkinLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "wall_player" -> s.wallClassic
        else          -> s.wallVariant.format(id.removePrefix("wall_player").toIntOrNull() ?: 0)
    }
}

@Composable
private fun cardBackLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "card_back_frame"   -> s.cardBackBasic
        "card_back_frame_2" -> s.cardBackStyle2
        else                -> s.cardBackStyle3
    }
}

// ── Záložka SCHOPNOSTI ────────────────────────────────────────────────────────

@Composable
private fun AbilitiesTab(profile: PlayerProfile, onChanged: (PlayerProfile) -> Unit) {
    val s = LocalStrings.current
    val activeCount = profile.activeAbilities.size
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(
            s.profileSectionAbilities,
            trailing      = s.profileActiveCount.format(activeCount, PassiveAbility.MAX_ACTIVE),
            trailingColor = if (activeCount >= PassiveAbility.MAX_ACTIVE) PrGold else PrMuted
        )
        Column(
            modifier            = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PassiveAbility.entries.forEach { ability ->
                AbilityRow(ability, profile, onChanged)
            }
        }
    }
}

@Composable
private fun AbilityRow(ability: PassiveAbility, profile: PlayerProfile, onChanged: (PlayerProfile) -> Unit) {
    val isUnlocked = ability.id in profile.unlockedAbilities
    val isActive   = ability.id in profile.activeAbilities
    val canUnlock  = profile.level >= ability.unlockLevel
    val slotsLeft  = profile.activeAbilities.size < PassiveAbility.MAX_ACTIVE
    val shape      = RoundedCornerShape(6.dp)
    val btn        = Modifier.width(66.dp).height(26.dp)

    TexturedPanel(
        R.drawable.plain_button_longer,
        Modifier.fillMaxWidth().then(if (isActive) Modifier.border(1.5.dp, PrGreen.copy(alpha = 0.8f), shape) else Modifier)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter            = painterResource(ability.iconRes),
                contentDescription = null,
                modifier           = Modifier.size(22.dp).alpha(if (canUnlock) 1f else 0.35f),
                contentScale       = ContentScale.Fit
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(ability.localizedTitle(), color = if (canUnlock) PrText else PrMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(ability.localizedDescription(), color = PrMuted, fontSize = 8.sp, lineHeight = 11.sp)
            }
            when {
                !canUnlock -> PlainButton(
                    text = "Lv. ${ability.unlockLevel}", modifier = btn, enabled = false,
                    textColor = PrMuted, fontSize = 9.sp, paddingH = 4.dp
                )
                !isUnlocked -> {
                    val canAfford = profile.gold >= ability.goldCost
                    PlainButtonWithIcon(
                        text = "${ability.goldCost}", iconRes = R.drawable.goldcoin_icon, modifier = btn,
                        enabled = canAfford, textColor = PrGold, fontSize = 9.sp, paddingH = 4.dp,
                        onClick = {
                            if (PlayerProfileManager.buyAbility(ability.id)) onChanged(PlayerProfileManager.profile!!)
                        }
                    )
                }
                isActive -> PlainButton(
                    text = LocalStrings.current.toggleOn, modifier = btn,
                    textColor = PrGreen, fontSize = 9.sp, paddingH = 4.dp,
                    onClick = {
                        PlayerProfileManager.setActiveAbilities(profile.activeAbilities - ability.id)
                        onChanged(PlayerProfileManager.profile!!)
                    }
                )
                else -> PlainButton(
                    text = if (slotsLeft) LocalStrings.current.toggleOff else LocalStrings.current.slotFull, modifier = btn,
                    enabled = slotsLeft, textColor = PrText, fontSize = 9.sp, paddingH = 4.dp,
                    onClick = {
                        PlayerProfileManager.setActiveAbilities(profile.activeAbilities + ability.id)
                        onChanged(PlayerProfileManager.profile!!)
                    }
                )
            }
        }
    }
}

// ── Záložka DEBUG ─────────────────────────────────────────────────────────────

@Composable
private fun DebugTab(profile: PlayerProfile, onProfileChanged: () -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(s.profileTabDebug)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val btn = Modifier.weight(1f).height(30.dp)
            PlainButtonWithIcon("+500", R.drawable.goldcoin_icon, btn, textColor = PrGold, fontSize = 10.sp, paddingH = 4.dp) {
                PlayerProfileManager.addRewards(xp = 0, gold = 500, gems = 0); onProfileChanged()
            }
            PlainButtonWithIcon("+50", R.drawable.diamond_icon, btn, textColor = PrGems, fontSize = 10.sp, paddingH = 4.dp) {
                PlayerProfileManager.addRewards(xp = 0, gold = 0, gems = 50); onProfileChanged()
            }
            PlainButtonWithIcon("+500", R.drawable.dust_icon, btn, textColor = PrDust, fontSize = 10.sp, paddingH = 4.dp) {
                PlayerProfileManager.save(profile.copy(dust = profile.dust + 500)); onProfileChanged()
            }
            PlainButtonWithIcon("+100 XP", R.drawable.star_icon, btn, textColor = PrGreen, fontSize = 10.sp, paddingH = 4.dp) {
                PlayerProfileManager.addRewards(xp = 100, gold = 0, gems = 0); onProfileChanged()
            }
        }

        DebugToggleRow(s.profileUnlockAll, profile.allCardsUnlocked) {
            CardCollectionManager.setAllCardsUnlocked(!profile.allCardsUnlocked)
            onProfileChanged()
        }
        // Odemkne všechny lokace i soupeře kampaně. Nemění postup (počitadla "X/10"
        // ani odměny) – jen obchází zámky, takže vypnutím se hráč vrátí přesně tam,
        // kde skutečně je.
        var campaignUnlocked by remember { mutableStateOf(CampaignManager.allUnlocked) }
        DebugToggleRow(s.profileUnlockCampaign, campaignUnlocked) {
            CampaignManager.setAllUnlocked(!campaignUnlocked)
            campaignUnlocked = CampaignManager.allUnlocked
        }
    }
}

@Composable
private fun DebugToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    TexturedPanel(R.drawable.plain_button_longer, Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = PrText, fontSize = 10.sp, modifier = Modifier.weight(1f))
            PlainButton(
                text      = if (checked) LocalStrings.current.toggleOn else LocalStrings.current.toggleOff,
                modifier  = Modifier.width(66.dp).height(26.dp),
                textColor = if (checked) PrGreen else PrMuted,
                fontSize  = 9.sp,
                paddingH  = 4.dp,
                onClick   = onToggle
            )
        }
    }
}
