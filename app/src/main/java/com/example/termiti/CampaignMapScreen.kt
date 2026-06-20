package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CmGold  = Color(0xFFD4A843)
private val CmTeal  = Color(0xFF3DBFAD)
private val CmText  = Color(0xFFEDE0C4)
private val CmMuted = Color(0xFF7A6E5F)
private val CmGreen = Color(0xFF4CAF50)

// Výška art okna card_frame: 64.3 % výšky karty (změřeno z GameCardView: 90dp / 140dp)
private const val ART_RATIO = 0.643f

@Composable
fun CampaignMapScreen(
    onLocationSelected: (CampaignLocation) -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Texturované pozadí z herního světa
        Image(
            painter      = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier     = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Tmavý overlay pro čitelnost
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBF09070D))
        )

        PlainButton(
            text      = "← Zpět",
            modifier  = Modifier.padding(16.dp).align(Alignment.TopStart),
            textColor = CmMuted,
            fontSize  = 12.sp,
            paddingH  = 14.dp,
            paddingV  = 8.dp,
            onClick   = onBack
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment         = Alignment.CenterVertically,
            horizontalArrangement     = Arrangement.spacedBy(32.dp)
        ) {
            // Levý panel – název kampaně
            Column(
                modifier                = Modifier.weight(1f),
                horizontalAlignment     = Alignment.CenterHorizontally,
                verticalArrangement     = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter            = painterResource(R.drawable.trophy_icon),
                    contentDescription = null,
                    modifier           = Modifier.size(52.dp)
                )
                Text(
                    "KAMPAŇ",
                    color        = CmGold,
                    fontSize     = 26.sp,
                    fontWeight   = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    "Vyber lokaci a poraž\nvšechny soupeře",
                    color       = CmMuted,
                    fontSize    = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign   = TextAlign.Center,
                    lineHeight  = 18.sp
                )
            }

            // Pravý panel – lokace jako karty
            LazyRow(
                modifier              = Modifier.weight(2f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding        = PaddingValues(horizontal = 4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items(CampaignData.locations) { location ->
                    LocationCard(location) {
                        if (CampaignManager.isLocationUnlocked(location)) {
                            SoundManager.playMenuTap()
                            onLocationSelected(location)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(location: CampaignLocation, onClick: () -> Unit) {
    val unlocked      = CampaignManager.isLocationUnlocked(location)
    val cleared       = CampaignManager.isLocationCleared(location)
    val defeatedCount = location.opponents.count { CampaignManager.isDefeated(it.id) }

    val cardH = 240.dp
    val artH  = (cardH.value * ART_RATIO).dp   // ~154 dp

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(cardH)
            .alpha(if (unlocked) 1f else 0.42f)
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier)
    ) {
        // ── Vrstva 0: tmavé pozadí art okna ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artH)
                .align(Alignment.TopStart)
                .background(Color(0xFF0D0A14))
        )

        // ── Vrstva 1: ilustrace lokace ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artH)
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

        // ── Vrstva 2: card frame (průhledné art okno odkryje ilustraci) ───────
        Image(
            painter            = painterResource(locationFrameRes(location.id)),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )

        // ── Vrstva 2.5: rarity overlay – tónuje barvu rámu ───────────────────
        Image(
            painter            = painterResource(locationRarityRes(location.id)),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )

        // ── Vrstva 3: textový obsah ───────────────────────────────────────────
        Column(
            modifier                = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(4.dp)
        ) {
            // Skok přes art okno
            Spacer(Modifier.height(artH + 4.dp))

            Text(
                location.name,
                color      = if (unlocked) CmText else CmMuted,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 15.sp,
                maxLines   = 2
            )
            Text(
                location.description,
                color      = CmMuted,
                fontSize   = 8.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 11.sp,
                maxLines   = 2
            )

            Spacer(Modifier.weight(1f))

            // Progress bar
            LocationProgressBar(defeatedCount, location.opponents.size, cleared)

            // Status
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painterResource(when {
                        cleared   -> R.drawable.check_icon
                        !unlocked -> R.drawable.lock_icon
                        else      -> R.drawable.castle_icon
                    }),
                    contentDescription = null,
                    modifier           = Modifier.size(11.dp)
                )
                Text(
                    when {
                        cleared  -> "Vyčištěno"
                        unlocked -> "$defeatedCount / ${location.opponents.size}"
                        else     -> "Zamčeno"
                    },
                    color      = when {
                        cleared  -> CmGreen
                        unlocked -> CmTeal
                        else     -> CmMuted
                    },
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LocationProgressBar(current: Int, total: Int, cleared: Boolean) {
    val fraction = if (total > 0) current.toFloat() / total else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(CmMuted.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(if (cleared) CmGreen else CmTeal)
        )
    }
}

// ── Mapování lokace → textury ─────────────────────────────────────────────────

@DrawableRes
internal fun locationFrameRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.card_frame_attack
    "loc_dwarves" -> R.drawable.card_frame_stones
    "loc_citadel" -> R.drawable.card_frame_magic
    "loc_dragon"  -> R.drawable.card_frame_chaos
    else          -> R.drawable.card_frame_magic
}

@DrawableRes
private fun locationArtRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.art_goblin
    "loc_dwarves" -> R.drawable.art_opevneni
    "loc_citadel" -> R.drawable.art_temny_ritual
    "loc_dragon"  -> R.drawable.art_chaoticky_drak
    else          -> R.drawable.art_magie
}

@DrawableRes
private fun locationRarityRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.rarity_common
    "loc_dwarves" -> R.drawable.rarity_rare
    "loc_citadel" -> R.drawable.rarity_epic
    "loc_dragon"  -> R.drawable.rarity_legendary
    else          -> R.drawable.rarity_common
}
