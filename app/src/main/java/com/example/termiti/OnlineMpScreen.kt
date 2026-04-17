package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun OnlineMpScreen(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val phase by vm.phase

    Box(Modifier.fillMaxSize()) {
        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0xAA000000)))

        when (phase) {
            OnlinePhase.NAME_INPUT  -> NameInputPanel(vm, onBack)
            OnlinePhase.CONNECTING  -> ConnectingPanel()
            OnlinePhase.LOBBY       -> LobbyPanel(vm, onBack)
            OnlinePhase.QUEUING     -> QueuingPanel(vm, onBack)
            OnlinePhase.MATCH_FOUND -> MatchFoundPanel(vm)
            OnlinePhase.ERROR       -> ErrorPanel(vm, onBack)
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
private fun ConnectingPanel() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = OnTeal, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Připojuji k serveru…", color = OnText, fontSize = 13.sp)
    }
}

// ─── Lobby ────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val name        by vm.playerName
    val onlineCount by vm.onlineCount
    val queueSize   by vm.queueSize

    CenteredCard {
        // Hlavička
        Text(
            "LOBBY",
            color        = OnTeal,
            fontSize     = 20.sp,
            fontWeight   = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(2.dp))
        Text("Hráč: $name", color = OnMuted, fontSize = 10.sp)

        Spacer(Modifier.height(20.dp))

        // Statistiky
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatBox(
                value  = onlineCount.toString(),
                label  = "Online",
                accent = OnGreen
            )
            StatBox(
                value  = queueSize.toString(),
                label  = "Ve frontě",
                accent = OnGold
            )
        }

        Spacer(Modifier.height(24.dp))

        // Rychlý zápas
        OnBtn("⚔️  Rychlý zápas", OnTeal, Modifier.width(260.dp)) {
            vm.joinQueue()
        }

        Spacer(Modifier.height(8.dp))

        OnBtn("← Odpojit", OnMuted, Modifier.width(260.dp)) {
            vm.disconnect(); onBack()
        }
    }
}

// ─── Ve frontě ───────────────────────────────────────────────────────────────

@Composable
private fun QueuingPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val queueSize by vm.queueSize

    CenteredCard {
        // Animovaný spinner
        CircularProgressIndicator(
            color     = OnTeal,
            modifier  = Modifier.size(52.dp),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(16.dp))

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
    val match by vm.matchInfo

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
        Spacer(Modifier.height(8.dp))
        Text(
            match?.opponentName ?: "Soupeř",
            color      = OnText,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (match?.side == "A") "⚔️ Ty začínáš první" else "⏳ Soupeř začíná první",
            color    = if (match?.side == "A") OnTeal else OnMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(16.dp))

        // TODO Etapa 3: přejít do herní obrazovky
        Text(
            "Herní obrazovka (Etapa 3)",
            color    = OnMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
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

        OnBtn("🔄  Zkusit znovu", OnTeal, Modifier.width(220.dp)) { vm.clearError() }
        Spacer(Modifier.height(8.dp))
        OnBtn("← Zpět", OnMuted, Modifier.width(220.dp)) { vm.clearError(); onBack() }
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(label, color = OnMuted, fontSize = 9.sp, letterSpacing = 1.sp)
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
            .padding(vertical = 10.dp),
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
