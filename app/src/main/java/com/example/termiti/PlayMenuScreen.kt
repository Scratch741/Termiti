package com.example.termiti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PmBgDeep    = Color(0xFF0D0A0E)
private val PmBgPanel   = Color(0xFF13101A)
private val PmBgCard    = Color(0xFF1A1320)
private val PmGold      = Color(0xFFD4A843)
private val PmTealLight = Color(0xFF3DBFAD)
private val PmText      = Color(0xFFEDE0C4)
private val PmMuted     = Color(0xFF7A6E5F)

@Composable
fun PlayMenuScreen(
    onOwnDeck:    () -> Unit,
    onRandomDeck: () -> Unit,
    onSuperRandom: () -> Unit,
    onArena:      () -> Unit,
    onCampaign:   () -> Unit,
    onBack:       () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PmBgDeep, PmBgPanel, PmBgDeep)))
    ) {
        // ── Zpět ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(PmBgCard)
                .border(1.dp, PmMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("← Zpět", color = PmMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // ── Obsah ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Levá strana – titulek
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚔️", fontSize = 40.sp)
                Text(
                    "VÝBĚR MÓDU",
                    color = PmGold,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    "Zvol způsob boje",
                    color = PmMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            // Pravá strana – módy
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FantasyButton(
                    text     = "VLASTNÍ BALÍČEK",
                    subtitle = "Hraj se svým sestaveným balíčkem proti AI",
                    modifier = Modifier.fillMaxWidth(0.9f),
                    compact  = true,
                    onClick  = onOwnDeck
                )
                FantasyButton(
                    text     = "NÁHODNÝ BALÍČEK",
                    subtitle = "Dostaneš náhodně sestavený balíček",
                    modifier = Modifier.fillMaxWidth(0.9f),
                    compact  = true,
                    onClick  = onRandomDeck
                )
                FantasyButton(
                    text     = "SUPER NÁHODNÝ",
                    subtitle = "50 karet (15/15/15/5) – větší balíček, větší chaos",
                    modifier = Modifier.fillMaxWidth(0.9f),
                    compact  = true,
                    onClick  = onSuperRandom
                )
                FantasyButton(
                    text     = "ARÉNA",
                    subtitle = "Sestav balíček z nabídky a bojuj o výhry",
                    modifier = Modifier.fillMaxWidth(0.9f),
                    compact  = true,
                    onClick  = onArena
                )
                FantasyButton(
                    text     = "KAMPAŇ",
                    subtitle = "Projdi lokacemi a poraž záporáky",
                    modifier = Modifier.fillMaxWidth(0.9f),
                    compact  = true,
                    onClick  = onCampaign
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: String,
    title: String,
    description: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = PmText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(description, color = PmMuted, fontSize = 10.sp, textAlign = TextAlign.Start)
        }
    }
}
