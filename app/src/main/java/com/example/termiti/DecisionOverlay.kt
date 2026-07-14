package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

@Composable
fun DecisionOverlay(
    decision         : DecisionState,
    secondsLeft      : Int?,
    onChoice         : (Card) -> Unit,
    onResourceChoice : ((ResourceType, Int) -> Unit)? = null
) {
    val s       = LocalStrings.current
    var peeking by remember { mutableStateOf(false) }

    if (peeking) {
        // ── Peek mód: hra je viditelná, ale veškerý input je zablokovaný ─────
        // Průhledný overlay přes celou obrazovku pohltí dotyky (=hra nejde ovládat).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures {} }   // blokuje vše
        ) {
            // Plovoucí tlačítko pro návrat – sedí těsně nad rukou hráče (152 dp)
            Box(
                modifier          = Modifier.fillMaxSize(),
                contentAlignment  = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 162.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xEE1A0A2E))
                        .border(1.dp, Gold.copy(alpha = 0.70f), RoundedCornerShape(24.dp))
                        .clickable { peeking = false }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("↩", color = Gold, fontSize = 14.sp)
                    Text(
                        s.decisionBackToDecision,
                        color         = Gold,
                        fontSize      = 13.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    if (secondsLeft != null) {
                        val timerColor = if (secondsLeft <= 10) Color(0xFFFF4444) else TextMuted
                        Text(
                            "${secondsLeft}s",
                            color      = timerColor,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        // ── Normální mód: plný overlay ────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .pointerInput(Unit) { detectTapGestures {} },
            contentAlignment = Alignment.Center
        ) {
            // Šířka před zvětšením: po scale(1.15) nesmí přesáhnout 96 % obrazovky
            val maxPanelW = maxWidth * 0.96f / 1.15f
            Column(
                modifier = Modifier
                    .scale(1.15f)
                    .widthIn(max = maxPanelW)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        Modifier.paint(
                            painterResource(R.drawable.mulligan_background),
                            contentScale = ContentScale.Crop
                        )
                    )
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Nadpis + timer
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        decision.title,
                        color = Gold, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 5.sp
                    )
                    if (secondsLeft != null) {
                        val timerColor = if (secondsLeft <= 10) Color(0xFFFF4444) else TextMuted
                        Text(
                            "${secondsLeft}s",
                            color = timerColor, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Podnapis – **tučná** klíčová slova zlatě (spálí, ukradne, získáš…)
                Text(
                    parseCardDesc(decision.subtitle, boldColor = Gold),
                    color = TextPrimary, fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )

                // Obsah rozhodnutí – buď výběr zdroje, nebo výběr karet
                if (decision.resourceChoices.isNotEmpty() && onResourceChoice != null) {
                    // ── Výběr zdroje (Alchymistova volba) ─────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        decision.resourceChoices.forEach { rc ->
                            val (iconRes, label, accent) = when (rc.type) {
                                ResourceType.MAGIC  -> Triple(R.drawable.magie_icon,  "Magie",  Color(0xFF7EC8E3))
                                ResourceType.ATTACK -> Triple(R.drawable.utok_icon,   "Útok",   Color(0xFFE07070))
                                ResourceType.STONES -> Triple(R.drawable.kamen_icon2, "Kameny", Color(0xFFB39DDB))
                                ResourceType.CHAOS  -> Triple(R.drawable.chaos_icon,  "Chaos",  Gold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.14f))
                                    .border(1.5.dp, accent.copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                                    .clickable { onResourceChoice(rc.type, rc.amount) }
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(32.dp))
                                    Text(
                                        "+${rc.amount}",
                                        color      = accent,
                                        fontSize   = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        label,
                                        color      = TextMuted,
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign  = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Výběr karet – kliknutím se vybere; při přeplnění scrollovatelné ──
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        decision.options.forEach { card ->
                            Box(modifier = Modifier.clickable { onChoice(card) }) {
                                CardView(
                                    card        = card,
                                    canPlay     = true,
                                    discardMode = false,
                                    onClick     = { onChoice(card) },
                                    showGlow    = true
                                )
                            }
                        }
                    }
                }

                // Tlačítko náhledu
                PlainButton(
                    text      = s.decisionPreviewGame,
                    textColor = Gold,
                    fontSize  = 10.sp,
                    paddingH  = 12.dp,
                    paddingV  = 6.dp,
                    onClick   = { peeking = true }
                )
            }
        }
    }
}
