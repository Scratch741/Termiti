package com.example.termiti

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val ToastBg     = Color(0xFF1A1520)
private val ToastBorder = Color(0xFF5A4A7A)
private val XpColor     = Color(0xFF7EE8A2)
private val GoldColor   = Color(0xFFD4A843)
private val GemColor    = Color(0xFF6EE0F0)
private val LevelColor  = Color(0xFFFFD700)

/**
 * Overlay toast vpravo dole – zobrazí odměny (XP, zlato, drahokamy, level-up).
 * Umísti do hlavního Surface v MainActivity jako poslední child.
 */
@Composable
fun RewardToastOverlay() {
    var currentEvent by remember { mutableStateOf<RewardNotifier.RewardEvent?>(null) }
    var visible      by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RewardNotifier.events.collect { event ->
            // Pokud je toast viditelný, krátce počkej před dalším
            if (visible) {
                visible = false
                delay(300)
            }
            currentEvent = event
            visible = true
            delay(5200)
            visible = false
            delay(350)
            currentEvent = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
            exit    = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
        ) {
            currentEvent?.let { ev ->
                RewardToastCard(ev)
            }
        }
    }
}

@Composable
private fun RewardToastCard(ev: RewardNotifier.RewardEvent) {
    Column(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ToastBg)
            .border(1.dp, ToastBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Zdroj (quest název apod.) – skryj při level-upu, tam ho nahrazuje banner
        if (ev.source.isNotEmpty() && !ev.levelUp) {
            Text(
                ev.source,
                color      = Color(0xFFBBAACF),
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 2
            )
        }

        // Level-up banner
        if (ev.levelUp) {
            Text(
                "🎉 LEVEL ${ev.newLevel}!",
                color      = LevelColor,
                fontSize   = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Odměny
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ev.xp   > 0) RewardChip("⭐", "+${ev.xp} XP",  XpColor)
            if (ev.gold > 0) RewardChip("🪙", "+${ev.gold}",    GoldColor)
            if (ev.gems > 0) RewardChip("💎", "+${ev.gems}",    GemColor)
        }
    }
}

@Composable
private fun RewardChip(icon: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(icon,  fontSize = 12.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
