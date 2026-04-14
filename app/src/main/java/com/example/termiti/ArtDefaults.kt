package com.example.termiti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

// ═══════════════════════════════════════════════════════════════════════════════
//  ArtDefaults.kt
//  Globální nastavení zobrazení ilustrací u textured karet.
//
//  Změnou konstant níže upravíš VŠECHNY karty najednou.
//  Per-karta doladění se nastavuje přímo v Card() přes artBiasX/Y a artScale.
// ═══════════════════════════════════════════════════════════════════════════════

object ArtDefaults {

    /**
     * Základní zoom ilustrace platný pro všechny karty.
     *   1.0f = bez změny (Crop vyplní oblast karty)
     *   1.2f = 20 % přiblížit (zobrazí se menší výřez)
     *   0.85f = 15 % oddálit (vidět více z obrázku, ale mohou vzniknout prázdné okraje)
     */
    const val SCALE: Float = 1.0f

    /**
     * Globální posun ilustrace vodorovně.
     *   -1.0f = přitáhnout k levému okraji
     *    0.0f = vycentrovat
     *   +1.0f = přitáhnout k pravému okraji
     */
    const val BIAS_X: Float = 0.0f

    /**
     * Globální posun ilustrace svisle.
     *   -1.0f = přitáhnout k hornímu okraji  ← doporučeno, oblast obrázku je top ~50 % karty
     *    0.0f = vycentrovat (střed obrázku = střed celé karty → zobrazuje se "moc dole")
     *   +1.0f = přitáhnout k dolnímu okraji
     *
     *  Výchozí hodnota -1.0f: střed ilustrace se zobrazí u horního okraje oblasti karty.
     */
    const val BIAS_Y: Float = -1.0f
}

/**
 * Vrátí ID prostředku pro překryvný obrázek rarity.
 */
@Composable
fun rarityOverlayResource(rarity: Rarity): Int {
    val context = LocalContext.current
    val name = when (rarity) {
        Rarity.COMMON -> "rarity_common"
        Rarity.RARE -> "rarity_rare"
        Rarity.EPIC -> "rarity_epic"
        Rarity.LEGENDARY -> "rarity_legendary"
    }
    return remember(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
}

// ─── Pomocné funkce (dostupné z GameScreen i DeckBuilderScreen) ────────────────

/**
 * Modifier pro ilustraci karty: aplikuje globální scale * per-karta artScale přes graphicsLayer.
 *
 * POZOR: Nepoužívej Modifier.scale() — ten škáluje composable až po layoutu,
 * takže BiasAlignment v parametru alignment u Image nepočítá se správnou velikostí.
 * graphicsLayer škáluje obsah uvnitř bounds, takže ContentScale.Crop + alignment fungují správně.
 */
fun artModifier(card: Card): Modifier =
    Modifier
        .fillMaxSize()
        .graphicsLayer {
            val s = ArtDefaults.SCALE * card.artScale
            scaleX = s
            scaleY = s
        }

/**
 * BiasAlignment pro ilustraci karty.
 * Globální bias + per-karta korekce. Hodnoty mimo rozsah <-1, 1> posunou obrázek "mimo" hranice karty.
 */
fun artAlignment(card: Card): BiasAlignment =
    BiasAlignment(
        horizontalBias = ArtDefaults.BIAS_X + card.artBiasX,
        verticalBias   = ArtDefaults.BIAS_Y + card.artBiasY
    )
