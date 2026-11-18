package com.example.termiti

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * „Zlatá" karta – úroveň 1 (zkušebně, karta "T48").
 *
 * Vše se kreslí kódem nad stávajícím artem (rám zůstává v barvě zdroje):
 * přejíždějící lesk po rámu, pulzující zlatá záře kolem karty a stoupající
 * jiskry v oblasti ilustrace. Jeden nekonečný časovač na kartu; hodnota se
 * čte jen ve fázi kreslení, takže animace nespouští rekompozici.
 */
@Stable
class GoldenFx(val time: State<Float>)

private const val GOLD_CYCLE_MS = 6000

@Composable
fun rememberGoldenFx(): GoldenFx {
    val tr = rememberInfiniteTransition(label = "golden")
    val t = tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GOLD_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "goldenTime"
    )
    return remember(t) { GoldenFx(t) }
}

val GoldLight = Color(0xFFFFE9A8)
val GoldMid   = Color(0xFFF2C14E)
val GoldDeep  = Color(0xFFB8862B)

/** Pulzující zlatá záře vně karty (před clipem). */
fun Modifier.goldenGlow(fx: GoldenFx, scale: Float = 1f): Modifier = drawBehind {
    val t = fx.time.value
    val pulse = 0.55f + 0.45f * sin(2f * PI.toFloat() * t * 2f)
    val cr = CornerRadius(9.dp.toPx() * scale)
    repeat(4) { i ->
        val spread = (i + 1) * 2.5f * density * scale
        drawRoundRect(
            color = GoldMid.copy(alpha = pulse * 0.13f / (i + 1)),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = cr
        )
    }
}

/**
 * Lesk přejíždějící po rámu: šikmý světlý pruh se kreslí přes obsah s
 * BlendMode.SrcAtop, takže svítí jen tam, kde má rám pixely (ne přes ilustraci).
 */
fun Modifier.goldenShine(fx: GoldenFx): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // 2 průchody za cyklus; pruh jede v první polovině každého průchodu
        val s = (fx.time.value * 2f) % 1f
        val p = s / 0.5f
        if (p >= 1f) return@drawWithContent
        val w = size.width; val h = size.height
        // Směr pohybu: šikmo z levého horního rohu do pravého dolního.
        // Pruh je kolmý na tento směr; jeho střed jede po průmětu celé karty
        // od -band do (průmět pravého dolního rohu + band), takže začne i skončí
        // celý mimo kartu – nic se neusekne a dojede až dolů.
        val len = kotlin.math.sqrt(w * w + h * h)
        val dx = w / len; val dy = h / len
        val band = len * 0.12f
        val far = w * dx + h * dy
        val e = p * p * (3f - 2f * p)                       // plynulý rozjezd i dojezd
        val c = -band + (far + band * 2f) * e
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to GoldLight.copy(alpha = 0.32f),
                1f to Color.Transparent,
                start = Offset(dx * (c - band), dy * (c - band)),
                end = Offset(dx * (c + band), dy * (c + band))
            ),
            blendMode = BlendMode.SrcAtop
        )
    }

/** Jiskry stoupající v oblasti ilustrace. */
@Composable
fun GoldenSparks(fx: GoldenFx, modifier: Modifier = Modifier, count: Int = 9, scale: Float = 1f) {
    // Pevná „náhoda" na jiskru: x, rychlost (celé násobky cyklu = bez skoku), fáze, velikost
    val seeds = remember(count) {
        List(count) { i ->
            val r = java.util.Random(4807L + i * 131L)
            floatArrayOf(
                0.12f + r.nextFloat() * 0.76f,       // x
                (1 + r.nextInt(2)).toFloat(),        // průchodů za cyklus
                r.nextFloat(),                       // fáze
                0.45f + r.nextFloat() * 0.5f,        // velikost
                (r.nextFloat() - 0.5f) * 0.10f       // drift do strany
            )
        }
    }
    Canvas(modifier) {
        val t = fx.time.value
        val unit = 1.dp.toPx() * scale
        for (sd in seeds) {
            val life = (t * sd[1] + sd[2]) % 1f
            val y = size.height * (0.95f - 0.85f * life)
            val x = size.width * (sd[0] + sd[4] * life) +
                    sin(life * 2f * PI.toFloat() * 2f + sd[2] * 6f) * 2f * unit
            val a = when {
                life < 0.15f -> life / 0.15f
                life > 0.70f -> (1f - life) / 0.30f
                else -> 1f
            }.coerceIn(0f, 1f)
            val r = sd[3] * unit
            drawCircle(GoldMid.copy(alpha = a * 0.18f), radius = r * 2.4f, center = Offset(x, y))
            drawCircle(GoldLight.copy(alpha = a * 0.95f), radius = r, center = Offset(x, y))
        }
    }
}

// ── Úroveň 2: rozvrstvený obrázek ─────────────────────────────────────────────

/**
 * Art rozdělený na vrstvy: pozadí bez postav ([bgResId]) a postavy s průhledností
 * ([fgResId], BiRefNet). Oba obrázky mají stejný čtvercový rozměr jako původní art.
 * [glowX]/[glowY] = střed magické záře ve zlomcích obrázku (0..1), [glowColor] ARGB.
 */
data class ArtLayers(
    val bgResId: Int,
    val fgResId: Int,
    val glowX: Float = -1f,
    val glowY: Float = -1f,
    val glowRadius: Float = 0.10f,
    val glowColor: Long = 0xFFB066FF
)

/**
 * Animovaná ilustrace z vrstev: postavy se lehounce vznášejí nahoru a dolů,
 * pozadí se nepatrně pohne opačně (hloubka), mezi vrstvami pulzuje magická záře. Použití místo Image v oblasti ilustrace karty.
 */
@Composable
fun GoldenLayeredArt(card: Card, layers: ArtLayers, fx: GoldenFx, alpha: Float = 1f) {
    val align = artAlignment(card)
    Box(artModifier(card)) {
        Image(
            painter = androidx.compose.ui.res.painterResource(layers.bgResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val a = 2f * PI.toFloat() * fx.time.value
                scaleX = 1.07f; scaleY = 1.07f
                // jen lehký protipohyb k vznášení postav (hloubka), bez kroužení do stran
                translationY = -sin(a * 2f) * size.height * 0.006f
            },
            contentScale = ContentScale.Crop,
            alignment = align,
            alpha = alpha
        )
        if (layers.glowX >= 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val s = maxOf(size.width, size.height)
                val ox = (size.width - s) * (align.horizontalBias + 1f) / 2f
                val oy = (size.height - s) * (align.verticalBias + 1f) / 2f
                val c = Offset(ox + s * layers.glowX, oy + s * layers.glowY)
                val pulse = 0.6f + 0.4f * sin(2f * PI.toFloat() * fx.time.value * 3f)
                val col = Color(layers.glowColor)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to col.copy(alpha = 0.75f * pulse * alpha),
                        0.45f to col.copy(alpha = 0.30f * pulse * alpha),
                        1f to Color.Transparent,
                        center = c, radius = s * layers.glowRadius * (0.9f + 0.2f * pulse)
                    ),
                    radius = s * layers.glowRadius * 1.1f, center = c,
                    blendMode = BlendMode.Plus
                )
            }
        }
        Image(
            painter = androidx.compose.ui.res.painterResource(layers.fgResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val a = 2f * PI.toFloat() * fx.time.value
                // lehounké vznášení nahoru a dolů (perioda 3 s)
                translationY = sin(a * 2f) * size.height * 0.014f
            },
            contentScale = ContentScale.Crop,
            alignment = align,
            alpha = alpha
        )
    }
}
