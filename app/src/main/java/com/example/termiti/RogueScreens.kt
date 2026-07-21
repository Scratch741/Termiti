package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════
// Roguelike obrazovky: draft (výběr balíčku), odměna, konec runu.
// ═══════════════════════════════════════════════════════════════════════════

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

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF160F14), BgDeep), radius = 1200f)
        )
    ) {
        Row(Modifier.fillMaxSize()) {

            // ── Katalog karet (vlevo) ─────────────────────────────────────────
            Column(Modifier.weight(3f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val s = LocalStrings.current
                // Řádek 1: zpět + titul + zdroj
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlainButton("← Zpět", textColor = TextMuted, fontSize = 9.sp,
                        paddingH = 8.dp, paddingV = 4.dp, onClick = onBack)
                    Text("SESTAV BALÍČEK", color = Gold, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    FilterChip(R.drawable.magie_icon,  s.resMagic,  filterRes == ResourceType.MAGIC,  MagicBlue)   { filterRes = if (filterRes == ResourceType.MAGIC)  null else ResourceType.MAGIC }
                    FilterChip(R.drawable.utok_icon,   s.resAttack, filterRes == ResourceType.ATTACK, AttackRed)   { filterRes = if (filterRes == ResourceType.ATTACK) null else ResourceType.ATTACK }
                    FilterChip(R.drawable.kamen_icon2, s.resStone,  filterRes == ResourceType.STONES, StoneColor)  { filterRes = if (filterRes == ResourceType.STONES) null else ResourceType.STONES }
                    FilterChip(R.drawable.chaos_icon,  s.resChaos,  filterRes == ResourceType.CHAOS,  ChaosOrange) { filterRes = if (filterRes == ResourceType.CHAOS)  null else ResourceType.CHAOS }
                }
                // Řádek 2: kategorie (vč. Dolů) + rarita
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(s.catAttack,    filterCat == "Útok",       AttackRed)         { filterCat = if (filterCat == "Útok") null else "Útok" }
                    FilterChip(s.catDefense,   filterCat == "Obrana",     StoneColor)        { filterCat = if (filterCat == "Obrana") null else "Obrana" }
                    FilterChip(s.catResources, filterCat == "Zdroje",     MagicPurple)       { filterCat = if (filterCat == "Zdroje") null else "Zdroje" }
                    FilterChip(s.catMines,     filterCat == "Doly",       Gold)              { filterCat = if (filterCat == "Doly") null else "Doly" }
                    FilterChip(s.catCombo,     filterCat == "Kombo",      TealLight)         { filterCat = if (filterCat == "Kombo") null else "Kombo" }
                    FilterChip(s.catDecision,  filterCat == "Rozhodnutí", Color(0xFFAB47BC)) { filterCat = if (filterCat == "Rozhodnutí") null else "Rozhodnutí" }
                    Spacer(Modifier.weight(1f))
                    Rarity.entries.forEach { r ->
                        FilterChip(r.label, filterRarity == r, rarityColor(r)) { filterRarity = if (filterRarity == r) null else r }
                    }
                }
                // Řádek 3: cena
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
                    Spacer(Modifier.weight(1f))
                    Text("${catalog.size} karet", color = TextMuted.copy(alpha = 0.6f), fontSize = 8.sp)
                }

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(4),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(catalog, key = { it.id }) { card ->
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

                // Rozpad podle rarity
                Rarity.entries.reversed().forEach { r ->
                    val n = draft.count { it.rarity == r }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label, color = rarityColor(r), fontSize = 9.sp)
                        Text("$n", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.weight(1f))

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

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF0F160F), BgDeep), radius = 1200f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: postup + HP
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlainButton("Vzdát run", textColor = TextMuted, fontSize = 9.sp, paddingH = 8.dp, paddingV = 4.dp, onClick = onExit)
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

            Text("Vyber jednu odměnu", color = Gold, fontSize = 10.sp, letterSpacing = 1.sp)

            // 3 karty
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                run.rewardCards.forEach { card ->
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.scale(1.1f)) {
                            CardView(card = card, canPlay = true, discardMode = false, showFade = false,
                                onClick = { viewModel.applyRogueReward(RogueReward.AddCard(card)) })
                        }
                        Spacer(Modifier.height(24.dp))
                        PlainButton("PŘIDAT", modifier = Modifier.width(96.dp), textColor = TealLight,
                            fontSize = 9.sp, paddingH = 0.dp, paddingV = 5.dp,
                            onClick = { viewModel.applyRogueReward(RogueReward.AddCard(card)) })
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }

            // Statové odměny + skip
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RogueStatButton("🏰 +${RogueConfig.REWARD_MAX_CASTLE} max hrad", Gold) { viewModel.applyRogueReward(RogueReward.MaxCastle) }
                RogueStatButton("🧱 +${RogueConfig.REWARD_WALL} hradby", StoneColor) { viewModel.applyRogueReward(RogueReward.Wall) }
                RogueStatButton("✚ Oprava +${RogueConfig.REWARD_REPAIR}", HpGreen) { viewModel.applyRogueReward(RogueReward.Repair) }
                RogueStatButton("⛏️ +1 důl magie", MagicPurple) { viewModel.applyRogueReward(RogueReward.MineMagic) }
                RogueStatButton("Přeskočit", TextMuted) { viewModel.skipRogueReward() }
            }
        }
    }
}

@Composable
private fun RogueStatButton(label: String, accent: Color, onClick: () -> Unit) {
    PlainButton(
        text      = label,
        textColor = accent,
        fontSize  = 10.sp,
        paddingH  = 12.dp,
        paddingV  = 9.dp,
        onClick   = onClick
    )
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
