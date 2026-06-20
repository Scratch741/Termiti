package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ClGold  = Color(0xFFD4A843)
private val ClTeal  = Color(0xFF3DBFAD)
private val ClText  = Color(0xFFEDE0C4)
private val ClMuted = Color(0xFF7A6E5F)
private val ClGreen = Color(0xFF4CAF50)

private const val ART_RATIO = 0.643f

@Composable
fun CampaignLocationScreen(
    location: CampaignLocation,
    onOpponentSelected: (CampaignOpponent) -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Texturované pozadí
        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBF09070D))
        )

        PlainButton(
            text      = "← Zpět",
            modifier  = Modifier.padding(16.dp).align(Alignment.TopStart),
            textColor = ClMuted,
            fontSize  = 12.sp,
            paddingH  = 14.dp,
            paddingV  = 8.dp,
            onClick   = onBack
        )

        Row(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Levý panel – info o lokaci (s card_frame lokace)
            val locCardH = 220.dp
            val locArtH  = (locCardH.value * ART_RATIO).dp

            Box(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth()
                    .align(Alignment.CenterVertically)
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(locCardH)
                        .align(Alignment.Center)
                ) {
                    // Art pozadí lokace
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(locArtH)
                            .align(Alignment.TopStart)
                            .background(Color(0xFF0D0A14))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(locArtH)
                            .align(Alignment.TopStart)
                            .clipToBounds()
                    ) {
                        Image(
                            painter            = painterResource(locationArtRes(location.id)),
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                    }
                    // Card frame
                    Image(
                        painter            = painterResource(locationFrameRes(location.id)),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.FillBounds
                    )
                    // Rarity overlay
                    Image(
                        painter            = painterResource(locationRarityRes(location.id)),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.FillBounds
                    )
                    // Text
                    Column(
                        modifier                = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalAlignment     = Alignment.CenterHorizontally,
                        verticalArrangement     = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(Modifier.height(locArtH + 4.dp))

                        Text(
                            location.name.uppercase(),
                            color        = ClGold,
                            fontSize     = 11.sp,
                            fontWeight   = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign    = TextAlign.Center
                        )
                        Text(
                            location.description,
                            color      = ClMuted,
                            fontSize   = 8.sp,
                            textAlign  = TextAlign.Center,
                            lineHeight = 11.sp,
                            maxLines   = 3
                        )
                        Spacer(Modifier.weight(1f))
                        val defeated = location.opponents.count { CampaignManager.isDefeated(it.id) }
                        val total    = location.opponents.size
                        Text(
                            "$defeated / $total poraženo",
                            color      = if (defeated == total) ClGreen else ClTeal,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Pravý panel – soupeři jako karty
            LazyRow(
                modifier              = Modifier.weight(3f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding        = PaddingValues(horizontal = 4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items(location.opponents) { opponent ->
                    val unlocked   = CampaignManager.isUnlocked(location, opponent)
                    val isDefeated = CampaignManager.isDefeated(opponent.id)
                    OpponentCard(
                        opponent   = opponent,
                        locationId = location.id,
                        unlocked   = unlocked,
                        defeated   = isDefeated,
                        onClick    = {
                            SoundManager.playMenuTap()
                            onOpponentSelected(opponent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpponentCard(
    opponent  : CampaignOpponent,
    locationId: String,
    unlocked  : Boolean,
    defeated  : Boolean,
    onClick   : () -> Unit
) {
    val cardH = 220.dp
    val artH  = (cardH.value * ART_RATIO).dp   // ~141 dp

    Box(
        modifier = Modifier
            .width(148.dp)
            .height(cardH)
            .alpha(if (unlocked) 1f else 0.4f)
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier)
    ) {
        // ── Vrstva 0: tmavé pozadí art okna ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artH)
                .align(Alignment.TopStart)
                .background(
                    if (opponent.isBoss) Color(0xFF1A0508) else Color(0xFF0D0A14)
                )
        )

        // ── Vrstva 1: avatar soupeře vycentrovaný v art okně ─────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artH)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            AvatarView(opponent.avatar, size = 60.dp)
        }

        // ── Vrstva 2: card frame ──────────────────────────────────────────────
        Image(
            painter            = painterResource(locationFrameRes(locationId)),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )

        // ── Vrstva 2.5: rarity overlay (legendary = boss, epic = ostatní) ────
        Image(
            painter            = painterResource(
                if (opponent.isBoss) R.drawable.rarity_legendary else R.drawable.rarity_epic
            ),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )

        // ── Vrstva 3: textový obsah ───────────────────────────────────────────
        Column(
            modifier                = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(3.dp)
        ) {
            Spacer(Modifier.height(artH + 4.dp))

            // Boss badge nebo prázdný řádek pro zarovnání
            if (opponent.isBoss) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Image(
                        painterResource(R.drawable.rarity_legendary),
                        contentDescription = null,
                        modifier           = Modifier.height(10.dp).width(24.dp)
                    )
                    Text(
                        "BOSS",
                        color        = ClGold,
                        fontSize     = 8.sp,
                        fontWeight   = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Text(
                opponent.name,
                color      = ClText,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines   = 2
            )
            Text(
                opponent.title,
                color      = ClMuted,
                fontSize   = 8.sp,
                textAlign  = TextAlign.Center,
                fontStyle  = FontStyle.Italic
            )

            Spacer(Modifier.weight(1f))

            // Odměna
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Image(painterResource(R.drawable.goldcoin_icon), null, Modifier.size(11.dp))
                    Text("${opponent.rewardGold}", color = ClGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (opponent.rewardGems > 0) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Image(painterResource(R.drawable.diamond_icon), null, Modifier.size(11.dp))
                        Text("${opponent.rewardGems}", color = Color(0xFF7EC8E3), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painterResource(when {
                        defeated  -> R.drawable.check_icon
                        !unlocked -> R.drawable.lock_icon
                        else      -> R.drawable.utok_icon
                    }),
                    contentDescription = null,
                    modifier           = Modifier.size(11.dp)
                )
                Text(
                    when {
                        defeated  -> "Poražen"
                        !unlocked -> "Zamčen"
                        else      -> "Bojuj!"
                    },
                    color      = when {
                        defeated  -> ClGreen
                        !unlocked -> ClMuted
                        else      -> ClTeal
                    },
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

// Avatar: pokud je string jméno resources, zobrazí Image; jinak emoji Text
@Composable
private fun AvatarView(avatar: String, size: Dp) {
    val resId: Int? = when (avatar) {
        "enemy_icon_1" -> R.drawable.enemy_icon_1
        "enemy_icon_2" -> R.drawable.enemy_icon_2
        "enemy_icon_3" -> R.drawable.enemy_icon_3
        "hammer_icon"  -> R.drawable.hammer_icon
        else           -> null
    }
    if (resId != null) {
        Image(
            painterResource(resId),
            contentDescription = null,
            modifier           = Modifier.size(size)
        )
    } else {
        Text(
            text      = avatar,
            fontSize  = (size.value * 0.65f).sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .size(size)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}

// ── Pomocné funkce ────────────────────────────────────────────────────────────

private fun locationArtRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.art_goblin
    "loc_dwarves" -> R.drawable.art_opevneni
    "loc_citadel" -> R.drawable.art_temny_ritual
    "loc_dragon"  -> R.drawable.art_chaoticky_drak
    else          -> R.drawable.art_magie
}

private fun locationRarityRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.rarity_common
    "loc_dwarves" -> R.drawable.rarity_rare
    "loc_citadel" -> R.drawable.rarity_epic
    "loc_dragon"  -> R.drawable.rarity_legendary
    else          -> R.drawable.rarity_common
}
