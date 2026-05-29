package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrBgDeep    = Color(0xFF0D0A0E)
private val PrBgPanel   = Color(0xFF13101A)
private val PrBgCard    = Color(0xFF1A1320)
private val PrGold      = Color(0xFFD4A843)
private val PrText      = Color(0xFFEDE0C4)
private val PrMuted     = Color(0xFF7A6E5F)
private val PrGems      = Color(0xFF7EC8E3)
private val PrGreen     = Color(0xFF3DBFAD)

/** Čtyři základní avatary — všechny odemčeny na levelu 1. */
private val AVATARS = listOf(
    "⚔️" to 1,
    "🧙" to 1,
    "🛡️" to 1,
    "💀" to 1
)

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    var profile by remember { mutableStateOf(PlayerProfileManager.profile) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrBgDeep, PrBgPanel, PrBgDeep)))
    ) {
        // ── Zpět ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(PrBgCard)
                .border(1.dp, PrMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(LocalStrings.current.back, color = PrMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (profile == null) return@Box

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Levý sloupec ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar + jméno + level (řada)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrGold.copy(alpha = 0.12f))
                            .border(2.dp, PrGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(profile!!.avatar, fontSize = 24.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile!!.name, color = PrText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(LocalStrings.current.profileLevel.format(profile!!.level), color = PrGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // XP bar
                val xpFrac = (profile!!.xp.toFloat() / profile!!.xpNeeded()).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    LinearProgressIndicator(
                        progress     = { xpFrac },
                        modifier     = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color        = PrGold,
                        trackColor   = PrMuted.copy(alpha = 0.2f),
                        strokeCap    = StrokeCap.Round
                    )
                    Text(
                        "${profile!!.xp} / ${profile!!.xpNeeded()} XP",
                        color = PrMuted, fontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }

                // Měna
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CurrencyBadge("🪙", profile!!.gold, PrGold,           LocalStrings.current.profileGold, Modifier.weight(1f))
                    CurrencyBadge("💎", profile!!.gems, PrGems,           LocalStrings.current.profileGems, Modifier.weight(1f))
                    CurrencyBadge("✨", profile!!.dust, Color(0xFFB39DDB), LocalStrings.current.shopDust,    Modifier.weight(1f))
                }

                // Statistiky
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadge("⚔️", "${profile!!.winsOffline + profile!!.winsOnline}", LocalStrings.current.profileWins,   Modifier.weight(1f))
                    StatBadge("🎮", "${profile!!.totalGames}",                         LocalStrings.current.profilePlayed, Modifier.weight(1f))
                }

                // ── Denní questy ─────────────────────────────────────────────
                QuestSection(onProfileChanged = { profile = PlayerProfileManager.profile })

                // ── DEBUG ─────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A0A0A))
                        .border(1.dp, Color(0xFFCC3333).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "🛠 DEBUG",
                        color      = Color(0xFFCC3333).copy(alpha = 0.7f),
                        fontSize   = 7.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DebugBtn("🪙 +500", PrGold, Modifier.weight(1f)) {
                            val updated = PlayerProfileManager.addRewards(xp = 0, gold = 500, gems = 0)
                            profile = updated
                        }
                        DebugBtn("💎 +50", PrGems, Modifier.weight(1f)) {
                            val updated = PlayerProfileManager.addRewards(xp = 0, gold = 0, gems = 50)
                            profile = updated
                        }
                        DebugBtn("⭐ +100", PrGreen, Modifier.weight(1f)) {
                            val updated = PlayerProfileManager.addRewards(xp = 100, gold = 0, gems = 0)
                            profile = updated
                        }
                    }

                    // Přepínač "Všechny karty odemčeny"
                    val allUnlocked = profile!!.allCardsUnlocked
                    val dustColor   = Color(0xFFB39DDB)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🃏 ${LocalStrings.current.profileUnlockAll}",
                            color    = PrMuted,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (allUnlocked) PrGreen.copy(alpha = 0.15f)
                                    else PrMuted.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (allUnlocked) PrGreen.copy(alpha = 0.5f)
                                    else PrMuted.copy(alpha = 0.25f),
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
                        // Debug: +500 prachu
                        DebugBtn("✨ +500", dustColor, Modifier) {
                            val p = PlayerProfileManager.profile!!
                            PlayerProfileManager.save(p.copy(dust = p.dust + 500))
                            profile = PlayerProfileManager.profile
                        }
                    }
                }
            }

            // ── Pravý sloupec ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader("🖼️  ${LocalStrings.current.profileSectionAvatar}")
                AvatarPicker(
                    current   = profile!!.avatar,
                    level     = profile!!.level,
                    onChanged = { profile = it }
                )

                SectionHeader("🏰  ${LocalStrings.current.profileSectionCastle}")
                CastleSkinPicker(
                    current   = profile!!.castleSkin,
                    onChanged = { profile = it }
                )

                SectionHeader("🃏  ${LocalStrings.current.profileSectionCardBack}")
                CardBackSkinPicker(
                    current   = profile!!.cardBackSkin,
                    onChanged = { profile = it }
                )

                SectionHeader("⚡  ${LocalStrings.current.profileSectionAbilities}")
                // slot info
                val activeCount = profile!!.activeAbilities.size
                Text(
                    LocalStrings.current.profileActiveCount.format(activeCount, PassiveAbility.MAX_ACTIVE),
                    color = if (activeCount >= PassiveAbility.MAX_ACTIVE) PrGold else PrMuted,
                    fontSize = 8.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                PassiveAbility.entries.forEach { ability ->
                    AbilityRow(
                        ability   = ability,
                        profile   = profile!!,
                        onChanged = { profile = it }
                    )
                }

                SectionHeader("🎨  ${LocalStrings.current.profileSectionCosmetics}")
                ComingSoonCard(LocalStrings.current.profileCosmeticsSoon)
            }
        }
    }
}

// ── Ability row ───────────────────────────────────────────────────────────────

@Composable
private fun AbilityRow(
    ability: PassiveAbility,
    profile: PlayerProfile,
    onChanged: (PlayerProfile) -> Unit
) {
    val isUnlocked  = ability.id in profile.unlockedAbilities
    val isActive    = ability.id in profile.activeAbilities
    val canUnlock   = profile.level >= ability.unlockLevel
    val slotsLeft   = profile.activeAbilities.size < PassiveAbility.MAX_ACTIVE

    val borderColor = when {
        isActive   -> PrGreen.copy(alpha = 0.5f)
        isUnlocked -> PrGold.copy(alpha = 0.3f)
        else       -> PrMuted.copy(alpha = 0.15f)
    }
    val bgColor = when {
        isActive   -> PrGreen.copy(alpha = 0.06f)
        isUnlocked -> PrGold.copy(alpha = 0.04f)
        else       -> PrBgCard
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // icon
        Text(
            ability.icon,
            fontSize = 14.sp,
            modifier = Modifier.alpha(if (canUnlock) 1f else 0.35f)
        )
        // name + description
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                ability.localizedTitle(),
                color = if (canUnlock) PrText else PrMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                ability.localizedDescription(),
                color = PrMuted,
                fontSize = 8.sp,
                lineHeight = 11.sp
            )
        }
        // action
        when {
            !canUnlock -> {
                // level locked
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PrMuted.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("Lv.${ability.unlockLevel}", color = PrMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            !isUnlocked -> {
                // can buy
                val canAfford = profile.gold >= ability.goldCost
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (canAfford) PrGold.copy(alpha = 0.15f) else PrMuted.copy(alpha = 0.08f))
                        .border(1.dp, if (canAfford) PrGold.copy(alpha = 0.5f) else PrMuted.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                        .clickable(enabled = canAfford) {
                            SoundManager.playMenuTap()
                            if (PlayerProfileManager.buyAbility(ability.id)) {
                                onChanged(PlayerProfileManager.profile!!)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        "🪙 ${ability.goldCost}",
                        color = if (canAfford) PrGold else PrMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            isActive -> {
                // active → click to deactivate
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PrGreen.copy(alpha = 0.15f))
                        .border(1.dp, PrGreen.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                        .clickable {
                            SoundManager.playMenuTap()
                            val newList = profile.activeAbilities - ability.id
                            PlayerProfileManager.setActiveAbilities(newList)
                            onChanged(PlayerProfileManager.profile!!)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("✓ ON", color = PrGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                // purchased, inactive → activate if slot free
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PrBgCard)
                        .border(1.dp, PrMuted.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                        .clickable(enabled = slotsLeft) {
                            SoundManager.playMenuTap()
                            val newList = profile.activeAbilities + ability.id
                            PlayerProfileManager.setActiveAbilities(newList)
                            onChanged(PlayerProfileManager.profile!!)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (slotsLeft) "OFF" else LocalStrings.current.slotFull,
                        color = if (slotsLeft) PrText else PrMuted,
                        fontSize = 8.sp,
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
    current: String,
    level: Int,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AVATARS.forEach { (emoji, unlockLevel) ->
            val unlocked = level >= unlockLevel
            val selected = emoji == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            selected  -> PrGold.copy(alpha = 0.18f)
                            unlocked  -> PrBgPanel
                            else      -> PrBgCard
                        }
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = when {
                            selected -> PrGold
                            unlocked -> PrMuted.copy(alpha = 0.25f)
                            else     -> PrMuted.copy(alpha = 0.1f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .alpha(if (unlocked) 1f else 0.35f)
                    .clickable(enabled = unlocked && !selected) {
                        SoundManager.playMenuTap()
                        val updated = PlayerProfileManager.profile!!.copy(avatar = emoji)
                        PlayerProfileManager.save(updated)
                        onChanged(updated)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
        }
    }
}

// ── Castle skin picker ────────────────────────────────────────────────────────

private val CASTLE_SKINS = listOf("castle_player", "castle_player_2", "castle_player_3")

@Composable
private fun castleSkinLabel(id: String): String {
    val s = LocalStrings.current
    return when (id) {
        "castle_player"   -> s.castleClassic
        "castle_player_2" -> s.castleStone
        else              -> s.castleDark
    }
}

@Composable
private fun CastleSkinPicker(
    current: String,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CASTLE_SKINS.forEach { skinId ->
            val label = castleSkinLabel(skinId)
            val selected = skinId == current
            val resId = castleSkinDrawable(skinId)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) PrGold.copy(alpha = 0.12f) else PrBgPanel)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) PrGold else PrMuted.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
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
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentScale       = ContentScale.Fit
                )
                Text(
                    label,
                    color      = if (selected) PrGold else PrMuted,
                    fontSize   = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (selected) {
                    Text(
                        LocalStrings.current.profileActive,
                        color    = PrGold,
                        fontSize = 8.sp
                    )
                }
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
    current: String,
    onChanged: (PlayerProfile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CARD_BACK_SKINS.forEach { skinId ->
            val label = cardBackLabel(skinId)
            val selected = skinId == current
            val resId = cardBackSkinDrawable(skinId)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) PrGold.copy(alpha = 0.12f) else PrBgPanel)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) PrGold else PrMuted.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
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
                    modifier           = Modifier
                        .width(36.dp)
                        .height(52.dp),
                    contentScale       = ContentScale.FillBounds
                )
                Text(
                    label,
                    color      = if (selected) PrGold else PrMuted,
                    fontSize   = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (selected) {
                    Text(LocalStrings.current.profileActive, color = PrGold, fontSize = 8.sp)
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun CurrencyBadge(icon: String, amount: Int, color: Color, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text("$amount", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PrMuted, fontSize = 8.sp)
    }
}

@Composable
private fun StatBadge(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text(value, color = PrText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PrMuted, fontSize = 8.sp)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = PrGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun DebugBtn(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
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
    var quests       by remember { mutableStateOf(QuestManager.quests) }
    val canReroll    = QuestManager.canReroll()
    val QuestGold    = Color(0xFFD4A843)
    val QuestGreen   = Color(0xFF4DB86E)
    val QuestMuted   = Color(0xFF7A6E5F)
    val QuestBg      = Color(0xFF13101A)
    val QuestCard    = Color(0xFF1A1320)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(QuestBg)
            .border(1.dp, QuestGold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Hlavička
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋  ${LocalStrings.current.questsTitle}", color = QuestGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(LocalStrings.current.questsReset, color = QuestMuted, fontSize = 8.sp)
        }

        // Quest karty – claimnuté se skryjí
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
                questMuted = QuestMuted,
                questCard  = QuestCard
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
    questMuted: Color,
    questCard : Color
) {
    val progress = quest.progress.toFloat() / quest.target.toFloat()
    val accent = when {
        quest.claimed   -> questMuted
        quest.completed -> questGreen
        else            -> questGold
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(questCard)
            .border(1.dp, accent.copy(alpha = if (quest.completed) 0.6f else 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Řádek: ikona + popis + reroll
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(quest.icon(), fontSize = 13.sp)
                Text(
                    quest.label(),
                    color = if (quest.claimed) questMuted else PrText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (canReroll) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(questMuted.copy(alpha = 0.12f))
                        .border(1.dp, questMuted.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                        .clickable { SoundManager.playMenuTap(); onReroll() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("🔄", fontSize = 10.sp)
                }
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress   = { progress.coerceIn(0f, 1f) },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color      = accent,
            trackColor = questMuted.copy(alpha = 0.15f),
            strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Spodní řádek: progress čísla + odměny + claim tlačítko
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${quest.progress} / ${quest.target}",
                color = questMuted, fontSize = 8.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Odměny
                if (quest.rewardXp   > 0) Text("⭐ ${quest.rewardXp} XP", color = Color(0xFF7EE8A2), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                if (quest.rewardGold > 0) Text("🪙 ${quest.rewardGold}",  color = questGold,         fontSize = 8.sp, fontWeight = FontWeight.Bold)
                if (quest.rewardGems > 0) Text("💎 ${quest.rewardGems}",  color = Color(0xFF6EE0F0), fontSize = 8.sp, fontWeight = FontWeight.Bold)

                // Claim tlačítko
                if (quest.canClaim) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(questGreen.copy(alpha = 0.15f))
                            .border(1.dp, questGreen.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🔒", fontSize = 13.sp)
        Text(description, color = PrMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}
