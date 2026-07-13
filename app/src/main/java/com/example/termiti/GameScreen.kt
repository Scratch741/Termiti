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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Paleta barev + DiscardRed / rarityColor / resourceColor / resourceIcon → GameColors.kt

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel(),
    onBackToMenu: () -> Unit = {},
    isArena: Boolean = false,
    arenaWins: Int = 0,
    onArenaWin: () -> Unit = {},
    onArenaLose: () -> Unit = {},
    onGameEnd: ((win: Boolean) -> Unit)? = null,
    randomDeck: Boolean = false,
    superRandom: Boolean = false
) {
    val state            by viewModel.gameState
    val log              by viewModel.log
    val gameOver         by viewModel.gameOver
    val lastCard         by viewModel.lastCard
    val lastCardAction   by viewModel.lastCardAction
    val lastCardIsPlayer by viewModel.lastCardIsPlayer
    val revealedAiCard    by viewModel.revealedAiCard
    val revealedAiCardIdx by viewModel.revealedAiCardIdx
    val lostToOpponent   by viewModel.lostToOpponent
    val isMulligan           by viewModel.isMulligan
    val mulliganSelected     by viewModel.mulliganSelected
    val isComboTurn          by viewModel.isPlayerComboTurn
    val campaignOpponent     by viewModel.activeCampaignOpponent
    val aiPassives           by viewModel.aiPassiveAbilities
    val playerPassives = remember {
        PlayerProfileManager.profile?.activeAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()
    }

    var showMenuConfirm  by remember { mutableStateOf(false) }
    var showLostCards    by remember { mutableStateOf(false) }
    var showLog          by remember { mutableStateOf(false) }
    var showSettings     by remember { mutableStateOf(false) }
    var reviewMode       by remember { mutableStateOf(false) }
    var showOppHand      by remember { mutableStateOf(false) }
    var cardPreview      by remember { mutableStateOf<Card?>(null) }
    // Resetuj pohled na soupeřovu ruku při zavření review módu
    LaunchedEffect(reviewMode) { if (!reviewMode) showOppHand = false }

    // ── Flight overlay: animace "karta letí z ruky hráče do discard slotu" ───
    val flight = remember { FlightOverlayState() }
    // Spusť let, když hráč zahraje kartu (nová lastCard, isPlayer, máme source i target)
    LaunchedEffect(lastCard?.id, lastCardIsPlayer) {
        val c = lastCard ?: return@LaunchedEffect
        if (!lastCardIsPlayer) return@LaunchedEffect
        val from = flight.sources[c.id]
        val to   = flight.target
        if (from == null || to == null) {
            // Let nelze odstartovat → rovnou "dopadni" (ukaž statiku hned)
            flight.landedPlayerCardId = c.id
            return@LaunchedEffect
        }
        flight.flying = FlightJob(c, from, to, flight.nextId())
    }

    CompositionLocalProvider(LocalFlightOverlay provides flight) {
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

            // ── Separátor – horní okraj obrazovky (oddělení od letterbox pruhu) ──
            Image(
                painter = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().zIndex(1f),
                contentScale = ContentScale.FillWidth
            )

            // ── Top bar ───────────────────────────────────────────────────────
            val defaultEnemyAvatar = remember { randomEnemyAvatar() }
            NewTopBar(
                playerDeckSize = state.playerState.deck.size,
                aiDeckSize     = state.aiState.deck.size,
                isPlayerTurn   = state.activePlayer == ActivePlayer.PLAYER,
                isComboTurn    = isComboTurn,
                currentTurn    = state.currentTurn,
                arenaWins      = if (isArena) arenaWins else -1,
                playerLabel    = PlayerProfileManager.profile?.name   ?: LocalStrings.current.logActorPlayer,
                playerAvatar   = PlayerProfileManager.profile?.avatar ?: "player_icon_1",
                playerLevel    = PlayerProfileManager.profile?.level  ?: -1,
                opponentLabel  = campaignOpponent?.name   ?: LocalStrings.current.enemy,
                opponentAvatar = campaignOpponent?.avatar ?: defaultEnemyAvatar,
                onMenu         = { if (reviewMode) reviewMode = false else showMenuConfirm = true },
                playerPassives = playerPassives,
                aiPassives     = if (campaignOpponent == null) aiPassives else emptyList()
            )

            // ── Separátor – dolní okraj top baru ─────────────────────────────
            Image(
                painter = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().zIndex(1f),
                contentScale = ContentScale.FillWidth
            )

            // ── Hlavní řada ───────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                val isPlayerActive = state.activePlayer == ActivePlayer.PLAYER && gameOver == null
                val active         = isPlayerActive || isComboTurn

                // ── Levý panel: zdroje hráče ──────────────────────────────────
                NewResourcePanel(
                    playerState = state.playerState,
                    isAi        = false,
                    modifier    = Modifier.fillMaxHeight().width(135.dp),
                    bottomSlot  = {
                        NewPanelButton(
                            label   = "Log",
                            color   = Gold,
                            active  = true,
                            onClick = { showLog = true }
                        )
                    }
                )

                // ── Střed: bojiště ────────────────────────────────────────────
                NewBattlefield(
                    playerState       = state.playerState,
                    aiState           = state.aiState,
                    lastCard          = lastCard,
                    lastCardAction    = lastCardAction,
                    lastCardIsPlayer  = lastCardIsPlayer,
                    revealedAiCard    = revealedAiCard,
                    revealedAiCardIdx = revealedAiCardIdx,
                    modifier          = Modifier.fillMaxHeight().weight(1f),
                    playerWinTarget   = state.playerWinTarget,
                    aiWinTarget       = state.aiWinTarget,
                    playerMaxHand     = state.playerMaxHand,
                    aiMaxHand         = state.aiMaxHand,
                    playerCastleResId = castleSkinDrawable(
                        PlayerProfileManager.profile?.castleSkin ?: "castle_player"
                    )
                    // opponentCastleResId = default (AI vždy základní hrad)
                )

                // ── Pravý panel: zdroje AI ────────────────────────────────────
                NewResourcePanel(
                    playerState = state.aiState,
                    isAi        = true,
                    modifier    = Modifier.fillMaxHeight().width(135.dp),
                    bottomSlot  = {
                        if (gameOver != null) {
                            // Review mód: toggle soupeřovy ruky
                            NewPanelButton(
                                label   = if (showOppHand) LocalStrings.current.viewMyHand else LocalStrings.current.viewOpponentHand,
                                color   = if (showOppHand) TealLight else Gold,
                                active  = true,
                                onClick = { showOppHand = !showOppHand }
                            )
                        } else {
                            val s = LocalStrings.current
                            val isGameEnding = active
                                && state.playerState.hand.isEmpty() && state.playerState.deck.isEmpty()
                                && state.aiState.deck.isEmpty()
                            val btnLabel = when {
                                isComboTurn  -> s.endCombo
                                isGameEnding -> s.endGame
                                else         -> s.endTurn
                            }
                            val btnColor = when {
                                isComboTurn  -> Gold
                                isGameEnding -> ChaosOrange
                                active       -> TealLight
                                else         -> TextMuted.copy(alpha = 0.35f)
                            }
                            val btnGlow = when {
                                !active      -> null
                                isComboTurn  -> ChaosOrange
                                isGameEnding -> Crimson
                                else         -> HpGreen
                            }
                            NewPanelButton(
                                label     = btnLabel,
                                color     = btnColor,
                                active    = active,
                                glowColor = btnGlow,
                                onClick   = if (active) {
                                    { if (isComboTurn) viewModel.endPlayerTurn() else viewModel.waitTurn() }
                                } else null
                            )
                        }
                    }
                )
            }

            // ── Separátor – horní okraj prostoru karet ───────────────
            Image(
                painter = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().zIndex(1f),
                contentScale = ContentScale.FillWidth
            )

            // ── Ruka hráče – přes celou šířku dole ───────────────────
            val displayHand = if (gameOver != null && showOppHand)
                state.aiState.hand else state.playerState.hand
            HandPanel(
                hand             = displayHand,
                isPlayerTurn     = state.activePlayer == ActivePlayer.PLAYER && gameOver == null,
                isComboTurn      = if (gameOver != null) false else isComboTurn,
                playerResources  = state.playerState.resources,
                onPlayCard       = { viewModel.playCard(it) },
                onDiscardCard    = { viewModel.discardCard(it) },
                onWait           = { viewModel.waitTurn() },
                onEndTurn        = { viewModel.endPlayerTurn() },
                showHeader       = false,
                playerWallHp     = state.playerState.wallHP,
                playerCastleHp   = state.playerState.castleHP,
                oppResources     = state.aiState.resources,
                lastPlayedType   = state.playerState.lastPlayedType,
                onLongPressCard  = { cardPreview = it },
                animateDraws     = gameOver == null,   // review mód: přepínání rukou bez příletu
                canDiscard       = !viewModel.playerDiscardUsed.value,
                modifier         = Modifier.fillMaxWidth().height(152.dp)
                                           .paint(
                                               painterResource(R.drawable.hand_background),
                                               contentScale = ContentScale.Crop
                                           )
            )
        }

        // ── Dialogy a overlay ─────────────────────────────────────────────────
        gameOver?.let { result ->
            val isPlayerWin = result.isPlayerWin()
            LaunchedEffect(result) { onGameEnd?.invoke(isPlayerWin) }
            // V kampani dialog nezobrazujeme – onGameEnd naviguje na CampaignResultScreen
            if (!reviewMode && campaignOpponent == null) {
                if (isArena) {
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
                        onRestart = { viewModel.restartGame(randomDeck = randomDeck, superRandom = superRandom) },
                        onMenu    = { viewModel.restartGame(); onBackToMenu() },
                        onReview  = { reviewMode = true }
                    )
                }
            }
        }

        if (showMenuConfirm) {
            GameDialog(onDismissRequest = { showMenuConfirm = false }) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .paint(painterResource(R.drawable.mulligan_background), contentScale = ContentScale.Crop)
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Opustit hru?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Rozehraná partie bude ztracena. Opravdu chceš odejít do menu?", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    MenuButton(
                        label    = LocalStrings.current.settings,
                        imageRes = R.drawable.button_5,
                        accent   = TextPrimary,
                        onClick  = { showMenuConfirm = false; showSettings = true }
                    )
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlainButton(
                            text      = "Zůstat",
                            textColor = TealLight,
                            fontSize  = 13.sp,
                            paddingH  = 20.dp,
                            paddingV  = 8.dp,
                            onClick   = { showMenuConfirm = false }
                        )
                        PlainButton(
                            text      = "Odejít",
                            textColor = Crimson,
                            fontSize  = 13.sp,
                            paddingH  = 20.dp,
                            paddingV  = 8.dp,
                            onClick   = { viewModel.restartGame(); onBackToMenu() }
                        )
                    }
                }
            }
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

        val pendingDecision  by viewModel.pendingDecision
        val decisionSeconds  by viewModel.decisionSecondsLeft
        pendingDecision?.let { decision ->
            DecisionOverlay(
                decision         = decision,
                secondsLeft      = decisionSeconds,
                onChoice         = { viewModel.resolveDecision(it) },
                onResourceChoice = { type, amount -> viewModel.resolveResourceDecision(type, amount) }
            )
        }

        if (showLostCards) {
            LostCardsOverlay(
                lostCards = lostToOpponent,
                onDismiss = { showLostCards = false },
                onMenu    = { showLostCards = false; showMenuConfirm = true }
            )
        }

        if (showLog) {
            LogOverlay(
                log            = log,
                onDismiss      = { showLog = false },
                lostCards      = lostToOpponent,
                onShowLostCards = { showLostCards = true }
            )
        }

        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        }

        // ── Game-end delay (1s): blokuje veškerý vstup hráče ─────────────────
        val gameEndPending by viewModel.gameEndPending
        if (gameEndPending) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        // Náhled karty (long press) – kreslí se nad hrou, ale pod letem
        cardPreview?.let { card ->
            CardFullPreviewOverlay(
                card      = card,
                onDismiss = { cardPreview = null },
                xPreview  = if (card.isXCost) (state.playerState.resources[card.costType] ?: 0) else null
            )
        }

        // Letící karta – kreslí se jako poslední, aby byla nad vším
        FlightOverlayBox(flight)
    }
    } // CompositionLocalProvider
}
