package com.example.termiti

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    is CardEffect.ConvertWallToCastle -> "Obrana"
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
internal fun Card.categories(): Set<String> =
    effects.mapNotNull { it.toCategory() }.toSet().ifEmpty { setOf("Ostatní") }

private fun Card.category() = categories().first()

/**
 * Lokalizuje auto-generovaný název balíčku ("Balíček 1" / "Deck 1" → aktivní jazyk).
 * Vlastní (přejmenované) názvy projdou beze změny.
 */
private val DEFAULT_DECK_NAME = Regex("^(?:Balíček|Deck) (\\d+)$")
fun localizedDeckName(name: String): String {
    val s = LanguageManager.currentStrings
    // Vestavěné názvy (startovní balíček, šablony) jsou uložené česky – přelož je.
    when (name) {
        "Začátečník"  -> return s.deckStarter
        "⚔️ Útočník"  -> return s.presetAttacker
        "⚔️ Útočník2" -> return s.presetAttacker2
        "🔮 Mágik"    -> return s.presetMage
        "🏰 Obránce"  -> return s.presetDefender
        "🏰 Obránce2" -> return s.presetDefender2
        "📚 Kartář"   -> return s.presetCardsmith
        "🕵️ Sabotér"  -> return s.presetSaboteur
    }
    val m = DEFAULT_DECK_NAME.find(name) ?: return name
    return s.deckDefaultName.format(m.groupValues[1].toInt())
}

// ─── Root ────────────────────────────────────────────────────────────────────
/**
 * @param rogue Roguelike režim: stejná obrazovka, ale upravují se tři uložené roguelike
 *   balíčky ([GameViewModel.roguePresets]) místo konstruovaných. Balíček má
 *   [RogueConfig.DECK_SIZE] karet a rozpočet vzácností [RogueConfig.BUDGET]; místo Šablon
 *   je v hlavičce tlačítko, které s hotovým balíčkem zahájí běh.
 */
@Composable
fun DeckBuilderScreen(viewModel: GameViewModel, onBack: () -> Unit, rogue: Boolean = false) {
    val decks: List<Deck> =
        if (rogue) viewModel.roguePresets.mapIndexed { i, p -> Deck(i, p.name, p.cardCounts) }
        else viewModel.decks
    val activeDeckIdx  = if (rogue) viewModel.rogueSlot.value else viewModel.activeDeckIndex.value
    val deckSize       = if (rogue) RogueConfig.DECK_SIZE else 30

    var editingIdx     by remember { mutableIntStateOf(activeDeckIdx) }
    val editingDeck    = decks[editingIdx]

    val setCount: (String, Int) -> Unit = { id, n ->
        if (rogue) viewModel.setRogueCardCount(editingIdx, id, n) else viewModel.setCardCount(editingIdx, id, n)
    }
    // Rozpočet vzácností (jen roguelike): legendární 4 · epická 2 · vzácná 1 · běžná 0
    val cardById    = remember(viewModel.allCards) { viewModel.allCards.associateBy { it.id } }
    val budgetSpent = if (!rogue) 0 else editingDeck.cardCounts.entries.sumOf { (id, n) ->
        (cardById[id]?.let { RogueConfig.rarityBudgetCost(it.rarity) } ?: 0) * n
    }
    val fitsBudget: (Card) -> Boolean = { c ->
        !rogue || budgetSpent + RogueConfig.rarityBudgetCost(c.rarity) <= RogueConfig.BUDGET
    }
    val deckComplete = editingDeck.totalCards == deckSize && (!rogue || budgetSpent <= RogueConfig.BUDGET)

    var previewCard    by remember { mutableStateOf<Card?>(null) }
    var profile        by remember { mutableStateOf(PlayerProfileManager.profile) }

    // Filter state
    var filterRes      by remember { mutableStateOf<ResourceType?>(null) }
    var filterUnlocked by remember { mutableStateOf(false) }
    var searchQuery    by remember { mutableStateOf("") }
    var filterCost     by remember { mutableStateOf<Int?>(null) }
    var filterRarity   by remember { mutableStateOf<Rarity?>(null) }

    var showPresets    by remember { mutableStateOf(false) }
    var showDeckPicker by remember { mutableStateOf(false) }
    /** Šablona čekající na potvrzení (přepsala by neprázdný balíček). */
    var pendingPreset  by remember { mutableStateOf<Int?>(null) }
    /** Dotaz při odchodu s rozdělaným balíčkem (1–29 karet). */
    var showIncomplete by remember { mutableStateOf(false) }
    val requestBack = {
        // Roguelike balíček se ukládá průběžně a nikam se „nedoplňuje" → bez dotazu
        if (!rogue && editingDeck.totalCards in 1..29) showIncomplete = true else onBack()
    }

    val filteredCards = remember(filterRes, filterUnlocked, searchQuery, filterCost, filterRarity, profile) {
        viewModel.allCards
            .filter { card ->
                card.effects.none { it is CardEffect.TrapOnDraw } &&
                !card.isPlaceholder &&
                (filterRes == null || card.costType == filterRes) &&
                (filterRarity == null || card.rarity == filterRarity) &&
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

    // Upravovaný balíček se stává aktivním (hraje se s ním), jakmile má 30 karet –
    // po výběru hotového balíčku i po doplnění poslední karty. Samostatné tlačítko
    // „Nastavit aktivní" proto není potřeba.
    LaunchedEffect(editingIdx, editingDeck.isValid) {
        if (rogue) viewModel.setRogueSlot(editingIdx)   // roguelike: vybraný balíček = ten, se kterým se půjde do běhu
        else if (editingDeck.isValid && editingIdx != activeDeckIdx) viewModel.setActiveDeck(editingIdx)
    }

    Box(Modifier.fillMaxSize()) {
        // Hnědá kůže z bg_plain (stejná rodina jako menu a profil). Zvětšení odsune
        // kamenný rám textury za okraj obrazovky – obsah jde až do kraje.
        Image(
            painter = painterResource(R.drawable.bg_plain),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.13f; scaleY = 1.16f },
            contentScale = ContentScale.FillBounds
        )
        Row(Modifier.fillMaxSize()) {

            // ── Left: album katalogu (listuje se po stránkách 4×2) ────────────
            Column(Modifier.weight(CATALOG_WEIGHT).fillMaxHeight()) {
                ResourceTabBar(
                    filterRes   = filterRes,
                    onResFilter = { filterRes = if (filterRes == it) null else it },
                    filterRarity   = filterRarity,
                    onRarityFilter = { filterRarity = if (filterRarity == it) null else it },
                    onBack      = requestBack
                )
                SectionSeparator()

                val pageCount  = maxOf(1, (filteredCards.size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE)
                val pagerState = rememberPagerState(pageCount = { pageCount })
                val scope      = rememberCoroutineScope()
                // Nový filtr = jiná sada karet → zpět na první stranu
                LaunchedEffect(filterRes, filterUnlocked, searchQuery, filterCost, filterRarity) { pagerState.scrollToPage(0) }

                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PageArrow("‹", enabled = pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                    HorizontalPager(
                        state    = pagerState,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) { page ->
                        AlbumPage(filteredCards.drop(page * CARDS_PER_PAGE).take(CARDS_PER_PAGE)) { card, scale ->
                            val count  = editingDeck.cardCounts[card.id] ?: 0
                            val isFull = editingDeck.totalCards >= deckSize || !fitsBudget(card)
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
                            AlbumCard(
                                card     = card,
                                count    = count,
                                usable   = usable,
                                isNew    = isNew,
                                canAdd   = count < usable && !isFull,
                                scale    = scale,
                                onAdd    = { setCount(card.id, count + 1) },
                                onDetail = {
                                    if (isNew) {
                                        PlayerProfileManager.markCardsSeen(setOf(card.id))
                                        profile = PlayerProfileManager.profile
                                    }
                                    previewCard = card
                                }
                            )
                        }
                    }
                    PageArrow("›", enabled = pagerState.currentPage < pageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }

                SectionSeparator()
                CatalogBottomBar(
                    filterCost     = filterCost,
                    onCostFilter   = { filterCost = if (filterCost == it) null else it },
                    searchQuery    = searchQuery,
                    onSearchChange = { searchQuery = it },
                    filterUnlocked = filterUnlocked,
                    onUnlocked     = { filterUnlocked = !filterUnlocked },
                    page           = pagerState.currentPage + 1,
                    pageCount      = pageCount
                )
            }

            VerticalSeparatorImage()

            // ── Right: top bar + deck panel ───────────────────────────
            Column(Modifier.weight(DECK_WEIGHT).fillMaxHeight()) {
                TopBar(
                    editingDeck = editingDeck,
                    deckIconRes = deckIconRes(editingDeck, viewModel.allCards),
                    onPickDeck  = { showDeckPicker = true },
                    onShowPresets = { showPresets = true },
                    onRename    = { if (rogue) viewModel.renameRoguePreset(editingIdx, it) else viewModel.renameDeck(editingIdx, it) },
                    // Roguelike: místo Šablon tlačítko, které s hotovým balíčkem zahájí běh
                    startRunEnabled = if (rogue) deckComplete else null,
                    onStartRun  = { viewModel.setRogueSlot(editingIdx); viewModel.startRogueRun() }
                )
                SectionSeparator()
                DeckPanel(
                    deck            = editingDeck,
                    allCards        = viewModel.allCards,
                    onRemove        = { cardId ->
                        val c = editingDeck.cardCounts[cardId] ?: 0
                        if (c > 0) setCount(cardId, c - 1)
                    },
                    deckSize        = deckSize,
                    complete        = deckComplete,
                    budget          = if (rogue) budgetSpent to RogueConfig.BUDGET else null,
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
                    val pvCount  = editingDeck.cardCounts[card.id] ?: 0
                    val pvUsable = when {
                        profile?.allCardsUnlocked == true ||
                        CardCollectionManager.isBasicCard(card) -> card.rarity.maxCopies
                        else -> minOf(
                            profile?.cardCollection?.getOrDefault(card.id, 0) ?: 0,
                            card.rarity.maxCopies
                        )
                    }
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
                            viewModel.trimDecksToOwned(card.id)
                        },
                        onClose = { previewCard = null },
                        deckCount    = pvCount,
                        deckMax      = pvUsable,
                        canAddToDeck = pvCount < pvUsable && editingDeck.totalCards < deckSize && fitsBudget(card),
                        onAddToDeck  = {
                            setCount(card.id, pvCount + 1)
                        },
                        onRemoveFromDeck = {
                            if (pvCount > 0) setCount(card.id, pvCount - 1)
                        }
                    )
                }
            }
        }

        // ── Výběr balíčku k úpravě ────────────────────────────────────────────
        if (showDeckPicker) {
            DeckPickerOverlay(
                decks      = decks,
                allCards   = viewModel.allCards,
                editingIdx = editingIdx,
                activeIdx  = activeDeckIdx,
                deckSize   = deckSize,
                onPick     = { idx ->
                    editingIdx = idx          // aktivním se stane sám, je-li hotový (viz LaunchedEffect)
                    showDeckPicker = false
                },
                onDismiss  = { showDeckPicker = false }
            )
        }

        // ── Šablony balíčků ───────────────────────────────────────────────────
        if (showPresets) {
            PresetOverlay(
                presets   = viewModel.presetTemplates.map { it.first },
                onPick    = {
                    showPresets = false
                    // Prázdný balíček není co přepsat → rovnou; jinak se nejdřív zeptej
                    if (editingDeck.totalCards == 0) viewModel.loadPreset(editingIdx, it) else pendingPreset = it
                },
                onDismiss = { showPresets = false }
            )
        }
        pendingPreset?.let { idx ->
            val s = LocalStrings.current
            ChoiceOverlay(
                title     = s.dbPresetConfirmTitle,
                message   = s.dbPresetConfirmMsg.format(deckTitle(viewModel.presetTemplates[idx].first), deckTitle(editingDeck.name)),
                onDismiss = { pendingPreset = null },
                choices   = listOf(
                    Choice(s.cancel, TextMuted) { pendingPreset = null },
                    Choice(s.dbPresetConfirmYes, Gold) { viewModel.loadPreset(editingIdx, idx); pendingPreset = null }
                )
            )
        }

        // ── Odchod s rozdělaným balíčkem ──────────────────────────────────────
        if (showIncomplete) {
            val s = LocalStrings.current
            ChoiceOverlay(
                title     = s.dbIncompleteTitle,
                message   = s.dbIncompleteMsg.format(deckTitle(editingDeck.name), editingDeck.totalCards),
                onDismiss = { showIncomplete = false },
                choices   = listOf(
                    Choice(s.dbIncompleteStay, TextMuted) { showIncomplete = false },
                    Choice(s.dbIncompleteLeave, AttackRed.copy(alpha = 0.85f)) { showIncomplete = false; onBack() },
                    // Doplní a zůstane v editoru, ať hráč vidí, co přibylo
                    Choice(s.dbIncompleteFill, Gold) { viewModel.autoCompleteDeck(editingIdx); showIncomplete = false }
                )
            )
        }

        // ── Hlášení o srovnání limitů kopií ──────────────────────────────────
        // Kreslí se NAD náhledem karty: je to jednorázová informace o tom, že
        // hráči něco zmizelo z kolekce, a musí ji vzít na vědomí dřív, než
        // začne balíček upravovat.
        val limitReport by viewModel.cardLimitReport
        limitReport?.let { report ->
            val cardMap = remember(viewModel.allCards) { viewModel.allCards.associateBy { it.id } }
            val rows = remember(report) {
                report.changes.mapNotNull { ch -> cardMap[ch.cardId]?.let { it to ch } }
            }
            if (rows.isEmpty()) {
                // Všechny dotčené karty zmizely z katalogu → není co ukazovat
                LaunchedEffect(report) { viewModel.dismissCardLimitReport() }
            } else {
                CardLimitChangeOverlay(
                    changes     = rows,
                    dustGained  = report.dustGained,
                    deckRemoved = report.deckCardsRemoved,
                    onDismiss   = {
                        SoundManager.playMenuTap()
                        viewModel.dismissCardLimitReport()
                        profile = PlayerProfileManager.profile   // prach se změnil
                    }
                )
            }
        }
    }
}

/**
 * Název balíčku vedle ikony převládající suroviny (hlavička, výběr balíčku): bez
 * úvodního emoji, které si název nese ze šablony („⚔️ Útočník") – jinak by měl ikony dvě.
 */
internal fun deckTitle(name: String): String =
    localizedDeckName(name).dropWhile { !it.isLetterOrDigit() }.ifEmpty { localizedDeckName(name) }

// ─── Top Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    editingDeck: Deck,
    @DrawableRes deckIconRes: Int,
    onPickDeck: () -> Unit,
    onShowPresets: () -> Unit,
    onRename: (String) -> Unit,
    /** null = konstruovaný režim (tlačítko Šablony); jinak roguelike tlačítko pro zahájení běhu. */
    startRunEnabled: Boolean? = null,
    onStartRun: () -> Unit = {}
) {
    var isEditingName  by remember(editingDeck.id) { mutableStateOf(false) }
    var nameInput      by remember(editingDeck.name) { mutableStateOf(editingDeck.name) }
    val focusRequester = remember { FocusRequester() }

    Row(
        Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT).background(DbBar.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                cursorBrush     = SolidColor(Gold),
                modifier = Modifier
                    .width(150.dp)
                    .focusRequester(focusRequester)
                    .height(28.dp)
                    .buttonTexture(R.drawable.plain_button)
                    .wrapContentHeight()
                    .padding(horizontal = 10.dp)
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
            // Ikona převládající suroviny + název; klik otevře výběr balíčku
            Box(
                Modifier
                    .widthIn(min = 150.dp, max = 190.dp)   // bez weight: jinak by se o místo dělil s mezerou před Šablonami
                    .height(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { SoundManager.playMenuTap(); onPickDeck() },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.matchParentSize().buttonTexture(R.drawable.plain_button))
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(painterResource(deckIconRes), contentDescription = null, modifier = Modifier.size(15.dp), contentScale = ContentScale.Fit)
                    Text(
                        deckTitle(editingDeck.name),
                        color      = TextPrimary,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }
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
        if (startRunEnabled != null) {
            PlainButton(
                text      = LocalStrings.current.rogueStartRun,
                modifier  = Modifier.width(96.dp).height(28.dp),
                textColor = TealLight,
                fontSize  = 9.sp,
                paddingH  = 4.dp,
                paddingV  = 0.dp,
                enabled   = startRunEnabled,
                onClick   = onStartRun
            )
        } else {
            PlainButton(
                text      = LocalStrings.current.dbTemplates,
                modifier  = Modifier.width(84.dp).height(28.dp),
                textColor = Gold.copy(alpha = 0.9f),
                fontSize  = 9.sp,
                paddingH  = 4.dp,
                paddingV  = 0.dp,
                onClick   = onShowPresets
            )
        }
    }
}

/**
 * Surovina, která v balíčku převládá (nejvíc karet, bez shody na prvním místě),
 * nebo null. Podle ní má balíček ikonu v hlavičce i ve výběru balíčků.
 */
internal fun Deck.dominantResource(allCards: List<Card>): ResourceType? {
    val byType = allCards
        .filter { (cardCounts[it.id] ?: 0) > 0 }
        .groupBy { it.costType }
        .mapValues { (_, cards) -> cards.sumOf { cardCounts[it.id] ?: 0 } }
    val top = byType.maxByOrNull { it.value } ?: return null
    return top.key.takeIf { byType.values.count { it == top.value } == 1 }
}

@DrawableRes
private fun deckIconRes(deck: Deck, allCards: List<Card>): Int =
    deck.dominantResource(allCards)?.let { resourceIconRes(it) } ?: R.drawable.card_icon

// ─── Rozložení ───────────────────────────────────────────────────────────────
/** Poměr šířky katalogu a panelu balíčku (pruh filtrů má pevnou šířku [RAIL_WIDTH]). */
private const val CATALOG_WEIGHT = 1.5f
private const val DECK_WEIGHT    = 1f
/** Album: karet na řádek a na stránku (2 řádky). */
private const val CARDS_PER_ROW  = 4
/** Levé odsazení obsahu horní i spodní lišty – v zákrytu s levým okrajem první karty alba. */
private val BAR_INSET = 33.dp
private const val CARDS_PER_PAGE = 8
private val RAIL_CHIP  = 30.dp
/** Výška horní lišty katalogu i hlavičky balíčku – oddělovače pod nimi musí být v jedné lince. */
private val TOP_BAR_HEIGHT = 40.dp
/** Podklad lišt – teplá tmavá, ladí s hnědým pozadím. */
private val DbBar = Color(0xFF0C0806)

// ─── Záložky surovin ──────────────────────────────────────────────────────────
/**
 * Horní lišta katalogu: Zpět, filtr suroviny a filtr vzácnosti. Suroviny jsou jen ikony
 * (dřív tlačítka s názvem), aby se vedle nich vešly čtyři drahokamy vzácností.
 * Klik na aktivní filtr ho zruší.
 */
@Composable
private fun ResourceTabBar(
    filterRes: ResourceType?,
    onResFilter: (ResourceType) -> Unit,
    filterRarity: Rarity?,
    onRarityFilter: (Rarity) -> Unit,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .background(DbBar.copy(alpha = 0.55f))
            .padding(start = BAR_INSET, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        PlainButton(
            text      = s.back,
            modifier  = Modifier.width(76.dp).height(30.dp),
            textColor = TextMuted,
            fontSize  = 9.sp,
            paddingH  = 4.dp,
            paddingV  = 0.dp,
            onClick   = onBack
        )
        // Obě skupiny drží pevnou šířku a sedí uprostřed zbylého místa
        Spacer(Modifier.weight(1f))
        ResourceType.entries.forEach { type ->
            IconChip(resourceIconRes(type), filterRes == type, size = 30.dp) { onResFilter(type) }
        }
        Spacer(Modifier.width(10.dp))
        Rarity.entries.forEach { r ->
            RarityChip(r, filterRarity == r, size = 30.dp) { onRarityFilter(r) }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Filtr vzácnosti: destička s drahokamem dané vzácnosti (výřez z rámu karty – [RarityGem]). */
@Composable
private fun RarityChip(rarity: Rarity, active: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { SoundManager.playMenuTap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().buttonTexture(R.drawable.plain_button_mini, alpha = if (active) 1f else 0.6f))
        Box(Modifier.alpha(if (active) 1f else 0.6f)) { RarityGem(rarity, size * 0.56f) }
        if (active) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color        = ChaosOrange,
                    style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}

/** Šipka listování albem. */
@Composable
private fun PageArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 2.dp).alpha(if (enabled) 1f else 0.25f)) {
        CostChip(label = label, active = false, dimInactive = false, width = 24.dp) { if (enabled) onClick() }
    }
}

/** Jedna stránka alba: 2 řádky po [CARDS_PER_ROW] kartách, zmenšených tak, aby se vešly celé. */
@Composable
private fun AlbumPage(cards: List<Card>, cardContent: @Composable (Card, Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp)) {
        val gap   = 5.dp
        val rows  = CARDS_PER_PAGE / CARDS_PER_ROW
        val scale = minOf(
            (maxWidth  - gap * (CARDS_PER_ROW - 1)) / CARDS_PER_ROW / 100.dp,
            (maxHeight - gap * (rows - 1)) / rows / 140.dp
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (c in 0 until CARDS_PER_ROW) {
                        val card = cards.getOrNull(r * CARDS_PER_ROW + c)
                        Box(Modifier.size(100.dp * scale, 140.dp * scale)) {
                            if (card != null) cardContent(card, scale)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Karta v albu. Klik přidá kopii do balíčku; když přidat nejde (zamčená, plný počet
 * kopií, plný balíček), otevře detail. Podržení otevře detail vždy (výroba, rozebrání).
 * Tečky vpravo nahoře: kopie v balíčku / vlastněné / chybějící – viz [CopyDots].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    card: Card,
    count: Int,
    usable: Int,
    isNew: Boolean,
    canAdd: Boolean,
    scale: Float,
    onAdd: () -> Unit,
    onDetail: () -> Unit
) {
    val isLocked = usable == 0 && !CardCollectionManager.isBasicCard(card)
    val shape    = RoundedCornerShape(6.dp * scale)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .combinedClickable(
                onClick     = { SoundManager.playMenuTap(); if (canAdd) onAdd() else onDetail() },
                onLongClick = onDetail
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.requiredSize(100.dp, 140.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            CardPreview(card = card)
        }
        when {
            isNew     -> Box(Modifier.matchParentSize().border(2.dp, Gold.copy(alpha = 0.85f), shape))
            count > 0 -> Box(Modifier.matchParentSize().border(2.dp, resColor(card.costType).copy(alpha = 0.85f), shape))
        }
        if (isLocked) {
            Box(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.lock_icon), contentDescription = null, modifier = Modifier.size(22.dp))
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                CopyDots(maxCopies = card.rarity.maxCopies, inDeck = count, usable = usable, rarityColor = rarityColor(card.rarity))
            }
        }
        if (isNew) NewBadge(Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp))
    }
}

/** Čtvercový chip s ikonou (stejná textura a oranžový obrys jako [CostChip]). */
@Composable
private fun IconChip(@DrawableRes iconRes: Int, active: Boolean, size: Dp = RAIL_CHIP, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { SoundManager.playMenuTap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().buttonTexture(R.drawable.plain_button_mini, alpha = if (active) 1f else 0.6f))
        Image(
            painter            = painterResource(iconRes),
            contentDescription = null,
            modifier           = Modifier.size(size * 0.5f).alpha(if (active) 1f else 0.55f),
            contentScale       = ContentScale.Fit
        )
        if (active) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color        = ChaosOrange,
                    style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}

// ─── Cost Chip ────────────────────────────────────────────────────────────────
@Composable
internal fun CostChip(
    label: String,
    active: Boolean,
    width: androidx.compose.ui.unit.Dp = 31.dp,
    /** false = neaktivní chip není ztlumený (akční tlačítko, ne přepínač). */
    dimInactive: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = width, height = width)
            .alpha(if (active || !dimInactive) 1f else 0.5f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { SoundManager.playMenuTap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().buttonTexture(R.drawable.plain_button_mini))
        Text(
            text       = label,
            color      = if (active) Gold else if (dimInactive) TextMuted else TextPrimary,
            fontSize   = if (dimInactive) 9.sp else 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign  = TextAlign.Center
        )
        if (active) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color        = ChaosOrange,
                    style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}

// ─── Spodní lišta katalogu ────────────────────────────────────────────────────
/** Filtr podle ceny, hledání slova v názvu i textu karty, zámek (jen odemčené) a číslo strany. */
@Composable
private fun CatalogBottomBar(
    filterCost: Int?,
    onCostFilter: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filterUnlocked: Boolean,
    onUnlocked: () -> Unit,
    page: Int,
    pageCount: Int
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .background(DbBar.copy(alpha = 0.55f))
            .padding(start = BAR_INSET, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        (0..7).forEach { cost ->
            CostChip(label = if (cost == 7) "7+" else "$cost", active = filterCost == cost, width = 27.dp) { onCostFilter(cost) }
        }
        // Vlevo skupina cen, vpravo hledání + zámek + strana; volné místo je mezi nimi
        Spacer(Modifier.weight(1f))
        SearchField(searchQuery, onSearchChange)
        Spacer(Modifier.width(4.dp))
        IconChip(R.drawable.lock_icon, filterUnlocked, size = 27.dp, onClick = onUnlocked)
        Text(
            "$page / $pageCount",
            color      = TextMuted,
            fontSize   = 9.sp,
            textAlign  = TextAlign.End,
            modifier   = Modifier.width(38.dp)
        )
    }
}

/**
 * Hledání na herní destičce (plain_button_longer) místo plochého rámečku: lupa vlevo,
 * text uprostřed, křížek (cross_icon) vpravo uvnitř pole – pole tak při psaní nemění šířku.
 */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val active = query.isNotBlank()
    BasicTextField(
        value           = query,
        onValueChange   = onChange,
        singleLine      = true,
        textStyle       = TextStyle(color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        cursorBrush     = SolidColor(Gold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier        = Modifier.width(150.dp).height(27.dp),
        decorationBox   = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .buttonTexture(R.drawable.plain_button_longer)
                    .padding(start = 9.dp, end = 7.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MagnifierIcon(if (active) Gold else TextMuted, Modifier.size(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (!active) {
                        Text(LocalStrings.current.dbSearchHint, color = TextMuted.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
                    }
                    inner()
                }
                if (active) {
                    Image(
                        painterResource(R.drawable.cross_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { SoundManager.playMenuTap(); onChange("") }
                    )
                }
            }
        }
    )
}

/** Lupa kreslená čarou – mezi ikonami hry žádná není. */
@Composable
private fun MagnifierIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.15f
        val r      = size.minDimension * 0.32f
        val c      = Offset(r + stroke / 2, r + stroke / 2)
        drawCircle(color, r, c, style = Stroke(stroke))
        val k = r * 0.7071f
        drawLine(color, Offset(c.x + k, c.y + k), Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, StrokeCap.Round)
    }
}

// ─── Výběr balíčku ────────────────────────────────────────────────────────────
@Composable
private fun DeckPickerOverlay(
    decks: List<Deck>,
    allCards: List<Card>,
    editingIdx: Int,
    activeIdx: Int,
    deckSize: Int = 30,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CampaignTitle(s.dbPickDeck, fontSize = 24.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                decks.forEachIndexed { i, deck ->
                    val iconRes = remember(deck.cardCounts) { deckIconRes(deck, allCards) }
                    val shape   = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .size(width = 184.dp, height = 124.dp)
                            .then(if (i == editingIdx) Modifier.border(2.dp, Gold, shape) else Modifier)
                            .clip(shape)
                            .clickable { SoundManager.playMenuTap(); onPick(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter            = painterResource(R.drawable.mulligan_background),
                            contentDescription = null,
                            modifier           = Modifier.matchParentSize(),
                            contentScale       = ContentScale.FillBounds
                        )
                        // Pevné výšky řádků: výchozí řádkování Textu je vyšší než písmo a obsah
                        // pak přetekl přes zdobený rám (ikona nahoře, „aktivní" dole).
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp), contentScale = ContentScale.Fit)
                            Text(
                                deckTitle(deck.name),
                                color = TextPrimary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 130.dp)
                            )
                            Text(
                                "${deck.totalCards} / $deckSize",
                                color = if (deck.totalCards == deckSize) HpGreen else Gold.copy(alpha = 0.8f),
                                fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold
                            )
                            // Řádek drží místo i u neaktivních balíčků, ať jsou všechny dlaždice stejně rozložené
                            Text(
                                if (i == activeIdx) s.dbActiveShort else " ",
                                color = TealLight, fontSize = 8.sp, lineHeight = 10.sp
                            )
                        }
                    }
                }
            }
            PlainButton(
                text      = s.back2,
                modifier  = Modifier.width(110.dp).height(32.dp),
                textColor = TextMuted,
                fontSize  = 10.sp,
                paddingH  = 6.dp,
                paddingV  = 0.dp,
                onClick   = onDismiss
            )
        }
    }
}

// ─── Dotaz s volbami ──────────────────────────────────────────────────────────
internal class Choice(val label: String, val color: Color, val onClick: () -> Unit)

/** Překryv s nadpisem, větou a řadou tlačítek (potvrzení šablony, odchod s rozdělaným balíčkem,
 *  hláška o neúplném balíčku před hrou – MainActivity). */
@Composable
internal fun ChoiceOverlay(title: String, message: String, onDismiss: () -> Unit, choices: List<Choice>) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CampaignTitle(title, fontSize = 24.sp)
            Text(
                message,
                color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.forEach { c ->
                    PlainButton(
                        text      = c.label,
                        modifier  = Modifier.width(120.dp).height(34.dp),
                        textColor = c.color,
                        fontSize  = 11.sp,
                        paddingH  = 6.dp,
                        paddingV  = 0.dp,
                        onClick   = c.onClick
                    )
                }
            }
        }
    }
}

// ─── Šablony balíčků ──────────────────────────────────────────────────────────
@Composable
private fun PresetOverlay(presets: List<String>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CampaignTitle(LocalStrings.current.dbTemplates, fontSize = 24.sp)
            presets.withIndex().chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (i, name) ->
                        PlainButton(
                            text      = localizedDeckName(name),
                            modifier  = Modifier.width(132.dp).height(36.dp),
                            textColor = Gold,
                            fontSize  = 10.sp,
                            paddingH  = 6.dp,
                            paddingV  = 0.dp,
                            onClick   = { onPick(i) }
                        )
                    }
                }
            }
            PlainButton(
                text      = LocalStrings.current.back2,
                modifier  = Modifier.width(110.dp).height(32.dp),
                textColor = TextMuted,
                fontSize  = 10.sp,
                paddingH  = 6.dp,
                paddingV  = 0.dp,
                onClick   = onDismiss
            )
        }
    }
}

@Composable
internal fun FilterChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(iconRes = null, label = label, active = active, color = color, onClick = onClick)
}

@Composable
internal fun FilterChip(@DrawableRes iconRes: Int?, label: String, active: Boolean, color: Color, onClick: () -> Unit) {
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
            // Bitmapy z CardBitmapCache: zmenšené na velikost dlaždice a dekódované
            // mimo UI vlákno – painterResource je dekódoval zvětšené podle hustoty
            // displeje a synchronně, což při rychlém rolování sekalo.
            rememberCardBitmap(artResId, 100.dp, 90.dp)?.let { art ->
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = artModifier(card),
                    contentScale = ContentScale.Crop,
                    alignment = artAlignment(card)
                )
            }
        }
        // Rám
        if (frameResId != 0) {
            rememberCardBitmap(frameResId, 100.dp, 140.dp)?.let { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        // Překryv rarity
        val rarityOverlayId = rarityOverlayResource(card.rarity)
        if (rarityOverlayId != 0) {
            rememberCardBitmap(rarityOverlayId, 100.dp, 140.dp)?.let { overlay ->
                Image(
                    bitmap = overlay,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
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
            val descText = card.displayDescription
            val parsedDesc = remember(descText) { parseCardDesc(descText) }
            Text(parsedDesc, color = Color(0xFFDDD0B0), fontSize = 7.sp,
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
    onClose    : () -> Unit,
    // Ovládání balíčku přímo z rozkliku – ať se nemusí náhled zavírat
    deckCount  : Int,
    deckMax    : Int,
    canAddToDeck: Boolean,
    onAddToDeck : () -> Unit,
    onRemoveFromDeck: () -> Unit
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

    // Panel je scrollovatelný – zabraňuje oříznutí obsahu na nízkých obrazovkách (landscape).
    // Herní textura bočního panelu (bronzový rám s medailony v rozích) kreslená po částech,
    // aby se rohy nedeformovaly – viz sidePanelBackground. Větší padding drží text mimo rám.
    Box(
        modifier = Modifier
            .width(210.dp)
            .heightIn(max = 340.dp)
            .sidePanelBackground()
    ) {
        // Vnější sloupec: obsah (roluje se, když se nevejde) + tlačítko připnuté dole.
        // Tlačítko je mimo rolování, takže ho žádná změna obsahu neodsune z dohledu.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Název – na střed a stejně jako na kartě (bílý, černý obrys) ────
            OutlinedTitle(card.displayName, Modifier.fillMaxWidth())

            // ── Rarita (drahokam z rámu karty) · cena · vlastněné kopie ──────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
            ) {
                RarityGem(card.rarity, 15.dp)
                Text(card.rarity.displayLabel, color = rc, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Image(painterResource(resourceIconRes(card.costType)), contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    if (card.isXCost) "X" else "${card.cost}",
                    color = costColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                if (collectible) {
                    Spacer(Modifier.width(5.dp))
                    Image(painterResource(R.drawable.card_icon), contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(
                        "$realOwned/${card.rarity.maxCopies}",
                        color = if (realOwned >= card.rarity.maxCopies) HpGreen else TealLight,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            PanelSeparator()

            // ── V balíčku ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    LocalStrings.current.dbInDeck,
                    color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                DeckCountStepper(
                    count    = deckCount,
                    maxCount = deckMax,
                    canAdd   = canAddToDeck,
                    onAdd    = onAddToDeck,
                    onRemove = onRemoveFromDeck
                )
            }

            if (!isBasic) {
                PanelSeparator()

                val dismantleAccent = Color(0xFFE57373)
                // Zůstatek prachu – jen když se s ním dá něco dělat. Čekající výrobu / rozebrání
                // ukazuje TADY jako „135 → 95", ne v novém řádku: řádek navíc by po kliknutí
                // na + posunul všechno pod sebou včetně tlačítka Potvrdit.
                if (collectible) {
                    val dustAfter = dust - pendingCraft * card.rarity.craftCost + pendingDismantle * card.rarity.dustValue
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End)
                    ) {
                        Image(painterResource(R.drawable.dust_icon), contentDescription = null, modifier = Modifier.size(12.dp))
                        Text("$dust", color = if (hasPending) TextMuted else DustColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (hasPending) {
                            Text("→", color = TextMuted, fontSize = 10.sp)
                            Text(
                                "$dustAfter",
                                color = if (pendingCraft > 0) DustColor else dismantleAccent,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Vyrobit ──────────────────────────────────────────────────
                ActionCounter(
                    label       = LocalStrings.current.dbCraft,
                    dustText    = "${card.rarity.craftCost}",
                    iconRes     = R.drawable.hammer_icon,
                    accent      = DustColor,
                    count       = pendingCraft,
                    maxCount    = maxCraft,
                    onDecrement = { pendingCraft-- },
                    onIncrement = { pendingCraft++; pendingDismantle = 0 }
                )
                // ── Rozebrat ──────────────────────────────────────────────────
                ActionCounter(
                    label       = LocalStrings.current.dbDisassemble,
                    dustText    = "+${card.rarity.dustValue}",
                    iconRes     = R.drawable.explode_icon,
                    accent      = dismantleAccent,
                    count       = pendingDismantle,
                    maxCount    = maxDismantle,
                    onDecrement = { pendingDismantle-- },
                    onIncrement = { pendingDismantle++; pendingCraft = 0 }
                )
            }
        }   // konec rolované části

            // ── Hotovo – aplikuje pending akci a zavře panel (připnuté dole) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .buttonTexture(R.drawable.plain_button_longer)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        SoundManager.playMenuTap()
                        repeat(pendingCraft) { onCraft() }
                        repeat(pendingDismantle) { onDismantle() }
                        onClose()
                    }
                    .padding(vertical = 9.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Image(painterResource(R.drawable.check_icon), contentDescription = null, modifier = Modifier.size(13.dp))
                Text(
                    if (hasPending) LocalStrings.current.dbConfirm else LocalStrings.current.dbDone,
                    color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val DustColor = Color(0xFFB39DDB)

/**
 * Pozadí z bg_side_panels.png (336×704) pro panel libovolné výšky bez deformace rohů.
 *
 * FillBounds by obrázek 1 : 2,1 natáhl do tvaru panelu (např. 210×300 = 1 : 1,4) a kulaté
 * medailony v rozích by byly šišaté. Proto se kreslí po třech pásech v jednotném měřítku
 * podle šířky: horní a dolní pás s rohy a ornamenty v přesném poměru stran, natahuje se
 * jen střední pás – rovné bočnice.
 */
@Composable
private fun Modifier.sidePanelBackground(): Modifier {
    val bmp = ImageBitmap.imageResource(R.drawable.bg_side_panels)
    return drawBehind {
        val scale  = size.width / bmp.width
        // Rohy s ornamenty zabírají horních a dolních ~112 px z výšky 704
        val capSrc = (bmp.height * 112f / 704f).roundToInt()
        val w      = size.width.roundToInt()
        val h      = size.height.roundToInt()
        val capDst = (capSrc * scale).roundToInt().coerceAtMost(h / 2)
        drawImage(bmp, IntOffset(0, 0), IntSize(bmp.width, capSrc),
                  IntOffset(0, 0), IntSize(w, capDst))
        drawImage(bmp, IntOffset(0, capSrc), IntSize(bmp.width, bmp.height - 2 * capSrc),
                  IntOffset(0, capDst), IntSize(w, (h - 2 * capDst).coerceAtLeast(0)))
        drawImage(bmp, IntOffset(0, bmp.height - capSrc), IntSize(bmp.width, capSrc),
                  IntOffset(0, h - capDst), IntSize(w, capDst))
    }
}

/** Název ve stylu názvu na kartě (ArcCardName): tučný bílý s černým obrysem, na střed. */
@Composable
private fun OutlinedTitle(text: String, modifier: Modifier = Modifier) {
    val base = TextStyle(
        fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
    )
    val strokePx = with(LocalDensity.current) { 17.sp.toPx() * 0.28f }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text, maxLines = 2, modifier = Modifier.fillMaxWidth(),
            style = base.copy(
                color = Color.Black,
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx, join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        )
        Text(text, maxLines = 2, modifier = Modifier.fillMaxWidth(), style = base.copy(color = Color.White))
    }
}

/**
 * Drahokam rarity – výřez z overlaye rámu karty (rarity_*.png, 800×1195, drahokam
 * nahoře uprostřed: x 355–445, y 4–94). Stejný postup jako CardCostBadge.
 */
@Composable
private fun RarityGem(rarity: Rarity, size: Dp) {
    val bmp = ImageBitmap.imageResource(rarityOverlayResource(rarity))
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val sx = bmp.width / 800f
        val sy = bmp.height / 1195f
        drawImage(
            image     = bmp,
            srcOffset = IntOffset((355 * sx).roundToInt(), (4 * sy).roundToInt()),
            srcSize   = IntSize((90 * sx).roundToInt(), (90 * sy).roundToInt()),
            dstSize   = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
        )
    }
}

/** Oddělovač sekcí panelu – herní textura místo čáry. */
@Composable
private fun PanelSeparator() {
    Image(
        painter            = painterResource(R.drawable.bg_separator),
        contentDescription = null,
        modifier           = Modifier.fillMaxWidth().height(3.dp),
        contentScale       = ContentScale.FillBounds
    )
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
/** Řádek na herním panelu: ikona akce, název, cena/zisk v prachu (ikona prachu) a [−] n [+]. */
@Composable
private fun ActionCounter(
    label      : String,
    dustText   : String,
    accent     : Color,
    count      : Int,
    maxCount   : Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    @DrawableRes iconRes: Int
) {
    val active    = count > 0
    val available = active || maxCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .buttonTexture(R.drawable.plain_button_longer)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painterResource(iconRes), contentDescription = null,
            modifier = Modifier.size(18.dp).alpha(if (available) 1f else 0.45f)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                label,
                color      = if (available) accent else TextMuted,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Image(painterResource(R.drawable.dust_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                Text(dustText, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        CountBtn("−", enabled = count > 0, onClick = onDecrement)
        Text(
            "$count",
            color    = if (active) accent else TextMuted,
            fontSize = 11.sp,
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
internal fun CatalogCardItem(
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
        Box(itemModifier) {
            Column(
                Modifier
                    .background(Color(0xE60E0A08))
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
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.65f)),
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
    CostChip(label = label, active = enabled, width = 26.dp, onClick = { if (enabled) onClick() })
}

/**
 * Ovládání počtu kopií karty v balíčku: [−] n / max [+].
 *
 * Sdílí ho dlaždice v katalogu i rozklik karty (deck builder i roguelike
 * draft), aby se karta dala přidat i odtud a nebylo nutné náhled zavírat.
 */
@Composable
internal fun DeckCountStepper(
    count: Int,
    maxCount: Int,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CountBtn("−", enabled = count > 0, onClick = onRemove)
        Text(
            "$count / $maxCount",
            color      = if (count > 0) TealLight else TextMuted,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
        CountBtn("+", enabled = canAdd, onClick = onAdd)
    }
}

// ─── Deck Panel ───────────────────────────────────────────────────────────────
@Composable
private fun DeckPanel(
    deck: Deck,
    allCards: List<Card>,
    onRemove: (String) -> Unit,
    deckSize: Int = 30,
    complete: Boolean = deck.isValid,
    /** Roguelike rozpočet vzácností (utraceno, strop), jinak null. */
    budget: Pair<Int, Int>? = null,
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
        // Bez vlastní textury: rám bg_side_panels těsně vedle dělicí čáry ji zdvojoval.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
        // Seznam karet. Složení + mana křivka jsou jeho první položka – odjedou
        // se scrollem a seznam pak má celou výšku panelu.
        val listState = rememberLazyListState()
        LazyColumn(
            Modifier.weight(1f).scrollRail(listState),
            state               = listState,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item(key = "stats") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 3.dp)
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DeckStats(deck, deckCards, Modifier.weight(1f), deckSize, complete, budget)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Gold.copy(alpha = 0.18f))
                    )
                    ManaCurveChart(deck, deckCards, Modifier.weight(1f))
                }
            }
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

            item(key = "bottom_pad") { Spacer(Modifier.height(4.dp)) }
        }

        }
    }
}

/**
 * Cenovka karty jako malý kruhový výřez ze skutečného rámu karty (card_frame_magic/
 * attack/stones/chaos.png) – stejná grafika jako "kolečko" na herní kartě (viz
 * GameCardView.kt CardViewTextured/CardPreview: cena je vždy jen text přes kruh
 * NAKRESLENÝ v rámu, ne samostatný flat kruh). Referenční geometrie: na kartě
 * 100×140dp sedí ten kruh na offsetu (1.5dp, 2dp) o velikosti 18dp – tady se
 * stejný poměr jen přeškáluje na [size], ať vizuálně sedí i mimo plnou kartu.
 */
@Composable
private fun CardCostBadge(card: Card, size: Dp = 20.dp) {
    val context = LocalContext.current
    val frameResId = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
    }
    Box(modifier = Modifier.size(size).clip(CircleShape)) {
        if (frameResId != 0) {
            // Canvas + drawImage se zdrojovým/cílovým obdélníkem – přímý výřez bitmapy
            // podle pixelů, ne přes Modifier.size/offset/align (ty na malých velikostech
            // spolehlivě neořezávaly správně, viz předchozí pokusy). Zdrojový obdélník je
            // stejný poměr jako na referenční kartě 100×140dp: kolečko sedí na (1.5,2)dp
            // o velikosti 18×18dp, přepočteno na skutečné pixely bitmapy.
            val bitmap = ImageBitmap.imageResource(id = frameResId)
            Canvas(modifier = Modifier.matchParentSize()) {
                val srcW = bitmap.width
                val srcH = bitmap.height
                val srcX = (1.5f / 100f * srcW).roundToInt()
                val srcY = (2f   / 140f * srcH).roundToInt()
                val srcSzW = (18f / 100f * srcW).roundToInt()
                val srcSzH = (18f / 140f * srcH).roundToInt()
                drawImage(
                    image      = bitmap,
                    srcOffset  = IntOffset(srcX, srcY),
                    srcSize    = IntSize(srcSzW, srcSzH),
                    dstOffset  = IntOffset.Zero,
                    dstSize    = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
                )
            }
        }
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            val costLabel = if (card.isXCost) "X" else "${card.effectiveCost}"
            val costStyle = TextStyle(
                fontSize        = (size.value / 18f * 9f).sp,
                fontWeight      = FontWeight.ExtraBold,
                textAlign       = TextAlign.Center,
                platformStyle   = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim      = LineHeightStyle.Trim.Both
                )
            )
            val outline = size / 18f
            // Černý obrys – 4 posunuté kopie (stejná technika jako na skutečné kartě)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(x = -outline), style = costStyle)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(x = outline),  style = costStyle)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(y = -outline), style = costStyle)
            Text(costLabel, color = Color.Black, modifier = Modifier.fillMaxWidth().offset(y = outline),  style = costStyle)
            Text(costLabel, color = Color.White, modifier = Modifier.fillMaxWidth(), style = costStyle)
        }
    }
}

@Composable
internal fun DeckCardRow(card: Card, count: Int, onRemove: () -> Unit) {
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
                // alignment MUSÍ být taky – bez něj Compose ořízne na střed
                // bez ohledu na bias (stejná dvojice jako u velké ilustrace)
                alignment    = deckListAlignment(card),
                modifier     = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Výřez miniatury lze doladit nezávisle na kartě – viz deckListArt()
                        val (s, pivotX, pivotY) = deckListArt(card)
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(pivotX, pivotY)
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
            CardCostBadge(card, size = 20.dp)
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

/** Nadpis sekce statistik balíčku – stejný styl pro složení i mana křivku. */
@Composable
private fun StatsHeader(text: String) {
    Text(
        text.uppercase(),
        color         = Gold,
        fontSize      = 9.sp,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines      = 1
    )
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

    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        StatsHeader(LocalStrings.current.dbManaCurve)
        // Sloupce s počtem karet těsně nad každým. Číslo má pevnou výšku, sloupce se dělí
        // o zbytek váhami – výška sloupce se tak počítá bez popisku a nepřekrývá ho.
        Row(
            Modifier.fillMaxWidth().height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { count ->
                val fillFraction = if (count > 0) (count.toFloat() / maxCount).coerceAtLeast(0.06f) else 0f
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight((1f - fillFraction).coerceAtLeast(0.001f)))
                    Text(
                        if (count > 0) "$count" else "",
                        color    = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp),
                        style    = TextStyle(
                            fontSize        = 9.sp,
                            fontWeight      = FontWeight.Bold,
                            lineHeight      = 10.sp,
                            platformStyle   = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim      = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                    if (count > 0) Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(fillFraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Gold, Gold.copy(alpha = 0.55f))
                                )
                            )
                    )
                }
            }
        }
        // Osa X
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            (0..7).forEach { i ->
                Text(
                    if (i < 7) "$i" else "7+",
                    modifier   = Modifier.weight(1f),
                    color      = if (buckets[i] > 0) Color.White.copy(alpha = 0.85f) else TextMuted,
                    fontSize   = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DeckStats(
    deck: Deck, deckCards: List<Card>, modifier: Modifier = Modifier,
    deckSize: Int = 30, complete: Boolean = deck.isValid, budget: Pair<Int, Int>? = null
) {
    val byType = ResourceType.entries.associateWith { type ->
        deckCards.filter { it.costType == type }.sumOf { deck.cardCounts[it.id] ?: 0 }
    }
    val total = deck.totalCards.coerceAtLeast(1).toFloat()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // Nadpis + počet karet: zelený = hotový balíček (30), červený = chybí nebo přebývá
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StatsHeader(LocalStrings.current.dbComposition)
            Text(
                "${deck.totalCards}/$deckSize",
                color      = if (complete) HpGreen else AttackRed,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1
            )
        }
        if (budget != null) {
            // Stejný řádek jako nadpis výše: popisek verzálkami, hodnota zeleně (vejde se)
            // nebo červeně (přes rozpočet). Text „Rozpočet: 12 / 20 bodů" se dělí u dvojtečky.
            val label = LocalStrings.current.rogueBudget.format(budget.first, budget.second).uppercase()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                StatsHeader(label.substringBefore(':'))
                Text(
                    label.substringAfter(':', label).trim(),
                    color      = if (budget.first > budget.second) AttackRed else HpGreen,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1
                )
            }
        }
        // 2×2: ikona + velké číslo, pod tím proužek = podíl na balíčku
        ResourceType.entries.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { type ->
                    val count = byType[type] ?: 0
                    val color = resColor(type)
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Image(
                                painterResource(resourceIconRes(type)),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "$count",
                                color      = if (count > 0) Color.White else TextMuted,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(count / total).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}
