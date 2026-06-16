package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Herní tlačítko s texturou plain_button.png (nebo plain_button_longer.png pro širší variantu).
 *
 * @param selected     false → 50 % alpha (toggle stav). Ignorováno pokud outlineColor != null.
 * @param outlineColor Pokud nastaveno: tlačítko je vždy plně opaque; při selected=true se
 *                     vykreslí oranžový (nebo jiný) obrys. selected=false → jen bez obrysu.
 * @param buttonRes    Textura; výchozí plain_button, pro delší boxy plain_button_longer.
 */
@Composable
fun PlainButton(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFEDE0C4),
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    enabled: Boolean = true,
    selected: Boolean = true,
    outlineColor: Color? = null,
    paddingH: Dp = 10.dp,
    paddingV: Dp = 5.dp,
    @DrawableRes buttonRes: Int = R.drawable.plain_button,
    lighten: Float = 0f,
    onClick: () -> Unit = {}
) {
    val alpha = when {
        !enabled          -> 0.35f
        outlineColor != null -> 1f       // režim obrysu → vždy plná opacita
        !selected         -> 0.50f
        else              -> 1f
    }
    val outlineMod = if (outlineColor != null && selected && enabled) {
        Modifier.drawBehind {
            drawRoundRect(
                color        = outlineColor,
                style        = Stroke(width = 1.5.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    } else Modifier

    val cf = if (lighten > 0f) ColorFilter.tint(Color.White.copy(alpha = lighten), BlendMode.Screen) else null
    Box(
        modifier = modifier
            .alpha(alpha)
            .then(outlineMod)
            .paint(painterResource(buttonRes), contentScale = ContentScale.FillBounds, colorFilter = cf)
            .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = text,
            color      = textColor,
            fontSize   = fontSize,
            fontWeight = fontWeight,
            textAlign  = TextAlign.Center,
            maxLines   = 1
        )
    }
}

/** Varianta s ikonou vlevo od textu. */
@Composable
fun PlainButtonWithIcon(
    text: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFEDE0C4),
    fontSize: TextUnit = 11.sp,
    enabled: Boolean = true,
    selected: Boolean = true,
    outlineColor: Color? = null,
    paddingH: Dp = 10.dp,
    paddingV: Dp = 5.dp,
    @DrawableRes buttonRes: Int = R.drawable.plain_button,
    lighten: Float = 0f,
    onClick: () -> Unit = {}
) {
    val alpha = when {
        !enabled             -> 0.35f
        outlineColor != null -> 1f
        !selected            -> 0.50f
        else                 -> 1f
    }
    val outlineMod = if (outlineColor != null && selected && enabled) {
        Modifier.drawBehind {
            drawRoundRect(
                color        = outlineColor,
                style        = Stroke(width = 1.5.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    } else Modifier

    val cf = if (lighten > 0f) ColorFilter.tint(Color.White.copy(alpha = lighten), BlendMode.Screen) else null
    Box(
        modifier = modifier
            .alpha(alpha)
            .then(outlineMod)
            .paint(painterResource(buttonRes), contentScale = ContentScale.FillBounds, colorFilter = cf)
            .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(9.dp))
            Text(text, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
