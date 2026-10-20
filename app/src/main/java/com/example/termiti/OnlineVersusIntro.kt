package com.example.termiti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// ============================================================
// OnlineVersusIntro.kt
// Úvod online zápasu před mulliganem: hráč proti soupeři.
//
// Scéna: bojiště, na kterém se bude hrát (pozadí vybrané serverem), ztmavené.
// Dvě herní desky (bg_side_panels) přilétnou z boků – avatar v prstenu barvy
// strany, jméno písmem nadpisů, úroveň, hodnocení, skóre v módu a pasivní
// schopnosti. Uprostřed dopadne „VS" s otřesem a zábleskem. Dole kdo začíná
// a ukazatel času; klepnutím se úvod přeskočí.
// ============================================================

private val VsGold    = Color(0xFFD4A843)
private val VsText    = Color(0xFFEDE0C4)
private val VsMuted   = Color(0xFF9A8C78)
private val VsTeal    = Color(0xFF3DBFAD)
private val VsCrimson = Color(0xFFD9443A)
private val VsWin     = Color(0xFF6FCF73)

/** Přechod jména mé strany – tyrkysový protějšek TitleGold / TitleBlood. */
private val TitleTeal = listOf(Color(0xFFDFFFF9), Color(0xFF8FE6D7), Color(0xFF34A897), Color(0xFF1C5F56))

/** Celková délka úvodu bez odchodu (příchod + držení). */
private const val VS_HOLD_MS = 4300L

/** Jedna strana zápasu tak, jak ji úvod ukazuje. */
private class VsSide(
    val name: String,
    val avatar: String,
    val level: Int,
    val rating: Int?,
    val stats: OnlineModeStats?,
    val abilities: List<PassiveAbility>,
    val isMe: Boolean
)

@Composable
internal fun OnlineVersusIntro(vm: OnlineLobbyViewModel, onDone: () -> Unit) {
    val s            = LocalStrings.current
    val match        by vm.matchInfo
    val myRating     by vm.myRating
    val oppRating    by vm.opponentRating
    val playerName   by vm.playerName
    val allModeStats by vm.allModeStats
    val background   by vm.battleBackgroundResId
    val profile      = PlayerProfileManager.profile

    val mode      = match?.mode ?: "normal"
    val modeLabel = if (mode == "super_random") s.mpSuperRandom else s.mpQuickMatch
    val iStart    = match?.side == "A"

    val me = VsSide(
        name      = playerName.ifBlank { profile?.name ?: s.lbPlayer },
        avatar    = profile?.avatar ?: "player_icon_1",
        level     = profile?.level ?: -1,
        rating    = myRating,
        // Skóre v daném módu: preferuj data z MATCH_FOUND, záloha statistiky z WELCOME
        stats     = match?.myStats ?: allModeStats[mode],
        abilities = profile?.activeAbilities?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList(),
        isMe      = true
    )
    val opp = VsSide(
        name      = match?.opponentName ?: s.opponentDefault,
        avatar    = match?.opponentAvatar ?: "enemy_icon_1",
        level     = match?.opponentLevel ?: -1,
        rating    = oppRating,
        stats     = match?.opponentStats,
        abilities = match?.opponentAbilities?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList(),
        isMe      = false
    )

    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }   // příchod desek (pružina)
    val slam  = remember { Animatable(0f) }   // dopad „VS"
    val shake = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val timer = remember { Animatable(0f) }   // ukazatel do konce úvodu
    val exit  = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }

    fun leave() {
        if (leaving) return
        leaving = true
        scope.launch {
            exit.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        launch { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)) }
        launch { timer.animateTo(1f, tween(VS_HOLD_MS.toInt(), easing = LinearEasing)) }
        delay(330)
        slam.animateTo(1f, tween(230, easing = FastOutLinearInEasing))
        SoundManager.playPackBurst()
        launch { flash.snapTo(0.75f); flash.animateTo(0f, tween(520)) }
        launch { shake.snapTo(1f); shake.animateTo(0f, tween(420)) }
        delay(VS_HOLD_MS - 560)
        leave()
    }

    val inf = rememberInfiniteTransition(label = "vs")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .pointerInput(Unit) { detectTapGestures { leave() } }   // klepnutí úvod přeskočí (a blokuje hru pod ním)
    ) {
        val W = maxWidth
        val H = maxHeight

        // ── Bojiště, na kterém se bude hrát, ztmavené do pozadí ───────────────
        Image(
            painter            = painterResource(background),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize().graphicsLayer { val z = 1.06f - 0.06f * enter.value; scaleX = z; scaleY = z },
            contentScale       = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Color(0xCC07050A))
                drawRect(Brush.radialGradient(
                    0f to Color.Transparent, 0.55f to Color(0x55000000), 1f to Color(0xE6000000),
                    center = Offset(size.width / 2, size.height / 2), radius = size.maxDimension * 0.62f
                ))
            }
        )

        val panelW = minOf(W * 0.33f, 250.dp)
        // Výška: mezi nadpisem módu (~40 dp) a spodním pruhem (~45 dp), ať se nepřekrývají
        val panelH = minOf(H * 0.70f, 290.dp)

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val a = shake.value
                translationX = sin(a * 44f) * a * 10.dp.toPx()
                translationY = cos(a * 37f) * a * 6.dp.toPx()
            }
        ) {
            // ── Mód nahoře ──────────────────────────────────────────────────
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = H * 0.035f)
                    .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * -20.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                CampaignTitle(modeLabel, fontSize = 17.sp)
                Image(
                    painter            = painterResource(R.drawable.bg_separator),
                    contentDescription = null,
                    modifier           = Modifier.width(240.dp),
                    contentScale       = ContentScale.FillWidth
                )
            }

            // ── Desky hráčů a VS ────────────────────────────────────────────
            Row(
                // o kousek výš: dole je spodní pruh vyšší než nadpis módu nahoře
                Modifier.align(Alignment.Center).offset(y = (-6).dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(W * 0.035f)
            ) {
                VsPanel(me, panelW, panelH, Modifier.graphicsLayer {
                    val e = enter.value
                    translationX = -(1f - e) * W.toPx() * 0.6f - exit.value * 120.dp.toPx()
                    alpha = e.coerceIn(0f, 1f)
                })
                VsEmblem(slam = slam.value, pulse = pulse)
                VsPanel(opp, panelW, panelH, Modifier.graphicsLayer {
                    val e = enter.value
                    translationX = (1f - e) * W.toPx() * 0.6f + exit.value * 120.dp.toPx()
                    alpha = e.coerceIn(0f, 1f)
                })
            }

            // ── Kdo začíná + ukazatel času ─────────────────────────────────
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    .graphicsLayer { alpha = slam.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier.height(28.dp).widthIn(min = 230.dp).buttonTexture(R.drawable.plain_button_longer)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (iStart) s.vsYouStart else s.vsOpponentStarts,
                        color = if (iStart) VsTeal else VsText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                // tenký zlatý ukazatel, kolik zbývá do mulliganu, a vedle nápověda
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(150.dp).height(3.dp).drawBehind {
                            drawRect(Color(0x33D4A843))
                            drawRect(VsGold, size = size.copy(width = size.width * (1f - timer.value)))
                        }
                    )
                    Text(s.vsTapToSkip, color = VsMuted.copy(alpha = 0.8f), fontSize = 9.sp)
                }
            }
        }

        // Záblesk při dopadu VS
        Canvas(Modifier.fillMaxSize()) {
            val a = flash.value
            if (a > 0.01f) drawRect(Brush.radialGradient(
                0f to Color.White.copy(alpha = a), 0.3f to VsGold.copy(alpha = a * 0.6f), 1f to Color.Transparent,
                center = Offset(size.width / 2, size.height / 2), radius = size.minDimension * 0.55f
            ))
        }
    }
}

/** „VS" uprostřed: dopadne z velkého měřítka, za ním dýchá zlatá záře a svislé světelné čáry. */
@Composable
private fun VsEmblem(slam: Float, pulse: Float) {
    Box(Modifier.size(width = 86.dp, height = 220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val a = slam
            // svislé čáry nahoru a dolů – dělí desky
            val lineW = 2.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, VsGold.copy(alpha = 0.7f * a)), startY = 0f, endY = size.height * 0.36f),
                topLeft = Offset(size.width / 2 - lineW / 2, 0f), size = size.copy(width = lineW, height = size.height * 0.36f)
            )
            drawRect(
                Brush.verticalGradient(listOf(VsGold.copy(alpha = 0.7f * a), Color.Transparent), startY = size.height * 0.64f, endY = size.height),
                topLeft = Offset(size.width / 2 - lineW / 2, size.height * 0.64f), size = size.copy(width = lineW, height = size.height * 0.36f)
            )
            // záře za písmeny
            drawCircle(Brush.radialGradient(
                0f to VsGold.copy(alpha = (0.42f + 0.18f * pulse) * a), 1f to Color.Transparent,
                center = center, radius = size.width * 0.75f
            ), radius = size.width * 0.75f)
            // prstenec
            drawCircle(VsGold.copy(alpha = 0.55f * a), radius = size.width * 0.42f, style = Stroke(1.5.dp.toPx()))
        }
        // Verzálky Cinzelu sedí o 0,048 em nad středem řádku (výška verzálek 700, ascent 976,
        // descent 372 z 1000) – při 40 sp ~2 dp. Posun dolů je dá přesně do středu prstenu.
        CampaignTitle(
            "VS",
            fontSize = 40.sp,
            modifier = Modifier.offset(y = 2.dp).graphicsLayer {
                val sc = 2.6f - 1.6f * slam
                scaleX = sc; scaleY = sc
                alpha  = slam
            }
        )
    }
}

/** Deska jedné strany na textuře bg_side_panels (rohy s medailony v poměru – sidePanelBackground). */
@Composable
private fun VsPanel(side: VsSide, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val s      = LocalStrings.current
    val accent = if (side.isMe) VsTeal else VsCrimson

    Box(modifier.size(width, height).sidePanelBackground(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Avatar v prstenu barvy strany
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(0f to accent.copy(alpha = 0.45f), 1f to Color.Transparent), radius = size.minDimension / 2)
                    drawCircle(Color(0xFF14100C), radius = size.minDimension * 0.42f)
                    drawCircle(accent, radius = size.minDimension * 0.42f, style = Stroke(2.5.dp.toPx()))
                    drawCircle(VsGold.copy(alpha = 0.6f), radius = size.minDimension * 0.47f, style = Stroke(1.dp.toPx()))
                }
                AvatarDisplay(avatar = side.avatar, sizeDp = 46f, modifier = Modifier.clip(CircleShape))
            }

            // Jméno písmem nadpisů
            CampaignTitle(side.name, fontSize = 14.sp, gradient = if (side.isMe) TitleTeal else TitleBlood)

            // Úroveň a hodnocení na destičkách
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (side.level >= 0) VsChip(R.drawable.star_icon, s.profileLevel.format(side.level), VsText)
                if (side.rating != null) VsChip(R.drawable.trophy_icon, "${side.rating}", VsGold)
            }

            // Skóre v módu: výhry · prohry (+ úspěšnost od 5 her)
            val st = side.stats
            if (st != null && st.games > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${st.wins} ${s.statWinShort}", color = VsWin, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("·", color = VsMuted, fontSize = 11.sp)
                    Text("${st.losses} ${s.statLossShort}", color = VsCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (st.games >= 5) {
                        Text("·", color = VsMuted, fontSize = 11.sp)
                        Text("${st.winRate} %", color = VsMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(s.vsFirstGame, color = VsMuted, fontSize = 10.sp)
            }

            Image(
                painter            = painterResource(R.drawable.bg_separator),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth(),
                contentScale       = ContentScale.FillWidth
            )

            // Pasivní schopnosti
            if (side.abilities.isEmpty()) {
                Text(s.vsNoAbilities, color = VsMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
            } else {
                // Schopnosti vedle sebe ve sloupcích na střed (pod sebou by se dvě nevešly nad
                // spodní ozdobu desky): destička s ikonou, název, popis až na 3 řádky.
                Row(
                    // odsazení od spodních rohových medailonů desky
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.Top
                ) {
                    side.abilities.take(2).forEach { ab ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(Modifier.size(20.dp).buttonTexture(R.drawable.plain_button_mini), contentAlignment = Alignment.Center) {
                                Image(painterResource(ab.iconRes), contentDescription = null, modifier = Modifier.size(13.dp))
                            }
                            Text(ab.localizedTitle(), color = VsText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ab.localizedDescription(), color = VsMuted, fontSize = 8.sp, lineHeight = 9.5.sp,
                                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Malá destička s ikonou a údajem (úroveň, hodnocení). */
@Composable
private fun VsChip(iconRes: Int, text: String, color: Color) {
    Row(
        Modifier.height(24.dp).buttonTexture(R.drawable.plain_button).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(12.dp))
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
