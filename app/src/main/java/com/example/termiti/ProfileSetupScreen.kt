package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PsGold        = Color(0xFFD4A843)
private val PsTealLight   = Color(0xFF3DBFAD)
private val PsTextPrimary = Color(0xFFEDE0C4)
private val PsTextMuted   = Color(0xFF7A6E5F)

@Composable
fun ProfileSetupScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = name.trim().length in 2..16

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight

        // ── Pozadí (stejné jako hlavní menu) ─────────────────────────────────
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

        // ── Obsah: vycentrovaný sloupec přímo na pozadí ───────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter            = painterResource(R.drawable.logo_darkmage),
                contentDescription = "DarkMage",
                modifier           = Modifier
                    .width(220.dp)
                    .wrapContentHeight(),
                contentScale       = ContentScale.FillWidth
            )

            Text(
                "Zadej své jméno hrdiny",
                color    = PsTextPrimary.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(Color.Black, Offset(0f, 2f), blurRadius = 6f)
                )
            )

            // ── Textové pole ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .border(
                        1.dp,
                        if (canConfirm) PsGold.copy(alpha = 0.70f)
                        else PsTextMuted.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                BasicTextField(
                    value          = name,
                    onValueChange  = { if (it.length <= 16) name = it },
                    singleLine     = true,
                    textStyle      = TextStyle(
                        color      = PsTextPrimary,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush    = SolidColor(PsGold),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canConfirm) {
                            SoundManager.playMenuTap()
                            PlayerProfileManager.createProfile(name)
                            onDone()
                        }
                    }),
                    modifier       = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox  = { inner ->
                        if (name.isEmpty()) {
                            Text("Jméno hrdiny…", color = PsTextMuted, fontSize = 15.sp)
                        }
                        inner()
                    }
                )
            }

            Text(
                "${name.trim().length}/16",
                color    = PsTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End)
            )

            // ── Potvrdit ──────────────────────────────────────────────────────
            PlainButton(
                text      = "VSTOUPIT DO HRY",
                modifier  = Modifier.fillMaxWidth(),
                textColor = PsTealLight,
                fontSize  = 12.sp,
                enabled   = canConfirm,
                paddingH  = 0.dp,
                paddingV  = 10.dp,
                buttonRes = R.drawable.plain_button_longer,
                onClick   = {
                    PlayerProfileManager.createProfile(name)
                    onDone()
                }
            )
        }
    }
}
