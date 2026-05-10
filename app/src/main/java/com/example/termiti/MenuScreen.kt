package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta barev → GameColors.kt

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onBuildDeck: () -> Unit,
    onMultiplayer: () -> Unit,
    onProfile: () -> Unit,
    onShop: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    val profile = PlayerProfileManager.profile

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí ────────────────────────────────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // ── Pochodně (menu_bg 1791×975, AR≈1.837) ────────────────────────────
        // x, y = procenta RAW obrázku; kód přepočítá ContentScale.Crop sám
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
        // y=0.18f = malinko níž než 0.16f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f - cropY - torchSize * 0.80f
            ),
            size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f - cropY - torchSize * 0.80f
            ),
            size = torchSize, seed = 1.7f
        )

        // ── Hlavní layout: side=weight(1f) | střed=pevná šířka | side=weight(1f) ──
        // Střed má pevnou šířku (W*0.46 nebo H – co je menší), zbytek si strany
        // rozdělí rovnoměrně → levý/pravý sloupec je vždy symetricky vycentrovaný.
        val centerW  = minOf(W * 0.46f, H * 1.0f)
        val iconSize = H * 0.12f

        // ── RUČNÍ POSUN SLOUPCŮ ─────────────────────────────────────────────
        // Funguje i se zápornými hodnotami (na rozdíl od padding).
        val leftColShift       = -5.dp   // levý sloupec:  kladné = doprava (ke středu), záporné = doleva
        val leftColVertShift   = 30.dp    // levý sloupec:  kladné = dolů, záporné = nahoru
        val rightColShift      = -25.dp  // pravý sloupec: kladné = doleva  (ke středu), záporné = doprava
        val rightColVertShift  = 35.dp   // pravý sloupec: kladné = dolů, záporné = nahoru
        // ────────────────────────────────────────────────────────────────────

        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = H * 0.02f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Levý sloupec – profil ─────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (profile != null) {
                    Column(
                        modifier            = Modifier.offset(x = leftColShift, y = leftColVertShift),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(H * 0.025f)
                    ) {
                        ProfileInfo(profile, H)
                    }
                }
            }

            // ── Střed – titulek + 4 tlačítka (pevná šířka) ───────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().width(centerW),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.width(centerW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.010f)
                ) {
                    Text(
                        "DARKMAGE",
                        color         = Gold,
                        fontSize      = (H.value * 0.08f).sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        style         = TextStyle(
                            shadow = Shadow(
                                color      = Color.Black.copy(alpha = 0.85f),
                                offset     = Offset(0f, 3f),
                                blurRadius = 10f
                            )
                        )
                    )
                    Text(
                        "Karetní hradní bitva",
                        color         = TextMuted,
                        fontSize      = (H.value * 0.024f).sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(H * 0.01f))
                    MenuButton("HRÁT",           imageRes = R.drawable.button_1, accent = TealLight,         onClick = onPlay)
                    MenuButton("MULTIPLAYER",     imageRes = R.drawable.button_2, accent = Color(0xFF3A7BD5), onClick = onMultiplayer)
                    MenuButton("SESTAVIT BALÍK",  imageRes = R.drawable.button_3, accent = Gold,              onClick = onBuildDeck)
                    MenuButton("PROFIL",          imageRes = R.drawable.button_4, accent = Color(0xFF7EC8E3), onClick = onProfile)
                }
            }

            // ── Pravý sloupec – ikony ─────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.offset(x = -rightColShift, y = rightColVertShift),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.005f)
                ) {
                    IconMenuButton(imageRes = R.drawable.button_7, label = "BALÍČKY",  size = iconSize, onClick = onShop)
                    IconMenuButton(imageRes = R.drawable.button_5, label = "NASTAVENÍ", size = iconSize, onClick = onSettings)
                    IconMenuButton(imageRes = R.drawable.button_6, label = "KONEC",     size = iconSize, onClick = onExit)
                }
            }
        }
    }
}

// ── Profilový sloupec ─────────────────────────────────────────────────────────

@Composable
private fun ProfileInfo(profile: PlayerProfile, H: Dp) {
    val fs = (H.value * 0.035f).sp
    // Avatar
    Box(
        modifier = Modifier
            .size(H * 0.09f)
            .clip(RoundedCornerShape(H * 0.02f))
            .background(Gold.copy(alpha = 0.15f))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(H * 0.02f)),
        contentAlignment = Alignment.Center
    ) {
        Text(profile.avatar, fontSize = (H.value * 0.045f).sp)
    }
    // Level pod avatarem
    Text("Lv. ${profile.level}", color = Gold, fontSize = fs, fontWeight = FontWeight.Bold)
    // Gold
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.015f)) {
        Text("🪙", fontSize = fs)
        Text("${profile.gold}", color = Gold, fontSize = fs, fontWeight = FontWeight.Bold)
    }
    // Gems
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.015f)) {
        Text("💎", fontSize = fs)
        Text("${profile.gems}", color = Color(0xFF7EC8E3), fontSize = fs, fontWeight = FontWeight.Bold)
    }
    // Dust
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(H * 0.015f)) {
        Text("✨", fontSize = fs)
        Text("${profile.dust}", color = Color(0xFFB39DDB), fontSize = fs, fontWeight = FontWeight.Bold)
    }
}

// ── Emoji ikonkové tlačítko (bez image assetu) ────────────────────────────────

@Composable
private fun EmojiIconMenuButton(
    emoji: String,
    label: String,
    size: Dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.08f)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.14f))
                .border(1.5.dp, Gold.copy(alpha = 0.55f), CircleShape)
                .clickable { SoundManager.playMenuTap(); onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = (size.value * 0.42f).sp)
        }
        Text(
            label,
            color         = TextMuted,
            fontSize      = (size.value * 0.20f).sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ── Ikonkové tlačítko ─────────────────────────────────────────────────────────

@Composable
private fun IconMenuButton(
    @DrawableRes imageRes: Int,
    label: String,
    size: Dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.08f)
    ) {
        // Obrázek 662×107 (AR=6.19), centroid ikony na x=12.5% šířky.
        // requiredHeight/Width obejde omezení Boxu → obraz se renderuje v plné šířce.
        // offset(x = -0.27×size) posune tak, aby ikona byla přesně uprostřed kruhu.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable { SoundManager.playMenuTap(); onClick() }
        ) {
            Image(
                painter            = painterResource(imageRes),
                contentDescription = label,
                modifier           = Modifier
                    .requiredHeight(size)
                    .requiredWidth(size * 6.19f)
                    .offset(x = -(size * -2.4f)),
                contentScale       = ContentScale.FillBounds
            )
        }
        Text(
            label,
            color         = TextMuted,
            fontSize      = (size.value * 0.20f).sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ── Tlačítko menu ─────────────────────────────────────────────────────────────

@Composable
fun MenuButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier.fillMaxWidth(0.9f),
    enabled: Boolean = true,
    @DrawableRes imageRes: Int? = null,
    onClick: () -> Unit
) {
    val alphaVal = if (enabled) 1f else 0.4f

    if (imageRes != null) {
        // Varianta s obrázkem – BoxWithConstraints pro proporcionální text offset
        BoxWithConstraints(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
                .alpha(alphaVal)
        ) {
            val btnW = maxWidth
            val btnH = maxHeight
            Image(
                painter            = painterResource(imageRes),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth(),
                contentScale       = ContentScale.FillWidth
            )
            // Ikonka zabírá ~1/6 šířky → text začíná za ní
            Box(
                modifier         = Modifier
                    .matchParentSize()
                    .padding(start = btnW * 0.23f, end = btnW * 0.03f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    label,
                    color         = TextPrimary,
                    fontSize      = (btnW.value * 0.045f).sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    } else {
        // Fallback bez obrázku
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = if (enabled) 0.12f else 0.05f))
                .border(1.dp, accent.copy(alpha = if (enabled) 0.5f else 0.2f), RoundedCornerShape(10.dp))
                .then(if (enabled) Modifier.clickable { SoundManager.playMenuTap(); onClick() } else Modifier)
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color         = if (enabled) TextPrimary.copy(alpha = alphaVal) else TextMuted.copy(alpha = alphaVal),
                fontSize      = 13.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}
