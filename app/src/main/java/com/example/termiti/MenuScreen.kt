package com.example.termiti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgDeep  = Color(0xFF0D0A0E)
private val BgPanel = Color(0xFF13101A)
private val Gold    = Color(0xFFD4A843)
private val Teal    = Color(0xFF2A7A6F)
private val TealLight = Color(0xFF3DBFAD)
private val TextPrimary = Color(0xFFEDE0C4)
private val TextMuted   = Color(0xFF7A6E5F)

@Composable
fun MenuScreen(
    onStart: () -> Unit,
    onBuildDeck: () -> Unit,
    onArena: () -> Unit,
    onMultiplayer: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDeep, BgPanel, BgDeep)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Název
            Text(
                "TERMITI",
                color = Gold,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )
            Text(
                "Karetní hradní bitva",
                color = TextMuted,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(12.dp))

            MenuButton("⚔️  HRÁT", accent = TealLight, onClick = onStart)
            MenuButton("🌐  MULTIPLAYER", accent = Color(0xFF3A7BD5), onClick = onMultiplayer)
            MenuButton("🃏  SESTAVIT BALÍK", accent = Gold, onClick = onBuildDeck)
            MenuButton("🏟️  ARÉNA", accent = Color(0xFF9B59B6), onClick = onArena)
            MenuButton("✕  KONEC", accent = Color(0xFF8B4444), onClick = onExit)
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = if (enabled) 0.12f else 0.05f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.5f else 0.2f), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) TextPrimary.copy(alpha = alpha) else TextMuted.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}
