package com.example.termiti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Herní density (virtuální rozlišení) nastavená v DesignFrame.
 * null = běžíme mimo rám (např. preview) → žádné přeškálování.
 */
val LocalGameDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Dialog, který uvnitř obnoví herní škálování.
 *
 * Dialog() vytváří vlastní okno s vlastním Compose rootem a ten si LocalDensity
 * přepíše zpět na systémovou hodnotu – density override z DesignFrame do něj
 * nepropadne. Texty a dp rozměry v dialozích (Zůstat/Vzdát se, game-over…) by
 * tak na tabletech zůstaly v systémovém (malém) měřítku. Tento wrapper uvnitř
 * dialogu znovu nastaví herní density, takže obsah škáluje stejně jako zbytek hry.
 */
@Composable
fun GameDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val gameDensity = LocalGameDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        if (gameDensity != null) {
            CompositionLocalProvider(LocalDensity provides gameDensity) { content() }
        } else {
            content()
        }
    }
}
