package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val StBgCard      = Color(0xFF1A1320)
private val StGold        = Color(0xFFD4A843)
private val StTealLight   = Color(0xFF3DBFAD)
private val StTextPrimary = Color(0xFFEDE0C4)
private val StTextMuted   = Color(0xFF7A6E5F)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var musicVol by remember { mutableFloatStateOf(SoundManager.musicVolume) }
    var sfxVol   by remember { mutableFloatStateOf(SoundManager.sfxVolume) }
    val s           = LocalStrings.current
    val currentPack by LanguageManager.currentPackState

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí – stejné jako main menu ───────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // ── Pochodně – stejná logika jako v MenuScreen ────────────────────────
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

        // ── Stejný 3-sloupcový layout jako hlavní menu ────────────────────────
        val profile          = PlayerProfileManager.profile
        val centerW          = minOf(W * 0.46f, H * 1.0f)
        val iconSize         = H * 0.12f
        val leftColShift     = -5.dp
        val leftColVertShift = 30.dp
        val rightColShift    = -25.dp
        val rightColVertShift= 35.dp
        val centerShift      = 9.dp

        Row(
            modifier          = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
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

            // ── Střed – nastavení ─────────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().width(centerW).offset(x = centerShift),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.width(centerW).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        s.settings,
                        color = StGold, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    SettingsSlider(
                        label = s.music,
                        value = musicVol,
                        onValueChange = { v -> musicVol = v; SoundManager.setMusicVolume(v) }
                    )
                    SettingsSlider(
                        label = s.soundEffects,
                        value = sfxVol,
                        onValueChange = { v -> sfxVol = v; SoundManager.setSfxVolume(v) }
                    )
                    LanguageToggle(
                        label       = s.languageLabel,
                        currentPack = currentPack,
                        allPacks    = LanguageManager.availablePacks,
                        onSelect    = { pack -> LanguageManager.setLanguage(pack) }
                    )
                }
            }

            // ── Pravý sloupec – zpět ──────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.offset(x = -rightColShift, y = rightColVertShift),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.005f)
                ) {
                    Box(Modifier.graphicsLayer { alpha = 0f }) {
                        IconMenuButton(imageRes = R.drawable.button_7, label = s.shop, size = iconSize, onClick = {})
                    }
                    Box(Modifier.graphicsLayer { alpha = 0f }) {
                        IconMenuButton(imageRes = R.drawable.button_5, label = s.settings, size = iconSize, onClick = {})
                    }
                    IconMenuButton(imageRes = R.drawable.button_6, label = s.back.removePrefix("← "), size = iconSize, onClick = { onBack() })
                }
            }
        }
    } // konec BoxWithConstraints
}

@Composable
private fun LanguageToggle(
    label:       String,
    currentPack: LanguagePack?,
    allPacks:    List<LanguagePack>,
    onSelect:    (LanguagePack) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = StTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allPacks.forEach { pack ->
                val selected = currentPack?.language?.code == pack.language.code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) StGold.copy(alpha = 0.18f) else StBgCard
                        )
                        .border(
                            1.dp,
                            if (selected) StGold else StTextMuted.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { SoundManager.playMenuTap(); onSelect(pack) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${pack.language.flag}  ${pack.language.name}",
                        color      = if (selected) StGold else StTextMuted,
                        fontSize   = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = StTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${(value * 100).roundToInt()} %",
                color = StTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor        = StGold,
                activeTrackColor  = StGold.copy(alpha = 0.8f),
                inactiveTrackColor = StTextMuted.copy(alpha = 0.3f)
            )
        )
    }
}
