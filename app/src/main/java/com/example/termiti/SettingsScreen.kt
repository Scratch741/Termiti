package com.example.termiti

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    CampaignTitle(s.settings, fontSize = 26.sp)
                    // Podtržení nadpisu – stejný oddělovač jako v profilu
                    Image(
                        painter            = painterResource(R.drawable.bg_separator),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxWidth(),
                        contentScale       = ContentScale.FillWidth
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
                        IconMenuButton(imageRes = R.drawable.button_3, label = s.shop, size = iconSize, onClick = {})
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

/** Herní destička (plain_button_longer) pod jednou položkou nastavení. */
@Composable
private fun SettingsPlate(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .buttonTexture(R.drawable.plain_button_longer)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun LanguageToggle(
    label:       String,
    currentPack: LanguagePack?,
    allPacks:    List<LanguagePack>,
    onSelect:    (LanguagePack) -> Unit
) {
    SettingsPlate {
        Text(label, color = StTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allPacks.forEach { pack ->
                val selected = currentPack?.language?.code == pack.language.code
                PlainButton(
                    text         = "${pack.language.flag}  ${pack.language.name}",
                    modifier     = Modifier.weight(1f).height(30.dp),
                    textColor    = if (selected) StGold else StTextPrimary,
                    fontSize     = 11.sp,
                    selected     = selected,
                    outlineColor = StGold,      // vybraný jazyk = zlatý obrys, ostatní plně čitelné
                    paddingH     = 4.dp,
                    paddingV     = 0.dp,
                    onClick      = { onSelect(pack) }
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
    SettingsPlate {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = StTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "${(value * 100).roundToInt()} %",
                color = StGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        GameSlider(value, onValueChange)
    }
}

/**
 * Posuvník z herních textur místo Material Slideru: drážka = plain_button_longer,
 * jezdec = plain_button_mini, výplň zlatá. Ovládá se klepnutím i tažením kdekoli v řádku.
 */
@Composable
private fun GameSlider(value: Float, onValueChange: (Float) -> Unit) {
    val thumb    = 22.dp
    val onChange by rememberUpdatedState(onValueChange)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumb)
            .pointerInput(Unit) {
                val t = thumb.toPx()
                fun at(x: Float) = ((x - t / 2f) / (size.width - t).coerceAtLeast(1f)).coerceIn(0f, 1f)
                detectTapGestures { onChange(at(it.x)) }
            }
            .pointerInput(Unit) {
                val t = thumb.toPx()
                fun at(x: Float) = ((x - t / 2f) / (size.width - t).coerceAtLeast(1f)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { onChange(at(it.x)) }
                ) { change, _ -> change.consume(); onChange(at(change.position.x)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val travel = maxWidth - thumb
        val v      = value.coerceIn(0f, 1f)
        // Drážka
        Box(Modifier.fillMaxWidth().height(12.dp).buttonTexture(R.drawable.plain_button_longer))
        // Výplň od začátku drážky po střed jezdce
        Box(
            Modifier
                .padding(start = 4.dp)
                .width((travel * v + thumb / 2 - 4.dp).coerceAtLeast(0.dp))
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(StGold.copy(alpha = 0.45f), StGold)))
        )
        // Jezdec
        Box(Modifier.offset(x = travel * v).size(thumb).buttonTexture(R.drawable.plain_button_mini)) {
            Box(Modifier.align(Alignment.Center).size(6.dp).background(StGold, CircleShape))
        }
    }
}
