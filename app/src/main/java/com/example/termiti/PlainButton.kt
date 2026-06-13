package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Herní tlačítko s texturou plain_button.png.
 * Nahrazuje plain clip+background+border boxy všude v UI.
 *
 * @param selected  Pokud true → plná opacity; false → snížená opacity (pro toggle chipy).
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
    paddingH: Dp = 10.dp,
    paddingV: Dp = 5.dp,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .alpha(when {
                !enabled -> 0.35f
                !selected -> 0.50f
                else -> 1f
            })
            .paint(painterResource(R.drawable.plain_button), contentScale = ContentScale.FillBounds)
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
    paddingH: Dp = 10.dp,
    paddingV: Dp = 5.dp,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .alpha(when { !enabled -> 0.35f; !selected -> 0.50f; else -> 1f })
            .paint(painterResource(R.drawable.plain_button), contentScale = ContentScale.FillBounds)
            .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(10.dp))
            Text(text, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
