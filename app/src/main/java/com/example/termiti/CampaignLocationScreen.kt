package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ClGold  = Color(0xFFD4A843)
private val ClTeal  = Color(0xFF3DBFAD)
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
        // bg_campaign.png má vykreslený kamenný rám po obvodu, takže se nesmí
        // ořezávat (Crop) – rám by z části vypadl mimo obrazovku. FillBounds ho
        // udrží přilepený k okrajům; mírné roztažení malby není poznat.
        Image(
            painter            = painterResource(R.drawable.bg_campaign),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )
        // Tmavý overlay pro čitelnost – bg_campaign.png je samo o sobě už tmavé,
        // silný overlay (dřív 0xBF) by ho prakticky celé překryl. Jen jemné dolazení.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4009070D))
        )

        // Stejné rozložení jako výběr lokace (CampaignMapScreen): obsah uvnitř rámu,
        // nahoře název lokace, pod ním řada karet soupeřů.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val insetX      = campaignInsetX(maxWidth)
            val insetTop    = campaignInsetTop(maxHeight)
            val insetBottom = campaignInsetBottom(maxHeight)

            // Karty soupeřů stejně velké jako karty lokací na předchozí obrazovce:
            // měřítko pro 5 karet vedle sebe, převedené na návrhovou výšku karty soupeře.
            val gap      = 14.dp
            val innerW   = maxWidth - insetX * 2
            val innerH   = maxHeight - insetTop - insetBottom
            val fitW     = (innerW - gap * 4 - LOCATION_GLOW * 2) / (LOCATION_CARD_W * 5)
            val fitH     = (innerH - 60.dp) / LOCATION_CARD_H
            val mapScale = minOf(fitW, fitH, 1.1f).coerceAtLeast(0.72f)
            val scale    = mapScale * (LOCATION_CARD_H / OPP_CARD_H)

            val defeated = location.opponents.count { CampaignManager.isDefeated(it.id) }
            val total    = location.opponents.size

            // Posun řady na prvního neporaženého soupeře (a jednoho před ním),
            // ať po návratu z bitvy není nutné hledat, kde hráč skončil.
            val scrollState = rememberScrollState()
            val density     = LocalDensity.current
            val firstOpen   = location.opponents.indexOfFirst { !CampaignManager.isDefeated(it.id) }
            LaunchedEffect(location.id) {
                if (firstOpen > 0) {
                    val step = with(density) { (OPP_CARD_W * scale + gap).toPx() }
                    scrollState.scrollTo(((firstOpen - 1) * step).toInt())
                }
            }

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
                    CampaignTitle(location.displayName)
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            location.displayDescription,
                            color         = ClMuted,
                            fontSize      = 11.sp,
                            letterSpacing = 0.5.sp,
                            maxLines      = 1,
                            overflow      = TextOverflow.Ellipsis,
                            modifier      = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            "$defeated / $total",
                            color      = if (defeated == total) ClGreen else ClTeal,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    // padding UVNITŘ posuvné řady: horizontalScroll ořezává na šířku řady,
                    // záře krajních karet by jinak byla useknutá
                    modifier              = Modifier
                        .horizontalScroll(scrollState)
                        .padding(horizontal = LOCATION_GLOW),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    location.opponents.forEachIndexed { index, opponent ->
                        OpponentCard(
                            opponent   = opponent,
                            locationId = location.id,
                            order      = index + 1,
                            total      = total,
                            unlocked   = CampaignManager.isUnlocked(location, opponent),
                            defeated   = CampaignManager.isDefeated(opponent.id),
                            scale      = scale,
                            onClick    = {
                                SoundManager.playMenuTap()
                                onOpponentSelected(opponent)
                            }
                        )
                    }
                }
            }

            CampaignBackButton(insetX, insetTop, onBack)
        }
    }
}

// Návrhová velikost karty soupeře – pozice uvnitř karty jsou spočítané pro ni;
// na obrazovce se karta celá zmenší/zvětší přes scale (stejně jako karta lokace).
// Šířka z poměru karty lokace (160 : 240) – dřív pevných 148 dp (poměr 0,673 místo 0,667),
// takže při stejné výšce na obrazovce byl soupeř o ~1 dp širší než lokace.
private val OPP_CARD_H = 220.dp
private val OPP_CARD_W = OPP_CARD_H * (LOCATION_CARD_W / LOCATION_CARD_H)

@Composable
private fun OpponentCard(
    opponent  : CampaignOpponent,
    locationId: String,
    order     : Int,
    total     : Int,
    unlocked  : Boolean,
    defeated  : Boolean,
    scale     : Float,
    onClick   : () -> Unit
) {
    val cardH = OPP_CARD_H
    val artH  = (cardH.value * ART_RATIO).dp   // ~141 dp

    // Záře podle stavu: poražen / na řadě / zamčený. Kreslí se PŘED alpha,
    // aby ji ztmavení zamčené karty neztlumilo.
    val glowColor = when {
        defeated -> GlowCleared
        unlocked -> GlowInProgress
        else     -> GlowLocked
    }
    // Poměr zaoblení stejný jako u karty lokace (14 dp na 240 dp výšky)
    val corner = LOCATION_CORNER * (OPP_CARD_H / LOCATION_CARD_H)

    Box(
        modifier = Modifier
            .size(OPP_CARD_W * scale, OPP_CARD_H * scale)
            .drawBehind { drawStatusGlow(glowColor, LOCATION_GLOW.toPx(), corner.toPx() * scale) }
            .alpha(if (unlocked) 1f else 0.4f)
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
    Box(
        modifier = Modifier
            .requiredSize(OPP_CARD_W, OPP_CARD_H)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                shape = RoundedCornerShape(corner); clip = true
            }
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

        // ── Vrstva 1: art soupeře přes celé art okno (jako u herních karet) ──
        OpponentCardArt(
            avatar   = opponent.cardArt ?: opponent.avatar,
            artH     = artH,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // ── Vrstva 2: card frame ──────────────────────────────────────────────
        Image(
            painter            = painterResource(locationFrameRes(locationId)),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )

        // ── Vrstva 2.5: rarity overlay – odstupňovaná podle pořadí, boss = legendary ──
        Image(
            painter            = painterResource(opponentRarityRes(order, total, opponent.isBoss)),
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
        // Mezera od jména: ArcCardName končí na y=142dp (offset 110 + výška 32),
        // text proto začíná až na 145dp, ne 140dp (dřív se s ním překrýval).
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 154.dp)
                .fillMaxWidth()
                .height(41.dp)
                .clipToBounds()
                // 13 dp (dřív 14): karta je o 1,3 dp užší (poměr stran jako karta lokace)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    opponent.displayDescription,
                    color      = Color(0xFFDDD0B0),
                    fontSize   = 8.5.sp,
                    textAlign  = TextAlign.Center,
                    lineHeight = 10.5.sp,
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
                .offset(y = 200.dp)
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
                    defeated  -> LocalStrings.current.campaignDefeated
                    !unlocked -> LocalStrings.current.campaignOppLocked
                    else      -> LocalStrings.current.campaignFight
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

/** Avatary s plnou ilustrací (celoplošný art, ne malá ikonka) – vykreslují se přes celé art okno karty. */
private val FULL_ART_AVATARS = setOf(
    "goblin_pruzkumnik", "goblin_lucistnik", "goblin_saman", "goblin_valecnik", "goblin_drancovac",
    "goblin_berserk", "goblin_troll", "goblin_velitel", "goblin_valecny_nacelnik", "goblin_kral",
    "hory_hornik", "hory_tesar", "hory_strazce", "hory_kovar", "hory_ranger",
    "hory_bojovnik", "hory_mag", "hory_general", "hory_kolos", "hory_thane",
    "bazina_zaba", "bazina_pijavice", "bazina_carodejka", "bazina_zaklinac", "bazina_alchymista",
    "bazina_jezibaba", "bazina_had", "bazina_bludicka", "bazina_druid", "bazina_pan_mlhy",
    "citadela_rytir", "citadela_lucistnik", "citadela_carodej", "citadela_nekromant", "citadela_valecnik",
    "citadela_strazce", "citadela_saboter", "citadela_drak", "citadela_general", "citadela_pan"
)

/** Mapuje avatar ID na drawable resource, nebo null pokud jde o emoji řetězec. */
internal fun avatarDrawableRes(avatar: String): Int? = when (avatar) {
    "enemy_icon_1" -> R.drawable.enemy_icon_1
    "enemy_icon_2" -> R.drawable.enemy_icon_2
    "enemy_icon_3" -> R.drawable.enemy_icon_3
    "hammer_icon"  -> R.drawable.hammer_icon
    "player_icon_10"          -> R.drawable.player_icon_10
    "goblin_pruzkumnik"       -> R.drawable.goblin_pruzkumnik
    "goblin_lucistnik"        -> R.drawable.goblin_lucistnik
    "goblin_saman"            -> R.drawable.goblin_saman
    "goblin_valecnik"         -> R.drawable.goblin_valecnik
    "goblin_drancovac"        -> R.drawable.goblin_drancovac
    "goblin_berserk"          -> R.drawable.goblin_berserk
    "goblin_troll"            -> R.drawable.goblin_troll
    "goblin_velitel"          -> R.drawable.goblin_velitel
    "goblin_valecny_nacelnik" -> R.drawable.goblin_valecny_nacelnik
    "goblin_kral_profil"      -> R.drawable.goblin_kral_profil
    "goblin_kral"             -> R.drawable.goblin_kral
    "hory_hornik"             -> R.drawable.hory_hornik
    "hory_tesar"              -> R.drawable.hory_tesar
    "hory_strazce"            -> R.drawable.hory_strazce
    "hory_kovar"              -> R.drawable.hory_kovar
    "hory_ranger"             -> R.drawable.hory_ranger
    "hory_bojovnik"           -> R.drawable.hory_bojovnik
    "hory_mag"                -> R.drawable.hory_mag
    "hory_general"            -> R.drawable.hory_general
    "hory_kolos"              -> R.drawable.hory_kolos
    "hory_thane"              -> R.drawable.hory_thane
    "bazina_zaba"              -> R.drawable.bazina_zaba
    "bazina_pijavice"          -> R.drawable.bazina_pijavice
    "bazina_carodejka"         -> R.drawable.bazina_carodejka
    "bazina_zaklinac"          -> R.drawable.bazina_zaklinac
    "bazina_alchymista"        -> R.drawable.bazina_alchymista
    "bazina_jezibaba"          -> R.drawable.bazina_jezibaba
    "bazina_had"               -> R.drawable.bazina_had
    "bazina_bludicka"          -> R.drawable.bazina_bludicka
    "bazina_druid"             -> R.drawable.bazina_druid
    "bazina_pan_mlhy"          -> R.drawable.bazina_pan_mlhy
    "citadela_rytir"           -> R.drawable.citadela_rytir
    "citadela_lucistnik"       -> R.drawable.citadela_lucistnik
    "citadela_carodej"         -> R.drawable.citadela_carodej
    "citadela_nekromant"       -> R.drawable.citadela_nekromant
    "citadela_valecnik"        -> R.drawable.citadela_valecnik
    "citadela_strazce"         -> R.drawable.citadela_strazce
    "citadela_saboter"         -> R.drawable.citadela_saboter
    "citadela_drak"            -> R.drawable.citadela_drak
    "citadela_general"         -> R.drawable.citadela_general
    "citadela_pan"             -> R.drawable.citadela_pan
    else           -> null
}

// Avatar: pokud je string jméno resources, zobrazí Image; jinak emoji Text
@Composable
private fun AvatarView(avatar: String, size: Dp) {
    val resId = avatarDrawableRes(avatar)
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

/**
 * Avatary, kde hlava/koruna sahá až k úplně hornímu okraji ilustrace – středový
 * Crop by jí useknul vršek. Tyhle se místo toho zarovnávají nahoru (ořez jde
 * jen zespodu), aby zůstala celá vidět.
 */
private val TOP_ALIGNED_AVATARS = setOf(
    "goblin_troll", "goblin_valecny_nacelnik", "goblin_kral"
)

/**
 * Svislé doladění artu v okně karty: kladná hodnota = posun DOLŮ, jako podíl výšky
 * art okna (0.10f = o 10 %). Pro avatary, kterým středový Crop sedí moc vysoko.
 *
 * Proč ne jen [Alignment]: ilustrace jsou čtvercové a okno je 148 × ~141 dp, takže
 * po Cropu přečnívají svisle jen o pár dp – zarovnáním se dá posunout sotva o 2 %.
 * Posun proto doprovází mírné přiblížení (viz [OpponentCardArt]), aby na opačné
 * straně nevznikl prázdný pruh.
 */
private val ART_SHIFT_Y = mapOf(
    "bazina_carodejka" to 0.10f,
    "bazina_zaklinac"  to 0.10f,
    "bazina_jezibaba"  to 0.10f,
    "bazina_druid"     to 0.10f,
    "bazina_pan_mlhy"  to 0.10f,
    // Temná citadela: postavy mají hlavu u horního okraje ilustrace
    "citadela_rytir"     to 0.20f,
    "citadela_lucistnik" to 0.20f,
    "citadela_carodej"   to 0.20f,
    "citadela_nekromant" to 0.20f,
    "citadela_valecnik"  to 0.20f,
    "citadela_strazce"   to 0.20f,
    "citadela_saboter"   to 0.20f,
    "citadela_drak"      to 0.20f,
    "citadela_general"   to 0.20f,
    "citadela_pan"       to 0.20f
)

/**
 * Art karty soupeře v art okně (celá šířka × artH), stejně jako u skutečných herních karet
 * (CardView) – Crop přes celou plochu, ne malá centrovaná ikonka. Pro avatary bez plné
 * ilustrace (staré enemy_icon_N, emoji lokace) padá zpátky na malou centrovanou AvatarView.
 */
@Composable
private fun OpponentCardArt(avatar: String, artH: Dp, modifier: Modifier = Modifier) {
    val resId = avatarDrawableRes(avatar)
    if (avatar in FULL_ART_AVATARS && resId != null) {
        val shift = ART_SHIFT_Y[avatar] ?: 0f
        if (shift == 0f) {
            Image(
                painter            = painterResource(resId),
                contentDescription = null,
                modifier           = modifier.fillMaxWidth().height(artH),
                contentScale       = ContentScale.Crop,
                alignment           = if (avatar in TOP_ALIGNED_AVATARS) Alignment.TopCenter else Alignment.Center
            )
        } else {
            // Art se kreslí do vyššího (o 2× posun) okna a celý se posune – přesah
            // pokryje obě strany, takže po ořezu rodiče nikde nezůstane prázdno.
            // requiredHeight: nesmí ho omezit výška rodiče, jinak by přesah nevznikl.
            Box(modifier.fillMaxWidth().height(artH).clipToBounds()) {
                Image(
                    painter            = painterResource(resId),
                    contentDescription = null,
                    modifier           = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .requiredHeight(artH * (1f + 2f * kotlin.math.abs(shift)))
                        .offset(y = artH * shift),
                    contentScale       = ContentScale.Crop
                )
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxWidth().height(artH),
            contentAlignment = Alignment.Center
        ) {
            AvatarView(avatar, size = 60.dp)
        }
    }
}

// ── Pomocné funkce ────────────────────────────────────────────────────────────

/**
 * Odstupňovaná rarita soupeře podle pořadí v lokaci – rovnoměrně common/rare/epic
 * napříč neboss soupeři, boss vždy legendary. Např. 9 běžných + boss: 1-3 common,
 * 4-6 rare, 7-9 epic, boss legendary.
 */
private fun opponentRarityRes(order: Int, total: Int, isBoss: Boolean): Int {
    if (isBoss) return R.drawable.rarity_legendary
    val regularCount = (total - 1).coerceAtLeast(1)
    val frac = (order - 1).toFloat() / regularCount
    return when {
        frac < 1f / 3f -> R.drawable.rarity_common
        frac < 2f / 3f -> R.drawable.rarity_rare
        else           -> R.drawable.rarity_epic
    }
}
