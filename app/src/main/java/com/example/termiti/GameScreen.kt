package com.example.termiti

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.style.DrawStyle
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

// ─── Art helpers jsou v ArtDefaults.kt (artModifier / artAlignment) ──────────

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
    is CardEffect.AddResourceDelayed -> "⏳"
    is CardEffect.BlockMine         -> "🚫"
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
    val lostToOpponent   by viewModel.lostToOpponent
    val isMulligan       by viewModel.isMulligan
    val mulliganSelected by viewModel.mulliganSelected
    val isComboTurn      by viewModel.isPlayerComboTurn

    var showMenuConfirm  by remember { mutableStateOf(false) }
    var showLostCards    by remember { mutableStateOf(false) }
    var showLog          by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Pozadí ───────────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0x88000000)))

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            NewTopBar(
                playerDeckSize = state.playerState.deck.size,
                aiDeckSize     = state.aiState.deck.size,
                isPlayerTurn   = state.activePlayer == ActivePlayer.PLAYER,
                isComboTurn    = isComboTurn,
                currentTurn    = state.currentTurn,
                arenaWins      = if (isArena) arenaWins else -1,
                onMenu         = { showMenuConfirm = true }
            )

            // ── Hlavní řada ───────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                val isPlayerActive = state.activePlayer == ActivePlayer.PLAYER && gameOver == null
                val active         = isPlayerActive || isComboTurn

                // ── Levý panel: zdroje hráče ──────────────────────────────────
                NewResourcePanel(
                    playerState = state.playerState,
                    isAi        = false,
                    modifier    = Modifier.fillMaxHeight().width(112.dp),
                    bottomSlot  = {
                        NewPanelButton(
                            label   = "📜 Log",
                            color   = Gold,
                            active  = true,
                            onClick = { showLog = true }
                        )
                        if (lostToOpponent.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            NewPanelButton(
                                label   = "🃏 ${lostToOpponent.size}",
                                color   = Color(0xFF9B59B6),
                                active  = true,
                                onClick = { showLostCards = true }
                            )
                        }
                    }
                )

                // ── Střed: bojiště ────────────────────────────────────────────
                NewBattlefield(
                    playerState      = state.playerState,
                    aiState          = state.aiState,
                    lastCard         = lastCard,
                    lastCardAction   = lastCardAction,
                    lastCardIsPlayer = lastCardIsPlayer,
                    modifier         = Modifier.fillMaxHeight().weight(1f)
                )

                // ── Pravý panel: zdroje AI ────────────────────────────────────
                NewResourcePanel(
                    playerState = state.aiState,
                    isAi        = true,
                    modifier    = Modifier.fillMaxHeight().width(112.dp),
                    bottomSlot  = {
                        val btnLabel = if (isComboTurn) "⚡ Konec combo" else "⏩ Konec tahu"
                        val btnColor = when {
                            isComboTurn -> Gold
                            active      -> TealLight
                            else        -> TextMuted.copy(alpha = 0.35f)
                        }
                        NewPanelButton(
                            label   = btnLabel,
                            color   = btnColor,
                            active  = active,
                            onClick = if (active) {
                                { if (isComboTurn) viewModel.endPlayerTurn() else viewModel.waitTurn() }
                            } else null
                        )
                    }
                )
            }

            // ── Ruka hráče – přes celou šířku dole ───────────────────
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF6B3D12)))
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
                modifier         = Modifier.fillMaxWidth().height(152.dp)
                                           .background(Color(0xD8120A03))
            )
        }

        // ── Dialogy a overlay ─────────────────────────────────────────────────
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

        if (showLog) {
            LogOverlay(log = log, onDismiss = { showLog = false })
        }
    }
}

// ─── New Top Bar ──────────────────────────────────────────────────────────────

@Composable
fun NewTopBar(
    playerDeckSize: Int,
    aiDeckSize: Int,
    isPlayerTurn: Boolean,
    isComboTurn: Boolean,
    currentTurn: Int,
    arenaWins: Int = -1,
    opponentLabel: String = "Nepřítel",
    onMenu: () -> Unit,
    playerTimerText: String? = null,
    playerTimerColor: Color = Color(0xFF4CAF50),
    oppTimerText: String? = null,
    oppTimerColor: Color = Color(0xFF4CAF50)
) {
    val activeTurn = isPlayerTurn || isComboTurn
    val dotColor = if (activeTurn) TealLight else Crimson
    val turnText = when {
        isComboTurn   -> "⚡ COMBO"
        isPlayerTurn  -> "TVŮJ TAH"
        else          -> "$opponentLabel HRAJE"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF3D200A), Color(0xFF2A1505))))
            .drawBehind {
                drawRect(
                    Color(0xFF6B3D12),
                    topLeft = Offset(0f, size.height - 2.dp.toPx()),
                    size    = Size(size.width, 2.dp.toPx())
                )
            }
            .padding(horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Hráč vlevo ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .clickable { onMenu() }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) { Text("☰", color = TextMuted, fontSize = 11.sp) }

            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A2010), Color(0xFF5C3010))))
                    .border(1.5.dp, Gold.copy(alpha = 0.65f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Text("🧙", fontSize = 13.sp) }

            Text("Hráč", color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("🂠", fontSize = 11.sp)
                Text("$playerDeckSize", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (playerTimerText != null) {
                Text(playerTimerText, color = playerTimerColor,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Střed ─────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Gold.copy(alpha = 0.10f))
                    .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text("Kolo $currentTurn", color = Gold, fontSize = 10.sp, letterSpacing = 1.sp)
            }

            if (arenaWins >= 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Gold.copy(alpha = 0.07f))
                        .border(1.dp, Gold.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text("⚔️ $arenaWins", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.size(7.dp)) {
                    drawCircle(dotColor.copy(alpha = 0.30f), radius = size.width)
                    drawCircle(dotColor, radius = size.width * 0.55f)
                }
                Text(turnText, color = dotColor, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // ── AI vpravo ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (oppTimerText != null) {
                Text(oppTimerText, color = oppTimerColor,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("$aiDeckSize", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("🂠", fontSize = 11.sp)
            }

            Text(opponentLabel, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A0A0A), Color(0xFF5C1010))))
                    .border(1.5.dp, Crimson.copy(alpha = 0.65f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Text("👺", fontSize = 13.sp) }
        }
    }
}

// ─── New Resource Panel ───────────────────────────────────────────────────────

@Composable
fun NewResourcePanel(
    playerState: PlayerState,
    isAi: Boolean,
    modifier: Modifier = Modifier,
    bottomSlot: @Composable ColumnScope.() -> Unit = {}
) {
    val magic     = playerState.resources[ResourceType.MAGIC]  ?: 0
    val attack    = playerState.resources[ResourceType.ATTACK] ?: 0
    val stones    = playerState.resources[ResourceType.STONES] ?: 0
    val chaos     = playerState.resources[ResourceType.CHAOS]  ?: 0
    val mineMagic = playerState.mines[ResourceType.MAGIC]  ?: 0
    val mineAtk   = playerState.mines[ResourceType.ATTACK] ?: 0
    val mineSto   = playerState.mines[ResourceType.STONES] ?: 0
    val mineChaos = playerState.mines[ResourceType.CHAOS]  ?: 0
    val blkMagic  = playerState.mineBlockedTurns[ResourceType.MAGIC]  ?: 0
    val blkAtk    = playerState.mineBlockedTurns[ResourceType.ATTACK] ?: 0
    val blkSto    = playerState.mineBlockedTurns[ResourceType.STONES] ?: 0
    val blkChaos  = playerState.mineBlockedTurns[ResourceType.CHAOS]  ?: 0

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2E1A08), Color(0xFF231408), Color(0xFF2E1A08))
                )
            )
            .drawBehind {
                val w = 3.dp.toPx()
                val x = if (isAi) w / 2f else size.width - w / 2f
                drawLine(Color(0xFF6B3D12), Offset(x, 0f), Offset(x, size.height), w)
            }
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        NewResourceSection("✨", "Magie",  mineMagic, magic,  MagicPurple, isAi = isAi, blockedTurns = blkMagic)
        NewResourceSection("⚔️", "Útok",   mineAtk,   attack, AttackRed,   isAi = isAi, blockedTurns = blkAtk)
        NewResourceSection("🪨", "Kameny", mineSto,   stones, StoneColor,  isAi = isAi, blockedTurns = blkSto)
        NewResourceSection("🌀", "Chaos",  mineChaos, chaos,  ChaosOrange, isAi = isAi, blockedTurns = blkChaos, isLast = true)
        Spacer(Modifier.weight(1f))
        bottomSlot()
    }
}

@Composable
fun NewResourceSection(
    icon: String,
    name: String,
    mine: Int,
    amount: Int,
    color: Color,
    isAi: Boolean = false,
    isLast: Boolean = false,
    blockedTurns: Int = 0
) {
    val blocked = blockedTurns > 0
    val mineColor = if (blocked) Color(0xFFE53935) else Gold

    // Pomocný slot pro počet dolů + indikátor blokace (vždy jeden řádek)
    @Composable
    fun MineSlot(align: Alignment.Horizontal) {
        val text  = if (blocked) "⛔$blockedTurns" else if (mine > 0) "$mine" else "—"
        val size  = if (blocked) 9.sp else 11.sp
        Text(
            text,
            color      = mineColor,
            fontSize   = size,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.widthIn(min = 14.dp),
            textAlign  = if (align == Alignment.End) TextAlign.End else TextAlign.Start
        )
    }

    // ── Jedna kompaktní řádka: [mine#] [icon] [name] ... [amount] ──────────
    // Pro AI zrcadlově: [amount] ... [name] [icon] [mine#]
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (!isLast) Modifier.drawBehind {
                    val y = size.height - 0.5f
                    drawLine(Gold.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 0.5f)
                } else Modifier
            )
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isAi) {
            // Hráč: mine# | ikona | název (roztažený) | zásoba
            MineSlot(Alignment.Start)
            Spacer(Modifier.width(2.dp))
            Text(icon, fontSize = 9.sp, lineHeight = 10.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                name, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("$amount", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else {
            // AI – zrcadlo: zásoba | název (roztažený) | ikona | mine#
            Text("$amount", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                name, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(3.dp))
            Text(icon, fontSize = 9.sp, lineHeight = 10.sp)
            Spacer(Modifier.width(2.dp))
            MineSlot(Alignment.End)
        }
    }
}

@Composable
fun NewPanelButton(
    label: String,
    color: Color,
    active: Boolean,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = if (active) 0.12f else 0.05f))
            .border(1.dp, color.copy(alpha = if (active) 0.50f else 0.18f), RoundedCornerShape(5.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color         = color.copy(alpha = if (active) 1f else 0.38f),
            fontSize      = 9.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            textAlign     = TextAlign.Center
        )
    }
}

// ─── New Battlefield ──────────────────────────────────────────────────────────

@Composable
fun NewBattlefield(
    playerState: PlayerState,
    aiState: PlayerState,
    lastCard: Card?,
    lastCardAction: CardAction?,
    lastCardIsPlayer: Boolean,
    modifier: Modifier = Modifier,
    revealedAiCard: Card? = null,     // karta zahrána soupeřem
    revealedAiCardIdx: Int? = null    // původní index v ruce (před zahráním)
) {
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF0A1A0A), Color(0xFF151A08), Color(0xFF1A1008)))
        )
    ) {
        // Přirozená velikost karty
        val cardNatH = 140.dp
        val cardNatW = 100.dp
        // Dostupný prostor: pod AI stripem (46dp fixní), malý dolní dech (8dp).
        // Hrady jsou v rozích → blokují jen strany, ne střed bojiště.
        val cardAvailH = maxHeight - 46.dp - 8.dp
        val cardAvailW = maxWidth  - 24.dp      // 12dp margin na každé straně
        val cardScaleH = (cardAvailH / cardNatH).coerceIn(0.4f, 1.35f)
        val cardScaleW = (cardAvailW / cardNatW).coerceIn(0.4f, 1.35f)
        val cardScale  = minOf(cardScaleH, cardScaleW)
        val scaledH    = cardNatH * cardScale
        val scaledW    = cardNatW * cardScale

        // ── AI ruka (nahoře) – fixní výška, zahraná karta na správné pozici ──
        val aiStripH = 46.dp
        // Celkový počet slotů: zbývající ruka + 1 zahraná karta (pokud víme index)
        val showReveal = revealedAiCard != null && revealedAiCardIdx != null
        val totalSlots = if (showReveal) aiState.hand.size + 1 else aiState.hand.size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(aiStripH)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))
                )
                .padding(horizontal = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            for (slot in 0 until totalSlots) {
                if (slot > 0) Spacer(Modifier.width(4.dp))
                if (showReveal && slot == revealedAiCardIdx) {
                    // Odhalená zahraná karta – na své původní pozici v ruce
                    PlayedCardSlot(revealedAiCard!!)
                } else {
                    CardBack()
                }
            }
        }

        // ── Hrad hráče – vlevo dole (věž vlevo, hradba vpravo → blíže středu) ──
        NewCastleStructure(
            castleHp = playerState.castleHP,
            wallHp   = playerState.wallHP,
            isPlayer = true,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 4.dp)
        )

        // ── Hrad AI – vpravo dole (hradba vlevo → blíže středu, věž vpravo) ────
        NewCastleStructure(
            castleHp = aiState.castleHP,
            wallHp   = aiState.wallHP,
            isPlayer = false,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 4.dp)
        )

        // ── Poslední zahraná karta – vycentrovaná ve volné ploše pod AI stripem ──
        val cardTopY = aiStripH + (maxHeight - aiStripH - scaledH) / 2
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cardTopY),
            contentAlignment = Alignment.Center
        ) {
            if (lastCard != null) {
                val ringColor = when (lastCardAction) {
                    CardAction.PLAYED    -> if (lastCardIsPlayer) TealLight else Crimson
                    CardAction.DISCARDED -> if (lastCardIsPlayer) Teal.copy(alpha = 0.55f) else Crimson.copy(alpha = 0.55f)
                    CardAction.BURNED    -> Color(0xFFE07B39)
                    CardAction.STOLEN    -> Color(0xFF9B59B6)
                    null                 -> Gold.copy(alpha = 0.40f)
                }
                // Outer box určuje fyzické místo (scaled) — karta se škáluje ze středu
                Box(
                    Modifier
                        .size(scaledW, scaledH)
                        .clip(RoundedCornerShape(7.dp))
                        .border(2.dp, ringColor, RoundedCornerShape(7.dp))
                ) {
                    // requiredSize = přirozená velikost; graphicsLayer škáluje ze středu
                    Box(
                        Modifier
                            .requiredSize(cardNatW, cardNatH)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                    ) {
                        CardView(card = lastCard, canPlay = false, discardMode = false, onClick = {})
                        // Overlay: ikona akce přesně uprostřed karty
                        val overlayIcon = when (lastCardAction) {
                            CardAction.DISCARDED -> "✕"
                            CardAction.BURNED    -> "🔥"
                            else                 -> null
                        }
                        if (overlayIcon != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text      = overlayIcon,
                                    fontSize  = 38.sp,
                                    color     = if (lastCardAction == CardAction.DISCARDED) Color(0xFFE53935) else Color.Unspecified,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .size(scaledW, scaledH)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Gold.copy(alpha = 0.03f))
                        .border(1.dp, Gold.copy(alpha = 0.10f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", color = TextMuted.copy(alpha = 0.18f), fontSize = (16f * cardScale).sp)
                }
            }
        }
    }
}

// ─── Castle Structure ─────────────────────────────────────────────────────────

@Composable
private fun NewCastleStructure(
    castleHp: Int,
    wallHp: Int,
    isPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isPlayer) Teal    else Crimson
    val accentLight = if (isPlayer) TealLight else Color(0xFFFF7070)

    val wallFrac by animateFloatAsState(
        targetValue   = (wallHp / 50f).coerceIn(0f, 1f),
        animationSpec = tween(400),
        label         = "wall_frac"
    )
    val wallBlocks = (10f * wallFrac).roundToInt().coerceIn(0, 10)

    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (isPlayer) {
            CastleTowerBlock(castleHp, accentColor, accentLight, isPlayer = true)
            WallBlock(wallHp, wallBlocks, accentColor)
        } else {
            WallBlock(wallHp, wallBlocks, accentColor)
            CastleTowerBlock(castleHp, accentColor, accentLight, isPlayer = false)
        }
    }
}

@Composable
private fun CastleTowerBlock(
    castleHp: Int,
    accentColor: Color,
    accentLight: Color,
    isPlayer: Boolean
) {
    val castleFullH = 165.dp
    val castleFullW = 110.dp
    val hpFrac = (castleHp / 60f).coerceIn(0f, 1f)

    val offsetY by animateDpAsState(
        targetValue   = castleFullH * (1f - hpFrac),
        animationSpec = tween(600, easing = EaseOutCubic),
        label         = "castle_emerge"
    )

    // Clip box = pevné okno, HP badge přes hrad
    Box(
        modifier = Modifier
            .size(castleFullW, castleFullH)
            .clip(androidx.compose.ui.graphics.RectangleShape)
    ) {
        Image(
            painter            = painterResource(R.drawable.castle_player),
            contentDescription = if (isPlayer) "Hráčův hrad" else "Soupeřův hrad",
            modifier           = Modifier
                .size(castleFullW, castleFullH)
                .offset(y = offsetY)
                .graphicsLayer { scaleX = if (isPlayer) 1f else -1f },
            contentScale       = ContentScale.Fit
        )
        // HP badge přes hrad – vlevo dole (hráč) nebo vpravo dole (soupeř)
        Box(
            modifier = Modifier
                .align(if (isPlayer) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .border(0.5.dp, accentLight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                "🏰 $castleHp",
                color      = accentLight,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WallBlock(wallHp: Int, blockCount: Int, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val visibleBlocks = if (wallHp > 0) blockCount.coerceAtLeast(1) else 0
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(visibleBlocks) {
                Box(
                    Modifier
                        .size(width = 22.dp, height = 7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor.copy(alpha = 0.50f), accentColor.copy(alpha = 0.72f))
                            )
                        )
                        .border(0.5.dp, Color.Black.copy(alpha = 0.40f), RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("🧱 $wallHp", color = accentColor.copy(alpha = 0.70f),
            fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Log Overlay ──────────────────────────────────────────────────────────────

@Composable
fun LogOverlay(log: List<String>, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false, onClick = {})
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1520), BgPanel)))
                .border(1.dp, Gold.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(18.dp)
                .widthIn(max = 340.dp)
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LOG", color = Gold, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            HorizontalDivider(color = Gold.copy(alpha = 0.20f))
            LogPanel(log = log, modifier = Modifier.weight(1f).fillMaxWidth(), scrollable = true)
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            ) {
                Text("Zavřít", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(16.dp)
                    .clipToBounds()
            ) {
                Image(
                    painter = painterResource(card.artResId),
                    contentDescription = null,
                    modifier = artModifier(card),
                    contentScale = ContentScale.Crop,
                    alignment = artAlignment(card)
                )
            }
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

/** Slot zahrané karty soupeře v pruhu ruky – čitelný název + cena, červený rámeček. */
@Composable
private fun PlayedCardSlot(card: Card) {
    val costColor = resourceColor(card.costType)
    Column(
        modifier = Modifier
            .size(width = 38.dp, height = 38.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF2A0A0A))
            .border(1.5.dp, Crimson, RoundedCornerShape(5.dp))
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            effectIcon(card),
            fontSize = 12.sp
        )
        Text(
            card.name,
            color      = TextPrimary,
            fontSize   = 6.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            lineHeight = 7.sp
        )
        Text(
            "${resourceIcon(card.costType)}${card.cost}",
            color    = costColor,
            fontSize = 6.sp
        )
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
                Text(card.name,
                    color = if (canPlay || discardMode || isDragging) TextPrimary else TextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                Text(card.description,
                    color = TextMuted, fontSize = 8.sp, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val rc = rarityColor(card.rarity)
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(rc.copy(alpha = if (canPlay || discardMode) 0.8f else 0.3f)))
                }
            }
            // Ikony stavu vpravo nahoře – nepřekrývají text karty
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (conditionMet != null) {
                    val condColor = if (conditionMet) Color(0xFF4DB86E) else Color(0xFF888888)
                    val condIcon  = if (conditionMet) "✓" else "✗"
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.60f))
                            .border(1.dp, condColor.copy(alpha = 0.75f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            condIcon,
                            color = condColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 10.sp
                        )
                    }
                }
                if (isComboCard) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.60f))
                            .border(1.dp, ComboYellow.copy(alpha = if (canPlay) 0.75f else 0.35f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⚡",
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 10.sp
                        )
                    }
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
        // Vrstva 1: ilustrace karty
        // Oblast ilustrace je horních ~90 dp — pokrývá průhlednou zónu frame i gradient přechod.
        // 70 dp by způsobilo tvrdou hranu uprostřed semi-transparentní části rámu.
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
                alignment = artAlignment(card),
                alpha = if (canPlay || discardMode) 1f else 0.6f
            )
        }

        // Vrstva 2: rám karty (průhlednost v oblasti ilustrace zajistí soubor card_frame.png)
        if (frameResId != 0) {
            Image(
                painter = painterResource(frameResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Vrstva 2.5: Překryv rarity (PNG s průhledností, mění barvu jen určité části)
        val rarityOverlayId = rarityOverlayResource(card.rarity)
        if (rarityOverlayId != 0) {
            Image(
                painter = painterResource(rarityOverlayId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Vrstva 3: cena karty v levém horním kruhu rámu
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 2.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            // Černý obrys
            Text(
                "${card.cost}",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    drawStyle = DrawStyle.Stroke(width = 3f, join = StrokeJoin.Round),
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
            // Bílá výplň
            Text(
                "${card.cost}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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

        // Vrstva 5: text karty pod názvem (90–112 dp od vrchu, max 3 řádky)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 90.dp)
                .fillMaxWidth()
                .height(22.dp)
                .clipToBounds()
                .padding(horizontal = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                card.description,
                color = Color(0xFFDDD0B0),
                fontSize = 6.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 8.5.sp
            )
        }

        // Vrstva 5b: ikony stavu (splněno / combo) – vpravo nahoře, nepřekrývají text ani grafiku
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (conditionMet != null) {
                val condColor = if (conditionMet) Color(0xFF4DB86E) else Color(0xFF888888)
                val condIcon  = if (conditionMet) "✓" else "✗"
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, condColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        condIcon,
                        color = condColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 9.sp
                    )
                }
            }
            if (isComboCard) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, ComboYellow.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⚡",
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 9.sp
                    )
                }
            }
        }

        // Vrstva 6: typ karty v úplně dolním pruhu (127–139 dp od vrchu)
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
                    card.type.uppercase(),
                    color = Color(0xFFD4B870),
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
    onSkip: () -> Unit,
    submitted: Boolean = false,
    goesFirst: Boolean? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE5000000))
            // Blokuje dotyky pod overlayem na pozadí (ne na kartách/tlačítkách).
            // detectTapGestures čeká jen na NEkonsumovaný DOWN — pokud karta DOWN
            // pohltí dřív (child = Main pass dříve), outer Box čeká dál a gesto
            // karty neruší. Pokud DOWN nespolykl nikdo (pozadí), outer Box ho
            // pohltí a game UI pod ním nic nedostane.
            .pointerInput(Unit) { detectTapGestures {} },
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

            // Badge: kdo jde první (jen pokud je znám)
            if (goesFirst != null) {
                Text(
                    if (goesFirst) "⚔️ Ty začínáš první" else "⏳ Soupeř začíná první",
                    color = if (goesFirst) Teal else TextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }

            // Podtitulek / čekání po odeslání
            if (submitted) {
                Text(
                    "Čekám na soupeře…",
                    color = Teal, fontSize = 10.sp, textAlign = TextAlign.Center
                )
            } else {
                Text(
                    if (selectedIds.isEmpty())
                        "Klikni na karty, které chceš vyměnit za náhodné z balíku"
                    else
                        "Vyměníš ${selectedIds.size} ${if (selectedIds.size == 1) "kartu" else "karty"} — klikni znovu pro zrušení výběru",
                    color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center
                )
            }

            // Karty
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hand.forEach { card ->
                    val isSelected = card.id in selectedIds
                    Box(
                        modifier = Modifier.let {
                            if (!submitted) it.clickable { onToggle(card.id) } else it
                        }
                    ) {
                        CardView(
                            card        = card,
                            canPlay     = !isSelected && !submitted,
                            discardMode = isSelected,
                            onClick     = { if (!submitted) onToggle(card.id) }
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
                        // Ztmavení karet po odeslání
                        if (submitted) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
            }

            // Tlačítka — skrytá po odeslání
            if (!submitted) {
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