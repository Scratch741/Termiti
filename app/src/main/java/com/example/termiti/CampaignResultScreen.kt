package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints

private val CrGold  = Color(0xFFD4A843)
private val CrGreen = Color(0xFF4CAF50)
private val CrRed   = Color(0xFFE57373)
private val CrText  = Color(0xFFEDE0C4)
private val CrMuted = Color(0xFF7A6E5F)
private val CrTeal  = Color(0xFF3DBFAD)

@Composable
fun CampaignResultScreen(
    opponent        : CampaignOpponent,
    playerWon       : Boolean,
    onRetry         : () -> Unit,
    onBackToLocation: () -> Unit,
    onBackToMap     : () -> Unit
) {
    val rewardClaimed = remember(opponent.id, playerWon) {
        if (playerWon) {
            CampaignManager.markDefeated(opponent.id)
            CampaignManager.claimReward(opponent)
        } else false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // ── Texturované pozadí ────────────────────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC09070D))
        )

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
                modifier           = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (playerWon) "VÍTĚZSTVÍ!" else "PORÁŽKA",
                color        = if (playerWon) CrGold else CrRed,
                fontSize     = 32.sp,
                fontWeight   = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // ── Soupeř ────────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvatarView(opponent.avatar, size = 40.dp)
                Column {
                    Text(
                        opponent.name,
                        color      = CrText,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        opponent.title,
                        color    = CrMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Odměna (pouze při výhře) ──────────────────────────────────────
            if (playerWon && rewardClaimed) {
                Box(
                    modifier = Modifier
                        .paint(
                            painterResource(R.drawable.plain_button_longer),
                            contentScale = ContentScale.FillBounds
                        )
                        .padding(horizontal = 32.dp, vertical = 18.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ODMĚNA ZA PRVNÍ PORAŽENÍ",
                            color        = CrGold,
                            fontSize     = 11.sp,
                            fontWeight   = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            RewardBadge(R.drawable.star_icon,     "+${opponent.rewardXp} XP", Color(0xFF7EE8A2))
                            RewardBadge(R.drawable.goldcoin_icon, "${opponent.rewardGold}",   CrGold)
                            if (opponent.rewardGems > 0) {
                                RewardBadge(R.drawable.diamond_icon, "${opponent.rewardGems}", CrTeal)
                            }
                        }
                    }
                }
            } else if (playerWon) {
                Box(
                    modifier = Modifier
                        .paint(
                            painterResource(R.drawable.plain_button_longer),
                            contentScale = ContentScale.FillBounds,
                            alpha        = 0.6f
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Odměna již byla vyplacena",
                        color    = CrMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Tlačítka ──────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!playerWon) {
                    PlainButton(
                        text      = "🔄  Zkusit znovu",
                        modifier  = Modifier,
                        textColor = CrText,
                        fontSize  = 13.sp,
                        paddingH  = 20.dp,
                        paddingV  = 14.dp,
                        onClick   = onRetry
                    )
                }
                PlainButton(
                    text      = "📍  Zpět na lokaci",
                    modifier  = Modifier,
                    textColor = CrText,
                    fontSize  = 13.sp,
                    paddingH  = 20.dp,
                    paddingV  = 14.dp,
                    onClick   = onBackToLocation
                )
            }
        }
    }
}

@Composable
private fun RewardBadge(@DrawableRes iconRes: Int, value: String, color: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// Avatar: resource string → Image, emoji string → Text
@Composable
private fun AvatarView(avatar: String, size: Dp) {
    val resId: Int? = when (avatar) {
        "enemy_icon_1" -> R.drawable.enemy_icon_1
        "enemy_icon_2" -> R.drawable.enemy_icon_2
        "enemy_icon_3" -> R.drawable.enemy_icon_3
        "hammer_icon"  -> R.drawable.hammer_icon
        else           -> null
    }
    if (resId != null) {
        Image(
            painterResource(resId),
            contentDescription = null,
            modifier           = Modifier.size(size)
        )
    } else {
        Text(
            text      = avatar,
            fontSize  = (size.value * 0.65f).sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .size(size)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}
