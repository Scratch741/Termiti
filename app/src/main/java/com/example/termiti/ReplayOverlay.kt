package com.example.termiti

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ReplayOverlay(
    replay  : GameReplay,
    onClose : () -> Unit
) {
    val frames = replay.frames
    if (frames.isEmpty()) { onClose(); return }

    var frameIndex by remember { mutableStateOf(0) }
    var isPlaying  by remember { mutableStateOf(false) }

    val frame = frames[frameIndex]
    val state = frame.state

    // Auto-play
    LaunchedEffect(isPlaying, frameIndex) {
        if (!isPlaying) return@LaunchedEffect
        if (frameIndex >= frames.size - 1) { isPlaying = false; return@LaunchedEffect }
        delay(1400L)
        frameIndex++
    }

    // Celá obrazovka (stejné pozadí jako hra)
    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter            = painterResource(R.drawable.bg_game),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)))

        Column(Modifier.fillMaxSize()) {

            // ── Header ─────────────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(Color(0xEE1A0A2E))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Titulek + výsledek
                Column {
                    Text(
                        "🎬 REPLAY",
                        color         = Gold,
                        fontSize      = 13.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    val resultText  = if (replay.result.isPlayerWin()) "✔ ${replay.playerName} vyhrál" else "✘ ${replay.playerName} prohrál"
                    val resultColor = if (replay.result.isPlayerWin()) TealLight else Crimson
                    Text(resultText, color = resultColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Čítač snímků + kolo
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Akce ${frameIndex + 1} / ${frames.size}",
                        color      = TextMuted,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Kolo ${frame.turnNumber}",
                        color    = Gold.copy(alpha = 0.65f),
                        fontSize = 9.sp
                    )
                }

                // Zavřít
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("✕ Zavřít", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Banner: kdo co zahrál ──────────────────────────────────────────
            val bannerCard = frame.card
            if (bannerCard != null) {
                val actorColor = if (frame.isPlayer) TealLight else Crimson
                val actorEmoji = if (frame.isPlayer) replay.playerAvatar else replay.opponentAvatar
                val actorName  = if (frame.isPlayer) replay.playerName  else replay.opponentName
                val actionText = if (frame.action == CardAction.PLAYED) "zahrál" else "zahodil"
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .background(Color(0xBB0D0018))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "$actorEmoji $actorName $actionText: ",
                        color      = actorColor,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(bannerCard.displayName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .background(Color(0xBB0D0018))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Kolo ${frame.turnNumber}", color = TextMuted, fontSize = 10.sp)
                }
            }

            // ── Herní plocha – key(frameIndex) resetuje veškeré remember uvnitř ──
            // Bez key() by NewBattlefield si pamatovalo stale displayCard ze
            // starého snímku přes animaci a ukazovalo špatnou kartu v discardu.
            key(frameIndex) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    NewResourcePanel(
                        playerState = state.playerState,
                        isAi        = false,
                        modifier    = Modifier.fillMaxHeight().width(112.dp)
                    )
                    NewBattlefield(
                        playerState      = state.playerState,
                        aiState          = state.aiState,
                        lastCard         = frame.card,
                        lastCardAction   = frame.action,
                        lastCardIsPlayer = frame.isPlayer,
                        modifier         = Modifier.fillMaxHeight().weight(1f),
                        playerWinTarget  = state.playerWinTarget,
                        aiWinTarget      = state.aiWinTarget,
                        playerMaxHand    = state.playerMaxHand
                    )
                    NewResourcePanel(
                        playerState = state.aiState,
                        isAi        = true,
                        modifier    = Modifier.fillMaxHeight().width(112.dp)
                    )
                }
            }

            // ── Ruka hráče ─────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF6B3D12)))
            HandPanel(
                hand            = state.playerState.hand,
                isPlayerTurn    = false,   // replay: žádná interakce
                isComboTurn     = false,
                playerResources = state.playerState.resources,
                onPlayCard      = {},
                onDiscardCard   = {},
                onWait          = {},
                onEndTurn       = {},
                showHeader      = false,
                playerWallHp    = state.playerState.wallHP,
                playerCastleHp  = state.playerState.castleHP,
                animateDraws    = false,   // replay: skoky mezi snímky nemají „přilétat"
                modifier        = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .paint(painterResource(R.drawable.hand_background), contentScale = ContentScale.Crop)
            )

            // ── Navigační lišta ────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(Color(0xEE1A0A2E))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ReplayNavBtn("⏮", enabled = frameIndex > 0) {
                    frameIndex = 0; isPlaying = false
                }
                ReplayNavBtn("◀", enabled = frameIndex > 0) {
                    frameIndex--; isPlaying = false
                }
                ReplayNavBtn(
                    label   = if (isPlaying) "⏸" else "▶",
                    enabled = frames.size > 1,
                    color   = if (isPlaying) Gold else TealLight,
                    large   = true
                ) {
                    isPlaying = !isPlaying
                }
                ReplayNavBtn("▶", enabled = frameIndex < frames.size - 1) {
                    frameIndex++; isPlaying = false
                }
                ReplayNavBtn("⏭", enabled = frameIndex < frames.size - 1) {
                    frameIndex = frames.size - 1; isPlaying = false
                }
            }

            // ── Progress bar ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0xFF1A0A2E))
            ) {
                val progress = if (frames.size > 1)
                    frameIndex.toFloat() / (frames.size - 1).toFloat() else 1f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Brush.horizontalGradient(listOf(TealLight, Gold)))
                )
            }
        }
    }
}

@Composable
private fun ReplayNavBtn(
    label   : String,
    enabled : Boolean = true,
    color   : Color   = TextPrimary,
    large   : Boolean = false,
    onClick : () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.10f else 0.03f))
            .border(
                1.dp,
                color.copy(alpha = if (enabled) 0.45f else 0.10f),
                RoundedCornerShape(10.dp)
            )
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(
                horizontal = if (large) 20.dp else 14.dp,
                vertical   = if (large) 10.dp else 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = if (enabled) color else TextMuted.copy(alpha = 0.25f),
            fontSize   = if (large) 20.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
    }
}
