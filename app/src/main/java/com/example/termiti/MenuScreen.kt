package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.termiti.BuildConfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity

// Paleta barev → GameColors.kt

/**
 * Úvod menu jen jednou za běh aplikace. Fáze se počítá z času od začátku úvodu ([menuIntroStartNs],
 * pro celý proces), ne z animací vázaných na obrazovku: po spuštění Android přepne na šířku
 * a aktivitu vytvoří znovu – dřív se úvod rozběhl v první (neviditelné) verzi a ve viditelné
 * už byl „přehraný“. Začátek se nastaví až v prvním snímku na šířku. Při návratu z jiné
 * obrazovky je úvod dávno hotový a menu se ukáže hned.
 */
private var menuIntroStartNs = -1L
private const val MENU_INTRO_LEN = 4.2f

/** Měkké dobrzdění nájezdu nápisu (ease-out quint): rychle se rozjede, dlouho a jemně dosedá. */
private val MenuIntroEasing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Průběh 0..1 úseku úvodu od [start] s délkou [len] (s), s křivkou [easing]. */
private fun introPhase(t: Float, start: Float, len: Float, easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing): Float =
    easing.transform(((t - start) / len).coerceIn(0f, 1f))

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onBuildDeck: () -> Unit,
    onMultiplayer: () -> Unit,
    onProfile: () -> Unit,
    onShop: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    val s       = LocalStrings.current
    val profile = PlayerProfileManager.profile

    // Úvod (sekundy od začátku): nápis DARKMAGE se snese nad kápi (0,7 s + 2,2 s), volby se vynoří
    // (1,8 s + 1,4 s); ikony vpravo postupně shora dolů: truhla se otevře a zůstane otevřená
    // (1,2 s + 0,45 s), pak se otočí kolo (1,85 s + 1,3 s), nakonec se pootevřou dveře (3,3 s + 0,7 s). Klepnutí kamkoli úvod přeskočí.
    val introT = remember {
        mutableFloatStateOf(if (menuIntroStartNs < 0) 0f else (System.nanoTime() - menuIntroStartNs) / 1e9f)
    }
    val scope = rememberCoroutineScope()
    fun titleIn()   = introPhase(introT.floatValue, 0.7f, 2.2f, MenuIntroEasing)
    fun reveal()    = introPhase(introT.floatValue, 1.8f, 1.4f)
    fun chestOpen() = introPhase(introT.floatValue, 1.2f, 0.45f)   // po úvodu zůstává otevřená
    fun gearTurn()  = introPhase(introT.floatValue, 1.85f, 1.3f)
    fun doorOpen()  = introPhase(introT.floatValue, 3.3f, 0.7f)
    // Jen ano/ne – čtení času přímo by přepočítávalo celé menu v každém snímku úvodu
    val introRunning by remember { derivedStateOf { introT.floatValue < MENU_INTRO_LEN } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight
        val landscape = W > H
        LaunchedEffect(landscape) {
            if (!landscape) return@LaunchedEffect
            if (menuIntroStartNs < 0) menuIntroStartNs = System.nanoTime()
            while (introT.floatValue < MENU_INTRO_LEN) withFrameNanos {
                introT.floatValue = (System.nanoTime() - menuIntroStartNs) / 1e9f
            }
        }

        // ── Pozadí ────────────────────────────────────────────────────────────
        // menu_bg s Temným mágem v prostředním okně (rám, pochodně i panely pixelově
        // stejné jako menu_bg – souřadnice pochodní níže platí beze změny)
        MenuBackground()

        // ── Pochodně (menu_bg 1791×975, AR≈1.837) ────────────────────────────
        // x, y = procenta RAW obrázku; kód přepočítá ContentScale.Crop sám
        val imgAR  = 1791f / 975f
        val dispAR = W.value / H.value.coerceAtLeast(1f)
        val imgDispW: Dp
        val imgDispH: Dp
        val cropX: Dp
        val cropY: Dp
        if (dispAR >= imgAR) {
            imgDispW = W
            imgDispH = W / imgAR
            cropX    = 0.dp
            cropY    = (imgDispH - H) / 2f
        } else {
            imgDispW = H * imgAR
            imgDispH = H
            cropX    = (imgDispW - W) / 2f
            cropY    = 0.dp
        }
        val torchSize = H * 0.15f
        // y=0.18f = malinko níž než 0.16f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f - cropY - torchSize * 0.80f
            ),
            size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f - cropY - torchSize * 0.80f
            ),
            size = torchSize, seed = 1.7f
        )

        // ── Hlavní layout: side=weight(1f) | střed=pevná šířka | side=weight(1f) ──
        // Střed má pevnou šířku (W*0.46 nebo H – co je menší), zbytek si strany
        // rozdělí rovnoměrně → levý/pravý sloupec je vždy symetricky vycentrovaný.
        val centerW  = minOf(W * 0.46f, H * 1.0f)
        val iconSize = H * 0.12f

        // ── RUČNÍ POSUN SLOUPCŮ ─────────────────────────────────────────────
        // Funguje i se zápornými hodnotami (na rozdíl od padding).
        val leftColShift       = -5.dp   // levý sloupec:  kladné = doprava (ke středu), záporné = doleva
        val leftColVertShift   = 30.dp    // levý sloupec:  kladné = dolů, záporné = nahoru
        val rightColShift      = -25.dp  // pravý sloupec: kladné = doleva  (ke středu), záporné = doprava
        val rightColVertShift  = 35.dp   // pravý sloupec: kladné = dolů, záporné = nahoru
        val centerShift        = 9.dp    // střední sloupec: kladné = doprava, záporné = doleva
        // ────────────────────────────────────────────────────────────────────

        Row(
            modifier = Modifier
                .fillMaxSize()
                // Bez statusBars/navigationBars paddingu: hra je přes celou obrazovku, ale
                // při startu systémové lišty chvíli svítí a padding by po jejich skrytí
                // posunul boční sloupce (viditelné „doskočení“).
                .padding(vertical = H * 0.02f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Levý sloupec – profil ─────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (profile != null) {
                    Column(
                        modifier            = Modifier.offset(x = leftColShift, y = leftColVertShift),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(H * 0.025f)
                    ) {
                        ProfileInfo(profile, H, onAvatarClick = onProfile)
                    }
                }
            }

            // ── Střed – titulek + 4 tlačítka (pevná šířka) ───────────────────
            Box(
                modifier         = Modifier
                    .fillMaxHeight().width(centerW).offset(x = centerShift)
                    // Úvod: jen prostředek (logo + tlačítka) se pomalu vynoří, boční sloupce svítí hned
                    .graphicsLayer { alpha = reveal(); translationY = (1f - reveal()) * 10.dp.toPx() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.width(centerW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.010f)
                ) {
                    Spacer(Modifier.height(menuTitleSpace(centerW)))
                    Spacer(modifier = Modifier.height(H * 0.01f))
                    // Volby jako nápisy (bez desek), aby byl mág v pozadí vidět
                    val optW = centerW * 0.95f
                    Column(Modifier.menuVeil(), horizontalAlignment = Alignment.CenterHorizontally) {
                        MenuTextOption(s.play, H, optW, primary = true, onClick = onPlay)
                        MenuOptionDivider(centerW * 0.55f)
                        MenuTextOption(s.multiplayer, H, optW, onClick = onMultiplayer)
                        MenuOptionDivider(centerW * 0.55f)
                        MenuTextOption(s.buildDeck, H, optW, onClick = onBuildDeck)
                        MenuOptionDivider(centerW * 0.55f)
                        MenuTextOption(s.profile, H, optW, onClick = onProfile)
                    }
                }
            }

            // ── Pravý sloupec – ikony ─────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.offset(x = -rightColShift, y = rightColVertShift),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.005f)
                ) {
                    // Úvod: truhla se otevře a zavře, kolo se otočí, dveře se pootevřou
                    IconMenuButton(imageRes = R.drawable.button_3, label = s.shop, size = iconSize,
                        overlay = { MedallionLayer(R.drawable.menu_icon_chest_open, alpha = { chestOpen() }) }, onClick = onShop)
                    IconMenuButton(imageRes = R.drawable.button_5, label = s.settings, size = iconSize,
                        overlay = { MedallionLayer(R.drawable.menu_icon_gear_disk, alpha = { if (gearTurn() in 0.001f..0.999f) 1f else 0f }, rotation = { gearTurn() * 360f }, pivotTexX = 74f, pivotTexY = 52.5f) },
                        onClick = onSettings)
                    IconMenuButton(imageRes = R.drawable.button_6, label = s.exit, size = iconSize,
                        overlay = { MedallionLayer(R.drawable.menu_icon_door_closed, alpha = { 1f - doorOpen() }) }, onClick = onExit)
                }
            }
        }

        // ── Nápis DARKMAGE nad kápí mága (pevně podle pozadí) ─────────────────
        // Úvod: sjede svisle od horního okraje obrazovky na své místo
        MenuTitle(centerW, W, H, imgDispH, cropY, enter = { titleIn() })

        // ── Verze v pravém dolním rohu ────────────────────────────────────────
        // Během úvodu: klepnutí kamkoli zobrazí menu hned (nespustí žádné tlačítko)
        if (introRunning) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { menuIntroStartNs = System.nanoTime() - 100_000_000_000L; introT.floatValue = 100f }
            )
        }

        Text(
            text     = "v${BuildConfig.VERSION_NAME}",
            color    = TextPrimary.copy(alpha = 0.75f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 6.dp)
        )
    }
}

// ── Profilový sloupec ─────────────────────────────────────────────────────────

@Composable
fun ProfileInfo(profile: PlayerProfile, H: Dp, onAvatarClick: (() -> Unit)? = null) {
    val fs = (H.value * 0.035f).sp
    // Avatar – klik otevře profil (je-li onAvatarClick zadán)
    Box(
        modifier = Modifier
            .size(H * 0.162f)
            .clip(RoundedCornerShape(H * 0.036f))
            .background(Gold.copy(alpha = 0.15f))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(H * 0.036f))
            .then(
                if (onAvatarClick != null) Modifier.clickable { SoundManager.playMenuTap(); onAvatarClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AvatarDisplay(profile.avatar, sizeDp = H.value * 0.144f)
    }
    // Level pod avatarem
    Text("Lv. ${profile.level}", color = Gold, fontSize = fs, fontWeight = FontWeight.Bold)
    // Gold
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.015f)) {
        Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(fs.value.dp))
        Text("${profile.gold}", color = Gold, fontSize = fs, fontWeight = FontWeight.Bold)
    }
    // Gems
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.015f)) {
        Image(painterResource(R.drawable.diamond_icon), contentDescription = null, modifier = Modifier.size(fs.value.dp))
        Text("${profile.gems}", color = Color(0xFF7EC8E3), fontSize = fs, fontWeight = FontWeight.Bold)
    }
    // Dust
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.015f)) {
        Image(painterResource(R.drawable.dust_icon), contentDescription = null, modifier = Modifier.size(fs.value.dp))
        Text("${profile.dust}", color = Color(0xFFB39DDB), fontSize = fs, fontWeight = FontWeight.Bold)
    }
}

// ── Emoji ikonkové tlačítko (bez image assetu) ────────────────────────────────

@Composable
private fun EmojiIconMenuButton(
    emoji: String,
    label: String,
    size: Dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.08f)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.14f))
                .border(1.5.dp, Gold.copy(alpha = 0.55f), CircleShape)
                .clickable { SoundManager.playMenuTap(); onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = (size.value * 0.42f).sp)
        }
        Text(
            label,
            color         = TextMuted,
            fontSize      = (size.value * 0.20f).sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ── Ikonkové tlačítko ─────────────────────────────────────────────────────────

@Composable
fun IconMenuButton(
    @DrawableRes imageRes: Int,
    label: String,
    size: Dp,
    /** Vrstva přes medailon (animace v úvodu menu: truhla, kolo, dveře – viz [MedallionLayer]). */
    overlay: @Composable BoxScope.() -> Unit = {},
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.03f)
    ) {
        // Obrázek 662×107 (AR=6.19), centroid ikony na x=12.5% šířky.
        // requiredHeight/Width obejde omezení Boxu → obraz se renderuje v plné šířce.
        // offset(x = -0.27×size) posune tak, aby ikona byla přesně uprostřed kruhu.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable { SoundManager.playMenuTap(); onClick() }
        ) {
            Image(
                painter            = painterResource(imageRes),
                contentDescription = label,
                modifier           = Modifier
                    .requiredHeight(size)
                    .requiredWidth(size * 6.19f)
                    .offset(x = -(size * -2.4f)),
                contentScale       = ContentScale.FillBounds
            )
            overlay()
        }
        Text(
            label,
            color         = TextMuted,
            fontSize      = (size.value * 0.20f).sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ── Tlačítko menu ─────────────────────────────────────────────────────────────

/** Text vedlejších tlačítek menu: světlý pergamen → tlumený bronz (hlavní má [TitleGold]). */
private val MenuTextParchment = listOf(Color(0xFFF6EBD0), Color(0xFFDCC69C), Color(0xFFA98B5A))

/** Barva žhavé záře hlavního tlačítka (bronz/žár jako bronzové tlačítko v dialozích). */
private val MenuEmber = Color(0xFFE8A83A)

/**
 * Tlačítko hlavního menu a sub-menu (textura button_* 662×107: medailon s ikonou vlevo,
 * tmavá deska s bronzovým lemem).
 *
 * - [primary] = hlavní volba obrazovky: pulzující žhavá záře kolem a teplé nasvícení
 *   desky, text v plném zlatě. Vedlejší tlačítka mají text v tlumeném pergamenu.
 * - Text je Cinzel se svislým přechodem jako logo DARKMAGE a nadpisy.
 * - Stisk: tlačítko se zmáčkne na 97 % a rozjasní (místo obdélníkového ripple efektu,
 *   který na tvar textury neseděl).
 */
@Composable
fun MenuButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier.fillMaxWidth(0.9f),
    enabled: Boolean = true,
    @DrawableRes imageRes: Int? = null,
    /** Volitelná ikonka před textem (např. mince u ceny). */
    @DrawableRes leadingIconRes: Int? = null,
    /** Hlavní volba obrazovky (v hlavním menu HRÁT). */
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val alphaVal = if (enabled) 1f else 0.4f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(90), label = "menuPress")
    val clickMod = if (enabled) Modifier.clickable(interaction, indication = null) { SoundManager.playMenuTap(); onClick() } else Modifier

    if (imageRes != null) {
        // Pomalé „dýchání“ záře hlavního tlačítka
        val glow = if (primary && enabled) {
            val t = rememberInfiniteTransition(label = "menuGlow")
            t.animateFloat(
                initialValue  = 0.55f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label         = "menuGlowA"
            ).value
        } else 0f
        val lighten = if (pressed && enabled) 0.14f else 0f

        // Varianta s obrázkem – BoxWithConstraints pro proporcionální text offset
        BoxWithConstraints(
            modifier = modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .drawBehind {
                    if (glow <= 0f) return@drawBehind
                    // Žhavá záře za deskou: soustředné obrysy s klesající průhledností
                    // (deska začíná kousek od levého okraje, medailon je kulatý a vyšší)
                    val steps = 7
                    val reach = 9.dp.toPx()
                    val left  = size.width * 0.07f
                    for (i in steps downTo 1) {
                        val g = reach * i / steps
                        drawRoundRect(
                            color        = MenuEmber.copy(alpha = 0.10f * glow * (1f - (i - 1f) / steps)),
                            topLeft      = Offset(left - g, size.height * 0.06f - g),
                            size         = Size(size.width - left + 2 * g - size.width * 0.005f, size.height * 0.88f + 2 * g),
                            cornerRadius = CornerRadius(6.dp.toPx() + g)
                        )
                    }
                }
                .then(clickMod)
                .alpha(alphaVal)
        ) {
            val btnW = maxWidth
            Image(
                painter            = painterResource(imageRes),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth(),
                contentScale       = ContentScale.FillWidth,
                colorFilter        = if (lighten > 0f) ColorFilter.tint(Color.White.copy(alpha = lighten), BlendMode.Screen) else null
            )
            if (primary) {
                // Teplé nasvícení tmavé plochy desky (jako by za ní žhnul oheň)
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(start = btnW * 0.20f, end = btnW * 0.02f, top = btnW * 0.022f, bottom = btnW * 0.022f)
                        .drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    0f to MenuEmber.copy(alpha = 0.22f * (0.6f + 0.4f * glow)),
                                    1f to Color.Transparent,
                                    center = Offset(size.width * 0.35f, size.height * 0.5f),
                                    radius = size.width * 0.6f
                                )
                            )
                        }
                )
            }
            // Ikonka zabírá ~1/6 šířky → text začíná za ní
            Box(
                modifier         = Modifier
                    .matchParentSize()
                    .padding(start = btnW * 0.23f, end = btnW * 0.03f),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(btnW * 0.02f)
                ) {
                    if (leadingIconRes != null) {
                        Image(
                            painter            = painterResource(leadingIconRes),
                            contentDescription = null,
                            modifier           = Modifier.size(btnW * 0.055f)
                        )
                    }
                    Text(
                        label.uppercase(),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style    = TextStyle(
                            fontFamily    = CinzelBold,
                            fontWeight    = FontWeight.Bold,
                            fontSize      = (btnW.value * (if (primary) 0.050f else 0.045f)).sp,
                            letterSpacing = 2.sp,
                            brush         = Brush.verticalGradient(if (primary) TitleGold else MenuTextParchment),
                            shadow        = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 3f), 6f)
                        )
                    )
                }
            }
        }
    } else {
        // Fallback bez obrázku
        Box(
            modifier = modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = if (enabled) 0.12f else 0.05f))
                .border(1.dp, accent.copy(alpha = if (enabled) 0.5f else 0.2f), RoundedCornerShape(10.dp))
                .then(clickMod)
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (leadingIconRes != null) {
                    Image(
                        painter            = painterResource(leadingIconRes),
                        contentDescription = null,
                        modifier           = Modifier.size(15.dp).alpha(alphaVal)
                    )
                }
                Text(
                    label,
                    color         = if (enabled) TextPrimary.copy(alpha = alphaVal) else TextMuted.copy(alpha = alphaVal),
                    fontSize      = 13.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

// ── Menu „jen nápisy“ (varianta B, 2026-11-23) ───────────────────────────────
// Volby bez desek: mág v pozadí zůstává celý vidět. Nápis v Cinzelu s tmavým obrysem,
// mezi volbami bronzová linka.

/**
 * Tmavý oválný závoj za seznamem voleb, aby byl text čitelný i přes světlé plameny.
 * Kreslí se i mimo hranice prvku a do stran mizí.
 */
internal fun Modifier.menuVeil(): Modifier = drawBehind {
    val r = size.height * 0.62f
    scale(scaleX = size.width / size.height * 0.95f, scaleY = 1f, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xB0060406), 0.55f to Color(0x80060406), 1f to Color.Transparent,
                center = center, radius = r
            ),
            radius = r, center = center
        )
    }
}

/** Kulatý bronzový otazník (pravidla režimu) – větší dotyková plocha než kresba. */
@Composable
internal fun MenuHelpBadge(size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .requiredSize(size * 1.6f)
            .clickable(remember { MutableInteractionSource() }, indication = null) { SoundManager.playMenuTap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(size).drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(Color.Black.copy(alpha = 0.85f), r)
                val w = 1.5.dp.toPx()
                drawCircle(Brush.verticalGradient(listOf(BronzeLight, BronzeDark)), r - w / 2f - 1f, style = Stroke(w))
                drawCircle(Color(0xFF141012), r - w - 1.5f)
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "?",
                style = TextStyle(
                    fontFamily = CinzelBold,
                    fontWeight = FontWeight.Bold,
                    fontSize   = (size.value * 0.6f).sp,
                    brush      = Brush.verticalGradient(TitleGold)
                )
            )
        }
    }
}

/** Oddělovač mezi volbami: bronzová linka mizející do stran s kosočtverečkem uprostřed. */
@Composable
internal fun MenuOptionDivider(width: Dp) {
    BronzeDivider(Modifier.width(width), alpha = 0.6f)
}

/** Text vedlejších voleb menu: světlejší pergamen než u desek, aby nápis bez desky vynikl. */
private val MenuTextBright = listOf(Color(0xFFFFF6E2), Color(0xFFF3E0B6), Color(0xFFCFAE74))
/** Bronzový přechod zvýrazněné části volby (název balíčku v lobby). */
private val MenuTextBronze = listOf(Color(0xFFF6C985), Color(0xFFD99A52), Color(0xFF9C6630))

/**
 * Volba menu jako nápis na ose sloupce, za ním případně otazník s pravidly
 * (vlevo je stejně široké prázdné místo, aby text zůstal na ose).
 *
 * - Pod písmeny je rozmazaný tmavý obrys, takže nápis vynikne i přes plameny v pozadí.
 * - [primary] (HRÁT): větší písmo v plném zlatě a místo tmavého obrysu pulzující žhavá záře.
 * - Stisk zmáčkne volbu na 96 %.
 */
@Composable
internal fun MenuTextOption(
    label: String,
    H: Dp,
    width: Dp,
    primary: Boolean = false,
    /** Vypnutá volba je ztlumená a nereaguje (např. výběr balíčku bez platného balíčku). */
    enabled: Boolean = true,
    /** Část textu za [label] zvýrazněná bronzem (název balíčku v online lobby). */
    highlight: String? = null,
    onHelp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(90), label = "optPress")
    val glow = if (primary) {
        rememberInfiniteTransition(label = "optGlow").animateFloat(
            initialValue  = 0.55f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label         = "optGlowA"
        ).value
    } else 0f
    val density  = LocalDensity.current
    val glowBlur = with(density) { 14.dp.toPx() }
    val haloBlur = with(density) { 8.dp.toPx() }
    val slot = H * 0.075f
    val base = TextStyle(
        fontFamily    = CinzelBold,
        fontWeight    = FontWeight.Bold,
        fontSize      = (H.value * (if (primary) 0.074f else 0.052f)).sp,
        letterSpacing = (H.value * 0.004f).sp,
        brush         = Brush.verticalGradient(if (primary) TitleGold else MenuTextBright)
    )
    val text = label.uppercase() + (highlight?.uppercase() ?: "")
    val styled = androidx.compose.ui.text.buildAnnotatedString {
        append(label.uppercase())
        if (highlight != null) {
            pushStyle(androidx.compose.ui.text.SpanStyle(brush = Brush.verticalGradient(MenuTextBronze)))
            append(highlight.uppercase())
            pop()
        }
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(H * 0.12f)
            .then(if (enabled) Modifier.clickable(interaction, indication = null) { SoundManager.playMenuTap(); onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier              = Modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale; alpha = if (enabled) 1f else 0.4f },
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(H * 0.02f)
        ) {
            if (onHelp != null) Spacer(Modifier.width(slot))
            Box(contentAlignment = Alignment.Center) {
                // Spodní vrstva: stejný text plnou barvou s rozmazaným stínem té barvy
                // (žhavá záře u HRÁT, jinak tmavý obrys pro čitelnost). Stín musí mít
                // vrstva bez brush – Android kreslí stín textu s přechodem barvami přechodu.
                val halo = if (primary) MenuEmber.copy(alpha = 0.9f * glow) else Color.Black.copy(alpha = 0.95f)
                // Dlouhý text (název balíčku v lobby) se zkrátí třemi tečkami, obě vrstvy stejně
                Text(
                    text, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily    = base.fontFamily,
                        fontWeight    = base.fontWeight,
                        fontSize      = base.fontSize,
                        letterSpacing = base.letterSpacing,
                        color         = halo,
                        shadow        = Shadow(halo, Offset.Zero, if (primary) glowBlur else haloBlur)
                    )
                )
                Text(styled, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = base)
            }
            if (onHelp != null) Box(Modifier.size(slot), contentAlignment = Alignment.Center) { MenuHelpBadge(H * 0.052f, onHelp) }
        }
    }
}

/** Výška místa po dřívějším logu s deskou – volby pod ním zůstávají tam, kde byly. */
internal fun menuTitleSpace(centerW: Dp): Dp = centerW * 1.1f * 377f / 1400f

/**
 * Nápis DARKMAGE: jen zlatá písmena vyříznutá z loga (logo_darkmage_text 863×170, písmena
 * v řádcích 24–145, s tmavým stínem). Umístění se počítá z pozadí menu_bg_mage, ne z
 * vystředěného sloupce voleb: spodek písmen je těsně nad špičkou kápě Temného mága
 * (y = 225 z 925 px obrázku). Hlavní menu i nabídka Hrát volají totéž, takže nápis při
 * přechodu mezi nimi neposkočí. Kreslí se do BoxWithConstraints celé obrazovky (zarovnání vlevo nahoře).
 *
 * @param imgDispH výška pozadí po ContentScale.Crop, [cropY] jeho ořez nahoře (jako u pochodní)
 */
@Composable
internal fun MenuTitle(
    centerW: Dp, W: Dp, H: Dp, imgDispH: Dp, cropY: Dp,
    /** Průběh nájezdu 0→1 (čte se až při kreslení); 1 = nápis stojí na svém místě. */
    enter: () -> Float = { 1f }
) {
    val tw    = centerW * 1.1f * 863f / 1400f
    val th    = tw * 170f / 863f
    val tipY  = imgDispH * (225f / 925f) - cropY
    val top   = tipY - H * 0.012f - th * (145f / 170f)
    Image(
        painter            = painterResource(R.drawable.logo_darkmage_text),
        contentDescription = "DarkMage",
        modifier           = Modifier
            .offset(x = (W - tw) / 2 + 9.dp, y = top)
            .width(tw)
            // Vrstva až za offsetem: přiblížení a posun se počítají od středu nápisu.
            // (Dřív byla před offsetem → střed zmenšení byl u levého okraje obrazovky
            // a nápis při dosedání ujížděl do strany.)
            .graphicsLayer {
                val k = enter()
                if (k >= 1f) return@graphicsLayer
                // start celý nad horním okrajem obrazovky, svisle dolů na místo
                translationY = -(1f - k) * (top.toPx() + size.height + 8.dp.toPx())
                alpha        = (k * 2.5f).coerceIn(0f, 1f)
                scaleX       = 1.04f - 0.04f * k
                scaleY       = 1.04f - 0.04f * k
            },
        contentScale       = ContentScale.FillWidth
    )
}

/**
 * Vrstva přes medailon [IconMenuButton]: obrázek 106×107 = výřez textury button_* kolem
 * medailonu (x 23–129, střed medailonu x 76, y 53,5), jen vnitřní kruh. V boxu ikony je
 * textura posunutá tak, že výřez začíná na 2 % šířky a střed medailonu je na 51,5 %.
 * [alpha] a [rotation] se čtou až při kreslení (animace nepřekreslují menu).
 */
@Composable
internal fun BoxScope.MedallionLayer(
    @DrawableRes res: Int,
    alpha: () -> Float,
    rotation: () -> Float = { 0f },
    /** Střed otáčení v pixelech textury button_* (kolo na ikoně nastavení má střed 74 / 52,5, ne střed medailonu). */
    pivotTexX: Float = 76f,
    pivotTexY: Float = 53.5f
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val img = remember(res) { decodeRaw(ctx.resources, res) }
    Canvas(Modifier.matchParentSize()) {
        val a = alpha()
        if (a <= 0.003f) return@Canvas
        val sz = this.size.height
        val pivot = Offset((0.020f + (pivotTexX - 23f) / 107f) * sz, pivotTexY / 107f * sz)
        rotate(rotation(), pivot) {
            drawImage(
                img,
                dstOffset     = androidx.compose.ui.unit.IntOffset((0.020f * sz).toInt(), 0),
                dstSize       = androidx.compose.ui.unit.IntSize((sz * 106f / 107f).toInt(), sz.toInt()),
                alpha         = a.coerceIn(0f, 1f),
                filterQuality = FilterQuality.High
            )
        }
    }
}
