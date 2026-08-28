package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrGold   = Color(0xFFD4A843)
private val PrText   = Color(0xFFEDE0C4)
private val PrMuted  = Color(0xFF7A6E5F)
private val PrGems   = Color(0xFF7EC8E3)
private val PrGreen  = Color(0xFF3DBFAD)

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

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    var profile by remember { mutableStateOf(PlayerProfileManager.profile) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Textured background
        Image(
            painter            = painterResource(R.drawable.bg_plain),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        if (profile != null) {
            Row(
                modifier          = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Levý sloupec ─────────────────────────────────────────────────
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Image(
                        painter            = painterResource(R.drawable.bg_side_panels),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Avatar + jméno + level
                        Row(
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AvatarDisplay(profile!!.avatar, sizeDp = 42f)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(profile!!.name, color = PrText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    LocalStrings.current.profileLevel.format(profile!!.level),
                                    color = PrGold, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // XP bar
                        val xpFrac = (profile!!.xp.toFloat() / profile!!.xpNeeded()).coerceIn(0f, 1f)
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            LinearProgressIndicator(
                                progress   = { xpFrac },
                                modifier   = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color      = PrGold,
                                trackColor = PrMuted.copy(alpha = 0.2f),
                                strokeCap  = StrokeCap.Round
                            )
                            Text(
                                "${profile!!.xp} / ${profile!!.xpNeeded()} XP",
                                color    = PrMuted, fontSize = 9.sp,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                            )
                        }

                        // Měna
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CurrencyBadge(R.drawable.goldcoin_icon, profile!!.gold,  PrGold,              LocalStrings.current.profileGold, Modifier.weight(1f))
                            CurrencyBadge(R.drawable.diamond_icon,  profile!!.gems,  PrGems,              LocalStrings.current.profileGems, Modifier.weight(1f))
                            CurrencyBadge(R.drawable.dust_icon,     profile!!.dust,  Color(0xFFB39DDB),   LocalStrings.current.shopDust,    Modifier.weight(1f))
                        }

                        // Statistiky
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBadge(R.drawable.utok_icon, "${profile!!.winsOffline + profile!!.winsOnline}", LocalStrings.current.profileWins,   Modifier.weight(1f))
                            StatBadge(R.drawable.card_icon, "${profile!!.totalGames}",                         LocalStrings.current.profilePlayed, Modifier.weight(1f))
                        }

                        // Denní questy
                        QuestSection(onProfileChanged = { profile = PlayerProfileManager.profile })

                        // DEBUG
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x33CC3333), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "DEBUG",
                                color         = Color(0xFFCC3333).copy(alpha = 0.7f),
                                fontSize      = 7.sp,
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                DebugBtn("+500 zl", PrGold, Modifier.weight(1f)) {
                                    profile = PlayerProfileManager.addRewards(xp = 0, gold = 500, gems = 0)
                                }
                                DebugBtn("+50 diam", PrGems, Modifier.weight(1f)) {
                                    profile = PlayerProfileManager.addRewards(xp = 0, gold = 0, gems = 50)
                                }
                                DebugBtn("+100 XP", PrGreen, Modifier.weight(1f)) {
                                    profile = PlayerProfileManager.addRewards(xp = 100, gold = 0, gems = 0)
                                }
                            }
                            val allUnlocked = profile!!.allCardsUnlocked
                            val dustColor   = Color(0xFFB39DDB)
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    LocalStrings.current.profileUnlockAll,
                                    color    = PrMuted,
                                    fontSize = 9.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (allUnlocked) PrGreen.copy(alpha = 0.2f) else PrMuted.copy(alpha = 0.12f),
                                            RoundedCornerShape(5.dp)
                                        )
                                        .clickable {
                                            SoundManager.playMenuTap()
                                            CardCollectionManager.setAllCardsUnlocked(!allUnlocked)
                                            profile = PlayerProfileManager.profile
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (allUnlocked) LocalStrings.current.toggleOn else LocalStrings.current.toggleOff,
                                        color      = if (allUnlocked) PrGreen else PrMuted,
                                        fontSize   = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DebugBtn("+500 pr", dustColor, Modifier) {
                                    val p = PlayerProfileManager.profile!!
                                    PlayerProfileManager.save(p.copy(dust = p.dust + 500))
                                    profile = PlayerProfileManager.profile
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── Pravý sloupec ─────────────────────────────────────────────────
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Image(
                        painter            = painterResource(R.drawable.bg_side_panels),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize().graphicsLayer { scaleX = -1f },
                        contentScale       = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionHeader(LocalStrings.current.profileSectionAvatar)
                        AvatarPicker(current = profile!!.avatar, level = profile!!.level, onChanged = { profile = it })

                        SectionHeader(LocalStrings.current.profileSectionCastle)
                        CastleSkinPicker(current = profile!!.castleSkin, onChanged = { profile = it })

                        SectionHeader(LocalStrings.current.profileSectionWall)
                        WallSkinPicker(current = profile!!.wallSkin, onChanged = { profile = it })

                        SectionHeader(LocalStrings.current.profileSectionCardBack)
                        CardBackSkinPicker(current = profile!!.cardBackSkin, onChanged = { profile = it })

                        SectionHeader(LocalStrings.current.profileSectionAbilities)
                        val activeCount = profile!!.activeAbilities.size
                        Text(
                            LocalStrings.current.profileActiveCount.format(activeCount, PassiveAbility.MAX_ACTIVE),
                            color    = if (activeCount >= PassiveAbility.MAX_ACTIVE) PrGold else PrMuted,
                            fontSize = 8.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                        PassiveAbility.entries.forEach { ability ->
                            AbilityRow(ability = ability, profile = profile!!, onChanged = { profile = it })
                        }

                        SectionHeader(LocalStrings.current.profileSectionCosmetics)
                        ComingSoonCard(LocalStrings.current.profileCosmeticsSoon)

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Tlačítko Zpět (floating) ──────────────────────────────────────────
        PlainButton(
            text      = "Zpět",
            modifier  = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp),
            textColor = PrMuted,
            fontSize  = 12.sp,
            paddingH  = 14.dp,
            paddingV  = 8.dp,
            onClick   = { SoundManager.playMenuTap(); onBack() }
        )
    }
}

// ── Ability row ───────────────────────────────────────────────────────────────

@Composable
private fun AbilityRow(
    ability: PassiveAbility,
    profile: PlayerProfile,
    onChanged: (PlayerProfile) -> Unit
) {
    val isUnlocked = ability.id in profile.unlockedAbilities
    val isActive   = ability.id in profile.activeAbilities
    val canUnlock  = profile.level >= ability.unlockLevel
    val slotsLeft  = profile.activeAbilities.size < PassiveAbility.MAX_ACTIVE

    val bgColor = when {
        isActive   -> PrGreen.copy(alpha = 0.10f)
        isUnlocked -> PrGold.copy(alpha = 0.06f)
        else       -> Color(0x22000000)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter            = painterResource(ability.iconRes),
            contentDescription = null,
            modifier           = Modifier.size(18.dp).alpha(if (canUnlock) 1f else 0.35f)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                ability.localizedTitle(),
                color      = if (canUnlock) PrText else PrMuted,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(ability.localizedDescription(), color = PrMuted, fontSize = 8.sp, lineHeight = 11.sp)
        }
        when {
            !canUnlock -> {
                Box(
                    modifier         = Modifier.background(PrMuted.copy(alpha = 0.15f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Lv.${ability.unlockLevel}", color = PrMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            !isUnlocked -> {
                val canAfford = profile.gold >= ability.goldCost
                Box(
                    modifier = Modifier
                        .background(
                            if (canAfford) PrGold.copy(alpha = 0.18f) else PrMuted.copy(alpha = 0.10f),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable(enabled = canAfford) {
                            SoundManager.playMenuTap()
                            if (PlayerProfileManager.buyAbility(ability.id)) {
                                onChanged(PlayerProfileManager.profile!!)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                        Text("${ability.goldCost}", color = if (canAfford) PrGold else PrMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            isActive -> {
                Box(
                    modifier = Modifier
                        .background(PrGreen.copy(alpha = 0.20f), RoundedCornerShape(5.dp))
                        .clickable {
                            SoundManager.playMenuTap()
                            val newList = profile.activeAbilities - ability.id
                            PlayerProfileManager.setActiveAbilities(newList)
                            onChanged(PlayerProfileManager.profile!!)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ON", color = PrGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .background(PrMuted.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                        .clickable(enabled = slotsLeft) {
                            SoundManager.playMenuTap()
                            val newList = profile.activeAbilities + ability.id
                            PlayerProfileManager.setActiveAbilities(newList)
                            onChanged(PlayerProfileManager.profile!!)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (slotsLeft) "OFF" else LocalStrings.current.slotFull,
                        color      = if (slotsLeft) PrText else PrMuted,
                        fontSize   = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Avatar picker ─────────────────────────────────────────────────────────────

@Composable
private fun AvatarPicker(
    current:   String,
    level:     Int,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        AVATARS.forEach { (avatarId, unlockLevel) ->
            val unlocked = level >= unlockLevel
            val selected = avatarId == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(
                        if (selected) PrGold.copy(alpha = 0.20f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .then(if (selected) Modifier.border(1.5.dp, PrGold, RoundedCornerShape(8.dp)) else Modifier)
                    .alpha(if (unlocked) 1f else 0.30f)
                    .clickable(enabled = unlocked && !selected) {
                        SoundManager.playMenuTap()
                        val updated = PlayerProfileManager.profile!!.copy(avatar = avatarId)
                        PlayerProfileManager.save(updated)
                        onChanged(updated)
                    },
                contentAlignment = Alignment.Center
            ) {
                AvatarDisplay(avatarId, sizeDp = 28f)
            }
        }
    }
}

// ── Castle skin picker ────────────────────────────────────────────────────────

private val CASTLE_SKINS = listOf(
    "castle_player", "castle_player_2", "castle_player_3", "castle_player_4", "castle_player_5",
    "castle_player_6", "castle_player_7", "castle_player_8", "castle_player_9", "castle_player_10",
    "castle_player_11", "castle_player_12", "castle_player_13"
)

@Composable
private fun castleSkinLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "castle_player"   -> s.castleClassic
        "castle_player_2" -> s.castleStone
        "castle_player_3" -> s.castleDark
        "castle_player_4" -> s.castleOutlawCamp
        else              -> s.castleVariant.format(id.substringAfterLast('_').toIntOrNull() ?: 0)
    }
}

@Composable
private fun CastleSkinPicker(
    current:   String,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CASTLE_SKINS.forEach { skinId ->
            val label    = castleSkinLabel(skinId)
            val selected = skinId == current
            val resId    = castleSkinDrawable(skinId)
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .background(
                        if (selected) PrGold.copy(alpha = 0.15f) else Color(0x22000000),
                        RoundedCornerShape(8.dp)
                    )
                    .then(if (selected) Modifier.border(1.5.dp, PrGold, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable(enabled = !selected) {
                        SoundManager.playMenuTap()
                        val updated = PlayerProfileManager.profile!!.copy(castleSkin = skinId)
                        PlayerProfileManager.save(updated)
                        onChanged(updated)
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter            = painterResource(resId),
                    contentDescription = label,
                    modifier           = Modifier.fillMaxWidth().height(60.dp),
                    contentScale       = ContentScale.Fit
                )
                Text(label, color = if (selected) PrGold else PrMuted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                if (selected) Text(LocalStrings.current.profileActive, color = PrGold, fontSize = 8.sp)
            }
        }
    }
}

// ── Wall skin picker ──────────────────────────────────────────────────────────

private val WALL_SKINS = listOf("wall_player", "wall_player2", "wall_player3", "wall_player4", "wall_player5", "wall_player6")

@Composable
private fun wallSkinLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "wall_player" -> s.wallClassic
        else          -> s.wallVariant.format(id.removePrefix("wall_player").toIntOrNull() ?: 0)
    }
}

@Composable
private fun WallSkinPicker(
    current:   String,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WALL_SKINS.forEach { skinId ->
            val label    = wallSkinLabel(skinId)
            val selected = skinId == current
            val resId    = wallSkinDrawable(skinId)
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .background(
                        if (selected) PrGold.copy(alpha = 0.15f) else Color(0x22000000),
                        RoundedCornerShape(8.dp)
                    )
                    .then(if (selected) Modifier.border(1.5.dp, PrGold, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable(enabled = !selected) {
                        SoundManager.playMenuTap()
                        val updated = PlayerProfileManager.profile!!.copy(wallSkin = skinId)
                        PlayerProfileManager.save(updated)
                        onChanged(updated)
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter            = painterResource(resId),
                    contentDescription = label,
                    modifier           = Modifier.fillMaxWidth().height(60.dp),
                    contentScale       = ContentScale.Fit
                )
                Text(label, color = if (selected) PrGold else PrMuted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                if (selected) Text(LocalStrings.current.profileActive, color = PrGold, fontSize = 8.sp)
            }
        }
    }
}

// ── Card back skin picker ─────────────────────────────────────────────────────

private val CARD_BACK_SKINS = listOf("card_back_frame", "card_back_frame_2", "card_back_frame_3")

@Composable
private fun cardBackLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "card_back_frame"   -> s.cardBackBasic
        "card_back_frame_2" -> s.cardBackStyle2
        else                -> s.cardBackStyle3
    }
}

@Composable
private fun CardBackSkinPicker(
    current:   String,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CARD_BACK_SKINS.forEach { skinId ->
            val label    = cardBackLabel(skinId)
            val selected = skinId == current
            val resId    = cardBackSkinDrawable(skinId)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) PrGold.copy(alpha = 0.15f) else Color(0x22000000),
                        RoundedCornerShape(8.dp)
                    )
                    .then(if (selected) Modifier.border(1.5.dp, PrGold, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable(enabled = !selected) {
                        SoundManager.playMenuTap()
                        val updated = PlayerProfileManager.profile!!.copy(cardBackSkin = skinId)
                        PlayerProfileManager.save(updated)
                        onChanged(updated)
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter            = painterResource(resId),
                    contentDescription = label,
                    modifier           = Modifier.width(36.dp).height(52.dp),
                    contentScale       = ContentScale.FillBounds
                )
                Text(label, color = if (selected) PrGold else PrMuted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (selected) Text(LocalStrings.current.profileActive, color = PrGold, fontSize = 8.sp)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun CurrencyBadge(
    @DrawableRes iconRes: Int,
    amount: Int,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
        Text("$amount", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PrMuted, fontSize = 8.sp)
    }
}

@Composable
private fun StatBadge(
    @DrawableRes iconRes: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(value, color = PrText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PrMuted, fontSize = 8.sp)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = PrGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Image(
            painter            = painterResource(R.drawable.bg_separator),
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth(),
            contentScale       = ContentScale.FillWidth
        )
    }
}

@Composable
private fun DebugBtn(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Denní questy ─────────────────────────────────────────────────────────────

@Composable
private fun QuestSection(onProfileChanged: () -> Unit) {
    var quests    by remember { mutableStateOf(QuestManager.quests) }
    val canReroll = QuestManager.canReroll()
    val QuestGold  = Color(0xFFD4A843)
    val QuestGreen = Color(0xFF4DB86E)
    val QuestMuted = Color(0xFF7A6E5F)

    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(LocalStrings.current.questsTitle, color = QuestGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(LocalStrings.current.questsReset, color = QuestMuted, fontSize = 8.sp)
        }
        Image(
            painter            = painterResource(R.drawable.bg_separator),
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth(),
            contentScale       = ContentScale.FillWidth
        )

        quests.filter { !it.claimed }.forEach { quest ->
            QuestCard(
                quest      = quest,
                canReroll  = canReroll && !quest.completed,
                onClaim    = {
                    QuestManager.claimQuest(quest.id)
                    quests = QuestManager.quests
                    onProfileChanged()
                },
                onReroll   = {
                    QuestManager.reroll(quest.id)
                    quests = QuestManager.quests
                },
                questGold  = QuestGold,
                questGreen = QuestGreen,
                questMuted = QuestMuted
            )
        }
    }
}

@Composable
private fun QuestCard(
    quest     : DailyQuest,
    canReroll : Boolean,
    onClaim   : () -> Unit,
    onReroll  : () -> Unit,
    questGold : Color,
    questGreen: Color,
    questMuted: Color
) {
    val progress = quest.progress.toFloat() / quest.target.toFloat()
    val accent   = when {
        quest.claimed   -> questMuted
        quest.completed -> questGreen
        else            -> questGold
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = if (quest.completed) 0.10f else 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier              = Modifier.weight(1f)
            ) {
                Image(painterResource(quest.iconRes()), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(quest.label(), color = if (quest.claimed) questMuted else PrText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            if (canReroll) {
                Box(
                    modifier = Modifier
                        .background(questMuted.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                        .clickable { SoundManager.playMenuTap(); onReroll() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("↺", fontSize = 10.sp, color = questMuted)
                }
            }
        }

        LinearProgressIndicator(
            progress   = { progress.coerceIn(0f, 1f) },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color      = accent,
            trackColor = questMuted.copy(alpha = 0.15f),
            strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("${quest.progress} / ${quest.target}", color = questMuted, fontSize = 8.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (quest.rewardXp > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Image(painterResource(R.drawable.star_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                    Text("${quest.rewardXp} XP", color = Color(0xFF7EE8A2), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (quest.rewardGold > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                    Text("${quest.rewardGold}", color = questGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (quest.rewardGems > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Image(painterResource(R.drawable.diamond_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                    Text("${quest.rewardGems}", color = Color(0xFF6EE0F0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (quest.canClaim) {
                    Box(
                        modifier = Modifier
                            .background(questGreen.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
                            .clickable { SoundManager.playMenuTap(); onClaim() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(LocalStrings.current.questClaim, color = questGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComingSoonCard(description: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(painterResource(R.drawable.lock_icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(description, color = PrMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}
