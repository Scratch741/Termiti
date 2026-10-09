package com.example.termiti

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ============================================================
// PackOpening.kt
// Otevírání balíčku karet jako malý obřad ve třech dějstvích:
//
//   1. ZAPEČETĚNÝ BALÍČEK – hráč ho podržením „nabije" (třese se, svítí, stoupá tón)
//      a roztrhne. Obsah se losuje až v tu chvíli; do té doby jde odejít a balíček zůstane.
//   2. ODHALOVÁNÍ – pět karet vyletí rubem nahoru. Podržením karta prozradí vzácnost
//      barvou záře, klepnutím se otočí. Čím vzácnější, tím delší a hlasitější otočka;
//      legendární zastaví ostatní karty, zatřese obrazovkou a rozsvítí ji.
//      Duplikát se promění v prach, který odletí do počítadla.
//   3. SOUHRN – co je nové, kolik prachu, postup sbírky, záruka legendární
//      a hlavně tlačítko „Otevřít další".
//
// Datová část (zásoba, losování, záruka) je v CardCollectionManager.
// ============================================================

private val PkGold  = Color(0xFFD4A843)
private val PkText  = Color(0xFFEDE0C4)
private val PkMuted = Color(0xFF9A8C78)
private val PkDust  = Color(0xFFB39DDB)
private val PkGreen = Color(0xFF6FCF73)

/** Barva vzácnosti pro záře, jiskry a paprsky. */
internal fun packRarityColor(r: Rarity) = when (r) {
    Rarity.COMMON    -> Color(0xFFBDBDBD)
    Rarity.RARE      -> Color(0xFF4A90D9)
    Rarity.EPIC      -> Color(0xFFB064E0)
    Rarity.LEGENDARY -> Color(0xFFF2C14E)
}

/** Jak dlouho se balíček drží, než se roztrhne. */
private const val HOLD_TO_OPEN_MS = 850

private fun android.view.View.tick()   = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
private fun android.view.View.thump()  = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
private fun android.view.View.strong() = performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

// ─── Vzhled balíčku ───────────────────────────────────────────────────────────

/**
 * Balíček karet (pack_art.png) – výřez s průhledným pozadím, na výšku. Do čtvercového
 * místa se vejde na výšku (Fit), takže je užší než původní čtvercový obrázek.
 */
@Composable
internal fun PackVisual(modifier: Modifier = Modifier) {
    Image(
        painter            = painterResource(R.drawable.pack_art),
        contentDescription = null,
        contentScale       = ContentScale.Fit,
        modifier           = modifier
    )
}

// ─── Částicové efekty ─────────────────────────────────────────────────────────

private class Spark(val angle: Float, val speed: Float, val size: Float, val delay: Float)

private fun makeSparks(seed: Int, count: Int): List<Spark> {
    val r = Random(seed)
    return List(count) {
        Spark(
            angle = r.nextFloat() * 2f * PI.toFloat(),
            speed = 0.35f + r.nextFloat() * 0.65f,
            size  = 0.6f + r.nextFloat() * 0.9f,
            delay = r.nextFloat() * 0.18f
        )
    }
}

/** Jiskry letící ze středu ven, mírně padají a hasnou. [p] 0..1 = průběh. */
private fun DrawScope.drawSparks(sparks: List<Spark>, p: Float, maxRadius: Float, color: Color) {
    if (p <= 0f || p >= 1f) return
    val unit = 2.4.dp.toPx()
    for (s in sparks) {
        val t = ((p - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
        if (t <= 0f) continue
        val eased = 1f - (1f - t) * (1f - t)
        val dist  = maxRadius * s.speed * eased
        val pos   = Offset(
            center.x + cos(s.angle) * dist,
            center.y + sin(s.angle) * dist + t * t * maxRadius * 0.18f
        )
        val a = (1f - t) * (1f - t)
        val r = unit * s.size * (1f - 0.45f * t)
        drawCircle(color.copy(alpha = a * 0.9f), r * 1.7f, pos)
        drawCircle(Color.White.copy(alpha = a), r * 0.7f, pos)
    }
}

/** Rozpínající se prstenec světla. */
private fun DrawScope.drawRing(p: Float, maxRadius: Float, color: Color) {
    if (p <= 0f || p >= 1f) return
    val eased = 1f - (1f - p) * (1f - p)
    drawCircle(
        color  = color.copy(alpha = (1f - p) * 0.8f),
        radius = maxRadius * (0.25f + 0.75f * eased),
        style  = Stroke(width = 3.dp.toPx() * (1f - p) + 1f)
    )
}

/** Paprsky světla otáčející se kolem středu. */
private fun DrawScope.drawRays(rotation: Float, alpha: Float, color: Color, count: Int = 14) {
    if (alpha <= 0f) return
    val radius = size.minDimension / 2f
    val brush = Brush.radialGradient(
        0f to color.copy(alpha = 0.85f * alpha), 0.55f to color.copy(alpha = 0.35f * alpha), 1f to Color.Transparent,
        center = center, radius = radius
    )
    rotate(rotation) {
        val step = 360f / count
        for (k in 0 until count) {
            drawArc(
                brush      = brush,
                startAngle = k * step,
                sweepAngle = step * (if (k % 2 == 0) 0.34f else 0.20f),
                useCenter  = true,
                topLeft    = Offset(center.x - radius, center.y - radius),
                size       = Size(radius * 2, radius * 2)
            )
        }
    }
}

/**
 * Měkká záře ve tvaru karty: hodně tenkých zaoblených vrstev přes sebe, každá jen slabě
 * krycí – směrem ke kartě se sčítají do plynulého přechodu (pět silných vrstev dělalo
 * viditelné pruhy).
 */
private fun DrawScope.drawCardGlow(color: Color, intensity: Float, cardW: Float, cardH: Float, reach: Float = 0.34f) {
    if (intensity <= 0.01f) return
    val layers = 16
    for (l in layers downTo 1) {
        // reach = jak daleko za okraj karty záře sahá (podíl šířky karty)
        val grow = l * cardW * reach / layers
        drawRoundRect(
            color        = color.copy(alpha = (0.115f * intensity.coerceAtMost(1.2f)).coerceAtMost(1f)),
            topLeft      = Offset(center.x - cardW / 2 - grow, center.y - cardH / 2 - grow),
            size         = Size(cardW + 2 * grow, cardH + 2 * grow),
            cornerRadius = CornerRadius(cardW * 0.08f + grow, cardW * 0.08f + grow)
        )
    }
}

// ─── Hlavní překryv ───────────────────────────────────────────────────────────

/** Let prachu z duplikátní karty do počítadla. */
private class DustFlight(val slot: Int, val amount: Int, val progress: Animatable<Float, *>)

/**
 * Celoobrazovkové otevírání balíčků ze zásoby. Zůstává otevřené, dokud hráč otevírá
 * další balíčky; [onProfileChanged] se volá po každé změně profilu (otevření, koupě).
 */
@Composable
fun PackOpeningOverlay(allCards: List<Card>, onProfileChanged: () -> Unit, onClose: () -> Unit) {
    val s       = LocalStrings.current
    val view    = LocalView.current
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()

    var packKey   by remember { mutableIntStateOf(0) }                 // nový balíček = nové animace
    var result    by remember { mutableStateOf<PackResult?>(null) }    // null = balíček je zapečetěný
    val requested = remember { mutableStateListOf<Int>() }             // karty, které se mají otočit
    val settled   = remember { mutableStateListOf<Int>() }             // karty s dokončenou otočkou
    var focus     by remember { mutableStateOf<Int?>(null) }           // epická/legendární právě v obřadu
    var packsLeft by remember { mutableIntStateOf(CardCollectionManager.unopenedPacks()) }
    var dustTotal by remember { mutableIntStateOf(0) }                 // prach získaný za celé otevírání
    var revealingAll by remember { mutableStateOf(false) }
    val flights   = remember { mutableStateListOf<DustFlight>() }

    val backdrop  = remember { Animatable(0f) }
    val flash     = remember { Animatable(0f) }    // roztržení balíčku
    val goldFlash = remember { Animatable(0f) }    // legendární karta
    val shake     = remember { Animatable(0f) }
    val exit      = remember { Animatable(0f) }    // odlet karet před dalším balíčkem

    LaunchedEffect(Unit) { backdrop.animateTo(1f, tween(260)) }

    val cards      = result?.cards.orEmpty()
    val allSettled = result != null && settled.size == cards.size

    fun close() { SoundManager.playMenuTap(); onClose() }

    fun openPack() {
        val r = CardCollectionManager.openStoredPack(allCards)
        if (r == null) { onClose(); return }
        packsLeft = CardCollectionManager.unopenedPacks()
        onProfileChanged()
        SoundManager.playPackBurst()
        view.strong()
        result = r
        scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(720, easing = LinearOutSlowInEasing)) }
        scope.launch { shake.snapTo(0.6f); shake.animateTo(0f, tween(380)) }
    }

    fun nextPack(buyFirst: Boolean) {
        if (buyFirst) {
            if (!CardCollectionManager.buyPacks(1)) return
            SoundManager.playPackBuy()
            packsLeft = CardCollectionManager.unopenedPacks()
            onProfileChanged()
        }
        scope.launch {
            exit.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            result = null
            requested.clear(); settled.clear(); flights.clear()
            focus = null; revealingAll = false
            packKey++
            exit.snapTo(0f)
        }
    }

    fun requestReveal(i: Int) {
        if (focus == null && i !in requested) requested.add(i)
    }

    fun revealAll() {
        val r = result ?: return
        if (revealingAll) return
        revealingAll = true
        val key = packKey
        scope.launch {
            // Od nejběžnější po nejvzácnější – to nejlepší nakonec
            val order = r.cards.indices.filter { it !in requested }.sortedBy { r.cards[it].card.rarity.ordinal }
            for (i in order) {
                snapshotFlow { focus }.first { it == null }
                if (packKey != key) return@launch
                if (i in requested) continue
                requested.add(i)
                when (r.cards[i].card.rarity) {
                    Rarity.COMMON -> delay(190)
                    Rarity.RARE   -> delay(340)
                    else          -> snapshotFlow { i in settled }.first { it }
                }
            }
        }
    }

    // Zpět: zapečetěný balíček jde opustit (zůstane v zásobě), rozdané karty se nejdřív dootáčí
    BackHandler {
        when {
            result == null -> close()
            allSettled     -> close()
            else           -> revealAll()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.93f * backdrop.value)) }
            // pohltí klepnutí, ať nepropadnou do obchodu pod překryvem
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        val W = maxWidth
        val H = maxHeight
        var rootTopLeft  by remember { mutableStateOf(Offset.Zero) }
        var dustChipPos  by remember { mutableStateOf(Offset.Unspecified) }

        // Rozměry vějíře karet
        val n       = cards.size.coerceAtLeast(1)
        val gap     = 16.dp
        val cardSc  = minOf(1.3f, (W - 72.dp) / (100.dp * n + gap * (n - 1)), (H * 0.46f) / 140.dp)
        val slotW   = 100.dp * cardSc + gap
        val rowDy   = -H * 0.06f

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootTopLeft = it.boundsInRoot().topLeft }
                .graphicsLayer {
                    // otřes obrazovky (roztržení, legendární karta)
                    val a = shake.value
                    translationX = sin(a * 46f) * a * 14.dp.toPx()
                    translationY = cos(a * 39f) * a * 9.dp.toPx()
                }
        ) {
            // ── 1. Zapečetěný balíček ─────────────────────────────────────────
            if (result == null) {
                key(packKey) {
                    SealedPack(
                        size     = minOf(H * 0.70f, 270.dp),
                        modifier = Modifier.align(Alignment.Center).offset(y = -H * 0.03f),
                        onOpen   = { openPack() }
                    )
                }
                Text(
                    s.packHoldHint,
                    color = PkMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
                )
                PlainButton(
                    text      = s.back2,
                    modifier  = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp).width(92.dp).height(30.dp),
                    textColor = PkMuted,
                    fontSize  = 10.sp,
                    paddingH  = 4.dp,
                    paddingV  = 0.dp,
                    onClick   = { onClose() }
                )
            }

            // ── 2. Odhalování karet ───────────────────────────────────────────
            result?.let { r ->
                val legendaryFocus = focus?.let { r.cards.getOrNull(it)?.card?.rarity == Rarity.LEGENDARY } == true
                r.cards.forEachIndexed { i, gain ->
                    key(packKey, i) {
                        PackCard(
                            gain      = gain,
                            index     = i,
                            count     = r.cards.size,
                            scale     = cardSc,
                            slotDx    = slotW * (i - (r.cards.size - 1) / 2f),
                            modifier  = Modifier.align(Alignment.Center).offset(y = rowDy),
                            requested = i in requested,
                            locked    = focus != null,
                            dimmed    = legendaryFocus && focus != i,
                            exit      = { exit.value },
                            exitDrop  = H * 0.7f,
                            onTap     = { requestReveal(i) },
                            onFocus   = { on -> focus = if (on) i else null },
                            onLegendaryFlash = {
                                view.strong()
                                scope.launch { goldFlash.snapTo(0.85f); goldFlash.animateTo(0f, tween(900)) }
                                scope.launch { shake.snapTo(1f); shake.animateTo(0f, tween(620)) }
                            },
                            onSettled = {
                                if (i !in settled) settled.add(i)
                                if (gain.isDuplicate && gain.dustGained > 0) {
                                    val key = packKey
                                    scope.launch {
                                        delay(420)   // nejdřív ať je vidět, co to bylo
                                        if (packKey != key) return@launch
                                        val f = DustFlight(i, gain.dustGained, Animatable(0f))
                                        flights.add(f)
                                        SoundManager.playPackDust()
                                        f.progress.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
                                        flights.remove(f)
                                        dustTotal += f.amount
                                    }
                                }
                            }
                        )
                    }
                }

                // Let prachu z duplikátů do počítadla
                if (flights.isNotEmpty() && dustChipPos != Offset.Unspecified) {
                    Canvas(Modifier.fillMaxSize().zIndex(30f)) {
                        val target = dustChipPos - rootTopLeft
                        for (f in flights) {
                            val start = Offset(
                                size.width / 2f + (slotW * (f.slot - (r.cards.size - 1) / 2f)).toPx(),
                                size.height / 2f + rowDy.toPx() + 30.dp.toPx()
                            )
                            val ctrl = Offset((start.x + target.x) / 2f, minOf(start.y, target.y) - 70.dp.toPx())
                            val p = f.progress.value
                            for (k in 0 until 12) {
                                val t = (p * 1.45f - k * 0.04f).coerceIn(0f, 1f)
                                if (t <= 0f || t >= 1f) continue
                                val u = 1f - t
                                val pos = Offset(
                                    u * u * start.x + 2 * u * t * ctrl.x + t * t * target.x + sin(k * 2.1f + t * 9f) * 6.dp.toPx() * u,
                                    u * u * start.y + 2 * u * t * ctrl.y + t * t * target.y + cos(k * 1.7f + t * 7f) * 6.dp.toPx() * u
                                )
                                val a = minOf(1f, t * 5f) * (1f - t * 0.35f)
                                drawCircle(PkDust.copy(alpha = 0.55f * a), 4.2.dp.toPx() * (1f - 0.4f * t), pos)
                                drawCircle(Color.White.copy(alpha = 0.8f * a), 1.5.dp.toPx(), pos)
                            }
                        }
                    }
                }
            }

            // ── Horní lišta: zbývající balíčky + prach ─────────────────────────
            Row(
                Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (result != null || dustTotal > 0) {
                    val shownDust by animateIntAsState(dustTotal, tween(500), label = "dust")
                    val pulse = remember { Animatable(1f) }
                    LaunchedEffect(dustTotal) {
                        if (dustTotal > 0) { pulse.snapTo(1.28f); pulse.animateTo(1f, spring(dampingRatio = 0.45f)) }
                    }
                    Row(
                        Modifier
                            .onGloballyPositioned { dustChipPos = it.boundsInRoot().center }
                            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                            .height(30.dp)
                            .buttonTexture(R.drawable.plain_button)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Image(painterResource(R.drawable.dust_icon), null, Modifier.size(14.dp))
                        Text("+$shownDust", color = PkDust, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.height(30.dp).buttonTexture(R.drawable.plain_button).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.card_icon), null, Modifier.size(14.dp))
                    Text("× $packsLeft", color = PkText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── 3. Spodní část: nápověda / souhrn ─────────────────────────────
            result?.let { r ->
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp)
                        .graphicsLayer { alpha = 1f - exit.value },
                    contentAlignment = Alignment.Center
                ) {
                    if (!allSettled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(s.packTapHint, color = PkMuted, fontSize = 11.sp)
                            if (!revealingAll && requested.size < r.cards.size) {
                                PlainButton(
                                    text      = s.packRevealAll,
                                    modifier  = Modifier.width(120.dp).height(30.dp),
                                    textColor = PkText,
                                    fontSize  = 10.sp,
                                    paddingH  = 4.dp,
                                    paddingV  = 0.dp,
                                    onClick   = { revealAll() }
                                )
                            }
                        }
                    } else {
                        PackSummary(
                            result    = r,
                            allCards  = allCards,
                            packsLeft = packsLeft,
                            onNext    = { nextPack(buyFirst = false) },
                            onBuyNext = { nextPack(buyFirst = true) },
                            onDone    = { close() }
                        )
                    }
                }
            }
        }

        // Záblesk při roztržení: světlo se rozpíná ze středu a hasne
        run {
            Canvas(Modifier.fillMaxSize()) {
                val a = flash.value
                if (a <= 0.01f) return@Canvas
                val radius = size.maxDimension * (0.25f + 0.95f * (1f - a))
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = a), 0.35f to Color(0xFFFFE9A8).copy(alpha = a * 0.85f),
                        0.7f to Color(0xFFB064E0).copy(alpha = a * 0.35f), 1f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height / 2f - with(density) { (H * 0.03f).toPx() }),
                        radius = radius
                    )
                )
            }
        }
        // Zlatý záblesk přes celou obrazovku u legendární karty
        Canvas(Modifier.fillMaxSize()) {
            val a = goldFlash.value
            if (a > 0.01f) drawRect(Color(0xFFFFE08A).copy(alpha = a * 0.55f))
        }
    }
}

// ─── Zapečetěný balíček ───────────────────────────────────────────────────────

/**
 * Balíček uprostřed obrazovky. Podržením se nabíjí (třes, záře, prstenec postupu);
 * při puštění se nabití vrací. Po naplnění zavolá [onOpen] – jednou.
 */
@Composable
private fun SealedPack(size: Dp, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val view = LocalView.current
    var holding by remember { mutableStateOf(false) }
    var opened  by remember { mutableStateOf(false) }
    val charge  = remember { Animatable(0f) }
    val enter   = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(holding) {
        if (opened) return@LaunchedEffect
        if (holding) {
            val left = ((1f - charge.value) * HOLD_TO_OPEN_MS).toInt().coerceAtLeast(1)
            charge.animateTo(1f, tween(left, easing = LinearEasing))
            if (charge.value >= 1f && !opened) { opened = true; onOpen() }
        } else {
            charge.animateTo(0f, tween(280))
        }
    }
    // Stoupající „cinknutí" a klepnutí vibrace ve čtvrtinách nabití
    LaunchedEffect(Unit) {
        snapshotFlow { (charge.value * 4f).toInt().coerceAtMost(3) }
            .distinctUntilChanged()
            .collect { step -> if (step in 1..3 && holding) { SoundManager.playPackCharge(step); view.tick() } }
    }

    val inf = rememberInfiniteTransition(label = "pack")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val wobble  by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(55, easing = LinearEasing), RepeatMode.Reverse), label = "wobble")
    val spin    by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false })
            },
        contentAlignment = Alignment.Center
    ) {
        val c = charge.value
        // Záře, paprsky a prstenec postupu za balíčkem
        Canvas(Modifier.requiredSize(size * 1.7f)) {
            val glow = 0.30f + 0.20f * breathe + 0.50f * c
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFB064E0).copy(alpha = 0.55f * glow), 0.5f to PkGold.copy(alpha = 0.22f * glow), 1f to Color.Transparent,
                    radius = this.size.minDimension / 2f
                )
            )
            drawRays(spin, alpha = (c - 0.25f).coerceAtLeast(0f) * 1.1f, color = PkGold)
            if (c > 0.01f) {
                val rr = size.toPx() * 0.46f
                drawArc(
                    color = PkGold.copy(alpha = 0.9f), startAngle = -90f, sweepAngle = 360f * c, useCenter = false,
                    topLeft = Offset(center.x - rr, center.y - rr), size = Size(rr * 2, rr * 2),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        PackVisual(
            Modifier.fillMaxSize().graphicsLayer {
                val e  = enter.value
                val sc = (0.55f + 0.45f * e) * (1f + 0.025f * breathe + 0.09f * c)
                scaleX = sc; scaleY = sc
                alpha  = e.coerceIn(0f, 1f)
                translationY = (1f - e) * -60.dp.toPx() - 4.dp.toPx() * breathe
                translationX = wobble * c * 5.dp.toPx()
                rotationZ    = wobble * c * 2.6f
            }
        )
    }
}

// ─── Jedna karta ve vějíři ────────────────────────────────────────────────────

/**
 * Karta z balíčku: přiletí rubem nahoru, podržením napoví vzácnost září, po vyžádání
 * ([requested]) se otočí obřadem podle vzácnosti a zavolá [onSettled].
 *
 * @param locked právě probíhá obřad jiné (epické/legendární) karty – klepnutí se ignoruje
 * @param dimmed probíhá obřad legendární karty a tahle ustupuje do pozadí
 */
@Composable
private fun PackCard(
    gain: CardGain,
    index: Int,
    count: Int,
    scale: Float,
    slotDx: Dp,
    modifier: Modifier,
    requested: Boolean,
    locked: Boolean,
    dimmed: Boolean,
    /** Průběh odletu před dalším balíčkem (0..1) – čte se až ve vrstvě, ne při skládání. */
    exit: () -> Float,
    exitDrop: Dp,
    onTap: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onLegendaryFlash: () -> Unit,
    onSettled: () -> Unit
) {
    val s      = LocalStrings.current
    val view   = LocalView.current
    val rarity = gain.card.rarity
    val color  = packRarityColor(rarity)

    val enter = remember { Animatable(0f) }
    val flip  = remember { Animatable(0f) }      // 0 = rub, 180 = líc
    val lift  = remember { Animatable(1f) }      // zvětšení při obřadu
    val glow  = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }      // jiskry a prstenec
    val rays  = remember { Animatable(0f) }      // paprsky za legendární kartou
    val badge = remember { Animatable(0f) }      // štítek NOVÁ / prach
    var holding    by remember { mutableStateOf(false) }
    var inCeremony by remember { mutableStateOf(false) }
    var settled    by remember { mutableStateOf(false) }

    val sparks = remember {
        makeSparks(seed = index * 31 + gain.card.id.hashCode(), count = when (rarity) {
            Rarity.COMMON -> 0; Rarity.RARE -> 12; Rarity.EPIC -> 28; Rarity.LEGENDARY -> 54
        })
    }

    // Přílet ze středu (z roztrženého balíčku), karty postupně
    LaunchedEffect(Unit) {
        delay(120L + 85L * index)
        SoundManager.playCardDraw()
        enter.animateTo(1f, tween(430, easing = FastOutSlowInEasing))
    }

    // Obřad otočení
    LaunchedEffect(requested) {
        if (!requested || settled) return@LaunchedEffect
        when (rarity) {
            Rarity.COMMON -> {
                SoundManager.playPackFlip()
                flip.animateTo(180f, tween(300, easing = FastOutSlowInEasing))
                SoundManager.playPackReveal(0)
                glow.animateTo(0.20f, tween(220))
            }
            Rarity.RARE -> {
                SoundManager.playPackFlip()
                coroutineScope {
                    launch { glow.animateTo(1f, tween(380)) }
                    launch { flip.animateTo(180f, tween(380, easing = FastOutSlowInEasing)) }
                }
                SoundManager.playPackReveal(1)
                view.tick()
                launch { burst.snapTo(0f); burst.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
                glow.animateTo(0.42f, tween(500))
            }
            Rarity.EPIC -> {
                inCeremony = true; onFocus(true)
                SoundManager.playPackAnticipation(0.32f, legendary = false)
                coroutineScope {
                    launch { lift.animateTo(1.15f, tween(320, easing = FastOutSlowInEasing)) }
                    launch { glow.animateTo(1f, tween(320)) }
                }
                SoundManager.playPackFlip()
                flip.animateTo(180f, tween(460, easing = FastOutSlowInEasing))
                SoundManager.playPackReveal(2)
                view.thump()
                launch { burst.snapTo(0f); burst.animateTo(1f, tween(950, easing = LinearOutSlowInEasing)) }
                delay(260)
                inCeremony = false; onFocus(false)
                launch { glow.animateTo(0.80f, tween(600)) }
                lift.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            }
            Rarity.LEGENDARY -> {
                inCeremony = true; onFocus(true)
                // Napětí: karta stoupá, třese se, rozhoří se paprsky
                SoundManager.playPackAnticipation(0.66f, legendary = true)
                coroutineScope {
                    launch { lift.animateTo(1.26f, tween(660, easing = FastOutSlowInEasing)) }
                    launch { glow.animateTo(1.15f, tween(660)) }
                    launch { rays.animateTo(1f, tween(660)) }
                }
                delay(120)
                SoundManager.playPackFlip()
                val flipJob = launch { flip.animateTo(180f, tween(580, easing = FastOutSlowInEasing)) }
                delay(290)              // v půlce otočky, když se karta ukáže lícem
                onLegendaryFlash()
                SoundManager.playPackReveal(3)
                // jiskry běží dál samy (1,5 s) – obřad na ně nečeká
                launch { burst.snapTo(0f); burst.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) }
                flipJob.join()
                delay(780)              // nechat ji chvíli zářit
                inCeremony = false; onFocus(false)
                launch { rays.animateTo(0.42f, tween(700)) }
                launch { glow.animateTo(0.95f, tween(700)) }
                lift.animateTo(1.05f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            }
        }
        settled = true
        onSettled()
        if (gain.isNew || gain.isDuplicate) {
            badge.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }

    val inf = rememberInfiniteTransition(label = "card")
    val phase  by inf.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase")
    val wobble by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Reverse), label = "wobble")
    val spin   by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "spin")
    val dim    by animateFloatAsState(if (dimmed) 0.32f else 1f, tween(260), label = "dim")

    val cardW = 100.dp
    val cardH = 140.dp
    val showFace = flip.value > 90f
    val tappable = !requested && !locked && enter.value > 0.9f

    Box(
        modifier
            .offset(x = slotDx)
            .zIndex(if (inCeremony) 20f else if (settled && rarity == Rarity.LEGENDARY) 5f else 0f)
            .size(cardW * scale, cardH * scale)
            .graphicsLayer {
                val e = enter.value
                translationX = -slotDx.toPx() * (1f - e)
                val out = exit()
                translationY = (1f - e) * 24.dp.toPx() + out * exitDrop.toPx() +
                    (if (!requested) sin(phase + index * 1.3f) * 2.5.dp.toPx() else 0f)
                val sc = 0.25f + 0.75f * e
                scaleX = sc; scaleY = sc
                rotationZ = (1f - e) * (index - (count - 1) / 2f) * 16f
                alpha = minOf(1f, e * 3f) * (1f - out) * dim
            }
            .pointerInput(tappable) {
                if (tappable) {
                    detectTapGestures(
                        onPress     = { holding = true; tryAwaitRelease(); holding = false },
                        // prázdný onLongPress: dlouhé podržení je jen nahlédnutí na vzácnost,
                        // po puštění se karta neotočí (bez něj by se každé puštění bralo jako klepnutí)
                        onLongPress = { },
                        onTap       = { onTap() }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Záře + paprsky (za kartou). Nápověda: při podržení neotočená karta prozradí barvu vzácnosti.
        val pulse = if (settled && rarity.ordinal >= Rarity.EPIC.ordinal) 0.82f + 0.18f * sin(phase * 2f) else 1f
        val peek  = if (holding && !requested) 0.55f else 0f
        Canvas(Modifier.requiredSize(cardW * scale * 3.4f)) {
            drawRays(spin, rays.value * (0.85f + 0.15f * sin(phase * 2f)), color)
            drawCardGlow(
                color     = color,
                intensity = maxOf(glow.value * pulse, peek),
                // Obrys roste se vzácností: běžná a vzácná jen tenký lem, ať se sousední karty
                // neslévají do jedné barevné skvrny; epická a legendární září naplno.
                reach     = when (rarity) {
                    Rarity.COMMON -> 0.09f; Rarity.RARE -> 0.13f; Rarity.EPIC -> 0.26f; Rarity.LEGENDARY -> 0.34f
                },
                cardW     = cardW.toPx() * scale * lift.value,
                cardH     = cardH.toPx() * scale * lift.value
            )
        }

        // Samotná karta – rub / líc, otáčí se kolem svislé osy
        Box(
            Modifier
                .requiredSize(cardW, cardH)
                .graphicsLayer {
                    val sc = scale * lift.value
                    scaleX = sc; scaleY = sc
                    rotationY = flip.value
                    cameraDistance = 14f * density
                    // třes při napětí před otočením (jen dokud je karta rubem nahoru)
                    if (inCeremony && flip.value < 20f) rotationZ = wobble * (lift.value - 1f) * 14f
                },
            contentAlignment = Alignment.Center
        ) {
            if (!showFace) {
                // Rub má poměr stran 0,615 – do 100×140 by se roztáhl. Kreslí se užší (86×140);
                // při otočce je karta v 90° stejně vidět z hrany, takže přechod na širší líc není znát.
                Image(
                    painter            = painterResource(playerCardBackResId()),
                    contentDescription = null,
                    modifier           = Modifier.size(width = 86.dp, height = 140.dp),
                    contentScale       = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    CardPreview(card = gain.card)
                    // Duplikát: karta ztmavne, hodnotu převezme štítek s prachem
                    if (gain.isDuplicate) {
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.30f * badge.value)))
                    }
                }
            }
        }

        // Jiskry a prstenec (před kartou)
        if (burst.value > 0f && burst.value < 1f) {
            Canvas(Modifier.requiredSize(cardW * scale * 4.2f)) {
                val maxR = size.minDimension / 2f
                drawRing(burst.value, maxR * (if (rarity == Rarity.LEGENDARY) 0.95f else 0.6f), color)
                if (rarity == Rarity.LEGENDARY) drawRing((burst.value * 1.5f - 0.25f).coerceIn(0f, 1f), maxR * 0.7f, Color.White)
                drawSparks(sparks, burst.value, maxR * (if (rarity == Rarity.LEGENDARY) 1f else 0.72f), color)
            }
        }

        // Štítky: NOVÁ nahoře, prach za duplikát dole
        if (badge.value > 0.01f) {
            if (gain.isNew) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-11).dp)
                        .graphicsLayer { scaleX = badge.value; scaleY = badge.value; alpha = badge.value.coerceIn(0f, 1f) }
                        .size(width = 58.dp, height = 20.dp)
                        .buttonTexture(R.drawable.plain_button),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.packNew, color = PkGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            if (gain.isDuplicate) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 11.dp)
                        .graphicsLayer { scaleX = badge.value; scaleY = badge.value; alpha = badge.value.coerceIn(0f, 1f) }
                        .height(22.dp)
                        .buttonTexture(R.drawable.plain_button)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("+${gain.dustGained}", color = PkDust, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Image(painterResource(R.drawable.dust_icon), null, Modifier.size(13.dp))
                }
            }
        }
    }
}

// ─── Souhrn balíčku ───────────────────────────────────────────────────────────

/** Co balíček přinesl + jak pokračovat. Hlavní tlačítko vede rovnou k dalšímu balíčku. */
@Composable
private fun PackSummary(
    result: PackResult,
    allCards: List<Card>,
    packsLeft: Int,
    onNext: () -> Unit,
    onBuyNext: () -> Unit,
    onDone: () -> Unit
) {
    val s = LocalStrings.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(250); appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    val newCards   = result.cards.count { it.isNew }
    val progress   = remember(result) { CardCollectionManager.collectionProgress(allCards) }
    val untilPity  = CardCollectionManager.packsUntilGuaranteedLegendary()
    val canBuy     = CardCollectionManager.canBuyPacks(1)

    Column(
        Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 18.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.height(30.dp).buttonTexture(R.drawable.plain_button_longer).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            @Composable
            fun Stat(iconRes: Int, text: String, color: Color) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(iconRes), null, Modifier.size(13.dp))
                    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Stat(R.drawable.star_icon, s.packSummaryNew.format(newCards), if (newCards > 0) PkGold else PkMuted)
            if (result.totalDustGained > 0) Stat(R.drawable.dust_icon, "+${result.totalDustGained}", PkDust)
            Stat(R.drawable.card_icon, s.packSummaryCollection.format(progress.first, progress.second), PkText)
        }
        Text(
            when {
                result.pityUsed -> s.packPityHit
                untilPity <= 1  -> s.shopPityNext
                else            -> s.shopPity.format(untilPity)
            },
            color = if (result.pityUsed || untilPity <= 1) PkGold else PkMuted, fontSize = 9.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlainButton(
                text      = s.shopFinish,
                modifier  = Modifier.width(110.dp).height(34.dp),
                textColor = PkMuted,
                fontSize  = 11.sp,
                paddingH  = 4.dp,
                paddingV  = 0.dp,
                onClick   = onDone
            )
            when {
                packsLeft > 0 -> PlainButton(
                    text      = s.packOpenNext.format(packsLeft),
                    modifier  = Modifier.width(210.dp).height(34.dp),
                    textColor = PkGreen,
                    fontSize  = 12.sp,
                    paddingH  = 4.dp,
                    paddingV  = 0.dp,
                    buttonRes = R.drawable.plain_button_longer,
                    onClick   = onNext
                )
                canBuy -> PlainButtonWithIcon(
                    text      = s.packBuyNext.format(CardCollectionManager.PACK_COST_GOLD),
                    iconRes   = R.drawable.goldcoin_icon,
                    modifier  = Modifier.width(210.dp).height(34.dp),
                    textColor = PkGold,
                    fontSize  = 12.sp,
                    paddingH  = 4.dp,
                    paddingV  = 0.dp,
                    buttonRes = R.drawable.plain_button_longer,
                    onClick   = onBuyNext
                )
            }
        }
    }
}
