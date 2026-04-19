package com.example.termiti

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════════
//  ArtDefaults.kt
//  Globální nastavení zobrazení ilustrací u textured karet.
//
//  Změnou konstant níže upravíš VŠECHNY karty najednou.
//  Per-karta doladění se nastavuje přímo v Card() přes artBiasX/Y a artScale.
// ═══════════════════════════════════════════════════════════════════════════════

object ArtDefaults {

    /**
     * Základní zoom ilustrace platný pro všechny karty.
     *   1.2f = Ideální kompromis, aby se do 70dp výšky vešla hlava i stůl.
     */
    const val SCALE: Float = 1.1f

    /**
     * Globální posun ilustrace vodorovně.
     */
    const val BIAS_X: Float = 0.0f

    /**
     * Globální posun ilustrace svisle.
     *   0.25f: Posune obraz nahoru (schová strop), hlava bude u vršku a stůl u spodku.
     */
    const val BIAS_Y: Float = 0.25f
}

/**
 * Vrátí ID prostředku pro překryvný obrázek rarity.
 */
@Composable
fun rarityOverlayResource(rarity: Rarity): Int {
    val context = LocalContext.current
    val name = when (rarity) {
        Rarity.COMMON -> "rarity_common"
        Rarity.RARE -> "rarity_rare"
        Rarity.EPIC -> "rarity_epic"
        Rarity.LEGENDARY -> "rarity_legendary"
    }
    return remember(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
}

// ─── Pomocné funkce (dostupné z GameScreen i DeckBuilderScreen) ────────────────

/**
 * Modifier pro ilustraci karty: aplikuje globální scale * per-karta artScale přes graphicsLayer.
 *
 * POZOR: Nepoužívej Modifier.scale() — ten škáluje composable až po layoutu,
 * takže BiasAlignment v parametru alignment u Image nepočítá se správnou velikostí.
 * graphicsLayer škáluje obsah uvnitř bounds, takže ContentScale.Crop + alignment fungují správně.
 */
fun artModifier(card: Card): Modifier =
    Modifier
        .fillMaxSize()
        .graphicsLayer {
            val s = ArtDefaults.SCALE * card.artScale
            scaleX = s
            scaleY = s

            // Sladění středu transformace s biasem zarovnání.
            // Zajišťuje, že při zvětšování (zoomu) "neutíká" vycentrovaný bod (drift).
            // Pokud je karta zarovnaná k vršku, zoom probíhá směrem od vršku.
            val bx = ArtDefaults.BIAS_X + card.artBiasX
            val by = ArtDefaults.BIAS_Y + card.artBiasY
            transformOrigin = TransformOrigin(
                pivotFractionX = ((bx + 1f) / 2f).coerceIn(0f, 1f),
                pivotFractionY = ((by + 1f) / 2f).coerceIn(0f, 1f)
            )
        }

/**
 * BiasAlignment pro ilustraci karty.
 * Globální bias + per-karta korekce. Hodnoty mimo rozsah <-1, 1> posunou obrázek "mimo" hranice karty.
 */
fun artAlignment(card: Card): BiasAlignment =
    BiasAlignment(
        horizontalBias = ArtDefaults.BIAS_X + card.artBiasX,
        verticalBias   = ArtDefaults.BIAS_Y + card.artBiasY
    )

// ─── Arc Card Name ─────────────────────────────────────────────────────────────
/**
 * Vykreslí název karty podél kruhového oblouku pomocí nativeCanvas.drawTextOnPath.
 *
 * Sdílené mezi GameScreen a DeckBuilderScreen.
 *
 * @param arcRadiusDp  Poloměr oblouku v dp. Větší = plošší křivka.
 *                     Kladný → text se klene nahoru uprostřed (∩).
 *                     Záporný → text se prohlubuje dolů uprostřed (∪).
 * @param baselineFrac Relativní poloha základní linie v plátně (0 = vršek, 1 = dno).
 */
@Composable
fun ArcCardName(
    name         : String,
    modifier     : Modifier = Modifier,
    fontSizeSp   : Float    = 8f,
    arcRadiusDp  : Float    = 350f,
    baselineFrac : Float    = 0.78f
) {
    val sign      = if (arcRadiusDp >= 0f) 1f else -1f
    val absRadius = kotlin.math.abs(arcRadiusDp)

    Canvas(modifier = modifier) {
        val fontPx   = fontSizeSp.sp.toPx()
        val radiusPx = absRadius.dp.toPx()
        val w        = size.width
        val h        = size.height

        // Střed kružnice: sign > 0 → pod plátnem (klene nahoru),
        //                 sign < 0 → nad plátnem (prohlubuje dolů)
        val baseY      = h * baselineFrac
        val cx         = w / 2f
        val cy         = sign * radiusPx + baseY

        val halfSpan   = (w / 2f / radiusPx).toDouble().coerceIn(-1.0, 1.0)
        val halfDeg    = Math.toDegrees(Math.asin(halfSpan)).toFloat()

        val midAngle   = if (sign > 0f) 270f else 90f
        val startAngle = if (sign > 0f) midAngle - halfDeg else midAngle + halfDeg
        val sweepAngle = if (sign > 0f) halfDeg * 2f       else -(halfDeg * 2f)

        val oval = android.graphics.RectF(
            cx - radiusPx, cy - radiusPx,
            cx + radiusPx, cy + radiusPx
        )
        val path = android.graphics.Path().also { it.addArc(oval, startAngle, sweepAngle) }

        drawIntoCanvas { c ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                typeface    = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
                textSize  = fontPx
                textAlign = android.graphics.Paint.Align.CENTER
            }
            // Obrys (stroke)
            paint.style       = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = fontPx * 0.28f
            paint.strokeJoin  = android.graphics.Paint.Join.ROUND
            paint.strokeCap   = android.graphics.Paint.Cap.ROUND
            paint.color       = android.graphics.Color.BLACK
            c.nativeCanvas.drawTextOnPath(name, path, 0f, 0f, paint)
            // Výplň (fill)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            c.nativeCanvas.drawTextOnPath(name, path, 0f, 0f, paint)
        }
    }
}
