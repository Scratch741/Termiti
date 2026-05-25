package com.example.termiti

import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ChaosOrange, rarityColor → GameColors.kt

private fun resColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> MagicPurple
    ResourceType.ATTACK -> AttackRed
    ResourceType.STONES -> StoneColor
    ResourceType.CHAOS  -> ChaosOrange
}

private fun resIcon(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> "✨"
    ResourceType.ATTACK -> "⚔️"
    ResourceType.STONES -> "🪨"
    ResourceType.CHAOS  -> "🌀"
}

private fun draftEffectIcon(card: Card) = when (card.effects.firstOrNull()) {
    is CardEffect.AttackPlayer      -> "⚔️"
    is CardEffect.AttackCastle      -> "🎯"
    is CardEffect.AttackWall        -> "💣"
    is CardEffect.BuildCastle       -> "🏰"
    is CardEffect.BuildWall         -> "🧱"
    is CardEffect.AddResource       -> "💰"
    is CardEffect.AddMine           -> "⛏️"
    is CardEffect.StealResource     -> "🗡️"
    is CardEffect.DrainResource     -> "☠️"
    is CardEffect.ConditionalEffect -> "🔮"
    is CardEffect.DestroyMine       -> "💥"
    is CardEffect.StealCard         -> "🃏"
    is CardEffect.BurnCard          -> "🔥"
    is CardEffect.AddCardsToDeck    -> "📦"
    is CardEffect.AddToOpponentDeck -> "💣"
    is CardEffect.TrapOnDraw        -> "💥"
    is CardEffect.DrawCard          -> "🎴"
    is CardEffect.StealCastle        -> "🧛"
    is CardEffect.AddResourceDelayed  -> "⏳"
    is CardEffect.BlockMine           -> "🚫"
    is CardEffect.XScaledAttackPlayer -> "⚔️"
    is CardEffect.XScaledAttackCastle -> "🎯"
    is CardEffect.XScaledBuildCastle  -> "🏰"
    is CardEffect.XScaledDualResource -> "💰"
    is CardEffect.SwapHands           -> "🔄"
    is CardEffect.RandomizeHands      -> "🎲"
    is CardEffect.DrawPerCardPlayed        -> "🎴"
    is CardEffect.GainResourcePerCardPlayed -> "⚡"
    is CardEffect.GainCastlePerCardPlayed   -> "🏯"
    is CardEffect.ShapeShift                -> "🎭"
    is CardEffect.ConvertMine               -> "🔀"
    is CardEffect.DecisionBurnOpponent      -> "🔥"
    is CardEffect.DecisionChooseType        -> "🎯"
    is CardEffect.DecisionFromDiscard       -> "♻️"
    is CardEffect.DecisionFromDeck          -> "🔍"
    is CardEffect.DecisionMine              -> "⛏️"
    is CardEffect.DrawBoth                  -> "🎴"
    is CardEffect.CloneNextPlayed           -> "🔁"
    is CardEffect.SmartJoker                -> "🃏"
    null                              -> "❓"
}

// ─── Draft Screen ─────────────────────────────────────────────────────────────
@Composable
fun ArenaDraftScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val offers     by viewModel.arenaOffers
    val draft       = viewModel.arenaDraft
    val draftCount  = draft.size

    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(
                colors = listOf(Color(0xFF16102A), BgDeep),
                radius = 1200f
            ))
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Header ───────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("← Zpět", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ARÉNA — DRAFT", color = Gold,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                    )
                    Text("$draftCount / 30 karet", color = TextMuted, fontSize = 9.sp)
                }

                Text(
                    "Vyber jednu kartu",
                    color = TextMuted.copy(alpha = 0.6f), fontSize = 9.sp
                )
            }

            // ── Progress bar ─────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                Box(
                    Modifier.fillMaxWidth(draftCount / 30f).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(Teal, Gold)))
                )
            }

            // ── Main area: cards + stats panel ───────────────────────
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 3 Card Offers (centre)
                Row(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    offers.forEach { card ->
                        Spacer(Modifier.width(8.dp))
                        DraftCardChoice(card = card, onClick = { viewModel.pickArenaCard(card) })
                        Spacer(Modifier.width(8.dp))
                    }
                }

                // Right stats panel
                DraftStatsPanel(
                    draft = draft,
                    allCards = viewModel.allCards,
                    modifier = Modifier.width(160.dp).fillMaxHeight()
                )
            }
        }
    }
}

// ─── Draft Stats Panel ────────────────────────────────────────────────────────
@Composable
private fun DraftStatsPanel(
    draft: List<Card>,
    allCards: List<Card>,
    modifier: Modifier = Modifier
) {
    val total = draft.size.coerceAtLeast(1).toFloat()

    // Counts by resource type
    val byType = ResourceType.entries.associateWith { type ->
        draft.count { it.costType == type }
    }

    // Counts by category
    fun Card.cat() = when (effects.firstOrNull()) {
        is CardEffect.AttackPlayer,
        is CardEffect.AttackCastle,
        is CardEffect.AttackWall,
        is CardEffect.StealResource,
        is CardEffect.DrainResource,
        is CardEffect.DestroyMine,
        is CardEffect.BurnCard,
        is CardEffect.StealCard,
        is CardEffect.ConditionalEffect -> "Útok"
        is CardEffect.BuildCastle,
        is CardEffect.BuildWall         -> "Obrana"
        is CardEffect.AddResource       -> "Zdroje"
        is CardEffect.AddMine           -> "Doly"
        else                            -> "Ostatní"
    }
    val byCategory = listOf("Útok", "Obrana", "Zdroje", "Doly", "Ostatní").associateWith { cat ->
        draft.count { it.cat() == cat }
    }

    // Recent 6 picks
    val recent = draft.takeLast(6).reversed()

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BgPanel.copy(alpha = 0.7f))
            .border(1.dp, Gold.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "SLOŽENÍ BALÍČKU",
            color = TextMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
        )

        // By resource type
        Text("Zdroje", color = TextMuted.copy(alpha = 0.6f), fontSize = 7.sp, letterSpacing = 1.sp)
        ResourceType.entries.forEach { type ->
            val count = byType[type] ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(resIcon(type), fontSize = 8.sp, modifier = Modifier.width(14.dp))
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(count / total).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(resColor(type).copy(alpha = 0.7f))
                    )
                }
                Text(
                    "$count",
                    color = if (count > 0) resColor(type) else TextMuted.copy(alpha = 0.3f),
                    fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(14.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        HorizontalDivider(color = Gold.copy(alpha = 0.08f))

        // By category
        Text("Efekty", color = TextMuted.copy(alpha = 0.6f), fontSize = 7.sp, letterSpacing = 1.sp)
        val catColors = mapOf(
            "Útok"    to AttackRed,
            "Obrana"  to StoneColor,
            "Zdroje"  to MagicPurple,
            "Doly"    to Gold,
            "Ostatní" to TextMuted
        )
        byCategory.forEach { (cat, count) ->
            if (count == 0) return@forEach
            val cc = catColors[cat] ?: TextMuted
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(count / total).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(cc.copy(alpha = 0.55f))
                    )
                }
                Text(
                    "$count",
                    color = cc, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(14.dp),
                    textAlign = TextAlign.End
                )
                Text(cat, color = cc.copy(alpha = 0.7f), fontSize = 7.sp, modifier = Modifier.width(40.dp))
            }
        }

        if (recent.isNotEmpty()) {
            HorizontalDivider(color = Gold.copy(alpha = 0.08f))
            Text("Poslední výběry", color = TextMuted.copy(alpha = 0.6f), fontSize = 7.sp, letterSpacing = 1.sp)
            recent.forEach { card ->
                val cc = resColor(card.costType)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(draftEffectIcon(card), fontSize = 8.sp)
                    Text(
                        card.name,
                        color = TextPrimary.copy(alpha = 0.7f),
                        fontSize = 7.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        resIcon(card.costType),
                        fontSize = 7.sp,
                        modifier = Modifier.width(10.dp)
                    )
                }
            }
        }
    }
}

// ─── Draft Card Choice ────────────────────────────────────────────────────────
@Composable
private fun DraftCardChoice(card: Card, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Karta v plné podobě – scale 1.25× aby byla dobře vidět
        Box(modifier = Modifier.scale(1.25f)) {
            CardView(
                card        = card,
                canPlay     = true,
                discardMode = false,
                showFade    = false,
                onClick     = onClick
            )
        }

        // Kompenzace vizuálního přesahu ze scale(1.25)
        Spacer(Modifier.height(36.dp))

        // Tlačítko Vybrat pod kartou
        Box(
            Modifier
                .width(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Teal.copy(alpha = 0.3f), Gold.copy(alpha = 0.2f))))
                .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .clickable { SoundManager.playMenuTap(); onClick() }
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("VYBRAT", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

// ─── Arena End Screen ─────────────────────────────────────────────────────────
@Composable
fun ArenaEndScreen(wins: Int, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(
                colors = listOf(Color(0xFF1A0A0A), BgDeep),
                radius = 1200f
            )),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1020), BgPanel)))
                .border(1.dp, Crimson.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 40.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💀", fontSize = 32.sp)
                Text(
                    "ARÉNA UKONČENA",
                    color = Crimson, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                )
            }

            // Win counter badge
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(
                        if (wins >= 5) Gold.copy(alpha = 0.12f)
                        else if (wins >= 3) Teal.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        1.dp,
                        if (wins >= 5) Gold.copy(alpha = 0.5f)
                        else if (wins >= 3) TealLight.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚔️ $wins",
                        color = when {
                            wins >= 5 -> Gold
                            wins >= 3 -> TealLight
                            else      -> TextPrimary
                        },
                        fontSize = 26.sp, fontWeight = FontWeight.Bold
                    )
                    Text("vítězství", color = TextMuted, fontSize = 11.sp)
                }
            }

            Text(
                when {
                    wins == 0 -> "Příště to vyjde!"
                    wins < 3  -> "Dobrý začátek."
                    wins < 5  -> "Solidní výkon!"
                    wins < 8  -> "Výborně! Jsi silný protivník."
                    else      -> "Legenda arény!"
                },
                color = TextPrimary, fontSize = 11.sp, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Teal.copy(alpha = 0.15f))
                    .border(1.dp, TealLight.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ZPĚT DO MENU",
                    color = TealLight, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
            }
        }
    }
}
