package com.example.termiti

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// TutorialOverlay.kt
// Interaktivní tutoriál: bitva proti prvnímu soupeři kampaně s pevným balíčkem
// a nápovědou, která se posouvá podle toho, co hráč ve hře udělá.
// Zatím se spouští z Profil → DEBUG (GameViewModel.startTutorial).
// ============================================================

/**
 * Balíček pro tutoriál (30 karet) – stejné složení jako startovní „Začátečník"
 * (GameViewModel.grantStarterDeck): levné útoky, stavba, doly a pár zdrojů, nic složitého.
 * Tutoriál tak nezávisí na tom, co má hráč zrovna v aktivním balíčku.
 */
internal val TUTORIAL_DECK: Map<String, Int> = mapOf(
    "012" to 2, "065" to 1, "003" to 2, "D01" to 2, "013" to 2, "038" to 2, "015" to 2,
    "D04" to 3, "001" to 3, "056" to 2, "047" to 3, "007" to 3, "010" to 3
)

/**
 * Krok tutoriálu.
 *  • INFO – hráč čte a pokračuje tlačítkem.
 *  • PLAY – čeká, až hráč něco udělá (zahraje/zahodí kartu nebo ukončí tah).
 *  • OPPONENT – běží tah soupeře; sám skončí, až je hráč zase na tahu.
 */
private enum class TutKind { INFO, PLAY, OPPONENT }

private class TutStep(val kind: TutKind, val text: (AppStrings, GameState) -> String)

private val TUTORIAL_STEPS = listOf(
    TutStep(TutKind.INFO)     { s, g -> s.tutGoal.format(g.playerWinTarget) },
    TutStep(TutKind.INFO)     { s, _ -> s.tutCastle },
    TutStep(TutKind.INFO)     { s, _ -> s.tutResources },
    TutStep(TutKind.PLAY)     { s, _ -> s.tutPlayCard },
    TutStep(TutKind.OPPONENT) { s, _ -> s.tutOpponentTurn },
    TutStep(TutKind.INFO)     { s, _ -> s.tutCombo },
    TutStep(TutKind.INFO)     { s, _ -> s.tutDiscard },
    TutStep(TutKind.INFO)     { s, _ -> s.tutEndTurn.format(s.endTurn) },
    TutStep(TutKind.INFO)     { s, _ -> s.tutDone }
)

/**
 * Nápověda tutoriálu nad bojištěm. Hru pod sebou NEBLOKUJE – zabírá jen vlastní destičku
 * nahoře uprostřed, takže hráč může hrát i během čtení a kroky s tím počítají
 * (krok „zahraj kartu" se splní čímkoli, co hráč na svém tahu udělá).
 *
 * @param actionCount počet hráčových akcí v této hře (zahrání + zahození) – roste = hráč něco udělal
 */
@Composable
fun TutorialOverlay(
    state: GameState,
    actionCount: Int,
    modifier: Modifier = Modifier,
    onFinish: () -> Unit
) {
    val s = LocalStrings.current
    var index by remember { mutableIntStateOf(0) }
    val step  = TUTORIAL_STEPS.getOrNull(index) ?: return
    val playerTurn = state.activePlayer == ActivePlayer.PLAYER

    // PLAY: počkej na první hráčovu akci nebo na předání tahu soupeři
    var actionsAtStepStart by remember(index) { mutableIntStateOf(actionCount) }
    LaunchedEffect(index, actionCount, playerTurn) {
        when (step.kind) {
            TutKind.PLAY     -> if (actionCount > actionsAtStepStart || !playerTurn) index++
            // OPPONENT: když je hráč už zase na tahu (soupeř dohrál, nebo hráč hrál kombo
            // a tah vůbec nepředal), krok nemá co ukazovat → dál
            TutKind.OPPONENT -> if (playerTurn) index++
            TutKind.INFO     -> Unit
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .buttonTexture(R.drawable.plain_button_longer)
            // pohlcuje klepnutí na destičku, ať nepropadnou na bojiště pod ní
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            s.tutTitle.format(index + 1, TUTORIAL_STEPS.size),
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
            if (step.kind == TutKind.INFO) {
                val last = index == TUTORIAL_STEPS.lastIndex
                PlainButton(
                    text      = if (last) s.tutFinish else s.tutNext,
                    modifier  = Modifier.width(110.dp).height(26.dp),
                    textColor = Gold,
                    fontSize  = 9.sp,
                    paddingH  = 4.dp,
                    paddingV  = 0.dp,
                    onClick   = { if (last) onFinish() else index++ }
                )
            } else {
                Text(
                    if (step.kind == TutKind.PLAY) s.tutWaitingYou else s.tutWaitingOpponent,
                    color = Color(0xFF7EC8E3), fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
