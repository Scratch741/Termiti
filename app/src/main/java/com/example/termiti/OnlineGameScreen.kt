package com.example.termiti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
private val OgChaosOrange = Color(0xFFE67E22)

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
        // Mulligan overlay musí PŘEŽÍT přechod do GAME_PLAYING. Server pošle
        // GAME_STATE hned, jak potvrdí oba hráči – kdo potvrdí jako druhý, tomu
        // by se overlay zabil uprostřed lízání a karty by se do ruky nikdy
        // nepředaly (žádný plynulý přechod). Overlay se proto zavře až sám,
        // po doběhnutí animace (onFinished).
        var mulliganVisible by remember { mutableStateOf(false) }
        LaunchedEffect(phase) {
            if (phase == OnlinePhase.GAME_MULLIGAN) mulliganVisible = true
        }
        // Pojistka pro případ, že by animace uvázla – overlay nesmí zůstat viset
        // přes rozehranou hru. Musí být s rezervou DELŠÍ než celé dorozdání:
        // DRAW_STAGGER_MS (500) × (karet−1) + DRAW_FLIGHT_MS (450) + handoff (460),
        // tj. u plné ruky ~3,9 s. Kratší timeout by animaci sám uřízl.
        LaunchedEffect(phase, mulliganVisible) {
            if (mulliganVisible && phase != OnlinePhase.GAME_MULLIGAN) {
                delay(6_000L)
                mulliganVisible = false
            }
        }

        when (phase) {
            OnlinePhase.GAME_MULLIGAN, OnlinePhase.GAME_PLAYING -> {
                OnlineGameplay(vm, onBack)
                if (mulliganVisible) {
                    OnlineMulliganLayer(
                        vm           = vm,
                        // Server rozjel hru → dorozdej a předej karty do ruky
                        startHandoff = phase != OnlinePhase.GAME_MULLIGAN,
                        onFinished   = { mulliganVisible = false }
                    )
                }
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

        // ── VS intro (jako Hearthstone): pár vteřin před mulliganem ──────────
        // Zobrazí se jen při vstupu do hry přes mulligan (ne při reconnectu
        // do rozehrané hry). Kreslí se NAD mulligan overlayem.
        var showVsIntro by remember { mutableStateOf(phase == OnlinePhase.GAME_MULLIGAN) }
        if (showVsIntro && phase == OnlinePhase.GAME_MULLIGAN) {
            OnlineVersusIntro(vm, onDone = { showVsIntro = false })
        }
    }
}

// ─── VS intro: Hráč vs Soupeř tabulka před mulliganem ────────────────────────

@Composable
private fun OnlineVersusIntro(vm: OnlineLobbyViewModel, onDone: () -> Unit) {
    val match        by vm.matchInfo
    val myRating     by vm.myRating
    val oppRating    by vm.opponentRating
    val playerName   by vm.playerName
    val allModeStats by vm.allModeStats
    val profile      = PlayerProfileManager.profile

    val mode      = match?.mode ?: "normal"
    val modeLabel = if (mode == "super_random") "SUPER NÁHODNÝ" else "RYCHLÝ ZÁPAS"
    // Skóre v daném módu: preferuj data z MATCH_FOUND, fallback na WELCOME statistiky
    val myStats   = match?.myStats ?: allModeStats[mode]
    val oppStats  = match?.opponentStats

    val enter = remember { Animatable(0f) }   // slide-in stran + pop "VS"
    val exit  = remember { Animatable(0f) }   // závěrečný fade-out
    LaunchedEffect(Unit) {
        // Celkem 5 s: 450 ms slide-in + 4150 ms hold + 400 ms fade-out
        enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(4150L)
        exit.animateTo(1f, tween(400))
        onDone()
    }
    val density = LocalDensity.current
    val slidePx = with(density) { 220.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color(0xF20D0A14))
            .pointerInput(Unit) { detectTapGestures {} },   // blokuje vstup pod introm
        contentAlignment = Alignment.Center
    ) {
        // Mód – badge nahoře
        Text(
            modeLabel,
            color         = OgGold,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier      = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp)
                .graphicsLayer { alpha = enter.value }
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            // Já – přilétá zleva
            Box(Modifier.graphicsLayer { translationX = -(1f - enter.value) * slidePx }) {
                VsSideColumn(
                    name      = playerName.ifBlank { profile?.name ?: "Hráč" },
                    avatar    = profile?.avatar ?: "player_icon_1",
                    level     = profile?.level ?: -1,
                    rating    = myRating,
                    stats     = myStats,
                    abilities = profile?.activeAbilities
                        ?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList(),
                    accent    = OgTealLight
                )
            }
            // VS – pop uprostřed
            Text(
                "VS",
                color         = OgGold,
                fontSize      = 34.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier      = Modifier.graphicsLayer {
                    scaleX = 0.4f + 0.6f * enter.value
                    scaleY = 0.4f + 0.6f * enter.value
                    alpha  = enter.value
                }
            )
            // Soupeř – přilétá zprava
            Box(Modifier.graphicsLayer { translationX = (1f - enter.value) * slidePx }) {
                VsSideColumn(
                    name      = match?.opponentName ?: "Soupeř",
                    avatar    = match?.opponentAvatar ?: "enemy_icon_1",
                    level     = match?.opponentLevel ?: -1,
                    rating    = oppRating,
                    stats     = oppStats,
                    abilities = match?.opponentAbilities
                        ?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList(),
                    accent    = OgCrimson
                )
            }
        }
        // Kdo začíná – dole
        Text(
            if (match?.side == "A") "⚔ Ty začínáš první" else "⏳ Soupeř začíná první",
            color      = if (match?.side == "A") OgTealLight else OgTextMuted,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .graphicsLayer { alpha = enter.value }
        )
    }
}

@Composable
private fun VsSideColumn(
    name: String,
    avatar: String,
    level: Int,
    rating: Int?,
    stats: OnlineModeStats?,
    abilities: List<PassiveAbility>,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier            = Modifier.width(190.dp)
    ) {
        AvatarDisplay(avatar = avatar, sizeDp = 64f)
        Text(name, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (level >= 0) Text("Level $level", color = OgTextMuted, fontSize = 10.sp)
        if (rating != null) {
            Text("🏆 $rating", color = OgGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        // Skóre v daném módu: výhry / prohry (+ winrate od 5 her)
        if (stats != null && stats.games > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${stats.wins}V", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("·", color = OgTextMuted, fontSize = 11.sp)
                Text("${stats.losses}P", color = OgCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (stats.games >= 5) {
                    Text("(${stats.winRate} %)", color = OgTextMuted, fontSize = 10.sp)
                }
            }
        }
        // Pasivní schopnosti s popisky
        if (abilities.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            abilities.take(3).forEach { ab ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Image(
                        painterResource(ab.iconRes),
                        contentDescription = ab.title,
                        modifier = Modifier.size(15.dp)
                    )
                    Column {
                        Text(ab.title, color = OgTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            ab.description,
                            color      = OgTextMuted,
                            fontSize   = 8.sp,
                            lineHeight = 9.5.sp,
                            maxLines   = 2,
                            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
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
    // Server nechává tah aktivní (isMyTurn zůstane true po MÉM zahrání) jen pro
    // combo karty / NextCardIsCombo boost - nekombo karta vždy rovnou ukončí tah
    // (viz GameSession.js _advanceTurn). Takže pokud jsem po svém zahrání pořád
    // na tahu, jde vždy o combo řetěz – ekvivalent offline isPlayerComboTurn.
    val isComboTurn = gs.isMyTurn && lastCardByMe

    var showLog        by remember { mutableStateOf(false) }
    var showLostCards  by remember { mutableStateOf(false) }
    var showMenu       by remember { mutableStateOf(false) }
    var showSettings   by remember { mutableStateOf(false) }
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
    val gameLog        by vm.gameLog
    val lostToOpponent by vm.lostToOpponent
    val oppHandLoss    by vm.opponentHandLoss
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
            if (turnLeftMs > 0L) "${(turnLeftMs / 1000L)}s"
            else {
                val bankLeft = if (isMe) myBankLeftMs else oppBankLeftMs
                "${(bankLeft / 1000L)}s"
            }
        } else {
            val bankMs = if (isMe) gs.timebankMeMs else gs.timebankOppMs
            "${(bankMs / 1000L)}s"
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

    // Zvuk začátku tahu: dřívější playCardDraw() při přepnutí tahu nahradila
    // animace příletu líznuté karty (HandPanel), která hraje zvuk per karta.

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

    // ── Ghost efekt: moje karty spálené/ukradené soupeřem se rozplynou v ruce ──
    // (pozice z flight.sources – jen karty, které byly vidět v ruce, ne z balíčku)
    // Zpracovávají se VŠECHNY nové záznamy najednou – více ztrát v jednom framu
    // (Spálená knihovna = 2 spálení) by jinak ukázalo jen poslední ghost.
    var lossSeenCount by remember { mutableStateOf(lostToOpponent.size) }
    LaunchedEffect(lostToOpponent) {
        if (lostToOpponent.size < lossSeenCount) {
            // Nová hra vyprázdnila seznam → resetuj počítadlo
            lossSeenCount = lostToOpponent.size
            return@LaunchedEffect
        }
        val newEntries = lostToOpponent.take(lostToOpponent.size - lossSeenCount)
        lossSeenCount = lostToOpponent.size
        // Nejstarší první, ať ghosty naskočí ve stejném pořadí jako ztráty
        for (entry in newEntries.asReversed()) {
            if (entry.action == CardAction.BURNED || entry.action == CardAction.STOLEN) {
                flight.sources[entry.card.id]?.let { rect ->
                    flight.spawnLoss(entry.card, null, entry.action, rect)
                }
            }
        }
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

            // ── Separátor – horní okraj obrazovky (oddělení od letterbox pruhu) ──
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth().zIndex(1f),
                contentScale       = ContentScale.FillWidth
            )

            // ── Top bar ───────────────────────────────────────────────────────
            NewTopBar(
                playerDeckSize   = myPs.deck.size,
                aiDeckSize       = oppPs.deck.size,
                isPlayerTurn     = gs.isMyTurn,
                isComboTurn      = isComboTurn,
                currentTurn      = gs.turnNumber,
                playerLabel      = PlayerProfileManager.profile?.name   ?: "Hráč",
                playerAvatar     = PlayerProfileManager.profile?.avatar ?: "player_icon_1",
                playerLevel      = PlayerProfileManager.profile?.level  ?: -1,
                opponentLabel    = opponentName,
                opponentAvatar   = matchInfo?.opponentAvatar ?: "enemy_icon_1",
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
                            label   = "Log",
                            color   = OgGold,
                            active  = true,
                            onClick = { SoundManager.playMenuTap(); showLog = !showLog }
                        )
                    }
                )

                // ── Bojiště ───────────────────────────────────────────────────
                NewBattlefield(
                    animateDraws          = !isGameOver,
                    playerState           = myPs,
                    aiState               = oppPs,
                    lastCard              = lastCard,
                    lastCardAction        = lastCardAction,
                    lastCardIsPlayer      = lastCardByMe,
                    modifier              = Modifier.fillMaxHeight().weight(1f),
                    revealedAiCard        = if (!lastCardByMe) lastCard else null,
                    revealedAiCardIdx     = if (!lastCardByMe) gs.oppState.lastPlayedIdx else null,
                    oppLossQueue          = oppHandLoss,
                    playerWinTarget       = gs.myWinTarget,
                    aiWinTarget           = gs.oppWinTarget,
                    playerMaxHand         = gs.myState.maxHandSize,
                    aiMaxHand             = gs.oppState.maxHandSize,
                    opponentCardBackResId = cardBackSkinDrawable(
                        matchInfo?.opponentCardBackSkin ?: "card_back_frame"
                    ),
                    playerCastleResId     = castleSkinDrawable(
                        PlayerProfileManager.profile?.castleSkin ?: "castle_player"
                    ),
                    opponentCastleResId   = castleSkinDrawable(
                        matchInfo?.opponentCastleSkin ?: "castle_player"
                    ),
                    playerWallResId       = wallSkinDrawable(
                        PlayerProfileManager.profile?.wallSkin ?: "wall_player"
                    ),
                    opponentWallResId     = wallSkinDrawable(
                        matchInfo?.opponentWallSkin ?: "wall_player"
                    ),
                    backgroundResId       = vm.battleBackgroundResId.value
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
                                label   = if (showOppHand) LocalStrings.current.viewMyHand else LocalStrings.current.viewOpponentHand,
                                color   = if (showOppHand) OgTealLight else OgGold,
                                active  = true,
                                onClick = { SoundManager.playMenuTap(); showOppHand = !showOppHand }
                            )
                        } else {
                            val s = LocalStrings.current
                            val isGameEnding = gs.isMyTurn
                                && gs.myState.deckSize == 0 && gs.oppState.deckSize == 0
                            val btnColor = when {
                                isGameEnding -> OgChaosOrange
                                gs.isMyTurn  -> OgTealLight
                                else         -> OgTextMuted.copy(alpha = 0.35f)
                            }
                            val btnGlow = when {
                                !gs.isMyTurn -> null
                                isGameEnding -> Crimson
                                else         -> HpGreen
                            }
                            NewPanelButton(
                                label     = when {
                                    isGameEnding -> s.endGame
                                    isComboTurn  -> s.endCombo
                                    gs.isMyTurn  -> s.endTurn
                                    else         -> s.waitingTurn
                                },
                                color     = btnColor,
                                active    = gs.isMyTurn,
                                glowColor = btnGlow,
                                onClick   = if (gs.isMyTurn) {
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
            val viewingOppHand = isGameOver && showOppHand
            val displayHand = if (viewingOppHand) oppPs.hand else myPs.hand
            // Review soupeřovy ruky: podmínky (✓/✗), dostupnost i X-náhled se musí
            // vyhodnocovat z pohledu VLASTNÍKA ruky (soupeře), ne hráče
            val handOwner = if (viewingOppHand) oppPs else myPs
            val handOpp   = if (viewingOppHand) myPs else oppPs
            HandPanel(
                hand             = displayHand,
                isPlayerTurn     = gs.isMyTurn && !isGameOver,
                isComboTurn      = isComboTurn && !isGameOver,
                playerResources  = handOwner.resources,
                onPlayCard       = { card -> vm.playCard(card.id) },
                onDiscardCard    = { card -> vm.discardCard(card.id) },
                onWait           = { /* noop */ },
                onEndTurn        = { vm.endTurn() },
                showHeader       = false,
                playerWallHp     = handOwner.wallHP,
                playerCastleHp   = handOwner.castleHP,
                oppResources     = handOpp.resources,
                onLongPressCard  = { card -> if (card.id != "__dummy__") cardPreview = card },
                animateDraws     = !isGameOver,   // review mód: přepínání rukou bez příletu
                canDiscard       = !gs.myState.discardUsed,   // zahození jen 1× za kolo (server autoritativní)
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
            CardFullPreviewOverlay(
                card      = card,
                onDismiss = { cardPreview = null },
                xPreview  = if (card.isXCost) (myPs.resources[card.costType] ?: 0) else null
            )
        }

        // Letící karta (hráčova) – nad vším ostatním v Boxu
        FlightOverlayBox(flight)
    }

    // ── Menu dialog ───────────────────────────────────────────────────────────
    if (showMenu) {
        GameDialog(onDismissRequest = { showMenu = false }) {
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
                    PlainButton(
                        text      = "Zůstat",
                        textColor = TealLight,
                        fontSize  = 13.sp,
                        paddingH  = 20.dp,
                        paddingV  = 8.dp,
                        onClick   = { SoundManager.playMenuTap(); showMenu = false }
                    )
                    PlainButton(
                        text      = "Vzdát se",
                        textColor = Crimson,
                        fontSize  = 13.sp,
                        paddingH  = 20.dp,
                        paddingV  = 8.dp,
                        onClick   = { SoundManager.playMenuTap(); showMenu = false; vm.forfeit() }
                    )
                }
            }
        }
    }

    // ── Log overlay ───────────────────────────────────────────────────────────
    if (showLog) {
        LogOverlay(
            log             = gameLog,
            onDismiss       = { showLog = false },
            lostCards       = lostToOpponent,
            onShowLostCards = { showLostCards = true }
        )
    }

    // ── Spálené & ukradené karty ──────────────────────────────────────────────
    if (showLostCards) {
        LostCardsOverlay(
            lostCards = lostToOpponent,
            onDismiss = { showLostCards = false }
        )
    }

    // ── Nastavení overlay ─────────────────────────────────────────────────────
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    }
    } // CompositionLocalProvider
}

// ─── Mulligan vrstva ──────────────────────────────────────────────────────────

@Composable
private fun OnlineMulliganLayer(
    vm: OnlineLobbyViewModel,
    startHandoff: Boolean = false,
    onFinished: () -> Unit = {}
) {
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

    // Po odeslání běží stejný odpočet dál – oba klienti ho startují ze stejného
    // serverového deadlinu, takže zbývající sekundy = čas, který má soupeř na
    // rozhodnutí. Jakmile soupeř potvrdí, čeká se už jen na GAME_STATE → skryj.
    val waitingSecondsLeft = secondsLeft.takeIf { submitted && !oppDone }

    MulliganOverlay(
        hand        = hand,
        selectedIds = selected,
        submitted   = submitted,
        goesFirst   = goesFirst,
        secondsLeft = secondsLeft,
        waitingSecondsLeft = waitingSecondsLeft,
        onToggle    = { if (!submitted) { SoundManager.playDeckSelect(); vm.toggleMulligan(it) } },
        onConfirm   = { SoundManager.playMenuTap(); vm.confirmMulligan() },
        onSkip      = { SoundManager.playMenuTap(); vm.skipMulligan() },
        onFinished  = onFinished,
        startHandoff = startHandoff
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
    val wasSuperRandom = vm.isSuperRandom.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .paint(painterResource(R.drawable.mulligan_background), contentScale = ContentScale.Crop)
                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val (iconRes, headline, subline) = when {
                result == null            -> Triple(R.drawable.clock_icon, "Konec hry", "")
                result!!.winner == "DRAW_BOTH_DEAD" -> Triple(R.drawable.skull_icon, "Remíza!", "Oba hrady byly zničeny současně")
                result!!.winner == "DRAW" -> Triple(R.drawable.shield_icon, "Remíza!", "Obě strany mají stejný hrad")
                result!!.youWin           -> Triple(R.drawable.trophy_icon, "Vítězství!", "Porazil jsi ${vm.matchInfo.value?.opponentName ?: "soupeře"}")
                else                      -> Triple(R.drawable.skull_icon, "Prohra", "${result!!.winnerName ?: "Soupeř"} zvítězil")
            }

            // Ikona + nadpis na jednom řádku → ušetří výšku
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment     = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Image(painterResource(iconRes), contentDescription = null, modifier = androidx.compose.ui.Modifier.size(30.dp))
                Text(
                    text       = headline,
                    color      = OgGold,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            }
            if (subline.isNotEmpty()) {
                Text(
                    text      = subline,
                    color     = OgTextMuted,
                    fontSize  = 12.sp,
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
                    Image(painterResource(R.drawable.star_icon), contentDescription = null, modifier = androidx.compose.ui.Modifier.size(14.dp))
                    Text(
                        "Rating: $newRating",
                        color    = OgGold,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        "($deltaStr)",
                        color    = deltaColor,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            MenuButton(
                label    = "Hrát znovu",
                imageRes = R.drawable.button_1,
                accent   = TealLight,
                onClick  = {
                    SoundManager.playMenuTap()
                    vm.returnToLobby()
                    vm.joinQueue(superRandom = wasSuperRandom)
                }
            )

            MenuButton(
                label    = "Zpět do lobby",
                imageRes = R.drawable.button_3,
                accent   = OgGold,
                onClick  = { SoundManager.playMenuTap(); vm.returnToLobby() }
            )

            MenuButton(
                label    = LocalStrings.current.inspectGame,
                imageRes = R.drawable.button_9,
                accent   = OgTextMuted,
                onClick  = { SoundManager.playMenuTap(); onReview() }
            )

            PlainButton(
                text      = "Odejít",
                textColor = OgTextMuted,
                fontSize  = 12.sp,
                paddingH  = 20.dp,
                paddingV  = 8.dp,
                onClick   = { SoundManager.playMenuTap(); vm.disconnect(); onBack() }
            )
        }
    }
}
