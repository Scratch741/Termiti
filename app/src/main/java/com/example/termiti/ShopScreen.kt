package com.example.termiti

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
private val ShRed     = Color(0xFFE57373)

private fun shResColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> Color(0xFF9B59B6)
    ResourceType.ATTACK -> Color(0xFFBF2D2D)
    ResourceType.STONES -> Color(0xFFB8A898)
    ResourceType.CHAOS  -> Color(0xFFE67E22)
}
private fun shResIcon(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> "✨"
    ResourceType.ATTACK -> "⚔️"
    ResourceType.STONES -> "🪨"
    ResourceType.CHAOS  -> "🌀"
}
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

private enum class ShopTab { COLLECTION, CRAFT }

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShopScreen(allCards: List<Card>, onBack: () -> Unit) {
    var profile     by remember { mutableStateOf(PlayerProfileManager.profile) }
    var selectedTab by remember { mutableStateOf(ShopTab.COLLECTION) }
    var pendingPack by remember { mutableStateOf<PackResult?>(null) }
    var filterRes   by remember { mutableStateOf<ResourceType?>(null) }

    // Seřazené karty: legendární → epické → vzácné → běžné; pak dle ceny
    val sortedCards = remember(allCards) {
        allCards.sortedWith(
            compareByDescending<Card> { it.rarity.ordinal }
                .thenBy  { it.cost }
                .thenBy  { it.name }
        )
    }

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
                    "🃏  SBÍRKA & OBCHOD",
                    color = ShGold, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                // Měny
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪙 ${profile?.gold ?: 0}", color = ShGold,  fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("✨ ${profile?.dust ?: 0}", color = ShDust,  fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = ShGold.copy(alpha = 0.12f))

            // ── Obsah ──────────────────────────────────────────────────────────
            Row(Modifier.fillMaxSize()) {

                // ── Levý panel: Balíček ───────────────────────────────────────
                Column(
                    Modifier.width(210.dp).fillMaxHeight()
                        .background(ShBgPanel.copy(alpha = 0.6f))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PackShopPanel(
                        gold = profile?.gold ?: 0,
                        onOpenPack = {
                            val result = CardCollectionManager.openPack(allCards)
                            if (result != null) {
                                pendingPack = result
                                profile = PlayerProfileManager.profile
                            }
                        }
                    )
                }

                VerticalDivider(color = ShGold.copy(alpha = 0.15f))

                // ── Pravý panel: Kolekce / Výroba ─────────────────────────────
                Column(Modifier.weight(1f).fillMaxHeight()) {

                    // Záložky + filtry
                    Row(
                        Modifier.fillMaxWidth().background(ShBgPanel)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShTabChip("📚 Kolekce", selectedTab == ShopTab.COLLECTION) {
                            selectedTab = ShopTab.COLLECTION
                        }
                        ShTabChip("🔨 Výroba",  selectedTab == ShopTab.CRAFT) {
                            selectedTab = ShopTab.CRAFT
                        }
                        Spacer(Modifier.width(10.dp))
                        VerticalDivider(Modifier.height(18.dp), color = ShMuted.copy(alpha = 0.3f))
                        Spacer(Modifier.width(6.dp))
                        Text("Zdroj:", color = ShMuted, fontSize = 9.sp)
                        ResourceType.entries.forEach { res ->
                            ShFilterChip(shResIcon(res), filterRes == res, shResColor(res)) {
                                filterRes = if (filterRes == res) null else res
                            }
                        }
                    }

                    HorizontalDivider(color = ShGold.copy(alpha = 0.1f))

                    val allUnlocked = profile?.allCardsUnlocked ?: true

                    val filteredCards = remember(sortedCards, filterRes, selectedTab, allUnlocked) {
                        sortedCards.filter { filterRes == null || it.costType == filterRes }
                            .let { base ->
                                when (selectedTab) {
                                    ShopTab.COLLECTION -> base
                                    ShopTab.CRAFT      -> base.filter { card ->
                                        !CardCollectionManager.isBasicCard(card) &&
                                        CardCollectionManager.ownedCopies(card.id) < card.rarity.maxCopies
                                    }
                                }
                            }
                    }

                    when (selectedTab) {
                        ShopTab.COLLECTION -> CollectionGrid(
                            cards       = filteredCards,
                            allUnlocked = allUnlocked,
                            onDismantle = { cardId ->
                                CardCollectionManager.dismantleCard(cardId, allCards)
                                profile = PlayerProfileManager.profile
                            }
                        )
                        ShopTab.CRAFT -> CraftGrid(
                            cards       = filteredCards,
                            dust        = profile?.dust ?: 0,
                            allUnlocked = allUnlocked,
                            onCraft     = { cardId ->
                                CardCollectionManager.craftCard(cardId, allCards)
                                profile = PlayerProfileManager.profile
                            }
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

// ── Levý panel: Obchod s balíčky ─────────────────────────────────────────────

@Composable
private fun PackShopPanel(gold: Int, onOpenPack: () -> Unit) {
    val canAfford = gold >= CardCollectionManager.PACK_COST_GOLD

    // Nadpis
    Text(
        "📦  BALÍČEK",
        color = ShGold, fontSize = 13.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
    )

    // Info karta
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ShBgCard)
            .border(1.dp, ShGold.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        PackInfoRow("🃏", "5 karet")
        PackInfoRow("⭐", "1× vzácná nebo lepší")
        Spacer(Modifier.height(2.dp))
        Text("Šance z náhodných slotů:", color = ShMuted, fontSize = 8.sp)
        PackInfoRow("⚪", "Běžná   70 %",  shRarityColor(Rarity.COMMON))
        PackInfoRow("🔵", "Vzácná  22 %",  shRarityColor(Rarity.RARE))
        PackInfoRow("🟣", "Epická   6 %",  shRarityColor(Rarity.EPIC))
        PackInfoRow("🟡", "Legen.   2 %",  shRarityColor(Rarity.LEGENDARY))
    }

    Spacer(Modifier.height(4.dp))

    // Cena a tlačítko
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (canAfford) ShGold.copy(alpha = 0.12f)
                else ShMuted.copy(alpha = 0.07f)
            )
            .border(
                1.5.dp,
                if (canAfford) ShGold.copy(alpha = 0.65f)
                else ShMuted.copy(alpha = 0.25f),
                RoundedCornerShape(10.dp)
            )
            .then(if (canAfford) Modifier.clickable { SoundManager.playMenuTap(); onOpenPack() } else Modifier)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "🪙 ${CardCollectionManager.PACK_COST_GOLD}",
                color = if (canAfford) ShGold else ShMuted,
                fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(
                if (canAfford) "OTEVŘÍT BALÍČEK" else "Nedostatek zlata",
                color = if (canAfford) ShText else ShMuted,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }

    // Prach info
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = ShMuted.copy(alpha = 0.2f))
    Spacer(Modifier.height(4.dp))
    Text("VÝROBA (z prachu)", color = ShDust, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Rarity.entries.filter { it != Rarity.COMMON }.forEach { r ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(r.label, color = shRarityColor(r), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text("✨ ${r.craftCost}", color = ShDust, fontSize = 8.sp)
        }
    }
    Spacer(Modifier.height(2.dp))
    Text("ROZEBRAT (za prach)", color = ShMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Rarity.entries.filter { it != Rarity.COMMON }.forEach { r ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(r.label, color = shRarityColor(r), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text("✨ ${r.dustValue}", color = ShMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun PackInfoRow(icon: String, text: String, color: Color = ShMuted) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 10.sp)
        Text(text, color = color, fontSize = 9.sp)
    }
}

// ── Kolekce: grid všech karet ─────────────────────────────────────────────────

@Composable
private fun CollectionGrid(
    cards: List<Card>,
    allUnlocked: Boolean,
    onDismantle: (String) -> Unit
) {
    if (cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Žádné karty dle filtru.", color = ShMuted, fontSize = 12.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(cards, key = { it.id }) { card ->
            val isBasic   = CardCollectionManager.isBasicCard(card)
            val owned     = if (allUnlocked || isBasic) card.rarity.maxCopies
                            else CardCollectionManager.ownedCopies(card.id)
            val maxCopies = card.rarity.maxCopies
            val isLocked  = !allUnlocked && !isBasic && owned == 0

            CollectionCardTile(
                card      = card,
                owned     = owned,
                maxCopies = maxCopies,
                isBasic   = isBasic,
                isLocked  = isLocked,
                onDismantle = if (!isBasic && !allUnlocked && owned > 0)
                    { { onDismantle(card.id) } } else null
            )
        }
    }
}

@Composable
private fun CollectionCardTile(
    card: Card,
    owned: Int,
    maxCopies: Int,
    isBasic: Boolean,
    isLocked: Boolean,
    onDismantle: (() -> Unit)?
) {
    val rc = shRarityColor(card.rarity)
    val borderColor = when {
        isLocked       -> ShMuted.copy(alpha = 0.15f)
        owned >= maxCopies -> rc.copy(alpha = 0.7f)
        else               -> rc.copy(alpha = 0.4f)
    }

    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ShBgCard)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .alpha(if (isLocked) 0.45f else 1f)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Rarity + resource
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(rc)
            )
            Row(
                Modifier.clip(RoundedCornerShape(3.dp))
                    .background(shResColor(card.costType).copy(alpha = 0.15f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shResIcon(card.costType), fontSize = 7.sp)
                Text(
                    if (card.isXCost) "X" else "${card.cost}",
                    color = shResColor(card.costType), fontSize = 8.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // Ikona efektu
        Text(shEffectIcon(card), fontSize = 20.sp)

        // Název
        Text(
            card.name,
            color = if (isLocked) ShMuted else ShText,
            fontSize = 8.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 10.sp
        )

        // Badge: Základní / kopie
        if (isBasic) {
            Text(
                "⚪ Základní",
                color = ShMuted, fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                if (isLocked) "🔒  0/$maxCopies" else "$owned/$maxCopies",
                color = when {
                    isLocked       -> ShMuted
                    owned >= maxCopies -> ShGreen
                    else               -> ShTeal
                },
                fontSize = 8.sp, fontWeight = FontWeight.Bold
            )
        }

        // Rozebrat tlačítko
        if (onDismantle != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ShRed.copy(alpha = 0.10f))
                    .border(1.dp, ShRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { SoundManager.playMenuTap(); onDismantle() }
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🔨 +${card.rarity.dustValue}✨",
                    color = ShRed.copy(alpha = 0.85f), fontSize = 7.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Výroba: grid craftovatelných karet ────────────────────────────────────────

@Composable
private fun CraftGrid(
    cards: List<Card>,
    dust: Int,
    allUnlocked: Boolean,
    onCraft: (String) -> Unit
) {
    if (allUnlocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🃏", fontSize = 32.sp)
                Text(
                    "Odemčení všech karet je zapnuto.",
                    color = ShGold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Výroba je dostupná pouze v sběratelském režimu.\nVypni přepínač v Profilu → DEBUG.",
                    color = ShMuted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, lineHeight = 15.sp
                )
            }
        }
        return
    }
    if (cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✅", fontSize = 32.sp)
                Text(
                    "Máš všechny dostupné sběratelské karty!",
                    color = ShGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(cards, key = { it.id }) { card ->
            val owned      = CardCollectionManager.ownedCopies(card.id)
            val canAfford  = dust >= card.rarity.craftCost
            CraftCardTile(
                card      = card,
                owned     = owned,
                dust      = dust,
                canAfford = canAfford,
                onCraft   = { onCraft(card.id) }
            )
        }
    }
}

@Composable
private fun CraftCardTile(
    card: Card,
    owned: Int,
    dust: Int,
    canAfford: Boolean,
    onCraft: () -> Unit
) {
    val rc = shRarityColor(card.rarity)
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ShBgCard)
            .border(1.5.dp, rc.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Rarity + resource (stejné jako v collection)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(rc))
            Row(
                Modifier.clip(RoundedCornerShape(3.dp))
                    .background(shResColor(card.costType).copy(alpha = 0.15f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shResIcon(card.costType), fontSize = 7.sp)
                Text(
                    if (card.isXCost) "X" else "${card.cost}",
                    color = shResColor(card.costType), fontSize = 8.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(shEffectIcon(card), fontSize = 20.sp)
        Text(
            card.name, color = ShText, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 10.sp
        )
        Text(
            "$owned/${card.rarity.maxCopies}",
            color = ShTeal, fontSize = 8.sp, fontWeight = FontWeight.Bold
        )
        // Cena craftu
        Text(
            "✨ ${card.rarity.craftCost}",
            color = if (canAfford) ShDust else ShMuted,
            fontSize = 9.sp, fontWeight = FontWeight.Bold
        )
        // Tlačítko Vyrobit
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (canAfford) ShDust.copy(alpha = 0.12f) else ShMuted.copy(alpha = 0.07f))
                .border(1.dp, if (canAfford) ShDust.copy(alpha = 0.5f) else ShMuted.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .then(if (canAfford) Modifier.clickable { SoundManager.playMenuTap(); onCraft() } else Modifier)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "VYROBIT",
                color = if (canAfford) ShDust else ShMuted,
                fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
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
            .clickable(enabled = false) { /* pohlcení kliků pod overlayem */ },
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

            // 5 karet
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
                Text(
                    "Klepni na kartu pro odkrytí",
                    color = ShMuted, fontSize = 11.sp
                )
            } else {
                // Souhrn prachu
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
        targetValue    = if (isRevealed) 180f else 0f,
        animationSpec  = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label          = "cardFlip"
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
            // Rubová strana
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0D0A1A)))
                    )
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
            // Lícová strana — zrcadlová korekce
            Box(
                Modifier
                    .graphicsLayer { rotationY = 180f }
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(ShBgCard, Color(0xFF0F0C18)))
                    )
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
                    // Rarity tečka
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(rc))

                    // Ikona efektu
                    Text(shEffectIcon(gain.card), fontSize = 22.sp)

                    // Název
                    Text(
                        gain.card.name,
                        color = if (gain.isDuplicate) ShMuted else ShText,
                        fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )

                    // Rarita
                    Text(
                        gain.card.rarity.label,
                        color = rc, fontSize = 7.sp, fontWeight = FontWeight.Bold
                    )

                    // Duplikát badge
                    if (gain.isDuplicate) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(ShMuted.copy(alpha = 0.15f))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "💨 +${gain.dustGained}✨",
                                color = ShMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── UI helpers ────────────────────────────────────────────────────────────────

@Composable
private fun ShTabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) ShGold.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (active) ShGold.copy(alpha = 0.55f) else ShMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) ShGold else ShMuted,
            fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ShFilterChip(icon: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (active) color.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.09f), RoundedCornerShape(5.dp))
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(icon, fontSize = 12.sp)
    }
}
