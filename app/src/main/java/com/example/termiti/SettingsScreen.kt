package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

        // ── Panel uprostřed ───────────────────────────────────────────────────
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Nadpis
            Text(
                s.settings,
                color = StGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            // Hlasitost hudby
            SettingsSlider(
                label = s.music,
                value = musicVol,
                onValueChange = { v ->
                    musicVol = v
                    SoundManager.setMusicVolume(v)
                }
            )

            // Hlasitost efektů
            SettingsSlider(
                label = s.soundEffects,
                value = sfxVol,
                onValueChange = { v ->
                    sfxVol = v
                    SoundManager.setSfxVolume(v)
                }
            )

            // Jazyk
            LanguageToggle(
                label       = s.languageLabel,
                currentPack = currentPack,
                allPacks    = LanguageManager.availablePacks,
                onSelect    = { pack -> LanguageManager.setLanguage(pack) }
            )

            // Zpět
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StTealLight.copy(alpha = 0.12f))
                    .border(1.dp, StTealLight.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { SoundManager.playMenuTap(); onBack() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.back,
                    color = StTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    } // konec středového Boxu
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
