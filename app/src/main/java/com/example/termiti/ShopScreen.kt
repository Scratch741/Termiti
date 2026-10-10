package com.example.termiti

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí – stejné jako main menu ───────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

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
                Column(
                    modifier            = Modifier.width(centerW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.012f)
                ) {
                    // Titulek + oddělovač (stejný jako v lobby multiplayeru)
                    CampaignTitle(LocalStrings.current.shopPacks, fontSize = 28.sp)
                    Image(
                        painter            = painterResource(R.drawable.bg_separator),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxWidth(),
                        contentScale       = ContentScale.FillWidth
                    )

                    // Police s neotevřenými balíčky – klepnutím se jde otevírat
                    PackShelf(
                        packs  = packs,
                        size   = H * 0.32f,
                        onOpen = { if (packs > 0) { SoundManager.playMenuTap(); opening = true } }
                    )

                    // Hlavní tlačítko: otevřít ze zásoby, nebo (bez zásoby) koupit jeden a rovnou otevírat
                    if (packs > 0) {
                        MenuButton(
                            label    = s.shopOpenPack.format(packs),
                            accent   = ShGold,
                            imageRes = R.drawable.button_7,
                            onClick  = { opening = true }
                        )
                    } else {
                        MenuButton(
                            label    = "$cost   ${s.shopBuyPack}",
                            accent   = if (canAfford) ShGold else ShMuted,
                            imageRes = R.drawable.button_7,
                            leadingIconRes = R.drawable.goldcoin_icon,
                            enabled  = canAfford,
                            onClick  = { pendingBuy = 1 to true }
                        )
                    }

                    // Nákup do zásoby: 1 / 5 / 10 balíčků
                    Row(
                        modifier              = Modifier.fillMaxWidth(0.9f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 5, 10).forEach { n ->
                            val can = gold >= n * cost
                            PlainButtonWithIcon(
                                text      = s.shopBuyN.format(n, n * cost),
                                iconRes   = R.drawable.goldcoin_icon,
                                modifier  = Modifier.weight(1f).height(30.dp),
                                textColor = if (can) ShGold else ShMuted,
                                fontSize  = 10.sp,
                                enabled   = can,
                                paddingH  = 4.dp,
                                paddingV  = 0.dp,
                                onClick   = { pendingBuy = n to false }
                            )
                        }
                    }

                    // Šance na vzácnost + záruka legendární
                    Row(
                        modifier              = Modifier.fillMaxWidth(0.9f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Rarity.entries.forEach { r ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                RarityGem(r, 11.dp)
                                Text("${r.packWeight} %", color = shRarityColor(r), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val untilPity = (CardCollectionManager.PITY_PACKS - (profile?.packsSinceLegendary ?: 0))
                        .coerceIn(1, CardCollectionManager.PITY_PACKS)
                    Text(
                        if (untilPity <= 1) s.shopPityNext else "${s.shopPackInfo}  •  ${s.shopPity.format(untilPity)}",
                        color = if (untilPity <= 1) ShGold else ShMuted, fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
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
                        IconMenuButton(imageRes = R.drawable.button_3, label = LocalStrings.current.shop, size = iconSize, onClick = {})
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.shopBuyConfirmCount.format(count), color = ShText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(15.dp))
                Text("$price", color = ShGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(s.shopBuyConfirmLeft.format(goldAfter), color = ShMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlainButton(
                    text      = s.cancel,
                    modifier  = Modifier.width(120.dp).height(34.dp),
                    textColor = ShMuted,
                    fontSize  = 11.sp,
                    paddingH  = 6.dp,
                    paddingV  = 0.dp,
                    onClick   = onCancel
                )
                PlainButton(
                    text      = s.shopBuy,
                    modifier  = Modifier.width(120.dp).height(34.dp),
                    textColor = ShGold,
                    fontSize  = 11.sp,
                    paddingH  = 6.dp,
                    paddingV  = 0.dp,
                    onClick   = onConfirm
                )
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
            .size(width = size * 1.9f, height = size)
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
                    alpha  = if (packs > 0) 1f else 0.30f
                }
            )
        }
        if (packs > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = size * 0.22f)
                    .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
                    .size(width = 46.dp, height = 24.dp)
                    .buttonTexture(R.drawable.plain_button),
                contentAlignment = Alignment.Center
            ) {
                Text("× $packs", color = ShGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                s.shopNoPacks, color = ShMuted, fontSize = 10.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
