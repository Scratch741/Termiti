package com.example.termiti

import androidx.compose.animation.core.*
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

// Paleta barev → GameColors.kt

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

// rarityColor → GameColors.kt

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
    is CardEffect.AddToOpponentDeck -> "💣"
    is CardEffect.TrapOnDraw        -> "💥"
    is CardEffect.DrawCard          -> "🎴"
    is CardEffect.StealCastle        -> "🧛"
    is CardEffect.AddResourceDelayed  -> "⏳"
    is CardEffect.BlockMine           -> "🚫"
    is CardEffect.XScaledAttackPlayer -> "⚔️"
    is CardEffect.XScaledAttackCastle -> "🎯"
    is CardEffect.XScaledBuildCastle  -> "🏰"
    is CardEffect.XScaledDualResource -> "💰"
    is CardEffect.SwapHands           -> "🔄"
    is CardEffect.DrawPerCardPlayed        -> "🎴"
    is CardEffect.GainResourcePerCardPlayed -> "⚡"
    is CardEffect.GainCastlePerCardPlayed   -> "🏯"
    is CardEffect.ShapeShift                -> "🎭"
    is CardEffect.ConvertMine               -> "🔀"
    is CardEffect.DecisionBurnOpponent      -> "🔥"
    is CardEffect.DecisionChooseType        -> "🎯"
    is CardEffect.DecisionFromDiscard       -> "♻️"
    is CardEffect.DecisionFromDeck          -> "🔍"
    is CardEffect.DecisionMine              -> "⛏️"
    is CardEffect.DrawBoth                  -> "🎴"
    is CardEffect.CloneNextPlayed           -> "🔁"
    is CardEffect.SmartJoker                -> "🃏"
    null                              -> "❓"
}

private fun CardEffect.toCategory(): String? = when (this) {
    is CardEffect.AttackPlayer,
    is CardEffect.AttackCastle,
    is CardEffect.AttackWall,
    is CardEffect.StealResource,
    is CardEffect.DrainResource     -> "Útok"
    is CardEffect.ConditionalEffect -> this.effect.toCategory()
    is CardEffect.BuildCastle       -> if (this.amount > 0) "Obrana" else "Útok"
    is CardEffect.BuildWall         -> if (this.amount > 0) "Obrana" else "Útok"
    is CardEffect.AddResource       -> "Zdroje"
    is CardEffect.AddMine,
    is CardEffect.ConvertMine       -> "Doly"
    is CardEffect.DecisionBurnOpponent,
    is CardEffect.DecisionChooseType,
    is CardEffect.DecisionFromDiscard,
    is CardEffect.DecisionFromDeck,
    is CardEffect.DecisionMine      -> "Rozhodnutí"
    else                            -> null
}

/** Vrátí všechny kategorie efektů karty (karta může mít víc najednou, např. Obrana + Doly). */
private fun Card.categories(): Set<String> =
    effects.mapNotNull { it.toCategory() }.toSet().ifEmpty { setOf("Ostatní") }

private fun Card.category() = categories().first()

// ─── Root ────────────────────────────────────────────────────────────────────
@Composable
fun DeckBuilderScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val decks          = viewModel.decks
    val activeDeckIdx  by viewModel.activeDeckIndex

    var editingIdx     by remember { mutableIntStateOf(activeDeckIdx) }
    val editingDeck    = decks[editingIdx]

    var previewCard    by remember { mutableStateOf<Card?>(null) }
    var profile        by remember { mutableStateOf(PlayerProfileManager.profile) }

    // Filter state
    var filterRes      by remember { mutableStateOf<ResourceType?>(null) }
    var filterCat      by remember { mutableStateOf<String?>(null) }
    var filterUnlocked by remember { mutableStateOf(false) }
    var searchQuery    by remember { mutableStateOf("") }
    var filterCost     by remember { mutableStateOf<Int?>(null) }

    val filteredCards = remember(filterRes, filterCat, filterUnlocked, searchQuery, filterCost, profile) {
        viewModel.allCards
            .filter { card ->
                card.effects.none { it is CardEffect.TrapOnDraw } &&
                !card.isPlaceholder &&
                (filterRes == null || card.costType == filterRes) &&
                (filterCat == null ||
                    (filterCat == "Kombo" && card.isCombo) ||
                    (filterCat != "Kombo" && card.categories().contains(filterCat))) &&
                (!filterUnlocked || run {
                    profile?.allCardsUnlocked == true ||
                    CardCollectionManager.isBasicCard(card) ||
                    (profile?.cardCollection?.getOrDefault(card.id, 0) ?: 0) > 0
                }) &&
                (searchQuery.isBlank() || card.name.contains(searchQuery.trim(), ignoreCase = true) ||
                    card.description.contains(searchQuery.trim(), ignoreCase = true)) &&
                (filterCost == null ||
                    if (filterCost == 7) card.cost >= 7 else card.cost == filterCost)
            }
            .sortedWith(compareBy({ it.cost }, { it.costType.ordinal }, { it.name }))
    }

    Box(Modifier.fillMaxSize().background(BgDeep)) {
        Row(Modifier.fillMaxSize()) {

            // ── Left: catalog ─────────────────────────────────────────────────
            Column(Modifier.weight(3f).fillMaxHeight()) {
                FilterBar(
                    filterRes      = filterRes,
                    filterCat      = filterCat,
                    filterUnlocked = filterUnlocked,
                    searchQuery    = searchQuery,
                    onResFilter    = { filterRes = if (filterRes == it) null else it },
                    onCatFilter    = { filterCat = if (filterCat == it) null else it },
                    onUnlocked     = { filterUnlocked = !filterUnlocked },
                    onSearchChange = { searchQuery = it }
                )
                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(4),
                    contentPadding        = PaddingValues(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        val count  = editingDeck.cardCounts[card.id] ?: 0
                        val isFull = editingDeck.totalCards >= 30
                        val usable = when {
                            profile?.allCardsUnlocked == true ||
                            CardCollectionManager.isBasicCard(card) -> card.rarity.maxCopies
                            else -> minOf(
                                profile?.cardCollection?.getOrDefault(card.id, 0) ?: 0,
                                card.rarity.maxCopies
                            )
                        }
                        val isNew = !CardCollectionManager.isBasicCard(card) &&
                            usable > 0 &&
                            profile?.allCardsUnlocked != true &&
                            card.id !in (profile?.seenCards ?: emptySet())
                        CatalogCardItem(
                            card        = card,
                            count       = count,
                            usable      = usable,
                            isNew       = isNew,
                            deckFull    = isFull,
                            onIncrement = {
                                if (count < usable && !isFull)
                                    viewModel.setCardCount(editingIdx, card.id, count + 1)
                            },
                            onDecrement = {
                                if (count > 0)
                                    viewModel.setCardCount(editingIdx, card.id, count - 1)
                            },
                            onPreview = {
                                if (isNew) {
                                    PlayerProfileManager.markCardsSeen(setOf(card.id))
                                    profile = PlayerProfileManager.profile
                                }
                                previewCard = card
                            }
                        )
                    }
                }
                // ── Spodní lišta: počet + filtr dle mana costu ───────────────
                ManaCostFilterBar(
                    showing      = filteredCards.size,
                    total        = viewModel.allCards.size,
                    filterCost   = filterCost,
                    onCostFilter = { filterCost = if (filterCost == it) null else it }
                )
            }

            VerticalDivider(color = Gold.copy(alpha = 0.2f))

            // ── Right: top bar + deck panel ───────────────────────────
            Column(Modifier.weight(2f).fillMaxHeight()) {
                TopBar(
                    decks         = decks,
                    activeDeckIdx = activeDeckIdx,
                    editingIdx    = editingIdx,
                    onSelectDeck  = { idx ->
                        editingIdx = idx
                        if (decks[idx].isValid) viewModel.setActiveDeck(idx)
                    },
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
            val card = previewCard!!

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
                Row(
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* pohlcení kliků – nezavírá overlay */ },
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FullCardPreview(card)
                    CardActionPanel(
                        card        = card,
                        profile     = profile,
                        onCraft = {
                            CardCollectionManager.craftCard(card.id, viewModel.allCards)
                            profile = PlayerProfileManager.profile
                        },
                        onDismantle = {
                            CardCollectionManager.dismantleCard(card.id, viewModel.allCards)
                            profile = PlayerProfileManager.profile
                        },
                        onClose = { previewCard = null }
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
    filterUnlocked: Boolean,
    searchQuery: String,
    onResFilter: (ResourceType) -> Unit,
    onCatFilter: (String) -> Unit,
    onUnlocked: () -> Unit,
    onSearchChange: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(BgPanel.copy(alpha = 0.6f))
    ) {
        // ── Řádek 1: zdroj + hledání ─────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("Zdroj:", color = TextMuted, fontSize = 9.sp)
            FilterChip("✨ Magie",  filterRes == ResourceType.MAGIC,  MagicPurple) { onResFilter(ResourceType.MAGIC)  }
            FilterChip("⚔️ Útok",  filterRes == ResourceType.ATTACK, AttackRed)   { onResFilter(ResourceType.ATTACK) }
            FilterChip("🪨 Kámen", filterRes == ResourceType.STONES, StoneColor)  { onResFilter(ResourceType.STONES) }
            FilterChip("🌀 Chaos", filterRes == ResourceType.CHAOS,  ChaosOrange) { onResFilter(ResourceType.CHAOS)  }
            Spacer(Modifier.weight(1f))
            BasicTextField(
                value         = searchQuery,
                onValueChange = onSearchChange,
                singleLine    = true,
                textStyle     = TextStyle(color = TextPrimary, fontSize = 10.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier      = Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(
                        1.dp,
                        if (searchQuery.isNotBlank()) Gold.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                decorationBox = { inner ->
                    Box {
                        if (searchQuery.isBlank()) {
                            Text("🔍 Hledat…", color = TextMuted.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                        inner()
                    }
                }
            )
            if (searchQuery.isNotBlank()) {
                Box(
                    Modifier.size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { onSearchChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // ── Řádek 2: efekt + kombo + odemčené ────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("Efekt:", color = TextMuted, fontSize = 9.sp)
            FilterChip("Útok",        filterCat == "Útok",        AttackRed)              { onCatFilter("Útok")        }
            FilterChip("Obrana",      filterCat == "Obrana",      StoneColor)             { onCatFilter("Obrana")      }
            FilterChip("Zdroje",      filterCat == "Zdroje",      MagicPurple)            { onCatFilter("Zdroje")      }
            FilterChip("Doly",        filterCat == "Doly",        Gold)                   { onCatFilter("Doly")        }
            FilterChip("🔗 Kombo",    filterCat == "Kombo",       TealLight)              { onCatFilter("Kombo")       }
            FilterChip("Rozhodnutí",  filterCat == "Rozhodnutí",  Color(0xFFAB47BC))      { onCatFilter("Rozhodnutí")  }
            Spacer(Modifier.weight(1f))
            FilterChip("🔓 Odemčené", filterUnlocked, HpGreen) { onUnlocked() }
        }
    }
}

// ─── Mana Cost Filter Bar ─────────────────────────────────────────────────────
@Composable
private fun ManaCostFilterBar(
    showing: Int,
    total: Int,
    filterCost: Int?,
    onCostFilter: (Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(BgPanel.copy(alpha = 0.8f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        // Počet karet – zarovnán vlevo
        Text(
            "$showing/$total",
            color = TextMuted,
            fontSize = 8.sp,
            letterSpacing = 0.3.sp,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        // Chipy – vycentrované
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            (0..7).forEach { cost ->
                val label  = if (cost == 7) "7+" else "$cost"
                val active = filterCost == cost
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) Gold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (active) Gold.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onCostFilter(cost) }
                        .padding(horizontal = 13.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color      = if (active) Gold else TextMuted,
                        fontSize   = 8.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
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
fun CardPreview(card: Card) {
    val artResId = card.effectiveArtResId()
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
                .offset(y = 92.dp)
                .fillMaxWidth()
                .height(38.dp)
                .clipToBounds()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(parseCardDesc(card.description), color = Color(0xFFDDD0B0), fontSize = 7.sp,
                textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 9.sp,
                style = LocalTextStyle.current.merge(
                    TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            )
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

// ─── Card Action Panel (pravá strana overlay) ────────────────────────────────
@Composable
private fun CardActionPanel(
    card       : Card,
    profile    : PlayerProfile?,
    onCraft    : () -> Unit,
    onDismantle: () -> Unit,
    onClose    : () -> Unit
) {
    val isBasic     = CardCollectionManager.isBasicCard(card)
    val allUnlocked = profile?.allCardsUnlocked ?: true
    // Read from the `profile` parameter (Compose state) so the panel reflects
    // changes immediately after crafting/dismantling without needing navigation.
    val realOwned   = when {
        allUnlocked || isBasic -> card.rarity.maxCopies
        else -> profile?.cardCollection?.getOrDefault(card.id, 0) ?: 0
    }
    val dust        = profile?.dust ?: 0
    val rc          = rarityColor(card.rarity)
    val costColor   = resColor(card.costType)

    val collectible = !isBasic && !allUnlocked

    // Pending počty – aplikují se až kliknutím na Hotovo.
    // Klik mimo panel (onClose bez Hotovo) je zahodí.
    // Craft a Dismantle jsou vzájemně výlučné: nastavení jednoho nuluje druhý.
    var pendingCraft     by remember { mutableStateOf(0) }
    var pendingDismantle by remember { mutableStateOf(0) }
    val hasPending = pendingCraft > 0 || pendingDismantle > 0

    // Kolik kopií lze ještě vyrobit (omezeno prachem i místem v kolekci)
    val maxCraft     = if (collectible) minOf(card.rarity.maxCopies - realOwned, if (card.rarity.craftCost > 0) dust / card.rarity.craftCost else 0) else 0
    val maxDismantle = if (collectible) realOwned else 0

    // Panel je scrollovatelný – zabraňuje oříznutí obsahu na nízkých obrazovkách (landscape)
    Box(
        modifier = Modifier
            .width(210.dp)
            .heightIn(max = 340.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgPanel)
            .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Jméno + počet kopií ──────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(card.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp, modifier = Modifier.weight(1f))
                if (!isBasic && !allUnlocked) {
                    Text(
                        "$realOwned/${card.rarity.maxCopies}",
                        color = if (realOwned >= card.rarity.maxCopies) HpGreen else TealLight,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(rc))
                Text(card.rarity.label, color = rc, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(resIcon(card.costType), fontSize = 10.sp)
                Text(
                    if (card.isXCost) "X" else "${card.cost}",
                    color = costColor, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
                if (!isBasic && !allUnlocked) {
                    Spacer(Modifier.weight(1f))
                    Text("✨ $dust", color = TextMuted, fontSize = 9.sp)
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.15f))

            if (!isBasic) {
                // ── Vyrobit ──────────────────────────────────────────────────
                val craftAccent = Color(0xFFB39DDB)
                ActionCounter(
                    label       = "🔨  Vyrobit  ✨${card.rarity.craftCost}",
                    accent      = craftAccent,
                    count       = pendingCraft,
                    maxCount    = maxCraft,
                    onDecrement = { pendingCraft-- },
                    onIncrement = { pendingCraft++; pendingDismantle = 0 }
                )
                if (pendingCraft > 0) {
                    Text(
                        "−${pendingCraft * card.rarity.craftCost} ✨ prachu",
                        color = craftAccent, fontSize = 9.sp
                    )
                }

                // ── Rozebrat ──────────────────────────────────────────────────
                val dismantleAccent = Color(0xFFE57373)
                ActionCounter(
                    label       = "💥  Rozebrat  +${card.rarity.dustValue}✨",
                    accent      = dismantleAccent,
                    count       = pendingDismantle,
                    maxCount    = maxDismantle,
                    onDecrement = { pendingDismantle-- },
                    onIncrement = { pendingDismantle++; pendingCraft = 0 }
                )
                if (pendingDismantle > 0) {
                    Text(
                        "+${pendingDismantle * card.rarity.dustValue} ✨ prachu",
                        color = dismantleAccent, fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // ── Hotovo – aplikuje pending akci a zavře panel ─────────────────
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasPending) Gold.copy(alpha = 0.22f) else Gold.copy(alpha = 0.12f))
                    .border(1.dp, if (hasPending) Gold.copy(alpha = 0.9f) else Gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable {
                        SoundManager.playMenuTap()
                        repeat(pendingCraft) { onCraft() }
                        repeat(pendingDismantle) { onDismantle() }
                        onClose()
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (hasPending) "✓  Potvrdit" else "✓  Hotovo",
                    color = Gold, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun PanelActionBtn(
    label: String, enabled: Boolean, accent: Color,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(when {
                selected -> accent.copy(alpha = 0.28f)
                enabled  -> accent.copy(alpha = 0.12f)
                else     -> Color.White.copy(alpha = 0.04f)
            })
            .border(1.dp, when {
                selected -> accent.copy(alpha = 0.9f)
                enabled  -> accent.copy(alpha = 0.5f)
                else     -> Color.White.copy(alpha = 0.08f)
            }, RoundedCornerShape(6.dp))
            .then(if (enabled || selected) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) accent else TextMuted.copy(alpha = 0.4f),
            fontSize = 9.sp, fontWeight = FontWeight.Bold
        )
    }
}

// ─── Action Counter (−  N  + řádek pro craft/dismantle) ──────────────────────
@Composable
private fun ActionCounter(
    label      : String,
    accent     : Color,
    count      : Int,
    maxCount   : Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    val active = count > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (active) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color    = if (active || maxCount > 0) accent else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        CountBtn("−", enabled = count > 0, onClick = onDecrement)
        Text(
            "$count",
            color    = if (active) accent else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 14.dp)
        )
        CountBtn("+", enabled = count < maxCount, onClick = onIncrement)
    }
}

// ─── Full Card Preview (zvětšený náhled) ──────────────────────────────────
@Composable
private fun FullCardPreview(card: Card) {
    val costColor    = resColor(card.costType)
    val rarityCol    = rarityColor(card.rarity)
    val artResId     = card.effectiveArtResId()
    val context      = LocalContext.current
    val frameResId   = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
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

            // Popis — y = 92dp × 2.52 = 232dp, výška = 38dp × 2.52 = 96dp, padding = 10dp × 2.52 = 25dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 232.dp)
                    .height(96.dp)
                    .clipToBounds()
                    .padding(horizontal = 25.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    parseCardDesc(card.description),
                    color = Color(0xFFDDD0B0),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 23.sp,
                    style = LocalTextStyle.current.merge(
                        TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                )
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

    }
}

// ─── Catalog Card Item ────────────────────────────────────────────────────────
@Composable
private fun CatalogCardItem(
    card: Card,
    count: Int,
    usable: Int,
    isNew: Boolean,
    deckFull: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onPreview: () -> Unit
) {
    val hasAny    = count > 0
    val isLocked  = usable == 0 && !CardCollectionManager.isBasicCard(card)
    val costColor = resColor(card.costType)
    val border    = when {
        isNew    -> Gold.copy(alpha = 0.85f)
        isLocked -> Color.White.copy(alpha = 0.05f)
        hasAny   -> costColor.copy(alpha = 0.55f)
        else     -> Color.White.copy(alpha = 0.07f)
    }
    val rc        = rarityColor(card.rarity)

    // Klik → náhled karty; přidání do decku přes tlačítko [+] uvnitř dlaždice
    val itemModifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(7.dp))
        .clickable { onPreview() }

        // ── Texturovaná karta ─────────────────────────────────────────────────
        Box(itemModifier.alpha(if (isLocked) 0.35f else 1f)) {
            Column(
                Modifier
                    .background(Color(0xFF0F0C14))
                    .border(if (isNew) 2.dp else 1.5.dp, border, RoundedCornerShape(7.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CardPreview(card = card)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountBtn("−", enabled = count > 0, onClick = onDecrement)
                    Spacer(Modifier.width(4.dp))
                    CopyDots(
                        maxCopies  = card.rarity.maxCopies,
                        inDeck     = count,
                        usable     = usable,
                        rarityColor = rc
                    )
                    Spacer(Modifier.width(4.dp))
                    CountBtn("+", enabled = count < usable && !deckFull, onClick = onIncrement)
                }
            }
            // Zámek overlay
            if (isLocked) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 18.sp)
                }
            }
            // Badge "NOVÉ"
            if (isNew) {
                NewBadge(Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 3.dp))
            }
        }
}

// ─── Badge „NOVÉ" ─────────────────────────────────────────────────────────────
@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "new_badge")
    val alpha by transition.animateFloat(
        initialValue  = 0.75f,
        targetValue   = 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Gold.copy(alpha = alpha))
            .padding(horizontal = 4.dp, vertical = 1.5.dp)
    ) {
        Text(
            "NOVÉ",
            color      = Color(0xFF1A1320),
            fontSize   = 6.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─── Copy Dots ────────────────────────────────────────────────────────────────
// Zobrazuje stav kopií karty:
//   ● rarity barva = v balíčku
//   ○ bílá 20%    = vlastněno, ale není v balíčku
//   ✕ červená     = chybí (nevlastněno) — jen když usable < maxCopies
@Composable
private fun CopyDots(
    maxCopies : Int,
    inDeck    : Int,
    usable    : Int,
    rarityColor: Color
) {
    val incomplete = usable < maxCopies
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(maxCopies) { i ->
            val color = when {
                i < inDeck  -> rarityColor.copy(alpha = 0.85f)
                i < usable  -> Color.White.copy(alpha = 0.20f)
                incomplete  -> Color(0xFFE57373).copy(alpha = 0.80f)
                else        -> Color.White.copy(alpha = 0.07f)
            }
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                // Křížek pro nevlastněné sloty
                if (i >= usable && incomplete) {
                    Text(
                        "×",
                        color    = Color.White.copy(alpha = 0.75f),
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 6.sp
                    )
                }
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
            .then(if (enabled) Modifier.clickable { SoundManager.playDeckSelect(); onClick() } else Modifier),
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
    val s          = LocalStrings.current
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
                        Text(s.deckClear, color = AttackRed.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
