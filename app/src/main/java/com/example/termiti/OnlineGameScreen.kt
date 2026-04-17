package com.example.termiti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─── Palette (mirrors GameScreen private palette) ────────────────────────────
private val OgBgDeep      = Color(0xFF0D0A0E)
private val OgGold        = Color(0xFFD4A843)
private val OgTextPrimary = Color(0xFFEDE0C4)
private val OgTextMuted   = Color(0xFF7A6E5F)
private val OgTealLight   = Color(0xFF3DBFAD)
private val OgCrimson     = Color(0xFFBF2D2D)

// ─── Converter: OnlinePlayerState → PlayerState ───────────────────────────────

/**
 * Konvertuje OnlinePlayerState (String klíče) na PlayerState (ResourceType klíče)
 * tak, aby bylo možné znovupoužít existující composables (NewBattlefield, NewResourcePanel, …).
 *
 * @param oppHandSize  Pokud je >= 0, plní hand N prázdnými karty (zobrazení rubů soupeře).
 */
private fun OnlinePlayerState.toPlayerState(oppHandSize: Int = -1): PlayerState {
    val resMap = mutableMapOf(
        ResourceType.MAGIC  to (resources["MAGIC"]  ?: 0),
        ResourceType.ATTACK to (resources["ATTACK"] ?: 0),
        ResourceType.STONES to (resources["STONES"] ?: 0),
        ResourceType.CHAOS  to (resources["CHAOS"]  ?: 0)
    )
    val mineMap = mutableMapOf(
        ResourceType.MAGIC  to (mines["MAGIC"]  ?: 0),
        ResourceType.ATTACK to (mines["ATTACK"] ?: 0),
        ResourceType.STONES to (mines["STONES"] ?: 0)
    )

    val handList: MutableList<Card> = if (oppHandSize >= 0) {
        // Soupeřova ruka – skrytá: použijeme dummy karty (jen počet → zobrazí se jako ruby)
        MutableList(oppHandSize) { dummyCard }
    } else {
        hand.toMutableList()
    }

    return PlayerState(
        castleHP    = castleHP,
        wallHP      = wallHP,
        resources   = resMap,
        mines       = mineMap,
        deck        = MutableList(deckSize) { dummyCard },
        hand        = handList,
        discardPile = MutableList(discardSize) { dummyCard }
    )
}

/** Prázdná karta jako placeholder pro skryté soupeřovy karty. */
private val dummyCard = Card(
    id          = "__dummy__",
    name        = "?",
    description = "",
    cost        = 0,
    costType    = ResourceType.MAGIC,
    effects     = emptyList(),
    rarity      = Rarity.COMMON,
    isCombo     = false,
    artResId    = null
)

// ─── Online herní obrazovka ───────────────────────────────────────────────────

@Composable
fun OnlineGameScreen(
    vm: OnlineLobbyViewModel,
    onBack: () -> Unit
) {
    val phase by vm.phase

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OgBgDeep)
    ) {
        when (phase) {
            OnlinePhase.GAME_MULLIGAN -> {
                // Zobraz prázdnou hru v pozadí + mulligan overlay
                OnlineGameplay(vm, onBack)
                OnlineMulliganLayer(vm)
            }
            OnlinePhase.GAME_PLAYING -> {
                OnlineGameplay(vm, onBack)
            }
            OnlinePhase.GAME_OVER -> {
                OnlineGameplay(vm, onBack)
                OnlineGameOverOverlay(vm, onBack)
            }
            else -> {
                // Zpět do lobby přes onBack (nemělo by nastat)
            }
        }
    }
}

// ─── Herní plátno ─────────────────────────────────────────────────────────────

@Composable
private fun OnlineGameplay(
    vm: OnlineLobbyViewModel,
    onBack: () -> Unit
) {
    val gs             by vm.gameState
    val matchInfo      by vm.matchInfo
    val lastCard       by vm.lastPlayedCard
    val lastCardByMe   by vm.lastPlayedByMe
    val myPs  = gs.myState.toPlayerState()
    val oppPs = gs.oppState.toPlayerState(oppHandSize = gs.oppState.handSize)

    var showLog  by remember { mutableStateOf(false) }
    val gameLog  by vm.gameLog

    val opponentName = matchInfo?.opponentName ?: "Soupeř"

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Pozadí – stejné jako offline hra ─────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0x88000000)))

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            NewTopBar(
                playerDeckSize = myPs.deck.size,
                aiDeckSize     = oppPs.deck.size,
                isPlayerTurn   = gs.isMyTurn,
                isComboTurn    = false,
                currentTurn    = gs.turnNumber,
                opponentLabel  = opponentName,
                onMenu         = { showLog = !showLog }
            )

            // ── Timer bar ─────────────────────────────────────────────────────
            TurnTimerBar(gs)

            // ── Hlavní řada: zdroje + bojiště ────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                // ── Levý panel: moje zdroje ──────────────────────────────────
                NewResourcePanel(
                    playerState = myPs,
                    isAi        = false,
                    modifier    = Modifier.fillMaxHeight().width(112.dp),
                    bottomSlot  = {
                        NewPanelButton(
                            label   = "📜 Log",
                            color   = OgGold,
                            active  = true,
                            onClick = { showLog = !showLog }
                        )
                    }
                )

                // ── Bojiště ───────────────────────────────────────────────────
                NewBattlefield(
                    playerState      = myPs,
                    aiState          = oppPs,
                    lastCard         = lastCard,
                    lastCardAction   = if (lastCard != null) CardAction.PLAYED else null,
                    lastCardIsPlayer = lastCardByMe,
                    modifier         = Modifier.fillMaxHeight().weight(1f),
                    revealedAiCard   = if (!lastCardByMe) lastCard else null
                )

                // ── Pravý panel: zdroje soupeře ───────────────────────────────
                NewResourcePanel(
                    playerState = oppPs,
                    isAi        = true,
                    modifier    = Modifier.fillMaxHeight().width(112.dp),
                    bottomSlot  = {
                        val btnColor = if (gs.isMyTurn) OgTealLight
                                       else OgTextMuted.copy(alpha = 0.35f)
                        NewPanelButton(
                            label   = if (gs.isMyTurn) "⏩ Konec tahu" else "⏳ Čekám…",
                            color   = btnColor,
                            active  = gs.isMyTurn,
                            onClick = if (gs.isMyTurn) {
                                {
                                    if (gs.myState.deckSize == 0 && gs.oppState.deckSize == 0)
                                        vm.skipTurn()
                                    else
                                        vm.endTurn()
                                }
                            } else null
                        )
                    }
                )
            }

            // ── Ruka ──────────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF6B3D12)))
            HandPanel(
                hand             = myPs.hand,
                isPlayerTurn     = gs.isMyTurn,
                isComboTurn      = false,
                playerResources  = myPs.resources,
                onPlayCard       = { card -> vm.playCard(card.id) },
                onDiscardCard    = { card -> vm.discardCard(card.id) },
                onWait           = { /* noop */ },
                onEndTurn        = { vm.endTurn() },
                showHeader       = false,
                playerWallHp     = myPs.wallHP,
                playerCastleHp   = myPs.castleHP
            )
        }
    }

    // ── Log overlay ───────────────────────────────────────────────────────────
    if (showLog) {
        LogOverlay(
            log = gameLog,
            onDismiss = { showLog = false }
        )
    }
}

// ─── Mulligan vrstva ──────────────────────────────────────────────────────────

@Composable
private fun OnlineMulliganLayer(vm: OnlineLobbyViewModel) {
    val hand         by vm.mulliganHand
    val selected     by vm.mulliganSelected
    val submitted    by vm.mulliganSubmitted
    val oppDone      by vm.opponentMulliganDone
    val matchInfo    by vm.matchInfo

    // goesFirst = true pokud jsme strana A (první hráč)
    val goesFirst: Boolean? = when {
        !submitted                              -> null     // ještě jsme neodeslali
        submitted && oppDone                    -> matchInfo?.side == "A"
        else                                    -> null
    }

    MulliganOverlay(
        hand        = hand,
        selectedIds = selected,
        submitted   = submitted,
        goesFirst   = goesFirst,
        onToggle    = { if (!submitted) vm.toggleMulligan(it) },
        onConfirm   = { vm.confirmMulligan() },
        onSkip      = { vm.skipMulligan() }
    )
}

// ─── Turn timer bar ───────────────────────────────────────────────────────────

@Composable
private fun TurnTimerBar(gs: OnlineGameState) {
    // Lokální tickující čas (každou sekundu)
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(gs.turnStartedAt) {
        while (true) {
            delay(500)
            nowMs = System.currentTimeMillis()
        }
    }

    val elapsedSec  = ((nowMs - gs.turnStartedAt) / 1000f).coerceAtLeast(0f)
    val turnSec     = gs.turnSeconds.toFloat()
    val timebankSec = (if (gs.isMyTurn) gs.timebankMe else gs.timebankOpp).toFloat()

    // Kolik zbývá z tahu a timebanku
    val turnRemain = (turnSec - elapsedSec).coerceIn(0f, turnSec)
    val bankUsed   = (elapsedSec - turnSec).coerceIn(0f, timebankSec)
    val bankRemain = (timebankSec - bankUsed).coerceIn(0f, timebankSec)

    val usingBank  = elapsedSec > turnSec
    val totalSec   = turnSec + timebankSec

    // Barva: zelená → žlutá → červená
    val frac = if (usingBank) bankRemain / timebankSec else turnRemain / turnSec
    val barColor = when {
        frac > 0.5f -> Color(0xFF4CAF50)
        frac > 0.2f -> Color(0xFFFFB300)
        else        -> Color(0xFFE53935)
    }

    val displaySec = if (usingBank) bankRemain.toInt() else turnRemain.toInt()
    val label      = if (usingBank) "⏳ banka: ${displaySec}s" else "${displaySec}s"
    val barFrac    = ((turnRemain + bankRemain) / totalSec).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(Color(0xFF0A0A0A)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress bar
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Pozadí
            drawRect(Color(0xFF1A1A1A))
            // Vyplněná část
            drawRect(
                color   = barColor.copy(alpha = 0.85f),
                topLeft = Offset.Zero,
                size    = Size(size.width * barFrac, size.height)
            )
        }
        // Čas
        Text(
            text     = label,
            color    = barColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        // Timebank soupeře (malý)
        val oppBank = if (gs.isMyTurn) gs.timebankOpp else gs.timebankMe
        Text(
            text     = "opp: ${oppBank}s",
            color    = OgTextMuted,
            fontSize = 8.sp,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

// ─── Game Over overlay ────────────────────────────────────────────────────────

@Composable
private fun OnlineGameOverOverlay(
    vm: OnlineLobbyViewModel,
    onBack: () -> Unit
) {
    val result by vm.gameResult

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1320), Color(0xFF0D0A0E))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val (emoji, headline, subline) = when {
                result == null                     -> Triple("⏳", "Konec hry", "")
                result!!.winner == "DRAW"          -> Triple("🤝", "Remíza!", "Obě strany mají stejný hrad")
                result!!.youWin                    -> Triple("🏆", "Vítězství!", "Porazil jsi ${result!!.winnerName ?: "soupeře"}")
                else                               -> Triple("💀", "Prohra", "${result!!.winnerName ?: "Soupeř"} zvítězil")
            }

            Text(emoji, fontSize = 56.sp)
            Text(
                text       = headline,
                color      = OgGold,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            if (subline.isNotEmpty()) {
                Text(
                    text      = subline,
                    color     = OgTextMuted,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onBack,
                colors  = ButtonDefaults.buttonColors(containerColor = OgGold),
                shape   = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Zpět do lobby", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
