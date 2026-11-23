package com.example.termiti

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ShGold  = Color(0xFFD4A843)
private val ShText  = Color(0xFFEDE0C4)
private val ShMuted = Color(0xFF7A6E5F)
private val ShGreen = Color(0xFF4CAF50)
private val ShDust  = Color(0xFFB39DDB)
private val ShBgCard = Color(0xFF1A1320)

private fun shRarityColor(r: Rarity) = when (r) {
    Rarity.COMMON    -> Color(0xFF9E9E9E)
    Rarity.RARE      -> Color(0xFF4A90D9)
    Rarity.EPIC      -> Color(0xFF9B59B6)
    Rarity.LEGENDARY -> Color(0xFFD4A843)
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShopScreen(allCards: List<Card>, onBack: () -> Unit) {
    var profile by remember { mutableStateOf(PlayerProfileManager.profile) }
    /** Běží otevírání balíčků (PackOpeningOverlay). */
    var opening by remember { mutableStateOf(false) }
    /** Nákup čekající na potvrzení: počet balíčků a jestli se má po koupi rovnou otevírat. */
    var pendingBuy by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val s = LocalStrings.current

    val cost      = CardCollectionManager.PACK_COST_GOLD
    val gold      = profile?.gold ?: 0
    val packs     = profile?.unopenedPacks ?: 0
    val canAfford = gold >= cost

    /** Koupí [n] balíčků do zásoby; obsah se losuje až při otevření. */
    fun buy(n: Int): Boolean {
        if (!CardCollectionManager.buyPacks(n)) return false
        SoundManager.playPackBuy()
        profile = PlayerProfileManager.profile
        return true
    }

    // Systémové Zpět: zavře dotaz na nákup / otevírání, jinak zpět do menu (dřív zavřelo celou hru)
    androidx.activity.compose.BackHandler {
        when {
            pendingBuy != null -> pendingBuy = null
            opening            -> { opening = false; profile = PlayerProfileManager.profile }
            else               -> onBack()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí – stejné jako main menu ───────────────────────────────────
        MenuBackground()

        // ── Pochodně ─────────────────────────────────────────────────────────
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

        // ── Stejný 3-sloupcový layout jako hlavní menu ────────────────────────
        val centerW          = minOf(W * 0.46f, H * 1.0f)
        val iconSize         = H * 0.12f
        val leftColShift     = -5.dp
        val leftColVertShift = 30.dp
        val rightColShift    = -25.dp
        val rightColVertShift= 35.dp
        val centerShift      = 9.dp

        Row(
            modifier          = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
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
                        ProfileInfo(profile!!, H)
                    }
                }
            }

            // ── Střed – obsah obchodu ─────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().width(centerW).offset(x = centerShift),
                contentAlignment = Alignment.Center
            ) {
                // Rozložení „Oltář“: balíček se vznáší mezi rukama mága pod kápí, pod ním
                // kované tlačítko a menší železná pro nákup více balíčků do zásoby
                Column(
                    modifier            = Modifier.width(centerW).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(H * 0.03f))
                    CampaignTitle(s.shopPacks, fontSize = 28.sp)
                    BronzeDivider(Modifier.fillMaxWidth(0.85f))
                    Spacer(Modifier.weight(1f))
                    PackShelf(
                        packs  = packs,
                        size   = H * 0.23f,
                        onOpen = { if (packs > 0) { SoundManager.playMenuTap(); opening = true } }
                    )
                    Spacer(Modifier.height(H * 0.025f))
                    if (packs > 0) {
                        ForgedButton(
                            text     = s.shopOpen,
                            modifier = Modifier.width(centerW * 0.58f).height(H * 0.105f),
                            fontSize = 21.sp,
                            glow     = true,
                            onClick  = { opening = true }
                        )
                    } else {
                        ForgedButton(
                            text     = "${s.shopBuy.uppercase()}  ·  $cost",
                            modifier = Modifier.width(centerW * 0.58f).height(H * 0.105f),
                            fontSize = 19.sp,
                            coin     = true,
                            glow     = canAfford,
                            enabled  = canAfford,
                            onClick  = { pendingBuy = 1 to true }
                        )
                    }
                    Spacer(Modifier.height(H * 0.018f))
                    // Nákup do zásoby (se zásobou i 1×, jinak 5× a 10×)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (packs > 0) listOf(1, 5, 10) else listOf(5, 10)).forEach { n ->
                            ForgedButton(
                                text     = "$n×  ${n * cost}",
                                // Se zásobou tři tlačítka – široká tak, aby se „10× 1000“ s mincí vešlo
                                modifier = Modifier.width(centerW * (if (packs > 0) 0.285f else 0.285f)).height(H * 0.075f),
                                fontSize = if (packs > 0) 11.sp else 12.sp,
                                coin     = true,
                                iron     = true,
                                enabled  = gold >= n * cost,
                                onClick  = { pendingBuy = n to false }
                            )
                        }
                    }
                    Spacer(Modifier.height(H * 0.03f))
                    // Šance na vzácnost
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Rarity.entries.forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RarityGem(r, 13.dp)
                                Text("${r.packWeight} %", style = obsLabelStyle(11.sp, shRarityColor(r).lighten()).copy(shadow = TextShadowDark))
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    val untilPity = (CardCollectionManager.PITY_PACKS - (profile?.packsSinceLegendary ?: 0))
                        .coerceIn(1, CardCollectionManager.PITY_PACKS)
                    Text(s.shopPackInfo.uppercase(), style = obsLabelStyle(8.sp, ObsText, letterSpacing = 0.6.sp).copy(shadow = TextShadowDark))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        (if (untilPity <= 1) s.shopPityNext else s.shopPity.format(untilPity)).uppercase(),
                        style = obsLabelStyle(8.sp, if (untilPity <= 1) BronzeLight else BronzeText, letterSpacing = 0.6.sp).copy(shadow = TextShadowDark)
                    )
                    Spacer(Modifier.height(H * 0.035f))
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
                    Box(Modifier.graphicsLayer { alpha = 0f }) {
                        IconMenuButton(imageRes = R.drawable.button_3, label = LocalStrings.current.shop, size = iconSize,
                        overlay = { MedallionLayer(R.drawable.menu_icon_chest_open, alpha = { 1f }) }, onClick = {})
                    }
                    Box(Modifier.graphicsLayer { alpha = 0f }) {
                        IconMenuButton(imageRes = R.drawable.button_5, label = LocalStrings.current.settings, size = iconSize, onClick = {})
                    }
                    IconMenuButton(imageRes = R.drawable.button_6, label = LocalStrings.current.back.removePrefix("← "), size = iconSize, onClick = { onBack() })
                }
            }
        }

        // ── Potvrzení nákupu ──────────────────────────────────────────────────
        pendingBuy?.let { (n, openAfter) ->
            BuyConfirmOverlay(
                count     = n,
                price     = n * cost,
                goldAfter = gold - n * cost,
                onCancel  = { pendingBuy = null },
                onConfirm = {
                    pendingBuy = null
                    if (buy(n) && openAfter) opening = true
                }
            )
        }

        // ── Otevírání balíčků (PackOpening.kt) ────────────────────────────────
        if (opening) {
            PackOpeningOverlay(
                allCards         = allCards,
                onProfileChanged = { profile = PlayerProfileManager.profile },
                onClose          = { opening = false; profile = PlayerProfileManager.profile }
            )
        }
    }
}

// ── Potvrzení nákupu ──────────────────────────────────────────────────────────

/** Dotaz před utracením zlata: kolik balíčků, za kolik a kolik zlata zbyde. */
@Composable
private fun BuyConfirmOverlay(count: Int, price: Int, goldAfter: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val s = LocalStrings.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CampaignTitle(s.shopBuyConfirmTitle, fontSize = 24.sp)
            BronzeDivider(Modifier.width(260.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.shopBuyConfirmCount.format(count).uppercase(), style = obsLabelStyle(13.sp, ObsText, letterSpacing = 1.sp))
                Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(16.dp))
                Text("$price", style = obsLabelStyle(14.sp, BronzeText))
            }
            Text(s.shopBuyConfirmLeft.format(goldAfter), color = ObsTextMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ObsButton(text = s.cancel.uppercase(), modifier = Modifier.width(130.dp).height(38.dp), fontSize = 12.sp, onClick = onCancel)
                ObsButton(text = s.shopBuy.uppercase(), modifier = Modifier.width(130.dp).height(38.dp), primary = true, fontSize = 13.sp, onClick = onConfirm)
            }
        }
    }
}

// ── Police s neotevřenými balíčky ─────────────────────────────────────────────

/**
 * Zásoba balíčků: až tři balíčky ve vějíři a počet. Po nákupu poskočí; klepnutím se jde
 * otevírat. Prázdná police ukazuje jen stín balíčku.
 */
@Composable
private fun PackShelf(packs: Int, size: Dp, onOpen: () -> Unit) {
    val s = LocalStrings.current
    val bump = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(packs) }
    LaunchedEffect(packs) {
        if (packs > last) {
            bump.snapTo(1.22f)
            bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
        last = packs
    }
    val inf = rememberInfiniteTransition(label = "shelf")
    val breathe by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )

    Box(
        Modifier
            .size(width = size * 1.25f, height = size * 1.15f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = packs > 0,
                onClick = onOpen
            ),
        contentAlignment = Alignment.Center
    ) {
        if (packs > 0) {
            Canvas(Modifier.requiredSize(size * 1.5f)) {
                val a = 0.30f + 0.18f * breathe
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFB064E0).copy(alpha = 0.6f * a), 0.55f to ShGold.copy(alpha = 0.25f * a), 1f to Color.Transparent,
                        radius = this.size.minDimension / 2f
                    )
                )
            }
        }
        // Krajní balíčky první, prostřední navrch
        val shown = packs.coerceIn(1, 3)
        val mid   = (shown - 1) / 2f
        (0 until shown).sortedByDescending { kotlin.math.abs(it - mid) }.forEach { k ->
            val d = k - mid
            PackVisual(
                Modifier.size(size).graphicsLayer {
                    translationX = d * size.toPx() * 0.26f
                    translationY = kotlin.math.abs(d) * size.toPx() * 0.04f - (if (packs > 0) 3.dp.toPx() * breathe else 0f)
                    rotationZ    = d * 9f
                    val sc = bump.value * (1f - 0.08f * kotlin.math.abs(d))
                    scaleX = sc; scaleY = sc
                    alpha  = 1f
                }
            )
        }
        // Počet v zásobě vždy (i „× 0“) – balíček sám je vždy plně vidět
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = size * 0.02f, bottom = size * 0.02f)
                .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
                .size(width = 52.dp, height = 26.dp)
                .obsidianSurface(13.dp).bronzeRim(13.dp, level = 1.1f),
            contentAlignment = Alignment.Center
        ) {
            Text("× $packs", style = obsLabelStyle(14.sp, if (packs > 0) BronzeText else ObsTextMuted))
        }
    }
}

// ── Nákup do zásoby ───────────────────────────────────────────────────────────

/** Tmavý stín textu přímo na pozadí (bez desky). */
private val TextShadowDark = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 2f), 6f)

/** Obsidiánová dlaždice „n× · cena“: počet Cinzelem nahoře, cena s mincí pod ním. */
@Composable
private fun BuyTile(n: Int, price: Int, enabled: Boolean, H: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(H * 0.14f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .obsidianSurface(6.dp).bronzeRim(6.dp, level = 0.95f)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) { SoundManager.playMenuTap(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("$n×", style = obsLabelStyle(17.sp, ObsText))
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Image(painterResource(R.drawable.goldcoin_icon), null, Modifier.size(12.dp))
            Text("$price", style = obsLabelStyle(11.sp, BronzeText))
        }
    }
}

/** Žhavá záře kolem hlavního tlačítka obchodu (jako HRÁT v menu). */
private fun Modifier.shopGlow(): Modifier = drawBehind {
    val g = 10.dp.toPx()
    for (i in 6 downTo 1) {
        val e = g * i / 6
        drawRoundRect(
            Color(0xFFE8A83A).copy(alpha = 0.06f * (1f - (i - 1) / 6f)),
            topLeft = androidx.compose.ui.geometry.Offset(-e, -e),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * e, size.height + 2 * e),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx() + e)
        )
    }
}

/** Světlejší odstín barvy vzácnosti (procenta na tmavém pozadí). */
private fun Color.lighten(k: Float = 0.25f) = Color(red + (1f - red) * k, green + (1f - green) * k, blue + (1f - blue) * k, alpha)

// ── Kované tlačítko ───────────────────────────────────────────────────────────

/** Ražený text na bronzu (tmavý přechod, světlý odlesk pod hranou) a světlý text na železe. */
private val ForgedInk  = listOf(Color(0xFF4A2E0E), Color(0xFF1E1206))
private val ForgedSilver = listOf(Color(0xFFF6EBD0), Color(0xFFDCC69C), Color(0xFFA98B5A))

/**
 * Tlačítko z kované textury dialogů: [iron] = false → button_bronze (hlavní akce, ražený tmavý
 * text, volitelně žhavá záře), true → button_iron (vedlejší, světlý text). [coin] = mince před textem.
 * Stisk zmáčkne tlačítko na 96 %.
 */
@Composable
private fun ForgedButton(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    coin: Boolean = false,
    iron: Boolean = false,
    glow: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(90), label = "forgedPress")
    val pulse = if (glow && enabled) rememberInfiniteTransition(label = "forgedGlow").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "forgedGlowA"
    ).value else 0f
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .drawBehind {
                if (pulse <= 0f) return@drawBehind
                val g = 12.dp.toPx()
                for (i in 7 downTo 1) {
                    val e = g * i / 7
                    drawRoundRect(
                        Color(0xFFE8A83A).copy(alpha = 0.075f * pulse * (1f - (i - 1) / 7f)),
                        topLeft = androidx.compose.ui.geometry.Offset(-e, -e),
                        size = androidx.compose.ui.geometry.Size(size.width + 2 * e, size.height + 2 * e),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx() + e)
                    )
                }
            }
            .buttonTexture(if (iron) R.drawable.button_iron else R.drawable.button_bronze)
            .clickable(interaction, indication = null, enabled = enabled) { SoundManager.playMenuTap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (coin) Image(painterResource(R.drawable.goldcoin_icon), null, Modifier.size(fontSize.value.dp * 1.15f))
            Text(
                text,
                maxLines = 1,
                style = obsLabelStyle(fontSize, Color.Unspecified, letterSpacing = 1.5.sp).copy(
                    brush  = Brush.verticalGradient(if (iron) ForgedSilver else ForgedInk),
                    shadow = if (iron) androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 2f), 4f)
                             else androidx.compose.ui.graphics.Shadow(Color(0x80FFE7B0), androidx.compose.ui.geometry.Offset(0f, 1.5f), 0.5f)
                )
            )
        }
    }
}
