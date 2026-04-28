package com.example.termiti

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val TAU = (2f * PI).toFloat()

// ─── Hlavní composable ────────────────────────────────────────────────────────

/**
 * Realistický plamen pochodně kreslený čistě přes Canvas.
 *
 * Implementace:
 *  • 3 bezierové jazyky plamene s navzájem nesynchronizovanými animacemi
 *    (periody jsou nesoudělná prvočísla → nikdy se plně nesejdou)
 *  • Každý jazyk má nezávislé levé/pravé řídící body → organický tvar, ne zrcadlení
 *  • BlurMaskFilter pro měkký glow kolem základny (hardware-accelerated)
 *  • Vertikální gradient: průhledná špička → barevná základna
 *  • Jemné celkové kývání + záblesky jasu (flicker)
 *
 * @param size   Celková velikost canvasu (plamen + glow). Doporučeno 24–36 dp.
 * @param seed   Časový offset pro fazové rozlišení více plamenů vedle sebe.
 */
@Composable
fun TorchFlame(
    modifier : Modifier = Modifier,
    size     : Dp       = 28.dp,
    seed     : Float    = 0f
) {
    val inf = rememberInfiniteTransition(label = "flame_$seed")

    // Tři časové osy s nesoudělnými periodami (ms): 479, 557, 673
    // → fazové rozložení se nikdy přesně nezopakuje ve slyšitelném čase
    val t1 by inf.animateFloat(seed, TAU + seed,
        infiniteRepeatable(tween(479, easing = LinearEasing)), label = "t1")
    val t2 by inf.animateFloat(seed * 1.3f, TAU + seed * 1.3f,
        infiniteRepeatable(tween(557, easing = LinearEasing)), label = "t2")
    val t3 by inf.animateFloat(seed * 0.7f, TAU + seed * 0.7f,
        infiniteRepeatable(tween(673, easing = LinearEasing)), label = "t3")

    // Celkové kývání: pomalé, sinusové, 0.58 Hz
    val sway by inf.animateFloat(-1f, 1f,
        infiniteRepeatable(tween(1723, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway")

    // Záblesky jasu: 3.1 Hz, mírná amplituda aby nebylo křečovité
    val flicker by inf.animateFloat(0.74f, 1f,
        infiniteRepeatable(tween(322, easing = LinearEasing), RepeatMode.Reverse),
        label = "flicker")

    Canvas(modifier = modifier.size(size)) {
        val w     = this.size.width
        val h     = this.size.height
        val cx    = w / 2f
        // Základna plamene je 80 % výšky canvasu; horních 20 % je prostor pro glow
        val baseY = h * 0.80f
        val swPx  = sway * w * 0.06f   // max ±6 % šířky kývání

        // ── Glow – měkký amber kruh kolem základny ──────────────────────────
        drawFlameGlow(
            cx     = cx + swPx * 0.25f,
            cy     = baseY - h * 0.18f,
            radius = w * 0.50f,
            argb   = intArrayOf(180, 255, 120, 15),
            alpha  = flicker
        )

        // ── Jazyk 3: nejširší, nejtmavší, nejvyšší (červeno-oranžový) ────────
        drawFlameTongue(
            cx     = cx + swPx,
            baseY  = baseY,
            width  = w * 0.80f,
            height = h * 0.84f,
            t1     = t3,
            t2     = t3 * 1.19f,
            tipOff = swPx * 1.55f,
            brush  = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color(0x00FF2200),
                    0.28f to Color(0x99FF2200),
                    0.62f to Color(0xCCFF6600),
                    1.00f to Color(0xFFFFAA00)
                ),
                startY = baseY - h * 0.84f,
                endY   = baseY
            ),
            alpha = 0.80f * flicker
        )

        // ── Jazyk 2: střední, oranžovo-žlutý ─────────────────────────────────
        drawFlameTongue(
            cx     = cx + swPx * 0.72f,
            baseY  = baseY,
            width  = w * 0.55f,
            height = h * 0.68f,
            t1     = t2,
            t2     = t2 * 1.41f,
            tipOff = swPx * 1.10f,
            brush  = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color(0x00FFB300),
                    0.26f to Color(0xBBFF9900),
                    0.62f to Color(0xEEFFCC00),
                    1.00f to Color(0xFFFFEE66)
                ),
                startY = baseY - h * 0.68f,
                endY   = baseY
            ),
            alpha = 0.92f * flicker
        )

        // ── Jazyk 1: jádro, nejužší, žluto-bílý ──────────────────────────────
        drawFlameTongue(
            cx     = cx + swPx * 0.42f,
            baseY  = baseY,
            width  = w * 0.28f,
            height = h * 0.50f,
            t1     = t1,
            t2     = t1 * 1.57f,
            tipOff = swPx * 0.60f,
            brush  = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color(0x00FFFFFF),
                    0.20f to Color(0xCCFFFFCC),
                    0.58f to Color(0xFFFFFF99),
                    1.00f to Color(0xFFFFFFFF)
                ),
                startY = baseY - h * 0.50f,
                endY   = baseY
            ),
            alpha = flicker
        )

        // ── Žhavá tečka v základně (ember) ────────────────────────────────────
        drawFlameGlow(
            cx     = cx + swPx * 0.18f,
            cy     = baseY + h * 0.02f,
            radius = w * 0.10f,
            argb   = intArrayOf(230, 255, 255, 200),
            alpha  = 0.95f * flicker
        )
    }
}

// ─── Pomocné funkce kreslení ──────────────────────────────────────────────────

/**
 * Jeden jazykový tvar plamene jako kubická bezierová křivka.
 *
 * Levé a pravé řídící body jsou animovány různými fázemi [t1]/[t2] →
 * plamen není nikdy symetrický a nevypadá "počítačově".
 */
private fun DrawScope.drawFlameTongue(
    cx     : Float,
    baseY  : Float,
    width  : Float,
    height : Float,
    t1     : Float,
    t2     : Float,
    tipOff : Float,
    brush  : Brush,
    alpha  : Float = 1f
) {
    val hw = width / 2f

    // Levá strana — každý bod jiná fáze
    val lp1x = cx - hw * (0.80f + 0.20f * sin(t1))
    val lp1y = baseY - height * (0.26f + 0.07f * sin(t2))
    val lp2x = cx - hw * (0.36f + 0.24f * sin(t2 * 1.09f))
    val lp2y = baseY - height * (0.62f + 0.10f * sin(t1 * 0.88f))

    // Pravá strana — posunutá fáze, jiné koeficienty → asymetrie
    val rp2x = cx + hw * (0.36f + 0.24f * sin(t1 * 1.21f + 1.05f))
    val rp2y = baseY - height * (0.62f + 0.10f * sin(t2 * 1.07f + 0.55f))
    val rp1x = cx + hw * (0.80f + 0.20f * sin(t2 + 0.85f))
    val rp1y = baseY - height * (0.26f + 0.07f * sin(t1 + 1.28f))

    val path = Path().apply {
        moveTo(cx - hw * 0.40f, baseY)
        cubicTo(lp1x, lp1y, lp2x, lp2y, cx + tipOff, baseY - height)
        cubicTo(rp2x, rp2y, rp1x, rp1y, cx + hw * 0.40f, baseY)
        close()
    }

    drawPath(path = path, brush = brush, alpha = alpha)
}

/**
 * Měkký kruhový záblesk (glow) pomocí BlurMaskFilter.
 * Vyžaduje hardware acceleration (standard na všech moderních zařízeních).
 */
private fun DrawScope.drawFlameGlow(
    cx    : Float,
    cy    : Float,
    radius: Float,
    argb  : IntArray,   // [alpha, red, green, blue] 0-255
    alpha : Float = 1f
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            maskFilter  = android.graphics.BlurMaskFilter(
                radius * 0.85f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            color = android.graphics.Color.argb(
                (argb[0] * alpha).toInt().coerceIn(0, 255),
                argb[1], argb[2], argb[3]
            )
        }
        canvas.nativeCanvas.drawCircle(cx, cy, radius * 0.52f, paint)
    }
}
