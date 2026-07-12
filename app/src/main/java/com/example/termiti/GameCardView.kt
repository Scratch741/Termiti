package com.example.termiti


import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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

/**
 * Parsuje popis karty – text uzavřený v **…** se zobrazí tučně a velkými písmeny.
 * Příklad: "za každou **STAVBU** +3" → "STAVBU" bude bold.
 */
/**
 * Vykreslí popis karty s podporou:
 *  • **tučného** textu (mezi dvojicí `**`)
 *  • ručního zalomení řádku symbolem `|` (nebo literálem `\n`) → nový řádek
 * Příklad: "Zaútočí za 5.|**Toto kolo:** +2 útok." se zalomí za první větou.
 */
fun parseCardDesc(text: String): AnnotatedString = buildAnnotatedString {
    // Zalomení řádku: '|' i literál "\n" převedeme na skutečný nový řádek
    val normalized = text.replace("\\n", "\n").replace('|', '\n')
    val parts = normalized.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 0) append(part)
        else {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(part)
            pop()
        }
    }
}

internal fun effectIcon(card: Card) = when (card.effects.firstOrNull()) {
    is CardEffect.AttackPlayer      -> "⚔️"
    is CardEffect.AttackCastle      -> "🎯"
    is CardEffect.AttackWall        -> "💣"
    is CardEffect.BuildCastle       -> "🏰"
    is CardEffect.BuildWall         -> "🧱"
    is CardEffect.AddResource       -> "💰"
    is CardEffect.AddMine           -> "⛏️"
    is CardEffect.StealResource     -> "🗡️"
    is CardEffect.DrainResource     -> "☠️"
    is CardEffect.ConditionalEffect -> "🔮"
    is CardEffect.DestroyMine       -> "💥"
    is CardEffect.StealCard         -> "🃏"
    is CardEffect.BurnCard          -> "🔥"
    is CardEffect.AddCardsToDeck    -> "📦"
    is CardEffect.AddToOpponentDeck -> "💣"
    is CardEffect.TrapOnDraw        -> "💥"
    is CardEffect.DrawCard          -> "🎴"
    is CardEffect.StealCastle       -> "🧛"
    is CardEffect.AddResourceDelayed  -> "⏳"
    is CardEffect.BlockMine           -> "🚫"
    is CardEffect.XScaledAttackPlayer -> "⚔️"
    is CardEffect.XScaledAttackCastle -> "🎯"
    is CardEffect.XScaledBuildCastle  -> "🏰"
    is CardEffect.XScaledDualResource -> "💰"
    is CardEffect.SwapHands           -> "🔄"
    is CardEffect.RandomizeHands      -> "🎲"
    is CardEffect.GiveRandomCard      -> "🎴"
    is CardEffect.ModifyHandCost      -> "🏷️"
    is CardEffect.DrawPerCardPlayed        -> "🎴"
    is CardEffect.GainResourcePerCardPlayed -> "⚡"
    is CardEffect.GainCastlePerCardPlayed   -> "🏯"
    is CardEffect.ShapeShift                -> "🎭"
    is CardEffect.ConvertMine               -> "🔀"
    is CardEffect.DecisionBurnOpponent      -> "🔥"
    is CardEffect.DecisionChooseType        -> "🎯"
    is CardEffect.DecisionFromDiscard       -> "♻️"
    is CardEffect.DecisionFromDeck          -> "🔍"
    is CardEffect.DecisionDrawFromDeck      -> "📥"
    is CardEffect.DecisionMine              -> "⛏️"
    is CardEffect.DrawBoth                  -> "🎴"
    is CardEffect.CloneNextPlayed           -> "🔁"
    is CardEffect.SmartJoker                -> "🃏"
    is CardEffect.MomentumAttack            -> "⚡"
    is CardEffect.PeekAndStealHand          -> "🕵️"
    is CardEffect.DecisionChooseResource    -> "⚗️"
    is CardEffect.Mirror                    -> "🪞"
    is CardEffect.Clone                     -> "🧬"
    is CardEffect.NextCardIsCombo           -> "⚡"
    is CardEffect.DiscountRandomCard        -> "🏷️"
    null                              -> "❓"
}

/** Zobrazí ikonu efektu karty — PNG pro efekty s ikonou, emoji pro ostatní. */
@Composable
internal fun EffectIconView(card: Card, size: androidx.compose.ui.unit.Dp, fontSizeSp: Float) {
    val iconRes: Int? = when (card.effects.firstOrNull()) {
        is CardEffect.AttackPlayer, is CardEffect.XScaledAttackPlayer, is CardEffect.MomentumAttack -> R.drawable.utok_icon
        is CardEffect.BuildCastle, is CardEffect.XScaledBuildCastle                                 -> R.drawable.castle_icon
        is CardEffect.BuildWall                                                                      -> R.drawable.stavba_icon
        is CardEffect.AttackWall, is CardEffect.AttackCastle, is CardEffect.XScaledAttackCastle,
        is CardEffect.DestroyMine, is CardEffect.TrapOnDraw, is CardEffect.AddToOpponentDeck        -> R.drawable.explode_icon
        else -> null
    }
    if (iconRes != null) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(size))
    } else {
        Text(effectIcon(card), fontSize = fontSizeSp.sp)
    }
}

/** Mapuje ID skinu rubu karty na drawable resource. */
fun cardBackSkinDrawable(skinId: String): Int = when (skinId) {
    "card_back_frame_2" -> R.drawable.card_back_frame_2
    "card_back_frame_3" -> R.drawable.card_back_frame_3
    else                -> R.drawable.card_back_frame
}

/** Vrátí res-ID skinu rubu z profilu přihlášeného hráče. */
fun playerCardBackResId(): Int =
    cardBackSkinDrawable(PlayerProfileManager.profile?.cardBackSkin ?: "card_back_frame")

@Composable
fun CardBack(
    modifier:  Modifier = Modifier,
    skinResId: Int      = R.drawable.card_back_frame   // výchozí = základní rub (AI / neznámý hráč)
) {
    Image(
        painter            = painterResource(skinResId),
        contentDescription = null,
        contentScale       = ContentScale.FillBounds,
        modifier           = modifier.size(width = 22.dp, height = 32.dp)
    )
}

/** Vrátí název drawable rámu podle typu zdroje karty. */
private fun cardFrameName(costType: ResourceType) = when (costType) {
    ResourceType.MAGIC  -> "card_frame_magic"
    ResourceType.ATTACK -> "card_frame_attack"
    ResourceType.CHAOS  -> "card_frame_chaos"
    ResourceType.STONES -> "card_frame_stones"
}

/** Miniatura líce karty – stejná velikost jako CardBack (22×32 dp). */
@Composable
fun MiniCardFront(card: Card, modifier: Modifier = Modifier, borderColor: Color? = null) {
    val borderColor = borderColor ?: rarityColor(card.rarity)
    val context = LocalContext.current
    val frameResId = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
    }
    Box(
        modifier = modifier
            .size(width = 22.dp, height = 32.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, borderColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(16.dp)
                .clipToBounds()
        ) {
            Image(
                painter = painterResource(card.effectiveArtResId()),
                contentDescription = null,
                modifier = artModifier(card),
                contentScale = ContentScale.Crop,
                alignment = artAlignment(card)
            )
        }
        if (frameResId != 0) {
            Image(
                painter = painterResource(frameResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        // Cena v levém horním rohu
        val miniCostColor = when {
            card.isXCost           -> Color.White
            card.costModifier < 0  -> Color(0xFF00E676)
            card.costModifier > 0  -> Color(0xFFFF5252)
            else                   -> Color.White
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 1.dp, y = 1.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(2.dp))
                .padding(horizontal = 1.5.dp, vertical = 0.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (card.isXCost) "X" else "${card.effectiveCost}", color = miniCostColor, fontSize = 6.sp, fontWeight = FontWeight.ExtraBold)
        }
        // 🔨 odznak – vygenerovaná karta (vpravo nahoře)
        if (card.isGenerated) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(0.5.dp, Color(0xFFFFD54F).copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.hammer_icon), contentDescription = null, modifier = Modifier.size(5.dp))
            }
        }
        // Název karty ve spodní části mini karty
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(15.dp)
                .background(Color.Black.copy(alpha = 0.40f))
                .padding(horizontal = 1.5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                card.displayName,
                color      = Color.White,
                fontSize   = 3.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 4.5.sp,
                style      = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

// Zvýrazněná odhalená mini karta – poslední zahraná AI kartou
@Composable
internal fun CardBackPlayed(card: Card) {
    val costColor = resourceColor(card.costType)
    val modifiedCostColor = when {
        card.isXCost           -> costColor
        card.costModifier < 0  -> Color(0xFF00E676)
        card.costModifier > 0  -> Color(0xFFFF5252)
        else                   -> costColor
    }
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BgCard)
            .border(1.5.dp, Crimson, RoundedCornerShape(4.dp))
    ) {
        // Efektová ikona – nahoře uprostřed
        Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp)) {
            EffectIconView(card, size = 14.dp, fontSizeSp = 11f)
        }
        // Název karty – dole
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 2.dp, vertical = 1.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(card.displayName, color = TextPrimary, fontSize = 5.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 6.sp)
        }
        // Cena – nahoře vlevo
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 2.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Image(painterResource(resourceIconRes(card.costType)), contentDescription = null, modifier = Modifier.size(7.dp))
                Text(if (card.isXCost) "X" else "${card.effectiveCost}", color = modifiedCostColor, fontSize = 5.5.sp)
            }
        }
    }
}

/** Slot zahrané karty soupeře v pruhu ruky – miniatura artu s červeným okrajem. */
@Composable
internal fun PlayedCardSlot(card: Card) {
    MiniCardFront(card = card, borderColor = Crimson)
}


/**
 * Pro X-kost kartu vrátí suffix popisu " **(N)**" (tučně, parseCardDesc) s hodnotou,
 * kterou efekt PRÁVĚ TEĎ vyprodukuje (dmg / suroviny / hrad) podle dostupných
 * surovin [available]. Pro ostatní karty (nebo bez známých surovin) vrátí "".
 */
fun xPreviewSuffix(card: Card, available: Int?): String {
    if (available == null || !card.isXCost) return ""
    val n = card.effects.firstNotNullOfOrNull { fx ->
        when (fx) {
            is CardEffect.XScaledAttackPlayer -> available / fx.divisor
            is CardEffect.XScaledAttackCastle -> available / fx.divisor
            is CardEffect.XScaledBuildCastle  -> available / fx.divisor
            is CardEffect.XScaledDualResource -> available / fx.divisor
            else -> null
        }
    } ?: return ""
    return " **($n)**"
}

// ─── Card ─────────────────────────────────────────────────────────────────────
@Composable
fun CardView(
    card: Card,
    canPlay: Boolean,
    discardMode: Boolean,
    onClick: () -> Unit,
    onDiscard: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    isComboCard: Boolean = card.isCombo,
    conditionMet: Boolean? = null,  // null = karta nemá podmínku
    showFade: Boolean = true,       // false = vždy plná opacity (např. zahraná karta uprostřed)
    showGlow: Boolean = true,
    xPreview: Int? = null           // X-kost: dostupné suroviny → náhled hodnoty v popisu
) {
    val offsetY   = remember { Animatable(0f) }
    val scope     = rememberCoroutineScope()
    val density   = LocalDensity.current
    val threshold = remember(density) { with(density) { 68.dp.toPx() } }
    val progress  = (-offsetY.value / threshold).coerceIn(0f, 1f)
    val isDragging = offsetY.value < -6f

    // ── Zelený glow: zahratelná karta se splněnou podmínkou ───────────────────
    val showGreenGlow = showGlow && canPlay && conditionMet == true
    val glowTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.35f,
        targetValue  = 0.75f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val GlowGreen = Color(0xFF4DB86E)

    // ── Fialový glow: Shapeshifter karta (i po transformaci – trackovano přes ID prefix) ──
    // Sekundární check přes localizationId kryjí online morph karty, u nichž efekty
    // nebyly zachovány kvůli fallbacku v parseCardArray.
    val isShapeShifter = card.isShapeShifterInstance()
        || card.effects.any { it is CardEffect.Mirror || it is CardEffect.Clone }
        || card.localizationId == "__mirror__" || card.localizationId == "__clone__"
    val purpleGlowAlpha by glowTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purpleGlowAlpha"
    )

    val dragModifier = if (onDiscard != null) Modifier.pointerInput(card.id) {
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    if (offsetY.value <= -threshold) onDiscard()
                    offsetY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 280f))
                }
            },
            onDragCancel = { scope.launch { offsetY.animateTo(0f, spring()) } },
            onVerticalDrag = { _, delta ->
                scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtMost(0f)) }
            }
        )
    } else Modifier

    // ── Textured card layout (art_default pro karty bez vlastního artu) ─────
    CardViewTextured(
        card           = card,
        artResId       = card.effectiveArtResId(),
        canPlay        = canPlay,
        discardMode    = discardMode,
        isDragging     = isDragging,
        progress       = progress,
        offsetY        = offsetY,
        conditionMet   = conditionMet,
        isComboCard    = isComboCard,
        dragModifier   = dragModifier,
        showFade       = showFade,
        showGreenGlow  = showGreenGlow,
        glowAlpha      = glowAlpha,
        showPurpleGlow = isShapeShifter,
        purpleGlowAlpha = purpleGlowAlpha,
        onClick        = onClick,
        onLongPress    = onLongPress,
        xPreview       = xPreview
    )
}

// ── Textured card view ────────────────────────────────────────────────────────
@Composable
private fun CardViewTextured(
    card: Card,
    artResId: Int,
    canPlay: Boolean,
    discardMode: Boolean,
    isDragging: Boolean,
    progress: Float,
    offsetY: Animatable<Float, *>,
    conditionMet: Boolean?,
    isComboCard: Boolean,
    dragModifier: Modifier,
    showFade: Boolean = true,
    showGreenGlow: Boolean = false,
    glowAlpha: Float = 0f,
    showPurpleGlow: Boolean = false,
    purpleGlowAlpha: Float = 0f,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    xPreview: Int? = null
) {
    val context = LocalContext.current
    // Načtení rámu karty dynamicky podle costType (card_frame_magic/attack/chaos/stones).
    // Pokud soubor neexistuje, vrátí 0 a rám se nepřikresluje.
    val frameResId = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
    }

    val GlowGreen  = Color(0xFF4DB86E)
    val GlowPurple = Color(0xFFBB66FF)

    // Zahratelná Shapeshifter: breathing animace; nezahratelná: statická ztlumená fialová
    val effectivePurpleAlpha = when {
        !showPurpleGlow        -> 0f
        canPlay                -> purpleGlowAlpha          // 0.5–1.0 breathing
        else                   -> 0.30f                    // statická, ztlumená
    }

    val borderColor = when {
        isDragging             -> DiscardRed.copy(alpha = 0.35f + progress * 0.6f)
        discardMode            -> DiscardRed.copy(alpha = 0.8f)
        showPurpleGlow         -> GlowPurple.copy(alpha = effectivePurpleAlpha)
        canPlay && isComboCard -> ComboYellow
        showGreenGlow          -> GlowGreen.copy(alpha = glowAlpha)
        canPlay                -> Gold
        else                   -> Color.Transparent
    }
    val borderWidth = if (showPurpleGlow) 2.dp else 1.5.dp
    val bgColor = when {
        showPurpleGlow && canPlay  -> Color(0xFF1A0830)    // živá tmavě fialová
        showPurpleGlow             -> Color(0xFF0F0520)    // tmavší = nezahratelná
        showGreenGlow              -> Color(0xFF0D1E12)
        else                       -> BgCard
    }

    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 140.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .then(dragModifier)
            .drawBehind {
                if (showPurpleGlow) {
                    val cr = CornerRadius(10.dp.toPx())
                    repeat(4) { i ->
                        val spread = (i + 1) * 4f
                        drawRoundRect(
                            color = GlowPurple.copy(alpha = effectivePurpleAlpha * 0.18f / (i + 1)),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + spread * 2, size.height + spread * 2),
                            cornerRadius = cr
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(if (borderColor != Color.Transparent)
                Modifier.border(borderWidth, borderColor, RoundedCornerShape(6.dp)) else Modifier)
            .then(when {
                // Zahratelná / odhazovatelná: klik + long press (s ripple efektem)
                canPlay || discardMode -> Modifier.combinedClickable(
                    onClick    = onClick,
                    onLongClick = onLongPress
                )
                // Nezahratelná, ale má long press: reaguje na podržení bez ripple
                onLongPress != null -> Modifier.combinedClickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick           = {},
                    onLongClick       = onLongPress
                )
                else -> Modifier
            })
    ) {
        // Vrstva 1: ilustrace karty
        // Oblast ilustrace je horních ~90 dp — pokrývá průhlednou zónu frame i gradient přechod.
        // 70 dp by způsobilo tvrdou hranu uprostřed semi-transparentní části rámu.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(90.dp)
                .clipToBounds()
        ) {
            Image(
                painter = painterResource(artResId),
                contentDescription = null,
                modifier = artModifier(card),
                contentScale = ContentScale.Crop,
                alignment = artAlignment(card),
                alpha = if (!showFade || canPlay || discardMode) 1f else 0.6f
            )
        }

        // Vrstva 2: rám karty (průhlednost v oblasti ilustrace zajistí soubor card_frame.png)
        if (frameResId != 0) {
            Image(
                painter = painterResource(frameResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Vrstva 2.5: Překryv rarity (PNG s průhledností, mění barvu jen určité části)
        val rarityOverlayId = rarityOverlayResource(card.rarity)
        if (rarityOverlayId != 0) {
            Image(
                painter = painterResource(rarityOverlayId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Vrstva 3: cena karty v levém horním kruhu rámu
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 1.5.dp, y = 2.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            val costLabel = if (card.isXCost) "X" else "${card.effectiveCost}"
            val costFillColor = when {
                card.isXCost           -> Color.White
                card.costModifier < 0  -> Color(0xFF00E676)  // zelená – sleva
                card.costModifier > 0  -> Color(0xFFFF5252)  // červená – zdražení
                else                   -> Color.White
            }
            // TextStyle s LineHeightStyle.Trim.Both = glyf přesně uprostřed boxu
            val costStyle = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )
            // Černý obrys – 4 posunuté kopie
            for (off in listOf(Offset(-1f, 0f), Offset(1f, 0f), Offset(0f, -1f), Offset(0f, 1f))) {
                Text(costLabel, color = Color.Black,
                    modifier = Modifier.fillMaxWidth().offset(x = off.x.dp, y = off.y.dp),
                    style = costStyle)
            }
            // Výplň (bílá / zelená / červená)
            Text(costLabel, color = costFillColor,
                modifier = Modifier.fillMaxWidth(),
                style = costStyle)
        }

        // Vrstva 4: název karty v obloukovém pásu (~70 dp od vrchu) — zakřivený text
        ArcCardName(
            name         = card.displayName,
            modifier     = Modifier
                .align(Alignment.TopStart)
                .offset(y = 69.dp)
                .fillMaxWidth()
                .height(22.dp),
            fontSizeSp   = 8f,
            arcRadiusDp  = 350f,
            baselineFrac = 0.78f
        )

        // Vrstva 5: text karty pod názvem (92–130 dp od vrchu)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 92.dp)
                .fillMaxWidth()
                .height(38.dp)
                .clipToBounds()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                parseCardDesc(card.displayDescription + xPreviewSuffix(card, xPreview)),
                color = Color(0xFFDDD0B0),
                fontSize = 7.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 9.sp,
                style = LocalTextStyle.current.merge(
                    TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            )
        }

        // Vrstva 5b: ikony stavu (generováno / splněno / combo) – vpravo nahoře
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (card.isGenerated) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.75f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painterResource(R.drawable.hammer_icon), contentDescription = null, modifier = Modifier.size(10.dp))
                }
            }
            if (isComboCard) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, ComboYellow.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⚡",
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 9.sp
                    )
                }
            }
            if (conditionMet != null) {
                val condColor = if (conditionMet) Color(0xFF4DB86E) else Color(0xFF888888)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, condColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(if (conditionMet) R.drawable.check_icon else R.drawable.cross_icon),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Vrstva 6: typ karty v úplně dolním pruhu (127–139 dp od vrchu)
        if (card.type.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 129.dp)
                    .fillMaxWidth()
                    .height(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    card.displayType.uppercase(),
                    color = Color(0xFFD4B870),
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }

        // Vrstva 7: zelený glow overlay (na vrchu — ale průhledný, nepřekáží čtení)
        if (showGreenGlow) {
            Box(Modifier.fillMaxSize()
                .border(3.dp, GlowGreen.copy(alpha = glowAlpha * 0.35f), RoundedCornerShape(6.dp)))
        }

        // Vrstva 8: overlay při tažení / discard
        if (isDragging) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(DiscardRed.copy(alpha = (progress * 0.55f).coerceAtMost(0.55f))),
                contentAlignment = Alignment.Center
            ) { if (progress > 0.35f) Image(painterResource(R.drawable.cross_icon), contentDescription = null, modifier = Modifier.size((12 + progress * 16).dp)) }
        } else if (discardMode) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(DiscardRed.copy(alpha = 0.35f)),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    Modifier.padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DiscardRed.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Image(painterResource(R.drawable.cross_icon), contentDescription = null, modifier = Modifier.size(8.dp)) }
            }
        }
    }
}

// ─── Fullscreen card preview overlay ─────────────────────────────────────────

/**
 * Modální overlay zobrazující velký náhled [card].
 * Kliknutí kamkoli (mimo kartu nebo na pozadí) zavolá [onDismiss].
 *
 * Rozměry 220×308 dp (2.2× základní 100×140 dp) – vhodné i pro landscape.
 */
@Composable
fun CardFullPreviewOverlay(card: Card, onDismiss: () -> Unit, xPreview: Int? = null) {
    val context = LocalContext.current
    val frameResId = remember(card.costType) {
        context.resources.getIdentifier(cardFrameName(card.costType), "drawable", context.packageName)
    }
    val artResId        = card.effectiveArtResId()
    val rarityOverlayId = rarityOverlayResource(card.rarity)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Kliknutí na samotnou kartu neprotíká do dismiss overlay
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = 308.dp)
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick           = {}
                )
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
        ) {
            // Artwork (90dp × 2.2 = 198dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(198.dp)
                    .clipToBounds()
            ) {
                Image(
                    painter          = painterResource(artResId),
                    contentDescription = null,
                    modifier         = artModifier(card),
                    contentScale     = ContentScale.Crop,
                    alignment        = artAlignment(card)
                )
            }

            // Rám
            if (frameResId != 0) {
                Image(
                    painter          = painterResource(frameResId),
                    contentDescription = null,
                    modifier         = Modifier.fillMaxSize(),
                    contentScale     = ContentScale.FillBounds
                )
            }
            // Rarity overlay
            if (rarityOverlayId != 0) {
                Image(
                    painter          = painterResource(rarityOverlayId),
                    contentDescription = null,
                    modifier         = Modifier.fillMaxSize(),
                    contentScale     = ContentScale.FillBounds
                )
            }

            // Cena (offset × 2.2)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 3.dp, y = 4.dp)
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                val costLabel = if (card.isXCost) "X" else "${card.effectiveCost}"
                val bigCostFillColor = when {
                    card.isXCost           -> Color.White
                    card.costModifier < 0  -> Color(0xFF00E676)  // zelená – sleva
                    card.costModifier > 0  -> Color(0xFFFF5252)  // červená – zdražení
                    else                   -> Color.White
                }
                val costStyle = TextStyle(
                    fontSize        = 20.sp,
                    fontWeight      = FontWeight.ExtraBold,
                    textAlign       = TextAlign.Center,
                    platformStyle   = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim      = LineHeightStyle.Trim.Both
                    )
                )
                for (off in listOf(Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f))) {
                    Text(costLabel, color = Color.Black,
                        modifier = Modifier.fillMaxWidth().offset(x = off.x.dp, y = off.y.dp),
                        style = costStyle)
                }
                Text(costLabel, color = bigCostFillColor,
                    modifier = Modifier.fillMaxWidth(), style = costStyle)
            }

            // Název v obloukovém pásu (69dp × 2.2 = 152dp, výška 22dp × 2.2 = 48dp)
            ArcCardName(
                name         = card.displayName,
                modifier     = Modifier
                    .fillMaxWidth()
                    .padding(top = 152.dp)
                    .height(48.dp),
                fontSizeSp   = 17f,
                arcRadiusDp  = 770f,   // 350 × 2.2
                baselineFrac = 0.78f
            )

            // Popis (92dp × 2.2 = 202dp, výška 38dp × 2.2 = 84dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 202.dp)
                    .height(84.dp)
                    .clipToBounds()
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    parseCardDesc(card.displayDescription + xPreviewSuffix(card, xPreview)),
                    color     = Color(0xFFDDD0B0),
                    fontSize  = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines  = 4,
                    overflow  = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    style     = LocalTextStyle.current.merge(
                        TextStyle(
                            platformStyle   = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim      = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                )
            }

            // Typ (129dp × 2.2 = 284dp, výška 12dp × 2.2 = 26dp)
            if (card.type.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 284.dp)
                        .fillMaxWidth()
                        .height(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        card.displayType.uppercase(),
                        color         = Color(0xFFD4B870),
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign     = TextAlign.Center,
                        style         = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }

        // Hint pro zavření – vždy přímo pod kartou
        Spacer(Modifier.height(14.dp))
        Text(
            "Klepnutím zavřeš",
            color    = Color.White.copy(alpha = 0.35f),
            fontSize = 10.sp
        )
        } // Column
    }
}

