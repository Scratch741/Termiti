package com.example.termiti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrBgDeep    = Color(0xFF0D0A0E)
private val PrBgPanel   = Color(0xFF13101A)
private val PrBgCard    = Color(0xFF1A1320)
private val PrGold      = Color(0xFFD4A843)
private val PrTealLight = Color(0xFF3DBFAD)
private val PrText      = Color(0xFFEDE0C4)
private val PrMuted     = Color(0xFF7A6E5F)
private val PrGems      = Color(0xFF7EC8E3)

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val profile = PlayerProfileManager.profile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrBgDeep, PrBgPanel, PrBgDeep)))
    ) {
        // ── Zpět ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(PrBgCard)
                .border(1.dp, PrMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("← Zpět", color = PrMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (profile == null) return@Box

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Levý sloupec ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar + jméno + level (řada)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrGold.copy(alpha = 0.12f))
                            .border(2.dp, PrGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚔️", fontSize = 24.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile.name, color = PrText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Úroveň ${profile.level}", color = PrGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // XP bar
                val xpFrac = (profile.xp.toFloat() / profile.xpNeeded()).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    LinearProgressIndicator(
                        progress     = { xpFrac },
                        modifier     = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color        = PrGold,
                        trackColor   = PrMuted.copy(alpha = 0.2f),
                        strokeCap    = StrokeCap.Round
                    )
                    Text(
                        "${profile.xp} / ${profile.xpNeeded()} XP",
                        color = PrMuted, fontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }

                // Měna
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CurrencyBadge("🪙", profile.gold,  PrGold, "Zlato",      Modifier.weight(1f))
                    CurrencyBadge("💎", profile.gems,  PrGems, "Drahokamy",  Modifier.weight(1f))
                }

                // Statistiky
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadge("⚔️", "${profile.winsOffline + profile.winsOnline}", "Výher",    Modifier.weight(1f))
                    StatBadge("🎮", "${profile.totalGames}",                       "Odehráno", Modifier.weight(1f))
                }
            }

            // ── Pravý sloupec: budoucí sekce ──────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader("🖼️  Ikonka hráče")
                ComingSoonCard("Výběr avatara bude dostupný v budoucí aktualizaci.")

                SectionHeader("⚡  Pasivní schopnosti")
                ComingSoonCard("Odemkni schopnosti levelem a aktivuj je před bitvou.")

                SectionHeader("🎨  Kosmetika")
                ComingSoonCard("Různé cardbacky, hrady a zdi za drahokamy.")
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun CurrencyBadge(icon: String, amount: Int, color: Color, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text("$amount", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PrMuted, fontSize = 8.sp)
    }
}

@Composable
private fun StatBadge(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text(value, color = PrText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PrMuted, fontSize = 8.sp)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = PrGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun ComingSoonCard(description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PrBgCard)
            .border(1.dp, PrMuted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🔒", fontSize = 13.sp)
        Text(description, color = PrMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}
