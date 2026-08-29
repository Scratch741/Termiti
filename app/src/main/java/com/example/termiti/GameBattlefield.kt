package com.example.termiti

import com.example.termiti.R
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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

// ─── New Battlefield ──────────────────────────────────────────────────────────

@Composable
fun NewBattlefield(
    playerState: PlayerState,
    aiState: PlayerState,
    lastCard: Card?,
    lastCardAction: CardAction?,
    lastCardIsPlayer: Boolean,
    modifier: Modifier = Modifier,
    revealedAiCard: Card? = null,     // karta zahrána soupeřem
    revealedAiCardIdx: Int? = null,   // původní index v ruce (před zahráním)
    // Ordered fronta karet ztracených ze soupeřovy ruky (spálené/ukradené hráčem) –
    // offline GameViewModel.aiHandLossLog, online OnlineLobbyViewModel.opponentHandLoss.
    oppLossQueue: List<CardHistoryEntry> = emptyList(),
    playerWinTarget: Int = 60,        // 60 nebo 65 s extra_castle pasivní schopností
    aiWinTarget: Int = 60,            // win target soupeře / AI
    playerMaxHand: Int = 7,           // max. velikost ruky hráče (7 nebo 8 s extra_hand_card)
    aiMaxHand: Int = 7,               // max. velikost ruky soupeře/AI (7 nebo 8 s extra_hand_card)
    opponentCardBackResId: Int = R.drawable.card_back_frame,  // skin rubu karet soupeře/AI
    playerCastleResId: Int = R.drawable.castle_player,        // skin hradu hráče
    opponentCastleResId: Int = R.drawable.castle_player,      // skin hradu soupeře/AI
    playerWallResId: Int = R.drawable.wall_player,             // skin hradby hráče
    opponentWallResId: Int = R.drawable.wall_player,            // skin hradby soupeře/AI
    // Pozadí bojiště – náhodné pro běžné hry (viz randomBattleBackground()) nebo
    // vynucené konkrétní pro kampaň (např. castle_background_goblin). Plameny se
    // renderují JEN pro výchozí castle_background – jinde by nesedly na pochodně.
    backgroundResId: Int = R.drawable.castle_background
) {
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .paint(
                painterResource(backgroundResId),
                contentScale = ContentScale.Crop
            )
    ) {
        // Přirozená velikost karty
        val cardNatH = 140.dp
        val cardNatW = 100.dp
        // Dostupný prostor: pod AI stripem (46dp fixní), malý dolní dech (8dp).
        // Hrady jsou v rozích → blokují jen strany, ne střed bojiště.
        val cardAvailH = maxHeight - 46.dp - 8.dp
        val cardAvailW = maxWidth  - 24.dp      // 12dp margin na každé straně
        val cardScaleH = (cardAvailH / cardNatH).coerceIn(0.4f, 1.35f)
        val cardScaleW = (cardAvailW / cardNatW).coerceIn(0.4f, 1.35f)
        val cardScale  = minOf(cardScaleH, cardScaleW)
        val scaledH    = cardNatH * cardScale
        val scaledW    = cardNatW * cardScale

        // ── AI ruka (nahoře) – animovaná: nové ruby přilétají, po zahrání se sesune ──
        val aiStripH = 46.dp
        val showReveal = revealedAiCard != null && revealedAiCardIdx != null
        val aiHandSize = aiState.hand.size

        // Ruby karet nemají identitu → syntetické stabilní klíče slotů. Bez nich
        // nelze animovat přílet nové karty ani plynulé sesunutí po zahrání.
        // Init na aktuální velikost ruky = úvodní ruka se neanimuje (jako u hráče).
        val aiSlotIds    = remember { mutableStateListOf<Int>().apply { repeat(aiHandSize) { add(it) } } }
        var aiNextSlotId by remember { mutableIntStateOf(aiHandSize) }
        var aiNewSlotIds by remember { mutableStateOf(emptySet<Int>()) }
        // Poslední známé pozice rubů + overlay pro ghost efekt spálené/ukradené karty
        val aiSlotRects = remember { mutableMapOf<Int, Rect>() }
        val lossFx      = LocalFlightOverlay.current
        // Kurzor do oppLossQueue – konzumováno FIFO, jedna položka na jeden odebraný slot.
        var oppLossCursor by remember { mutableIntStateOf(0) }

        LaunchedEffect(aiHandSize) {
            // Fronta se resetovala (nová hra) → kurzor taky, jinak by zůstal za koncem
            if (oppLossQueue.size < oppLossCursor) oppLossCursor = 0
            if (aiSlotIds.size < aiHandSize) {
                // Líznutí: přidej nové klíče na konec a označ je pro fly-in animaci
                val added = mutableSetOf<Int>()
                while (aiSlotIds.size < aiHandSize) {
                    aiSlotIds.add(aiNextSlotId)
                    added.add(aiNextSlotId)
                    aiNextSlotId++
                }
                aiNewSlotIds = added
            } else if (aiSlotIds.size > aiHandSize) {
                // Zahrání/zahození/spálení: odeber klíč na pozici zahrané karty
                // (zbytek se přes animateItem plynule sesune), jinak z konce
                while (aiSlotIds.size > aiHandSize) {
                    // Zahraná karta (reveal) mizí ze své ZNÁMÉ pozice. Spálení/krádež/
                    // zahození nemá známou pozici (herní logika vybírá kartu náhodně
                    // podle identity, ne podle slotu ve stripu) → NÁHODNÝ slot, jinak
                    // by vizuálně vždy mizela ta úplně poslední (nejvíc vpravo) karta.
                    val removeAt = revealedAiCardIdx?.takeIf { it < aiSlotIds.size }
                        ?: aiSlotIds.indices.random()
                    val removedKey = aiSlotIds[removeAt]
                    aiSlotIds.removeAt(removeAt)
                    // Rub zmizel bez odhalení + poslední akce = spálení/krádež →
                    // ghost efekt (oranžová/fialová), aby bylo vidět, že soupeř
                    // přišel o kartu z ruky a jak. Pokud známe konkrétní kartu,
                    // ukaž její líc – stejný efekt jako u hráčovy ruky; jinak
                    // fallback na rub.
                    if (!showReveal && lossFx != null) {
                        aiSlotRects[removedKey]?.let { rect ->
                            // Přednostně konkrétní událost z fronty (správná karta i při
                            // více simultánních ztrátách za jednu akci – Spálená
                            // knihovna/BurnCard(2) apod.). Frontu plní offline i online.
                            // Fallback na lastCard zůstává pro starší server bez
                            // příznaku `fromHand` v CARD_LOST (fronta pak přijde prázdná).
                            val queued = oppLossQueue.getOrNull(oppLossCursor)
                            val act: CardAction
                            val face: Card?
                            if (queued != null) {
                                oppLossCursor++
                                act  = queued.action
                                face = queued.card
                            } else if (lastCardAction == CardAction.BURNED || lastCardAction == CardAction.STOLEN) {
                                act  = lastCardAction
                                face = lastCard?.takeIf { !lastCardIsPlayer }
                            } else {
                                return@let
                            }
                            if (face != null) {
                                // Líc ve velikosti odhalené karty (31×44) na pozici rubu (22×32)
                                val w = rect.width  * (31f / 22f)
                                val h = rect.height * (44f / 32f)
                                val inflated = Rect(
                                    rect.center.x - w / 2f, rect.center.y - h / 2f,
                                    rect.center.x + w / 2f, rect.center.y + h / 2f
                                )
                                lossFx.spawnLoss(face, null, act, inflated)
                            } else {
                                lossFx.spawnLoss(null, opponentCardBackResId, act, rect)
                            }
                        }
                    }
                    aiSlotRects.remove(removedKey)
                }
            }
        }

        // Položky stripu: ruby + odhalená zahraná karta na své původní pozici.
        // Klíč obsahuje id karty → při combo sérii (více karet za sebou) se pop
        // animace spustí pro každou novou odhalenou kartu.
        val revealKey = "revealed:${revealedAiCard?.id}"
        val stripItems: List<Pair<Any, Boolean>> = buildList {
            val total = aiSlotIds.size + if (showReveal) 1 else 0
            var back = 0
            for (slot in 0 until total) {
                if (showReveal && slot == revealedAiCardIdx) add(revealKey as Any to true)
                else if (back < aiSlotIds.size) add(aiSlotIds[back++] as Any to false)
            }
            if (showReveal && none { it.second }) add(revealKey as Any to true)
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(aiStripH)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))
                )
                .padding(horizontal = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            items(stripItems, key = { it.first }) { item ->
                if (item.second) {
                    // Odhalená zahraná karta – pop na své původní pozici v ruce, klidový
                    // stav 1.15× (o 15 % větší než okolní ruby) → jasně vyčnívá jako
                    // "právě zahraná". Vizuální scale (graphicsLayer) – nemění layout
                    // box, takže nezasahuje do ghost/pozičního počítání jinde.
                    // transformOrigin = horní hrana: růst jde jen DOLŮ do bojiště,
                    // ne nahoru – jinak by karta přesahovala nad AI strip do separátoru
                    // pod horní lištou (ten má zIndex(1f) → schovával by horní část karty).
                    val pop = remember { Animatable(0.6f) }
                    LaunchedEffect(Unit) { pop.animateTo(1.15f, tween(260, easing = FastOutSlowInEasing)) }
                    Box(
                        Modifier
                            .animateItem()
                            .graphicsLayer {
                                scaleX = pop.value; scaleY = pop.value
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                    ) { PlayedCardSlot(revealedAiCard!!) }
                } else {
                    // Nově líznutý rub přilétá zprava (od balíčku) – zrcadlí animaci hráče
                    val slotKey   = item.first as Int
                    val appearIdx = remember(slotKey) {
                        if (slotKey in aiNewSlotIds) aiNewSlotIds.sorted().indexOf(slotKey) else -1
                    }
                    val flyIn = remember(slotKey) { Animatable(if (appearIdx >= 0) 1f else 0f) }
                    var flyStartPx by remember(slotKey) { mutableFloatStateOf(0f) }
                    if (appearIdx >= 0) LaunchedEffect(slotKey) {
                        delay(200L * appearIdx)
                        flyIn.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                    }
                    Box(
                        Modifier
                            // fadeInSpec = null: výchozí fade-in by maskoval začátek příletu
                            .animateItem(fadeInSpec = null)
                            .onGloballyPositioned { c ->
                                flyStartPx = c.findRootCoordinates().size.width - c.positionInRoot().x
                                aiSlotRects[slotKey] = c.boundsInRoot()
                            }
                            .graphicsLayer {
                                val p = flyIn.value
                                val startX = if (flyStartPx > 0f) flyStartPx else 600.dp.toPx()
                                translationX = p * startX
                                rotationZ    = p * 10f
                            }
                    ) { CardBack(skinResId = opponentCardBackResId) }
                }
            }
        }

        // ── Plameny na pochodeňových pozicích (renderovány PŘED hrady → jsou za nimi) ──
        //
        //  Pozice jsou nafitované na pochodně konkrétně v castle_background.png (1200×400 px).
        //  U jiných pozadí (castle_background_swamp/vulcan/winter/goblin) by seděly jen náhodou
        //  – proto se plameny renderují VÝHRADNĚ pro výchozí pozadí, viz backgroundResId dole.
        //
        //  x, y = procenta OBRÁZKU. Měř přímo na obrázku: x=0 je levý kraj, x=100 pravý;
        //  y=0 vršek, y=100 spodek. Kód sám přepočítá ContentScale.Crop crop a zobrazí
        //  plamen na správném místě.
        //
        //  size = velikost plamene v dp (výchozí 20)
        //  seed = nemeň (odděluje fáze animací sousedních plamenů)
        //
        if (backgroundResId == R.drawable.castle_background) {
            data class Flame(val x: Float, val y: Float, val seed: Float, val size: Float = 20f)
            val flames = remember {
                listOf(
                    Flame(x = 30f, y = 65f, seed = 0.0f),            // ①
                    Flame(x = 34f, y = 57f, seed = 1.7f),            // ②
                    Flame(x = 71f, y = 55.2f, seed = 2.5f),            // ③
                    Flame(x = 78.5f, y = 65f, seed = 3.7f),            // ④
                    Flame(x = 83.3f, y = 55f, seed = 0.9f, size = 25f),            // ⑤
                    Flame(x = 97f, y = 51.5f, seed = 2.1f, size = 42f) // ⑥
                )
            }

            // Přepočet image-space → display-space s korekcí ContentScale.Crop.
            // Obrázek je 1200×400 (poměr 3:1).
            val imgAR   = 3.0f
            val dispAR  = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val imgDispW: Dp
            val imgDispH: Dp
            val cropX:   Dp
            val cropY:   Dp
            if (dispAR >= imgAR) {
                // Zobrazení je širší než obrázek → škáluje se na šířku, ořez nahoře/dole
                imgDispW = maxWidth
                imgDispH = maxWidth / imgAR
                cropX    = 0.dp
                cropY    = (imgDispH - maxHeight) / 2f
            } else {
                // Zobrazení je užší než obrázek → škáluje se na výšku, ořez vlevo/vpravo
                imgDispW = maxHeight * imgAR
                imgDispH = maxHeight
                cropX    = (imgDispW - maxWidth) / 2f
                cropY    = 0.dp
            }

            flames.forEach { f ->
                val fSz = f.size.dp
                val xDisplay = imgDispW * (f.x / 100f) - cropX - fSz / 2
                val yDisplay = imgDispH * (f.y / 100f) - cropY - fSz * 0.80f
                TorchFlame(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = xDisplay, y = yDisplay),
                    size = fSz,
                    seed = f.seed
                )
            }
        }

        // ── Hrady – vlevo/vpravo dole (renderovány PO plamenech → překrývají je) ──
        NewCastleStructure(
            castleHp    = playerState.castleHP,
            wallHp      = playerState.wallHP,
            isPlayer    = true,
            winTarget   = playerWinTarget,
            maxWall     = playerState.maxWall,
            castleResId = playerCastleResId,
            wallResId   = playerWallResId,
            modifier    = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp)
        )
        NewCastleStructure(
            castleHp    = aiState.castleHP,
            wallHp      = aiState.wallHP,
            isPlayer    = false,
            winTarget   = aiWinTarget,
            maxWall     = aiState.maxWall,
            castleResId = opponentCastleResId,
            wallResId   = opponentWallResId,
            modifier    = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp)
        )

        // ── HP badge hráče – vlevo nahoře ───────────────────────────────────────
        CastleHpBadge(
            castleHp    = playerState.castleHP,
            wallHp      = playerState.wallHP,
            isPlayer    = true,
            winTarget   = playerWinTarget,
            maxWall     = playerState.maxWall,
            handSize    = playerState.hand.size,
            maxHandSize = playerMaxHand,
            modifier    = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 4.dp)
        )

        // ── HP badge AI – vpravo nahoře ──────────────────────────────────────────
        CastleHpBadge(
            castleHp    = aiState.castleHP,
            wallHp      = aiState.wallHP,
            isPlayer    = false,
            winTarget   = aiWinTarget,
            maxWall     = aiState.maxWall,
            handSize    = aiState.hand.size,
            maxHandSize = aiMaxHand,
            modifier    = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
        )

        // ── Poslední zahraná karta – vycentrovaná ve volné ploše pod AI stripem ──
        val cardTopY = aiStripH + (maxHeight - aiStripH - scaledH) / 2
        // Statický slot drží STABILNÍ zobrazení – aktualizuje se jen když karta
        // skutečně "dopadne" (u hráče po letu, u soupeře hned). Bez toho by
        // vznikala díra v discardu po dobu letu nové karty.
        val flight = LocalFlightOverlay.current
        var displayCard         by remember { mutableStateOf(lastCard) }
        var displayAction       by remember { mutableStateOf(lastCardAction) }
        var displayIsPlayer     by remember { mutableStateOf(lastCardIsPlayer) }

        LaunchedEffect(lastCard?.id, lastCardIsPlayer, flight?.landedPlayerCardId) {
            val c = lastCard
            if (c == null) { displayCard = null; return@LaunchedEffect }
            // Soupeřovu kartu zobraz hned; hráčovu až po dokončení letu
            val landed = !lastCardIsPlayer ||
                         flight == null ||
                         flight.landedPlayerCardId == c.id
            if (landed) {
                displayCard     = c
                displayAction   = lastCardAction
                displayIsPlayer = lastCardIsPlayer
            }
        }
        // Průběh letu hráčovy karty → použijeme k postupnému rozplynutí STARÉ karty
        // v discard slotu. Stará karta zmizí dříve, než na ni letící karta přilétí
        // (p ≈ 0.55–0.88), takže přistání probíhá do prázdného slotu bez překrytí.
        val activePlayerFlight = flight != null &&
                                  flight.isFlying(lastCard?.id ?: "") &&
                                  lastCardIsPlayer
        val fp         = if (activePlayerFlight) flight!!.flightProgress else 0f
        val slotAlpha  = when {
            fp <= 0.55f -> 1f
            fp <= 0.88f -> 1f - (fp - 0.55f) / 0.33f
            else        -> 0f
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cardTopY)
                .trackFlightTarget(),
            contentAlignment = Alignment.Center
        ) {
            val shown = displayCard
            if (shown != null) {
                val ringColor = when (displayAction) {
                    CardAction.PLAYED    -> if (displayIsPlayer) TealLight else Crimson
                    CardAction.DISCARDED -> if (displayIsPlayer) Teal.copy(alpha = 0.55f) else Crimson.copy(alpha = 0.55f)
                    CardAction.BURNED    -> Color(0xFFE07B39)
                    CardAction.STOLEN    -> Color(0xFF9B59B6)
                    null                 -> Gold.copy(alpha = 0.40f)
                }
                // Soupeřova karta: klasická fly-in animace (scale + translate shora).
                // Hráčova karta: statika se mění až po dopadu → není potřeba fade.
                val flyKey = "${shown.id}|${displayIsPlayer}"
                val flyProgress = remember(flyKey) { Animatable(if (displayIsPlayer) 1f else 0f) }
                LaunchedEffect(flyKey) {
                    if (!displayIsPlayer) flyProgress.animateTo(1f, tween(220))
                }
                // Outer box určuje fyzické místo (scaled)
                Box(
                    Modifier
                        .size(scaledW, scaledH)
                        .graphicsLayer {
                            val p = flyProgress.value
                            val e = 1f - (1f - p) * (1f - p)   // easeOut quadratic
                            if (displayIsPlayer) {
                                alpha = slotAlpha
                                scaleX = 1f
                                scaleY = 1f
                            } else {
                                val s = 0.55f + 0.45f * e
                                scaleX = s
                                scaleY = s
                                alpha = e * slotAlpha
                                translationY = (1f - e) * 160f * -1f   // AI strana: shora dolů
                            }
                        }
                        .clip(RoundedCornerShape(7.dp))
                        .border(2.dp, ringColor, RoundedCornerShape(7.dp))
                ) {
                    // requiredSize = přirozená velikost; graphicsLayer škáluje ze středu
                    Box(
                        Modifier
                            .requiredSize(cardNatW, cardNatH)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                    ) {
                        CardView(card = shown, canPlay = false, discardMode = false, showFade = false, onClick = {})
                        // Overlay: ikona akce přesně uprostřed karty
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (displayAction) {
                                CardAction.DISCARDED -> Image(
                                    painterResource(R.drawable.cross_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                CardAction.BURNED -> Text("🔥", fontSize = 38.sp, textAlign = TextAlign.Center)
                                else -> Unit
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .size(scaledW, scaledH)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Gold.copy(alpha = 0.03f))
                        .border(1.dp, Gold.copy(alpha = 0.10f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", color = TextMuted.copy(alpha = 0.18f), fontSize = (16f * cardScale).sp)
                }
            }
        }

    }
}

/**
 * Remapuje HP na vizuální frakci [0, 1].
 * Při HP = 0  →  0f   (zničeno, nic vidět)
 * Při HP > 0  →  [minFrac, 1f]  – minimum minFrac zaručuje, že budova je vždy trochu vidět
 */
private fun hpToVisualFrac(hp: Int, maxHp: Float, minFrac: Float = 0.15f): Float {
    if (hp <= 0) return 0f
    val raw = (hp / maxHp).coerceIn(0f, 1f)
    return minFrac + (1f - minFrac) * raw
}

// ─── Bojiště – pozadí ─────────────────────────────────────────────────────────

/**
 * Pool pozadí pro běžné (nekampaňové) hry – offline, WiFi MP, online, aréna, roguelike.
 * Plameny (torch sconces) jsou nafitované jen na castle_background – ostatní pozadí je
 * proto vždy zobrazují BEZ plamenů (viz NewBattlefield).
 */
private val RANDOM_BATTLE_BACKGROUNDS = listOf(
    R.drawable.castle_background,
    R.drawable.castle_background_swamp,
    R.drawable.castle_background_vulcan,
    R.drawable.castle_background_winter,
    R.drawable.castle_background_goblin,
    R.drawable.castle_background_citadela
)

/** Náhodně vybere pozadí bojiště z [RANDOM_BATTLE_BACKGROUNDS]. Volat JEDNOU na začátku hry a uložit. */
fun randomBattleBackground(): Int = RANDOM_BATTLE_BACKGROUNDS.random()

/**
 * Mapuje ID pozadí (string, jak ho posílá online server v MATCH_FOUND/GAME_STATE)
 * na drawable resource. Online zápasy NEvolí pozadí náhodně na klientovi (viz
 * [randomBattleBackground]) – server ho vybere jednou při matchmakingu a pošle
 * oběma hráčům stejné, aby se jim neodlišovalo.
 */
fun battleBackgroundDrawable(id: String): Int = when (id) {
    "castle_background_swamp"  -> R.drawable.castle_background_swamp
    "castle_background_vulcan" -> R.drawable.castle_background_vulcan
    "castle_background_winter" -> R.drawable.castle_background_winter
    "castle_background_goblin" -> R.drawable.castle_background_goblin
    "castle_background_citadela" -> R.drawable.castle_background_citadela
    else                        -> R.drawable.castle_background
}

/**
 * Pool "obyčejných" (nekampaňových) hradů/hradeb – stejné skiny, jaké si hráč
 * může vybrat v Profilu. Kampaňové skiny (castle_hory, wall_goblin, ...) tu
 * záměrně nejsou – ty patří jen dané lokaci. String ID (ne rovnou drawable),
 * aby šly použít i tam, kde se soupeř nese jako CampaignOpponent.aiCastleSkin/
 * aiWallSkin (roguelike – viz generateRogueEnemy()), ne přímo jako resource ID.
 */
private val RANDOM_OPPONENT_CASTLE_SKIN_IDS = listOf(
    "castle_player", "castle_player_2", "castle_player_3", "castle_player_4", "castle_player_5",
    "castle_player_6", "castle_player_7", "castle_player_8", "castle_player_9", "castle_player_10",
    "castle_player_11", "castle_player_12", "castle_player_13"
)
private val RANDOM_OPPONENT_WALL_SKIN_IDS = listOf(
    "wall_player", "wall_player2", "wall_player3", "wall_player4", "wall_player5", "wall_player6"
)

/** Náhodně vybere ID vzhledu soupeřova hradu mimo kampaň. */
fun randomOpponentCastleSkinId(): String = RANDOM_OPPONENT_CASTLE_SKIN_IDS.random()
/** Náhodně vybere ID vzhledu soupeřovy hradby mimo kampaň. */
fun randomOpponentWallSkinId(): String = RANDOM_OPPONENT_WALL_SKIN_IDS.random()

/** Náhodně vybere vzhled soupeřova hradu mimo kampaň. Volat JEDNOU na začátku hry a uložit. */
fun randomOpponentCastleResId(): Int = castleSkinDrawable(randomOpponentCastleSkinId())
/** Náhodně vybere vzhled soupeřovy hradby mimo kampaň. Volat JEDNOU na začátku hry a uložit. */
fun randomOpponentWallResId(): Int = wallSkinDrawable(randomOpponentWallSkinId())

// ─── Castle Structure ─────────────────────────────────────────────────────────

/** Mapuje ID skinu hradu na drawable resource. */
fun castleSkinDrawable(skinId: String): Int = when (skinId) {
    "castle_player_2"  -> R.drawable.castle_player_2
    "castle_player_3"  -> R.drawable.castle_player_3
    "castle_player_4"  -> R.drawable.castle_player_4
    "castle_player_5"  -> R.drawable.castle_player_5
    "castle_player_6"  -> R.drawable.castle_player_6
    "castle_player_7"  -> R.drawable.castle_player_7
    "castle_player_8"  -> R.drawable.castle_player_8
    "castle_player_9"  -> R.drawable.castle_player_9
    "castle_player_10" -> R.drawable.castle_player_10
    "castle_player_11" -> R.drawable.castle_player_11
    "castle_player_12" -> R.drawable.castle_player_12
    "castle_player_13" -> R.drawable.castle_player_13
    "castle_hory"       -> R.drawable.castle_hory
    "castle_citadela"   -> R.drawable.castle_citadela
    "castle_drak"       -> R.drawable.castle_drak
    else                -> R.drawable.castle_player
}

/** Mapuje ID skinu hradby na drawable resource. */
fun wallSkinDrawable(skinId: String): Int = when (skinId) {
    "wall_player2" -> R.drawable.wall_player2
    "wall_player3" -> R.drawable.wall_player3
    "wall_player4" -> R.drawable.wall_player4
    "wall_player5" -> R.drawable.wall_player5
    "wall_player6" -> R.drawable.wall_player6
    "wall_goblin"  -> R.drawable.wall_goblin
    "wall_hory"     -> R.drawable.wall_hory
    "wall_citadela" -> R.drawable.wall_citadela
    "wall_drak"     -> R.drawable.wall_drak
    else           -> R.drawable.wall_player
}

@Composable
private fun NewCastleStructure(
    castleHp: Int,
    wallHp: Int,
    isPlayer: Boolean,
    winTarget: Int = 60,
    maxWall: Int = MAX_WALL,
    castleResId: Int = R.drawable.castle_player,
    wallResId: Int = R.drawable.wall_player,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isPlayer) Teal    else Crimson
    val accentLight = if (isPlayer) TealLight else Color(0xFFFF7070)

    val wallFrac by animateFloatAsState(
        targetValue   = (wallHp / maxWall.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(400),
        label         = "wall_frac"
    )
    val wallBlocks = (10f * wallFrac).roundToInt().coerceIn(0, 10)

    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (isPlayer) {
            CastleTowerBlock(castleHp, accentColor, accentLight, isPlayer = true,  winTarget = winTarget, castleResId = castleResId)
            WallBlock(wallHp, wallBlocks, accentColor, isPlayer = true, wallResId = wallResId, maxWall = maxWall)
        } else {
            WallBlock(wallHp, wallBlocks, accentColor, isPlayer = false, wallResId = wallResId, maxWall = maxWall)
            CastleTowerBlock(castleHp, accentColor, accentLight, isPlayer = false, winTarget = winTarget, castleResId = castleResId)
        }
    }
}

@Composable
private fun CastleTowerBlock(
    castleHp: Int,
    accentColor: Color,
    accentLight: Color,
    isPlayer: Boolean,
    winTarget: Int = 60,
    castleResId: Int = R.drawable.castle_player
) {
    val castleFullH = 165.dp
    val castleFullW = 110.dp
    // Při winTarget ≥ 999 (výstavba zakázána) použij vizuální strop 60,
    // jinak by hrad při 30 HP vypadal skoro zahrabaný pod zemí.
    val visualMaxHp = if (winTarget >= 999) 60f else winTarget.toFloat()
    val hpFrac = hpToVisualFrac(castleHp, maxHp = visualMaxHp)

    val offsetY by animateDpAsState(
        targetValue   = castleFullH * (1f - hpFrac),
        animationSpec = tween(600, easing = EaseOutCubic),
        label         = "castle_emerge"
    )

    // Vnější Box: bez clipu → plovoucí čísla mohou přesahovat nahoru
    Box(modifier = Modifier.size(castleFullW, castleFullH)) {
        // Clip box – pouze obrázek hradu bez badge (badge je nahoře v rohu)
        Box(
            modifier = Modifier
                .size(castleFullW, castleFullH)
                .clip(androidx.compose.ui.graphics.RectangleShape)
        ) {
            Image(
                painter            = painterResource(castleResId),
                contentDescription = if (isPlayer) "Hráčův hrad" else "Soupeřův hrad",
                modifier           = Modifier
                    .size(castleFullW, castleFullH)
                    .offset(y = offsetY)
                    .graphicsLayer { scaleX = if (isPlayer) 1f else -1f },
                contentScale       = ContentScale.Fit,
                alignment          = Alignment.BottomCenter
            )
        }
        // zIndex(1f) zajistí vykreslení čísla NAVRCH textury hradu
        Box(Modifier.fillMaxSize().zIndex(1f)) {
            HpFloats(castleHp, sizeSp = 17f, startOffsetY = offsetY)
        }
    }
}

@Composable
private fun WallBlock(wallHp: Int, blockCount: Int, accentColor: Color, isPlayer: Boolean = true, wallResId: Int = R.drawable.wall_player, maxWall: Int = MAX_WALL) {
    val wallFullW = 42.dp
    val wallFullH = 58.dp
    val wallFrac  = hpToVisualFrac(wallHp, maxHp = maxWall.toFloat())

    val offsetY by animateDpAsState(
        targetValue   = wallFullH * (1f - wallFrac),
        animationSpec = tween(600, easing = EaseOutCubic),
        label         = "wall_emerge"
    )

    // Vnější Box: bez clipu → plovoucí čísla mohou přesahovat nahoru
    Box(modifier = Modifier.size(wallFullW, wallFullH)) {
        Box(
            modifier = Modifier
                .size(wallFullW, wallFullH)
                .clip(androidx.compose.ui.graphics.RectangleShape)
        ) {
            Image(
                painter            = painterResource(wallResId),
                contentDescription = "Zeď",
                modifier           = Modifier
                    .size(wallFullW, wallFullH)
                    .offset(y = offsetY)
                    .graphicsLayer { scaleX = if (isPlayer) 1f else -1f },
                contentScale       = ContentScale.FillBounds
            )
        }
        // zIndex(1f) zajistí vykreslení čísla NAVRCH textury hradby
        Box(Modifier.fillMaxSize().zIndex(1f)) {
            HpFloats(wallHp, sizeSp = 13f, startOffsetY = offsetY)
        }
    }
}

// ─── Castle HP Badge (nahoře v rohu) ─────────────────────────────────────────

@Composable
private fun CastleHpBadge(
    castleHp: Int,
    wallHp: Int,
    isPlayer: Boolean,
    winTarget: Int = 60,
    maxWall: Int = MAX_WALL,
    handSize: Int = 0,
    maxHandSize: Int = 7,
    modifier: Modifier = Modifier
) {
    val accentLight = if (isPlayer) TealLight else Color(0xFFFF7070)
    val accentColor = if (isPlayer) Teal else Crimson

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(painterResource(R.drawable.castle_icon), contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                if (winTarget >= 999) "$castleHp" else "$castleHp/$winTarget",
                color      = accentLight,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(painterResource(R.drawable.wall_icon), contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                "$wallHp/$maxWall",
                color      = accentColor,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(painterResource(playerCardBackResId()), contentDescription = null,
                modifier = Modifier.size(width = 8.dp, height = 12.dp))
            Text(
                "$handSize/$maxHandSize",
                color      = Color(0xFFCCBB88),
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Log Overlay ──────────────────────────────────────────────────────────────

@Composable
fun LogOverlay(log: List<LogEntry>, onDismiss: () -> Unit, lostCards: List<CardHistoryEntry> = emptyList(), onShowLostCards: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false, onClick = {})
                .clip(RoundedCornerShape(12.dp))
                .paint(painterResource(R.drawable.mulligan_background), contentScale = ContentScale.Crop)
                .border(1.dp, Gold.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(18.dp)
                .widthIn(max = 360.dp)
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(LocalStrings.current.gameLog, color = Gold, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            HorizontalDivider(color = Gold.copy(alpha = 0.20f))
            LogPanel(log = log, modifier = Modifier.weight(1f).fillMaxWidth(), scrollable = true)
            if (lostCards.isNotEmpty() && onShowLostCards != null) {
                PlainButton(
                    text      = "Spálené & ukradené (${lostCards.size})",
                    modifier  = Modifier.fillMaxWidth(),
                    buttonRes = R.drawable.plain_button_longer,
                    textColor = Color(0xFF9B59B6),
                    fontSize  = 11.sp,
                    paddingH  = 12.dp,
                    paddingV  = 8.dp,
                    onClick   = { onDismiss(); onShowLostCards() }
                )
            }
            PlainButton(
                text      = "Zavřít",
                modifier  = Modifier.align(Alignment.CenterHorizontally),
                textColor = TextMuted,
                fontSize  = 11.sp,
                paddingH  = 28.dp,
                paddingV  = 8.dp,
                onClick   = onDismiss
            )
        }
    }
}

// ─── Offline mini history card ────────────────────────────────────────────────

@Composable
private fun OfflineMiniHistoryCard(
    card: Card, action: CardAction, isMine: Boolean,
    onClick: () -> Unit = {}
) {
    val borderColor = when (action) {
        CardAction.BURNED    -> Color(0xFFE07B39).copy(alpha = 0.85f)
        CardAction.STOLEN    -> Color(0xFF9B59B6).copy(alpha = 0.85f)
        CardAction.DISCARDED -> if (isMine) Teal.copy(alpha = 0.55f) else Crimson.copy(alpha = 0.55f)
        CardAction.PLAYED    -> if (isMine) TealLight.copy(alpha = 0.80f) else Crimson.copy(alpha = 0.80f)
    }
    val bgColor = when (action) {
        CardAction.BURNED    -> Color(0xFF1A1000)
        CardAction.STOLEN    -> Color(0xFF1A0A2A)
        CardAction.DISCARDED -> Color(0xFF250A0A)
        CardAction.PLAYED    -> Color(0xFF1A1320)
    }
    val overlayIcon = when (action) {
        CardAction.BURNED    -> "🔥"
        CardAction.STOLEN    -> "🃏"
        CardAction.DISCARDED -> null   // cross_icon se vykreslí samostatně níže
        CardAction.PLAYED    -> null
    }
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 32.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
    ) {
        MiniCardFront(card = card)
        if (overlayIcon != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    overlayIcon,
                    color      = borderColor.copy(alpha = 0.95f),
                    fontSize   = if (action == CardAction.BURNED) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (action == CardAction.DISCARDED) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(R.drawable.cross_icon),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

// ─── AI Hand Row ──────────────────────────────────────────────────────────────
@Composable
fun AiHandRow(
    handSize: Int,
    lastPlayed: Card?,
    currentTurn: Int,
    onMenu: () -> Unit,
    arenaWins: Int = -1,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(BgPanel.copy(alpha = 0.65f))
            .padding(vertical = 5.dp)
    ) {
        // Menu tlačítko vlevo
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                .clickable { onMenu() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("☰ Menu", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        // Karty AI uprostřed
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (lastPlayed != null) {
                CardBackPlayed(lastPlayed)
                Spacer(Modifier.width(5.dp))
            }
            repeat(handSize) { i ->
                if (i > 0) Spacer(Modifier.width(5.dp))
                CardBack()
            }
        }

        // Vpravo: arena badge nebo čítač kol
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (arenaWins >= 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp))
                        .background(Gold.copy(alpha = 0.1f))
                        .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Image(painterResource(R.drawable.utok_icon), contentDescription = null, modifier = Modifier.size(11.dp))
                        Text("$arenaWins", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            PlainButton(
                text      = "Kolo $currentTurn",
                buttonRes = R.drawable.plain_button,
                modifier  = Modifier.size(width = 72.dp, height = 34.dp),
                textColor = Gold.copy(alpha = 0.85f),
                fontSize  = 11.sp,
                lighten   = 0.05f,
                paddingH  = 0.dp,
                paddingV  = 0.dp,
            )
        }
    }
}

