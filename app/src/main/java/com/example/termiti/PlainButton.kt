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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Podklad z textury plain_button* bez deformace rohů.
 *
 * FillBounds roztáhne celou texturu do tvaru prvku – plain_button (297×107) na tlačítku
 * 190×28 dp má pak rohové ozdoby s nýty dvakrát širší než vyšší. Tady se textura kreslí
 * devítidílně: čtyři rohy v jednom měřítku (podle výšky prvku), natahují se jen rovné hrany
 * a střed. Když je prvek užší než dva rohy, měřítko se řídí šířkou a natahuje se svislý střed.
 *
 * Rozměry prvku určuje dál neviditelný paint() – tlačítka bez vlastní velikosti se odjakživa
 * měří podle textury, takže se rozložení obrazovek nemění, jen vykreslení.
 */
@Composable
fun Modifier.buttonTexture(
    @DrawableRes res: Int,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f
): Modifier {
    val bmp = ImageBitmap.imageResource(res)
    return paint(painterResource(res), contentScale = ContentScale.FillBounds, alpha = 0f).drawBehind {
        val w = size.width.roundToInt(); val h = size.height.roundToInt()
        if (w <= 0 || h <= 0) return@drawBehind
        // Roh = čtverec 40/107 výšky textury (ozdoba s nýtem sahá ~36 px od kraje)
        val c  = (bmp.height * 40f / 107f).roundToInt()
        val s  = minOf(size.height / bmp.height, size.width / (2f * c))
        val cd = (c * s).roundToInt().coerceAtMost(minOf(w, h) / 2)
        val srcX = intArrayOf(0, c, bmp.width - c, bmp.width)
        val srcY = intArrayOf(0, c, bmp.height - c, bmp.height)
        val dstX = intArrayOf(0, cd, w - cd, w)
        val dstY = intArrayOf(0, cd, h - cd, h)
        for (iy in 0..2) for (ix in 0..2) {
            val dw = dstX[ix + 1] - dstX[ix]; val dh = dstY[iy + 1] - dstY[iy]
            if (dw <= 0 || dh <= 0) continue
            drawImage(
                image       = bmp,
                srcOffset   = IntOffset(srcX[ix], srcY[iy]),
                srcSize     = IntSize(srcX[ix + 1] - srcX[ix], srcY[iy + 1] - srcY[iy]),
                dstOffset   = IntOffset(dstX[ix], dstY[iy]),
                dstSize     = IntSize(dw, dh),
                alpha       = alpha,
                colorFilter = colorFilter
            )
        }
    }
}

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
            .buttonTexture(buttonRes, colorFilter = cf)
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
            maxLines   = 1,
            style      = TextStyle(
                lineHeight     = fontSize,
                platformStyle  = PlatformTextStyle(includeFontPadding = false)
            )
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
            .buttonTexture(buttonRes, colorFilter = cf)
            .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(9.dp))
            Text(
                text      = text,
                color     = textColor,
                fontSize  = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                style     = TextStyle(
                    lineHeight    = fontSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}
