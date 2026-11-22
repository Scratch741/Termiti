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
 * Pozadí panelu z textury rámu (dialog_frame) bez deformace a bez ořezu rohů.
 *
 * Devítidílně jako [buttonTexture], ale rohy mají pevnou velikost [corner] nezávisle na
 * velikosti panelu a výřez rohu v textuře je [srcCornerPx] px – rám tak vypadá stejně na
 * malém i velkém panelu (ContentScale.Crop dřív u jiných poměrů stran usekl rohové ozdoby).
 */
@Composable
fun Modifier.frameTexture(
    @DrawableRes res: Int,
    srcCornerPx: Int,
    corner: Dp
): Modifier {
    val bmp = ImageBitmap.imageResource(res)
    return drawBehind {
        val w = size.width.roundToInt(); val h = size.height.roundToInt()
        if (w <= 0 || h <= 0) return@drawBehind
        val c  = srcCornerPx.coerceAtMost(minOf(bmp.width, bmp.height) / 2)
        val cd = corner.toPx().roundToInt().coerceAtMost(minOf(w, h) / 2)
        val srcX = intArrayOf(0, c, bmp.width - c, bmp.width)
        val srcY = intArrayOf(0, c, bmp.height - c, bmp.height)
        val dstX = intArrayOf(0, cd, w - cd, w)
        val dstY = intArrayOf(0, cd, h - cd, h)
        for (iy in 0..2) for (ix in 0..2) {
            val dw = dstX[ix + 1] - dstX[ix]; val dh = dstY[iy + 1] - dstY[iy]
            if (dw <= 0 || dh <= 0) continue
            drawImage(
                image     = bmp,
                srcOffset = IntOffset(srcX[ix], srcY[iy]),
                srcSize   = IntSize(srcX[ix + 1] - srcX[ix], srcY[iy + 1] - srcY[iy]),
                dstOffset = IntOffset(dstX[ix], dstY[iy]),
                dstSize   = IntSize(dw, dh)
            )
        }
    }
}

/**
 * Rám jako [frameTexture], ale s vlastní výplní uprostřed: [fillRes] se kreslí POD rám
 * ořezem na poměr plochy (ContentScale.Crop), takže se jeho textura neroztahuje jako střed
 * devítidílného rámu. [frameRes] musí mít průhledný střed (dialog_frame_open).
 * [fillInset] = odsazení výplně od okraje panelu jako zlomek rohu; výplň musí sahat pod
 * vnitřní lem rámu (u dialog_frame začíná otvor ~0,29 rohu shora a ~0,48 rohu z boku).
 */
@Composable
fun Modifier.frameTextureFilled(
    @DrawableRes frameRes: Int,
    @DrawableRes fillRes: Int,
    srcCornerPx: Int,
    corner: Dp,
    fillInset: Float = 0.22f
): Modifier {
    val fill = ImageBitmap.imageResource(fillRes)
    return drawBehind {
        val ins = corner.toPx() * fillInset
        val dw = size.width - 2 * ins; val dh = size.height - 2 * ins
        if (dw <= 1f || dh <= 1f) return@drawBehind
        val k  = maxOf(dw / fill.width, dh / fill.height)
        val sw = (dw / k).roundToInt().coerceIn(1, fill.width)
        val sh = (dh / k).roundToInt().coerceIn(1, fill.height)
        drawImage(
            image     = fill,
            srcOffset = IntOffset((fill.width - sw) / 2, (fill.height - sh) / 2),
            srcSize   = IntSize(sw, sh),
            dstOffset = IntOffset(ins.roundToInt(), ins.roundToInt()),
            dstSize   = IntSize(dw.roundToInt(), dh.roundToInt())
        )
    }.frameTexture(frameRes, srcCornerPx, corner)
}

/**
 * Společný rám dialogů: tmavý kámen + pergamen, začernalý bronz s gotickými rohy
 * (návrh UI 2026-11-21). Rohy 40 dp → boční lem ~22 dp, horní ~14 dp; obsah dialogu
 * proto potřebuje odsazení aspoň ~22 dp do stran a ~16 dp nahoru/dolů.
 */
@Composable
fun Modifier.dialogFrame(corner: Dp = 40.dp): Modifier =
    frameTexture(R.drawable.dialog_frame, srcCornerPx = 170, corner = corner)

/**
 * Nadpis dialogu na kamenné desce s gotickými konci (dialog_title_plaque). Deska se natahuje
 * jen uprostřed (konce mají pevnou velikost), takže pojme krátký i dlouhý nadpis.
 */
@Composable
internal fun DialogTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    gradient: List<Color> = TitleGold
) {
    // Třídílně: levý a pravý konec (165 px textury) se jen škálují podle výšky desky,
    // natahuje se pouze rovný střed – konce s ozdobami a oválným lícem zůstanou celé.
    val bmp = ImageBitmap.imageResource(R.drawable.dialog_title_plaque)
    Box(
        modifier = modifier
            .widthIn(min = 200.dp)
            .height(52.dp)
            .drawBehind {
                val w = size.width.roundToInt(); val h = size.height.roundToInt()
                if (w <= 0 || h <= 0) return@drawBehind
                val cap  = 165
                val capD = (cap * h / bmp.height.toFloat()).roundToInt().coerceAtMost(w / 2)
                val srcX = intArrayOf(0, cap, bmp.width - cap, bmp.width)
                val dstX = intArrayOf(0, capD, w - capD, w)
                for (i in 0..2) {
                    val dw = dstX[i + 1] - dstX[i]
                    if (dw <= 0) continue
                    drawImage(
                        image     = bmp,
                        srcOffset = IntOffset(srcX[i], 0),
                        srcSize   = IntSize(srcX[i + 1] - srcX[i], bmp.height),
                        dstOffset = IntOffset(dstX[i], 0),
                        dstSize   = IntSize(dw, h)
                    )
                }
            }
            .padding(horizontal = 50.dp),
        contentAlignment = Alignment.Center
    ) {
        CampaignTitle(text, fontSize = fontSize, gradient = gradient, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Ztlumený text na pergamenu rámu dialogu (TextMuted je tam málo čitelný). */
internal val DialogTextMuted = Color(0xFFC9B48A)

/** Text na bronzovém tlačítku (button_bronze) – tmavý, na zlatém podkladu jinak nejde číst. */
internal val BronzeButtonText = Color(0xFF140B02)

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
    /** Průhlednost vypnutého tlačítka (na světlém pozadí ho 35 % schová úplně). */
    disabledAlpha: Float = 0.35f,
    onClick: () -> Unit = {}
) {
    val alpha = when {
        !enabled          -> disabledAlpha
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
