package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Barvy ───────────────────────────────────────────────────────────────────
private val OnTeal   = Color(0xFF3DBFAD)
private val OnGold   = Color(0xFFD4A843)
private val OnRed    = Color(0xFFCF4A4A)
private val OnGreen  = Color(0xFF4CAF50)
private val OnMuted  = Color(0xFF7A6E5F)
private val OnText   = Color(0xFFEDE0C4)
private val OnBgDeep = Color(0xFF0A0D14)
private val OnPanel  = Color(0xFF111520)

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun OnlineMpScreen(
    vm: OnlineLobbyViewModel,
    decks: List<Deck> = emptyList(),
    onBack: () -> Unit,
    onLeaderboard: () -> Unit = {}
) {
    val phase by vm.phase

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

    // Herní fáze: plná obrazovka bez lobby pozadí
    if (phase == OnlinePhase.GAME_MULLIGAN ||
        phase == OnlinePhase.GAME_PLAYING  ||
        phase == OnlinePhase.GAME_OVER) {
        OnlineGameScreen(vm = vm, onBack = {
            vm.disconnect()
            onBack()
        })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0xAA000000)))

        when (phase) {
            OnlinePhase.NAME_INPUT  -> {
                // Pokud má hráč profilový nick, auto-connect proběhne v LaunchedEffect –
                // zobraz rovnou ConnectingPanel, aby se NAME_INPUT panel neobjevil ani na okamžik.
                if (!PlayerProfileManager.profile?.name.isNullOrBlank()) {
                    ConnectingPanel(onCancel = { vm.disconnect(); onBack() })
                } else {
                    NameInputPanel(vm, onBack)
                }
            }
            OnlinePhase.CONNECTING  -> ConnectingPanel(onCancel = { vm.disconnect(); onBack() })
            OnlinePhase.LOBBY       -> LobbyPanel(vm, decks, onBack, onLeaderboard)
            OnlinePhase.QUEUING     -> QueuingPanel(vm, onBack)
            OnlinePhase.MATCH_FOUND -> MatchFoundPanel(vm)
            OnlinePhase.ERROR       -> ErrorPanel(vm, onBack)
            else                    -> { /* herní fáze ošetřeny výše */ }
        }
    }
}

// ─── Zadání přezdívky ─────────────────────────────────────────────────────────

@Composable
private fun NameInputPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val name  by vm.playerName
    val error by vm.errorMsg

    CenteredCard {
        Text(
            "🌐 ONLINE MULTIPLAYER",
            color        = OnGold,
            fontSize     = 18.sp,
            fontWeight   = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Připoj se k lobby serveru a najdi soupeře",
            color    = OnMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // Pole přezdívky
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

        OnBtn("Připojit se", OnTeal, Modifier.width(260.dp),
            enabled = name.isNotBlank()) { vm.connect() }

        Spacer(Modifier.height(8.dp))
        OnBtn("← Zpět", OnMuted, Modifier.width(260.dp)) { onBack() }
    }
}

// ─── Připojování ─────────────────────────────────────────────────────────────

@Composable
private fun ConnectingPanel(onCancel: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = OnTeal, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Připojuji k serveru…", color = OnText, fontSize = 13.sp)
        if (onCancel != null) {
            Spacer(Modifier.height(20.dp))
            OnBtn("← Zrušit", OnMuted, Modifier.width(180.dp)) { onCancel() }
        }
    }
}

// ─── Lobby ────────────────────────────────────────────────────────────────────

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

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier              = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1520), OnPanel)))
                .border(1.dp, OnGold.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Levý sloupec: hlavička + server info + tlačítka ──────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    "LOBBY",
                    color         = OnTeal,
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(2.dp))
                // Jméno hráče + stav připojení na jednom řádku
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Hráč: $name", color = OnMuted, fontSize = 10.sp)
                    if (statusMsg.isNotBlank()) {
                        Text("• $statusMsg", color = OnTeal.copy(alpha = 0.8f), fontSize = 9.sp)
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        errorMsg,
                        color     = OnRed,
                        fontSize  = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.widthIn(max = 200.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(value = onlineCount.toString(), label = "Online",    accent = OnGreen)
                    StatBox(value = queueSize.toString(),   label = "Ve frontě", accent = OnGold)
                }

                Spacer(Modifier.height(8.dp))

                OnBtn("⚔️  Rychlý zápas", OnTeal, Modifier.width(190.dp)) {
                    vm.joinQueue(superRandom = false)
                }
                Spacer(Modifier.height(4.dp))
                OnBtn("🌪️  Super Náhodný", Color(0xFFAA44CC), Modifier.width(190.dp)) {
                    vm.joinQueue(superRandom = true)
                }
                Spacer(Modifier.height(4.dp))
                OnBtn("🏆  Žebříček", OnGold, Modifier.width(190.dp)) { onLeaderboard() }
                Spacer(Modifier.height(4.dp))
                OnBtn("← Odpojit", OnMuted, Modifier.width(190.dp)) { vm.disconnect(); onBack() }
            }

            // ── Oddělovač ─────────────────────────────────────────────────────
            Box(Modifier.width(1.dp).height(200.dp).background(OnGold.copy(alpha = 0.15f)))

            // ── Střední sloupec: statistiky hráče ────────────────────────────
            Column(
                modifier            = Modifier.width(170.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "STATISTIKY",
                    color         = OnMuted,
                    fontSize      = 8.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight    = FontWeight.Bold
                )
                ModeStatsBlock(
                    label  = "⚔️ Constructed",
                    stats  = allModeStats["normal"],
                    accent = OnTeal
                )
                ModeStatsBlock(
                    label  = "🌪️ Super Náhodný",
                    stats  = allModeStats["super_random"],
                    accent = Color(0xFFAA44CC)
                )
            }

            // ── Oddělovač ─────────────────────────────────────────────────────
            Box(Modifier.width(1.dp).height(200.dp).background(OnGold.copy(alpha = 0.15f)))

            // ── Pravý sloupec: výběr balíčku ──────────────────────────────────
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.width(160.dp)
            ) {
                Text(
                    "BALÍČEK",
                    color         = OnMuted,
                    fontSize      = 8.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight    = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                // Chipy vertikálně: Náhodný + uložené balíčky
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    DeckChip(
                        label    = "🎲 Náhodný",
                        selected = selectedDeckIdx == -1,
                        valid    = true,
                        onClick  = { vm.setDeckChoice(-1, null) }
                    )
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

@Composable
private fun DeckChip(
    label     : String,
    selected  : Boolean,
    valid     : Boolean,
    cardCount : Int = 30,
    onClick   : () -> Unit
) {
    val accent = when {
        selected && valid  -> OnTeal
        selected && !valid -> OnRed
        valid              -> OnMuted
        else               -> OnRed.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = if (selected) 0.18f else 0.07f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.3f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color      = if (selected) OnText else OnMuted,
            fontSize   = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        if (cardCount != 30) {
            Text(
                "$cardCount/30",
                color    = OnRed.copy(alpha = 0.8f),
                fontSize = 8.sp
            )
        }
    }
}

// ─── Ve frontě ───────────────────────────────────────────────────────────────

@Composable
private fun QueuingPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val queueSize    by vm.queueSize
    val isSuperRandom by vm.isSuperRandom

    val accentColor  = if (isSuperRandom) Color(0xFFAA44CC) else OnTeal
    val modeLabel    = if (isSuperRandom) "🌪️ Super Náhodný" else "⚔️ Rychlý zápas"

    CenteredCard {
        // Animovaný spinner
        CircularProgressIndicator(
            color       = accentColor,
            modifier    = Modifier.size(52.dp),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(12.dp))

        Text(
            modeLabel,
            color      = accentColor,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))

        Text(
            "Hledám soupeře…",
            color      = OnText,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        if (queueSize > 1) {
            Text(
                "Ve frontě: $queueSize hráčů",
                color    = OnMuted,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        OnBtn("✕  Zrušit", OnRed, Modifier.width(220.dp)) {
            vm.leaveQueue()
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

    CenteredCard {
        Text("⚔️", fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "SOUPEŘ NALEZEN!",
            color        = OnGreen,
            fontSize     = 20.sp,
            fontWeight   = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(12.dp))

        // Rating srovnání
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Hráč
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    match?.opponentAvatar?.let { "👤" } ?: "👤",
                    fontSize = 24.sp
                )
                Text(playerName, color = OnTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (myRating != null) Text("⭐ $myRating", color = OnGold, fontSize = 10.sp)
            }
            Text("VS", color = OnMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            // Soupeř
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarDisplay(match?.opponentAvatar ?: "enemy_icon_1", sizeDp = 32f)
                Text(
                    match?.opponentName ?: "Soupeř",
                    color      = OnRed,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (opponentRating != null) Text("⭐ $opponentRating", color = OnGold, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (match?.side == "A") "⚔️ Ty začínáš první" else "⏳ Soupeř začíná první",
            color    = if (match?.side == "A") OnTeal else OnMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(16.dp))

        CircularProgressIndicator(
            color       = OnTeal,
            modifier    = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Připravuji hru…",
            color    = OnMuted,
            fontSize = 10.sp
        )
    }
}

// ─── Chyba ───────────────────────────────────────────────────────────────────

@Composable
private fun ErrorPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val error by vm.errorMsg

    CenteredCard {
        Text("⚡", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "CHYBA PŘIPOJENÍ",
            color        = OnRed,
            fontSize     = 16.sp,
            fontWeight   = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error,
            color     = OnMuted,
            fontSize  = 10.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.widthIn(max = 260.dp)
        )
        Spacer(Modifier.height(20.dp))

        OnBtn("🔄  Zkusit znovu", OnTeal, Modifier.width(220.dp)) { vm.retryConnect() }
        Spacer(Modifier.height(8.dp))
        OnBtn("← Zpět", OnMuted, Modifier.width(220.dp)) { vm.disconnect(); onBack() }
    }
}

// ─── Sdílené komponenty ───────────────────────────────────────────────────────

@Composable
private fun CenteredCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1520), OnPanel)))
                .border(1.dp, OnGold.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun StatBox(value: String, label: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = OnMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
    }
}

// ─── Blok statistik pro jeden herní mód ──────────────────────────────────────
@Composable
private fun ModeStatsBlock(label: String, stats: OnlineModeStats?, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        val s = stats ?: OnlineModeStats()   // výchozí: rating 1000, 0 her
        // Rating řádek
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("⭐ Rating", color = OnMuted, fontSize = 9.sp)
                Text(
                    "${s.rating}",
                    color      = OnGold,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Výhry / prohry / remízy
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("W / L / D", color = OnMuted, fontSize = 9.sp)
                Text(
                    "${s.wins} / ${s.losses} / ${s.draws}",
                    color    = OnText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Win rate + počet her
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Win%", color = OnMuted, fontSize = 9.sp)
                val wrColor = when {
                    s.winRate >= 60 -> OnGreen
                    s.winRate >= 45 -> OnGold
                    s.games == 0    -> OnMuted
                    else            -> OnRed
                }
                Text(
                    if (s.games == 0) "–" else "${s.winRate}%  (${s.games} her)",
                    color      = wrColor,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
    }
}

@Composable
private fun OnBtn(
    label   : String,
    accent  : Color,
    modifier: Modifier = Modifier,
    enabled : Boolean  = true,
    onClick : () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = if (enabled) 0.12f else 0.04f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.5f else 0.15f), RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color         = if (enabled) OnText else OnMuted,
            fontSize      = 13.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
