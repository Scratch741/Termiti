package com.example.termiti

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Palette (local copy) ────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0D0A0E)
private val BgCard      = Color(0xFF1A1320)
private val BgPanel     = Color(0xFF13101A)
private val Gold        = Color(0xFFD4A843)
private val Crimson     = Color(0xFFBF2D2D)
private val Teal        = Color(0xFF2A7A6F)
private val TealLight   = Color(0xFF3DBFAD)
private val HpGreen     = Color(0xFF4CAF50)
private val TextPrimary = Color(0xFFEDE0C4)
private val TextMuted   = Color(0xFF7A6E5F)
private val MagicPurple  = Color(0xFF9B59B6)
private val StoneColor   = Color(0xFFB8A898)
private val AttackRed    = Color(0xFFBF2D2D)
private val ChaosOrange  = Color(0xFFE67E22)

// ─── Helpers ─────────────────────────────────────────────────────────────────
private fun cardFrameName(costType: ResourceType) = when (costType) {
    ResourceType.MAGIC  -> "card_frame_magic"
    ResourceType.ATTACK -> "card_frame_attack"
    ResourceType.CHAOS  -> "card_frame_chaos"
    ResourceType.STONES -> "card_frame_stones"
}

private fun resColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> MagicPurple
    ResourceType.ATTACK -> AttackRed
    ResourceType.STONES -> StoneColor
    ResourceType.CHAOS  -> ChaosOrange
}

private fun rarityColor(rarity: Rarity) = when (rarity) {
    Rarity.COMMON    -> Color(0xFF9E9E9E)
    Rarity.RARE      -> Color(0xFF4A90D9)
    Rarity.EPIC      -> Color(0xFF9B59B6)
    Rarity.LEGENDARY -> Color(0xFFD4A843)
}

private fun resIcon(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> "✨"
    ResourceType.ATTACK -> "⚔️"
    ResourceType.STONES -> "🪨"
    ResourceType.CHAOS  -> "🌀"
}

private fun effectIcon(card: Card) = when (card.effects.firstOrNull()) {
    is CardEffect.AttackPlayer      -> "⚔️"
    is CardEffect.AttackCastle      -> "🎯"
    is CardEffect.AttackWall        -> "💣"
    is CardEffect.BuildCastle       -> "🏰"
    is CardEffect.BuildWall         -> "🧱"
    is CardEffect.AddResource       -> "💰"
    is CardEffect.AddMine           -> "⛏️"
    is CardEffect.StealResource     -> "🗡️"
    is CardEffect.DrainResource     -> "☠️"
    is CardEffect.ConditionalEffect -> "🔮"
    is CardEffect.DestroyMine       -> "💥"
    is CardEffect.StealCard         -> "🃏"
    is CardEffect.BurnCard          -> "🔥"
    is CardEffect.AddCardsToDeck    -> "📦"
    is CardEffect.DrawCard          -> "🎴"
    is CardEffect.StealCastle        -> "🧛"
    is CardEffect.AddResourceDelayed  -> "⏳"
    is CardEffect.BlockMine           -> "🚫"
    is CardEffect.XScaledAttackPlayer -> "⚔️"
    is CardEffect.XScaledAttackCastle -> "🎯"
    is CardEffect.XScaledBuildCastle  -> "🏰"
    is CardEffect.XScaledDualResource -> "💰"
    null                              -> "❓"
}

private fun Card.category() = when (effects.firstOrNull()) {
    is CardEffect.AttackPlayer,
    is CardEffect.AttackCastle,
    is CardEffect.AttackWall,
    is CardEffect.StealResource,
    is CardEffect.DrainResource,
    is CardEffect.ConditionalEffect -> "Útok"
    is CardEffect.BuildCastle,
    is CardEffect.BuildWall         -> "Obrana"
    is CardEffect.AddResource       -> "Zdroje"
    is CardEffect.AddMine           -> "Doly"
    else                            -> "Ostatní"
}

// ─── Root ────────────────────────────────────────────────────────────────────
@Composable
fun DeckBuilderScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val decks          = viewModel.decks
    val activeDeckIdx  by viewModel.activeDeckIndex

    var editingIdx     by remember { mutableIntStateOf(activeDeckIdx) }
    val editingDeck    = decks[editingIdx]

    var previewCard    by remember { mutableStateOf<Card?>(null) }

    // Filter state
    var filterRes      by remember { mutableStateOf<ResourceType?>(null) }
    var filterCat      by remember { mutableStateOf<String?>(null) }

    val filteredCards  = remember(filterRes, filterCat) {
        viewModel.allCards
            .filter { card ->
                (filterRes == null || card.costType == filterRes) &&
                (filterCat == null || card.category() == filterCat)
            }
            .sortedWith(compareBy({ it.cost }, { it.costType.ordinal }, { it.name }))
    }

    Box(Modifier.fillMaxSize().background(BgDeep)) {
        Row(Modifier.fillMaxSize()) {

            // ── Left: catalog starts immediately at top ───────────────
            Column(Modifier.weight(3f).fillMaxHeight()) {
                FilterBar(
                    filterRes   = filterRes,
                    filterCat   = filterCat,
                    onResFilter = { filterRes = if (filterRes == it) null else it },
                    onCatFilter = { filterCat = if (filterCat == it) null else it }
                )
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        val count  = editingDeck.cardCounts[card.id] ?: 0
                        val isFull = editingDeck.totalCards >= 30
                        CatalogCardItem(
                            card        = card,
                            count       = count,
                            deckFull    = isFull,
                            onIncrement = {
                                if (count < card.rarity.maxCopies && !isFull)
                                    viewModel.setCardCount(editingIdx, card.id, count + 1)
                            },
                            onDecrement = {
                                if (count > 0)
                                    viewModel.setCardCount(editingIdx, card.id, count - 1)
                            },
                            onPreview = { previewCard = card }
                        )
                    }
                }
            }

            VerticalDivider(color = Gold.copy(alpha = 0.2f))

            // ── Right: top bar + deck panel ───────────────────────────
            Column(Modifier.weight(2f).fillMaxHeight()) {
                TopBar(
                    decks         = decks,
                    activeDeckIdx = activeDeckIdx,
                    editingIdx    = editingIdx,
                    onSelectDeck  = { editingIdx = it },
                    onBack        = onBack
                )
                HorizontalDivider(color = Gold.copy(alpha = 0.2f))
                DeckPanel(
                    deck            = editingDeck,
                    allCards        = viewModel.allCards,
                    isActive        = editingIdx == activeDeckIdx,
                    presetTemplates = viewModel.presetTemplates,
                    onLoadPreset    = { viewModel.loadPreset(editingIdx, it) },
                    onClear         = { viewModel.clearDeck(editingIdx) },
                    onSetActive     = { viewModel.setActiveDeck(editingIdx) },
                    onRename        = { viewModel.renameDeck(editingIdx, it) },
                    onRemove        = { cardId ->
                        val c = editingDeck.cardCounts[cardId] ?: 0
                        if (c > 0) viewModel.setCardCount(editingIdx, cardId, c - 1)
                    },
                    modifier        = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }

        // ── Full Card Preview Overlay ─────────────────────────────────────────
        if (previewCard != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to Color(0xCC000000),
                            0.6f to Color(0xDD000000),
                            1.0f to Color(0xF0000000)
                        )
                    )
                    .clickable { previewCard = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* pohlcení kliků – nezavírá overlay */ },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    FullCardPreview(previewCard!!)
                    Text(
                        "Klepni mimo kartu pro zavření",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    decks: List<Deck>,
    activeDeckIdx: Int,
    editingIdx: Int,
    onSelectDeck: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(BgPanel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Row 1: back + title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("← Zpět", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "SESTAVIT BALÍK", color = Gold,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )
        }
        // Row 2: deck slots
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sloty:", color = TextMuted, fontSize = 8.sp)
            decks.forEachIndexed { index, deck ->
                DeckSlotChip(
                    deck      = deck,
                    isActive  = index == activeDeckIdx,
                    isEditing = index == editingIdx,
                    onClick   = { onSelectDeck(index) }
                )
            }
        }
    }
}

@Composable
private fun DeckSlotChip(
    deck: Deck,
    isActive: Boolean,
    isEditing: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isActive && isEditing -> Gold
        isActive              -> TealLight
        isEditing             -> TextPrimary.copy(alpha = 0.35f)
        else                  -> TextMuted.copy(alpha = 0.2f)
    }
    val bg = if (isEditing) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.02f)

    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                deck.name,
                color = if (isEditing) TextPrimary else TextMuted,
                fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${deck.totalCards}/30",
                    color = if (deck.isValid) HpGreen else TextMuted.copy(alpha = 0.6f),
                    fontSize = 8.sp
                )
                if (isActive) {
                    Text("✓ aktivní", color = TealLight, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Filter Bar ───────────────────────────────────────────────────────────────
@Composable
private fun FilterBar(
    filterRes: ResourceType?,
    filterCat: String?,
    onResFilter: (ResourceType) -> Unit,
    onCatFilter: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(BgPanel.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("Zdroj:", color = TextMuted, fontSize = 9.sp)
        FilterChip("✨", filterRes == ResourceType.MAGIC,  MagicPurple)  { onResFilter(ResourceType.MAGIC)  }
        FilterChip("⚔️", filterRes == ResourceType.ATTACK, AttackRed)    { onResFilter(ResourceType.ATTACK) }
        FilterChip("🪨", filterRes == ResourceType.STONES, StoneColor)   { onResFilter(ResourceType.STONES) }
        FilterChip("🌀", filterRes == ResourceType.CHAOS,  ChaosOrange)  { onResFilter(ResourceType.CHAOS)  }

        Spacer(Modifier.width(6.dp))
        VerticalDivider(modifier = Modifier.height(16.dp), color = Gold.copy(alpha = 0.2f))
        Spacer(Modifier.width(6.dp))

        Text("Efekt:", color = TextMuted, fontSize = 9.sp)
        FilterChip("⚔️ Útok",    filterCat == "Útok",   AttackRed)   { onCatFilter("Útok")   }
        FilterChip("🧱 Obrana",  filterCat == "Obrana", StoneColor)  { onCatFilter("Obrana") }
        FilterChip("💰 Zdroje",  filterCat == "Zdroje", MagicPurple) { onCatFilter("Zdroje") }
        FilterChip("⛏️ Doly",   filterCat == "Doly",   Gold)        { onCatFilter("Doly")   }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(5.dp))
            .background(if (active) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (active) color.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(5.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            color  = if (active) color else TextMuted,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Card Preview (texturovaný náhled pro deck builder) ──────────────────────
@Composable
private fun CardPreview(card: Card) {
    val artResId = card.artResId ?: return
    val context = LocalContext.current
    val frameResId = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
    }
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 140.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        // Ilustrace – 90 dp pokryje průhlednou zónu frame včetně gradient přechodu
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(90.dp)
                .clipToBounds()
        ) {
            Image(
                painter = painterResource(artResId),
                contentDescription = null,
                modifier = artModifier(card),
                contentScale = ContentScale.Crop,
                alignment = artAlignment(card)
            )
        }
        // Rám
        if (frameResId != 0) {
            Image(
                painter = painterResource(frameResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Překryv rarity
        val rarityOverlayId = rarityOverlayResource(card.rarity)
        if (rarityOverlayId != 0) {
            Image(
                painter = painterResource(rarityOverlayId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        // Cena
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 1.5.dp, y = 2.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            val costLabel = if (card.isXCost) "X" else "${card.cost}"
            val costStyle = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )
            // Černý obrys – 4 posunuté kopie (fillMaxWidth = glyf centrován v šíři boxu)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(x = (-1).dp), style = costStyle)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(x = 1.dp),  style = costStyle)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(y = (-1).dp), style = costStyle)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(y = 1.dp),  style = costStyle)
            // Bílá výplň
            Text(costLabel, color = Color.White, modifier = Modifier.fillMaxWidth(), style = costStyle)
        }
        // Název — zakřivený text sledující oblouk ribbonu
        ArcCardName(
            name         = card.name,
            modifier     = Modifier
                .align(Alignment.TopStart)
                .offset(y = 69.dp)
                .fillMaxWidth()
                .height(22.dp),
            fontSizeSp   = 8f,
            arcRadiusDp  = 350f,
            baselineFrac = 0.78f
        )
        // Popis
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 94.dp)
                .fillMaxWidth()
                .height(28.dp)
                .clipToBounds()
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(card.description, color = Color(0xFFDDD0B0), fontSize = 7.sp,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 9.sp)
        }
        // Typ
        if (card.type.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 129.dp)
                    .fillMaxWidth()
                    .height(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    card.type.uppercase(), color = Color(0xFFD4B870),
                    fontSize = 6.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}

// ─── Full Card Preview (zvětšený náhled) ──────────────────────────────────
@Composable
private fun FullCardPreview(card: Card) {
    val costColor    = resColor(card.costType)
    val rarityCol    = rarityColor(card.rarity)
    val artResId     = card.artResId
    val context      = LocalContext.current
    val frameResId   = remember(card.costType) {
        if (artResId != null)
            context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
        else 0
    }
    val rarityOverlayId = rarityOverlayResource(card.rarity)

    // Karta 252×353 dp = 2.52× reálné karty (100×140 dp) — o 10 % menší než 2.8×
    Box(
        modifier = Modifier
            .size(width = 252.dp, height = 353.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(2.dp, costColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        if (artResId != null) {
            // ── Texturovaná karta ────────────────────────────────────────────
            // Artwork box: 90dp × 2.52 = 227dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(227.dp)
                    .clipToBounds()
            ) {
                Image(
                    painter = painterResource(artResId),
                    contentDescription = null,
                    modifier = artModifier(card),
                    contentScale = ContentScale.Crop,
                    alignment = artAlignment(card)
                )
            }

            // Rám
            if (frameResId != 0) {
                Image(
                    painter = painterResource(frameResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Překryv rarity
            if (rarityOverlayId != 0) {
                Image(
                    painter = painterResource(rarityOverlayId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Cena — x = 2dp × 2.52 = 5dp, y = 2dp × 2.52 = 5dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 5.dp, y = 5.dp)
                    .size(45.dp),
                contentAlignment = Alignment.Center
            ) {
                val costStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                val costLabel = if (card.isXCost) "X" else "${card.cost}"
                // Černý obrys – 4 posunuté kopie
                Text(costLabel, color = Color.Black, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.offset(x = (-2).dp), style = costStyle)
                Text(costLabel, color = Color.Black, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.offset(x = 2.dp), style = costStyle)
                Text(costLabel, color = Color.Black, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.offset(y = (-2).dp), style = costStyle)
                Text(costLabel, color = Color.Black, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.offset(y = 2.dp), style = costStyle)
                // Bílá výplň
                Text(costLabel, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, style = costStyle)
            }

            // Název — zakřivený text (× 2.52 od malé karty)
            ArcCardName(
                name         = card.name,
                modifier     = Modifier
                    .fillMaxWidth()
                    .padding(top = 174.dp)
                    .height(55.dp),
                fontSizeSp   = 18f,
                arcRadiusDp  = 882f,   // 350 × 2.52
                baselineFrac = 0.78f
            )

            // Popis — y = 94dp × 2.52 = 237dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 237.dp, start = 22.dp, end = 22.dp)
                    .height(76.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(card.description, color = Color(0xFFDDD0B0),
                    fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
            }

            // Typ — y = 129dp × 2.521 = 325dp
            if (card.type.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 325.dp)
                        .fillMaxWidth()
                        .height(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        card.type.uppercase(), color = Color(0xFFD4B870),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }

        } else {
            // ── Klasická karta bez artworku ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(
                            costColor.copy(alpha = 0.08f),
                            BgCard
                        ))
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cena + typ zdroje
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(costColor.copy(alpha = 0.15f))
                            .border(2.dp, costColor.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (card.isXCost) "X" else "${card.cost}", color = costColor,
                            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(resIcon(card.costType), fontSize = 28.sp)
                }

                HorizontalDivider(color = costColor.copy(alpha = 0.2f))

                // Název
                Text(card.name, color = TextPrimary,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)

                // Popis
                Text(card.description, color = Color(0xFFDDD0B0),
                    fontSize = 16.sp, lineHeight = 22.sp)

                if (card.type.isNotEmpty()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    Text(card.type.uppercase(), color = Color(0xFFD4B870),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }

            }
        }
    }
}

// ─── Catalog Card Item ────────────────────────────────────────────────────────
@Composable
private fun CatalogCardItem(
    card: Card,
    count: Int,
    deckFull: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onPreview: () -> Unit
) {
    val hasAny    = count > 0
    val costColor = resColor(card.costType)
    val border    = if (hasAny) costColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f)
    val rc        = rarityColor(card.rarity)

    // Klik → náhled karty; přidání do decku přes tlačítko [+] uvnitř dlaždice
    val itemModifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(7.dp))
        .clickable { onPreview() }

    if (card.artResId != null) {
        // ── Texturovaná karta ─────────────────────────────────────────────────
        Column(
            itemModifier
                .background(Color(0xFF0F0C14))
                .border(1.5.dp, border, RoundedCornerShape(7.dp))
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Náhled karty s texturou
            CardPreview(card = card)

            // Počítadlo: [−] puntíky [+]
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountBtn("−", enabled = count > 0, onClick = onDecrement)
                Spacer(Modifier.width(4.dp))
                repeat(card.rarity.maxCopies) { i ->
                    Box(
                        Modifier.size(8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i < count) rc.copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.07f)
                            )
                    )
                    if (i < card.rarity.maxCopies - 1) Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.width(4.dp))
                CountBtn("+", enabled = count < card.rarity.maxCopies && !deckFull, onClick = onIncrement)
            }
        }
    } else {
        // ── Klasická karta (bez textury) ──────────────────────────────────────
        val bg = if (hasAny) BgCard else Color(0xFF0F0C14)
        Column(
            itemModifier
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(7.dp))
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Icon + cost
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(effectIcon(card), fontSize = 14.sp)
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(costColor.copy(alpha = 0.13f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(resIcon(card.costType), fontSize = 8.sp)
                    Text(if (card.isXCost) "X" else "${card.cost}", color = costColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Name
            Text(
                card.name,
                color = if (hasAny) TextPrimary else TextMuted,
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            // Description (1 line)
            Text(
                card.description,
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 7.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                lineHeight = 9.sp
            )

            // Count selector: [−] pips [+]
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountBtn("−", enabled = count > 0, onClick = onDecrement)
                Spacer(Modifier.width(4.dp))
                repeat(card.rarity.maxCopies) { i ->
                    Box(
                        Modifier.size(8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i < count) rc.copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.07f)
                            )
                    )
                    if (i < card.rarity.maxCopies - 1) Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.width(4.dp))
                CountBtn("+", enabled = count < card.rarity.maxCopies && !deckFull, onClick = onIncrement)
            }
        }
    }
}

@Composable
private fun CountBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (enabled) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.25f),
            fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}

// ─── Deck Panel ───────────────────────────────────────────────────────────────
@Composable
private fun DeckPanel(
    deck: Deck,
    allCards: List<Card>,
    isActive: Boolean,
    presetTemplates: List<Pair<String, Map<String, Int>>>,
    onLoadPreset: (Int) -> Unit,
    onClear: () -> Unit,
    onSetActive: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress   = (deck.totalCards / 30f).coerceIn(0f, 1f)
    val validColor = when {
        deck.isValid         -> HpGreen
        deck.totalCards > 30 -> AttackRed
        else                 -> Gold.copy(alpha = 0.7f)
    }

    val deckCards = remember(deck.cardCounts) {
        allCards
            .filter { (deck.cardCounts[it.id] ?: 0) > 0 }
            .sortedWith(compareBy({ it.costType.ordinal }, { it.cost }, { it.name }))
    }

    var isEditingName by remember(deck.id) { mutableStateOf(false) }
    var nameInput     by remember(deck.name) { mutableStateOf(deck.name) }
    val focusRequester = remember { FocusRequester() }

    // Group by resource type (calculate outside LazyColumn scope)
    val groups = remember(deckCards) { deckCards.groupBy { it.costType } }

    Column(
        modifier.background(
            Brush.verticalGradient(listOf(BgPanel, BgDeep))
        ).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditingName) {
                BasicTextField(
                    value         = nameInput,
                    onValueChange = { if (it.length <= 20) nameInput = it },
                    singleLine    = true,
                    textStyle     = androidx.compose.ui.text.TextStyle(
                        color      = TextPrimary,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onRename(nameInput)
                        isEditingName = false
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Gold.copy(alpha = 0.15f))
                        .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable { onRename(nameInput); isEditingName = false }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("✓", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(deck.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, TextMuted.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .clickable { nameInput = deck.name; isEditingName = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("✎", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
            Text(
                "${deck.totalCards} / 30",
                color = validColor, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }

        // Progress bar
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(validColor.copy(alpha = 0.75f))
            )
        }

        // Status text
        when {
            deck.isValid         -> Text("✓ Balíček je připraven ke hře", color = HpGreen,   fontSize = 9.sp)
            deck.totalCards > 30 -> Text("Příliš mnoho karet (${deck.totalCards - 30} navíc)", color = AttackRed, fontSize = 9.sp)
            else                 -> Text("Ještě ${40 - deck.totalCards} karet", color = TextMuted, fontSize = 9.sp)
        }

        // Šablony
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Šablony", color = TextMuted, fontSize = 8.sp, letterSpacing = 1.sp)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                presetTemplates.forEachIndexed { i, (name, _) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Gold.copy(alpha = 0.07f))
                            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                            .clickable { onLoadPreset(i) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(name, color = Gold.copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        HorizontalDivider(color = Gold.copy(alpha = 0.1f))

        // Card list + stats + buttons all inside one scrollable LazyColumn
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ResourceType.entries.forEach { type ->
                val cards = groups[type] ?: return@forEach
                item(key = "header_$type") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(resIcon(type), fontSize = 9.sp)
                        Text(
                            type.label.replaceFirstChar { it.uppercase() },
                            color = resColor(type).copy(alpha = 0.7f),
                            fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                        Box(
                            Modifier.weight(1f).height(1.dp)
                                .background(resColor(type).copy(alpha = 0.15f))
                        )
                        val groupCount = cards.sumOf { deck.cardCounts[it.id] ?: 0 }
                        Text("$groupCount", color = resColor(type).copy(alpha = 0.5f), fontSize = 8.sp)
                    }
                }
                items(cards, key = { it.id }) { card ->
                    DeckCardRow(
                        card     = card,
                        count    = deck.cardCounts[card.id] ?: 0,
                        onRemove = { onRemove(card.id) }
                    )
                }
            }

            // Stats footer
            item(key = "stats_divider") {
                HorizontalDivider(color = Gold.copy(alpha = 0.1f), modifier = Modifier.padding(top = 4.dp))
            }
            item(key = "stats") {
                DeckStats(deck, deckCards)
            }

            // Action buttons footer
            item(key = "actions_divider") {
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
            }
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (isActive) TealLight.copy(alpha = 0.08f)
                                else if (deck.isValid) Teal.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.03f)
                            )
                            .border(
                                1.dp,
                                if (isActive) TealLight.copy(alpha = 0.4f)
                                else if (deck.isValid) TealLight.copy(alpha = 0.45f)
                                else TextMuted.copy(alpha = 0.15f),
                                RoundedCornerShape(7.dp)
                            )
                            .then(
                                if (!isActive && deck.isValid) Modifier.clickable { onSetActive() }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isActive) "✓ Aktivní balíček" else "Nastavit aktivní",
                            color = if (isActive) TealLight else if (deck.isValid) TealLight else TextMuted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .background(AttackRed.copy(alpha = 0.08f))
                            .border(1.dp, AttackRed.copy(alpha = 0.3f), RoundedCornerShape(7.dp))
                            .clickable { onClear() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Vymazat", color = AttackRed.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // bottom padding so last item isn't right at the edge
            item(key = "bottom_pad") { Spacer(Modifier.height(6.dp)) }
        }
    }
}

@Composable
private fun DeckCardRow(card: Card, count: Int, onRemove: () -> Unit) {
    val costColor = resColor(card.costType)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(costColor.copy(alpha = 0.05f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(effectIcon(card), fontSize = 10.sp)
        Text(
            card.name, color = TextPrimary,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("×$count", color = costColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        // Remove one
        Box(
            Modifier.size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AttackRed.copy(alpha = 0.1f))
                .border(1.dp, AttackRed.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Text("−", color = AttackRed.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeckStats(deck: Deck, deckCards: List<Card>) {
    val byType = ResourceType.entries.associateWith { type ->
        deckCards.filter { it.costType == type }.sumOf { deck.cardCounts[it.id] ?: 0 }
    }
    val total = deck.totalCards.coerceAtLeast(1).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Složení balíčku", color = TextMuted, fontSize = 8.sp, letterSpacing = 1.sp)
        ResourceType.entries.forEach { type ->
            val count = byType[type] ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(resIcon(type), fontSize = 9.sp, modifier = Modifier.width(14.dp))
                Box(
                    Modifier.weight(1f).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(count / total).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(resColor(type).copy(alpha = 0.65f))
                    )
                }
                Text("$count", color = resColor(type), fontSize = 8.sp, modifier = Modifier.width(16.dp), textAlign = TextAlign.End)
            }
        }
    }
}
