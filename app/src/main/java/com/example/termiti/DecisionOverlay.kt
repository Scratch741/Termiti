package com.example.termiti

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
    decision    : DecisionState,
    secondsLeft : Int?,
    onChoice    : (Card) -> Unit
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
                .background(Color(0xE5000000))
                .pointerInput(Unit) { detectTapGestures {} },
            contentAlignment = Alignment.Center
        ) {
            val maxPanelW = maxWidth * 0.96f
            Column(
                modifier = Modifier
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

                // Podnapis
                Text(
                    decision.subtitle,
                    color = TextMuted, fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )

                // Karty – kliknutím se vybere; při přeplnění scrollovatelné
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

                // Tlačítko náhledu – vycentrované
                Box(
                    modifier          = Modifier.fillMaxWidth(),
                    contentAlignment  = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gold.copy(alpha = 0.15f))
                            .border(1.dp, Gold.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .clickable { peeking = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            s.decisionPreviewGame,
                            color      = Gold,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
