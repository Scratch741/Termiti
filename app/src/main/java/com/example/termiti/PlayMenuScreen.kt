package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PmBgCard    = Color(0xFF1A1320)
private val PmGold      = Color(0xFFD4A843)
private val PmMuted     = Color(0xFF7A6E5F)

@Composable
fun PlayMenuScreen(
    onOwnDeck:    () -> Unit,
    onSuperRandom: () -> Unit,
    onArena:      () -> Unit,
    onCampaign:   () -> Unit,
    onBack:       () -> Unit
) {
    val s = LocalStrings.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí – stejné jako main menu ───────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // ── Pochodně ──────────────────────────────────────────────────────────
        val imgAR  = 1791f / 975f
        val dispAR = W.value / H.value.coerceAtLeast(1f)
        val imgDispW: Dp
        val imgDispH: Dp
        val cropX: Dp
        val cropY: Dp
        if (dispAR >= imgAR) {
            imgDispW = W
            imgDispH = W / imgAR
            cropX    = 0.dp
            cropY    = (imgDispH - H) / 2f
        } else {
            imgDispW = H * imgAR
            imgDispH = H
            cropX    = (imgDispW - W) / 2f
            cropY    = 0.dp
        }
        val torchSize = H * 0.15f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ),
            size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ),
            size = torchSize, seed = 1.7f
        )

        // ── Zpět ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(PmBgCard.copy(alpha = 0.75f))
                .border(1.dp, PmMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(s.back, color = PmMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // ── Obsah ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Titulek
            Text("⚔️", fontSize = 40.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "VÝBĚR MÓDU",
                color = PmGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(20.dp))

            // Buttony – stejná šířka sloupce jako v hlavním menu
            val btnColW = minOf(W * 0.46f, H * 1.0f)
            Column(
                modifier = Modifier.width(btnColW),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(H * 0.010f)
            ) {
                MenuButton(s.ownDeck,     imageRes = R.drawable.button_1, accent = TealLight,         modifier = Modifier.fillMaxWidth(0.9f), onClick = onOwnDeck)
                MenuButton(s.superRandom, imageRes = R.drawable.button_9, accent = Color(0xFFE57373), modifier = Modifier.fillMaxWidth(0.9f), onClick = onSuperRandom)
                MenuButton(s.arena,       imageRes = R.drawable.button_2, accent = Gold,              modifier = Modifier.fillMaxWidth(0.9f), onClick = onArena)
                MenuButton(s.campaign,    imageRes = R.drawable.button_3, accent = Color(0xFF7EC8E3), modifier = Modifier.fillMaxWidth(0.9f), onClick = onCampaign)
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: String,
    title: String,
    description: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = Color(0xFFEDE0C4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(description, color = PmMuted, fontSize = 10.sp, textAlign = TextAlign.Start)
        }
    }
}
