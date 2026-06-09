package com.example.termiti

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Rodiny fontů použité na kartách:
 *  - CinzelBold            → názvy karet
 *  - CinzelExtraBold       → čísla (poškození, zdroje, ceny)
 *  - MerriweatherSemiBold  → popisy karet
 */
val CinzelBold = FontFamily(
    Font(R.font.cinzel_bold, FontWeight.Bold)
)

val CinzelExtraBold = FontFamily(
    Font(R.font.cinzel_extrabold, FontWeight.ExtraBold)
)

val MerriweatherSemiBold = FontFamily(
    Font(R.font.merriweather_semibold, FontWeight.SemiBold)
)
