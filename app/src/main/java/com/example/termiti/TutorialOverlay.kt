package com.example.termiti

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// TutorialOverlay.kt
// Skriptovaný tutoriál: bitva proti prvnímu soupeři kampaně, ve které je předem
// dané všechno – pořadí karet obou balíčků, kdo začíná, co zahraje soupeř i co má
// v každém kroku udělat hráč. Jiné akce, než jakou krok čeká, hra ignoruje
// (GameViewModel.tutorialExpect), a to, na co krok ukazuje, svítí zlatě.
// Zatím se spouští z Profil → DEBUG (GameViewModel.startTutorial).
//
// Průběh (hráč začíná, hrad 30, hradby 15; soupeř hrad 25, hradby 5):
//   mulligan  ruka Mobilizace, Silný úder, Palisáda, Drak → Drak se vymění za Rychlý útok
//   1. kolo   Mobilizace (kombo, +3 útok) → Silný úder 11: hradby soupeře 0, hrad 19
//             soupeř: Goblin (hradby 15 → 13)
//   2. kolo   Palisáda (hradby 19)
//             soupeř: Mobilizace + Ogr 9 (hradby 10)
//   3. kolo   zahodit Zoufalého žolda (+3 útok) → Rychlý útok 6 → Ogr 9: hrad soupeře 4
//             soupeř: Zoufalý žold 5 (hradby 5)
//   4. kolo   Ohnivá koule −8 přímo do hradu → výhra
// Při změně karet 012/007/010/051/001/132/047/003/046 je potřeba čísla přepočítat.
// ============================================================

/**
 * Balíček pro tutoriál – stejné jednoduché karty jako startovní „Začátečník".
 * Karty z [TUTORIAL_PLAYER_ORDER] jdou navrch v daném pořadí, zbytek je jen výplň
 * (ke které se skript nedostane).
 */
internal val TUTORIAL_DECK: Map<String, Int> = mapOf(
    "012" to 2, "065" to 1, "003" to 2, "D01" to 2, "013" to 2, "038" to 2, "015" to 2,
    "D04" to 3, "001" to 3, "056" to 2, "047" to 3, "007" to 3, "010" to 3
)

/** Hráčův balíček shora: 4 karty úvodní ruky, náhrada za mulligan a líznutí ve 2.–4. kole. */
internal val TUTORIAL_PLAYER_ORDER = listOf("012", "007", "010", "051", "001", "132", "047", "003")

/** Soupeřova úvodní ruka = karty, které zahraje, v pořadí zahrání. */
internal val TUTORIAL_AI_ORDER = listOf("046", "012", "047", "132")

/** Hráčův balíček pro tutoriál v pevném pořadí (nemíchá se). */
internal fun tutorialPlayerDeck(allCards: List<Card>): List<Card> {
    val byId = allCards.associateBy { it.id }
    val rest = TUTORIAL_DECK.toMutableMap()
    TUTORIAL_PLAYER_ORDER.forEach { id -> rest[id]?.let { if (it > 1) rest[id] = it - 1 else rest.remove(id) } }
    val ids = TUTORIAL_PLAYER_ORDER + rest.flatMap { (id, n) -> List(n) { id } }
    return ids.mapNotNull { byId[it] }
}

/** Co krok čeká. Datové třídy se porovnávají hodnotou – viz GameViewModel.tutorialBlocks. */
internal sealed class TutExpect {
    /** Hráč čte a pokračuje tlačítkem. */
    object Info : TutExpect()
    /** Běží tah soupeře; krok skončí, až je hráč zase na tahu. */
    object Opponent : TutExpect()
    /** Mulligan: vyměnit právě tuto kartu. */
    data class Mulligan(val baseId: String) : TutExpect()
    data class Play(val baseId: String) : TutExpect()
    data class Discard(val baseId: String) : TutExpect()
}

/** Prvek obrazovky, na který krok ukazuje (zlatý pulzující rám – [tutorialGlow]). */
enum class TutTarget {
    PLAYER_CASTLE, PLAYER_WALL, ENEMY_CASTLE,
    /** Levý panel: bílá čísla vpravo = kolik suroviny hráč právě má. */
    RES_AMOUNTS,
    /** Levý panel: zlatá čísla vlevo = doly (přírůstek za kolo). */
    RES_MINES
}

internal class TutStep(
    val expect: TutExpect,
    val targets: Set<TutTarget> = emptySet(),
    val text: (AppStrings, GameState) -> String
)

private fun cardName(id: String) = LanguageManager.cardName(id, id)

internal val TUTORIAL_STEPS: List<TutStep> = listOf(
    TutStep(TutExpect.Mulligan("051")) { s, _ -> s.tutMulligan.format(cardName("051")) },

    TutStep(TutExpect.Info, setOf(TutTarget.PLAYER_CASTLE, TutTarget.ENEMY_CASTLE)) { s, g -> s.tutGoal.format(g.playerWinTarget) },
    TutStep(TutExpect.Info, setOf(TutTarget.PLAYER_WALL))  { s, _ -> s.tutCastle },
    TutStep(TutExpect.Info, setOf(TutTarget.RES_AMOUNTS))  { s, _ -> s.tutResources },
    TutStep(TutExpect.Info, setOf(TutTarget.RES_MINES))    { s, _ -> s.tutMines },
    TutStep(TutExpect.Play("012"), setOf(TutTarget.RES_AMOUNTS)) { s, _ -> s.tutPlayMobilize.format(cardName("012")) },
    TutStep(TutExpect.Play("007"), setOf(TutTarget.ENEMY_CASTLE)) { s, _ -> s.tutPlayStrike.format(cardName("007")) },
    TutStep(TutExpect.Opponent) { s, _ -> s.tutOpponentTurn },

    TutStep(TutExpect.Info, setOf(TutTarget.PLAYER_WALL))  { s, _ -> s.tutWallHit },
    TutStep(TutExpect.Play("010"), setOf(TutTarget.PLAYER_WALL)) { s, _ -> s.tutPlayPalisade.format(cardName("010")) },
    TutStep(TutExpect.Opponent) { s, _ -> s.tutOpponentAgain },

    TutStep(TutExpect.Info, setOf(TutTarget.PLAYER_WALL))  { s, _ -> s.tutOpponentCombo },
    TutStep(TutExpect.Discard("132"), setOf(TutTarget.RES_AMOUNTS)) { s, _ -> s.tutDiscardMerc.format(cardName("132")) },
    TutStep(TutExpect.Play("001"), setOf(TutTarget.ENEMY_CASTLE)) { s, _ -> s.tutPlayQuick.format(cardName("001")) },
    TutStep(TutExpect.Play("047"), setOf(TutTarget.ENEMY_CASTLE)) { s, _ -> s.tutPlayOgre.format(cardName("047")) },
    TutStep(TutExpect.Opponent) { s, _ -> s.tutOpponentAgain },

    TutStep(TutExpect.Play("003"), setOf(TutTarget.ENEMY_CASTLE)) { s, g ->
        s.tutFinisher.format(g.aiState.castleHP, cardName("003"))
    }
)

/** Karta, kterou má hráč v tomto kroku zahrát nebo zahodit (v ruce svítí zlatě). */
internal val TutStep.handCardId: String?
    get() = when (val e = expect) {
        is TutExpect.Play    -> e.baseId
        is TutExpect.Discard -> e.baseId
        else                 -> null
    }

/** Zlatý pulzující rám kolem prvku, na který tutoriál ukazuje. */
internal fun Modifier.tutorialGlow(on: Boolean, corner: Dp = 8.dp): Modifier =
    if (!on) this else composed {
        val pulse by rememberInfiniteTransition(label = "tut_glow").animateFloat(
            initialValue  = 0.45f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label         = "tut_glow_alpha"
        )
        drawWithContent {
            drawContent()
            val r = CornerRadius(corner.toPx())
            drawRoundRect(Gold.copy(alpha = pulse * 0.30f), cornerRadius = r, style = Stroke(7.dp.toPx()))
            drawRoundRect(Gold.copy(alpha = pulse),         cornerRadius = r, style = Stroke(2.5.dp.toPx()))
        }
    }

/**
 * Destička s textem kroku nad bojištěm. Zabírá jen sebe; hru pod sebou neblokuje
 * dotykem, ale akce mimo skript odmítá GameViewModel.
 *
 * @param stepIndex aktuální krok (GameViewModel.tutorialStep)
 * @param onNext    posun na další krok (tlačítko u Info, konec tahu soupeře)
 * @param onFinish  ukončení tutoriálu – hra pak pokračuje volně
 */
@Composable
fun TutorialOverlay(
    state: GameState,
    stepIndex: Int,
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val s = LocalStrings.current
    val step = TUTORIAL_STEPS.getOrNull(stepIndex) ?: return
    val playerTurn = state.activePlayer == ActivePlayer.PLAYER

    // Tah soupeře: krok skončí, až hráč soupeřův tah opravdu viděl a je zase na řadě.
    // (Krok začíná ještě ve chvíli, kdy je na tahu hráč – zahraná karta se teprve vyhodnocuje.)
    var sawOpponent by remember(stepIndex) { mutableStateOf(false) }
    LaunchedEffect(stepIndex, playerTurn) {
        if (step.expect != TutExpect.Opponent) return@LaunchedEffect
        if (!playerTurn) sawOpponent = true
        else if (sawOpponent) onNext()
    }

    // Pojistka: karta, kterou krok chce, v ruce není (skript se rozešel se hrou) →
    // tutoriál skončí a hráč dohraje volně, místo aby uvázl.
    LaunchedEffect(stepIndex, playerTurn, state.playerState.hand.size) {
        val wanted = step.handCardId ?: return@LaunchedEffect
        if (playerTurn && state.playerState.hand.none { it.baseId == wanted }) onFinish()
    }

    Column(
        modifier = modifier
            // Užší než bojiště mínus odznaky hradů v jeho horních rozích (hrad / hradby / ruka)
            .widthIn(max = 290.dp)
            .buttonTexture(R.drawable.plain_button_longer)
            // pohlcuje klepnutí na destičku, ať nepropadnou na bojiště pod ní
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            s.tutTitle.format(stepIndex + 1, TUTORIAL_STEPS.size),
            color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )
        Text(
            step.text(s, state),
            color = TextPrimary, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlainButton(
                text      = s.tutSkip,
                modifier  = Modifier.width(96.dp).height(26.dp),
                textColor = TextMuted,
                fontSize  = 9.sp,
                paddingH  = 4.dp,
                paddingV  = 0.dp,
                onClick   = onFinish
            )
            when (step.expect) {
                TutExpect.Info -> PlainButton(
                    text      = s.tutNext,
                    modifier  = Modifier.width(110.dp).height(26.dp).tutorialGlow(true, corner = 5.dp),
                    textColor = Gold,
                    fontSize  = 9.sp,
                    paddingH  = 4.dp,
                    paddingV  = 0.dp,
                    onClick   = onNext
                )
                TutExpect.Opponent -> Text(
                    s.tutWaitingOpponent,
                    color = Color(0xFF7EC8E3), fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
                else -> Text(
                    s.tutWaitingYou,
                    color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
