// ============================================================
// FlightOverlay.kt
// Animace "karta letí z ruky hráče do discard slotu uprostřed"
// ============================================================
package com.example.termiti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ─── Stav ────────────────────────────────────────────────────────────────────

/** Běžící let konkrétní karty. id slouží jako klíč pro LaunchedEffect. */
data class FlightJob(
    val card    : Card,
    val fromRect: Rect,   // zdrojová pozice (karta v ruce, okenní souřadnice)
    val toRect  : Rect,   // cílová pozice (discard slot v bojišti)
    val id      : Long
)

/**
 * Ghost efekt karty mizící z ruky – spálená (oranžová) / ukradená (fialová).
 * Karta se na své pozici rozplyne s barevným nádechem, aby bylo vidět,
 * CO se stalo a KTERÉ karty se to týkalo.
 */
data class LossGhost(
    val card      : Card?,        // karta (moje ruka); null = rub (soupeřova ruka)
    val backResId : Int?,         // skin rubu pro card == null
    val action    : CardAction,   // BURNED | STOLEN → barva efektu
    val rect      : Rect,         // poslední známá pozice karty (okenní souřadnice)
    val id        : Long
)

class FlightOverlayState {
    /** Poslední známý bounds každé karty v ruce (okenní souřadnice). */
    val sources: SnapshotStateMap<String, Rect> = mutableStateMapOf()
    /** Bounds cíle (slot poslední zahrané karty uprostřed bojiště). */
    var target: Rect? by mutableStateOf(null)
    /** Právě běžící let (null = žádná animace). */
    var flying: FlightJob? by mutableStateOf(null)
    /**
     * Id poslední karty, která „dopadla" (dokončila let nebo let přeskočila).
     * Statická karta v bojišti se ukáže jen pro tuto kartu.
     */
    var landedPlayerCardId: String? by mutableStateOf(null)
    /**
     * Průběh aktuální letové animace (0..1).
     * GameScreen ho čte k postupnému rozplynutí staré karty v discard slotu,
     * takže letící karta přilétí do prázdného slotu – žádné prolnutí.
     */
    var flightProgress: Float by mutableFloatStateOf(0f)

    /** Běžící ghost efekty ztracených karet (spálení / krádež). */
    val lossGhosts: SnapshotStateList<LossGhost> = mutableStateListOf()

    private var _counter: Long = 0L
    fun nextId(): Long = ++_counter

    /** True, pokud pro tuto kartu právě běží letová animace. */
    fun isFlying(cardId: String): Boolean = flying?.card?.id == cardId

    /** Spustí ghost efekt na dané pozici. [card] = líc (moje ruka), null = rub soupeře. */
    fun spawnLoss(card: Card?, backResId: Int?, action: CardAction, rect: Rect) {
        lossGhosts.add(LossGhost(card, backResId, action, rect, nextId()))
    }
}

val LocalFlightOverlay = compositionLocalOf<FlightOverlayState?> { null }

// ─── Modifiery pro sledování pozic ───────────────────────────────────────────

/** Sleduje pozici karty v ruce – registruje její bounds do FlightOverlayState. */
fun Modifier.trackFlightSource(cardId: String): Modifier = composed {
    val flight = LocalFlightOverlay.current ?: return@composed this
    this.onGloballyPositioned { coords ->
        flight.sources[cardId] = coords.boundsInRoot()
    }
}

/** Sleduje pozici discard slotu v bojišti. */
fun Modifier.trackFlightTarget(): Modifier = composed {
    val flight = LocalFlightOverlay.current ?: return@composed this
    this.onGloballyPositioned { coords ->
        flight.target = coords.boundsInRoot()
    }
}

// ─── Overlay – renderuje letící kartu ────────────────────────────────────────

@Composable
fun FlightOverlayBox(flight: FlightOverlayState) {
    // Ghost efekty ztracených karet (spálení / krádež) – nezávislé na letu
    flight.lossGhosts.forEach { ghost ->
        key(ghost.id) { LossGhostView(flight, ghost) }
    }
    val job = flight.flying ?: return
    val progress = remember(job.id) { Animatable(0f) }

    // Jediná plynulá animace 0 → 1, žádné mezipauzy.
    LaunchedEffect(job.id) {
        progress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
        // Atomicky: přepni statický slot na novou kartu + uvolni overlay.
        // Statická karta v tento okamžik už má alpha = 0 (rozplynula se dříve
        // prostřednictvím flightProgress), takže nedochází k vizuálnímu překrytí.
        Snapshot.withMutableSnapshot {
            flight.flightProgress = 0f
            flight.landedPlayerCardId = job.card.id
            if (flight.flying?.id == job.id) flight.flying = null
        }
    }

    val p = progress.value
    // Průběh zpřístupníme GameScreen přes SideEffect (synchronně po každé kompozici).
    SideEffect { flight.flightProgress = p }

    val density = LocalDensity.current
    val natW = with(density) { 100.dp.toPx() }
    val natH = with(density) { 140.dp.toPx() }

    // Aktuální střed (lerp from → to)
    val curCx = job.fromRect.center.x + (job.toRect.center.x - job.fromRect.center.x) * p
    val curCy = job.fromRect.center.y + (job.toRect.center.y - job.fromRect.center.y) * p
    // Škála odpovídá relativní velikosti targetu oproti přirozené (100×140 dp)
    val startScale = (job.fromRect.width / natW).coerceAtLeast(0.1f)
    val endScale   = (job.toRect.width   / natW).coerceAtLeast(0.1f)
    val curScale   = startScale + (endScale - startScale) * p

    // Levý horní roh letící karty (karta je přirozeně 100×140 dp, škálováno ze středu)
    val topLeftX = curCx - natW / 2f
    val topLeftY = curCy - natH / 2f

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = topLeftX.roundToInt(),
                        y = topLeftY.roundToInt()
                    )
                }
                .requiredSize(100.dp, 140.dp)
                .graphicsLayer {
                    scaleX = curScale
                    scaleY = curScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        ) {
            CardView(card = job.card, canPlay = false, discardMode = false, onClick = {})
        }
    }
}

// ─── Ghost efekt ztracené karty (spálená / ukradená) ─────────────────────────

@Composable
private fun LossGhostView(flight: FlightOverlayState, ghost: LossGhost) {
    val stolen = ghost.action == CardAction.STOLEN
    // Explicitní fáze místo jedné easing křivky – jediný způsob, jak zaručit
    // skutečný "hold" čas s plnou viditelností. Spálení i krádež mají STEJNÉ
    // časování (dřív spálení bledlo mnohem rychleji – 450/650 ms – takže
    // nešlo v klidu přečíst, KTERÁ karta shořela):
    //   pop  (200 ms)  – rychlé zvětšení + barevné zvýraznění
    //   hold (1600 ms) – karta drží plně viditelná, jde v klidu přečíst
    //   fade (900 ms)  – stoupá a rozplývá se
    val popAnim  = remember(ghost.id) { Animatable(0f) }
    val fadeAnim = remember(ghost.id) { Animatable(0f) }
    LaunchedEffect(ghost.id) {
        popAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(1600L)
        fadeAnim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        flight.lossGhosts.remove(ghost)
    }
    val pop  = popAnim.value
    val fade = fadeAnim.value
    val tint = if (stolen) Color(0xFF9B59B6)   // fialová = ukradeno
               else        Color(0xFFE07B39)   // oranžová = spáleno

    val density = LocalDensity.current
    val risePx  = with(density) { 56.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        if (ghost.card != null) {
            // ── Moje karta: líc na pozici v ruce, podrží se, pak stoupá a mizí ──
            val natW = with(density) { 100.dp.toPx() }
            val natH = with(density) { 140.dp.toPx() }
            val baseScale = (ghost.rect.width / natW).coerceAtLeast(0.1f)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (ghost.rect.center.x - natW / 2f).roundToInt(),
                            y = (ghost.rect.center.y - natH / 2f - risePx * fade).roundToInt()
                        )
                    }
                    .requiredSize(100.dp, 140.dp)
                    .graphicsLayer {
                        // Jemné zvětšení (+5 %) – jen naznačí, že se s kartou něco děje
                        val s = baseScale * (1f + 0.05f * pop)
                        scaleX = s; scaleY = s
                        alpha  = 1f - fade
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                CardView(card = ghost.card, canPlay = false, discardMode = false, onClick = {})
                // Barevný nádech: nastoupí s popem, slábne s rozpouštěním
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = 0.45f * pop * (1f - 0.5f * fade)))
                )
                // Výrazný barevný obrys – hlavní vodítko, komu/co zmizelo
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.5.dp, tint.copy(alpha = 0.95f * pop * (1f - fade)), RoundedCornerShape(8.dp))
                )
            }
        } else if (ghost.backResId != null) {
            // ── Soupeřův rub: podrží se na pozici ve stripu, pak mizí ──
            val w = with(density) { ghost.rect.width.toDp() }
            val h = with(density) { ghost.rect.height.toDp() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = ghost.rect.left.roundToInt(),
                            y = (ghost.rect.top - risePx * 0.6f * fade).roundToInt()
                        )
                    }
                    .requiredSize(w, h)
                    .graphicsLayer {
                        val s = 1f + 0.05f * pop
                        scaleX = s; scaleY = s
                        alpha  = 1f - fade
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                Image(
                    painter            = painterResource(ghost.backResId),
                    contentDescription = null,
                    modifier           = Modifier.matchParentSize(),
                    contentScale       = ContentScale.Fit
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(tint.copy(alpha = 0.55f * pop * (1f - 0.5f * fade)))
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, tint.copy(alpha = 0.95f * pop * (1f - fade)), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
