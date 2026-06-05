package com.example.termiti


import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Floating HP-delta animace ───────────────────────────────────────────────

private data class DeltaEvt(val id: Long, val delta: Int)

/**
 * Jedno plovoucí číslo: zelené (+N) při léčení / stavbě,
 * červené (-N) při poškození. Animuje se nahoru a postupně mizí.
 */
@Composable
private fun FloatingDeltaNumber(delta: Int, sizeSp: Float = 15f, startOffsetY: Dp = 0.dp) {
    val positive = delta > 0
    val color    = if (positive) Color(0xFF4CAF50) else Color(0xFFE53935)
    val text     = if (positive) "+$delta" else "$delta"

    val animY  = remember { Animatable(0f) }
    val alpha  = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { animY.animateTo(-32f, tween(2600, easing = EaseOutCubic)) }
        delay(1400)
        alpha.animateTo(0f, tween(1000))
    }

    // startOffsetY posune číslo na úroveň vrcholu hradu/hradby v rámci outer Boxu
    Box(
        modifier         = Modifier.fillMaxWidth().offset(y = startOffsetY),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text       = text,
            color      = color,
            fontSize   = sizeSp.sp,
            fontWeight = FontWeight.ExtraBold,
            style      = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color      = Color.Black,
                    offset     = Offset(0f, 1.5f),
                    blurRadius = 6f
                )
            ),
            modifier = Modifier.graphicsLayer {
                translationY = animY.value
                this.alpha   = alpha.value
            }
        )
    }
}

/**
 * Sleduje změny [hp] a pro každou změnu zobrazí plovoucí delta číslo.
 * Umísti jako overlay do Box nad vizuál hradu nebo hradby.
 */
@Composable
internal fun HpFloats(hp: Int, sizeSp: Float = 15f, startOffsetY: Dp = 0.dp) {
    val events = remember { mutableStateListOf<DeltaEvt>() }
    val prev   = remember { mutableIntStateOf(hp) }

    LaunchedEffect(hp) {
        val d = hp - prev.intValue
        prev.intValue = hp
        if (d == 0) return@LaunchedEffect
        val evt = DeltaEvt(System.nanoTime(), d)
        events += evt
        delay(3000)
        events -= evt
    }

    events.forEach { evt ->
        key(evt.id) { FloatingDeltaNumber(evt.delta, sizeSp, startOffsetY) }
    }
}

// ─── Resource delta badge ────────────────────────────────────────────────────
// Statický (+N / -N), nevznáší se – jen zobrazí na ~2 s a zmizí.

/** Jedno číslo změny suroviny: zelené pro přírůstek, červené pro úbytek. */
@Composable
private fun ResourceDeltaNumber(delta: Int) {
    val positive = delta > 0
    val color    = if (positive) Color(0xFF4CAF50) else Color(0xFFE53935)
    val text     = if (positive) "+$delta" else "$delta"
    val alpha    = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(1200)
        alpha.animateTo(0f, tween(900))
    }

    Text(
        text       = text,
        color      = color,
        fontSize   = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        style      = androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black, offset = Offset(0f, 1f), blurRadius = 4f
            )
        ),
        modifier   = Modifier.graphicsLayer { this.alpha = alpha.value }
    )
}

/**
 * Wrapper sledující změny [amount]: pro každou změnu zobrazí [ResourceDeltaNumber].
 * Vkládej do Row vedle čísla suroviny.
 */
@Composable
internal fun ResourceDelta(amount: Int, modifier: Modifier = Modifier) {
    val events = remember { mutableStateListOf<DeltaEvt>() }
    val prev   = remember { mutableIntStateOf(amount) }

    LaunchedEffect(amount) {
        val d = amount - prev.intValue
        prev.intValue = amount
        if (d == 0) return@LaunchedEffect
        val evt = DeltaEvt(System.nanoTime(), d)
        events += evt
        delay(2500)
        events -= evt
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        events.forEach { evt ->
            key(evt.id) { ResourceDeltaNumber(evt.delta) }
        }
    }
}

// ─── Mulligan Overlay ────────────────────────────────────────────────────────
@Composable
fun MulliganOverlay(
    hand: List<Card>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    submitted: Boolean = false,
    goesFirst: Boolean? = null,
    secondsLeft: Int? = null      // null = bez timeru (offline / WiFi)
) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE5000000))
            // Blokuje dotyky pod overlayem na pozadí (ne na kartách/tlačítkách).
            // detectTapGestures čeká jen na NEkonsumovaný DOWN — pokud karta DOWN
            // pohltí dřív (child = Main pass dříve), outer Box čeká dál a gesto
            // karty neruší. Pokud DOWN nespolykl nikdo (pozadí), outer Box ho
            // pohltí a game UI pod ním nic nedostane.
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(
                    Modifier.paint(
                        painterResource(R.drawable.mulligan_background),
                        contentScale = ContentScale.Crop
                    )
                )
                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Nadpis + timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    s.mulliganTitle,
                    color = Gold, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 5.sp
                )
                if (secondsLeft != null && !submitted) {
                    val timerColor = if (secondsLeft <= 10) Color(0xFFFF4444) else TextMuted
                    Text(
                        "${secondsLeft}s",
                        color = timerColor, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badge: kdo jde první (jen pokud je znám)
            if (goesFirst != null) {
                Text(
                    if (goesFirst) "⚔️ ${s.mulliganYouFirst}" else "⏳ ${s.mulliganOpponentFirst}",
                    color = if (goesFirst) Teal else TextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }

            // Podtitulek / čekání po odeslání
            if (submitted) {
                Text(
                    s.mulliganWaitingOpponent,
                    color = Teal, fontSize = 10.sp, textAlign = TextAlign.Center
                )
            } else {
                Text(
                    if (selectedIds.isEmpty())
                        s.mulliganInstruction
                    else
                        s.mulliganSelected.format(selectedIds.size),
                    color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center
                )
            }

            // Karty
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hand.forEach { card ->
                    val isSelected = card.id in selectedIds
                    Box(
                        modifier = Modifier.let {
                            if (!submitted) it.clickable { onToggle(card.id) } else it
                        }
                    ) {
                        CardView(
                            card        = card,
                            canPlay     = !isSelected && !submitted,
                            discardMode = isSelected,
                            onClick     = { if (!submitted) onToggle(card.id) },
                            showGlow    = false
                        )
                        // Overlay na vybrané kartě
                        if (isSelected) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DiscardRed.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↩", fontSize = 26.sp, color = Color.White)
                            }
                        }
                        // Ztmavení karet po odeslání
                        if (submitted) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
            }

            // Tlačítka — skrytá po odeslání
            if (!submitted) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Přeskočit
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gold.copy(alpha = 0.18f))
                            .border(1.5.dp, Gold.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                            .clickable { onSkip() }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s.mulliganPlayNoSwap,
                            color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }

                    // Vyměnit
                    val canConfirm = selectedIds.isNotEmpty()
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (canConfirm) Teal.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.03f)
                            )
                            .border(
                                1.dp,
                                if (canConfirm) TealLight.copy(alpha = 0.55f)
                                else TextMuted.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .then(if (canConfirm) Modifier.clickable { onConfirm() } else Modifier)
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (canConfirm) "${s.mulliganSwap} (${selectedIds.size})" else s.mulliganSwap,
                            color = if (canConfirm) TealLight else TextMuted.copy(alpha = 0.3f),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─── Lost Cards Overlay ───────────────────────────────────────────────────────
@Composable
fun LostCardsOverlay(lostCards: List<CardHistoryEntry>, onDismiss: () -> Unit, onMenu: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD8000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false, onClick = {})   // zamezí průchodu kliknutí
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1020), BgPanel)))
                .border(1.dp, Color(0xFF9B59B6).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Nadpis
            Text(
                "SPÁLENÉ & UKRADENÉ KARTY",
                color = Color(0xFF9B59B6), fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(
                "Karty, o které tě připravil soupeř.",
                color = TextMuted, fontSize = 10.sp
            )

            if (lostCards.isEmpty()) {
                Text(
                    "Žádná karta zatím nebyla spálena ani ukradena.",
                    color = TextMuted.copy(alpha = 0.6f), fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                // Scrollovatelná mřížka karet
                val scroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .horizontalScroll(scroll)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lostCards.forEach { entry ->
                        val badgeColor = if (entry.action == CardAction.STOLEN)
                            Color(0xFF9B59B6) else Color(0xFFE07B39)
                        val badgeText  = if (entry.action == CardAction.STOLEN) "🃏 UKRADENO" else "🔥 SPÁLENO"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .border(1.5.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            ) {
                                CardView(card = entry.card, canPlay = false, discardMode = false, onClick = {})
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(badgeText, color = badgeColor, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Tlačítka
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onMenu != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Crimson.copy(alpha = 0.08f))
                            .border(1.dp, Crimson.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onMenu)
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☰ Menu", color = Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 28.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Zavřít", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Game Over ────────────────────────────────────────────────────────────────
@Composable
fun GameOverDialog(result: GameResult, onRestart: () -> Unit, onMenu: () -> Unit, onReview: () -> Unit = {}, onReplay: () -> Unit = {}) {
    val s = LocalStrings.current
    val (title, sub) = when (result) {
        GameResult.PLAYER_CASTLE_DESTROYED -> s.resultDefeat  to s.resultCastleDestroyedSelf
        GameResult.AI_CASTLE_DESTROYED     -> s.resultVictory to s.resultCastleDestroyed
        GameResult.PLAYER_CASTLE_BUILT     -> s.resultVictory to s.resultCastleBuilt
        GameResult.AI_CASTLE_BUILT         -> s.resultDefeat  to s.resultCastleBuiltOpponent
        GameResult.PLAYER_HP_WINS          -> s.resultVictory to s.resultHpWins
        GameResult.AI_HP_WINS              -> s.resultDefeat  to s.resultHpLose
        GameResult.PLAYER_HP_WINS_TURN_LIMIT -> s.resultVictory to s.resultHpWinsTurnLimit
        GameResult.AI_HP_WINS_TURN_LIMIT   -> s.resultDefeat  to s.resultHpLoseTurnLimit
        GameResult.DRAW                    -> s.resultDraw    to s.resultHpDraw
        GameResult.DRAW_BOTH_DEAD          -> s.resultDraw    to s.resultBothDead
    }
    val isWin = result.isPlayerWin()

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(
                    if (isWin) listOf(Color(0xFF1A2A1A), BgPanel)
                    else listOf(Color(0xFF2A1010), BgPanel)
                ))
                .border(1.dp,
                    if (isWin) TealLight.copy(alpha = 0.5f) else Crimson.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isWin) "⚔️" else "💀", fontSize = 36.sp)
            Spacer(Modifier.height(10.dp))
            Text(title.uppercase(), color = if (isWin) TealLight else Crimson,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(sub, color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWin) Teal else CrimsonDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(s.resultPlayAgain.uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onReview,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2A35)),
                shape   = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📋 PROHLÉDNOUT HRU", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onMenu,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2030)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(s.resultBackToMenu.uppercase(), color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}

// ─── Arena Game Over Dialog ───────────────────────────────────────────────────
@Composable
fun ArenaGameOverDialog(
    result: GameResult,
    wins: Int,
    isPlayerWin: Boolean,
    onNextBattle: () -> Unit,
    onEndArena: () -> Unit
) {
    val (title, sub) = when (result) {
        GameResult.PLAYER_CASTLE_DESTROYED -> "Prohrál jsi"  to "Tvůj hrad byl zničen."
        GameResult.AI_CASTLE_DESTROYED     -> "Vítězství!"   to "Zničil jsi nepřátelský hrad."
        GameResult.PLAYER_CASTLE_BUILT     -> "Vítězství!"   to "Postavil jsi mocný hrad."
        GameResult.AI_CASTLE_BUILT         -> "Prohrál jsi"  to "Nepřítel dokončil svůj hrad."
        GameResult.PLAYER_HP_WINS          -> "Vítězství!"   to "Balíčky došly – tvůj hrad je vyšší."
        GameResult.AI_HP_WINS              -> "Prohrál jsi"  to "Balíčky došly – nepřítel má vyšší hrad."
        GameResult.PLAYER_HP_WINS_TURN_LIMIT -> "Vítězství!" to "Limit 99 kol – tvůj hrad je vyšší."
        GameResult.AI_HP_WINS_TURN_LIMIT   -> "Prohrál jsi"  to "Limit 99 kol – nepřítel má vyšší hrad."
        GameResult.DRAW                    -> "Remíza"       to "Balíčky došly – hrady jsou stejně vysoké."
        GameResult.DRAW_BOTH_DEAD          -> "Remíza"       to "Oba hrady byly zničeny současně."
    }

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(
                    if (isPlayerWin) listOf(Color(0xFF1A2A1A), BgPanel)
                    else listOf(Color(0xFF2A1010), BgPanel)
                ))
                .border(
                    1.dp,
                    if (isPlayerWin) TealLight.copy(alpha = 0.5f) else Crimson.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(if (isPlayerWin) "⚔️" else "💀", fontSize = 32.sp)
            Text(
                title.uppercase(),
                color = if (isPlayerWin) TealLight else Crimson,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(sub, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)

            // Win counter
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Gold.copy(alpha = 0.08f))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "Vítězství: $wins",
                    color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            if (isPlayerWin) {
                Button(
                    onClick = onNextBattle,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DALŠÍ BITVA", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            } else {
                Button(
                    onClick = onEndArena,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("UKONČIT ARÉNU", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}

