package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource

private val CmBgDeep  = Color(0xFF09070D)
private val CmBgPanel = Color(0xFF13101A)
private val CmGold    = Color(0xFFD4A843)
private val CmTeal    = Color(0xFF3DBFAD)
private val CmText    = Color(0xFFEDE0C4)
private val CmMuted   = Color(0xFF7A6E5F)
private val CmGreen   = Color(0xFF4CAF50)
private val CmLocked  = Color(0xFF3A3040)

@Composable
fun CampaignMapScreen(
    onLocationSelected: (CampaignLocation) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CmBgDeep, CmBgPanel, CmBgDeep)))
    ) {
        // ── Zpět ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(CmBgPanel)
                .border(1.dp, CmMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("← Zpět", color = CmMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🗺️", fontSize = 36.sp)
                Text(
                    "KAMPAŇ",
                    color = CmGold,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    "Vyber lokaci a poraž\nvšechny soupeře",
                    color = CmMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            // Pravá strana – lokace (horizontální scroll)
            LazyRow(
                modifier = Modifier.weight(2f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(CampaignData.locations) { location ->
                    LocationCard(
                        location = location,
                        onClick  = {
                            if (CampaignManager.isLocationUnlocked(location)) {
                                SoundManager.playMenuTap()
                                onLocationSelected(location)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    location: CampaignLocation,
    onClick: () -> Unit
) {
    val unlocked = CampaignManager.isLocationUnlocked(location)
    val cleared  = CampaignManager.isLocationCleared(location)
    val defeatedCount = location.opponents.count { CampaignManager.isDefeated(it.id) }

    val borderColor = when {
        cleared  -> CmGreen
        unlocked -> CmGold.copy(alpha = 0.6f)
        else     -> CmMuted.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (unlocked) Color(0xFF1A1420) else CmLocked)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier)
            .alpha(if (unlocked) 1f else 0.5f)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Emoji lokace
            Text(location.emoji, fontSize = 32.sp, textAlign = TextAlign.Center)

            // Název
            Text(
                location.name,
                color = if (unlocked) CmText else CmMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Popis
            Text(
                location.description,
                color = CmMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )

            // Progres bar
            ProgressBar(
                current = defeatedCount,
                total   = location.opponents.size,
                cleared = cleared
            )

            // Status text
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val statusColor = when {
                    cleared  -> CmGreen
                    unlocked -> CmTeal
                    else     -> CmMuted
                }
                if (cleared || !unlocked) {
                    Image(
                        painterResource(if (cleared) R.drawable.check_icon else R.drawable.lock_icon),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    when {
                        cleared  -> "Vyčištěno"
                        unlocked -> "$defeatedCount / ${location.opponents.size} soupeřů"
                        else     -> "Zamčeno"
                    },
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(current: Int, total: Int, cleared: Boolean) {
    val fraction = if (total > 0) current.toFloat() / total else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(CmMuted.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(if (cleared) CmGreen else CmTeal)
        )
    }
}
