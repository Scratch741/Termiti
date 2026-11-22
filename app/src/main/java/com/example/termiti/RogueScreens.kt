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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

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
                    ObsButton("Menu", Modifier.width(76.dp).height(28.dp), fontSize = 10.sp, onClick = onMenu)
                    ObsButton(s.surrender, Modifier.width(84.dp).height(28.dp), fontSize = 10.sp, onClick = { showExitConfirm = true })
                    // Info o běhu dostane jen zbylé místo (se zkrácením) – hrad a hradby vpravo
                    // se tak nikdy nezalomí na dva řádky.
                    // Dva řádky: „obtížnost · akt“ a pod tím „následuje Bitva X / 12“ – na jeden
                    // řádek se mezi tlačítka a hrad nevešlo (končilo „následuj…“).
                    val next  = s.rogueNext.format(run.actTitle, run.battleLabel).split("  ·  ", limit = 2)
                    Column(
                        Modifier.weight(1f).padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${run.difficulty.displayName}  ·  ${next[0]}",
                            color = ObsTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (next.size > 1) Text(
                            next[1],
                            color = BronzeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Hrad a hradby v obsidiánové destičce
                    Row(
                        Modifier
                            .height(26.dp)
                            .obsidianSurface(5.dp, Color(0xFF070608), Color(0xFF151216), 0.97f, sheen = false)
                            .bronzeRim(5.dp, level = 0.8f)
                            .padding(horizontal = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(painterResource(R.drawable.castle_icon), contentDescription = null, modifier = Modifier.size(15.dp))
                        Text("${run.hp}/${run.maxCastle}", color = HpGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(4.dp))
                        Image(painterResource(R.drawable.wall_icon), contentDescription = null, modifier = Modifier.size(15.dp))
                        Text("${run.wall}", color = StoneColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
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
                                style = obsLabelStyle(14.sp, ObsText, 1.5.sp)
                            )
                            Text(
                                s.roguePicksLeft.format(run.rewardCardPicksLeft),
                                style = obsLabelStyle(14.sp, BronzeText, 1.sp)
                            )
                        }
                        RewardCardRow(run.rewardCards, Modifier.weight(1f).fillMaxWidth()) { viewModel.pickRewardCard(it) }
                        // Nová nabídka hned pod kartami, ke kterým patří
                        ObsButton(
                            s.rogueReroll.format(run.rerollsLeft),
                            modifier = Modifier.width(210.dp).height(34.dp),
                            fontSize = 12.sp,
                            enabled  = run.rerollsLeft > 0,
                            onClick  = { viewModel.rerollRewardCards() }
                        )
                        Spacer(Modifier.height(20.dp))
                    } else {
                        Text(s.roguePickExtra.uppercase(), style = obsLabelStyle(12.sp, ObsText, 1.sp))
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
                            ObsButton(
                                s.rogueSkip,
                                modifier = Modifier.width(210.dp).height(34.dp),
                                fontSize = 12.sp,
                                onClick  = { viewModel.skipRewardBonus() }
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
                        Box(Modifier.matchParentSize().obsidianSurface(5.dp, Color(0xFF1E1A1E), Color(0xFF0E0C0F), 0.97f).bronzeRim(5.dp, level = 1.1f))
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(painterResource(R.drawable.card_icon), contentDescription = null, modifier = Modifier.size(15.dp), contentScale = ContentScale.Fit)
                            Text(
                                s.rogueYourDeck.format(run.deck.size),
                                style = obsLabelStyle(12.sp, BronzeText), maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    RogueMinesRow(run.startMines)
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
                    .dialogFrame()
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogTitle(LocalStrings.current.surrenderQ, fontSize = 22.sp)
                Spacer(Modifier.height(2.dp))
                Text(LocalStrings.current.rogueSurrenderMsg, color = DialogTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlainButton(
                        text = LocalStrings.current.stay, buttonRes = R.drawable.button_bronze, textColor = BronzeButtonText, fontSize = 13.sp,
                        paddingH = 20.dp, paddingV = 8.dp,
                        onClick = { showExitConfirm = false }
                    )
                    PlainButton(
                        text = LocalStrings.current.surrender, buttonRes = R.drawable.button_iron, textColor = HpRed, fontSize = 13.sp,
                        paddingH = 20.dp, paddingV = 8.dp,
                        onClick = { showExitConfirm = false; onExit() }
                    )
                }
            }
        }
    }
}

/**
 * Nabízené karty vedle sebe (4), zvětšené tak, aby vyplnily místo.
 * Stejná kresba karty jako v albu Tvorby balíčku; klepnutí kartu vezme.
 * Kolem každé karty obrys a měkká záře v barvě vzácnosti (barvy jako při otevírání
 * balíčků); běžná jen jemně, legendární nejsilněji, epická a legendární pomalu pulzují.
 */
@Composable
private fun RewardCardRow(cards: List<Card>, modifier: Modifier = Modifier, onPick: (Card) -> Unit) {
    // Podklad pod kartami = stejný obsidiánový panel jako Složení balíčku a Mana křivka vpravo
    BoxWithConstraints(
        modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .obsidianSurface(8.dp, alpha = 0.82f)
            .bronzeRim(8.dp)
            .bronzeCorners()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val n     = cards.size.coerceAtLeast(1)
        val glow  = REWARD_GLOW
        val gap   = 12.dp + glow
        // Místo pro záři kolem krajních karet, aby ji neořízl rámeček nabídky
        val scale = minOf(
            (maxWidth - gap * (n - 1) - glow * 2) / n / 100.dp,
            (maxHeight - glow * 2) / 140.dp,
            1.7f
        )
        val pulse by rememberInfiniteTransition(label = "rewardGlow").animateFloat(
            initialValue  = 0.75f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label         = "pulse"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            cards.forEach { card ->
                val color    = packRarityColor(card.rarity)
                val strength = rewardGlowStrength(card.rarity)
                val pulsing  = card.rarity >= Rarity.EPIC
                Box(
                    Modifier
                        .size(100.dp * scale, 140.dp * scale)
                        .drawBehind {
                            drawRarityGlow(color, strength * (if (pulsing) pulse else 1f),
                                           glow.toPx(), (6.dp * scale).toPx())
                        }
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

/** Jak daleko za okraj nabízené karty sahá záře vzácnosti. */
private val REWARD_GLOW = 9.dp

private fun rewardGlowStrength(r: Rarity) = when (r) {
    Rarity.COMMON    -> 0.35f
    Rarity.RARE      -> 0.70f
    Rarity.EPIC      -> 0.95f
    Rarity.LEGENDARY -> 1.15f
}

/**
 * Záře a obrys kolem karty (kreslí se za kartu, přesahuje její okraj o [reach]).
 * Hodně tenkých slabých vrstev – sčítají se do plynulého přechodu bez pruhů.
 */
private fun DrawScope.drawRarityGlow(color: Color, strength: Float, reach: Float, corner: Float) {
    val layers = 14
    val layerAlpha = (0.08f * strength).coerceIn(0f, 1f)
    for (l in layers downTo 1) {
        val g = l * reach / layers
        drawRoundRect(
            color        = color.copy(alpha = layerAlpha),
            topLeft      = Offset(-g, -g),
            size         = Size(size.width + 2 * g, size.height + 2 * g),
            cornerRadius = CornerRadius(corner + g, corner + g)
        )
    }
    val o = 1.5.dp.toPx()
    drawRoundRect(
        color        = color.copy(alpha = (0.55f + 0.4f * strength).coerceAtMost(1f)),
        topLeft      = Offset(-o, -o),
        size         = Size(size.width + 2 * o, size.height + 2 * o),
        cornerRadius = CornerRadius(corner + o, corner + o),
        style        = Stroke(2.dp.toPx())
    )
}

/**
 * Doly, se kterými hráč nastoupí do další bitvy: ikona dolu a počet u každé suroviny
 * (bez dolu ztlumeně). Bonus za bosse +1 důl se tu hned projeví.
 */
@Composable
private fun RogueMinesRow(mines: Map<ResourceType, Int>) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Image(painterResource(R.drawable.mine_icon), contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(2.dp))
        for (t in listOf(ResourceType.MAGIC, ResourceType.ATTACK, ResourceType.STONES, ResourceType.CHAOS)) {
            val n = mines[t] ?: 0
            Row(
                Modifier.alpha(if (n > 0) 1f else 0.4f),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Image(painterResource(resourceIconRes(t)), contentDescription = null, modifier = Modifier.size(14.dp))
                Text("$n", color = resColor(t), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(3.dp))
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
            .obsidianSurface(8.dp, alpha = 0.95f)
            .bronzeRim(8.dp, level = 0.9f)
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label.uppercase(), style = obsLabelStyle(7.5.sp, ObsTextMuted, 0.5.sp))
    }
}

// ─── Výběr obtížnosti před zahájením runu ───────────────────────────────────
/**
 * Čtyři obtížnosti vedle sebe: co mění, co dá dokončení a bonus za první průchod.
 * Zamčená obtížnost ukazuje, kterou je potřeba dokončit. Klepnutí obtížnost vybere,
 * run zahájí až tlačítko (výchozí výběr = naposledy hraná odemčená obtížnost).
 */
@Composable
fun RogueDifficultyDialog(
    initial  : RogueDifficulty,
    onDismiss: () -> Unit,
    onStart  : (RogueDifficulty) -> Unit
) {
    val s       = LocalStrings.current
    val cleared = remember { PlayerProfileManager.rogueCleared() }
    var selected by remember { mutableStateOf(initial) }
    val shape   = RoundedCornerShape(16.dp)

    GameDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .dialogFrame()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DialogTitle(s.rogueChooseDifficulty, fontSize = 22.sp)
            Row(
                Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RogueDifficulty.entries.forEach { d ->
                    RogueDifficultyTile(d, cleared, selected == d) { selected = d }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlainButton(
                    text = s.back2, textColor = TextMuted, fontSize = 12.sp,
                    modifier = Modifier.width(130.dp).height(36.dp), paddingH = 4.dp, paddingV = 0.dp,
                    onClick = onDismiss
                )
                PlainButton(
                    text = s.rogueStartRun, textColor = TealLight, fontSize = 12.sp,
                    modifier = Modifier.width(170.dp).height(36.dp), paddingH = 4.dp, paddingV = 0.dp,
                    buttonRes = R.drawable.plain_button_longer,
                    enabled = selected.isUnlocked(cleared),
                    onClick = { onStart(selected) }
                )
            }
        }
    }
}

@Composable
private fun RogueDifficultyTile(d: RogueDifficulty, cleared: Set<String>, selected: Boolean, onClick: () -> Unit) {
    val s        = LocalStrings.current
    val unlocked = d.isUnlocked(cleared)
    val done     = d.name in cleared
    val shape    = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(146.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(Color.Black.copy(alpha = if (selected) 0.62f else 0.45f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Gold.copy(alpha = 0.22f), shape)
            .clickable(enabled = unlocked) { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        val dim = if (unlocked) 1f else 0.45f
        Text(
            d.displayName.uppercase(), color = if (selected) Gold else TextPrimary,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            maxLines = 1, modifier = Modifier.alpha(dim)
        )
        Text(
            d.displayDesc, color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center,
            lineHeight = 11.sp, minLines = 3, modifier = Modifier.alpha(dim)
        )
        Text(
            s.rogueDiffStats.format((d.statMul * 100).roundToInt(), d.healOnWin, d.rerolls),
            color = TextPrimary, fontSize = 9.sp, textAlign = TextAlign.Center,
            lineHeight = 12.sp, modifier = Modifier.alpha(dim)
        )
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Gold.copy(alpha = 0.18f), modifier = Modifier.padding(horizontal = 6.dp))
        Text(s.rogueDiffReward, color = TextMuted, fontSize = 8.sp, modifier = Modifier.alpha(dim))
        Row(
            Modifier.alpha(dim),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(13.dp))
            Text("${d.totalRunGold}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (d.finalPacks > 0) {
                Spacer(Modifier.width(4.dp))
                Image(painterResource(R.drawable.pack_art), contentDescription = null, modifier = Modifier.size(15.dp))
                Text("${d.finalPacks}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        when {
            !unlocked -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.lock_icon), contentDescription = null, modifier = Modifier.size(11.dp))
                Text(
                    s.rogueDiffLocked.format(d.requires?.displayName ?: ""), color = Crimson,
                    fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 10.sp
                )
            }
            done -> Text("\u2713 ${s.rogueDiffCleared}", color = HpGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            else -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("+${d.firstClearPacks}", color = HpGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Image(painterResource(R.drawable.pack_art), contentDescription = null, modifier = Modifier.size(13.dp))
                Text(s.rogueDiffFirstClear, color = HpGreen, fontSize = 8.sp)
            }
        }
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

            viewModel.rogueEndReward.value?.let { RogueEndRewards(it) }

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

/** Souhrn odměn na konci runu: zlaťáky, balíčky, XP, bonus za první dokončení, odemčená obtížnost. */
@Composable
private fun RogueEndRewards(r: RogueEndReward) {
    val s = LocalStrings.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(s.rogueRewardsTitle, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        if (r.gold == 0 && r.packs == 0 && r.xp == 0) {
            Text(s.rogueNoReward, color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (r.gold > 0) {
                Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("+${r.gold}", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (r.packs > 0) {
                Spacer(Modifier.width(8.dp))
                Image(painterResource(R.drawable.pack_art), contentDescription = null, modifier = Modifier.size(22.dp))
                Text("+${r.packs}", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (r.xp > 0) {
                Spacer(Modifier.width(8.dp))
                Text("+${r.xp} XP", color = TealLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (r.firstClearPacks > 0) {
            Text(s.rogueFirstClearBonus.format(r.firstClearPacks), color = HpGreen, fontSize = 11.sp)
        }
        r.unlocked?.let {
            Text(s.rogueUnlockedDiff.format(it.displayName), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
