package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── TEST rozložení bojiště „Aréna“ (GameSettings.arenaLayout) ────────────────
//
// Bojiště přes celou obrazovku. Prvky jsou ve stylu „Obsidián a bronz“ (ObsidianBronze.kt,
// schválený u Tvorby balíčku): černé sklo, tenké bronzové hrany, kosočtverce, Cinzel.
//
//   ┌ portrét + suroviny (já) ──── ruka soupeře (vějíř) ──── suroviny + portrét (soupeř) ┐
//   │ hrad + hradba              zahraná karta              hradba + hrad                 │
//   │ medailony životů                                      medailony životů              │
//   └ ☰  Log ─────────────── moje ruka (vějíř, klepnutím přiblížit) ──── kolo / Konec tahu ┘

/** Barva strany: tyrkysová = já, rudá = soupeř. */
internal fun arenaSideColor(isPlayer: Boolean) = if (isPlayer) TealLight else Color(0xFFFF6A5E)

private fun DrawScope.arenaDiamond(c: Offset, s: Float) {
    fun poly(k: Float) = Path().apply {
        moveTo(c.x, c.y - s * k); lineTo(c.x + s * k, c.y); lineTo(c.x, c.y + s * k); lineTo(c.x - s * k, c.y); close()
    }
    drawPath(poly(1.25f), Color.Black.copy(alpha = 0.8f))
    drawPath(poly(1f), BronzeDark)
    drawPath(poly(0.55f), BronzeLight)
}

/** Kovaný bronzový prstenec (černá linka, kov světlý nahoře / tmavý dole, vlasový odlesk). */
private fun DrawScope.bronzeRing(c: Offset, r: Float, width: Float) {
    drawCircle(Color.Black.copy(alpha = 0.9f), r, c)
    drawCircle(Brush.verticalGradient(listOf(BronzeLight, Bronze, BronzeDark), startY = c.y - r, endY = c.y + r), r - 1f, c)
    drawCircle(Color(0x40FFF0D0), r - 1.6f, c, style = Stroke(1f))
    drawCircle(Color.Black.copy(alpha = 0.85f), r - width, c)
    drawCircle(Color(0x30FFF0D0), r - width - 1f, c, style = Stroke(1f))
}

private fun arenaText(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Bold) = TextStyle(
    color      = color,
    fontSize   = size,
    fontWeight = weight,
    shadow     = Shadow(Color.Black, Offset(0f, 1.5f), 3f),
    platformStyle   = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
)

// ─── Portrét ──────────────────────────────────────────────────────────────────

/**
 * Portrét v kovaném bronzovém prstenci se jmenovkou přes spodní okraj (jako hrdina v HS).
 * Strana, která je na tahu, má kolem prstence pulzující záři své barvy.
 */
@Composable
private fun ArenaPortrait(avatar: String, name: String, isPlayer: Boolean, active: Boolean) {
    val side = arenaSideColor(isPlayer)
    val pulse by rememberInfiniteTransition(label = "portraitGlow").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse"
    )
    val ring = 64.dp
    Box(Modifier.size(width = 84.dp, height = ring + 12.dp), contentAlignment = Alignment.TopCenter) {
        // Záře a prstenec
        Canvas(Modifier.size(ring + 20.dp).offset(y = (-10).dp)) {
            val c = center
            val r = (ring / 2).toPx()
            val glowA = if (active) 0.55f * pulse else 0.16f
            drawCircle(Brush.radialGradient(listOf(side.copy(alpha = glowA), Color.Transparent), c, r + 10.dp.toPx()), r + 10.dp.toPx(), c)
            drawCircle(Color.Black.copy(alpha = 0.5f), r, c + Offset(0f, 2.dp.toPx()))
            bronzeRing(c, r, 4.dp.toPx())
            if (active) drawCircle(side.copy(alpha = 0.55f + 0.35f * pulse), r - 4.dp.toPx() - 0.5f, c, style = Stroke(1.5.dp.toPx()))
            // kosočtverce na bocích prstence
            arenaDiamond(Offset(c.x - r + 1f, c.y), 3.dp.toPx())
            arenaDiamond(Offset(c.x + r - 1f, c.y), 3.dp.toPx())
        }
        // Avatar v kruhu uvnitř prstence
        val inner = ring - 9.dp
        Box(
            Modifier.padding(top = 4.5.dp).size(inner).clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AvatarDisplay(avatar, sizeDp = if (avatarResId(avatar) != null) inner.value else inner.value * 0.7f)
        }
        // Jmenovka přes spodek prstence
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(min = 64.dp, max = 84.dp)
                .height(17.dp)
                .obsidianSurface(4.dp, alpha = 0.97f, sheen = false)
                .bronzeRim(4.dp, level = 0.95f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = obsLabelStyle(9.sp, if (isPlayer) ObsText else Color(0xFFF0C8BC), 0.3.sp)
            )
        }
    }
}

// ─── Suroviny ─────────────────────────────────────────────────────────────────

/**
 * Suroviny 2 × 2 na obsidiánové desce: ikona, zásoba (velké číslo v barvě suroviny)
 * a přírůstek dolu (+N, zablokovaný důl rudě). Buňky odděluje bronzový kříž s kosočtvercem.
 */
@Composable
private fun ArenaResources(
    state: PlayerState,
    /** Surovina, kterou stojí zaměřená / tažená karta (zvýrazní se), a jestli na ni stačí. */
    highlight: ResourceType? = null,
    affordable: Boolean = true
) {
    Box(
        Modifier
            .obsidianSurface(8.dp, alpha = 0.92f)
            .bronzeRim(8.dp)
            .bronzeCorners(3.5.dp)
            .drawBehind {
                // bronzový kříž mezi buňkami
                val cx = size.width / 2; val cy = size.height / 2
                val m = 7.dp.toPx()
                drawLine(Brush.verticalGradient(listOf(Color.Transparent, Bronze.copy(alpha = 0.55f), Color.Transparent)),
                    Offset(cx, m), Offset(cx, size.height - m), 1f)
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Bronze.copy(alpha = 0.55f), Color.Transparent)),
                    Offset(m, cy), Offset(size.width - m, cy), 1f)
            }
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Column {
            ResourceType.entries.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { t ->
                        val amount  = state.resources[t] ?: 0
                        val mine    = state.mines[t] ?: 0
                        val blocked = (state.mineBlockedTurns[t] ?: 0) > 0
                        val hl = highlight == t
                        val hlA by androidx.compose.animation.core.animateFloatAsState(if (hl) 1f else 0f, tween(180), label = "resHl")
                        val hlCol = if (affordable) BronzeLight else Color(0xFFFF6A5E)
                        Row(
                            Modifier
                                .width(72.dp).height(27.dp)
                                .drawBehind {
                                    if (hlA > 0.01f) {
                                        val r = CornerRadius(6.dp.toPx())
                                        drawRoundRect(hlCol.copy(alpha = 0.16f * hlA), cornerRadius = r)
                                        drawRoundRect(hlCol.copy(alpha = 0.85f * hlA), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
                                    }
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(painterResource(resourceIconRes(t)), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            ValueWithDelta(amount, arenaText(17.sp, resourceColor(t)))
                            Spacer(Modifier.weight(1f))
                            // Přírůstek dolu za kolo: zeleně (příjem), zablokovaný důl rudě, nulový tlumeně
                            Text(
                                "+$mine", maxLines = 1,
                                style = arenaText(
                                    11.sp,
                                    when { blocked -> HpRed; mine > 0 -> MineIncome; else -> ObsTextMuted },
                                    FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ikonka ruky: tři karty ve vějíři (kreslená – žádný vhodný obrázek ani znak písma). */
@Composable
private fun HandFanIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val w = size.width * 0.42f; val h = size.height * 0.62f
        val pivot = Offset(size.width / 2, size.height * 0.98f)
        for (a in listOf(-22f, 0f, 22f)) {
            rotate(a, pivot) {
                val tl = Offset(pivot.x - w / 2, pivot.y - h - 1.dp.toPx())
                drawRoundRect(Color(0xFF2A2024), tl, Size(w, h), CornerRadius(1.5f))
                drawRoundRect(BronzeLight, tl, Size(w, h), CornerRadius(1.5f), style = Stroke(1f))
            }
        }
    }
}

/** Řádek pod surovinami: balíček, ruka (počet / maximum) a pasivní schopnosti. */
@Composable
private fun ArenaStatLine(
    deckSize: Int, handSize: Int, maxHand: Int,
    @DrawableRes cardBackResId: Int,
    passives: List<PassiveAbility>,
    alignEnd: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, if (alignEnd) Alignment.End else Alignment.Start),
        modifier = Modifier.width(152.dp).padding(horizontal = 4.dp)
    ) {
        @Composable fun Passives() = passives.forEach { p ->
            Box(
                Modifier.size(18.dp).drawBehind {
                    val r = size.minDimension / 2
                    drawCircle(Color.Black.copy(alpha = 0.8f), r)
                    drawCircle(Brush.verticalGradient(listOf(Color(0xFFF0D29A), BronzeLight, Bronze)), r - 1f)
                },
                contentAlignment = Alignment.Center
            ) { Image(painterResource(p.iconRes), contentDescription = null, modifier = Modifier.size(12.dp)) }
        }
        if (alignEnd) Passives()
        CardBackMini(cardBackResId, Modifier.size(width = 10.dp, height = 14.dp))
        Text("$deckSize", style = arenaText(11.sp, ObsText))
        Spacer(Modifier.width(2.dp))
        HandFanIcon()
        Text("$handSize/$maxHand", style = arenaText(11.sp, if (handSize >= maxHand) Color(0xFFE0A050) else ObsText))
        if (!alignEnd) Passives()
    }
}

/** Můj roh (vlevo nahoře): portrét se jmenovkou, vpravo suroviny a pod nimi balíček / ruka. */
@Composable
fun ArenaPlayerCorner(
    state: PlayerState, avatar: String, name: String, active: Boolean,
    deckSize: Int, maxHand: Int, passives: List<PassiveAbility>,
    modifier: Modifier = Modifier,
    /** Karta, kterou hráč právě zkoumá nebo táhne – její surovina se v panelu zvýrazní. */
    inspected: Card? = null
) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ArenaPortrait(avatar, name, isPlayer = true, active = active)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ArenaResources(
                state,
                highlight  = inspected?.costType,
                affordable = inspected == null || inspected.isXCost ||
                             (state.resources[inspected.costType] ?: 0) >= inspected.effectiveCost
            )
            ArenaStatLine(deckSize, state.hand.size, maxHand, playerCardBackResId(), passives, alignEnd = false)
        }
    }
}

/** Roh soupeře (vpravo nahoře): zrcadlo mého rohu. */
@Composable
fun ArenaOpponentCorner(
    state: PlayerState, avatar: String, name: String, active: Boolean,
    deckSize: Int, maxHand: Int, @DrawableRes cardBackResId: Int, passives: List<PassiveAbility>,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
            ArenaResources(state)
            ArenaStatLine(deckSize, state.hand.size, maxHand, cardBackResId, passives, alignEnd = true)
        }
        ArenaPortrait(avatar, name, isPlayer = false, active = active)
    }
}

// ─── Životy hradu a hradby ────────────────────────────────────────────────────

/**
 * Medailon životů pod patou hradu / hradby: bronzový prstenec, obsidiánový střed, číslo v barvě
 * strany, pod ním cíl (výhra / maximum hradby) a dole na prstenci ikona hradu nebo hradby.
 * Visí celý pod stavbou – nic z ní nezakrývá.
 */
@Composable
internal fun ArenaHpMedallion(
    value: Int, max: Int?, isPlayer: Boolean, @DrawableRes iconRes: Int,
    size: Dp, modifier: Modifier = Modifier
) {
    val side = arenaSideColor(isPlayer)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = center
            val r = this.size.minDimension / 2 - 1.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.55f), r + 1.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
            bronzeRing(c, r, 3.dp.toPx())
            val ri = r - 3.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2E2529), Color(0xFF0B090B)), center = c - Offset(0f, ri * 0.3f), radius = ri * 1.3f), ri - 0.5f, c)
            drawCircle(Brush.radialGradient(listOf(side.copy(alpha = 0.22f), Color.Transparent), c, ri), ri, c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Změna životů: číslo poskočí (letící rozdíl už ukazuje HpFloats nad stavbou)
            val bump = remember { Animatable(1f) }
            var prev by remember { mutableIntStateOf(value) }
            LaunchedEffect(value) {
                if (value != prev) {
                    prev = value
                    bump.snapTo(1.35f)
                    bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
            }
            Text(
                "$value", style = arenaText((size.value * 0.36f).sp, side, FontWeight.ExtraBold),
                modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }
            )
            if (max != null) Text("/$max", style = arenaText((size.value * 0.18f).sp, ObsTextMuted))
        }
        Image(
            painterResource(iconRes), contentDescription = null,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = size * 0.2f).size(size * 0.34f)
        )
    }
}

// ─── Ruka soupeře: rub karty ──────────────────────────────────────────────────

/** Celý rub karty (ne výřez jako [CardBackMini]) s tmavou linkou a stínem – pro vějíř soupeře. */
@Composable
internal fun ArenaCardBack(@DrawableRes resId: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .drawBehind {
                drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(0f, 3.dp.toPx()), size, CornerRadius(5.dp.toPx()))
            }
            .clip(shape)
    ) {
        Image(painterResource(resId), contentDescription = null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().drawBehind {
            drawRoundRect(Color.Black.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
        })
    }
}

// ─── Ovládání ─────────────────────────────────────────────────────────────────

/** Menu a log (vlevo dole): kulatá obsidiánová tlačítka s bronzovou hranou. */
@Composable
fun ArenaMenuButtons(onMenu: () -> Unit, onLog: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ObsButton(text = "☰", modifier = Modifier.size(40.dp), fontSize = 15.sp, paddingH = 0.dp, radius = 20.dp, onClick = onMenu)
        ObsButton(text = "Log", modifier = Modifier.height(40.dp).width(64.dp), fontSize = 12.sp, paddingH = 0.dp, radius = 20.dp, onClick = onLog)
    }
}

/**
 * Konec tahu (vpravo dole) – zároveň ukazatel, kdo je na tahu (jako v HS): na mém tahu
 * kovaný bronz s pulzující září, na tahu soupeře tlumený obsidián s „Tah soupeře“.
 * Nad tlačítkem číslo kola.
 */
@Composable
fun ArenaEndTurnButton(
    label: String, round: String?, active: Boolean, glow: Color,
    onClick: (() -> Unit)?, modifier: Modifier = Modifier,
    /** Hráč už nemá co zahrát → tlačítko výrazněji pulzuje (napověda jako v HS). */
    hint: Boolean = false
) {
    val pulse by rememberInfiniteTransition(label = "endTurnGlow").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (hint) 700 else 1400), RepeatMode.Reverse), label = "pulse"
    )
    val glowA by androidx.compose.animation.core.animateFloatAsState(
        if (!active) 0f else if (hint) 1f else 0.45f, tween(300), label = "glowA"
    )
    // Pevný rozměr v obou stavech – tlačítko se při střídání tahů neposouvá
    Box(modifier.size(width = 196.dp, height = 78.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                if (glowA <= 0.01f) return@Canvas
                // Měkká záře kolem pilulky: soustředné obrysy s klesající sytostí (bez ořezu, bez zubů)
                val bw = 164.dp.toPx(); val bh = 48.dp.toPx()
                val steps = 12
                val reach = 14.dp.toPx() * (0.75f + 0.25f * pulse)
                for (i in steps downTo 1) {
                    val k = i / steps.toFloat()
                    val g = reach * k
                    val a = glowA * (0.30f + 0.25f * pulse) * (1f - k) * (1f - k)
                    drawRoundRect(
                        glow.copy(alpha = a),
                        topLeft = Offset(center.x - bw / 2 - g, center.y - bh / 2 - g),
                        size = Size(bw + 2 * g, bh + 2 * g),
                        cornerRadius = CornerRadius(bh / 2 + g),
                        style = Stroke(reach / steps * 1.6f)
                    )
                }
            }
            ObsButton(
                text      = label.uppercase(),
                modifier  = Modifier.width(164.dp).height(48.dp),
                primary   = active,
                fontSize  = 12.sp,
                textColor = if (active) null else ObsTextMuted,
                radius    = 24.dp,
                subText   = round?.uppercase(),
                onClick   = { onClick?.invoke() }
            )
    }
}

// ─── Zpětná vazba ─────────────────────────────────────────────────────────────

/** Zelená pro přírůstek dolu – na obsidiánu čitelná, jasně jiná než barvy surovin. */
private val MineIncome = Color(0xFF8EDC6A)

/**
 * Číslo, které při změně poskočí a vypustí letící rozdíl (+N zeleně / −N rudě) – hráč hned
 * vidí, co mu karta nebo soupeř vzal a přidal (jako v Arcomage).
 */
@Composable
private fun ValueWithDelta(value: Int, style: TextStyle) {
    var prev by remember { mutableIntStateOf(value) }
    val floats = remember { mutableStateListOf<Pair<Long, Int>>() }
    val bump = remember { Animatable(1f) }
    LaunchedEffect(value) {
        val d = value - prev
        prev = value
        if (d != 0) {
            floats.add(System.nanoTime() to d)
            bump.snapTo(1.4f)
            bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    Box(contentAlignment = Alignment.Center) {
        Text("$value", style = style, maxLines = 1, modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value })
        floats.forEach { (id, d) ->
            key(id) { DeltaFloat(d) { floats.removeAll { it.first == id } } }
        }
    }
}

@Composable
private fun DeltaFloat(delta: Int, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1300, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        onDone()
    }
    Text(
        if (delta > 0) "+$delta" else "−${-delta}",
        style = arenaText(13.sp, if (delta > 0) MineIncome else Color(0xFFFF6A5E), FontWeight.ExtraBold),
        maxLines = 1,
        modifier = Modifier
            .wrapContentSize(unbounded = true)
            .graphicsLayer {
                translationX = 16.dp.toPx()
                translationY = -(6.dp.toPx() + 20.dp.toPx() * p.value)
                alpha = if (p.value < 0.7f) 1f else 1f - (p.value - 0.7f) / 0.3f
            }
    )
}

/**
 * Nápověda při tažení karty: kam ji pustit. Nad slotem uprostřed rámeček (zlatý = zahraje se,
 * rudý = nemáš suroviny), dole rudý pruh pro zahození (šedý, když už jsi v tomto kole zahodil).
 * Nemá gesta – dotyky jdou skrz.
 */
@Composable
fun ArenaDragHints(canDiscard: Boolean, modifier: Modifier = Modifier) {
    val flight = LocalFlightOverlay.current ?: return
    val ghost  = flight.dragGhost
    var last by remember { mutableStateOf<DragGhost?>(null) }
    if (ghost != null) SideEffect { last = ghost }
    val shown by androidx.compose.animation.core.animateFloatAsState(if (ghost != null) 1f else 0f, tween(160), label = "dragHints")
    val g = ghost ?: last
    // Bez předčasného return – ten v tomto souboru rozbil vykreslení nápisu tahu
    if (g != null && shown >= 0.01f) DragHintsContent(flight, g, shown, canDiscard, modifier)
}

@Composable
private fun DragHintsContent(flight: FlightOverlayState, g: DragGhost, shown: Float, canDiscard: Boolean, modifier: Modifier) {
    val s = LocalStrings.current
    val pulse by rememberInfiniteTransition(label = "dropPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p"
    )
    var origin by remember { mutableStateOf(Offset.Zero) }
    val inPlay = g.zone == DragZone.PLAY
    val inDisc = g.zone == DragZone.DISCARD
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer { alpha = shown }
    ) {
        // ── Cíl: slot zahrané karty uprostřed ──
        flight.target?.let { t ->
            val col = when { inPlay && g.playable -> BronzeLight; inPlay -> Color(0xFFFF6A5E); else -> BronzeLight }
            val strength = if (inPlay) 1f else 0.35f
            Canvas(Modifier.fillMaxSize()) {
                val pad = 6.dp.toPx()
                val r = Rect(t.left - origin.x - pad, t.top - origin.y - pad, t.right - origin.x + pad, t.bottom - origin.y + pad)
                val steps = 10
                val reach = 16.dp.toPx()
                if (inPlay) for (i in steps downTo 1) {
                    val k = i / steps.toFloat(); val gr = reach * k
                    drawRoundRect(
                        col.copy(alpha = (0.22f + 0.18f * pulse) * (1f - k) * (1f - k)),
                        topLeft = Offset(r.left - gr, r.top - gr), size = Size(r.width + 2 * gr, r.height + 2 * gr),
                        cornerRadius = CornerRadius(10.dp.toPx() + gr), style = Stroke(reach / steps * 1.6f)
                    )
                }
                drawRoundRect(
                    col.copy(alpha = 0.9f * strength),
                    topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        2.dp.toPx(),
                        pathEffect = if (inPlay) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
                    )
                )
            }
        }
        // ── Zahození: rudý pruh u spodní hrany ──
        val discCol = if (canDiscard) Color(0xFFD23C2E) else Color(0xFF6E6A66)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, discCol.copy(alpha = if (inDisc) 0.55f + 0.15f * pulse else 0.22f))))
        )
        if (!inPlay && !inDisc) Text(
            if (canDiscard) s.arenaDragDiscard else s.arenaDiscardUsed,
            style = obsLabelStyle(11.sp, if (canDiscard) Color(0xFFFFC2B8) else ObsTextMuted, 1.sp)
                .copy(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).graphicsLayer { alpha = 0.85f }
        )
        // Co se stane po puštění – destička těsně nad taženou kartou (pod prstem by ji nebylo vidět)
        val label: Pair<String, Color>? = when {
            inPlay && g.playable -> s.arenaDropPlay to BronzeText
            inPlay               -> s.arenaNoResources to Color(0xFFFF8A7E)
            inDisc && canDiscard -> s.arenaDropDiscard to Color(0xFFFFB0A4)
            inDisc               -> s.arenaDiscardUsed to ObsTextMuted
            else                 -> null
        }
        if (label != null) {
            val cxDp  = with(density) { (g.rect.center.x - origin.x).toDp() }
            val topDp = with(density) { (g.rect.top - origin.y).toDp() }
            // Nad kartou; u horního okraje pod ní
            val yDp = if (topDp > 40.dp) topDp - 34.dp else with(density) { (g.rect.bottom - origin.y).toDp() } + 8.dp
            Box(
                Modifier
                    .offset { IntOffset((cxDp - 90.dp).roundToPx(), yDp.roundToPx()) }
                    .width(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .height(26.dp)
                        .obsidianSurface(13.dp, alpha = 0.95f, sheen = false)
                        .bronzeRim(13.dp, level = 0.9f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label.first, maxLines = 1, style = obsLabelStyle(11.sp, label.second, 0.8.sp))
                }
            }
        }
    }
}

/**
 * Nápis „Váš tah“ přes střed bojiště na začátku mého tahu (jako v HS): tmavý pruh s bronzovými
 * linkami, text Cinzelem dosedne ze zvětšení, chvíli drží a zmizí. [trigger] > 0 = ukázat.
 */
@Composable
fun ArenaTurnBanner(trigger: Int, text: String, modifier: Modifier = Modifier) {
    val a  = remember { Animatable(0f) }
    val sc = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        launch { sc.snapTo(1.35f); sc.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f)) }
        a.snapTo(0f)
        a.animateTo(1f, tween(200))
        delay(900)
        a.animateTo(0f, tween(420))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .graphicsLayer { alpha = a.value }
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.78f), Color.Transparent)))
                val line = Brush.horizontalGradient(listOf(Color.Transparent, BronzeLight, BronzeLight, Color.Transparent))
                drawLine(line, Offset(size.width * 0.15f, 1.dp.toPx()), Offset(size.width * 0.85f, 1.dp.toPx()), 1.5.dp.toPx())
                drawLine(line, Offset(size.width * 0.15f, size.height - 1.dp.toPx()), Offset(size.width * 0.85f, size.height - 1.dp.toPx()), 1.5.dp.toPx())
                arenaDiamond(Offset(size.width / 2, 1.dp.toPx()), 4.dp.toPx())
                arenaDiamond(Offset(size.width / 2, size.height - 1.dp.toPx()), 4.dp.toPx())
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            style = obsLabelStyle(30.sp, BronzeText, 8.sp).copy(shadow = Shadow(Color.Black, Offset(0f, 3f), 8f)),
            modifier = Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value }
        )
    }
}
