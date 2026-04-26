package com.example.termiti

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

private val PsBgDeep    = Color(0xFF0D0A0E)
private val PsBgPanel   = Color(0xFF13101A)
private val PsBgCard    = Color(0xFF1A1320)
private val PsGold      = Color(0xFFD4A843)
private val PsTealLight = Color(0xFF3DBFAD)
private val PsTextPrimary = Color(0xFFEDE0C4)
private val PsTextMuted   = Color(0xFF7A6E5F)

@Composable
fun ProfileSetupScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = name.trim().length in 2..16

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PsBgDeep, PsBgPanel, PsBgDeep))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PsBgCard)
                .border(1.dp, PsGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⚔️", fontSize = 36.sp)

            Text(
                "Vítej v Termiti!",
                color      = PsGold,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                "Zadej své jméno hrdiny",
                color    = PsTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            // Textové pole
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PsBgPanel)
                    .border(
                        1.dp,
                        if (canConfirm) PsGold.copy(alpha = 0.6f) else PsTextMuted.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 16) name = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color      = PsTextPrimary,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(PsGold),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canConfirm) {
                            SoundManager.playMenuTap()
                            PlayerProfileManager.createProfile(name)
                            onDone()
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
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

            // Potvrdit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (canConfirm) PsTealLight.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (canConfirm) PsTealLight.copy(alpha = 0.6f)
                        else PsTextMuted.copy(alpha = 0.2f),
                        RoundedCornerShape(10.dp)
                    )
                    .then(
                        if (canConfirm) Modifier.clickable {
                            SoundManager.playMenuTap()
                            PlayerProfileManager.createProfile(name)
                            onDone()
                        } else Modifier
                    )
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "VSTOUPIT DO BITVY",
                    color = if (canConfirm) PsTextPrimary else PsTextMuted.copy(alpha = 0.4f),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
