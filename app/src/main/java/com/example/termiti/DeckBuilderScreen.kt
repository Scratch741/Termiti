package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
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

@Composable
private fun SectionSeparator(modifier: Modifier = Modifier) {
    Image(
        painter      = painterResource(R.drawable.bg_separator),
        contentDescription = null,
        modifier     = modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth
    )
}

/**
 * Horizontální separátor otočený o 90° pomocí layout+placeWithLayer.
 * Layout modifier swapuje width↔height constraints → Image si myslí, že je
 * horizontální, ale výsledek zabere správnou výšku a šířku jako vertikální pruh.
 */
@Composable
private fun VerticalSeparatorImage() {
    Image(
        painter      = painterResource(R.drawable.bg_separator),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier     = Modifier
            .fillMaxHeight()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth  = constraints.minHeight,
                        maxWidth  = constraints.maxHeight,
                        minHeight = 0,
                        maxHeight = if (constraints.maxWidth != Constraints.Infinity)
                            constraints.maxWidth else constraints.maxHeight
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.placeWithLayer(
                        x = -(placeable.width  - placeable.height) / 2,
                        y = -(placeable.height - placeable.width)  / 2
                    ) {
                        rotationZ = 90f
                    }
                }
            }
    )
}

private fun cardFrameName(costType: ResourceType) = when (costType) {
    ResourceType.MAGIC  -> "card_frame_magic"
    ResourceType.ATTACK -> "card_frame_attack"
    ResourceType.CHAOS  -> "card_frame_chaos"
    ResourceType.STONES -> "card_frame_stones"
}

private fun resColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> MagicBlue
    ResourceType.ATTACK -> AttackRed
    ResourceType.STONES -> StoneColor
    ResourceType.CHAOS  -> ChaosOrange
}

// rarityColor → GameColors.kt

private fun CardEffect.toCategory(): String? = when (this) {
    is CardEffect.AttackPlayer,
    is CardEffect.AttackCastle,
    is CardEffect.AttackWall,
    is CardEffect.StealResource,
    is CardEffect.DrainResource,
    is CardEffect.MomentumAttack    -> "Útok"
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
    is CardEffect.DecisionDrawFromDeck,
    is CardEffect.DecisionMine,
    is CardEffect.DecisionChooseResource -> "Rozhodnutí"
    else                            -> null
}

/** Vrátí všechny kategorie efektů karty (karta může mít víc najednou, např. Obrana + Doly). */
private fun Card.categories(): Set<String> =
    effects.mapNotNull { it.toCategory() }.toSet().ifEmpty { setOf("Ostatní") }

private fun Card.category() = categories().first()

/**
 * Lokalizuje auto-generovaný název balíčku ("Balíček 1" / "Deck 1" → aktivní jazyk).
 * Vlastní (přejmenované) názvy projdou beze změny.
 */
private val DEFAULT_DECK_NAME = Regex("^(?:Balíček|Deck) (\\d+)$")
fun localizedDeckName(name: String): String {
    val m = DEFAULT_DECK_NAME.find(name) ?: return name
    return LanguageManager.currentStrings.deckDefaultName.format(m.groupValues[1].toInt())
}

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
                (searchQuery.isBlank() || card.displayName.contains(searchQuery.trim(), ignoreCase = true) ||
                    card.displayDescription.contains(searchQuery.trim(), ignoreCase = true)) &&
                (filterCost == null ||
                    if (filterCost == 7) card.cost >= 7 else card.cost == filterCost)
            }
            .sortedWith(compareBy({ it.cost }, { it.costType.ordinal }, { it.displayName }))
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.deckbuild_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Row(Modifier.fillMaxSize()) {

            // ── Left: catalog ─────────────────────────────────────────────────
            Column(Modifier.weight(3f).fillMaxHeight()) {
                FilterBar(
                    filterRes      = filterRes,
                    filterCat      = filterCat,
                    filterUnlocked = filterUnlocked,
                    onResFilter    = { filterRes = if (filterRes == it) null else it },
                    onCatFilter    = { filterCat = if (filterCat == it) null else it },
                    onUnlocked     = { filterUnlocked = !filterUnlocked },
                    onBack         = onBack
                )
                SectionSeparator()
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
                    showing        = filteredCards.size,
                    total          = viewModel.allCards.count { !it.isPlaceholder && it.effects.none { e -> e is CardEffect.TrapOnDraw } },
                    filterCost     = filterCost,
                    onCostFilter   = { filterCost = if (filterCost == it) null else it },
                    searchQuery    = searchQuery,
                    onSearchChange = { searchQuery = it }
                )
            }

            VerticalSeparatorImage()

            // ── Right: top bar + deck panel ───────────────────────────
            Column(Modifier.weight(2f).fillMaxHeight()) {
                TopBar(
                    decks         = decks,
                    activeDeckIdx = activeDeckIdx,
                    editingIdx    = editingIdx,
                    editingDeck   = editingDeck,
                    onSelectDeck  = { idx ->
                        editingIdx = idx
                        if (decks[idx].isValid) viewModel.setActiveDeck(idx)
                    },
                    onRename      = { viewModel.renameDeck(editingIdx, it) }
                )
                SectionSeparator()
                DeckPanel(
                    deck            = editingDeck,
                    allCards        = viewModel.allCards,
                    isActive        = editingIdx == activeDeckIdx,
                    presetTemplates = viewModel.presetTemplates,
                    onLoadPreset    = { viewModel.loadPreset(editingIdx, it) },
                    onClear         = { viewModel.clearDeck(editingIdx) },
                    onSetActive     = { viewModel.setActiveDeck(editingIdx) },
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
    editingDeck: Deck,
    onSelectDeck: (Int) -> Unit,
    onRename: (String) -> Unit
) {
    var isEditingName  by remember(editingDeck.id) { mutableStateOf(false) }
    var nameInput      by remember(editingDeck.name) { mutableStateOf(editingDeck.name) }
    val focusRequester = remember { FocusRequester() }

    val validColor = when {
        editingDeck.isValid         -> HpGreen
        editingDeck.totalCards > 30 -> AttackRed
        else                        -> Gold.copy(alpha = 0.7f)
    }

    Row(
        Modifier.fillMaxWidth().background(BgPanel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Slot chipy
        decks.forEachIndexed { index, deck ->
            DeckSlotChip(
                deck      = deck,
                isActive  = index == activeDeckIdx,
                isEditing = index == editingIdx,
                index     = index,
                onClick   = { onSelectDeck(index) }
            )
        }

        // Název decku (editovatelný)
        if (isEditingName) {
            BasicTextField(
                value           = nameInput,
                onValueChange   = { if (it.length <= 20) nameInput = it },
                singleLine      = true,
                textStyle       = TextStyle(
                    color      = TextPrimary,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onRename(nameInput); isEditingName = false
                }),
                modifier = Modifier
                    .width(90.dp)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            PlainButton(
                text      = "✓",
                modifier  = Modifier.heightIn(max = 22.dp).widthIn(max = 30.dp),
                textColor = Gold,
                fontSize  = 10.sp,
                paddingH  = 4.dp,
                paddingV  = 3.dp,
                onClick   = { onRename(nameInput); isEditingName = false }
            )
        } else {
            Text(
                localizedDeckName(editingDeck.name),
                color      = TextPrimary,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.widthIn(max = 130.dp)
            )
            PlainButton(
                text      = "✎",
                modifier  = Modifier.heightIn(max = 22.dp).widthIn(max = 28.dp),
                textColor = TextMuted,
                fontSize  = 9.sp,
                paddingH  = 4.dp,
                paddingV  = 3.dp,
                onClick   = { nameInput = editingDeck.name; isEditingName = true }
            )
        }

        Spacer(Modifier.weight(1f))

        // Počet karet
        Text(
            "${editingDeck.totalCards} / 30",
            color      = validColor,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DeckSlotChip(
    deck: Deck,
    isActive: Boolean,
    isEditing: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    val borderColor = when {
        isEditing && isActive -> Gold
        isEditing             -> TextPrimary.copy(alpha = 0.55f)
        isActive              -> TealLight.copy(alpha = 0.60f)
        else                  -> TextMuted.copy(alpha = 0.20f)
    }
    val bg = if (isEditing) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.02f)
    val textColor = when {
        isEditing && isActive -> Gold
        isEditing             -> TextPrimary
        isActive              -> TealLight
        else                  -> TextMuted.copy(alpha = 0.50f)
    }

    PlainButton(
        text      = "${index + 1}",
        modifier  = Modifier.size(30.dp),
        textColor = textColor,
        fontSize  = 12.sp,
        selected  = isEditing || isActive,
        paddingH  = 0.dp,
        paddingV  = 0.dp,
        onClick   = onClick
    )
}

// ─── Filter Bar ───────────────────────────────────────────────────────────────
@Composable
private fun FilterBar(
    filterRes: ResourceType?,
    filterCat: String?,
    filterUnlocked: Boolean,
    onResFilter: (ResourceType) -> Unit,
    onCatFilter: (String) -> Unit,
    onUnlocked: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(BgPanel.copy(alpha = 0.6f))
    ) {
        // ── Řádek 1: zdroj + zpět ────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Zdroj:", color = TextMuted, fontSize = 9.sp)
            FilterChip(R.drawable.magie_icon,  LocalStrings.current.resMagic,  filterRes == ResourceType.MAGIC,  MagicBlue)   { onResFilter(ResourceType.MAGIC)  }
            FilterChip(R.drawable.utok_icon,   LocalStrings.current.resAttack, filterRes == ResourceType.ATTACK, AttackRed)   { onResFilter(ResourceType.ATTACK) }
            FilterChip(R.drawable.kamen_icon2, LocalStrings.current.resStone,  filterRes == ResourceType.STONES, StoneColor)  { onResFilter(ResourceType.STONES) }
            FilterChip(R.drawable.chaos_icon,  LocalStrings.current.resChaos,  filterRes == ResourceType.CHAOS,  ChaosOrange) { onResFilter(ResourceType.CHAOS)  }
            Spacer(Modifier.weight(1f))
            PlainButton(
                text      = LocalStrings.current.back,
                modifier  = Modifier.heightIn(max = 22.dp).widthIn(max = 58.dp),
                textColor = TextMuted,
                fontSize  = 8.sp,
                paddingH  = 5.dp,
                paddingV  = 3.dp,
                onClick   = onBack
            )
        }
        // ── Řádek 2: efekt + kombo + odemčené ────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val s = LocalStrings.current
            Text(s.dbEffectLabel, color = TextMuted, fontSize = 9.sp)
            FilterChip(s.catAttack,    filterCat == "Útok",        AttackRed)              { onCatFilter("Útok")        }
            FilterChip(s.catDefense,   filterCat == "Obrana",      StoneColor)             { onCatFilter("Obrana")      }
            FilterChip(s.catResources, filterCat == "Zdroje",      MagicPurple)            { onCatFilter("Zdroje")      }
            FilterChip(s.catMines,     filterCat == "Doly",        Gold)                   { onCatFilter("Doly")        }
            FilterChip(s.catCombo,     filterCat == "Kombo",       TealLight)              { onCatFilter("Kombo")       }
            FilterChip(s.catDecision,  filterCat == "Rozhodnutí",  Color(0xFFAB47BC))      { onCatFilter("Rozhodnutí")  }
            Spacer(Modifier.weight(1f))
            FilterChip(s.dbFilterUnlocked, filterUnlocked, HpGreen) { onUnlocked() }
        }
    }
}

// ─── Mana Cost Filter Bar ─────────────────────────────────────────────────────
@Composable
private fun ManaCostFilterBar(
    showing: Int,
    total: Int,
    filterCost: Int?,
    onCostFilter: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BgPanel.copy(alpha = 0.8f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Počet karet
        Text(
            "$showing/$total",
            color = TextMuted,
            fontSize = 8.sp,
            letterSpacing = 0.3.sp
        )
        // Chipy ceny
        (0..7).forEach { cost ->
            val label  = if (cost == 7) "7+" else "$cost"
            val active = filterCost == cost
            PlainButton(
                text         = label,
                modifier     = Modifier.width(31.dp).heightIn(max = 22.dp),
                textColor    = if (active) Gold else TextMuted,
                fontSize     = 9.sp,
                fontWeight   = if (active) FontWeight.Bold else FontWeight.Normal,
                selected     = active,
                outlineColor = ChaosOrange,
                paddingH     = 0.dp,
                paddingV     = 2.dp,
                onClick      = { onCostFilter(cost) }
            )
        }
        Spacer(Modifier.weight(1f))
        // Hledání
        BasicTextField(
            value           = searchQuery,
            onValueChange   = onSearchChange,
            singleLine      = true,
            textStyle       = TextStyle(color = TextPrimary, fontSize = 10.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier        = Modifier
                .width(130.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(
                    1.dp,
                    if (searchQuery.isNotBlank()) Gold.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(5.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
            decorationBox   = { inner ->
                Box {
                    if (searchQuery.isBlank()) {
                        Text(LocalStrings.current.dbSearchHint, color = TextMuted.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                    inner()
                }
            }
        )
        if (searchQuery.isNotBlank()) {
            PlainButton(
                text      = "×",
                modifier  = Modifier.size(20.dp),
                textColor = TextMuted,
                fontSize  = 12.sp,
                paddingH  = 0.dp,
                paddingV  = 0.dp,
                onClick   = { onSearchChange("") }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(iconRes = null, label = label, active = active, color = color, onClick = onClick)
}

@Composable
private fun FilterChip(@DrawableRes iconRes: Int?, label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    if (iconRes != null) {
        PlainButtonWithIcon(
            text         = label,
            iconRes      = iconRes,
            modifier     = Modifier.heightIn(max = 22.dp).widthIn(max = 58.dp),
            textColor    = if (active) color else TextMuted,
            fontSize     = 8.sp,
            selected     = active,
            outlineColor = ChaosOrange,
            paddingH     = 5.dp,
            paddingV     = 3.dp,
            onClick      = onClick
        )
    } else {
        PlainButton(
            text         = label,
            modifier     = Modifier.heightIn(max = 22.dp).widthIn(max = 55.dp),
            textColor    = if (active) color else TextMuted,
            fontSize     = 7.sp,
            fontWeight   = if (active) FontWeight.Bold else FontWeight.Normal,
            selected     = active,
            outlineColor = ChaosOrange,
            paddingH     = 4.dp,
            paddingV     = 3.dp,
            onClick      = onClick
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
            name         = card.displayName,
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
            Text(parseCardDesc(card.displayDescription), color = Color(0xFFDDD0B0), fontSize = 7.sp,
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
                    card.displayType.uppercase(), color = Color(0xFFD4B870),
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
                Text(card.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
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
                Image(painterResource(resourceIconRes(card.costType)), contentDescription = null, modifier = Modifier.size(13.dp))
                Text(
                    if (card.isXCost) "X" else "${card.cost}",
                    color = costColor, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
                if (!isBasic && !allUnlocked) {
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Image(painterResource(R.drawable.dust_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                        Text("$dust", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.15f))

            if (!isBasic) {
                // ── Vyrobit ──────────────────────────────────────────────────
                val craftAccent = Color(0xFFB39DDB)
                ActionCounter(
                    label       = "Vyrobit  ✨${card.rarity.craftCost}",
                    iconRes     = R.drawable.hammer_icon,
                    accent      = craftAccent,
                    count       = pendingCraft,
                    maxCount    = maxCraft,
                    onDecrement = { pendingCraft-- },
                    onIncrement = { pendingCraft++; pendingDismantle = 0 }
                )
                if (pendingCraft > 0) {
                    Text(
                        LocalStrings.current.dbDustCost.format(pendingCraft * card.rarity.craftCost),
                        color = craftAccent, fontSize = 9.sp
                    )
                }

                // ── Rozebrat ──────────────────────────────────────────────────
                val dismantleAccent = Color(0xFFE57373)
                ActionCounter(
                    label       = LocalStrings.current.dbDisassemble.format(card.rarity.dustValue),
                    accent      = dismantleAccent,
                    count       = pendingDismantle,
                    maxCount    = maxDismantle,
                    onDecrement = { pendingDismantle-- },
                    onIncrement = { pendingDismantle++; pendingCraft = 0 },
                    iconRes     = R.drawable.explode_icon
                )
                if (pendingDismantle > 0) {
                    Text(
                        LocalStrings.current.dbDustGain.format(pendingDismantle * card.rarity.dustValue),
                        color = dismantleAccent, fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // ── Hotovo – aplikuje pending akci a zavře panel ─────────────────
            PlainButton(
                text      = if (hasPending) LocalStrings.current.dbConfirm else LocalStrings.current.dbDone,
                modifier  = Modifier.fillMaxWidth(),
                textColor = Gold,
                fontSize  = 11.sp,
                paddingH  = 0.dp,
                paddingV  = 9.dp,
                onClick   = {
                    repeat(pendingCraft) { onCraft() }
                    repeat(pendingDismantle) { onDismantle() }
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun PanelActionBtn(
    label: String, enabled: Boolean, accent: Color,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    PlainButton(
        text      = label,
        modifier  = Modifier.fillMaxWidth(),
        textColor = if (enabled || selected) accent else TextMuted.copy(alpha = 0.4f),
        fontSize  = 9.sp,
        enabled   = enabled || selected,
        selected  = selected,
        paddingH  = 0.dp,
        paddingV  = 7.dp,
        onClick   = onClick
    )
}

// ─── Action Counter (−  N  + řádek pro craft/dismantle) ──────────────────────
@Composable
private fun ActionCounter(
    label      : String,
    accent     : Color,
    count      : Int,
    maxCount   : Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    @DrawableRes iconRes: Int? = null
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
        if (iconRes != null) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(11.dp))
        }
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
                name         = card.displayName,
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
                    parseCardDesc(card.displayDescription),
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
                        card.displayType.uppercase(), color = Color(0xFFD4B870),
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
                    Image(painterResource(R.drawable.lock_icon), contentDescription = null, modifier = Modifier.size(22.dp))
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
            LocalStrings.current.dbBadgeNew,
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
    PlainButton(
        text      = label,
        modifier  = Modifier.size(22.dp),
        textColor = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.25f),
        fontSize  = 13.sp,
        enabled   = enabled,
        paddingH  = 0.dp,
        paddingV  = 0.dp,
        onClick   = onClick
    )
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
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current

    val deckCards = remember(deck.cardCounts) {
        allCards
            .filter { (deck.cardCounts[it.id] ?: 0) > 0 }
            .sortedWith(compareBy({ it.costType.ordinal }, { it.cost }, { it.displayName }))
    }

    // Group by resource type (calculate outside LazyColumn scope)
    val groups = remember(deckCards) { deckCards.groupBy { it.costType } }

    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.deckbuild_bg2),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
        // Složení + Mana křivka
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeckStats(deck, deckCards, Modifier.weight(1f))
            ManaCurveChart(deck, deckCards, Modifier.weight(1f))
        }

        // Šablony
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            presetTemplates.forEachIndexed { i, (name, _) ->
                PlainButton(
                    text      = name,
                    textColor = Gold.copy(alpha = 0.85f),
                    fontSize  = 8.sp,
                    paddingH  = 8.dp,
                    paddingV  = 4.dp,
                    onClick   = { onLoadPreset(i) }
                )
            }
        }

        SectionSeparator()

        // Card list + stats + buttons all inside one scrollable LazyColumn
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ResourceType.entries.forEach { type ->
                val cards = groups[type] ?: return@forEach
                item(key = "header_$type") {
                    val typeColor = resColor(type)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(painterResource(resourceIconRes(type)), contentDescription = null, modifier = Modifier.size(12.dp))
                        val groupCount = cards.sumOf { deck.cardCounts[it.id] ?: 0 }
                        Text(
                            "${when (type) {
                                ResourceType.MAGIC  -> LocalStrings.current.resMagic
                                ResourceType.ATTACK -> LocalStrings.current.resAttack
                                ResourceType.STONES -> LocalStrings.current.resStone
                                ResourceType.CHAOS  -> LocalStrings.current.resChaos
                            }.uppercase()} ($groupCount)",
                            color = typeColor.copy(alpha = 0.85f),
                            fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                        // Gradient fade line — od barvy typu do průhledna
                        Box(
                            Modifier.weight(1f).height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(typeColor.copy(alpha = 0.45f), Color.Transparent)
                                    )
                                )
                        )
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

            // Action buttons footer
            item(key = "actions_divider") {
                SectionSeparator()
            }
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlainButton(
                        text      = if (isActive) LocalStrings.current.dbActiveDeck else LocalStrings.current.dbSetActive,
                        modifier  = Modifier.weight(1f).fillMaxHeight(),
                        textColor = if (isActive || deck.isValid) TealLight else TextMuted,
                        fontSize  = 10.sp,
                        enabled   = !isActive && deck.isValid,
                        selected  = isActive,
                        paddingH  = 0.dp,
                        paddingV  = 0.dp,
                        onClick   = onSetActive
                    )
                    PlainButton(
                        text      = s.deckClear,
                        modifier  = Modifier.fillMaxHeight(),
                        textColor = AttackRed.copy(alpha = 0.75f),
                        fontSize  = 10.sp,
                        paddingH  = 14.dp,
                        paddingV  = 0.dp,
                        onClick   = onClear
                    )
                }
            }
            // bottom padding so last item isn't right at the edge
            item(key = "bottom_pad") { Spacer(Modifier.height(6.dp)) }
        }
        }
    }
}

@Composable
private fun DeckCardRow(card: Card, count: Int, onRemove: () -> Unit) {
    val costColor = resColor(card.costType)
    val artResId  = card.effectiveArtResId()

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF18121E))
            .border(1.dp, costColor.copy(alpha = 0.50f), RoundedCornerShape(4.dp))
            .clickable { onRemove() }
    ) {
        // ── Art peek: uprostřed vpravo, fade doleva ──────────────────────────
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .fillMaxHeight()
                .width(72.dp)
                .clipToBounds()
        ) {
            Image(
                painter      = painterResource(artResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = ArtDefaults.SCALE * card.artScale
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(
                            ((ArtDefaults.BIAS_X + card.artBiasX + 1f) / 2f).coerceIn(0f, 1f),
                            ((ArtDefaults.BIAS_Y + card.artBiasY + 1f) / 2f).coerceIn(0f, 1f)
                        )
                    }
            )
            // Gradient: art mizí doleva
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF18121E), Color.Transparent)))
            )
        }

        // ── Cost badge + název ────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(costColor.copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (card.isXCost) "X" else "${card.effectiveCost}",
                    color      = Color.White,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Center,
                    style = TextStyle(
                        platformStyle   = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim      = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
            Text(
                card.displayName,
                color    = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Count badge vpravo ────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 3.dp)
                .height(16.dp)
                .widthIn(min = 26.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF0D0A0E))
                .border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "x$count",
                color      = Gold,
                fontSize   = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    platformStyle   = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim      = LineHeightStyle.Trim.Both
                    )
                )
            )
        }
    }
}

@Composable
private fun ManaCurveChart(deck: Deck, deckCards: List<Card>, modifier: Modifier = Modifier) {
    // Bucket cards by cost: 0,1,2,3,4,5,6,7+
    val buckets = (0..7).map { bucket ->
        deckCards.filter { card ->
            if (bucket < 7) card.effectiveCost == bucket else card.effectiveCost >= 7
        }.sumOf { deck.cardCounts[it.id] ?: 0 }
    }
    val maxCount = buckets.maxOrNull()?.coerceAtLeast(1) ?: 1

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Mana křivka", color = TextMuted, fontSize = 7.sp, letterSpacing = 0.8.sp)
        // Bars
        Row(
            Modifier.fillMaxWidth().height(22.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { count ->
                val fillFraction = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(fillFraction.coerceAtLeast(if (count > 0) 0.04f else 0f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Gold.copy(alpha = 0.90f),
                                        Gold.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )
                }
            }
        }
        // X-axis labels
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            (0..7).forEach { i ->
                Text(
                    if (i < 7) "$i" else "7+",
                    modifier  = Modifier.weight(1f),
                    color     = TextMuted.copy(alpha = 0.45f),
                    fontSize  = 6.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DeckStats(deck: Deck, deckCards: List<Card>, modifier: Modifier = Modifier) {
    val byType = ResourceType.entries.associateWith { type ->
        deckCards.filter { it.costType == type }.sumOf { deck.cardCounts[it.id] ?: 0 }
    }
    val total = deck.totalCards.coerceAtLeast(1).toFloat()

    // 2×2 grid: pair resource types side by side
    val pairs = ResourceType.entries.chunked(2)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        pairs.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { type ->
                    val count = byType[type] ?: 0
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Image(
                            painterResource(resourceIconRes(type)),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp)
                        )
                        Box(
                            Modifier.weight(1f).height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(count / total).fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(resColor(type).copy(alpha = 0.70f))
                            )
                        }
                        Text(
                            "$count",
                            color    = resColor(type),
                            fontSize = 7.sp,
                            modifier = Modifier.width(12.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
