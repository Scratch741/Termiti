package com.example.termiti

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────────────────────────
private val ShBgDeep  = Color(0xFF09070D)
private val ShBgPanel = Color(0xFF13101A)
private val ShBgCard  = Color(0xFF1A1320)
private val ShGold    = Color(0xFFD4A843)
private val ShTeal    = Color(0xFF3DBFAD)
private val ShText    = Color(0xFFEDE0C4)
private val ShMuted   = Color(0xFF7A6E5F)
private val ShGreen   = Color(0xFF4CAF50)
private val ShDust    = Color(0xFFB39DDB)

private fun shRarityColor(r: Rarity) = when (r) {
    Rarity.COMMON    -> Color(0xFF9E9E9E)
    Rarity.RARE      -> Color(0xFF4A90D9)
    Rarity.EPIC      -> Color(0xFF9B59B6)
    Rarity.LEGENDARY -> Color(0xFFD4A843)
}
private fun shEffectIcon(card: Card) = when (card.effects.firstOrNull()) {
    is CardEffect.AttackPlayer,
    is CardEffect.AttackCastle,
    is CardEffect.AttackWall,
    is CardEffect.XScaledAttackPlayer,
    is CardEffect.XScaledAttackCastle  -> "⚔️"
    is CardEffect.BuildCastle,
    is CardEffect.XScaledBuildCastle   -> "🏰"
    is CardEffect.BuildWall            -> "🧱"
    is CardEffect.AddResource,
    is CardEffect.XScaledDualResource  -> "💰"
    is CardEffect.AddMine              -> "⛏️"
    is CardEffect.StealResource        -> "🗡️"
    is CardEffect.DrainResource        -> "☠️"
    is CardEffect.DestroyMine          -> "💥"
    is CardEffect.StealCard            -> "🃏"
    is CardEffect.BurnCard             -> "🔥"
    is CardEffect.DrawCard             -> "🎴"
    is CardEffect.ConditionalEffect    -> "🔮"
    is CardEffect.BlockMine            -> "🚫"
    is CardEffect.StealCastle          -> "🧛"
    is CardEffect.AddResourceDelayed   -> "⏳"
    is CardEffect.AddCardsToDeck       -> "📦"
    else                               -> "❓"
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShopScreen(allCards: List<Card>, onBack: () -> Unit) {
    var profile     by remember { mutableStateOf(PlayerProfileManager.profile) }
    var pendingPack by remember { mutableStateOf<PackResult?>(null) }

    val gold      = profile?.gold ?: 0
    val dust      = profile?.dust ?: 0
    val canAfford = gold >= CardCollectionManager.PACK_COST_GOLD

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ShBgDeep, ShBgPanel, ShBgDeep)))
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().background(ShBgPanel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(ShBgCard)
                        .border(1.dp, ShMuted.copy(alpha = 0.3f), RoundedCornerShape(7.dp))
                        .clickable { SoundManager.playMenuTap(); onBack() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("← Zpět", color = ShMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "📦  BALÍČKY",
                    color = ShGold, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪙 $gold", color = ShGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("✨ $dust", color = ShDust, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = ShGold.copy(alpha = 0.12f))

            // ── Obsah: info vlevo, nákup vpravo ──────────────────────────────
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // ── Levý panel: info o balíčku ────────────────────────────
                    Column(
                        Modifier
                            .width(240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ShBgCard)
                            .border(1.5.dp, ShGold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🃏", fontSize = 40.sp)
                        Text(
                            "BALÍČEK KARET",
                            color = ShGold, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                        )
                        HorizontalDivider(color = ShGold.copy(alpha = 0.15f))
                        PackInfoRow("🃏", "5 karet na balíček")
                        PackInfoRow("⭐", "1× vzácná nebo lepší (garantovaná)")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Šance z náhodných slotů",
                            color = ShMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Rarity.entries.forEach { r ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(shRarityColor(r)))
                                    Text("${r.packWeight} %", color = shRarityColor(r), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(r.label, color = ShMuted, fontSize = 7.sp)
                                }
                            }
                        }
                    }

                    // ── Pravý panel: nákup ────────────────────────────────────
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.width(200.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canAfford) ShGold.copy(alpha = 0.14f) else ShMuted.copy(alpha = 0.07f))
                                .border(2.dp, if (canAfford) ShGold.copy(alpha = 0.75f) else ShMuted.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .then(
                                    if (canAfford) Modifier.clickable {
                                        SoundManager.playMenuTap()
                                        val result = CardCollectionManager.openPack(allCards)
                                        if (result != null) {
                                            pendingPack = result
                                            profile = PlayerProfileManager.profile
                                        }
                                    } else Modifier
                                )
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "🪙 ${CardCollectionManager.PACK_COST_GOLD}",
                                    color = if (canAfford) ShGold else ShMuted,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "KOUPIT BALÍČEK",
                                    color = if (canAfford) ShText else ShMuted,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        Text(
                            if (canAfford)
                                "Můžeš si koupit ${gold / CardCollectionManager.PACK_COST_GOLD}× balíček"
                            else
                                "Zlato získáš vítězstvím v bitvě",
                            color = ShMuted, fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
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

            if (!allRevealed) {
                Text("Klepni na kartu pro odkrytí", color = ShMuted, fontSize = 11.sp)
            } else {
                if (result.totalDustGained > 0) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShDust.copy(alpha = 0.10f))
                            .border(1.dp, ShDust.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💨", fontSize = 16.sp)
                        Text(
                            "Duplikáty → +${result.totalDustGained} ✨ prachu",
                            color = ShDust, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShGreen.copy(alpha = 0.12f))
                        .border(1.dp, ShGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { SoundManager.playMenuTap(); onDismiss() }
                        .padding(horizontal = 32.dp, vertical = 10.dp),
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

// ── Flip animace jedné karty z balíčku ────────────────────────────────────────

@Composable
private fun FlippablePackCard(gain: CardGain, isRevealed: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue   = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label         = "cardFlip"
    )
    val rc = shRarityColor(gain.card.rarity)

    Box(
        Modifier
            .size(width = 88.dp, height = 123.dp)
            .graphicsLayer {
                rotationY      = rotation
                cameraDistance = 8f * density
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isRevealed) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0D0A1A))))
                    .border(1.5.dp, Color(0xFF4A3070).copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🃏", fontSize = 28.sp)
                    Text("?", color = Color(0xFF6A4A90), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(
                Modifier
                    .graphicsLayer { rotationY = 180f }
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(ShBgCard, Color(0xFF0F0C18))))
                    .border(
                        2.dp,
                        if (gain.isDuplicate) ShMuted.copy(alpha = 0.5f) else rc,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(rc))
                    Text(shEffectIcon(gain.card), fontSize = 22.sp)
                    Text(
                        gain.card.name,
                        color = if (gain.isDuplicate) ShMuted else ShText,
                        fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                    Text(gain.card.rarity.label, color = rc, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    if (gain.isDuplicate) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(ShMuted.copy(alpha = 0.15f))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💨 +${gain.dustGained}✨", color = ShMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun PackInfoRow(icon: String, text: String, color: Color = ShMuted) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 11.sp)
        Text(text, color = color, fontSize = 9.sp)
    }
}
