package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
    // Sestavení roguelike balíčku je Tvorba balíčku v roguelike režimu: stejné album,
    // filtry, detail karty i výběr ze tří (roguelike) balíčků – viz DeckBuilderScreen(rogue).
    DeckBuilderScreen(viewModel = viewModel, onBack = onBack, rogue = true)
}

// ─── Odměna po výhře ────────────────────────────────────────────────────────
@Composable
fun RogueRewardScreen(viewModel: GameViewModel, onExit: () -> Unit, onMenu: () -> Unit) {
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
            Column(
                Modifier.weight(2.45f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            // Header: postup + HP
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlainButton("Menu", textColor = TextMuted, fontSize = 9.sp, paddingH = 8.dp, paddingV = 4.dp, onClick = onMenu)
                    PlainButton(LocalStrings.current.surrender, textColor = TextMuted, fontSize = 9.sp, paddingH = 8.dp, paddingV = 4.dp, onClick = { showExitConfirm = true })
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CampaignTitle(LocalStrings.current.rogueVictory, fontSize = 22.sp)
                    Text(LocalStrings.current.rogueNext.format(run.actTitle, run.battleLabel), color = TextMuted, fontSize = 9.sp)
                    if (run.rewardCardPicksLeft > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            LocalStrings.current.roguePickRequired.format(run.rewardCardPicksLeft),
                            color = Gold, fontSize = 10.sp, letterSpacing = 1.sp
                        )
                    }
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
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            PlainButton(LocalStrings.current.rogueAdd, modifier = Modifier.width(90.dp), textColor = TealLight,
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
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CampaignTitle("BONUS ZA BOSSE", fontSize = 20.sp)
                    Text(LocalStrings.current.roguePickExtra, color = TextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RogueBonusCard(R.drawable.castle_icon, "+${RogueConfig.REWARD_MAX_CASTLE}", "Max hrad", Gold) { viewModel.pickRewardBonus(RogueReward.MaxCastle) }
                        RogueBonusCard(R.drawable.wall_icon, "+${RogueConfig.REWARD_WALL}", "Hradby", StoneColor) { viewModel.pickRewardBonus(RogueReward.Wall) }
                        RogueBonusCard(R.drawable.shield_icon, "+${RogueConfig.REWARD_REPAIR}", "Oprava", HpGreen) { viewModel.pickRewardBonus(RogueReward.Repair) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RogueBonusCard(resourceIconRes(ResourceType.MAGIC), LocalStrings.current.resMagic, LocalStrings.current.rogueNewMine, MagicBlue) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.MAGIC)) }
                        RogueBonusCard(resourceIconRes(ResourceType.ATTACK), LocalStrings.current.resAttack, LocalStrings.current.rogueNewMine, AttackRed) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.ATTACK)) }
                        RogueBonusCard(resourceIconRes(ResourceType.STONES), LocalStrings.current.resStone, LocalStrings.current.rogueNewMine, StoneColor) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.STONES)) }
                        RogueBonusCard(resourceIconRes(ResourceType.CHAOS), LocalStrings.current.resChaos, LocalStrings.current.rogueNewMine, ChaosOrange) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.CHAOS)) }
                    }
                    Spacer(Modifier.height(14.dp))
                    PlainButton(LocalStrings.current.rogueSkip, textColor = TextMuted, fontSize = 10.sp,
                        paddingH = 16.dp, paddingV = 6.dp, onClick = { viewModel.skipRewardBonus() })
                }
            }
            }

            Spacer(Modifier.width(24.dp))

            // ── Přehled balíčku (vpravo) – stejný styl jako DeckPanel v deckbuilderu ──
            RogueDeckOverview(deck = run.deck, modifier = Modifier.weight(0.85f).fillMaxHeight())
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
                CampaignTitle(LocalStrings.current.surrenderQ, fontSize = 24.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(2.dp))
                Text(LocalStrings.current.rogueSurrenderMsg, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlainButton(
                        text = LocalStrings.current.stay, textColor = TealLight, fontSize = 13.sp,
                        paddingH = 20.dp, paddingV = 8.dp,
                        onClick = { showExitConfirm = false }
                    )
                    PlainButton(
                        text = LocalStrings.current.surrender, textColor = Crimson, fontSize = 13.sp,
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
        Text(LocalStrings.current.rogueYourDeck.format(deck.size), color = Gold, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        // Souhrn zdrojů v balíčku – kolik karet kterého typu, na první pohled bez scrollování.
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            ResourceType.entries.forEach { type ->
                val total = (groups[type] ?: emptyList()).sumOf { counts[it.baseId] ?: 0 }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Image(painterResource(resourceIconRes(type)), contentDescription = null, modifier = Modifier.size(10.dp))
                    Text("$total", color = resColor(type).copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
private fun RogueBonusCard(@DrawableRes icon: Int, value: String, label: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .height(108.dp)
            .buttonTexture(R.drawable.plain_button_mini)
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
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
            // Nadpis výsledku stejně jako v kampani (Cinzel + přechod), bez poháru/lebky
            CampaignTitle(
                if (victory) LocalStrings.current.rogueRunComplete else LocalStrings.current.rogueRunOver,
                fontSize = 28.sp,
                gradient = if (victory) TitleGold else TitleBlood
            )
            Text(
                if (victory) LocalStrings.current.rogueAllBattles.format(RogueConfig.TOTAL_BATTLES)
                else LocalStrings.current.rogueCastleFell,
                color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Gold.copy(alpha = 0.08f))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(LocalStrings.current.rogueBattlesWon.format(battlesWon, RogueConfig.TOTAL_BATTLES),
                    color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            PlainButton(LocalStrings.current.backToMenuCaps, modifier = Modifier.fillMaxWidth(),
                textColor = TextPrimary, fontSize = 12.sp, paddingH = 0.dp, paddingV = 10.dp,
                buttonRes = R.drawable.plain_button_longer, onClick = onBack)
        }
    }
}
