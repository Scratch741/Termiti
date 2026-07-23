package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════
// Roguelike obrazovky: draft (výběr balíčku), odměna, konec runu.
// ═══════════════════════════════════════════════════════════════════════════

private fun resColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> MagicBlue
    ResourceType.ATTACK -> AttackRed
    ResourceType.STONES -> StoneColor
    ResourceType.CHAOS  -> ChaosOrange
}

// ─── Draft: deckbuilder – vyber 20 karet z kolekce v rámci rozpočtu ────────
@Composable
fun RogueDraftScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val draft = viewModel.rogueDraft            // pozorovaný SnapshotStateList
    val picked = draft.size
    val spent  = draft.sumOf { RogueConfig.rarityBudgetCost(it.rarity) }
    val full   = picked >= RogueConfig.DECK_SIZE

    var filterRes    by remember { mutableStateOf<ResourceType?>(null) }
    var filterCat    by remember { mutableStateOf<String?>(null) }
    var filterRarity by remember { mutableStateOf<Rarity?>(null) }
    var filterCost   by remember { mutableStateOf<Int?>(null) }
    var previewCard  by remember { mutableStateOf<Card?>(null) }

    // Karty z kolekce (vlastněné/základní) + všechny filtry
    val catalog = remember(filterRes, filterCat, filterRarity, filterCost) {
        viewModel.allCards
            .filter { c ->
                !c.isPlaceholder && c.effects.none { it is CardEffect.TrapOnDraw } &&
                CardCollectionManager.usableCopies(c) > 0 &&
                (filterRes == null || c.costType == filterRes) &&
                (filterRarity == null || c.rarity == filterRarity) &&
                (filterCat == null ||
                    (filterCat == "Kombo" && c.isCombo) ||
                    (filterCat != "Kombo" && c.categories().contains(filterCat))) &&
                (filterCost == null || (if (filterCost == 7) c.cost >= 7 else c.cost == filterCost))
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

            // ── Katalog karet (vlevo) ─────────────────────────────────────────
            Column(Modifier.weight(3f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val s = LocalStrings.current
                // Řádek 1: zpět + titul + zdroj (stejné pořadí jako FilterBar v deckbuilderu)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SESTAV BALÍČEK", color = Gold, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Zdroj:", color = TextMuted, fontSize = 9.sp)
                    FilterChip(R.drawable.magie_icon,  s.resMagic,  filterRes == ResourceType.MAGIC,  MagicBlue)   { filterRes = if (filterRes == ResourceType.MAGIC)  null else ResourceType.MAGIC }
                    FilterChip(R.drawable.utok_icon,   s.resAttack, filterRes == ResourceType.ATTACK, AttackRed)   { filterRes = if (filterRes == ResourceType.ATTACK) null else ResourceType.ATTACK }
                    FilterChip(R.drawable.kamen_icon2, s.resStone,  filterRes == ResourceType.STONES, StoneColor)  { filterRes = if (filterRes == ResourceType.STONES) null else ResourceType.STONES }
                    FilterChip(R.drawable.chaos_icon,  s.resChaos,  filterRes == ResourceType.CHAOS,  ChaosOrange) { filterRes = if (filterRes == ResourceType.CHAOS)  null else ResourceType.CHAOS }
                    PlainButton(
                        text      = "← Zpět",
                        modifier  = Modifier.heightIn(max = 22.dp).widthIn(max = 58.dp),
                        textColor = TextMuted,
                        fontSize  = 8.sp,
                        paddingH  = 5.dp,
                        paddingV  = 3.dp,
                        onClick   = onBack
                    )
                }
                // Řádek 2: kategorie (vč. Dolů) – vlastní řádek jako v deckbuilderu
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.dbEffectLabel, color = TextMuted, fontSize = 9.sp)
                    FilterChip(s.catAttack,    filterCat == "Útok",       AttackRed)         { filterCat = if (filterCat == "Útok") null else "Útok" }
                    FilterChip(s.catDefense,   filterCat == "Obrana",     StoneColor)        { filterCat = if (filterCat == "Obrana") null else "Obrana" }
                    FilterChip(s.catResources, filterCat == "Zdroje",     MagicPurple)       { filterCat = if (filterCat == "Zdroje") null else "Zdroje" }
                    FilterChip(s.catMines,     filterCat == "Doly",       Gold)              { filterCat = if (filterCat == "Doly") null else "Doly" }
                    FilterChip(s.catCombo,     filterCat == "Kombo",      TealLight)         { filterCat = if (filterCat == "Kombo") null else "Kombo" }
                    FilterChip(s.catDecision,  filterCat == "Rozhodnutí", Color(0xFFAB47BC)) { filterCat = if (filterCat == "Rozhodnutí") null else "Rozhodnutí" }
                }
                // Řádek 3: rarita – VLASTNÍ řádek, aby se legendární nezařezávala
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rarita:", color = TextMuted, fontSize = 9.sp)
                    Rarity.entries.forEach { r ->
                        FilterChip(r.label, filterRarity == r, rarityColor(r)) { filterRarity = if (filterRarity == r) null else r }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${catalog.size} karet", color = TextMuted.copy(alpha = 0.6f), fontSize = 8.sp)
                }
                // Řádek 4: cena (jako ManaCostFilterBar v deckbuilderu)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cena:", color = TextMuted, fontSize = 9.sp)
                    (0..7).forEach { cost ->
                        CostChip(if (cost == 7) "7+" else "$cost", filterCost == cost, width = 26.dp) {
                            filterCost = if (filterCost == cost) null else cost
                        }
                    }
                }

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(4),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.weight(1f).fillMaxWidth()
                ) {
                    gridItems(catalog, key = { it.id }) { card ->
                        val count  = draft.count { it.baseId == card.baseId }
                        val usable = CardCollectionManager.usableCopies(card)
                        val overBudget = spent + RogueConfig.rarityBudgetCost(card.rarity) > RogueConfig.BUDGET
                        CatalogCardItem(
                            card        = card,
                            count       = count,
                            usable      = usable,
                            isNew       = false,
                            deckFull    = full || overBudget,   // blokuje [+] při plném balíčku i překročení rozpočtu
                            onIncrement = { viewModel.rogueAddCard(card) },
                            onDecrement = { viewModel.rogueRemoveCard(card) },
                            onPreview   = { previewCard = card }
                        )
                    }
                }
            }

            // ── Panel balíčku (vpravo) ────────────────────────────────────────
            Column(
                Modifier.weight(2f).fillMaxHeight()
                    .background(BgPanel.copy(alpha = 0.75f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ROGUELIKE — BALÍČEK", color = Gold, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

                // Uložené balíčky (šablony) – uloží/načte rozestavěný draft
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Uložené balíčky:", color = TextMuted, fontSize = 8.sp)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        viewModel.roguePresets.forEachIndexed { i, preset ->
                            val hasCards = preset.cardCounts.isNotEmpty()
                            Row(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Gold.copy(alpha = if (hasCards) 0.35f else 0.12f), RoundedCornerShape(6.dp))
                                    .clickable(enabled = hasCards) { viewModel.loadRoguePreset(i) }
                                    .padding(horizontal = 5.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    preset.name,
                                    color = if (hasCards) TextPrimary else TextMuted.copy(alpha = 0.5f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "💾",
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable {
                                        viewModel.saveRoguePreset(i)
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.1f))

                // Počet karet
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Karty: $picked / ${RogueConfig.DECK_SIZE}",
                        color = if (full) HpGreen else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.fillMaxWidth().height(5.dp)
                            .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(Modifier.fillMaxWidth(picked / RogueConfig.DECK_SIZE.toFloat()).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(TealLight, Gold))))
                    }
                }

                // Rozpočet
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val overCap = spent >= RogueConfig.BUDGET
                    Text("Rozpočet: $spent / ${RogueConfig.BUDGET} bodů",
                        color = if (overCap) Gold else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.fillMaxWidth().height(5.dp)
                            .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(Modifier.fillMaxWidth((spent / RogueConfig.BUDGET.toFloat()).coerceIn(0f, 1f)).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(Gold, Color(0xFFB388FF)))))
                    }
                    Text("Legendary 4 · Epic 2 · Rare 1 · Common 0",
                        color = TextMuted.copy(alpha = 0.6f), fontSize = 7.5.sp)
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.1f))

                // Seznam karet v balíčku – stejně jako DeckPanel v deckbuilderu
                val draftCounts = remember(draft.toList()) { draft.groupingBy { it.baseId }.eachCount() }
                val draftCards  = remember(draft.toList()) {
                    draft.distinctBy { it.baseId }
                        .sortedWith(compareBy({ it.costType.ordinal }, { it.cost }, { it.displayName }))
                }
                val draftGroups = remember(draftCards) { draftCards.groupBy { it.costType } }

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ResourceType.entries.forEach { type ->
                        val cards = draftGroups[type] ?: return@forEach
                        item(key = "header_$type") {
                            val typeColor = resColor(type)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Image(painterResource(resourceIconRes(type)), contentDescription = null, modifier = Modifier.size(12.dp))
                                val groupCount = cards.sumOf { draftCounts[it.baseId] ?: 0 }
                                val s = LocalStrings.current
                                Text(
                                    "${when (type) {
                                        ResourceType.MAGIC  -> s.resMagic
                                        ResourceType.ATTACK -> s.resAttack
                                        ResourceType.STONES -> s.resStone
                                        ResourceType.CHAOS  -> s.resChaos
                                    }.uppercase()} ($groupCount)",
                                    color = typeColor.copy(alpha = 0.85f),
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                                )
                                Box(
                                    Modifier.weight(1f).height(1.dp)
                                        .background(Brush.horizontalGradient(listOf(typeColor.copy(alpha = 0.45f), Color.Transparent)))
                                )
                            }
                        }
                        items(cards, key = { it.id }) { card ->
                            DeckCardRow(
                                card     = card,
                                count    = draftCounts[card.baseId] ?: 0,
                                onRemove = { viewModel.rogueRemoveCard(card) }
                            )
                        }
                    }
                    if (draft.isEmpty()) {
                        item(key = "empty_hint") {
                            Text(
                                "Balíček je zatím prázdný.",
                                color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                    }
                }

                if (!full) {
                    Text("Doplň balíček na ${RogueConfig.DECK_SIZE} karet.",
                        color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                PlainButton(
                    text      = if (full) "ZAHÁJIT RUN" else "BALÍČEK NENÍ HOTOVÝ",
                    modifier  = Modifier.fillMaxWidth(),
                    textColor = if (full) TealLight else TextMuted.copy(alpha = 0.4f),
                    fontSize  = 13.sp, paddingH = 0.dp, paddingV = 11.dp,
                    enabled   = full,
                    buttonRes = R.drawable.plain_button_longer,
                    onClick   = { if (full) viewModel.startRogueRun() }
                )
            }
        }

        // Náhled karty
        previewCard?.let { CardFullPreviewOverlay(card = it, onDismiss = { previewCard = null }) }
    }
}

// ─── Odměna po výhře ────────────────────────────────────────────────────────
@Composable
fun RogueRewardScreen(viewModel: GameViewModel, onExit: () -> Unit) {
    val run = viewModel.rogueRun.value ?: return
    var showExitConfirm by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_plain),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 26.dp)) {
            // ── Přehled balíčku (vlevo) – stejný styl jako DeckPanel v deckbuilderu ──
            RogueDeckOverview(deck = run.deck, modifier = Modifier.weight(0.85f).fillMaxHeight())

            Spacer(Modifier.width(24.dp))

            Column(
                Modifier.weight(2.45f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            // Header: postup + HP
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlainButton("Vzdát se", textColor = TextMuted, fontSize = 9.sp, paddingH = 8.dp, paddingV = 4.dp, onClick = { showExitConfirm = true })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VÍTĚZSTVÍ", color = TealLight, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("${run.actTitle}  ·  následuje ${run.battleLabel}", color = TextMuted, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(painterResource(R.drawable.castle_icon), contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("${run.hp} / ${run.maxCastle}", color = HpGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Image(painterResource(R.drawable.wall_icon), contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("${run.wall}", color = StoneColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (run.rewardCardPicksLeft > 0) {
                // ── Fáze 1: POVINNÝ výběr karet – nelze přeskočit, balíček musí růst ──
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Vyber kartu (POVINNÉ) — zbývá ${run.rewardCardPicksLeft}×",
                        color = Gold, fontSize = 10.sp, letterSpacing = 1.sp
                    )
                    PlainButton(
                        "🔄 Reroll (${run.rerollsLeft}×)",
                        textColor = if (run.rerollsLeft > 0) TealLight else TextMuted.copy(alpha = 0.4f),
                        fontSize = 8.sp, paddingH = 8.dp, paddingV = 4.dp,
                        enabled = run.rerollsLeft > 0,
                        onClick = { viewModel.rerollRewardCards() }
                    )
                }
                // Řádek karet – horizontálně scrollovatelný, aby se nezařezával při 5 kartách
                // (odměna za bosse nabízí víc karet, než se vejde vedle sebe v užším panelu).
                Row(
                    Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    run.rewardCards.forEach { card ->
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.scale(0.9f)) {
                                CardView(card = card, canPlay = true, discardMode = false, showFade = false,
                                    onClick = { viewModel.pickRewardCard(card) })
                            }
                            Spacer(Modifier.height(20.dp))
                            PlainButton("PŘIDAT", modifier = Modifier.width(90.dp), textColor = TealLight,
                                fontSize = 9.sp, paddingH = 0.dp, paddingV = 5.dp,
                                onClick = { viewModel.pickRewardCard(card) })
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            } else {
                // ── Fáze 2: bonus navíc, jen po zabití bosse (volitelné) ──
                // Karty místo namačkaného řádku – zabírají volný prostor, který
                // tu jinak zbýval prázdný, a nic se nezařezává mimo obrazovku.
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("★ BONUS ZA BOSSE ★", color = Gold, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Vyber jeden navíc, nebo přeskoč", color = TextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RogueBonusCard("🏰", "+${RogueConfig.REWARD_MAX_CASTLE}", "Max hrad", Gold) { viewModel.pickRewardBonus(RogueReward.MaxCastle) }
                        RogueBonusCard("🧱", "+${RogueConfig.REWARD_WALL}", "Hradby", StoneColor) { viewModel.pickRewardBonus(RogueReward.Wall) }
                        RogueBonusCard("✚", "+${RogueConfig.REWARD_REPAIR}", "Oprava", HpGreen) { viewModel.pickRewardBonus(RogueReward.Repair) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RogueBonusCard("⛏️", "Magie", "Nový důl", MagicBlue) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.MAGIC)) }
                        RogueBonusCard("⛏️", "Útok", "Nový důl", AttackRed) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.ATTACK)) }
                        RogueBonusCard("⛏️", "Kámen", "Nový důl", StoneColor) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.STONES)) }
                        RogueBonusCard("⛏️", "Chaos", "Nový důl", ChaosOrange) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.CHAOS)) }
                    }
                    Spacer(Modifier.height(20.dp))
                    PlainButton("Přeskočit", textColor = TextMuted, fontSize = 10.sp,
                        paddingH = 16.dp, paddingV = 6.dp, onClick = { viewModel.skipRewardBonus() })
                }
            }
            }
        }
    }

    if (showExitConfirm) {
        GameDialog(onDismissRequest = { showExitConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .paint(painterResource(R.drawable.mulligan_background), contentScale = ContentScale.Crop)
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Vzdát se?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(2.dp))
                Text("Rozehraný run bude ztracen. Opravdu se chceš vzdát?", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlainButton(
                        text = "Zůstat", textColor = TealLight, fontSize = 13.sp,
                        paddingH = 20.dp, paddingV = 8.dp,
                        onClick = { showExitConfirm = false }
                    )
                    PlainButton(
                        text = "Vzdát se", textColor = Crimson, fontSize = 13.sp,
                        paddingH = 20.dp, paddingV = 8.dp,
                        onClick = { showExitConfirm = false; onExit() }
                    )
                }
            }
        }
    }
}

/**
 * Přehled aktuálního běžeckého balíčku (jen k nahlédnutí, bez odebírání) –
 * stejný styl seskupení podle zdroje + DeckCardRow jako DeckPanel v deckbuilderu.
 */
@Composable
private fun RogueDeckOverview(deck: List<Card>, modifier: Modifier = Modifier) {
    val counts = remember(deck) { deck.groupingBy { it.baseId }.eachCount() }
    val cards  = remember(deck) {
        deck.distinctBy { it.baseId }
            .sortedWith(compareBy({ it.costType.ordinal }, { it.cost }, { it.displayName }))
    }
    val groups = remember(cards) { cards.groupBy { it.costType } }
    val s = LocalStrings.current

    Column(modifier) {
        Text("TVŮJ BALÍČEK (${deck.size})", color = Gold, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ResourceType.entries.forEach { type ->
                val typeCards = groups[type] ?: return@forEach
                item(key = "rdeck_hdr_$type") {
                    val typeColor = resColor(type)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(painterResource(resourceIconRes(type)), contentDescription = null, modifier = Modifier.size(11.dp))
                        val groupCount = typeCards.sumOf { counts[it.baseId] ?: 0 }
                        Text(
                            "${when (type) {
                                ResourceType.MAGIC  -> s.resMagic
                                ResourceType.ATTACK -> s.resAttack
                                ResourceType.STONES -> s.resStone
                                ResourceType.CHAOS  -> s.resChaos
                            }.uppercase()} ($groupCount)",
                            color = typeColor.copy(alpha = 0.85f),
                            fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                        Box(
                            Modifier.weight(1f).height(1.dp)
                                .background(Brush.horizontalGradient(listOf(typeColor.copy(alpha = 0.45f), Color.Transparent)))
                        )
                    }
                }
                items(typeCards, key = { it.id }) { card ->
                    DeckCardRow(card = card, count = counts[card.baseId] ?: 0, onRemove = {})
                }
            }
        }
    }
}

/**
 * Čtvercová karta bonusové odměny (Fáze 2 odměny) – texturované plain_button_mini
 * pozadí (stejné jako CostChip/CatalogCardItem), ne vlastní barevný box.
 */
@Composable
private fun RogueBonusCard(icon: String, value: String, label: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .height(108.dp)
            .paint(painterResource(R.drawable.plain_button_mini), contentScale = ContentScale.FillBounds)
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, color = TextMuted, fontSize = 7.5.sp, textAlign = TextAlign.Center)
    }
}

// ─── Konec runu ─────────────────────────────────────────────────────────────
@Composable
fun RogueEndScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val victory = viewModel.rogueVictory.value
    val run     = viewModel.rogueRun.value
    val battlesWon = (run?.battleIndex ?: 0).coerceAtMost(RogueConfig.TOTAL_BATTLES)

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(if (victory) Color(0xFF14180E) else Color(0xFF1A0A0A), BgDeep),
                radius = 1200f
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)          // bez ohraničení fillMaxWidth dítě roztáhne kartu na celou obrazovku
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1420), BgPanel)))
                .border(1.dp, (if (victory) Gold else Crimson).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 44.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painterResource(if (victory) R.drawable.trophy_icon else R.drawable.skull_icon),
                contentDescription = null, modifier = Modifier.size(40.dp)
            )
            Text(
                if (victory) "RUN DOKONČEN!" else "RUN SKONČIL",
                color = if (victory) Gold else Crimson,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(
                if (victory) "Probil ses všemi ${RogueConfig.TOTAL_BATTLES} bitvami."
                else "Tvůj hrad padl.",
                color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Gold.copy(alpha = 0.08f))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text("Vyhrané bitvy: $battlesWon / ${RogueConfig.TOTAL_BATTLES}",
                    color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            PlainButton("ZPĚT DO MENU", modifier = Modifier.fillMaxWidth(),
                textColor = TextPrimary, fontSize = 12.sp, paddingH = 0.dp, paddingV = 10.dp,
                buttonRes = R.drawable.plain_button_longer, onClick = onBack)
        }
    }
}
