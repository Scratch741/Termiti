package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── TEST rozložení bojiště „Aréna“ (GameSettings.arenaLayout) ────────────────
// Návrh: design/ui_redesign/bojiste2/navrh_A2.png. Rohové panely z kouřového skla
// s tenkou bronzovou hranou: já vlevo dole, soupeř zrcadlově vpravo nahoře.

private val ArenaGlass  = Color(0xFF0A080E).copy(alpha = 0.62f)
private val ArenaBronze = Color(0xFFB98A4E).copy(alpha = 0.55f)
private val ArenaShape  = RoundedCornerShape(10.dp)

/** Kouřové sklo s bronzovou hranou. */
private fun Modifier.arenaGlass(): Modifier = this
    .clip(ArenaShape)
    .background(ArenaGlass)
    .border(1.dp, ArenaBronze, ArenaShape)

/** Kulatý portrét s bronzovým lemem a září strany (tyrkysová = já, rudá = soupeř). */
@Composable
private fun ArenaPortrait(avatar: String, isPlayer: Boolean, size: Int = 54) {
    val glow = if (isPlayer) TealLight else Crimson
    Box(
        Modifier
            .size(size.dp)
            .shadow(10.dp, RoundedCornerShape(50), ambientColor = glow, spotColor = glow)
            .clip(RoundedCornerShape(50))
            .background(Color.Black)
            .border(2.dp, Color(0xFFB98A4E), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) { AvatarDisplay(avatar, sizeDp = if (avatarResId(avatar) != null) size.toFloat() else size * 0.7f) }
}

/**
 * Suroviny v mřížce 2 × 2 (magie, útok / kámen, chaos): ikona, zásoba, přírůstek dolu
 * (+N; zablokovaný důl rudě). Úzký panel – v řádku by do něj zajela ruka.
 */
@Composable
private fun ArenaResources(state: PlayerState) {
    Column(
        Modifier.arenaGlass().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ResourceType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { t ->
                    val amount  = state.resources[t] ?: 0
                    val mine    = state.mines[t] ?: 0
                    val blocked = (state.mineBlockedTurns[t] ?: 0) > 0
                    Row(
                        Modifier.width(62.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Image(painterResource(resourceIconRes(t)), contentDescription = null, modifier = Modifier.size(15.dp))
                        Text("$amount", color = resourceColor(t), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "+$mine",
                            color = if (blocked) HpRed else TextMuted,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Můj roh: portrét v levém horním rohu (jméno pod ním), suroviny vpravo od něj – zrcadlo soupeře. */
@Composable
fun ArenaPlayerCorner(state: PlayerState, avatar: String, name: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ArenaPortrait(avatar, isPlayer = true)
            Text(
                name, color = TealLight, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Box(Modifier.padding(top = 2.dp)) { ArenaResources(state) }
    }
}

/** Roh soupeře: portrét v pravém horním rohu (jméno pod ním), suroviny vlevo od něj. */
@Composable
fun ArenaOpponentCorner(state: PlayerState, avatar: String, name: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 2.dp)) { ArenaResources(state) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ArenaPortrait(avatar, isPlayer = false)
            Text(
                name, color = Color(0xFFE7B7A8), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Levý dolní roh: herní informace pod sebou – menu + log, kolo, čí je tah, balíček. */
@Composable
fun ArenaInfoStack(
    currentTurn: Int,
    isPlayerTurn: Boolean,
    isComboTurn: Boolean,
    deckSize: Int,
    onMenu: () -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val active = isPlayerTurn || isComboTurn
    val turnText = when { isComboTurn -> "COMBO"; isPlayerTurn -> s.yourTurn; else -> s.opponentTurn }
    Column(modifier.width(124.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Stav hry: kolo, čí je tah, balíček
        Column(
            Modifier.fillMaxWidth().arenaGlass().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("${s.round} $currentTurn", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardBackMini(playerCardBackResId(), Modifier.size(width = 11.dp, height = 16.dp))
                    Text("$deckSize", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(turnText, color = if (active) TealLight else Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        // Menu a log
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(width = 40.dp, height = 32.dp).arenaGlass().clickable { onMenu() },
                contentAlignment = Alignment.Center
            ) { Text("☰", color = TextPrimary, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).height(32.dp).arenaGlass().clickable { onLog() },
                contentAlignment = Alignment.Center
            ) { Text("Log", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Ukončit tah: bronz, když jsem na tahu; jinak tlumené železo. */
@Composable
fun ArenaEndTurnButton(label: String, active: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    PlainButton(
        text      = label.uppercase(),
        modifier  = modifier.width(140.dp),
        textColor = if (active) BronzeButtonText else DialogTextMuted.copy(alpha = 0.6f),
        fontSize  = 13.sp,
        paddingH  = 0.dp,
        paddingV  = 11.dp,
        buttonRes = if (active) R.drawable.button_bronze else R.drawable.button_iron,
        onClick   = { onClick?.invoke() }
    )
}
