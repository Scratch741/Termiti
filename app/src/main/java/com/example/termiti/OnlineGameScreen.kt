package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
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
    // CHAOS důl přidáme jen pokud existuje – výchozí hodnota je 0 (nezobrazovat),
    // ale jakmile ho hráč postaví, server ho posílá a musíme ho mapovat.
    mines["CHAOS"]?.let { if (it > 0) mineMap[ResourceType.CHAOS] = it }
    val blockedMap = mutableMapOf<ResourceType, Int>()
    for ((key, value) in mineBlockedTurns) {
        val rt = runCatching { ResourceType.valueOf(key) }.getOrNull()
        if (rt != null && value > 0) blockedMap[rt] = value
    }
    val pendingList = pendingResources.mapNotNull { p ->
        val rt = runCatching { ResourceType.valueOf(p.type) }.getOrNull() ?: return@mapNotNull null
        PendingResource(rt, p.amount, p.turnsLeft)
    }.toMutableList()

    val handList: MutableList<Card> = when {
        // Server odhalil skutečné karty soupeře (konec hry – review mód)
        hand.isNotEmpty()  -> hand.toMutableList()
        // Soupeřova ruka – skrytá: použijeme dummy karty (jen počet → zobrazí se jako ruby)
        oppHandSize >= 0   -> MutableList(oppHandSize) { dummyCard }
        else               -> hand.toMutableList()
    }

    return PlayerState(
        castleHP         = castleHP,
        wallHP           = wallHP,
        resources        = resMap,
        mines            = mineMap,
        mineBlockedTurns = blockedMap,
        pendingResources = pendingList,
        deck             = MutableList(deckSize) { dummyCard },
        hand             = handList,
        discardPile      = MutableList(discardSize) { dummyCard }
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
    val phase      by vm.phase
    val gameResult by vm.gameResult

    // Review mód: hráč si prohlíží zamrzlou hru po jejím skončení
    var reviewMode by remember { mutableStateOf(false) }
    // Reset review módu při přechodu z GAME_OVER do jiné fáze
    LaunchedEffect(phase) { if (phase != OnlinePhase.GAME_OVER) reviewMode = false }

    // Zvuk + odměna – spustí se JEDNOU při příchodu výsledku, bez ohledu na reviewMode.
    // Záměrně hoisted sem (mimo OnlineGameOverOverlay), aby se nepouštělo znovu
    // pokaždé, když overlay vstoupí/opustí kompozici při přepínání review módu.
    LaunchedEffect(gameResult) {
        val r = gameResult ?: return@LaunchedEffect
        when {
            r.winner == "DRAW" || r.winner == "DRAW_BOTH_DEAD" -> Unit
            r.youWin -> SoundManager.playWin()
            else     -> SoundManager.playLose()
        }
        PlayerProfileManager.recordGameResult(win = r.youWin, online = true)
    }

    val opponentDisconnected    by vm.opponentDisconnected
    val opponentDisconnectSec   by vm.opponentDisconnectSec
    val isReconnecting          by vm.isReconnecting
    val onlinePendingDecision   by vm.onlinePendingDecision
    val onlineDecisionSecondsLeft by vm.onlineDecisionSecondsLeft

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
                OnlineGameplay(
                    vm           = vm,
                    onBack       = onBack,
                    reviewMode   = reviewMode,
                    onShowResult = { reviewMode = false }
                )
                if (!reviewMode) {
                    OnlineGameOverOverlay(
                        vm       = vm,
                        onBack   = onBack,
                        onReview = { reviewMode = true }
                    )
                }
            }
            else -> {
                // Zpět do lobby přes onBack (nemělo by nastat)
            }
        }

        // ── Overlay: Rozhodnutí ───────────────────────────────────────────────
        onlinePendingDecision?.let { decision ->
            DecisionOverlay(
                decision         = decision,
                secondsLeft      = onlineDecisionSecondsLeft,
                onChoice         = { vm.resolveOnlineDecision(it) },
                onResourceChoice = { type, amount -> vm.resolveOnlineResourceDecision(type, amount) }
            )
        }

        // ── Overlay: soupeř se odpojil, čekáme na reconnect ──────────────────
        if (opponentDisconnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📡", fontSize = 48.sp)
                    Text(
                        "Soupeř se odpojil",
                        color = OgTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Čekám na reconnect…",
                        color = OgTextMuted,
                        fontSize = 14.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(OgCrimson.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${opponentDisconnectSec}s",
                            color = OgCrimson,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // ── Overlay: vlastní auto-reconnect ──────────────────────────────────
        if (isReconnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔄", fontSize = 48.sp)
                    Text(
                        "Ztraceno připojení",
                        color = OgTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Připojuji se zpět…",
                        color = OgTextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ─── Herní plátno ─────────────────────────────────────────────────────────────

@Composable
private fun OnlineGameplay(
    vm: OnlineLobbyViewModel,
    onBack: () -> Unit,
    reviewMode: Boolean = false,
    onShowResult: () -> Unit = {}
) {
    val gs             by vm.gameState
    val matchInfo      by vm.matchInfo
    val lastCard       by vm.lastPlayedCard
    val lastCardByMe   by vm.lastPlayedByMe
    val lastCardAction by vm.lastPlayedAction
    val myPs  = gs.myState.toPlayerState()
    val oppPs = gs.oppState.toPlayerState(oppHandSize = gs.oppState.handSize)

    var showLog      by remember { mutableStateOf(false) }
    var showMenu     by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showOppHand by remember { mutableStateOf(false) }
    var cardPreview by remember { mutableStateOf<Card?>(null) }
    val playerPassives = remember {
        PlayerProfileManager.profile?.activeAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()
    }
    val opponentPassives = remember(matchInfo?.opponentAbilities) {
        matchInfo?.opponentAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()
    }
    val gameLog     by vm.gameLog
    val phase       by vm.phase
    val isGameOver  = phase == OnlinePhase.GAME_OVER
    // Resetuj pohled na soupeřovu ruku při zavření review módu
    LaunchedEffect(reviewMode) { if (!reviewMode) showOppHand = false }

    val opponentName = matchInfo?.opponentName ?: "Soupeř"

    // ── Timer výpočet ─────────────────────────────────────────────────────────
    // Server posílá relativní časy (zbývající ms v čase odeslání).
    // Klient ukládá receivedAt a odpočítává od přijetí – bez závislosti
    // na synchronizaci hodin mezi zařízeními.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(gs.receivedAt, isGameOver) {
        if (isGameOver) return@LaunchedEffect   // hra skončila → zastav tikání
        while (true) { delay(500L); nowMs = System.currentTimeMillis() }
    }

    // Kolik ms uplynulo od přijetí zprávy
    val elapsedAfterReceiveMs = (nowMs - gs.receivedAt).coerceAtLeast(0L)

    // Zbývající čas tahu a timebanku pro aktivního hráče
    val turnLeftMs  = (gs.turnRemainingMs  - elapsedAfterReceiveMs).coerceAtLeast(0L)
    val myBankLeftMs  = (gs.timebankMeMs  - (if (gs.isMyTurn  && turnLeftMs == 0L) elapsedAfterReceiveMs - gs.turnRemainingMs else 0L)).coerceAtLeast(0L)
    val oppBankLeftMs = (gs.timebankOppMs - (if (!gs.isMyTurn && turnLeftMs == 0L) elapsedAfterReceiveMs - gs.turnRemainingMs else 0L)).coerceAtLeast(0L)

    fun timerText(isMe: Boolean): String {
        return if (gs.isMyTurn == isMe) {
            // Aktivní hráč – odpočítávej
            if (turnLeftMs > 0L) "${(turnLeftMs / 1000L)}s"
            else {
                val bankLeft = if (isMe) myBankLeftMs else oppBankLeftMs
                "⏳${(bankLeft / 1000L)}s"
            }
        } else {
            // Mimo tah – zobraz zbývající timebank staticky
            val bankMs = if (isMe) gs.timebankMeMs else gs.timebankOppMs
            "📦${(bankMs / 1000L)}s"
        }
    }

    fun timerColor(isMe: Boolean): Color {
        if (gs.isMyTurn != isMe) return OgTextMuted
        val frac = if (turnLeftMs > 0L) {
            turnLeftMs.toFloat() / gs.turnRemainingMs.coerceAtLeast(1L)
        } else {
            val bankLeft = if (isMe) myBankLeftMs else oppBankLeftMs
            val bankTotal = if (isMe) gs.timebankMeMs else gs.timebankOppMs
            bankLeft.toFloat() / bankTotal.coerceAtLeast(1L)
        }
        return when {
            frac > 0.5f -> Color(0xFF4CAF50)
            frac > 0.2f -> Color(0xFFFFB300)
            else        -> Color(0xFFE53935)
        }
    }

    // ── Zvuk: zahraná/zahozená karta ─────────────────────────────────────────
    LaunchedEffect(lastCard?.id, lastCardByMe, lastCardAction) {
        val c = lastCard ?: return@LaunchedEffect
        when (lastCardAction) {
            CardAction.PLAYED    -> playSoundForCardGlobal(c)
            CardAction.DISCARDED -> SoundManager.playDiscard()
            CardAction.BURNED    -> SoundManager.playAttack()   // soupeř spálil naši kartu
            CardAction.STOLEN    -> SoundManager.playAttack()
            null                 -> Unit
        }
    }

    // ── Zvuk: začátek mého tahu (= soupeř právě skončil) ─────────────────────
    val prevIsMyTurn = remember { mutableStateOf(gs.isMyTurn) }
    LaunchedEffect(gs.isMyTurn) {
        if (gs.isMyTurn && !prevIsMyTurn.value) SoundManager.playCardDraw()
        prevIsMyTurn.value = gs.isMyTurn
    }

    // ── Flight overlay state (animace "karta letí z ruky do discardu") ──────
    val flight = remember { FlightOverlayState() }
    LaunchedEffect(lastCard?.id, lastCardByMe) {
        val c = lastCard ?: return@LaunchedEffect
        if (!lastCardByMe) return@LaunchedEffect
        val from = flight.sources[c.id]
        val to   = flight.target
        if (from == null || to == null) {
            flight.landedPlayerCardId = c.id
            return@LaunchedEffect
        }
        flight.flying = FlightJob(c, from, to, flight.nextId())
    }

    CompositionLocalProvider(LocalFlightOverlay provides flight) {
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
                playerDeckSize   = myPs.deck.size,
                aiDeckSize       = oppPs.deck.size,
                isPlayerTurn     = gs.isMyTurn,
                isComboTurn      = false,
                currentTurn      = gs.turnNumber,
                playerLabel      = PlayerProfileManager.profile?.name   ?: "Hráč",
                playerAvatar     = PlayerProfileManager.profile?.avatar ?: "player_icon_1",
                playerLevel      = PlayerProfileManager.profile?.level  ?: -1,
                opponentLabel    = opponentName,
                opponentAvatar   = matchInfo?.opponentAvatar ?: "👺",
                opponentLevel    = matchInfo?.opponentLevel  ?: -1,
                onMenu           = { if (reviewMode) onShowResult() else showMenu = true },
                playerTimerText  = timerText(isMe = true),
                playerTimerColor = timerColor(isMe = true),
                oppTimerText     = timerText(isMe = false),
                oppTimerColor    = timerColor(isMe = false),
                playerPassives   = playerPassives,
                aiPassives       = opponentPassives
            )

            // ── Separátor – dolní okraj top baru ─────────────────────────────
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth().zIndex(1f),
                contentScale       = ContentScale.FillWidth
            )

            // ── Hlavní řada: zdroje + bojiště ────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                // ── Levý panel: moje zdroje ──────────────────────────────────
                NewResourcePanel(
                    playerState = myPs,
                    isAi        = false,
                    modifier    = Modifier.fillMaxHeight().width(135.dp),
                    bottomSlot  = {
                        NewPanelButton(
                            label   = "📜 Log",
                            color   = OgGold,
                            active  = true,
                            onClick = { SoundManager.playMenuTap(); showLog = !showLog }
                        )
                    }
                )

                // ── Bojiště ───────────────────────────────────────────────────
                NewBattlefield(
                    playerState           = myPs,
                    aiState               = oppPs,
                    lastCard              = lastCard,
                    lastCardAction        = lastCardAction,
                    lastCardIsPlayer      = lastCardByMe,
                    modifier              = Modifier.fillMaxHeight().weight(1f),
                    revealedAiCard        = if (!lastCardByMe) lastCard else null,
                    revealedAiCardIdx     = if (!lastCardByMe) gs.oppState.lastPlayedIdx else null,
                    playerWinTarget       = gs.myWinTarget,
                    aiWinTarget           = gs.oppWinTarget,
                    playerMaxHand         = gs.myState.maxHandSize,
                    opponentCardBackResId = cardBackSkinDrawable(
                        matchInfo?.opponentCardBackSkin ?: "card_back_frame"
                    ),
                    playerCastleResId     = castleSkinDrawable(
                        PlayerProfileManager.profile?.castleSkin ?: "castle_player"
                    ),
                    opponentCastleResId   = castleSkinDrawable(
                        matchInfo?.opponentCastleSkin ?: "castle_player"
                    )
                )

                // ── Pravý panel: zdroje soupeře ───────────────────────────────
                NewResourcePanel(
                    playerState = oppPs,
                    isAi        = true,
                    modifier    = Modifier.fillMaxHeight().width(135.dp),
                    bottomSlot  = {
                        if (isGameOver) {
                            // Review mód: toggle soupeřovy ruky
                            NewPanelButton(
                                label   = if (showOppHand) "🃏 ${LocalStrings.current.viewMyHand}" else "👁 ${LocalStrings.current.viewOpponentHand}",
                                color   = if (showOppHand) OgTealLight else OgGold,
                                active  = true,
                                onClick = { SoundManager.playMenuTap(); showOppHand = !showOppHand }
                            )
                        } else {
                            val s = LocalStrings.current
                            val btnColor = if (gs.isMyTurn) OgTealLight
                                           else OgTextMuted.copy(alpha = 0.35f)
                            NewPanelButton(
                                label   = if (gs.isMyTurn) "⏩ ${s.endTurn}" else "⏳ ${s.waitingTurn}",
                                color   = btnColor,
                                active  = gs.isMyTurn,
                                onClick = if (gs.isMyTurn) {
                                    {
                                        SoundManager.playMenuTap()
                                        if (gs.myState.deckSize == 0 && gs.oppState.deckSize == 0)
                                            vm.skipTurn()
                                        else
                                            vm.endTurn()
                                    }
                                } else null
                            )
                        }
                    }
                )
            }

            // ── Separátor – horní okraj prostoru karet ───────────────────────
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth().zIndex(1f),
                contentScale       = ContentScale.FillWidth
            )

            // ── Ruka ──────────────────────────────────────────────────────────
            // V review módu: zobraz soupeřovu ruku (skryté karty – dummy); jinak moji
            val displayHand = if (isGameOver && showOppHand) oppPs.hand else myPs.hand
            HandPanel(
                hand             = displayHand,
                isPlayerTurn     = gs.isMyTurn && !isGameOver,
                isComboTurn      = false,
                playerResources  = myPs.resources,
                onPlayCard       = { card -> vm.playCard(card.id) },
                onDiscardCard    = { card -> vm.discardCard(card.id) },
                onWait           = { /* noop */ },
                onEndTurn        = { vm.endTurn() },
                showHeader       = false,
                playerWallHp     = myPs.wallHP,
                playerCastleHp   = myPs.castleHP,
                oppResources     = oppPs.resources,
                onLongPressCard  = { card -> if (card.id != "__dummy__") cardPreview = card },
                // Pevná výška ruky – zabrání posunu lišty, když je ruka prázdná
                modifier         = Modifier.fillMaxWidth().height(152.dp)
                                           .paint(
                                               painterResource(R.drawable.hand_background),
                                               contentScale = ContentScale.Crop
                                           )
            )
        }

        // ── Game-end delay (1s): blokuje veškerý vstup hráče ─────────────────
        val gameEndPending by vm.gameEndPending
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
            CardFullPreviewOverlay(card = card, onDismiss = { cardPreview = null })
        }

        // Letící karta (hráčova) – nad vším ostatním v Boxu
        FlightOverlayBox(flight)
    }

    // ── Menu dialog ───────────────────────────────────────────────────────────
    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .paint(painterResource(R.drawable.mulligan_background), contentScale = ContentScale.Crop)
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Menu", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Vzdát se? Prohra bude zaznamenána a soupeř bude prohlášen vítězem.",
                    color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                MenuButton(
                    label    = LocalStrings.current.settings,
                    imageRes = R.drawable.button_5,
                    accent   = TextPrimary,
                    onClick  = { SoundManager.playMenuTap(); showMenu = false; showSettings = true }
                )
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { SoundManager.playMenuTap(); showMenu = false }) {
                        Text("Zůstat", color = TealLight, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { SoundManager.playMenuTap(); showMenu = false; vm.forfeit() }) {
                        Text("Vzdát se", color = Crimson, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ── Log overlay ───────────────────────────────────────────────────────────
    if (showLog) {
        LogOverlay(log = gameLog, onDismiss = { showLog = false })
    }

    // ── Nastavení overlay ─────────────────────────────────────────────────────
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    }
    } // CompositionLocalProvider
}

// ─── Mulligan vrstva ──────────────────────────────────────────────────────────

@Composable
private fun OnlineMulliganLayer(vm: OnlineLobbyViewModel) {
    val hand         by vm.mulliganHand
    val selected     by vm.mulliganSelected
    val submitted    by vm.mulliganSubmitted
    val oppDone      by vm.opponentMulliganDone
    val matchInfo    by vm.matchInfo
    val secondsLeft  by vm.mulliganSecondsLeft

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
        secondsLeft = secondsLeft,
        onToggle    = { if (!submitted) { SoundManager.playDeckSelect(); vm.toggleMulligan(it) } },
        onConfirm   = { SoundManager.playMenuTap(); vm.confirmMulligan() },
        onSkip      = { SoundManager.playMenuTap(); vm.skipMulligan() }
    )
}

// ─── Game Over overlay ────────────────────────────────────────────────────────

@Composable
private fun OnlineGameOverOverlay(
    vm: OnlineLobbyViewModel,
    onBack: () -> Unit,
    onReview: () -> Unit = {}
) {
    val result       by vm.gameResult
    val ratingChange by vm.ratingChange
    val newRating    by vm.newRating

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1320), Color(0xFF0D0A0E))),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (emoji, headline, subline) = when {
                result == null            -> Triple("⏳", "Konec hry", "")
                result!!.winner == "DRAW_BOTH_DEAD" -> Triple("💥", "Remíza!", "Oba hrady byly zničeny současně")
                result!!.winner == "DRAW" -> Triple("🤝", "Remíza!", "Obě strany mají stejný hrad")
                result!!.youWin           -> Triple("🏆", "Vítězství!", "Porazil jsi ${vm.matchInfo.value?.opponentName ?: "soupeře"}")
                else                      -> Triple("💀", "Prohra", "${result!!.winnerName ?: "Soupeř"} zvítězil")
            }

            Text(emoji, fontSize = 40.sp)
            Text(
                text       = headline,
                color      = OgGold,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            if (subline.isNotEmpty()) {
                Text(
                    text      = subline,
                    color     = OgTextMuted,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Rating změna
            if (ratingChange != null && newRating != null) {
                val deltaStr = if (ratingChange!! >= 0) "+$ratingChange" else "$ratingChange"
                val deltaColor = when {
                    ratingChange!! > 0 -> Color(0xFF4CAF50)
                    ratingChange!! < 0 -> Color(0xFFCF4A4A)
                    else               -> Color(0xFF7A6E5F)
                }
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    verticalAlignment     = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "⭐ Rating: $newRating",
                        color    = OgGold,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        "($deltaStr)",
                        color    = deltaColor,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = { SoundManager.playMenuTap(); vm.returnToLobby() },
                colors   = ButtonDefaults.buttonColors(containerColor = OgGold),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Zpět do lobby", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick  = { SoundManager.playMenuTap(); onReview() },
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2A35)),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("📋 ${LocalStrings.current.inspectGame}", color = OgTextPrimary, fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = { SoundManager.playMenuTap(); vm.disconnect(); onBack() }) {
                Text("Odejít", color = OgTextMuted, fontSize = 12.sp)
            }
        }
    }
}
