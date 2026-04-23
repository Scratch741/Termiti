// ============================================================
// FlightOverlay.kt
// Animace "karta letí z ruky hráče do discard slotu uprostřed"
// ============================================================
package com.example.termiti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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

    private var _counter: Long = 0L
    fun nextId(): Long = ++_counter

    /** True, pokud pro tuto kartu právě běží letová animace. */
    fun isFlying(cardId: String): Boolean = flying?.card?.id == cardId
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
