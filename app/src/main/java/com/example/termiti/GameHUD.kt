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

/**
 * Vrátí true/false pokud karta má podmínkový efekt a je/není splněn.
 * Vrátí null pokud karta žádnou podmínku nemá.
 * Prochází všechny efekty, ne jen první (podmínka může být i na 2. místě).
 */
private fun cardConditionMet(
    card: Card,
    resources: Map<ResourceType, Int>,
    wallHp: Int,
    castleHp: Int,
    oppResources: Map<ResourceType, Int> = emptyMap(),
    lastPlayedType: String? = null
): Boolean? {
    val conditionalEffects = card.effects.filterIsInstance<CardEffect.ConditionalEffect>()
    if (conditionalEffects.isEmpty()) return null

    return conditionalEffects.all { ce ->
        when (val c = ce.condition) {
            is Condition.ResourceAbove            -> (resources[c.type] ?: 0) > c.threshold
            is Condition.WallAbove                -> wallHp   > c.threshold
            is Condition.WallBelow                -> wallHp   < c.threshold
            is Condition.CastleAbove              -> castleHp > c.threshold
            is Condition.CastleBelow              -> castleHp < c.threshold
            is Condition.LastPlayedType           -> lastPlayedType == c.cardType
            is Condition.ResourceMoreThanOpponent -> (resources[c.type] ?: 0) > (oppResources[c.type] ?: 0)
        }
    }
}

// ─── New Top Bar ──────────────────────────────────────────────────────────────

@Composable
fun NewTopBar(
    playerDeckSize: Int,
    aiDeckSize: Int,
    isPlayerTurn: Boolean,
    isComboTurn: Boolean,
    currentTurn: Int,
    arenaWins: Int = -1,
    playerLabel: String = "Hráč",
    playerAvatar: String = "⚔️",
    playerLevel: Int = -1,
    opponentLabel: String = "Nepřítel",
    opponentAvatar: String = "👺",
    opponentLevel: Int = -1,
    onMenu: () -> Unit,
    playerTimerText: String? = null,
    playerTimerColor: Color = Color(0xFF4CAF50),
    oppTimerText: String? = null,
    oppTimerColor: Color = Color(0xFF4CAF50),
    playerPassives: List<PassiveAbility> = emptyList(),
    aiPassives: List<PassiveAbility> = emptyList()
) {
    val s          = LocalStrings.current
    val activeTurn = isPlayerTurn || isComboTurn
    val dotColor = if (activeTurn) TealLight else Crimson
    val turnText = when {
        isComboTurn   -> "⚡ COMBO"
        isPlayerTurn  -> s.yourTurn
        else          -> s.opponentTurn
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .paint(painterResource(R.drawable.bg_top_panel), contentScale = ContentScale.Crop)
            .padding(horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Hráč vlevo ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .clickable { onMenu() }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) { Text("☰", color = TextMuted, fontSize = 11.sp) }

            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A2010), Color(0xFF5C3010))))
                    .border(1.5.dp, Gold.copy(alpha = 0.65f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Text(playerAvatar, fontSize = 13.sp) }

            Text(playerLabel, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

            if (playerLevel >= 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Gold.copy(alpha = 0.15f))
                        .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("Lv.$playerLevel", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }

            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("🂠", fontSize = 11.sp)
                Text("$playerDeckSize", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (playerPassives.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    playerPassives.forEach { ability ->
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A0A2E).copy(alpha = 0.70f))
                                .border(0.5.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(ability.icon, fontSize = 11.sp) }
                    }
                }
            }
            if (playerTimerText != null) {
                Text(playerTimerText, color = playerTimerColor,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Střed ─────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Gold.copy(alpha = 0.10f))
                    .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text("${s.round} $currentTurn", color = Gold, fontSize = 10.sp, letterSpacing = 1.sp)
            }

            if (arenaWins >= 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Gold.copy(alpha = 0.07f))
                        .border(1.dp, Gold.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text("⚔️ $arenaWins", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.size(7.dp)) {
                    drawCircle(dotColor.copy(alpha = 0.30f), radius = size.width)
                    drawCircle(dotColor, radius = size.width * 0.55f)
                }
                Text(turnText, color = dotColor, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // ── AI vpravo ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (oppTimerText != null) {
                Text(oppTimerText, color = oppTimerColor,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (aiPassives.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    aiPassives.forEach { ability ->
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A0A2E).copy(alpha = 0.70f))
                                .border(0.5.dp, Crimson.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(ability.icon, fontSize = 11.sp) }
                    }
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("$aiDeckSize", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("🂠", fontSize = 11.sp)
            }

            if (opponentLevel >= 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Crimson.copy(alpha = 0.15f))
                        .border(1.dp, Crimson.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("Lv.$opponentLevel", color = Crimson, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }

            Text(opponentLabel, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A0A0A), Color(0xFF5C1010))))
                    .border(1.5.dp, Crimson.copy(alpha = 0.65f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Text(opponentAvatar, fontSize = 13.sp) }
        }
    }
}

// ─── New Resource Panel ───────────────────────────────────────────────────────

@Composable
fun NewResourcePanel(
    playerState: PlayerState,
    isAi: Boolean,
    modifier: Modifier = Modifier,
    bottomSlot: @Composable ColumnScope.() -> Unit = {}
) {
    val magic     = playerState.resources[ResourceType.MAGIC]  ?: 0
    val attack    = playerState.resources[ResourceType.ATTACK] ?: 0
    val stones    = playerState.resources[ResourceType.STONES] ?: 0
    val chaos     = playerState.resources[ResourceType.CHAOS]  ?: 0
    val mineMagic = playerState.mines[ResourceType.MAGIC]  ?: 0
    val mineAtk   = playerState.mines[ResourceType.ATTACK] ?: 0
    val mineSto   = playerState.mines[ResourceType.STONES] ?: 0
    val mineChaos = playerState.mines[ResourceType.CHAOS]  ?: 0
    val blkMagic  = playerState.mineBlockedTurns[ResourceType.MAGIC]  ?: 0
    val blkAtk    = playerState.mineBlockedTurns[ResourceType.ATTACK] ?: 0
    val blkSto    = playerState.mineBlockedTurns[ResourceType.STONES] ?: 0
    val blkChaos  = playerState.mineBlockedTurns[ResourceType.CHAOS]  ?: 0

    Column(
        modifier = modifier
            .paint(
                painterResource(R.drawable.bg_side_panels),
                contentScale = ContentScale.Crop
            )
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        NewResourceSection("✨", "Magie",  mineMagic, magic,  MagicPurple, isAi = isAi, blockedTurns = blkMagic)
        NewResourceSection("⚔️", "Útok",   mineAtk,   attack, AttackRed,   isAi = isAi, blockedTurns = blkAtk)
        NewResourceSection("🪨", "Kameny", mineSto,   stones, StoneColor,  isAi = isAi, blockedTurns = blkSto)
        NewResourceSection("🌀", "Chaos",  mineChaos, chaos,  ChaosOrange, isAi = isAi, blockedTurns = blkChaos, isLast = true)
        Spacer(Modifier.weight(1f))
        bottomSlot()
    }
}

@Composable
fun NewResourceSection(
    icon: String,
    name: String,
    mine: Int,
    amount: Int,
    color: Color,
    isAi: Boolean = false,
    isLast: Boolean = false,
    blockedTurns: Int = 0
) {
    val blocked = blockedTurns > 0
    val mineColor = if (blocked) Color(0xFFE53935) else Gold

    // Pomocný slot pro počet dolů + indikátor blokace (vždy jeden řádek)
    @Composable
    fun MineSlot(align: Alignment.Horizontal) {
        val text  = if (blocked) "⛔$blockedTurns" else if (mine > 0) "$mine" else "—"
        val size  = if (blocked) 9.sp else 11.sp
        Text(
            text,
            color      = mineColor,
            fontSize   = size,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.widthIn(min = 14.dp),
            textAlign  = if (align == Alignment.End) TextAlign.End else TextAlign.Start
        )
    }

    // ── Jedna kompaktní řádka: [mine#] [icon] [name] ... [amount] ──────────
    // Pro AI zrcadlově: [amount] ... [name] [icon] [mine#]
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (!isLast) Modifier.drawBehind {
                    val y = size.height - 0.5f
                    drawLine(Gold.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 0.5f)
                } else Modifier
            )
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isAi) {
            // Hráč: mine# | ikona | název (roztažený) | zásoba  (+delta jako overlay vpravo)
            MineSlot(Alignment.Start)
            Spacer(Modifier.width(2.dp))
            Text(icon, fontSize = 9.sp, lineHeight = 10.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                name, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box {
                Text("$amount", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
                // delta se vykreslí VPRAVO za číslem (offset = šířka čísla), layout ho nezahrnuje
                Box(Modifier.offset(x = 32.dp).width(0.dp).wrapContentWidth(unbounded = true)) {
                    ResourceDelta(amount)
                }
            }
        } else {
            // AI – zrcadlo: zásoba (delta jako overlay vlevo) | název (roztažený) | ikona | mine#
            Box {
                Text("$amount", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start, modifier = Modifier.width(30.dp))
                // delta se vykreslí VLEVO před číslem (Alignment.End na 0-width = roste do záporných x)
                Box(Modifier.width(0.dp).wrapContentWidth(align = Alignment.End, unbounded = true)) {
                    ResourceDelta(amount)
                }
            }
            Text(
                name, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(3.dp))
            Text(icon, fontSize = 9.sp, lineHeight = 10.sp)
            Spacer(Modifier.width(2.dp))
            MineSlot(Alignment.End)
        }
    }
}

@Composable
fun NewPanelButton(
    label: String,
    color: Color,
    active: Boolean,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = if (active) 0.12f else 0.05f))
            .border(1.dp, color.copy(alpha = if (active) 0.50f else 0.18f), RoundedCornerShape(5.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color         = color.copy(alpha = if (active) 1f else 0.38f),
            fontSize      = 9.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            textAlign     = TextAlign.Center
        )
    }
}


// ─── Player Panel ─────────────────────────────────────────────────────────────
@Composable
fun PlayerPanel(
    label: String, playerState: PlayerState, isEnemy: Boolean, modifier: Modifier = Modifier,
    winTarget: Int = 60
) {
    val accent     = if (isEnemy) Crimson else Teal
    val castleHp   = playerState.castleHP
    val wallHp     = playerState.wallHP
    val magic      = playerState.resources[ResourceType.MAGIC]  ?: 0
    val attack     = playerState.resources[ResourceType.ATTACK] ?: 0
    val stones     = playerState.resources[ResourceType.STONES] ?: 0
    val chaos      = playerState.resources[ResourceType.CHAOS]  ?: 0
    val mineMagic  = playerState.mines[ResourceType.MAGIC]  ?: 0
    val mineAtk    = playerState.mines[ResourceType.ATTACK] ?: 0
    val mineSto    = playerState.mines[ResourceType.STONES] ?: 0
    val mineChaos  = playerState.mines[ResourceType.CHAOS]  ?: 0
    val hasChaos   = chaos > 0 || mineChaos > 0

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Brush.verticalGradient(
                if (isEnemy) listOf(BgPanel.copy(alpha = 0.70f), BgDeep.copy(alpha = 0.70f))
                else         listOf(BgDeep.copy(alpha = 0.70f),  BgPanel.copy(alpha = 0.70f))
            ))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Jméno + balíčky
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), color = accent, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DeckChip("🃏", playerState.deck.size, "bal.")
                DeckChip("🗑", playerState.discardPile.size, "odh.")
            }
        }

        Spacer(Modifier.height(6.dp))

        // Vizuál hradu/hradeb – vyplní volný prostor
        CastleWallVisual(castleHp = castleHp, wallHp = wallHp, winTarget = winTarget,
            modifier = Modifier.fillMaxWidth().weight(1f))

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Gold.copy(alpha = 0.1f))
        Spacer(Modifier.height(6.dp))

        ResourcesRow(
            magic, mineMagic,
            attack, mineAtk,
            stones, mineSto,
            chaos, mineChaos, hasChaos
        )
    }
}

@Composable
fun CastleWallVisual(castleHp: Int, wallHp: Int, winTarget: Int = 60, modifier: Modifier = Modifier) {
    val castleColor = if (castleHp > 15) HpGreen else HpRed
    val castleFrac by animateFloatAsState(
        (castleHp / winTarget.toFloat()).coerceIn(0f, 1f),
        tween(600, easing = EaseOutCubic), label = "castle"
    )
    val wallFrac by animateFloatAsState(
        (wallHp / 50f).coerceIn(0f, 1f),
        tween(600, easing = EaseOutCubic), label = "wall"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        // ── Hradby (wall) ────────────────────────────────────────
        Column(
            modifier = Modifier.width(32.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val wallPainter = painterResource(R.drawable.wall_player)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // Vrchol viditelné hradby = (1 - wallFrac) * výška boxu
                val wallTopDp = maxHeight * (1f - wallFrac)
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    // Tmavé pozadí (prázdná zeď)
                    drawRect(Color.White.copy(alpha = 0.07f))
                    if (wallFrac > 0f) {
                        // Ořež na spodních wallFrac a vykresli plný obrázek
                        clipRect(
                            left   = 0f,
                            top    = h * (1f - wallFrac),
                            right  = w,
                            bottom = h
                        ) {
                            with(wallPainter) { draw(Size(w, h)) }
                        }
                    }
                }
                // Plovoucí delta – startOffsetY = vrchol viditelné hradby
                HpFloats(wallHp, sizeSp = 11f, startOffsetY = wallTopDp)
            }
            Spacer(Modifier.height(3.dp))
            Text("🧱 $wallHp", color = WallBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }

        // ── Hrad (castle tower) ───────────────────────────────────
        Column(
            modifier = Modifier.width(34.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // Vrchol viditelného hradu = bodyTop + bodyH*(1-castleFrac) = (0.15 + 0.85*(1-frac)) * výška
                val castleTopDp = maxHeight * (0.15f + 0.85f * (1f - castleFrac))
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    // Cimbuří = 15 % výšky
                    val merH = h * 0.15f
                    val merW = w / 5f
                    val bodyTop = merH
                    val bodyH = h - bodyTop
                    val fillH = bodyH * castleFrac
                    val fillTop = bodyTop + bodyH - fillH

                    drawRect(Color.White.copy(alpha = 0.07f),
                        topLeft = Offset(0f, bodyTop), size = Size(w, bodyH))
                    if (fillH > 0f)
                        drawRect(castleColor.copy(alpha = 0.60f),
                            topLeft = Offset(0f, fillTop), size = Size(w, fillH))

                    for (i in listOf(0, 2, 4)) {
                        val mx = merW * i
                        drawRect(Color.White.copy(alpha = 0.07f),
                            topLeft = Offset(mx, 0f), size = Size(merW, merH))
                        drawRect(castleColor.copy(alpha = 0.42f),
                            topLeft = Offset(mx, 0f), size = Size(merW, merH), style = Stroke(1f))
                    }
                    drawRect(castleColor.copy(alpha = 0.50f),
                        topLeft = Offset(0f, bodyTop), size = Size(w, bodyH), style = Stroke(1f))

                    val slitW = w * 0.13f
                    val slitH = bodyH * 0.28f
                    drawRect(Color.Black.copy(alpha = 0.65f),
                        topLeft = Offset((w - slitW) / 2f, bodyTop + bodyH * 0.22f),
                        size = Size(slitW, slitH))
                }
                // Plovoucí delta – startOffsetY = vrchol viditelného hradu
                HpFloats(castleHp, sizeSp = 11f, startOffsetY = castleTopDp)
            }
            Spacer(Modifier.height(3.dp))
            Text("🏰 $castleHp/$winTarget", color = castleColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResourcesRow(
    magic: Int, mineMagic: Int,
    attack: Int, mineAtk: Int,
    stones: Int, mineSto: Int,
    chaos: Int, mineChaos: Int,
    @Suppress("UNUSED_PARAMETER") showChaos: Boolean = true   // chaos se zobrazuje vždy
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResourceChip("✨", magic,  mineMagic, MagicPurple)
        ResourceChip("⚔️", attack, mineAtk,  AttackRed)
        ResourceChip("🪨", stones, mineSto,  StoneColor)
        ResourceChip("🌀", chaos,  mineChaos, ChaosOrange)
    }
}

@Composable
private fun ResourceChip(icon: String, value: Int, mine: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text("$value", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("+$mine", color = color.copy(alpha = 0.55f), fontSize = 9.sp)
    }
}

@Composable
fun DeckChip(icon: String, count: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(icon, fontSize = 10.sp)
        Spacer(Modifier.width(2.dp))
        Text("$count", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(label, color = TextMuted, fontSize = 8.sp)
    }
}


// ─── Hand ─────────────────────────────────────────────────────────────────────
// ComboYellow → GameColors.kt

@Composable
fun HandPanel(
    hand: List<Card>,
    isPlayerTurn: Boolean,
    isComboTurn: Boolean,
    playerResources: Map<ResourceType, Int>,
    onPlayCard: (Card) -> Unit,
    onDiscardCard: (Card) -> Unit,
    onWait: () -> Unit,
    onEndTurn: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,      // false = skryje "RUKA (n)" a tlačítko čekat
    playerWallHp: Int = 0,
    playerCastleHp: Int = 0,
    oppResources: Map<ResourceType, Int> = emptyMap(),
    lastPlayedType: String? = null,
    onLongPressCard: ((Card) -> Unit)? = null
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {

        // ── Záhlaví ruky ─────────────────────────────────────────────────────
        if (showHeader) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RUKA (${hand.size})",
                    color = TextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                if (isPlayerTurn || isComboTurn) {
                    ActionChip(label = "⏳ Čekat", color = TealLight, onClick = onWait)
                }
            }
        }

        // LazyRow vyplňuje šířku a centruje obsah přes Arrangement.
        // Díky tomu se LazyRow nezmenšuje při ubrání karty a animateItem()
        // animuje pohyb všech karet (vlevo i vpravo) v jednom systému
        // – žádné "teleportování" levé poloviny.
        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            contentPadding        = PaddingValues(horizontal = 10.dp)
        ) {
            items(hand, key = { it.id }) { card ->
                val affordable = card.isXCost || (playerResources[card.costType] ?: 0) >= card.effectiveCost
                Box(
                    Modifier
                        .animateItem()
                        .trackFlightSource(card.id)
                ) {
                    CardView(
                        card          = card,
                        canPlay       = isPlayerTurn && affordable,
                        isComboCard   = card.isCombo,
                        discardMode   = false,
                        onClick       = { onPlayCard(card) },
                        onDiscard     = if (isPlayerTurn) { { onDiscardCard(card) } } else null,
                        onLongPress   = if (onLongPressCard != null) { { onLongPressCard(card) } } else null,
                        conditionMet  = cardConditionMet(
                            card,
                            playerResources,
                            playerWallHp,
                            playerCastleHp,
                            oppResources,
                            lastPlayedType
                        )
                    )
                }
            }
        }
    }
}
