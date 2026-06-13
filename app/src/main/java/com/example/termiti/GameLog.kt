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

// ─── Log Entry Row ─────────────────────────────────────────────────────────────

@Composable
private fun LogEntryRow(entry: LogEntry, rowAlpha: Float = 1f) {
    when (entry) {
        is LogEntry.SystemEvent -> {
            Text(
                text       = parseCardDesc(entry.message),
                color      = TextMuted.copy(alpha = 0.8f * rowAlpha),
                fontSize   = 9.sp,
                lineHeight = 12.sp,
                fontStyle  = FontStyle.Italic,
                modifier   = Modifier
                    .fillMaxWidth()
                    .alpha(rowAlpha)
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            )
        }
        is LogEntry.CardEvent -> {
            val s = LocalStrings.current
            val actionColor = when (entry.action) {
                CardAction.PLAYED    -> if (entry.isMe) TealLight else Crimson
                CardAction.DISCARDED -> TextMuted
                CardAction.BURNED    -> ChaosOrange
                CardAction.STOLEN    -> MagicPurple
            }
            val actionLabel = when (entry.action) {
                CardAction.PLAYED    -> s.logVerbPlayed
                CardAction.DISCARDED -> s.logVerbDiscarded
                CardAction.BURNED    -> s.logVerbBurned
                CardAction.STOLEN    -> s.logVerbStolen
            }
            // actorName je uloženo jako literál "Hráč"/"AI" → lokalizuj při zobrazení
            val actorLabel = when (entry.actorName) {
                "Hráč" -> s.logActorPlayer
                "AI"   -> s.logActorAi
                else   -> entry.actorName
            }
            val rc = rarityColor(entry.card.rarity)

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(rowAlpha)
                    .drawBehind {
                        drawRect(color = actionColor.copy(alpha = 0.06f * rowAlpha))
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                // ── Miniatura karty ───────────────────────────────────────────
                val artId = entry.card.effectiveArtResId()
                Box(
                        modifier = Modifier
                            .size(width = 27.dp, height = 36.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .border(1.dp, rc.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                    ) {
                        Image(
                            painter        = painterResource(artId),
                            contentDescription = null,
                            contentScale   = ContentScale.Crop,
                            modifier       = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = ArtDefaults.SCALE * entry.card.artScale
                                    scaleX = s; scaleY = s
                                    transformOrigin = TransformOrigin(
                                        pivotFractionX = ((ArtDefaults.BIAS_X + entry.card.artBiasX + 1f) / 2f).coerceIn(0f, 1f),
                                        pivotFractionY = ((ArtDefaults.BIAS_Y + entry.card.artBiasY + 1f) / 2f).coerceIn(0f, 1f)
                                    )
                                }
                        )
                    }
                    Spacer(Modifier.width(6.dp))

                // ── Textová část ─────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    // ── řádek 1: aktor · sloveso ... kolo ─────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = actorLabel,
                            color      = if (entry.isMe) TealLight else Crimson,
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.widthIn(max = 60.dp)
                        )
                        Text(
                            text  = " · $actionLabel",
                            color = actionColor.copy(alpha = 0.85f),
                            fontSize = 7.sp
                        )
                        Spacer(Modifier.weight(1f))
                        if (entry.turn > 0) {
                            Text(
                                text     = "T${entry.turn}",
                                color    = TextMuted.copy(alpha = 0.50f * rowAlpha),
                                fontSize = 7.sp
                            )
                        }
                    }
                    // ── řádek 2: název karty ──────────────────────────────────
                    Text(
                        text       = entry.card.displayName,
                        color      = Gold.copy(alpha = 0.92f),
                        fontSize   = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    // ── řádek 3: popis karty ──────────────────────────────────
                    if (entry.card.displayDescription.isNotBlank()) {
                        Text(
                            text       = parseCardDesc(entry.card.displayDescription),
                            color      = TextMuted.copy(alpha = 0.72f),
                            fontSize   = 7.sp,
                            lineHeight = 9.sp,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── Log ──────────────────────────────────────────────────────────────────────
@Composable
fun LogPanel(
    log: List<LogEntry>,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false   // true = scrollovatelný overlay (nejnovější nahoře)
) {
    if (!scrollable) {
        // ── Kompaktní styl (poslední záznamy) ─────────────────────────────────
        Column(modifier = modifier.background(BgDeep).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("LOG", color = TextMuted, fontSize = 9.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (log.isEmpty()) {
                Text("— Hra začíná —", color = TextMuted, fontSize = 10.sp,
                    fontStyle = FontStyle.Italic)
            } else {
                val recent = log.takeLast(5)
                recent.forEach { entry ->
                    LogEntryRow(entry, 1f)
                }
            }
        }
    } else {
        // ── Scrollovatelný styl: nejnovější nahoře ────────────────────────────
        val reversed  = remember(log) { log.reversed() }
        val listState = rememberLazyListState()

        LaunchedEffect(log.size) {
            if (reversed.isNotEmpty()) listState.animateScrollToItem(0)
        }

        LazyColumn(
            state               = listState,
            modifier            = modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            reverseLayout       = false
        ) {
            itemsIndexed(reversed) { index, entry ->
                LogEntryRow(entry, 1f)
                if (index < reversed.lastIndex) {
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.08f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
internal fun ActionChip(label: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    PlainButton(
        text     = label,
        textColor = color,
        fontSize  = 10.sp,
        paddingH  = 10.dp,
        paddingV  = 3.dp,
        onClick   = onClick
    )
}

