package com.example.termiti

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ─── Palette ─────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0D0A0E)
private val BgCard      = Color(0xFF1A1320)
private val BgPanel     = Color(0xFF13101A)
private val Gold        = Color(0xFFD4A843)
private val CrimsonDark = Color(0xFF8B1A1A)
private val Crimson     = Color(0xFFBF2D2D)
private val Teal        = Color(0xFF2A7A6F)
private val TealLight   = Color(0xFF3DBFAD)
private val TextPrimary = Color(0xFFEDE0C4)
private val TextMuted   = Color(0xFF7A6E5F)
private val HpGreen     = Color(0xFF4CAF50)
private val HpRed       = Color(0xFFE53935)
private val WallBlue    = Color(0xFF5C9BD6)
private val MagicPurple  = Color(0xFF9B59B6)
private val StoneColor   = Color(0xFFB8A898)
private val AttackRed    = Color(0xFFBF2D2D)
private val ChaosOrange  = Color(0xFFE67E22)
private val DiscardRed   = Color(0xFFE53935)

private fun rarityColor(rarity: Rarity) = when (rarity) {
    Rarity.COMMON    -> Color(0xFF9E9E9E)
    Rarity.RARE      -> Color(0xFF4A90D9)
    Rarity.EPIC      -> Color(0xFF9B59B6)
    Rarity.LEGENDARY -> Color(0xFFD4A843)
}

private fun resourceColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> MagicPurple
    ResourceType.ATTACK -> AttackRed
    ResourceType.STONES -> StoneColor
    ResourceType.CHAOS  -> ChaosOrange
}

private fun resourceIcon(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> "✨"
    ResourceType.ATTACK -> "⚔️"
    ResourceType.STONES -> "🪨"
    ResourceType.CHAOS  -> "🌀"
}

/**
 * Vrátí true/false pokud karta má podmínkový efekt a je/není splněn.
 * Vrátí null pokud karta žádnou podmínku nemá.
 * Prochází všechny efekty, ne jen první (podmínka může být i na 2. místě).
 */
private fun cardConditionMet(
    card: Card,
    resources: Map<ResourceType, Int>,
    wallHp: Int,
    castleHp: Int
): Boolean? {
    val ce = card.effects.filterIsInstance<CardEffect.ConditionalEffect>().firstOrNull()
        ?: return null
    return when (val c = ce.condition) {
        is Condition.ResourceAbove -> (resources[c.type] ?: 0) > c.threshold
        is Condition.WallAbove     -> wallHp   > c.threshold
        is Condition.WallBelow     -> wallHp   < c.threshold
        is Condition.CastleAbove   -> castleHp > c.threshold
    }
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
    is CardEffect.StealCastle       -> "🧛"
    null                            -> "❓"
}

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel(),
    onBackToMenu: () -> Unit = {},
    isArena: Boolean = false,
    arenaWins: Int = 0,
    onArenaWin: () -> Unit = {},
    onArenaLose: () -> Unit = {}
) {
    val state            by viewModel.gameState
    val log              by viewModel.log
    val gameOver         by viewModel.gameOver
    val lastCard         by viewModel.lastCard
    val lastCardAction   by viewModel.lastCardAction
    val lastCardIsPlayer by viewModel.lastCardIsPlayer
    val cardHistory      by viewModel.cardHistory
    val lostToOpponent   by viewModel.lostToOpponent
    val isMulligan       by viewModel.isMulligan
    val mulliganSelected by viewModel.mulliganSelected
    val isComboTurn      by viewModel.isPlayerComboTurn

    var showMenuConfirm  by remember { mutableStateOf(false) }
    var showLostCards    by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Pozadí ───────────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Tmavý overlay – zachová čitelnost UI (0x77 = ~47% černá)
        Box(Modifier.fillMaxSize().background(Color(0x77000000)))

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Hlavní řada – boční panely od vrchu + střed ───────────
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                PlayerPanel(
                    label = "Nepřítel", playerState = state.aiState, isEnemy = true,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )

                VerticalDivider(color = Gold.copy(alpha = 0.25f))

                // Střed
                Column(modifier = Modifier.fillMaxHeight().weight(2f)) {

                    OfflineStatusBar(
                        activePlayer = state.activePlayer,
                        isComboTurn  = isComboTurn,
                        currentTurn  = state.currentTurn,
                        arenaWins    = if (isArena) arenaWins else -1,
                        modifier     = Modifier.fillMaxWidth()
                    )

                    OfflineAiHandRow(
                        handSize = state.aiState.hand.size,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = Gold.copy(alpha = 0.2f))

                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                        // ── Vlevo: poslední zahraná karta ──────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(BgDeep.copy(alpha = 0.60f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (lastCard != null) {
                                val ringColor = when (lastCardAction) {
                                    CardAction.PLAYED    -> if (lastCardIsPlayer) Teal else Crimson
                                    CardAction.DISCARDED -> if (lastCardIsPlayer) Teal.copy(alpha = 0.6f) else Crimson.copy(alpha = 0.6f)
                                    CardAction.BURNED    -> Color(0xFFE07B39)
                                    CardAction.STOLEN    -> Color(0xFF9B59B6)
                                }
                                Box(Modifier.scale(1.2f)) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(2.5.dp, ringColor, RoundedCornerShape(8.dp))
                                    ) {
                                        CardView(card = lastCard!!, canPlay = false, discardMode = false, onClick = {})
                                    }
                                }
                            } else {
                                Text("—", color = TextMuted.copy(alpha = 0.2f), fontSize = 16.sp)
                            }
                        }

                        VerticalDivider(color = Gold.copy(alpha = 0.12f))

                        // ── Vpravo: log + historie + konec tahu ────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(BgDeep.copy(alpha = 0.60f))
                        ) {
                            LogPanel(
                                log        = log,
                                modifier   = Modifier.fillMaxWidth().weight(1f),
                                scrollable = true
                            )

                            if (cardHistory.isNotEmpty()) {
                                HorizontalDivider(color = Gold.copy(alpha = 0.1f))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(BgDeep.copy(alpha = 0.35f))
                                        .padding(horizontal = 4.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 📜 ikona s badge ztracených karet – kliknutí otevře popup
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .clickable(enabled = lostToOpponent.isNotEmpty()) {
                                                showLostCards = true
                                            }
                                    ) {
                                        Text("📜", fontSize = 9.sp)
                                        if (lostToOpponent.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-2).dp)
                                                    .size(9.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFFBF2D2D)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${lostToOpponent.size}",
                                                    color = Color.White,
                                                    fontSize = 5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        contentPadding        = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        itemsIndexed(cardHistory) { _, entry ->
                                            OfflineMiniHistoryCard(
                                                card     = entry.card,
                                                action   = entry.action,
                                                isMine   = entry.isMine,
                                                onClick  = { showLostCards = true }
                                            )
                                        }
                                    }
                                }
                            }

                            val isPlayerActive = state.activePlayer == ActivePlayer.PLAYER && gameOver == null
                            val active         = isPlayerActive || isComboTurn
                            val btnLabel       = if (isComboTurn) "⚡ Konec combo" else "⏩ Konec tahu"
                            val btnColor       = when {
                                isComboTurn -> Gold
                                active      -> TealLight
                                else        -> TextMuted.copy(alpha = 0.35f)
                            }
                            HorizontalDivider(color = Gold.copy(alpha = 0.12f))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (active) Modifier.clickable {
                                        if (isComboTurn) viewModel.endPlayerTurn() else viewModel.waitTurn()
                                    } else Modifier)
                                    .background(btnColor.copy(alpha = 0.08f))
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(btnLabel, color = btnColor, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }

                VerticalDivider(color = Gold.copy(alpha = 0.25f))

                PlayerPanel(
                    label = "Ty", playerState = state.playerState, isEnemy = false,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }

            // ── Ruka hráče – přes celou šířku dole ───────────────────
            HorizontalDivider(color = Gold.copy(alpha = 0.2f))
            HandPanel(
                hand             = state.playerState.hand,
                isPlayerTurn     = state.activePlayer == ActivePlayer.PLAYER && gameOver == null,
                isComboTurn      = isComboTurn,
                playerResources  = state.playerState.resources,
                onPlayCard       = { viewModel.playCard(it) },
                onDiscardCard    = { viewModel.discardCard(it) },
                onWait           = { viewModel.waitTurn() },
                onEndTurn        = { viewModel.endPlayerTurn() },
                showHeader       = false,
                playerWallHp     = state.playerState.wallHP,
                playerCastleHp   = state.playerState.castleHP,
                modifier         = Modifier.fillMaxWidth().height(150.dp)
                                           .background(BgDeep.copy(alpha = 0.82f))
            )
        }

        // ── Menu tlačítko overlay ─────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 38.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                .clickable { showMenuConfirm = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("☰", color = TextMuted, fontSize = 12.sp)
        }

        gameOver?.let { result ->
            if (isArena) {
                val isPlayerWin = result.isPlayerWin()
                ArenaGameOverDialog(
                    result       = result,
                    wins         = if (isPlayerWin) arenaWins + 1 else arenaWins,
                    isPlayerWin  = isPlayerWin,
                    onNextBattle = { onArenaWin() },
                    onEndArena   = { onArenaLose() }
                )
            } else {
                GameOverDialog(
                    result    = result,
                    onRestart = { viewModel.restartGame() },
                    onMenu    = { viewModel.restartGame(); onBackToMenu() }
                )
            }
        }

        if (showMenuConfirm) {
            AlertDialog(
                onDismissRequest = { showMenuConfirm = false },
                containerColor   = Color(0xFF1A1320),
                titleContentColor = Color(0xFFEDE0C4),
                textContentColor  = Color(0xFF7A6E5F),
                title = { Text("Opustit hru?", fontWeight = FontWeight.Bold) },
                text  = { Text("Rozehraná partie bude ztracena. Opravdu chceš odejít do menu?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.restartGame(); onBackToMenu() }) {
                        Text("Odejít", color = Color(0xFFBF2D2D), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMenuConfirm = false }) {
                        Text("Zůstat", color = Color(0xFF3DBFAD), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (isMulligan) {
            MulliganOverlay(
                hand             = state.playerState.hand,
                selectedIds      = mulliganSelected,
                onToggle         = { viewModel.toggleMulliganCard(it) },
                onConfirm        = { viewModel.confirmMulligan() },
                onSkip           = { viewModel.skipMulligan() }
            )
        }

        if (showLostCards) {
            LostCardsOverlay(
                lostCards = lostToOpponent,
                onDismiss = { showLostCards = false }
            )
        }
    }
}

// ─── Offline status bar ───────────────────────────────────────────────────────

@Composable
private fun OfflineStatusBar(
    activePlayer: ActivePlayer,
    isComboTurn: Boolean,
    currentTurn: Int,
    arenaWins: Int = -1,
    modifier: Modifier = Modifier
) {
    val isPlayerTurn = activePlayer == ActivePlayer.PLAYER
    val color = if (isPlayerTurn || isComboTurn) Teal else Crimson
    val text = when {
        isComboTurn  -> "⚡  COMBO"
        isPlayerTurn -> "⚔️  TVŮJ TAH"
        else         -> "⏳  AI HRAJE"
    }
    Row(
        modifier
            .background(color.copy(alpha = 0.08f))
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(color.copy(alpha = 0.2f),
                    Offset(0f, size.height - stroke), Offset(size.width, size.height - stroke), stroke)
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (arenaWins >= 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp))
                        .background(Gold.copy(alpha = 0.1f))
                        .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("⚔️ $arenaWins", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Kolo $currentTurn", color = TextMuted, fontSize = 9.sp)
        }
    }
}

// ─── Offline AI hand row ──────────────────────────────────────────────────────

@Composable
private fun OfflineAiHandRow(handSize: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Nepřítel: ", color = TextMuted, fontSize = 9.sp)
        LazyRow(
            modifier              = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.Center,
            contentPadding        = PaddingValues(horizontal = 2.dp)
        ) {
            items(handSize) { CardBack() }
        }
    }
}

// ─── Offline mini history card ────────────────────────────────────────────────

@Composable
private fun OfflineMiniHistoryCard(
    card: Card, action: CardAction, isMine: Boolean,
    onClick: () -> Unit = {}
) {
    val borderColor = when (action) {
        CardAction.BURNED    -> Color(0xFFE07B39).copy(alpha = 0.85f)
        CardAction.STOLEN    -> Color(0xFF9B59B6).copy(alpha = 0.85f)
        CardAction.DISCARDED -> if (isMine) Teal.copy(alpha = 0.55f) else Crimson.copy(alpha = 0.55f)
        CardAction.PLAYED    -> if (isMine) TealLight.copy(alpha = 0.80f) else Crimson.copy(alpha = 0.80f)
    }
    val bgColor = when (action) {
        CardAction.BURNED    -> Color(0xFF1A1000)
        CardAction.STOLEN    -> Color(0xFF1A0A2A)
        CardAction.DISCARDED -> Color(0xFF250A0A)
        CardAction.PLAYED    -> Color(0xFF1A1320)
    }
    val overlayIcon = when (action) {
        CardAction.BURNED    -> "🔥"
        CardAction.STOLEN    -> "🃏"
        CardAction.DISCARDED -> "✕"
        CardAction.PLAYED    -> null
    }
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 32.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
    ) {
        MiniCardFront(card = card)
        if (overlayIcon != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    overlayIcon,
                    color      = borderColor.copy(alpha = 0.95f),
                    fontSize   = if (action == CardAction.BURNED) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── AI Hand Row ──────────────────────────────────────────────────────────────
@Composable
fun AiHandRow(
    handSize: Int,
    lastPlayed: Card?,
    currentTurn: Int,
    onMenu: () -> Unit,
    arenaWins: Int = -1,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(BgPanel.copy(alpha = 0.65f))
            .padding(vertical = 5.dp)
    ) {
        // Menu tlačítko vlevo
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                .clickable { onMenu() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("☰ Menu", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        // Karty AI uprostřed
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (lastPlayed != null) {
                CardBackPlayed(lastPlayed)
                Spacer(Modifier.width(5.dp))
            }
            repeat(handSize) { i ->
                if (i > 0) Spacer(Modifier.width(5.dp))
                CardBack()
            }
        }

        // Vpravo: arena badge nebo čítač kol
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (arenaWins >= 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp))
                        .background(Gold.copy(alpha = 0.1f))
                        .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("⚔️ $arenaWins", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Kolo $currentTurn", color = Gold.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 22.dp, height = 32.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E1530))
            .border(1.dp, CrimsonDark.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = CrimsonDark.copy(alpha = 0.6f), fontSize = 10.sp,
            fontWeight = FontWeight.Bold)
    }
}

/** Vrátí název drawable rámu podle typu zdroje karty. */
private fun cardFrameName(costType: ResourceType) = when (costType) {
    ResourceType.MAGIC  -> "card_frame_magic"
    ResourceType.ATTACK -> "card_frame_attack"
    ResourceType.CHAOS  -> "card_frame_chaos"
    ResourceType.STONES -> "card_frame_stones"
}

/** Miniatura líce karty – stejná velikost jako CardBack (22×32 dp). */
@Composable
fun MiniCardFront(card: Card, modifier: Modifier = Modifier) {
    val borderColor = rarityColor(card.rarity)
    if (card.artResId != null) {
        // Miniatura textured karty
        val context = LocalContext.current
        val frameResId = remember(card.costType) {
            context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
        }
        Box(
            modifier = modifier
                .size(width = 22.dp, height = 32.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, borderColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
        ) {
            Image(
                painter = painterResource(card.artResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(card.artBiasX, card.artBiasY)
            )
            if (frameResId != 0) {
                Image(
                    painter = painterResource(frameResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            // Cena v levém horním rohu
            Box(
                Modifier.align(Alignment.TopStart).padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("${card.cost}", color = Color.White, fontSize = 4.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    } else {
        val costColor = resourceColor(card.costType)
        Column(
            modifier = modifier
                .size(width = 22.dp, height = 32.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgCard)
                .border(1.dp, borderColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                .padding(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(effectIcon(card), fontSize = 8.sp, lineHeight = 9.sp)
            Text(
                text = card.name, color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 3.8.sp, lineHeight = 4.5.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${resourceIcon(card.costType)}${card.cost}", color = costColor,
                fontSize = 4.5.sp, lineHeight = 5.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

// Zvýrazněná odhalená mini karta – poslední zahraná AI kartou
@Composable
private fun CardBackPlayed(card: Card) {
    val costColor = resourceColor(card.costType)
    Column(
        modifier = Modifier
            .size(width = 36.dp, height = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BgCard)
            .border(1.5.dp, Crimson, RoundedCornerShape(4.dp))
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(effectIcon(card), fontSize = 11.sp)
        Text(card.name, color = TextPrimary, fontSize = 5.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 6.sp)
        Text("${resourceIcon(card.costType)}${card.cost}", color = costColor, fontSize = 5.5.sp)
    }
}

// ─── Player Panel ─────────────────────────────────────────────────────────────
@Composable
fun PlayerPanel(
    label: String, playerState: PlayerState, isEnemy: Boolean, modifier: Modifier = Modifier
) {
    val accent     = if (isEnemy) Crimson else Teal
    val castleHp   = playerState.castleHP
    val wallHp     = playerState.wallHP
    val magic      = playerState.resources[ResourceType.MAGIC]  ?: 0
    val attack     = playerState.resources[ResourceType.ATTACK] ?: 0
    val stones     = playerState.resources[ResourceType.STONES] ?: 0
    val chaos      = playerState.resources[ResourceType.CHAOS]  ?: 0
    val mineMagic  = playerState.mines[ResourceType.MAGIC]  ?: 0
    val mineAtk    = playerState.mines[ResourceType.ATTACK] ?: 0
    val mineSto    = playerState.mines[ResourceType.STONES] ?: 0
    val mineChaos  = playerState.mines[ResourceType.CHAOS]  ?: 0
    val hasChaos   = chaos > 0 || mineChaos > 0

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Brush.verticalGradient(
                if (isEnemy) listOf(BgPanel.copy(alpha = 0.70f), BgDeep.copy(alpha = 0.70f))
                else         listOf(BgDeep.copy(alpha = 0.70f),  BgPanel.copy(alpha = 0.70f))
            ))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Jméno + balíčky
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), color = accent, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DeckChip("🃏", playerState.deck.size, "bal.")
                DeckChip("🗑", playerState.discardPile.size, "odh.")
            }
        }

        Spacer(Modifier.height(6.dp))

        // Vizuál hradu/hradeb – vyplní volný prostor
        CastleWallVisual(castleHp = castleHp, wallHp = wallHp,
            modifier = Modifier.fillMaxWidth().weight(1f))

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Gold.copy(alpha = 0.1f))
        Spacer(Modifier.height(6.dp))

        ResourcesRow(
            magic, mineMagic,
            attack, mineAtk,
            stones, mineSto,
            chaos, mineChaos, hasChaos
        )
    }
}

@Composable
fun CastleWallVisual(castleHp: Int, wallHp: Int, modifier: Modifier = Modifier) {
    val castleColor = if (castleHp > 15) HpGreen else HpRed
    val castleFrac by animateFloatAsState(
        (castleHp / 100f).coerceIn(0f, 1f),
        tween(600, easing = EaseOutCubic), label = "castle"
    )
    val wallFrac by animateFloatAsState(
        (wallHp / 50f).coerceIn(0f, 1f),
        tween(600, easing = EaseOutCubic), label = "wall"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        // ── Hradby (wall) ────────────────────────────────────────
        Column(
            modifier = Modifier.width(26.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val w = size.width; val h = size.height
                val fillH = h * wallFrac
                val r = CornerRadius(3.dp.toPx())

                drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = r)
                if (fillH > 0f)
                    drawRect(WallBlue.copy(alpha = 0.65f),
                        topLeft = Offset(0f, h - fillH), size = Size(w, fillH))

                val rows = 5
                for (i in 1 until rows) {
                    val y = h / rows * i
                    drawLine(Color.Black.copy(alpha = 0.28f), Offset(0f, y), Offset(w, y), 1f)
                }
                for (row in 0 until rows) {
                    val xOff = if (row % 2 == 0) 0f else w / 2f
                    val y1 = h / rows * row; val y2 = h / rows * (row + 1)
                    drawLine(Color.Black.copy(alpha = 0.2f), Offset(xOff, y1), Offset(xOff, y2), 1f)
                }
                drawRoundRect(WallBlue.copy(alpha = 0.55f), cornerRadius = r, style = Stroke(1f))
            }
            Spacer(Modifier.height(3.dp))
            Text("🧱 $wallHp", color = WallBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }

        // ── Hrad (castle tower) ───────────────────────────────────
        Column(
            modifier = Modifier.width(34.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val w = size.width; val h = size.height
                // Cimbuří = 15 % výšky
                val merH = h * 0.15f
                val merW = w / 5f
                val bodyTop = merH
                val bodyH = h - bodyTop
                val fillH = bodyH * castleFrac
                val fillTop = bodyTop + bodyH - fillH

                drawRect(Color.White.copy(alpha = 0.07f),
                    topLeft = Offset(0f, bodyTop), size = Size(w, bodyH))
                if (fillH > 0f)
                    drawRect(castleColor.copy(alpha = 0.60f),
                        topLeft = Offset(0f, fillTop), size = Size(w, fillH))

                for (i in listOf(0, 2, 4)) {
                    val mx = merW * i
                    drawRect(Color.White.copy(alpha = 0.07f),
                        topLeft = Offset(mx, 0f), size = Size(merW, merH))
                    drawRect(castleColor.copy(alpha = 0.42f),
                        topLeft = Offset(mx, 0f), size = Size(merW, merH), style = Stroke(1f))
                }
                drawRect(castleColor.copy(alpha = 0.50f),
                    topLeft = Offset(0f, bodyTop), size = Size(w, bodyH), style = Stroke(1f))

                val slitW = w * 0.13f
                val slitH = bodyH * 0.28f
                drawRect(Color.Black.copy(alpha = 0.65f),
                    topLeft = Offset((w - slitW) / 2f, bodyTop + bodyH * 0.22f),
                    size = Size(slitW, slitH))
            }
            Spacer(Modifier.height(3.dp))
            Text("🏰 $castleHp", color = castleColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResourcesRow(
    magic: Int, mineMagic: Int,
    attack: Int, mineAtk: Int,
    stones: Int, mineSto: Int,
    chaos: Int, mineChaos: Int,
    @Suppress("UNUSED_PARAMETER") showChaos: Boolean = true   // chaos se zobrazuje vždy
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResourceChip("✨", magic,  mineMagic, MagicPurple)
        ResourceChip("⚔️", attack, mineAtk,  AttackRed)
        ResourceChip("🪨", stones, mineSto,  StoneColor)
        ResourceChip("🌀", chaos,  mineChaos, ChaosOrange)
    }
}

@Composable
private fun ResourceChip(icon: String, value: Int, mine: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text("$value", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("+$mine", color = color.copy(alpha = 0.55f), fontSize = 9.sp)
    }
}

@Composable
fun DeckChip(icon: String, count: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(icon, fontSize = 10.sp)
        Spacer(Modifier.width(2.dp))
        Text("$count", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(label, color = TextMuted, fontSize = 8.sp)
    }
}

// ─── Log ──────────────────────────────────────────────────────────────────────
@Composable
fun LogPanel(
    log: List<String>,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false   // true = multiplayer styl (nejnovější nahoře, scrollovatelný)
) {
    if (!scrollable) {
        // ── Původní offline styl ──────────────────────────────────────────────
        Column(modifier = modifier.background(BgDeep).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("LOG", color = TextMuted, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (log.isEmpty()) {
                Text("— Hra začíná —", color = TextMuted, fontSize = 11.sp, fontStyle = FontStyle.Italic)
            } else {
                log.takeLast(5).forEachIndexed { i, line ->
                    val alpha = 0.3f + (i.toFloat() / log.size.coerceAtLeast(1)) * 0.7f
                    Text(line, color = Gold.copy(alpha = alpha), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        // ── Multiplayer styl: nejnovější nahoře, scrollovatelný ───────────────
        val reversed   = remember(log) { log.reversed() }
        val listState  = rememberLazyListState()

        // Při každé nové zprávě skroluj na začátek (= nejnovější)
        LaunchedEffect(log.size) {
            if (reversed.isNotEmpty()) listState.animateScrollToItem(0)
        }

        LazyColumn(
            state             = listState,
            modifier          = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            reverseLayout     = false
        ) {
            itemsIndexed(reversed) { index, line ->
                // Nejnovější (index 0) plná barva, starší postupně vybledají
                val alpha = (1f - index * 0.22f).coerceAtLeast(0.25f)
                Text(
                    text     = line,
                    color    = Gold.copy(alpha = alpha),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (filled) 0.2f else 0.08f))
            .border(1.dp, color.copy(alpha = if (filled) 0.6f else 0.4f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Hand ─────────────────────────────────────────────────────────────────────
private val ComboYellow = Color(0xFFFFD600)

@Composable
fun HandPanel(
    hand: List<Card>,
    isPlayerTurn: Boolean,
    isComboTurn: Boolean,
    playerResources: Map<ResourceType, Int>,
    onPlayCard: (Card) -> Unit,
    onDiscardCard: (Card) -> Unit,
    onWait: () -> Unit,
    onEndTurn: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,      // false = skryje "RUKA (n)" a tlačítko čekat
    playerWallHp: Int = 0,
    playerCastleHp: Int = 0
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {

        // ── Záhlaví ruky ─────────────────────────────────────────────────────
        if (showHeader) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RUKA (${hand.size})",
                    color = TextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                if (isPlayerTurn || isComboTurn) {
                    ActionChip(label = "⏳ Čekat", color = TealLight, onClick = onWait)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp)
            ) {
                hand.forEach { card ->
                    val affordable = (playerResources[card.costType] ?: 0) >= card.cost
                    CardView(
                        card          = card,
                        canPlay       = isPlayerTurn && affordable,
                        isComboCard   = card.isCombo,
                        discardMode   = false,
                        onClick       = { onPlayCard(card) },
                        onDiscard     = if (isPlayerTurn) { { onDiscardCard(card) } } else null,
                        conditionMet  = cardConditionMet(card, playerResources, playerWallHp, playerCastleHp)
                    )
                }
            }
        }
    }
}

// ─── Card ─────────────────────────────────────────────────────────────────────
@Composable
fun CardView(
    card: Card,
    canPlay: Boolean,
    discardMode: Boolean,
    onClick: () -> Unit,
    onDiscard: (() -> Unit)? = null,
    isComboCard: Boolean = card.isCombo,
    conditionMet: Boolean? = null   // null = karta nemá podmínku
) {
    val offsetY   = remember { Animatable(0f) }
    val scope     = rememberCoroutineScope()
    val density   = LocalDensity.current
    val threshold = remember(density) { with(density) { 68.dp.toPx() } }
    val progress  = (-offsetY.value / threshold).coerceIn(0f, 1f)
    val isDragging = offsetY.value < -6f

    val dragModifier = if (onDiscard != null) Modifier.pointerInput(card.id) {
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    if (offsetY.value <= -threshold) onDiscard()
                    offsetY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 280f))
                }
            },
            onDragCancel = { scope.launch { offsetY.animateTo(0f, spring()) } },
            onVerticalDrag = { _, delta ->
                scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtMost(0f)) }
            }
        )
    } else Modifier

    if (card.artResId != null) {
        // ── Textured card layout ─────────────────────────────────────────────
        CardViewTextured(
            card        = card,
            artResId    = card.artResId,
            canPlay     = canPlay,
            discardMode = discardMode,
            isDragging  = isDragging,
            progress    = progress,
            offsetY     = offsetY,
            conditionMet = conditionMet,
            isComboCard = isComboCard,
            dragModifier = dragModifier,
            onClick     = onClick
        )
    } else {
        // ── Classic card layout ──────────────────────────────────────────────
        val costColor = resourceColor(card.costType)
        val borderColor = when {
            isDragging             -> DiscardRed.copy(alpha = 0.35f + progress * 0.6f)
            discardMode            -> DiscardRed.copy(alpha = 0.7f)
            canPlay && isComboCard -> ComboYellow
            canPlay                -> Gold
            else                   -> TextMuted.copy(alpha = 0.25f)
        }
        val bgColor = when {
            isDragging || discardMode -> Color(0xFF250A0A)
            canPlay && isComboCard    -> Color(0xFF1E1A10)
            canPlay                   -> BgCard
            else                      -> BgCard.copy(alpha = 0.45f)
        }

        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 140.dp)
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .then(dragModifier)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .then(if (canPlay || discardMode) Modifier.clickable { onClick() } else Modifier)
                    .padding(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(effectIcon(card), fontSize = 16.sp)
                    if (discardMode) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(DiscardRed.copy(alpha = 0.2f))
                                .border(1.dp, DiscardRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) { Text("✕", color = DiscardRed, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(costColor.copy(alpha = 0.18f))
                                .border(1.dp, costColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(resourceIcon(card.costType), fontSize = 10.sp)
                            Spacer(Modifier.width(2.dp))
                            Text("${card.cost}", color = costColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (conditionMet != null) {
                    val condColor = if (conditionMet) Color(0xFF4DB86E) else Color(0xFF888888)
                    val condText  = if (conditionMet) "✓ SPLNĚNO"       else "✗ NESPLNĚNO"
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(condColor.copy(alpha = if (conditionMet) 0.15f else 0.08f))
                            .border(0.5.dp, condColor.copy(alpha = if (conditionMet) 0.7f else 0.35f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(condText, color = condColor, fontSize = 6.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
                Text(card.name,
                    color = if (canPlay || discardMode || isDragging) TextPrimary else TextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                Text(card.description,
                    color = TextMuted, fontSize = 8.sp, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isComboCard) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(ComboYellow.copy(alpha = if (canPlay) 0.18f else 0.07f))
                                .border(0.5.dp, ComboYellow.copy(alpha = if (canPlay) 0.6f else 0.2f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡ COMBO", color = ComboYellow.copy(alpha = if (canPlay) 1f else 0.4f),
                                fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    val rc = rarityColor(card.rarity)
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(rc.copy(alpha = if (canPlay || discardMode) 0.8f else 0.3f)))
                }
            }
            if (isDragging) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        .background(DiscardRed.copy(alpha = (progress * 0.55f).coerceAtMost(0.55f))),
                    contentAlignment = Alignment.Center
                ) { if (progress > 0.35f) Text("🗑️", fontSize = (12 + progress * 16).sp) }
            }
        }
    }
}

// ── Textured card view ────────────────────────────────────────────────────────
@Composable
private fun CardViewTextured(
    card: Card,
    artResId: Int,
    canPlay: Boolean,
    discardMode: Boolean,
    isDragging: Boolean,
    progress: Float,
    offsetY: Animatable<Float, *>,
    conditionMet: Boolean?,
    isComboCard: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Načtení rámu karty dynamicky podle costType (card_frame_magic/attack/chaos/stones).
    // Pokud soubor neexistuje, vrátí 0 a rám se nepřikresluje.
    val frameResId = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
    }

    val borderColor = when {
        isDragging             -> DiscardRed.copy(alpha = 0.35f + progress * 0.6f)
        discardMode            -> DiscardRed.copy(alpha = 0.8f)
        canPlay && isComboCard -> ComboYellow
        canPlay                -> Gold
        else                   -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 140.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .then(dragModifier)
            .clip(RoundedCornerShape(6.dp))
            .then(if (borderColor != Color.Transparent)
                Modifier.border(1.5.dp, borderColor, RoundedCornerShape(6.dp)) else Modifier)
            .then(if (canPlay || discardMode) Modifier.clickable { onClick() } else Modifier)
    ) {
        // Vrstva 1: ilustrace karty (vyplní celou plochu, ořízne se rámem)
        Image(
            painter = painterResource(artResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(card.artBiasX, card.artBiasY),
            alpha = if (canPlay || discardMode) 1f else 0.6f
        )

        // Vrstva 2: rám karty (průhlednost v oblasti ilustrace zajistí soubor card_frame.png)
        if (frameResId != 0) {
            Image(
                painter = painterResource(frameResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Vrstva 3: cena karty v levém horním kruhu rámu
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 0.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${card.cost}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Vrstva 4: název karty v červeném obloukovém pásu (~70–90 dp od vrchu)
        // Proporce rámu 816×1194 px → výška karty 140 dp: y = px * (140/1194)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 70.dp)
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                card.name,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Vrstva 5: text karty pod názvem (~94–124 dp od vrchu)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 94.dp)
                .fillMaxWidth()
                .height(28.dp)
                .clipToBounds()
                .padding(horizontal = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Indikátor podmínky
            if (conditionMet != null) {
                val condColor = if (conditionMet) Color(0xFF4DB86E) else Color(0xFF888888)
                val condText  = if (conditionMet) "✓ SPLNĚNO" else "✗ NESPLNĚNO"
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(condColor.copy(alpha = 0.25f))
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(condText, color = condColor, fontSize = 5.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (isComboCard) {
                Text("⚡ COMBO", color = ComboYellow, fontSize = 6.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                card.description,
                color = Color(0xFFDDD0B0),
                fontSize = 7.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 9.sp
            )
        }

        // Vrstva 6: typ karty v dolním pruhu (~123–135 dp od vrchu, nad spodním rámem)
        if (card.type.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 120.dp)
                    .fillMaxWidth()
                    .height(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    card.type.uppercase(),
                    color = Color(0xFFD4B870),
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Vrstva 7: overlay při tažení / discard
        if (isDragging) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(DiscardRed.copy(alpha = (progress * 0.55f).coerceAtMost(0.55f))),
                contentAlignment = Alignment.Center
            ) { if (progress > 0.35f) Text("🗑️", fontSize = (12 + progress * 16).sp) }
        } else if (discardMode) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(DiscardRed.copy(alpha = 0.35f)),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    Modifier.padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DiscardRed.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("✕", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Mulligan Overlay ────────────────────────────────────────────────────────
@Composable
fun MulliganOverlay(
    hand: List<Card>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE5000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1520), BgPanel)))
                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Nadpis
            Text(
                "MULLIGAN",
                color = Gold, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 5.sp
            )
            Text(
                if (selectedIds.isEmpty())
                    "Klikni na karty, které chceš vyměnit za náhodné z balíku"
                else
                    "Vyměníš ${selectedIds.size} ${if (selectedIds.size == 1) "kartu" else "karty"} — klikni znovu pro zrušení výběru",
                color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center
            )

            // Karty
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hand.forEach { card ->
                    val isSelected = card.id in selectedIds
                    Box(
                        modifier = Modifier.clickable { onToggle(card.id) }
                    ) {
                        CardView(
                            card        = card,
                            canPlay     = !isSelected,
                            discardMode = isSelected,
                            onClick     = { onToggle(card.id) }
                        )
                        // Overlay na vybrané kartě
                        if (isSelected) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DiscardRed.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↩", fontSize = 26.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Tlačítka
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Přeskočit
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable { onSkip() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Hrát bez výměny",
                        color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }

                // Vyměnit
                val canConfirm = selectedIds.isNotEmpty()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (canConfirm) Teal.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.03f)
                        )
                        .border(
                            1.dp,
                            if (canConfirm) TealLight.copy(alpha = 0.55f)
                            else TextMuted.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .then(if (canConfirm) Modifier.clickable { onConfirm() } else Modifier)
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (canConfirm) "Vyměnit (${selectedIds.size})" else "Vyměnit",
                        color = if (canConfirm) TealLight else TextMuted.copy(alpha = 0.3f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Lost Cards Overlay ───────────────────────────────────────────────────────
@Composable
fun LostCardsOverlay(lostCards: List<CardHistoryEntry>, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD8000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false, onClick = {})   // zamezí průchodu kliknutí
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1020), BgPanel)))
                .border(1.dp, Color(0xFF9B59B6).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Nadpis
            Text(
                "SPÁLENÉ & UKRADENÉ KARTY",
                color = Color(0xFF9B59B6), fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(
                "Karty, o které tě připravil soupeř.",
                color = TextMuted, fontSize = 10.sp
            )

            if (lostCards.isEmpty()) {
                Text(
                    "Žádná karta zatím nebyla spálena ani ukradena.",
                    color = TextMuted.copy(alpha = 0.6f), fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                // Scrollovatelná mřížka karet
                val scroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .horizontalScroll(scroll)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lostCards.forEach { entry ->
                        val badgeColor = if (entry.action == CardAction.STOLEN)
                            Color(0xFF9B59B6) else Color(0xFFE07B39)
                        val badgeText  = if (entry.action == CardAction.STOLEN) "🃏 UKRADENO" else "🔥 SPÁLENO"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .border(1.5.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            ) {
                                CardView(card = entry.card, canPlay = false, discardMode = false, onClick = {})
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(badgeText, color = badgeColor, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Zavřít
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 28.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Zavřít", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Game Over ────────────────────────────────────────────────────────────────
@Composable
fun GameOverDialog(result: GameResult, onRestart: () -> Unit, onMenu: () -> Unit) {
    val (title, sub) = when (result) {
        GameResult.PLAYER_CASTLE_DESTROYED -> "Prohrál jsi"  to "Tvůj hrad byl zničen."
        GameResult.AI_CASTLE_DESTROYED     -> "Vítězství!"   to "Zničil jsi nepřátelský hrad."
        GameResult.PLAYER_CASTLE_BUILT     -> "Vítězství!"   to "Postavil jsi mocný hrad."
        GameResult.AI_CASTLE_BUILT         -> "Prohrál jsi"  to "Nepřítel dokončil svůj hrad."
        GameResult.PLAYER_HP_WINS          -> "Vítězství!"   to "Balíčky došly – tvůj hrad je vyšší."
        GameResult.AI_HP_WINS              -> "Prohrál jsi"  to "Balíčky došly – nepřítel má vyšší hrad."
        GameResult.DRAW                    -> "Remíza"       to "Balíčky došly – hrady jsou stejně vysoké."
    }
    val isWin = result.isPlayerWin()

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(
                    if (isWin) listOf(Color(0xFF1A2A1A), BgPanel)
                    else listOf(Color(0xFF2A1010), BgPanel)
                ))
                .border(1.dp,
                    if (isWin) TealLight.copy(alpha = 0.5f) else Crimson.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isWin) "⚔️" else "💀", fontSize = 36.sp)
            Spacer(Modifier.height(10.dp))
            Text(title.uppercase(), color = if (isWin) TealLight else Crimson,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(sub, color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWin) Teal else CrimsonDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("HRÁT ZNOVU", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onMenu,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2030)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("MENU", color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}

// ─── Arena Game Over Dialog ───────────────────────────────────────────────────
@Composable
fun ArenaGameOverDialog(
    result: GameResult,
    wins: Int,
    isPlayerWin: Boolean,
    onNextBattle: () -> Unit,
    onEndArena: () -> Unit
) {
    val (title, sub) = when (result) {
        GameResult.PLAYER_CASTLE_DESTROYED -> "Prohrál jsi"  to "Tvůj hrad byl zničen."
        GameResult.AI_CASTLE_DESTROYED     -> "Vítězství!"   to "Zničil jsi nepřátelský hrad."
        GameResult.PLAYER_CASTLE_BUILT     -> "Vítězství!"   to "Postavil jsi mocný hrad."
        GameResult.AI_CASTLE_BUILT         -> "Prohrál jsi"  to "Nepřítel dokončil svůj hrad."
        GameResult.PLAYER_HP_WINS          -> "Vítězství!"   to "Balíčky došly – tvůj hrad je vyšší."
        GameResult.AI_HP_WINS              -> "Prohrál jsi"  to "Balíčky došly – nepřítel má vyšší hrad."
        GameResult.DRAW                    -> "Remíza"       to "Balíčky došly – hrady jsou stejně vysoké."
    }

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(
                    if (isPlayerWin) listOf(Color(0xFF1A2A1A), BgPanel)
                    else listOf(Color(0xFF2A1010), BgPanel)
                ))
                .border(
                    1.dp,
                    if (isPlayerWin) TealLight.copy(alpha = 0.5f) else Crimson.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(if (isPlayerWin) "⚔️" else "💀", fontSize = 32.sp)
            Text(
                title.uppercase(),
                color = if (isPlayerWin) TealLight else Crimson,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(sub, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)

            // Win counter
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Gold.copy(alpha = 0.08f))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "Vítězství: $wins",
                    color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            if (isPlayerWin) {
                Button(
                    onClick = onNextBattle,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DALŠÍ BITVA", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            } else {
                Button(
                    onClick = onEndArena,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("UKONČIT ARÉNU", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}