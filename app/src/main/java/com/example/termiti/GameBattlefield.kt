package com.example.termiti

import kotlin.math.cos
import kotlin.random.Random
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.CompositingStrategy
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
    tutorialTargets: Set<TutTarget> = emptySet(),             // tutoriál: co má svítit zlatě
    playerCastleResId: Int = R.drawable.castle_player,        // skin hradu hráče
    opponentCastleResId: Int = R.drawable.castle_player,      // skin hradu soupeře/AI
    playerWallResId: Int = R.drawable.wall_player,             // skin hradby hráče
    opponentWallResId: Int = R.drawable.wall_player,            // skin hradby soupeře/AI
    // Pozadí bojiště – náhodné pro běžné hry (viz randomBattleBackground()) nebo
    // vynucené konkrétní pro kampaň (např. castle_background_goblin). Plameny se
    // renderují JEN pro výchozí castle_background – jinde by nesedly na pochodně.
    backgroundResId: Int = R.drawable.castle_background,
    /**
     * false = nerozdávej rub soupeře animací (konec hry / review).
     *
     * Přílet rubu startuje mimo pravý okraj obrazovky (translationX = flyStartPx)
     * a NEMÁ alpha bránu – dokud animace nedoběhne, karta reálně existuje, jen ji
     * není vidět. Když hra skončí přesně v tom okně, zůstane rub navždy za krajem:
     * hráč vidí v soupeřově ruce o kartu míň, než kolik jich ukáže „Prohlédnout hru".
     */
    animateDraws: Boolean = true
) {
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .paint(
                painterResource(backgroundResId),
                contentScale = ContentScale.Crop
            )
    ) {
        // Oživení pozadí (savana, bažiny…) – viz BattlefieldAmbience. Kreslí se jako první,
        // hned nad obrázkem pozadí: karty soupeře, hrady i zahraná karta jsou nad ním.
        BattlefieldAmbience(backgroundResId, Modifier.matchParentSize())

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
        // Index ve frontě → (slot, pozice rubu), odkud ghost té ztráty odletí. Přiděluje
        // se všem čekajícím ztrátám NARÁZ, dřív než první delay – viz níž.
        val lossOrigins  = remember { mutableMapOf<Int, Pair<Int, Rect>>() }
        // Sloty, ze kterých ghost odletěl/odletí. Zmenšení ruky odebere přednostně je,
        // takže zmizí právě ten rub, nad kterým hořel ghost, a ne jiný náhodný.
        val lossSlotKeys = remember { mutableListOf<Int>() }

        // Ghost ztracené karty se pouští PODLE FRONTY, ne podle zmenšení stripu.
        // Server pošle CARD_LOST hned, ale počet karet v soupeřově ruce se často
        // vůbec nezmenší: když spálení zároveň ukončí kolo, soupeř si v témže
        // GAME_STATE lízne a velikost ruky vyjde nastejno. Ghost se pak neukázal
        // vůbec – a protože kurzor zůstal stát, vyskočil až o několik kol později
        // u úplně jiné akce.
        LaunchedEffect(oppLossQueue.size) {
            // Fronta se resetovala (nová hra) → kurzor taky, jinak by zůstal za koncem
            if (oppLossQueue.size < oppLossCursor) {
                oppLossCursor = 0; lossOrigins.clear(); lossSlotKeys.clear()
            }
            // Ruby jsou anonymní (neznáme slot konkrétní karty) → každá ztráta dostane
            // náhodný rub, ale pokaždé JINÝ, a pozice se zachytí hned teď.
            //
            // Dřív se rub losoval až těsně před každým ghostem. Spálená knihovna na
            // soupeře se 2 kartami: ghost 1 vyletěl, pak delay(220) – mezitím zmenšení
            // ruky (2 → 0) odebralo oba ruby i s pozicemi, takže ghost 2 neměl odkud
            // vyletět a `continue` ho tiše zahodil. Vizuálně shořela jen 1 karta.
            // Při víc kartách v ruce zas mohl ghost 2 padnout na stejný rub jako ghost 1.
            lossSlotKeys.retainAll(aiSlotIds)
            val busy = lossSlotKeys.toSet() + lossOrigins.values.map { it.first }
            val free = aiSlotIds.filter { it !in busy && it in aiSlotRects }.shuffled().toMutableList()
            for (i in oppLossCursor until oppLossQueue.size) {
                if (i in lossOrigins) continue
                val key = free.removeFirstOrNull() ?: break
                lossOrigins[i] = key to aiSlotRects.getValue(key)
                lossSlotKeys.add(key)
            }
            while (oppLossCursor < oppLossQueue.size) {
                val idx   = oppLossCursor
                val entry = oppLossQueue[idx]
                oppLossCursor++
                // Záloha, kdyby na ztrátu nezbyl volný rub (víc ztrát než karet)
                val rect = lossOrigins.remove(idx)?.second
                    ?: aiSlotIds.mapNotNull { aiSlotRects[it] }.randomOrNull() ?: continue
                // Líc ve velikosti odhalené karty (31×44) na pozici rubu (22×32)
                val w = rect.width  * (31f / 22f)
                val h = rect.height * (44f / 32f)
                lossFx?.spawnLoss(
                    entry.card, null, entry.action,
                    Rect(
                        rect.center.x - w / 2f, rect.center.y - h / 2f,
                        rect.center.x + w / 2f, rect.center.y + h / 2f
                    )
                )
                // Víc ztrát naráz (Spálená knihovna) ať jde po sobě, ne přes sebe
                delay(220L)
            }
        }

        LaunchedEffect(aiHandSize) {
            if (aiSlotIds.size < aiHandSize) {
                // Líznutí: přidej nové klíče na konec a označ je pro fly-in animaci
                val added = mutableSetOf<Int>()
                // Ruka rostla → čekající odebrání z dřívější ztráty už nepřijde (soupeř
                // si v témže stavu dolízl a velikost vyšla nastejno); nedrž je dál.
                lossSlotKeys.clear()
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
                    // Ztráta z fronty má svůj slot už přidělený (ghost nad ním hořel).
                    val removeAt = revealedAiCardIdx?.takeIf { it < aiSlotIds.size }
                        ?: lossSlotKeys.firstNotNullOfOrNull { k -> aiSlotIds.indexOf(k).takeIf { it >= 0 } }
                        ?: aiSlotIds.indices.random()
                    val removedKey = aiSlotIds[removeAt]
                    aiSlotIds.removeAt(removeAt)
                    lossSlotKeys.remove(removedKey)
                    // Rub zmizel bez odhalení + poslední akce = spálení/krádež →
                    // ghost efekt (oranžová/fialová), aby bylo vidět, že soupeř
                    // přišel o kartu z ruky a jak.
                    //
                    // Tohle je JEN fallback pro starší server, který v CARD_LOST
                    // neposílá `fromHand` (fronta pak zůstane prázdná). Když fronta
                    // existuje, ghost už pustil efekt výš – tady by vznikl duplikát.
                    if (!showReveal && lossFx != null && oppLossQueue.isEmpty()) {
                        aiSlotRects[removedKey]?.let { rect ->
                            val act: CardAction
                            val face: Card?
                            if (lastCardAction == CardAction.BURNED || lastCardAction == CardAction.STOLEN) {
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
                        if (animateDraws && slotKey in aiNewSlotIds) aiNewSlotIds.sorted().indexOf(slotKey) else -1
                    }
                    val flyIn = remember(slotKey) { Animatable(if (appearIdx >= 0) 1f else 0f) }
                    var flyStartPx by remember(slotKey) { mutableFloatStateOf(0f) }
                    if (appearIdx >= 0) LaunchedEffect(slotKey) {
                        delay(200L * appearIdx)
                        flyIn.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                    }
                    // Konec hry uprostřed příletu: dosaď rub do klidové polohy, ať
                    // nezůstane viset za okrajem. Řeší i rub, jehož animace se
                    // rozeběhla a nestihla doběhnout.
                    LaunchedEffect(animateDraws) { if (!animateDraws) flyIn.snapTo(0f) }
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
            glowCastle  = TutTarget.PLAYER_CASTLE in tutorialTargets,
            glowWall    = TutTarget.PLAYER_WALL in tutorialTargets,
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
            glowCastle  = TutTarget.ENEMY_CASTLE in tutorialTargets,
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
            cardBackResId = playerCardBackResId(),
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
            cardBackResId = opponentCardBackResId,
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

        // ── Nahlédnutí pod vrchní kartu ───────────────────────────────────────
        // Karty, které ležely nahoře před tou současnou (nejnovější první, max PILE_PEEK_MAX).
        // Vede si je bojiště samo z toho, co postupně zobrazilo – funguje tak stejně offline,
        // online i v přehrávači, bez dalších dat od view modelu.
        val pileUnder = remember { mutableStateListOf<PileEntry>() }
        // Rozevření vějíře v px: + doprava, − doleva. Mění se přímo v obsluze tažení (žádná
        // korutina na každou událost), po puštění prstu se vrací na 0 jednou animací níž.
        var pullPx   by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        // Starší karty jsou ve stromu od začátku tažení do doběhnutí návratu. Dřív je řídil
        // práh |pull| > 0,5 px: kolem nuly (prst skoro na místě, dokmit pružiny) se přes něj
        // přecházelo tam a zpět a šest celých karet se každý snímek skládalo a zahazovalo.
        var fanOpen by remember { mutableStateOf(false) }
        // Krok mezi vysunutými kartami = 4/5 šířky karty, takže z každé starší karty je vidět
        // většina (název, cena i text). Vějíř se rozkládá na OBĚ strany od středu – vrchní karta
        // jede ve směru tahu, zbytek balíčku ustupuje opačně – a využije tak celou šířku bojiště.
        val pileStepPx  = with(LocalDensity.current) { (scaledW * PILE_PEEK_VISIBLE).toPx() }
        // Kolik starších karet se při tomhle kroku do bojiště vejde (rozpětí vějíře = počet × krok)
        val pileMaxUnder = with(LocalDensity.current) {
            ((maxWidth - scaledW - 12.dp).toPx() / pileStepPx).toInt().coerceIn(1, PILE_PEEK_MAX)
        }

        LaunchedEffect(lastCard?.id, lastCardIsPlayer, flight?.landedPlayerCardId) {
            val c = lastCard
            if (c == null) { displayCard = null; pileUnder.clear(); return@LaunchedEffect }
            // Soupeřovu kartu zobraz hned; hráčovu až po dokončení letu
            val landed = !lastCardIsPlayer ||
                         flight == null ||
                         flight.landedPlayerCardId == c.id
            if (landed) {
                // Dosavadní vrchní karta jde pod novou
                val prev = displayCard
                if (prev != null && prev.id != c.id) {
                    pileUnder.add(0, PileEntry(prev, displayAction, displayIsPlayer))
                    while (pileUnder.size > PILE_PEEK_MAX) pileUnder.removeAt(pileUnder.lastIndex)
                }
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
        // Návrat po puštění prstu. Nové tažení (dragging = true) efekt přeruší a vějíř zůstane
        // otevřený; zavře se až po doběhnutí animace.
        LaunchedEffect(dragging) {
            if (dragging) return@LaunchedEffect
            if (pullPx != 0f) {
                animate(pullPx, 0f, animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> pullPx = v }
            }
            fanOpen = false
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cardTopY)
                .trackFlightTarget()
                // Tažením do strany se zpod vrchní karty vysouvají starší; puštěním se vrátí
                .pointerInput(pileStepPx, pileMaxUnder) {
                    detectDragGestures(
                        onDragStart  = { if (pileUnder.isNotEmpty()) { fanOpen = true; dragging = true } },
                        onDragEnd    = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, drag ->
                        if (pileUnder.isEmpty()) return@detectDragGestures
                        change.consume()
                        val limit = pileStepPx * minOf(pileUnder.size, pileMaxUnder)
                        pullPx = (pullPx + drag.x).coerceIn(-limit, limit)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Vrchní karta jede s prstem (hráč ji zná, může ležet pod ním) a starší se za ní
            // táhnou jako rozložený vějíř: k-tá (1 = hned pod vrchní) je o k kroků pozadu.
            // Čím delší tah, tím víc karet se odlepí od balíčku; ty nejstarší zůstávají na místě.
            // Kreslí se jen během nahlížení, nejhlubší první (nejnovější je navrchu).
            if (fanOpen) {
                for (k in pileUnder.size downTo 1) {
                    val entry = pileUnder[k - 1]
                    key(entry.card.id, k) {
                        PileCard(
                            entry    = entry,
                            scaledW  = scaledW, scaledH = scaledH,
                            natW     = cardNatW, natH = cardNatH, cardScale = cardScale,
                            modifier = Modifier.graphicsLayer {
                                // Každá karta vějíře je vlastní vrstva (textura): nakreslí se jednou
                                // a při tažení se jen posouvá. Bez toho se každý snímek kreslilo až
                                // sedm celých karet z velkých textur (rám 800×1195, vzácnost, art)
                                // a grafická paměť přetekla – textury se pak nahrávaly znovu každý
                                // snímek a hra spadla na ~6 snímků za sekundu.
                                compositingStrategy = CompositingStrategy.Offscreen
                                // Rozevření |v|: vrchní karta je o půlku ve směru tahu, k-tá karta
                                // k kroků za ní; karty, na které se ještě nedostalo, drží pohromadě
                                // na opačném konci (zbytek balíčku).
                                val v = pullPx
                                val open = kotlin.math.abs(v)
                                translationX = kotlin.math.sign(v) * (open / 2f - minOf(k * pileStepPx, open))
                            }
                        )
                    }
                }
            }
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
                            translationX = pullPx / 2f         // nahlížení pod balíček: vějíř se rozevírá od středu
                            // Během nahlížení vlastní vrstva – viz karty vějíře výš
                            compositingStrategy = if (fanOpen) CompositingStrategy.Offscreen else CompositingStrategy.Auto
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
                                CardAction.BURNED -> Image(
                                    painterResource(R.drawable.burn_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
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

/** Kolik karet pod vrchní si bojiště pamatuje (odhalit jich jde tolik, kolik se vejde na šířku). */
private const val PILE_PEEK_MAX = 6

/** Jaká část šířky starší karty je při nahlížení vidět (krok vějíře). */
private const val PILE_PEEK_VISIBLE = 0.8f

/** Karta, která ležela na odhazovacím balíčku – s tím, co se s ní stalo a čí byla. */
private class PileEntry(val card: Card, val action: CardAction?, val isPlayer: Boolean)

/** Barva rámečku karty na odhazovacím balíčku podle akce a hráče. */
private fun pileRingColor(action: CardAction?, isPlayer: Boolean): Color = when (action) {
    CardAction.PLAYED    -> if (isPlayer) TealLight else Crimson
    CardAction.DISCARDED -> if (isPlayer) Teal.copy(alpha = 0.55f) else Crimson.copy(alpha = 0.55f)
    CardAction.BURNED    -> Color(0xFFE07B39)
    CardAction.STOLEN    -> Color(0xFF9B59B6)
    null                 -> Gold.copy(alpha = 0.40f)
}

/** Starší karta z odhazovacího balíčku – stejný vzhled jako vrchní (rámeček, ikona zahození/spálení). */
@Composable
private fun PileCard(
    entry: PileEntry,
    scaledW: Dp, scaledH: Dp,
    natW: Dp, natH: Dp, cardScale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(scaledW, scaledH)
            .clip(RoundedCornerShape(7.dp))
            .border(2.dp, pileRingColor(entry.action, entry.isPlayer), RoundedCornerShape(7.dp))
    ) {
        Box(
            Modifier
                .requiredSize(natW, natH)
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        ) {
            CardView(card = entry.card, canPlay = false, discardMode = false, showFade = false, onClick = {})
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (entry.action) {
                    CardAction.DISCARDED -> Image(painterResource(R.drawable.cross_icon), contentDescription = null, modifier = Modifier.size(48.dp))
                    CardAction.BURNED    -> Image(painterResource(R.drawable.burn_icon),  contentDescription = null, modifier = Modifier.size(48.dp))
                    else -> Unit
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

// ─── Oživení pozadí bojiště ───────────────────────────────────────────────────
//
// Lehké animace nad statickým obrázkem pozadí (1200×400). Záměrně nenáročné: nic se
// nenahrává (žádné snímky animace, žádná textura navíc), kreslí se jen průhledné
// přechody a tráva se překresluje z téhož obrázku, který je už načtený jako pozadí.
// Pozice se zadávají v pixelech obrázku; přepočet na displej má stejný ořez jako
// pozadí (ContentScale.Crop).

/** Kreslicí pomůcky pro jednu scénu – souřadnice a velikosti v pixelech obrázku 1200×400. */
private class AmbienceScope(
    private val draw: DrawScope,
    private val bg: ImageBitmap,
    val time: Float
) {
    private val imgW = if (draw.size.width / draw.size.height >= 3f) draw.size.width else draw.size.height * 3f
    private val k    = imgW / 1200f
    private val offX = (draw.size.width - imgW) / 2f
    private val offY = (draw.size.height - imgW / 3f) / 2f
    private fun at(x: Float, y: Float) = Offset(offX + x * k, offY + y * k)

    /** Měkká kruhová záře / obláček. */
    fun glow(x: Float, y: Float, radius: Float, color: Color, alpha: Float) {
        if (alpha <= 0.005f || radius <= 0f) return
        val c = at(x, y)
        draw.drawCircle(
            brush  = Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceAtMost(1f)), Color.Transparent), c, radius * k),
            radius = radius * k,
            center = c
        )
    }

    /** Plná tečka (vločka, zrnko) – levnější než [glow], pro desítky drobných částic. */
    fun dot(x: Float, y: Float, radius: Float, color: Color, alpha: Float) {
        if (alpha <= 0.005f) return
        draw.drawCircle(color.copy(alpha = alpha.coerceAtMost(1f)), radius * k, at(x, y))
    }

    /**
     * Tráva ve větru: plochy se překreslí z obrázku po malých dlaždicích, každá posunutá
     * o pár pixelů do strany podle vlny, která plochou prochází zleva doprava. Bližší tráva
     * (níž v obrázku) se hýbe víc. Pod dlaždicemi zůstává nehybné pozadí, takže posunem
     * nevznikají díry; ořez na mnohoúhelník drží pohyb mimo kameny, ploty a vodu.
     */
    fun sway(polys: List<List<Pair<Float, Float>>>, strength: Float = 1f) = with(draw) {
        val srcK = bg.width / 1200f
        for (poly in polys) {
            val path = Path().apply {
                poly.forEachIndexed { i, (px, py) -> at(px, py).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                close()
            }
            val x0 = poly.minOf { it.first };  val x1 = poly.maxOf { it.first }
            val y0 = poly.minOf { it.second }; val y1 = poly.maxOf { it.second }
            clipPath(path) {
                var y = y0
                while (y < y1) {
                    val h   = minOf(GRASS_TILE_H, y1 - y)
                    val amp = (1.6f + 3.4f * ((y - 190f) / 160f).coerceIn(0f, 1f)) * k * strength
                    var x = x0
                    while (x < x1) {
                        val w    = minOf(GRASS_TILE_W, x1 - x)
                        val gust = 0.6f + 0.4f * sin(time * 0.9f - x / 260f)
                        val dx   = amp * gust * (sin(time * 2.6f - x / 150f + y / 37f) + 0.35f * sin(time * 5.3f - x / 60f + y / 11f))
                        val dst  = at(x, y)
                        translate(left = dx) {
                            drawImage(
                                image     = bg,
                                srcOffset = IntOffset((x * srcK).toInt(), (y * srcK).toInt()),
                                srcSize   = IntSize((w * srcK).toInt().coerceAtLeast(1), (h * srcK).toInt().coerceAtLeast(1)),
                                dstOffset = IntOffset(dst.x.toInt(), dst.y.toInt()),
                                dstSize   = IntSize(kotlin.math.ceil(w * k).toInt() + 1, kotlin.math.ceil(h * k).toInt() + 1)
                            )
                        }
                        x += GRASS_TILE_W
                    }
                    y += GRASS_TILE_H
                }
            }
        }
    }
}

/** Velikost dlaždice trávy v px obrázku: menší = plynulejší vlna, víc kreslení. */
private const val GRASS_TILE_W = 24f
private const val GRASS_TILE_H = 6f

/**
 * Plátno přes celé bojiště, na které [scene] každý snímek nakreslí oživení pozadí [bgRes].
 * Čas se posouvá každý snímek – při 20 krocích za sekundu se drobné letící částice trhaly.
 */
@Composable
private fun BackgroundAmbience(@DrawableRes bgRes: Int, modifier: Modifier, scene: AmbienceScope.() -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }   // sekundy
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    val bg = ImageBitmap.imageResource(bgRes)
    Canvas(modifier) { AmbienceScope(this, bg, t).scene() }
}

/** Oživení pro dané pozadí, nebo nic, pokud pozadí žádné nemá. */
@Composable
private fun BattlefieldAmbience(@DrawableRes backgroundResId: Int, modifier: Modifier = Modifier) {
    when (backgroundResId) {
        R.drawable.castle_background_goblin -> BackgroundAmbience(backgroundResId, modifier) { savannaScene() }
        R.drawable.castle_background_swamp  -> BackgroundAmbience(backgroundResId, modifier) { swampScene() }
        R.drawable.castle_background_winter -> BackgroundAmbience(backgroundResId, modifier) { winterScene() }
        R.drawable.castle_background_vulcan -> BackgroundAmbience(backgroundResId, modifier) { volcanoScene() }
        R.drawable.castle_background_citadela -> BackgroundAmbience(backgroundResId, modifier) { citadelScene() }
        R.drawable.castle_background        -> BackgroundAmbience(backgroundResId, modifier) { wastelandScene() }
    }
}

// ── Savana (castle_background_goblin) ─────────────────────────────────────────
// Tráva ve větru, mihotání ohně s jiskrami, kouř u vzdálených táborů, prosvítání slunce, prach.

/** Plochy čisté trávy – z anim_savanna/animate.py. */
private val SAVANNA_GRASS: List<List<Pair<Float, Float>>> = listOf(
    listOf(175f to 290f, 250f to 278f, 285f to 300f, 280f to 340f, 215f to 350f, 180f to 335f),
    listOf(350f to 200f, 600f to 195f, 655f to 205f, 665f to 225f, 600f to 238f, 500f to 245f, 400f to 250f, 350f to 250f),
    listOf(400f to 300f, 445f to 300f, 455f to 325f, 400f to 330f),
    listOf(720f to 320f, 800f to 300f, 800f to 400f, 690f to 400f, 700f to 350f),
    listOf(870f to 285f, 950f to 275f, 1005f to 262f, 1005f to 290f, 985f to 300f, 960f to 330f, 900f to 335f, 840f to 325f, 830f to 290f),
    listOf(985f to 200f, 1040f to 197f, 1040f to 215f, 1025f to 250f, 990f to 245f)
)

/** Zrnko prachu ve větru: výška, rychlost přeletu, kolébání, velikost a průhlednost. */
private class Dust(rnd: Random, yMin: Float = 170f, yMax: Float = 370f) {
    val start    = rnd.nextFloat()
    val y        = yMin + rnd.nextFloat() * (yMax - yMin)
    val speed    = 0.03f + rnd.nextFloat() * 0.035f
    val sway     = 3f + rnd.nextFloat() * 8f
    val swayFreq = 0.5f + rnd.nextFloat() * 1.2f
    val phase    = rnd.nextFloat() * 6.283f
    val size     = 1.8f + rnd.nextFloat() * 2.6f
    val alpha    = 0.25f + rnd.nextFloat() * 0.25f
}
private val SAVANNA_DUST = Random(4711).let { rnd -> List(16) { Dust(rnd) } }

private fun AmbienceScope.savannaScene() {
    sway(SAVANNA_GRASS)

    // Slunce za mraky: pomalé prosvítání uprostřed oblohy
    glow(600f, 58f, 190f, Color(0xFFFFF0B8), 0.07f + 0.07f * sin(time * 0.9f) + 0.03f * sin(time * 2.3f + 1.1f))

    // Oheň: nepravidelné mihotání ze tří nesoudělných sinusovek
    val flick = 0.5f + 0.22f * sin(time * 7.1f) + 0.16f * sin(time * 12.7f + 1.3f) + 0.10f * sin(time * 19.3f + 0.6f)
    glow(488f, 160f, 30f + 8f * flick, Color(0xFFFF8A2B), 0.40f + 0.40f * flick)
    glow(488f, 158f, 9f + 3f * flick,  Color(0xFFFFE9B8), 0.55f + 0.40f * flick)
    glow(516f, 163f, 17f, Color(0xFFFF7A22), 0.22f + 0.24f * (0.5f + 0.5f * sin(time * 5.3f + 2.1f)))
    // Jiskry: stoupají nad ohněm a zhasínají
    for (i in 0 until 6) {
        val p = ((time / 1.7f) + i * 0.173f) % 1f
        val x = 488f + sin(i * 2.4f + p * 6f) * (3f + 6f * p)
        glow(x, 158f - 30f * p, 2.2f, Color(0xFFFFC56A), 0.9f * (1f - p))
    }

    // Kouř: u každého ohniště pět obláčků za sebou; stoupají, rozšiřují se a mizí
    fun plume(x: Float, y: Float, rise: Float, width: Float, strength: Float, phase: Float) {
        for (i in 0 until 5) {
            val p     = ((time / 6f) + phase + i / 5f) % 1f            // 0 = u ohně, 1 = nahoře
            val drift = (10f * p + sin(p * 5.2f + phase * 9f + i) * 4f) * p   // vítr ho odnáší doprava
            val fade  = (p / 0.15f).coerceAtMost(1f) * (1f - p)        // náběh a pozvolné zmizení
            glow(x + drift, y - rise * p, width * (0.6f + 1.3f * p), Color(0xFF5A5448), 0.62f * strength * fade)
        }
    }
    plume(482f, 150f, rise = 74f, width = 17f, strength = 1.0f, phase = 0.00f)
    plume(511f, 152f, rise = 50f, width = 10f, strength = 0.7f, phase = 0.37f)
    plume(858f, 128f, rise = 72f, width = 17f, strength = 1.0f, phase = 0.61f)

    // Prach ve větru: světlá zrnka táhnoucí nad trávou zleva doprava, každé náhodnou drahou
    for (d in SAVANNA_DUST) {
        val p    = (time * d.speed + d.start) % 1f
        val edge = (p / 0.1f).coerceAtMost(1f) * ((1f - p) / 0.1f).coerceAtMost(1f)
        glow(-20f + 1240f * p, d.y + sin(time * d.swayFreq + d.phase) * d.sway, d.size, Color(0xFFFFF2C4), d.alpha * edge)
    }
}

// ── Bažiny (castle_background_swamp) ──────────────────────────────────────────
// Bludičky, světlušky, mlha táhnoucí nad vodou, měsíc za mraky, odlesky na hladině, tráva.

/** Tráva na ostrůvku, na pravém břehu a vlevo u kořenů. */
private val SWAMP_GRASS: List<List<Pair<Float, Float>>> = listOf(
    listOf(612f to 226f, 690f to 220f, 752f to 228f, 760f to 240f, 700f to 248f, 630f to 246f),
    listOf(830f to 268f, 990f to 258f, 1030f to 285f, 960f to 320f, 850f to 318f, 815f to 290f),
    listOf(120f to 300f, 260f to 285f, 300f to 330f, 250f to 360f, 130f to 355f)
)

/** Bludičky namalované v obrázku: x, y, velikost. */
private val SWAMP_WISPS = listOf(
    Triple(215f, 320f, 15f), Triple(298f, 198f, 7f), Triple(420f, 186f, 6f),
    Triple(505f, 183f, 5f),  Triple(688f, 232f, 9f), Triple(728f, 225f, 5f)
)

private fun AmbienceScope.swampScene() {
    sway(SWAMP_GRASS, strength = 0.8f)

    // Měsíc za mraky: pomalu sílí a slábne, s ním i svit na mracích
    val moon = 0.5f + 0.35f * sin(time * 0.7f) + 0.15f * sin(time * 1.9f + 0.8f)
    glow(812f, 46f, 34f,  Color(0xFFE9FFF6), 0.30f + 0.30f * moon)
    glow(760f, 55f, 170f, Color(0xFFB8E6DC), 0.05f + 0.07f * moon)
    // Odlesky na hladině
    glow(790f, 254f, 16f, Color(0xFFDFFFF4), 0.20f + 0.35f * moon * (0.6f + 0.4f * sin(time * 3.1f)))
    glow(545f, 236f, 9f,  Color(0xFFBFE8E0), 0.10f + 0.16f * (0.5f + 0.5f * sin(time * 2.3f + 1.7f)))
    glow(522f, 292f, 9f,  Color(0xFFBFE8E0), 0.08f + 0.14f * (0.5f + 0.5f * sin(time * 1.9f + 4.0f)))

    // Mlha: široké světlé pásy táhnoucí nízko nad vodou zleva doprava
    for (i in 0 until 6) {
        val p    = (time * (0.012f + 0.004f * (i % 3)) + i * 0.1667f) % 1f
        val edge = (p / 0.15f).coerceAtMost(1f) * ((1f - p) / 0.15f).coerceAtMost(1f)
        val y    = 168f + (i * 29) % 70 + sin(time * 0.3f + i) * 5f
        glow(-150f + 1500f * p, y, 95f + 25f * (i % 2), Color(0xFFA9D6CF), 0.13f * edge)
    }

    // Bludičky: každá pulzuje vlastním rytmem a lehce se pohupuje
    SWAMP_WISPS.forEachIndexed { i, (x, y, r) ->
        val pulse = 0.5f + 0.5f * sin(time * (1.1f + 0.23f * i) + i * 1.9f)
        val bob   = sin(time * 0.9f + i * 2.1f) * 2.5f
        glow(x, y + bob, r * (2.2f + 0.8f * pulse), Color(0xFF39E6D0), 0.22f + 0.38f * pulse)
        glow(x, y + bob, r * 0.7f,                  Color(0xFFDFFFFA), 0.35f + 0.55f * pulse)
    }

    // Světlušky: bloudí po osmičkách nad trávou a vodou, rozsvěcí se a zhasínají
    for (i in 0 until 10) {
        val cx    = 140f + (i * 107) % 940
        val cy    = 205f + (i * 61) % 130
        val x     = cx + sin(time * (0.35f + 0.05f * (i % 4)) + i * 1.3f) * 38f
        val y     = cy + sin(time * (0.7f + 0.08f * (i % 3)) + i * 0.7f) * 14f
        val blink = sin(time * (1.3f + 0.17f * i) + i * 2.6f).coerceAtLeast(0f)
        glow(x, y, 6f,   Color(0xFF8CFFB0), 0.30f * blink)
        glow(x, y, 1.8f, Color(0xFFF2FFD8), 0.95f * blink)
    }
}

// ── Zima (castle_background_winter) ───────────────────────────────────────────
// Sněžení ve větru, sníh hnaný při zemi, zářící ledové krystaly, měsíc za mraky.

/**
 * Jedna vločka: výchozí místo, rychlost pádu a snosu větrem, kolébání, velikost a průhlednost.
 * [depth] 0 = daleko (drobná, pomalá, průsvitná) … 1 = blízko (velká, rychlá).
 */
private class Flake(rnd: Random) {
    private val depth = rnd.nextFloat().let { it * it }     // víc vzdálených než blízkých
    val x0       = rnd.nextFloat() * 1300f
    val y0       = rnd.nextFloat() * 440f
    val fall     = 22f + 50f * depth + rnd.nextFloat() * 14f
    val wind     = 14f + 40f * depth + rnd.nextFloat() * 18f
    val sway     = 3f + rnd.nextFloat() * 9f
    val swayFreq = 0.6f + rnd.nextFloat() * 1.6f
    val phase    = rnd.nextFloat() * 6.283f
    val size     = 0.7f + 1.7f * depth + rnd.nextFloat() * 0.4f
    val alpha    = 0.28f + 0.5f * depth + rnd.nextFloat() * 0.15f
}

/**
 * Vločky se losují jednou s pevným semínkem. Dřív se jejich místa a rychlosti počítaly
 * z pořadového čísla (i * 173 % 1300…) a vločky pak padaly ve viditelných šikmých řadách.
 */
private val WINTER_FLAKES = Random(20261009).let { rnd -> List(120) { Flake(rnd) } }

/** Ledové krystaly namalované v obrázku: x, y, velikost. */
private val WINTER_CRYSTALS = listOf(
    Triple(215f, 208f, 16f), Triple(140f, 230f, 8f),  Triple(380f, 172f, 12f), Triple(445f, 243f, 6f),
    Triple(822f, 182f, 12f), Triple(828f, 236f, 8f),  Triple(975f, 205f, 11f), Triple(1100f, 190f, 7f)
)

private fun AmbienceScope.winterScene() {
    // Měsíc za mraky: pomalu sílí a slábne, s ním i svit na mracích
    val moon = 0.5f + 0.35f * sin(time * 0.6f) + 0.15f * sin(time * 1.7f + 0.8f)
    glow(655f, 88f, 30f,  Color(0xFFFFFFFF), 0.25f + 0.30f * moon)
    glow(655f, 92f, 180f, Color(0xFFD8E6F5), 0.05f + 0.07f * moon)

    // Ledové krystaly: studená záře, každý vlastním rytmem, občas ostrý záblesk
    WINTER_CRYSTALS.forEachIndexed { i, (x, y, r) ->
        val pulse   = 0.5f + 0.5f * sin(time * (0.9f + 0.19f * i) + i * 2.3f)
        val sparkle = sin(time * (2.1f + 0.31f * i) + i * 1.7f).coerceAtLeast(0f).let { it * it * it * it }
        glow(x, y, r * (2.0f + 0.7f * pulse), Color(0xFF4FC3FF), 0.16f + 0.30f * pulse)
        glow(x, y - r * 0.3f, r * 0.5f,       Color(0xFFEAF8FF), 0.20f + 0.75f * sparkle)
    }

    // Sníh hnaný při zemi: světlé pásy ženoucí se nad plání zleva doprava
    for (i in 0 until 6) {
        val p    = (time * (0.05f + 0.015f * (i % 3)) + i * 0.1667f) % 1f
        val edge = (p / 0.15f).coerceAtMost(1f) * ((1f - p) / 0.15f).coerceAtMost(1f)
        val y    = 235f + (i * 37) % 110 + sin(time * 0.5f + i) * 6f
        glow(-150f + 1500f * p, y, 80f + 30f * (i % 2), Color(0xFFF2F7FF), 0.11f * edge)
    }

    // Sněžení: každá vločka má vlastní náhodnou dráhu (viz WINTER_FLAKES)
    for (f in WINTER_FLAKES) {
        val y = (f.y0 + time * f.fall) % 440f - 20f
        val x = (f.x0 + time * f.wind + sin(time * f.swayFreq + f.phase) * f.sway) % 1300f - 50f
        dot(x, y, f.size, Color(0xFFFFFFFF), f.alpha)
    }
}

// ── Sopka (castle_background_vulcan) ──────────────────────────────────────────
// Žhnoucí kráter a kouř nad ním, pulzující láva v puklinách, jiskry stoupající z lávy.

/** Místa, kde láva v obrázku září nejvíc: x, y, poloměr záře. */
private val VOLCANO_LAVA = listOf(
    Triple(320f, 255f, 34f),  Triple(388f, 262f, 22f),  Triple(245f, 283f, 22f),
    Triple(890f, 218f, 26f),  Triple(1015f, 292f, 30f), Triple(1080f, 345f, 46f),
    Triple(690f, 255f, 60f),  Triple(600f, 300f, 60f),  Triple(470f, 350f, 62f),
    Triple(300f, 330f, 55f),  Triple(770f, 345f, 55f),  Triple(640f, 205f, 40f)
)

/** Jiskra z lávy: kde vzniká, jak rychle a vysoko stoupá, jak se cestou kolébá. */
private class Ember(rnd: Random) {
    val x0       = 150f + rnd.nextFloat() * 950f
    val y0       = 215f + rnd.nextFloat() * 170f
    val rise     = 60f + rnd.nextFloat() * 130f
    val period   = 2.6f + rnd.nextFloat() * 4.5f
    val start    = rnd.nextFloat()
    val drift    = -18f + rnd.nextFloat() * 50f
    val sway     = 3f + rnd.nextFloat() * 9f
    val swayFreq = 1.0f + rnd.nextFloat() * 2.2f
    val phase    = rnd.nextFloat() * 6.283f
    val size     = 1.0f + rnd.nextFloat() * 1.6f
}
private val VOLCANO_EMBERS = Random(1883).let { rnd -> List(60) { Ember(rnd) } }

private fun AmbienceScope.volcanoScene() {
    // Záře za sopkou a žhnoucí kráter
    val heat = 0.5f + 0.3f * sin(time * 0.8f) + 0.2f * sin(time * 2.1f + 0.7f)
    glow(750f, 105f, 210f, Color(0xFFFF6A2A), 0.05f + 0.07f * heat)
    val crater = 0.5f + 0.25f * sin(time * 5.1f) + 0.15f * sin(time * 9.7f + 1.1f) + 0.10f * sin(time * 15.3f)
    glow(748f, 80f, 26f + 8f * crater, Color(0xFFFF7A2B), 0.35f + 0.40f * crater)
    glow(748f, 78f, 8f,                Color(0xFFFFE2A0), 0.40f + 0.50f * crater)

    // Kouř z kráteru: valí se vzhůru a vítr ho stáčí doleva jako v obrázku
    for (i in 0 until 6) {
        val p    = ((time / 9f) + i / 6f) % 1f
        val fade = (p / 0.15f).coerceAtMost(1f) * (1f - p)
        glow(746f - 120f * p * p - sin(p * 4f + i) * 6f, 74f - 62f * p, 16f + 46f * p, Color(0xFF2E2826), 0.55f * fade)
    }
    // Menší dým v dálce vlevo
    for (i in 0 until 4) {
        val p    = ((time / 7f) + i / 4f + 0.3f) % 1f
        val fade = (p / 0.15f).coerceAtMost(1f) * (1f - p)
        glow(522f - 40f * p * p, 132f - 44f * p, 9f + 22f * p, Color(0xFF332C29), 0.45f * fade)
    }

    // Láva: záře nad puklinami dýchá, každé místo vlastním rytmem
    VOLCANO_LAVA.forEachIndexed { i, (x, y, r) ->
        val pulse = 0.5f + 0.35f * sin(time * (0.8f + 0.13f * i) + i * 2.1f) + 0.15f * sin(time * (2.9f + 0.2f * i) + i)
        glow(x, y, r * (1.0f + 0.25f * pulse), Color(0xFFFF5A1F), 0.10f + 0.20f * pulse)
        glow(x, y, r * 0.35f,                  Color(0xFFFFC46B), 0.06f + 0.18f * pulse)
    }

    // Jiskry: vylétnou z lávy, stoupají, vítr je unáší a cestou vyhasínají
    for (e in VOLCANO_EMBERS) {
        val p    = (time / e.period + e.start) % 1f
        val fade = (p / 0.1f).coerceAtMost(1f) * (1f - p) * (1f - p)
        val x    = e.x0 + e.drift * p + sin(time * e.swayFreq + e.phase) * e.sway * p
        val y    = e.y0 - e.rise * p
        glow(x, y, e.size * 2.6f, Color(0xFFFF7A2B), 0.55f * fade)
        dot(x, y, e.size * 0.7f,  Color(0xFFFFE0A0), 0.95f * fade)
    }
}

// ── Temná citadela (castle_background_citadela) ───────────────────────────────
// Světlo za citadelou, fialová okna, ptáci kroužící kolem věží, mlha na obzoru, tráva, pyl.

/** Tráva v popředí vlevo a vpravo a pruh louky před citadelou. */
private val CITADEL_GRASS: List<List<Pair<Float, Float>>> = listOf(
    listOf(0f to 296f, 190f to 290f, 295f to 312f, 285f to 346f, 110f to 352f, 0f to 348f),
    listOf(735f to 300f, 900f to 296f, 1200f to 292f, 1200f to 348f, 1010f to 350f, 800f to 340f),
    listOf(120f to 212f, 480f to 206f, 760f to 210f, 1010f to 218f, 900f to 246f, 420f to 244f, 150f to 240f)
)

/** Okna citadely: x, y, velikost záře. */
private val CITADEL_WINDOWS = listOf(
    Triple(566f, 118f, 7f), Triple(590f, 126f, 8f), Triple(613f, 118f, 7f),
    Triple(578f, 150f, 6f), Triple(603f, 152f, 6f)
)

private val CITADEL_MOTES = Random(1313).let { rnd -> List(14) { Dust(rnd, 215f, 380f) } }

private fun AmbienceScope.citadelScene() {
    sway(CITADEL_GRASS, strength = 0.7f)

    // Světlo prodírající se mraky za citadelou: dva pomalu se přelévající zdroje
    val light = 0.5f + 0.3f * sin(time * 0.5f) + 0.2f * sin(time * 1.3f + 1.0f)
    glow(590f + 30f * sin(time * 0.23f), 62f, 210f, Color(0xFFE8F0FF), 0.06f + 0.08f * light)
    glow(600f - 40f * sin(time * 0.17f + 2f), 95f, 130f, Color(0xFFFFFFFF), 0.04f + 0.06f * (1f - light))

    // Fialová okna: neklidná magická záře
    CITADEL_WINDOWS.forEachIndexed { i, (x, y, r) ->
        val pulse = 0.5f + 0.3f * sin(time * (1.4f + 0.3f * i) + i * 2.2f) + 0.2f * sin(time * (4.3f + 0.5f * i) + i)
        glow(x, y, r * (1.8f + 0.9f * pulse), Color(0xFFA24BFF), 0.22f + 0.40f * pulse)
    }

    // Ptáci: tělo a dvě mávající křídla
    fun bird(x: Float, y: Float, size: Float, flap: Float) {
        val wing = sin(time * 9f + flap) * 1.1f * size
        dot(x, y, 1.5f * size, Color(0xFF0B0A10), 0.85f)
        dot(x - 2.3f * size, y - 0.6f * size + wing, 1.0f * size, Color(0xFF0B0A10), 0.85f)
        dot(x + 2.3f * size, y - 0.6f * size + wing, 1.0f * size, Color(0xFF0B0A10), 0.85f)
    }
    // …krouží kolem věží
    for (i in 0 until 6) {
        val a = time * (0.35f + 0.06f * i) + i * 1.05f
        bird(590f + cos(a) * (55f + 14f * i), 78f + sin(a) * (14f + 4f * i) + sin(a * 2.3f) * 3f, 1f, i.toFloat())
    }
    // Mlha na obzoru: pásy táhnoucí před hradbami citadely
    for (i in 0 until 6) {
        val p    = (time * (0.01f + 0.004f * (i % 3)) + i * 0.1667f) % 1f
        val edge = (p / 0.15f).coerceAtMost(1f) * ((1f - p) / 0.15f).coerceAtMost(1f)
        glow(-150f + 1500f * p, 180f + (i * 17) % 34 + sin(time * 0.3f + i) * 3f, 85f + 25f * (i % 2), Color(0xFFC9D2DE), 0.13f * edge)
    }

    // Pyl a prach nad loukou
    for (d in CITADEL_MOTES) {
        val p    = (time * d.speed * 0.6f + d.start) % 1f
        val edge = (p / 0.1f).coerceAtMost(1f) * ((1f - p) / 0.1f).coerceAtMost(1f)
        glow(-20f + 1240f * p, d.y + sin(time * d.swayFreq + d.phase) * d.sway, d.size * 0.8f, Color(0xFFDDE8C8), d.alpha * 0.8f * edge)
    }
}

// ── Pustina (castle_background, výchozí) ──────────────────────────────────────
// Slunce za mraky, doutnající ohniště s jiskrami, světlo pochodní na zemi, prach.
// Plameny pochodní (TorchFlame) se kreslí zvlášť v NewBattlefield a zůstávají navrchu.

/** Paty pochodní v obrázku (stejná místa jako plameny v NewBattlefield) – světlo na zemi pod nimi. */
private val WASTELAND_TORCHES =
    listOf(360f to 268f, 408f to 236f, 852f to 230f, 942f to 268f, 1000f to 228f, 1164f to 216f)

private val WASTELAND_DUST = Random(2718).let { rnd -> List(16) { Dust(rnd, 225f, 385f) } }

private fun AmbienceScope.wastelandScene() {
    // Slunce prodírající se mraky
    val sun = 0.5f + 0.3f * sin(time * 0.55f) + 0.2f * sin(time * 1.4f + 0.9f)
    glow(640f + 25f * sin(time * 0.2f), 95f, 200f, Color(0xFFFFE9B0), 0.06f + 0.08f * sun)
    glow(640f, 215f, 150f, Color(0xFFFFE2A0), 0.03f + 0.05f * sun)      // odraz na pláni

    // Světlo pochodní na zemi: mihotá se s plamenem
    WASTELAND_TORCHES.forEachIndexed { i, (x, y) ->
        val f = 0.5f + 0.25f * sin(time * (6.1f + 0.7f * i) + i) + 0.15f * sin(time * (11.3f + i) + 2f * i)
        glow(x, y, 34f, Color(0xFFFF8A2B), 0.07f + 0.10f * f)
    }

    // Doutnající ohniště vlevo a vpravo: žár a jiskry
    listOf(350f to 262f, 940f to 262f).forEachIndexed { n, (x, y) ->
        val f = 0.5f + 0.25f * sin(time * 6.3f + n * 2f) + 0.15f * sin(time * 13.1f + n) + 0.10f * sin(time * 19.7f)
        glow(x, y, 20f + 5f * f, Color(0xFFFF7A22), 0.25f + 0.30f * f)
        glow(x, y, 6f,           Color(0xFFFFD890), 0.25f + 0.35f * f)
        for (i in 0 until 5) {
            val p = ((time / 2.1f) + i * 0.2f + n * 0.37f) % 1f
            dot(x + sin(i * 2.4f + p * 5f + n) * (2f + 7f * p), y - 26f * p, 1.0f, Color(0xFFFFC56A), 0.9f * (1f - p))
        }
    }

    // Prach hnaný přes rozpukanou pláň
    for (d in WASTELAND_DUST) {
        val p    = (time * d.speed + d.start) % 1f
        val edge = (p / 0.1f).coerceAtMost(1f) * ((1f - p) / 0.1f).coerceAtMost(1f)
        glow(-20f + 1240f * p, d.y + sin(time * d.swayFreq + d.phase) * d.sway, d.size, Color(0xFFFFE8B8), d.alpha * 0.8f * edge)
    }
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
    "castle_player", "castle_player_2", "castle_player_3", "castle_player_5",
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
    "castle_baziny"     -> R.drawable.castle_baziny
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
    "wall_baziny"   -> R.drawable.wall_baziny
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
    glowCastle: Boolean = false,   // tutoriál: zlatý rám kolem hradu
    glowWall: Boolean = false,     // tutoriál: zlatý rám kolem hradeb
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
            Box(Modifier.tutorialGlow(glowCastle)) {
                CastleTowerBlock(castleHp, accentColor, accentLight, isPlayer = true,  winTarget = winTarget, castleResId = castleResId)
            }
            Box(Modifier.tutorialGlow(glowWall)) {
                WallBlock(wallHp, wallBlocks, accentColor, isPlayer = true, wallResId = wallResId, maxWall = maxWall)
            }
        } else {
            Box(Modifier.tutorialGlow(glowWall)) {
                WallBlock(wallHp, wallBlocks, accentColor, isPlayer = false, wallResId = wallResId, maxWall = maxWall)
            }
            Box(Modifier.tutorialGlow(glowCastle)) {
                CastleTowerBlock(castleHp, accentColor, accentLight, isPlayer = false, winTarget = winTarget, castleResId = castleResId)
            }
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
    @DrawableRes cardBackResId: Int = R.drawable.card_back_frame,
    modifier: Modifier = Modifier
) {
    // Barva strany jen na hradu a hradbách (tyrkysová = ty, červená = soupeř); počet karet
    // v ruce je neutrální jako ostatní čísla v liště.
    val accent = if (isPlayer) TealLight else Color(0xFFFF7070)

    // Herní destička jako u ostatních prvků lišty (dřív černý zaoblený rámeček)
    Box(
        modifier = modifier.size(width = 178.dp, height = 28.dp).buttonTexture(R.drawable.plain_button_longer),
        contentAlignment = Alignment.Center
    ) {
        // Každý údaj má pevně široké místo (podle nejdelší hodnoty: „100/105", „40/40", „8/8"),
        // takže se řádek neposouvá, když číslo přejde z dvouciferného na jednociferné.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Composable
            fun Stat(text: String, color: Color, width: Dp, icon: @Composable () -> Unit) {
                Row(
                    modifier              = Modifier.width(width),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    icon()
                    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
            Stat(if (winTarget >= 999) "$castleHp" else "$castleHp/$winTarget", accent, 63.dp) {
                Image(painterResource(R.drawable.castle_icon), contentDescription = null, modifier = Modifier.size(13.dp))
            }
            Stat("$wallHp/$maxWall", accent, 50.dp) {
                Image(painterResource(R.drawable.wall_icon), contentDescription = null, modifier = Modifier.size(13.dp))
            }
            Stat("$handSize/$maxHandSize", TextPrimary, 33.dp) {
                CardBackMini(cardBackResId, Modifier.size(width = 9.dp, height = 13.dp))
            }
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
            CampaignTitle(LocalStrings.current.gameLog, fontSize = 20.sp)
            HorizontalDivider(color = Gold.copy(alpha = 0.20f))
            LogPanel(log = log, modifier = Modifier.weight(1f).fillMaxWidth(), scrollable = true)
            if (lostCards.isNotEmpty() && onShowLostCards != null) {
                PlainButton(
                    text      = LocalStrings.current.lostCardsButton.format(lostCards.size),
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
                text      = LocalStrings.current.close,
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
        CardAction.BURNED    -> null   // burn_icon se vykreslí samostatně níže
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
        if (action == CardAction.DISCARDED || action == CardAction.BURNED) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(
                        if (action == CardAction.BURNED) R.drawable.burn_icon
                        else                             R.drawable.cross_icon
                    ),
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
                text      = LocalStrings.current.roundN.format(currentTurn),
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

