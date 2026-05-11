package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Výběr módu ──────────────────────────────────────────────────────────────

@Composable
fun MpSelectScreen(
    onOnline : () -> Unit,
    onLocal  : () -> Unit,
    onBack   : () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí – stejné jako hlavní menu ─────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // ── Pochodně (menu_bg 1791×975, AR≈1.837) ────────────────────────────
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

        // ── Tlačítko Zpět ─────────────────────────────────────────────────────
        Box(
            Modifier
                .padding(start = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("← Zpět", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // ── Střed: nadpis + tlačítka ─────────────────────────────────────────
        val centerW = minOf(W * 0.46f, H * 1.0f)

        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier            = Modifier.width(centerW),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(H * 0.010f)
            ) {
                Text(
                    "MULTIPLAYER",
                    color         = Gold,
                    fontSize      = (H.value * 0.08f).sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
                Text(
                    "Vyber způsob připojení",
                    color         = TextMuted,
                    fontSize      = (H.value * 0.024f).sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(H * 0.01f))
                MenuButton(
                    label    = "ONLINE",
                    accent   = TealLight,
                    imageRes = R.drawable.button_8,
                    onClick  = onOnline
                )
                MenuButton(
                    label    = "LOKÁLNĚ",
                    accent   = Gold,
                    imageRes = R.drawable.button_9,
                    onClick  = onLocal
                )
            }
        }
    }
}
