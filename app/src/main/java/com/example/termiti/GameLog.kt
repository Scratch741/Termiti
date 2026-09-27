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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.compositeOver
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
        is LogEntry.AbilityEvent -> LogAbilityRow(entry, rowAlpha)
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
            val actorLabel = logActorLabel(entry.actorName)
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
                // ── Miniatura karty + cena v levém horním rohu (jako na kartě) ──
                val artId = entry.card.effectiveArtResId()
                Box(Modifier.size(width = 36.dp, height = 44.dp)) {
                Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(width = 30.dp, height = 40.dp)
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
                    LogCostBadge(entry, Modifier.align(Alignment.TopStart))
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
                            // Kolo výrazně – orientace v logu podle kol
                            Text(
                                text       = "T${entry.turn}",
                                color      = TextPrimary.copy(alpha = 0.90f * rowAlpha),
                                fontSize   = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // ── řádek 2: název karty + odznaky hned za ním ────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = entry.card.displayName,
                            color      = Gold.copy(alpha = 0.92f),
                            fontSize   = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                        LogCardBadges(entry, Modifier.padding(start = 5.dp))
                    }
                    // ── řádek 3: popis karty (barva textu jako na kartě) ──────
                    if (entry.card.displayDescription.isNotBlank()) {
                        Text(
                            text       = parseCardDesc(entry.card.displayDescription),
                            color      = Color(0xFFDDD0B0).copy(alpha = 0.88f),
                            fontSize   = 8.sp,
                            lineHeight = 10.sp,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * actorName je v záznamu uložen jako literál "Hráč"/"AI" → překládá se až při zobrazení.
 * U hráče má přednost jméno z profilu (stejně jako v top baru a v replayi); logActorPlayer
 * je jen záloha, když profil chybí nebo je jméno prázdné. Online posílá rovnou skutečná
 * jména, ta projdou větví else beze změny.
 */
@Composable
private fun logActorLabel(actorName: String): String {
    val s = LocalStrings.current
    return when (actorName) {
        "Hráč" -> PlayerProfileManager.profile?.name?.takeIf { it.isNotBlank() } ?: s.logActorPlayer
        "AI"   -> s.logActorAi
        else   -> actorName
    }
}

/**
 * Pasivní schopnost ve stejném rozvržení jako karta: vlevo ikona schopnosti (stejná jako
 * v profilu) s avatarem vlastníka v rohu, vpravo kdo · „pasivní schopnost“, název, popis.
 */
@Composable
private fun LogAbilityRow(entry: LogEntry.AbilityEvent, rowAlpha: Float) {
    val ownerColor = if (entry.isMe) TealLight else Crimson
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .drawBehind { drawRect(color = ownerColor.copy(alpha = 0.06f * rowAlpha)) }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Box(Modifier.size(width = 36.dp, height = 44.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(width = 30.dp, height = 40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Gold.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            ) {
                Image(
                    painter            = painterResource(entry.ability.iconRes),
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp)
                )
            }
            LogOwnerAvatar(entry, Modifier.align(Alignment.TopStart))
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = logActorLabel(entry.actorName),
                    color      = ownerColor,
                    fontSize   = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.widthIn(max = 60.dp)
                )
                Text(
                    text     = " · ${LocalStrings.current.logVerbAbility}",
                    color    = ownerColor.copy(alpha = 0.85f),
                    fontSize = 7.sp
                )
            }
            Text(
                text       = entry.ability.localizedTitle(),
                color      = Gold.copy(alpha = 0.92f),
                fontSize   = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text       = entry.ability.localizedDescription(),
                color      = Color(0xFFDDD0B0).copy(alpha = 0.88f),
                fontSize   = 8.sp,
                lineHeight = 10.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Avatar vlastníka schopnosti v rohu ikony – kolečko s rámečkem v barvě strany.
 * Portréty kampaně (goblin_*, hory_*, bazina_*) i ikony hráče/soupeře; neznámé jméno
 * (emoji avatar staršího soupeře) spadne na výchozí ikonu strany.
 */
@Composable
private fun LogOwnerAvatar(entry: LogEntry.AbilityEvent, modifier: Modifier = Modifier) {
    val resId = avatarDrawableRes(entry.ownerAvatar)
        ?: avatarResId(entry.ownerAvatar)
        ?: if (entry.isMe) R.drawable.player_icon_1 else R.drawable.enemy_icon_1
    Image(
        painter            = painterResource(resId),
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        modifier           = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .border(1.dp, (if (entry.isMe) TealLight else Crimson).copy(alpha = 0.9f), CircleShape)
    )
}

/**
 * Cena karty v rohu náhledu – kolečko v barvě suroviny jako na kartě. Číslo = kolik se
 * zaplatilo; u X-karty „X“, nebo „X6“ když víme, kolik se utratilo (pak je to oválek).
 * Barva čísla jako v ruce: zelená = sleva, červená = zdražení.
 */
@Composable
private fun LogCostBadge(entry: LogEntry.CardEvent, modifier: Modifier = Modifier) {
    val card = entry.card
    val label = when {
        card.isXCost && entry.paidCost != null -> "X${entry.paidCost}"
        card.isXCost                           -> "X"
        else                                   -> "${entry.paidCost ?: card.effectiveCost}"
    }
    val textColor = when {
        card.isXCost          -> Color.White
        card.costModifier < 0 -> Color(0xFF00E676)
        card.costModifier > 0 -> Color(0xFFFF5252)
        else                  -> Color.White
    }
    val res = resourceColor(card.costType)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 16.dp)
            .widthIn(min = 16.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(res, Color.Black.copy(alpha = 0.85f).compositeOver(res))))
            .border(1.dp, Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 3.dp)
    ) {
        Text(
            label,
            color = textColor,
            style = TextStyle(
                fontSize        = 9.sp,
                fontWeight      = FontWeight.ExtraBold,
                textAlign       = TextAlign.Center,
                platformStyle   = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim      = LineHeightStyle.Trim.Both
                )
            )
        )
    }
}

/** Odznaky jako na kartě v ruce: vygenerovaná (kladívko), combo (⚡), efekt při zahození (lebka). */
@Composable
private fun LogCardBadges(entry: LogEntry.CardEvent, modifier: Modifier = Modifier) {
    val card = entry.card
    val combo = entry.asCombo || card.isCombo
    if (!card.isGenerated && !combo && card.discardEffects.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (card.isGenerated) LogBadge(Color(0xFFFFD54F)) {
            Image(painterResource(R.drawable.hammer_icon), contentDescription = null, modifier = Modifier.size(9.dp))
        }
        if (combo) LogBadge(ComboYellow) {
            Text("⚡", fontSize = 8.sp, lineHeight = 8.sp, textAlign = TextAlign.Center)
        }
        if (card.discardEffects.isNotEmpty()) LogBadge(DiscardRed) {
            Image(painterResource(R.drawable.skull_icon), contentDescription = null, modifier = Modifier.size(9.dp))
        }
    }
}

@Composable
private fun LogBadge(borderColor: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) { content() }
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
                Text(LocalStrings.current.logGameStarts, color = TextMuted, fontSize = 10.sp,
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

