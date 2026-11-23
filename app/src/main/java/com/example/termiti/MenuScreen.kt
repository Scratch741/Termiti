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

// Paleta barev → GameColors.kt

/**
 * Úvod menu jen jednou za běh aplikace: po spuštění hry chvíli svítí v okně jen Temný
 * mág (boční sloupce jsou vidět hned), pak se pomalu vynoří logo a tlačítka. Při návratu z jiné
 * obrazovky se menu ukáže hned.
 */
private var menuIntroPlayed = false

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

    // 0 = mág bez loga a tlačítek, 1 = celé menu. Klepnutí kamkoli úvod přeskočí.
    val reveal = remember { Animatable(if (menuIntroPlayed) 1f else 0f) }
    val scope  = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!menuIntroPlayed) {
            menuIntroPlayed = true
            delay(1000)
            reveal.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí ────────────────────────────────────────────────────────────
        // menu_bg s Temným mágem v prostředním okně (rám, pochodně i panely pixelově
        // stejné jako menu_bg – souřadnice pochodní níže platí beze změny)
        Image(
            painter            = painterResource(R.drawable.menu_bg_mage),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

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
                    .graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 10.dp.toPx() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.width(centerW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.010f)
                ) {
                    Image(
                        painter            = painterResource(R.drawable.logo_darkmage),
                        contentDescription = "DarkMage",
                        modifier           = Modifier
                            .requiredWidth(centerW * 1.1f)
                            .offset(y = -H * -0.01f),
                        contentScale       = ContentScale.FillWidth
                    )
                    Spacer(modifier = Modifier.height(H * 0.01f))
                    MenuButton(s.play,         imageRes = R.drawable.button_1, accent = TealLight,         primary = true, onClick = onPlay)
                    MenuButton(s.multiplayer,  imageRes = R.drawable.button_8, accent = Color(0xFF3A7BD5), onClick = onMultiplayer)
                    MenuButton(s.buildDeck,    imageRes = R.drawable.button_7, accent = Gold,              onClick = onBuildDeck)
                    MenuButton(s.profile,      imageRes = R.drawable.button_4, accent = Color(0xFF7EC8E3), onClick = onProfile)
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
                    IconMenuButton(imageRes = R.drawable.button_3, label = s.shop,     size = iconSize, onClick = onShop)
                    IconMenuButton(imageRes = R.drawable.button_5, label = s.settings, size = iconSize, onClick = onSettings)
                    IconMenuButton(imageRes = R.drawable.button_6, label = s.exit,     size = iconSize, onClick = onExit)
                }
            }
        }

        // ── Verze v pravém dolním rohu ────────────────────────────────────────
        // Během úvodu: klepnutí kamkoli zobrazí menu hned (nespustí žádné tlačítko)
        if (reveal.value < 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { scope.launch { reveal.snapTo(1f) } }
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
