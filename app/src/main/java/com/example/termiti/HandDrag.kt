package com.example.termiti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Režim „Tažením“ ([GameSettings.dragToPlay]): kartu z ruky hráč přetáhne nad bojiště
 * a pustí (= zahraje), nebo ji stáhne dolů za spodní okraj (= zahodí). Puštění jinde
 * kartu vrátí do ruky.
 *
 * Tažená karta se kreslí ve vrstvě letu ([FlightOverlayState.dragGhost]); karta v ruce se
 * mezitím skryje (viz [isHiddenByDrag]). Při zahrání/zahození se puštěná pozice zapíše
 * do [FlightOverlayState.sources] a připíchne ([FlightOverlayState.pinnedSources]), takže
 * let do slotu uprostřed začne tam, kde hráč kartu pustil.
 *
 * Gesto začne jen převážně svislým pohybem – vodorovný tah dál posouvá ruku (LazyRow).
 *
 * @param handBounds  okraje panelu ruky (okenní souřadnice): střed karty nad horní hranou
 *                    = zahrát, pod spodní hranou (mimo obrazovku) = zahodit
 */
internal fun Modifier.cardDragToPlay(
    card: Card,
    canPlay: Boolean,
    canDiscard: Boolean,
    handBounds: () -> Rect?,
    onPlay: () -> Unit,
    onDiscard: () -> Unit,
    /** true = gesto začne pohybem libovolným směrem (ruka, která nejde posouvat – Aréna). */
    anyDirection: Boolean = false,
    /** Natočení karty v ruce (vějíř Arény) – tažená kopie se z něj narovná. */
    rotation: () -> Float = { 0f }
): Modifier = composed {
    val flight = LocalFlightOverlay.current ?: return@composed this
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Tažená karta má vždy přirozenou velikost (100 × 140 dp) se středem tam, kde je karta
    // v ruce – i když je ruka zmenšená nebo přiblížená. Let zahrání pak začíná ve stejné velikosti.
    val natW = with(density) { 100.dp.toPx() }
    val natH = with(density) { 140.dp.toPx() }
    val scope  = rememberCoroutineScope()
    val play     by rememberUpdatedState(onPlay)
    val discard  by rememberUpdatedState(onDiscard)
    val playOk   by rememberUpdatedState(canPlay)
    val discOk   by rememberUpdatedState(canDiscard)
    // Konec tahu, konec hry nebo odchod z obrazovky uprostřed tažení zruší gesto –
    // tažená kopie nesmí zůstat viset a karta v ruce skrytá.
    DisposableEffect(card.id) {
        onDispose { if (flight.dragGhost?.card?.id == card.id) flight.dragGhost = null }
    }

    fun zoneOf(r: Rect): DragZone {
        val hb = handBounds() ?: return DragZone.NONE
        return when {
            r.center.y < hb.top    -> DragZone.PLAY
            r.center.y > hb.bottom -> DragZone.DISCARD
            else                   -> DragZone.NONE
        }
    }

    /** Puštěná karta odchází (zahrání/zahození): let začne z [rect], karta v ruce zůstane skrytá. */
    fun release(rect: Rect, action: () -> Unit) {
        flight.pinnedSources[card.id] = true
        flight.sources[card.id] = rect
        flight.dragGhost = null
        action()
        // Odmítnuté zahrání (online server, tutoriál) → karta zůstala v ruce: znovu ji ukaž
        scope.launch {
            delay(1500)
            flight.pinnedSources.remove(card.id)
        }
    }

    this.pointerInput(card.id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                if (anyDirection || abs(over.y) > abs(over.x)) change.consume()
            } ?: return@awaitEachGesture
            val src  = flight.sources[card.id] ?: return@awaitEachGesture
            val base = Rect(src.center.x - natW / 2f, src.center.y - natH / 2f, src.center.x + natW / 2f, src.center.y + natH / 2f)

            var off = slop.position - down.position
            flight.dragGhost = DragGhost(card, base.translate(off), zoneOf(base.translate(off)), playOk, rotation())
            val finished = drag(slop.id) { ch ->
                off += ch.positionChange()
                ch.consume()
                val r = base.translate(off)
                flight.dragGhost = DragGhost(card, r, zoneOf(r), playOk)
            }

            val rect = base.translate(off)
            val zone = if (finished) zoneOf(rect) else DragZone.NONE
            when {
                zone == DragZone.PLAY && playOk -> release(rect) { play() }
                zone == DragZone.DISCARD && discOk -> scope.launch {
                    // Karta dojede dolů za okraj, pak se zahodí
                    val a = Animatable(off, Offset.VectorConverter)
                    a.animateTo(off + Offset(0f, rect.height), tween(140, easing = FastOutSlowInEasing)) {
                        flight.dragGhost = DragGhost(card, base.translate(value), DragZone.DISCARD)
                    }
                    release(base.translate(a.value)) { discard() }
                }
                else -> scope.launch {
                    // Návrat do ruky
                    val a = Animatable(off, Offset.VectorConverter)
                    a.animateTo(Offset.Zero, tween(200, easing = FastOutSlowInEasing)) {
                        flight.dragGhost = DragGhost(card, base.translate(value), DragZone.NONE, playOk)
                    }
                    if (flight.dragGhost?.card?.id == card.id) flight.dragGhost = null
                }
            }
        }
    }
}

/** True, když kartu z ruky právě nahrazuje tažená kopie nebo už odešla (čeká na let). */
internal fun FlightOverlayState.isHiddenByDrag(cardId: String): Boolean =
    dragGhost?.card?.id == cardId || pinnedSources.containsKey(cardId)
