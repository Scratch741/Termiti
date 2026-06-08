package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.res.painterResource

private val CrBgDeep  = Color(0xFF09070D)
private val CrBgPanel = Color(0xFF13101A)
private val CrGold    = Color(0xFFD4A843)
private val CrGreen   = Color(0xFF4CAF50)
private val CrRed     = Color(0xFFE57373)
private val CrText    = Color(0xFFEDE0C4)
private val CrMuted   = Color(0xFF7A6E5F)
private val CrTeal    = Color(0xFF3DBFAD)

@Composable
fun CampaignResultScreen(
    opponent: CampaignOpponent,
    playerWon: Boolean,
    onRetry: () -> Unit,
    onBackToLocation: () -> Unit,
    onBackToMap: () -> Unit
) {
    // Vyplat odměnu jednou při prvním vykreslení (pokud hráč vyhrál)
    val rewardClaimed = remember(opponent.id, playerWon) {
        if (playerWon) {
            CampaignManager.markDefeated(opponent.id)
            CampaignManager.claimReward(opponent)
        } else false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CrBgDeep, CrBgPanel, CrBgDeep)))
    ) {
        val screenHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = screenHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Výsledek ──────────────────────────────────────────────────────
            Image(
                painterResource(if (playerWon) R.drawable.trophy_icon else R.drawable.skull_icon),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (playerWon) "VÍTĚZSTVÍ!" else "PORÁŽKA",
                color = if (playerWon) CrGold else CrRed,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Soupeř
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(opponent.avatar, fontSize = 28.sp)
                Column {
                    Text(
                        opponent.name,
                        color = CrText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        opponent.title,
                        color = CrMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Odměna (pouze při výhře) ──────────────────────────────────────
            if (playerWon && rewardClaimed) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CrGold.copy(alpha = 0.08f))
                        .border(1.dp, CrGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "ODMĚNA ZA PRVNÍ PORAŽENÍ",
                            color = CrGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RewardBadge(R.drawable.star_icon,     "+${opponent.rewardXp} XP",  Color(0xFF7EE8A2))
                            RewardBadge(R.drawable.goldcoin_icon, "${opponent.rewardGold}",    CrGold)
                            if (opponent.rewardGems > 0) {
                                RewardBadge(R.drawable.diamond_icon, "${opponent.rewardGems}", CrTeal)
                            }
                        }
                    }
                }
            } else if (playerWon) {
                // Odměna již byla vyplacena dříve
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CrMuted.copy(alpha = 0.06f))
                        .border(1.dp, CrMuted.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Odměna již byla vyplacena",
                        color = CrMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Tlačítka ──────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!playerWon) {
                    ResultButton(
                        label   = "🔄  Zkusit znovu",
                        accent  = CrTeal,
                        onClick = onRetry
                    )
                }
                ResultButton(
                    label   = "📍  Zpět na lokaci",
                    accent  = CrGold,
                    onClick = onBackToLocation
                )
            }
        }
    }
}

@Composable
private fun RewardBadge(@DrawableRes iconRes: Int, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = CrText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
    }
}
