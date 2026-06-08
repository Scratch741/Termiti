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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource

private val ClBgDeep  = Color(0xFF09070D)
private val ClBgPanel = Color(0xFF13101A)
private val ClGold    = Color(0xFFD4A843)
private val ClTeal    = Color(0xFF3DBFAD)
private val ClText    = Color(0xFFEDE0C4)
private val ClMuted   = Color(0xFF7A6E5F)
private val ClGreen   = Color(0xFF4CAF50)
private val ClRed     = Color(0xFFE57373)

@Composable
fun CampaignLocationScreen(
    location: CampaignLocation,
    onOpponentSelected: (CampaignOpponent) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ClBgDeep, ClBgPanel, ClBgDeep)))
    ) {
        // ── Zpět ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(ClBgPanel)
                .border(1.dp, ClMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("← Zpět", color = ClMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // ── Obsah: Row – vlevo titulek, vpravo soupeři ────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Levá strana – info o lokaci
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(location.emoji, fontSize = 32.sp)
                Text(
                    location.name.uppercase(),
                    color = ClGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    location.description,
                    color = ClMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
                val defeated = location.opponents.count { CampaignManager.isDefeated(it.id) }
                val total    = location.opponents.size
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$defeated / $total poraženo",
                    color = if (defeated == total) ClGreen else ClTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Pravá strana – seznam soupeřů
            LazyRow(
                modifier = Modifier.weight(3f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(location.opponents) { opponent ->
                    val unlocked = CampaignManager.isUnlocked(location, opponent)
                    val isDefeated = CampaignManager.isDefeated(opponent.id)
                    OpponentCard(
                        opponent = opponent,
                        unlocked = unlocked,
                        defeated = isDefeated,
                        onClick  = {
                            SoundManager.playMenuTap()
                            onOpponentSelected(opponent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpponentCard(
    opponent: CampaignOpponent,
    unlocked: Boolean,
    defeated: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        defeated && opponent.isBoss  -> ClGold
        defeated                     -> ClGreen
        unlocked && opponent.isBoss  -> ClRed.copy(alpha = 0.8f)
        unlocked                     -> ClTeal.copy(alpha = 0.6f)
        else                         -> ClMuted.copy(alpha = 0.3f)
    }
    val bgColor = if (opponent.isBoss && unlocked) Color(0xFF1F0A0A) else Color(0xFF1A1420)

    Box(
        modifier = Modifier
            .width(155.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier)
            .alpha(if (unlocked) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Boss badge
            if (opponent.isBoss) {
                Text(
                    "👑 BOSS",
                    color = ClGold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }

            // Avatar
            Text(opponent.avatar, fontSize = 32.sp, textAlign = TextAlign.Center)

            // Jméno
            Text(
                opponent.name,
                color = ClText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            // Titul
            Text(
                opponent.title,
                color = ClMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic
            )

            // Popis
            Text(
                opponent.description,
                color = ClMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )

            // Odměna
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Image(painterResource(R.drawable.goldcoin_icon), contentDescription = null, modifier = Modifier.size(12.dp))
                    Text("${opponent.rewardGold}", color = ClGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (opponent.rewardGems > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Image(painterResource(R.drawable.diamond_icon), contentDescription = null, modifier = Modifier.size(12.dp))
                        Text("${opponent.rewardGems}", color = Color(0xFF7EC8E3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val statusColor = when {
                    defeated  -> ClGreen
                    !unlocked -> ClMuted
                    else      -> ClTeal
                }
                Image(
                    painterResource(when {
                        defeated  -> R.drawable.check_icon
                        !unlocked -> R.drawable.lock_icon
                        else      -> R.drawable.utok_icon
                    }),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    when {
                        defeated  -> "Poražen"
                        !unlocked -> "Zamčen"
                        else      -> "Bojuj!"
                    },
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
