package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onBackToMap     : () -> Unit,
    onNextOpponent  : (() -> Unit)? = null,
    /** True = odměna za první poražení byla právě teď vyplacena (řeší volající). */
    rewardClaimed   : Boolean = false,
    /** Náhled právě dohrané bitvy; null = tlačítko se nezobrazí. */
    onReviewGame    : (() -> Unit)? = null
) {
    val s = LocalStrings.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // ── Texturované pozadí ────────────────────────────────────────────────
        // bg_campaign.png má vykreslený kamenný rám po obvodu, takže se nesmí
        // ořezávat (Crop) – rám by z části vypadl mimo obrazovku. FillBounds ho
        // udrží přilepený k okrajům; mírné roztažení malby není poznat.
        Image(
            painter            = painterResource(R.drawable.bg_campaign),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.FillBounds
        )
        // Stejný jemný overlay jako na ostatních obrazovkách kampaně – bg_campaign
        // je samo o sobě tmavé, původních 0xCC (kvůli světlému bg_game) by ho zakrylo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4009070D))
        )

        val screenHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = screenHeight)
                .verticalScroll(rememberScrollState())
                // odsazení od kamenného rámu jako na ostatních obrazovkách kampaně + rezerva,
                // aby nadpis ani tlačítka nelícovaly s rámem
                .padding(
                    start  = campaignInsetX(maxWidth),
                    end    = campaignInsetX(maxWidth),
                    top    = campaignInsetTop(maxHeight) + 8.dp,
                    bottom = campaignInsetBottom(maxHeight) + 8.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Výsledek – stejný nadpis jako na ostatních obrazovkách kampaně ─────
            CampaignTitle(
                if (playerWon) s.campaignVictory else s.campaignDefeat,
                fontSize = 36.sp,
                gradient = if (playerWon) TitleGold else TitleBlood
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ── Soupeř: portrét, jméno, titul – vše na ose ────────────────────
            OpponentPortrait(opponent, ringColor = if (playerWon) CrGold else CrRed)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                opponent.displayName,
                color      = CrText,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                style      = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f))
            )
            Text(
                opponent.displayTitle,
                color     = CrGold.copy(alpha = 0.85f),
                fontSize  = 12.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

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
                            s.campaignRewardFirstKill,
                            color         = CrGold,
                            fontSize      = 13.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            textAlign     = TextAlign.Center
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
                            alpha        = 0.85f
                        )
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(painterResource(R.drawable.check_icon), contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            s.campaignRewardAlreadyClaimed,
                            color      = CrText.copy(alpha = 0.9f),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center
                        )
                    }
                }
            }

                    Spacer(modifier = Modifier.height(18.dp))

            // ── Tlačítka ──────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!playerWon) {
                    PlainButton(
                        text      = s.campaignRetry,
                        modifier  = Modifier,
                        textColor = CrText,
                        fontSize  = 13.sp,
                        paddingH  = 20.dp,
                        paddingV  = 12.dp,
                        onClick   = onRetry
                    )
                }
                PlainButton(
                    text      = s.campaignBackToLocation,
                    modifier  = Modifier,
                    textColor = CrText,
                    fontSize  = 13.sp,
                    paddingH  = 20.dp,
                    paddingV  = 12.dp,
                    onClick   = onBackToLocation
                )
                if (onReviewGame != null) {
                    PlainButton(
                        text      = s.inspectGame,
                        modifier  = Modifier,
                        textColor = CrText,
                        fontSize  = 13.sp,
                        paddingH  = 20.dp,
                        paddingV  = 12.dp,
                        onClick   = onReviewGame
                    )
                }
                if (playerWon && onNextOpponent != null) {
                    PlainButton(
                        text      = s.campaignNextOpponent,
                        modifier  = Modifier,
                        textColor = CrGold,
                        fontSize  = 13.sp,
                        paddingH  = 20.dp,
                        paddingV  = 12.dp,
                        onClick   = onNextOpponent
                    )
                }
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

/**
 * Kulatý portrét soupeře: ilustrace z karty soupeře (cardArt – goblin_*, hory_*, bazina_*),
 * jinak jeho avatar; soupeři bez obrázku (starší emoji avatary) dostanou ikonu nepřítele.
 * Rámeček v barvě výsledku (zlatá výhra / červená prohra).
 */
@Composable
private fun OpponentPortrait(opponent: CampaignOpponent, ringColor: Color) {
    val resId = opponent.cardArt?.let { avatarDrawableRes(it) }
        ?: avatarDrawableRes(opponent.avatar)
        ?: avatarResId(opponent.avatar)
        ?: R.drawable.enemy_icon_1
    Image(
        painter            = painterResource(resId),
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        alignment          = Alignment.TopCenter,
        modifier           = Modifier
            .size(64.dp)
            .shadow(10.dp, CircleShape, ambientColor = ringColor, spotColor = ringColor)
            .clip(CircleShape)
            .background(Color.Black)
            .border(2.dp, ringColor, CircleShape)
    )
}
