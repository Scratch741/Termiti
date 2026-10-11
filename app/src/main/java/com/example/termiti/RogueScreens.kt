package com.example.termiti

import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.graphicsLayer
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
/**
 * Odměna po vyhrané bitvě ve stejném rozložení jako Tvorba balíčku: vlevo horní lišta,
 * výzva, karty na výběr a pod nimi Nová nabídka, vpravo za svislou linkou panel balíčku se složením
 * a mana křivkou (přímo [DbDeckPanel]). Klepnutí na kartu ji přidá do balíčku, stejně
 * jako v albu.
 */
@Composable
fun RogueRewardScreen(viewModel: GameViewModel, onExit: () -> Unit, onMenu: () -> Unit) {
    val run = viewModel.rogueRun.value ?: return
    val s   = LocalStrings.current
    var showExitConfirm by remember { mutableStateOf(false) }
    val picking = run.rewardCardPicksLeft > 0

    // Balíček běhu jako Deck pro sdílený panel. Karty běhu mají instanční id, panel čte
    // šablony podle základního id; co by v šablonách chybělo, doplní se z balíčku samotného.
    val counts   = remember(run.deck) { run.deck.groupingBy { it.baseId }.eachCount() }
    val runDeck  = remember(counts) { Deck(id = -1, name = "", cardCounts = counts) }
    val deckPool = remember(run.deck, viewModel.allCards) {
        val known = viewModel.allCards.filter { it.id in counts }
        val ids   = known.mapTo(HashSet()) { it.id }
        known + run.deck.distinctBy { it.baseId }.filter { it.baseId !in ids }.map { it.copy(id = it.baseId) }
    }

    Box(Modifier.fillMaxSize()) {
        DbBackground()
        Row(Modifier.fillMaxSize()) {

            // ── Vlevo: lišta běhu, výběr odměny, spodní lišta ─────────────────
            Column(Modifier.weight(DB_CATALOG_WEIGHT).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DbBarHeight)
                        .background(DbBarColor)
                        .padding(start = DbBarInset, end = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PlainButton(
                        "Menu", modifier = Modifier.width(76.dp).height(30.dp),
                        textColor = TextMuted, fontSize = 9.sp, paddingH = 4.dp, paddingV = 0.dp, onClick = onMenu
                    )
                    PlainButton(
                        s.surrender, modifier = Modifier.width(76.dp).height(30.dp),
                        textColor = TextMuted, fontSize = 9.sp, paddingH = 4.dp, paddingV = 0.dp,
                        onClick = { showExitConfirm = true }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        s.rogueNext.format(run.actTitle, run.battleLabel),
                        color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    Image(painterResource(R.drawable.castle_icon), contentDescription = null, modifier = Modifier.size(15.dp))
                    Text("${run.hp} / ${run.maxCastle}", color = HpGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Image(painterResource(R.drawable.wall_icon), contentDescription = null, modifier = Modifier.size(15.dp))
                    Text("${run.wall}", color = StoneColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                DbSeparator()

                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CampaignTitle(if (picking) s.rogueVictory else "BONUS ZA BOSSE", fontSize = 25.sp)
                    if (picking) {
                        // ── Fáze 1: POVINNÝ výběr karet – nelze přeskočit, balíček musí růst ──
                        // Výzva a počet zbývajících výběrů velkým písmem hned pod nadpisem
                        Row(
                            Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                s.roguePickCard.uppercase(),
                                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                            )
                            Text(
                                s.roguePicksLeft.format(run.rewardCardPicksLeft),
                                color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                            )
                        }
                        RewardCardRow(run.rewardCards, Modifier.weight(1f).fillMaxWidth()) { viewModel.pickRewardCard(it) }
                        // Nová nabídka hned pod kartami, ke kterým patří
                        PlainButton(
                            s.rogueReroll.format(run.rerollsLeft),
                            modifier  = Modifier.width(210.dp).height(36.dp),
                            textColor = if (run.rerollsLeft > 0) TealLight else TextMuted,
                            fontSize  = 12.sp, paddingH = 4.dp, paddingV = 0.dp,
                            enabled   = run.rerollsLeft > 0,
                            buttonRes = R.drawable.plain_button_longer,
                            onClick   = { viewModel.rerollRewardCards() }
                        )
                        Spacer(Modifier.height(20.dp))
                    } else {
                        Text(s.roguePickExtra, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        // ── Fáze 2: bonus navíc, jen po zabití bosse (volitelné) ──
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RogueBonusCard(R.drawable.castle_icon, "+${RogueConfig.REWARD_MAX_CASTLE}", "Max hrad", Gold) { viewModel.pickRewardBonus(RogueReward.MaxCastle) }
                                RogueBonusCard(R.drawable.wall_icon, "+${RogueConfig.REWARD_WALL}", "Hradby", StoneColor) { viewModel.pickRewardBonus(RogueReward.Wall) }
                                RogueBonusCard(R.drawable.shield_icon, "+${RogueConfig.REWARD_REPAIR}", "Oprava", HpGreen) { viewModel.pickRewardBonus(RogueReward.Repair) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RogueBonusCard(resourceIconRes(ResourceType.MAGIC), s.resMagic, s.rogueNewMine, MagicBlue) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.MAGIC)) }
                                RogueBonusCard(resourceIconRes(ResourceType.ATTACK), s.resAttack, s.rogueNewMine, AttackRed) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.ATTACK)) }
                                RogueBonusCard(resourceIconRes(ResourceType.STONES), s.resStone, s.rogueNewMine, StoneColor) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.STONES)) }
                                RogueBonusCard(resourceIconRes(ResourceType.CHAOS), s.resChaos, s.rogueNewMine, ChaosOrange) { viewModel.pickRewardBonus(RogueReward.Mine(ResourceType.CHAOS)) }
                            }
                            Spacer(Modifier.height(4.dp))
                            PlainButton(
                                s.rogueSkip,
                                modifier  = Modifier.width(210.dp).height(36.dp),
                                textColor = TextPrimary, fontSize = 12.sp, paddingH = 4.dp, paddingV = 0.dp,
                                buttonRes = R.drawable.plain_button_longer,
                                onClick   = { viewModel.skipRewardBonus() }
                            )
                        }
                    }
                }

            }

            DbVerticalSeparator()

            // ── Vpravo: hlavička + panel balíčku (totožný s Tvorbou balíčku) ──
            Column(Modifier.weight(DB_DECK_WEIGHT).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(DbBarHeight).background(DbBarColor).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.widthIn(min = 150.dp).height(28.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().buttonTexture(R.drawable.plain_button))
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(painterResource(R.drawable.card_icon), contentDescription = null, modifier = Modifier.size(15.dp), contentScale = ContentScale.Fit)
                            Text(
                                s.rogueYourDeck.format(run.deck.size),
                                color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                        }
                    }
                }
                DbSeparator()
                DbDeckPanel(
                    deck     = runDeck,
                    allCards = deckPool,
                    deckSize = run.deck.size,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
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
 * Nabízené karty vedle sebe, zvětšené tak, aby vyplnily místo (4 běžně, 5 po bossovi).
 * Stejná kresba karty jako v albu Tvorby balíčku; klepnutí kartu vezme.
 */
@Composable
private fun RewardCardRow(cards: List<Card>, modifier: Modifier = Modifier, onPick: (Card) -> Unit) {
    BoxWithConstraints(modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val n     = cards.size.coerceAtLeast(1)
        val gap   = 12.dp
        val scale = minOf(
            (maxWidth - gap * (n - 1)) / n / 100.dp,
            maxHeight / 140.dp,
            1.7f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            cards.forEach { card ->
                Box(
                    Modifier
                        .size(100.dp * scale, 140.dp * scale)
                        .clip(RoundedCornerShape(6.dp * scale))
                        .clickable { SoundManager.playMenuTap(); onPick(card) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.requiredSize(100.dp, 140.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                        CardPreview(card = card)
                    }
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
    val s       = LocalStrings.current
    val victory = viewModel.rogueVictory.value
    val run     = viewModel.rogueRun.value
    val battlesWon = (run?.battleIndex ?: 0).coerceAtMost(RogueConfig.TOTAL_BATTLES)

    // Stejná scéna jako výsledek v kampani: texturované pozadí s kamenným rámem a obsah
    // uvnitř něj. (Dřív plochý zaoblený panel s barevným okrajem a počet výher v rámečku,
    // který vypadal jako tlačítko.)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Image(
            painter            = painterResource(R.drawable.bg_campaign),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )
        Box(Modifier.fillMaxSize().background(Color(0x4009070D)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start  = campaignInsetX(maxWidth),
                    end    = campaignInsetX(maxWidth),
                    top    = campaignInsetTop(maxHeight) + 8.dp,
                    bottom = campaignInsetBottom(maxHeight) + 8.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            CampaignTitle(
                if (victory) s.rogueRunComplete else s.rogueRunOver,
                fontSize = 36.sp,
                gradient = if (victory) TitleGold else TitleBlood
            )
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.width(320.dp),
                contentScale       = ContentScale.FillWidth
            )
            Text(
                if (victory) s.rogueAllBattles.format(RogueConfig.TOTAL_BATTLES) else s.rogueCastleFell,
                color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center
            )

            // Vyhrané bitvy: údaj s pohárem a postupem po bitvách, ne rámeček připomínající tlačítko
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.trophy_icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    s.rogueBattlesWon.format(battlesWon, RogueConfig.TOTAL_BATTLES),
                    color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
            // Dvanáct bitev ve třech aktech – vyhrané zlatě, boss každého aktu větší
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(RogueConfig.TOTAL_BATTLES) { i ->
                    val boss = i % RogueConfig.BATTLES_PER_ACT == RogueConfig.BATTLES_PER_ACT - 1
                    val won  = i < battlesWon
                    Image(
                        painter            = painterResource(if (boss) R.drawable.skull_icon else R.drawable.shield_icon),
                        contentDescription = null,
                        modifier           = Modifier.size(if (boss) 18.dp else 13.dp).alpha(if (won) 1f else 0.22f)
                    )
                    // mezera mezi akty
                    if (boss && i < RogueConfig.TOTAL_BATTLES - 1) Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            PlainButton(
                text      = s.backToMenuCaps,
                modifier  = Modifier.width(260.dp).height(40.dp),
                textColor = TextPrimary,
                fontSize  = 12.sp,
                paddingH  = 0.dp,
                paddingV  = 0.dp,
                buttonRes = R.drawable.plain_button_longer,
                onClick   = onBack
            )
        }
    }
}
