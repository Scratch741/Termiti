package com.example.termiti

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Animované pozadí menu s Temným mágem (2026-11-23).
 *
 * - menu_bg_mage_clean.jpg: pozadí menu_bg_mage bez osmi levitujících krystalů
 *   (místa krystalů doplněná přes comfy_inpaint).
 * - menu_bg_crystals.png: krystaly vyříznuté z originálu (atlas 628×394 od bodu 557,86 pozadí);
 *   každý se pomalu vznáší nahoru a dolů a lehce kývá, každý v jiném rytmu.
 * - Oheň v dlaních: mihotavá záře a jiskry stoupající z dlaní (aditivně).
 * - Kouř: chuchvalce z textury menu_smoke (fraktální šum), světle tyrkysové, stoupají vedle rukou.
 * - Prsty: oblast rukou se jemně prohýbá (drawBitmapMesh), konečky se nepatrně hýbou.
 * - Rudé oči pod kápí: zhruba jednou za 15 s se na ~3 s rozžhnou a pomalu mrknou.
 * - Čas je sdílený pro celou aplikaci (rememberMenuTime), takže při přepnutí menu animace pokračují.
 *
 * Souřadnice jsou v pixelech pozadí (1701×925); přepočet odpovídá ContentScale.Crop,
 * takže pochodně a nápis, které obrazovky počítají ze stejného ořezu, sedí.
 */

private const val BG_W = 1701f
private const val BG_H = 925f
private const val ATLAS_X = 557
private const val ATLAS_Y = 86

/** Krystaly: x, y, šířka, výška v pixelech pozadí. */
private val CRYSTALS = intArrayOf(
    582, 105, 35, 90,
    558, 195, 26, 65,
    557, 303, 33, 89,
    577, 424, 24, 55,
    1100, 86, 35, 68,
    1140, 185, 27, 74,
    1145, 302, 40, 97,
    1149, 419, 26, 61,
)

/** Dlaně s ohněm (střed, v pixelech pozadí). Kouř stoupá vně rukou (směr ±1). */
private val HANDS = arrayOf(Offset(462f, 660f), Offset(1248f, 670f))
private val SMOKE_DIR = floatArrayOf(-1f, 1f)

private val EmberHot  = Color(0xFFFFE6A0)
private val EmberCool = Color(0xFFFF5A14)
private val FireGlow  = Color(0xFFFF8A2A)
private val SmokeTeal = Color(0xFFA6E6DA)
private val EyeRed    = Color(0xFFFF3A1C)
private val EyeHot    = Color(0xFFFFC890)

/** Oči pod kápí (pixely pozadí; obličej je tmavý ovál se středem x = 870) a jejich sklon (mračí se). */
private val EYES = arrayOf(Offset(853f, 331f), Offset(887f, 331f))
private val EYE_TILT = floatArrayOf(12f, -12f)
private const val EYE_CYCLE = 15f

/** Deterministický „náhodný“ hash 0..1 (žádné alokace ani Random v kreslení). */
private fun hash01(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
}

/** Načte drawable v původní velikosti (res/drawable se jinak zvětšuje podle hustoty displeje). */
internal fun decodeRaw(res: Resources, @DrawableRes id: Int): ImageBitmap =
    BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false }).asImageBitmap()

/** Obrázky pozadí se načtou jednou za běh aplikace – přepnutí menu je nenačítá znovu. */
private object MenuBgImages {
    var bg: ImageBitmap? = null
    var atlas: ImageBitmap? = null
    var smoke: ImageBitmap? = null
    /** Výřezy rukou z čistého pozadí pro jemné prohýbání prstů (drawBitmapMesh). */
    var hands: Array<android.graphics.Bitmap>? = null
}

/** Oblasti rukou v pixelech pozadí: x, y, šířka, výška (prsty a dlaň, okraje se nehýbou). */
private val HAND_RECTS = intArrayOf(370, 555, 170, 160, 1170, 565, 170, 160)
private const val MESH = 10
private val handVerts = FloatArray((MESH + 1) * (MESH + 1) * 2)
private val meshPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)

/**
 * Společné hodiny animací menu (pozadí, pochodně). Počátek je první snímek po spuštění
 * aplikace, takže animace při přepnutí obrazovky (menu → Hrát → lobby…) plynule pokračují
 * a nezačínají znovu. Čas snímku je z Choreographeru, na všech obrazovkách stejná osa.
 */
private var menuClockOrigin = -1L

@Composable
internal fun rememberMenuTime(): State<Float> {
    // Hned od prvního snímku nové obrazovky aktuální čas (osa Choreographeru = System.nanoTime);
    // s nulou by první snímek po přepnutí menu nakreslil krystaly, jiskry i pochodně ve stavu
    // z času 0 a pak skočily zpět – to bylo vidět jako probliknutí.
    val time = remember {
        mutableFloatStateOf(if (menuClockOrigin < 0) 0f else (System.nanoTime() - menuClockOrigin) / 1_000_000_000f)
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { n ->
            if (menuClockOrigin < 0) menuClockOrigin = n
            time.floatValue = (n - menuClockOrigin) / 1_000_000_000f
        }
    }
    return time
}

@Composable
fun MenuBackground(modifier: Modifier = Modifier) {
    val res   = LocalContext.current.resources
    val bg    = remember { MenuBgImages.bg    ?: decodeRaw(res, R.drawable.menu_bg_mage_clean).also { MenuBgImages.bg = it } }
    val atlas = remember { MenuBgImages.atlas ?: decodeRaw(res, R.drawable.menu_bg_crystals).also { MenuBgImages.atlas = it } }
    val smoke = remember { MenuBgImages.smoke ?: decodeRaw(res, R.drawable.menu_smoke).also { MenuBgImages.smoke = it } }
    val hands = remember {
        MenuBgImages.hands ?: Array(2) { h ->
            android.graphics.Bitmap.createBitmap(bg.asAndroidBitmap(), HAND_RECTS[h * 4], HAND_RECTS[h * 4 + 1], HAND_RECTS[h * 4 + 2], HAND_RECTS[h * 4 + 3])
        }.also { MenuBgImages.hands = it }
    }

    // Čas v sekundách (sdílený); čte se jen při kreslení → animace nerekomponuje obsah menu
    val time by rememberMenuTime()

    Canvas(modifier.fillMaxSize()) {
        val t  = time
        val s  = max(size.width / BG_W, size.height / BG_H)
        val ox = (size.width - BG_W * s) / 2f
        val oy = (size.height - BG_H * s) / 2f
        fun px(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)

        drawImage(
            bg,
            dstOffset     = IntOffset(ox.roundToInt(), oy.roundToInt()),
            dstSize       = IntSize((BG_W * s).roundToInt(), (BG_H * s).roundToInt()),
            filterQuality = FilterQuality.Medium
        )

        // ── Prsty: oblast každé ruky se nepatrně prohýbá (síť 10×10, okraje stojí, uprostřed
        //    nejvýš ~2 px pozadí; fáze se mění napříč rukou, takže se každý prst hne trochu jinak)
        drawIntoCanvas { cv ->
            for (h in 0 until 2) {
                val rx = HAND_RECTS[h * 4]; val ry = HAND_RECTS[h * 4 + 1]
                val rw = HAND_RECTS[h * 4 + 2]; val rh = HAND_RECTS[h * 4 + 3]
                var vi = 0
                for (j in 0..MESH) for (i in 0..MESH) {
                    val u = i / MESH.toFloat(); val v = j / MESH.toFloat()
                    val w = sin(PI.toFloat() * u) * sin(PI.toFloat() * v)
                    val dx = 2.0f * w * sin(t * 1.9f + u * 5.5f + h * 1.3f)
                    val dy = 1.4f * w * sin(t * 1.55f + u * 4.3f + 1f + h * 0.7f)
                    handVerts[vi++] = ox + (rx + u * rw + dx) * s
                    handVerts[vi++] = oy + (ry + v * rh + dy) * s
                }
                cv.nativeCanvas.drawBitmapMesh(hands[h], MESH, MESH, handVerts, 0, null, 0, meshPaint)
            }
        }

        // ── Kouř vedle rukou: chuchvalce z textury (menu_smoke 2×256 px, bílá s průhledností),
        //    světle tyrkysové, stoupají, rozšiřují se, pomalu se točí a mizí ─────────
        val tint = ColorFilter.tint(SmokeTeal, BlendMode.Modulate)
        for (h in HANDS.indices) {
            val hand = HANDS[h]
            for (k in 0 until 7) {
                val seed = 1000 + h * 50 + k
                val life = 7f + hash01(seed) * 5f
                val u    = ((t + hash01(seed + 1) * life) % life) / life
                val cx   = hand.x + SMOKE_DIR[h] * (55f + hash01(seed + 2) * 70f + 90f * u) + sin(t * 0.35f + k) * 10f
                val cy   = hand.y + 90f - u * 330f
                val sz   = (150f + hash01(seed + 3) * 80f) * (0.6f + 0.8f * u) * s
                val a    = 0.42f * sin(PI.toFloat() * u).pow(1.2f)
                val rot  = hash01(seed + 4) * 360f + SMOKE_DIR[h] * 25f * u
                val c    = px(cx, cy)
                val isz  = sz.roundToInt()
                translate(c.x - sz / 2f, c.y - sz / 2f) {
                    rotate(rot, pivot = Offset(sz / 2f, sz / 2f)) {
                        drawImage(
                            smoke,
                            srcOffset   = IntOffset((k % 2) * 256, 0),
                            srcSize     = IntSize(256, 256),
                            dstSize     = IntSize(isz, isz),
                            alpha       = a,
                            colorFilter = tint
                        )
                    }
                }
            }
        }

        // ── Levitující krystaly ──────────────────────────────────────────────
        for (i in 0 until CRYSTALS.size / 4) {
            val x = CRYSTALS[i * 4]; val y = CRYSTALS[i * 4 + 1]
            val w = CRYSTALS[i * 4 + 2]; val h = CRYSTALS[i * 4 + 3]
            val period = 3.6f + (i % 4) * 0.7f
            val ph     = i * 1.9f
            val dy     = sin(2f * PI.toFloat() * t / period + ph) * 5f * s
            val rot    = sin(2f * PI.toFloat() * t / (period * 1.37f) + ph * 0.7f) * 3f
            val dst    = px(x.toFloat(), y.toFloat())
            val dw = (w * s).roundToInt(); val dh = (h * s).roundToInt()
            translate(top = dy) {
                rotate(rot, pivot = Offset(dst.x + dw / 2f, dst.y + dh / 2f)) {
                    drawImage(
                        atlas,
                        srcOffset     = IntOffset(x - ATLAS_X, y - ATLAS_Y),
                        srcSize       = IntSize(w, h),
                        dstOffset     = IntOffset(dst.x.roundToInt(), dst.y.roundToInt()),
                        dstSize       = IntSize(dw, dh),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
        }

        // ── Rudé oči pod kápí: zhruba jednou za 15 s (pokaždé jindy v cyklu) se rozžhnou,
        //    chvíli svítí, uprostřed mág mrkne, pak pohasnou ─────────────────────────
        val n     = floor(t / EYE_CYCLE).toInt()
        val et    = t - (n * EYE_CYCLE + 2f + hash01(5000 + n) * 9f)
        if (et in 0f..3.1f) {
            fun smooth(x: Float) = x * x * (3f - 2f * x)
            var k = when {
                et < 0.5f -> smooth(et / 0.5f)
                et > 2.4f -> 1f - smooth((et - 2.4f) / 0.7f)
                else      -> 1f
            }
            k *= 0.9f + 0.1f * sin(t * 23f)
            // pomalé mrknutí 1,3–1,9 s: víčko oko svisle zúží až do zavření a zase otevře
            val open = when {
                et in 1.3f..1.55f -> 1f - smooth((et - 1.3f) / 0.25f)
                et in 1.55f..1.65f -> 0f
                et in 1.65f..1.9f -> smooth((et - 1.65f) / 0.25f)
                else -> 1f
            }
            for (e in EYES.indices) {
                val c  = px(EYES[e].x, EYES[e].y)
                val gr = 38f * s
                drawCircle(
                    Brush.radialGradient(
                        0f to EyeRed.copy(alpha = 0.6f * k * (0.35f + 0.65f * open)), 0.45f to EyeRed.copy(alpha = 0.2f * k * (0.35f + 0.65f * open)), 1f to Color.Transparent,
                        center = c, radius = gr
                    ),
                    radius = gr, center = c, blendMode = BlendMode.Plus
                )
                if (open > 0.02f) rotate(EYE_TILT[e], pivot = c) {
                    val h1 = 5.6f * s * open; val h2 = 2.4f * s * open
                    drawOval(EyeRed.copy(alpha = k), topLeft = Offset(c.x - 8f * s, c.y - h1 / 2f),
                        size = Size(16f * s, h1), blendMode = BlendMode.Plus)
                    drawOval(EyeHot.copy(alpha = 0.9f * k), topLeft = Offset(c.x - 4.5f * s, c.y - h2 / 2f),
                        size = Size(9f * s, h2), blendMode = BlendMode.Plus)
                }
            }
        }

        // ── Oheň v dlaních: mihotavá záře + jiskry ──────────────────────────
        for (h in HANDS.indices) {
            val hand = HANDS[h]
            val flick = 0.72f + 0.16f * sin(t * 7.3f + h * 2f) + 0.12f * sin(t * 13.7f + h)
            val gc = px(hand.x, hand.y - 30f)
            val gr = 120f * s
            drawCircle(
                Brush.radialGradient(
                    0f to FireGlow.copy(alpha = 0.22f * flick), 0.5f to FireGlow.copy(alpha = 0.08f * flick), 1f to Color.Transparent,
                    center = gc, radius = gr
                ),
                radius = gr, center = gc, blendMode = BlendMode.Plus
            )
            for (k in 0 until 26) {
                val seed = h * 100 + k
                val life = 1.5f + hash01(seed) * 1.7f
                val u    = ((t + hash01(seed + 7) * life) % life) / life
                val sx   = hand.x + (hash01(seed + 3) - 0.5f) * 70f
                val sy   = hand.y - 10f + (hash01(seed + 4) - 0.5f) * 30f
                val rise = 130f + hash01(seed + 5) * 140f
                val x    = sx + sin(t * 1.7f + k) * 9f * u + (hash01(seed + 6) - 0.5f) * 50f * u
                val y    = sy - rise * u
                val a    = sin(PI.toFloat() * u).pow(0.8f) * (0.55f + 0.45f * hash01(seed + 8))
                val r    = (1.3f + hash01(seed + 9) * 1.8f) * s * (1f - 0.5f * u)
                val col  = lerp(EmberHot, EmberCool, u)
                val c    = px(x, y)
                drawCircle(col.copy(alpha = a * 0.25f), radius = r * 3f, center = c, blendMode = BlendMode.Plus)
                drawCircle(col.copy(alpha = a), radius = r, center = c, blendMode = BlendMode.Plus)
            }
        }
    }
}
