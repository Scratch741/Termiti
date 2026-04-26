package com.example.termiti

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val StBgDeep    = Color(0xFF0D0A0E)
private val StBgPanel   = Color(0xFF13101A)
private val StBgCard    = Color(0xFF1A1320)
private val StGold      = Color(0xFFD4A843)
private val StTealLight = Color(0xFF3DBFAD)
private val StTextPrimary = Color(0xFFEDE0C4)
private val StTextMuted   = Color(0xFF7A6E5F)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var musicVol by remember { mutableFloatStateOf(SoundManager.musicVolume) }
    var sfxVol   by remember { mutableFloatStateOf(SoundManager.sfxVolume) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(StBgDeep, StBgPanel, StBgDeep))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StBgCard)
                .border(1.dp, StGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Nadpis
            Text(
                "⚙️  NASTAVENÍ",
                color = StGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            // Hlasitost hudby
            SettingsSlider(
                label = "🎵  Hudba",
                value = musicVol,
                onValueChange = { v ->
                    musicVol = v
                    SoundManager.setMusicVolume(v)
                }
            )

            // Hlasitost efektů
            SettingsSlider(
                label = "🔊  Efekty",
                value = sfxVol,
                onValueChange = { v ->
                    sfxVol = v
                    SoundManager.setSfxVolume(v)
                }
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
                    "←  ZPĚT",
                    color = StTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
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
