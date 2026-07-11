package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Barvy ───────────────────────────────────────────────────────────────────
private val OnTeal   = Color(0xFF3DBFAD)
private val OnGold   = Color(0xFFD4A843)
private val OnRed    = Color(0xFFCF4A4A)
private val OnGreen  = Color(0xFF4CAF50)
private val OnMuted  = Color(0xFF7A6E5F)
private val OnText   = Color(0xFFEDE0C4)
private val OnPurple = Color(0xFFAA44CC)

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun OnlineMpScreen(
    vm: OnlineLobbyViewModel,
    decks: List<Deck> = emptyList(),
    onBack: () -> Unit,
    onLeaderboard: () -> Unit = {}
) {
    val phase by vm.phase

    // Odpojit WS vždy, když OnlineMpScreen opustí composici (navigace do menu,
    // LEADERBOARD, sleep/wake s jiným screenem atd.). Bez toho by ViewModel
    // mohl auto-reconnectnout na server i když uživatel vidí hlavní menu.
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }

    // Auto-connect: použij nick z profilu, přeskoč NAME_INPUT obrazovku
    LaunchedEffect(Unit) {
        if (phase == OnlinePhase.NAME_INPUT) {
            val profileName = PlayerProfileManager.profile?.name
            if (!profileName.isNullOrBlank()) {
                vm.setName(profileName)
                vm.connect()
            }
        }
    }

    // Herní fáze: plná obrazovka bez lobby
    if (phase == OnlinePhase.GAME_MULLIGAN ||
        phase == OnlinePhase.GAME_PLAYING  ||
        phase == OnlinePhase.GAME_OVER) {
        OnlineGameScreen(vm = vm, onBack = { vm.disconnect(); onBack() })
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí: stejné jako hlavní menu ──────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // ── Pochodně (stejná matematika jako PlayMenuScreen) ─────────────────
        val imgAR  = 1791f / 975f
        val dispAR = W.value / H.value.coerceAtLeast(1f)
        val imgDispW: Dp
        val imgDispH: Dp
        val cropX: Dp
        val cropY: Dp
        if (dispAR >= imgAR) {
            imgDispW = W; imgDispH = W / imgAR; cropX = 0.dp; cropY = (imgDispH - H) / 2f
        } else {
            imgDispW = H * imgAR; imgDispH = H; cropX = (imgDispW - W) / 2f; cropY = 0.dp
        }
        val torchSize = H * 0.15f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 1.7f
        )

        // ── Fáze ─────────────────────────────────────────────────────────────
        when (phase) {
            OnlinePhase.NAME_INPUT  ->
                if (!PlayerProfileManager.profile?.name.isNullOrBlank())
                    ConnectingPanel(onCancel = { vm.disconnect(); onBack() })
                else NameInputPanel(vm, onBack)
            OnlinePhase.CONNECTING  -> ConnectingPanel(onCancel = { vm.disconnect(); onBack() })
            OnlinePhase.LOBBY       -> LobbyPanel(vm, decks, onBack, onLeaderboard)
            OnlinePhase.QUEUING     -> QueuingPanel(vm, onBack)
            OnlinePhase.MATCH_FOUND -> MatchFoundPanel(vm)
            OnlinePhase.ERROR       -> ErrorPanel(vm, onBack)
            else                    -> {}
        }
    }
}

// ─── Lobby: 3-sloupcový layout ────────────────────────────────────────────────
@Composable
private fun LobbyPanel(
    vm: OnlineLobbyViewModel,
    decks: List<Deck>,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit = {}
) {
    val name            by vm.playerName
    val onlineCount     by vm.onlineCount
    val queueSize       by vm.queueSize
    val selectedDeckIdx by vm.selectedDeckIndex
    val errorMsg        by vm.errorMsg
    val statusMsg       by vm.statusMsg
    val allModeStats    by vm.allModeStats

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val W = maxWidth
        val H = maxHeight
        val centerW = minOf(W * 0.44f, H * 1.0f)

        Row(
            Modifier.fillMaxSize().padding(vertical = H * 0.015f),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ── Levý sloupec: hráč + statistiky ──────────────────────────────
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Image(
                    painter            = painterResource(R.drawable.bg_side_panels),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop
                )
                // Lehký tmavý overlay pro čitelnost textu
                Box(Modifier.fillMaxSize().background(Color(0x55000000)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Avatar + jméno vedle sebe
                    val profile = PlayerProfileManager.profile
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvatarDisplay(
                            avatar  = profile?.avatar ?: "player_icon_1",
                            sizeDp  = (H.value * 0.065f).coerceIn(26f, 48f)
                        )
                        Text(
                            name,
                            color = OnText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }

                    LobbyDivider()

                    // Statistiky
                    Text(
                        "STATISTIKY",
                        color = OnMuted, fontSize = 7.sp,
                        letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold
                    )
                    ModeStatsBlock(
                        label  = "Constructed",
                        stats  = allModeStats["normal"],
                        accent = OnTeal
                    )
                    ModeStatsBlock(
                        label  = "Super Náhodný",
                        stats  = allModeStats["super_random"],
                        accent = OnPurple
                    )

                    if (errorMsg.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            errorMsg,
                            color = OnRed, fontSize = 9.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }
                }
            }

            // ── Střed: nadpis + akční tlačítka ────────────────────────────────
            Box(
                Modifier.width(centerW).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.width(centerW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.008f)
                ) {
                    // Nadpis
                    Text(
                        "ONLINE",
                        color         = OnTeal,
                        fontSize      = (centerW.value * 0.085f).sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 5.sp
                    )
                    Text(
                        "LOBBY",
                        color         = OnGold,
                        fontSize      = (centerW.value * 0.115f).sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 8.sp
                    )

                    Spacer(Modifier.height(H * 0.018f))

                    // Akční tlačítka – stejný styl jako v hlavním menu
                    MenuButton(
                        label    = "Rychlý zápas",
                        imageRes = R.drawable.button_1,
                        accent   = TealLight,
                        onClick  = { vm.joinQueue(superRandom = false) }
                    )
                    MenuButton(
                        label    = "Super Náhodný",
                        imageRes = R.drawable.button_9,
                        accent   = OnPurple,
                        onClick  = { vm.joinQueue(superRandom = true) }
                    )
                    MenuButton(
                        label    = "Žebříček",
                        imageRes = R.drawable.button_4,
                        accent   = OnGold,
                        onClick  = onLeaderboard
                    )
                    MenuButton(
                        label    = "Odpojit",
                        imageRes = R.drawable.button_6,
                        accent   = OnMuted,
                        onClick  = { vm.disconnect(); onBack() }
                    )
                }
            }

            // ── Pravý sloupec: výběr balíčku ──────────────────────────────────
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Image(
                    painter            = painterResource(R.drawable.bg_side_panels),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize().graphicsLayer { scaleX = -1f },
                    contentScale       = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color(0x55000000)))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Stav připojení
                    if (statusMsg.isNotBlank()) {
                        Text(
                            "• $statusMsg",
                            color = OnTeal.copy(alpha = 0.85f),
                            fontSize = 9.sp
                        )
                    }

                    // Online / fronta
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(onlineCount.toString(), color = OnGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Online", color = OnMuted, fontSize = 8.sp, letterSpacing = 0.5.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(queueSize.toString(), color = OnGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Fronta", color = OnMuted, fontSize = 8.sp, letterSpacing = 0.5.sp)
                        }
                    }

                    LobbyDivider()

                    Text(
                        "BALÍČEK",
                        color = OnMuted, fontSize = 7.sp,
                        letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold
                    )
                    LobbyDivider()

                    // Deck list: Box s weight(1f) → Column uvnitř smí scrollovat
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            decks.forEachIndexed { idx, deck ->
                                DeckChip(
                                    label     = localizedDeckName(deck.name),
                                    selected  = selectedDeckIdx == idx,
                                    valid     = deck.isValid,
                                    cardCount = deck.totalCards,
                                    onClick   = {
                                        if (deck.isValid) {
                                            val ids = deck.cardCounts
                                                .flatMap { (id, count) -> List(count) { id } }
                                            vm.setDeckChoice(idx, ids)
                                        } else {
                                            vm.setDeckChoice(idx, null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Zadání přezdívky ─────────────────────────────────────────────────────────
@Composable
private fun NameInputPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val name  by vm.playerName
    val error by vm.errorMsg

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "ONLINE MULTIPLAYER",
                color         = OnGold,
                fontSize      = 18.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(4.dp))
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.width(260.dp),
                contentScale       = ContentScale.FillWidth
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Připoj se k lobby serveru a najdi soupeře",
                color     = OnMuted,
                fontSize  = 10.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value         = name,
                onValueChange = { vm.setName(it) },
                label         = { Text("Přezdívka", color = OnMuted, fontSize = 11.sp) },
                singleLine    = true,
                modifier      = Modifier.width(260.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.connect() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = OnTeal,
                    unfocusedBorderColor = OnMuted.copy(alpha = 0.4f),
                    focusedTextColor     = OnText,
                    unfocusedTextColor   = OnText,
                    cursorColor          = OnTeal
                )
            )

            if (error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = OnRed, fontSize = 10.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(16.dp))

            OnBtn("Připojit se", OnTeal, Modifier.width(260.dp), enabled = name.isNotBlank()) { vm.connect() }
            Spacer(Modifier.height(8.dp))
            OnBtn("← Zpět", OnMuted, Modifier.width(260.dp)) { onBack() }
        }
    }
}

// ─── Připojování ─────────────────────────────────────────────────────────────
@Composable
private fun ConnectingPanel(onCancel: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = OnTeal, modifier = Modifier.size(52.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Připojuji k serveru…",
                color = OnText, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            if (onCancel != null) {
                Spacer(Modifier.height(20.dp))
                OnBtn("← Zrušit", OnMuted, Modifier.width(200.dp)) { onCancel() }
            }
        }
    }
}

// ─── Ve frontě ───────────────────────────────────────────────────────────────
@Composable
private fun QueuingPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val queueSize     by vm.queueSize
    val isSuperRandom by vm.isSuperRandom

    val accentColor = if (isSuperRandom) OnPurple else OnTeal
    val modeLabel   = if (isSuperRandom) "Super Náhodný" else "Rychlý zápas"

    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            elapsedSec++
        }
    }
    val queueTime = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    BoxWithConstraints(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
    ) {
        val W = maxWidth
        val H = maxHeight
        val centerW = minOf(W * 0.44f, H * 1.0f)

        Row(
            Modifier.fillMaxSize().padding(vertical = H * 0.02f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Levý sloupec: profil — stejně jako hlavní menu ─────────────────
            Box(
                Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val profile = PlayerProfileManager.profile
                if (profile != null) {
                    Column(
                        modifier            = Modifier.offset(x = (-5).dp, y = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(H * 0.025f)
                    ) {
                        ProfileInfo(profile, H)
                    }
                }
            }

            // ── Střed: spinner + text ─────────────────────────────────────────
            Box(
                Modifier.width(centerW).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color       = accentColor,
                        modifier    = Modifier.size(56.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        modeLabel,
                        color = accentColor, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    Image(
                        painter            = painterResource(R.drawable.bg_separator),
                        contentDescription = null,
                        modifier           = Modifier.width(centerW * 0.7f),
                        contentScale       = ContentScale.FillWidth
                    )
                    Text(
                        "Hledám soupeře…",
                        color = OnText, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        queueTime,
                        color = accentColor.copy(alpha = 0.75f),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    if (queueSize > 1) {
                        Text("Ve frontě: $queueSize hráčů", color = OnMuted, fontSize = 11.sp)
                    }
                }
            }

            // ── Pravý sloupec: Zpět — stejná pozice jako v PlayMenuScreen ─────
            Box(
                Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.offset(x = 25.dp, y = 35.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.005f)
                ) {
                    // Neviditelné placeholdery — drží stejnou pozici jako button_7 a button_5
                    Box(Modifier.alpha(0f)) {
                        IconMenuButton(imageRes = R.drawable.button_7, label = "", size = H * 0.12f, onClick = {})
                    }
                    Box(Modifier.alpha(0f)) {
                        IconMenuButton(imageRes = R.drawable.button_5, label = "", size = H * 0.12f, onClick = {})
                    }
                    IconMenuButton(
                        imageRes = R.drawable.button_6,
                        label    = "Zpět",
                        size     = H * 0.12f,
                        onClick  = { vm.leaveQueue() }
                    )
                }
            }
        }
    }
}

// ─── Zápas nalezen ───────────────────────────────────────────────────────────
@Composable
private fun MatchFoundPanel(vm: OnlineLobbyViewModel) {
    val match          by vm.matchInfo
    val myRating       by vm.myRating
    val opponentRating by vm.opponentRating
    val playerName     by vm.playerName

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SOUPEŘ NALEZEN!",
                color         = OnGreen,
                fontSize      = 20.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(4.dp))
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.width(260.dp),
                contentScale       = ContentScale.FillWidth
            )
            Spacer(Modifier.height(12.dp))

            // Rating srovnání
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val profile = PlayerProfileManager.profile
                    AvatarDisplay(avatar = profile?.avatar ?: "player_icon_1", sizeDp = 36f)
                    Spacer(Modifier.height(4.dp))
                    Text(playerName, color = OnTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (myRating != null) Text("$myRating", color = OnGold, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VS", color = OnMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarDisplay(match?.opponentAvatar ?: "enemy_icon_1", sizeDp = 36f)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        match?.opponentName ?: "Soupeř",
                        color = OnRed, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    if (opponentRating != null) Text("$opponentRating", color = OnGold, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (match?.side == "A") "Ty začínáš první" else "Soupeř začíná první",
                color    = if (match?.side == "A") OnTeal else OnMuted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = OnTeal, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.height(6.dp))
            Text("Připravuji hru…", color = OnMuted, fontSize = 10.sp)
        }
    }
}

// ─── Chyba ───────────────────────────────────────────────────────────────────
@Composable
private fun ErrorPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val error by vm.errorMsg

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "CHYBA PŘIPOJENÍ",
                color         = OnRed,
                fontSize      = 18.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.width(240.dp),
                contentScale       = ContentScale.FillWidth
            )
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                color     = OnMuted,
                fontSize  = 11.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 280.dp)
            )
            Spacer(Modifier.height(28.dp))
            OnBtn("Zkusit znovu", OnTeal, Modifier.width(240.dp)) { vm.retryConnect() }
            Spacer(Modifier.height(10.dp))
            OnBtn("← Zpět", OnMuted, Modifier.width(240.dp)) { vm.disconnect(); onBack() }
        }
    }
}

// ─── Sdílené komponenty ───────────────────────────────────────────────────────

/** Horizontální textured separátor uvnitř panelu */
@Composable
private fun LobbyDivider() {
    Image(
        painter            = painterResource(R.drawable.bg_separator),
        contentDescription = null,
        modifier           = Modifier.fillMaxWidth(),
        contentScale       = ContentScale.FillWidth
    )
}


@Composable
private fun ModeStatsBlock(label: String, stats: OnlineModeStats?, accent: Color) {
    Column(
        modifier            = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        val s = stats ?: OnlineModeStats()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rating", color = OnMuted, fontSize = 9.sp)
            Text("${s.rating}", color = OnGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("W / L / D", color = OnMuted, fontSize = 9.sp)
            Text("${s.wins} / ${s.losses} / ${s.draws}", color = OnText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Win%", color = OnMuted, fontSize = 9.sp)
            val wrColor = when {
                s.winRate >= 60 -> OnGreen
                s.winRate >= 45 -> OnGold
                s.games == 0    -> OnMuted
                else            -> OnRed
            }
            Text(
                if (s.games == 0) "–" else "${s.winRate}%  (${s.games} her)",
                color = wrColor, fontSize = 9.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeckChip(
    label     : String,
    selected  : Boolean,
    valid     : Boolean,
    cardCount : Int = 30,
    onClick   : () -> Unit
) {
    val textColor = when {
        selected && valid  -> OnTeal
        selected && !valid -> OnRed
        valid              -> OnMuted
        else               -> OnRed.copy(alpha = 0.6f)
    }
    val countSuffix = if (cardCount != 30) " ($cardCount/30)" else ""
    PlainButton(
        text       = "$label$countSuffix",
        modifier   = Modifier.fillMaxWidth(),
        textColor  = textColor,
        fontSize   = 9.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        selected   = selected,
        paddingH   = 8.dp,
        paddingV   = 4.dp,
        onClick    = onClick
    )
}

@Composable
private fun OnBtn(
    label   : String,
    accent  : Color,
    modifier: Modifier = Modifier,
    enabled : Boolean  = true,
    onClick : () -> Unit
) {
    PlainButton(
        text      = label,
        modifier  = modifier,
        textColor = if (enabled) OnText else OnMuted,
        fontSize  = 13.sp,
        enabled   = enabled,
        paddingH  = 0.dp,
        paddingV  = 8.dp,
        onClick   = onClick
    )
}
