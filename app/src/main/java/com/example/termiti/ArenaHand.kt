package com.example.termiti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Výška plochy ruky v rozložení Aréna. Je vyšší než samotný vějíř, aby se do ní vešla
 * zvětšená zaměřená karta. Plocha nemá vlastní gesta – dotyky mimo karty projdou dál
 * (tlačítka, hrady pod ní).
 */
internal val ARENA_HAND_BOX = 250.dp

/** Pás ruky v klidu (od spodní hrany): tažená karta nad ním se zahraje. */
internal val ARENA_HAND_BAND = 150.dp

/** Pozice karty ve vějíři: střed (v rámci plochy ruky) a natočení ve stupních. */
internal data class FanSlot(val x: Float, val y: Float, val rot: Float)

/**
 * Vějíř: karty rozložené po oblouku o poloměru [radius] kolem společného bodu. Střed
 * prostřední karty je [centerX], [centerY]; [down] = true → bod otáčení je pod kartami
 * (moje ruka, krajní karty klesají), false → nad nimi (ruka soupeře, krajní karty stoupají).
 * Úhel každé karty sedí přesně s její polohou na oblouku.
 */
internal fun fanSlot(i: Int, n: Int, step: Float, radius: Float, centerX: Float, centerY: Float, down: Boolean): FanSlot {
    val phi = (i - (n - 1) / 2f) * step / radius
    val dx  = radius * sin(phi)
    val dy  = radius * (1f - cos(phi))
    val deg = Math.toDegrees(phi.toDouble()).toFloat()
    return if (down) FanSlot(centerX + dx, centerY + dy, deg)
           else      FanSlot(centerX + dx, centerY - dy, -deg)
}

// Velikosti karty v ruce (násobek přirozených 100 × 140 dp)
private const val HAND_SCALE      = 0.95f   // v klidu
private const val HAND_ZOOM_SCALE = 1.30f   // přiblížená ruka
private const val FOCUS_SCALE     = 1.62f   // zaměřená karta – celý text jde v klidu přečíst

/**
 * Moje ruka v rozložení Aréna – vějíř jako v Hearthstone.
 *
 * - **V klidu** jsou karty menší a překrývají se v oblouku.
 * - **Klepnutí na kartu** ([onTapCard]) ruku přiblíží: karty se zvětší, rozprostřou skoro
 *   přes celou šířku (téměř bez překrytí) a oblouk se srovná. Klepnutá karta je navíc
 *   **zaměřená** – narovná se, vyjede nahoru, zvětší se a ostatní jí uhnou, takže jde
 *   přečíst celý popis. Dotyk jiné karty v přiblížené ruce ([onPressCard]) přepne zaměření.
 * - Tažení karty ruku hned vrátí do klidu (řeší GameScreen přes dragGhost).
 *
 * Natočení a zvětšení je ve vrstvě VNĚ gest, takže dotyková plocha odpovídá tomu, co je
 * vidět (v LazyRow se oblouk kreslil až uvnitř gesta a karty šly chytit jen za levou část).
 * Zdroj letu/tažení je nenatočená poloha karty – tažená kopie má přirozenou velikost.
 */
@Composable
fun ArenaHand(
    hand: List<Card>,
    isPlayerTurn: Boolean,
    playerResources: Map<ResourceType, Int>,
    onPlayCard: (Card) -> Unit,
    onDiscardCard: (Card) -> Unit,
    playerWallHp: Int,
    playerCastleHp: Int,
    oppResources: Map<ResourceType, Int>,
    lastPlayedType: String?,
    onLongPressCard: ((Card) -> Unit)?,
    animateDraws: Boolean,
    canDiscard: Boolean,
    zoomed: Boolean,
    focusKey: String?,
    onPressCard: (String) -> Unit,
    onTapCard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val flight   = LocalFlightOverlay.current
    val dragMode = GameSettings.dragToPlay && flight != null
    val density  = LocalDensity.current
    var handRect by remember { mutableStateOf<Rect?>(null) }
    val pressCard by rememberUpdatedState(onPressCard)
    val tapCard   by rememberUpdatedState(onTapCard)
    val dragging = flight?.dragGhost != null
    var pressedKey by remember { mutableStateOf<String?>(null) }
    val zoom     = zoomed && !dragging
    val bandPx   = with(density) { ARENA_HAND_BAND.toPx() }

    // Unikátní klíče (online server může poslat dvě karty se stejným id) – viz HandPanel
    val handKeys = run {
        val seen = HashMap<String, Int>()
        hand.map { card ->
            val n = seen.getOrDefault(card.id, 0)
            seen[card.id] = n + 1
            if (n == 0) card.id else "${card.id}#dup$n"
        }
    }
    // Animace lízání: nové karty přilétají zprava (od balíčku), úvodní ruka ne
    val dealtIds = remember { mutableStateOf(hand.map { it.id }.toSet()) }
    val newIds   = if (animateDraws) hand.map { it.id }.filter { it !in dealtIds.value } else emptyList()
    LaunchedEffect(handKeys) { dealtIds.value = hand.map { it.id }.toSet() }

    // Ztmavení za přiblíženou rukou – světlé pozadí bojiště pak neruší čtení karet
    val dim by animateFloatAsState(if (zoom) 1f else 0f, tween(220), label = "handDim")

    BoxWithConstraints(modifier.onGloballyPositioned { handRect = it.boundsInRoot() }) {
        if (dim > 0.01f) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ARENA_HAND_BOX)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f * dim))))
        )

        val n      = hand.size
        val px     = { v: Float -> v * density.density }       // dp → px
        val W      = constraints.maxWidth.toFloat()
        val H      = constraints.maxHeight.toFloat()
        val cx     = W / 2f
        val cardW  = px(100f)
        val cardH  = px(140f)

        // ── Klid: oblouk, krok po oblouku 44–80 dp (5 karet skoro vedle sebe, plná ruka víc přes sebe)
        val restStep = if (n <= 1) 0f else px(((330f / (n - 1)).coerceIn(44f, 80f)) * HAND_SCALE)
        // ── Přiblížení: skoro rovná řada přes celou šířku, mezi kartami jen malá mezera
        val zW       = cardW * HAND_ZOOM_SCALE
        val zoomStep = if (n <= 1) 0f else minOf(zW + px(4f), (W - px(24f) - zW) / (n - 1))
        val step     = if (zoom) zoomStep else restStep
        val radius   = if (zoom) px(3200f) else px(900f * HAND_SCALE)
        val scale    = if (zoom) HAND_ZOOM_SCALE else HAND_SCALE
        val margin   = if (zoom) px(8f) else px(21f)
        val cy       = H - margin - cardH / 2f * scale

        val focusIdx = if (zoom) handKeys.indexOf(focusKey) else -1
        // Sousedé zaměřené karty uhnou do stran, aby zvětšená karta nikoho nepřekrývala
        val push = if (focusIdx < 0) 0f else ((cardW * FOCUS_SCALE - step) / 2f).coerceAtLeast(0f) * 0.75f

        hand.forEachIndexed { idx, card ->
            val handKey = handKeys[idx]
            key(handKey) {
                val focused = idx == focusIdx
                val slot = fanSlot(idx, n, step, radius, cx, cy, down = true)
                val fw   = cardW * FOCUS_SCALE / 2f + px(8f)
                val tx = when {
                    focused        -> slot.x.coerceIn(fw, W - fw)
                    focusIdx < 0   -> slot.x
                    idx < focusIdx -> slot.x - push
                    else           -> slot.x + push
                }
                val lifted = !focused && pressedKey == handKey && !zoom
                val ty = when {
                    focused -> H - px(6f) - cardH / 2f * FOCUS_SCALE
                    lifted  -> slot.y - px(16f)
                    else    -> slot.y
                }
                val tr = if (focused) 0f else slot.rot
                val ts = when { focused -> FOCUS_SCALE; lifted -> scale * 1.06f; else -> scale }

                val spec = spring<Float>(dampingRatio = 0.82f, stiffness = 520f)
                val ax by animateFloatAsState(tx, spec, label = "fanX")
                val ay by animateFloatAsState(ty, spec, label = "fanY")
                val ar by animateFloatAsState(tr, spec, label = "fanRot")
                val asc by animateFloatAsState(ts, spec, label = "fanScale")

                val affordable = card.isXCost || (playerResources[card.costType] ?: 0) >= card.effectiveCost
                val playable   = isPlayerTurn && affordable
                val discardOk  = isPlayerTurn && canDiscard

                val appearIdx = remember(card.id) { newIds.indexOf(card.id) }
                val flyIn     = remember(card.id) { Animatable(if (appearIdx >= 0) 1f else 0f) }
                var flyStartPx by remember(card.id) { mutableFloatStateOf(0f) }
                if (appearIdx >= 0) LaunchedEffect(card.id) {
                    kotlinx.coroutines.delay(DRAW_STAGGER_MS * appearIdx)
                    SoundManager.playCardDraw()
                    flyIn.animateTo(0f, tween(durationMillis = DRAW_FLIGHT_MS, easing = FastOutSlowInEasing))
                }

                Box(
                    Modifier
                        // Zaměřená karta je nad ostatními (i pro dotyk)
                        .zIndex(if (focused) 1000f else if (pressedKey == handKey) 500f else idx.toFloat())
                        .offset { IntOffset((ax - cardW / 2f).roundToInt(), (ay - cardH / 2f).roundToInt()) }
                        .size(100.dp, 140.dp)
                        // Zdroj letu/tažení = nenatočená poloha karty (vně natočení a zvětšení)
                        .trackFlightSource(card.id)
                        .onGloballyPositioned { c ->
                            flyStartPx = c.findRootCoordinates().size.width - c.positionInRoot().x
                        }
                        // Natočení, zvětšení a přílet líznuté karty. Gesta jsou až ZA touto
                        // vrstvou → dotyková plocha je natočená a zvětšená stejně jako karta.
                        .graphicsLayer {
                            val p = flyIn.value
                            val startX = if (flyStartPx > 0f) flyStartPx else 700.dp.toPx()
                            translationX = p * startX
                            translationY = -p * 30.dp.toPx()
                            rotationZ    = ar + p * 8f
                            scaleX = asc; scaleY = asc
                            alpha = when {
                                p > 0.98f -> 0f                                   // čeká na svůj delay
                                flight?.isHiddenByDrag(card.id) == true -> 0f     // táhne se kopie
                                else -> 1f
                            }
                        }
                        // Dotyk = zaměřit (v přiblížené ruce), klepnutí = přiblížit a zaměřit.
                        // Jen sleduje (Initial), nic nekonzumuje – tažení a dlouhý stisk běží dál.
                        .then(if (dragMode) Modifier.pointerInput(handKey) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                pressCard(handKey)
                                pressedKey = handKey
                                var moved = false
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                        if (!ch.pressed) {
                                            if (!moved && ch.uptimeMillis - down.uptimeMillis < 350) tapCard(handKey)
                                            break
                                        }
                                    }
                                } finally {
                                    if (pressedKey == handKey) pressedKey = null
                                }
                            }
                        } else Modifier)
                        .then(if (dragMode && isPlayerTurn) Modifier.cardDragToPlay(
                            card         = card,
                            canPlay      = playable,
                            canDiscard   = discardOk,
                            // Zóny tažení podle pásu ruky v klidu (plocha ruky je vyšší kvůli zaměřené kartě)
                            handBounds   = { handRect?.let { Rect(it.left, it.bottom - bandPx, it.right, it.bottom) } },
                            onPlay       = { onPlayCard(card) },
                            onDiscard    = { onDiscardCard(card) },
                            anyDirection = true,
                            rotation     = { ar }
                        ) else Modifier)
                ) {
                    CardView(
                        card         = card,
                        canPlay      = playable,
                        isComboCard  = card.isCombo,
                        discardMode  = false,
                        onClick      = { if (!dragMode) onPlayCard(card) },
                        onDiscard    = if (discardOk && !dragMode) { { onDiscardCard(card) } } else null,
                        onLongPress  = if (onLongPressCard != null) { { onLongPressCard(card) } } else null,
                        xPreview     = if (card.isXCost) (playerResources[card.costType] ?: 0) else null,
                        conditionMet = cardConditionMet(card, playerResources, playerWallHp, playerCastleHp, oppResources, lastPlayedType)
                    )
                }
            }
        }
    }
}
