package com.example.termiti

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zmenšené bitmapy pro dlaždice karet (Tvorba balíčku, obchod).
 *
 * Proč to existuje: obrázky leží v res/drawable/ bez hustotního kvalifikátoru, takže je
 * Android bere jako mdpi a painterResource je dekóduje ZVĚTŠENÉ podle hustoty displeje.
 * Rám karty 800×1195 se na 3× telefonu dekóduje na 2400×3585 (~34 MB) a kreslí se do
 * dlaždice ~300×420 px. Navíc synchronně na UI vlákně pokaždé, když dlaždice vjede do
 * obrazu – při rychlém rolování tak každý snímek čeká na dekódování několika obrázků.
 *
 * Tady se dekóduje bez zvětšení (inScaled = false), s inSampleSize na cílovou velikost,
 * na pozadí, a výsledek se drží v LRU cache sdílené všemi dlaždicemi.
 */
object CardBitmapCache {
    private const val MAX_BYTES = 48 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun key(resId: Int, reqW: Int, reqH: Int) = "$resId@${reqW}x$reqH"

    fun get(key: String): ImageBitmap? = cache.get(key)

    /** Dekóduje [resId] tak, aby měl aspoň [reqW]×[reqH] px, ale ne zbytečně víc. */
    fun decode(context: Context, resId: Int, reqW: Int, reqH: Int): ImageBitmap? {
        val key = key(resId, reqW, reqH)
        cache.get(key)?.let { return it }
        val res = context.resources
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeResource(res, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        val opts = BitmapFactory.Options().apply { inScaled = false; inSampleSize = sample }
        val bmp = BitmapFactory.decodeResource(res, resId, opts) ?: return null
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }

    /**
     * [resId] zmenšený přesně na šířku [reqW] px (výška podle poměru stran), s vyhlazením.
     * Na rozdíl od [decode] netrefuje jen mocniny dvou – 800 px na 450 px umí jen takhle.
     * Je-li originál užší než [reqW], vrátí se v původní velikosti.
     */
    fun scaled(context: Context, resId: Int, reqW: Int): ImageBitmap? {
        val key = "$resId@w$reqW"
        cache.get(key)?.let { return it }
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val full = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return null
        val out = if (full.width <= reqW) full else {
            val h = (full.height.toLong() * reqW / full.width).toInt().coerceAtLeast(1)
            android.graphics.Bitmap.createScaledBitmap(full, reqW, h, true).also { if (it !== full) full.recycle() }
        }
        return out.asImageBitmap().also { cache.put(key, it) }
    }
}

/** Rámy karet a překryvy vzácnosti – vrstvy, které kreslí každá karta ve hře. */
internal val CARD_LAYER_DRAWABLES = listOf(
    R.drawable.card_frame_magic, R.drawable.card_frame_attack, R.drawable.card_frame_stones, R.drawable.card_frame_chaos,
    R.drawable.rarity_common, R.drawable.rarity_rare, R.drawable.rarity_epic, R.drawable.rarity_legendary
)

/** Šířka, na kterou se vrstvy karet zmenšují: 1,5× karta (100 dp). Ve hře se karta kreslí nejvýš 1,35×. */
internal val CARD_LAYER_WIDTH = 150.dp

/**
 * Rám karty / překryv vzácnosti zmenšený na [CARD_LAYER_WIDTH] pro karty ve hře.
 *
 * Originály mají 800×1195 px (3,8 MB v grafické paměti každý, osm jich je přes 30 MB)
 * a karta ve hře z nich potřebuje sotva polovinu rozlišení. Zmenšená kopie má na 3×
 * displeji 450×672 px (1,2 MB) a je pořád jemnější než největší karta na bojišti (405 px).
 * Velký náhled karty dál kreslí originál přes painterResource.
 *
 * Volá se z kreslení karty: z cache hned; když v ní bitmapa ještě není (první karta dřív,
 * než doběhl [prewarmCardLayers]), dekóduje se na místě.
 */
@Composable
fun rememberCardLayer(resId: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val reqW = with(LocalDensity.current) { CARD_LAYER_WIDTH.roundToPx() }
    return remember(resId, reqW) { CardBitmapCache.scaled(context, resId, reqW) }
}

/** Připraví zmenšené vrstvy karet na pozadí, aby první vyložená karta na nic nečekala. */
@Composable
fun PrewarmCardLayers() {
    val context = LocalContext.current.applicationContext
    val reqW = with(LocalDensity.current) { CARD_LAYER_WIDTH.roundToPx() }
    LaunchedEffect(reqW) {
        withContext(Dispatchers.IO) { CARD_LAYER_DRAWABLES.forEach { CardBitmapCache.scaled(context, it, reqW) } }
    }
}

/**
 * Bitmapa pro dlaždici karty: z cache okamžitě, jinak se dekóduje na pozadí.
 * Vrací null, dokud se načítá – volající v tu chvíli nic nekreslí (jeden snímek).
 */
@Composable
fun rememberCardBitmap(resId: Int, width: Dp, height: Dp): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val reqW = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val reqH = with(density) { height.roundToPx() }.coerceAtLeast(1)
    val key = CardBitmapCache.key(resId, reqW, reqH)
    val state = remember(key) { mutableStateOf(CardBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (state.value == null) {
            state.value = withContext(Dispatchers.IO) { CardBitmapCache.decode(context, resId, reqW, reqH) }
        }
    }
    return state.value
}
