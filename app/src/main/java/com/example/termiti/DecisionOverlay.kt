package com.example.termiti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun DecisionOverlay(
    decision    : DecisionState,
    secondsLeft : Int?,
    onChoice    : (Card) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE5000000))
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
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
                verticalAlignment = Alignment.CenterVertically,
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

            // Karty – kliknutím se vybere
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Text(
                "Klikni na kartu pro výběr",
                color = TextMuted.copy(alpha = 0.6f),
                fontSize = 9.sp
            )
        }
    }
}
