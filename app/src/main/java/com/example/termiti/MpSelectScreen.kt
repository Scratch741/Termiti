package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Barvy (sdílené s ostatními MP obrazovkami) ───────────────────────────────
private val SelTeal   = Color(0xFF3DBFAD)
private val SelGold   = Color(0xFFD4A843)
private val SelMuted  = Color(0xFF7A6E5F)
private val SelText   = Color(0xFFEDE0C4)
private val SelBg     = Color(0xFF0A0D14)

// ─── Výběr módu ──────────────────────────────────────────────────────────────

@Composable
fun MpSelectScreen(
    onOnline : () -> Unit,
    onLocal  : () -> Unit,
    onBack   : () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        // Pozadí
        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Nadpis
            Text(
                "MULTIPLAYER",
                color        = SelGold,
                fontSize     = 28.sp,
                fontWeight   = FontWeight.Bold,
                letterSpacing = 5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Vyber způsob připojení",
                color    = SelMuted,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(40.dp))

            // Online tlačítko
            MpSelectButton(
                emoji   = "🌐",
                title   = "Online",
                subtitle = "Přes internet – lobby server",
                accent  = SelTeal,
                onClick = onOnline
            )

            Spacer(Modifier.height(16.dp))

            // Lokálně tlačítko
            MpSelectButton(
                emoji   = "📡",
                title   = "Lokálně",
                subtitle = "Přes WiFi – přímé připojení",
                accent  = SelGold,
                onClick = onLocal
            )

            Spacer(Modifier.height(40.dp))

            // Zpět
            Box(
                Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, SelMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("← Zpět", color = SelMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MpSelectButton(
    emoji   : String,
    title   : String,
    subtitle: String,
    accent  : Color,
    onClick : () -> Unit
) {
    Box(
        Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(emoji, fontSize = 32.sp)
            Column {
                Text(
                    title,
                    color      = SelText,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    subtitle,
                    color    = SelMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
