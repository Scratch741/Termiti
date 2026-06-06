package com.example.termiti

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
    var profile     by remember { mutableStateOf(PlayerProfileManager.profile) }
    var pendingPack by remember { mutableStateOf<PackResult?>(null) }

    val gold      = profile?.gold ?: 0
    val dust      = profile?.dust ?: 0
    val canAfford = gold >= CardCollectionManager.PACK_COST_GOLD

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
                    // Titulek
                    Image(
                        painter            = painterResource(R.drawable.deck_logo),
                        contentDescription = null,
                        modifier           = Modifier.size(H * 0.12f),
                        contentScale       = ContentScale.Fit
                    )
                    Text(
                        "BALÍČKY",
                        color = ShGold, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 4.sp
                    )

                    Spacer(Modifier.height(H * 0.01f))

                    // Info o balíčku
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("5 karet • 1× vzácná nebo lepší garantována",
                            color = ShMuted, fontSize = 10.sp, textAlign = TextAlign.Center)

                        // Rarity šance
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Rarity.entries.forEach { r ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(shRarityColor(r)))
                                    Text("${r.packWeight} %", color = shRarityColor(r), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(r.label, color = ShMuted, fontSize = 8.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(H * 0.01f))

                    // Tlačítko koupit
                    MenuButton(
                        label    = "🪙 ${CardCollectionManager.PACK_COST_GOLD}   KOUPIT BALÍČEK",
                        accent   = if (canAfford) ShGold else ShMuted,
                        imageRes = R.drawable.button_7,
                        enabled  = canAfford,
                        onClick  = {
                            SoundManager.playMenuTap()
                            val result = CardCollectionManager.openPack(allCards)
                            if (result != null) {
                                pendingPack = result
                                profile = PlayerProfileManager.profile
                            }
                        }
                    )

                    // Pomocný text
                    Text(
                        if (canAfford)
                            "Můžeš koupit ${gold / CardCollectionManager.PACK_COST_GOLD}× balíček"
                        else
                            "Zlato získáš vítězstvím v bitvě",
                        color = ShMuted, fontSize = 9.sp,
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
                        IconMenuButton(imageRes = R.drawable.button_7, label = LocalStrings.current.shop, size = iconSize, onClick = {})
                    }
                    Box(Modifier.graphicsLayer { alpha = 0f }) {
                        IconMenuButton(imageRes = R.drawable.button_5, label = LocalStrings.current.settings, size = iconSize, onClick = {})
                    }
                    IconMenuButton(imageRes = R.drawable.button_6, label = LocalStrings.current.back.removePrefix("← "), size = iconSize, onClick = { onBack() })
                }
            }
        }

        // ── Overlay otevírání balíčku ─────────────────────────────────────────
        if (pendingPack != null) {
            PackOpeningOverlay(
                result    = pendingPack!!,
                onDismiss = { pendingPack = null }
            )
        }
    }
}

// ── Overlay: otevírání balíčku ────────────────────────────────────────────────

@Composable
private fun PackOpeningOverlay(result: PackResult, onDismiss: () -> Unit) {
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    val allRevealed = revealed.size == result.cards.size

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "📦  BALÍČEK OTEVŘEN!",
                color = ShGold, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                result.cards.forEachIndexed { i, gain ->
                    FlippablePackCard(
                        gain       = gain,
                        isRevealed = i in revealed,
                        onClick    = { if (i !in revealed) revealed = revealed + i }
                    )
                }
            }

            Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                if (!allRevealed) {
                    Text("Klepni na kartu pro odkrytí", color = ShMuted, fontSize = 11.sp)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (result.totalDustGained > 0) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ShDust.copy(alpha = 0.10f))
                                    .border(1.dp, ShDust.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💨", fontSize = 13.sp)
                                Text(
                                    "Duplikáty → +${result.totalDustGained} ✨ prachu",
                                    color = ShDust, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ShGreen.copy(alpha = 0.12f))
                                .border(1.dp, ShGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { SoundManager.playMenuTap(); onDismiss() }
                                .padding(horizontal = 28.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✓  DOKONČIT",
                                color = ShGreen, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Flip animace jedné karty z balíčku ────────────────────────────────────────

@Composable
private fun FlippablePackCard(gain: CardGain, isRevealed: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue   = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label         = "cardFlip"
    )

    var isHolding by remember { mutableStateOf(false) }
    val glowColor = if (gain.card.rarity == Rarity.COMMON) Color(0xFFCFCFCF) else shRarityColor(gain.card.rarity)
    val showGlow  = isRevealed || isHolding

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.30f,
        targetValue   = 0.80f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(Modifier.size(width = 130.dp, height = 170.dp), contentAlignment = Alignment.Center) {
        if (showGlow) {
            Box(Modifier.size(width = 124.dp, height = 164.dp).clip(RoundedCornerShape(20.dp)).background(glowColor.copy(alpha = glowAlpha * 0.18f)))
            Box(Modifier.size(width = 116.dp, height = 156.dp).clip(RoundedCornerShape(15.dp)).background(glowColor.copy(alpha = glowAlpha * 0.35f)))
            Box(Modifier.size(width = 108.dp, height = 148.dp).clip(RoundedCornerShape(11.dp)).background(glowColor.copy(alpha = glowAlpha * 0.55f)))
        }
        Box(
            Modifier
                .size(width = 100.dp, height = 140.dp)
                .graphicsLayer { rotationY = rotation; cameraDistance = 8f * density }
                .pointerInput(isRevealed) {
                    if (!isRevealed) {
                        detectTapGestures(
                            onPress = { isHolding = true; tryAwaitRelease(); isHolding = false },
                            onTap   = { onClick() }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                Image(
                    painter            = painterResource(playerCardBackResId()),
                    contentDescription = null,
                    modifier           = Modifier.size(width = 100.dp, height = 140.dp),
                    contentScale       = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.TopCenter) {
                    CardPreview(card = gain.card)
                    if (gain.isDuplicate) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💨 +${gain.dustGained} ✨", color = ShMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
