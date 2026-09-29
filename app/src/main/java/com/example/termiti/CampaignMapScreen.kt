package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
        // bg_campaign.png má vykreslený kamenný rám po obvodu, takže se nesmí
        // ořezávat (Crop) – rám by z části vypadl mimo obrazovku. FillBounds ho
        // udrží přilepený k okrajům; mírné roztažení malby není poznat.
        Image(
            painter      = painterResource(R.drawable.bg_campaign),
            contentDescription = null,
            modifier     = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        // Tmavý overlay pro čitelnost – bg_campaign.png je samo o sobě už tmavé,
        // silný overlay (dřív 0xBF) by ho prakticky celé překryl. Jen jemné dolazení.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4009070D))
        )

        // Obsah drží UVNITŘ vykresleného kamenného rámu. Pozadí je FillBounds, takže
        // rám roste s obrazovkou – odsazení je proto v procentech (změřeno na
        // bg_campaign.png: boky ~4,5 % šířky, nahoře ~6 %, dole ~7,5 % výšky) + rezerva.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val insetX      = campaignInsetX(maxWidth)
            val insetTop    = campaignInsetTop(maxHeight)
            val insetBottom = campaignInsetBottom(maxHeight)

            // Karty: všechny lokace vedle sebe. Zmenší se, aby se vešly na šířku i na výšku
            // (výška bez nadpisu ~ 60 dp); min. 72 % – menší by nešel přečíst popisek.
            val count  = CampaignData.locations.size
            val gap    = 14.dp
            val innerW = maxWidth - insetX * 2
            val innerH = maxHeight - insetTop - insetBottom
            val fitW   = (innerW - gap * (count - 1) - LOCATION_GLOW * 2) / (LOCATION_CARD_W * count)
            val fitH   = (innerH - 60.dp) / LOCATION_CARD_H
            val scale  = minOf(fitW, fitH, 1.1f).coerceAtLeast(0.72f)

            // Nadpis a karty: SpaceEvenly rozdělí volné místo na stejné mezery nad nadpisem,
            // mezi nadpisem a kartami a pod kartami → nadpis sedí mezi rámem a lokacemi.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = insetX, end = insetX, top = insetTop, bottom = insetBottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CampaignTitle(LocalStrings.current.campaign)
                    Text(
                        LocalStrings.current.campaignPickHint,
                        color         = CmMuted,
                        fontSize      = 11.sp,
                        letterSpacing = 1.sp,
                        textAlign     = TextAlign.Center,
                        maxLines      = 1,
                        modifier      = Modifier.padding(start = 1.dp)
                    )
                }

                Row(
                    // padding UVNITŘ posuvné řady: horizontalScroll ořezává na šířku řady,
                    // záře krajních karet by jinak byla useknutá
                    modifier              = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = LOCATION_GLOW),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    CampaignData.locations.forEachIndexed { index, location ->
                        LocationCard(location, order = index + 1, scale = scale) {
                            if (CampaignManager.isLocationUnlocked(location)) {
                                SoundManager.playMenuTap()
                                onLocationSelected(location)
                            }
                        }
                    }
                }
            }

            CampaignBackButton(insetX, insetTop, onBack)
        }
    }
}

/** Zlatý přechod nadpisů kampaně (světlá horní hrana → bronzový spodek), jako logo DARKMAGE. */
internal val TitleGold = listOf(Color(0xFFFFF1C1), Color(0xFFE8C467), Color(0xFFB07A2A), Color(0xFF7A4E1C))
/** Krvavě červený přechod (nadpis prohry). */
internal val TitleBlood = listOf(Color(0xFFFFC4B8), Color(0xFFE5574A), Color(0xFFA3261E), Color(0xFF5E1210))
/** Stříbrný přechod (remíza / neutrální výsledek). */
internal val TitleSilver = listOf(Color(0xFFF4F4F4), Color(0xFFC9CED6), Color(0xFF8E96A3), Color(0xFF596170))

/**
 * Cinzel (Google Fonts, SIL OFL 1.1 – licence v assets/licenses). Soubor je variabilní font
 * vložený beze změny; tloušťka Bold se volí přes variationSettings (API 26+ = minSdk).
 */
@OptIn(ExperimentalTextApi::class)
private val CinzelBold = FontFamily(
    Font(
        R.font.cinzel,
        weight            = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

/**
 * Nadpis kampaně ve stylu loga DARKMAGE z hlavního menu: římské verzálky (Cinzel),
 * zlatý přechod (světlá horní hrana → bronzový spodek) a tmavý stín.
 */
@Composable
internal fun CampaignTitle(
    text: String,
    fontSize: TextUnit = 30.sp,
    gradient: List<Color> = TitleGold
) {
    // letterSpacing přidává mezeru i ZA poslední písmeno → padding(start) o stejnou
    // hodnotu vrací glyfy přesně na osu.
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 3.dp),
        style = TextStyle(
            fontFamily    = CinzelBold,
            fontWeight    = FontWeight.Bold,
            fontSize      = fontSize,
            letterSpacing = 3.sp,
            brush         = Brush.verticalGradient(gradient),
            shadow        = Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 3f), blurRadius = 8f)
        )
    )
}

// Odsazení obsahu od kamenného rámu v bg_campaign.png (sdílí výběr lokace i detail lokace).
// Pozadí je FillBounds → rám roste s obrazovkou, proto procenta (změřeno: boky ~4,5 % šířky,
// nahoře ~6 %, dole ~7,5 % výšky) + malá rezerva.
internal fun campaignInsetX(width: Dp): Dp       = width  * 0.045f + 10.dp
internal fun campaignInsetTop(height: Dp): Dp    = height * 0.06f  + 8.dp
internal fun campaignInsetBottom(height: Dp): Dp = height * 0.075f + 8.dp

/** Tlačítko Zpět v levém horním rohu uvnitř rámu – stejné místo na obou obrazovkách kampaně. */
@Composable
internal fun BoxScope.CampaignBackButton(insetX: Dp, insetTop: Dp, onBack: () -> Unit) {
    PlainButton(
        text      = LocalStrings.current.backShort,
        modifier  = Modifier
            .align(Alignment.TopStart)
            .padding(start = insetX + 8.dp, top = insetTop + 16.dp),
        textColor = CmMuted,
        fontSize  = 12.sp,
        paddingH  = 14.dp,
        paddingV  = 8.dp,
        onClick   = onBack
    )
}

// Návrhová velikost karty lokace – všechny pozice uvnitř karty jsou pro ni spočítané
// (viz komentáře u vrstev); na obrazovce se karta jen celá zmenší/zvětší přes [scale].
internal val LOCATION_CARD_W = 160.dp
internal val LOCATION_CARD_H = 240.dp
/** Zaoblení rohů karty lokace (v návrhové velikosti); vnitřní okraj záře navazuje. */
internal val LOCATION_CORNER = 14.dp
/** Jak daleko za okraj karty sahá záře stavu lokace. */
internal val LOCATION_GLOW   = 12.dp

internal val GlowCleared    = Color(0xFF4CAF50)   // hotovo / poražen
internal val GlowInProgress = Color(0xFFFFC107)   // rozehráno / na řadě
internal val GlowLocked     = Color(0xFFE53935)   // zatím nedosažitelné

@Composable
private fun LocationCard(location: CampaignLocation, order: Int, scale: Float, onClick: () -> Unit) {
    val unlocked      = CampaignManager.isLocationUnlocked(location)
    val cleared       = CampaignManager.isLocationCleared(location)
    val defeatedCount = location.opponents.count { CampaignManager.isDefeated(it.id) }

    val cardH = LOCATION_CARD_H
    val artH  = (cardH.value * ART_RATIO).dp   // ~154 dp

    // Vnější box zabírá zmenšenou velikost (layout i klik), vnitřní se kreslí v návrhové
    // velikosti a graphicsLayer ho zmenší – rozvržení karty tak zůstane beze změny.
    val glowColor = when {
        cleared   -> GlowCleared
        unlocked  -> GlowInProgress
        else      -> GlowLocked
    }
    Box(
        modifier = Modifier
            .size(LOCATION_CARD_W * scale, LOCATION_CARD_H * scale)
            .drawBehind { drawStatusGlow(glowColor, LOCATION_GLOW.toPx(), LOCATION_CORNER.toPx() * scale) }
            .alpha(if (unlocked) 1f else 0.42f)
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
    Box(
        modifier = Modifier
            .requiredSize(LOCATION_CARD_W, LOCATION_CARD_H)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                // oblé rohy karty – rám card_frame_* má rohy ostré
                shape = RoundedCornerShape(LOCATION_CORNER); clip = true
            }
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

        // ── Vrstva 2.6: pořadí kampaně vlevo nahoře – stejné místo/styl jako cena karty ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.4.dp, y = 3.2.dp)
                .size(29.dp),
            contentAlignment = Alignment.Center
        ) {
            val orderLabel = "$order"
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
            // Bílá výplň
            Text(orderLabel, color = Color.White, modifier = Modifier.fillMaxWidth(), style = orderStyle)
        }

        // ── Vrstva 3: jméno – zakřivený text na stejném místě jako u karet ────
        // Střed jmenného pruhu v card_frame_*.png je na 59,4 % výšky karty (pruh
        // 652–768 px z 1195 px). Text je opticky uprostřed, když platí
        //     baseline = 0,594 × výškaKarty + 0,355 × fontSize      (0,355 ≈ půl verzálky)
        // 240 dp / 13 sp → 147,4 dp = offset 120 dp + baselineFrac 0,78 × 35 dp.
        ArcCardName(
            name         = location.displayName,
            modifier     = Modifier
                .align(Alignment.TopStart)
                .offset(y = 120.dp)
                .fillMaxWidth()
                .height(35.dp),
            fontSizeSp   = 13f,
            arcRadiusDp  = 560f,
            baselineFrac = 0.78f
        )

        // ── Vrstva 4: text karty – popisek lokace, stejný styl jako popisek
        // soupeře v CampaignLocationScreen.kt (velikost/barva/umístění sjednoceny) ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 167.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clipToBounds()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                location.displayDescription,
                color      = if (unlocked) Color(0xFFDDD0B0) else CmMuted,
                fontSize   = 8.5.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 10.5.sp,
                maxLines   = 3,
                overflow   = TextOverflow.Ellipsis
            )
        }

        // ── Vrstva 5: progress bar + status – stejné místo jako typ karty ──────
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 210.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LocationProgressBar(defeatedCount, location.opponents.size, cleared)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
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
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        cleared  -> LocalStrings.current.campaignCleared
                        unlocked -> "$defeatedCount / ${location.opponents.size}"
                        else     -> LocalStrings.current.campaignLocked
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
        }
    }
    }
}

/**
 * Statická záře kolem karty: soustředné zaoblené rámečky, které směrem ven slábnou.
 * Bez BlurMaskFilter – ten na hardwarovém plátně funguje až od Androidu 9 (minSdk 26).
 */
internal fun DrawScope.drawStatusGlow(color: Color, spread: Float, corner: Float) {
    val steps = 12
    for (i in 0 until steps) {
        val t     = i / (steps - 1f)                 // 0 = u karty, 1 = nejdál
        val grow  = spread * t
        val alpha = 0.55f * (1f - t) * (1f - t)
        drawRoundRect(
            color        = color.copy(alpha = alpha),
            topLeft      = Offset(-grow, -grow),
            size         = Size(size.width + grow * 2, size.height + grow * 2),
            // zaoblení roste rychleji než záře → směrem ven čím dál oblejší obrys
            cornerRadius = CornerRadius(corner + grow * 1.6f),
            style        = Stroke(width = spread / steps * 1.6f)
        )
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
    "loc_swamp"   -> R.drawable.card_frame_magic
    "loc_dwarves" -> R.drawable.card_frame_stones
    "loc_citadel" -> R.drawable.card_frame_magic
    "loc_dragon"  -> R.drawable.card_frame_chaos
    else          -> R.drawable.card_frame_magic
}

@DrawableRes
private fun locationArtRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.goblin_tabor
    "loc_swamp"   -> R.drawable.magicke_baziny
    "loc_dwarves" -> R.drawable.trpaslici_hory
    "loc_citadel" -> R.drawable.art_temny_ritual
    "loc_dragon"  -> R.drawable.art_chaoticky_drak
    else          -> R.drawable.art_magie
}

@DrawableRes
private fun locationRarityRes(id: String): Int = when (id) {
    "loc_goblins" -> R.drawable.rarity_common
    "loc_swamp"   -> R.drawable.rarity_rare
    "loc_dwarves" -> R.drawable.rarity_rare
    "loc_citadel" -> R.drawable.rarity_epic
    "loc_dragon"  -> R.drawable.rarity_legendary
    else          -> R.drawable.rarity_common
}
