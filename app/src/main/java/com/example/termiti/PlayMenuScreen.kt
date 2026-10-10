package com.example.termiti

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PmBgCard    = Color(0xFF1A1320)
private val PmGold      = Color(0xFFD4A843)
private val PmMuted     = Color(0xFF7A6E5F)

@Composable
fun PlayMenuScreen(
    onOwnDeck:     () -> Unit,
    onSuperRandom: () -> Unit,
    onArena:       () -> Unit,
    onRoguelike:   () -> Unit = {},
    onCampaign:    () -> Unit,
    onBack:        () -> Unit,
    onShop:        () -> Unit = {},
    onSettings:    () -> Unit = {},
    onExit:        () -> Unit = {}
) {
    val s       = LocalStrings.current
    val profile = PlayerProfileManager.profile

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí ───────────────────────────────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.menu_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // ── Pochodně ─────────────────────────────────────────────────────────
        val imgAR  = 1791f / 975f
        val dispAR = W.value / H.value.coerceAtLeast(1f)
        val imgDispW: Dp
        val imgDispH: Dp
        val cropX: Dp
        val cropY: Dp
        if (dispAR >= imgAR) {
            imgDispW = W; imgDispH = W / imgAR; cropX = 0.dp; cropY = (imgDispH - H) / 2f
        } else {
            imgDispW = H * imgAR; imgDispH = H; cropX = (imgDispW - W) / 2f; cropY = 0.dp
        }
        val torchSize = H * 0.15f
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.112f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 0f
        )
        TorchFlame(
            modifier = Modifier.align(Alignment.TopStart).offset(
                x = imgDispW * 0.898f - cropX - torchSize / 2,
                y = imgDispH * 0.17f  - cropY - torchSize * 0.80f
            ), size = torchSize, seed = 1.7f
        )

        // Pravidla režimu (název tlačítka → text), otevřená otazníkem vedle tlačítka
        var rules by remember { mutableStateOf<Pair<String, String>?>(null) }

        // ── Stejný 3-sloupcový layout jako hlavní menu ────────────────────────
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

            // ── Střed – logo + buttony ────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxHeight().width(centerW).offset(x = centerShift),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier            = Modifier.width(centerW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(H * 0.010f)
                ) {
                    Image(
                        painter            = painterResource(R.drawable.logo_darkmage),
                        contentDescription = "DarkMage",
                        modifier           = Modifier
                            .requiredWidth(centerW * 1.1f)
                            .offset(y = -H * -0.01f),
                        contentScale       = ContentScale.FillWidth
                    )
                    Spacer(Modifier.height(H * 0.01f))
                    val helpSize = H * 0.075f
                    ModeRow(helpSize, onHelp = { rules = s.ownDeck to s.rulesConstructed }) {
                        MenuButton(s.ownDeck,     imageRes = R.drawable.button_14, accent = TealLight,         onClick = onOwnDeck)
                    }
                    ModeRow(helpSize, onHelp = { rules = s.superRandom to s.rulesSuperRandom }) {
                        MenuButton(s.superRandom, imageRes = R.drawable.button_10, accent = Color(0xFFE57373), onClick = onSuperRandom)
                    }
                    // Aréna dočasně skryta (nevejde se; ponechána jako záloha – onArena/routing zůstává)
                    ModeRow(helpSize, onHelp = { rules = "ROGUELIKE" to s.rulesRoguelike }) {
                        MenuButton("ROGUELIKE",   imageRes = R.drawable.button_13, accent = Color(0xFFB388FF), onClick = onRoguelike)
                    }
                    ModeRow(helpSize, onHelp = { rules = s.campaign to s.rulesCampaign }) {
                        MenuButton(s.campaign,    imageRes = R.drawable.button_12, accent = Color(0xFF7EC8E3), onClick = onCampaign)
                    }
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
                    IconMenuButton(imageRes = R.drawable.button_3, label = s.shop,     size = iconSize, onClick = onShop)
                    IconMenuButton(imageRes = R.drawable.button_5, label = s.settings, size = iconSize, onClick = onSettings)
                    IconMenuButton(imageRes = R.drawable.button_6, label = s.back.removePrefix("← "), size = iconSize, onClick = { onBack() })
                }
            }
        }

        rules?.let { (mode, text) -> RulesOverlay(s.rulesTitle.format(mode), text, s.close) { rules = null } }
    }
}

/**
 * Řádek režimu: tlačítko uprostřed jako dřív a vpravo VEDLE něj otazník s pravidly.
 * Otazník je posunutý za okraj sloupce, takže tlačítka zůstávají stejně široká a vystředěná.
 */
@Composable
internal fun ModeRow(helpSize: Dp, onHelp: () -> Unit, button: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        button()
        PlainButton(
            text      = "?",
            modifier  = Modifier.align(Alignment.CenterEnd).offset(x = helpSize * 0.8f).size(helpSize),
            textColor = Gold,
            fontSize  = (helpSize.value * 0.5f).sp,
            paddingH  = 0.dp,
            paddingV  = 0.dp,
            buttonRes = R.drawable.plain_button_mini,
            onClick   = onHelp
        )
    }
}

/** Překryv s pravidly režimu – zavře se tlačítkem i klepnutím mimo. */
@Composable
internal fun RulesOverlay(title: String, text: String, closeLabel: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxHeight(0.92f).widthIn(max = 460.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            CampaignTitle(title, fontSize = 22.sp)
            Text(
                text,
                color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.weight(1f, fill = false).run { val st = rememberScrollState(); scrollRail(st).verticalScroll(st) }
            )
            PlainButton(
                text      = closeLabel,
                modifier  = Modifier.width(120.dp).height(34.dp),
                textColor = Gold,
                fontSize  = 11.sp,
                paddingH  = 6.dp,
                paddingV  = 0.dp,
                onClick   = onDismiss
            )
        }
    }
}
