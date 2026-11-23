package com.example.termiti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    // Systémové Zpět: zpět do menu (dřív zavřelo celou hru)
    androidx.activity.compose.BackHandler { onBack() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí – stejné jako main menu ───────────────────────────────────
        MenuBackground()

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
                    modifier            = Modifier.width(centerW).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CampaignTitle(s.settings, fontSize = 26.sp)
                    BronzeDivider(Modifier.fillMaxWidth(0.85f))
                    // Jeden obsidiánový panel s bronzovou hranou (styl Tvorby balíčku a obchodu)
                    Column(
                        modifier            = Modifier.fillMaxWidth().obsidianPanel(10.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                        BronzeDivider(Modifier.fillMaxWidth(), alpha = 0.55f)
                        LanguageToggle(
                            label       = s.languageLabel,
                            currentPack = currentPack,
                            allPacks    = LanguageManager.availablePacks,
                            onSelect    = { pack -> LanguageManager.setLanguage(pack) }
                        )
                        PlayStyleToggle(
                            dragToPlay = GameSettings.dragToPlay,
                            onSelect   = { GameSettings.updateDragToPlay(it) }
                        )
                    }
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
                        IconMenuButton(imageRes = R.drawable.button_3, label = s.shop, size = iconSize,
                        overlay = { MedallionLayer(R.drawable.menu_icon_chest_open, alpha = { 1f }) }, onClick = {})
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
/** Popisek sekce: emoji z jazykového souboru pryč (🎵 Hudba → HUDBA), zlatý Cinzel. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.replace(Regex("^[^\\p{L}]+"), "").uppercase(),
        modifier = modifier,
        style    = obsLabelStyle(13.sp, BronzeText, letterSpacing = 2.sp)
    )
}

/** Přepínač z desek: vybraná je bronzová (jako hlavní tlačítko), ostatní obsidiánové. */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            ObsButton(
                text     = label.uppercase(),
                modifier = Modifier.weight(1f).height(34.dp),
                primary  = value == selected,
                fontSize = 12.sp,
                onClick  = { if (value != selected) onSelect(value) }
            )
        }
    }
}

@Composable
private fun LanguageToggle(
    label:       String,
    currentPack: LanguagePack?,
    allPacks:    List<LanguagePack>,
    onSelect:    (LanguagePack) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionLabel(label)
        Segmented(
            options  = allPacks.map { it to it.language.name },
            selected = allPacks.firstOrNull { it.language.code == currentPack?.language?.code } ?: allPacks.first(),
            onSelect = onSelect
        )
    }
}

/** Způsob hraní karet: klepnutím (výchozí) nebo tažením nad bojiště (viz [GameSettings.dragToPlay]). */
@Composable
private fun PlayStyleToggle(dragToPlay: Boolean, onSelect: (Boolean) -> Unit) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionLabel(s.playStyleLabel)
        Segmented(listOf(false to s.playStyleTap, true to s.playStyleDrag), dragToPlay, onSelect)
        Text(
            if (dragToPlay) s.playStyleDragHint else s.playStyleTapHint,
            color = ObsTextMuted, fontSize = 9.sp, lineHeight = 12.sp
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(label)
            Text("${(value * 100).roundToInt()} %", style = obsLabelStyle(13.sp, ObsText))
        }
        GameSlider(value, onValueChange)
    }
}

/**
 * Posuvník ve stylu obsidián/bronz: tmavá drážka s bronzovou hranou, bronzová výplň
 * a jezdec jako bronzový kosočtverec se žhavou září. Klepnutí i tažení kdekoli v řádku.
 */
@Composable
private fun GameSlider(value: Float, onValueChange: (Float) -> Unit) {
    val thumb    = 24.dp
    val onChange by rememberUpdatedState(onValueChange)
    Canvas(
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
            }
    ) {
        val t   = thumb.toPx()
        val cy  = size.height / 2
        val x0  = t / 2; val x1 = size.width - t / 2
        val xv  = x0 + (x1 - x0) * value.coerceIn(0f, 1f)
        val th  = 7.dp.toPx()
        val r   = androidx.compose.ui.geometry.CornerRadius(th / 2)
        // drážka
        drawRoundRect(Color(0xFF0A080A), androidx.compose.ui.geometry.Offset(x0 - th / 2, cy - th / 2),
            androidx.compose.ui.geometry.Size(x1 - x0 + th, th), r)
        // bronzová výplň
        if (xv > x0) drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFECBE78), Color(0xFF8C5C28)), startY = cy - th / 2, endY = cy + th / 2),
            androidx.compose.ui.geometry.Offset(x0 - th / 2 + 1f, cy - th / 2 + 1f),
            androidx.compose.ui.geometry.Size(xv - x0 + th / 2, th - 2f), r
        )
        drawRoundRect(Brush.verticalGradient(listOf(BronzeLight, BronzeDark)), androidx.compose.ui.geometry.Offset(x0 - th / 2, cy - th / 2),
            androidx.compose.ui.geometry.Size(x1 - x0 + th, th), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        // záře a kosočtverec
        drawCircle(Brush.radialGradient(listOf(Color(0x66E8A83A), Color.Transparent), center = androidx.compose.ui.geometry.Offset(xv, cy), radius = t),
            radius = t, center = androidx.compose.ui.geometry.Offset(xv, cy))
        fun diamond(sz: Float, brush: Brush) = drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(xv, cy - sz); lineTo(xv + sz, cy); lineTo(xv, cy + sz); lineTo(xv - sz, cy); close()
        }, brush)
        val sz = t * 0.46f
        diamond(sz + 1.5f, Brush.linearGradient(listOf(Color.Black, Color.Black)))
        diamond(sz, Brush.verticalGradient(listOf(Color(0xFFF2D49A), BronzeLight, BronzeDark), startY = cy - sz, endY = cy + sz))
        diamond(sz * 0.45f, Brush.verticalGradient(listOf(Color(0xFFFFF0C8), Bronze), startY = cy - sz, endY = cy + sz))
    }
}
