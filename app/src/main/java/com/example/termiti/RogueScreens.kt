package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════
// Roguelike obrazovky: draft (výběr balíčku), odměna, konec runu.
// ═══════════════════════════════════════════════════════════════════════════

// ─── Draft: výběr 15 karet s bodovým rozpočtem ─────────────────────────────
@Composable
fun RogueDraftScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val offers      by viewModel.rogueOffers
    val spent       by viewModel.rogueBudgetSpent
    val draft        = viewModel.rogueDraft
    val picked       = draft.size

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF160F14), BgDeep), radius = 1200f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlainButton("← Zpět", textColor = TextMuted, fontSize = 10.sp,
                    paddingH = 10.dp, paddingV = 5.dp, onClick = onBack)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ROGUELIKE — SESTAV BALÍČEK", color = Gold,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("$picked / ${RogueConfig.DECK_SIZE} karet   ·   Rozpočet $spent / ${RogueConfig.BUDGET}",
                        color = TextMuted, fontSize = 9.sp)
                }
                Text("Vyber jednu kartu", color = TextMuted.copy(alpha = 0.6f), fontSize = 9.sp)
            }

            // Progress bar (počet karet)
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                Box(
                    Modifier.fillMaxWidth(picked / RogueConfig.DECK_SIZE.toFloat()).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(TealLight, Gold)))
                )
            }

            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    offers.forEach { card ->
                        Spacer(Modifier.width(10.dp))
                        RogueCardChoice(card = card, onClick = { viewModel.pickRogueCard(card) })
                        Spacer(Modifier.width(10.dp))
                    }
                }
                RogueDeckPanel(draft = draft, modifier = Modifier.width(150.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun RogueCardChoice(card: Card, onClick: () -> Unit) {
    val cost = RogueConfig.rarityBudgetCost(card.rarity)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.scale(1.2f)) {
            CardView(card = card, canPlay = true, discardMode = false, showFade = false, onClick = onClick)
        }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.rarity.label, color = rarityColor(card.rarity), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            if (cost > 0) Text("• $cost b", color = Gold, fontSize = 8.sp)
        }
        PlainButton("VYBRAT", modifier = Modifier.width(104.dp), textColor = TextPrimary,
            fontSize = 10.sp, paddingH = 0.dp, paddingV = 6.dp, onClick = onClick)
    }
}

@Composable
private fun RogueDeckPanel(draft: List<Card>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BgPanel.copy(alpha = 0.6f))
            .border(1.dp, Gold.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("BALÍČEK (${draft.size})", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        HorizontalDivider(color = Gold.copy(alpha = 0.1f))
        // Rozpad podle rarity
        Rarity.entries.reversed().forEach { r ->
            val n = draft.count { it.rarity == r }
            if (n > 0) Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(r.label, color = rarityColor(r), fontSize = 8.sp)
                Text("$n", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = Gold.copy(alpha = 0.1f))
        Text("Poslední", color = TextMuted.copy(alpha = 0.6f), fontSize = 7.sp, letterSpacing = 1.sp)
        draft.takeLast(8).reversed().forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EffectIconView(c, size = 9.dp, fontSizeSp = 7f)
                Text(c.displayName, color = TextPrimary.copy(alpha = 0.75f), fontSize = 7.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── Odměna po výhře ────────────────────────────────────────────────────────
@Composable
fun RogueRewardScreen(viewModel: GameViewModel, onExit: () -> Unit) {
    val run = viewModel.rogueRun.value ?: return

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF0F160F), BgDeep), radius = 1200f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: postup + HP
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlainButton("Vzdát run", textColor = TextMuted, fontSize = 9.sp, paddingH = 8.dp, paddingV = 4.dp, onClick = onExit)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VÍTĚZSTVÍ", color = TealLight, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("${run.actTitle}  ·  následuje ${run.battleLabel}", color = TextMuted, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(painterResource(R.drawable.castle_icon), contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("${run.hp} / ${run.maxCastle}", color = HpGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Image(painterResource(R.drawable.wall_icon), contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("${run.wall}", color = StoneColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text("Vyber jednu odměnu", color = Gold, fontSize = 10.sp, letterSpacing = 1.sp)

            // 3 karty
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                run.rewardCards.forEach { card ->
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.scale(1.1f)) {
                            CardView(card = card, canPlay = true, discardMode = false, showFade = false,
                                onClick = { viewModel.applyRogueReward(RogueReward.AddCard(card)) })
                        }
                        Spacer(Modifier.height(24.dp))
                        PlainButton("PŘIDAT", modifier = Modifier.width(96.dp), textColor = TealLight,
                            fontSize = 9.sp, paddingH = 0.dp, paddingV = 5.dp,
                            onClick = { viewModel.applyRogueReward(RogueReward.AddCard(card)) })
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }

            // Statové odměny + skip
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RogueStatButton("🏰 +${RogueConfig.REWARD_MAX_CASTLE} max hrad", Gold) { viewModel.applyRogueReward(RogueReward.MaxCastle) }
                RogueStatButton("🧱 +${RogueConfig.REWARD_WALL} hradby", StoneColor) { viewModel.applyRogueReward(RogueReward.Wall) }
                RogueStatButton("✚ Oprava +${RogueConfig.REWARD_REPAIR}", HpGreen) { viewModel.applyRogueReward(RogueReward.Repair) }
                RogueStatButton("⛏️ +1 důl magie", MagicPurple) { viewModel.applyRogueReward(RogueReward.MineMagic) }
                RogueStatButton("Přeskočit", TextMuted) { viewModel.skipRogueReward() }
            }
        }
    }
}

@Composable
private fun RogueStatButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Konec runu ─────────────────────────────────────────────────────────────
@Composable
fun RogueEndScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val victory = viewModel.rogueVictory.value
    val run     = viewModel.rogueRun.value
    val battlesWon = (run?.battleIndex ?: 0).coerceAtMost(RogueConfig.TOTAL_BATTLES)

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(if (victory) Color(0xFF14180E) else Color(0xFF1A0A0A), BgDeep),
                radius = 1200f
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1420), BgPanel)))
                .border(1.dp, (if (victory) Gold else Crimson).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 44.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painterResource(if (victory) R.drawable.trophy_icon else R.drawable.skull_icon),
                contentDescription = null, modifier = Modifier.size(40.dp)
            )
            Text(
                if (victory) "RUN DOKONČEN!" else "RUN SKONČIL",
                color = if (victory) Gold else Crimson,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(
                if (victory) "Probil ses všemi ${RogueConfig.TOTAL_BATTLES} bitvami."
                else "Tvůj hrad padl.",
                color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Gold.copy(alpha = 0.08f))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text("Vyhrané bitvy: $battlesWon / ${RogueConfig.TOTAL_BATTLES}",
                    color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            PlainButton("ZPĚT DO MENU", modifier = Modifier.fillMaxWidth(),
                textColor = TextPrimary, fontSize = 12.sp, paddingH = 0.dp, paddingV = 10.dp,
                buttonRes = R.drawable.plain_button_longer, onClick = onBack)
        }
    }
}
