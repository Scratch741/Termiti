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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgDeep      = Color(0xFF0D0A0E)
private val BgPanel     = Color(0xFF13101A)
private val BgCard      = Color(0xFF1A1320)
private val Gold        = Color(0xFFD4A843)
private val TealLight   = Color(0xFF3DBFAD)
private val TextPrimary = Color(0xFFEDE0C4)
private val TextMuted   = Color(0xFF7A6E5F)

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onBuildDeck: () -> Unit,
    onMultiplayer: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    val profile = PlayerProfileManager.profile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDeep, BgPanel, BgDeep)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ── Levá strana: titul + profil ───────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "TERMITI",
                    color = Gold,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
                Text(
                    "Karetní hradní bitva",
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )

                if (profile != null) {
                    Spacer(Modifier.height(4.dp))
                    ProfileCard(profile)
                }
            }

            // ── Pravá strana: tlačítka ────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MenuButton("⚔️  HRÁT",          accent = TealLight,         onClick = onPlay)
                MenuButton("🌐  MULTIPLAYER",    accent = Color(0xFF3A7BD5), onClick = onMultiplayer)
                MenuButton("🃏  SESTAVIT BALÍK", accent = Gold,              onClick = onBuildDeck)
                MenuButton("👤  PROFIL",         accent = Color(0xFF7EC8E3), onClick = onProfile)
                MenuButton("⚙️  NASTAVENÍ",      accent = Color(0xFF607D8B), onClick = onSettings)
                MenuButton("✕  KONEC",          accent = Color(0xFF8B4444), onClick = onExit)
            }
        }
    }
}

// ── Profilová karta ───────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(profile: PlayerProfile) {
    val xpFrac = (profile.xp.toFloat() / profile.xpNeeded()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Jméno + level + měna
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Avatar + jméno
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Gold.copy(alpha = 0.15f))
                        .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚔️", fontSize = 15.sp)
                }
                Column {
                    Text(profile.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Lv. ${profile.level}", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Měna
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🪙", fontSize = 11.sp)
                    Text("${profile.gold}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("💎", fontSize = 11.sp)
                    Text("${profile.gems}", color = Color(0xFF7EC8E3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // XP bar
        LinearProgressIndicator(
            progress = { xpFrac },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Gold,
            trackColor = TextMuted.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round
        )
        Text(
            "${profile.xp} / ${profile.xpNeeded()} XP",
            color = TextMuted,
            fontSize = 9.sp
        )
    }
}

// ── Tlačítko menu ─────────────────────────────────────────────────────────────

@Composable
fun MenuButton(
    label: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = if (enabled) 0.12f else 0.05f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.5f else 0.2f), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) TextPrimary.copy(alpha = alpha) else TextMuted.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}
