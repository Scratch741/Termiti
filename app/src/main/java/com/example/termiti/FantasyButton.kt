package com.example.termiti

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FbBg = Brush.horizontalGradient(
    listOf(
        Color(0xFF1A1410),
        Color(0xFF2A1F18),
        Color(0xFF1A1410)
    )
)
private val FbBgPressed = Brush.horizontalGradient(
    listOf(
        Color(0xFF120E0A),
        Color(0xFF1E1610),
        Color(0xFF120E0A)
    )
)
private val FbBorder    = Color(0xFF8B6A3E)
private val FbHighlight = Color(0xFFB8965A)
private val FbText      = Color(0xFFEAD9B0)
private val FbMuted     = Color(0xFF9A8870)

/**
 * Fantasy styl tlačítka – tmavý kovový podklad, zlatý rám, rohové ornamenty,
 * top highlight, press animace (scale + ztmavení).
 *
 * @param text     Hlavní popisek tlačítka (velká písmena doporučena)
 * @param subtitle Volitelný menší popis pod textem
 * @param onClick  Akce po kliknutí
 */
@Composable
fun FantasyButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue    = if (isPressed) 0.97f else 1f,
        animationSpec  = tween(durationMillis = 80),
        label          = "fb_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) FbBgPressed else FbBg)
            .border(1.5.dp, FbBorder, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication        = null
            ) {
                SoundManager.playMenuTap()
                onClick()
            }
            .padding(vertical = 14.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text  = text,
                color = FbText.copy(alpha = if (isPressed) 0.80f else 1f),
                style = TextStyle(
                    fontSize      = 16.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
            if (subtitle != null) {
                // ── Oddělovač mezi názvem a podtextem ────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(FbHighlight.copy(alpha = if (isPressed) 0.15f else 0.30f))
                )
                Text(
                    text  = subtitle,
                    color = FbMuted.copy(alpha = if (isPressed) 0.65f else 0.85f),
                    style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp)
                )
            }
        }
    }
}
