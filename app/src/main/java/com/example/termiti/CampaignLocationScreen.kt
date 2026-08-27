package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ClGold  = Color(0xFFD4A843)
private val ClTeal  = Color(0xFF3DBFAD)
private val ClText  = Color(0xFFEDE0C4)
private val ClMuted = Color(0xFF7A6E5F)
private val ClGreen = Color(0xFF4CAF50)
private val ClXp    = Color(0xFF7EE8A2)   // stejná zelená jako XP badge na obrazovce výsledku
private val ClGem   = Color(0xFF7EC8E3)

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
                    // Jméno – zakřivený text na stejném místě jako u karet.
                    // 220 dp / 12 sp → baseline 135,0 dp = offset 110 dp + 0,78 × 32 dp
                    // (odvození vzorce viz CampaignMapScreen.LocationCard).
                    ArcCardName(
                        name         = location.displayName,
                        modifier     = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = 110.dp)
                            .fillMaxWidth()
                            .height(32.dp),
                        fontSizeSp   = 12f,
                        arcRadiusDp  = 520f,
                        baselineFrac = 0.78f
                    )

                    // Popisek – pevné, ořezané místo jako popis karty
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = 140.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .clipToBounds()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            location.displayDescription,
                            color      = ClMuted,
                            fontSize   = 8.sp,
                            textAlign  = TextAlign.Center,
                            lineHeight = 11.sp,
                            maxLines   = 4,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }

                    // Status – stejné místo jako typ karty
                    run {
                        val defeated = location.opponents.count { CampaignManager.isDefeated(it.id) }
                        val total    = location.opponents.size
                        Text(
                            "$defeated / $total poraženo",
                            color      = if (defeated == total) ClGreen else ClTeal,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = 198.dp)
                                .fillMaxWidth()
                        )
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
                itemsIndexed(location.opponents) { index, opponent ->
                    val unlocked   = CampaignManager.isUnlocked(location, opponent)
                    val isDefeated = CampaignManager.isDefeated(opponent.id)
                    OpponentCard(
                        opponent   = opponent,
                        locationId = location.id,
                        order      = index + 1,
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
    order     : Int,
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

        // ── Vrstva 2.6: pořadí soupeře vlevo nahoře – stejné místo/styl jako cena karty.
        //    Boss (poslední v lokaci) má číslo červené, stejně jako zdražení karty. ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.2.dp, y = 3.1.dp)
                .size(27.dp),
            contentAlignment = Alignment.Center
        ) {
            val orderLabel = "$order"
            val fillColor  = if (opponent.isBoss) Color(0xFFFF5252) else Color.White
            val orderStyle = TextStyle(
                fontSize      = 13.sp,
                fontWeight    = FontWeight.ExtraBold,
                textAlign     = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim      = LineHeightStyle.Trim.Both
                )
            )
            // Černý obrys – 4 posunuté kopie (stejná technika jako u ceny karty)
            Text(orderLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(x = (-1).dp), style = orderStyle)
            Text(orderLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(x = 1.dp),  style = orderStyle)
            Text(orderLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(y = (-1).dp), style = orderStyle)
            Text(orderLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(y = 1.dp),  style = orderStyle)
            // Výplň (bílá běžně, červená u bosse)
            Text(orderLabel, color = fillColor, modifier = Modifier.fillMaxWidth(), style = orderStyle)
        }

        // ── Vrstva 2.7: BOSS štítek vpravo nahoře (nezabírá místo v textu) ────
        if (opponent.isBoss) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Image(
                    painterResource(R.drawable.rarity_legendary),
                    contentDescription = null,
                    modifier           = Modifier.height(9.dp).width(20.dp)
                )
                Text("BOSS", color = ClGold, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // ── Vrstva 3: jméno – zakřivený text na stejném místě jako u karet ────
        // Stejné umístění jako u panelu lokace: 220 dp / 12 sp → baseline 135,0 dp.
        ArcCardName(
            name         = opponent.displayName,
            modifier     = Modifier
                .align(Alignment.TopStart)
                .offset(y = 110.dp)
                .fillMaxWidth()
                .height(32.dp),
            fontSizeSp   = 12f,
            arcRadiusDp  = 520f,
            baselineFrac = 0.78f
        )

        // ── Vrstva 4: text karty – popisek soupeře + odměna, stejné místo jako popis karty ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 140.dp)
                .fillMaxWidth()
                .height(54.dp)
                .clipToBounds()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    opponent.displayDescription,
                    color      = Color(0xFFDDD0B0),
                    fontSize   = 7.5.sp,
                    textAlign  = TextAlign.Center,
                    lineHeight = 9.5.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                // Odměny za první poražení – stejné pořadí i barvy jako na
                // obrazovce výsledku ([CampaignResultScreen] RewardBadge): XP, zlato, gemy.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    RewardChip(R.drawable.star_icon,     "${opponent.rewardXp}",   ClXp)
                    RewardChip(R.drawable.goldcoin_icon, "${opponent.rewardGold}", ClGold)
                    if (opponent.rewardGems > 0) {
                        RewardChip(R.drawable.diamond_icon, "${opponent.rewardGems}", ClGem)
                    }
                }
            }
        }

        // ── Vrstva 5: status – stejné místo jako typ karty ─────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 198.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
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
            Spacer(Modifier.width(4.dp))
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
    }
}

/** Ikona + hodnota jedné odměny v textovém pruhu karty soupeře. */
@Composable
private fun RewardChip(iconRes: Int, value: String, color: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Image(painterResource(iconRes), null, Modifier.size(10.dp))
        Text(value, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
