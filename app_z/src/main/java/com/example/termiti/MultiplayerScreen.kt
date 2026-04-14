package com.example.termiti

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Colors ──────────────────────────────────────────────────────────────────
private val MpBgDeep   = Color(0xFF0A0D14)
private val MpBgPanel  = Color(0xFF111520)
private val MpTeal     = Color(0xFF3DBFAD)
private val MpGold     = Color(0xFFD4A843)
private val MpRed      = Color(0xFFCF4A4A)
private val MpText     = Color(0xFFEDE0C4)
private val MpMuted    = Color(0xFF7A6E5F)
private val MpGreen    = Color(0xFF4CAF50)
private val MpPurple   = Color(0xFF9B59B6)

// ─── Root composable ─────────────────────────────────────────────────────────

@Composable
fun MultiplayerScreen(vm: MultiplayerViewModel, onBack: () -> Unit) {
    val phase by vm.phase
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0x77000000)))
        when (phase) {
            MpPhase.LOBBY        -> MpLobbyScreen(vm, onBack)
            MpPhase.CONNECTING   -> MpConnectingScreen(vm)
            MpPhase.MULLIGAN     -> MpMulliganScreen(vm)
            MpPhase.PLAYING      -> MpGameScreen(vm)
            MpPhase.GAME_OVER    -> MpGameOverScreen(vm, onBack)
            MpPhase.RECONNECTING -> MpReconnectingScreen(vm)
        }
    }
}

// ─── Lobby ───────────────────────────────────────────────────────────────────

@Composable
private fun MpLobbyScreen(vm: MultiplayerViewModel, onBack: () -> Unit) {
    var hostIp      by remember { mutableStateOf("") }
    var name        by remember { mutableStateOf(vm.myName.value) }
    val selectedIdx by vm.selectedDeckIndex
    val decks       = vm.decks
    val localIp     by vm.localIp
    val isScanning  by vm.isScanning
    val foundHosts  by vm.foundHosts

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌐 MULTIPLAYER", color = MpTeal, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Text("Lokální síť", color = MpMuted, fontSize = 11.sp, letterSpacing = 2.sp)

        Spacer(Modifier.height(24.dp))

        // ── Jméno ────────────────────────────────────────────────────────────
        MpTextField(
            value    = name,
            onValue  = { name = it.take(16); vm.myName.value = it.take(16) },
            label    = "Tvoje jméno",
            modifier = Modifier.width(280.dp)
        )

        Spacer(Modifier.height(20.dp))

        // ── Výběr balíčku ────────────────────────────────────────────────────
        if (decks.isNotEmpty()) {
            Text("BALÍČEK", color = MpMuted, fontSize = 9.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.width(280.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                decks.forEachIndexed { idx, deck ->
                    val isSel  = idx == selectedIdx
                    val isValid = deck.isValid
                    val accent  = if (isSel) MpTeal else MpMuted
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSel) MpTeal.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f))
                            .border(1.dp, accent.copy(alpha = if (isSel) 0.65f else 0.22f), RoundedCornerShape(7.dp))
                            .clickable { vm.selectDeck(idx) }
                            .padding(horizontal = 3.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                               verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(deck.name, color = accent,
                                fontSize = 8.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1)
                            Text(
                                if (isValid) "✓ 30" else "${deck.totalCards}/30",
                                color = if (isValid) MpGreen else MpMuted.copy(alpha = 0.6f),
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // Náhodný balíček
            val isRandom = selectedIdx == -1
            Box(
                Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isRandom) MpPurple.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f))
                    .border(1.dp,
                        if (isRandom) MpPurple.copy(alpha = 0.65f) else MpMuted.copy(alpha = 0.22f),
                        RoundedCornerShape(7.dp))
                    .clickable { vm.selectDeck(-1) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🎲  Náhodný balíček",
                    color = if (isRandom) MpPurple else MpMuted,
                    fontSize = 10.sp,
                    fontWeight = if (isRandom) FontWeight.Bold else FontWeight.Normal)
            }

            // Varování pro nevalidní vybraný balíček
            val selDeck = decks.getOrNull(selectedIdx)
            if (selDeck != null && !selDeck.isValid) {
                Spacer(Modifier.height(4.dp))
                Text("⚠️ Balíček není kompletní – bude použit náhodný",
                    color = MpGold.copy(alpha = 0.8f), fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(280.dp))
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── HOST ─────────────────────────────────────────────────────────────
        MpButton("📡  HOSTOVAT HRU", MpTeal, Modifier.width(280.dp)) { vm.hostGame() }

        Spacer(Modifier.height(16.dp))

        HorizontalDivider(Modifier.width(240.dp), color = MpMuted.copy(alpha = 0.3f))
        Text("nebo", color = MpMuted, fontSize = 10.sp,
            modifier = Modifier.padding(vertical = 8.dp))

        // ── JOIN ─────────────────────────────────────────────────────────────
        MpTextField(
            value        = hostIp,
            onValue      = { hostIp = it },
            label        = "IP adresa hostitele",
            modifier     = Modifier.width(280.dp),
            keyboardType = KeyboardType.Uri
        )
        Spacer(Modifier.height(8.dp))

        // ── Skenování sítě ───────────────────────────────────────────────────
        if (isScanning) {
            Row(
                Modifier.width(280.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color    = MpPurple,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Hledám hry v síti…", color = MpMuted, fontSize = 11.sp)
            }
        } else {
            MpButton(
                "🔍  Hledat hru v síti",
                MpPurple.copy(alpha = 0.7f),
                Modifier.width(280.dp)
            ) { vm.scanNetwork() }
        }

        // Nalezení hostitelé
        if (foundHosts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Nalezené hry:", color = MpMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            foundHosts.forEach { ip ->
                Box(
                    Modifier
                        .width(280.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MpGreen.copy(alpha = 0.10f))
                        .border(1.dp, MpGreen.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                        .clickable { vm.joinGame(ip) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("🎮  $ip", color = MpText, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Připojit →", color = MpGreen, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        MpButton("🔗  PŘIPOJIT SE", MpPurple, Modifier.width(280.dp),
            enabled = hostIp.isNotBlank()) { vm.joinGame(hostIp) }

        Spacer(Modifier.height(24.dp))
        MpButton("← Zpět", MpMuted, Modifier.width(280.dp)) { onBack() }
    }
}

// ─── Connecting ───────────────────────────────────────────────────────────────

@Composable
private fun MpConnectingScreen(vm: MultiplayerViewModel) {
    val status by vm.statusMsg
    val ip     by vm.localIp
    val host   by vm.isHost

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MpTeal, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))

        // Při hostování: prominentní IP vždy nahoře
        if (host) {
            val ipReady = ip.length > 3 && ip != "?"
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MpTeal.copy(alpha = 0.12f))
                    .border(1.5.dp, MpTeal.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                       verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SDÍLEJ SOUPEŘI", color = MpMuted, fontSize = 9.sp,
                        letterSpacing = 2.sp)
                    if (ipReady) {
                        Text(ip, color = MpTeal, fontSize = 28.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    } else {
                        CircularProgressIndicator(
                            color = MpTeal,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text("port ${NetworkManager.PORT}", color = MpMuted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(status, color = MpText, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp))

        Spacer(Modifier.height(24.dp))
        MpButton("✕ Zrušit", MpRed, Modifier.width(200.dp)) { vm.returnToLobby() }
    }
}

// ─── Mulligan ─────────────────────────────────────────────────────────────────

@Composable
private fun MpMulliganScreen(vm: MultiplayerViewModel) {
    val hand      by remember { derivedStateOf { vm.myState.value.hand.toList() } }
    val selected  by vm.mulliganSelected
    val submitted by vm.mulliganSubmitted
    val goesFirst by vm.goesFirst

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE5000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1520), MpBgPanel)))
                .border(1.dp, MpGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title
            Text(
                "MULLIGAN",
                color = MpGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp
            )

            // Who goes first badge
            if (goesFirst) {
                Text("⚔️ Ty začínáš první",  color = MpTeal,  fontSize = 10.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("⏳ Soupeř začíná první", color = MpMuted, fontSize = 10.sp)
            }

            // Subtitle / waiting indicator
            if (submitted) {
                Text("Čekám na soupeře…", color = MpTeal, fontSize = 10.sp, textAlign = TextAlign.Center)
            } else {
                val subtitle = if (selected.isEmpty()) "Vyber karty k výměně, nebo hraj bez výměny"
                               else "Vyměníš ${selected.size} ${if (selected.size == 1) "kartu" else if (selected.size < 5) "karty" else "karet"}"
                Text(subtitle, color = MpMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
            }

            // Cards row — locked (no clicks) after submit
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hand.forEach { card ->
                    val isSel = card.id in selected
                    Box(
                        Modifier.let { if (!submitted) it.clickable { vm.toggleMulligan(card.id) } else it }
                    ) {
                        CardView(
                            card        = card,
                            canPlay     = !isSel && !submitted,
                            discardMode = isSel,
                            onClick     = { if (!submitted) vm.toggleMulligan(card.id) }
                        )
                        if (isSel) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MpRed.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↩", fontSize = 26.sp, color = Color.White)
                            }
                        }
                        // Dim all cards after submit
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

            // Buttons — hidden after submit
            if (!submitted) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MpButton("Hrát bez výměny", MpMuted, Modifier.weight(1f)) {
                        vm.skipMulligan()
                    }
                    MpButton(
                        "Vyměnit (${selected.size})",
                        if (selected.isNotEmpty()) MpTeal else MpMuted,
                        Modifier.weight(1f),
                        enabled = selected.isNotEmpty()
                    ) { vm.confirmMulligan() }
                }
            }
        }
    }
}

// ─── Game screen ──────────────────────────────────────────────────────────────

@Composable
private fun MpGameScreen(vm: MultiplayerViewModel) {
    val myS         by remember { derivedStateOf { vm.myState.value } }
    val oppS        by remember { derivedStateOf { vm.oppState.value } }
    val isMyTurn    by vm.isMyTurn
    val isCombo     by vm.isComboTurn
    val log         by vm.gameLog
    val lastCard    by vm.lastCard
    val turn        by vm.currentTurn
    val oppName          by vm.oppName
    val oppRevealed      by vm.oppRevealedCardIds
    val lastCardAction by vm.lastCardAction
    val cardHistory    by vm.cardHistory
    val lostToOpponent by vm.lostToOpponent

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showLostCards    by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        Column(Modifier.fillMaxSize()) {

            // ── Hlavní řada: postranní panely + střed ─────────────────────
            Row(Modifier.fillMaxWidth().weight(1f)) {

                // Soupeřův panel vlevo
                PlayerPanel(
                    playerState = oppS,
                    isEnemy     = true,
                    label       = oppName,
                    modifier    = Modifier.fillMaxHeight().weight(1f)
                )

                VerticalDivider(color = MpMuted.copy(alpha = 0.2f))

                // Střed
                Column(Modifier.fillMaxHeight().weight(2f)) {

                    MpStatusBar(
                        turn     = turn,
                        isMyTurn = isMyTurn,
                        isCombo  = isCombo,
                        oppName  = oppName,
                        modifier = Modifier.fillMaxWidth()
                    )

                    MpOpponentHand(
                        hand        = oppS.hand.toList(),
                        revealedIds = oppRevealed,
                        modifier    = Modifier.fillMaxWidth().padding(4.dp)
                    )

                    HorizontalDivider(color = MpMuted.copy(alpha = 0.15f))

                    Row(Modifier.fillMaxWidth().weight(1f)) {

                        // ── Vlevo: poslední zahraná/zahozená karta + historie ───────
                        Column(
                            Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(MpBgDeep.copy(alpha = 0.60f))
                        ) {
                            // Hlavní oblast – velká karta uprostřed
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (lastCard != null) {
                                    val ringColor = when (lastCardAction) {
                                        CardAction.PLAYED    -> MpTeal
                                        CardAction.DISCARDED -> MpRed
                                        CardAction.BURNED    -> Color(0xFFE07B39)
                                        CardAction.STOLEN    -> Color(0xFF9B59B6)
                                    }
                                    Box(Modifier.scale(1.3f)) {
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(2.5.dp, ringColor, RoundedCornerShape(8.dp))
                                        ) {
                                            CardView(
                                                card        = lastCard!!,
                                                canPlay     = false,
                                                discardMode = false,
                                                onClick     = {}
                                            )
                                        }
                                    }
                                } else {
                                    Text("—", color = MpMuted.copy(alpha = 0.3f), fontSize = 16.sp)
                                }
                            }

                        }

                        VerticalDivider(color = MpMuted.copy(alpha = 0.12f))

                        // ── Vpravo: log (horní polovina) + tlačítko Konec tahu ─────
                        Column(
                            Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(MpBgDeep.copy(alpha = 0.60f))
                        ) {
                            LogPanel(
                                log        = log,
                                modifier   = Modifier.fillMaxWidth().weight(1f),
                                scrollable = true
                            )

                            // ── Historie zahraných karet ─────────────────────
                            if (cardHistory.isNotEmpty()) {
                                HorizontalDivider(color = MpMuted.copy(alpha = 0.15f))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MpBgDeep.copy(alpha = 0.35f))
                                        .padding(horizontal = 4.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        itemsIndexed(cardHistory) { _, entry ->
                                            MiniHistoryCard(
                                                card    = entry.card,
                                                action  = entry.action,
                                                isMine  = entry.isMine,
                                                onClick = { showLostCards = true }
                                            )
                                        }
                                    }
                                }
                            }

                            // Tlačítko je vždy viditelné – zšedivělo když není na tahu
                            val active = isMyTurn || isCombo
                            val btnLabel = if (isCombo) "⚡ Konec combo" else "⏩ Konec tahu"
                            val btnColor = when {
                                isCombo  -> MpGold
                                active   -> MpTeal
                                else     -> MpMuted.copy(alpha = 0.35f)
                            }
                            HorizontalDivider(color = MpMuted.copy(alpha = 0.2f))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (active) Modifier.clickable {
                                        if (isCombo) vm.endComboTurn() else vm.waitTurn()
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

                VerticalDivider(color = MpMuted.copy(alpha = 0.2f))

                // Můj panel vpravo
                PlayerPanel(
                    playerState = myS,
                    isEnemy     = false,
                    label       = "Ty",
                    modifier    = Modifier.fillMaxHeight().weight(1f)
                )
            }

            // ── Ruka hráče – pod hlavní řadou, stejně jako v offline verzi ─
            HorizontalDivider(color = MpMuted.copy(alpha = 0.2f))
            HandPanel(
                hand            = myS.hand.toList(),
                isPlayerTurn    = isMyTurn,
                isComboTurn     = isCombo,
                playerResources = myS.resources,
                onPlayCard      = { vm.playCard(it) },
                onDiscardCard   = { vm.discardCard(it) },
                onWait          = { vm.waitTurn() },
                onEndTurn       = { vm.endComboTurn() },
                showHeader      = false,
                playerWallHp    = myS.wallHP,
                playerCastleHp  = myS.castleHP,
                modifier        = Modifier.fillMaxWidth().height(150.dp)
            )
        }

        // ── Tlačítko menu v rohu ───────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 38.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, MpMuted.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                .clickable { showLeaveConfirm = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("☰", color = MpMuted, fontSize = 12.sp)
        }

        // ── Potvrzení odchodu ─────────────────────────────────────────────
        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                containerColor   = Color(0xFF1A1320),
                titleContentColor = MpText,
                textContentColor  = MpMuted,
                title   = { Text("Opustit hru?", fontWeight = FontWeight.Bold) },
                text    = { Text("Soupeř bude informován o tvém odchodu.") },
                confirmButton = {
                    TextButton(onClick = { vm.returnToLobby() }) {
                        Text("Odejít", color = MpRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirm = false }) {
                        Text("Zůstat", color = MpTeal, fontWeight = FontWeight.Bold)
                    }
                }
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

@Composable
private fun MpStatusBar(
    turn: Int,
    isMyTurn: Boolean,
    isCombo: Boolean,
    oppName: String,
    modifier: Modifier = Modifier
) {
    val color = if (isMyTurn || isCombo) MpTeal else MpMuted
    val text  = when {
        isCombo  -> "⚡  COMBO"
        isMyTurn -> "⚔️  TVŮJ TAH"
        else     -> "⏳  ${oppName.uppercase()} HRAJE"
    }
    Row(
        modifier
            .background(color.copy(alpha = 0.08f))
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(color.copy(alpha = 0.2f), Offset(0f, size.height - stroke), Offset(size.width, size.height - stroke), stroke)
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Kolo $turn", color = MpMuted, fontSize = 9.sp)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MpOpponentHand(
    hand: List<Card>,
    revealedIds: Set<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Soupeř: ", color = MpMuted, fontSize = 9.sp)

        LazyRow(
            modifier              = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.Center,
            contentPadding        = PaddingValues(horizontal = 2.dp)
        ) {
            items(hand, key = { it.id }) { card ->
                val isRevealed = card.id in revealedIds
                AnimatedContent(
                    targetState  = isRevealed,
                    modifier     = Modifier.animateItem(),
                    transitionSpec = {
                        // Přechod rub → líc: přiblížit + zesílit
                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "cardReveal_${card.id}"
                ) { revealed ->
                    if (revealed) {
                        MiniCardFront(card)
                    } else {
                        CardBack()
                    }
                }
            }
        }

    }
}

// ─── Reconnecting ─────────────────────────────────────────────────────────────

@Composable
private fun MpReconnectingScreen(vm: MultiplayerViewModel) {
    val isHost  by vm.isHost
    val status  by vm.statusMsg
    val ip      by vm.localIp
    val lastIp  by vm.lastHostIp

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚡", fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "PŘERUŠENO SPOJENÍ",
            color = MpRed, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(6.dp))
        Text("Stav hry je zachován.", color = MpMuted, fontSize = 11.sp)
        Spacer(Modifier.height(24.dp))

        if (isHost) {
            // Host čeká – zobrazí IP pro reconnect
            CircularProgressIndicator(color = MpTeal, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(status, color = MpText, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (ip.length > 3) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MpTeal.copy(alpha = 0.1f))
                        .border(1.dp, MpTeal.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Soupeř se musí znovu připojit na:", color = MpMuted, fontSize = 10.sp)
                        Text(ip, color = MpTeal, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("port ${NetworkManager.PORT}", color = MpMuted, fontSize = 10.sp)
                    }
                }
            }
        } else {
            // Guest – tlačítko Reconnect
            Text("Soupeř se odpojil nebo ztratil spojení.", color = MpMuted, fontSize = 12.sp,
                textAlign = TextAlign.Center)
            if (lastIp.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Hostitelská IP: $lastIp", color = MpText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = MpMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            MpButton("🔄  Reconnect", MpTeal, Modifier.width(260.dp)) { vm.tryReconnect() }
        }

        Spacer(Modifier.height(20.dp))
        MpButton("✕  Opustit hru", MpRed, Modifier.width(260.dp)) { vm.returnToLobby() }
    }
}

// ─── Game Over ────────────────────────────────────────────────────────────────

@Composable
private fun MpGameOverScreen(vm: MultiplayerViewModel, onBack: () -> Unit) {
    val won     by vm.gameOver
    val log     by vm.gameLog
    val isHost  by vm.isHost

    val isWin   = won == true
    val title   = if (isWin) "VÍTĚZSTVÍ!" else "PORÁŽKA"
    val color   = if (isWin) MpGreen else MpRed
    val emoji   = if (isWin) "🏆" else "💀"

    Column(
        Modifier
            .fillMaxSize()
            .background(MpBgDeep.copy(alpha = 0.95f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = color, fontSize = 28.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 4.sp)

        // Last log message as sub-text
        val lastMsg = log.lastOrNull()
        if (lastMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(lastMsg, color = MpText, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(32.dp))

        // Rematch only host initiates
        if (isHost) {
            MpButton("🔄  Rematch", MpTeal, Modifier.width(260.dp)) { vm.requestRematch() }
            Spacer(Modifier.height(8.dp))
        } else {
            val status by vm.statusMsg
            if (status.contains("Čekám") || status.contains("Žádost")) {
                Text(status, color = MpMuted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
            } else {
                MpButton("🔄  Žádat rematch", MpTeal, Modifier.width(260.dp)) { vm.requestRematch() }
                Spacer(Modifier.height(8.dp))
            }
        }

        MpButton("← Zpět do menu", MpMuted, Modifier.width(260.dp)) {
            vm.returnToLobby(); onBack()
        }
    }
}

// ─── Mini karta v historii ────────────────────────────────────────────────────

@Composable
private fun MiniHistoryCard(
    card: Card, action: CardAction, isMine: Boolean,
    onClick: () -> Unit = {}
) {
    val borderColor = when (action) {
        CardAction.BURNED    -> Color(0xFFE07B39).copy(alpha = 0.85f)
        CardAction.STOLEN    -> Color(0xFF9B59B6).copy(alpha = 0.85f)
        CardAction.DISCARDED -> if (isMine) MpTeal.copy(alpha = 0.55f) else MpRed.copy(alpha = 0.55f)
        CardAction.PLAYED    -> if (isMine) MpTeal.copy(alpha = 0.80f) else MpRed.copy(alpha = 0.80f)
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

// ─── Shared UI components ────────────────────────────────────────────────────

@Composable
private fun MpButton(
    label: String, accent: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = if (enabled) 0.12f else 0.04f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.5f else 0.15f), RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = if (enabled) MpText else MpMuted,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MpTextField(
    value: String, onValue: (String) -> Unit, label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label, color = MpMuted, fontSize = 11.sp) },
        singleLine    = true,
        modifier      = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MpTeal,
            unfocusedBorderColor = MpMuted.copy(alpha = 0.4f),
            focusedTextColor     = MpText,
            unfocusedTextColor   = MpText,
            cursorColor          = MpTeal
        )
    )
}
