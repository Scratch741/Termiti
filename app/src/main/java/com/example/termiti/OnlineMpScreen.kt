package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Barvy ───────────────────────────────────────────────────────────────────
private val OnRed    = Color(0xFFCF4A4A)
private val OnGreen  = Color(0xFF4CAF50)
private val OnBronze = Color(0xFF8A6A3A)

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun OnlineMpScreen(
    vm: OnlineLobbyViewModel,
    decks: List<Deck> = emptyList(),
    allCards: List<Card> = emptyList(),
    activeDeckIndex: Int = -1,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onLeaderboard: () -> Unit = {}
) {
    val phase by vm.phase
    // Žebříček se kreslí uvnitř lobby – spojení se serverem při jeho prohlížení zůstává.
    var showLeaderboard by rememberSaveable { mutableStateOf(false) }

    // Odpojit WS vždy, když OnlineMpScreen opustí composici (navigace do menu,
    // sleep/wake s jiným screenem atd.). Bez toho by ViewModel
    // mohl auto-reconnectnout na server i když uživatel vidí hlavní menu.
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }

    // Auto-connect: použij nick z profilu, přeskoč NAME_INPUT obrazovku
    LaunchedEffect(Unit) {
        if (phase == OnlinePhase.NAME_INPUT) {
            val profileName = PlayerProfileManager.profile?.name
            if (!profileName.isNullOrBlank()) {
                vm.setName(profileName)
                vm.connect()
            }
        }
    }

    // V lobby je předvybraný aktivní balíček (je-li úplný) – bez toho by rychlý zápas
    // bez ručního výběru hrál s náhodným balíčkem. returnToLobby() výběr nuluje, proto podle fáze.
    LaunchedEffect(phase) {
        if (phase == OnlinePhase.LOBBY && vm.selectedDeckIndex.value < 0) {
            val idx = if (decks.getOrNull(activeDeckIndex)?.isValid == true) activeDeckIndex
                      else decks.indexOfFirst { it.isValid }
            if (idx >= 0) vm.setDeckChoice(idx, decks[idx].cardIdList())
        }
    }

    // Herní fáze: plná obrazovka bez lobby
    if (phase == OnlinePhase.GAME_MULLIGAN ||
        phase == OnlinePhase.GAME_PLAYING  ||
        phase == OnlinePhase.GAME_OVER) {
        OnlineGameScreen(vm = vm, onBack = { vm.disconnect(); onBack() })
        return
    }

    if (showLeaderboard && phase == OnlinePhase.LOBBY) {
        LeaderboardScreen(onBack = { showLeaderboard = false })
        return
    }

    val leave = { vm.disconnect(); onBack() }
    when (phase) {
        OnlinePhase.NAME_INPUT  ->
            if (!PlayerProfileManager.profile?.name.isNullOrBlank()) ConnectingPanel(onCancel = leave)
            else NameInputPanel(vm, onBack)
        OnlinePhase.CONNECTING  -> ConnectingPanel(onCancel = leave)
        OnlinePhase.LOBBY       -> LobbyPanel(vm, decks, allCards, onBack = leave, onLeaderboard = { showLeaderboard = true })
        OnlinePhase.QUEUING     -> QueuingPanel(vm)
        OnlinePhase.MATCH_FOUND -> MatchFoundPanel(vm)
        OnlinePhase.ERROR       -> ErrorPanel(vm, onBack)
        else                    -> {}
    }
}

private fun Deck.cardIdList(): List<String> = cardCounts.flatMap { (id, count) -> List(count) { id } }

// ─── Společný rám menu ────────────────────────────────────────────────────────
//
// Stejné rozložení jako hlavní menu (MenuScreen): pozadí s pochodněmi, vlevo kamenná deska
// s hráčem, uprostřed záhlaví + tělo, vpravo deska s kulatými ikonami. Záhlaví zabírá přesně
// místo loga DARKMAGE a tělo místo čtyř tlačítek, takže při přechodu z menu nic neposkočí.

/** Výška jednoho tlačítka menu (button_N.png, 662×107) při šířce 90 % středu. */
internal fun menuButtonHeight(centerW: Dp): Dp = centerW * 0.9f * 107f / 662f
/** Mezera mezi tlačítky menu. */
internal fun menuGap(H: Dp): Dp = H * 0.010f

@Composable
internal fun MenuFrame(
    left   : @Composable ColumnScope.(H: Dp) -> Unit,
    right  : @Composable ColumnScope.(H: Dp, iconSize: Dp) -> Unit,
    /** true = střed vyplní celou výšku i šířku tmavého pole (žebříček), jinak záhlaví + tělo. */
    centerFill: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    center : @Composable ColumnScope.(centerW: Dp, H: Dp) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // Pochodně (stejná matematika jako MenuScreen)
        val imgAR  = 1791f / 975f
        val dispAR = W.value / H.value.coerceAtLeast(1f)
        val imgDispW: Dp
        val imgDispH: Dp
        val cropX: Dp
        val cropY: Dp
        if (dispAR >= imgAR) {
            imgDispW = W; imgDispH = W / imgAR; cropX = 0.dp; cropY = (imgDispH - H) / 2f
        } else {
            imgDispW = H * imgAR; imgDispH = H; cropX = (imgDispW - W) / 2f; cropY = 0.dp
        }
        val torchSize = H * 0.15f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 1.7f
        )

        val centerW  = minOf(W * 0.46f, H * 1.0f)
        val iconSize = H * 0.12f

        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = H * 0.02f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Posuny sloupců odpovídají MenuScreen (desky v pozadí nejsou přesně v ose sloupců).
            Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    modifier            = Modifier.offset(x = (-5).dp, y = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.025f)
                ) { left(H) }
            }
            Box(
                Modifier.fillMaxHeight().width(centerW).offset(x = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                if (centerFill) {
                    Column(
                        modifier            = Modifier
                            .requiredWidth(centerW * 1.16f)
                            .fillMaxHeight()
                            .padding(top = H * 0.045f, bottom = H * 0.055f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { center(centerW, H) }
                } else {
                    Column(
                        modifier            = Modifier.width(centerW),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(menuGap(H))
                    ) { center(centerW, H) }
                }
            }
            Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    modifier            = Modifier.offset(x = 25.dp, y = 35.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.005f)
                ) { right(H, iconSize) }
            }
        }
        overlay()
    }
}

/** Záhlaví na místě loga: nadpis, zdobená linka a volitelný řádek pod ní. */
@Composable
internal fun MenuHeader(
    centerW  : Dp,
    H        : Dp,
    title    : String,
    titleSize: TextUnit = (centerW.value * 0.095f).sp,
    gradient : List<Color> = TitleGold,
    sub      : @Composable () -> Unit = {}
) {
    Box(
        Modifier
            .requiredWidth(centerW * 1.1f)
            .aspectRatio(1400f / 377f)
            .offset(y = H * 0.01f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(H * 0.012f)
        ) {
            CampaignTitle(title, fontSize = titleSize, gradient = gradient)
            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.width(centerW * 0.82f),
                contentScale       = ContentScale.FillWidth
            )
            sub()
        }
    }
    Spacer(Modifier.height(H * 0.01f))
}

/** Tělo na místě čtyř tlačítek menu – drží výšku, aby záhlaví stálo na všech obrazovkách stejně. */
@Composable
internal fun MenuBody(centerW: Dp, H: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .height(menuButtonHeight(centerW) * 4 + menuGap(H) * 3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(menuGap(H)),
        content             = content
    )
}

/** Malý řádek pod linkou záhlaví. */
@Composable
internal fun HeaderNote(text: String, H: Dp, color: Color = TextMuted) {
    Text(
        text,
        color = color, fontSize = (H.value * 0.030f).sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, textAlign = TextAlign.Center, maxLines = 2
    )
}

/** Medailon s ikonou vyříznutý z tlačítka menu (stejný výřez jako IconMenuButton), bez popisku. */
@Composable
internal fun Medallion(@DrawableRes imageRes: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape)) {
        Image(
            painter            = painterResource(imageRes),
            contentDescription = null,
            modifier           = Modifier
                .requiredHeight(size)
                .requiredWidth(size * 6.19f)
                .offset(x = size * 2.4f),
            contentScale       = ContentScale.FillBounds
        )
    }
}

/** Medailon, kolem kterého obíhá zlatý oblouk – „pracuji" (připojování, hledání soupeře). */
@Composable
private fun BusyMedallion(@DrawableRes imageRes: Int, size: Dp) {
    val angle by rememberInfiniteTransition(label = "busy").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "angle"
    )
    Box(Modifier.size(size * 1.16f), contentAlignment = Alignment.Center) {
        Medallion(imageRes, size)
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.022f
            val inset  = stroke
            val arc    = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl     = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(OnBronze.copy(alpha = 0.35f), 0f, 360f, false, tl, arc, style = Stroke(stroke * 0.5f))
            rotate(angle) {
                drawArc(
                    brush      = Brush.sweepGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.15f), Color(0xFFFFE9A8))),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft    = tl, size = arc,
                    style      = Stroke(stroke, cap = StrokeCap.Butt)
                )
            }
        }
    }
}

/** Avatar v rámečku jako v hlavním menu. */
@Composable
internal fun AvatarPlate(avatar: String, size: Dp, accent: Color = Gold) {
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center
    ) {
        AvatarDisplay(avatar, sizeDp = size.value * 0.89f)
    }
}

/** Avatar + jméno hráče na kamenné desce. */
@Composable
internal fun PlateIdentity(avatar: String, name: String, H: Dp, accent: Color = Gold, nameColor: Color = TextPrimary) {
    // velikost avataru a mezera pod ním jako ProfileInfo v hlavním menu
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(H * 0.025f)
    ) {
        AvatarPlate(avatar, H * 0.162f, accent)
        Text(
            name,
            color = nameColor, fontSize = (H.value * 0.035f).sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = H * 0.25f)
        )
    }
}

/** Řádkování těsně podle písma (výchozí je o dost vyšší a bloky hodnocení by přetekly desku). */
private fun tight(fs: TextUnit) = androidx.compose.ui.text.TextStyle(
    lineHeight    = fs * 1.15f,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
)

/**
 * Hodnocení v jednom módu: popisek, rating s hvězdou a bilance.
 * @param showWinRate úspěšnost v procentech za bilancí (v lobby se nezobrazuje, v žebříčku ano).
 */
@Composable
internal fun RatingBlock(label: String, stats: OnlineModeStats?, H: Dp, showWinRate: Boolean = false) {
    val s = stats ?: OnlineModeStats()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(H * 0.010f)
    ) {
        Text(
            label.uppercase(),
            color = TextMuted, fontSize = (H.value * 0.021f).sp, style = tight((H.value * 0.021f).sp), fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp, maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.010f)) {
            Image(painterResource(R.drawable.star_icon), contentDescription = null, modifier = Modifier.size(H * 0.034f))
            Text("${s.rating}", color = Gold, fontSize = (H.value * 0.036f).sp, style = tight((H.value * 0.036f).sp), fontWeight = FontWeight.Bold)
        }
        val wrColor = when {
            s.games == 0    -> TextMuted
            s.winRate >= 60 -> OnGreen
            s.winRate >= 45 -> Gold
            else            -> OnRed
        }
        Row(horizontalArrangement = Arrangement.spacedBy(H * 0.012f)) {
            Text(
                "${s.wins}–${s.losses}–${s.draws}",
                color = TextPrimary.copy(alpha = 0.85f), fontSize = (H.value * 0.025f).sp, style = tight((H.value * 0.025f).sp), fontWeight = FontWeight.Bold
            )
            if (showWinRate && s.games > 0) {
                Text("${s.winRate} %", color = wrColor, fontSize = (H.value * 0.025f).sp, style = tight((H.value * 0.025f).sp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Obsah kamenné desky s hodnocením. Sloupec je ukotvený shora, ne na střed: avatar tak stojí
 * na všech obrazovkách multiplayeru přesně tam, kde v hlavním menu (horní hrana 32,6 % výšky),
 * ať je pod ním obsahu jakkoli mnoho – při překlikávání nic neposkakuje.
 *
 * @param columnShift svislý posun rodičovského sloupce v [MenuFrame] (levý 30 dp, pravý 35 dp).
 */
@Composable
internal fun PlateColumn(H: Dp, columnShift: Dp = 30.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier            = Modifier.fillMaxHeight().padding(top = H * 0.303f - columnShift),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(H * 0.020f),
        content             = content
    )
}

@Composable
internal fun PlateSeparator(H: Dp) {
    Image(
        painter            = painterResource(R.drawable.bg_separator),
        contentDescription = null,
        modifier           = Modifier.width(H * 0.20f),
        contentScale       = ContentScale.FillWidth
    )
}

/** Levá deska lobby: hráč a jeho hodnocení v obou módech. */
@Composable
private fun ColumnScope.LobbyProfile(vm: OnlineLobbyViewModel, H: Dp) {
    val name         by vm.playerName
    val allModeStats by vm.allModeStats
    PlateColumn(H) {
        PlateIdentity(PlayerProfileManager.profile?.avatar ?: "player_icon_1", name, H)
        PlateSeparator(H)
        RatingBlock("Constructed", allModeStats["normal"], H)
        RatingBlock(LocalStrings.current.mpModeSuperRandom, allModeStats["super_random"], H)
    }
}

/** Číslo s popiskem ve stejném půdorysu jako [IconMenuButton] – drží pozice ikon pod ním. */
@Composable
private fun PlateCounter(value: Int, label: String, color: Color, size: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.03f)
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text("$value", color = color, fontSize = (size.value * 0.50f).sp, fontWeight = FontWeight.Bold)
        }
        Text(
            label.uppercase(),
            color = TextMuted, fontSize = (size.value * 0.20f).sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )
    }
}

/** Pravá deska: kolik hráčů je online a ve frontě + tlačítko zpět na obvyklém místě. */
@Composable
private fun ColumnScope.LobbyStatus(vm: OnlineLobbyViewModel, iconSize: Dp, backLabel: String, onBack: () -> Unit) {
    val onlineCount by vm.onlineCount
    val queueSize   by vm.queueSize
    PlateCounter(onlineCount, LocalStrings.current.mpOnline, OnGreen, iconSize)
    PlateCounter(queueSize,   LocalStrings.current.mpQueue,  Gold,    iconSize)
    IconMenuButton(imageRes = R.drawable.button_6, label = backLabel, size = iconSize, onClick = onBack)
}

/** Pravá deska bez počítadel – jen tlačítko zpět na obvyklém místě. */
@Composable
internal fun ColumnScope.BackOnly(iconSize: Dp, label: String, onBack: () -> Unit) {
    repeat(2) {
        Box(Modifier.alpha(0f)) { PlateCounter(0, " ", Color.Transparent, iconSize) }
    }
    IconMenuButton(imageRes = R.drawable.button_6, label = label, size = iconSize, onClick = onBack)
}

private val backLabel: String
    @Composable get() = LocalStrings.current.mpBackPlain
private val cancelLabel: String
    @Composable get() = LocalStrings.current.mpCancel.removePrefix("← ")

// ─── Lobby ────────────────────────────────────────────────────────────────────
@Composable
private fun LobbyPanel(
    vm: OnlineLobbyViewModel,
    decks: List<Deck>,
    allCards: List<Card>,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit
) {
    val s               = LocalStrings.current
    val selectedDeckIdx by vm.selectedDeckIndex
    val errorMsg        by vm.errorMsg
    var pickingDeck     by remember { mutableStateOf(false) }
    var rules           by remember { mutableStateOf<Pair<String, String>?>(null) }

    val deckName = decks.getOrNull(selectedDeckIdx)?.let { deckTitle(localizedDeckName(it.name)) } ?: s.mpDeckRandom

    MenuFrame(
        left    = { H -> LobbyProfile(vm, H) },
        right   = { _, iconSize -> LobbyStatus(vm, iconSize, backLabel, onBack) },
        overlay = {
            if (pickingDeck) {
                OnlineDeckPicker(
                    decks       = decks,
                    allCards    = allCards,
                    selectedIdx = selectedDeckIdx,
                    onPick      = { idx -> vm.setDeckChoice(idx, decks[idx].cardIdList()); pickingDeck = false },
                    onDismiss   = { pickingDeck = false }
                )
            }
            rules?.let { (mode, text) -> RulesOverlay(s.rulesTitle.format(mode), text, s.close) { rules = null } }
        }
    ) { centerW, H ->
        MenuHeader(centerW, H, "Online lobby") {
            if (errorMsg.isNotBlank()) HeaderNote(errorMsg, H, OnRed)
        }
        MenuBody(centerW, H) {
            // Názvy módů i otazníky s pravidly stejně jako v nabídce Hrát
            val helpSize = H * 0.075f
            ModeRow(helpSize, onHelp = { rules = s.ownDeck to s.rulesOnlineConstructed }) {
                MenuButton(s.ownDeck,     imageRes = R.drawable.button_14, accent = TealLight, onClick = { vm.joinQueue(superRandom = false) })
            }
            ModeRow(helpSize, onHelp = { rules = s.superRandom to s.rulesOnlineSuperRandom }) {
                MenuButton(s.superRandom, imageRes = R.drawable.button_10, accent = TealLight, onClick = { vm.joinQueue(superRandom = true) })
            }
            MenuButton(
                "${s.mpDeckLabel} · ${deckName.uppercase()}",
                imageRes = R.drawable.button_7, accent = Gold,
                enabled  = decks.any { it.isValid },
                onClick  = { pickingDeck = true }
            )
            MenuButton(s.mpLeaderboard, imageRes = R.drawable.button_11, accent = Gold, onClick = onLeaderboard)
        }
    }
}

// ─── Výběr balíčku pro rychlý zápas ───────────────────────────────────────────
@Composable
private fun OnlineDeckPicker(
    decks: List<Deck>,
    allCards: List<Card>,
    selectedIdx: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CampaignTitle(s.dbPickDeck, fontSize = 24.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                decks.forEachIndexed { i, deck ->
                    val iconRes = remember(deck.cardCounts) {
                        deck.dominantResource(allCards)?.let { resourceIconRes(it) } ?: R.drawable.card_icon
                    }
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .size(width = 184.dp, height = 124.dp)
                            .alpha(if (deck.isValid) 1f else 0.45f)
                            .then(if (i == selectedIdx) Modifier.border(2.dp, Gold, shape) else Modifier)
                            .clip(shape)
                            .then(
                                if (deck.isValid) Modifier.clickable { SoundManager.playMenuTap(); onPick(i) }
                                // neúplný balíček klik spolkne, aby se překryv nezavřel
                                else Modifier.clickable(remember { MutableInteractionSource() }, null) {}
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter            = painterResource(R.drawable.mulligan_background),
                            contentDescription = null,
                            modifier           = Modifier.matchParentSize(),
                            contentScale       = ContentScale.FillBounds
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp), contentScale = ContentScale.Fit)
                            Text(
                                deckTitle(localizedDeckName(deck.name)),
                                color = TextPrimary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 130.dp)
                            )
                            Text(
                                "${deck.totalCards} / 30",
                                color = if (deck.isValid) HpGreen else OnRed,
                                fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            PlainButton(
                text      = s.back2,
                modifier  = Modifier.width(110.dp).height(32.dp),
                textColor = TextMuted,
                fontSize  = 10.sp,
                paddingH  = 6.dp,
                paddingV  = 0.dp,
                onClick   = onDismiss
            )
        }
    }
}

// ─── Zadání přezdívky ─────────────────────────────────────────────────────────
@Composable
private fun NameInputPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val s     = LocalStrings.current
    val name  by vm.playerName
    val error by vm.errorMsg

    MenuFrame(
        left  = { H -> PlayerProfileManager.profile?.let { ProfileInfo(it, H) } },
        right = { _, iconSize -> BackOnly(iconSize, backLabel, onBack) }
    ) { centerW, H ->
        MenuHeader(centerW, H, "Multiplayer") {
            HeaderNote(if (error.isNotBlank()) error else s.mpConnectHint, H, if (error.isNotBlank()) OnRed else TextMuted)
        }
        MenuBody(centerW, H) {
            OutlinedTextField(
                value         = name,
                onValueChange = { vm.setName(it) },
                label         = { Text(s.mpNickname, color = TextMuted, fontSize = 11.sp) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(0.9f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.connect() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Gold,
                    unfocusedBorderColor = OnBronze.copy(alpha = 0.6f),
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = Gold
                )
            )
            MenuButton(s.mpConnect, imageRes = R.drawable.button_8, accent = TealLight, enabled = name.isNotBlank(), onClick = { vm.connect() })
        }
    }
}

// ─── Připojování ─────────────────────────────────────────────────────────────
@Composable
private fun ConnectingPanel(onCancel: () -> Unit) {
    MenuFrame(
        left  = { H -> PlayerProfileManager.profile?.let { ProfileInfo(it, H) } },
        right = { _, iconSize -> BackOnly(iconSize, cancelLabel, onCancel) }
    ) { centerW, H ->
        MenuHeader(centerW, H, "Multiplayer") {
            HeaderNote(LocalStrings.current.mpConnecting, H)
        }
        MenuBody(centerW, H) {
            Spacer(Modifier.weight(1f))
            BusyMedallion(R.drawable.button_8, H * 0.26f)
            Spacer(Modifier.weight(1.6f))
        }
    }
}

// ─── Ve frontě ───────────────────────────────────────────────────────────────
@Composable
private fun QueuingPanel(vm: OnlineLobbyViewModel) {
    val s             = LocalStrings.current
    val queueSize     by vm.queueSize
    val isSuperRandom by vm.isSuperRandom
    val modeLabel     = if (isSuperRandom) s.superRandom else s.ownDeck

    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            elapsedSec++
        }
    }
    val queueTime = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    MenuFrame(
        left  = { H -> LobbyProfile(vm, H) },
        right = { _, iconSize -> LobbyStatus(vm, iconSize, cancelLabel) { vm.leaveQueue() } }
    ) { centerW, H ->
        MenuHeader(centerW, H, s.mpSearching.trimEnd('…', '.'), titleSize = (centerW.value * 0.078f).sp) {
            HeaderNote(modeLabel.uppercase(), H, Gold.copy(alpha = 0.85f))
        }
        MenuBody(centerW, H) {
            Spacer(Modifier.weight(1f))
            BusyMedallion(if (isSuperRandom) R.drawable.button_10 else R.drawable.button_14, H * 0.26f)
            Text(
                queueTime,
                color = TextPrimary, fontSize = (H.value * 0.058f).sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            )
            Text(
                if (queueSize > 1) s.mpInQueue.format(queueSize) else " ",
                color = TextMuted, fontSize = (H.value * 0.030f).sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

// ─── Zápas nalezen ───────────────────────────────────────────────────────────
@Composable
private fun MatchFoundPanel(vm: OnlineLobbyViewModel) {
    val s              = LocalStrings.current
    val match          by vm.matchInfo
    val myRating       by vm.myRating
    val opponentRating by vm.opponentRating
    val playerName     by vm.playerName
    val modeLabel      = if (match?.mode == "super_random") s.mpModeSuperRandom else "Constructed"

    // tečky za „Připravuji hru" přibývají, ať je vidět, že se něco děje
    val dots by produceState(0) {
        while (true) { kotlinx.coroutines.delay(400L); value = (value + 1) % 4 }
    }

    MenuFrame(
        left  = { H ->
            PlateColumn(H) {
                PlateIdentity(PlayerProfileManager.profile?.avatar ?: "player_icon_1", playerName, H)
                PlateSeparator(H)
                RatingBlock(modeLabel, match?.myStats ?: myRating?.let { OnlineModeStats(rating = it) }, H)
            }
        },
        // pravá deska = soupeř; svislý posun srovnaný s levou deskou (sloupec ikon stojí o 5 dp níž)
        right = { H, _ ->
            PlateColumn(H, columnShift = 35.dp) {
                PlateIdentity(
                    match?.opponentAvatar ?: "enemy_icon_1",
                    match?.opponentName ?: s.opponentDefault, H,
                    accent = OnRed
                )
                PlateSeparator(H)
                RatingBlock(modeLabel, match?.opponentStats ?: opponentRating?.let { OnlineModeStats(rating = it) }, H)
            }
        }
    ) { centerW, H ->
        MenuHeader(centerW, H, s.mpOpponentFound.trimEnd('!'), titleSize = (centerW.value * 0.078f).sp) {
            HeaderNote(
                if (match?.side == "A") s.mulliganYouFirst else s.mulliganOpponentFirst, H,
                if (match?.side == "A") Gold.copy(alpha = 0.85f) else TextMuted
            )
        }
        MenuBody(centerW, H) {
            Spacer(Modifier.weight(1f))
            CampaignTitle("VS", fontSize = (centerW.value * 0.22f).sp)
            Text(
                s.mpPreparing.trimEnd('…', '.') + ".".repeat(dots) + " ".repeat(3 - dots),
                color = TextMuted, fontSize = (H.value * 0.030f).sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1.3f))
        }
    }
}

// ─── Chyba ───────────────────────────────────────────────────────────────────
@Composable
private fun ErrorPanel(vm: OnlineLobbyViewModel, onBack: () -> Unit) {
    val s     = LocalStrings.current
    val error by vm.errorMsg
    val leave = { vm.disconnect(); onBack() }

    MenuFrame(
        left  = { H -> PlayerProfileManager.profile?.let { ProfileInfo(it, H) } },
        right = { _, iconSize -> BackOnly(iconSize, backLabel, leave) }
    ) { centerW, H ->
        MenuHeader(centerW, H, s.mpConnectionError, titleSize = (centerW.value * 0.078f).sp, gradient = TitleBlood)
        MenuBody(centerW, H) {
            Box(
                Modifier.fillMaxWidth(0.9f).height(menuButtonHeight(centerW) * 2 + menuGap(H)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    error,
                    color = TextPrimary.copy(alpha = 0.8f), fontSize = (H.value * 0.034f).sp,
                    textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis
                )
            }
            MenuButton(s.mpRetry, imageRes = R.drawable.button_8, accent = TealLight, onClick = { vm.retryConnect() })
        }
    }
}
